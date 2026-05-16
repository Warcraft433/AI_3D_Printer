/** @type {import('tailwindcss').Config} */
export default {
    content: ['./index.html', './src/**/*.{js,jsx}'],
    theme: {
        extend: {
            colors: {
                surface: '#0b0f19',
                card: '#111827',
                border: '#1f2d40',
                primary: '#8b5cf6',
                secondary: '#3b82f6',
                accent: '#a78bfa',
                muted: '#64748b',
                success: '#4ade80',
                danger: '#f87171',
                warning: '#fbbf24',
            },
            fontFamily: {
                sans: ['Inter', 'system-ui', 'sans-serif'],
            },
            boxShadow: {
                glow: '0 0 20px rgba(139,92,246,0.3)',
                'glow-lg': '0 0 40px rgba(139,92,246,0.4)',
                'glow-blue': '0 0 20px rgba(59,130,246,0.3)',
            },
            backgroundImage: {
                'gradient-primary': 'linear-gradient(135deg, #8b5cf6, #3b82f6)',
                'gradient-card': 'linear-gradient(135deg, rgba(139,92,246,0.1), rgba(59,130,246,0.05))',
            },
        },
    },
    plugins: [],
}
