import { useState, useEffect, useCallback } from 'react'
import { Printer, Clock, CheckCircle, XCircle, Pause, ArrowRight, Download, FileCode, Trash2 } from 'lucide-react'
import api from '../services/api'

const PIPE_STEPS = ['Model', 'Slicing', 'G-Code', 'Printer']

function PipelineStep({ label, active, done }) {
    return (
        <div className="flex items-center gap-2">
            <div className={`w-10 h-10 rounded-full flex items-center justify-center text-sm font-bold border-2 transition-all ${done ? 'bg-success/20 border-success text-success' : active ? 'bg-primary/20 border-primary text-primary shadow-glow' : 'bg-surface border-border text-muted'}`}>
                {done ? <CheckCircle size={18} /> : label[0]}
            </div>
            <span className={`text-sm font-medium ${done ? 'text-success' : active ? 'text-primary' : 'text-muted'}`}>{label}</span>
        </div>
    )
}

function JobCard({ initialJob, onCancel, onUpdate }) {
    const [job, setJob] = useState(initialJob)

    useEffect(() => {
        setJob(initialJob)
    }, [initialJob])

    useEffect(() => {
        // Only connect WebSocket for active print jobs (slicing, ready_to_print, printing, paused)
        if (['completed', 'failed', 'cancelled', 'queued'].includes(job.status)) {
            return
        }

        const wsUrl = `ws://localhost:8000/ws/print-jobs/${job.id}/`
        const socket = new WebSocket(wsUrl)

        socket.onopen = () => console.log(`WS Connected for Job ${job.id}`)

        socket.onmessage = (event) => {
            const data = JSON.parse(event.data)
            setJob(prev => ({
                ...prev,
                status: data.status || prev.status,
                progress: data.progress !== undefined ? data.progress : prev.progress,
                temperatures: data.temperatures || prev.temperatures,
                error_detail: data.error_detail || prev.error_detail
            }))
            // Also bubble up if we want the parent to know
            if (onUpdate) onUpdate(job.id, data)
        }

        socket.onclose = () => console.log(`WS Disconnected for Job ${job.id}`)

        return () => {
            socket.close()
        }
    }, [job.id, job.status]) // Re-evaluate if status suddenly changes to active/inactive

    const startPrint = async () => {
        try {
            await api.post(`/print-jobs/${job.id}/start-print/`)
            setJob(prev => ({ ...prev, status: 'printing' }))
        } catch (err) {
            console.error('Failed to start print:', err)
            alert('Failed to start print: ' + (err.response?.data?.error || err.message))
        }
    }

    const statusCfg = {
        queued: 'bg-muted/10 text-muted border-muted/25',
        slicing: 'bg-primary/10 text-primary border-primary/25',
        ready_to_print: 'bg-warning/10 text-warning border-warning/25',
        printing: 'bg-success/10 text-success border-success/25',
        paused: 'bg-warning/10 text-warning border-warning/25',
        completed: 'bg-secondary/10 text-secondary border-secondary/25',
        failed: 'bg-danger/10 text-danger border-danger/25',
        cancelled: 'bg-muted/10 text-muted border-muted/25'
    }

    return (
        <div className="glass-card space-y-4">
            <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                    <p className="text-white font-semibold truncate">{job.model_prompt || 'Print Job #' + job.id}</p>
                    <p className="text-muted text-xs mt-1">Added {new Date(job.created_at).toLocaleString()}</p>
                </div>
                <span className={`status-badge border flex-shrink-0 ${statusCfg[job.status] || statusCfg.queued}`}>
                    {(job.status === 'printing' || job.status === 'slicing') && <span className="pulse-dot" />}{job.status}
                </span>
            </div>

            {job.error_detail && (
                <div className="text-xs text-danger bg-danger/10 p-2 rounded border border-danger/20">
                    {job.error_detail}
                </div>
            )}

            {/* Pipeline */}
            <div className="flex items-center gap-1 flex-wrap">
                {PIPE_STEPS.map((s, i) => {
                    const activeIndex = job.status === 'printing' ? 3 : job.status === 'ready_to_print' ? 2 : job.status === 'slicing' ? 1 : 0;
                    const doneIndex = job.status === 'completed' ? 4 : activeIndex;

                    return (
                        <div key={s} className="flex items-center gap-1">
                            <PipelineStep label={s} done={i < doneIndex} active={i === activeIndex} />
                            {i < 3 && <ArrowRight size={14} className="text-muted mx-1" />}
                        </div>
                    )
                })}
            </div>
            {/* Progress */}
            {job.status === 'printing' && (
                <div>
                    <div className="flex justify-between text-xs text-muted mb-1.5">
                        <span>Progress</span><span>{job.progress}%</span>
                    </div>
                    <div className="bg-surface rounded-full h-2">
                        <div className="bg-gradient-primary h-2 rounded-full transition-all" style={{ width: `${job.progress}%` }} />
                    </div>
                    {job.estimated_time && (
                        <p className="text-xs text-muted mt-1 flex items-center gap-1"><Clock size={11} /> ~{Math.ceil(job.estimated_time / 60)} min remaining</p>
                    )}
                    {job.temperatures && (
                        <div className="flex gap-4 mt-3 text-xs text-muted">
                            {job.temperatures.tool0 && (
                                <span>Ex: {Math.round(job.temperatures.tool0.actual)}° / {Math.round(job.temperatures.tool0.target)}°</span>
                            )}
                            {job.temperatures.bed && (
                                <span>Bed: {Math.round(job.temperatures.bed.actual)}° / {Math.round(job.temperatures.bed.target)}°</span>
                            )}
                        </div>
                    )}
                </div>
            )}

            {/* Actions */}
            <div className="flex items-center gap-2 pt-2">
                {job.status === 'ready_to_print' && (
                    <button onClick={startPrint} className="btn-primary text-xs flex-1 justify-center py-2">
                        <Printer size={14} /> Start Print
                    </button>
                )}
                {job.gcode_url && (
                    <a href={job.gcode_url} download className="btn-ghost bg-surface text-xs text-primary border border-primary/20 hover:bg-primary/10 flex-[0.7] justify-center py-2 flex items-center gap-1 whitespace-nowrap">
                        <Download size={14} /> G-Code
                    </a>
                )}
                {(job.status === 'printing' || job.status === 'slicing' || job.status === 'ready_to_print') && (
                    <button onClick={() => onCancel(job.id)} className="btn-ghost text-xs text-danger border-danger/30 hover:bg-danger/10 flex-1 justify-center py-2">
                        <XCircle size={14} /> Cancel Job
                    </button>
                )}
            </div>
        </div>
    )
}

