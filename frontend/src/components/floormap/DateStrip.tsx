import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCircleCheck } from '@fortawesome/free-solid-svg-icons';
import type { components } from '../../api/schema';

type DayAvailability = components['schemas']['DayAvailabilityView'];

interface DateStripProps {
  days: DayAvailability[];
  selectedDate: string | null;
  onSelect: (date: string) => void;
}

/**
 * This week, with how busy each day is (PLAN.md §7).
 *
 * <p>The point is to let somebody see which day is quiet <em>before</em> committing to it, so each
 * day carries a fill bar rather than only a number — you can scan the row and pick the gap. Days
 * you already hold a desk on are marked, because the most common reason to look at this strip is to
 * find out which days you are already coming in.
 *
 * <p>Dates are formatted from the server's own ISO strings, never from a client-side "today": the
 * office decides what day it is.
 */
export function DateStrip({ days, selectedDate, onSelect }: DateStripProps) {
  return (
    <section aria-label="Pick a day" className="mb-4">
      <p className="eyebrow mb-1.5 text-center text-[11px] text-ink/60">This week</p>
      {/*
       * Centred on the page. `w-max` sizes the row to its content so `mx-auto` has
       * something to centre; `max-w-full` hands it back to the scroll container once
       * the week is wider than the viewport. Centring with `justify-center`
       * instead would put the first days past the left scroll origin, out of reach.
       * The page body never scrolls sideways either way.
       *
       * `overflow-x-auto` makes this a scroll container, which clips vertically too — so the
       * vertical padding is not decoration: without it the hover lift and the focus ring on a
       * day tile are sliced off at the top.
       */}
      <ul className="mx-auto flex w-max max-w-full gap-1.5 overflow-x-auto px-1 py-1.5">
        {days.map((day) => (
          <li key={day.date}>
            <DayButton
              day={day}
              selected={day.date === selectedDate}
              onSelect={onSelect}
            />
          </li>
        ))}
      </ul>
    </section>
  );
}

function DayButton({
  day,
  selected,
  onSelect,
}: {
  day: DayAvailability;
  selected: boolean;
  onSelect: (date: string) => void;
}) {
  const iso = day.date ?? '';
  const bookableSeats = day.bookableSeats ?? 0;
  const booked = day.bookedSeats ?? 0;
  // Desks under a live team hold. Counted against the day exactly as booked ones are: this number
  // has to be the same number the map draws as available for that date, or the strip contradicts
  // the floor sitting right beneath it.
  const held = day.heldSeats ?? 0;
  const taken = booked + held;
  const free = Math.max(0, bookableSeats - taken);
  const fullness = bookableSeats === 0 ? 0 : Math.round((taken / bookableSeats) * 100);
  const yours = day.yourSeatLabel;
  // Whether the office is open — decided by the server, which also refuses a claim on a closed
  // day. This only stops the click; it is not what enforces the rule.
  const open = day.bookable ?? true;
  // Told by the server, not inferred from position in the row: past the same-day cut-off the
  // strip opens on tomorrow, so the first tile is very often not today at all.
  const isToday = day.today ?? false;

  const date = new Date(`${iso}T00:00:00`);
  const weekday = date.toLocaleDateString(undefined, { weekday: 'short' });
  const dayOfMonth = date.toLocaleDateString(undefined, { day: 'numeric' });

  // The accessible name says everything the bar and the tick say visually — a fill percentage
  // rendered only as a coloured strip is invisible to a screen reader.
  const longDate = date.toLocaleDateString(undefined, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });
  // Holds are named rather than folded silently into the total, so a day that looks busier than
  // the booking count explains has an answer on the tile itself.
  const label = open
    ? `${longDate}${isToday ? ' (today)' : ''}: ${free} of ${bookableSeats} desks free${
        held > 0 ? ` (${held} held for teams)` : ''
      }${yours ? `. You have ${yours}.` : ''}`
    : `${longDate}: the office is closed`;

  return (
    <button
      type="button"
      onClick={() => onSelect(iso)}
      disabled={!open}
      aria-pressed={open ? selected : undefined}
      aria-label={label}
      title={label}
      className={`flex w-[5rem] shrink-0 flex-col items-center gap-1 ui-edge px-1.5 py-2 ${
        open
          ? `border-ink transition-transform hover:-translate-y-px ${
              selected ? 'bg-selected shadow-[var(--dd-shadow-sm)]' : 'bg-paper'
            }`
          : // Closed days stay in the row rather than vanishing: a fortnight with holes in it is
            // harder to read than one where every day is present and the shut ones say so.
            'cursor-not-allowed border-dashed border-ink/35 bg-paper-dim'
      }`}
    >
      <span
        className={`whitespace-nowrap font-mono text-[10px] font-bold ui-label ${
          open ? 'text-ink/60' : 'text-ink/35'
        }`}
      >
        {isToday ? 'Today' : weekday}
      </span>
      <span
        className={`font-mono text-lg font-bold leading-none ${open ? 'text-ink' : 'text-ink/35'}`}
      >
        {dayOfMonth}
      </span>

      {open ? (
        <>
          {/* Fullness as a bar: a row of these is scannable in a way a row of numbers is not. */}
          <span
            aria-hidden="true"
            className="h-1.5 w-full border border-ink/50 bg-paper-dim"
            title={`${fullness}% booked`}
          >
            <span className="block h-full bg-ink" style={{ width: `${fullness}%` }} />
          </span>
          <span className="flex h-3 items-center whitespace-nowrap font-mono text-[10px] font-bold ui-label text-ink/55">
            {yours ? (
              <FontAwesomeIcon
                icon={faCircleCheck}
                className="h-3 w-3 text-seat-checked-in"
                aria-hidden="true"
              />
            ) : (
              `${free} free`
            )}
          </span>
        </>
      ) : (
        <>
          <span aria-hidden="true" className="h-1.5 w-full" />
          <span className="flex h-3 items-center whitespace-nowrap font-mono text-[10px] font-bold ui-label text-ink/35">
            Closed
          </span>
        </>
      )}
    </button>
  );
}
