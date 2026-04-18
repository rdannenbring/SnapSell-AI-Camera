# SnapSell AI Camera — Application Design Description

## Overview

**SnapSell AI Camera** is a native Android app (built with Capacitor + React + TypeScript) designed for professional online sellers to quickly photograph, edit, and save product photos. It uses the device camera to capture images at various aspect ratios, offers manual adjustments and AI-powered filters (via Google Gemini), and saves finished photos directly to a user-selected folder on the device.

**Target users:** Professional online marketplace sellers (eBay, Poshmark, Mercari, etc.) who need high-quality product photos quickly.

**Platform:** Android native (Capacitor wrapper). The app runs fullscreen with a dark theme.

**Tech stack:** React 19, TypeScript, Tailwind CSS v4, Framer Motion, Lucide icons, Inter font, @capacitor/camera-filesystem, @capawesome/capacitor-file-picker, Google Gemini AI API.

---

## Global Design System

- **Theme:** Dark mode throughout. Backgrounds are `bg-black` (#000) or `bg-zinc-900` (#18181b) or `bg-zinc-950` (#09090b).
- **Typography:** Inter (sans-serif) for all UI text. JetBrains Mono for numeric values. Font weights range from 300–700.
- **Colors:**
  - Primary accent: Emerald-500 (#10b981) for positive actions (Save, Keep, Finish, active states).
  - Destructive: Red-500 (#ef4444) for delete actions.
  - AI accent: Blue-400 (#60a5fa), Purple-400 (#c084fc), Emerald-400 (#34d399) for different AI filter types.
  - Text: White at various opacities (100%, 70%, 60%, 50%, 40%, 30%, 20%) for hierarchy.
  - Borders: `border-white/5` through `border-white/10` (very subtle dividers).
- **Shapes:** Rounded-full for buttons and toggles. Rounded-xl/2xl for cards and sections. Rounded-lg for thumbnails.
- **Blur/backdrop:** `backdrop-blur-md` and `backdrop-blur-xl` used extensively for glass-like overlays.
- **Spacing:** 4px base unit. Common padding: p-4 (16px), p-6 (24px).
- **Icon library:** Lucide React (24px default, 16–20px for inline).

---

## Screen 1: Camera View (Default/Home Screen)

**Purpose:** Primary capture interface. This is the first screen users see when they open the app. It provides a live camera preview, aspect ratio selection, zoom controls, and a shutter button.

### Layout (top to bottom)

#### Top Bar (floating, transparent gradient overlay)
- **Left:** Settings gear icon button — circular, `bg-white/10 backdrop-blur-md`, opens Settings screen.
- **Center:** Aspect ratio toggle pills — a horizontal pill group with `bg-white/10 backdrop-blur-md` background. Three options: `1:1`, `4:3`, `16:9`. The active ratio has `bg-white text-black`; inactive are `text-white/70`. Rounded-full with small text.
- **Right:** Camera flip button — circular, `bg-white/10 backdrop-blur-md`, toggles between front (`user`) and rear (`environment`) cameras. Shows RefreshCw icon.

#### Camera Preview (center, flex-1)
- A video element filling a container sized to the selected aspect ratio:
  - `1:1` → square (`aspect-square`)
  - `4:3` → `aspect-[4/3]`
  - `16:9` → `aspect-[16/9]`
- The container is `max-w-2xl` (672px), centered horizontally and vertically, with `shadow-2xl`.
- **Grid overlay:** A 3×3 grid of thin white lines at 20% opacity over the preview (rule-of-thirds guide). Non-interactive.
- **Pinch-to-zoom:** Supported via touch gesture on the preview area.
- **Front camera:** Video is horizontally mirrored (`-scale-x-100`).
- **Error state:** If camera is inaccessible, shows a centered message "Unable to access camera. Please check permissions." with a Retry button.

#### Bottom Controls (black background, fixed at bottom)

**State A — Live Camera (default)**

1. **Zoom Presets Row** — Six circular buttons in a horizontal row: `0.5x`, `1x`, `2x`, `3x`, `4x`, `5x`.
   - Active: `bg-white text-black border-white scale-110` (slightly larger).
   - Inactive: `bg-white/5 text-white/50 border-white/10`.
   - Each button is 40×40px with 10px bold text.

2. **Zoom Slider** — Horizontal range input (0.5–5.0, step 0.1).
   - ZoomOut icon on the left, ZoomIn icon on the right.
   - Current zoom value shown as `0.0x` in mono font on the far right.
   - Icons and value label are `text-white/60`.

3. **Main Action Row** — Three elements in a row:
   - **Left: Photo counter** — 48×48px circle, `bg-zinc-900 border border-white/10`. Shows the count of captured photos in white bold text. Empty when no photos captured.
   - **Center: Shutter button** — Large circular capture button. Outer ring: 80×80px, `border-4 border-white`. Inner fill: 64×64px, solid `bg-white`. Tactile press animation (`active:scale-90`). Group hover scales inner circle slightly.
   - **Right: Process/Next button** — 48×48px circle with ArrowRight icon.
     - When photos exist: `bg-emerald-500 text-white` with shadow.
     - When no photos: `bg-white/5 text-white/20 cursor-not-allowed`.
     - Advances to Editor screen.

**State B — Photo Preview (after capture)**

After taking a photo (if "Show Preview After Capture" is enabled in settings), the camera preview is replaced by the captured image, and the bottom controls change to three action buttons:

1. **Delete** — Red theme. Circle with Trash2 icon (`bg-red-500/10`). Label "DELETE" in 10px uppercase.
2. **Re-take** — Neutral theme. Circle with RotateCcw icon (`bg-white/5`). Label "RE-TAKE" in 10px uppercase.
3. **Keep** — Green theme. Circle with Check icon (`bg-emerald-500/10`). Label "KEEP" in 10px uppercase.

These buttons are horizontally centered with generous spacing, each showing icon + label vertically stacked.

---

## Screen 2: Editor View

**Purpose:** Multi-photo editing workspace. Users can adjust exposure/contrast/scale, apply AI filters, browse captured photos via thumbnails, name their item with AI assistance, and save all photos to device.

### Layout (top to bottom)

#### Header Bar
- **Background:** `bg-zinc-900/50 backdrop-blur-md` with subtle `border-b border-white/5`.
- **Left:** Close (X) button — `text-white/70`, returns to Camera View.
- **Center:** Item name input — A text field with `bg-white/5 border border-white/10 rounded-full`. Placeholder: "Enter item name...". Contains a Sparkles icon button (blue-400) on the right that triggers AI-powered name suggestion based on captured photos. When processing, the Sparkles icon spins.
- **Right:** "Finish" save button — `bg-emerald-500 text-white rounded-full`, pill shape with Save icon + "Finish" text. Shows "Saving..." when save is in progress. Has `shadow-lg shadow-emerald-500/20`.

#### Preview Area (center, flex-1)
- **Main image:** The currently selected photo displayed at `max-h-[60vh]` with `rounded-lg shadow-2xl object-contain`. CSS filters applied in real-time:
  - `brightness()` based on exposure slider value (range -100 to +100, default 0).
  - `contrast()` based on contrast slider value (same range).
  - `scale()` based on scale slider value (0.1 to 2.0, default 1.0).
- **Processing overlay:** When AI is processing, a full overlay appears over the image: `bg-black/60 backdrop-blur-sm`, centered spinner (animated border ring), and "AI Processing..." text with pulse animation.
- **Photo actions overlay:** On hover over the main image, two small circular buttons appear in the top-right corner:
  - Re-take button: `bg-black/60 backdrop-blur-md` with RotateCcw icon.
  - Delete button: `bg-red-500/60 backdrop-blur-md` with Trash2 icon.
- **Thumbnail strip:** Horizontal scrollable row of small (64×64px) thumbnail buttons below the main image. Each has `rounded-lg` corners. Selected thumbnail has `border-2 border-white scale-110 shadow-lg`. Unselected have `border-transparent opacity-50`.

#### Bottom Control Panel
- **Background:** `bg-zinc-900` with `rounded-t-3xl`, `border-t border-white/5`, generous padding (p-6).
- **Panel Header Row:**
  - **Left side:** Three tab buttons in a row:
    - **Adjust** (Sliders icon) — Manual exposure, contrast, scale sliders.
    - **AI Filters** (Sparkles icon) — AI-powered filter presets.
    - **Crop** (Crop icon) — Placeholder "coming soon" message.
    - Active tab is `text-white`, inactive is `text-white/40`. Each shows icon + 10px uppercase label.
  - **Right side:** "Apply to All" toggle — A label (`text-[10px] uppercase font-bold text-white/40`) plus a small toggle switch (40×20px). When on: `bg-emerald-500` with white dot on right. When off: `bg-white/10` with dim dot on left.

- **Tab Content:**

  **Adjust tab:**
  Three horizontal slider rows, each with:
  - Icon on the left (Sun for exposure, Contrast icon, Maximize for scale) at `text-white/60`.
  - Range input slider spanning the middle.
  - Numeric value display on the right in mono font at `text-white/60`.
  - Exposure: -100 to +100, default 0.
  - Contrast: -100 to +100, default 0.
  - Scale: 0.1 to 2.0 step 0.1, default 1.0.

  **AI Filters tab:**
  A 3-column grid of filter preset buttons. Each is a `bg-white/5 rounded-xl` card with icon + label:
  - **Enhance** — Wand2 icon (blue-400). Prompt: "Enhance this product photo for an online store."
  - **White BG** — Layers icon (emerald-400). Prompt: "Remove the background and replace it with a clean, professional studio white background."
  - **Lifestyle** — Image icon (purple-400). Prompt: "Place this item in a professional lifestyle setting."
  - Each card: `p-3`, centered icon (20px) + 10px label text, `hover:bg-white/10`.

  **Crop tab:**
  Placeholder text: "Crop tool coming soon in next update" — `text-white/40 text-sm`, centered.

---

## Screen 3: Settings View

**Purpose:** Configure app defaults and storage location. Full-screen overlay (z-index 60) that slides over the camera view.

### Layout

#### Header
- **Background:** `bg-zinc-950/80 backdrop-blur-xl`, sticky at top, with `border-b border-white/5`.
- **Left:** "Settings" title — `text-xl font-bold tracking-tight`.
- **Right:** Close (X) button — `p-2 rounded-full bg-white/5`.

#### Content (scrollable, p-6, sections with 32px gap)

**Section 1: Camera Defaults**
- Section header: "CAMERA DEFAULTS" — `text-xs font-bold text-white/30 uppercase tracking-widest`.
- Card: `bg-zinc-900 rounded-2xl`, containing two rows:
  - **Default Aspect Ratio** — Row with Layout icon + label on left, a segmented control on right (`bg-black/40` container with three pills: 1:1, 4:3, 16:9). Active: `bg-white text-black shadow-lg`. Inactive: `text-white/40`.
  - **Show Preview After Capture** — Row with Eye icon + label on left, toggle switch on right (48×24px). When on: `bg-white` with black dot. When off: `bg-white/10` with dim dot.
  - Rows separated by `border-b border-white/5`.

**Section 2: Storage**
- Section header: "STORAGE" — same style as above.
- Card: `bg-zinc-900 rounded-2xl`, containing:
  - **Save Location** — Full-width button. Folder icon + "Save Location" label + current path displayed in `text-xs text-white/30 break-all` below the label. Right side shows ChevronRight icon (`text-white/20`) and status text ("Native Android only" or "Opening..."). Disabled when not on native Android.

**Section 3: About**
- Section header: "ABOUT" — same style.
- Card: `bg-zinc-900 rounded-2xl`, containing:
  - Info icon + "SnapSell AI Camera" name + "Version 1.0.0 (Beta)" in `text-xs text-white/30`.

**Footer:**
- Centered text: "Designed for professional sellers. Powered by Google Gemini AI." — `text-[10px] text-white/20 uppercase tracking-widest`.

---

## Screen 4: Toast Notification

**Purpose:** Non-blocking feedback shown briefly after saving photos.

- Appears at the bottom of the screen, centered horizontally.
- **Style:** `bg-zinc-900/95` (near-black with slight transparency), `rounded-full` pill shape, white text at `text-sm`, horizontal padding, `shadow-lg`.
- **Position:** `fixed bottom-6 left-1/2 -translate-x-1/2`, z-index 50.
- **Duration:** Auto-dismisses after ~3.5 seconds (5 seconds for fallback/warning messages).
- **Messages:**
  - Success: "Saved N photo(s)."
  - Fallback: "Saved N photo(s) to app storage fallback. Check permissions and re-select Save Location."

---

## User Flow Summary

1. **Open app** → Camera View (rear camera active, 1:1 default ratio).
2. **Adjust settings** → Tap gear icon → Settings View → Configure defaults and save location → Close.
3. **Capture photos** → Select aspect ratio → Optionally adjust zoom → Tap shutter → Preview appears (if enabled) → Keep or Re-take → Repeat for multiple photos.
4. **Edit photos** → Tap next arrow → Editor View → Browse thumbnails → Adjust exposure/contrast/scale per photo → Optionally apply AI filters → Name item (with AI suggestion).
5. **Save** → Tap "Finish" → Photos saved to selected folder → Toast confirmation → Return to Camera View.

---

## Current Known Design Issues (for theming pass)

1. **Light gray text readability:** Many labels and secondary text use `text-white/30` or `text-white/20` which is nearly invisible on the dark background.
2. **Dark-on-dark contrast:** Some areas have `text-white/40` on `bg-zinc-900` which is hard to read, especially section headers like "CAMERA DEFAULTS", "STORAGE", "ABOUT".
3. **Inactive tab/filter states:** `text-white/40` on `bg-white/5` cards has very low contrast.
4. **Settings save location path:** `text-white/30 break-all` on dark background is nearly unreadable for long paths.
5. **Thumbnail strip:** Inactive thumbnails at `opacity-50` can be hard to see.
6. **Range slider tracks:** `bg-white/10` and `bg-white/20` slider tracks are very subtle against dark backgrounds.

---

## Color & Spacing Reference (Tailwind Classes Used)

| Element | Background | Text | Border |
|---------|-----------|------|--------|
| App background | `bg-black` | — | — |
| Card/panel | `bg-zinc-900` | — | `border-white/5` |
| Settings bg | `bg-zinc-950` | — | — |
| Primary button (active) | `bg-emerald-500` | `text-white` | — |
| Secondary button | `bg-white/10` | `text-white` | — |
| Inactive button | `bg-white/5` | `text-white/50` | `border-white/10` |
| Section header | — | `text-white/30` | — |
| Secondary label | — | `text-white/60` | — |
| Tertiary label | — | `text-white/40` | — |
| Faint text | — | `text-white/20` | — |
| Input field | `bg-white/5` | — | `border-white/10` |
| Active toggle | `bg-white` or `bg-emerald-500` | — | — |
| Inactive toggle | `bg-white/10` | — | — |
| Top bar gradient | `from-black/60 to-transparent` | — | — |
| Shutter button outer | — | — | `border-4 border-white` |
| Shutter button inner | `bg-white` | — | — |
| Toast | `bg-zinc-900/95` | `text-white` | — |