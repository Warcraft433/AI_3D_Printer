import { useState, useEffect, Suspense, useMemo, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { createPortal } from 'react-dom'
import { Folder, Eye, Download, Trash2, X, Clock, Zap, Printer, AlertTriangle, RefreshCw } from 'lucide-react'
import { Canvas } from '@react-three/fiber'
import { OrbitControls, Stage } from '@react-three/drei'
import { OBJLoader } from 'three/examples/jsm/loaders/OBJLoader'
import { STLLoader } from 'three/examples/jsm/loaders/STLLoader'
import { useLoader } from '@react-three/fiber'
import * as THREE from 'three'
import api, { getAuthenticatedUrl } from '../services/api'
import SlicingModal from '../components/SlicingModal'

// ── display mode constants ──────────────────────────────────────────────────
const MODES = [
    { id: 'solid', label: 'Solid', icon: '⬛' },
    { id: 'wireframe', label: 'Wireframe', icon: '⬡' },
    { id: 'points', label: 'Points', icon: '⁘' },
]

// ── OBJ preview with material controls ─────────────────────────────────────
function ObjPreview({ url, color, displayMode }) {
    const obj = useLoader(OBJLoader, url)

    const scene = useMemo(() => {
        const clone = obj.clone(true)

        clone.traverse((child) => {
            if (!child.isMesh) return

            if (displayMode === 'wireframe') {
                child.material = new THREE.MeshStandardMaterial({
                    color,
                    wireframe: true,
                })
            } else if (displayMode === 'points') {
                const geo = child.geometry
                const mat = new THREE.PointsMaterial({ color, size: 0.015 })
                const points = new THREE.Points(geo, mat)
                // replace mesh with points in parent
                child.visible = false
                child.parent?.add(points)
            } else {
                // solid
                child.material = new THREE.MeshStandardMaterial({
                    color,
                    roughness: 0.5,
                    metalness: 0.1,
                })
            }
        })

        return clone
    }, [obj, color, displayMode])

    return <primitive object={scene} scale={1} />
}

// ── STL preview with material controls ─────────────────────────────────────
function StlPreview({ url, color, displayMode }) {
    const geom = useLoader(STLLoader, url)

    const mesh = useMemo(() => {
        geom.computeVertexNormals()
        let mat
        if (displayMode === 'wireframe') {
            mat = new THREE.MeshStandardMaterial({
                color,
                wireframe: true,
            })
        } else if (displayMode === 'points') {
            mat = new THREE.PointsMaterial({ color, size: 0.015 })
            return new THREE.Points(geom, mat)
        } else {
            mat = new THREE.MeshStandardMaterial({
                color,
                roughness: 0.5,
                metalness: 0.1,
            })
        }
        return new THREE.Mesh(geom, mat)
    }, [geom, color, displayMode])

    return <primitive object={mesh} scale={1} />
}


// ── status badge ────────────────────────────────────────────────────────────
function StatusBadge({ status }) {
    const cfg = {
        ready: 'bg-success/10 text-success border-success/25',
        processing: 'bg-warning/10 text-warning border-warning/25',
        failed: 'bg-danger/10 text-danger border-danger/25',
    }
    return (
        <span className={`status-badge border ${cfg[status] || cfg.processing}`}>
            {status === 'processing' && <span className="pulse-dot" />}
            {status}
        </span>
    )
}

// ── model card ──────────────────────────────────────────────────────────────
function ModelCard({ model, onView, onDelete, onSlice }) {
    return (
        <div className="glass-card flex flex-col gap-3 hover:shadow-glow-lg transition-shadow">
            <div className="h-36 rounded-lg bg-surface/50 border border-border/50 flex items-center justify-center overflow-hidden">
                <Folder size={36} className="text-muted opacity-30" />
            </div>
            <div className="flex-1 min-w-0">
                <p className="text-white text-sm font-medium truncate" title={model.prompt}>
                    {model.prompt || '[Image upload]'}
                </p>
                <p className="text-muted text-xs mt-1">{new Date(model.created_at).toLocaleDateString()}</p>
                <div className="flex items-center gap-2 mt-2 flex-wrap">
                    <StatusBadge status={model.status} />
                    {model.is_fallback ? (
                        <span className="status-badge border bg-warning/10 text-warning border-warning/25 text-[10px]">Demo Placeholder</span>
                    ) : (model.generation_method === 'procedural_cad' || model.is_procedural || model.source_type === 'procedural') ? (
                        <span className="status-badge border bg-primary/10 text-primary border-primary/25 text-[10px]">Procedural CAD</span>
                    ) : (
                        <span className="status-badge border bg-success/10 text-success border-success/25 text-[10px]">AI Generated</span>
                    )}
                    {model.generation_time && (
                        <span className="flex items-center gap-1 text-xs text-muted">
                            <Clock size={11} /> {model.generation_time}s
                        </span>
                    )}
                    <span className="flex items-center gap-1 text-xs text-muted">
                        <Zap size={11} /> {model.steps} steps
                    </span>
                </div>
            </div>
            <div className="flex gap-2">
                <button onClick={() => onView(model)} className="btn-ghost flex-1 text-xs justify-center">
                    <Eye size={14} /> View
                </button>
                {model.file_url && (
                    <button onClick={() => onSlice(model.id)} className="btn-primary flex-[2] text-xs justify-center shadow-glow">
                        <Printer size={14} /> Slice
                    </button>
                )}
                <button onClick={() => onDelete(model.id)} className="p-2 rounded-lg border border-border text-muted hover:text-danger hover:border-danger/30 transition-all">
                    <Trash2 size={14} />
                </button>
            </div>
        </div>
    )
}

// ── preset palette swatches ─────────────────────────────────────────────────
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

// ── Model Modal (portalled to document.body for correct z-index + stacking) ─
function ModelModal({ selected, onClose, onSlice, modelColor, setModelColor, displayMode, setDisplayMode }) {
    // ESC key to close
    useEffect(() => {
        const handleKey = (e) => { if (e.key === 'Escape') onClose() }
        document.addEventListener('keydown', handleKey)
        return () => document.removeEventListener('keydown', handleKey)
    }, [onClose])

    // Lock body scroll while modal is open
    useEffect(() => {
        document.body.style.overflow = 'hidden'
        return () => { document.body.style.overflow = '' }
    }, [])

    // Backdrop click: close only when clicking the dark overlay itself
    const handleBackdropClick = useCallback((e) => {
        if (e.target === e.currentTarget) onClose()
    }, [onClose])

    return createPortal(
        <div
            className="fixed inset-0 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm"
            style={{ zIndex: 9999 }}
            onClick={handleBackdropClick}
            aria-modal="true"
            role="dialog"
        >
            <div className="glass-card w-full max-w-3xl max-h-[90vh] overflow-y-auto space-y-4 relative">

                {/* sticky header */}
                <div className="flex items-center justify-between sticky top-0 z-10 bg-[#0f1117] pb-2 mb-2 border-b border-border/30">
                    <div>
                        <h3 className="text-white font-bold text-lg">Model Details</h3>
                        <p className="text-muted text-sm">{selected.prompt}</p>
                    </div>
                    <button
                        onClick={onClose}
                        aria-label="Close modal"
                        className="p-2 rounded-lg text-muted hover:text-white hover:bg-white/10 transition-all"
                    >
                        <X size={20} />
                    </button>
                </div>

                {/* ── preview controls bar ── */}
                {selected.file_url && (
                    <div className="flex flex-wrap items-center gap-3 px-1">

                        {/* display mode toggle */}
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
                                        }`}
                                >
                                    <span className="text-base leading-none">{m.icon}</span>
                                    {m.label}
                                </button>
                            ))}
                        </div>

                        {/* colour swatches + custom picker */}
                        <div className="flex items-center gap-1.5">
                            <span className="text-xs text-muted mr-1">Color:</span>
                            {SWATCHES.map(sw => (
                                <button
                                    key={sw}
                                    onClick={() => setModelColor(sw)}
                                    title={sw}
                                    style={{ background: sw }}
                                    className={`w-5 h-5 rounded-full border-2 transition-all
                                        ${modelColor === sw ? 'border-white scale-125' : 'border-transparent opacity-70 hover:opacity-100 hover:scale-110'}`}
                                />
                            ))}
                            {/* custom colour input */}
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
                )}

                {/* ── 3-D canvas ── */}
                <div className="h-80 rounded-xl overflow-hidden bg-surface/50 border border-border/50">
                    {selected.file_url ? (
                        <Canvas camera={{ position: [0, 0, 3] }}>
                            <Suspense fallback={null}>
                                <Stage environment="city" intensity={0.5}>
                                    {(selected.file_path?.toLowerCase().endsWith('.stl') || selected.file_url?.toLowerCase().includes('.stl')) ? (
                                        <StlPreview
                                            key={selected.file_url}
                                            url={getAuthenticatedUrl(selected.file_url)}
                                            color={modelColor}
                                            displayMode={displayMode}
                                        />
                                    ) : (
                                        <ObjPreview
                                            key={selected.file_url}
                                            url={getAuthenticatedUrl(selected.file_url)}
                                            color={modelColor}
                                            displayMode={displayMode}
                                        />
                                    )}
                                </Stage>
                                <OrbitControls autoRotate />
                            </Suspense>
                        </Canvas>
                    ) : (
                        <div className="flex items-center justify-center h-full text-muted">
                            <p className="text-sm">No preview available</p>
                        </div>
                    )}
                </div>

                {/* ── meta grid ── */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
                    {[
                        { label: 'Status', value: selected.status },
                        { label: 'Engine Type', value: selected.is_fallback ? 'Demo Placeholder' : ((selected.generation_method === 'procedural_cad' || selected.is_procedural || selected.source_type === 'procedural') ? 'Procedural CAD (Trimesh)' : 'AI Synthesis') },
                        { label: 'Generation Time', value: selected.generation_time ? `${selected.generation_time}s` : '—' },
                        { label: 'Created', value: new Date(selected.created_at).toLocaleString() },
                    ].map(({ label, value }) => (
                        <div key={label} className="p-3 rounded-lg bg-surface/50 border border-border/50">
                            <p className="text-xs text-muted mb-1">{label}</p>
                            <p className="text-white font-medium">{value}</p>
                        </div>
                    ))}
                </div>

                {selected.error_detail && (
                    <div className="p-3 rounded-lg bg-white/5 border border-white/10 text-xs text-muted">
                        <span className="font-semibold text-white/80">Generator Metadata: </span>
                        {selected.error_detail}
                    </div>
                )}

                {/* ── download & action ── */}
                {selected.file_url && (
                    <div className="flex gap-2">
                        <a href={getAuthenticatedUrl(`/api/models/${selected.id}/download`)} download className="btn-ghost bg-surface/50 w-full justify-center flex-1 py-3">
                            <Download size={16} /> Download {selected.file_path?.toLowerCase().endsWith('.stl') || selected.file_url?.toLowerCase().includes('.stl') ? 'STL' : 'OBJ'}
                        </a>
                        <button onClick={() => onSlice(selected.id)} className="btn-primary w-full justify-center flex-[2] shadow-glow py-3">
                            <Printer size={16} /> Send to Queue
                        </button>
                    </div>
                )}
            </div>
        </div>,
        document.body
    )
}

// ── main page ───────────────────────────────────────────────────────────────
export default function ModelHistory() {
    const [models, setModels] = useState([])
    const [loading, setLoading] = useState(true)
    const [selected, setSelected] = useState(null)
    const navigate = useNavigate()

    // preview controls
    const [modelColor, setModelColor] = useState('#a78bfa')
    const [displayMode, setDisplayMode] = useState('solid')

    const [slicingJob, setSlicingJob] = useState(null)
    const [slicingModelName, setSlicingModelName] = useState('')
    const [error, setError] = useState(null)

    const fetchModels = useCallback(() => {
        setLoading(true)
        setError(null)
        api.get('/models/')
            .then(r => {
                const data = r.data
                if (Array.isArray(data)) {
                    setModels(data)
                } else if (data && Array.isArray(data.results)) {
                    setModels(data.results)
                } else if (typeof data === 'string' && data.trim().startsWith('<')) {
                    throw new Error('Received HTML document instead of JSON. Ensure Vercel environment points to Spring Boot.')
                } else {
                    throw new Error('Unexpected data format received from backend.')
                }
            })
            .catch(err => {
                console.error('Model history fetch failed:', err)
                const message = err.response?.data?.error
                    || err.response?.data?.message
                    || err.message
                    || 'Failed to load 3D models.'
                setError(message)
            })
            .finally(() => setLoading(false))
    }, [])

    useEffect(() => { fetchModels() }, [fetchModels])

    const handleDelete = async (id) => {
        if (!window.confirm('Delete this model?')) return
        await api.delete(`/models/${id}/`)
        setModels(m => m.filter(x => x.id !== id))
        if (selected?.id === id) setSelected(null)
    }

    const openModal = (model) => {
        setModelColor('#a78bfa')
        setDisplayMode('solid')
        setSelected(model)
    }

    const handleSlice = async (modelId) => {
        try {
            const resp = await api.post('/print-jobs/', { model: modelId })
            setSlicingJob(resp.data.id)
            setSlicingModelName(models.find(m => m.id === modelId)?.prompt || 'Selected Model')
            if (selected) setSelected(null)
        } catch (err) {
            console.error(err)
            alert('Failed to start slicing: ' + (err?.response?.data?.error || err.message))
        }
    }

    const closeModal = useCallback(() => setSelected(null), [])

    return (
        <div className="page-enter space-y-6">
            <div className="flex items-center justify-between">
                <p className="text-muted text-sm">{models.length} model{models.length !== 1 ? 's' : ''} in database</p>
                {error && (
                    <button
                        onClick={fetchModels}
                        className="btn-ghost text-xs px-3 py-1 flex items-center gap-1.5 text-primary hover:bg-primary/10 border border-primary/20"
                    >
                        <RefreshCw size={12} /> Retry
                    </button>
                )}
            </div>

            {loading ? (
                <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-4">
                    {[1, 2, 3, 4].map(i => <div key={i} className="h-64 rounded-xl bg-white/5 animate-pulse" />)}
                </div>
            ) : error ? (
                <div className="glass-card flex flex-col items-center justify-center py-16 text-center border-danger/30">
                    <AlertTriangle size={48} className="mb-4 text-danger/80" />
                    <p className="text-lg font-semibold text-white mb-1">Failed to load models</p>
                    <p className="text-sm text-muted mb-4 max-w-md">{error}</p>
                    <button
                        onClick={fetchModels}
                        className="btn-primary text-xs px-4 py-2 flex items-center gap-2"
                    >
                        <RefreshCw size={14} /> Retry Connection
                    </button>
                </div>
            ) : models.length === 0 ? (
                <div className="glass-card flex flex-col items-center justify-center py-20 text-muted">
                    <Folder size={48} className="mb-4 opacity-20" />
                    <p className="text-lg font-semibold text-white mb-1">No models yet</p>
                    <p className="text-sm">Generate your first 3D model to see it here.</p>
                </div>
            ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                    {models.map(m => (
                        <ModelCard key={m.id} model={m} onView={openModal} onDelete={handleDelete} onSlice={handleSlice} />
                    ))}
                </div>
            )}

            {/* ── Modal ── rendered via Portal so it escapes layout stacking contexts */}
            {selected && (
                <ModelModal
                    selected={selected}
                    onClose={closeModal}
                    onSlice={handleSlice}
                    modelColor={modelColor}
                    setModelColor={setModelColor}
                    displayMode={displayMode}
                    setDisplayMode={setDisplayMode}
                />
            )}

            <SlicingModal
                isOpen={!!slicingJob}
                onClose={() => setSlicingJob(null)}
                jobId={slicingJob}
                modelName={slicingModelName}
            />
        </div>
    )
}
