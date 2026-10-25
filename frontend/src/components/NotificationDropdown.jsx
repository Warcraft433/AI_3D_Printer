import React, { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import {
    Bell,
    CheckCheck,
    Trash2,
    X,
    AlertTriangle,
    Printer,
    Sparkles,
    CheckCircle2,
    Info,
    ExternalLink,
    Radio,
    Flame
} from 'lucide-react'
import { useNotifications, formatTimeAgo } from '../context/NotificationContext'

export default function NotificationDropdown() {
    const navigate = useNavigate()
    const {
        notifications,
        unreadCount,
        isWsConnected,
        markAsRead,
        markAllAsRead,
        removeNotification,
        clearAll,
        toastAlert,
        setToastAlert,
    } = useNotifications()

    const [isOpen, setIsOpen] = useState(false)
    const [activeFilter, setActiveFilter] = useState('all') // 'all' | 'alerts' | 'prints' | 'models'
    const dropdownRef = useRef(null)

    // Close on click outside or Escape key
    useEffect(() => {
        const handleClickOutside = (e) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
                setIsOpen(false)
            }
        }
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') {
                setIsOpen(false)
            }
        }

        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside)
            document.addEventListener('keydown', handleKeyDown)
        }
        return () => {
            document.removeEventListener('mousedown', handleClickOutside)
            document.removeEventListener('keydown', handleKeyDown)
        }
    }, [isOpen])

    // Auto-dismiss floating toast alert after 7 seconds
    useEffect(() => {
        if (!toastAlert) return
        const timer = setTimeout(() => {
            setToastAlert(null)
        }, 7000)
        return () => clearTimeout(timer)
    }, [toastAlert, setToastAlert])

    // Filter notifications
    const filteredNotifications = notifications.filter((item) => {
        if (activeFilter === 'alerts') return item.type === 'defect'
        if (activeFilter === 'prints') return item.type === 'print'
        if (activeFilter === 'models') return item.type === 'model'
        return true
    })

    const handleItemClick = (item) => {
        markAsRead(item.id)
        if (item.link) {
            navigate(item.link)
            setIsOpen(false)
        }
    }

    const getItemIcon = (type, severity) => {
        if (type === 'defect' || severity === 'danger') {
            return (
                <div className="w-8 h-8 rounded-lg bg-red-500/15 border border-red-500/30 flex items-center justify-center text-red-400 shrink-0 shadow-sm shadow-red-500/20">
                    <AlertTriangle size={15} />
                </div>
            )
        }
        if (type === 'print') {
            return (
                <div className="w-8 h-8 rounded-lg bg-cyan-500/15 border border-cyan-500/30 flex items-center justify-center text-cyan-400 shrink-0 shadow-sm shadow-cyan-500/20">
                    <Printer size={15} />
                </div>
            )
        }
        if (type === 'model') {
            return (
                <div className="w-8 h-8 rounded-lg bg-purple-500/15 border border-purple-500/30 flex items-center justify-center text-purple-400 shrink-0 shadow-sm shadow-purple-500/20">
                    <Sparkles size={15} />
                </div>
            )
        }
        return (
            <div className="w-8 h-8 rounded-lg bg-blue-500/15 border border-blue-500/30 flex items-center justify-center text-blue-400 shrink-0 shadow-sm shadow-blue-500/20">
                <Info size={15} />
            </div>
        )
    }

    return (
        <div className="relative" ref={dropdownRef}>
            {/* ── Notification Bell Button ── */}
            <button
                type="button"
                id="notification-bell-btn"
                aria-label="Notifications"
                aria-haspopup="true"
                aria-expanded={isOpen}
                onClick={() => setIsOpen((prev) => !prev)}
                className={`relative p-2 rounded-xl transition-all duration-200 outline-none focus:ring-2 focus:ring-primary/40 ${
                    isOpen
                        ? 'bg-primary/20 text-white shadow-glow border border-primary/40'
                        : 'text-muted hover:text-white hover:bg-white/5 border border-transparent'
                }`}
                title={unreadCount > 0 ? `${unreadCount} unread notification(s)` : 'Notifications'}
            >
                <motion.div
                    animate={
                        unreadCount > 0
                            ? {
                                  rotate: [0, -12, 12, -8, 8, 0],
                                  transition: { repeat: Infinity, repeatDelay: 6, duration: 0.8 },
                              }
                            : {}
                    }
                >
                    <Bell size={19} className={unreadCount > 0 ? 'text-primary-light' : ''} />
                </motion.div>

                {/* Animated Unread Badge */}
                {unreadCount > 0 && (
                    <span className="absolute -top-1 -right-1 flex items-center justify-center min-w-[1.25rem] h-5 px-1 bg-gradient-to-r from-red-500 via-rose-500 to-primary text-white text-[10px] font-extrabold rounded-full shadow-lg shadow-red-500/30 border border-surface animate-in fade-in zoom-in">
                        <span className="absolute -inset-0.5 rounded-full bg-red-400/30 animate-ping" />
                        <span className="relative">{unreadCount > 9 ? '9+' : unreadCount}</span>
                    </span>
                )}
            </button>

            {/* ── Dropdown Panel ── */}
            <AnimatePresence>
                {isOpen && (
                    <motion.div
                        initial={{ opacity: 0, y: 10, scale: 0.96 }}
                        animate={{ opacity: 1, y: 0, scale: 1 }}
                        exit={{ opacity: 0, y: 8, scale: 0.96 }}
                        transition={{ duration: 0.16, ease: 'easeOut' }}
                        className="absolute right-0 mt-3 w-96 max-w-[calc(100vw-2rem)] z-50 origin-top-right rounded-2xl bg-card/95 backdrop-blur-2xl border border-border/80 shadow-2xl shadow-black/60 overflow-hidden text-slate-200"
                    >
                        {/* Header */}
                        <div className="p-4 border-b border-border/60 bg-surface/50">
                            <div className="flex items-center justify-between gap-2 mb-3">
                                <div className="flex items-center gap-2">
                                    <div className="p-1.5 rounded-lg bg-primary/15 text-primary border border-primary/25">
                                        <Bell size={16} />
                                    </div>
                                    <h3 className="font-semibold text-sm text-white tracking-tight">Notifications</h3>
                                    {unreadCount > 0 ? (
                                        <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-primary/20 text-accent border border-primary/30">
                                            {unreadCount} new
                                        </span>
                                    ) : (
                                        <span className="text-[11px] text-muted">All read</span>
                                    )}
                                </div>

                                <div className="flex items-center gap-1">
                                    {unreadCount > 0 && (
                                        <button
                                            type="button"
                                            onClick={markAllAsRead}
                                            title="Mark all as read"
                                            className="flex items-center gap-1 text-[11px] font-medium text-muted hover:text-white px-2 py-1 rounded-md hover:bg-white/5 transition-colors"
                                        >
                                            <CheckCheck size={13} className="text-primary" />
                                            Mark all read
                                        </button>
                                    )}
                                    {notifications.length > 0 && (
                                        <button
                                            type="button"
                                            onClick={clearAll}
                                            title="Clear all notifications"
                                            className="p-1.5 rounded-md text-muted hover:text-red-400 hover:bg-red-500/10 transition-colors"
                                        >
                                            <Trash2 size={13} />
                                        </button>
                                    )}
                                </div>
                            </div>

                            {/* Filter Tabs */}
                            <div className="flex items-center gap-1 p-0.5 rounded-lg bg-surface/80 border border-border/50 text-[11px]">
                                {[
                                    { id: 'all', label: 'All', count: notifications.length },
                                    { id: 'alerts', label: 'Alerts', count: notifications.filter((n) => n.type === 'defect').length },
                                    { id: 'prints', label: 'Prints', count: notifications.filter((n) => n.type === 'print').length },
                                    { id: 'models', label: 'Models', count: notifications.filter((n) => n.type === 'model').length },
                                ].map((tab) => (
                                    <button
                                        key={tab.id}
                                        type="button"
                                        onClick={() => setActiveFilter(tab.id)}
                                        className={`flex-1 flex items-center justify-center gap-1 py-1 rounded-md font-medium transition-all ${
                                            activeFilter === tab.id
                                                ? 'bg-card text-white shadow-sm border border-border/80'
                                                : 'text-muted hover:text-slate-200'
                                        }`}
                                    >
                                        <span>{tab.label}</span>
                                        {tab.count > 0 && (
                                            <span
                                                className={`text-[9px] px-1 rounded-full ${
                                                    activeFilter === tab.id
                                                        ? 'bg-primary/30 text-accent font-bold'
                                                        : 'bg-white/5 text-muted'
                                                }`}
                                            >
                                                {tab.count}
                                            </span>
                                        )}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Notifications List */}
                        <div className="max-h-80 overflow-y-auto divide-y divide-border/40 scrollbar-thin">
                            {filteredNotifications.length === 0 ? (
                                <div className="py-12 px-6 text-center flex flex-col items-center justify-center gap-2">
                                    <div className="w-12 h-12 rounded-2xl bg-white/5 border border-border/50 flex items-center justify-center text-muted">
                                        <CheckCircle2 size={24} className="text-emerald-400/80" />
                                    </div>
                                    <p className="text-sm font-semibold text-slate-300">No notifications here</p>
                                    <p className="text-xs text-muted max-w-[220px]">
                                        {activeFilter === 'alerts'
                                            ? 'Zero active defects detected. Printer runs normally.'
                                            : 'Everything is up to date! System telemetry is active.'}
                                    </p>
                                </div>
                            ) : (
                                filteredNotifications.map((item) => (
                                    <div
                                        key={item.id}
                                        onClick={() => handleItemClick(item)}
                                        className={`group relative p-3.5 flex items-start gap-3 transition-colors cursor-pointer hover:bg-white/[0.04] ${
                                            !item.read ? 'bg-primary/[0.03]' : ''
                                        }`}
                                    >
                                        {getItemIcon(item.type, item.severity)}

                                        <div className="flex-1 min-w-0 pr-4">
                                            <div className="flex items-center gap-2 mb-0.5">
                                                <h4 className={`text-xs font-semibold truncate ${!item.read ? 'text-white' : 'text-slate-300'}`}>
                                                    {item.title}
                                                </h4>
                                                {!item.read && (
                                                    <span className="w-1.5 h-1.5 rounded-full bg-primary shrink-0 shadow-sm shadow-primary/60" />
                                                )}
                                            </div>

                                            <p className="text-[11px] text-muted line-clamp-2 leading-relaxed mb-1">
                                                {item.message}
                                            </p>

                                            <div className="flex items-center gap-3 text-[10px] text-muted/80">
                                                <span>{formatTimeAgo(item.timestamp)}</span>
                                                {item.link && (
                                                    <span className="flex items-center gap-0.5 text-accent group-hover:underline">
                                                        Inspect <ExternalLink size={9} />
                                                    </span>
                                                )}
                                            </div>
                                        </div>

                                        {/* Dismiss single button */}
                                        <button
                                            type="button"
                                            title="Dismiss notification"
                                            onClick={(e) => {
                                                e.stopPropagation()
                                                removeNotification(item.id)
                                            }}
                                            className="opacity-0 group-hover:opacity-100 p-1 rounded-md text-muted hover:text-white hover:bg-white/10 transition-opacity"
                                        >
                                            <X size={12} />
                                        </button>
                                    </div>
                                ))
                            )}
                        </div>

                        {/* Footer Status Bar */}
                        <div className="p-3 bg-surface/80 border-t border-border/60 flex items-center justify-between text-xs">
                            <div className="flex items-center gap-2">
                                <span className="relative flex h-2 w-2">
                                    {isWsConnected && (
                                        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
                                    )}
                                    <span
                                        className={`relative inline-flex rounded-full h-2 w-2 ${
                                            isWsConnected ? 'bg-emerald-500' : 'bg-amber-500'
                                        }`}
                                    />
                                </span>
                                <span className="text-[11px] text-muted">
                                    {isWsConnected ? 'Live Feed Connected' : 'Feed Standby'}
                                </span>
                            </div>

                            <button
                                type="button"
                                onClick={() => {
                                    navigate('/ai-monitoring')
                                    setIsOpen(false)
                                }}
                                className="text-[11px] text-primary hover:text-accent font-semibold transition-colors flex items-center gap-1"
                            >
                                AI Monitor
                                <ExternalLink size={10} />
                            </button>
                        </div>
                    </motion.div>
                )}
            </AnimatePresence>

            {/* ── Global Floating Real-Time Defect Toast ── */}
            <AnimatePresence>
                {toastAlert && (
                    <motion.div
                        initial={{ opacity: 0, y: -20, scale: 0.95 }}
                        animate={{ opacity: 1, y: 0, scale: 1 }}
                        exit={{ opacity: 0, y: -15, scale: 0.95 }}
                        className="fixed top-20 right-6 z-[100] w-96 max-w-[calc(100vw-2rem)] rounded-2xl bg-card/95 backdrop-blur-2xl border border-red-500/40 p-4 shadow-2xl shadow-red-950/60 text-slate-200"
                    >
                        <div className="flex items-start gap-3">
                            <div className="w-10 h-10 rounded-xl bg-red-500/20 border border-red-500/40 flex items-center justify-center text-red-400 shrink-0 animate-pulse">
                                <AlertTriangle size={20} />
                            </div>
                            <div className="flex-1 min-w-0">
                                <div className="flex items-center justify-between gap-1 mb-1">
                                    <span className="text-[10px] font-bold uppercase tracking-wider text-red-400 bg-red-500/10 px-2 py-0.5 rounded-full border border-red-500/20 flex items-center gap-1">
                                        <Flame size={10} /> Real-Time Defect Alert
                                    </span>
                                    <button
                                        type="button"
                                        onClick={() => setToastAlert(null)}
                                        className="text-muted hover:text-white p-1 rounded-md"
                                    >
                                        <X size={14} />
                                    </button>
                                </div>
                                <h4 className="text-sm font-bold text-white mb-0.5">{toastAlert.title}</h4>
                                <p className="text-xs text-muted mb-3 leading-relaxed">{toastAlert.message}</p>
                                <div className="flex items-center gap-2">
                                    <button
                                        type="button"
                                        onClick={() => {
                                            if (toastAlert.link) navigate(toastAlert.link)
                                            setToastAlert(null)
                                        }}
                                        className="flex-1 py-1.5 px-3 rounded-lg bg-red-500/20 hover:bg-red-500/30 text-red-300 border border-red-500/30 text-xs font-semibold transition-all flex items-center justify-center gap-1"
                                    >
                                        Inspect Defect
                                        <ExternalLink size={12} />
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setToastAlert(null)}
                                        className="py-1.5 px-3 rounded-lg bg-white/5 hover:bg-white/10 text-muted hover:text-white text-xs font-medium transition-colors"
                                    >
                                        Dismiss
                                    </button>
                                </div>
                            </div>
                        </div>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    )
}
