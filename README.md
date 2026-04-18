<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://github.com/user-attachments/assets/0aa67016-6eaf-458a-adb2-6e31a0763ed6" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/93b75ea8-5964-48b3-ba1d-3b74dd5ac6c8

## Run Locally

**Prerequisites:**  Node.js


1. Install dependencies:
   `npm install`
2. Set the `GEMINI_API_KEY` in [.env.local](.env.local) to your Gemini API key
3. Run the app:
   `npm run dev`

## Android Native Build (Capacitor)

This project is configured for Capacitor Android with real on-device file saving.

### Prerequisites

- Android Studio
- Android SDK + emulator or physical Android device
- Node.js

### Build and open Android project

1. Install dependencies:
   `npm install`
2. Build web assets and sync into Android:
   `npm run android:build`
3. Open in Android Studio:
   `npm run android:open`

Then run the app from Android Studio on your device/emulator.

### Storage behavior

- Default Android save location: `/storage/emulated/0/Pictures/SnapSell`
- In **Settings → Save Location**, tapping the row opens a native directory picker (Android native app only).
- On **Finish** in the editor, photos are written to the selected device folder.
- If writing directly to selected folder fails, the app falls back to app documents storage.
