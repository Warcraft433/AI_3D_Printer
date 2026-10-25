import { useLocation } from 'react-router-dom'
import { LogOut, User } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import NotificationDropdown from './NotificationDropdown'

const PAGE_TITLES = {
    '/': 'Dashboard',
    '/generate': 'Text → 3D Model',
    '/image-to-3d': 'Image → 3D Model',
    '/history': 'Model History',
    '/ai-monitoring': 'AI Monitoring',
    '/printer-control': 'Printer Control',
    '/print-queue': 'Print Queue',
    '/settings': 'Settings',
}

export default function Topbar() {
    const { user, logout } = useAuth()
    const { pathname } = useLocation()
    const title = PAGE_TITLES[pathname] || 'AI 3D Printer'

    return (
        <header className="flex items-center justify-between px-6 py-4 border-b border-border bg-card/50 backdrop-blur-sm relative z-30">
            <div>
                <h1 className="text-lg font-bold text-white">{title}</h1>
                <p className="text-xs text-muted">AI-Enhanced 3D Printer · B.Tech FYP</p>
            </div>
            <div className="flex items-center gap-3">
                <NotificationDropdown />
                <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-white/5 border border-border">
                    <div className="w-7 h-7 rounded-full bg-gradient-to-br from-primary to-secondary flex items-center justify-center">
                        <User size={14} className="text-white" />
                    </div>
                    <span className="text-sm text-white font-medium">{user?.username || 'User'}</span>
                </div>
                <button
                    onClick={logout}
                    title="Sign Out"
                    aria-label="Sign Out"
                    className="p-2 rounded-lg text-muted hover:text-danger hover:bg-danger/10 transition-all"
                >
                    <LogOut size={18} />
                </button>
            </div>
        </header>
    )
}
