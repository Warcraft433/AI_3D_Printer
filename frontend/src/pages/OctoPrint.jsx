import React from "react";
import { Server, Activity, Globe } from 'lucide-react';

export default function OctoPrint() {
  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold bg-gradient-to-r from-white to-slate-400 bg-clip-text text-transparent">
            OctoPrint Control Panel
          </h2>
          <p className="text-muted text-sm mt-1">Manage and monitor your printer server in real-time.</p>
        </div>
        
        <div className="flex flex-wrap gap-3">
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-800/50 border border-slate-700/50 text-xs text-slate-300">
            <Server size={14} className="text-primary" />
            <span>Raspberry Pi</span>
          </div>
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-800/50 border border-slate-700/50 text-xs text-slate-300">
            <Globe size={14} className="text-secondary" />
            <span>localhost</span>
          </div>
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-primary/10 border border-primary/20 text-xs text-primary animate-pulse">
            <Activity size={14} />
            <span className="font-semibold uppercase tracking-wider">Connected</span>
          </div>
        </div>
      </div>

      <div className="relative w-full h-[calc(100vh-200px)] min-h-[500px] border border-border/50 rounded-2xl overflow-hidden bg-card shadow-2xl group transition-all duration-500 hover:border-primary/20">
        <div className="absolute inset-0 bg-primary/5 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none" />
        <iframe
          src="http://localhost:5000"
          className="w-full h-full border-none"
          title="OctoPrint"
          allow="fullscreen"
        />
      </div>
      
      <div className="flex items-center gap-2 text-xs text-muted/60 justify-center">
        <div className="w-1.5 h-1.5 rounded-full bg-primary" />
        <p>This panel provides direct access to the OctoPrint instance running on your local network.</p>
      </div>
    </div>
  );
}
