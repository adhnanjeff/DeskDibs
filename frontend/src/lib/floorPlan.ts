/**
 * The office as a Bauhaus blueprint.
 *
 * The *building shell* — rooms, utilities, balcony — is presentation chrome, so
 * its geometry lives here as a typed config, not scattered through components.
 * The *bookable* seats are data: the backend hands each table a band + column
 * (`posY` / `posX`, set by the V4 migration) and {@link clusterCenter} resolves
 * those logical indices into pixel centres on this canvas. Change the office
 * shape by editing these numbers or the migration — never a JSX tree.
 *
 * All coordinates are in canvas units; the map scales the whole canvas to fit.
 */

export type RoomCategory =
  | 'reception'
  | 'meeting'
  | 'cabin'
  | 'pantry'
  | 'server'
  | 'collab'
  | 'utility'
  | 'exit'
  | 'balcony'
  | 'workspace';

export interface Room {
  id: string;
  label: string;
  category: RoomCategory;
  x: number;
  y: number;
  w: number;
  h: number;
  /** Dashed outline (open collaboration zones). */
  dashed?: boolean;
  /** Render the label letter-spaced across the block (the balcony strip). */
  spread?: boolean;
}

export const CANVAS = { w: 1240, h: 554 } as const;

/** Seat tile geometry, shared by the cluster renderer. */
export const SEAT_TILE = 26;
export const SEAT_GAP = 4;

/** The pod's name plate: its height and the gap below it, above the seat grid. */
export const POD_LABEL_H = 15;
export const POD_LABEL_GAP = 4;

export const ROOM_FILL: Record<RoomCategory, string> = {
  reception: 'var(--color-room-reception)',
  meeting: 'var(--color-room-meeting)',
  cabin: 'var(--color-room-cabin)',
  pantry: 'var(--color-room-pantry)',
  server: 'var(--color-room-server)',
  collab: 'var(--color-room-collab)',
  utility: 'var(--color-room-utility)',
  exit: '#f3cfc9',
  balcony: 'var(--color-room-workspace)',
  workspace: 'var(--color-room-workspace)',
};

// ── Workspace region + the band/column → pixel resolver ──────────────────────

const WS = { x: 152, y: 182, w: 926, h: 262 } as const;
const WS_BOTTOM_GAP = 8;
const SLOTS = 10;
const PITCH = WS.w / SLOTS;
const slotX = (slot: number) => WS.x + PITCH / 2 + slot * PITCH;
// The Y a pod's TOP sits on, per band. Pods hang down from here, so every pod
// in a band shares one top line — a short 3-seater lines up with the tall ones.
// Centred vertically in the workspace box: the two-band block (~219 tall) leaves
// ~22 above and ~21 below inside the 262-tall box.
const UPPER_TOP = WS.y + 22;
const LOWER_TOP = WS.y + 140;

/**
 * The 8 lower-band clusters skip slot 2 and slot 7, leaving room for the two
 * collaboration zones — matching the real floor, where collab areas break the
 * lower desk row after the second cluster and again near the right.
 */
const LOWER_SLOTS = [0, 1, 3, 4, 5, 6, 8, 9];

/** Resolve a table's logical (band, column) to the top-centre of its pod. */
export function clusterCenter(band: number, col: number): { x: number; y: number } {
  if (band === 0) return { x: slotX(col), y: UPPER_TOP };
  return { x: slotX(LOWER_SLOTS[col] ?? col), y: LOWER_TOP };
}

const COLLAB_ALPHA_X = slotX(2);
const COLLAB_PRIME_X = slotX(7);

// ── The building shell ───────────────────────────────────────────────────────

interface StripItem {
  id: string;
  label: string;
  category: RoomCategory;
  weight: number;
}

/** Lay a run of rooms across a horizontal strip, widths proportional to weight. */
function layoutStrip(
  startX: number,
  endX: number,
  y: number,
  h: number,
  items: StripItem[],
): Room[] {
  const gap = 4;
  const totalWeight = items.reduce((sum, item) => sum + item.weight, 0);
  const usable = endX - startX - gap * (items.length - 1);
  const unit = usable / totalWeight;
  let cursor = startX;
  return items.map((item) => {
    const w = item.weight * unit;
    const room: Room = { id: item.id, label: item.label, category: item.category, x: cursor, y, w, h };
    cursor += w + gap;
    return room;
  });
}

const TOP_Y = 24;
const TOP_H = 150;
const LEFT_X = 24;
const LEFT_W = 120;
const RIGHT_X = 1086;
const RIGHT_W = 130;
const WS_BOTTOM = WS.y + WS.h;

/**
 * The left column, top to bottom: a long pantry, the fire staircase, the services shaft, and a
 * short fire exit. Derived rather than written as four magic numbers so the column always ends
 * flush with the workspace, whatever the pantry is given.
 */
