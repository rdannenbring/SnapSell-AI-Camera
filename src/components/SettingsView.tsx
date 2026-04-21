/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useMemo } from 'react';
import { X, Info, FolderOpen, Layout, Eye, Camera, Key, EyeOff, Eye as EyeIcon, Image, Gauge } from 'lucide-react';
import { AppSettings, AspectRatio } from '../types';
import { obscureApiKey, isObscuredKey } from '../services/aiService';
import { cn } from '../utils';

interface SettingsViewProps {
  settings: AppSettings;
  onUpdateSettings: (settings: AppSettings) => void;
  onClose: () => void;
}

export default function SettingsView({ settings, onUpdateSettings, onClose }: SettingsViewProps) {
  const [localSettings, setLocalSettings] = useState<AppSettings>(settings);
  const [pickStatus, setPickStatus] = useState('');
  const [showApiKey, setShowApiKey] = useState(false);

  // Build-time default key (from .env), obscured for display
  const buildTimeKey = import.meta.env.VITE_GEMINI_API_KEY || '';
  const buildTimeKeyObscured = buildTimeKey ? obscureApiKey(buildTimeKey) : '';

  // Determine what to show in the API key field:
  // - If user has set a runtime key → show it (or obscured)
  // - If no runtime key but build-time key exists → show obscured build-time key
  // - Otherwise → empty
  const getApiKeyDisplayValue = () => {
    if (localSettings.geminiApiKey) {
      return showApiKey ? localSettings.geminiApiKey : obscureApiKey(localSettings.geminiApiKey);
    }
    if (buildTimeKey) {
      return buildTimeKeyObscured;
    }
    return '';
  };

  const handleApiKeyChange = (value: string) => {
    // If user edited the obscured value, treat as new key
    if (isObscuredKey(value) && value === buildTimeKeyObscured) {
      // User didn't actually change it — keep using build-time default
      updateSetting('geminiApiKey', undefined);
    } else {
      updateSetting('geminiApiKey', value || undefined);
    }
  };

  const handleApiKeyFocus = (e: React.FocusEvent<HTMLInputElement>) => {
    // On focus, if showing obscured build-time key, clear it so user can type new key
    if (!localSettings.geminiApiKey && buildTimeKey) {
      e.target.value = '';
    }
    setShowApiKey(true);
  };

  const handleApiKeyBlur = (e: React.FocusEvent<HTMLInputElement>) => {
    const value = e.target.value;
    if (!value && buildTimeKey) {
      // User cleared it — revert to build-time default
      e.target.value = buildTimeKeyObscured;
      updateSetting('geminiApiKey', undefined);
    } else if (value && !isObscuredKey(value)) {
      handleApiKeyChange(value);
    }
    setShowApiKey(false);
  };

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

  // Estimate JPEG file size for a 1920×1080 photo at given quality
  const estimateFileSize = (quality: number): { kb: number; label: string } => {
    // Empirical model: ~120KB at q=50, cubic growth to ~960KB at q=100
    const kb = Math.round(120 * Math.pow(quality / 50, 3));
    if (kb > 1024) return { kb, label: `${(kb / 1024).toFixed(1)} MB` };
    return { kb, label: `${kb} KB` };
  };

  const getQualityTier = (quality: number): { label: string; color: string } => {
    if (quality <= 59) return { label: 'Low', color: 'text-error' };
    if (quality <= 74) return { label: 'Medium', color: 'text-yellow-400' };
    if (quality <= 89) return { label: 'High', color: 'text-primary' };
    return { label: 'Maximum', color: 'text-primary' };
  };

  const fileSizeInfo = useMemo(() => estimateFileSize(localSettings.imageQuality), [localSettings.imageQuality]);
  const qualityTier = useMemo(() => getQualityTier(localSettings.imageQuality), [localSettings.imageQuality]);

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

        {/* IMAGE QUALITY */}
        <section className="mb-10">
          <h2 className="text-[10px] font-mono font-bold text-on-surface-variant tracking-[0.2em] uppercase mb-4 px-2">
            Image Quality
          </h2>
          <div className="bg-surface-container rounded-lg overflow-hidden border border-outline-variant/10 shadow-xl">
            {/* Quality slider */}
            <div className="p-5">
              <div className="flex items-center gap-4 mb-4">
                <Gauge size={22} className="text-primary-dim" />
                <div className="flex-1">
                  <div className="flex items-center justify-between">
                    <p className="font-semibold text-on-surface">JPEG Quality</p>
                    <div className="flex items-center gap-2">
                      <span className={`text-xs font-mono font-bold ${qualityTier.color}`}>{qualityTier.label}</span>
                      <span className="bg-surface-highest px-3 py-1 rounded-full border border-outline-variant/20 text-xs font-mono font-bold text-primary">
                        {localSettings.imageQuality}%
                      </span>
                    </div>
                  </div>
                  <p className="text-xs text-on-surface-variant mt-0.5">Higher quality = larger file size</p>
                </div>
              </div>

              {/* Slider */}
              <div className="relative mb-4">
                <input
                  type="range"
                  min={50}
                  max={100}
                  step={5}
                  value={localSettings.imageQuality}
                  onChange={(e) => updateSetting('imageQuality', parseInt(e.target.value))}
                  className="w-full h-2 bg-surface-highest rounded-lg appearance-none cursor-pointer slider-obsidian"
                />
                <div className="flex justify-between mt-1">
                  <span className="text-[9px] font-mono text-on-surface-variant/50">50%</span>
                  <span className="text-[9px] font-mono text-on-surface-variant/50">75%</span>
                  <span className="text-[9px] font-mono text-on-surface-variant/50">100%</span>
                </div>
              </div>

              {/* File size estimate card */}
              <div className="flex items-center justify-between bg-surface-highest rounded-xl p-3 border border-outline-variant/10">
                <div className="flex items-center gap-3">
                  <Image size={16} className="text-on-surface-variant/50" />
                  <div>
                    <p className="text-[10px] font-mono text-on-surface-variant uppercase tracking-wider">Est. file size</p>
                    <p className="text-[10px] font-mono text-on-surface-variant/40">per 1920×1080 photo</p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-sm font-mono font-bold text-on-surface">{fileSizeInfo.label}</p>
                  <p className="text-[10px] font-mono text-on-surface-variant/50">{qualityTier.label} quality</p>
                </div>
              </div>

              {/* Quality presets */}
              <div className="flex items-center gap-2 mt-4">
                {[
                  { label: 'Web', value: 60 },
                  { label: 'Balanced', value: 80 },
                  { label: 'Sharp', value: 90 },
                  { label: 'Best', value: 100 },
                ].map((preset) => (
                  <button
                    key={preset.value}
                    onClick={() => updateSetting('imageQuality', preset.value)}
                    className={cn(
                      "flex-1 px-2 py-1.5 text-[10px] font-mono font-bold uppercase tracking-wider rounded-full transition-all border",
                      localSettings.imageQuality === preset.value
                        ? "bg-on-surface text-surface border-on-surface shadow-lg"
                        : "text-on-surface-variant border-outline-variant/20 hover:border-primary/50 hover:text-on-surface"
                    )}
                  >
                    {preset.label}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </section>

        {/* AI / GEMINI API KEY */}
        <section className="mb-10">
          <h2 className="text-[10px] font-mono font-bold text-on-surface-variant tracking-[0.2em] uppercase mb-4 px-2">
            AI Configuration
          </h2>
          <div className="bg-surface-container rounded-lg overflow-hidden border border-outline-variant/10 shadow-xl">
            <div className="p-5 hover:bg-surface-high transition-colors">
              <div className="flex items-center gap-4 mb-3">
                <Key size={22} className="text-primary-dim" />
                <div>
                  <p className="font-semibold text-on-surface">Gemini API Key</p>
                  <p className="text-xs text-on-surface-variant">Required for AI Enhance, White BG & Lifestyle filters</p>
                </div>
              </div>
              <div className="relative flex items-center bg-surface-lowest rounded-xl border border-outline-variant/20">
                <input
                  type={showApiKey ? 'text' : 'password'}
                  defaultValue={getApiKeyDisplayValue()}
                  onFocus={handleApiKeyFocus}
                  onBlur={handleApiKeyBlur}
                  placeholder="Enter your Gemini API key..."
                  className="w-full bg-transparent text-xs font-mono text-on-surface placeholder:text-on-surface-variant/40 p-3 pr-10 rounded-xl focus:outline-none focus:ring-1 focus:ring-primary/50"
                />
                <button
                  type="button"
                  onMouseDown={(e) => { e.preventDefault(); setShowApiKey(!showApiKey); }}
                  className="absolute right-2 p-1.5 text-on-surface-variant hover:text-on-surface transition-colors"
                >
                  {showApiKey ? <EyeOff size={14} /> : <EyeIcon size={14} />}
                </button>
              </div>
              {buildTimeKey && !localSettings.geminiApiKey && (
                <p className="text-[10px] font-mono text-primary/60 mt-2 uppercase tracking-tighter">
                  ✓ Build-time key loaded from .env
                </p>
              )}
              {localSettings.geminiApiKey && (
                <p className="text-[10px] font-mono text-primary/60 mt-2 uppercase tracking-tighter">
                  ✓ Using custom API key
                </p>
              )}
              {!buildTimeKey && !localSettings.geminiApiKey && (
                <p className="text-[10px] font-mono text-error/60 mt-2 uppercase tracking-tighter">
                  ⚠ No API key configured — AI features unavailable
                </p>
              )}
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