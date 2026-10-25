import { NavLink, useLocation } from 'react-router-dom'
import {
    LayoutDashboard, Sparkles, ImageIcon, Folder,
    Brain, Printer, Settings, ChevronLeft, ChevronRight, Usb, ListOrdered
} from 'lucide-react'
import { useAuth } from '../context/AuthContext'

const NAV = [
    { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
    { to: '/generate', icon: Sparkles, label: 'Text to 3D' },
    { to: '/generate?mode=image', icon: ImageIcon, label: 'Image to 3D' },
    { to: '/history', icon: Folder, label: 'Model History' },
    { to: '/ai-monitoring', icon: Brain, label: 'AI Monitoring' },
    { to: '/printer-control', icon: Usb, label: 'Printer Control' },
    { to: '/print-queue', icon: ListOrdered, label: 'Print Queue' },
    { to: '/settings', icon: Settings, label: 'Settings' },
]

export default function Sidebar({ collapsed, setCollapsed }) {
    const location = useLocation()

    return (
        <aside className={`flex flex-col bg-card border-r border-border transition-all duration-300 ${collapsed ? 'w-16' : 'w-60'}`}>
            {/* Logo */}
            <div className="flex items-center gap-3 px-4 py-5 border-b border-border">
                <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-primary to-secondary flex items-center justify-center flex-shrink-0 shadow-glow">
                    <Printer size={16} className="text-white" />
                </div>
                {!collapsed && (
                    <div>
                        <p className="text-white font-bold text-sm leading-tight">AI 3D Printer</p>
                        <p className="text-muted text-xs">BTech FYP 2025</p>
                    </div>
                )}
            </div>

            {/* Navigation */}
            <nav className="flex-1 px-2 py-4 space-y-1 overflow-y-auto">
                {NAV.map(({ to, icon: Icon, label }) => {
                    const currentUrl = location.pathname + location.search
                    const isActive = to.includes('?')
                        ? currentUrl === to
                        : (location.pathname === to && (!location.search || location.search === '?mode=text'))

                    return (
                        <NavLink
                            key={to}
                            to={to}
                            end={to === '/'}
                            className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all group
               ${isActive
                                    ? 'bg-primary/15 text-primary border border-primary/25 shadow-glow'
                                    : 'text-muted hover:text-white hover:bg-white/5'}`
                            }
                        >
                            <Icon size={18} className="flex-shrink-0" />
                            {!collapsed && <span>{label}</span>}
                        </NavLink>
                    )
                })}
            </nav>

            {/* Collapse toggle */}
            <div className="border-t border-border p-2">
                <button
                    onClick={() => setCollapsed(!collapsed)}
                    className="flex items-center justify-center w-full py-2 rounded-lg text-muted hover:text-white hover:bg-white/5 transition-all"
                >
                    {collapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
                    {!collapsed && <span className="text-xs ml-2">Collapse</span>}
                </button>
            </div>
        </aside>
    )
}
