# SnapSell AI Camera

A professional AI-powered camera app for creating product photos optimized for online marketplaces. Built with React, Capacitor, and Google Gemini AI.

## Features

- **Full-screen camera** with aspect ratio guides (1:1, 4:3, 16:9)
- **AI-powered filters**: Enhance, White Background, Lifestyle
- **Built-in editor** with exposure, contrast, and scale adjustments
- **Crop tool** with preset and free-form ratios
- **Batch editing** — apply adjustments to all photos at once
- **Direct save** to device storage via native Android integration
- **Android back gesture** support throughout the app

## Quick Start (Install from Release)

1. Download the latest APK from the [Releases](https://github.com/rdannenbring/SnapSell-AI-Camera/releases) page
2. Install on your Android device
3. Open the app → tap the ⚙️ Settings icon → enter your **Gemini API key** under **AI Configuration**
4. Get a free API key at [Google AI Studio](https://aistudio.google.com/apikey)

> **Note:** AI features (Enhance, White BG, Lifestyle) require a valid Gemini API key. You'll be prompted to add one in Settings if none is configured.

## Run Locally (Development)

**Prerequisites:** Node.js

1. Install dependencies:
   `npm install`
2. (Optional) Add your Gemini API key to `.env`:
   ```
   VITE_GEMINI_API_KEY=your_api_key_here
   ```
   This bakes the key into the build so you don't need to enter it in Settings each time.
3. Run the app:
   `npm run dev`

If you skip step 2, you can still enter an API key at runtime via **Settings → AI Configuration**.

## Android Native Build (Capacitor)

This project is configured for Capacitor Android with real on-device file saving.

### Prerequisites

- Android Studio
- Android SDK + emulator or physical Android device
- Node.js

### Build and open Android project

1. Install dependencies:
   `npm install`
2. (Optional) Add `VITE_GEMINI_API_KEY` to your `.env` file to embed the key at build time
3. Build web assets and sync into Android:
   `npm run android:build`
4. Open in Android Studio:
   `npm run android:open`

Then run the app from Android Studio on your device/emulator.

### API Key Behavior

| Scenario | Key Source |
|---|---|
| **Building from source** with `.env` | Key is embedded at build time (shown obscured in Settings) |
| **Installing from release APK** | Enter key manually in **Settings → AI Configuration** |
| **No key configured** | AI filters show an error directing you to Settings |

### Storage behavior

- Default Android save location: `/storage/emulated/0/Pictures/SnapSell`
- In **Settings → Save Location**, tapping the row opens a native directory picker (Android native app only).
- On **Finish** in the editor, photos are written to the selected device folder.
- If writing directly to selected folder fails, the app falls back to app documents storage.
