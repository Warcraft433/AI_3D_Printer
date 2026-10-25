import React, { useState, useEffect, useRef } from 'react'
import { Camera, CameraOff, AlertTriangle, ShieldCheck, RefreshCw, Volume2, VolumeX, Eye, PauseCircle, PlayCircle, Settings2 } from 'lucide-react'
import { usePrinter } from '../context/PrinterContext'
import api from '../services/api'

export default function AIMonitoring() {
    const { isConnected, printState, pausePrint, resumePrint } = usePrinter()

    // Camera State
    const [devices, setDevices] = useState([])
    const [selectedDeviceId, setSelectedDeviceId] = useState('')
    const [isStreaming, setIsStreaming] = useState(false)
    const [isDetecting, setIsDetecting] = useState(false)
    const [fps, setFps] = useState(0)

    // Detection State
    const [detections, setDetections] = useState([])
    const [defectAlert, setDefectAlert] = useState(null)
    const [audioAlertEnabled, setAudioAlertEnabled] = useState(true)
    const [confThreshold, setConfThreshold] = useState(0.35)
    const [defectLogs, setDefectLogs] = useState([])

    // DOM & Stream Refs
    const videoRef = useRef(null)
    const canvasRef = useRef(null)
    const streamRef = useRef(null)
    const detectionTimerRef = useRef(null)
    const lastAlertSoundTime = useRef(0)

    // 1. Enumerate Available Cameras
    useEffect(() => {
        const getDevices = async () => {
            try {
                if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
                    const allDevices = await navigator.mediaDevices.enumerateDevices()
                    const videoInputs = allDevices.filter(d => d.kind === 'videoinput')
                    setDevices(videoInputs)
                    if (videoInputs.length > 0 && !selectedDeviceId) {
                        setSelectedDeviceId(videoInputs[0].deviceId)
                    }
                }
            } catch (err) {
                console.error('Error enumerating cameras:', err)
            }
        }
        getDevices()
    }, [])

    // 1b. Load historical defect logs from Supabase database
    useEffect(() => {
        api.get('/error-logs/')
            .then(res => {
                if (Array.isArray(res.data)) {
                    setDefectLogs(res.data.map(log => ({
                        id: log.id,
                        error_type: log.error_type,
                        confidence: log.confidence,
                        time: log.timestamp ? new Date(log.timestamp).toLocaleTimeString() : 'Recorded'
                    })))
                }
            })
            .catch(err => console.warn('Could not fetch historical defect logs:', err.message))
    }, [])

    // 2. Start Camera Stream
    const startCamera = async (deviceId) => {
        stopCamera()
        try {
            const constraints = {
                video: deviceId ? { deviceId: { exact: deviceId } } : { facingMode: 'environment' },
                audio: false
            }
            const stream = await navigator.mediaDevices.getUserMedia(constraints)
            streamRef.current = stream
            if (videoRef.current) {
                videoRef.current.srcObject = stream
                await videoRef.current.play()
            }
            setIsStreaming(true)
            setIsDetecting(true)
        } catch (err) {
            console.error('Failed to access webcam:', err)
            alert(`Camera access failed: ${err.message}. Please allow camera permissions.`)
        }
    }

    // 3. Stop Camera Stream
    const stopCamera = () => {
        if (streamRef.current) {
            streamRef.current.getTracks().forEach(track => track.stop())
            streamRef.current = null
        }
        if (videoRef.current) {
            videoRef.current.srcObject = null
        }
        if (detectionTimerRef.current) {
            clearInterval(detectionTimerRef.current)
            detectionTimerRef.current = null
        }
        setIsStreaming(false)
        setIsDetecting(false)
        setDetections([])
        setDefectAlert(null)
    }

    // 4. Audio Alert Generator (Web Audio API - No external asset dependencies)
    const playDefectAlarm = () => {
        if (!audioAlertEnabled) return
        const now = Date.now()
        if (now - lastAlertSoundTime.current < 5000) return // Cooldown 5s
        lastAlertSoundTime.current = now

        try {
            const ctx = new (window.AudioContext || window.webkitAudioContext)()
            const osc = ctx.createOscillator()
            const gain = ctx.createGain()

            osc.type = 'sawtooth'
            osc.frequency.setValueAtTime(880, ctx.currentTime) // A5
            osc.frequency.exponentialRampToValueAtTime(440, ctx.currentTime + 0.4) // Drop to A4

            gain.gain.setValueAtTime(0.3, ctx.currentTime)
            gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.4)

            osc.connect(gain)
            gain.connect(ctx.destination)

            osc.start()
            osc.stop(ctx.currentTime + 0.45)
        } catch (e) {
            console.warn('Audio playback error:', e)
        }
    }

    // 5. Continuous AI Detection Loop
    useEffect(() => {
        if (!isStreaming || !isDetecting) return

        let lastTime = Date.now()
        let framesCount = 0

        const runDetection = async () => {
            if (!videoRef.current || videoRef.current.readyState < 2) return

            const video = videoRef.current
            const canvas = document.createElement('canvas')
            canvas.width = video.videoWidth || 640
            canvas.height = video.videoHeight || 480
            const ctx = canvas.getContext('2d')
            ctx.drawImage(video, 0, 0, canvas.width, canvas.height)

            canvas.toBlob(async (blob) => {
                if (!blob) return

                const formData = new FormData()
                formData.append('frame', blob, 'webcam.jpg')
                formData.append('confidence_threshold', confThreshold)

                try {
                    const res = await api.post('/dashboard/detect-frame/', formData, {
                        headers: { 'Content-Type': 'multipart/form-data' }
                    })

                    if (res.data && res.data.success) {
                        const newDetections = res.data.detections || []
                        setDetections(newDetections)

                        // Update FPS counter
                        framesCount++
                        const now = Date.now()
                        if (now - lastTime >= 2000) {
                            setFps(Math.round((framesCount / (now - lastTime)) * 1000))
                            framesCount = 0
                            lastTime = now
                        }

                        if (res.data.defect_detected) {
                            const topDefect = newDetections[0]
                            setDefectAlert({
                                label: topDefect.label,
                                confidence: topDefect.confidence,
                                time: res.data.timestamp || new Date().toLocaleTimeString()
                            })

                            playDefectAlarm()

                            setDefectLogs(prev => [
                                {
                                    id: Date.now(),
                                    error_type: topDefect.label,
                                    confidence: topDefect.confidence,
                                    time: res.data.timestamp || new Date().toLocaleTimeString()
                                },
                                ...prev.slice(0, 20)
                            ])
                        }
                    }
                } catch (err) {
                    console.warn('Frame detection request error:', err.message)
                }
            }, 'image/jpeg', 0.8)
        }

        detectionTimerRef.current = setInterval(runDetection, 1200) // Every 1.2s

        return () => {
            if (detectionTimerRef.current) {
                clearInterval(detectionTimerRef.current)
                detectionTimerRef.current = null
            }
        }
    }, [isStreaming, isDetecting, confThreshold, audioAlertEnabled])

    // Clean up on unmount
    useEffect(() => {
        return () => stopCamera()
    }, [])

    return (
        <div className="space-y-6 pb-12">
            {/* Header */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                    <div className="flex items-center gap-2 mb-1">
                        <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-primary/20 text-primary border border-primary/30">
                            YOLOv8 Computer Vision
                        </span>
                        <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${isStreaming ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' : 'bg-slate-700/50 text-slate-400'}`}>
                            {isStreaming ? 'LIVE WEBCAM' : 'CAMERA OFF'}
                        </span>
                    </div>
                    <h1 className="text-2xl md:text-3xl font-bold tracking-tight text-white">AI Print Error Monitoring</h1>
                    <p className="text-sm text-muted">Direct browser webcam feed with real-time spaghetti and defect detection</p>
                </div>

                {/* Quick Controls */}
                <div className="flex items-center gap-2 flex-wrap">
                    {/* Camera Selector */}
                    {devices.length > 0 && (
                        <select
                            value={selectedDeviceId}
                            onChange={(e) => {
                                setSelectedDeviceId(e.target.value)
                                if (isStreaming) startCamera(e.target.value)
                            }}
                            className="bg-surface/80 border border-border text-white text-xs rounded-lg px-3 py-2 outline-none focus:border-primary"
                        >
                            {devices.map((d, i) => (
                                <option key={d.deviceId || i} value={d.deviceId}>
                                    {d.label || `Camera ${i + 1}`}
                                </option>
                            ))}
                        </select>
                    )}

                    {/* Audio Alarm Toggle */}
                    <button
                        onClick={() => setAudioAlertEnabled(!audioAlertEnabled)}
                        title={audioAlertEnabled ? "Mute Alarm" : "Enable Alarm"}
                        className={`p-2 rounded-lg border transition-all ${audioAlertEnabled ? 'bg-primary/20 border-primary/40 text-primary' : 'bg-surface border-border text-slate-500'}`}
                    >
                        {audioAlertEnabled ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4" />}
                    </button>

                    {/* Start/Stop Button */}
                    {!isStreaming ? (
                        <button
                            onClick={() => startCamera(selectedDeviceId)}
                            className="flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-400 hover:to-teal-500 text-white text-sm font-semibold rounded-lg shadow-lg shadow-emerald-500/20 transition-all cursor-pointer"
                        >
                            <Camera className="w-4 h-4" />
                            Start Webcam
                        </button>
                    ) : (
                        <button
                            onClick={stopCamera}
                            className="flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-rose-500 to-red-600 hover:from-rose-400 hover:to-red-500 text-white text-sm font-semibold rounded-lg shadow-lg shadow-red-500/20 transition-all cursor-pointer"
                        >
                            <CameraOff className="w-4 h-4" />
                            Stop Camera
                        </button>
                    )}
                </div>
            </div>

            {/* Defect Alert Banner */}
            {defectAlert && (
                <div className="bg-rose-500/10 border-2 border-rose-500/50 rounded-xl p-4 flex flex-col md:flex-row md:items-center justify-between gap-4 animate-pulse shadow-lg shadow-rose-500/20">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-rose-500/20 flex items-center justify-center text-rose-400 shrink-0">
                            <AlertTriangle className="w-6 h-6" />
                        </div>
                        <div>
                            <h3 className="font-bold text-rose-300 text-base">⚠️ DEFECT DETECTED: {defectAlert.label}</h3>
                            <p className="text-xs text-rose-200/80">
                                Confidence: <strong>{(defectAlert.confidence * 100).toFixed(0)}%</strong> · Detected at {defectAlert.time}
                            </p>
                        </div>
                    </div>

                    <div className="flex items-center gap-2">
                        {isConnected && (
                            <button
                                onClick={pausePrint}
                                className="flex items-center gap-1.5 px-3 py-1.5 bg-rose-600 hover:bg-rose-500 text-white text-xs font-bold rounded-lg shadow cursor-pointer transition-all"
                            >
                                <PauseCircle className="w-4 h-4" />
                                Pause 3D Printer (M25)
                            </button>
                        )}
                        <button
                            onClick={() => setDefectAlert(null)}
                            className="px-3 py-1.5 bg-surface/80 hover:bg-surface text-slate-300 text-xs rounded-lg border border-border cursor-pointer transition-all"
                        >
                            Dismiss
                        </button>
                    </div>
                </div>
            )}

            {/* Video & AI Canvas Container */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Main Video Viewport */}
                <div className="lg:col-span-2 bg-surface border border-border/60 rounded-2xl p-4 flex flex-col gap-3 shadow-xl">
                    <div className="relative aspect-video bg-slate-950 rounded-xl overflow-hidden flex items-center justify-center border border-white/5">
                        {/* Native Video Element */}
                        <video
                            ref={videoRef}
                            playsInline
                            muted
                            className={`w-full h-full object-contain ${!isStreaming ? 'hidden' : ''}`}
                        />

                        {/* Off-screen Overlay Bounding Boxes */}
                        {isStreaming && detections.length > 0 && (
                            <div className="absolute inset-0 pointer-events-none">
                                {detections.map((det, idx) => (
                                    <div
                                        key={idx}
                                        style={{
                                            position: 'absolute',
                                            left: `${det.box.norm_x1 * 100}%`,
                                            top: `${det.box.norm_y1 * 100}%`,
                                            width: `${(det.box.norm_x2 - det.box.norm_x1) * 100}%`,
                                            height: `${(det.box.norm_y2 - det.box.norm_y1) * 100}%`,
                                        }}
                                        className="border-2 border-rose-500 bg-rose-500/10 rounded transition-all"
                                    >
                                        <span className="absolute -top-6 left-0 bg-rose-600 text-white text-[10px] font-bold px-1.5 py-0.5 rounded shadow whitespace-nowrap">
                                            {det.label} ({(det.confidence * 100).toFixed(0)}%)
                                        </span>
                                    </div>
                                ))}
                            </div>
                        )}

                        {/* Standby Placeholder */}
                        {!isStreaming && (
                            <div className="flex flex-col items-center gap-3 text-center p-6">
                                <div className="w-16 h-16 rounded-full bg-surface/80 border border-border flex items-center justify-center text-slate-500">
                                    <Camera className="w-8 h-8" />
                                </div>
                                <div>
                                    <h3 className="text-white font-semibold text-sm">Webcam is Inactive</h3>
                                    <p className="text-xs text-muted max-w-sm mt-1">
                                        Click <strong>"Start Webcam"</strong> above to begin direct browser video analysis with YOLOv8.
                                    </p>
                                </div>
                            </div>
                        )}

                        {/* Stream Stats Overlay */}
                        {isStreaming && (
                            <div className="absolute top-3 left-3 bg-slate-900/80 backdrop-blur border border-white/10 text-[11px] text-white px-2.5 py-1 rounded-md flex items-center gap-2">
                                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
                                <span>AI Active</span>
                                <span className="text-slate-400">|</span>
                                <span className="text-slate-300">{detections.length} Boxes</span>
                            </div>
                        )}
                    </div>

                    {/* Footer Controls & Confidence Slider */}
                    <div className="flex flex-wrap items-center justify-between gap-4 pt-2 border-t border-border/40 text-xs">
                        <div className="flex items-center gap-3">
                            <span className="text-muted flex items-center gap-1.5">
                                <Settings2 className="w-3.5 h-3.5" />
                                Detection Sensitivity:
                            </span>
                            <input
                                type="range"
                                min="0.1"
                                max="0.9"
                                step="0.05"
                                value={confThreshold}
                                onChange={(e) => setConfThreshold(parseFloat(e.target.value))}
                                className="w-24 accent-primary cursor-pointer"
                            />
                            <span className="text-white font-semibold">{(confThreshold * 100).toFixed(0)}%</span>
                        </div>

                        <div className="flex items-center gap-2 text-muted">
                            <span>Status:</span>
                            <span className={isConnected ? "text-emerald-400 font-medium" : "text-amber-400"}>
                                {printState}
                            </span>
                        </div>
                    </div>
                </div>

                {/* Defect Event Log & Status Panel */}
                <div className="space-y-4">
                    <div className="bg-surface border border-border/60 rounded-2xl p-4 shadow-xl flex flex-col h-full">
                        <div className="flex items-center justify-between pb-3 border-b border-border/40">
                            <div className="flex items-center gap-2">
                                <ShieldCheck className="w-4 h-4 text-primary" />
                                <h3 className="font-semibold text-white text-sm">AI Watchdog Log</h3>
                            </div>
                            <span className="text-[11px] text-muted">{defectLogs.length} Events</span>
                        </div>

                        <div className="mt-3 space-y-2 max-h-[380px] overflow-y-auto pr-1">
                            {defectLogs.length === 0 ? (
                                <div className="text-center py-12 text-muted text-xs">
                                    <Eye className="w-8 h-8 mx-auto mb-2 opacity-40" />
                                    No defects detected yet. Your prints are looking clean!
                                </div>
                            ) : (
                                defectLogs.map((log) => (
                                    <div
                                        key={log.id}
                                        className="bg-surface/60 border border-rose-500/20 hover:border-rose-500/40 rounded-lg p-2.5 text-xs transition-all flex items-center justify-between gap-2"
                                    >
                                        <div>
                                            <div className="font-semibold text-rose-300">{log.error_type}</div>
                                            <div className="text-[10px] text-muted">{log.time}</div>
                                        </div>
                                        <div className="text-right">
                                            <span className="px-2 py-0.5 bg-rose-500/20 text-rose-400 font-bold rounded text-[10px]">
                                                {(log.confidence * 100).toFixed(0)}%
                                            </span>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    )
}
