import axios from 'axios'

const getBaseUrl = () => {
    const rawUrl = import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL || '/api'
    const trimmed = (rawUrl || '/api').replace(/\/+$/, '')
    return trimmed.endsWith('/api') ? trimmed : `${trimmed}/api`
}

const api = axios.create({
    baseURL: getBaseUrl(),
    headers: { 'Content-Type': 'application/json' },
})

// Attach JWT access token to requests if present
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('access') || localStorage.getItem('token')
        if (token) {
            config.headers.Authorization = `Bearer ${token}`
        }
        // Let the browser set the boundary parameter automatically for FormData
        if (config.data instanceof FormData) {
            delete config.headers['Content-Type']
            if (config.headers?.delete) {
                config.headers.delete('Content-Type')
            }
        }
        return config
    },
    (error) => Promise.reject(error)
)

// Handle 401 unauthorized responses cleanly
api.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response && error.response.status === 401) {
            const currentPath = window.location.pathname
            if (currentPath !== '/login' && currentPath !== '/register') {
                localStorage.removeItem('access')
                localStorage.removeItem('token')
                localStorage.removeItem('user')
                window.location.href = '/login'
            }
        }
        return Promise.reject(error)
    }
)

export function getWebSocketUrl(path) {
    const cleanPath = path.startsWith('/') ? path : `/${path}`
    if (import.meta.env.VITE_WS_URL) {
        const base = import.meta.env.VITE_WS_URL.replace(/\/$/, '')
        return `${base}${cleanPath}`
    }
    const apiBase = import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL || ''
    if (apiBase && /^https?:\/\//i.test(apiBase)) {
        const base = apiBase.replace(/^http/i, 'ws').replace(/\/api\/?$/, '').replace(/\/+$/, '')
        return `${base}${cleanPath}`
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${window.location.host}${cleanPath}`
}

export function getAuthenticatedUrl(path) {
    if (!path) return ''
    let fullUrl = path
    if (path.startsWith('/')) {
        const rawBackend = import.meta.env.VITE_API_URL || import.meta.env.VITE_API_BASE_URL || ''
        if (rawBackend && /^https?:\/\//i.test(rawBackend)) {
            const host = rawBackend.replace(/\/api\/?$/, '').replace(/\/+$/, '')
            fullUrl = `${host}${path}`
        }
    }
    const token = localStorage.getItem('access') || localStorage.getItem('token')
    if (!token) return fullUrl
    const separator = fullUrl.includes('?') ? '&' : '?'
    return `${fullUrl}${separator}token=${encodeURIComponent(token)}`
}

export default api
