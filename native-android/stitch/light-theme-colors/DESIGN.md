---
name: Precision Technical Light
colors:
  surface: '#fbf8ff'
  surface-dim: '#dad9e3'
  surface-bright: '#fbf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f4f2fd'
  surface-container: '#eeedf7'
  surface-container-high: '#e8e7f1'
  surface-container-highest: '#e3e1ec'
  on-surface: '#1a1b22'
  on-surface-variant: '#3c4a42'
  inverse-surface: '#2f3038'
  inverse-on-surface: '#f1effa'
  outline: '#6c7a71'
  outline-variant: '#bbcabf'
  surface-tint: '#006c49'
  primary: '#006c49'
  on-primary: '#ffffff'
  primary-container: '#10b981'
  on-primary-container: '#00422b'
  inverse-primary: '#4edea3'
  secondary: '#5d5e60'
  on-secondary: '#ffffff'
  secondary-container: '#dfdfe0'
  on-secondary-container: '#616364'
  tertiary: '#a43a3a'
  on-tertiary: '#ffffff'
  tertiary-container: '#fc7c78'
  on-tertiary-container: '#711419'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#6ffbbe'
  primary-fixed-dim: '#4edea3'
  on-primary-fixed: '#002113'
  on-primary-fixed-variant: '#005236'
  secondary-fixed: '#e2e2e3'
  secondary-fixed-dim: '#c6c6c7'
  on-secondary-fixed: '#1a1c1d'
  on-secondary-fixed-variant: '#454748'
  tertiary-fixed: '#ffdad7'
  tertiary-fixed-dim: '#ffb3af'
  on-tertiary-fixed: '#410005'
  on-tertiary-fixed-variant: '#842225'
  background: '#fbf8ff'
  on-background: '#1a1b22'
  surface-variant: '#e3e1ec'
typography:
  h1:
    fontFamily: Inter
    fontSize: 48px
    fontWeight: '600'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  h2:
    fontFamily: Inter
    fontSize: 30px
    fontWeight: '600'
    lineHeight: '1.2'
    letterSpacing: -0.01em
  h3:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '500'
    lineHeight: '1.3'
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
  body-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.5'
  mono-label:
    fontFamily: JetBrains Mono
    fontSize: 13px
    fontWeight: '500'
    lineHeight: '1.2'
  mono-data:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.6'
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  base: 4px
  xs: 0.5rem
  sm: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  gutter: 1.5rem
  container-max: 1280px
---

## Brand & Style

The visual identity of this design system centers on technical rigor, clarity, and an "airy" architectural feel. It is designed for high-density information environments where cognitive load must be minimized through generous white space and precise alignment. 

The aesthetic blends **Minimalism** with **Corporate Modern** sensibilities. By prioritizing a clean white canvas, the system allows data and technical content to take center stage. The emotional response should be one of calm focus, reliability, and professional authority. Every element exists with purpose, utilizing thin strokes and subtle transitions to create a sophisticated, high-end toolset for experts.

## Colors

The palette is anchored by a pure white background to maximize luminosity and perceived space. Surface areas use a soft gray to define functional zones without breaking the light, open feel.

*   **Primary Action:** Emerald-500 is reserved strictly for interactive elements, success states, and primary calls to action. It provides a vibrant, high-contrast focal point against the neutral backdrop.
*   **Neutral Palette:** A sophisticated range of grays manages the hierarchy. Use #F4F4F5 for secondary surfaces (like sidebars or card backgrounds) and #E4E4E7 for structural borders.
*   **Typography:** Jet Black (#09090B) is used for headings to ensure maximum legibility, while a softer Zinc (#52525B) is used for body text to reduce eye strain during long reading sessions.

## Typography

This design system utilizes a dual-font approach to distinguish between narrative content and technical data.

*   **Inter:** The primary workhorse. It is used for all UI labels, headings, and body copy. Its neutral, geometric construction supports the "airy" feel while maintaining high readability at various sizes.
*   **JetBrains Mono:** Employed for "technical values," including code snippets, data tables, coordinates, and status labels. This provides a clear visual cue that the information is system-generated or mathematically precise.

Headings should use tight letter spacing and heavier weights to provide a strong structural anchor, while body text requires generous line heights to preserve the system's breathable character.

## Layout & Spacing

The layout philosophy follows a **fixed grid** for primary page containers, ensuring content remains centered and legible on large displays. Internal components utilize a fluid 8px-based spacing rhythm to maintain consistency.

*   **Grid:** A 12-column system with 24px (1.5rem) gutters is standard for dashboard layouts. 
*   **Margins:** Page-level margins should be a minimum of 32px (2rem) to reinforce the airy aesthetic.
*   **Density:** While the system is precise, it avoids "cramping" information. Use the `md` and `lg` spacing tokens for vertical sectioning to ensure the UI feels open and unhurried.

## Elevation & Depth

Hierarchy is established through **low-contrast outlines** and **tonal layers** rather than heavy shadows.

*   **Tiers:** The background is #FFFFFF. Level 1 surfaces (cards, sidebars) use #F4F4F5. Level 2 surfaces (active items, popovers) use #FFFFFF but are defined by a subtle 1px border (#E4E4E7).
*   **Shadows:** When depth is required (such as for dropdowns or modals), use "Ambient Shadows"—extremely diffused, low-opacity (2-4%) neutral grays. These should feel like a soft glow rather than a physical drop shadow.
*   **Borders:** Use a consistent 1px stroke for all containers. This reinforces the "precise" feel and provides clear definition between the white background and light gray surfaces.

## Shapes

The shape language is "Soft," utilizing small radii to maintain a disciplined, technical appearance. 

*   **Standard Elements:** Buttons, input fields, and small cards use a 0.25rem (4px) corner radius.
*   **Large Containers:** Section containers and large modals use a 0.5rem (8px) radius.
*   **Interaction:** Avoid pill-shaped buttons unless used for specific tags or chips; rectangular shapes with soft corners better reflect the system's professional and precise intent.

## Components

Components are designed to feel lightweight and responsive, with transitions that use a `cubic-bezier(0.4, 0, 0.2, 1)` timing function for a smooth, high-end feel.

*   **Buttons:** Primary buttons use a solid Emerald-500 fill with white text. Secondary buttons use a white background with an #E4E4E7 border and #09090B text. The hover state should involve a subtle shift in background brightness.
*   **Inputs:** Fields are defined by an #E4E4E7 border and #FFFFFF background. On focus, the border transitions to Emerald-500 with a faint 2px outer glow of the same color at 10% opacity.
*   **Technical Chips:** Use JetBrains Mono at 12px for chips. These should have a background of #F4F4F5 and no border for a clean, integrated look.
*   **Lists:** Data lists should utilize subtle horizontal separators (#E4E4E7) and generous padding (12px to 16px) to maintain the airy feel.
*   **Cards:** Use a #FFFFFF background with a 1px #E4E4E7 border. Do not use shadows for standard cards; reserve shadows for floating or hovered elements.
*   **Status Indicators:** Use small, solid circles. Emerald for "Active/Success," Zinc-400 for "Inactive," and Amber/Red for technical warnings, always paired with a Mono-label.