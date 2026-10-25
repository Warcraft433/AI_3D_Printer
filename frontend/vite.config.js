import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '')
    const rawBackend = env.VITE_BACKEND_URL || env.VITE_API_BASE_URL || env.VITE_API_URL || process.env.VITE_BACKEND_URL || process.env.VITE_API_BASE_URL || process.env.VITE_API_URL || 'http://127.0.0.1:8080'
    const backendUrl = rawBackend.replace(/\/api\/?$/, '').replace(/\/+$/, '')
    const wsUrl = env.VITE_WS_URL || process.env.VITE_WS_URL || backendUrl.replace(/^http/, 'ws')

    return {
        plugins: [react()],
        server: {
            host: '0.0.0.0',
            port: 5173,
            proxy: {
                '/api': {
                    target: backendUrl,
                    changeOrigin: true,
                },
                '/ws': {
                    target: wsUrl,
                    ws: true,
                    changeOrigin: true,
                },
                '/media': {
                    target: backendUrl,
                    changeOrigin: true,
                },
            },
        },
    }
})
