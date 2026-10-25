import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { 
    Layers, Download, ArrowLeft, AlertTriangle, Loader2, 
    Printer, FileCode, CheckCircle2, ChevronRight 
} from 'lucide-react';
import api, { getAuthenticatedUrl } from '../services/api';
import { parseGCode } from '../utils/gcodeParser';
import GCodeViewer from '../components/GCodeViewer';

export default function GCodePreview() {
    const { jobId } = useParams();
    const navigate = useNavigate();

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [jobDetails, setJobDetails] = useState(null);
    const [parsedData, setParsedData] = useState(null);

    useEffect(() => {
        if (!jobId) {
            setError('No job ID provided');
            setLoading(false);
            return;
        }

        let isMounted = true;
        setLoading(true);
        setError(null);

        async function loadJobAndGCode() {
            try {
                // 1. Fetch Job details
                let jobData = null;
                try {
                    const jobRes = await api.get(`/print-jobs/${jobId}/`);
                    jobData = jobRes.data;
                    if (isMounted) setJobDetails(jobData);
                } catch (metaErr) {
                    console.warn('Could not fetch print job details:', metaErr);
                }

                // 2. Fetch G-code
                let gcodeText = '';
                try {
                    const gcodeRes = await api.get(`/print-jobs/${jobId}/gcode/`, { responseType: 'text' });
                    gcodeText = gcodeRes.data;
                } catch (err) {
                    const dlRes = await api.get(`/print-jobs/${jobId}/download-gcode/`, { responseType: 'text' });
                    gcodeText = dlRes.data;
                }

                if (!isMounted) return;

                if (!gcodeText || typeof gcodeText !== 'string' || gcodeText.trim().length === 0) {
                    throw new Error('G-code file is empty or missing from storage.');
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

        loadJobAndGCode();

        return () => {
            isMounted = false;
        };
    }, [jobId]);

    const title = jobDetails?.model_prompt || `Print Job #${jobId}`;
    const gcodeUrl = jobDetails?.gcode_url ? getAuthenticatedUrl(jobDetails.gcode_url) : getAuthenticatedUrl(`/api/print-jobs/${jobId}/download-gcode/`);

    return (
        <div className="flex flex-col h-[calc(100vh-5rem)] space-y-4 page-enter">
            {/* Header & Breadcrumb */}
            <div className="flex flex-wrap items-center justify-between gap-4">
                <div className="space-y-1">
                    <div className="flex items-center gap-2 text-xs text-muted">
                        <Link to="/" className="hover:text-white transition-colors">Dashboard</Link>
                        <ChevronRight size={12} />
                        <Link to="/print-queue" className="hover:text-white transition-colors">Print Queue</Link>
                        <ChevronRight size={12} />
                        <span className="text-cyan-400 font-medium">G-Code Preview</span>
                    </div>
                    <div className="flex items-center gap-3">
                        <button
                            onClick={() => navigate(-1)}
                            className="p-1.5 rounded-lg bg-surface hover:bg-white/10 text-muted hover:text-white transition-colors"
                            title="Go Back"
                        >
                            <ArrowLeft size={16} />
                        </button>
                        <h1 className="text-xl font-bold text-white flex items-center gap-2 truncate">
                            <span>{title}</span>
                            <span className="text-xs font-mono font-normal px-2.5 py-0.5 rounded-full bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
                                Job #{jobId}
                            </span>
                        </h1>
                    </div>
                </div>

                {/* Actions */}
                <div className="flex items-center gap-3">
                    <a
                        href={gcodeUrl}
                        download
                        className="btn-ghost text-xs text-cyan-400 border border-cyan-500/20 hover:bg-cyan-500/10 px-3 py-2 rounded-xl flex items-center gap-1.5 transition-colors"
                    >
                        <Download size={15} />
                        <span>Download G-Code</span>
                    </a>
                    <Link
                        to="/print-queue"
                        className="btn-primary text-xs px-3.5 py-2 rounded-xl flex items-center gap-1.5 shadow-glow"
                    >
                        <Printer size={15} />
                        <span>Print Queue</span>
                    </Link>
                </div>
            </div>

            {/* Main Content Area */}
            <div className="flex-1 min-h-0 bg-slate-950 rounded-2xl border border-slate-800 shadow-2xl overflow-hidden relative flex flex-col">
                {loading && (
                    <div className="flex-1 flex flex-col items-center justify-center gap-4 text-center p-8">
                        <div className="relative">
                            <Loader2 size={52} className="animate-spin text-cyan-400" />
                            <FileCode size={24} className="absolute inset-0 m-auto text-cyan-200" />
                        </div>
                        <div className="space-y-1">
                            <h3 className="text-lg font-semibold text-slate-200">Analyzing Sliced Toolpaths</h3>
                            <p className="text-xs text-slate-400 max-w-md">
                                Extracting layer boundaries, extrusion profiles, travel moves, and slicing statistics...
                            </p>
                        </div>
                    </div>
                )}

                {error && !loading && (
                    <div className="flex-1 flex flex-col items-center justify-center p-8 text-center">
                        <div className="w-16 h-16 rounded-2xl bg-red-500/10 border border-red-500/20 flex items-center justify-center mb-4 text-red-400">
                            <AlertTriangle size={32} />
                        </div>
                        <h3 className="text-lg font-bold text-red-400 mb-2">G-Code Preview Failed</h3>
                        <p className="text-sm text-slate-300 max-w-md mb-6">{error}</p>
                        <div className="flex items-center gap-3">
                            <button
                                onClick={() => window.location.reload()}
                                className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-white border border-slate-700 transition-colors"
                            >
                                Retry
                            </button>
                            <Link
                                to="/print-queue"
                                className="px-4 py-2 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-xs font-bold text-slate-950 transition-colors"
                            >
                                Back to Queue
                            </Link>
                        </div>
                    </div>
                )}

                {parsedData && !loading && !error && (
                    <GCodeViewer parsedData={parsedData} jobTitle={title} />
                )}
            </div>
        </div>
    );
}
