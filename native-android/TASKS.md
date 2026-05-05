# SnapSell Native App — Development Task List

## Camera Screen
- [x] Camera preview with CameraX (full-screen, filling entire display)
- [x] Aspect ratio selection (1:1, 4:3, 16:9) with pill toggle in top bar
- [x] 4:3 frame with dark overlay bars (top/bottom), starting below top bar
- [x] 1:1 frame centered vertically with equal dark bars above and below
- [x] 16:9 immersive mode (full sensor, no overlay, tap to show/hide controls)
- [x] Front/back camera toggle
- [x] Zoom slider (0.5x–5x) with pinch-to-zoom gesture
- [x] Zoom preset buttons (0.5x, 1x, 2x, 5x) with text that fits inside circles
- [x] Shutter button with capture flash animation
- [x] Photo preview overlay after capture (AsyncImage)
- [x] Preview action buttons: FINISH / RE-TAKE / KEEP
- [x] Photo counter badge (bottom-left)
- [x] Process/next button (bottom-right, navigates to editor)
- [x] Captured image matches preview framing (CameraX setTargetAspectRatio)
- [x] Settings gear icon in top bar
- [x] Saved settings applied on startup (aspect ratio, quality, preview toggle)
- [x] Multiple photo collection (capturedPaths list, auto-keep when preview disabled)

## Settings Screen
- [x] Dark theme with gradient background
- [x] Gemini API key input with show/hide toggle
- [x] Default aspect ratio selector (1:1 / 4:3 / 16:9 pill buttons)
- [x] Image quality slider (50–100%)
- [x] Image quality presets (Low 60% / Med 80% / High 90% / Max 100%)
- [x] Estimated file size display (adjusts for quality + aspect ratio)
- [x] Show Preview After Capture toggle (properly aligned with text)
- [x] About section with version info
- [x] All settings persisted via SharedPreferences
- [x] Save location picker (choose output folder on device via OpenDocumentTree)

## Editor Screen
- [x] Photo preview with aspect ratio container
- [x] AI listing generation (Gemini API: title, description, suggested price)
- [x] Editable title, price, and description fields
- [x] Copy to clipboard button
- [x] Share button (Android share sheet)
- [x] Regenerate listing option
- [x] **Adjust tab** — Exposure/brightness slider (-100 to +100)
- [x] **Adjust tab** — Contrast slider (-100 to +100)
- [x] **Adjust tab** — Scale slider (0.1 to 2.0)
- [x] **AI Filters tab** — Enhance preset (improve photo quality)
- [x] **AI Filters tab** — White BG preset (remove/replace background)
- [x] **AI Filters tab** — Lifestyle preset (place item in lifestyle setting)
- [x] **Crop tab** — Placeholder "coming soon" message
- [x] **Tab UI** — Adjust / AI Filters / Crop tab navigation with icons
- [x] **Apply to All toggle** — Apply adjustments to all photos at once
- [x] **Multiple photo support** — Thumbnail strip for browsing captured photos
- [x] **Per-photo adjustments** — Each photo remembers its own edits
- [x] **Item name input** with AI name suggestion (Sparkles button)
- [x] **Finish/Save button** — Save all processed photos to device storage
- [x] **Processing overlay** — Spinner + "AI Processing..." text during filters

## Toast Notifications
- [x] Success toast after saving photos ("Saved N photo(s).")
- [x] Fallback/warning toast if save location unavailable
- [x] Auto-dismiss after ~3.5 seconds

## Core Infrastructure
- [x] CameraManager class (CameraX integration)
- [x] Aspect ratio crop at capture time
- [x] EXIF rotation handling
- [x] Front camera mirroring
- [x] Zoom via setLinearZoom (no double-crop)
- [x] Gemini API integration for listing generation
- [x] Theme system (Primary emerald, dark surfaces)
- [x] Navigation between Camera → Editor → Settings
- [x] Build & deploy script (`deploy-native.sh`)

## Polish & UX
- [x] Animated visibility for controls (fade in/out)
- [x] 16:9 immersive mode auto-hide controls after 5 seconds
- [x] 16:9 preview mode auto-hide controls after 3 seconds
- [x] Capture flash animation on shutter press
- [x] Haptic feedback on shutter press
- [x] Smooth transitions between screens (AnimatedContent slide)