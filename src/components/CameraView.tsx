/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useRef, useState, useEffect, useCallback } from 'react';
import { flushSync } from 'react-dom';
import { RefreshCw, Settings as SettingsIcon, ZoomIn, ZoomOut, Check, Trash2, RotateCcw, ArrowRight } from 'lucide-react';
import { usePinch } from '@use-gesture/react';
import { AspectRatio, PhotoData, AppSettings } from '../types';
import { cn } from '../utils';

interface CameraViewProps {
  onCapture: (photo: PhotoData) => void;
  onOpenSettings: () => void;
  onProcess: () => void;
  settings: AppSettings;
  photosCount: number;
  lastPhotoUrl: string | null;
  retakeId: string | null;
  onRetakeComplete: (photo: PhotoData) => void;
  onCameraResolution?: (width: number, height: number) => void;
  hardwareBackRef?: React.MutableRefObject<(() => boolean) | null>;
}

export default function CameraView({
  onCapture,
  onOpenSettings,
  onProcess,
  settings,
  photosCount,
  lastPhotoUrl,
  retakeId,
  onRetakeComplete,
  onCameraResolution,
  hardwareBackRef
}: CameraViewProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [aspectRatio, setAspectRatio] = useState<AspectRatio>(settings.defaultAspectRatio);
  const [zoom, setZoom] = useState(1);
  const zoomPresets = [0.5, 1, 2, 5];
  const [isCameraReady, setIsCameraReady] = useState(false);
  const [facingMode, setFacingMode] = useState<'user' | 'environment'>('environment');
  const [error, setError] = useState<string | null>(null);
  const [previewPhoto, setPreviewPhoto] = useState<PhotoData | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [frozenFrame, setFrozenFrame] = useState<string | null>(null);
  const [captureFlash, setCaptureFlash] = useState(false);

  // Cached ImageCapture instance — avoid recreating per capture
  const imageCaptureRef = useRef<ImageCapture | null>(null);

  // Reusable canvas for frozen frame — avoid GC pressure
  const frozenCanvasRef = useRef<HTMLCanvasElement | null>(null);

  // Register hardware back button handler — dismiss preview if showing
  useEffect(() => {
    if (!hardwareBackRef) return;
    hardwareBackRef.current = () => {
      if (previewPhoto) {
        setPreviewPhoto(null);
        return true;
      }
      return false;
    };
    return () => { hardwareBackRef.current = null; };
  });

  const startCamera = useCallback(async () => {
    setError(null);
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode,
          width: { ideal: 1920 },
          height: { ideal: 1080 }
        }
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        setIsCameraReady(true);
        const track = stream.getVideoTracks()[0];
        if (track) {
          const caps = (track as any).getCapabilities?.();
          if (caps?.width?.max && caps?.height?.max) {
            onCameraResolution?.(caps.width.max, caps.height.max);
          } else {
            const trackSettings = track.getSettings();
            if (trackSettings.width && trackSettings.height) {
              onCameraResolution?.(trackSettings.width, trackSettings.height);
            }
          }
        }
      }
    } catch (err) {
      console.error("Error accessing camera:", err);
      setError("Unable to access camera. Please check permissions.");
      setIsCameraReady(false);
    }
  }, [facingMode, onCameraResolution]);

  useEffect(() => {
    startCamera();
    return () => {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(track => track.stop());
        streamRef.current = null;
      }
    };
  }, [startCamera]);

  const getCropRect = (srcW: number, srcH: number) => {
    const ratioW = isPortrait
      ? (aspectRatio === '1:1' ? 1 : aspectRatio === '4:3' ? 3 : 9)
      : (aspectRatio === '1:1' ? 1 : aspectRatio === '4:3' ? 4 : 16);
    const ratioH = isPortrait
      ? (aspectRatio === '1:1' ? 1 : aspectRatio === '4:3' ? 4 : 16)
      : (aspectRatio === '1:1' ? 1 : aspectRatio === '4:3' ? 3 : 9);

    let cropW: number, cropH: number;
    if (aspectRatio === '1:1') {
      const size = Math.min(srcW, srcH);
      cropW = size;
      cropH = size;
    } else if (srcW / srcH > ratioW / ratioH) {
      cropH = srcH;
      cropW = srcH * (ratioW / ratioH);
    } else {
      cropW = srcW;
      cropH = srcW / (ratioW / ratioH);
    }

    const zoomFactor = 1 / zoom;
    const zoomedW = cropW * zoomFactor;
    const zoomedH = cropH * zoomFactor;
    const cropX = (srcW - zoomedW) / 2;
    const cropY = (srcH - zoomedH) / 2;

    return { cropX, cropY, cropW, cropH };
  };

  const cropBlobToPhoto = async (blob: Blob): Promise<PhotoData> => {
    const fullBitmap = await createImageBitmap(blob);
    const srcW = fullBitmap.width;
    const srcH = fullBitmap.height;

    const { cropX, cropY, cropW, cropH } = getCropRect(srcW, srcH);

    const croppedBitmap = await createImageBitmap(
      fullBitmap,
      Math.round(cropX), Math.round(cropY),
      Math.round(cropW), Math.round(cropH)
    );
    fullBitmap.close();

    const canvas = document.createElement('canvas');
    canvas.width = croppedBitmap.width;
    canvas.height = croppedBitmap.height;
    const ctx = canvas.getContext('2d')!;

    if (facingMode === 'user') {
      ctx.translate(canvas.width, 0);
      ctx.scale(-1, 1);
    }
    ctx.drawImage(croppedBitmap, 0, 0);
    croppedBitmap.close();

    const dataUrl = canvas.toDataURL('image/jpeg', 0.95);
    return {
      id: Math.random().toString(36).substr(2, 9),
      url: dataUrl,
      originalUrl: dataUrl,
      aspectRatio,
      timestamp: Date.now(),
      filters: { exposure: 0, contrast: 0, scale: 1 }
    };
  };

  /**
   * Grab frozen frame using blob URL (faster than toDataURL — async, off-main-thread).
   * Returns { url, revoke } or null. Caller must call revoke() when done.
   */
  const grabFrozenFrameAsync = async (): Promise<{ url: string; revoke: () => void } | null> => {
    const video = videoRef.current;
    if (!video) return null;
    let canvas = frozenCanvasRef.current;
    if (!canvas) {
      canvas = document.createElement('canvas');
      frozenCanvasRef.current = canvas;
    }
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext('2d')!;
    ctx.setTransform(1, 0, 0, 1, 0, 0); // reset transform
    if (facingMode === 'user') {
      ctx.translate(canvas.width, 0);
      ctx.scale(-1, 1);
    }
    ctx.drawImage(video, 0, 0);

    // toBlob is async and can run off-main-thread — much faster than toDataURL
    const blob = await new Promise<Blob | null>(resolve => canvas!.toBlob(resolve, 'image/jpeg', 0.5));
    if (!blob) return null;
    const url = URL.createObjectURL(blob);
    return { url, revoke: () => URL.revokeObjectURL(url) };
  };

  const waitForPaint = () => new Promise<void>(resolve => {
    // flushSync already forces synchronous DOM commit — single rAF is enough
    requestAnimationFrame(() => resolve());
  });

  const frozenFrameRevokeRef = useRef<(() => void) | null>(null);

  const handleCapture = async () => {
    if (!videoRef.current || !streamRef.current) return;

    // 1. Grab frozen frame asynchronously (toBlob is faster than toDataURL)
    const frozen = await grabFrozenFrameAsync();

    // 2. Use flushSync to synchronously paint the frozen frame overlay
    if (frozen) {
      // Revoke previous frozen frame blob URL if any
      frozenFrameRevokeRef.current?.();
      frozenFrameRevokeRef.current = frozen.revoke;
      flushSync(() => {
        setFrozenFrame(frozen.url);
        setIsProcessing(true);
      });
    } else {
      setIsProcessing(true);
    }

    // 3. Wait for the frozen frame to be composited (single rAF — flushSync already committed)
    await waitForPaint();

    // 4. Trigger the capture-area flash effect
    setCaptureFlash(true);

    // Helper to clean up frozen frame
    const cleanupFrozen = () => {
      frozenFrameRevokeRef.current?.();
      frozenFrameRevokeRef.current = null;
      setFrozenFrame(null);
    };

    try {
      // 5. Capture at full sensor resolution
      const track = streamRef.current.getVideoTracks()[0];
      if (track && typeof ImageCapture !== 'undefined') {
        try {
          // Reuse cached ImageCapture instance
          if (!imageCaptureRef.current || (imageCaptureRef.current as any).track !== track) {
            imageCaptureRef.current = new ImageCapture(track);
          }
          const blob = await imageCaptureRef.current.takePhoto();
          const photo = await cropBlobToPhoto(blob);

          setIsProcessing(false);
          setCaptureFlash(false);
          // NOTE: Do NOT clear frozenFrame here!
          // The video's onPlaying callback will clear it once the stream recovers.
          if (settings.showPreviewAfterCapture) {
            setPreviewPhoto(photo);
          } else if (retakeId) {
            onRetakeComplete(photo);
          } else {
            onCapture(photo);
          }
          return;
        } catch (err) {
          console.warn('ImageCapture.takePhoto() failed, falling back to canvas:', err);
        }
      }

      // Fallback: capture from video element
      const video = videoRef.current;
      const canvas = document.createElement('canvas');
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      const ctx = canvas.getContext('2d')!;
      if (facingMode === 'user') {
        ctx.translate(canvas.width, 0);
        ctx.scale(-1, 1);
      }
      ctx.drawImage(video, 0, 0);

      const videoBlob = await new Promise<Blob>((resolve) => {
        canvas.toBlob((b) => resolve(b!), 'image/jpeg', 0.95);
      });
      const photo = await cropBlobToPhoto(videoBlob);

      setIsProcessing(false);
      setCaptureFlash(false);
      if (settings.showPreviewAfterCapture) {
        setPreviewPhoto(photo);
      } else if (retakeId) {
        onRetakeComplete(photo);
      } else {
        onCapture(photo);
      }
    } catch (err) {
      console.error('Capture failed:', err);
      setIsProcessing(false);
      setCaptureFlash(false);
      cleanupFrozen();
    }
  };

  const handleKeep = () => {
    if (!previewPhoto) return;
    if (retakeId) {
      onRetakeComplete(previewPhoto);
    } else {
      onCapture(previewPhoto);
    }
    setPreviewPhoto(null);
  };

  const handleRetake = () => {
    setPreviewPhoto(null);
  };

  const handleDeletePreview = () => {
    setPreviewPhoto(null);
  };

  const bind = usePinch(({ offset: [d] }) => {
    const newZoom = Math.max(0.5, Math.min(5, 1 + d / 100));
    setZoom(newZoom);
  });

  const [isPortrait, setIsPortrait] = useState(true);
  useEffect(() => {
    const check = () => setIsPortrait(window.innerHeight > window.innerWidth);
    check();
    window.addEventListener('resize', check);
    return () => window.removeEventListener('resize', check);
  }, []);

  const getFrameAspectRatio = () => {
    if (isPortrait) {
      switch (aspectRatio) {
        case '1:1': return '1 / 1';
        case '4:3': return '3 / 4';
        case '16:9': return '9 / 16';
        default: return '1 / 1';
      }
    }
    switch (aspectRatio) {
      case '1:1': return '1 / 1';
      case '4:3': return '4 / 3';
      case '16:9': return '16 / 9';
      default: return '1 / 1';
    }
  };

  const getFrameRatioValue = () => {
    if (isPortrait) {
      switch (aspectRatio) {
        case '1:1': return 1;
        case '4:3': return 3 / 4;
        case '16:9': return 9 / 16;
        default: return 1;
      }
    }
    switch (aspectRatio) {
      case '1:1': return 1;
      case '4:3': return 4 / 3;
      case '16:9': return 16 / 9;
      default: return 1;
    }
  };

  return (
    <div
      {...(bind as any)()}
      className="relative h-full w-full bg-black overflow-hidden font-sans touch-none"
    >
      {/* ===== LAYER 0: Video feed — ALWAYS in DOM, never unmounted ===== */}
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted
        onPlaying={() => {
          // Video stream has recovered from ImageCapture interruption.
          // Safe to remove frozen frame now — user will see live feed.
          frozenFrameRevokeRef.current?.();
          frozenFrameRevokeRef.current = null;
          setFrozenFrame(null);
        }}
        className={cn(
          "absolute inset-0 w-full h-full object-cover transition-transform duration-300",
          facingMode === 'user' && "-scale-x-100"
        )}
        style={{ transform: `${facingMode === 'user' ? 'scaleX(-1) ' : ''}scale(${zoom})` }}
      />

      {/* ===== LAYER 1: Frozen frame — covers video during capture/recovery ===== */}
      {frozenFrame && (
        <img
          src={frozenFrame}
          className="absolute inset-0 w-full h-full object-cover pointer-events-none"
          style={{ zIndex: 5 }}
        />
      )}

      {/* ===== LAYER 2: Preview photo — covers everything when reviewing ===== */}
      {previewPhoto && (
        <img
          src={previewPhoto.url}
          className="absolute inset-0 w-full h-full object-contain"
          style={{ zIndex: 10 }}
        />
      )}

      {/* ===== LAYER 3: Error overlay ===== */}
      {error && !previewPhoto && (
        <div className="absolute inset-0 flex items-center justify-center p-6 text-center bg-black/80" style={{ zIndex: 25 }}>
          <div className="space-y-4">
            <p className="text-on-surface-variant text-sm">{error}</p>
            <button
              onClick={startCamera}
              className="px-4 py-2 bg-primary text-on-primary rounded-full text-xs font-bold"
            >
              Retry
            </button>
          </div>
        </div>
      )}

      {/* ===== LAYER 4: Capture Frame Guide ===== */}
      {!previewPhoto && (
        <div className="absolute inset-0 pointer-events-none flex items-center justify-center" style={{ zIndex: 8 }}>
          <div
            className="relative rounded-md"
            style={{
              aspectRatio: getFrameAspectRatio(),
              width: `min(100vw, calc(100vh * ${getFrameRatioValue()}))`,
              boxShadow: '0 0 0 200vmax rgba(180, 180, 195, 0.35)',
              border: '1.5px solid rgba(255, 255, 255, 0.35)',
            }}
          >
            <div className="absolute inset-0 pointer-events-none opacity-30 rounded-md overflow-hidden">
              <div className="absolute top-1/3 left-0 grid-line-h"></div>
              <div className="absolute top-2/3 left-0 grid-line-h"></div>
              <div className="absolute left-1/3 top-0 grid-line-v"></div>
              <div className="absolute left-2/3 top-0 grid-line-v"></div>
            </div>
          </div>
        </div>
      )}

      {/* ===== LAYER 5: Capture-area flash ===== */}
      {captureFlash && (
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none" style={{ zIndex: 12 }}>
          <div
            className="rounded-md bg-white/90"
            style={{
              aspectRatio: getFrameAspectRatio(),
              width: `min(100vw, calc(100vh * ${getFrameRatioValue()}))`,
              animation: 'capture-flash 300ms ease-out forwards',
            }}
            onAnimationEnd={() => setCaptureFlash(false)}
          />
        </div>
      )}

      {/* ===== OVERLAID CONTROLS (z-20+) ===== */}

      {/* Top Bar Overlay */}
      <header className="absolute top-0 left-0 right-0 flex items-center justify-between px-4 h-14" style={{ zIndex: 20 }}>
        <div className="absolute inset-0 bg-gradient-to-b from-black/60 to-transparent pointer-events-none" />

        <button
          onClick={onOpenSettings}
          className="relative p-2.5 text-white/90 hover:text-white bg-black/30 backdrop-blur-md transition-colors rounded-full active:scale-95"
        >
          <SettingsIcon size={20} />
        </button>

        {!previewPhoto && (
          <nav className="relative flex items-center bg-black/40 backdrop-blur-md rounded-full p-1 border border-white/10">
            {(['1:1', '4:3', '16:9'] as AspectRatio[]).map((ratio) => (
              <button
                key={ratio}
                onClick={() => setAspectRatio(ratio)}
                className={cn(
                  "px-3 py-1 font-mono text-[10px] tracking-widest transition-all",
                  aspectRatio === ratio
                    ? "text-primary bg-white/15 rounded-full shadow-sm"
                    : "text-white/60 hover:text-white"
                )}
              >
                {ratio}
              </button>
            ))}
          </nav>
        )}

        {!previewPhoto && (
          <button
            onClick={() => setFacingMode(prev => prev === 'user' ? 'environment' : 'user')}
            className="relative p-2.5 text-white/90 hover:text-white bg-black/30 backdrop-blur-md transition-colors rounded-full active:scale-95"
          >
            <RefreshCw size={20} />
          </button>
        )}
      </header>

      {/* Zoom Controls Overlay */}
      {!previewPhoto && (
        <div className="absolute left-0 right-0 flex flex-col items-center gap-3"
          style={{ zIndex: 20, bottom: '120px' }}
        >
          <div className="w-full max-w-[260px] px-4">
            <div className="flex justify-between items-center mb-1.5">
              <span className="font-mono text-[9px] text-white/50 uppercase">.5x</span>
              <span className="font-mono text-sm font-bold text-white drop-shadow-lg">
                {zoom.toFixed(1)}
                <span className="text-[9px] font-normal text-white/50 ml-0.5">x</span>
              </span>
              <span className="font-mono text-[9px] text-white/50 uppercase">5x</span>
            </div>
            <input
              type="range"
              min="0.5"
              max="5"
              step="0.1"
              value={zoom}
              onChange={(e) => setZoom(parseFloat(e.target.value))}
              className="w-full h-1 bg-white/20 rounded-lg appearance-none cursor-pointer slider-obsidian"
            />
          </div>

          <div className="flex items-center gap-3 bg-black/30 backdrop-blur-md px-3 py-1.5 rounded-full border border-white/10">
            {zoomPresets.map((preset) => (
              <button
                key={preset}
                onClick={() => setZoom(preset)}
                className={cn(
                  "font-mono text-[11px] transition-all",
                  zoom === preset
                    ? "text-primary bg-white/15 rounded-full w-8 h-8 flex items-center justify-center font-bold"
                    : "text-white/60 hover:text-white"
                )}
              >
                {preset === 0.5 ? '.5' : `${preset}${zoom === preset ? 'x' : ''}`}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Bottom Action Bar Overlay */}
      <footer className="absolute bottom-0 left-0 right-0 pb-6 pt-8" style={{ zIndex: 20 }}>
        <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/40 to-transparent pointer-events-none" />

        {!previewPhoto ? (
          <div className="relative flex justify-between items-center px-8">
            <div className="flex flex-col items-center gap-1 group cursor-pointer w-14">
              <div className="relative w-12 h-12 rounded-xl bg-white/15 backdrop-blur-md border border-white/15 flex items-center justify-center overflow-hidden">
                {lastPhotoUrl ? (
                  <>
                    <img src={lastPhotoUrl} className="w-full h-full object-cover rounded-xl" />
                    <span className="absolute inset-0 flex items-center justify-center bg-black/40 text-white font-bold text-sm rounded-xl">
                      {photosCount}
                    </span>
                  </>
                ) : (
                  <span className="text-white/30 text-lg">+</span>
                )}
              </div>
            </div>

            <div className="relative flex items-center justify-center">
              <div className="absolute inset-0 bg-white/10 blur-xl rounded-full scale-150" />
              <button
                onClick={handleCapture}
                disabled={!isCameraReady || isProcessing}
                className="w-[72px] h-[72px] rounded-full border-[3px] border-white/80 bg-transparent p-1.5 transition-transform active:scale-90 duration-75 relative z-10"
              >
                <div className="w-full h-full rounded-full bg-white/90 flex items-center justify-center">
                  <div className="w-11 h-11 rounded-full border border-white/30" />
                </div>
              </button>
            </div>

            <div className="w-12 flex justify-end">
              <button
                onClick={onProcess}
                disabled={photosCount === 0}
                className={cn(
                  "w-12 h-12 rounded-full flex items-center justify-center active:scale-90 transition-transform",
                  photosCount > 0
                    ? "bg-gradient-to-br from-primary to-primary-container text-on-primary shadow-lg shadow-primary/30"
                    : "bg-white/10 text-white/20 cursor-not-allowed"
                )}
              >
                <ArrowRight size={20} />
              </button>
            </div>
          </div>
        ) : (
          <div className="relative w-full max-w-sm mx-auto">
            <div className="flex justify-between items-center px-6">
              <button
                onClick={handleDeletePreview}
                className="flex flex-col items-center gap-2 text-error"
              >
                <div className="w-14 h-14 rounded-full bg-black/40 backdrop-blur-md flex items-center justify-center border border-white/10">
                  <Trash2 size={24} />
                </div>
                <span className="font-mono text-[10px] font-bold uppercase tracking-wider drop-shadow-lg">Delete</span>
              </button>

              <button
                onClick={handleRetake}
                className="flex flex-col items-center gap-2 text-white/80"
              >
                <div className="w-14 h-14 rounded-full bg-black/40 backdrop-blur-md flex items-center justify-center border border-white/10">
                  <RotateCcw size={24} />
                </div>
                <span className="font-mono text-[10px] font-bold uppercase tracking-wider drop-shadow-lg">Re-take</span>
              </button>

              <button
                onClick={handleKeep}
                className="flex flex-col items-center gap-2 text-primary"
              >
                <div className="w-14 h-14 rounded-full bg-primary/20 backdrop-blur-md flex items-center justify-center border border-primary/30">
                  <Check size={24} />
                </div>
                <span className="font-mono text-[10px] font-bold uppercase tracking-wider drop-shadow-lg">Keep</span>
              </button>
            </div>
          </div>
        )}
      </footer>
    </div>
  );
}