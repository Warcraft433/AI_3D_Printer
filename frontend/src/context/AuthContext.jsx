import { createContext, useContext, useState, useEffect } from 'react'
import api from '../services/api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
    const [user, setUser] = useState({ username: 'Guest', email: 'guest@example.com' })
    const [loading, setLoading] = useState(false)

    // No token checks needed in public mode
    useEffect(() => {
        setLoading(false)
    }, [])

    const login = async () => {
        // No-op for public mode
        return { user }
    }

    const register = async () => {
        // No-op for public mode
        return login()
    }

    const logout = () => {
        // No-op or just clear any leftover tokens
        localStorage.clear()
    }

    return (
        <AuthContext.Provider value={{ user, loading, login, register, logout }}>
            {children}
        </AuthContext.Provider>
    )
}

export const useAuth = () => useContext(AuthContext)
