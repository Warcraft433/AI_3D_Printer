import React, { useState, useEffect } from 'react';
import { Download, CheckCircle, Printer, Loader2, FileCode, Check, XCircle } from 'lucide-react';
import api from '../services/api';
import { useNavigate } from 'react-router-dom';

const STATUS_LABELS = {
    slicing_started: 'Initializing Slicer...',
    preparing_model: 'Preparing 3D Model...',
    running_prusaslicer: 'Running PrusaSlicer...',
    generating_gcode: 'Generating G-Code...',
    ready_to_print: 'Ready to Print',
    failed: 'Slicing Failed',
};

export default function SlicingModal({ isOpen, onClose, jobId, modelName }) {
    const [status, setStatus] = useState('slicing_started');
    const [progress, setProgress] = useState(0);
    const [error, setError] = useState(null);
    const [isDownloading, setIsDownloading] = useState(false);
    const navigate = useNavigate();

    useEffect(() => {
        if (!isOpen || !jobId) return;

        setStatus('slicing_started');
        setProgress(0);
        setError(null);

        const wsUrl = `ws://localhost:8000/ws/print-jobs/${jobId}/`;
        const socket = new WebSocket(wsUrl);

        socket.onopen = () => console.log('Slicing WS Connected');

        socket.onmessage = (event) => {
            const data = JSON.parse(event.data);
            if (data.status) {
                setStatus(data.status);
                // simulate some progress purely for visual feedback based on status
                if (data.status === 'slicing_started') setProgress(10);
                if (data.status === 'preparing_model') setProgress(30);
                if (data.status === 'running_prusaslicer') setProgress(60);
                if (data.status === 'generating_gcode') setProgress(90);
                if (data.status === 'ready_to_print') setProgress(100);
                if (data.status === 'failed') {
                    setError(data.error_detail || 'Unknown slicing error');
                }
            }
        };

        socket.onclose = () => console.log('Slicing WS Closed');

        return () => socket.close();
    }, [isOpen, jobId]);

    if (!isOpen) return null;

    const isSuccess = status === 'ready_to_print' || status === 'printing' || status === 'completed';
    const isError = status === 'failed';

    const handleDownload = async () => {
        if (!jobId) return;
        setIsDownloading(true);
        try {
            const response = await api.get(`/print-jobs/${jobId}/download-gcode/`, {
                responseType: 'blob', // critical for binary data
            });

            // Create a blob link to download
            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;

            // Try to extract filename from content-disposition header if present
            const contentDisposition = response.headers['content-disposition'];
            let fileName = 'sliced_model.gcode';
            if (contentDisposition) {
                const fileNameMatch = contentDisposition.match(/filename="?([^"]+)"?/);
                if (fileNameMatch && fileNameMatch.length === 2)
                    fileName = fileNameMatch[1];
            }

            link.setAttribute('download', fileName);
            document.body.appendChild(link);
            link.click();
            link.parentNode.removeChild(link);
            window.URL.revokeObjectURL(url);
        } catch (err) {
            console.error('Download error', err);
            alert('Failed to download G-Code securely.');
        } finally {
            setIsDownloading(false);
        }
    };

    const handlePrint = () => {
        navigate('/queue');
        onClose();
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm p-4 text-white">
            <div className="bg-surface border border-border rounded-2xl w-full max-w-md shadow-2xl overflow-hidden relative">
                {/* Header */}
                <div className="px-6 py-4 border-b border-white/5 flex items-center justify-between">
                    <h3 className="font-semibold text-lg flex items-center gap-2">
                        <FileCode size={20} className="text-primary" />
                        Slicing Workflow
                    </h3>
                    {(isSuccess || isError) && (
                        <button onClick={onClose} className="text-muted hover:text-white transition-colors">
                            <XCircle size={20} />
                        </button>
                    )}
                </div>

                {/* Body */}
                <div className="p-6 space-y-6">
                    <div className="text-center space-y-2">
                        <p className="text-muted text-sm uppercase tracking-wider font-semibold">Target Model</p>
                        <p className="font-medium text-lg truncate px-4">{modelName || `Job #${jobId}`}</p>
                    </div>

                    {!isSuccess && !isError && (
                        <div className="space-y-4">
                            <div className="flex justify-between items-center text-sm">
                                <span className="text-muted flex items-center gap-2">
                                    <Loader2 size={16} className="animate-spin text-primary" />
                                    {STATUS_LABELS[status] || 'Processing...'}
                                </span>
                                <span className="font-mono text-primary font-bold">{progress}%</span>
                            </div>
                            <div className="h-2 w-full bg-background rounded-full overflow-hidden">
                                <div
                                    className="h-full bg-gradient-primary rounded-full transition-all duration-500 ease-out"
                                    style={{ width: `${progress}%` }}
                                />
                            </div>
                        </div>
                    )}

                    {isSuccess && (
                        <div className="flex flex-col items-center gap-2 py-4 animate-in fade-in zoom-in duration-300">
                            <div className="w-16 h-16 rounded-full bg-success/20 flex items-center justify-center mb-2">
                                <CheckCircle size={32} className="text-success" />
                            </div>
                            <h4 className="text-success font-bold text-xl">Slicing Complete</h4>
                            <p className="text-sm text-muted">Your G-Code file is ready.</p>
                        </div>
                    )}

                    {isError && (
                        <div className="flex flex-col items-center gap-2 py-4">
                            <div className="w-16 h-16 rounded-full bg-danger/20 flex items-center justify-center mb-2">
                                <XCircle size={32} className="text-danger" />
                            </div>
                            <h4 className="text-danger font-bold text-xl">Slicing Failed</h4>
                            <p className="text-sm text-muted text-center bg-danger/10 p-2 rounded w-full border border-danger/20">{error}</p>
                        </div>
                    )}
                </div>

                {/* Footer Actions */}
                {isSuccess && (
                    <div className="px-6 py-4 bg-white/5 border-t border-white/5 flex gap-3">
                        <button
                            onClick={handleDownload}
                            disabled={isDownloading}
                            className="btn-ghost flex-1 py-3 justify-center text-primary border-primary/20 hover:bg-primary/10"
                        >
                            {isDownloading ? <Loader2 size={18} className="animate-spin" /> : <Download size={18} />}
                            Download G-Code
                        </button>
                        <button onClick={handlePrint} className="btn-primary flex-1 py-3 justify-center shadow-glow">
                            <Printer size={18} />
                            Go to Printer
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}
