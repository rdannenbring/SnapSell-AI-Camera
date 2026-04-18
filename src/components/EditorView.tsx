/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
  X, Check, Crop, Sliders, Sparkles,
  Sun, Contrast, Image as ImageIcon, RotateCcw,
  Layers, Wand2, Maximize, Trash2, Save, Plus
} from 'lucide-react';
import { PhotoData, AppSettings, AspectRatio } from '../types';
import { applyAIFilter, AI_PROMPTS, suggestItemName } from '../services/aiService';
import { cn } from '../utils';

interface EditorViewProps {
  photos: PhotoData[];
  onClose: () => void;
  onSave: (photos: PhotoData[], itemName?: string) => void;
  onRetake: (id: string) => void;
  onDelete: (id: string) => void;
  onAddMore: () => void;
  settings: AppSettings;
  isSaving?: boolean;
}

interface CropRect {
  x: number; // fraction 0-1
  y: number; // fraction 0-1
  w: number; // fraction 0-1
  h: number; // fraction 0-1
}

export default function EditorView({ photos, onClose, onSave, onRetake, onDelete, onAddMore, settings, isSaving = false }: EditorViewProps) {
  const [currentPhotos, setCurrentPhotos] = useState<PhotoData[]>(photos);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [itemName, setItemName] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [isNaming, setIsNaming] = useState(false);
  const [applyToAll, setApplyToAll] = useState(false);
  const [activeTab, setActiveTab] = useState<'adjust' | 'ai' | 'crop'>('adjust');
  const [cropAspectRatio, setCropAspectRatio] = useState<AspectRatio | 'free'>('free');

  // Crop state
  const [cropRect, setCropRect] = useState<CropRect>({ x: 0, y: 0, w: 1, h: 1 });
  const [dragCorner, setDragCorner] = useState<string | null>(null);
  const previewRef = useRef<HTMLDivElement>(null);

  const currentPhoto = currentPhotos[selectedIndex];

  // Reset crop rect when changing photo or entering crop tab
  useEffect(() => {
    setCropRect({ x: 0, y: 0, w: 1, h: 1 });
  }, [selectedIndex]);

  useEffect(() => {
    if (activeTab === 'crop') {
      // Crop always applies to current photo only
      setApplyToAll(false);
      applyCropPreset(cropAspectRatio);
    }
  }, [activeTab]);

  const applyCropPreset = (ratio: AspectRatio | 'free') => {
    if (ratio === 'free') {
      setCropRect({ x: 0, y: 0, w: 1, h: 1 });
    } else if (ratio === '1:1') {
      const size = 0.8;
      setCropRect({ x: (1 - size) / 2, y: (1 - size) / 2, w: size, h: size });
    } else if (ratio === '4:3') {
      const w = 0.9;
      const h = w * (3 / 4);
      setCropRect({ x: (1 - w) / 2, y: (1 - h) / 2, w, h });
    } else if (ratio === '16:9') {
      const w = 0.95;
      const h = w * (9 / 16);
      setCropRect({ x: (1 - w) / 2, y: (1 - h) / 2, w, h });
    }
  };

  const handleRatioChange = (ratio: AspectRatio | 'free') => {
    setCropAspectRatio(ratio);
    applyCropPreset(ratio);
  };

  // Pointer event handlers for crop dragging
  const handlePointerDown = useCallback((corner: string) => (e: React.PointerEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragCorner(corner);
  }, []);

  useEffect(() => {
    if (!dragCorner) return;

    const handleMove = (e: PointerEvent) => {
      e.preventDefault();
      if (!previewRef.current) return;

      const rect = previewRef.current.getBoundingClientRect();
      const px = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
      const py = Math.max(0, Math.min(1, (e.clientY - rect.top) / rect.height));

      setCropRect(prev => {
        let { x, y, w, h } = prev;
        const right = x + w;
        const bottom = y + h;
        const MIN = 0.1;

        if (dragCorner === 'tl') {
          x = Math.min(px, right - MIN);
          y = Math.min(py, bottom - MIN);
          w = right - x;
          h = bottom - y;
        } else if (dragCorner === 'tr') {
          w = Math.max(MIN, px - x);
          y = Math.min(py, bottom - MIN);
          h = bottom - y;
        } else if (dragCorner === 'bl') {
          x = Math.min(px, right - MIN);
          w = right - x;
          h = Math.max(MIN, py - y);
        } else if (dragCorner === 'br') {
          w = Math.max(MIN, px - x);
          h = Math.max(MIN, py - y);
        }

        // Constrain to aspect ratio
        if (cropAspectRatio !== 'free') {
          const targetRatio = cropAspectRatio === '1:1' ? 1 :
            cropAspectRatio === '4:3' ? 4 / 3 : 16 / 9;
          const currentRatio = w / h;
          if (currentRatio > targetRatio) {
            const newW = h * targetRatio;
            if (dragCorner === 'tl' || dragCorner === 'bl') x = right - newW;
            w = newW;
          } else {
            const newH = w / targetRatio;
            if (dragCorner === 'tl' || dragCorner === 'tr') y = bottom - newH;
            h = newH;
          }
        }

        return { x, y, w, h };
      });
    };

    const handleUp = () => {
      setDragCorner(null);
    };

    document.addEventListener('pointermove', handleMove);
    document.addEventListener('pointerup', handleUp);
    return () => {
      document.removeEventListener('pointermove', handleMove);
      document.removeEventListener('pointerup', handleUp);
    };
  }, [dragCorner, cropAspectRatio]);

  const updateCurrentPhoto = (updates: Partial<PhotoData>) => {
    const updated = currentPhotos.map((p, i) => {
      if (applyToAll || i === selectedIndex) {
        return { ...p, ...updates };
      }
      return p;
    });
    setCurrentPhotos(updated);
  };

  const handleAIFilter = async (prompt: string) => {
    setIsProcessing(true);
    try {
      if (applyToAll) {
        const updated = await Promise.all(currentPhotos.map(async (p) => {
          const result = await applyAIFilter(p.url, prompt);
          return { ...p, url: result, filters: { ...p.filters, aiFilter: prompt, exposure: p.filters?.exposure || 0, contrast: p.filters?.contrast || 0, scale: p.filters?.scale || 1 } };
        }));
        setCurrentPhotos(updated);
      } else {
        const result = await applyAIFilter(currentPhoto.url, prompt);
        updateCurrentPhoto({
          url: result,
          filters: {
            ...currentPhoto.filters,
            aiFilter: prompt,
            exposure: currentPhoto.filters?.exposure || 0,
            contrast: currentPhoto.filters?.contrast || 0,
            scale: currentPhoto.filters?.scale || 1
          }
        });
      }
    } catch (err) {
      console.error("AI Filter error:", err);
      const msg = err instanceof Error ? err.message : String(err);
      alert("AI processing failed: " + msg);
    } finally {
      setIsProcessing(false);
    }
  };

  const handleSuggestName = async () => {
    if (currentPhotos.length === 0) return;
    setIsNaming(true);
    try {
      const suggestion = await suggestItemName(currentPhotos.map(p => p.url));
      setItemName(suggestion);
    } catch (err) {
      console.error("Naming error:", err);
    } finally {
      setIsNaming(false);
    }
  };

  const handleCrop = () => {
    const img = new Image();
    img.onload = () => {
      const sx = cropRect.x * img.width;
      const sy = cropRect.y * img.height;
      const sw = cropRect.w * img.width;
      const sh = cropRect.h * img.height;

      const canvas = document.createElement('canvas');
      canvas.width = sw;
      canvas.height = sh;
      const ctx = canvas.getContext('2d');
      if (!ctx) return;

      ctx.drawImage(img, sx, sy, sw, sh, 0, 0, sw, sh);
      const croppedUrl = canvas.toDataURL('image/jpeg', 0.9);

      updateCurrentPhoto({
        url: croppedUrl,
        aspectRatio: cropAspectRatio === 'free' ? currentPhoto.aspectRatio : cropAspectRatio,
      });

      // Reset crop rect after applying
      setCropRect({ x: 0, y: 0, w: 1, h: 1 });
    };
    img.src = currentPhoto.url;
  };

  const handleFinish = async () => {
    // Bake CSS filters (exposure/contrast/scale) into actual image data before saving
    const MAX_DIM = 2048;
    const bakedPhotos = await Promise.all(currentPhotos.map(async (photo) => {
      const filters = photo.filters || { exposure: 0, contrast: 0, scale: 1 };
      // Only bake if there are non-default filters
      if (filters.exposure === 0 && filters.contrast === 0 && filters.scale === 1) {
        return photo;
      }

      return new Promise<PhotoData>((resolve) => {
        const img = new Image();
        img.onload = () => {
          // Limit canvas size to prevent OOM on mobile
          let w = img.width;
          let h = img.height;
          if (w > MAX_DIM || h > MAX_DIM) {
            const r = Math.min(MAX_DIM / w, MAX_DIM / h);
            w = Math.round(w * r);
            h = Math.round(h * r);
          }
          const canvas = document.createElement('canvas');
          canvas.width = w;
          canvas.height = h;
          const ctx = canvas.getContext('2d');
          if (!ctx) { resolve(photo); return; }

          ctx.filter = `brightness(${100 + filters.exposure}%) contrast(${100 + filters.contrast}%)`;
          // Scale from center
          const scale = filters.scale;
          const scaledW = w * scale;
          const scaledH = h * scale;
          const offsetX = (w - scaledW) / 2;
          const offsetY = (h - scaledH) / 2;

          ctx.drawImage(img, offsetX, offsetY, scaledW, scaledH);
          // Use JPEG instead of PNG — much smaller, prevents OOM
          resolve({
            ...photo,
            url: canvas.toDataURL('image/jpeg', 0.9),
          });
        };
        img.onerror = () => resolve(photo);
        img.src = photo.url;
      });
    }));

    onSave(bakedPhotos, itemName || undefined);
  };

  if (!currentPhoto) return null;

  const filters = currentPhoto.filters || { exposure: 0, contrast: 0, scale: 1 };

  // Map stored aspect ratio to CSS aspect-ratio value
  const aspectRatioMap: Record<string, string> = {
    '1:1': '1',
    '4:3': '4/3',
    '16:9': '16/9',
  };
  const previewAspectRatio = aspectRatioMap[currentPhoto.aspectRatio] || '4/3';

  return (
    <div className="fixed inset-0 z-50 bg-surface-lowest flex flex-col font-sans">

      {/* Header */}
      <header className="shrink-0 flex items-center justify-between px-6 h-16 bg-black/40 backdrop-blur-md z-50">
        <button onClick={onClose} className="p-2 rounded-full hover:bg-surface-container transition-colors active:scale-95">
          <X size={20} className="text-on-surface-variant" />
        </button>
        <div className="flex items-center gap-2 bg-surface-container/50 px-4 py-1.5 rounded-full border border-outline-variant/10">
          <Sparkles size={14} className="text-primary" />
          <input
            type="text"
            placeholder="Enter item name..."
            value={itemName}
            onChange={(e) => setItemName(e.target.value)}
            className="bg-transparent border-none focus:ring-0 text-sm font-semibold tracking-tight text-on-surface placeholder:text-outline w-40 text-center focus:outline-none"
          />
        </div>
        <button
          onClick={handleFinish}
          disabled={isSaving}
          className="bg-gradient-to-br from-primary to-primary-container text-on-primary-container px-5 py-2 rounded-full text-xs font-bold tracking-widest uppercase hover:opacity-90 active:scale-95 transition-all disabled:opacity-60"
        >
          <span className="flex items-center gap-2">
            <Save size={14} />
            {isSaving ? 'Saving...' : 'Finish'}
          </span>
        </button>
      </header>

      {/* Main Canvas */}
      <main className="flex-1 min-h-0 flex flex-col overflow-hidden">
        {/* Preview Area */}
        <div className="flex-1 min-h-0 flex items-center justify-center p-6 relative">
          <div
            ref={previewRef}
            className="relative max-w-md max-h-full rounded-lg overflow-hidden shadow-[0_0_60px_rgba(0,0,0,0.5)]"
            style={{
              aspectRatio: previewAspectRatio,
              maxWidth: '100%',
              maxHeight: '100%',
              touchAction: dragCorner ? 'none' : 'auto',
            }}
          >
            <img
              src={currentPhoto.url}
              className="w-full h-full object-contain"
              style={{
                filter: `brightness(${100 + filters.exposure}%) contrast(${100 + filters.contrast}%)`,
                transform: `scale(${filters.scale})`
              }}
              alt="Preview"
            />

            {/* Crop Overlay - only visible in crop tab */}
            {activeTab === 'crop' && (
              <>
                {/* Dimmed areas outside crop */}
                <div className="absolute inset-0 pointer-events-none">
                  {/* Top dim */}
                  <div className="absolute bg-black/50" style={{ top: 0, left: 0, right: 0, height: `${cropRect.y * 100}%` }} />
                  {/* Bottom dim */}
                  <div className="absolute bg-black/50" style={{ bottom: 0, left: 0, right: 0, height: `${(1 - cropRect.y - cropRect.h) * 100}%` }} />
                  {/* Left dim */}
                  <div className="absolute bg-black/50" style={{ top: `${cropRect.y * 100}%`, left: 0, width: `${cropRect.x * 100}%`, height: `${cropRect.h * 100}%` }} />
                  {/* Right dim */}
                  <div className="absolute bg-black/50" style={{ top: `${cropRect.y * 100}%`, right: 0, width: `${(1 - cropRect.x - cropRect.w) * 100}%`, height: `${cropRect.h * 100}%` }} />
                </div>

                {/* Crop border */}
                <div
                  className="absolute border-2 border-white pointer-events-none"
                  style={{
                    top: `${cropRect.y * 100}%`,
                    left: `${cropRect.x * 100}%`,
                    width: `${cropRect.w * 100}%`,
                    height: `${cropRect.h * 100}%`,
                  }}
                >
                  {/* Grid lines (rule of thirds) */}
                  <div className="absolute w-full h-full">
                    <div className="absolute left-1/3 top-0 bottom-0 w-px bg-white/30" />
                    <div className="absolute left-2/3 top-0 bottom-0 w-px bg-white/30" />
                    <div className="absolute top-1/3 left-0 right-0 h-px bg-white/30" />
                    <div className="absolute top-2/3 left-0 right-0 h-px bg-white/30" />
                  </div>
                </div>

                {/* Corner handles */}
                {(['tl', 'tr', 'bl', 'br'] as const).map(corner => {
                  const isTop = corner.startsWith('t');
                  const isLeft = corner.endsWith('l');
                  const style: React.CSSProperties = {
                    position: 'absolute',
                    width: 28,
                    height: 28,
                    top: isTop ? `${cropRect.y * 100}%` : `${(cropRect.y + cropRect.h) * 100}%`,
                    left: isLeft ? `${cropRect.x * 100}%` : `${(cropRect.x + cropRect.w) * 100}%`,
                    transform: `translate(${isLeft ? '-50%' : '-50%'}, ${isTop ? '-50%' : '-50%'})`,
                    zIndex: 20,
                    touchAction: 'none',
                  };

                  return (
                    <div
                      key={corner}
                      style={style}
                      onPointerDown={handlePointerDown(corner)}
                      className="flex items-center justify-center cursor-pointer"
                    >
                      <div className="w-5 h-5 bg-white rounded-full shadow-lg border-2 border-primary/60 active:scale-125 transition-transform" />
                    </div>
                  );
                })}
              </>
            )}

            {/* Photo Actions Overlay */}
            <div className="absolute top-4 right-4 flex gap-2 z-30">
              <button
                onClick={() => onRetake(currentPhoto.id)}
                className="p-2 bg-black/60 backdrop-blur-md rounded-full text-white hover:bg-surface-highest transition-colors border border-white/5"
                title="Re-take"
              >
                <RotateCcw size={16} />
              </button>
              <button
                onClick={() => {
                  onDelete(currentPhoto.id);
                  if (selectedIndex >= currentPhotos.length - 1) {
                    setSelectedIndex(Math.max(0, currentPhotos.length - 2));
                  }
                  setCurrentPhotos(prev => prev.filter(p => p.id !== currentPhoto.id));
                }}
                className="p-2 bg-error/60 backdrop-blur-md rounded-full text-white hover:bg-error/80 transition-colors border border-white/5"
                title="Delete"
              >
                <Trash2 size={16} />
              </button>
            </div>

            {isProcessing && (
              <div className="absolute inset-0 bg-black/60 backdrop-blur-sm rounded-lg flex flex-col items-center justify-center text-white gap-4 z-10">
                <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
                <p className="font-mono text-xs font-bold tracking-[0.2em] uppercase animate-pulse">AI Processing...</p>
              </div>
            )}
          </div>
        </div>

        {/* Thumbnail Strip with Add Button */}
        <div className="shrink-0 px-6 py-3">
          <div className="flex gap-3 overflow-x-auto no-scrollbar pb-2 items-center">
            {currentPhotos.map((p, i) => (
              <button
                key={p.id}
                onClick={() => setSelectedIndex(i)}
                className={cn(
                  "shrink-0 w-16 h-16 rounded-xl overflow-hidden border-2 transition-all",
                  selectedIndex === i
                    ? "border-primary p-0.5 bg-primary/20"
                    : "border-outline-variant/20 hover:border-primary/50 opacity-60"
                )}
              >
                <img src={p.url} className={cn("w-full h-full object-cover", selectedIndex === i && "rounded-lg")} />
              </button>
            ))}
            {/* Add More Photos Button */}
            <button
              onClick={onAddMore}
              className="shrink-0 w-16 h-16 rounded-xl border-2 border-dashed border-outline-variant/30 flex items-center justify-center hover:border-primary/50 hover:bg-surface-container/50 transition-all"
            >
              <Plus size={20} className="text-on-surface-variant/50" />
            </button>
          </div>
        </div>
      </main>

      {/* Bottom Adjustment Panel */}
      <section className="shrink-0 bg-surface rounded-t-[2.5rem] border-t border-outline-variant/10 shadow-[0_-20px_40px_rgba(0,0,0,0.8)]">
        {/* Tab Header */}
        <div className="px-8 pt-6 pb-2">
          {/* Apply to All toggle - hidden when cropping */}
          {activeTab !== 'crop' && (
          <div className="flex items-center justify-end gap-3 mb-5">
            <span className="text-[10px] font-mono font-bold uppercase text-on-surface-variant tracking-wider">Apply to All</span>
            <button
              onClick={() => setApplyToAll(!applyToAll)}
              className={cn(
                "w-10 h-5 rounded-full relative transition-colors",
                applyToAll ? "bg-primary" : "bg-surface-container"
              )}
            >
              <div className={cn(
                "absolute top-1 w-3 h-3 rounded-full transition-all",
                applyToAll ? "right-1 bg-on-primary" : "left-1 bg-on-surface-variant"
              )} />
            </button>
          </div>
          )}

          {/* Tab icons */}
          <nav className="flex items-center justify-center gap-8">
            {([
              { key: 'adjust' as const, icon: Sliders },
              { key: 'ai' as const, icon: Sparkles },
              { key: 'crop' as const, icon: Crop },
            ]).map(({ key, icon: Icon }) => (
              <button
                key={key}
                onClick={() => setActiveTab(key)}
                className={cn(
                  "flex flex-col items-center gap-1.5 group transition-colors",
                  activeTab === key ? "text-primary" : "text-on-surface-variant group-hover:text-on-surface"
                )}
              >
                <Icon size={22} />
                <div className={cn("w-1 h-1 rounded-full transition-colors", activeTab === key ? "bg-primary" : "bg-transparent")} />
              </button>
            ))}
          </nav>
        </div>

        {/* Tab Content — fixed height to prevent layout shift between tabs */}
        <div className="px-8 pb-10 h-[260px]">
          {activeTab === 'adjust' && (
            <div className="space-y-4 mt-4">
              {/* Exposure */}
              <div className="space-y-2">
                <div className="flex justify-between items-center">
                  <label className="text-[11px] font-mono font-bold uppercase tracking-widest text-on-surface-variant">Exposure</label>
                  <span className="text-xs font-mono text-primary">
                    {filters.exposure >= 0 ? '+' : ''}{filters.exposure}
                  </span>
                </div>
                <input
                  type="range" min="-100" max="100" value={filters.exposure}
                  onChange={(e) => updateCurrentPhoto({ filters: { ...filters, exposure: parseInt(e.target.value) } })}
                  className="w-full h-1 bg-surface-container rounded-lg appearance-none cursor-pointer slider-obsidian"
                />
              </div>
              {/* Contrast */}
              <div className="space-y-2">
                <div className="flex justify-between items-center">
                  <label className="text-[11px] font-mono font-bold uppercase tracking-widest text-on-surface-variant">Contrast</label>
                  <span className="text-xs font-mono text-primary">
                    {filters.contrast >= 0 ? '+' : ''}{filters.contrast}
                  </span>
                </div>
                <input
                  type="range" min="-100" max="100" value={filters.contrast}
                  onChange={(e) => updateCurrentPhoto({ filters: { ...filters, contrast: parseInt(e.target.value) } })}
                  className="w-full h-1 bg-surface-container rounded-lg appearance-none cursor-pointer slider-obsidian"
                />
              </div>
              {/* Scale */}
              <div className="space-y-2">
                <div className="flex justify-between items-center">
                  <label className="text-[11px] font-mono font-bold uppercase tracking-widest text-on-surface-variant">Scale</label>
                  <span className="text-xs font-mono text-primary">{filters.scale.toFixed(1)}x</span>
                </div>
                <input
                  type="range" min="0.1" max="2" step="0.1" value={filters.scale}
                  onChange={(e) => updateCurrentPhoto({ filters: { ...filters, scale: parseFloat(e.target.value) } })}
                  className="w-full h-1 bg-surface-container rounded-lg appearance-none cursor-pointer slider-obsidian"
                />
              </div>
            </div>
          )}

          {activeTab === 'ai' && (
            <div className="grid grid-cols-3 gap-3 mt-4">
              <button
                onClick={() => handleAIFilter(AI_PROMPTS.ENHANCE)}
                disabled={isProcessing}
                className="flex flex-col items-center justify-center gap-3 p-4 rounded-2xl bg-white/5 border border-white/5 hover:bg-white/10 transition-all group disabled:opacity-50"
              >
                <div className="w-10 h-10 rounded-full bg-secondary-container/20 flex items-center justify-center group-hover:scale-110 transition-transform">
                  <Wand2 size={20} className="text-secondary" />
                </div>
                <span className="font-mono text-[10px] uppercase font-bold text-on-surface-variant tracking-tighter">Enhance</span>
              </button>
              <button
                onClick={() => handleAIFilter(AI_PROMPTS.CLEAN_BACKGROUND)}
                disabled={isProcessing}
                className="flex flex-col items-center justify-center gap-3 p-4 rounded-2xl bg-white/5 border border-white/5 hover:bg-white/10 transition-all group disabled:opacity-50"
              >
                <div className="w-10 h-10 rounded-full bg-primary-container/20 flex items-center justify-center group-hover:scale-110 transition-transform">
                  <Layers size={20} className="text-primary" />
                </div>
                <span className="font-mono text-[10px] uppercase font-bold text-on-surface-variant tracking-tighter">White BG</span>
              </button>
              <button
                onClick={() => handleAIFilter(AI_PROMPTS.LIFESTYLE)}
                disabled={isProcessing}
                className="flex flex-col items-center justify-center gap-3 p-4 rounded-2xl bg-white/5 border border-white/5 hover:bg-white/10 transition-all group disabled:opacity-50"
              >
                <div className="w-10 h-10 rounded-full bg-tertiary-container/20 flex items-center justify-center group-hover:scale-110 transition-transform">
                  <ImageIcon size={20} className="text-tertiary" />
                </div>
                <span className="font-mono text-[10px] uppercase font-bold text-on-surface-variant tracking-tighter">Lifestyle</span>
              </button>
            </div>
          )}

          {activeTab === 'crop' && (
            <div className="mt-4 space-y-6">
              {/* Aspect Ratio Buttons */}
              <div className="flex items-center gap-2 justify-center">
                {(['free', '1:1', '4:3', '16:9'] as const).map((ratio) => (
                  <button
                    key={ratio}
                    onClick={() => handleRatioChange(ratio)}
                    className={cn(
                      "px-4 py-2 text-xs font-mono font-bold tracking-widest rounded-full transition-all border",
                      cropAspectRatio === ratio
                        ? "bg-primary/10 border-primary text-primary"
                        : "bg-surface-container border-outline-variant/20 text-on-surface-variant hover:text-on-surface"
                    )}
                  >
                    {ratio === 'free' ? 'Free' : ratio}
                  </button>
                ))}
              </div>

              {/* Apply Crop Button */}
              <div className="flex justify-center">
                <button
                  onClick={handleCrop}
                  className="flex items-center gap-2 px-8 py-3 rounded-full bg-gradient-to-br from-primary to-primary-container text-on-primary-container font-bold text-sm tracking-widest uppercase active:scale-95 transition-transform"
                >
                  <Crop size={16} />
                  Apply Crop
                </button>
              </div>

              {/* Reset to original */}
              <div className="flex justify-center">
                <button
                  onClick={() => {
                    updateCurrentPhoto({ url: currentPhoto.originalUrl });
                    setCropRect({ x: 0, y: 0, w: 1, h: 1 });
                  }}
                  className="text-[10px] font-mono font-bold uppercase text-on-surface-variant hover:text-primary transition-colors tracking-wider"
                >
                  Reset to Original
                </button>
              </div>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}