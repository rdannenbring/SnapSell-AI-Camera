/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export type AspectRatio = '1:1' | '4:3' | '16:9';

export interface AppSettings {
  defaultAspectRatio: AspectRatio;
  saveLocation: string;
  showPreviewAfterCapture: boolean;
  geminiApiKey?: string;
  geminiImageEditModel?: string;
  geminiNamingModel?: string;
  useSameModelForNamingAndEditing?: boolean;
  imageQuality: number; // 50–100 JPEG quality (default 85)
  cameraResolution?: { width: number; height: number }; // actual sensor resolution
  autoGenerateFilename: boolean; // use AI to suggest item name on save when empty
}

export interface PhotoData {
  id: string;
  url: string;
  originalUrl: string;
  aspectRatio: AspectRatio;
  timestamp: number;
  filters?: {
    exposure: number;
    contrast: number;
    scale: number;
    aiFilter?: string;
  };
}
