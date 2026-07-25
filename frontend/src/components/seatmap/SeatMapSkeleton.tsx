import { TableSkeleton } from './TableSkeleton';

/**
 * Mirrors the interim 110-seat layout from PLAN.md §3: Right Wing R1–R10
 * (6 seats each) and Left Wing L1–L7 (6 seats each) plus L8 (8 seats). The
 * skeleton is shaped like the floor plan it stands in for, so nothing
 * shifts when the real seat map replaces it.
 */
const RIGHT_WING_TABLES = Array.from({ length: 10 }, (_, i) => ({
  label: `R${i + 1}`,
  capacity: 6 as const,
}));

const LEFT_WING_TABLES = [
  ...Array.from({ length: 7 }, (_, i) => ({
    label: `L${i + 1}`,
    capacity: 6 as const,
  })),
  { label: 'L8', capacity: 8 as const },
];

export function SeatMapSkeleton() {
  return (
    <div
      className="grid grid-cols-1 gap-8 md:grid-cols-2"
      data-testid="seatmap-skeleton"
    >
      <section aria-hidden="true">
        <div className="mb-3 h-4 w-20 rounded bg-slate-200" />
        <div className="flex flex-wrap gap-3">
          {LEFT_WING_TABLES.map((t) => (
            <TableSkeleton
              key={t.label}
              label={t.label}
              capacity={t.capacity}
            />
          ))}
        </div>
      </section>
      <section aria-hidden="true">
        <div className="mb-3 h-4 w-20 rounded bg-slate-200" />
        <div className="flex flex-wrap gap-3">
          {RIGHT_WING_TABLES.map((t) => (
            <TableSkeleton
              key={t.label}
              label={t.label}
              capacity={t.capacity}
            />
          ))}
        </div>
      </section>
      <span className="sr-only" role="status">
        Loading the seat map…
      </span>
    </div>
  );
}
