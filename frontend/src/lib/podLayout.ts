/**
 * Where each seat sits inside its pod — the one place that arithmetic lives.
 *
 * The 2D map renders a pod with CSS grid and the 3D view places meshes in world
 * space, but both must agree on which cell a seat occupies, or the two views
 * quietly disagree about the office. So the *logical* layout (column, row) and
 * the *canvas* position derived from it are computed here and consumed by both.
 */

import { SEAT_TILE, SEAT_GAP, POD_LABEL_H, POD_LABEL_GAP } from './floorPlan';
import type { SeatTileModel } from './seatModel';

export interface PodCell {
  seat: SeatTileModel;
  /** 1-based CSS-grid column. */
  col: number;
  /** 1-based CSS-grid row. */
  row: number;
}

export interface PodLayout {
  cols: number;
  rows: number;
  /** Grid box size in canvas units (excludes the label). */
  width: number;
  height: number;
  /** True when the pod is a single-file run rather than a back-to-back block. */
  straight: boolean;
  cells: PodCell[];
}

/**
 * A pod stands vertically: side A is the left column, side B the facing right
 * column, and a seat's index is its row top-to-bottom. A small pod (3 desks or
 * fewer) is a straight single-file run instead — the real floor's 3-seaters are
 * a line against the wall, not a 2+1 corner.
 */
export function layoutPod(seats: SeatTileModel[]): PodLayout {
  const straight = seats.length <= 3;
  const hasSideB = seats.some((s) => s.side === 'B');

  const cells: PodCell[] = straight
    ? [...seats]
        .sort((a, b) =>
          a.side === b.side ? a.seatIndex - b.seatIndex : a.side === 'A' ? -1 : 1,
        )
        .map((seat, i) => ({ seat, col: 1, row: i + 1 }))
    : seats.map((seat) => ({
        seat,
        col: seat.side === 'B' ? 2 : 1,
        row: seat.seatIndex,
      }));

  const cols = straight ? 1 : hasSideB ? 2 : 1;
  const rows = cells.reduce((max, cell) => Math.max(max, cell.row), 1);

  return {
    cols,
    rows,
    width: cols * SEAT_TILE + (cols - 1) * SEAT_GAP,
    height: rows * SEAT_TILE + (rows - 1) * SEAT_GAP,
    straight,
    cells,
  };
}

/**
 * The canvas-space centre of one cell, given the pod's top-centre anchor (what
 * {@link import('./floorPlan').clusterCenter} returns). Mirrors exactly how the
 * DOM lays the pod out: name plate first, then the grid hanging below it.
 */
export function cellCenter(
  anchor: { x: number; y: number },
  layout: PodLayout,
  cell: PodCell,
): { x: number; y: number } {
  const left = anchor.x - layout.width / 2;
  const top = anchor.y + POD_LABEL_H + POD_LABEL_GAP;
  return {
    x: left + (cell.col - 1) * (SEAT_TILE + SEAT_GAP) + SEAT_TILE / 2,
    y: top + (cell.row - 1) * (SEAT_TILE + SEAT_GAP) + SEAT_TILE / 2,
  };
}

/** The canvas-space centre of the pod's seat grid (used to place the desk). */
export function gridCenter(
  anchor: { x: number; y: number },
  layout: PodLayout,
): { x: number; y: number } {
  return {
    x: anchor.x,
    y: anchor.y + POD_LABEL_H + POD_LABEL_GAP + layout.height / 2,
  };
}
