import { useState, useEffect } from 'react'
import { Settings as SettingsIcon, Cpu, HardDrive, Wifi, Sliders, Save, Usb, Camera } from 'lucide-react'
import api from '../services/api'

export default function Settings() {
    const [settings, setSettings] = useState({
        gpuMode: 'local',
        storagePath: 'media/users/{id}/generated_models',
        yoloThreshold: 70,
        printerIp: '127.0.0.1:5000',
        baudRate: 115200,
        cameraMode: 'user',
        connectionType: 'web_serial',
        autoSave: true,
        darkMode: true,
    })
    const [saved, setSaved] = useState(false)
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        const loadSettings = async () => {
            try {
                const res = await api.get('/settings/')
                if (res.data) {
                    setSettings(prev => ({ ...prev, ...res.data }))
                }
            } catch {
                const local = localStorage.getItem('printguard_settings')
                if (local) {
                    try {
                        setSettings(prev => ({ ...prev, ...JSON.parse(local) }))
                    } catch (e) {}
                }
            } finally {
                setLoading(false)
            }
        }
        loadSettings()
    }, [])

    const handleSave = async () => {
        try {
            await api.post('/settings/', settings)
        } catch {
            // gracefully fallback to local storage
        }
        localStorage.setItem('printguard_settings', JSON.stringify(settings))
        setSaved(true)
        setTimeout(() => setSaved(false), 2500)
    }

    const Section = ({ icon: Icon, title, children }) => (
        <div className="glass-card space-y-4">
            <h2 className="text-white font-semibold flex items-center gap-2">
                <Icon size={18} className="text-primary" /> {title}
            </h2>
            {children}
        </div>
    )

    const Field = ({ label, hint, children }) => (
        <div>
            <label className="label">{label}</label>
            {children}
            {hint && <p className="text-xs text-muted mt-1">{hint}</p>}
        </div>
    )

    const Toggle = ({ val, onChange }) => (
        <button onClick={() => onChange(!val)} className={`relative w-12 h-6 rounded-full transition-all ${val ? 'bg-primary' : 'bg-border'}`}>
            <span className={`absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-all ${val ? 'left-6' : 'left-0.5'}`} />
        </button>
    )

    return (
        <div className="page-enter space-y-6 max-w-3xl">
            {/* Header info */}
            <div className="flex items-center justify-between pb-2 border-b border-border/40">
                <div>
                    <h1 className="text-2xl font-bold text-white flex items-center gap-2">
                        <SettingsIcon className="text-primary" size={24} /> System Configuration
                    </h1>
                    <p className="text-xs text-muted mt-1">Configure AI detection threshold, USB Web Serial, and media storage</p>
                </div>
                {saved && <span className="text-xs text-emerald-400 font-medium px-2 py-1 rounded bg-emerald-500/10 border border-emerald-500/30">Settings Saved</span>}
            </div>

            {/* Hardware & Direct Printer Control */}
            <Section icon={Usb} title="Hardware & Printer Interface">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <Field label="Primary Hardware Link" hint="Direct USB connection via browser Web Serial API">
                        <select
                            className="input"
                            value={settings.connectionType}
                            onChange={e => setSettings({ ...settings, connectionType: e.target.value })}
                        >
                            <option value="web_serial">Direct USB Web Serial (navigator.serial)</option>
                            <option value="octoprint">Network OctoPrint / Virtual Server</option>
                        </select>
                    </Field>
                    <Field label="Serial Baud Rate" hint="Standard 3D printer serial baud rate">
                        <select
                            className="input"
                            value={settings.baudRate}
                            onChange={e => setSettings({ ...settings, baudRate: Number(e.target.value) })}
                        >
                            <option value={115200}>115200 baud (Standard Ender/Prusa)</option>
                            <option value={250000}>250000 baud (High speed)</option>
                            <option value={57600}>57600 baud (Legacy)</option>
                        </select>
                    </Field>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <Field label="OctoPrint / Virtual Server IP" hint="Used when Network OctoPrint mode is enabled">
                        <input className="input" value={settings.printerIp} onChange={e => setSettings({ ...settings, printerIp: e.target.value })} />
                    </Field>
                    <Field label="AI Monitoring Camera" hint="Direct browser camera capture">
                        <select
                            className="input"
                            value={settings.cameraMode}
                            onChange={e => setSettings({ ...settings, cameraMode: e.target.value })}
                        >
                            <option value="user">Front / Integrated Webcam</option>
                            <option value="environment">External / USB Bed Camera</option>
                        </select>
                    </Field>
                </div>

                <div className="rounded-xl px-4 py-3 bg-secondary/5 border border-secondary/20 text-secondary text-sm">
                    💡 <strong>Browser-Native Hardware:</strong> PrintGuard AI directly communicates with your 3D printer via USB Web Serial (<code>navigator.serial</code>) and stream captures via WebCam (<code>navigator.mediaDevices</code>). External Raspberry Pi is not required.
                </div>
            </Section>

            {/* AI Monitoring */}
            <Section icon={Sliders} title="AI Monitoring (YOLOv8 nano)">
                <Field label={`Confidence Threshold: ${settings.yoloThreshold}%`} hint="Detections above this confidence threshold trigger automatic safety pause">
                    <input
                        type="range"
                        min="50"
                        max="99"
                        value={settings.yoloThreshold}
                        onChange={e => setSettings({ ...settings, yoloThreshold: Number(e.target.value) })}
                        className="w-full accent-violet-500 mt-1"
                    />
                    <div className="flex justify-between text-xs text-muted mt-1">
                        <span>50% (High Sensitivity)</span>
                        <span>75% (Balanced)</span>
                        <span>99% (Strict Spaghetti Detection)</span>
                    </div>
                </Field>
            </Section>

            {/* Compute & Generative 3D */}
            <Section icon={Cpu} title="Compute Settings">
                <Field label="3D Generation Compute Mode">
                    <div className="flex gap-2">
                        {['local', 'cloud'].map(mode => (
                            <button
                                key={mode}
                                onClick={() => setSettings({ ...settings, gpuMode: mode })}
                                className={`flex-1 py-2.5 rounded-lg text-sm font-semibold border transition-all capitalize ${settings.gpuMode === mode ? 'bg-primary/20 border-primary/50 text-primary shadow-glow' : 'border-border text-muted hover:text-white'}`}
                            >
                                {mode === 'local' ? '🖥 Local GPU (CUDA / PyTorch 3.11)' : '☁️ Cloud Subprocess'}
                            </button>
                        ))}
                    </div>
                </Field>
                <Field label="Auto-save generated models to database">
                    <div className="flex items-center gap-3 mt-1">
                        <Toggle val={settings.autoSave} onChange={v => setSettings({ ...settings, autoSave: v })} />
                        <span className="text-sm text-slate-300">{settings.autoSave ? 'Enabled' : 'Disabled'}</span>
                    </div>
                </Field>
            </Section>

            {/* Storage */}
            <Section icon={HardDrive} title="File Storage">
                <Field label="Model Storage Directory" hint="Storage location for sliced G-Code and 3D OBJ meshes">
                    <input className="input" value={settings.storagePath} onChange={e => setSettings({ ...settings, storagePath: e.target.value })} />
                </Field>
            </Section>

            <div className="flex justify-end pt-2">
                <button onClick={handleSave} className="btn-primary">
                    <Save size={16} />
                    {saved ? '✓ Saved!' : 'Save Settings'}
                </button>
            </div>
        </div>
    )
}
