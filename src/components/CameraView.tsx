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
}

export default function CameraView({
  onCapture,
  onOpenSettings,
  onProcess,
  settings,
  photosCount,
  lastPhotoUrl,
  retakeId,
  onRetakeComplete
}: CameraViewProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [aspectRatio, setAspectRatio] = useState<AspectRatio>(settings.defaultAspectRatio);
  const [zoom, setZoom] = useState(1);
  const zoomPresets = [0.5, 1, 2, 5];
  const [isCameraReady, setIsCameraReady] = useState(false);
  const [facingMode, setFacingMode] = useState<'user' | 'environment'>('environment');
  const [error, setError] = useState<string | null>(null);
  const [previewPhoto, setPreviewPhoto] = useState<PhotoData | null>(null);

  const startCamera = useCallback(async () => {
    setError(null);
    if (videoRef.current?.srcObject) {
      const tracks = (videoRef.current.srcObject as MediaStream).getTracks();
      tracks.forEach(track => track.stop());
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode,
          width: { ideal: 1920 },
          height: { ideal: 1080 }
        }
      });
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        setIsCameraReady(true);
      }
    } catch (err) {
      console.error("Error accessing camera:", err);
      setError("Unable to access camera. Please check permissions.");
      setIsCameraReady(false);
    }
  }, [facingMode]);

  useEffect(() => {
    if (!previewPhoto) {
      startCamera();
    }
    return () => {
      if (videoRef.current?.srcObject) {
        const tracks = (videoRef.current.srcObject as MediaStream).getTracks();
        tracks.forEach(track => track.stop());
      }
    };
  }, [startCamera, previewPhoto]);

  const handleCapture = () => {
    if (!videoRef.current) return;

    const video = videoRef.current;
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let targetWidth = video.videoWidth;
    let targetHeight = video.videoHeight;

    if (aspectRatio === '1:1') {
      const size = Math.min(video.videoWidth, video.videoHeight);
      targetWidth = size;
      targetHeight = size;
    } else if (aspectRatio === '4:3') {
      if (video.videoWidth / video.videoHeight > 4 / 3) {
        targetWidth = video.videoHeight * (4 / 3);
      } else {
        targetHeight = video.videoWidth / (4 / 3);
      }
    } else if (aspectRatio === '16:9') {
      if (video.videoWidth / video.videoHeight > 16 / 9) {
        targetWidth = video.videoHeight * (16 / 9);
      } else {
        targetHeight = video.videoWidth / (16 / 9);
      }
    }

    canvas.width = targetWidth;
    canvas.height = targetHeight;

    const offsetX = (video.videoWidth - targetWidth) / 2;
    const offsetY = (video.videoHeight - targetHeight) / 2;

    const zoomFactor = 1 / zoom;
    const zoomedWidth = targetWidth * zoomFactor;
    const zoomedHeight = targetHeight * zoomFactor;
    const zoomOffsetX = offsetX + (targetWidth - zoomedWidth) / 2;
    const zoomOffsetY = offsetY + (targetHeight - zoomedHeight) / 2;

    if (facingMode === 'user') {
      ctx.translate(canvas.width, 0);
      ctx.scale(-1, 1);
    }

    ctx.drawImage(
      video,
      zoomOffsetX, zoomOffsetY, zoomedWidth, zoomedHeight,
      0, 0, targetWidth, targetHeight
    );

    const dataUrl = canvas.toDataURL('image/png');
    const photo: PhotoData = {
      id: Math.random().toString(36).substr(2, 9),
      url: dataUrl,
      originalUrl: dataUrl,
      aspectRatio,
      timestamp: Date.now(),
      filters: {
        exposure: 0,
        contrast: 0,
        scale: 1
      }
    };

    if (settings.showPreviewAfterCapture) {
      setPreviewPhoto(photo);
    } else {
      if (retakeId) {
        onRetakeComplete(photo);
      } else {
        onCapture(photo);
      }
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

  const getAspectRatioClass = () => {
    switch (aspectRatio) {
      case '1:1': return 'aspect-square';
      case '4:3': return 'aspect-[4/3]';
      case '16:9': return 'aspect-[16/9]';
      default: return 'aspect-square';
    }
  };

  return (
    <div className="relative h-full w-full bg-surface-lowest flex flex-col overflow-hidden font-sans">
      {/* Top Navigation */}
      <header className="relative z-20 flex items-center justify-between px-4 h-14 shrink-0 mt-2">
        <button
          onClick={onOpenSettings}
          className="p-2 text-primary hover:bg-surface-highest transition-colors rounded-full active:scale-95"
        >
          <SettingsIcon size={22} />
        </button>

        {!previewPhoto && (
          <nav className="flex items-center bg-surface-low/80 backdrop-blur-md rounded-full p-1 border border-white/5">
            {(['1:1', '4:3', '16:9'] as AspectRatio[]).map((ratio) => (
              <button
                key={ratio}
                onClick={() => setAspectRatio(ratio)}
                className={cn(
                  "px-3 py-1 font-mono text-[10px] tracking-widest transition-all",
                  aspectRatio === ratio
                    ? "text-primary bg-surface-container rounded-full shadow-sm"
                    : "text-on-surface-variant hover:text-white"
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
            className="p-2 text-primary hover:bg-surface-highest transition-colors rounded-full active:scale-95"
          >
            <RefreshCw size={22} />
          </button>
        )}
      </header>

      {/* Camera Preview / Photo Preview */}
      <main className="flex-1 flex flex-col items-center justify-center min-h-0 px-4 gap-4">
        <div
          {...(bind as any)()}
          className={cn(
            "relative overflow-hidden bg-surface-container rounded-2xl shadow-2xl border border-white/5 shrink touch-none",
            "w-full max-w-[min(85vw,500px)] max-h-[45vh]",
            getAspectRatioClass()
          )}
        >
          {!previewPhoto ? (
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
                <div className="absolute inset-0 flex items-center justify-center p-6 text-center bg-surface-container">
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
              {/* 3x3 Grid Overlay */}
              <div className="absolute inset-0 pointer-events-none opacity-40">
                <div className="absolute top-1/3 left-0 grid-line-h"></div>
                <div className="absolute top-2/3 left-0 grid-line-h"></div>
                <div className="absolute left-1/3 top-0 grid-line-v"></div>
                <div className="absolute left-2/3 top-0 grid-line-v"></div>
              </div>
            </>
          ) : (
            <img src={previewPhoto.url} className="absolute inset-0 w-full h-full object-cover" />
          )}
        </div>

        {/* Controls — only show when not in preview mode */}
        {!previewPhoto && (
          <div className="w-full max-w-xs flex flex-col items-center gap-4 py-2">
            {/* Zoom Presets */}
            <div className="flex items-center gap-4 bg-surface-low/60 backdrop-blur-sm px-3 py-1.5 rounded-full border border-white/5">
              {zoomPresets.map((preset) => (
                <button
                  key={preset}
                  onClick={() => setZoom(preset)}
                  className={cn(
                    "font-mono text-[10px] transition-all",
                    zoom === preset
                      ? "text-primary bg-primary/10 rounded-full w-7 h-7 flex items-center justify-center"
                      : "text-on-surface-variant hover:text-primary"
                  )}
                >
                  {preset === 0.5 ? '.5' : `${preset}${zoom === preset ? 'x' : ''}`}
                </button>
              ))}
            </div>

            {/* Zoom Slider */}
            <div className="w-full px-4">
              <div className="flex justify-between items-center mb-2">
                <span className="font-mono text-[10px] text-on-surface-variant uppercase">Auto</span>
                <span className="font-mono text-lg font-bold text-primary">
                  {zoom.toFixed(1)}
                  <span className="text-[10px] font-normal text-on-surface-variant ml-1">X</span>
                </span>
                <span className="font-mono text-[10px] text-on-surface-variant uppercase">Wide</span>
              </div>
              <input
                type="range"
                min="0.5"
                max="5"
                step="0.1"
                value={zoom}
                onChange={(e) => setZoom(parseFloat(e.target.value))}
                className="w-full h-1 bg-surface-high rounded-lg appearance-none cursor-pointer slider-obsidian"
              />
            </div>
          </div>
        )}
      </main>

      {/* Bottom Action Area */}
      <footer className="shrink-0 flex flex-col gap-2 pb-6 pt-2">
        {!previewPhoto ? (
          /* Live Camera State */
          <div className="flex justify-between items-center px-8">
            {/* Photo Counter / Gallery */}
            <div className="flex flex-col items-center gap-1 group cursor-pointer w-14">
              <div className="relative w-12 h-12 rounded-xl bg-surface-high border border-white/10 flex items-center justify-center overflow-hidden">
                {lastPhotoUrl ? (
                  <>
                    <img src={lastPhotoUrl} className="w-full h-full object-cover rounded-xl" />
                    <span className="absolute inset-0 flex items-center justify-center bg-black/40 text-white font-bold text-sm rounded-xl">
                      {photosCount}
                    </span>
                  </>
                ) : (
                  <span className="text-on-surface-variant/40 text-lg">+</span>
                )}
              </div>
            </div>

            {/* Shutter Button */}
            <div className="relative flex items-center justify-center">
              <div className="absolute inset-0 bg-primary/10 blur-xl rounded-full scale-150" />
              <button
                onClick={handleCapture}
                disabled={!isCameraReady}
                className="w-16 h-16 rounded-full border-4 border-on-surface bg-transparent p-1 transition-transform active:scale-95 duration-75 relative z-10"
              >
                <div className="w-full h-full rounded-full bg-on-surface flex items-center justify-center">
                  <div className="w-10 h-10 rounded-full border border-surface/20" />
                </div>
              </button>
            </div>

            {/* Process / Next Button */}
            <div className="w-12 flex justify-end">
              <button
                onClick={onProcess}
                disabled={photosCount === 0}
                className={cn(
                  "w-11 h-11 rounded-full flex items-center justify-center active:scale-90 transition-transform",
                  photosCount > 0
                    ? "bg-gradient-to-br from-primary to-primary-container text-on-primary shadow-lg shadow-primary/20"
                    : "bg-surface-high text-on-surface-variant/30 cursor-not-allowed"
                )}
              >
                <ArrowRight size={20} />
              </button>
            </div>
          </div>
        ) : (
          /* Preview State — Keep / Re-take / Delete */
          <div className="w-full max-w-sm mx-auto">
            <div className="flex justify-between items-center px-6">
              <button
                onClick={handleDeletePreview}
                className="flex flex-col items-center gap-2 text-error"
              >
                <div className="w-12 h-12 rounded-full bg-error/10 flex items-center justify-center">
                  <Trash2 size={24} />
                </div>
                <span className="font-mono text-[10px] font-bold uppercase tracking-wider">Delete</span>
              </button>

              <button
                onClick={handleRetake}
                className="flex flex-col items-center gap-2 text-on-surface-variant"
              >
                <div className="w-12 h-12 rounded-full bg-surface-container flex items-center justify-center">
                  <RotateCcw size={24} />
                </div>
                <span className="font-mono text-[10px] font-bold uppercase tracking-wider">Re-take</span>
              </button>

              <button
                onClick={handleKeep}
                className="flex flex-col items-center gap-2 text-primary"
              >
                <div className="w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center">
                  <Check size={24} />
                </div>
                <span className="font-mono text-[10px] font-bold uppercase tracking-wider">Keep</span>
              </button>
            </div>
          </div>
        )}
      </footer>
    </div>
  );
}