import { useState } from 'react'
import { Settings as SettingsIcon, Cpu, HardDrive, Wifi, Sliders, Save } from 'lucide-react'

export default function Settings() {
    const [settings, setSettings] = useState({
        gpuMode: 'local',
        storagePath: 'media/users/{id}/generated_models',
        yoloThreshold: 70,
        printerIp: '192.168.1.100',
        piIp: '192.168.1.101',
        autoSave: true,
        darkMode: true,
    })
    const [saved, setSaved] = useState(false)

    const handleSave = () => {
        setSaved(true)
        setTimeout(() => setSaved(false), 2000)
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
            {/* GPU Mode */}
            <Section icon={Cpu} title="Compute Settings">
                <Field label="GPU Mode">
                    <div className="flex gap-2">
                        {['local', 'cloud'].map(mode => (
                            <button key={mode} onClick={() => setSettings({ ...settings, gpuMode: mode })}
                                className={`flex-1 py-2.5 rounded-lg text-sm font-semibold border transition-all capitalize ${settings.gpuMode === mode ? 'bg-primary/20 border-primary/50 text-primary shadow-glow' : 'border-border text-muted hover:text-white'}`}>
                                {mode === 'local' ? '🖥 Local (RTX 2050)' : '☁️ Cloud'}
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
                <Field label="Model Storage Path" hint="Use {id} as a placeholder for user ID">
                    <input className="input" value={settings.storagePath} onChange={e => setSettings({ ...settings, storagePath: e.target.value })} />
                </Field>
            </Section>

            {/* YOLOv8 */}
            <Section icon={Sliders} title="AI Monitoring (YOLOv8)">
                <Field label={`Confidence Threshold: ${settings.yoloThreshold}%`} hint="Detections above this threshold will trigger an action">
                    <input type="range" min="50" max="99" value={settings.yoloThreshold}
                        onChange={e => setSettings({ ...settings, yoloThreshold: Number(e.target.value) })}
                        className="w-full accent-violet-500 mt-1" />
                    <div className="flex justify-between text-xs text-muted mt-1">
                        <span>50% (Sensitive)</span><span>99% (Strict)</span>
                    </div>
                </Field>
            </Section>

            {/* Printer / Raspberry Pi */}
            <Section icon={Wifi} title="Printer Network">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <Field label="Printer IP (OctoPrint)" hint="e.g. 192.168.1.100">
                        <input className="input" value={settings.printerIp} onChange={e => setSettings({ ...settings, printerIp: e.target.value })} />
                    </Field>
                    <Field label="Raspberry Pi IP" hint="Deployment target">
                        <input className="input" value={settings.piIp} onChange={e => setSettings({ ...settings, piIp: e.target.value })} />
                    </Field>
                </div>
                <div className="rounded-xl px-4 py-3 bg-secondary/5 border border-secondary/20 text-secondary text-sm">
                    💡 OctoPrint API integration is ready to be activated. Connect your Raspberry Pi and enter its IP above.
                </div>
            </Section>

            <div className="flex justify-end">
                <button onClick={handleSave} className="btn-primary">
                    <Save size={16} />
                    {saved ? '✓ Saved!' : 'Save Settings'}
                </button>
            </div>
        </div>
    )
}
