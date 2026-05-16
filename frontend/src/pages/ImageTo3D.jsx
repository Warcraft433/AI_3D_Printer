import { useState, Suspense, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { ImageIcon, Upload, Download, AlertCircle, RotateCcw, Printer, Wand2, Cpu, History, ArrowRight, Layers, Box, Info, CheckCircle2, XCircle, RefreshCw } from 'lucide-react'
import { Canvas } from '@react-three/fiber'
import { OrbitControls, Stage } from '@react-three/drei'
import { OBJLoader } from 'three/examples/jsm/loaders/OBJLoader'
import { useLoader } from '@react-three/fiber'
import * as THREE from 'three'
import { useGeneration } from '../context/GenerationContext'
import api from '../services/api'
import SlicingModal from '../components/SlicingModal'

// ── display mode constants ────────────────────────────────────────────────────
const MODES = [
    { id: 'solid', label: 'Solid', icon: '⬛' },
    { id: 'wireframe', label: 'Wireframe', icon: '⬡' },
    { id: 'points', label: 'Points', icon: '⁘' },
]

// ── colour palette ────────────────────────────────────────────────────────────
const SWATCHES = [
    '#a78bfa', // violet (default)
    '#38bdf8', // sky
    '#34d399', // emerald
    '#f87171', // red
    '#fb923c', // orange
    '#facc15', // yellow
    '#e2e8f0', // white-ish
    '#1e293b', // dark
]

// ── OBJ renderer with material controls ──────────────────────────────────────
function ObjPreview({ url, color, displayMode }) {
    const obj = useLoader(OBJLoader, url)

    const scene = useMemo(() => {
        const clone = obj.clone(true)
        clone.traverse((child) => {
            if (!child.isMesh) return
            if (displayMode === 'wireframe') {
                child.material = new THREE.MeshStandardMaterial({ color, wireframe: true })
            } else if (displayMode === 'points') {
                const mat = new THREE.PointsMaterial({ color, size: 0.015 })
                const pts = new THREE.Points(child.geometry, mat)
                child.visible = false
                child.parent?.add(pts)
            } else {
                child.material = new THREE.MeshStandardMaterial({ color, roughness: 0.5, metalness: 0.1 })
            }
        })
        return clone
    }, [obj, color, displayMode])

    return <primitive object={scene} scale={1} />
}

// ── viewer control bar (mode + colour) ───────────────────────────────────────
function ViewerControls({ modelColor, setModelColor, displayMode, setDisplayMode }) {
    return (
        <div className="flex flex-wrap items-center gap-3 px-1 py-2">
            <div className="flex items-center gap-1 bg-surface/60 border border-border/50 rounded-lg p-1">
                {MODES.map(m => (
                    <button
                        key={m.id}
                        onClick={() => setDisplayMode(m.id)}
                        title={m.label}
                        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-all
                            ${displayMode === m.id
                                ? 'bg-primary text-white shadow-glow'
                                : 'text-muted hover:text-white hover:bg-white/10'
                            } `}
                    >
                        <span className="text-base leading-none">{m.icon}</span>
                        {m.label}
                    </button>
                ))}
            </div>

            <div className="flex items-center gap-1.5 flex-wrap">
                <span className="text-xs text-muted mr-1">Color:</span>
                {SWATCHES.map(sw => (
                    <button
                        key={sw}
                        onClick={() => setModelColor(sw)}
                        title={sw}
                        style={{ background: sw }}
                        className={`w-5 h-5 rounded-full border-2 transition-all
                            ${modelColor === sw ? 'border-white scale-125' : 'border-transparent opacity-70 hover:opacity-100 hover:scale-110'} `}
                    />
                ))}
                <label title="Custom colour" className="relative w-5 h-5 rounded-full border-2 border-dashed border-white/40 hover:border-white/80 cursor-pointer overflow-hidden transition-all">
                    <input
                        type="color"
                        value={modelColor}
                        onChange={e => setModelColor(e.target.value)}
                        className="absolute inset-0 opacity-0 cursor-pointer w-full h-full"
                    />
                    <span className="absolute inset-0 flex items-center justify-center text-[9px] text-white/60 pointer-events-none">✎</span>
                </label>
            </div>
        </div>
    )
}

const STAGE_LABELS = {
    queued: 'Queued — waiting to start…',
    processing: 'Starting generation…',
    loading_models: '⚙ Warming up Shap-E models (image300M)…',
    generating_latents: '🔮 Generating 3D latents…',
    decoding_mesh: '🧊 Decoding mesh from latents…',
    repairing_mesh: '🛠 Repairing and cleaning 3D mesh…',
}

export default function ImageTo3D() {
    const {
        imageTask: task,
        imageState: state,
        imageElapsed: elapsed,
        imageError: error,
        imageFile: file,
        imagePreview: preview,
        imageSteps: steps,
        setImageFile,
        setImageSteps: setSteps,
        startImageGeneration,
        resetImage
    } = useGeneration()

    // viewer state
    const [modelColor, setModelColor] = useState('#a78bfa')
    const [displayMode, setDisplayMode] = useState('solid')
    const [slicingJob, setSlicingJob] = useState(null)

    const navigate = useNavigate()

    const handleFile = (e) => {
        const f = e.target.files[0]
        if (!f) return
        setImageFile(f)
    }

    const handleGenerate = () => {
        if (!file) return
        startImageGeneration(file, steps)
    }

    const isRunning = state === 'loading' || state === 'polling'
    const modelReady = !!task?.file_url
    const fmt = s => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`

    const handleSlice = async () => {
        try {
            const resp = await api.post('/print-jobs/', { model: task.id })
            setSlicingJob(resp.data.id)
        } catch (err) {
            console.error(err)
            alert('Failed to start slicing: ' + (err.response?.data?.error || err.message))
        }
    }

    return (
        <div className="page-enter space-y-6">
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Input Panel */}
                <div className="glass-card space-y-5">
                    <div className="flex items-center justify-between">
                        <label className="label">Upload Image</label>
                        {state !== 'idle' && (
                            <button onClick={resetImage} className="text-xs text-primary hover:text-white flex items-center gap-1 transition-colors">
                                <RotateCcw size={12} /> Generate New Model
                            </button>
                        )}
                    </div>

                    <label className={`flex flex-col items-center justify-center border-2 border-dashed border-border rounded-xl transition-all min-h-[180px] bg-surface/50 
                        ${isRunning || state === 'done' ? 'cursor-not-allowed opacity-75' : 'cursor-pointer hover:border-primary/50'}
`}>
                        {preview ? (
                            <img src={preview} alt="preview" className="max-h-48 rounded-lg object-contain" />
                        ) : (
                            <div className="flex flex-col items-center text-muted py-8">
                                <Upload size={32} className="mb-2 opacity-40" />
                                <p className="text-sm">Click to upload image</p>
                                <p className="text-xs mt-1">PNG, JPG, WEBP supported</p>
                            </div>
                        )}
                        <input
                            type="file"
                            accept="image/*"
                            className="hidden"
                            onChange={handleFile}
                            disabled={isRunning}
                        />
                    </label>

                    <div>
                        <label className="label">Quality Preset</label>
                        <div className="flex gap-2">
                            {[{ l: '⚡ Fast', s: 32 }, { l: '⚖ Balanced', s: 48 }, { l: '✦ Quality', s: 64 }].map(q => (
                                <button
                                    key={q.s}
                                    onClick={() => setSteps(q.s)}
                                    disabled={isRunning || state === 'done'}
                                    className={`flex-1 py-2 rounded-lg text-sm font-semibold border transition-all 
                                        ${steps === q.s ? 'bg-primary/20 border-primary/50 text-primary shadow-glow' : 'border-border text-muted hover:text-white'}
                                        ${isRunning || state === 'done' ? 'opacity-50 cursor-not-allowed' : ''}
`}
                                >
                                    {q.l}
                                </button>
                            ))}
                        </div>
                    </div>

                    {state === 'idle' && (
                        <button
                            className="btn-primary w-full justify-center"
                            onClick={handleGenerate}
                            disabled={!file}
                        >
                            <ImageIcon size={16} /> Generate from Image
                        </button>
                    )}

                    {isRunning && (
                        <div className="text-center space-y-4">
                            <div className="relative inline-block">
                                <div className="w-16 h-16 border-4 border-primary/20 border-t-primary rounded-full animate-spin mx-auto" />
                                <button
                                    onClick={() => window.location.reload()}
                                    className="absolute inset-0 flex items-center justify-center opacity-0 hover:opacity-100 transition-opacity"
                                    title="Manually sync status"
                                >
                                    <RefreshCw size={20} className="text-primary" />
                                </button>
                            </div>
                            <div>
                                <p className="text-white font-medium">
                                    {state === 'loading' ? 'Uploading image…' : (STAGE_LABELS[task?.status] || 'Processing…')}
                                </p>
                                <div className="flex items-center justify-center gap-2 mt-2">
                                    <div className="flex items-center gap-1.5 px-3 py-1 bg-white/5 border border-white/10 rounded-full">
                                        <span className="text-3xl font-bold font-mono tracking-tight text-white">
                                            {fmt(elapsed)}
                                        </span>
                                    </div>
                                </div>
                                <p className="text-xs text-muted mt-2 uppercase tracking-widest font-semibold flex items-center justify-center gap-2">
                                    Stage: {task?.status || 'processing'}
                                    {state === 'polling' && (
                                        <button
                                            onClick={() => window.location.reload()}
                                            className="hover:text-primary transition-colors"
                                            title="Sync now"
                                        >
                                            <RefreshCw size={12} className="animate-spin-slow" />
                                        </button>
                                    )}
                                </p>
                            </div>
                            <p className="text-muted text-[10px] mt-2 italic px-6 text-center">
                                Task is safe. You can navigate away; progress will persist here.
                            </p>
                        </div>
                    )}

                    {error && (
                        <div className="flex flex-col gap-3 py-2">
                            <div className="flex flex-col gap-2 p-3 rounded-lg bg-warning/10 border border-warning/25 text-warning text-sm">
                                <div className="flex gap-2"><AlertCircle size={16} className="flex-shrink-0 mt-0.5" />{error}</div>
                            </div>
                            <button
                                className="btn-ghost w-full justify-center text-warning hover:bg-warning/10"
                                onClick={resetImage}
                            >
                                <RotateCcw size={16} /> Try Again / Reset
                            </button>
                        </div>
                    )}

                    {state === 'done' && task && (
                        <div className="p-4 rounded-lg bg-success/10 border border-success/25 space-y-3">
                            <p className="text-success font-semibold text-sm mb-1">✓ Model Ready!</p>
                            <p className="text-muted text-xs">Generated in {task.generation_time}s</p>
                            <div className="flex gap-2">
                                <a href={task.file_url} download className="btn-ghost bg-surface/50 text-xs px-2 py-2 flex-1 justify-center whitespace-nowrap">
                                    <Download size={14} /> OBJ
                                </a>
                                <button onClick={handleSlice} className="btn-primary text-xs px-2 py-2 flex-[2] justify-center shadow-glow">
                                    <Printer size={14} /> Send to Slicer
                                </button>
                                <button onClick={resetImage} className="btn-ghost text-xs px-2 py-2 flex-1 justify-center whitespace-nowrap">
                                    <RotateCcw size={14} /> New
                                </button>
                            </div>
                        </div>
                    )}
                </div>

                {/* 3D Viewer */}
                <div className="glass-card flex flex-col gap-3">
                    <p className="text-white font-semibold">3D Preview</p>

                    {modelReady && (
                        <ViewerControls
                            modelColor={modelColor}
                            setModelColor={setModelColor}
                            displayMode={displayMode}
                            setDisplayMode={setDisplayMode}
                        />
                    )}

                    <div className="flex-1 min-h-[380px] rounded-xl overflow-hidden bg-surface/50 border border-border/50">
                        {modelReady ? (
                            <Canvas camera={{ position: [0, 0, 3] }}>
                                <Suspense fallback={null}>
                                    <Stage environment="city" intensity={0.5}>
                                        <ObjPreview
                                            url={task.file_url}
                                            color={modelColor}
                                            displayMode={displayMode}
                                        />
                                    </Stage>
                                    <OrbitControls autoRotate />
                                </Suspense>
                            </Canvas>
                        ) : (
                            <div className="flex flex-col items-center justify-center h-full text-muted">
                                <div className={`w-24 h-24 border-2 border-dashed border-border rounded-2xl flex items-center justify-center mb-4 ${isRunning ? 'animate-pulse border-primary/30' : ''}`}>
                                    <ImageIcon size={36} className={`${isRunning ? 'text-primary' : 'opacity-30'}`} />
                                </div>
                                <p className="text-sm">{isRunning ? 'Translating pixels to 3D...' : 'Upload an image to preview'}</p>
                            </div>
                        )}
                    </div>
                </div>
            </div>

            <SlicingModal
                isOpen={!!slicingJob}
                onClose={() => setSlicingJob(null)}
                jobId={slicingJob}
                modelName="Image Upload 3D Model"
            />
        </div>
    )
}
