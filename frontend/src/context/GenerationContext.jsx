/**
 * GenerationContext — global state for Shap-E model generation.
 *
 * This context lives above the router so generation progress (task, state,
 * elapsed timer, error) survives route changes. The component that triggers
 * a generation (GenerateModel / ImageTo3D) sets state here; when the user
 * navigates away and back, the page re-reads this context and the timer /
 * realtime WebSocket updates are already in progress.
 *
 * On first mount we also look for any in-progress backend task (status
 * "processing") so that a browser-refresh can also restore state.
 */
import { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react'
import api from '../services/api'

// ── context ──────────────────────────────────────────────────────────────────
const GenerationContext = createContext(null)

// ── helper: build initial sub-state ──────────────────────────────────────────
const INITIAL_TEXT = () => ({
    task: null,
    state: 'idle',
    elapsed: 0,
    error: '',
    prompt: localStorage.getItem('gen_text_prompt') || '',
    steps: parseInt(localStorage.getItem('gen_text_steps')) || 32,
})

const INITIAL_IMAGE = () => ({
    task: null,
    state: 'idle',
    elapsed: 0,
    error: '',
    file: null,
    preview: null,
    steps: 32,
})

// ── provider ──────────────────────────────────────────────────────────────────
export function GenerationProvider({ children }) {
    // ----- text generation state -----
    const [text, setText_] = useState(INITIAL_TEXT())
    // ----- image generation state -----
    const [image, setImage_] = useState(INITIAL_IMAGE())

    // timer & WS refs
    const textTimer = useRef(null)
    const textWs = useRef(null)
    const imageTimer = useRef(null)
    const imageWs = useRef(null)

    const setText = useCallback((patch) => setText_(prev => ({ ...prev, ...patch })), [])
    const setImage = useCallback((patch) => setImage_(prev => ({ ...prev, ...patch })), [])

    // Sync text inputs to localStorage
    useEffect(() => {
        localStorage.setItem('gen_text_prompt', text.prompt || '')
        localStorage.setItem('gen_text_steps', text.steps || 32)
    }, [text.prompt, text.steps])

    // ── timer utils ──────────────────────────────────────────────────────────
    const startTextTimer = useCallback((startTime) => {
        clearInterval(textTimer.current)
        const baseTime = startTime ? new Date(startTime).getTime() : Date.now()

        const update = () => {
            const now = Date.now()
            const diff = Math.floor((now - baseTime) / 1000)
            setText({ elapsed: diff > 0 ? diff : 0 })
        }
        update()
        textTimer.current = setInterval(update, 1000)
    }, [setText])

    const stopTextTimer = useCallback(() => {
        clearInterval(textTimer.current)
        textTimer.current = null
    }, [])

    const startImageTimer = useCallback((startTime) => {
        clearInterval(imageTimer.current)
        const baseTime = startTime ? new Date(startTime).getTime() : Date.now()

        const update = () => {
            const now = Date.now()
            const diff = Math.floor((now - baseTime) / 1000)
            setImage({ elapsed: diff > 0 ? diff : 0 })
        }
        update()
        imageTimer.current = setInterval(update, 1000)
    }, [setImage])

    const stopImageTimer = useCallback(() => {
        clearInterval(imageTimer.current)
        imageTimer.current = null
    }, [])

    const stopTextWS = useCallback(() => {
        if (textWs.current) {
            textWs.current.close()
            textWs.current = null
        }
    }, [])

    const stopImageWS = useCallback(() => {
        if (imageWs.current) {
            imageWs.current.close()
            imageWs.current = null
        }
    }, [])

    // ── WebSocket factory ────────────────────────────────────────────────────
    const startWebSocket = useCallback((modelId, setter, stopTimerFn, wsRef_) => {
        if (wsRef_.current) wsRef_.current.close()

        const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8000'
        const wsHost = apiUrl.replace(/^http/, 'ws')

        const ws = new WebSocket(`${wsHost}/ws/generation/${modelId}/`)
        wsRef_.current = ws

        ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data)
                setter(prev => {
                    const updatedTask = { ...prev.task, status: data.status }
                    if (data.file_url) updatedTask.file_url = data.file_url
                    if (data.generation_time) updatedTask.generation_time = data.generation_time
                    if (data.error_detail) updatedTask.error_detail = data.error_detail

                    if (data.status === 'ready') {
                        stopTimerFn()
                        return { ...prev, task: updatedTask, state: 'done' }
                    } else if (data.status === 'failed') {
                        stopTimerFn()
                        return { ...prev, task: updatedTask, state: 'error', error: data.error_detail || 'Generation failed.' }
                    }
                    return { ...prev, task: updatedTask }
                })
            } catch (err) {
                console.error('WS parse error:', err)
            }
        }
        ws.onerror = (e) => console.error('WS error:', e)
    }, [])

    // ── on mount: restore any active task ────────────────────────────────────
    useEffect(() => {
        const restoreTask = async (sourceType, setter, startTimerFn, stopTimerFn, wsRef_) => {
            const savedId = sessionStorage.getItem(`gen_task_id_${sourceType}`)
            if (!savedId) return
            try {
                const { data } = await api.get(`/models/${savedId}/`)
                if (data.status === 'ready') {
                    setter({ task: data, state: 'done', error: '', elapsed: data.generation_time || 0 })
                } else if (data.status === 'failed') {
                    setter({ task: data, state: 'error', error: data.error_detail || 'Generation failed.' })
                } else {
                    setter({ task: data, state: 'polling', error: '' })
                    startTimerFn(data.created_at)
                    startWebSocket(data.id, setter, stopTimerFn, wsRef_)
                }
            } catch {
                sessionStorage.removeItem(`gen_task_id_${sourceType}`)
            }
        }

        restoreTask('text', setText, startTextTimer, stopTextTimer, textWs)
        restoreTask('image', setImage, startImageTimer, stopImageTimer, imageWs)

        return () => {
            stopTextTimer()
            stopImageTimer()
            stopTextWS()
            stopImageWS()
        }
    }, [startWebSocket, startTextTimer, stopTextTimer, startImageTimer, stopImageTimer, stopTextWS, stopImageWS, setText, setImage])

    // ── Reliability Poller: Fallback when WS fails ──────────────────────────
    useEffect(() => {
        const poll = async () => {
            // Check text
            if (text.state === 'polling' && text.task?.id) {
                try {
                    const { data } = await api.get(`/models/${text.task.id}/`)
                    if (data.status === 'ready') {
                        stopTextTimer()
                        setText({ task: data, state: 'done' })
                    } else if (data.status === 'failed') {
                        stopTextTimer()
                        setText({ task: data, state: 'error', error: data.error_detail || 'Generation failed.' })
                    }
                } catch (e) {
                    console.error('Polling text error:', e)
                }
            }
            // Check image
            if (image.state === 'polling' && image.task?.id) {
                try {
                    const { data } = await api.get(`/models/${image.task.id}/`)
                    if (data.status === 'ready') {
                        stopImageTimer()
                        setImage({ task: data, state: 'done' })
                    } else if (data.status === 'failed') {
                        stopImageTimer()
                        setImage({ task: data, state: 'error', error: data.error_detail || 'Generation failed.' })
                    }
                } catch (e) {
                    console.error('Polling image error:', e)
                }
            }
        }

        const interval = setInterval(poll, 15000) // 15s fallback
        return () => clearInterval(interval)
    }, [text.state, text.task?.id, image.state, image.task?.id, stopTextTimer, stopImageTimer, setText, setImage])

    // ── public actions ────────────────────────────────────────────────────────

    const startTextGeneration = useCallback(async (prompt, steps) => {
        stopTextWS()
        stopTextTimer()
        setText({ state: 'loading', task: null, error: '', elapsed: 0, prompt, steps })
        try {
            const { data } = await api.post('/generate/text/', { prompt, steps })
            sessionStorage.setItem('gen_task_id_text', data.id)
            setText({ task: data, state: 'polling' })
            startTextTimer(data.created_at)
            startWebSocket(data.id, setText, stopTextTimer, textWs)
        } catch (e) {
            stopTextTimer()
            setText({ state: 'error', error: e.response?.data?.error || 'Failed to start generation.' })
        }
    }, [startWebSocket, startTextTimer, stopTextTimer, stopTextWS, setText])

    const startImageGeneration = useCallback(async (file, steps) => {
        stopImageWS()
        stopImageTimer()
        // Preview logic
        const precuerUrl = URL.createObjectURL(file)
        setImage({ state: 'loading', task: null, error: '', elapsed: 0, file, preview: precuerUrl, steps })

        const form = new FormData()
        form.append('image', file)
        form.append('steps', steps)
        try {
            const { data } = await api.post('/generate/image/', form, {
                headers: { 'Content-Type': 'multipart/form-data' },
            })
            sessionStorage.setItem('gen_task_id_image', data.id)
            setImage({ task: data, state: 'polling' })
            startImageTimer(data.created_at)
            startWebSocket(data.id, setImage, stopImageTimer, imageWs)
        } catch (e) {
            stopImageTimer()
            setImage({ state: 'error', error: e.response?.data?.error || 'Failed to start generation.' })
        }
    }, [startWebSocket, startImageTimer, stopImageTimer, stopImageWS, setImage])

    const resetText = useCallback(() => {
        stopTextWS()
        stopTextTimer()
        sessionStorage.removeItem('gen_task_id_text')
        setText_(INITIAL_TEXT())
    }, [stopTextWS, stopTextTimer])

    const resetImage = useCallback(() => {
        stopImageWS()
        stopImageTimer()
        sessionStorage.removeItem('gen_task_id_image')
        setImage_(INITIAL_IMAGE())
    }, [stopImageWS, stopImageTimer])

    const value = {
        // text
        textTask: text.task,
        textState: text.state,
        textElapsed: text.elapsed,
        textError: text.error,
        textPrompt: text.prompt,
        textSteps: text.steps,
        setTextPrompt: (p) => setText({ prompt: p }),
        setTextSteps: (s) => setText({ steps: s }),
        startTextGeneration,
        resetText,
        // image
        imageTask: image.task,
        imageState: image.state,
        imageElapsed: image.elapsed,
        imageError: image.error,
        imageFile: image.file,
        imagePreview: image.preview,
        imageSteps: image.steps,
        setImageFile: (f) => {
            if (f) setImage({ file: f, preview: URL.createObjectURL(f) })
            else setImage({ file: null, preview: null })
        },
        setImageSteps: (s) => setImage({ steps: s }),
        startImageGeneration,
        resetImage,
    }

    return (
        <GenerationContext.Provider value={value}>
            {children}
        </GenerationContext.Provider>
    )
}


export const useGeneration = () => {
    const ctx = useContext(GenerationContext)
    if (!ctx) throw new Error('useGeneration must be used inside <GenerationProvider>')
    return ctx
}
