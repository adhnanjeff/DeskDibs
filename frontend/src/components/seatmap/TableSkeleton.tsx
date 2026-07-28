interface TableSkeletonProps {
  /** Number of seats on the table — 6 or 8 in the interim layout. Drives
   * how many seat dots render per long side so the skeleton's proportions
   * match the real table it stands in for. */
  capacity: 6 | 8;
  label: string;
}

/**
 * A shimmering placeholder shaped like one long table with a glass divider
 * down the middle and seats along both long sides — the actual geometry
 * PLAN.md §3 describes, not a generic rectangle. Sized so the layout does
 * not jump when the real seat map (Phase 6) replaces it.
 */
export function TableSkeleton({ capacity, label }: TableSkeletonProps) {
  const perSide = capacity / 2;
  const seatsA = Array.from({ length: perSide }, (_, i) => i);
  const seatsB = Array.from({ length: perSide }, (_, i) => i);

  return (
    <div
      className="relative flex w-full max-w-[220px] min-w-[160px] flex-col gap-1.5 overflow-hidden rounded-lg border border-line bg-white p-2.5 shadow-sm"
      aria-hidden="true"
    >
      <div className="flex justify-center gap-2">
        {seatsA.map((i) => (
          <div key={`a-${i}`} className="h-3 w-3 rounded-full bg-paper-dim" />
        ))}
      </div>
      <div className="h-6 rounded border border-dashed border-line bg-paper-dim" />
      <div className="flex justify-center gap-2">
        {seatsB.map((i) => (
          <div key={`b-${i}`} className="h-3 w-3 rounded-full bg-paper-dim" />
        ))}
      </div>
      <div className="mt-0.5 h-2 w-8 self-center rounded bg-paper-dim" />
      <span className="sr-only">Loading table {label}</span>
      <div className="skeleton-shimmer pointer-events-none absolute inset-0" />
    </div>
  );
}
