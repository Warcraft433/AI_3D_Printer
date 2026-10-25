import { Routes, Route, Navigate, BrowserRouter } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import { NotificationProvider } from './context/NotificationContext'
import { GenerationProvider } from './context/GenerationContext'
import { PrinterProvider } from './context/PrinterContext'
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
import PrinterControl from './pages/PrinterControl'
import GCodePreview from './pages/GCodePreview'

function ProtectedRoute({ children }) {
    const { user, loading } = useAuth()
    if (loading) {
        return (
            <div className="min-h-screen bg-surface flex items-center justify-center">
                <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin" />
            </div>
        )
    }
    if (!user) {
        return <Navigate to="/login" replace />
    }
    return children
}

function PublicRoute({ children }) {
    const { user, loading } = useAuth()
    if (loading) {
        return (
            <div className="min-h-screen bg-surface flex items-center justify-center">
                <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin" />
            </div>
        )
    }
    if (user) {
        return <Navigate to="/" replace />
    }
    return children
}

export default function App() {
    return (
        <BrowserRouter>
            <AuthProvider>
                <NotificationProvider>
                    <PrinterProvider>
                        <GenerationProvider>
                            <Routes>
                                <Route path="/login" element={<PublicRoute><Login /></PublicRoute>} />
                                <Route path="/register" element={<PublicRoute><Register /></PublicRoute>} />

                                <Route path="/" element={<ProtectedRoute><MainLayout /></ProtectedRoute>}>
                                    <Route index element={<Dashboard />} />
                                    <Route path="generate" element={<GenerateModel />} />
                                    <Route path="image-to-3d" element={<Navigate to="/generate?mode=image" replace />} />
                                    <Route path="history" element={<ModelHistory />} />
                                    <Route path="ai-monitoring" element={<AIMonitoring />} />
                                    <Route path="printer-control" element={<PrinterControl />} />
                                    <Route path="print-queue" element={<PrintQueue />} />
                                    <Route path="gcode-preview/:jobId" element={<GCodePreview />} />
                                    <Route path="octoprint" element={<Navigate to="/printer-control" replace />} />
                                    <Route path="settings" element={<Settings />} />
                                </Route>
                                <Route path="*" element={<Navigate to="/" replace />} />
                            </Routes>
                        </GenerationProvider>
                    </PrinterProvider>
                </NotificationProvider>
            </AuthProvider>
        </BrowserRouter>
    )
}
