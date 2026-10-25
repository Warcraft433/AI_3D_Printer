import React, { useState } from 'react'
import { Usb, Wifi, Terminal, Flame, Home, Power, AlertOctagon, Play, Pause, RefreshCw, Send, Radio, CheckCircle, XCircle } from 'lucide-react'
import { usePrinter } from '../context/PrinterContext'
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts'

export default function PrinterControl() {
    const {
        connectionType,
        isConnected,
        isConnecting,
        statusMessage,
        temperatures,
        tempHistory,
        printState,
        terminalLogs,
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
    } = usePrinter()

    const [activeTab, setActiveTab] = useState('usb') // 'usb' | 'network' | 'simulated'
    const [baudRate, setBaudRate] = useState('115200')
    const [networkUrlInput, setNetworkUrlInput] = useState('http://192.168.1.100:5000')
    const [networkApiKeyInput, setNetworkApiKeyInput] = useState('')
    const [customGCode, setCustomGCode] = useState('')
    const [moveStep, setMoveStep] = useState(10) // mm

    const handleSendCustomGCode = (e) => {
        e.preventDefault()
        if (!customGCode.trim()) return
        sendGCode(customGCode.trim())
        setCustomGCode('')
    }

    const moveAxis = (axis, distance) => {
        sendGCode(`G91`) // Relative positioning
        sendGCode(`G1 ${axis}${distance} F3000`)
        sendGCode(`G90`) // Absolute positioning
    }

    return (
        <div className="space-y-6 pb-12">
            {/* Integration in Progress Banner */}
            <div className="p-4 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-300 flex items-center justify-between">
                <div className="flex items-center gap-3">
                    <span className="px-2.5 py-1 rounded-md bg-amber-500/20 text-amber-300 text-xs font-bold uppercase tracking-wider shrink-0">
                        Integration in Progress (Coming Soon)
                    </span>
                    <p className="text-xs text-amber-200/90">
                        Physical 3D printer hardware is labeled <strong>“Coming Soon”</strong>. A physical printer is not required for the current college project demonstration. The controls below are provided for protocol simulation and future printer integration.
                    </p>
                </div>
            </div>

            {/* Header */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                    <div className="flex items-center gap-2 mb-1">
                        <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-500/20 text-amber-300 border border-amber-500/30">
                            Hardware: Coming Soon
                        </span>
                        <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${isConnected ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' : 'bg-surface text-muted border border-border'}`}>
                            {isConnected ? `DEMO / VIRTUAL LINK (${connectionType.toUpperCase()})` : 'INTEGRATION IN PROGRESS'}
                        </span>
                    </div>
                    <h1 className="text-2xl md:text-3xl font-bold tracking-tight text-white">3D Printer Direct Control</h1>
                    <p className="text-sm text-muted">Direct USB Web Serial connection, Wi-Fi network link, and G-Code terminal interface</p>
                </div>

                {/* Emergency Stop Button */}
                {isConnected && (
                    <button
                        onClick={emergencyStop}
                        className="flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-red-600 to-rose-700 hover:from-red-500 hover:to-rose-600 text-white text-xs font-bold rounded-lg shadow-lg shadow-red-600/30 transition-all cursor-pointer"
                    >
                        <AlertOctagon className="w-4 h-4" />
                        EMERGENCY STOP (M112)
                    </button>
                )}
            </div>

            {/* Connection Selector Card */}
            <div className="bg-surface border border-border/60 rounded-2xl p-5 shadow-xl">
                {/* Tabs */}
                <div className="flex items-center gap-2 border-b border-border/40 pb-4 mb-4">
                    <button
                        onClick={() => setActiveTab('usb')}
                        className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition-all cursor-pointer ${activeTab === 'usb' ? 'bg-primary text-white shadow-glow' : 'text-muted hover:text-white hover:bg-white/5'}`}
                    >
                        <Usb className="w-4 h-4" />
                        Direct USB (Web Serial)
                    </button>
                    <button
                        onClick={() => setActiveTab('network')}
                        className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition-all cursor-pointer ${activeTab === 'network' ? 'bg-primary text-white shadow-glow' : 'text-muted hover:text-white hover:bg-white/5'}`}
                    >
                        <Wifi className="w-4 h-4" />
                        Network IP (Wi-Fi / Klipper)
                    </button>
                    <button
                        onClick={() => setActiveTab('simulated')}
                        className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition-all cursor-pointer ${activeTab === 'simulated' ? 'bg-primary text-white shadow-glow' : 'text-muted hover:text-white hover:bg-white/5'}`}
                    >
                        <Radio className="w-4 h-4" />
                        Virtual 3D Printer
                    </button>
                </div>

                {/* Tab 1: USB Serial */}
                {activeTab === 'usb' && (
                    <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                        <div className="space-y-1">
                            <h3 className="font-semibold text-white text-sm">Direct USB Cable Connection</h3>
                            <p className="text-xs text-muted">
                                Connect your Ender 3, Prusa, or Marlin printer directly to your laptop with a USB cable.
                            </p>
                            {!isWebSerialSupported && (
                                <p className="text-xs text-amber-400">
                                    ⚠️ Web Serial requires Google Chrome, Microsoft Edge, or Opera.
                                </p>
                            )}
                        </div>

                        <div className="flex items-center gap-3">
                            <select
                                value={baudRate}
                                onChange={(e) => setBaudRate(e.target.value)}
                                disabled={isConnected}
                                className="bg-surface/80 border border-border text-white text-xs rounded-lg px-3 py-2 outline-none focus:border-primary"
                            >
                                <option value="115200">115200 Baud (Standard)</option>
                                <option value="250000">250000 Baud (High-Speed)</option>
                                <option value="57600">57600 Baud</option>
                            </select>

                            {!isConnected ? (
                                <button
                                    onClick={() => connectUSB(baudRate)}
                                    disabled={isConnecting || !isWebSerialSupported}
                                    className="flex items-center gap-2 px-5 py-2 bg-gradient-to-r from-primary to-indigo-600 hover:from-primary/90 hover:to-indigo-500 text-white text-xs font-semibold rounded-lg shadow-glow transition-all cursor-pointer disabled:opacity-50"
                                >
                                    <Usb className="w-4 h-4" />
                                    {isConnecting ? 'Connecting...' : 'Connect USB Printer'}
                                </button>
                            ) : (
                                <button
                                    onClick={disconnectUSB}
                                    className="flex items-center gap-2 px-5 py-2 bg-rose-600 hover:bg-rose-500 text-white text-xs font-semibold rounded-lg shadow transition-all cursor-pointer"
                                >
                                    <XCircle className="w-4 h-4" />
                                    Disconnect
                                </button>
                            )}
                        </div>
                    </div>
                )}

                {/* Tab 2: Network IP */}
                {activeTab === 'network' && (
                    <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
                        <div className="space-y-3 flex-1">
                            <div>
                                <label className="block text-xs font-medium text-muted mb-1">Printer IP / URL</label>
                                <input
                                    type="text"
                                    value={networkUrlInput}
                                    onChange={(e) => setNetworkUrlInput(e.target.value)}
                                    placeholder="http://192.168.1.100:5000 or http://klipper.local"
                                    className="w-full bg-surface/80 border border-border text-white text-xs rounded-lg px-3 py-2 outline-none focus:border-primary"
                                />
                            </div>
                        </div>

                        <button
                            onClick={() => connectNetwork(networkUrlInput, networkApiKeyInput)}
                            disabled={isConnecting}
                            className="flex items-center gap-2 px-5 py-2 bg-gradient-to-r from-primary to-indigo-600 hover:from-primary/90 hover:to-indigo-500 text-white text-xs font-semibold rounded-lg shadow-glow transition-all cursor-pointer"
                        >
                            <Wifi className="w-4 h-4" />
                            {isConnecting ? 'Connecting...' : 'Connect Network Printer'}
                        </button>
                    </div>
                )}

                {/* Tab 3: Virtual Simulated */}
                {activeTab === 'simulated' && (
                    <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                        <div className="space-y-1">
                            <h3 className="font-semibold text-white text-sm">Virtual 3D Printer Simulation</h3>
                            <p className="text-xs text-muted">Test print jobs, temperatures, and defect detection without physical hardware.</p>
                        </div>

                        <button
                            onClick={connectSimulated}
                            className="flex items-center gap-2 px-5 py-2 bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-400 hover:to-teal-500 text-white text-xs font-semibold rounded-lg shadow transition-all cursor-pointer"
                        >
                            <CheckCircle className="w-4 h-4" />
                            Start Simulation Mode
                        </button>
                    </div>
                )}
            </div>

            {/* Main Controls & Live Terminal Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Left Column: Temperature & Quick Movement */}
                <div className="space-y-6">
                    {/* Live Temperature Cards */}
                    <div className="bg-surface border border-border/60 rounded-2xl p-5 shadow-xl space-y-4">
                        <div className="flex items-center justify-between pb-3 border-b border-border/40">
                            <div className="flex items-center gap-2">
                                <Flame className="w-4 h-4 text-amber-400" />
                                <h3 className="font-semibold text-white text-sm">Thermal Status</h3>
                            </div>
                            <span className="text-[11px] text-muted">{isConnected ? 'Live M105 Polling' : 'Offline'}</span>
                        </div>

                        <div className="grid grid-cols-2 gap-3">
                            <div className="bg-surface/60 border border-border/40 rounded-xl p-3">
                                <span className="text-[11px] text-muted block mb-1">Hotend (Nozzle)</span>
                                <div className="text-xl font-bold text-white">
                                    {temperatures.nozzle.toFixed(1)}°C
                                </div>
                                <span className="text-[10px] text-muted">Target: {temperatures.targetNozzle}°C</span>
                            </div>

                            <div className="bg-surface/60 border border-border/40 rounded-xl p-3">
                                <span className="text-[11px] text-muted block mb-1">Heated Bed</span>
                                <div className="text-xl font-bold text-white">
                                    {temperatures.bed.toFixed(1)}°C
                                </div>
                                <span className="text-[10px] text-muted">Target: {temperatures.targetBed}°C</span>
                            </div>
                        </div>

                        {/* Quick Temp Preset Buttons */}
                        <div className="flex items-center gap-2 pt-2">
                            <button
                                onClick={heatPLA}
                                className="flex-1 py-1.5 bg-amber-500/20 hover:bg-amber-500/30 text-amber-300 border border-amber-500/40 rounded-lg text-xs font-semibold transition-all cursor-pointer"
                            >
                                Preheat PLA (210/60)
                            </button>
                            <button
                                onClick={cooldown}
                                className="flex-1 py-1.5 bg-slate-700/50 hover:bg-slate-700 text-slate-300 border border-border rounded-lg text-xs font-semibold transition-all cursor-pointer"
                            >
                                Cooldown (0/0)
                            </button>
                        </div>
                    </div>

                    {/* Jog / Movement Controls */}
                    <div className="bg-surface border border-border/60 rounded-2xl p-5 shadow-xl space-y-4">
                        <div className="flex items-center justify-between pb-3 border-b border-border/40">
                            <div className="flex items-center gap-2">
                                <Home className="w-4 h-4 text-primary" />
                                <h3 className="font-semibold text-white text-sm">Motion Controls</h3>
                            </div>
                            {/* Step selector */}
                            <div className="flex items-center gap-1">
                                {[0.1, 1, 10, 50].map(step => (
                                    <button
                                        key={step}
                                        onClick={() => setMoveStep(step)}
                                        className={`px-2 py-0.5 text-[10px] rounded font-semibold transition-all cursor-pointer ${moveStep === step ? 'bg-primary text-white' : 'bg-surface/80 text-muted hover:text-white'}`}
                                    >
                                        {step}mm
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* XY D-Pad & Z Controls */}
                        <div className="flex items-center justify-around py-2">
                            {/* XY Pad */}
                            <div className="grid grid-cols-3 gap-1.5 w-28">
                                <div />
                                <button onClick={() => moveAxis('Y', moveStep)} className="p-2 bg-surface/80 hover:bg-primary/20 border border-border rounded text-xs font-bold text-white text-center cursor-pointer">+Y</button>
                                <div />
                                <button onClick={() => moveAxis('X', -moveStep)} className="p-2 bg-surface/80 hover:bg-primary/20 border border-border rounded text-xs font-bold text-white text-center cursor-pointer">-X</button>
                                <button onClick={homeAll} title="Home All Axes (G28)" className="p-2 bg-primary/20 hover:bg-primary/40 border border-primary/40 rounded text-xs font-bold text-primary text-center cursor-pointer">⌂</button>
                                <button onClick={() => moveAxis('X', moveStep)} className="p-2 bg-surface/80 hover:bg-primary/20 border border-border rounded text-xs font-bold text-white text-center cursor-pointer">+X</button>
                                <div />
                                <button onClick={() => moveAxis('Y', -moveStep)} className="p-2 bg-surface/80 hover:bg-primary/20 border border-border rounded text-xs font-bold text-white text-center cursor-pointer">-Y</button>
                                <div />
                            </div>

                            {/* Z Axis Column */}
                            <div className="flex flex-col gap-2">
                                <button onClick={() => moveAxis('Z', moveStep)} className="px-3 py-1.5 bg-surface/80 hover:bg-primary/20 border border-border rounded text-xs font-bold text-white cursor-pointer">+Z</button>
                                <button onClick={() => moveAxis('Z', -moveStep)} className="px-3 py-1.5 bg-surface/80 hover:bg-primary/20 border border-border rounded text-xs font-bold text-white cursor-pointer">-Z</button>
                            </div>
                        </div>

                        <div className="flex items-center gap-2 pt-2 border-t border-border/40">
                            <button onClick={disableMotors} className="flex-1 py-1.5 bg-surface hover:bg-white/5 text-muted hover:text-white border border-border rounded-lg text-xs transition-all cursor-pointer">
                                Disable Motors (M84)
                            </button>
                        </div>
                    </div>
                </div>

                {/* Right Column: Live G-Code Terminal & Telemetry Graph */}
                <div className="lg:col-span-2 space-y-6">
                    {/* Live Temperature Telemetry Chart */}
                    <div className="bg-surface border border-border/60 rounded-2xl p-5 shadow-xl">
                        <h3 className="font-semibold text-white text-sm mb-3">Live Temperature Curve</h3>
                        <div className="h-44 w-full">
                            <ResponsiveContainer width="100%" height="100%">
                                <LineChart data={tempHistory}>
                                    <XAxis dataKey="time" stroke="#64748b" fontSize={10} />
                                    <YAxis stroke="#64748b" fontSize={10} domain={[0, 260]} />
                                    <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: 8, fontSize: 11 }} />
                                    <Line type="monotone" dataKey="nozzle" stroke="#f59e0b" name="Nozzle °C" strokeWidth={2} dot={false} isAnimationActive={false} />
                                    <Line type="monotone" dataKey="bed" stroke="#06b6d4" name="Bed °C" strokeWidth={2} dot={false} isAnimationActive={false} />
                                </LineChart>
                            </ResponsiveContainer>
                        </div>
                    </div>

                    {/* Interactive G-Code Terminal */}
                    <div className="bg-surface border border-border/60 rounded-2xl p-5 shadow-xl flex flex-col h-[320px]">
                        <div className="flex items-center justify-between pb-3 border-b border-border/40">
                            <div className="flex items-center gap-2">
                                <Terminal className="w-4 h-4 text-emerald-400" />
                                <h3 className="font-semibold text-white text-sm">G-Code Serial Terminal</h3>
                            </div>
                            <span className="text-[11px] text-muted">Baud: {baudRate}</span>
                        </div>

                        {/* Scrollable Terminal Box */}
                        <div className="flex-1 bg-slate-950 rounded-xl p-3 my-3 overflow-y-auto font-mono text-xs space-y-1 border border-white/5">
                            {terminalLogs.map((log, idx) => (
                                <div key={idx} className="flex items-start gap-2">
                                    <span className="text-slate-600 select-none">{log.time}</span>
                                    <span className={
                                        log.type === 'tx' ? 'text-cyan-400 font-semibold' :
                                        log.type === 'rx' ? 'text-slate-300' :
                                        log.type === 'success' ? 'text-emerald-400' :
                                        log.type === 'error' ? 'text-rose-400' :
                                        'text-slate-400'
                                    }>
                                        {log.text}
                                    </span>
                                </div>
                            ))}
                        </div>

                        {/* Send Command Form */}
                        <form onSubmit={handleSendCustomGCode} className="flex items-center gap-2">
                            <input
                                type="text"
                                value={customGCode}
                                onChange={(e) => setCustomGCode(e.target.value)}
                                placeholder="Enter G-Code command (e.g. M105, G28, G1 X50 Y50)..."
                                className="flex-1 bg-slate-950 border border-border text-white font-mono text-xs rounded-lg px-3 py-2 outline-none focus:border-primary"
                            />
                            <button
                                type="submit"
                                disabled={!isConnected}
                                className="flex items-center gap-1.5 px-4 py-2 bg-primary hover:bg-primary/90 text-white text-xs font-semibold rounded-lg shadow-glow transition-all cursor-pointer disabled:opacity-40"
                            >
                                <Send className="w-3.5 h-3.5" />
                                Send
                            </button>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    )
}
