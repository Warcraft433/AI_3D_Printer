import { useLocation } from 'react-router-dom'
import { Bell, LogOut, User } from 'lucide-react'
import { useAuth } from '../context/AuthContext'

const PAGE_TITLES = {
    '/': 'Dashboard',
    '/generate': 'Text → 3D Model',
    '/image-to-3d': 'Image → 3D Model',
    '/history': 'Model History',
    '/ai-monitoring': 'AI Monitoring',
    '/print-queue': 'Print Queue',
    '/settings': 'Settings',
}

export default function Topbar() {
    const { user, logout } = useAuth()
    const { pathname } = useLocation()
    const title = PAGE_TITLES[pathname] || 'AI 3D Printer'

    return (
        <header className="flex items-center justify-between px-6 py-4 border-b border-border bg-card/50 backdrop-blur-sm">
            <div>
                <h1 className="text-lg font-bold text-white">{title}</h1>
                <p className="text-xs text-muted">AI-Enhanced 3D Printer · B.Tech FYP</p>
            </div>
            <div className="flex items-center gap-3">
                <button className="relative p-2 rounded-lg text-muted hover:text-white hover:bg-white/5 transition-all">
                    <Bell size={18} />
                    <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-primary rounded-full" />
                </button>
                <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-white/5 border border-border">
                    <div className="w-7 h-7 rounded-full bg-gradient-to-br from-primary to-secondary flex items-center justify-center">
                        <User size={14} className="text-white" />
                    </div>
                    <span className="text-sm text-white font-medium">Guest User</span>
                </div>
            </div>
        </header>
    )
}
