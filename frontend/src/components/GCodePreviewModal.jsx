import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
    X, Download, AlertTriangle, Loader2, Layers, 
    ExternalLink, CheckCircle2, FileCode 
} from 'lucide-react';
import api, { getAuthenticatedUrl } from '../services/api';
import { parseGCode } from '../utils/gcodeParser';
import GCodeViewer from './GCodeViewer';

export default function GCodePreviewModal({ jobId, isOpen, onClose, initialTitle }) {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [parsedData, setParsedData] = useState(null);
    const [jobDetails, setJobDetails] = useState(null);

    useEffect(() => {
        if (!isOpen || !jobId) {
            setParsedData(null);
            setError(null);
            setLoading(false);
            return;
        }

        let isMounted = true;
        setLoading(true);
        setError(null);

        async function fetchAndParseGCode() {
            try {
                // 1. Fetch job metadata
                let jobMeta = null;
                try {
                    const jobRes = await api.get(`/print-jobs/${jobId}/`);
                    jobMeta = jobRes.data;
                    if (isMounted) setJobDetails(jobMeta);
                } catch (metaErr) {
                    console.warn('Could not fetch print job details, proceeding with direct G-code fetch:', metaErr);
                }

                // 2. Fetch genuine G-code text
                // Try dedicated /gcode endpoint first, fallback to /download-gcode
                let gcodeText = '';
                try {
                    const gcodeRes = await api.get(`/print-jobs/${jobId}/gcode/`, { responseType: 'text' });
                    gcodeText = gcodeRes.data;
                } catch (gcodeErr) {
                    // Fallback to download endpoint
                    const dlRes = await api.get(`/print-jobs/${jobId}/download-gcode/`, { responseType: 'text' });
                    gcodeText = dlRes.data;
                }

                if (!isMounted) return;

                if (!gcodeText || typeof gcodeText !== 'string' || gcodeText.trim().length === 0) {
                    throw new Error('Retrieved G-code file is empty.');
                }

                // 3. Parse G-code
                const parsed = parseGCode(gcodeText);
                if (isMounted) {
                    setParsedData(parsed);
                    setLoading(false);
                }
            } catch (err) {
                console.error('Failed to load or parse G-code:', err);
                if (isMounted) {
                    const msg = err.response?.data?.message || err.response?.data?.error || err.message || 'Failed to parse G-code.';
                    setError(msg);
                    setLoading(false);
                }
            }
        }

        fetchAndParseGCode();

        return () => {
            isMounted = false;
        };
    }, [isOpen, jobId]);

    if (!isOpen) return null;

    const displayTitle = initialTitle || jobDetails?.model_prompt || `Print Job #${jobId}`;
    const gcodeDownloadUrl = jobDetails?.gcode_url ? getAuthenticatedUrl(jobDetails.gcode_url) : getAuthenticatedUrl(`/api/print-jobs/${jobId}/download-gcode/`);

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-3 md:p-6 bg-slate-950/80 backdrop-blur-md animate-in fade-in duration-200">
            <div className="flex flex-col w-full max-w-6xl h-[92vh] bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl overflow-hidden relative">
                
                {/* Modal Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-800 bg-slate-900/90 backdrop-blur">
                    <div className="flex items-center gap-3 min-w-0">
                        <div className="p-2.5 rounded-xl bg-cyan-500/10 border border-cyan-500/20 text-cyan-400 flex-shrink-0">
                            <Layers size={22} />
                        </div>
                        <div className="min-w-0">
                            <div className="flex items-center gap-2 flex-wrap">
                                <h2 className="text-lg font-bold text-white truncate">{displayTitle}</h2>
                                <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
                                    G-Code Preview
                                </span>
                                {jobDetails?.status && (
                                    <span className="text-[11px] font-medium px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                                        Status: {jobDetails.status}
                                    </span>
                                )}
                            </div>
                            <p className="text-xs text-slate-400 truncate mt-0.5">
                                Inspect real sliced toolhead paths, layer boundaries, and material usage before sending to printer.
                            </p>
                        </div>
                    </div>

                    {/* Actions */}
                    <div className="flex items-center gap-2 flex-shrink-0">
                        <a
                            href={gcodeDownloadUrl}
                            download
                            className="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-cyan-400 border border-cyan-500/20 hover:border-cyan-500/40 transition-colors hidden sm:flex items-center gap-1.5 text-xs font-medium"
                            title="Download G-Code File"
                        >
                            <Download size={14} />
                            <span>Download</span>
                        </a>
                        <button
                            onClick={() => {
                                onClose();
                                navigate(`/gcode-preview/${jobId}`);
                            }}
                            className="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white border border-slate-700 transition-colors hidden sm:flex items-center gap-1.5 text-xs font-medium"
                            title="Open in Dedicated Full Page View"
                        >
                            <ExternalLink size={14} />
                            <span>Full Page</span>
                        </button>
                        <button
                            onClick={onClose}
                            className="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white border border-slate-700 transition-colors"
                            title="Close Preview"
                        >
                            <X size={18} />
                        </button>
                    </div>
                </div>

                {/* Modal Content */}
                <div className="flex-1 min-h-0 relative bg-slate-950 flex flex-col justify-center items-center overflow-hidden">
                    {loading && (
                        <div className="flex flex-col items-center justify-center gap-3 p-8 text-center animate-in fade-in duration-300">
                            <div className="relative">
                                <Loader2 size={44} className="animate-spin text-cyan-400" />
                                <FileCode size={20} className="absolute inset-0 m-auto text-cyan-200" />
                            </div>
                            <div className="space-y-1">
                                <h4 className="text-base font-semibold text-slate-200">Loading Sliced G-Code</h4>
                                <p className="text-xs text-slate-400 max-w-sm">
                                    Streaming genuine sliced toolpaths from storage and generating layer-by-layer 3D trajectories...
                                </p>
                            </div>
                        </div>
                    )}

                    {error && !loading && (
                        <div className="flex flex-col items-center justify-center p-8 max-w-md text-center bg-red-950/20 border border-red-500/20 rounded-2xl animate-in zoom-in-95 duration-200">
                            <div className="w-14 h-14 rounded-full bg-red-500/10 border border-red-500/20 flex items-center justify-center mb-3 text-red-400">
                                <AlertTriangle size={28} />
                            </div>
                            <h4 className="text-base font-bold text-red-400 mb-1">Preview Unavailable</h4>
                            <p className="text-xs text-slate-300 mb-4">{error}</p>
                            <p className="text-[11px] text-slate-500 mb-4">
                                Ensure the 3D model was sliced successfully and that genuine G-code is available for this job.
                            </p>
                            <button
                                onClick={onClose}
                                className="px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold border border-slate-700 transition-colors"
                            >
                                Dismiss
                            </button>
                        </div>
                    )}

                    {parsedData && !loading && !error && (
                        <div className="w-full h-full flex flex-col min-h-0">
                            <GCodeViewer parsedData={parsedData} jobTitle={displayTitle} />
                        </div>
                    )}
                </div>

            </div>
        </div>
    );
}
