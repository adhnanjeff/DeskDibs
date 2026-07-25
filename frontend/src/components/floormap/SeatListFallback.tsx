import type { components } from '../../api/schema';
import { buildSeatModel, type SeatAnimation, type SeatTileModel } from '../../lib/seatModel';
import { SeatTile } from './SeatTile';

type SeatMapResponse = components['schemas']['SeatMapResponse'];

interface SeatListFallbackProps {
  seatMap: SeatMapResponse;
  currentUserId?: number | null;
  selectedSeatId: number | null;
  onSelectSeat: (seat: SeatTileModel) => void;
  pendingSeatId: number | null;
  animatingSeat: { seatId: number; kind: SeatAnimation } | null;
}

const noHover = () => {};

/**
 * A plain, scrollable browse of every seat grouped floor → zone → table — the
 * map-free fallback for small screens and keyboard-first use. Reuses the same
 * seat tiles, so selecting a seat here books it exactly as it does on the map.
 */
export function SeatListFallback({
  seatMap,
  currentUserId,
  selectedSeatId,
  onSelectSeat,
  pendingSeatId,
  animatingSeat,
}: SeatListFallbackProps) {
  return (
    <div className="flex flex-col gap-6">
      {(seatMap.floors ?? []).map((floor) =>
        (floor.zones ?? []).map((zone) => (
          <section key={`${floor.floorId}-${zone.zoneId}`}>
            <h3 className="eyebrow mb-2 text-[11px] text-ink/60">{zone.name}</h3>
            <div className="flex flex-wrap gap-3">
              {(zone.tables ?? []).map((table) => (
                <div
                  key={table.tableId}
                  className="border-2 border-ink bg-paper p-2.5 shadow-brutal-sm"
                >
                  <p className="mb-2 font-mono text-[10px] font-semibold uppercase tracking-wider text-ink/55">
                    {table.label}
                  </p>
                  <div className="grid max-w-[148px] grid-cols-3 gap-1.5">
                    {(table.seats ?? []).map((seat) => {
                      const model = buildSeatModel(seat, currentUserId, pendingSeatId);
                      return (
                        <SeatTile
                          key={model.seatId}
                          seat={model}
                          selected={model.seatId === selectedSeatId}
                          animation={
                            animatingSeat?.seatId === model.seatId ? animatingSeat.kind : null
                          }
                          onSelect={onSelectSeat}
                          onHover={noHover}
                        />
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          </section>
        )),
      )}
    </div>
  );
}
