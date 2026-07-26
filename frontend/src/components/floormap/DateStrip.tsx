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
 * The next two weeks, with how busy each day is (PLAN.md §7).
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
      <p className="eyebrow mb-1.5 text-[11px] text-ink/60">Next two weeks</p>
      {/* Scrolls inside itself on a narrow screen; the page body never scrolls sideways. */}
      <ul className="flex gap-1.5 overflow-x-auto pb-1">
        {days.map((day, index) => (
          <li key={day.date}>
            <DayButton
              day={day}
              isToday={index === 0}
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
  isToday,
  selected,
  onSelect,
}: {
  day: DayAvailability;
  isToday: boolean;
  selected: boolean;
  onSelect: (date: string) => void;
}) {
  const iso = day.date ?? '';
  const bookableSeats = day.bookableSeats ?? 0;
  const booked = day.bookedSeats ?? 0;
  const free = Math.max(0, bookableSeats - booked);
  const fullness = bookableSeats === 0 ? 0 : Math.round((booked / bookableSeats) * 100);
  const yours = day.yourSeatLabel;
  // Whether the office is open — decided by the server, which also refuses a claim on a closed
  // day. This only stops the click; it is not what enforces the rule.
  const open = day.bookable ?? true;

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
  const label = open
    ? `${longDate}${isToday ? ' (today)' : ''}: ${free} of ${bookableSeats} desks free${
        yours ? `. You have ${yours}.` : ''
      }`
    : `${longDate}: the office is closed`;

  return (
    <button
      type="button"
      onClick={() => onSelect(iso)}
      disabled={!open}
      aria-pressed={open ? selected : undefined}
      aria-label={label}
      title={label}
      className={`flex w-[4.25rem] shrink-0 flex-col items-center gap-1 border-2 px-1.5 py-2 ${
        open
          ? `border-ink transition-transform hover:-translate-y-px ${
              selected ? 'bg-bauhaus-yellow shadow-brutal-sm' : 'bg-paper'
            }`
          : // Closed days stay in the row rather than vanishing: a fortnight with holes in it is
            // harder to read than one where every day is present and the shut ones say so.
            'cursor-not-allowed border-dashed border-ink/35 bg-paper-dim'
      }`}
    >
      <span
        className={`font-mono text-[10px] font-bold uppercase tracking-wider ${
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
          <span className="flex h-3 items-center font-mono text-[10px] font-bold uppercase tracking-wider text-ink/55">
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
          <span className="flex h-3 items-center font-mono text-[10px] font-bold uppercase tracking-wider text-ink/35">
            Closed
          </span>
        </>
      )}
    </button>
  );
}
