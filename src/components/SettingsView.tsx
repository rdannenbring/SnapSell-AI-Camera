/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { X, ChevronRight, Folder, Layout, Info, Eye } from 'lucide-react';
import { AppSettings, AspectRatio } from '../types';
import { cn } from '../utils';

interface SettingsViewProps {
  settings: AppSettings;
  onUpdate: (settings: AppSettings) => void;
  onClose: () => void;
}

export default function SettingsView({ settings, onUpdate, onClose }: SettingsViewProps) {
  return (
    <div className="fixed inset-0 z-[60] bg-zinc-950 text-white font-sans overflow-y-auto">
      <div className="p-6 flex justify-between items-center border-b border-white/5 sticky top-0 bg-zinc-950/80 backdrop-blur-xl">
        <h1 className="text-xl font-bold tracking-tight">Settings</h1>
        <button onClick={onClose} className="p-2 rounded-full bg-white/5">
          <X size={20} />
        </button>
      </div>

      <div className="p-6 space-y-8">
        <section>
          <h2 className="text-xs font-bold text-white/30 uppercase tracking-widest mb-4">Camera Defaults</h2>
          <div className="bg-zinc-900 rounded-2xl overflow-hidden">
            <div className="p-4 flex items-center justify-between border-b border-white/5">
              <div className="flex items-center gap-3">
                <Layout size={20} className="text-white/60" />
                <span>Default Aspect Ratio</span>
              </div>
              <div className="flex gap-1 bg-black/40 p-1 rounded-lg">
                {(['1:1', '4:3', '16:9'] as AspectRatio[]).map((ratio) => (
                  <button
                    key={ratio}
                    onClick={() => onUpdate({ ...settings, defaultAspectRatio: ratio })}
                    className={cn(
                      "px-3 py-1 rounded-md text-xs font-medium transition-all",
                      settings.defaultAspectRatio === ratio ? "bg-white text-black shadow-lg" : "text-white/40"
                    )}
                  >
                    {ratio}
                  </button>
                ))}
              </div>
            </div>
            <div className="p-4 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Eye size={20} className="text-white/60" />
                <span>Show Preview After Capture</span>
              </div>
              <button 
                onClick={() => onUpdate({ ...settings, showPreviewAfterCapture: !settings.showPreviewAfterCapture })}
                className={cn(
                  "w-12 h-6 rounded-full transition-colors relative",
                  settings.showPreviewAfterCapture ? "bg-white" : "bg-white/10"
                )}
              >
                <div className={cn(
                  "absolute top-1 w-4 h-4 rounded-full transition-all",
                  settings.showPreviewAfterCapture ? "right-1 bg-black" : "left-1 bg-white/40"
                )} />
              </button>
            </div>
          </div>
        </section>

        <section>
          <h2 className="text-xs font-bold text-white/30 uppercase tracking-widest mb-4">Storage</h2>
          <div className="bg-zinc-900 rounded-2xl overflow-hidden">
            <div className="p-4 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Folder size={20} className="text-white/60" />
                <div className="flex flex-col">
                  <span>Save Location</span>
                  <span className="text-xs text-white/30">{settings.saveLocation}</span>
                </div>
              </div>
              <ChevronRight size={20} className="text-white/20" />
            </div>
          </div>
        </section>

        <section>
          <h2 className="text-xs font-bold text-white/30 uppercase tracking-widest mb-4">About</h2>
          <div className="bg-zinc-900 rounded-2xl overflow-hidden">
            <div className="p-4 flex items-center gap-3">
              <Info size={20} className="text-white/60" />
              <div className="flex flex-col">
                <span className="font-medium">SnapSell AI Camera</span>
                <span className="text-xs text-white/30">Version 1.0.0 (Beta)</span>
              </div>
            </div>
          </div>
        </section>

        <div className="pt-8 text-center">
          <p className="text-[10px] text-white/20 uppercase tracking-widest leading-relaxed">
            Designed for professional sellers.<br />Powered by Google Gemini AI.
          </p>
        </div>
      </div>
    </div>
  );
}
