/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { X, Info, FolderOpen, Layout, Eye, Camera } from 'lucide-react';
import { AppSettings, AspectRatio } from '../types';
import { cn } from '../utils';

interface SettingsViewProps {
  settings: AppSettings;
  onUpdateSettings: (settings: AppSettings) => void;
  onClose: () => void;
}

export default function SettingsView({ settings, onUpdateSettings, onClose }: SettingsViewProps) {
  const [localSettings, setLocalSettings] = useState<AppSettings>(settings);
  const [pickStatus, setPickStatus] = useState('');

  const updateSetting = <K extends keyof AppSettings>(key: K, value: AppSettings[K]) => {
    const updated = { ...localSettings, [key]: value };
    setLocalSettings(updated);
    onUpdateSettings(updated);
  };

  const handlePickFolder = async () => {
    try {
      setPickStatus('Opening...');
      const { Capacitor } = await import('@capacitor/core');
      if (!Capacitor.isNativePlatform()) {
        setPickStatus('Native Android only');
        return;
      }
      const { FilePicker } = await import('@capawesome/capacitor-file-picker');
      const result = await FilePicker.pickDirectory();
      const path = (result as any).path || (result as any).uri;
      if (path) {
        updateSetting('saveLocation', path);
        setPickStatus('');
      } else {
        setPickStatus('No path returned');
      }
    } catch (err: any) {
      console.error('Folder pick error:', err);
      if (err?.message?.includes('cancel') || err?.code === 'USER_CANCELLED') {
        setPickStatus('');
      } else {
        setPickStatus('Error: ' + (err?.message || String(err)));
      }
    }
  };

  return (
    <div className="fixed inset-0 z-60 bg-surface-lowest min-h-screen overflow-y-auto font-sans">
      {/* Header */}
      <nav className="fixed top-0 left-0 right-0 z-50 flex items-center justify-between px-6 h-20 bg-surface-lowest/80 backdrop-blur-xl">
        <div className="flex items-center gap-4">
          <span className="text-2xl font-black tracking-tighter text-primary">SNAPSELL</span>
        </div>
        <div className="flex items-center gap-6">
          <h1 className="font-bold tracking-tight text-lg uppercase">Settings</h1>
          <button
            onClick={onClose}
            className="w-10 h-10 flex items-center justify-center rounded-full bg-surface-container hover:bg-surface-high transition-colors active:scale-95"
          >
            <X size={20} className="text-on-surface" />
          </button>
        </div>
      </nav>

      <main className="pt-24 pb-32 max-w-2xl mx-auto px-6">
        {/* CAMERA DEFAULTS */}
        <section className="mb-10">
          <h2 className="text-[10px] font-mono font-bold text-on-surface-variant tracking-[0.2em] uppercase mb-4 px-2">
            Camera Defaults
          </h2>
          <div className="bg-surface-container rounded-lg overflow-hidden border border-outline-variant/10 shadow-xl">
            {/* Aspect Ratio */}
            <div className="flex items-center justify-between p-5 hover:bg-surface-high transition-colors group">
              <div className="flex items-center gap-4">
                <Layout size={22} className="text-primary-dim" />
                <div>
                  <p className="font-semibold text-on-surface">Aspect Ratio</p>
                  <p className="text-xs text-on-surface-variant">Default frame for product listings</p>
                </div>
              </div>
              <div className="flex items-center gap-2 bg-surface-highest px-3 py-1.5 rounded-full border border-outline-variant/20">
                <span className="text-xs font-mono font-bold text-primary">{localSettings.defaultAspectRatio}</span>
                <Layout size={12} className="text-on-surface-variant" />
              </div>
            </div>

            <div className="h-px bg-outline-variant/10 mx-5" />

            {/* Aspect Ratio Selector */}
            <div className="p-5">
              <div className="flex items-center gap-2 bg-surface-highest rounded-full p-1">
                {(['1:1', '4:3', '16:9'] as AspectRatio[]).map((ratio) => (
                  <button
                    key={ratio}
                    onClick={() => updateSetting('defaultAspectRatio', ratio)}
                    className={cn(
                      "flex-1 px-4 py-2 text-xs font-mono font-bold tracking-widest rounded-full transition-all",
                      localSettings.defaultAspectRatio === ratio
                        ? "bg-on-surface text-surface shadow-lg"
                        : "text-on-surface-variant hover:text-on-surface"
                    )}
                  >
                    {ratio}
                  </button>
                ))}
              </div>
            </div>

            <div className="h-px bg-outline-variant/10 mx-5" />

            {/* Preview Toggle */}
            <div className="flex items-center justify-between p-5 hover:bg-surface-high transition-colors">
              <div className="flex items-center gap-4">
                <Eye size={22} className="text-primary-dim" />
                <div>
                  <p className="font-semibold text-on-surface">Real-time Preview</p>
                  <p className="text-xs text-on-surface-variant">Toggle AI processing overlay</p>
                </div>
              </div>
              <button
                onClick={() => updateSetting('showPreviewAfterCapture', !localSettings.showPreviewAfterCapture)}
                className={cn(
                  "w-12 h-6 rounded-full relative transition-colors",
                  localSettings.showPreviewAfterCapture ? "bg-primary" : "bg-surface-highest border border-outline-variant/30"
                )}
              >
                <span className={cn(
                  "absolute top-1 w-4 h-4 rounded-full shadow-sm transition-all",
                  localSettings.showPreviewAfterCapture
                    ? "right-1 bg-on-primary-container"
                    : "left-1 bg-on-surface-variant"
                )} />
              </button>
            </div>
          </div>
        </section>

        {/* STORAGE */}
        <section className="mb-10">
          <h2 className="text-[10px] font-mono font-bold text-on-surface-variant tracking-[0.2em] uppercase mb-4 px-2">
            Storage
          </h2>
          <div className="bg-surface-container rounded-lg overflow-hidden border border-outline-variant/10 shadow-xl">
            <div className="p-5 hover:bg-surface-high transition-colors">
              <div className="flex items-center gap-4 mb-3">
                <FolderOpen size={22} className="text-primary-dim" />
                <p className="font-semibold text-on-surface">Save Location</p>
              </div>
              <div className="flex items-center justify-between bg-surface-lowest p-3 rounded-xl border border-outline-variant/20">
                <p className="text-xs font-mono text-on-surface-variant truncate mr-4">
                  {localSettings.saveLocation || 'Not configured'}
                </p>
                <button
                  onClick={handlePickFolder}
                  className="text-[10px] font-mono font-bold text-primary px-3 py-1 rounded-full bg-primary/10 hover:bg-primary/20 transition-colors uppercase tracking-widest shrink-0"
                >
                  Edit
                </button>
              </div>
              {pickStatus && (
                <p className="text-[10px] font-mono text-on-surface-variant/50 mt-2 uppercase tracking-tighter">{pickStatus}</p>
              )}
            </div>
          </div>
        </section>

        {/* ABOUT */}
        <section className="mb-16">
          <h2 className="text-[10px] font-mono font-bold text-on-surface-variant tracking-[0.2em] uppercase mb-4 px-2">
            About
          </h2>
          <div className="bg-surface-container rounded-lg overflow-hidden border border-outline-variant/10 shadow-xl">
            <div className="flex items-center justify-between p-5">
              <div className="flex items-center gap-4">
                <Info size={22} className="text-primary-dim" />
                <div>
                  <p className="font-semibold text-on-surface">SnapSell AI Camera</p>
                  <p className="text-xs text-on-surface-variant">Version 1.0.0 (Beta)</p>
                </div>
              </div>
              <p className="font-mono text-xs font-bold text-primary">v1.0</p>
            </div>
          </div>
        </section>

        {/* Footer */}
        <footer className="text-center space-y-4">
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-surface-high/50 border border-outline-variant/10">
            <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
            <p className="text-[10px] font-mono font-bold text-on-surface-variant uppercase tracking-[0.15em]">
              Designed for professional sellers
            </p>
          </div>
          <p className="text-[9px] text-on-surface-variant/30 font-mono uppercase tracking-widest">
            © 2024 SNAPSELL PRECISION OPTICS
          </p>
        </footer>
      </main>

      {/* Return to Lens Button */}
      <div className="fixed bottom-10 left-1/2 -translate-x-1/2 z-50">
        <button
          onClick={onClose}
          className="group flex items-center gap-3 bg-gradient-to-br from-primary to-primary-container px-8 py-4 rounded-full shadow-[0_0_24px_rgba(105,246,184,0.15)] active:scale-95 transition-all"
        >
          <Camera size={20} className="text-on-primary-container" />
          <span className="font-bold text-on-primary-container text-sm tracking-tight">Return to Lens</span>
        </button>
      </div>

      {/* Background Gradient Glow */}
      <div className="fixed top-0 right-0 -z-10 w-[500px] h-[500px] bg-primary/5 blur-[120px] rounded-full pointer-events-none" />
      <div className="fixed bottom-0 left-0 -z-10 w-[400px] h-[400px] bg-secondary-container/5 blur-[100px] rounded-full pointer-events-none" />
    </div>
  );
}