export default function PrintQueue() {
    const [jobs, setJobs] = useState([])
    const [loading, setLoading] = useState(true)
    const [octoprintStatus, setOctoprintStatus] = useState(null) // null = checking, object = result

    const fetchJobs = useCallback(() =>
        api.get('/print-jobs/').then(r => setJobs(r.data.results || r.data || [])).finally(() => setLoading(false))
        , [])

    useEffect(() => { fetchJobs() }, [fetchJobs])

    // Poll OctoPrint connection status every 8 seconds
    useEffect(() => {
        const checkOctoPrint = () => {
            api.get('/dashboard/printer-status/')
                .then(r => setOctoprintStatus(r.data))
                .catch(() => setOctoprintStatus({ printer_connected: false, print_state: 'Unreachable' }))
        }
        checkOctoPrint()
        const interval = setInterval(checkOctoPrint, 8000)
        return () => clearInterval(interval)
    }, [])

    const cancelJob = async (id) => {
        await api.patch(`/print-jobs/${id}/`, { status: 'cancelled' })
        fetchJobs()
    }

    const clearJobs = async () => {
        try {
            await api.post('/print-jobs/clear-finished/')
            fetchJobs()
        } catch (err) {
            console.error('Failed to clear jobs:', err)
        }
    }

    return (
        <div className="page-enter space-y-6">
            {/* OctoPrint Connection Banner */}
            <div className="glass-card flex items-center gap-4">
                <div className={`p-3 rounded-xl ${octoprintStatus?.printer_connected ? 'bg-success/10' : 'bg-warning/10'}`}>
                    <Printer size={24} className={octoprintStatus?.printer_connected ? 'text-success' : 'text-warning'} />
                </div>
                <div>
                    <p className="text-white font-semibold">OctoPrint Integration</p>
                    <p className="text-muted text-sm">
                        {octoprintStatus === null
                            ? 'Checking connection...'
                            : octoprintStatus.printer_connected
                                ? `${octoprintStatus.print_state}${octoprintStatus.simulated ? ' (Virtual Printer)' : ''}`
                                : 'Connect your Raspberry Pi to enable real print control'}
                    </p>
                </div>
                <div className="ml-auto flex items-center gap-2">
                    {jobs.some(j => ['completed', 'failed', 'cancelled'].includes(j.status)) && (
                        <button
                            onClick={clearJobs}
                            className="btn-ghost text-xs text-danger hover:bg-danger/10 flex items-center gap-1.5 px-3"
                        >
                            <Trash2 size={14} /> Clear Finished
                        </button>
                    )}
                    {octoprintStatus === null ? (
                        <span className="status-badge border bg-muted/10 text-muted border-muted/25 animate-pulse">Checking...</span>
                    ) : octoprintStatus.printer_connected ? (
                        <span className="status-badge border bg-success/10 text-success border-success/25"><span className="pulse-dot shrink-0" /> Connected · port 5000</span>
                    ) : (
                        <span className="status-badge border bg-warning/10 text-warning border-warning/25">Not Connected</span>
                    )}
                    <a href="http://localhost:5000" target="_blank" rel="noopener noreferrer" className="btn-ghost text-xs">Open OctoPrint ↗</a>
                </div>
            </div>

            {/* Jobs */}
            {loading ? (
                <div className="space-y-4">{[1, 2].map(i => <div key={i} className="h-40 rounded-xl bg-white/5 animate-pulse" />)}</div>
            ) : jobs.length === 0 ? (
                <div className="glass-card flex flex-col items-center justify-center py-20 text-muted">
                    <Printer size={48} className="mb-4 opacity-20" />
                    <p className="text-lg font-semibold text-white mb-1">Queue is empty</p>
                    <p className="text-sm">Send a model to print from the Generate or History pages.</p>
                </div>
            ) : (
                <div className="space-y-4">
                    {jobs.map(job => <JobCard key={job.id} initialJob={job} onCancel={cancelJob} />)}
                </div>
            )}
        </div>
    )
}
