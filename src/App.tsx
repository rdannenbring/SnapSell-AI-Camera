/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import CameraView from './components/CameraView';
import EditorView from './components/EditorView';
import SettingsView from './components/SettingsView';
import { AppSettings, PhotoData } from './types';

export default function App() {
  const [view, setView] = useState<'camera' | 'editor' | 'settings'>('camera');
  const [photos, setPhotos] = useState<PhotoData[]>([]);
  const [editingPhotoId, setEditingPhotoId] = useState<string | null>(null);
  const [settings, setSettings] = useState<AppSettings>({
    defaultAspectRatio: '1:1',
    saveLocation: '/Pictures/SnapSell',
    showPreviewAfterCapture: true
  });

  // Load settings from localStorage if available
  useEffect(() => {
    const saved = localStorage.getItem('snapsell_settings');
    if (saved) {
      setSettings(prev => ({ ...prev, ...JSON.parse(saved) }));
    }
  }, []);

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

  const handleSaveAll = (updatedPhotos: PhotoData[]) => {
    // In a real app, this would use native APIs to save files
    console.log('Saving photos:', updatedPhotos);
    setView('camera');
    setPhotos([]);
  };

  const handleRetake = (id: string) => {
    setEditingPhotoId(id);
    setView('camera');
  };

  const handleDelete = (id: string) => {
    setPhotos(prev => prev.filter(p => p.id !== id));
  };

  return (
    <div className="h-screen w-screen bg-black overflow-hidden">
      {view === 'camera' && (
        <CameraView 
          onCapture={handleCapture} 
          onOpenSettings={() => setView('settings')}
          onProcess={handleProcess}
          settings={settings}
          photosCount={photos.length}
          retakeId={editingPhotoId}
          onRetakeComplete={(photo) => {
            setPhotos(prev => prev.map(p => p.id === editingPhotoId ? { ...photo, id: editingPhotoId } : p));
            setEditingPhotoId(null);
            setView('editor');
          }}
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
          settings={settings}
        />
      )}

      {view === 'settings' && (
        <SettingsView 
          settings={settings} 
          onUpdate={handleUpdateSettings} 
          onClose={() => setView('camera')}
        />
      )}
    </div>
  );
}

