import { useEffect, useState } from 'react'
import { Cpu, Printer, Zap, AlertTriangle, Activity, Clock, BarChart3, TrendingUp } from 'lucide-react'
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { motion, AnimatePresence } from 'framer-motion'
import api from '../services/api'

const mockChartData = [
    { name: 'Mon', defects: 2, prints: 4 },
    { name: 'Tue', defects: 1, prints: 6 },
    { name: 'Wed', defects: 4, prints: 3 },
    { name: 'Thu', defects: 0, prints: 8 },
    { name: 'Fri', defects: 2, prints: 5 },
    { name: 'Sat', defects: 1, prints: 9 },
    { name: 'Sun', defects: 3, prints: 4 },
]

function StatCard({ icon: Icon, label, value, sub, color = 'primary' }) {
    const colors = { 
        primary: 'text-primary', 
        success: 'text-success', 
        warning: 'text-warning', 
        danger: 'text-danger', 
        secondary: 'text-secondary' 
    }
    return (
        <motion.div 
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="stat-card group"
        >
            <div className="flex items-center justify-between">
                <p className="text-muted text-sm font-medium">{label}</p>
                <div className={`p-2 rounded-lg bg-current/10 ${colors[color]} group-hover:scale-110 transition-transform`}>
                    <Icon size={18} className={colors[color]} />
                </div>
            </div>
            <p className="text-3xl font-bold text-white mt-1">{value ?? '—'}</p>
            {sub && <p className="text-xs text-muted mt-1">{sub}</p>}
        </motion.div>
    )
}

