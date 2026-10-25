import { useState, Suspense, useMemo, useRef, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
    Sparkles,
    ImageIcon,
    Upload,
    Download,
    Printer,
    RefreshCw,
    RotateCcw,
    Layers,
    Box,
    CheckCircle2,
    Clock,
    AlertCircle,
    Eye,
    X,
    Cpu,
    ArrowRight
} from 'lucide-react'
import { Canvas, useLoader, useFrame } from '@react-three/fiber'
import { OrbitControls, Grid, Center } from '@react-three/drei'
import { OBJLoader } from 'three/examples/jsm/loaders/OBJLoader'
import { STLLoader } from 'three/examples/jsm/loaders/STLLoader'
import * as THREE from 'three'
import { useGeneration } from '../context/GenerationContext'
import api, { getAuthenticatedUrl } from '../services/api'
import SlicingModal from '../components/SlicingModal'

// ── Curated Inspiration Prompts ──────────────────────────────────────────────
const INSPIRATION_CHIPS = [
    { label: '☕ Coffee Mug', prompt: 'Modern ceramic coffee mug with geometric handle' },
    { label: '♟️ Chess Knight', prompt: 'Staunton Chess Knight horse head with carved mane' },
    { label: '🚀 Rocket Ship', prompt: 'Vintage sci-fi cartoon rocket ship with three fin thrusters' },
    { label: '🎧 Headphone Stand', prompt: 'Ergonomic desktop headphone stand holder' },
    { label: '⚙️ Spur Gear', prompt: 'Mechanical 12-tooth spur gear with center bore' },
    { label: '🏺 Spiral Vase', prompt: 'Parametric spiral fluted ceramic flower vase' },
]

// ── Quality Step Presets ─────────────────────────────────────────────────────
const QUALITY_PRESETS = [
    { id: 16, label: 'Fast Draft', desc: '16 steps • ~2-3s' },
    { id: 32, label: 'Standard', desc: '32 steps • ~4-5s' },
    { id: 48, label: 'High Quality', desc: '48 steps • ~6-8s' },
]

// ── Three.js OBJ Loader ──────────────────────────────────────────────────────
function ObjPreview({ url, displayMode }) {
    const obj = useLoader(OBJLoader, url)

    const scene = useMemo(() => {
        const clone = obj.clone(true)
        clone.traverse((child) => {
            if (!child.isMesh) return
            if (child.geometry) {
                child.geometry.computeVertexNormals()
            }
            child.material = new THREE.MeshStandardMaterial({
                color: '#8b5cf6',
                wireframe: displayMode === 'wireframe',
                roughness: 0.35,
                metalness: 0.2,
            })
        })

        const box = new THREE.Box3().setFromObject(clone)
        const size = new THREE.Vector3()
        box.getSize(size)
        const center = new THREE.Vector3()
        box.getCenter(center)

        const maxDim = Math.max(size.x, size.y, size.z)
        const targetDim = 2.8
        const scale = targetDim / (maxDim > 0 ? maxDim : 1)

        clone.scale.setScalar(scale)
        clone.position.x = -center.x * scale
        clone.position.z = -center.z * scale
        clone.position.y = -box.min.y * scale

        return clone
    }, [obj, displayMode])

    return <primitive object={scene} />
}

// ── Three.js STL Loader ──────────────────────────────────────────────────────
function StlPreview({ url, displayMode }) {
    const geom = useLoader(STLLoader, url)

    const mesh = useMemo(() => {
        geom.computeVertexNormals()
        const mat = new THREE.MeshStandardMaterial({
            color: '#8b5cf6',
            wireframe: displayMode === 'wireframe',
            roughness: 0.35,
            metalness: 0.2,
        })
        const m = new THREE.Mesh(geom, mat)

        geom.computeBoundingBox()
        const box = geom.boundingBox
        const size = new THREE.Vector3()
        box.getSize(size)
        const center = new THREE.Vector3()
        box.getCenter(center)

        const maxDim = Math.max(size.x, size.y, size.z)
        const targetDim = 2.8
        const scale = targetDim / (maxDim > 0 ? maxDim : 1)

        m.scale.setScalar(scale)
        m.position.x = -center.x * scale
        m.position.z = -center.z * scale
        m.position.y = -box.min.y * scale
        return m
    }, [geom, displayMode])

    return <primitive object={mesh} />
}

