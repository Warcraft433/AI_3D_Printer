import { createContext, useContext, useState, useEffect } from 'react'
import api from '../services/api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
    const [user, setUser] = useState(() => {
        const saved = localStorage.getItem('user')
        if (saved) {
            try { return JSON.parse(saved) } catch { return null }
        }
        return null
    })
    const [loading, setLoading] = useState(true)

    // Check existing session on load
    useEffect(() => {
        const token = localStorage.getItem('access') || localStorage.getItem('token')
        if (!token) {
            setUser(null)
            setLoading(false)
            return
        }

        api.get('/auth/me/')
            .then(res => {
                if (res.data) {
                    setUser(res.data)
                    localStorage.setItem('user', JSON.stringify(res.data))
                }
            })
            .catch(() => {
                localStorage.removeItem('access')
                localStorage.removeItem('token')
                localStorage.removeItem('user')
                setUser(null)
            })
            .finally(() => setLoading(false))
    }, [])

    const login = async (username, password) => {
        const res = await api.post('/auth/login/', { username, password })
        const token = res.data.access
        const userData = res.data.user || { username }
        localStorage.setItem('access', token)
        localStorage.setItem('token', token)
        localStorage.setItem('user', JSON.stringify(userData))
        setUser(userData)
        return res.data
    }

    const register = async (username, email, password) => {
        await api.post('/auth/register/', { username, email, password })
        return login(username, password)
    }

    const logout = () => {
        localStorage.removeItem('access')
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        setUser(null)
        window.location.href = '/login'
    }

    return (
        <AuthContext.Provider value={{ user, loading, login, register, logout }}>
            {children}
        </AuthContext.Provider>
    )
}

export const useAuth = () => useContext(AuthContext)
