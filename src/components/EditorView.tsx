/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
  X, Check, Crop, Sliders, Sparkles,
  Contrast, Image as ImageIcon, RotateCcw,
  Layers, Wand2, Trash2, Save, Plus, XCircle
} from 'lucide-react';
import { usePinch } from '@use-gesture/react';
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
  hardwareBackRef?: React.MutableRefObject<(() => boolean) | null>;
}

interface CropRect {
  x: number;
  y: number;
  w: number;
  h: number;
}

export default function EditorView({ photos, onClose, onSave, onRetake, onDelete, onAddMore, settings, isSaving = false, hardwareBackRef }: EditorViewProps) {
  const [currentPhotos, setCurrentPhotos] = useState<PhotoData[]>(photos);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [itemName, setItemName] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [applyToAll, setApplyToAll] = useState(false);
  const [activeTab, setActiveTab] = useState<'adjust' | 'ai' | 'crop'>('adjust');
  // Default crop to the photo's own aspect ratio
  const [cropAspectRatio, setCropAspectRatio] = useState<AspectRatio | 'free'>(photos[0]?.aspectRatio || 'free');
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [fullscreenZoom, setFullscreenZoom] = useState(1);

  // Crop state
  const [cropRect, setCropRect] = useState<CropRect>({ x: 0, y: 0, w: 1, h: 1 });
  const [dragType, setDragType] = useState<string | null>(null);
  const cropContainerRef = useRef<HTMLDivElement>(null);
  const dragStartRef = useRef<{ px: number; py: number; rect: CropRect } | null>(null);

  const currentPhoto = currentPhotos[selectedIndex];

  // Register hardware back button handler
  useEffect(() => {
    if (!hardwareBackRef) return;
    hardwareBackRef.current = () => {
      if (isFullscreen) {
        setIsFullscreen(false);
        return true;
      }
      if (activeTab === 'crop') {
        handleCancelCrop();
        return true;
      }
      return false;
    };
    return () => { hardwareBackRef.current = null; };
  });

  // Reset fullscreen zoom when switching photos
  useEffect(() => {
    setFullscreenZoom(1);
  }, [selectedIndex]);

  // Reset crop when changing photos — default to photo's own aspect ratio
  useEffect(() => {
    if (!currentPhoto) return;
    setCropRect({ x: 0, y: 0, w: 1, h: 1 });
    if (activeTab === 'crop') {
      setCropAspectRatio(currentPhoto.aspectRatio);
    }
  }, [selectedIndex]);

  // When entering crop mode, set ratio to photo's ratio and apply
  useEffect(() => {
    if (activeTab === 'crop' && currentPhoto) {
      setApplyToAll(false);
      setCropAspectRatio(currentPhoto.aspectRatio);
      applyCropPreset(currentPhoto.aspectRatio);
    }
  }, [activeTab]);

  // Get the container's physical aspect ratio (width / height) accounting for orientation
  const getContainerRatio = () => {
    const isPortrait = window.innerHeight > window.innerWidth;
    if (currentPhoto.aspectRatio === '1:1') return 1;
    if (currentPhoto.aspectRatio === '4:3') return isPortrait ? 3 / 4 : 4 / 3;
    return isPortrait ? 9 / 16 : 16 / 9;
  };

  const applyCropPreset = (ratio: AspectRatio | 'free') => {
    // If ratio matches the photo's own ratio, full rect
    if (ratio !== 'free' && currentPhoto && ratio === currentPhoto.aspectRatio) {
      setCropRect({ x: 0, y: 0, w: 1, h: 1 });
      return;
    }
    if (ratio === 'free') {
      setCropRect({ x: 0, y: 0, w: 1, h: 1 });
      return;
    }

    // Calculate crop rect that produces the target physical aspect ratio
    // Container has physical ratio cRatio = containerW / containerH
    // Crop rect {w, h} produces physical ratio = (w * containerW) / (h * containerH) = targetRatio
    // So w/h = targetRatio / cRatio
    // On portrait devices, "4:3" and "16:9" should be portrait (taller than wide)
    const cRatio = getContainerRatio();
    const isPortrait = window.innerHeight > window.innerWidth;
    const tRatio = ratio === '1:1' ? 1 : ratio === '4:3' ? (isPortrait ? 3 / 4 : 4 / 3) : (isPortrait ? 9 / 16 : 16 / 9);
    const r = tRatio / cRatio; // ratio of normalized w to h

    let w: number, h: number;
    if (r >= 1) {
      // Crop is wider than tall in normalized coords
      w = 0.9;
      h = w / r;
    } else {
      // Crop is taller than wide in normalized coords
      h = 0.9;
      w = h * r;
    }

    setCropRect({ x: (1 - w) / 2, y: (1 - h) / 2, w, h });
  };

  const handleRatioChange = (ratio: AspectRatio | 'free') => {
    setCropAspectRatio(ratio);
    applyCropPreset(ratio);
  };

  // Pinch-to-zoom — highly sensitive, uses raw distance change
  const fullscreenBaseZoomRef = useRef(1);
  const fullscreenStartDistRef = useRef(0);
  const bindFullscreenPinch = usePinch(({ da: [distance], origin: [ox, oy], active, event }) => {
    event?.preventDefault();
    if (active && fullscreenStartDistRef.current === 0) {
      fullscreenStartDistRef.current = distance;
    }
    if (!active) {
      fullscreenStartDistRef.current = 0;
      return;
    }
    const startDist = fullscreenStartDistRef.current || distance;
    const ratio = distance / startDist;
    const newZoom = Math.max(0.5, Math.min(5, fullscreenBaseZoomRef.current * ratio));
    setFullscreenZoom(newZoom);
    fullscreenBaseZoomRef.current = newZoom;
  }, { pointer: { touch: true } });

  // Pointer down handler for crop — supports corners, edges, and move
  // Always stores initial pointer position and rect to avoid jump on first touch
  const handlePointerDown = useCallback((type: string) => (e: React.PointerEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragType(type);

    const container = cropContainerRef.current;
    if (!container) return;
    const rect = container.getBoundingClientRect();
    dragStartRef.current = {
      px: (e.clientX - rect.left) / rect.width,
      py: (e.clientY - rect.top) / rect.height,
      rect: { ...cropRect },
    };
  }, [cropRect]);

  // Drag move handler
  useEffect(() => {
    if (!dragType) return;

    const handleMove = (e: PointerEvent) => {
      e.preventDefault();
      const container = cropContainerRef.current;
      if (!container) return;

      const rect = container.getBoundingClientRect();
      const px = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
      const py = Math.max(0, Math.min(1, (e.clientY - rect.top) / rect.height));

      if (!dragStartRef.current) return;

      const { px: spx, py: spy, rect: orig } = dragStartRef.current;
      const dx = px - spx;
      const dy = py - spy;
      const MIN = 0.1;
      const origRight = orig.x + orig.w;
      const origBottom = orig.y + orig.h;

      setCropRect(() => {
        let x = orig.x, y = orig.y, w = orig.w, h = orig.h;

        // MOVE entire rect — delta from start
        if (dragType === 'move') {
          x = Math.max(0, Math.min(1 - orig.w, orig.x + dx));
          y = Math.max(0, Math.min(1 - orig.h, orig.y + dy));
          return { x, y, w, h };
        }

        // CORNER drags — apply delta from start position
        if (dragType === 'tl') {
          const newW = Math.max(MIN, orig.w - dx);
          const newH = Math.max(MIN, orig.h - dy);
          x = origRight - newW;
          y = origBottom - newH;
          w = newW;
          h = newH;
        } else if (dragType === 'tr') {
          w = Math.max(MIN, orig.w + dx);
          const newH = Math.max(MIN, orig.h - dy);
          y = origBottom - newH;
          h = newH;
        } else if (dragType === 'bl') {
          const newW = Math.max(MIN, orig.w - dx);
          x = origRight - newW;
          w = newW;
          h = Math.max(MIN, orig.h + dy);
        } else if (dragType === 'br') {
          w = Math.max(MIN, orig.w + dx);
          h = Math.max(MIN, orig.h + dy);
        }

        // EDGE drags — apply delta from start position
        if (dragType === 'top') {
          const newH = Math.max(MIN, orig.h - dy);
          y = origBottom - newH;
          h = newH;
        } else if (dragType === 'bottom') {
          h = Math.max(MIN, orig.h + dy);
        } else if (dragType === 'left') {
          const newW = Math.max(MIN, orig.w - dx);
          x = origRight - newW;
          w = newW;
        } else if (dragType === 'right') {
          w = Math.max(MIN, orig.w + dx);
        }

        // Apply aspect ratio constraint for non-free, non-move
        // Physical ratio = (w * containerW) / (h * containerH) = (w/h) * cRatio
        // For target physical ratio: w/h = targetRatio / cRatio
        if (cropAspectRatio !== 'free' && dragType !== 'move') {
          const isPortrait = window.innerHeight > window.innerWidth;
          const targetRatio = cropAspectRatio === '1:1' ? 1 :
            cropAspectRatio === '4:3' ? (isPortrait ? 3 / 4 : 4 / 3) : (isPortrait ? 9 / 16 : 16 / 9);
          const cRatio = getContainerRatio();
          const normTarget = targetRatio / cRatio; // normalized w/h for target physical ratio

          if (dragType === 'top' || dragType === 'bottom') {
            // Height changed — adjust width to match
            const newW = h * normTarget;
            const cx = x + w / 2;
            w = Math.min(newW, 1);
            x = Math.max(0, Math.min(cx - w / 2, 1 - w));
          } else if (dragType === 'left' || dragType === 'right') {
            // Width changed — adjust height to match
            const newH = w / normTarget;
            const cy = y + h / 2;
            h = Math.min(newH, 1);
            y = Math.max(0, Math.min(cy - h / 2, 1 - h));
          } else {
            // Corner drags — pin opposite corner
            const currentNormRatio = w / h;
            if (currentNormRatio > normTarget) {
              const newW = h * normTarget;
              if (dragType === 'tl' || dragType === 'bl') x = (x + w) - newW;
              w = newW;
            } else {
              const newH = w / normTarget;
              if (dragType === 'tl' || dragType === 'tr') y = (y + h) - newH;
              h = newH;
            }
          }
        }

        return { x, y, w, h };
      });
    };

    const handleUp = () => {
      setDragType(null);
      dragStartRef.current = null;
    };

    document.addEventListener('pointermove', handleMove);
    document.addEventListener('pointerup', handleUp);
    return () => {
      document.removeEventListener('pointermove', handleMove);
      document.removeEventListener('pointerup', handleUp);
    };
  }, [dragType, cropAspectRatio]);

  const updateCurrentPhoto = (updates: Partial<PhotoData>) => {
    const updated = currentPhotos.map((p, i) => {
      if (applyToAll || i === selectedIndex) return { ...p, ...updates };
      return p;
    });
    setCurrentPhotos(updated);
  };

  const handleAIFilter = async (prompt: string) => {
    setIsProcessing(true);
    try {
      if (applyToAll) {
        const updated = await Promise.all(currentPhotos.map(async (p) => {
          const result = await applyAIFilter(p.url, prompt, settings.geminiApiKey);
          return { ...p, url: result, filters: { ...p.filters, aiFilter: prompt, exposure: p.filters?.exposure || 0, contrast: p.filters?.contrast || 0, scale: p.filters?.scale || 1 } };
        }));
        setCurrentPhotos(updated);
      } else {
        const result = await applyAIFilter(currentPhoto.url, prompt, settings.geminiApiKey);
        updateCurrentPhoto({
          url: result,
          filters: { ...currentPhoto.filters, aiFilter: prompt, exposure: currentPhoto.filters?.exposure || 0, contrast: currentPhoto.filters?.contrast || 0, scale: currentPhoto.filters?.scale || 1 }
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
      const croppedUrl = canvas.toDataURL('image/jpeg', settings.imageQuality / 100);

      updateCurrentPhoto({
        url: croppedUrl,
        aspectRatio: cropAspectRatio === 'free' ? currentPhoto.aspectRatio : cropAspectRatio,
      });

      setCropRect({ x: 0, y: 0, w: 1, h: 1 });
      setActiveTab('adjust');
    };
    img.src = currentPhoto.url;
  };

  const handleCancelCrop = () => {
    setCropRect({ x: 0, y: 0, w: 1, h: 1 });
    setActiveTab('adjust');
  };

  const handleFinish = async () => {
    const MAX_DIM = 2048;
    const bakedPhotos = await Promise.all(currentPhotos.map(async (photo) => {
      const filters = photo.filters || { exposure: 0, contrast: 0, scale: 1 };
      if (filters.exposure === 0 && filters.contrast === 0 && filters.scale === 1) return photo;

      return new Promise<PhotoData>((resolve) => {
        const img = new Image();
        img.onload = () => {
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
          const scale = filters.scale;
          const scaledW = w * scale;
          const scaledH = h * scale;
          const offsetX = (w - scaledW) / 2;
          const offsetY = (h - scaledH) / 2;

          ctx.drawImage(img, offsetX, offsetY, scaledW, scaledH);
          resolve({ ...photo, url: canvas.toDataURL('image/jpeg', settings.imageQuality / 100) });
        };
        img.onerror = () => resolve(photo);
        img.src = photo.url;
      });
    }));

    onSave(bakedPhotos, itemName || undefined);
  };

  if (!currentPhoto) return null;

  const filters = currentPhoto.filters || { exposure: 0, contrast: 0, scale: 1 };

  // ===== FULLSCREEN PHOTO VIEWER =====
  if (isFullscreen) {
    return (
      <div className="fixed inset-0 z-[60] bg-black font-sans touch-none">
        <div
          {...(bindFullscreenPinch as any)()}
          className="absolute inset-0 flex items-center justify-center"
        >
          <img
            src={currentPhoto.url}
            className="max-w-full max-h-full object-contain transition-transform duration-150"
            style={{
              transform: `scale(${fullscreenZoom})`,
              filter: `brightness(${100 + filters.exposure}%) contrast(${100 + filters.contrast}%)`,
            }}
            alt="Fullscreen preview"
            draggable={false}
          />
        </div>

        <button
          onClick={() => setIsFullscreen(false)}
          className="absolute top-4 right-4 z-20 p-3 bg-black/30 rounded-full text-white/90 hover:text-white active:scale-95 transition-all"
        >
          <X size={22} />
        </button>

        {fullscreenZoom === 1 && (
          <div className="absolute bottom-8 left-0 right-0 text-center pointer-events-none">
            <p className="text-white/30 text-[10px] font-mono uppercase tracking-widest">Pinch to zoom</p>
          </div>
        )}
      </div>
    );
  }

  // ===== CROP MODE — Full-screen image with overlaid controls =====
  // Calculate actual rendered image dimensions based on aspect ratio
  const getCropImageDimensions = () => {
    const screenW = window.innerWidth;
    const screenH = window.innerHeight;
    const isPortrait = screenH > screenW;

    let imageRatio: number;
    if (currentPhoto.aspectRatio === '1:1') {
      imageRatio = 1;
    } else if (currentPhoto.aspectRatio === '4:3') {
      imageRatio = isPortrait ? 3 / 4 : 4 / 3;
    } else {
      imageRatio = isPortrait ? 9 / 16 : 16 / 9;
    }

    const screenRatio = screenW / screenH;
    let width: number, height: number;
    if (imageRatio > screenRatio) {
      width = screenW;
      height = screenW / imageRatio;
    } else {
      height = screenH;
      width = screenH * imageRatio;
    }
    return { width, height };
  };

  if (activeTab === 'crop') {
    const imgDims = getCropImageDimensions();
    // For tall aspect ratios (9:16 portrait), shrink height to leave room for bottom buttons
    const isTallCrop = imgDims.height >= imgDims.width;
    const maxCropHeight = isTallCrop ? window.innerHeight * 0.75 : window.innerHeight;
    const cropWidth = isTallCrop ? Math.min(imgDims.width, maxCropHeight * (imgDims.width / imgDims.height)) : imgDims.width;
    const cropHeight = isTallCrop ? Math.min(imgDims.height, maxCropHeight) : imgDims.height;

    return (
      <div className="fixed inset-0 z-50 bg-black font-sans touch-none">
        {/* Centered image container sized to actual image bounds */}
        <div className="absolute inset-0 flex items-center justify-center">
          <div
            ref={cropContainerRef}
            className="relative overflow-hidden"
            style={{
              width: cropWidth,
              height: cropHeight,
            }}
          >
            <img
              src={currentPhoto.url}
              className="w-full h-full select-none"
              alt="Crop preview"
              draggable={false}
            />

            {/* Dimmed areas outside crop */}
            <div className="absolute inset-0 pointer-events-none">
              <div className="absolute bg-black/60" style={{ top: 0, left: 0, right: 0, height: `${cropRect.y * 100}%` }} />
              <div className="absolute bg-black/60" style={{ bottom: 0, left: 0, right: 0, height: `${(1 - cropRect.y - cropRect.h) * 100}%` }} />
              <div className="absolute bg-black/60" style={{ top: `${cropRect.y * 100}%`, left: 0, width: `${cropRect.x * 100}%`, height: `${cropRect.h * 100}%` }} />
              <div className="absolute bg-black/60" style={{ top: `${cropRect.y * 100}%`, right: 0, width: `${(1 - cropRect.x - cropRect.w) * 100}%`, height: `${cropRect.h * 100}%` }} />
            </div>

            {/* Crop border — draggable for move */}
            <div
              className="absolute border-2 border-white cursor-move"
              style={{
                top: `${cropRect.y * 100}%`,
                left: `${cropRect.x * 100}%`,
                width: `${cropRect.w * 100}%`,
                height: `${cropRect.h * 100}%`,
                zIndex: 10,
                touchAction: 'none',
              }}
              onPointerDown={handlePointerDown('move')}
            >
              {/* 3x3 Grid */}
              <div className="absolute w-full h-full pointer-events-none">
                <div className="absolute left-1/3 top-0 bottom-0 w-px bg-white/30" />
                <div className="absolute left-2/3 top-0 bottom-0 w-px bg-white/30" />
                <div className="absolute top-1/3 left-0 right-0 h-px bg-white/30" />
                <div className="absolute top-2/3 left-0 right-0 h-px bg-white/30" />
              </div>

              {/* Edge handles — very large touch zones extending far inward */}
              <div className="absolute top-0 left-16 right-16 cursor-n-resize z-20"
                   style={{ height: '25%', transform: 'translateY(-5%)' }}
                   onPointerDown={handlePointerDown('top')} />
              <div className="absolute bottom-0 left-16 right-16 cursor-s-resize z-20"
                   style={{ height: '25%', transform: 'translateY(5%)' }}
                   onPointerDown={handlePointerDown('bottom')} />
              <div className="absolute left-0 top-16 bottom-16 cursor-w-resize z-20"
                   style={{ width: '25%', transform: 'translateX(-5%)' }}
                   onPointerDown={handlePointerDown('left')} />
              <div className="absolute right-0 top-16 bottom-16 cursor-e-resize z-20"
                   style={{ width: '25%', transform: 'translateX(5%)' }}
                   onPointerDown={handlePointerDown('right')} />

              {/* Corner handles — huge invisible touch targets, small visible dots */}
              {(['tl', 'tr', 'bl', 'br'] as const).map(corner => {
                const isTop = corner.startsWith('t');
                const isLeft = corner.endsWith('l');
                return (
                  <div
                    key={corner}
                    className="absolute flex items-center justify-center cursor-pointer z-30"
                    style={{
                      width: '35%',
                      height: '35%',
                      top: isTop ? 0 : '100%',
                      left: isLeft ? 0 : '100%',
                      transform: `${isTop ? 'translateY(-10%)' : 'translateY(-90%)'} ${isLeft ? 'translateX(-10%)' : 'translateX(-90%)'}`,
                      touchAction: 'none',
                    }}
                    onPointerDown={handlePointerDown(corner)}
                  >
                    <div className="w-5 h-5 bg-white rounded-full shadow-lg border-2 border-primary/60 active:scale-125 transition-transform"
                         style={{ position: 'absolute', top: isTop ? '10%' : '90%', left: isLeft ? '10%' : '90%', transform: 'translate(-50%, -50%)' }} />
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* Top bar */}
        <header className="absolute top-0 left-0 right-0 z-20 flex items-center justify-between px-4 h-14">
          <div className="absolute inset-0 bg-gradient-to-b from-black/60 to-transparent pointer-events-none" />
          <button
            onClick={handleCancelCrop}
            className="relative p-2.5 text-white/90 hover:text-white bg-black/30 transition-colors rounded-full active:scale-95"
          >
            <XCircle size={22} />
          </button>
          <nav className="relative flex items-center bg-black/40 rounded-full p-1 border border-white/10">
            {(['free', '1:1', '4:3', '16:9'] as const).map((ratio) => (
              <button
                key={ratio}
                onClick={() => handleRatioChange(ratio)}
                className={cn(
                  "px-3 py-1 font-mono text-[10px] tracking-widest transition-all",
                  cropAspectRatio === ratio
                    ? "text-primary bg-white/15 rounded-full shadow-sm"
                    : "text-white/60 hover:text-white"
                )}
              >
                {ratio === 'free' ? 'Free' : ratio}
              </button>
            ))}
          </nav>
          <div className="w-10" />
        </header>

        {/* Bottom bar with Cancel, Reset, Apply */}
        <footer className="absolute bottom-0 left-0 right-0 z-20 pb-6 pt-10">
          <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/40 to-transparent pointer-events-none" />
          <div className="relative flex justify-center items-center gap-3 px-4">
            <button
              onClick={handleCancelCrop}
              className="px-4 py-2.5 rounded-full bg-white/15 border border-white/15 text-white/80 font-mono text-xs font-bold uppercase tracking-widest active:scale-95 transition-transform"
            >
              Cancel
            </button>
            <button
              onClick={() => {
                updateCurrentPhoto({ url: currentPhoto.originalUrl });
                setCropRect({ x: 0, y: 0, w: 1, h: 1 });
              }}
              className="px-4 py-2.5 rounded-full bg-white/15 border border-white/15 text-white/80 font-mono text-xs font-bold uppercase tracking-widest active:scale-95 transition-transform"
            >
              Reset
            </button>
            <button
              onClick={handleCrop}
              className="flex items-center gap-2 px-6 py-2.5 rounded-full bg-gradient-to-br from-primary to-primary-container text-on-primary-container font-bold text-sm tracking-widest uppercase active:scale-95 transition-transform shadow-lg shadow-primary/30"
            >
              <Crop size={16} />
              Apply
            </button>
          </div>
        </footer>
      </div>
    );
  }

  // ===== NORMAL EDITOR MODE — Full-screen photo with overlaid transparent controls =====
  return (
    <div className="fixed inset-0 z-50 bg-black font-sans overflow-hidden">
      {/* Full-screen photo — fills entire screen */}
      <div
        className="absolute inset-0 flex items-center justify-center cursor-pointer"
        onClick={() => setIsFullscreen(true)}
      >
        <img
          src={currentPhoto.url}
          className="w-full h-full object-contain"
          style={{
            filter: `brightness(${100 + filters.exposure}%) contrast(${100 + filters.contrast}%)`,
          }}
          alt="Preview"
          draggable={false}
        />
      </div>

      {isProcessing && (
        <div className="absolute inset-0 bg-black/60 backdrop-blur-sm flex flex-col items-center justify-center text-white gap-4 z-10">
          <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
          <p className="font-mono text-xs font-bold tracking-[0.2em] uppercase animate-pulse">AI Processing...</p>
        </div>
      )}

      {/* Header — overlaid */}
      <header className="absolute top-0 left-0 right-0 z-20 flex items-center justify-between px-4 h-14">
        <div className="absolute inset-0 bg-gradient-to-b from-black/60 to-transparent pointer-events-none" />

        <button onClick={onClose} className="relative p-2.5 text-white/90 hover:text-white bg-black/30 rounded-full active:scale-95 transition-colors">
          <X size={20} />
        </button>

        <div className="relative flex items-center gap-2 bg-black/40 px-4 py-1.5 rounded-full border border-white/10" onClick={e => e.stopPropagation()}>
          <Sparkles size={14} className="text-primary" />
          <input
            type="text"
            placeholder="Item name..."
            value={itemName}
            onChange={(e) => setItemName(e.target.value)}
            className="bg-transparent border-none focus:ring-0 text-sm font-semibold text-white placeholder:text-white/40 w-28 text-center focus:outline-none"
          />
        </div>

        <button
          onClick={handleFinish}
          disabled={isSaving}
          className="relative bg-gradient-to-br from-primary to-primary-container text-on-primary-container px-4 py-2 rounded-full text-xs font-bold tracking-widest uppercase active:scale-95 transition-all disabled:opacity-60"
        >
          <span className="flex items-center gap-1.5">
            <Save size={14} />
            {isSaving ? '...' : 'Save'}
          </span>
        </button>
      </header>

      {/* Photo actions — right side */}
      <div className="absolute top-16 right-3 z-20 flex flex-col gap-2">
        <button
          onClick={(e) => { e.stopPropagation(); onRetake(currentPhoto.id); }}
          className="p-2 bg-black/30 rounded-full text-white/80 hover:text-white transition-colors active:scale-95"
          title="Re-take"
        >
          <RotateCcw size={16} />
        </button>
        <button
          onClick={(e) => {
            e.stopPropagation();
            onDelete(currentPhoto.id);
            if (selectedIndex >= currentPhotos.length - 1) {
              setSelectedIndex(Math.max(0, currentPhotos.length - 2));
            }
            setCurrentPhotos(prev => prev.filter(p => p.id !== currentPhoto.id));
          }}
          className="p-2 bg-black/30 rounded-full text-error/80 hover:text-error transition-colors active:scale-95"
          title="Delete"
        >
          <Trash2 size={16} />
        </button>
      </div>

      {/* Bottom controls — overlaid */}
      <div className="absolute bottom-0 left-0 right-0 z-20" onClick={e => e.stopPropagation()}>
        <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/40 to-transparent pointer-events-none" />
        <div className="relative px-4 pt-8 pb-4 pointer-events-auto">
          {/* Thumbnail strip */}
          <div className="flex gap-2.5 overflow-x-auto no-scrollbar items-center mb-3">
            {currentPhotos.map((p, i) => (
              <button
                key={p.id}
                onClick={() => setSelectedIndex(i)}
                className={cn(
                  "shrink-0 w-11 h-11 rounded-lg overflow-hidden border-2 transition-all",
                  selectedIndex === i
                    ? "border-primary p-0.5 bg-primary/20"
                    : "border-white/15 hover:border-primary/50 opacity-50"
                )}
              >
                <img src={p.url} className={cn("w-full h-full object-cover", selectedIndex === i && "rounded")} />
              </button>
            ))}
            <button
              onClick={onAddMore}
              className="shrink-0 w-11 h-11 rounded-lg border-2 border-dashed border-white/20 flex items-center justify-center hover:border-primary/50 hover:bg-white/5 transition-all"
            >
              <Plus size={14} className="text-white/40" />
            </button>
          </div>

          {/* Toggle + Tabs */}
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <span className="text-[9px] font-mono font-bold uppercase text-white/50 tracking-wider">All</span>
              <button
                onClick={() => setApplyToAll(!applyToAll)}
                className={cn(
                  "w-8 h-[18px] rounded-full relative transition-colors",
                  applyToAll ? "bg-primary" : "bg-white/20"
                )}
              >
                <div className={cn(
                  "absolute top-[2px] w-[14px] h-[14px] rounded-full transition-all",
                  applyToAll ? "right-[2px] bg-on-primary" : "left-[2px] bg-white/50"
                )} />
              </button>
            </div>

            <nav className="flex items-center gap-6">
              {([
                { key: 'adjust' as const, icon: Sliders },
                { key: 'ai' as const, icon: Sparkles },
                { key: 'crop' as const, icon: Crop },
              ]).map(({ key, icon: Icon }) => (
                <button
                  key={key}
                  onClick={() => setActiveTab(key)}
                  className={cn(
                    "flex flex-col items-center gap-0.5 group transition-colors",
                    activeTab === key ? "text-primary" : "text-white/50 group-hover:text-white/80"
                  )}
                >
                  <Icon size={18} />
                  <div className={cn("w-1 h-1 rounded-full transition-colors", activeTab === key ? "bg-primary" : "bg-transparent")} />
                </button>
              ))}
            </nav>
          </div>

          {/* Tab Content */}
          {activeTab === 'adjust' && (
            <div className="space-y-2">
              {[
                { label: 'Exposure', value: filters.exposure, display: `${filters.exposure >= 0 ? '+' : ''}${filters.exposure}`, min: -100, max: 100, field: 'exposure' as const },
                { label: 'Contrast', value: filters.contrast, display: `${filters.contrast >= 0 ? '+' : ''}${filters.contrast}`, min: -100, max: 100, field: 'contrast' as const },
                { label: 'Scale', value: filters.scale, display: `${filters.scale.toFixed(1)}x`, min: 0.1, max: 2, step: 0.1, field: 'scale' as const },
              ].map(({ label, value, display, field, ...rest }) => (
                <div key={field} className="space-y-0.5">
                  <div className="flex justify-between items-center">
                    <label className="text-[10px] font-mono font-bold uppercase tracking-widest text-white/50">{label}</label>
                    <span className="text-[11px] font-mono text-primary">{display}</span>
                  </div>
                  <input
                    type="range"
                    value={value}
                    onChange={(e) => updateCurrentPhoto({
                      filters: { ...filters, [field]: field === 'scale' ? parseFloat(e.target.value) : parseInt(e.target.value) }
                    })}
                    className="w-full h-1 bg-white/20 rounded-lg appearance-none cursor-pointer slider-obsidian"
                    {...rest}
                  />
                </div>
              ))}
            </div>
          )}

          {activeTab === 'ai' && (
            <div className="grid grid-cols-3 gap-2">
              {[
                { prompt: AI_PROMPTS.ENHANCE, icon: Wand2, label: 'Enhance', color: 'secondary' },
                { prompt: AI_PROMPTS.CLEAN_BACKGROUND, icon: Layers, label: 'White BG', color: 'primary' },
                { prompt: AI_PROMPTS.LIFESTYLE, icon: ImageIcon, label: 'Lifestyle', color: 'tertiary' },
              ].map(({ prompt, icon: Icon, label, color }) => (
                <button
                  key={label}
                  onClick={() => handleAIFilter(prompt)}
                  disabled={isProcessing}
                  className="flex flex-col items-center justify-center gap-1.5 p-2.5 rounded-2xl bg-white/5 border border-white/10 hover:bg-white/10 transition-all group disabled:opacity-50"
                >
                  <div className={`w-8 h-8 rounded-full bg-${color}-container/20 flex items-center justify-center group-hover:scale-110 transition-transform`}>
                    <Icon size={16} className={`text-${color}`} />
                  </div>
                  <span className="font-mono text-[9px] uppercase font-bold text-white/50 tracking-tighter">{label}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}