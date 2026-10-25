import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Printer, UserPlus, Eye, EyeOff, Lock, User, Mail, AlertCircle, CheckCircle2 } from 'lucide-react'
import { useAuth } from '../context/AuthContext'

export default function Register() {
    const { register } = useAuth()
    const navigate = useNavigate()
    const [form, setForm] = useState({ username: '', email: '', password: '', confirmPassword: '' })
    const [showPassword, setShowPassword] = useState(false)
    const [showConfirmPassword, setShowConfirmPassword] = useState(false)
    const [error, setError] = useState('')
    const [successMessage, setSuccessMessage] = useState('')
    const [loading, setLoading] = useState(false)

    const submit = async (e) => {
        e.preventDefault()
        const trimmedUser = form.username.trim()
        const trimmedEmail = form.email.trim()

        if (!trimmedUser || trimmedUser.length < 3) {
            setError('Username must be at least 3 characters long')
            return
        }

        if (trimmedEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
            setError('Please enter a valid email address')
            return
        }

        if (!form.password || form.password.length < 6) {
            setError('Password must be at least 6 characters long')
            return
        }

        if (form.password !== form.confirmPassword) {
            setError('Passwords do not match')
            return
        }

        setLoading(true)
        setError('')
        try {
            await register(trimmedUser, trimmedEmail, form.password)
            setSuccessMessage('Registration successful! Redirecting to dashboard...')
            setTimeout(() => {
                navigate('/')
            }, 600)
        } catch (err) {
            const apiError = err.response?.data?.error || err.response?.data?.detail
            if (apiError) {
                setError(apiError)
            } else if (err.response?.status === 409) {
                setError('Username or email is already registered')
            } else if (err.response?.status === 400) {
                setError('Invalid registration details. Please verify your inputs.')
            } else {
                setError('Registration failed. Please check network connection and try again.')
            }
        } finally {
            setLoading(false)
        }
    }

    return (
        <div className="min-h-screen bg-surface flex items-center justify-center p-4">
            <div className="absolute inset-0 bg-gradient-to-br from-primary/5 via-transparent to-secondary/5 pointer-events-none" />
            <div className="w-full max-w-md relative z-10">
                <div className="text-center mb-8">
                    <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-primary shadow-glow mb-4">
                        <Printer size={28} className="text-white" />
                    </div>
                    <h1 className="text-3xl font-bold text-white tracking-tight">Create Account</h1>
                    <p className="text-muted mt-1 text-sm">Join the PrintGuard AI Platform</p>
                </div>
                <div className="glass-card">
                    {error && (
                        <div className="mb-4 px-4 py-3 rounded-lg bg-danger/10 border border-danger/25 text-danger text-sm flex items-start gap-2">
                            <AlertCircle size={16} className="mt-0.5 shrink-0" />
                            <span>{error}</span>
                        </div>
                    )}
                    {successMessage && (
                        <div className="mb-4 px-4 py-3 rounded-lg bg-emerald-500/10 border border-emerald-500/25 text-emerald-400 text-sm flex items-start gap-2">
                            <CheckCircle2 size={16} className="mt-0.5 shrink-0" />
                            <span>{successMessage}</span>
                        </div>
                    )}
                    <form onSubmit={submit} className="space-y-4">
                        <div>
                            <label className="label" htmlFor="register-username">Username</label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-muted">
                                    <User size={16} />
                                </div>
                                <input
                                    id="register-username"
                                    className="input pl-9"
                                    type="text"
                                    placeholder="Choose a username"
                                    value={form.username}
                                    onChange={e => setForm({ ...form, username: e.target.value })}
                                    required
                                    autoComplete="username"
                                />
                            </div>
                        </div>
                        <div>
                            <label className="label" htmlFor="register-email">Email Address</label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-muted">
                                    <Mail size={16} />
                                </div>
                                <input
                                    id="register-email"
                                    className="input pl-9"
                                    type="email"
                                    placeholder="you@example.com"
                                    value={form.email}
                                    onChange={e => setForm({ ...form, email: e.target.value })}
                                    autoComplete="email"
                                />
                            </div>
                        </div>
                        <div>
                            <label className="label" htmlFor="register-password">Password</label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-muted">
                                    <Lock size={16} />
                                </div>
                                <input
                                    id="register-password"
                                    className="input pl-9 pr-10"
                                    type={showPassword ? 'text' : 'password'}
                                    placeholder="Min. 6 characters"
                                    value={form.password}
                                    onChange={e => setForm({ ...form, password: e.target.value })}
                                    required
                                    autoComplete="new-password"
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
                        <div>
                            <label className="label" htmlFor="register-confirm-password">Confirm Password</label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-muted">
                                    <Lock size={16} />
                                </div>
                                <input
                                    id="register-confirm-password"
                                    className="input pl-9 pr-10"
                                    type={showConfirmPassword ? 'text' : 'password'}
                                    placeholder="Re-enter your password"
                                    value={form.confirmPassword}
                                    onChange={e => setForm({ ...form, confirmPassword: e.target.value })}
                                    required
                                    autoComplete="new-password"
                                />
                                <button
                                    type="button"
                                    className="absolute inset-y-0 right-0 pr-3 flex items-center text-muted hover:text-white transition-colors"
                                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                                    aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                                >
                                    {showConfirmPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                                </button>
                            </div>
                        </div>
                        <button type="submit" className="btn-primary w-full justify-center mt-2" disabled={loading}>
                            <UserPlus size={16} />
                            {loading ? 'Creating account...' : 'Create Account'}
                        </button>
                    </form>
                    <p className="text-center text-muted text-sm mt-5">
                        Already have an account?{' '}
                        <Link to="/login" className="text-primary hover:text-accent font-medium transition-colors">Sign In</Link>
                    </p>
                </div>
            </div>
        </div>
    )
}
