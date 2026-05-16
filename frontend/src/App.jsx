import { Routes, Route, Navigate } from 'react-router-dom'
import { BrowserRouter } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import { GenerationProvider } from './context/GenerationContext'
import MainLayout from './layout/MainLayout'

import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import GenerateModel from './pages/GenerateModel'
import ImageTo3D from './pages/ImageTo3D'
import ModelHistory from './pages/ModelHistory'
import AIMonitoring from './pages/AIMonitoring'
import PrintQueue from './pages/PrintQueue'
import Settings from './pages/Settings'
import OctoPrint from './pages/OctoPrint'

function PrivateRoute({ children }) {
    const { user, loading } = useAuth()
    if (loading) return (
        <div className="flex items-center justify-center h-screen bg-surface">
            <div className="flex flex-col items-center gap-4">
                <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
                <p className="text-slate-400 text-sm">Loading...</p>
            </div>
        </div>
    )
    return user ? children : <Navigate to="/login" replace />
}

export default function App() {
    return (
        <BrowserRouter>
            <AuthProvider>
                <GenerationProvider>
                    <Routes>
                        {/* Redirect login/register to home as they are no longer needed */}
                        <Route path="/login" element={<Navigate to="/" replace />} />
                        <Route path="/register" element={<Navigate to="/" replace />} />

                        <Route path="/" element={<MainLayout />}>
                            <Route index element={<Dashboard />} />
                            <Route path="generate" element={<GenerateModel />} />
                            <Route path="image-to-3d" element={<ImageTo3D />} />
                            <Route path="history" element={<ModelHistory />} />
                            <Route path="ai-monitoring" element={<AIMonitoring />} />
                            <Route path="print-queue" element={<PrintQueue />} />
                            <Route path="octoprint" element={<OctoPrint />} />
                            <Route path="settings" element={<Settings />} />
                        </Route>
                        <Route path="*" element={<Navigate to="/" replace />} />
                    </Routes>
                </GenerationProvider>
            </AuthProvider>
        </BrowserRouter>
    )
}
