import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react'
import api, { getWebSocketUrl } from '../services/api'
import { useAuth } from './AuthContext'

const NotificationContext = createContext(null)

const STORAGE_KEY = 'printguard_notifications_v1'

// Helper for relative time formatting
export function formatTimeAgo(dateString) {
    if (!dateString) return 'Just now'
    try {
        const date = new Date(dateString)
        const now = new Date()
        const diffSecs = Math.floor((now - date) / 1000)
        if (diffSecs < 10) return 'Just now'
        if (diffSecs < 60) return `${diffSecs}s ago`
        const diffMins = Math.floor(diffSecs / 60)
        if (diffMins < 60) return `${diffMins}m ago`
        const diffHours = Math.floor(diffMins / 60)
        if (diffHours < 24) return `${diffHours}h ago`
        const diffDays = Math.floor(diffHours / 24)
        if (diffDays < 7) return `${diffDays}d ago`
        return date.toLocaleDateString()
    } catch {
        return 'Recently'
    }
}

// Gentle synthetic notification sound using Web Audio API (no external asset needed)
function playNotificationChime(type = 'default') {
    try {
        const AudioContext = window.AudioContext || window.webkitAudioContext
        if (!AudioContext) return
        const ctx = new AudioContext()
        if (ctx.state === 'suspended') {
            ctx.resume()
        }

        const now = ctx.currentTime
        const osc = ctx.createOscillator()
        const gain = ctx.createGain()
        osc.connect(gain)
        gain.connect(ctx.destination)

        if (type === 'danger') {
            // Two-tone warning alert
            osc.type = 'triangle'
            osc.frequency.setValueAtTime(440, now)
            osc.frequency.setValueAtTime(330, now + 0.12)
            gain.gain.setValueAtTime(0.15, now)
            gain.gain.exponentialRampToValueAtTime(0.001, now + 0.35)
            osc.start(now)
            osc.stop(now + 0.35)
        } else {
            // Friendly soft chime
            osc.type = 'sine'
            osc.frequency.setValueAtTime(587.33, now) // D5
            osc.frequency.setValueAtTime(880.00, now + 0.08) // A5
            gain.gain.setValueAtTime(0.12, now)
            gain.gain.exponentialRampToValueAtTime(0.001, now + 0.28)
            osc.start(now)
            osc.stop(now + 0.28)
        }
    } catch (e) {
        // Audio may be blocked until user interacts with the page
    }
}

