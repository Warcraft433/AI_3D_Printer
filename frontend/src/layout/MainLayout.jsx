import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from '../components/Sidebar'
import Topbar from '../components/Topbar'

export default function MainLayout() {
    const [collapsed, setCollapsed] = useState(false)
    return (
        <div className="flex h-screen bg-surface overflow-hidden">
            <Sidebar collapsed={collapsed} setCollapsed={setCollapsed} />
            <div className="flex flex-col flex-1 overflow-hidden">
                <Topbar />
                <main className="flex-1 overflow-y-auto p-6 page-enter">
                    <Outlet />
                </main>
            </div>
        </div>
    )
}