const PANTRY_H = 196;
const COLUMN_GAP = 4;
const STAIRCASE_Y = TOP_Y + PANTRY_H + 8;
const STAIRCASE_H = 90;
const SHAFT_Y = STAIRCASE_Y + STAIRCASE_H + COLUMN_GAP;
const SHAFT_H = 62;
const FIRE_EXIT_Y = SHAFT_Y + SHAFT_H + COLUMN_GAP;

/**
 * How far the fire-exit landing and the entrance lobby stand proud of the building line. On the
 * real plan both break the rectangle — the fire exit to the left, the entry to the right — and
 * flattening them was what made the drawn floor read as a box rather than as this floor.
 */
const OVERHANG_X = 4;
const ENTRY_OVERHANG = 18;

// Rooms across the top corridor, right of the pantry. The 4th room (after the
// pantry and two manager cabins) is the server room, per the finalized plan.
const topStrip = layoutStrip(WS.x, WS.x + WS.w, TOP_Y, TOP_H, [
  { id: 'manager-cabin-1', label: 'Manager Cabin', category: 'cabin', weight: 1 },
  { id: 'manager-cabin-2', label: 'Manager Cabin', category: 'cabin', weight: 1 },
  { id: 'server-room', label: 'Server Room', category: 'server', weight: 1 },
  { id: 'manager-cabin-3', label: 'Manager Cabin', category: 'cabin', weight: 1 },
  // Capacities left to right along the top corridor. The wider rooms get proportionally more
  // width, so the strip reads as the floor does rather than as equal boxes.
  { id: 'm1', label: '6P Meeting', category: 'meeting', weight: 1.1 },
  { id: 'm2', label: '8P Meeting', category: 'meeting', weight: 1.3 },
  { id: 'm3', label: '4P Meeting', category: 'meeting', weight: 0.95 },
  { id: 'director-cabin', label: 'Director Cabin', category: 'cabin', weight: 1 },
  { id: 'm4', label: '4P Meeting', category: 'meeting', weight: 0.95 },
]);

/**
 * Rooms are drawn back-to-front: the open workspace box first (it sits behind
 * the clusters), then the enclosed rooms, then the collab overlays.
 */
export const ROOMS: Room[] = [
  { id: 'workspace', label: 'Open Workstations', category: 'workspace', x: WS.x, y: WS.y, w: WS.w, h: WS.h },

  { id: 'pantry', label: 'Dry Pantry', category: 'pantry', x: LEFT_X, y: TOP_Y, w: LEFT_W, h: PANTRY_H },
  ...topStrip,

  // Right side: the walk-in entrance juts out at the top, then a small reception,
  // a larger meeting room, and the service area at the bottom.
  { id: 'entrance', label: 'Office Entry', category: 'utility', x: RIGHT_X, y: TOP_Y - 6, w: RIGHT_W + ENTRY_OVERHANG, h: 100 },
  { id: 'reception', label: 'Reception', category: 'reception', x: RIGHT_X, y: 118, w: RIGHT_W, h: 92 },
  { id: 'right-meeting', label: '12P Meeting', category: 'meeting', x: RIGHT_X, y: 214, w: RIGHT_W, h: 150 },
  { id: 'service', label: 'Service Area', category: 'utility', x: RIGHT_X, y: 368, w: RIGHT_W, h: WS_BOTTOM - 368 },

  // Left utilities below the pantry — no toilets on this floor. The fire exit is the smallest
  // of the three and, like the real floor, its landing juts out past the building line: it is
  // drawn wider than the column and starting left of LEFT_X.
  { id: 'fire-staircase', label: 'Fire Staircase', category: 'utility', x: LEFT_X, y: STAIRCASE_Y, w: LEFT_W, h: STAIRCASE_H },
  { id: 'electrical', label: 'Elec / PHE Shaft', category: 'utility', x: LEFT_X, y: SHAFT_Y, w: LEFT_W, h: SHAFT_H },
  { id: 'fire-exit', label: 'Fire Exit', category: 'exit', x: OVERHANG_X, y: FIRE_EXIT_Y, w: LEFT_W + (LEFT_X - OVERHANG_X), h: WS_BOTTOM - FIRE_EXIT_Y },

  { id: 'balcony', label: 'Open Balcony', category: 'balcony', x: LEFT_X, y: WS_BOTTOM + WS_BOTTOM_GAP, w: 1192, h: 84, spread: true },

  {
    id: 'collab-alpha',
    label: 'Collab Zone Alpha',
    category: 'collab',
    x: COLLAB_ALPHA_X - 44,
    y: LOWER_TOP - 2,
    w: 88,
    h: 106,
    dashed: true,
  },
  {
    id: 'collab-prime',
    label: 'Collab Hub Prime',
    category: 'collab',
    x: COLLAB_PRIME_X - 46,
    y: LOWER_TOP - 4,
    w: 92,
    h: 110,
  },
];
