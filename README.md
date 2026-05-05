# SnapSell AI Camera

A professional AI-powered camera app for creating product photos optimized for online marketplaces. Built with React, Capacitor, and Google Gemini AI — with a fully native Android (Kotlin + Jetpack Compose) version.

## Features

- **Full-screen camera** with aspect ratio guides (1:1, 4:3, 16:9)
- **AI-powered filters**: Enhance, White Background, Lifestyle
- **Custom AI prompts** with voice input — describe how AI should modify your photo
- **Built-in editor** with exposure, contrast, and scale adjustments
- **Crop tool** with preset and free-form ratios (inline crop with drag handles)
- **Batch editing** — apply adjustments to all photos at once
- **AI listing generation** — auto-generate title, description, and suggested price
- **AI file naming** — auto-suggest descriptive file names from photo content
- **Direct save** to device storage via native Android integration
- **Android back gesture** support throughout the app

---

## Quick Start (Install from Release)

1. Download the latest APK from the [Releases](https://github.com/rdannenbring/SnapSell-AI-Camera/releases) page
2. Install on your Android device
3. Open the app → tap the ⚙️ Settings icon → enter your **Gemini API key** under **AI Configuration**
4. Get a free API key at [Google AI Studio](https://aistudio.google.com/apikey)

> **Note:** AI features (Enhance, White BG, Lifestyle, custom prompts) require a valid Gemini API key. You'll be prompted to add one in Settings if none is configured.

---

## Web App (React + Vite)

### Run Locally (Development)

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

---

## Capacitor Android Build

This project includes a Capacitor-wrapped Android app in the `android/` directory.

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

---

## Native Android App (Kotlin + Jetpack Compose)

A fully native Android version lives in the `native-android/` directory, built with Kotlin, Jetpack Compose, CameraX, and Hilt.

### Architecture

```
native-android/
├── app/src/main/java/com/snapsell/nativecamera/
│   ├── MainActivity.kt            # Single-activity host
│   ├── SnapSellApp.kt             # Compose navigation + Hilt entry
│   ├── camera/CameraManager.kt    # CameraX integration
│   ├── data/
│   │   ├── model/                 # Data models
│   │   ├── remote/                # Gemini API client
│   │   └── repository/            # Data repositories
│   ├── di/                        # Hilt modules
│   ├── image/                     # Image processing utilities
│   └── ui/
│       ├── camera/CameraScreen.kt # Full camera UI
│       ├── editor/
│       │   ├── EditorScreen.kt    # Photo editor + AI filters
│       │   ├── InlineCropPanel.kt # In-place crop tool
│       │   └── SnapSellCropActivity.kt
│       ├── settings/SettingsScreen.kt
│       └── theme/Theme.kt         # Obsidian Lens color palette
├── stitch/                        # Design reference screenshots
└── TASKS.md                       # Detailed feature checklist
```

### Key Technologies

| Component | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Camera | CameraX |
| DI | Hilt |
| AI | Google Gemini API (image editing + text generation) |
| Navigation | Compose Navigation |
| Storage | MediaStore + FileProvider |
| Crop | uCrop library |

### Features (Native)

- **Camera**: Full-screen CameraX preview, aspect ratio switching (1:1 / 4:3 / 16:9), front/back toggle, zoom slider + pinch-to-zoom (0.5x–5x), shutter with flash animation + haptic feedback
- **Editor**: Multi-photo support with thumbnail strip, per-photo adjustments (exposure, contrast), AI filters (Enhance / White BG / Lifestyle), custom AI prompt with voice input, inline crop with drag handles and rule-of-thirds grid, reset-to-original, AI-powered file naming
- **AI Filters**: Gemini-powered image editing with software fallback, custom text prompts via text field or voice input
- **Settings**: Gemini API key management, save location picker (native directory picker), image quality control, aspect ratio default, auto-suggest filename toggle
- **Storage**: Saves processed photos to user-selected folder, falls back to `Pictures/SnapSell` then app-specific storage

### Build and Run

**Prerequisites:** Android Studio, Android SDK, physical device or emulator

1. Open the `native-android/` directory in Android Studio
2. Let Gradle sync and download dependencies
3. Connect an Android device or start an emulator
4. Run the `app` configuration

Or from the command line:
```bash
cd native-android
./gradlew :app:assembleDebug
```

The debug APK will be at `native-android/app/build/outputs/apk/debug/`.

### API Key Configuration

In the native app, enter your Gemini API key in **Settings → AI Configuration**. The key is stored securely on-device via SharedPreferences. Get a free key at [Google AI Studio](https://aistudio.google.com/apikey).

### Deploy to Connected Device

```bash
cd native-android
./gradlew :app:installDebug
```
