/**
 * The 2D blueprint, lifted into three dimensions.
 *
 * There is no second floor plan. This module is a pure projection of
 * {@link import('./floorPlan')} — canvas units become world units, rooms become
 * extruded volumes, and seat cells become chair positions via
 * {@link import('./podLayout')}. Edit the office in `floorPlan.ts` or the
 * migration; the 3D view follows automatically.
 *
 * Axes: canvas X → world X (right), canvas Y → world Z (away from camera), and
 * world Y is up. The origin sits at the centre of the floor plate so orbiting
 * spins around the building rather than a corner.
 */

import { CANVAS, type Room, type RoomCategory } from './floorPlan';

/** Canvas units → world units. 1240×554 canvas becomes a 124×55 plate. */
export const WORLD_SCALE = 0.1;

export const worldX = (canvasX: number) => (canvasX - CANVAS.w / 2) * WORLD_SCALE;
export const worldZ = (canvasY: number) => (canvasY - CANVAS.h / 2) * WORLD_SCALE;
export const worldLen = (canvasUnits: number) => canvasUnits * WORLD_SCALE;

export const PLATE = {
  w: worldLen(CANVAS.w),
  d: worldLen(CANVAS.h),
} as const;

/**
 * Room heights are deliberately squashed — a true 3m ceiling at this scale would
 * be a 6-unit wall that hides the whole workspace from any angle a user actually
 * orbits to. These read as the massing blocks of a scale model: tall enough to
 * be rooms, low enough to see the floor plan they enclose.
 */
export const ROOM_HEIGHT: Record<RoomCategory, number> = {
  cabin: 1.9,
  meeting: 1.9,
  server: 2.1,
  reception: 1.5,
  pantry: 1.9,
  utility: 1.7,
  exit: 1.7,
  collab: 0.14,
  balcony: 0.1,
  workspace: 0.06,
};

export interface RoomVolume {
  room: Room;
  /** World-space centre of the box. */
  position: [number, number, number];
  /** World-space [width, height, depth]. */
  size: [number, number, number];
  /** True for the flat pads (workspace, balcony, collab) drawn as floor insets. */
  isPad: boolean;
}

export function roomVolume(room: Room): RoomVolume {
  const h = ROOM_HEIGHT[room.category];
  return {
    room,
    position: [worldX(room.x + room.w / 2), h / 2, worldZ(room.y + room.h / 2)],
    size: [worldLen(room.w), h, worldLen(room.h)],
    isPad: h < 0.3,
  };
}

// ── Furniture proportions, all in world units ────────────────────────────────

/** A seat cell is 26 canvas units across; the chair sits inside that footprint. */
export const CHAIR = {
  padW: 1.15,
  padD: 1.15,
  padH: 0.12,
  /** Height of the seat pad above the floor. */
  seatY: 0.5,
  backH: 0.62,
  backT: 0.14,
  legR: 0.075,
} as const;

export const DESK = {
  /** Desk slab width for a back-to-back pod (fits between the two chair rows). */
  w: 1.5,
  h: 0.09,
  y: 0.78,
  /** Sideways offset for a single-file pod, whose chairs share one column. */
  straightOffset: 1.35,
} as const;

/**
 * Can this browser actually give us a 3D context?
 *
 * Worth checking before mounting: three throws its "Error creating WebGL
 * context" from inside a promise, so React error boundaries never see it and
 * the failure surfaces as an unhandled rejection over a blank pane. A cheap
 * probe up front turns that into an explanation the user can act on. The probe
 * context is released immediately — browsers cap how many can exist at once.
 */
export function isWebGLAvailable(): boolean {
  if (typeof document === 'undefined') return false;
  try {
    const canvas = document.createElement('canvas');
    const gl =
      canvas.getContext('webgl2') ??
      canvas.getContext('webgl') ??
      canvas.getContext('experimental-webgl');
    if (!gl || !('getExtension' in gl)) return false;
    gl.getExtension('WEBGL_lose_context')?.loseContext();
    return true;
  } catch {
    return false;
  }
}

/**
 * Resolve a design token to a concrete colour. three.js needs a literal, but
 * the palette lives in `index.css` — reading it at runtime keeps one source of
 * truth (and lets the scene follow a theme swap) instead of a duplicated table.
 */
export function cssColor(token: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback;
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue(token)
    .trim();
  return value || fallback;
}

/**
 * The void the model sits in. Deliberately darker than `--color-paper`: the
 * plan is a lit scale model on a dark table, so the flat room colours read as
 * brightly as they do on paper in the 2D view.
 */
export const SCENE_BACKDROP = '#171614';

/** The scene's non-seat palette, resolved once per mount. */
export function readScenePalette() {
  return {
    ink: cssColor('--color-ink', '#141414'),
    paper: cssColor('--color-paper', '#f4f1e9'),
    paperDim: cssColor('--color-paper-dim', '#eae4d6'),
    yellow: cssColor('--color-bauhaus-yellow', '#f5c518'),
    room: {
      reception: cssColor('--color-room-reception', '#cfe3f7'),
      meeting: cssColor('--color-room-meeting', '#cfe9cf'),
      cabin: cssColor('--color-room-cabin', '#ddd5f2'),
      pantry: cssColor('--color-room-pantry', '#efe2c7'),
      server: cssColor('--color-room-server', '#cbb9ea'),
      collab: cssColor('--color-room-collab', '#f7e79a'),
      utility: cssColor('--color-room-utility', '#e6e2d8'),
      exit: '#f3cfc9',
      balcony: cssColor('--color-room-workspace', '#ffffff'),
      workspace: cssColor('--color-room-workspace', '#ffffff'),
    } satisfies Record<RoomCategory, string>,
  };
}

/** Seat-state fills, resolved from the same tokens the 2D tiles use. */
export function readSeatPalette() {
  return {
    AVAILABLE: cssColor('--color-seat-available', '#e9e2d0'),
    SELECTED: cssColor('--color-seat-selected', '#f5c518'),
    YOURS: cssColor('--color-seat-yours', '#2b6ce5'),
    OCCUPIED: cssColor('--color-seat-occupied', '#141414'),
    TEAM_RESERVED: cssColor('--color-seat-team-reserved', '#b9a4e6'),
    CHECKED_IN: cssColor('--color-seat-checked-in', '#1f9d6b'),
    DISABLED: cssColor('--color-seat-disabled', '#b7b3a8'),
    PENDING: cssColor('--color-seat-pending', '#f5c518'),
  };
}
