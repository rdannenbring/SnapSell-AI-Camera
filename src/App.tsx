/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef, useCallback } from 'react';
import { App as CapApp } from '@capacitor/app';
import CameraView from './components/CameraView';
import EditorView from './components/EditorView';
import SettingsView from './components/SettingsView';
import { AppSettings, PhotoData } from './types';
import { getDefaultSaveLocation, isNativeAndroid, pickSaveDirectory, savePhotosToDevice } from './services/storageService';

export default function App() {
  const [view, setView] = useState<'camera' | 'editor' | 'settings'>('camera');
  const [photos, setPhotos] = useState<PhotoData[]>([]);
  const [editingPhotoId, setEditingPhotoId] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isPickingSaveLocation, setIsPickingSaveLocation] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const toastTimeoutRef = useRef<number | null>(null);
  // Ref for child components to register a back-button handler (returns true if handled)
  const hardwareBackRef = useRef<(() => boolean) | null>(null);

  // Handle Android hardware back button / back gesture
  useEffect(() => {
    const handler = CapApp.addListener('backButton', () => {
      if (hardwareBackRef.current && hardwareBackRef.current()) {
        return; // Child component handled it
      }
      // Default: navigate back through views
      if (view === 'settings') {
        setView('camera');
      } else if (view === 'editor') {
        setView('camera');
      }
      // If on camera view, do nothing (or could exit app)
    });

    return () => {
      handler.then(h => h.remove()).catch(() => {});
    };
  }, [view]);
  const [settings, setSettings] = useState<AppSettings>({
    defaultAspectRatio: '1:1',
    saveLocation: getDefaultSaveLocation(),
    showPreviewAfterCapture: true,
    imageQuality: 85,
  });

  // Load settings from localStorage if available
  useEffect(() => {
    const saved = localStorage.getItem('snapsell_settings');
    if (saved) {
      const parsed = JSON.parse(saved) as Partial<AppSettings>;
      setSettings(prev => ({
        ...prev,
        ...parsed,
        saveLocation: parsed.saveLocation || prev.saveLocation || getDefaultSaveLocation(),
      }));
    }
  }, []);

  useEffect(() => {
    return () => {
      if (toastTimeoutRef.current) {
        window.clearTimeout(toastTimeoutRef.current);
      }
    };
  }, []);

  const showToast = (message: string, durationMs = 3500) => {
    setToastMessage(message);
    if (toastTimeoutRef.current) {
      window.clearTimeout(toastTimeoutRef.current);
    }
    toastTimeoutRef.current = window.setTimeout(() => {
      setToastMessage(null);
      toastTimeoutRef.current = null;
    }, durationMs);
  };

  const handleUpdateSettings = (newSettings: AppSettings) => {
    setSettings(newSettings);
    localStorage.setItem('snapsell_settings', JSON.stringify(newSettings));
  };

  const handleCapture = (photo: PhotoData) => {
    setPhotos(prev => [...prev, photo]);
  };

  const handleProcess = () => {
    setView('editor');
  };

  const handlePickSaveLocation = async () => {
    setIsPickingSaveLocation(true);
    try {
      const selectedPath = await pickSaveDirectory();
      const updated = { ...settings, saveLocation: selectedPath };
      handleUpdateSettings(updated);
    } catch (err) {
      console.error('Save location picker failed:', err);
      const errorMessage = err instanceof Error ? err.message : String(err);
      alert(
        'Could not open directory picker.\n\n' +
        'Please make sure you are in the installed native Android app (not browser/PWA), then try again.\n\n' +
        `Details: ${errorMessage}`
      );
    } finally {
      setIsPickingSaveLocation(false);
    }
  };

  const handleSaveAll = async (updatedPhotos: PhotoData[], itemName?: string) => {
    setIsSaving(true);
    try {
      const result = await savePhotosToDevice(updatedPhotos, settings.saveLocation, itemName, settings.imageQuality / 100);
      const savedPaths = Array.isArray(result) ? result : result.savedPaths;
      const usedFallback = Array.isArray(result) ? false : result.usedFallback;

      console.log('Saved photos:', savedPaths);
      if (usedFallback) {
        showToast(
          `Saved ${savedPaths.length} photo${savedPaths.length === 1 ? '' : 's'} to app storage fallback. Check permissions and re-select Save Location.`,
          5000,
        );
      } else {
        showToast(`Saved ${savedPaths.length} photo${savedPaths.length === 1 ? '' : 's'}.`);
      }
      setView('camera');
      setPhotos([]);
    } catch (err) {
      console.error('Save failed:', err);
      alert('Unable to save photos. Please verify storage permissions and selected folder.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleRetake = (id: string) => {
    setEditingPhotoId(id);
    setView('camera');
  };

  const handleDelete = (id: string) => {
    setPhotos(prev => prev.filter(p => p.id !== id));
  };

  return (
    <div className="h-screen w-screen bg-surface-lowest overflow-hidden font-sans">
      {view === 'camera' && (
        <CameraView 
          onCapture={handleCapture} 
          onOpenSettings={() => setView('settings')}
          onProcess={handleProcess}
          settings={settings}
          photosCount={photos.length}
          lastPhotoUrl={photos.length > 0 ? photos[photos.length - 1].url : null}
          retakeId={editingPhotoId}
          onRetakeComplete={(photo) => {
            setPhotos(prev => prev.map(p => p.id === editingPhotoId ? { ...photo, id: editingPhotoId } : p));
            setEditingPhotoId(null);
            setView('editor');
          }}
          hardwareBackRef={hardwareBackRef}
        />
      )}

      {view === 'editor' && (
        <EditorView 
          photos={photos} 
          onClose={() => {
            setView('camera');
          }}
          onSave={handleSaveAll}
          onRetake={handleRetake}
          onDelete={handleDelete}
          onAddMore={() => setView('camera')}
          settings={settings}
          isSaving={isSaving}
          hardwareBackRef={hardwareBackRef}
        />
      )}

      {view === 'settings' && (
        <SettingsView 
          settings={settings} 
          onUpdateSettings={handleUpdateSettings} 
          onClose={() => setView('camera')}
        />
      )}

      {toastMessage && (
        <div className="fixed bottom-32 left-1/2 z-50 -translate-x-1/2 rounded-full bg-surface-container/95 backdrop-blur-md px-5 py-2.5 text-sm text-on-surface shadow-lg border border-outline-variant/10 font-mono">
          <div className="flex items-center gap-2">
            <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
            {toastMessage}
          </div>
        </div>
      )}
    </div>
  );
}

