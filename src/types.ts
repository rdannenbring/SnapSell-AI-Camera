/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export type AspectRatio = '1:1' | '4:3' | '16:9';

export interface AppSettings {
  defaultAspectRatio: AspectRatio;
  saveLocation: string;
  showPreviewAfterCapture: boolean;
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
