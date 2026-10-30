# Third-party notices

This app (MIT, see [LICENSE](LICENSE)) bundles or adapts the following third-party material.

## Funnel Sans (font)

- File: `app/src/main/assets/fonts/FunnelSans-wght.ttf` (variable weight)
- Source: Google Fonts repository, <https://github.com/google/fonts/tree/main/ofl/funnelsans> (upstream project: <https://github.com/Dicotype/Funnel>)
- Copyright 2024 The Funnel Project Authors (https://github.com/Dicotype/Funnel). Designed by NORD ID and Kristian Möller.
- License: **SIL Open Font License, Version 1.1**. Full text: [`app/src/main/assets/fonts/FunnelSans-OFL.txt`](app/src/main/assets/fonts/FunnelSans-OFL.txt)
- The font is used unmodified. It covers Latin scripts only; Chinese, Japanese and Korean text uses the device's system font.

## Material Icons (icon shapes)

- Files: vector drawables in `app/src/main/res/drawable/ic_*.xml` (except the launcher icon) use path data from Google's Material Icons / Material Symbols.
- Source: <https://github.com/google/material-design-icons>
- License: **Apache License 2.0** (<https://www.apache.org/licenses/LICENSE-2.0>)
- Colors and sizes are set by the app at runtime.

## Design reference (nothing reused)

- The v0.6 visual style (neutral palette, rounded cards, pill buttons, bottom-sheet motion) was inspired by <https://designeer.xyz>.
- That site states its content is for inspiration only and that rights stay with the original creators ("All Rights Reserved"), so **no assets, code, text, logos, or images were copied** from it. Only general visual ideas (color tone, corner radius, spacing, easing feel) were recreated from scratch.

## Geist Mono (font)

- File: `app/src/main/assets/fonts/GeistMono-wght.ttf` (variable font, unmodified)
- Source: https://github.com/google/fonts/tree/main/ofl/geistmono (upstream: https://github.com/vercel/geist-font)
- Copyright 2024 The Geist Project Authors (https://github.com/vercel/geist-font.git)
- License: SIL Open Font License 1.1 — full text in `app/src/main/assets/fonts/GeistMono-OFL.txt`

## Design references for v0.7 (no code or assets reused)

v0.7's design re-creates ideas from these sites natively with Android Views (dot-matrix indicators, faded grid backgrounds,
mono badges/keycaps, hairline table rows, staggered blur-fade entrance). No source code, markup, CSS, images or pattern
data were copied from them.

| Site | Repository | License |
| --- | --- | --- |
| sv-animations.vercel.app | github.com/SikandarJODD/animations | MIT |
| sv-table.vercel.app | github.com/SikandarJODD/sv-table | MIT |
| sv-blocks.vercel.app | github.com/SikandarJODD/cnblocks | MIT |
| sv-efferd.pages.dev | github.com/SikandarJODD/sv-efferd | MIT |
| sv-matrix.vercel.app | github.com/SikandarJODD/sv-matrix | MIT |
| sv-particles.vercel.app | (no license file found) | — inspiration only |
| sv-agentation.com | github.com/SikandarJODD/sv-agentation | MIT |

## Values ported in v0.8 (MIT)

v0.8 ports a few numeric values (timings, easing curves, opacity levels and the dot ordering rule) from the following
MIT-licensed projects and re-implements them in Kotlin. No Svelte/TypeScript/CSS source files were copied.

- **SikandarJODD/animations** (sv-animations.vercel.app), `BlurFade`: duration 0.4 s, delay 0.04 s + n, offset 6, blur 6px,
  ease "easeOut" (cubic-bezier(0, 0, 0.58, 1)) → `Motion.enter` in `Motion.kt`.
- **SikandarJODD/sv-matrix** (sv-matrix.vercel.app), dot-matrix "path-wave": 1500 ms cycle, opacity base 0.16 / peak 1,
  `dmx-ripple` keyframes with cubic-bezier(0.42, 0, 0.58, 1), per-dot delay = path × 0.2333 × cycle, snake path order on
  the grid, reduced-motion static opacity 0.12 + path × 0.72 → `DotMatrixView.Pattern.SNAKE` in `DotMatrixView.kt`.

Both projects are distributed under the following license:

```
MIT License

Copyright (c) 2026 Sikandar Bhide

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

sv-particles has no license file, so nothing was taken from it (inspiration only).

## Design references for v0.8 (patterns only; no images or assets)

v0.8's UX changes were informed by browsing the public pages of these design galleries. The screenshots they show are
the copyright of their respective apps; none of them were copied into this app or repository. Only general interaction
patterns were re-created natively with Android Views.

| Gallery | Patterns taken |
| --- | --- |
| mobbin.com | onboarding flows, bottom sheet / action sheet |
| refero.design | onboarding / workflow sequences, quiet list styling |
| appshots.design | onboarding & settings flows |
| 60fps.design | bottom-sheet drag, pop / bounce micro-interactions |
| screensdesign.com | onboarding broken into numbered steps |
| chamjo.design | small count badges, polished spacing |
| pageflows.com | step-by-step recorded flows with progress |
| appllama.io, asoinspo.com | (store-listing galleries; no in-app pattern used) |
| collectui.com | empty state, settings page |
| spottedinprod.com | grouped settings with section headers |
| pttrns.com | empty state with one primary action, grouped settings |

## v0.9: design guideline (MIT) and new icons

- **ehmo/platform-design-skills** — `android-design-guidelines` (commit `dc2be825`), MIT License,
  <https://github.com/ehmo/platform-design-skills>. Used as the checklist for the v0.9 audit
  ([docs/DESIGN_AUDIT_v0.9.md](docs/DESIGN_AUDIT_v0.9.md)). Rule IDs (R1.1–R10.x) are cited in code comments; no code was copied.
  Copyright (c) the platform-design-skills authors. Permission is hereby granted, free of charge, under the terms of the MIT
  License (same text as above).
- **Material Icons** (Google, Apache License 2.0, <https://www.apache.org/licenses/LICENSE-2.0>) — new in v0.9:
  `ic_more_vert.xml` (more_vert), `ic_contrast.xml` (contrast), and the launcher-shortcut icons `shortcut_play.xml` /
  `shortcut_show.xml`, which draw the existing Material `ic_play` / `ic_touch` glyph paths on a circle.

