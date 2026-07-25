import { useMemo } from 'react';
import type { components } from '../../api/schema';
import { clusterCenter, ROOMS } from '../../lib/floorPlan';
import { DESK, roomVolume, worldLen, worldX, worldZ, type RoomVolume } from '../../lib/floor3d';
import { cellCenter, gridCenter, layoutPod } from '../../lib/podLayout';
import { buildSeatModel, type SeatTileModel } from '../../lib/seatModel';

type SeatMapResponse = components['schemas']['SeatMapResponse'];

export interface Seat3D {
  seat: SeatTileModel;
  position: [number, number, number];
  /** +1 when the desk is to the seat's right, -1 when it is to its left. */
  facing: 1 | -1;
}

export interface Pod3D {
  tableId: number;
  label: string;
  seats: Seat3D[];
  desk: { position: [number, number, number]; size: [number, number, number] };
  /** Where the pod's floating name plate hangs. */
  labelPosition: [number, number, number];
}

export interface FloorScene {
  rooms: RoomVolume[];
  pods: Pod3D[];
  seatsById: Map<number, Seat3D>;
}

/**
 * Projects the live seat map into placed 3D geometry. Room volumes come from the
 * shared floor plan and seat positions from the shared pod layout, so this hook
 * only converts and groups — it never invents a position of its own.
 */
export function useFloorScene(
  seatMap: SeatMapResponse,
  currentUserId: number | null | undefined,
  pendingSeatId: number | null,
): FloorScene {
  const rooms = useMemo(() => ROOMS.map(roomVolume), []);

  const pods = useMemo<Pod3D[]>(() => {
    const out: Pod3D[] = [];
    for (const floor of seatMap.floors ?? []) {
      for (const zone of floor.zones ?? []) {
        for (const table of zone.tables ?? []) {
          const models = (table.seats ?? []).map((s) =>
            buildSeatModel(s, currentUserId, pendingSeatId),
          );
          if (models.length === 0) continue;

          const anchor = clusterCenter(table.posY ?? 0, table.posX ?? 0);
          const layout = layoutPod(models);

          const seats: Seat3D[] = layout.cells.map((cell) => {
            const c = cellCenter(anchor, layout, cell);
            // A chair faces its desk: in a back-to-back pod the desk runs down
            // the middle, so column 1 looks right and column 2 looks left. A
            // single-file pod has its desk parked off to the right.
            const facing: 1 | -1 = layout.straight ? 1 : cell.col === 1 ? 1 : -1;
            return { seat: cell.seat, position: [worldX(c.x), 0, worldZ(c.y)], facing };
          });

          const grid = gridCenter(anchor, layout);
          const deskX = worldX(grid.x) + (layout.straight ? DESK.straightOffset : 0);

          out.push({
            tableId: table.tableId ?? -1,
            label: table.label ?? '',
            seats,
            desk: {
              position: [deskX, DESK.y, worldZ(grid.y)],
              size: [DESK.w, DESK.h, worldLen(layout.height)],
            },
            // Over the middle of the pod, just clear of the desk — anchored to
            // the pod's top it drifts up into the wall of the room behind it.
            labelPosition: [worldX(grid.x), 1.5, worldZ(grid.y)],
          });
        }
      }
    }
    return out;
  }, [seatMap, currentUserId, pendingSeatId]);

  const seatsById = useMemo(() => {
    const map = new Map<number, Seat3D>();
    for (const pod of pods) for (const s of pod.seats) map.set(s.seat.seatId, s);
    return map;
  }, [pods]);

  return { rooms, pods, seatsById };
}
