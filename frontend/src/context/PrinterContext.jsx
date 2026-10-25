import React, { createContext, useContext, useState, useEffect, useRef } from 'react'

const PrinterContext = createContext(null)

export function PrinterProvider({ children }) {
    // Connection State
    const [connectionType, setConnectionType] = useState('usb') // 'usb' | 'network' | 'simulated'
    const [isConnected, setIsConnected] = useState(false)
    const [isConnecting, setIsConnecting] = useState(false)
    const [statusMessage, setStatusMessage] = useState('Printer Disconnected')

    // Telemetry State
    const [temperatures, setTemperatures] = useState({
        nozzle: 0,
        targetNozzle: 0,
        bed: 0,
        targetBed: 0,
    })
    const [tempHistory, setTempHistory] = useState([])
    const [printProgress, setPrintProgress] = useState(0)
    const [printState, setPrintState] = useState('Idle') // 'Idle' | 'Printing' | 'Paused' | 'Error'
    
    // G-Code Terminal Logs
    const [terminalLogs, setTerminalLogs] = useState([
        { time: new Date().toLocaleTimeString(), text: 'System ready. Connect via USB or Network IP.' }
    ])

    // Network IP Settings
    const [networkUrl, setNetworkUrl] = useState('http://192.168.1.100:5000')
    const [networkApiKey, setNetworkApiKey] = useState('')

    // Web Serial Refs
    const portRef = useRef(null)
    const readerRef = useRef(null)
    const writerRef = useRef(null)
    const keepReadingRef = useRef(false)
    const isPrintingRef = useRef(false)

    const logTerminal = (text, type = 'info') => {
        setTerminalLogs(prev => [
            ...prev.slice(-150), // keep last 150 lines
            { time: new Date().toLocaleTimeString(), text, type }
        ])
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Web Serial API (USB Cable Direct to Printer)
    // ──────────────────────────────────────────────────────────────────────────
    const isWebSerialSupported = typeof navigator !== 'undefined' && 'serial' in navigator

    const connectUSB = async (baudRate = 115200) => {
        if (!isWebSerialSupported) {
            logTerminal('Web Serial API not supported in this browser. Use Chrome, Edge, or Opera.', 'error')
            alert('Web Serial API is not supported in this browser. Please use Chrome or Edge for direct USB printing.')
            return false
        }

        setIsConnecting(true)
        try {
            logTerminal('Requesting USB Serial Port...', 'info')
            const port = await navigator.serial.requestPort()
            await port.open({ baudRate: parseInt(baudRate) })
            
            portRef.current = port
            keepReadingRef.current = true
            setIsConnected(true)
            setConnectionType('usb')
            setPrintState('Connected (USB)')
            setStatusMessage('Connected via USB Serial')
            logTerminal(`Serial port opened successfully at ${baudRate} baud.`, 'success')

            // Start background reader loop
            startSerialReadLoop(port)

            // Initial query
            setTimeout(() => {
                sendGCode('M105') // Request temperatures
                sendGCode('M115') // Firmware info
            }, 1500)

            return true
        } catch (err) {
            logTerminal(`USB Connection Error: ${err.message}`, 'error')
            setIsConnected(false)
            return false
        } finally {
            setIsConnecting(false)
        }
    }

    const startSerialReadLoop = async (port) => {
        const textDecoder = new TextDecoderStream()
        const readableStreamClosed = port.readable.pipeTo(textDecoder.writable)
        const reader = textDecoder.readable.getReader()
        readerRef.current = reader

        let buffer = ''
        try {
            while (keepReadingRef.current) {
                const { value, done } = await reader.read()
                if (done) break
                if (value) {
                    buffer += value
                    const lines = buffer.split('\n')
                    buffer = lines.pop() // keep incomplete last line

                    for (const line of lines) {
                        const trimmed = line.trim()
                        if (!trimmed) continue
                        logTerminal(`← ${trimmed}`, 'rx')
                        parseGCodeResponse(trimmed)
                    }
                }
            }
        } catch (err) {
            logTerminal(`Serial Reader error: ${err.message}`, 'error')
        } finally {
            reader.releaseLock()
        }
    }

    const parseGCodeResponse = (line) => {
        // Parse standard Marlin/RepRap temperature line: "ok T:215.2 /220.0 B:60.1 /60.0"
        const tMatch = line.match(/T:(\d+\.?\d*)\s*\/(\d+\.?\d*)/i)
        const bMatch = line.match(/B:(\d+\.?\d*)\s*\/(\d+\.?\d*)/i)

        if (tMatch || bMatch) {
            const nozzle = tMatch ? parseFloat(tMatch[1]) : temperatures.nozzle
            const targetNozzle = tMatch ? parseFloat(tMatch[2]) : temperatures.targetNozzle
            const bed = bMatch ? parseFloat(bMatch[1]) : temperatures.bed
            const targetBed = bMatch ? parseFloat(bMatch[2]) : temperatures.targetBed

            setTemperatures({ nozzle, targetNozzle, bed, targetBed })
            
            setTempHistory(prev => [
                ...prev.slice(-30),
                {
                    time: new Date().toLocaleTimeString().split(' ')[0],
                    nozzle,
                    targetNozzle,
                    bed,
                    targetBed
                }
            ])
        }
    }

    const sendGCode = async (cmd) => {
        if (!portRef.current || !portRef.current.writable) {
            logTerminal(`Cannot send: Serial port not open. (Command: ${cmd})`, 'error')
            return false
        }

        try {
            const encoder = new TextEncoder()
            const writer = portRef.current.writable.getWriter()
            logTerminal(`→ ${cmd}`, 'tx')
            await writer.write(encoder.encode(cmd + '\n'))
            writer.releaseLock()
            return true
        } catch (err) {
            logTerminal(`Send Error: ${err.message}`, 'error')
            return false
        }
    }

    const disconnectUSB = async () => {
        keepReadingRef.current = false
        if (readerRef.current) {
            try { await readerRef.current.cancel() } catch (_) {}
        }
        if (portRef.current) {
            try { await portRef.current.close() } catch (_) {}
            portRef.current = null
        }
        setIsConnected(false)
        setPrintState('Idle')
        setStatusMessage('Printer Disconnected')
        logTerminal('Disconnected from USB Serial port.', 'info')
    }

    // Temperature polling timer for USB
    useEffect(() => {
        if (isConnected && connectionType === 'usb') {
            const interval = setInterval(() => {
                sendGCode('M105') // Poll temperature
            }, 3000)
            return () => clearInterval(interval)
        }
    }, [isConnected, connectionType])

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Network IP Mode (Klipper / Moonraker / OctoPrint)
    // ──────────────────────────────────────────────────────────────────────────
    const connectNetwork = async (url, apiKey = '') => {
        setIsConnecting(true)
        logTerminal(`Connecting to Network Printer at ${url}...`, 'info')
        try {
            const cleanUrl = url.replace(/\/$/, '')
            const res = await fetch(`${cleanUrl}/api/printer`, {
                headers: apiKey ? { 'X-Api-Key': apiKey } : {}
            })
            if (res.ok) {
                setNetworkUrl(cleanUrl)
                setNetworkApiKey(apiKey)
                setIsConnected(true)
                setConnectionType('network')
                setPrintState('Connected (Wi-Fi)')
                setStatusMessage(`Connected to ${cleanUrl}`)
                logTerminal(`Successfully connected to network printer at ${cleanUrl}`, 'success')
                return true
            } else {
                throw new Error(`HTTP Status ${res.status}`)
            }
        } catch (err) {
            logTerminal(`Network Connection Failed: ${err.message}`, 'error')
            return false
        } finally {
            setIsConnecting(false)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Built-In Simulated Printer Mode (Offline / Testing)
    // ──────────────────────────────────────────────────────────────────────────
    const connectSimulated = () => {
        setIsConnected(true)
        setConnectionType('simulated')
        setPrintState('Connected (Simulated)')
        setStatusMessage('Virtual 3D Printer Active')
        setTemperatures({ nozzle: 215, targetNozzle: 220, bed: 60, targetBed: 60 })
        logTerminal('Virtual 3D Printer initialized for offline testing.', 'success')
    }

    // Quick Action Helpers
    const homeAll = () => sendGCode('G28')
    const heatPLA = () => {
        sendGCode('M104 S210')
        sendGCode('M140 S60')
        logTerminal('Heating for PLA (Nozzle: 210°C, Bed: 60°C)', 'info')
    }
    const cooldown = () => {
        sendGCode('M104 S0')
        sendGCode('M140 S0')
        logTerminal('Cooldown commanded (Nozzle & Bed off)', 'info')
    }
    const disableMotors = () => sendGCode('M84')
    const emergencyStop = () => sendGCode('M112')
    const pausePrint = () => {
        sendGCode('M25') // Standard SD pause or host pause
        setPrintState('Paused')
        logTerminal('PAUSE command sent (M25)', 'error')
    }
    const resumePrint = () => {
        sendGCode('M24')
        setPrintState('Printing')
        logTerminal('RESUME command sent (M24)', 'success')
    }

    return (
        <PrinterContext.Provider value={{
            connectionType,
            isConnected,
            isConnecting,
            statusMessage,
            temperatures,
            tempHistory,
            printProgress,
            printState,
            terminalLogs,
            networkUrl,
            networkApiKey,
            isWebSerialSupported,
            connectUSB,
            disconnectUSB,
            connectNetwork,
            connectSimulated,
            sendGCode,
            homeAll,
            heatPLA,
            cooldown,
            disableMotors,
            emergencyStop,
            pausePrint,
            resumePrint,
            setPrintProgress,
            setPrintState,
        }}>
            {children}
        </PrinterContext.Provider>
    )
}

export function usePrinter() {
    const context = useContext(PrinterContext)
    if (!context) {
        throw new Error('usePrinter must be used within a PrinterProvider')
    }
    return context
}
