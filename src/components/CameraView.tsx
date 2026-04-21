/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useRef, useState, useEffect, useCallback } from 'react';
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
  const [shutterFlash, setShutterFlash] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [frozenFrame, setFrozenFrame] = useState<string | null>(null); // low-res frame to show during capture

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
      // Request moderate resolution for smooth viewfinder — ImageCapture.takePhoto()
      // captures at full sensor resolution regardless of stream settings
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
        // Read actual camera sensor's maximum resolution
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
    if (!previewPhoto) {
      startCamera();
    }
    return () => {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(track => track.stop());
        streamRef.current = null;
      }
    };
  }, [startCamera, previewPhoto]);

  /**
   * Compute crop rectangle for the selected aspect ratio and zoom level.
   */
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

    // Apply zoom: crop a smaller centered region
    const zoomFactor = 1 / zoom;
    const zoomedW = cropW * zoomFactor;
    const zoomedH = cropH * zoomFactor;
    const cropX = (srcW - zoomedW) / 2;
    const cropY = (srcH - zoomedH) / 2;

    return { cropX, cropY, cropW, cropH };
  };

  /**
   * Crop a Blob from ImageCapture using GPU-accelerated createImageBitmap.
   */
  const cropBlobToPhoto = async (blob: Blob): Promise<PhotoData> => {
    // Decode the blob into a bitmap (GPU-accelerated)
    const fullBitmap = await createImageBitmap(blob);
    const srcW = fullBitmap.width;
    const srcH = fullBitmap.height;

    const { cropX, cropY, cropW, cropH } = getCropRect(srcW, srcH);

    // Crop the bitmap directly (GPU-accelerated, no full-res canvas needed)
    const croppedBitmap = await createImageBitmap(
      fullBitmap,
      Math.round(cropX), Math.round(cropY),
      Math.round(cropW), Math.round(cropH)
    );
    fullBitmap.close();

    // Draw the smaller cropped bitmap to canvas
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
   * Grab a quick low-res frame from the video element to use as a frozen preview.
   */
  const grabFrozenFrame = (): string | null => {
    const video = videoRef.current;
    if (!video) return null;
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext('2d')!;
    if (facingMode === 'user') {
      ctx.translate(canvas.width, 0);
      ctx.scale(-1, 1);
    }
    ctx.drawImage(video, 0, 0);
    return canvas.toDataURL('image/jpeg', 0.7);
  };

  const handleCapture = async () => {
    if (!videoRef.current || !streamRef.current) return;

    // Immediately freeze the current video frame to prevent black screen
    const frozen = grabFrozenFrame();
    if (frozen) setFrozenFrame(frozen);

    // Trigger shutter flash animation
    setShutterFlash(true);
    setIsProcessing(true);

    try {
      // Try ImageCapture.takePhoto() for full sensor resolution
      const track = streamRef.current.getVideoTracks()[0];
      if (track && typeof ImageCapture !== 'undefined') {
        try {
          const imageCapture = new ImageCapture(track);
          const blob = await imageCapture.takePhoto();
          const photo = await cropBlobToPhoto(blob);

          setIsProcessing(false);
          setFrozenFrame(null);
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

      // Crop via createImageBitmap for consistency
      const videoBlob = await new Promise<Blob>((resolve) => {
        canvas.toBlob((b) => resolve(b!), 'image/jpeg', 0.95);
      });
      const photo = await cropBlobToPhoto(videoBlob);

      setIsProcessing(false);
      setFrozenFrame(null);
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
      setFrozenFrame(null);
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

  // Detect portrait orientation for frame guide and capture
  const [isPortrait, setIsPortrait] = useState(true);
  useEffect(() => {
    const check = () => setIsPortrait(window.innerHeight > window.innerWidth);
    check();
    window.addEventListener('resize', check);
    return () => window.removeEventListener('resize', check);
  }, []);

  // Aspect ratio for the frame guide (CSS aspect-ratio value)
  const getFrameAspectRatio = () => {
    if (isPortrait) {
      switch (aspectRatio) {
        case '1:1': return '1 / 1';
        case '4:3': return '3 / 4';   // portrait 4:3
        case '16:9': return '9 / 16';  // portrait 16:9
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

  // Numeric W/H ratio for CSS min() width calculation
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
      {/* Three-state rendering: preview photo > frozen frame > live video */}
      {previewPhoto ? (
        /* State 1: Show captured photo preview */
        <img src={previewPhoto.url} className="absolute inset-0 w-full h-full object-contain" />
      ) : frozenFrame ? (
        /* State 2: Frozen frame during capture processing — hides video to prevent play-button flash */
        <img src={frozenFrame} className="absolute inset-0 w-full h-full object-cover" />
      ) : (
        /* State 3: Live camera feed */
        <>
          <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            className={cn(
              "absolute inset-0 w-full h-full object-cover transition-transform duration-300",
              facingMode === 'user' && "-scale-x-100"
            )}
            style={{ transform: `${facingMode === 'user' ? 'scaleX(-1) ' : ''}scale(${zoom})` }}
          />
          {error && (
            <div className="absolute inset-0 z-30 flex items-center justify-center p-6 text-center bg-black/80">
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

          {/* Capture Frame Guide — dimmed overlay with clear capture area */}
          <div className="absolute inset-0 z-10 pointer-events-none flex items-center justify-center">
            <div
              className="relative rounded-md"
              style={{
                aspectRatio: getFrameAspectRatio(),
                width: `min(100vw, calc(100vh * ${getFrameRatioValue()}))`,
                boxShadow: '0 0 0 200vmax rgba(180, 180, 195, 0.35)',
                border: '1.5px solid rgba(255, 255, 255, 0.35)',
              }}
            >
              {/* 3x3 Grid Overlay */}
              <div className="absolute inset-0 pointer-events-none opacity-30 rounded-md overflow-hidden">
                <div className="absolute top-1/3 left-0 grid-line-h"></div>
                <div className="absolute top-2/3 left-0 grid-line-h"></div>
                <div className="absolute left-1/3 top-0 grid-line-v"></div>
                <div className="absolute left-2/3 top-0 grid-line-v"></div>
              </div>
            </div>
          </div>
        </>
      )}

      {/* ===== OVERLAID CONTROLS ===== */}

      {/* Top Bar Overlay */}
      <header className="absolute top-0 left-0 right-0 z-20 flex items-center justify-between px-4 h-14">
        {/* Gradient backdrop for readability */}
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

      {/* Zoom Controls Overlay — only show when not in preview mode */}
      {!previewPhoto && (
        <div className="absolute left-0 right-0 z-20 flex flex-col items-center gap-3"
          style={{ bottom: '120px' }}
        >
          {/* Zoom Slider */}
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

          {/* Zoom Presets */}
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
      <footer className="absolute bottom-0 left-0 right-0 z-20 pb-6 pt-8">
        {/* Gradient backdrop for readability */}
        <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/40 to-transparent pointer-events-none" />

        {!previewPhoto ? (
          /* Live Camera State */
          <div className="relative flex justify-between items-center px-8">
            {/* Photo Counter / Gallery */}
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

            {/* Shutter Button */}
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

            {/* Process / Next Button */}
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
          /* Preview State — Keep / Re-take / Delete */
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