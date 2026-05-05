# Design System Specification

## 1. Overview & Creative North Star: "The Precision Lens"
This design system is built for the elite marketplace seller—users who view their smartphone not just as a communication device, but as a high-performance optical instrument. 

**Creative North Star: The Precision Lens**
The aesthetic rejects the "web-app" look in favor of an **Editorial Pro-Tool** philosophy. We mimic the high-contrast, tactile interfaces of Leica cameras and Phase One digital backs. The UI does not sit "on top" of the screen; it emerges from the void. By utilizing a "Deep Dark" foundation, we eliminate visual noise, allowing the product photography and AI-assisted data to command absolute focus.

**Breaking the Template:**
*   **Asymmetric Breathing Room:** Eschew rigid 16dp gutters. Use expansive, intentional negative space to group features.
*   **Data as Art:** Numeric values are treated with the reverence of a watch face, using mono-spaced typography to signal technical accuracy.
*   **Tactile Depth:** We move away from flat cards toward "optical layers" using glassmorphism and tonal stacking.

---

## 2. Colors: The Void and The Pulse
The palette is rooted in `surface_container_lowest` (#000000) to ensure infinite contrast and power efficiency on OLED displays.

### The "No-Line" Rule
**Explicit Instruction:** Traditional 1px solid borders are strictly prohibited for sectioning content. Boundaries are defined by the "Tonal Shift" method. A card should be distinguishable from the background only by its shift to `surface_container` (#191919) or `surface_container_high` (#1f1f1f).

### Surface Hierarchy & Nesting
*   **Level 0 (Canvas):** `surface` (#0e0e0e) or `surface_container_lowest` (#000000).
*   **Level 1 (Sections):** `surface_container_low` (#131313) for large layout blocks.
*   **Level 2 (Interactive Elements):** `surface_container` (#191919) for primary cards.
*   **Level 3 (Pop-overs/Modals):** `surface_container_highest` (#262626) to create maximum "lift."

### The "Glass & Gradient" Rule
AI-driven features must utilize `secondary_container` with a `backdrop-blur-md` (12px–16px) to create a "Frosted Intelligence" look. 
*   **Signature Texture:** Primary buttons use a linear gradient from `primary` (#69f6b8) to `primary_container` (#06b77f) at a 135° angle, providing a metallic, "anodized" finish.

---

## 3. Typography: Editorial Authority
We pair the Swiss-style neutrality of **Inter** with the technical precision of **JetBrains Mono**.

| Level | Token | Font | Size | Weight | Tracking |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Display** | `display-lg` | Inter | 3.5rem | 700 (Bold) | -0.02em |
| **Header** | `headline-sm` | Inter | 1.5rem | 600 (Semi) | -0.01em |
| **Data/ISO** | `title-md` | JetBrains Mono | 1.125rem | 500 (Med) | 0.05em |
| **Body** | `body-md` | Inter | 0.875rem | 400 (Reg) | 0.01em |
| **Technical Label** | `label-sm` | JetBrains Mono | 0.6875rem | 700 (Bold) | 0.1em (Caps) |

**Rationale:** Use JetBrains Mono for all "changing" data (prices, exposure values, AI confidence scores) to prevent layout jitter and emphasize the "pro-tool" nature.

---

## 4. Elevation & Depth: Tonal Layering
We do not use shadows to simulate height; we use light.

*   **Layering Principle:** To "lift" a component, move it one step up the `surface_container` scale. A card on the home screen should be `surface_container_low`, and a button inside that card should be `surface_container_high`.
*   **Ambient Glow:** For floating AI action buttons, use a 24px blur shadow with 8% opacity, color-matched to `primary` (#69f6b8). It should look like a soft LED glow reflecting off a black glass desk.
*   **The Ghost Border:** For accessibility in input fields, use `outline_variant` (#484848) at **15% opacity**. It should be felt, not seen.

---

## 5. Components: The Professional Toolset

### Buttons (Rounded-Full)
*   **Primary:** `primary` gradient background, `on_primary` text. High-performance "Shutter" style.
*   **AI Action:** Glassmorphism (`secondary` @ 20% opacity) + `backdrop-blur-md`.
*   **Tertiary:** No background. Text in `primary_dim` with an icon.

### Cards (Rounded-2xl)
*   **The "No-Divider" Rule:** Content within cards must be separated by whitespace (16dp/24dp) or a change in typography weight. Never use horizontal lines.
*   **Background:** Use `surface_container` for product cards.

### Input Fields
*   **Style:** Minimalist underline or subtle tonal shift. No boxes.
*   **Focus State:** The label transitions to `primary` (#69f6b8), and the cursor adopts a soft glow.

### Specialized Components
*   **The AI "HUD" Overlay:** A semi-transparent pane using `surface_container_highest` @ 70% opacity with a heavy blur. Used for real-time camera settings and AI suggestions.
*   **Confidence Meter:** A thin, horizontal track using `primary` for the fill and `surface_variant` for the background, indicating AI object-detection accuracy.

---

## 6. Do's and Don'ts

### Do
*   **Do** use JetBrains Mono for all currency and numeric values. It signals accuracy.
*   **Do** use `primary` (#69f6b8) sparingly. It is a "Success" and "Action" signal, not a decorative element.
*   **Do** lean into asymmetry. A large display-font price off-set to the right creates a premium, editorial feel.

### Don't
*   **Don't** use 100% white (#FFFFFF) for body text. Use `on_surface_variant` (#ababab) to reduce eye strain and maintain the "Deep Dark" mood.
*   **Don't** use standard Android "Shadows." They look muddy on pure black. Use tonal shifts or colored glows.
*   **Don't** use 90-degree corners. Everything must feel precision-milled (Rounded-2xl or Rounded-Full).

---

**Director’s Final Note:**
*This system is not about "filling a screen." It is about framing the user's inventory. Every pixel of emerald green must feel earned. If a screen feels too dark, do not add lines—add space.*