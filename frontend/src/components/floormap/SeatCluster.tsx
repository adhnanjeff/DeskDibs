import { SeatTile } from './SeatTile';
import type { SeatAnimation, SeatTileModel } from '../../lib/seatModel';
import { SEAT_TILE, SEAT_GAP, POD_LABEL_H, POD_LABEL_GAP } from '../../lib/floorPlan';
import { layoutPod } from '../../lib/podLayout';

interface SeatClusterProps {
  label: string;
  center: { x: number; y: number };
  seats: SeatTileModel[];
  selectedSeatId: number | null;
  animatingSeat: { seatId: number; kind: SeatAnimation } | null;
  onSelect: (seat: SeatTileModel) => void;
  onHover: (seat: SeatTileModel | null, el: HTMLElement | null) => void;
}

/**
 * One desk pod, rotated to stand vertically like the real floor. Which cell each
 * seat occupies is decided by {@link layoutPod} — shared with the 3D view so the
 * two renderings can never disagree. Positioned by its top-centre, so every pod
 * in a band hangs from one line and a short 3-seater aligns with the tall ones.
 */
export function SeatCluster({
  label,
  center,
  seats,
  selectedSeatId,
  animatingSeat,
  onSelect,
  onHover,
}: SeatClusterProps) {
  const layout = layoutPod(seats);
  const left = center.x - layout.width / 2;

  return (
    <div className="absolute" style={{ left, top: center.y, width: layout.width }}>
      <div
        className="truncate text-center font-mono text-[10px] font-semibold uppercase tracking-wider text-ink/60"
        style={{ height: POD_LABEL_H, marginBottom: POD_LABEL_GAP }}
      >
        {label}
      </div>
      <div
        className="grid"
        style={{
          gridTemplateColumns: `repeat(${layout.cols}, ${SEAT_TILE}px)`,
          gridTemplateRows: `repeat(${layout.rows}, ${SEAT_TILE}px)`,
          gap: SEAT_GAP,
        }}
      >
        {layout.cells.map((cell) => (
          <div
            key={cell.seat.seatId}
            style={{ gridColumnStart: cell.col, gridRowStart: cell.row }}
          >
            <SeatTile
              seat={cell.seat}
              selected={cell.seat.seatId === selectedSeatId}
              animation={
                animatingSeat?.seatId === cell.seat.seatId ? animatingSeat.kind : null
              }
              onSelect={onSelect}
              onHover={onHover}
            />
          </div>
        ))}
      </div>
    </div>
  );
}
