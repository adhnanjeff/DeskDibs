import { SeatTile } from './SeatTile';
import type { SeatAnimation, SeatTileModel } from '../../lib/seatModel';
import { SEAT_TILE, SEAT_GAP } from '../../lib/floorPlan';

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
 * One desk pod, rotated to stand vertically like the real floor: side A is the
 * left column, side B the facing right column, and each seat's index is its row
 * top-to-bottom. A 3-seater has an empty B slot, so it renders as one short
 * column plus a single facing seat. Positioned by its centre — the backend owns
 * where it sits.
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
  // A small pod (3 desks or fewer) is a straight single-file run, not a
  // back-to-back block — so it draws as one column instead of a 2+1 corner.
  const straight = seats.length <= 3;
  const sideA = seats.filter((s) => s.side === 'A');
  const sideB = seats.filter((s) => s.side === 'B');
  const ordered = [...seats].sort((a, b) =>
    a.side === b.side ? a.seatIndex - b.seatIndex : a.side === 'A' ? -1 : 1,
  );

  const cols = straight ? 1 : sideB.length > 0 ? 2 : 1;
  const rows = straight ? ordered.length : Math.max(sideA.length, sideB.length, 1);

  const gridW = cols * SEAT_TILE + (cols - 1) * SEAT_GAP;
  const labelH = 15;
  // Top-anchored: every pod in a band hangs from the same top line, so a short
  // 3-seater lines up with the tall 6-seaters instead of floating centred.
  const left = center.x - gridW / 2;
  const top = center.y;

  return (
    <div className="absolute" style={{ left, top, width: gridW }}>
      <div
        className="mb-1 truncate text-center font-mono text-[10px] font-semibold uppercase tracking-wider text-ink/60"
        style={{ height: labelH }}
      >
        {label}
      </div>
      <div
        className="grid"
        style={{
          gridTemplateColumns: `repeat(${cols}, ${SEAT_TILE}px)`,
          gridTemplateRows: `repeat(${rows}, ${SEAT_TILE}px)`,
          gap: SEAT_GAP,
        }}
      >
        {(straight ? ordered : seats).map((seat, i) => (
          <div
            key={seat.seatId}
            style={
              straight
                ? { gridColumnStart: 1, gridRowStart: i + 1 }
                : { gridColumnStart: seat.side === 'B' ? 2 : 1, gridRowStart: seat.seatIndex }
            }
          >
            <SeatTile
              seat={seat}
              selected={seat.seatId === selectedSeatId}
              animation={animatingSeat?.seatId === seat.seatId ? animatingSeat.kind : null}
              onSelect={onSelect}
              onHover={onHover}
            />
          </div>
        ))}
      </div>
    </div>
  );
}
