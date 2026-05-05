/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { GoogleGenAI } from "@google/genai";

export const DEFAULT_IMAGE_EDIT_MODEL = "gemini-2.5-flash-image";
export const DEFAULT_NAMING_MODEL = "gemini-2.5-flash";

export interface GeminiModelInfo {
  name: string;
  displayName?: string;
}

// Track the current API key so we can re-create the client when it changes
let currentApiKey: string | null = null;
let aiClient: GoogleGenAI | null = null;

/** Get the build-time default API key (from .env or fallback) */
function getDefaultApiKey() {
  return process.env.GEMINI_API_KEY || '';
}

/** Get the effective API key: runtime override > build-time default */
function getApiKey(runtimeOverride?: string) {
  return runtimeOverride || getDefaultApiKey();
}

/** Obscure a key for display: show first 4 chars + *** + last 4 chars */
export function obscureApiKey(key: string): string {
  if (!key || key.length < 12) return '***';
  return key.slice(0, 4) + '***' + key.slice(-4);
}

/** Check whether a value is the obscured placeholder (not a real key) */
export function isObscuredKey(value: string): boolean {
  return value.includes('***');
}

function getAI(runtimeApiKey?: string) {
  const apiKey = getApiKey(runtimeApiKey);
  if (!apiKey) {
    throw new Error(
      'Gemini API key not configured. Open Settings to enter your API key.'
    );
  }

  // Re-create client if key changed
  if (!aiClient || currentApiKey !== apiKey) {
    aiClient = new GoogleGenAI({ apiKey });
    currentApiKey = apiKey;
  }

  return aiClient;
}

/** Compress a data-URL image to JPEG, max 2048px, to avoid OOM crashes on Android */
function compressDataUrl(dataUrl: string, maxDim = 2048, quality = 0.85): Promise<string> {
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      let { width, height } = img;
      if (width <= maxDim && height <= maxDim && dataUrl.length < 2_000_000) {
        resolve(dataUrl);
        return;
      }
      if (width > maxDim || height > maxDim) {
        const r = Math.min(maxDim / width, maxDim / height);
        width = Math.round(width * r);
        height = Math.round(height * r);
      }
      const c = document.createElement('canvas');
      c.width = width;
      c.height = height;
      const ctx = c.getContext('2d');
      if (!ctx) { resolve(dataUrl); return; }
      ctx.drawImage(img, 0, 0, width, height);
      resolve(c.toDataURL('image/jpeg', quality));
    };
    img.onerror = () => resolve(dataUrl);
    img.src = dataUrl;
  });
}

export async function applyAIFilter(base64Image: string, prompt: string, runtimeApiKey?: string) {
  return applyAIFilterWithModel(base64Image, prompt, runtimeApiKey, DEFAULT_IMAGE_EDIT_MODEL);
}

export async function listGeminiModels(runtimeApiKey?: string): Promise<GeminiModelInfo[]> {
  const apiKey = getApiKey(runtimeApiKey);
  if (!apiKey) {
    throw new Error('Gemini API key not configured.');
  }

  const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${encodeURIComponent(apiKey)}`);
  if (!response.ok) {
    const errText = await response.text();
    throw new Error(`Failed to fetch Gemini models (${response.status}): ${errText || response.statusText}`);
  }

  const data = await response.json() as { models?: Array<{ name?: string; displayName?: string; supportedGenerationMethods?: string[] }> };
  const allModels = (data.models || [])
    .filter((m) => (m.supportedGenerationMethods || []).includes('generateContent'))
    .map((m) => ({
      name: (m.name || '').replace(/^models\//, ''),
      displayName: m.displayName,
    }))
    .filter((m) => !!m.name)
    .sort((a, b) => a.name.localeCompare(b.name));

  return allModels;
}

export async function suggestItemName(base64Images: string[], runtimeApiKey?: string, modelName?: string) {
  const ai = getAI(runtimeApiKey);
  const model = modelName || DEFAULT_NAMING_MODEL;
  
  const response = await ai.models.generateContent({
    model,
    contents: {
      parts: [
        ...base64Images.map(img => ({
          inlineData: {
            data: img.split(',')[1],
            mimeType: "image/png",
          },
        })),
        {
          text: "Based on these photos of a product for sale online, suggest a short, professional, and descriptive item name (3-6 words). Return ONLY the name, no extra text.",
        },
      ],
    },
  });

  return response.text?.trim() || "Item";
}

export async function applyAIFilterWithModel(base64Image: string, prompt: string, runtimeApiKey?: string, modelName?: string) {
  const ai = getAI(runtimeApiKey);
  const model = modelName || DEFAULT_IMAGE_EDIT_MODEL;

  const response = await ai.models.generateContent({
    model,
    contents: {
      parts: [
        {
          inlineData: {
            data: base64Image.split(',')[1],
            mimeType: "image/png",
          },
        },
        {
          text: prompt,
        },
      ],
    },
    config: {
      responseModalities: ["IMAGE", "TEXT"],
    },
  });

  for (const part of response.candidates?.[0]?.content?.parts || []) {
    if (part.inlineData) {
      const rawUrl = `data:image/png;base64,${part.inlineData.data}`;
      return await compressDataUrl(rawUrl);
    }
  }

  throw new Error("No image generated by AI");
}

export const AI_PROMPTS = {
  ENHANCE: "Enhance this product photo for an online store. Improve lighting, clarity, and colors while keeping it natural.",
  CLEAN_BACKGROUND: "Remove the background and replace it with a clean, professional studio white background. Keep the product (clothing/accessory) perfectly intact.",
  LIFESTYLE: "Place this item in a professional lifestyle setting suitable for an online fashion store. Ensure the lighting matches.",
};
