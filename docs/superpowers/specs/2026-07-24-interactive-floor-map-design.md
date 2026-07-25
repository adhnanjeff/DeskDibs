# Interactive Bauhaus Floor Map — design & plan

**Date:** 2026-07-24 · **Branch:** `feat/interactive-floor-map` · **Supersedes** the
Phase-6 placeholder (`SeatMapPlaceholder`).

The product is the top-down floor map. This replaces the non-interactive
placeholder list with a real, clickable, live SVG floor plan matching the
office's *actual* layout, re-skinned into a neo-brutalist **Bauhaus** theme.

## Locked decisions

1. **Seat data:** Flyway `V4` repositions the **existing 18 tables**' `pos_x/pos_y`
   into the image's upper-10 / lower-8 bands. Labels, counts, capacities
   untouched → the 150-thread concurrency tests stay valid.
2. **Icons:** animated **FontAwesome** only (no emoji — honours `CLAUDE.md`).
3. **Design ref:** copy the **style/theme** of the user's Stitch mockup (not its
   layout — the layout is wrong there). Layout comes from the user's two floor
   images + the written spec.
4. **Scope:** re-skin the **whole app** (header + footer + map) this pass.
5. **Realtime:** included now — live seat changes over the existing WebSocket.

## Theme (from Stitch)

Neo-brutalist Bauhaus: warm cream/paper canvas, near-black ink, **thick black
hairline borders + hard offset shadows (no blur)**. Bauhaus primaries as
function — **yellow = selected/active**, **red = primary action**, blue & green
& lavender = room categories. Seats are **square tiles** (cream=available,
solid-black=occupied, yellow=selected) + icon + label so colour is never the
only signal. Type: **Space Grotesk** (display/UI, uppercase headings) + existing
**IBM Plex Mono** (labels, seat codes — preserves the "data face" rationale).

Room colours reconcile the user's explicit colour spec with the Stitch flat +
black-border treatment: reception light-blue, meeting green, cabins lavender,
pantry beige, server purple, collab yellow, utilities light-grey, balcony
white/outlined. Tunable live.

## Architecture

Three-layer SVG map, data-driven where it counts:

- **Building shell layer** — outer boundary + all rooms (pantry / fire staircase
  / toilets / electrical / fire exit on the **left**; meeting rooms, cabins,
  collab, along the **top**; reception / server / service on the **right**;
  balcony across the **bottom**). Geometry lives in a typed frontend config
  (`src/lib/floorPlan.ts`) — it's the *building*, not bookable inventory. Static,
  non-interactive, coloured + labelled.
- **Seat layer** — bookable. Driven live off the API's `table.posX/posY` + seats,
  auto-fitted into the workspace rect as two bands. Each table = a pod
  (`capacity/2` cols × 2 sides); each seat = a clickable, keyboard-focusable tile.
- **Collab overlays** — Collab Alpha (dashed) + Collab Prime (yellow) in the
  workspace's lower-centre.

**Components** (`src/components/floormap/`): `FloorMap` (SVG + pan/zoom `<g>`),
`RoomShell`, `SeatCluster`, `SeatTile`, `SeatTooltip` (hover/focus → occupant),
`FloorLegend`, `FloorSidebar` (floor select · SELECTED SEAT · red BOOK NOW),
`SeatListFallback` (mobile/no-map browsing). Shell: re-skinned `AppShell` +
new `AppFooter`.

**State/data:**
- `useSeatMap(date)` — existing query, unchanged.
- `useClaimSeat()` — new TanStack **mutation** → `POST /api/bookings` with a
  generated `Idempotency-Key`; **optimistic** cache update (seat → PENDING);
  on success → OCCUPIED/YOURS, on **409** → rollback + seat flips OCCUPIED.
- `useSeatMapLive(date)` — new; `@stomp/stompjs` client (`src/realtime/stompClient.ts`)
  connecting `ws://…/ws`, bearer token on the CONNECT frame, subscribes
  `/topic/seatmap/{date}`, merges `SeatStatusChanged` into the query cache via a
  **pure reducer** (unit-testable without a live socket).
- Selection is page-level state in `SeatMapPage` (one `selectedSeatId`).

## Backend

`V4__reposition_workstations.sql` — `UPDATE desk_table SET pos_x, pos_y` for the
18 labelled tables: R1–R10 → upper band, L1–L8 → lower band, evenly spread.
Positions only. Idempotent. DO-block guard asserts: still 18 tables / 110 seats,
10 tables in the upper band + 8 in the lower. `ddl-auto=validate` unaffected (no
schema change).

## Motion (must perform)

Select → quick pop to yellow. Claim in flight → PENDING pulse. Success → green
ripple. Lost race (409) → red shake, seat → occupied. Someone else claims (live)
→ fade to occupied. All gated by `prefers-reduced-motion` (already global in
`index.css`).

## Responsive & a11y

Pan/zoom the SVG (pointer + wheel + pinch); touch targets ≥44px; list-view
fallback toggle; no horizontal body scroll; verified at 375/768/1280.
Full keyboard seat navigation, visible focus rings, ARIA **live region**
announcing seat changes, each seat carries an accessible name (label + state +
occupant).

## Testing

- Frontend (Vitest + MSW): seat select → panel updates → BOOK NOW claims;
  409 → shake + occupied; keyboard selection; the live-merge reducer as a pure
  function. Update `SeatMapPage.test.tsx` (drops placeholder) + `AppShell.test.tsx`
  (reskin).
- Backend (Testcontainers): assert `V4` produced two distinct y-bands (10 upper /
  8 lower) and counts are unchanged. Concurrency tests must still pass untouched.

## Build phases

A. Theme foundation — tokens, fonts, `AppShell` reskin + `AppFooter`, `DESIGN.md`.
B. Backend `V4` migration + guard + test.
C. `floorPlan.ts` config + `FloorMap` shell (rooms layer) rendering.
D. Seat layer + selection + sidebar + legend + tooltip.
E. `useClaimSeat` (optimistic + 409 motion).
F. Realtime (`stompClient` + `useSeatMapLive` + reducer).
G. Responsive (pan/zoom + list fallback) + a11y + reduced-motion.
H. Tests + lint + build + impeccable; screenshots; hand off commit message.

## Out of scope (this pass)

Team-reserve flow (lives on the existing Reservations page — map only *shows*
TEAM_RESERVED), admin drag-to-reposition tables, multi-floor (only "Main Floor"
exists), real architectural SVG import (interim geometric plan for now).