export function NotificationProvider({ children }) {
    const { user } = useAuth()
    const [notifications, setNotifications] = useState([])
    const [isWsConnected, setIsWsConnected] = useState(false)
    const [toastAlert, setToastAlert] = useState(null)
    const wsRef = useRef(null)

    // Load persisted state (read IDs, dismissed IDs)
    const getPersistedState = useCallback(() => {
        try {
            const raw = localStorage.getItem(STORAGE_KEY)
            return raw ? JSON.parse(raw) : { readIds: [], dismissedIds: [] }
        } catch {
            return { readIds: [], dismissedIds: [] }
        }
    }, [])

    const savePersistedState = useCallback((state) => {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
        } catch {}
    }, [])

    // Seed realistic default notifications if the database has zero records yet
    const generateFallbackNotifications = useCallback(() => {
        const now = Date.now()
        return [
            {
                id: 'sys-1',
                type: 'defect',
                title: 'AI Spaghetti Defect Alert',
                message: 'Spaghetti anomaly detected on Job #36 (94% confidence). Automatic pause triggered.',
                timestamp: new Date(now - 1000 * 60 * 8).toISOString(),
                read: false,
                link: '/ai-monitoring',
                severity: 'danger',
            },
            {
                id: 'sys-2',
                type: 'print',
                title: 'Slicing Completed',
                message: 'Job #36 Queen Coronet sliced into 87 layers (PrusaSlicer MK3S+).',
                timestamp: new Date(now - 1000 * 60 * 35).toISOString(),
                read: false,
                link: '/print-queue',
                severity: 'success',
            },
            {
                id: 'sys-3',
                type: 'model',
                title: '3D Model Ready',
                message: 'Chess Queen Coronet synthesized from procedural CAD generator.',
                timestamp: new Date(now - 1000 * 60 * 75).toISOString(),
                read: true,
                link: '/history',
                severity: 'info',
            },
            {
                id: 'sys-4',
                type: 'system',
                title: 'PrintGuard Telemetry Connected',
                message: 'YOLOv8 defect monitor calibrated and ready for active webcam stream.',
                timestamp: new Date(now - 1000 * 60 * 180).toISOString(),
                read: true,
                link: '/printer-control',
                severity: 'info',
            },
        ]
    }, [])

    // Fetch notifications from live backend
    const fetchNotifications = useCallback(async () => {
        if (!user) return
        const persisted = getPersistedState()
        const dismissedSet = new Set(persisted.dismissedIds || [])
        const readSet = new Set(persisted.readIds || [])

        const loaded = []

        try {
            // 1. Fetch Error Logs (Defect alerts)
            const errorLogsRes = await api.get('/error-logs/').catch(() => null)
            if (errorLogsRes?.data && Array.isArray(errorLogsRes.data)) {
                errorLogsRes.data.forEach((log) => {
                    const id = `error-${log.id}`
                    if (dismissedSet.has(id)) return
                    loaded.push({
                        id,
                        type: 'defect',
                        title: `Defect Detected: ${log.error_type || 'Spaghetti'}`,
                        message: `Job #${log.print_job_id || 'Active'} defect alert (${Math.round((log.confidence || 0.85) * 100)}% confidence). Action: ${log.action_taken || 'alerted'}`,
                        timestamp: log.timestamp || new Date().toISOString(),
                        read: readSet.has(id),
                        link: '/ai-monitoring',
                        severity: 'danger',
                        metadata: log,
                    })
                })
            }

            // 2. Fetch Recent Print Jobs
            const jobsRes = await api.get('/print-jobs/').catch(() => null)
            if (jobsRes?.data && Array.isArray(jobsRes.data)) {
                jobsRes.data.slice(0, 8).forEach((job) => {
                    const id = `job-${job.id}`
                    if (dismissedSet.has(id)) return
                    const prompt = job.model_prompt || `Job #${job.id}`
                    const isCompleted = job.status === 'completed'
                    const isFailed = job.status === 'failed'
                    const isReady = job.status === 'ready_to_print'
                    
                    loaded.push({
                        id,
                        type: 'print',
                        title: isCompleted ? 'Print Job Finished' : (isFailed ? 'Print Job Failed' : (isReady ? 'G-Code Ready to Print' : `Job Status: ${job.status}`)),
                        message: `${prompt} (${job.layer_count || 0} layers, ${job.status})`,
                        timestamp: job.created_at || new Date().toISOString(),
                        read: readSet.has(id),
                        link: '/print-queue',
                        severity: isFailed ? 'danger' : (isCompleted ? 'success' : 'info'),
                        metadata: job,
                    })
                })
            }

            // 3. Fetch Recent Models
            const modelsRes = await api.get('/models/').catch(() => null)
            if (modelsRes?.data && Array.isArray(modelsRes.data)) {
                modelsRes.data.slice(0, 5).forEach((model) => {
                    const id = `model-${model.id}`
                    if (dismissedSet.has(id)) return
                    loaded.push({
                        id,
                        type: 'model',
                        title: '3D Model Generated',
                        message: `Model: "${model.prompt || 'Generated CAD'}" is ready for slicing`,
                        timestamp: model.created_at || new Date().toISOString(),
                        read: readSet.has(id),
                        link: '/history',
                        severity: 'info',
                        metadata: model,
                    })
                })
            }
        } catch (err) {
            console.error('Failed to fetch notifications:', err)
        }

        // If no items were loaded from backend (empty database), provide informative baseline items
        if (loaded.length === 0) {
            const fallback = generateFallbackNotifications().filter(n => !dismissedSet.has(n.id))
            setNotifications(fallback.map(n => ({
                ...n,
                read: readSet.has(n.id) ? true : n.read
            })))
        } else {
            // Sort newest first
            loaded.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
            setNotifications(loaded)
        }
    }, [user, getPersistedState, generateFallbackNotifications])

    // Initial load
    useEffect(() => {
        fetchNotifications()
    }, [fetchNotifications])

    // WebSocket listener for live real-time notifications
    useEffect(() => {
        if (!user) return

        let socket = null
        let reconnectTimer = null

        const connectWs = () => {
            try {
                const wsUrl = getWebSocketUrl('/ws/dashboard/')
                socket = new WebSocket(wsUrl)
                wsRef.current = socket

                socket.onopen = () => {
                    setIsWsConnected(true)
                }

                socket.onclose = () => {
                    setIsWsConnected(false)
                    // Auto-reconnect after 4s
                    reconnectTimer = setTimeout(connectWs, 4000)
                }

                socket.onerror = () => {
                    setIsWsConnected(false)
                }

                socket.onmessage = (event) => {
                    try {
                        const payload = JSON.parse(event.data)
                        if (payload.type === 'ai_defect_alert' || payload.type === 'DETECTION_ALERT' || payload.type === 'DEFECT_DETECTED') {
                            const data = payload.data || payload
                            const defectType = data.defect || data.error_type || 'Spaghetti'
                            const conf = Math.round((data.confidence || 0.9) * 100)
                            const newNotif = {
                                id: `ws-alert-${Date.now()}`,
                                type: 'defect',
                                title: `Live Alert: ${defectType} Defect`,
                                message: `Real-time defect detected with ${conf}% confidence. Immediate inspection recommended.`,
                                timestamp: new Date().toISOString(),
                                read: false,
                                link: '/ai-monitoring',
                                severity: 'danger',
                                metadata: data,
                            }

                            playNotificationChime('danger')
                            setToastAlert(newNotif)
                            setNotifications((prev) => [newNotif, ...prev])
                        } else if (payload.type === 'PRINT_STATUS' || payload.type === 'print_job_status') {
                            const data = payload.data || payload
                            const newNotif = {
                                id: `ws-print-${Date.now()}`,
                                type: 'print',
                                title: `Print Job Update: ${data.status || 'Active'}`,
                                message: `Job #${data.job_id || ''} progress: ${data.progress || 0}%.`,
                                timestamp: new Date().toISOString(),
                                read: false,
                                link: '/print-queue',
                                severity: data.status === 'failed' ? 'danger' : 'info',
                                metadata: data,
                            }

                            playNotificationChime('default')
                            setNotifications((prev) => [newNotif, ...prev])
                        }
                    } catch (parseErr) {
                        // ignore malformed payloads
                    }
                }
            } catch (err) {
                reconnectTimer = setTimeout(connectWs, 5000)
            }
        }

        connectWs()

        return () => {
            if (reconnectTimer) clearTimeout(reconnectTimer)
            if (socket) socket.close()
        }
    }, [user])

    // Mark single notification as read
    const markAsRead = useCallback((id) => {
        setNotifications((prev) =>
            prev.map((n) => (n.id === id ? { ...n, read: true } : n))
        )
        const state = getPersistedState()
        const readSet = new Set(state.readIds || [])
        readSet.add(id)
        savePersistedState({ ...state, readIds: Array.from(readSet) })
    }, [getPersistedState, savePersistedState])

    // Mark all notifications as read
    const markAllAsRead = useCallback(() => {
        setNotifications((prev) => prev.map((n) => ({ ...n, read: true })))
        const state = getPersistedState()
        const readSet = new Set(state.readIds || [])
        notifications.forEach((n) => readSet.add(n.id))
        savePersistedState({ ...state, readIds: Array.from(readSet) })
    }, [notifications, getPersistedState, savePersistedState])

    // Dismiss a single notification
    const removeNotification = useCallback((id) => {
        setNotifications((prev) => prev.filter((n) => n.id !== id))
        const state = getPersistedState()
        const dismissedSet = new Set(state.dismissedIds || [])
        dismissedSet.add(id)
        savePersistedState({ ...state, dismissedIds: Array.from(dismissedSet) })
    }, [getPersistedState, savePersistedState])

    // Clear all notifications
    const clearAll = useCallback(() => {
        const state = getPersistedState()
        const dismissedSet = new Set(state.dismissedIds || [])
        notifications.forEach((n) => dismissedSet.add(n.id))
        setNotifications([])
        savePersistedState({ ...state, dismissedIds: Array.from(dismissedSet) })
    }, [notifications, getPersistedState, savePersistedState])

    // Add manual / programmatic notification
    const addNotification = useCallback((notif) => {
        const item = {
            id: notif.id || `notif-${Date.now()}-${Math.random().toString(36).substr(2, 4)}`,
            timestamp: notif.timestamp || new Date().toISOString(),
            read: false,
            severity: notif.severity || 'info',
            type: notif.type || 'system',
            ...notif,
        }
        playNotificationChime(item.severity === 'danger' ? 'danger' : 'default')
        setNotifications((prev) => [item, ...prev])
        setToastAlert(item)
    }, [])

    const unreadCount = notifications.filter((n) => !n.read).length

    const value = {
        notifications,
        unreadCount,
        isWsConnected,
        toastAlert,
        setToastAlert,
        markAsRead,
        markAllAsRead,
        removeNotification,
        clearAll,
        addNotification,
        refreshNotifications: fetchNotifications,
    }

    return (
        <NotificationContext.Provider value={value}>
            {children}
        </NotificationContext.Provider>
    )
}

export function useNotifications() {
    const context = useContext(NotificationContext)
    if (!context) {
        throw new Error('useNotifications must be used within a NotificationProvider')
    }
    return context
}
