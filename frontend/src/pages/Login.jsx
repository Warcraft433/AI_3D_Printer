import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Printer, LogIn, Eye, EyeOff, Lock, User, AlertCircle, Sparkles } from 'lucide-react'
import { useAuth } from '../context/AuthContext'

export default function Login() {
    const { login } = useAuth()
    const navigate = useNavigate()
    const [form, setForm] = useState({ username: '', password: '' })
    const [showPassword, setShowPassword] = useState(false)
    const [error, setError] = useState('')
    const [loading, setLoading] = useState(false)

    const doLogin = async (username, password) => {
        setLoading(true)
        setError('')
        try {
            await login(username, password)
            navigate('/')
        } catch (err) {
            const apiError = err.response?.data?.error || err.response?.data?.detail
            if (apiError) {
                setError(apiError)
            } else if (err.response?.status === 401) {
                setError('Invalid username or password')
            } else if (err.response?.status === 400) {
                setError('Invalid input. Please check your credentials.')
            } else {
                setError('Unable to connect to backend service (http://localhost:8080). Ensure backend is running.')
            }
        } finally {
            setLoading(false)
        }
    }

    const submit = (e) => {
        e.preventDefault()
        const trimmedUser = form.username.trim()
        if (!trimmedUser) {
            setError('Please enter your username')
            return
        }
        if (!form.password) {
            setError('Please enter your password')
            return
        }
        doLogin(trimmedUser, form.password)
    }

    const handleQuickDemoLogin = () => {
        setForm({ username: 'admin', password: 'admin123' })
        doLogin('admin', 'admin123')
    }

    return (
        <div className="min-h-screen bg-surface flex items-center justify-center p-4">
            <div className="absolute inset-0 bg-gradient-to-br from-primary/5 via-transparent to-secondary/5 pointer-events-none" />
            <div className="w-full max-w-md relative z-10">
                <div className="text-center mb-8">
                    <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-primary shadow-glow mb-4">
                        <Printer size={28} className="text-white" />
                    </div>
                    <h1 className="text-3xl font-bold text-white tracking-tight">PrintGuard AI</h1>
                    <p className="text-muted mt-1 text-sm">Autonomous 3D Printing & Quality Assurance</p>
                </div>
                <div className="glass-card">
                    <div className="flex items-center justify-between mb-6">
                        <h2 className="text-xl font-bold text-white">Sign In</h2>
                        <button
                            type="button"
                            onClick={handleQuickDemoLogin}
                            disabled={loading}
                            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary/20 text-primary hover:bg-primary/30 border border-primary/30 text-xs font-medium transition-all"
                            title="Instant Login as admin / admin123"
                        >
                            <Sparkles size={13} />
                            <span>Quick Demo Login</span>
                        </button>
                    </div>

                    {error && (
                        <div className="mb-4 px-4 py-3 rounded-lg bg-danger/10 border border-danger/25 text-danger text-sm flex items-start gap-2">
                            <AlertCircle size={16} className="mt-0.5 shrink-0" />
                            <span>{error}</span>
                        </div>
                    )}

                    <form onSubmit={submit} className="space-y-4">
                        <div>
                            <label className="label" htmlFor="login-username">Username</label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-muted">
                                    <User size={16} />
                                </div>
                                <input
                                    id="login-username"
                                    className="input pl-9"
                                    type="text"
                                    placeholder="Enter your username"
                                    value={form.username}
                                    onChange={e => setForm({ ...form, username: e.target.value })}
                                    required
                                    autoComplete="username"
                                />
                            </div>
                        </div>
                        <div>
                            <label className="label" htmlFor="login-password">Password</label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-muted">
                                    <Lock size={16} />
                                </div>
                                <input
                                    id="login-password"
                                    className="input pl-9 pr-10"
                                    type={showPassword ? 'text' : 'password'}
                                    placeholder="Enter your password"
                                    value={form.password}
                                    onChange={e => setForm({ ...form, password: e.target.value })}
                                    required
                                    autoComplete="current-password"
                                />
                                <button
                                    type="button"
                                    className="absolute inset-y-0 right-0 pr-3 flex items-center text-muted hover:text-white transition-colors"
                                    onClick={() => setShowPassword(!showPassword)}
                                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                                >
                                    {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                                </button>
                            </div>
                        </div>
                        <button type="submit" className="btn-primary w-full justify-center mt-2" disabled={loading}>
                            <LogIn size={16} />
                            {loading ? 'Authenticating...' : 'Sign In'}
                        </button>
                    </form>

                    <p className="text-center text-muted text-sm mt-5">
                        Don't have an account?{' '}
                        <Link to="/register" className="text-primary hover:text-accent font-medium transition-colors">Register</Link>
                    </p>
                </div>
            </div>
        </div>
    )
}
