import { useState, useEffect, useRef } from 'react'

const API_BASE = '/api/dashboard'

export default function AIMonitoring() {
    const [isMonitoring, setIsMonitoring] = useState(false)
    const [isLoading, setIsLoading] = useState(false)
    const [statusMsg, setStatusMsg] = useState('')
    const [alert, setAlert] = useState(null)
    const [defectLogs, setDefectLogs] = useState([])
    const imgRef = useRef(null)

    // Initial fetch of logs
    useEffect(() => {
        fetch('/api/error-logs/')
            .then(r => r.json())
            .then(data => setDefectLogs(data.results || data))
            .catch(() => {})
    }, [])

    // WebSocket for AI alerts
    useEffect(() => {
        const socket = new WebSocket(`ws://${window.location.host.split(':')[0]}:8000/ws/dashboard/`)
        socket.onmessage = (e) => {
            const data = JSON.parse(e.data)
            if (data.type === 'ai_defect_alert') {
                setAlert(data.data)
                
                // Add to local log for immediate display
                setDefectLogs(prev => [{
                    id: Date.now(),
                    error_type: data.data.defect,
                    confidence: parseFloat(data.data.confidence) / 100,
                    action_taken: 'paused',
                    timestamp: new Date().toISOString()
                }, ...prev])

                // Clear alert after 15 seconds
                setTimeout(() => setAlert(null), 15000)
            }
        }
        return () => socket.close()
    }, [])

    // On mount: fetch the current state from the backend
    useEffect(() => {
        fetch(`${API_BASE}/monitoring-status/`)
            .then(r => r.json())
            .then(data => setIsMonitoring(data.monitoring))
            .catch(() => setIsMonitoring(false))
    }, [])

    const handleStart = async () => {
        setIsLoading(true)
        setStatusMsg('')
        try {
            const res = await fetch(`${API_BASE}/start-monitoring/`, { method: 'POST' })
            const data = await res.json()
            if (data.monitoring) {
                setIsMonitoring(true)
            }
        } catch (e) {
            setStatusMsg('Failed to connect to backend.')
        } finally {
            setIsLoading(false)
        }
    }

    const handleStop = async () => {
        setIsLoading(true)
        setStatusMsg('')
        try {
            const res = await fetch(`${API_BASE}/stop-monitoring/`, { method: 'POST' })
            const data = await res.json()
            if (!data.monitoring) {
                setIsMonitoring(false)
            }
        } catch (e) {
            setStatusMsg('Failed to connect to backend.')
        } finally {
            setIsLoading(false)
        }
    }

    return (
        <div style={styles.page}>
            {/* Header */}
            <div style={styles.header}>
                <div style={styles.headerTop}>
                    <span style={styles.badge}>YOLOv8 · RTX 2050</span>
                </div>
                <h1 style={styles.title}>AI Monitoring</h1>
                <p style={styles.subtitle}>Live 3D Print Error Detection</p>
            </div>

            {/* Status indicator */}
            <div style={styles.statusRow}>
                <div style={{ ...styles.dot, background: isMonitoring ? '#22c55e' : '#6b7280' }} />
                <span style={{ ...styles.statusLabel, color: isMonitoring ? '#22c55e' : '#9ca3af' }}>
                    {isMonitoring ? 'MONITORING ACTIVE' : 'MONITORING STOPPED'}
                </span>
            </div>

            {/* Video area */}
            <div style={styles.videoArea}>
                {isMonitoring ? (
                    <img
                        ref={imgRef}
                        src={`${API_BASE}/video-feed/`}
                        alt="Live YOLO Feed"
                        style={styles.videoFeed}
                    />
                ) : (
                    <div style={styles.stoppedOverlay}>
                        <div style={styles.stoppedIcon}>⏹</div>
                        <p style={styles.stoppedTitle}>AI Monitoring is currently stopped.</p>
                        <p style={styles.stoppedHint}>
                            Click <strong>Start Monitoring</strong> to activate the camera and error detection.
                        </p>
                    </div>
                )}
            </div>

            {/* Defect Alert */}
            {alert && (
                <div style={styles.alertBox}>
                    <div style={styles.alertHeader}>
                        <span style={styles.alertIcon}>⚠️</span>
                        <div style={{ flex: 1 }}>
                            <p style={styles.alertTitle}>DEFECT DETECTED: {alert.defect}</p>
                            <p style={styles.alertConfidence}>Confidence: {alert.confidence} | {alert.timestamp}</p>
                        </div>
                    </div>
                    <div style={styles.alertBody}>
                        <p style={styles.alertAction}>
                            <span style={styles.pauseIcon}>⏸</span> {alert.action}
                        </p>
                        <p style={styles.alertSubtitle}>Operator Intervention Required</p>
                    </div>
                </div>
            )}

            {/* Live status strip */}
            {isMonitoring && (
                <div style={styles.liveStrip}>
                    <span style={styles.liveDot} />
                    <span>Monitoring Live Feed...</span>
                    <span style={styles.divider}>|</span>
                    <span style={{ color: '#a78bfa' }}>AI Error Detection Active</span>
                </div>
            )}

            {/* Controls */}
            <div style={styles.controls}>
                <button
                    id="btn-start-monitoring"
                    onClick={handleStart}
                    disabled={isMonitoring || isLoading}
                    style={{
                        ...styles.btn,
                        ...styles.btnStart,
                        opacity: (isMonitoring || isLoading) ? 0.45 : 1,
                        cursor: (isMonitoring || isLoading) ? 'not-allowed' : 'pointer',
                    }}
                >
                    {isLoading && !isMonitoring ? '...' : '▶  Start Monitoring'}
                </button>

                <button
                    id="btn-stop-monitoring"
                    onClick={handleStop}
                    disabled={!isMonitoring || isLoading}
                    style={{
                        ...styles.btn,
                        ...styles.btnStop,
                        opacity: (!isMonitoring || isLoading) ? 0.45 : 1,
                        cursor: (!isMonitoring || isLoading) ? 'not-allowed' : 'pointer',
                    }}
                >
                    {isLoading && isMonitoring ? '...' : '⏹  Stop Monitoring'}
                </button>
            </div>

            {/* Defect Log Section */}
            <div style={styles.logContainer}>
                <div style={styles.logHeader}>
                    <h2 style={styles.logTitle}>Defect Log</h2>
                    <span style={styles.logCount}>{defectLogs.length} Detections</span>
                </div>
                <div style={styles.logList}>
                    {defectLogs.length === 0 ? (
                        <div style={styles.emptyLog}>No defects detected yet. System healthy.</div>
                    ) : (
                        defectLogs.map(log => (
                            <div key={log.id} style={styles.logItem}>
                                <div style={styles.logImageContainer}>
                                    {log.frame_image ? (
                                        <img src={log.frame_image} alt="Defect" style={styles.logImage} />
                                    ) : (
                                        <div style={styles.logNoImage}>📷 FRAME</div>
                                    )}
                                </div>
                                <div style={styles.logDetails}>
                                    <div style={styles.logMainRow}>
                                        <span style={styles.logType}>{log.error_type?.replace('_', ' ').toUpperCase()}</span>
                                        <span style={styles.logTime}>{new Date(log.timestamp).toLocaleTimeString()}</span>
                                    </div>
                                    <div style={styles.logSubRow}>
                                        <span style={styles.logAction}>Action: PRINT PAUSED</span>
                                        <span style={styles.logConf}>Conf: {(log.confidence * 100).toFixed(0)}%</span>
                                    </div>
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </div>

            {/* Error / feedback message */}
            {statusMsg && (
                <p style={styles.errorMsg}>{statusMsg}</p>
            )}
        </div>
    )
}

/* ─── Styles ─────────────────────────────────────────────────────────────── */
const styles = {
    page: {
        fontFamily: "'Inter', 'Segoe UI', Arial, sans-serif",
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        padding: '40px 24px 60px',
        color: '#f1f5f9',
        background: 'transparent',
    },
    header: {
        textAlign: 'center',
        marginBottom: '28px',
    },
    headerTop: {
        marginBottom: '12px',
    },
    badge: {
        background: 'rgba(139,92,246,0.18)',
        border: '1px solid rgba(139,92,246,0.45)',
        color: '#a78bfa',
        padding: '4px 14px',
        borderRadius: '999px',
        fontSize: '0.78rem',
        fontWeight: 600,
        letterSpacing: '0.04em',
    },
    title: {
        fontSize: '2rem',
        fontWeight: 700,
        margin: '12px 0 4px',
        background: 'linear-gradient(135deg,#a78bfa,#60a5fa)',
        WebkitBackgroundClip: 'text',
        WebkitTextFillColor: 'transparent',
    },
    subtitle: {
        color: '#94a3b8',
        fontSize: '0.95rem',
        margin: 0,
    },
    statusRow: {
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        marginBottom: '20px',
    },
    dot: {
        width: 10,
        height: 10,
        borderRadius: '50%',
        boxShadow: '0 0 8px currentColor',
        transition: 'background 0.4s',
    },
    statusLabel: {
        fontSize: '0.75rem',
        fontWeight: 700,
        letterSpacing: '0.1em',
        transition: 'color 0.4s',
    },
    videoArea: {
        width: '100%',
        maxWidth: '860px',
        aspectRatio: '16/9',
        background: 'rgba(15,23,42,0.7)',
        border: '1px solid rgba(255,255,255,0.08)',
        borderRadius: '16px',
        overflow: 'hidden',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        marginBottom: '16px',
        boxShadow: '0 8px 40px rgba(0,0,0,0.5)',
    },
    videoFeed: {
        width: '100%',
        height: '100%',
        objectFit: 'cover',
        display: 'block',
    },
    stoppedOverlay: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '40px',
        textAlign: 'center',
        gap: '12px',
    },
    stoppedIcon: {
        fontSize: '3rem',
        opacity: 0.25,
        lineHeight: 1,
    },
    stoppedTitle: {
        fontSize: '1.1rem',
        fontWeight: 500,
        color: '#94a3b8',
        margin: 0,
    },
    stoppedHint: {
        fontSize: '0.88rem',
        color: '#64748b',
        margin: 0,
    },
    liveStrip: {
        display: 'flex',
        alignItems: 'center',
        gap: '10px',
        background: 'rgba(34,197,94,0.08)',
        border: '1px solid rgba(34,197,94,0.2)',
        borderRadius: '8px',
        padding: '8px 20px',
        fontSize: '0.85rem',
        color: '#86efac',
        marginBottom: '28px',
        fontWeight: 500,
    },
    liveDot: {
        display: 'inline-block',
        width: 8,
        height: 8,
        borderRadius: '50%',
        background: '#22c55e',
        boxShadow: '0 0 6px #22c55e',
        animation: 'none',
    },
    divider: {
        color: 'rgba(255,255,255,0.15)',
    },
    controls: {
        display: 'flex',
        gap: '16px',
        flexWrap: 'wrap',
        justifyContent: 'center',
        marginTop: '8px',
    },
    btn: {
        padding: '12px 32px',
        borderRadius: '10px',
        border: 'none',
        fontSize: '0.95rem',
        fontWeight: 600,
        letterSpacing: '0.03em',
        transition: 'opacity 0.2s, transform 0.15s, box-shadow 0.2s',
        outline: 'none',
    },
    btnStart: {
        background: 'linear-gradient(135deg,#22c55e,#16a34a)',
        color: '#fff',
        boxShadow: '0 4px 18px rgba(34,197,94,0.35)',
    },
    btnStop: {
        background: 'linear-gradient(135deg,#ef4444,#b91c1c)',
        color: '#fff',
        boxShadow: '0 4px 18px rgba(239,68,68,0.35)',
    },
    errorMsg: {
        marginTop: '16px',
        color: '#f87171',
        fontSize: '0.88rem',
    },
    alertBox: {
        width: '100%',
        maxWidth: '860px',
        background: 'rgba(239,68,68,0.15)',
        border: '1px solid rgba(239,68,68,0.4)',
        borderRadius: '12px',
        padding: '16px 20px',
        marginBottom: '20px',
        animation: 'pulse-red 2s infinite ease-in-out',
    },
    alertHeader: {
        display: 'flex',
        alignItems: 'center',
        gap: '12px',
        marginBottom: '8px',
    },
    alertIcon: {
        fontSize: '1.5rem',
    },
    alertTitle: {
        margin: 0,
        fontSize: '1rem',
        fontWeight: 700,
        color: '#fca5a5',
        letterSpacing: '0.02em',
    },
    alertConfidence: {
        margin: 0,
        fontSize: '0.75rem',
        color: 'rgba(252,165,165,0.7)',
    },
    alertBody: {
        borderTop: '1px solid rgba(239,68,68,0.2)',
        paddingTop: '8px',
        display: 'flex',
        flexDirection: 'column',
        gap: '4px',
    },
    alertAction: {
        margin: 0,
        fontSize: '0.9rem',
        fontWeight: 600,
        color: '#fff',
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
    },
    pauseIcon: {
        background: '#ef4444',
        width: '18px',
        height: '18px',
        borderRadius: '4px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: '0.6rem',
    },
    alertSubtitle: {
        margin: 0,
        fontSize: '0.8rem',
        color: '#f87171',
        opacity: 0.8,
    },
    logContainer: {
        width: '100%',
        maxWidth: '860px',
        marginTop: '32px',
        background: 'rgba(15,23,42,0.4)',
        border: '1px solid rgba(255,255,255,0.05)',
        borderRadius: '16px',
        padding: '24px',
    },
    logHeader: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: '20px',
        borderBottom: '1px solid rgba(255,255,255,0.05)',
        paddingBottom: '12px',
    },
    logTitle: {
        fontSize: '1.2rem',
        fontWeight: 600,
        margin: 0,
        color: '#f1f5f9',
    },
    logCount: {
        fontSize: '0.75rem',
        color: '#94a3b8',
        background: 'rgba(148,163,184,0.1)',
        padding: '2px 10px',
        borderRadius: '99px',
    },
    logList: {
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
    },
    emptyLog: {
        textAlign: 'center',
        padding: '30px',
        color: '#64748b',
        fontSize: '0.9rem',
        fontStyle: 'italic',
    },
    logItem: {
        display: 'flex',
        gap: '16px',
        background: 'rgba(255,255,255,0.03)',
        border: '1px solid rgba(255,255,255,0.05)',
        borderRadius: '12px',
        padding: '12px',
        alignItems: 'center',
        transition: 'background 0.2s',
    },
    logImageContainer: {
        width: '60px',
        height: '45px',
        background: '#0f172a',
        borderRadius: '6px',
        overflow: 'hidden',
        flexShrink: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
    },
    logImage: {
        width: '100%',
        height: '100%',
        objectFit: 'cover',
    },
    logNoImage: {
        fontSize: '0.5rem',
        color: '#475569',
        fontWeight: 700,
    },
    logDetails: {
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        gap: '4px',
    },
    logMainRow: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    logType: {
        fontSize: '0.85rem',
        fontWeight: 700,
        color: '#fca5a5',
    },
    logTime: {
        fontSize: '0.7rem',
        color: '#64748b',
    },
    logSubRow: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    logAction: {
        fontSize: '0.75rem',
        color: '#94a3b8',
        fontWeight: 500,
    },
    logConf: {
        fontSize: '0.7rem',
        color: '#475569',
    },
}
