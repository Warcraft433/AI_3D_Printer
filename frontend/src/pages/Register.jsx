import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Printer, UserPlus } from 'lucide-react'
import { useAuth } from '../context/AuthContext'

export default function Register() {
    const { register } = useAuth()
    const navigate = useNavigate()
    const [form, setForm] = useState({ username: '', email: '', password: '' })
    const [error, setError] = useState('')
    const [loading, setLoading] = useState(false)

    const submit = async (e) => {
        e.preventDefault()
        setLoading(true); setError('')
        try {
            await register(form.username, form.email, form.password)
            navigate('/')
        } catch (err) {
            const d = err.response?.data
            setError(d ? Object.values(d).flat().join(' ') : 'Registration failed')
        } finally { setLoading(false) }
    }

    return (
        <div className="min-h-screen bg-surface flex items-center justify-center p-4">
            <div className="absolute inset-0 bg-gradient-to-br from-primary/5 via-transparent to-secondary/5 pointer-events-none" />
            <div className="w-full max-w-md">
                <div className="text-center mb-8">
                    <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-primary shadow-glow mb-4">
                        <Printer size={28} className="text-white" />
                    </div>
                    <h1 className="text-3xl font-bold text-white">Create Account</h1>
                    <p className="text-muted mt-1">Join the AI 3D Printer Platform</p>
                </div>
                <div className="glass-card">
                    {error && <div className="mb-4 px-4 py-3 rounded-lg bg-danger/10 border border-danger/25 text-danger text-sm">{error}</div>}
                    <form onSubmit={submit} className="space-y-4">
                        <div>
                            <label className="label">Username</label>
                            <input className="input" type="text" placeholder="your_username" value={form.username}
                                onChange={e => setForm({ ...form, username: e.target.value })} required />
                        </div>
                        <div>
                            <label className="label">Email</label>
                            <input className="input" type="email" placeholder="you@example.com" value={form.email}
                                onChange={e => setForm({ ...form, email: e.target.value })} />
                        </div>
                        <div>
                            <label className="label">Password</label>
                            <input className="input" type="password" placeholder="min. 6 characters" value={form.password}
                                onChange={e => setForm({ ...form, password: e.target.value })} required />
                        </div>
                        <button type="submit" className="btn-primary w-full justify-center mt-2" disabled={loading}>
                            <UserPlus size={16} />
                            {loading ? 'Creating account...' : 'Create Account'}
                        </button>
                    </form>
                    <p className="text-center text-muted text-sm mt-4">
                        Already have an account?{' '}
                        <Link to="/login" className="text-primary hover:text-accent transition-colors">Sign In</Link>
                    </p>
                </div>
            </div>
        </div>
    )
}
