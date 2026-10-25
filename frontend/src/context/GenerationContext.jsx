/**
 * GenerationContext — global state for Shap-E model generation.
 *
 * This context lives above the router so generation progress (task, state,
 * elapsed timer, error) survives route changes. When the user navigates away
 * and back, the loaded model and timer stay preserved.
 */
import { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react'
import api, { getWebSocketUrl } from '../services/api'

const GenerationContext = createContext(null)

const INITIAL_TEXT = () => ({
    task: null,
    previousTask: null,
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

export function GenerationProvider({ children }) {
    const [text, setText_] = useState(INITIAL_TEXT())
    const [image, setImage_] = useState(INITIAL_IMAGE())

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
            setText({ elapsed: diff >= 0 ? diff : 0 })
        }
        update()
        textTimer.current = setInterval(update, 500)
    }, [setText])

    const stopTextTimer = useCallback(() => {
        if (textTimer.current) {
            clearInterval(textTimer.current)
            textTimer.current = null
        }
    }, [])

    const startImageTimer = useCallback((startTime) => {
        clearInterval(imageTimer.current)
        const baseTime = startTime ? new Date(startTime).getTime() : Date.now()

        const update = () => {
            const now = Date.now()
            const diff = Math.floor((now - baseTime) / 1000)
            setImage({ elapsed: diff >= 0 ? diff : 0 })
        }
        update()
        imageTimer.current = setInterval(update, 500)
    }, [setImage])

    const stopImageTimer = useCallback(() => {
        if (imageTimer.current) {
            clearInterval(imageTimer.current)
            imageTimer.current = null
        }
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

        const wsUrl = getWebSocketUrl(`/ws/generation/${modelId}/`)
        const ws = new WebSocket(wsUrl)
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
                    setter({ task: data, state: 'done', error: '', elapsed: Math.round(data.generation_time || 0) })
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

    // ── public actions ────────────────────────────────────────────────────────

    const startTextGeneration = useCallback(async (prompt, steps, options = {}) => {
        stopTextWS()
        stopTextTimer()
        const prevTask = text.task
        setText({ state: 'loading', task: null, previousTask: prevTask, error: '', elapsed: 0, prompt, steps })
        startTextTimer()

        try {
            const payload = {
                prompt: prompt.trim(),
                steps: steps || 32,
                inferenceSteps: steps || 32,
                engine: options.engine || 'shap-e',
                guidanceScale: options.guidanceScale !== undefined ? options.guidanceScale : 15.0,
                format: options.format || 'obj',
            }
            const { data } = await api.post('/models/generate', payload)
            sessionStorage.setItem('gen_task_id_text', data.id)
            if (data.status === 'ready') {
                stopTextTimer()
                setText({
                    task: data,
                    previousTask: null,
                    state: 'done',
                    error: '',
                    elapsed: Math.round(data.generation_time || 0)
                })
            } else if (data.status === 'failed') {
                stopTextTimer()
                setText({ task: null, previousTask: prevTask, state: 'error', error: data.error_detail || 'Generation failed.' })
            } else {
                setText({ task: data, state: 'polling' })
                startWebSocket(data.id, setText, stopTextTimer, textWs)
            }
        } catch (e) {
            stopTextTimer()
            const errMsg = e.response?.data?.error || e.response?.data?.detail || e.response?.data?.error_detail || e.message || 'Failed to generate 3D model.'
            setText({ state: 'error', task: null, previousTask: prevTask, error: errMsg })
        }
    }, [startWebSocket, startTextTimer, stopTextTimer, stopTextWS, setText, text.task])

    const startImageGeneration = useCallback(async (file, steps, options = {}) => {
        stopImageWS()
        stopImageTimer()
        const previewUrl = URL.createObjectURL(file)
        setImage({ state: 'loading', task: null, error: '', elapsed: 0, file, preview: previewUrl, steps })
        startImageTimer()

        const form = new FormData()
        form.append('image', file)
        form.append('file', file)
        form.append('steps', steps || 32)
        form.append('inferenceSteps', steps || 32)
        form.append('engine', options.engine || 'shap-e')
        form.append('guidanceScale', options.guidanceScale !== undefined ? options.guidanceScale : 3.0)
        form.append('format', options.format || 'obj')
        if (options.name) form.append('name', options.name)

        try {
            const { data } = await api.post('/models/generate-from-image', form)
            sessionStorage.setItem('gen_task_id_image', data.id)
            if (data.status === 'ready') {
                stopImageTimer()
                setImage({
                    task: data,
                    state: 'done',
                    error: '',
                    elapsed: Math.round(data.generation_time || 0)
                })
            } else if (data.status === 'failed') {
                stopImageTimer()
                setImage({ task: null, state: 'error', error: data.error_detail || 'Image generation failed.' })
            } else {
                setImage({ task: data, state: 'polling' })
                startWebSocket(data.id, setImage, stopImageTimer, imageWs)
            }
        } catch (e) {
            stopImageTimer()
            const errMsg = e.response?.data?.error || e.response?.data?.detail || e.response?.data?.message || e.message || 'Failed to generate 3D model from image.'
            setImage({ state: 'error', error: errMsg })
        }
    }, [startWebSocket, startImageTimer, stopImageTimer, stopImageWS, setImage])

    const resetText = useCallback((preserveCurrentPrompt = false) => {
        stopTextWS()
        stopTextTimer()
        sessionStorage.removeItem('gen_task_id_text')
        setText_(prev => ({
            ...INITIAL_TEXT(),
            prompt: preserveCurrentPrompt ? prev.prompt : (localStorage.getItem('gen_text_prompt') || ''),
            steps: prev.steps || 32,
        }))
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
        textPreviousTask: text.previousTask,
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
