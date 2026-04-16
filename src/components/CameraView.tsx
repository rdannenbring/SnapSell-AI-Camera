/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useRef, useState, useEffect, useCallback } from 'react';
import { Camera, RefreshCw, Settings as SettingsIcon, Maximize, Minimize, ZoomIn, ZoomOut, Check, X as CloseIcon, ArrowRight, Trash2, RotateCcw } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { usePinch } from '@use-gesture/react';
import { useSpring, animated } from '@react-spring/web';
import { AspectRatio, PhotoData, AppSettings } from '../types';
import { cn } from '../utils';

interface CameraViewProps {
  onCapture: (photo: PhotoData) => void;
  onOpenSettings: () => void;
  onProcess: () => void;
  settings: AppSettings;
  photosCount: number;
  retakeId: string | null;
  onRetakeComplete: (photo: PhotoData) => void;
}

export default function CameraView({ 
  onCapture, 
  onOpenSettings, 
  onProcess, 
  settings, 
  photosCount,
  retakeId,
  onRetakeComplete
}: CameraViewProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [aspectRatio, setAspectRatio] = useState<AspectRatio>(settings.defaultAspectRatio);
  const [zoom, setZoom] = useState(1);
  const zoomPresets = [0.5, 1, 2, 3, 4, 5];
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
      if (video.videoWidth / video.videoHeight > 4/3) {
        targetWidth = video.videoHeight * (4/3);
      } else {
        targetHeight = video.videoWidth / (4/3);
      }
    } else if (aspectRatio === '16:9') {
      if (video.videoWidth / video.videoHeight > 16/9) {
        targetWidth = video.videoHeight * (16/9);
      } else {
        targetHeight = video.videoWidth / (16/9);
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
    <div className="relative h-full w-full bg-black flex flex-col overflow-hidden font-sans">
      {/* Top Bar */}
      <div className="absolute top-0 left-0 right-0 z-20 p-4 flex justify-between items-center bg-gradient-to-b from-black/60 to-transparent">
        <button onClick={onOpenSettings} className="p-2 rounded-full bg-white/10 backdrop-blur-md text-white">
          <SettingsIcon size={24} />
        </button>
        {!previewPhoto && (
          <div className="flex gap-2 bg-white/10 backdrop-blur-md rounded-full p-1">
            {(['1:1', '4:3', '16:9'] as AspectRatio[]).map((ratio) => (
              <button
                key={ratio}
                onClick={() => setAspectRatio(ratio)}
                className={cn(
                  "px-3 py-1 rounded-full text-xs font-medium transition-colors",
                  aspectRatio === ratio ? "bg-white text-black" : "text-white/70 hover:text-white"
                )}
              >
                {ratio}
              </button>
            ))}
          </div>
        )}
        {!previewPhoto && (
          <button 
            onClick={() => setFacingMode(prev => prev === 'user' ? 'environment' : 'user')}
            className="p-2 rounded-full bg-white/10 backdrop-blur-md text-white"
          >
            <RefreshCw size={24} />
          </button>
        )}
      </div>

      {/* Camera Preview / Photo Preview */}
      <div className="flex-1 flex items-center justify-center relative touch-none">
        <div 
          {...(bind as any)()}
          className={cn("relative overflow-hidden bg-zinc-900 transition-all duration-300 w-full max-w-2xl shadow-2xl", getAspectRatioClass())}
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
                <div className="absolute inset-0 flex items-center justify-center p-6 text-center bg-zinc-900">
                  <div className="space-y-4">
                    <p className="text-white/60 text-sm">{error}</p>
                    <button 
                      onClick={startCamera}
                      className="px-4 py-2 bg-white text-black rounded-full text-xs font-bold"
                    >
                      Retry
                    </button>
                  </div>
                </div>
              )}
              {/* Grid Overlay */}
              <div className="absolute inset-0 grid grid-cols-3 grid-rows-3 pointer-events-none opacity-20">
                <div className="border-r border-b border-white"></div>
                <div className="border-r border-b border-white"></div>
                <div className="border-b border-white"></div>
                <div className="border-r border-b border-white"></div>
                <div className="border-r border-b border-white"></div>
                <div className="border-b border-white"></div>
                <div className="border-r border-white"></div>
                <div className="border-r border-white"></div>
                <div></div>
              </div>
            </>
          ) : (
            <img src={previewPhoto.url} className="absolute inset-0 w-full h-full object-cover" />
          )}
        </div>
      </div>

      {/* Bottom Controls */}
      <div className="pb-10 pt-6 px-8 bg-black flex flex-col items-center gap-6 shrink-0">
        {!previewPhoto ? (
          <>
            {/* Zoom Presets */}
            <div className="flex gap-3 items-center justify-center">
              {zoomPresets.map((preset) => (
                <button
                  key={preset}
                  onClick={() => setZoom(preset)}
                  className={cn(
                    "w-10 h-10 rounded-full flex items-center justify-center text-[10px] font-bold transition-all border",
                    zoom === preset 
                      ? "bg-white text-black border-white scale-110" 
                      : "bg-white/5 text-white/50 border-white/10 hover:bg-white/10"
                  )}
                >
                  {preset}x
                </button>
              ))}
            </div>

            {/* Zoom Slider */}
            <div className="w-full max-w-xs flex items-center gap-4 text-white/60">
              <ZoomOut size={16} />
              <input
                type="range"
                min="0.5"
                max="5"
                step="0.1"
                value={zoom}
                onChange={(e) => setZoom(parseFloat(e.target.value))}
                className="flex-1 accent-white h-1 bg-white/20 rounded-lg appearance-none cursor-pointer"
              />
              <ZoomIn size={16} />
              <span className="text-xs font-mono w-8">{zoom.toFixed(1)}x</span>
            </div>

            <div className="flex items-center justify-between w-full max-w-sm">
              <div className="w-12 h-12 rounded-full overflow-hidden bg-zinc-900 border border-white/10 flex items-center justify-center">
                {photosCount > 0 && (
                  <span className="text-white font-bold text-lg">{photosCount}</span>
                )}
              </div>
              
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  handleCapture();
                }}
                disabled={!isCameraReady}
                className="group relative flex items-center justify-center"
                style={{ WebkitTapHighlightColor: 'transparent' }}
              >
                <div className="w-20 h-20 rounded-full border-4 border-white flex items-center justify-center transition-transform active:scale-90">
                  <div className="w-16 h-16 rounded-full bg-white group-hover:scale-95 transition-transform" />
                </div>
              </button>

              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onProcess();
                }}
                disabled={photosCount === 0}
                className={cn(
                  "w-12 h-12 rounded-full flex items-center justify-center transition-all active:scale-90 shadow-lg",
                  photosCount > 0 ? "bg-emerald-500 text-white cursor-pointer hover:bg-emerald-400" : "bg-white/5 text-white/20 cursor-not-allowed"
                )}
                style={{ WebkitTapHighlightColor: 'transparent' }}
              >
                <ArrowRight size={24} />
              </button>
            </div>
          </>
        ) : (
          <div className="w-full max-w-sm space-y-6">
            <div className="flex justify-between items-center px-4">
              <button 
                onClick={(e) => {
                  e.stopPropagation();
                  handleDeletePreview();
                }}
                className="flex flex-col items-center gap-2 text-red-500"
                style={{ WebkitTapHighlightColor: 'transparent' }}
              >
                <div className="w-12 h-12 rounded-full bg-red-500/10 flex items-center justify-center">
                  <Trash2 size={24} />
                </div>
                <span className="text-[10px] font-bold uppercase tracking-wider">Delete</span>
              </button>
              
              <button 
                onClick={(e) => {
                  e.stopPropagation();
                  handleRetake();
                }}
                className="flex flex-col items-center gap-2 text-white/60"
                style={{ WebkitTapHighlightColor: 'transparent' }}
              >
                <div className="w-12 h-12 rounded-full bg-white/5 flex items-center justify-center">
                  <RotateCcw size={24} />
                </div>
                <span className="text-[10px] font-bold uppercase tracking-wider">Re-take</span>
              </button>

              <button 
                onClick={(e) => {
                  e.stopPropagation();
                  handleKeep();
                }}
                className="flex flex-col items-center gap-2 text-emerald-500"
                style={{ WebkitTapHighlightColor: 'transparent' }}
              >
                <div className="w-12 h-12 rounded-full bg-emerald-500/10 flex items-center justify-center">
                  <Check size={24} />
                </div>
                <span className="text-[10px] font-bold uppercase tracking-wider">Keep</span>
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
