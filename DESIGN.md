# DeskDibs — Design system

<!-- impeccable:design-schema 1 -->

**Surface:** the DeskDibs web app (Operate mode — the visitor completes a task:
claim a seat, check bookings, manage a team hold). **Direction:** neo-brutalist
**Bauhaus** — the office rendered as a working blueprint. Established Phase 5
(shell); re-themed and made interactive in the floor-map phase (`frontend/`).

## Thesis

The floor map is the interface, not a feature bolted onto a list. So the whole
app is drawn like an architect's floor plan: warm paper, near-black ink, thick
hairline borders and **hard offset shadows (no blur)**. Bauhaus primaries carry
function, never mood — **yellow = selected/active**, **red = the one primary
action**, and flat category hues label the rooms. Seat labels (`R3-A2`) are
coordinate data, set in a monospace grid face like instrument readouts.

## Palette

Paper + ink base, Bauhaus primaries for function, flat category fills for rooms,
and a seat-state set kept deliberately separate so a state's meaning never rides
on brand styling.

| Token | Hex | Use |
|---|---|---|
| `paper` / `paper-dim` | `#f4f1e9` / `#eae4d6` | Canvas, recessed panels |
| `ink` / `ink-soft` | `#141414` / `#57544c` | Borders, text, occupied seats |
| `bauhaus-yellow` | `#f5c518` | Selected / active / primary highlight |
| `bauhaus-red` | `#e5372a` | Book Now, footer accent line |
| `bauhaus-blue` | `#2b6ce5` | Wayfinding accent, "yours" seats |
| Room fills | blue / green / lavender / beige / purple / soft-yellow / grey | reception · meeting · cabin · pantry · server · collab · utilities |

Seat states are **square tiles**, each a distinct fill + icon + label (colour is
never the only signal): available (cream `faChair`), occupied (solid ink
`faUser`), yours (blue `faCircleUser`), selected (yellow `faSquareCheck`),
team-hold (lavender `faUsers`), checked-in (green `faUserCheck`), disabled (grey
`faBan`), pending (yellow `faHourglassHalf`). The registry
(`src/lib/seatState.ts`) is the single source; the map tiles, the list fallback
and the legend all read it, so they can never disagree on what a colour means.

## Type

Two families, chosen together:

- **Space Grotesk** — display + UI. A grotesque with real character (not the
  generic neutrality every AI-generated UI converges on); set uppercase with
  tracking for titles, tags and structural labels.
- **IBM Plex Mono** — data face. Seat codes, counts and the letter-spaced
  eyebrows (`.eyebrow`). A monospace grid visually separates "data" from "chrome".

Both load via Google Fonts in `index.html`, with system fallbacks in
`src/index.css`.

## Layout & components

- **App shell:** a solid ink header (wordmark in the mono face, uppercase nav
  with a yellow underline on the active route, signed-in chip, logout) over the
  paper canvas, closed by an ink footer with the signature red top-accent. Nav
  collapses to a hamburger below `md`; the body never scrolls horizontally.
- **Floor map (`src/components/floormap/`):** a three-layer, data-driven plan.
  1. *Building shell* — outer boundary + every room, drawn from a typed config
     (`src/lib/floorPlan.ts`): pantry / staircase / toilets / electrical / fire
     exit on the left; meeting rooms, cabins, collab across the top; reception /
     server / service on the right; balcony across the bottom. Decorative
     context, hidden from assistive tech.
  2. *Seat layer* — the bookable tiles, positioned live off the API's
     `table.posX/posY` (band + column, set by the `V4` migration) into the
     image's upper-10 / lower-8 bands.
  3. *Collab overlays* — Collab Alpha (dashed) + Collab Prime (yellow).
  The canvas pans and zooms as one unit (fits width at rest); a list-view
  fallback covers small screens and keyboard-first browsing.
- **Sidebar:** floor select · SELECTED SEAT · red BOOK NOW — the one primary
  action, on a hard shadow.

## Motion

Purposeful, never idle — every animation explains a state change.

- Select → quick pop to yellow. Claim in flight → PENDING. Success → a single
  **green ripple**. Lost race (409) → a short **red shake**, then the tile flips
  occupied. Someone else claims (live) → a **settle-in fade** to occupied.
- `prefers-reduced-motion: reduce` collapses all animation/transition durations
  globally at the CSS layer (`src/index.css`), so a new component can't skip it.

## Accessibility

WCAG AA. Solid ink `:focus-visible` rings. Each seat is a real button with an
accessible name (label + state + occupant); actionable seats are keyboard-
operable, and hover/focus reveals who's there. An ARIA live region announces
seat changes (own claims and live broadcasts). Icon-only controls carry
`aria-label`; decorative room blocks are `aria-hidden`.