export default function Dashboard() {
    const [stats, setStats] = useState(null)
    const [loading, setLoading] = useState(true)
    const [alert, setAlert] = useState(null)

    const fetchStats = () => {
        api.get('/dashboard/stats/').then(r => setStats(r.data)).finally(() => setLoading(false))
    }

    useEffect(() => {
        fetchStats()
        const interval = setInterval(fetchStats, 10000)

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
        const wsUrl = `${protocol}//${window.location.host}/ws/dashboard/`
        const socket = new WebSocket(wsUrl)

        socket.onmessage = (e) => {
            const data = JSON.parse(e.data)
            if (data.type === 'ai_defect_alert' || data.type === 'DETECTION_ALERT' || data.type === 'DEFECT_DETECTED') {
                const alertData = data.data
                if (data.type === 'ai_defect_alert') {
                    setAlert({
                        error_type: alertData.defect,
                        confidence: alertData.confidence,
                        action: alertData.action,
                        timestamp: alertData.timestamp,
                        image_url: alertData.image_url
                    })
                } else {
                    setAlert(alertData)
                }
                fetchStats()
                setTimeout(() => setAlert(null), 15000)
            }
        }

        return () => {
            clearInterval(interval)
            socket.close()
        }
    }, [])

    return (
        <motion.div 
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="space-y-6 relative pb-10"
        >
            {/* Real-time Alert Overlay */}
            <AnimatePresence>
                {alert && (
                    <motion.div 
                        initial={{ x: 400, opacity: 0 }}
                        animate={{ x: 0, opacity: 1 }}
                        exit={{ x: 400, opacity: 0 }}
                        className="fixed top-24 right-6 z-50 w-96"
                    >
                        <div className="glass-card border-danger/80 bg-danger/20 p-6 shadow-2xl shadow-danger/40 backdrop-blur-xl">
                            <div className="flex items-center gap-4 mb-4">
                                <div className="bg-danger/20 p-3 rounded-full animate-pulse border border-danger/40">
                                    <AlertTriangle className="text-danger" size={32} />
                                </div>
                                <div className="flex-1">
                                    <p className="text-white font-black text-xl leading-tight uppercase tracking-tight">Defect Detected</p>
                                    <p className="text-danger-light text-sm font-bold opacity-90">{alert.error_type} ({alert.confidence} confidence)</p>
                                </div>
                            </div>
                            
                            {alert.image_url ? (
                                <img src={alert.image_url} alt="Detection Visualization" className="w-full h-48 object-cover rounded-xl mb-4 border-2 border-danger/30 shadow-2xl" />
                            ) : (
                                <div className="w-full h-24 bg-surface/50 rounded-xl mb-4 flex flex-col items-center justify-center border border-border/30">
                                    <span className="text-2xl mb-1">⏸</span>
                                    <p className="text-[10px] text-muted font-bold uppercase tracking-widest">Printer Paused</p>
                                </div>
                            )}

                            <div className="bg-white/5 p-4 rounded-xl border border-white/10 space-y-2">
                                <p className="text-xs text-muted font-bold uppercase tracking-widest">System Response</p>
                                <p className="text-sm text-white font-semibold flex items-center gap-2">
                                    <span className="w-2 h-2 rounded-full bg-danger shadow-[0_0_8px_#ef4444]" />
                                    {alert.action || "Printing Paused Automatically"}
                                </p>
                            </div>

                            <button onClick={() => setAlert(null)} className="absolute top-4 right-4 text-white/30 hover:text-white transition-colors">✕</button>
                        </div>
                    </motion.div>
                )}
            </AnimatePresence>

            {/* Header Section */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-white tracking-tight">System Dashboard</h1>
                    <p className="text-muted text-sm mt-1">Real-time printer telemetry and AI diagnostics</p>
                </div>
                <div className="flex items-center gap-3">
                    <div className="px-3 py-1.5 rounded-full bg-surface border border-border text-xs font-semibold text-success flex items-center gap-2">
                        <span className="pulse-dot" /> Live System
                    </div>
                </div>
            </div>

            {/* Stats Grid */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                <StatCard icon={Zap} label="Total Models" value={stats?.total_models} sub={`${stats?.ready_models ?? 0} ready`} color="primary" />
                <StatCard icon={Printer} label="Total Prints" value={stats?.total_prints} sub={`${stats?.completed_prints ?? 0} completed`} color="success" />
                <StatCard icon={Activity} label="AI Status" value={alert ? 'ALERT' : stats?.ai_monitoring_status ?? 'Idle'} sub={`${stats?.recent_errors ?? 0} detections`} color={alert ? 'danger' : 'warning'} />
                <StatCard icon={Cpu} label="Inference Engine" value="YOLOv8" sub="Optimized TensorRT" color="secondary" />
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Analytics Chart */}
                <div className="lg:col-span-2 glass-card">
                    <div className="flex items-center justify-between mb-6">
                        <h2 className="text-white font-semibold flex items-center gap-2">
                            <TrendingUp size={18} className="text-primary" /> Print Analytics
                        </h2>
                        <div className="flex items-center gap-4 text-[10px] font-bold uppercase tracking-widest">
                            <span className="flex items-center gap-1.5 text-primary"><span className="w-2 h-2 rounded-full bg-primary" /> Prints</span>
                            <span className="flex items-center gap-1.5 text-danger"><span className="w-2 h-2 rounded-full bg-danger" /> Defects</span>
                        </div>
                    </div>
                    <div className="h-[250px] w-full">
                        <ResponsiveContainer width="100%" height="100%">
                            <AreaChart data={mockChartData}>
                                <defs>
                                    <linearGradient id="colorPrints" x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="5%" stopColor="#8b5cf6" stopOpacity={0.3}/>
                                        <stop offset="95%" stopColor="#8b5cf6" stopOpacity={0}/>
                                    </linearGradient>
                                    <linearGradient id="colorDefects" x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="5%" stopColor="#ef4444" stopOpacity={0.3}/>
                                        <stop offset="95%" stopColor="#ef4444" stopOpacity={0}/>
                                    </linearGradient>
                                </defs>
                                <CartesianGrid strokeDasharray="3 3" stroke="#1f2d40" vertical={false} />
                                <XAxis dataKey="name" stroke="#64748b" fontSize={10} axisLine={false} tickLine={false} />
                                <YAxis stroke="#64748b" fontSize={10} axisLine={false} tickLine={false} />
                                <Tooltip 
                                    contentStyle={{ backgroundColor: '#111827', border: '1px solid #1f2d40', borderRadius: '8px' }}
                                    itemStyle={{ fontSize: '12px' }}
                                />
                                <Area type="monotone" dataKey="prints" stroke="#8b5cf6" fillOpacity={1} fill="url(#colorPrints)" strokeWidth={2} />
                                <Area type="monotone" dataKey="defects" stroke="#ef4444" fillOpacity={1} fill="url(#colorDefects)" strokeWidth={2} />
                            </AreaChart>
                        </ResponsiveContainer>
                    </div>
                </div>

                {/* Active Print Status */}
                <div className="glass-card flex flex-col">
                    <div className="flex items-center justify-between mb-4">
                        <h2 className="text-white font-semibold flex items-center gap-2">
                            <Printer size={18} className="text-primary" /> Active Job
                        </h2>
                    </div>
                    <div className="flex-1 flex flex-col justify-center">
                        {stats?.active_print ? (
                            <div className="space-y-4">
                                <div>
                                    <p className="text-xs text-muted font-bold uppercase tracking-widest mb-1">Model Prompt</p>
                                    <p className="text-slate-200 text-sm font-medium">{stats.active_print.model_prompt}</p>
                                </div>
                                
                                <div>
                                    <div className="flex items-center justify-between mb-2">
                                        <span className="text-[10px] font-bold text-muted uppercase">Progress</span>
                                        <span className="text-sm font-bold text-white">{stats.active_print.progress}%</span>
                                    </div>
                                    <div className="w-full bg-surface rounded-full h-3 p-0.5 border border-border/50">
                                        <div 
                                            className="bg-gradient-primary h-full rounded-full transition-all duration-1000 shadow-glow" 
                                            style={{ width: `${stats.active_print.progress}%` }} 
                                        />
                                    </div>
                                </div>

                                <div className="pt-2 flex items-center justify-between">
                                    <span className={`status-badge border ${stats.active_print.status === 'paused' ? 'bg-warning/10 text-warning border-warning/25' : 'bg-success/10 text-success border-success/25'}`}>
                                        <span className={stats.active_print.status === 'printing' ? 'pulse-dot' : 'w-2 h-2 rounded-full bg-current'} /> {stats.active_print.status}
                                    </span>
                                    <div className="flex gap-2">
                                        <button className="btn-ghost !py-1 !px-3 text-xs">Manage</button>
                                    </div>
                                </div>
                            </div>
                        ) : (
                            <div className="text-center py-10 opacity-40">
                                <Printer size={48} className="mx-auto mb-4" />
                                <p className="text-sm font-medium">Idle Mode</p>
                            </div>
                        )}
                    </div>
                </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Recent Logs */}
                <div className="glass-card">
                    <h2 className="text-white font-semibold mb-4 flex items-center gap-2">
                        <Clock size={18} className="text-primary" /> Recent Event Logs
                    </h2>
                    <div className="space-y-2">
                        {stats?.recent_activity?.slice(0, 5).map((item, i) => (
                            <div key={i} className="flex items-start gap-3 p-3 rounded-lg bg-surface/30 border border-border/30 hover:bg-surface/50 transition-colors">
                                <div className={`mt-1.5 w-1.5 h-1.5 rounded-full flex-shrink-0 ${item.status === 'ready' ? 'bg-success' : item.status === 'failed' ? 'bg-danger' : 'bg-warning'}`} />
                                <div className="flex-1 min-w-0">
                                    <p className="text-xs text-slate-300 font-medium truncate">{item.description}</p>
                                    <p className="text-[10px] text-muted font-bold uppercase mt-0.5">{new Date(item.time).toLocaleTimeString()}</p>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Nodes Status */}
                <div className="glass-card">
                    <h2 className="text-white font-semibold mb-4 flex items-center gap-2">
                        <Activity size={18} className="text-primary" /> Network Nodes
                    </h2>
                    <div className="grid grid-cols-2 gap-3">
                        {[
                            { label: 'Django API', status: 'Online', uptime: '14d 2h' },
                            { status: 'Active', label: 'YOLO Detector', uptime: '3d 12h' },
                            { label: 'Redis Cache', status: 'Healthy', uptime: '28d 5h' },
                            { label: 'OctoPrint', status: 'Connected', uptime: '2d 1h' },
                        ].map(node => (
                            <div key={node.label} className="p-3 rounded-xl bg-surface/30 border border-border/30 flex flex-col gap-1">
                                <div className="flex items-center justify-between">
                                    <p className="text-[10px] text-muted font-bold uppercase">{node.label}</p>
                                    <span className="w-1.5 h-1.5 rounded-full bg-success shadow-[0_0_8px_#4ade80]" />
                                </div>
                                <p className="text-white font-bold text-sm">{node.status}</p>
                                <p className="text-[10px] text-muted">Uptime: {node.uptime}</p>
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        </motion.div>
    )
}
