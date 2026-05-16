import axios from 'axios'

const api = axios.create({
    baseURL: '/api',
    headers: { 'Content-Type': 'application/json' },
})

// Authentication interceptors removed for public mode.
// No Bearer tokens or 401 redirects are necessary.

export default api
