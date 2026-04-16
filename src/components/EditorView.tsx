/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useRef, useEffect } from 'react';
import { 
  X, Check, Crop, Sliders, Sparkles, Download, 
  Sun, Contrast, Image as ImageIcon, RotateCcw,
  Layers, Wand2, Maximize, Trash2, Save, ChevronLeft, ChevronRight
} from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { PhotoData, AppSettings } from '../types';
import { applyAIFilter, AI_PROMPTS, suggestItemName } from '../services/aiService';
import { cn } from '../utils';

interface EditorViewProps {
  photos: PhotoData[];
  onClose: () => void;
  onSave: (photos: PhotoData[]) => void;
  onRetake: (id: string) => void;
  onDelete: (id: string) => void;
  settings: AppSettings;
}

export default function EditorView({ photos, onClose, onSave, onRetake, onDelete, settings }: EditorViewProps) {
  const [currentPhotos, setCurrentPhotos] = useState<PhotoData[]>(photos);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [itemName, setItemName] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [isNaming, setIsNaming] = useState(false);
  const [applyToAll, setApplyToAll] = useState(false);
  const [activeTab, setActiveTab] = useState<'adjust' | 'ai' | 'crop'>('adjust');
  
  const currentPhoto = currentPhotos[selectedIndex];

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
      alert("AI processing failed. Please try again.");
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

  const formatDate = (date: Date) => {
    const yyyy = date.getFullYear();
    const mm = String(date.getMonth() + 1).padStart(2, '0');
    const dd = String(date.getDate()).padStart(2, '0');
    const hh = date.getHours();
    const min = String(date.getMinutes()).padStart(2, '0');
    const ampm = hh >= 12 ? 'PM' : 'AM';
    const hh12 = hh % 12 || 12;
    return `${yyyy}-${mm}-${dd}-${hh12}:${min} ${ampm}`;
  };

  const handleFinish = () => {
    const dateStr = formatDate(new Date());
    const finalPhotos = currentPhotos.map((p, i) => {
      const fileName = `${itemName || 'Item'}-${i + 1}-${dateStr}.png`;
      console.log(`Saving ${fileName} to ${settings.saveLocation}`);
      return p;
    });
    onSave(finalPhotos);
  };

  if (!currentPhoto) return null;

  const filters = currentPhoto.filters || { exposure: 0, contrast: 0, scale: 1 };

  return (
    <div className="fixed inset-0 z-50 bg-black flex flex-col font-sans">
      {/* Header */}
      <div className="p-4 flex justify-between items-center bg-zinc-900/50 backdrop-blur-md border-b border-white/5">
        <button onClick={onClose} className="p-2 text-white/70 hover:text-white">
          <X size={24} />
        </button>
        <div className="flex-1 px-4 max-w-md">
          <div className="relative">
            <input 
              type="text"
              placeholder="Enter item name..."
              value={itemName}
              onChange={(e) => setItemName(e.target.value)}
              className="w-full bg-white/5 border border-white/10 rounded-full px-4 py-2 text-sm focus:outline-none focus:border-white/30 pr-10"
            />
            <button 
              onClick={handleSuggestName}
              disabled={isNaming}
              className="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-blue-400 hover:text-blue-300 disabled:opacity-50"
            >
              <Sparkles size={16} className={isNaming ? "animate-spin" : ""} />
            </button>
          </div>
        </div>
        <button onClick={handleFinish} className="flex items-center gap-2 px-4 py-2 bg-emerald-500 text-white rounded-full text-sm font-bold shadow-lg shadow-emerald-500/20">
          <Save size={18} />
          Finish
        </button>
      </div>

      {/* Preview Area */}
      <div className="flex-1 relative flex flex-col items-center justify-center p-4 gap-4 overflow-hidden">
        <div className="relative max-w-full max-h-[60vh] shadow-2xl group">
          <img 
            src={currentPhoto.url} 
            className="max-w-full max-h-[60vh] rounded-lg shadow-2xl object-contain transition-all duration-300"
            style={{ 
              filter: `brightness(${100 + filters.exposure}%) contrast(${100 + filters.contrast}%)`,
              transform: `scale(${filters.scale})`
            }}
            alt="Preview"
          />
          
          {/* Photo Actions Overlay */}
          <div className="absolute top-4 right-4 flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
            <button 
              onClick={() => onRetake(currentPhoto.id)}
              className="p-2 bg-black/60 backdrop-blur-md rounded-full text-white hover:bg-black/80"
              title="Re-take"
            >
              <RotateCcw size={18} />
            </button>
            <button 
              onClick={() => {
                onDelete(currentPhoto.id);
                if (selectedIndex >= currentPhotos.length - 1) {
                  setSelectedIndex(Math.max(0, currentPhotos.length - 2));
                }
                setCurrentPhotos(prev => prev.filter(p => p.id !== currentPhoto.id));
              }}
              className="p-2 bg-red-500/60 backdrop-blur-md rounded-full text-white hover:bg-red-500/80"
              title="Delete"
            >
              <Trash2 size={18} />
            </button>
          </div>

          {isProcessing && (
            <div className="absolute inset-0 bg-black/60 backdrop-blur-sm rounded-lg flex flex-col items-center justify-center text-white gap-4 z-10">
              <div className="w-12 h-12 border-4 border-white/20 border-t-white rounded-full animate-spin" />
              <p className="text-sm font-medium animate-pulse tracking-widest uppercase">AI Processing...</p>
            </div>
          )}
        </div>

        {/* Thumbnails */}
        <div className="w-full flex items-center justify-center gap-2 overflow-x-auto py-2 px-4 no-scrollbar">
          {currentPhotos.map((p, i) => (
            <button
              key={p.id}
              onClick={() => setSelectedIndex(i)}
              className={cn(
                "w-16 h-16 rounded-lg overflow-hidden border-2 transition-all shrink-0",
                selectedIndex === i ? "border-white scale-110 shadow-lg" : "border-transparent opacity-50 hover:opacity-100"
              )}
            >
              <img src={p.url} className="w-full h-full object-cover" />
            </button>
          ))}
        </div>
      </div>

      {/* Controls */}
      <div className="bg-zinc-900 p-6 rounded-t-3xl shadow-2xl border-t border-white/5">
        <div className="flex items-center justify-between mb-6">
          <div className="flex gap-8">
            <button 
              onClick={() => setActiveTab('adjust')}
              className={cn("flex flex-col items-center gap-1 transition-colors", activeTab === 'adjust' ? "text-white" : "text-white/40")}
            >
              <Sliders size={20} />
              <span className="text-[10px] uppercase font-bold tracking-tighter">Adjust</span>
            </button>
            <button 
              onClick={() => setActiveTab('ai')}
              className={cn("flex flex-col items-center gap-1 transition-colors", activeTab === 'ai' ? "text-white" : "text-white/40")}
            >
              <Sparkles size={20} />
              <span className="text-[10px] uppercase font-bold tracking-tighter">AI Filters</span>
            </button>
            <button 
              onClick={() => setActiveTab('crop')}
              className={cn("flex flex-col items-center gap-1 transition-colors", activeTab === 'crop' ? "text-white" : "text-white/40")}
            >
              <Crop size={20} />
              <span className="text-[10px] uppercase font-bold tracking-tighter">Crop</span>
            </button>
          </div>

          <div className="flex items-center gap-3">
            <span className="text-[10px] uppercase font-bold text-white/40">Apply to All</span>
            <button 
              onClick={() => setApplyToAll(!applyToAll)}
              className={cn(
                "w-10 h-5 rounded-full transition-colors relative",
                applyToAll ? "bg-emerald-500" : "bg-white/10"
              )}
            >
              <div className={cn(
                "absolute top-1 w-3 h-3 rounded-full transition-all",
                applyToAll ? "right-1 bg-white" : "left-1 bg-white/40"
              )} />
            </button>
          </div>
        </div>

        {/* Tab Content */}
        <div className="min-h-[120px]">
          {activeTab === 'adjust' && (
            <div className="space-y-6">
              <div className="flex items-center gap-4">
                <Sun size={18} className="text-white/60" />
                <input 
                  type="range" min="-100" max="100" value={filters.exposure} 
                  onChange={(e) => updateCurrentPhoto({ filters: { ...filters, exposure: parseInt(e.target.value) } })}
                  className="flex-1 accent-white h-1 bg-white/10 rounded-full appearance-none"
                />
                <span className="text-xs font-mono text-white/60 w-8">{filters.exposure}</span>
              </div>
              <div className="flex items-center gap-4">
                <Contrast size={18} className="text-white/60" />
                <input 
                  type="range" min="-100" max="100" value={filters.contrast} 
                  onChange={(e) => updateCurrentPhoto({ filters: { ...filters, contrast: parseInt(e.target.value) } })}
                  className="flex-1 accent-white h-1 bg-white/10 rounded-full appearance-none"
                />
                <span className="text-xs font-mono text-white/60 w-8">{filters.contrast}</span>
              </div>
              <div className="flex items-center gap-4">
                <Maximize size={18} className="text-white/60" />
                <input 
                  type="range" min="0.1" max="2" step="0.1" value={filters.scale} 
                  onChange={(e) => updateCurrentPhoto({ filters: { ...filters, scale: parseFloat(e.target.value) } })}
                  className="flex-1 accent-white h-1 bg-white/10 rounded-full appearance-none"
                />
                <span className="text-xs font-mono text-white/60 w-8">{filters.scale.toFixed(1)}x</span>
              </div>
            </div>
          )}

          {activeTab === 'ai' && (
            <div className="grid grid-cols-3 gap-3">
              <button 
                onClick={() => handleAIFilter(AI_PROMPTS.ENHANCE)}
                className="flex flex-col items-center justify-center p-3 bg-white/5 rounded-xl hover:bg-white/10 transition-colors gap-2"
              >
                <Wand2 size={20} className="text-blue-400" />
                <span className="text-[10px] text-center font-medium">Enhance</span>
              </button>
              <button 
                onClick={() => handleAIFilter(AI_PROMPTS.CLEAN_BACKGROUND)}
                className="flex flex-col items-center justify-center p-3 bg-white/5 rounded-xl hover:bg-white/10 transition-colors gap-2"
              >
                <Layers size={20} className="text-emerald-400" />
                <span className="text-[10px] text-center font-medium">White BG</span>
              </button>
              <button 
                onClick={() => handleAIFilter(AI_PROMPTS.LIFESTYLE)}
                className="flex flex-col items-center justify-center p-3 bg-white/5 rounded-xl hover:bg-white/10 transition-colors gap-2"
              >
                <ImageIcon size={20} className="text-purple-400" />
                <span className="text-[10px] text-center font-medium">Lifestyle</span>
              </button>
            </div>
          )}

          {activeTab === 'crop' && (
            <div className="flex items-center justify-center h-full text-white/40 text-sm">
              <p>Crop tool coming soon in next update</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