// ── Main 3D Creation Studio Page ─────────────────────────────────────────────
export default function GenerateModel() {
    const [searchParams, setSearchParams] = useSearchParams()
    const activeMode = searchParams.get('mode') === 'image' ? 'image' : 'text'

    const {
        textTask,
        textPreviousTask,
        textState,
        textElapsed,
        textError,
        textPrompt,
        textSteps,
        setTextPrompt,
        setTextSteps,
        startTextGeneration,
        resetText,
        imageTask,
        imageState,
        imageElapsed,
        imageError,
        imageFile,
        imagePreview,
        imageSteps,
        setImageFile,
        setImageSteps,
        startImageGeneration,
        resetImage,
    } = useGeneration()

    // Viewport Display State
    const [displayMode, setDisplayMode] = useState('solid') // 'solid' | 'wireframe'
    const [autoRotate, setAutoRotate] = useState(true)
    const [outputFormat, setOutputFormat] = useState('stl')
    const [imageTitle, setImageTitle] = useState('')
    const [isDragging, setIsDragging] = useState(false)
    const [slicingJobId, setSlicingJobId] = useState(null)
    const [isSlicingModalOpen, setIsSlicingModalOpen] = useState(false)

    const fileInputRef = useRef(null)
    const controlsRef = useRef(null)

    // Current Mode State
    const isImageMode = activeMode === 'image'
    const currentTask = isImageMode ? imageTask : textTask
    const previousTask = isImageMode ? null : textPreviousTask
    const currentState = isImageMode ? imageState : textState
    const currentElapsed = isImageMode ? imageElapsed : textElapsed
    const currentError = isImageMode ? imageError : textError

    const isRunning = currentState === 'loading' || currentState === 'polling'
    const activeDisplayTask = currentTask || (currentError ? previousTask : null)
    const modelUrl = activeDisplayTask?.file_url ? getAuthenticatedUrl(activeDisplayTask.file_url) : null

    const isStl = useMemo(() => {
        const path = (activeDisplayTask?.file_path || '').toLowerCase()
        const url = (activeDisplayTask?.file_url || modelUrl || '').toLowerCase()
        const fmt = (activeDisplayTask?.format || '').toLowerCase()
        if (path.endsWith('.stl') || url.includes('.stl') || fmt === 'stl') return true
        if (path.endsWith('.obj') || url.includes('.obj') || fmt === 'obj') return false
        return outputFormat === 'stl'
    }, [activeDisplayTask, modelUrl, outputFormat])

    const setMode = (mode) => {
        setSearchParams({ mode })
    }

    // Handlers
    const handleGenerate = () => {
        if (isRunning) return
        if (isImageMode) {
            if (!imageFile) return
            startImageGeneration(imageFile, imageSteps || 32, {
                engine: 'shap-e',
                guidanceScale: 3.0,
                format: outputFormat,
                name: imageTitle || imageFile.name,
            })
        } else {
            if (!textPrompt || !textPrompt.trim()) return
            startTextGeneration(textPrompt.trim(), textSteps || 32, {
                engine: 'shap-e',
                guidanceScale: 15.0,
                format: outputFormat,
            })
        }
    }

    const handleReset = () => {
        if (isImageMode) {
            resetImage()
            setImageTitle('')
        } else {
            resetText(false)
        }
    }

    const handleDrop = (e) => {
        e.preventDefault()
        setIsDragging(false)
        if (e.dataTransfer.files && e.dataTransfer.files[0]) {
            const file = e.dataTransfer.files[0]
            setImageFile(file)
            if (!imageTitle) setImageTitle(file.name.replace(/\.[^/.]+$/, ''))
        }
    }

    const handleFileInput = (e) => {
        if (e.target.files && e.target.files[0]) {
            const file = e.target.files[0]
            setImageFile(file)
            if (!imageTitle) setImageTitle(file.name.replace(/\.[^/.]+$/, ''))
        }
    }

    const handleResetCamera = () => {
        if (controlsRef.current) {
            controlsRef.current.reset()
            const cam = controlsRef.current.object
            if (cam) {
                cam.position.set(0, 2.5, 4.5)
                controlsRef.current.target.set(0, 1.2, 0)
                controlsRef.current.update()
            }
        }
    }

    const handleDownload = () => {
        if (!modelUrl) return
        const a = document.createElement('a')
        a.href = modelUrl
        const filename = (activeDisplayTask?.prompt || 'generated_model').replace(/[^a-zA-Z0-9_-]/g, '_')
        a.download = `${filename}.${outputFormat}`
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
    }

    const handleSliceAndPrint = async () => {
        if (!activeDisplayTask?.id) return
        try {
            const res = await api.post('/print-jobs/', { model: activeDisplayTask.id })
            setSlicingJobId(res.data.id)
            setIsSlicingModalOpen(true)
        } catch (err) {
            alert('Failed to trigger slicer: ' + (err.response?.data?.error || err.message))
        }
    }

    return (
        <div className="h-[calc(100vh-5.5rem)] flex flex-col lg:flex-row overflow-hidden bg-[#0a0c10] border border-border/50 rounded-2xl shadow-2xl">
            {/* ══════════════════════════════════════════════════════════════════
                LEFT PANEL: SIMPLE CONTROLS & INPUTS
            ══════════════════════════════════════════════════════════════════ */}
            <div className="w-full lg:w-[440px] flex-shrink-0 bg-[#0e111a] border-r border-border/60 flex flex-col h-full overflow-y-auto">
                {/* Header & Mode Switcher */}
                <div className="p-5 border-b border-border/50 bg-[#0e111a]/95 sticky top-0 z-10 backdrop-blur-md">
                    <div className="flex items-center justify-between mb-4">
                        <div className="flex items-center gap-3">
                            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-primary to-violet-500 flex items-center justify-center shadow-glow">
                                <Sparkles size={18} className="text-white" />
                            </div>
                            <div>
                                <h1 className="text-base font-bold text-white tracking-wide">3D Creation Studio</h1>
                                <p className="text-xs text-muted">OpenAI Shap-E Neural Diffusion</p>
                            </div>
                        </div>

                        {/* Reset Button */}
                        <button
                            onClick={handleReset}
                            disabled={isRunning}
                            title="Reset Form & Clear"
                            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-muted hover:text-white hover:bg-white/5 border border-border/50 text-xs transition-all"
                        >
                            <RefreshCw size={13} className={isRunning ? 'animate-spin' : ''} />
                            <span>Reset</span>
                        </button>
                    </div>

                    {/* Mode Switcher Tabs */}
                    <div className="grid grid-cols-2 p-1 bg-surface/80 border border-border/70 rounded-xl">
                        <button
                            onClick={() => setMode('text')}
                            disabled={isRunning}
                            className={`flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-semibold transition-all ${
                                !isImageMode
                                    ? 'bg-gradient-to-r from-primary to-violet-600 text-white shadow-glow'
                                    : 'text-muted hover:text-white hover:bg-white/5'
                            }`}
                        >
                            <Sparkles size={14} />
                            <span>Text to 3D</span>
                        </button>
                        <button
                            onClick={() => setMode('image')}
                            disabled={isRunning}
                            className={`flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-semibold transition-all ${
                                isImageMode
                                    ? 'bg-gradient-to-r from-sky-500 to-indigo-600 text-white shadow-glow'
                                    : 'text-muted hover:text-white hover:bg-white/5'
                            }`}
                        >
                            <ImageIcon size={14} />
                            <span>Image to 3D</span>
                        </button>
                    </div>
                </div>

                {/* Main Controls Area */}
                <div className="p-5 space-y-5 flex-1">
                    {/* TEXT-TO-3D SECTION */}
                    {!isImageMode && (
                        <div className="space-y-4">
                            <div>
                                <label className="label flex items-center justify-between" htmlFor="prompt-input">
                                    <span>Describe your 3D model</span>
                                    <span className="text-[11px] text-muted font-normal">Natural Language</span>
                                </label>
                                <textarea
                                    id="prompt-input"
                                    className="input min-h-[95px] resize-none text-sm leading-relaxed"
                                    placeholder="e.g. Modern ceramic espresso coffee cup with geometric handle..."
                                    value={textPrompt}
                                    onChange={(e) => setTextPrompt(e.target.value)}
                                    disabled={isRunning}
                                />
                            </div>

                            {/* Inspiration Chips */}
                            <div>
                                <p className="text-[11px] font-semibold text-muted uppercase tracking-wider mb-2">
                                    Quick Inspiration Ideas
                                </p>
                                <div className="flex flex-wrap gap-1.5">
                                    {INSPIRATION_CHIPS.map((chip) => (
                                        <button
                                            key={chip.label}
                                            type="button"
                                            disabled={isRunning}
                                            onClick={() => setTextPrompt(chip.prompt)}
                                            className="px-2.5 py-1 rounded-lg text-xs bg-surface/80 hover:bg-primary/20 hover:text-primary border border-border/60 text-muted transition-all"
                                        >
                                            {chip.label}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        </div>
                    )}

                    {/* IMAGE-TO-3D SECTION */}
                    {isImageMode && (
                        <div className="space-y-4">
                            <div>
                                <label className="label">Reference Image or Sketch</label>
                                <input
                                    ref={fileInputRef}
                                    type="file"
                                    accept="image/*"
                                    className="hidden"
                                    onChange={handleFileInput}
                                    disabled={isRunning}
                                />

                                {imagePreview ? (
                                    <div className="relative rounded-xl border border-border/80 overflow-hidden bg-surface p-3 flex items-center gap-3">
                                        <img
                                            src={imagePreview}
                                            alt="Preview"
                                            className="w-16 h-16 object-cover rounded-lg border border-border/50 shrink-0"
                                        />
                                        <div className="min-w-0 flex-1">
                                            <p className="text-sm font-medium text-white truncate">
                                                {imageFile?.name || 'Uploaded Image'}
                                            </p>
                                            <p className="text-xs text-muted">
                                                {imageFile ? `${(imageFile.size / 1024).toFixed(1)} KB` : ''}
                                            </p>
                                        </div>
                                        <button
                                            type="button"
                                            disabled={isRunning}
                                            onClick={() => setImageFile(null)}
                                            className="p-1.5 rounded-lg text-muted hover:text-white hover:bg-white/10 transition-colors"
                                        >
                                            <X size={16} />
                                        </button>
                                    </div>
                                ) : (
                                    <div
                                        onDragOver={(e) => { e.preventDefault(); setIsDragging(true) }}
                                        onDragLeave={() => setIsDragging(false)}
                                        onDrop={handleDrop}
                                        onClick={() => fileInputRef.current?.click()}
                                        className={`border-2 border-dashed rounded-xl p-6 text-center cursor-pointer transition-all ${
                                            isDragging
                                                ? 'border-primary bg-primary/10'
                                                : 'border-border/80 hover:border-primary/60 bg-surface/50 hover:bg-surface'
                                        }`}
                                    >
                                        <div className="w-12 h-12 rounded-xl bg-primary/10 text-primary mx-auto flex items-center justify-center mb-3">
                                            <Upload size={22} />
                                        </div>
                                        <p className="text-sm font-medium text-white">Click or drag image here</p>
                                        <p className="text-xs text-muted mt-1">PNG, JPG, or WEBP (Transparent or solid bg)</p>
                                    </div>
                                )}
                            </div>

                            <div>
                                <label className="label" htmlFor="model-name-input">
                                    Model Title <span className="text-muted font-normal">(Optional)</span>
                                </label>
                                <input
                                    id="model-name-input"
                                    type="text"
                                    className="input text-sm"
                                    placeholder="e.g. Vintage Robot Figure"
                                    value={imageTitle}
                                    onChange={(e) => setImageTitle(e.target.value)}
                                    disabled={isRunning}
                                />
                            </div>
                        </div>
                    )}

                    {/* QUALITY & STEPS PRESETS */}
                    <div className="space-y-2 pt-1 border-t border-border/40">
                        <label className="label flex items-center justify-between">
                            <span>Synthesis Quality (Diffusion Steps)</span>
                            <span className="text-xs text-primary font-medium">GPU Accelerated</span>
                        </label>
                        <div className="grid grid-cols-3 gap-2">
                            {QUALITY_PRESETS.map((preset) => {
                                const isSelected = isImageMode
                                    ? (imageSteps || 32) === preset.id
                                    : (textSteps || 32) === preset.id
                                return (
                                    <button
                                        key={preset.id}
                                        type="button"
                                        disabled={isRunning}
                                        onClick={() => isImageMode ? setImageSteps(preset.id) : setTextSteps(preset.id)}
                                        className={`p-2.5 rounded-xl border text-left transition-all ${
                                            isSelected
                                                ? 'bg-primary/20 border-primary text-white shadow-glow'
                                                : 'bg-surface/60 border-border/60 text-muted hover:border-border hover:text-white'
                                        }`}
                                    >
                                        <p className="text-xs font-semibold">{preset.label}</p>
                                        <p className="text-[10px] text-muted mt-0.5">{preset.desc}</p>
                                    </button>
                                )
                            })}
                        </div>
                    </div>

                    {/* OUTPUT FORMAT */}
                    <div className="flex items-center justify-between pt-1">
                        <span className="text-xs text-muted font-medium">Output Mesh Format</span>
                        <div className="flex bg-surface border border-border/60 rounded-lg p-0.5">
                            <button
                                type="button"
                                disabled={isRunning}
                                onClick={() => setOutputFormat('stl')}
                                className={`px-3 py-1 rounded-md text-xs font-semibold transition-all ${
                                    outputFormat === 'stl'
                                        ? 'bg-primary text-white shadow-sm'
                                        : 'text-muted hover:text-white'
                                }`}
                            >
                                STL
                            </button>
                            <button
                                type="button"
                                disabled={isRunning}
                                onClick={() => setOutputFormat('obj')}
                                className={`px-3 py-1 rounded-md text-xs font-semibold transition-all ${
                                    outputFormat === 'obj'
                                        ? 'bg-primary text-white shadow-sm'
                                        : 'text-muted hover:text-white'
                                }`}
                            >
                                OBJ
                            </button>
                        </div>
                    </div>

                    {/* STATUS BANNER & LIVE TIMER */}
                    {isRunning && (
                        <div className="p-3.5 rounded-xl bg-primary/10 border border-primary/30 space-y-2 animate-pulse">
                            <div className="flex items-center justify-between">
                                <span className="text-xs font-semibold text-primary flex items-center gap-2">
                                    <Cpu size={14} className="animate-spin" />
                                    Synthesizing 3D Model...
                                </span>
                                <span className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-primary/20 text-white font-mono text-xs font-bold border border-primary/40">
                                    <Clock size={12} />
                                    {String(Math.floor(currentElapsed / 60)).padStart(2, '0')}:
                                    {String(currentElapsed % 60).padStart(2, '0')}s
                                </span>
                            </div>
                            <p className="text-[11px] text-muted">
                                {isImageMode
                                    ? 'Sampling visual latent diffusion on GPU (RTX 2050 fp16)...'
                                    : 'Sampling text prompt embedding and extracting 3D mesh...'}
                            </p>
                        </div>
                    )}

                    {/* ERROR BANNER */}
                    {currentError && (
                        <div className="p-3 rounded-xl bg-danger/10 border border-danger/30 text-danger text-xs flex items-start gap-2">
                            <AlertCircle size={15} className="mt-0.5 shrink-0" />
                            <div className="flex-1">
                                <p className="font-semibold">Generation Failed</p>
                                <p className="text-[11px] opacity-90 mt-0.5">{currentError}</p>
                            </div>
                        </div>
                    )}

                    {/* SUCCESS BANNER */}
                    {!isRunning && activeDisplayTask && activeDisplayTask.status === 'ready' && (
                        <div className="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs flex items-center justify-between">
                            <div className="flex items-center gap-2">
                                <CheckCircle2 size={16} className="text-emerald-400 shrink-0" />
                                <div>
                                    <p className="font-semibold text-white">3D Model Ready!</p>
                                    <p className="text-[11px] text-muted">
                                        Rendered in {activeDisplayTask.generation_time ? `${activeDisplayTask.generation_time.toFixed(1)}s` : `${currentElapsed}s`}
                                    </p>
                                </div>
                            </div>
                            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 font-bold uppercase">
                                {outputFormat}
                            </span>
                        </div>
                    )}

                    {/* GENERATE BUTTON */}
                    <button
                        type="button"
                        onClick={handleGenerate}
                        disabled={isRunning || (!isImageMode && !textPrompt?.trim()) || (isImageMode && !imageFile)}
                        className={`btn-primary w-full py-3 justify-center text-sm font-semibold tracking-wide shadow-lg transition-all ${
                            isRunning ? 'opacity-70 cursor-not-allowed' : 'hover:shadow-glow'
                        }`}
                    >
                        {isRunning ? (
                            <span className="flex items-center gap-2">
                                <RefreshCw size={16} className="animate-spin" />
                                Generating 3D Model... ({currentElapsed}s)
                            </span>
                        ) : (
                            <span className="flex items-center gap-2">
                                <Sparkles size={16} />
                                Generate 3D Model
                            </span>
                        )}
                    </button>
                </div>
            </div>

            {/* ══════════════════════════════════════════════════════════════════
                RIGHT PANEL: INTERACTIVE 3D WEBGL VIEWPORT
            ══════════════════════════════════════════════════════════════════ */}
            <div className="flex-1 flex flex-col h-full bg-[#07090e] relative overflow-hidden">
                {/* Viewport Top Bar */}
                <div className="absolute top-4 left-4 right-4 z-10 flex items-center justify-between pointer-events-none">
                    {/* View mode toggle */}
                    <div className="pointer-events-auto flex items-center gap-1.5 p-1 bg-card/90 border border-border/80 rounded-xl backdrop-blur-md shadow-lg">
                        <button
                            type="button"
                            onClick={() => setDisplayMode('solid')}
                            className={`px-3 py-1.5 rounded-lg text-xs font-medium flex items-center gap-1.5 transition-all ${
                                displayMode === 'solid'
                                    ? 'bg-primary text-white shadow-glow'
                                    : 'text-muted hover:text-white hover:bg-white/5'
                            }`}
                        >
                            <Box size={14} />
                            <span>Solid</span>
                        </button>
                        <button
                            type="button"
                            onClick={() => setDisplayMode('wireframe')}
                            className={`px-3 py-1.5 rounded-lg text-xs font-medium flex items-center gap-1.5 transition-all ${
                                displayMode === 'wireframe'
                                    ? 'bg-primary text-white shadow-glow'
                                    : 'text-muted hover:text-white hover:bg-white/5'
                            }`}
                        >
                            <Layers size={14} />
                            <span>Wireframe</span>
                        </button>
                    </div>

                    {/* Camera reset & auto-rotate */}
                    <div className="pointer-events-auto flex items-center gap-2">
                        <button
                            type="button"
                            onClick={() => setAutoRotate(!autoRotate)}
                            className={`px-3 py-1.5 rounded-xl border text-xs font-medium backdrop-blur-md transition-all ${
                                autoRotate
                                    ? 'bg-primary/20 border-primary text-white shadow-glow'
                                    : 'bg-card/90 border-border/80 text-muted hover:text-white'
                            }`}
                        >
                            Auto Rotate
                        </button>
                        <button
                            type="button"
                            onClick={handleResetCamera}
                            title="Reset Camera View"
                            className="p-2 rounded-xl bg-card/90 border border-border/80 text-muted hover:text-white hover:bg-white/5 backdrop-blur-md transition-all"
                        >
                            <RotateCcw size={15} />
                        </button>
                    </div>
                </div>

                {/* WebGL Canvas */}
                <div className="flex-1 w-full h-full">
                    <Canvas
                        camera={{ position: [0, 2.5, 4.5], fov: 45 }}
                        gl={{ antialias: true, alpha: true }}
                    >
                        <ambientLight intensity={0.8} />
                        <directionalLight position={[5, 10, 5]} intensity={1.5} castShadow />
                        <directionalLight position={[-5, 5, -5]} intensity={0.6} />
                        <pointLight position={[0, 4, 0]} intensity={0.8} />

                        {/* 3D Printer Bed Grid */}
                        <Grid
                            args={[10, 10]}
                            cellSize={0.5}
                            cellThickness={1}
                            cellColor="#262f40"
                            sectionSize={2.5}
                            sectionThickness={1.5}
                            sectionColor="#3b4860"
                            fadeDistance={25}
                            fadeStrength={1}
                            followCamera={false}
                            position={[0, 0, 0]}
                        />

                        {/* Render Active 3D Model */}
                        {modelUrl ? (
                            <Suspense fallback={null}>
                                <Center bottom position={[0, 0, 0]}>
                                    {isStl ? (
                                        <StlPreview key={modelUrl} url={modelUrl} displayMode={displayMode} />
                                    ) : (
                                        <ObjPreview key={modelUrl} url={modelUrl} displayMode={displayMode} />
                                    )}
                                </Center>
                            </Suspense>
                        ) : null}

                        <OrbitControls
                            ref={controlsRef}
                            autoRotate={autoRotate && !!modelUrl}
                            autoRotateSpeed={1.8}
                            minDistance={1.2}
                            maxDistance={12}
                            target={[0, 1.2, 0]}
                            enableDamping
                            dampingFactor={0.05}
                        />
                    </Canvas>
                </div>

                {/* Viewport Empty Placeholder when no model */}
                {!modelUrl && !isRunning && (
                    <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none p-6 text-center">
                        <div className="w-16 h-16 rounded-2xl bg-surface/80 border border-border/70 flex items-center justify-center text-muted mb-4 shadow-xl">
                            <Box size={28} className="text-primary/70" />
                        </div>
                        <h3 className="text-base font-semibold text-white">Interactive 3D Viewport</h3>
                        <p className="text-xs text-muted max-w-sm mt-1">
                            {isImageMode
                                ? 'Upload a photo and click Generate to synthesize a printable 3D mesh.'
                                : 'Enter a description and click Generate to synthesize a printable 3D mesh.'}
                        </p>
                    </div>
                )}

                {/* Bottom Viewport Action Bar (Ready Model Actions) */}
                {modelUrl && (
                    <div className="absolute bottom-4 left-4 right-4 z-10 flex flex-wrap items-center justify-between gap-3 p-3 bg-card/95 border border-border/80 rounded-2xl backdrop-blur-md shadow-2xl">
                        <div className="flex items-center gap-2.5">
                            <div className="w-8 h-8 rounded-lg bg-primary/20 text-primary flex items-center justify-center">
                                <Printer size={16} />
                            </div>
                            <div>
                                <p className="text-xs font-bold text-white truncate max-w-[200px]">
                                    {activeDisplayTask?.prompt || 'Generated 3D Model'}
                                </p>
                                <p className="text-[11px] text-muted">Bed Aligned (Z=0.0) • Ready for Slicing</p>
                            </div>
                        </div>

                        <div className="flex items-center gap-2">
                            <button
                                type="button"
                                onClick={handleDownload}
                                className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-surface hover:bg-white/10 border border-border/70 text-xs font-semibold text-white transition-all"
                            >
                                <Download size={14} />
                                <span>Download {outputFormat.toUpperCase()}</span>
                            </button>

                            <button
                                type="button"
                                onClick={handleSliceAndPrint}
                                className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-gradient-primary hover:shadow-glow text-xs font-bold text-white transition-all shadow-md"
                            >
                                <Printer size={14} />
                                <span>Slice & Send to 3D Printer</span>
                                <ArrowRight size={13} />
                            </button>
                        </div>
                    </div>
                )}
            </div>

            {/* Slicing & G-Code Modal */}
            {isSlicingModalOpen && slicingJobId && (
                <SlicingModal
                    isOpen={isSlicingModalOpen}
                    onClose={() => setIsSlicingModalOpen(false)}
                    jobId={slicingJobId}
                    modelName={activeDisplayTask?.prompt || 'Generated Model'}
                />
            )}
        </div>
    )
}
