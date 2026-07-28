import type { Room } from '../../lib/floorPlan';
import { ROOM_FILL } from '../../lib/floorPlan';

/**
 * One block of the building shell — a room, utility, collab zone or the
 * balcony. Purely decorative context for the bookable seats, so it is hidden
 * from assistive tech; the seats carry all the interactive semantics.
 */
export function RoomShell({ room }: { room: Room }) {
  const isWorkspace = room.category === 'workspace';
  const isBalcony = room.category === 'balcony';
  const isPrime = room.id === 'collab-prime';
  const isAlpha = room.id === 'collab-alpha';

  const background = isPrime ? 'var(--color-selected)' : ROOM_FILL[room.category];
  const borderColor = isAlpha ? 'var(--color-info)' : 'var(--color-ink)';

  return (
    <div
      aria-hidden="true"
      className={`absolute ${room.dashed ? 'ui-edge border-dashed' : 'ui-edge'} ${
        isPrime ? 'shadow-[var(--dd-shadow)]' : ''
      }`}
      style={{
        left: room.x,
        top: room.y,
        width: room.w,
        height: room.h,
        background,
        borderColor,
      }}
    >
      {isWorkspace ? (
        <span className="absolute left-2 top-1.5 font-mono text-[9px] font-semibold ui-label text-ink/35">
          {room.label}
        </span>
      ) : (
        <span
          className={`flex h-full w-full items-center justify-center px-1 text-center font-mono font-semibold uppercase leading-tight ${
            isBalcony
              ? 'text-[11px] tracking-[0.45em] text-ink/70'
              : 'text-[9.5px] tracking-wider'
          } ${isAlpha ? 'text-info' : 'text-ink/75'}`}
        >
          {room.label}
        </span>
      )}
    </div>
  );
}
