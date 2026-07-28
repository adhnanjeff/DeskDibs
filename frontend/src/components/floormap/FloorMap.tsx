import { useEffect, useMemo, useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faMagnifyingGlassPlus,
  faMagnifyingGlassMinus,
  faExpand,
} from '@fortawesome/free-solid-svg-icons';
import type { components } from '../../api/schema';
import { CANVAS, ROOMS, clusterCenter } from '../../lib/floorPlan';
import { cellCenter, layoutPod } from '../../lib/podLayout';
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
  /** Every picked seat — one when booking, a whole block when holding seats for a team. */
  selectedSeatIds: ReadonlySet<number>;
  onSelectSeat: (seat: SeatTileModel) => void;
  /** Overrides which seats are clickable; defaults to "only free ones". */
  canSelect?: (seat: SeatTileModel) => boolean;
  pendingSeatId: number | null;
  animatingSeat: { seatId: number; kind: SeatAnimation } | null;
  /** Seat pinned by a colleague search; the map zooms to it when this changes. */
  locatedSeatId?: number | null;
}

function SeatHoverCard({ seat, x, y }: { seat: SeatTileModel; x: number; y: number }) {
  const meta = SEAT_STATE_META[seat.displayState];
  return (
    <div
      className="pointer-events-none fixed z-50 -translate-x-1/2 -translate-y-full"
      style={{ left: x, top: y - 8 }}
    >
      <div className="ui-edge border-line bg-paper px-2.5 py-1.5 shadow-[var(--dd-shadow-sm)]">
        <p className="font-mono text-xs font-bold ui-label text-ink">
          {seat.seatLabel}
        </p>
        {seat.occupantName && (
          <p className="text-[11px] font-semibold text-ink/75">{seat.occupantName}</p>
        )}
        {/* The state always shows, even when the seat is somebody's — "Dev Employee" alone
            doesn't say whether they have actually turned up. */}
        <p className="font-mono text-[10px] ui-label text-ink/55">{meta.label}</p>
      </div>
    </div>
  );
}

export function FloorMap({
  seatMap,
  currentUserId,
  selectedSeatIds,
  onSelectSeat,
  canSelect,
  pendingSeatId,
  animatingSeat,
  locatedSeatId = null,
}: FloorMapProps) {
  // Destructured rather than kept as one `pz` object: the hook hands back a ref alongside its
  // values, and reading any property off that object during render trips react-hooks/refs.
  const {
    viewportRef,
    transform,
    canZoomIn,
    canZoomOut,
    onPointerDown,
    onPointerMove,
    onPointerUp,
    zoomIn,
    zoomOut,
    reset,
    focusOn,
  } = usePanZoom();
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

  /** Where every seat sits on the canvas — what the map needs to fly to one. */
  const seatPoints = useMemo(() => {
    const points = new Map<number, { x: number; y: number }>();
    for (const cluster of clusters) {
      const layout = layoutPod(cluster.seats);
      for (const cell of layout.cells) {
        points.set(cell.seat.seatId, cellCenter(cluster.center, layout, cell));
      }
    }
    return points;
  }, [clusters]);

  useEffect(() => {
    if (locatedSeatId == null) return;
    const point = seatPoints.get(locatedSeatId);
    if (point) focusOn(point.x, point.y);
  }, [locatedSeatId, seatPoints, focusOn]);

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
        ref={viewportRef}
        role="group"
        aria-label="Office floor map. Drag to pan, scroll or pinch to zoom."
        className="relative w-full cursor-grab touch-none overflow-hidden ui-edge border-line bg-paper-dim active:cursor-grabbing"
        style={{ aspectRatio: `${CANVAS.w} / ${CANVAS.h}` }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
      >
        <div
          className="absolute left-0 top-0 origin-top-left"
          style={{
            width: CANVAS.w,
            height: CANVAS.h,
            transform: `translate(${transform.x}px, ${transform.y}px) scale(${transform.scale})`,
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
              selectedSeatIds={selectedSeatIds}
              locatedSeatId={locatedSeatId}
              animatingSeat={animatingSeat}
              canSelect={canSelect}
              onSelect={onSelectSeat}
              onHover={handleHover}
            />
          ))}
        </div>
      </div>

      <div className="absolute bottom-3 right-3 flex flex-col gap-1.5">
        <ZoomButton label="Zoom in" icon={faMagnifyingGlassPlus} onClick={zoomIn} disabled={!canZoomIn} />
        <ZoomButton label="Zoom out" icon={faMagnifyingGlassMinus} onClick={zoomOut} disabled={!canZoomOut} />
        <ZoomButton label="Reset view" icon={faExpand} onClick={reset} disabled={false} />
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
      className="ui-control-icon flex h-9 w-9 items-center justify-center ui-edge border-line bg-paper text-ink shadow-[var(--dd-shadow-sm)] transition-transform hover:-translate-y-px disabled:opacity-40 disabled:hover:translate-y-0"
    >
      <FontAwesomeIcon icon={icon} className="h-4 w-4" aria-hidden="true" />
    </button>
  );
}
