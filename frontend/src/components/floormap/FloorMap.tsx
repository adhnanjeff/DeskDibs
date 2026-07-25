import { useMemo, useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faMagnifyingGlassPlus,
  faMagnifyingGlassMinus,
  faExpand,
} from '@fortawesome/free-solid-svg-icons';
import type { components } from '../../api/schema';
import { CANVAS, ROOMS, clusterCenter } from '../../lib/floorPlan';
import { SEAT_STATE_META } from '../../lib/seatState';
import { buildSeatModel, type SeatAnimation, type SeatTileModel } from '../../lib/seatModel';
import { RoomShell } from './RoomShell';
import { SeatCluster } from './SeatCluster';
import { usePanZoom } from './usePanZoom';

type SeatMapResponse = components['schemas']['SeatMapResponse'];

interface ClusterModel {
  tableId: number;
  label: string;
  center: { x: number; y: number };
  seats: SeatTileModel[];
}

interface FloorMapProps {
  seatMap: SeatMapResponse;
  currentUserId?: number | null;
  selectedSeatId: number | null;
  onSelectSeat: (seat: SeatTileModel) => void;
  pendingSeatId: number | null;
  animatingSeat: { seatId: number; kind: SeatAnimation } | null;
}

function SeatHoverCard({ seat, x, y }: { seat: SeatTileModel; x: number; y: number }) {
  const meta = SEAT_STATE_META[seat.displayState];
  return (
    <div
      className="pointer-events-none fixed z-50 -translate-x-1/2 -translate-y-full"
      style={{ left: x, top: y - 8 }}
    >
      <div className="border-2 border-ink bg-paper px-2.5 py-1.5 shadow-brutal-sm">
        <p className="font-mono text-xs font-bold uppercase tracking-wider text-ink">
          {seat.seatLabel}
        </p>
        <p className="text-[11px] font-semibold text-ink/75">
          {seat.occupantName ?? meta.label}
        </p>
      </div>
    </div>
  );
}

export function FloorMap({
  seatMap,
  currentUserId,
  selectedSeatId,
  onSelectSeat,
  pendingSeatId,
  animatingSeat,
}: FloorMapProps) {
  const pz = usePanZoom();
  const [hovered, setHovered] = useState<{ seat: SeatTileModel; x: number; y: number } | null>(
    null,
  );

  const clusters = useMemo<ClusterModel[]>(() => {
    const out: ClusterModel[] = [];
    for (const floor of seatMap.floors ?? []) {
      for (const zone of floor.zones ?? []) {
        for (const table of zone.tables ?? []) {
          out.push({
            tableId: table.tableId ?? -1,
            label: table.label ?? '',
            center: clusterCenter(table.posY ?? 0, table.posX ?? 0),
            seats: (table.seats ?? []).map((s) => buildSeatModel(s, currentUserId, pendingSeatId)),
          });
        }
      }
    }
    return out;
  }, [seatMap, currentUserId, pendingSeatId]);

  const handleHover = (seat: SeatTileModel | null, el: HTMLElement | null) => {
    if (!seat || !el) {
      setHovered(null);
      return;
    }
    const r = el.getBoundingClientRect();
    setHovered({ seat, x: r.left + r.width / 2, y: r.top });
  };

  return (
    <div className="relative">
      <div
        ref={pz.viewportRef}
        role="group"
        aria-label="Office floor map. Drag to pan, scroll or pinch to zoom."
        className="relative w-full cursor-grab touch-none overflow-hidden border-2 border-ink bg-paper-dim active:cursor-grabbing"
        style={{ aspectRatio: `${CANVAS.w} / ${CANVAS.h}` }}
        onPointerDown={pz.onPointerDown}
        onPointerMove={pz.onPointerMove}
        onPointerUp={pz.onPointerUp}
        onPointerCancel={pz.onPointerUp}
      >
        <div
          className="absolute left-0 top-0 origin-top-left"
          style={{
            width: CANVAS.w,
            height: CANVAS.h,
            transform: `translate(${pz.transform.x}px, ${pz.transform.y}px) scale(${pz.transform.scale})`,
          }}
        >
          {ROOMS.map((room) => (
            <RoomShell key={room.id} room={room} />
          ))}
          {clusters.map((cluster) => (
            <SeatCluster
              key={cluster.tableId}
              label={cluster.label}
              center={cluster.center}
              seats={cluster.seats}
              selectedSeatId={selectedSeatId}
              animatingSeat={animatingSeat}
              onSelect={onSelectSeat}
              onHover={handleHover}
            />
          ))}
        </div>
      </div>

      <div className="absolute bottom-3 right-3 flex flex-col gap-1.5">
        <ZoomButton label="Zoom in" icon={faMagnifyingGlassPlus} onClick={pz.zoomIn} disabled={!pz.canZoomIn} />
        <ZoomButton label="Zoom out" icon={faMagnifyingGlassMinus} onClick={pz.zoomOut} disabled={!pz.canZoomOut} />
        <ZoomButton label="Reset view" icon={faExpand} onClick={pz.reset} disabled={false} />
      </div>

      {hovered && <SeatHoverCard seat={hovered.seat} x={hovered.x} y={hovered.y} />}
    </div>
  );
}

function ZoomButton({
  label,
  icon,
  onClick,
  disabled,
}: {
  label: string;
  icon: typeof faExpand;
  onClick: () => void;
  disabled: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className="flex h-9 w-9 items-center justify-center border-2 border-ink bg-paper text-ink shadow-brutal-sm transition-transform hover:-translate-y-px disabled:opacity-40 disabled:hover:translate-y-0"
    >
      <FontAwesomeIcon icon={icon} className="h-4 w-4" aria-hidden="true" />
    </button>
  );
}
