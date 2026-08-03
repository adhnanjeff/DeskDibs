import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCalendarDays, faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';

/**
 * A date field with a calendar big enough to actually point at.
 *
 * <p>This exists because {@code <input type="date">}'s drop-down is browser chrome, not page DOM.
 * It is drawn outside the document, has no pseudo-element to hook, and ignores the page's fonts,
 * colours and sizing entirely — so on a touch screen it stays a grid of ~20px hit targets no
 * matter what the field around it does. There is no CSS that makes the native panel bigger; the
 * only way to get a bigger calendar is to draw one.
 *
 * <p>Drawing it also buys the two things the native picker could never do here: day cells that
 * clear the 44px touch floor both themes are held to, and a panel that is styled by
 * {@code --dd-*} like everything else, so it does not read as a piece of the operating system
 * dropped into the middle of the office theme.
 *
 * <p>The value stays an ISO {@code yyyy-mm-dd} string end to end. Nothing here parses or formats
 * through the client's timezone — a {@code new Date(iso)} would resolve at UTC midnight and shift
 * the day backwards for anyone west of Greenwich, which is exactly the class of bug that makes a
 * booking land on the wrong date.
 */
interface DateFieldProps {
  label: string;
  /** ISO `yyyy-mm-dd`. */
  value: string;
  /** ISO `yyyy-mm-dd`; days before it cannot be chosen. */
  min?: string;
  onChange: (iso: string) => void;
}

const WEEKDAY_LABELS = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];

/** Parts of an ISO date, without going near `Date` and its timezone. */
function partsOf(iso: string): { year: number; month: number; day: number } | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  if (!match) return null;
  return { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) };
}

function isoOf(year: number, month: number, day: number): string {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate();
}

/** Monday-first index (0–6) of the 1st of a month. */
function firstWeekdayIndex(year: number, month: number): number {
  return (new Date(year, month - 1, 1).getDay() + 6) % 7;
}

function addMonths(year: number, month: number, delta: number): { year: number; month: number } {
  const zeroBased = year * 12 + (month - 1) + delta;
  return { year: Math.floor(zeroBased / 12), month: (zeroBased % 12) + 1 };
}

/** The visible day, formatted for a human. Built at local noon so no timezone can move it. */
function formatLong(iso: string): string {
  const parts = partsOf(iso);
  if (!parts) return '—';
  return new Date(parts.year, parts.month - 1, parts.day, 12).toLocaleDateString(undefined, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

function formatMonth(year: number, month: number): string {
  return new Date(year, month - 1, 1, 12).toLocaleDateString(undefined, {
    month: 'long',
    year: 'numeric',
  });
}

export function DateField({ label, value, min, onChange }: DateFieldProps) {
  const [open, setOpen] = useState(false);
  // The day the arrow keys are sitting on, which is not the same as the chosen day: you move
  // around the grid first and commit second, so roving focus needs its own cursor.
  const [cursor, setCursor] = useState(value);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const gridRef = useRef<HTMLDivElement>(null);
  const panelId = useId();

  const shown = partsOf(cursor) ?? partsOf(value) ?? { year: 2000, month: 1, day: 1 };
  const [view, setView] = useState({ year: shown.year, month: shown.month });

  // Opening always lands on the chosen day, whatever month was left on screen last time.
  const openPanel = useCallback(() => {
    const parts = partsOf(value);
    if (parts) {
      setCursor(value);
      setView({ year: parts.year, month: parts.month });
    }
    setOpen(true);
  }, [value]);

  const closePanel = useCallback(
    (returnFocus: boolean) => {
      setOpen(false);
      if (returnFocus) triggerRef.current?.focus();
    },
    [],
  );

  // Click-away and Escape. Both end with focus back on the trigger when the dismissal was
  // deliberate, so keyboard users are never dropped at the top of the document.
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        closePanel(true);
      }
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open, closePanel]);

  // Move the DOM focus onto whichever day the cursor names, so the browser scrolls it into view
  // and a screen reader announces it as the arrows travel.
  useEffect(() => {
    if (!open) return;
    gridRef.current?.querySelector<HTMLButtonElement>('[data-focus="true"]')?.focus();
  }, [open, cursor, view.year, view.month]);

  const total = daysInMonth(view.year, view.month);
  const lead = firstWeekdayIndex(view.year, view.month);
  const cells = useMemo(
    () => [
      ...Array.from({ length: lead }, () => null),
      ...Array.from({ length: total }, (_, i) => i + 1),
    ],
    [lead, total],
  );

  const disabled = (iso: string) => (min ? iso < min : false);

  const moveCursor = (deltaDays: number) => {
    const parts = partsOf(cursor) ?? partsOf(value);
    if (!parts) return;
    const moved = new Date(parts.year, parts.month - 1, parts.day + deltaDays, 12);
    const iso = isoOf(moved.getFullYear(), moved.getMonth() + 1, moved.getDate());
    if (disabled(iso)) return;
    setCursor(iso);
    setView({ year: moved.getFullYear(), month: moved.getMonth() + 1 });
  };

  const onGridKeyDown = (event: React.KeyboardEvent) => {
    const jumps: Record<string, number> = {
      ArrowLeft: -1,
      ArrowRight: 1,
      ArrowUp: -7,
      ArrowDown: 7,
      PageUp: -28,
      PageDown: 28,
    };
    const jump = jumps[event.key];
    if (jump === undefined) return;
    event.preventDefault();
    moveCursor(jump);
  };

  const choose = (iso: string) => {
    onChange(iso);
    closePanel(true);
  };

  const previous = addMonths(view.year, view.month, -1);
  // A month entirely before `min` has nothing to offer, so the arrow that leads there is spent.
  const canGoBack = !min || isoOf(previous.year, previous.month, daysInMonth(previous.year, previous.month)) >= min;

  return (
    <div ref={rootRef} className="relative">
      <span className="block text-sm font-bold ui-label text-ink">{label}</span>
      <button
        ref={triggerRef}
        type="button"
        onClick={() => (open ? closePanel(false) : openPanel())}
        // Down-arrow opens the calendar, which is what both the native control and every other
        // popover on the web do. Enter and Space already activate a button on their own.
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown' && !open) {
            event.preventDefault();
            openPanel();
          }
        }}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        aria-label={`${label}: ${formatLong(value)}. Press to choose a date.`}
        className="ui-control mt-1.5 flex w-full items-center justify-between gap-2 ui-edge border-line bg-white px-3.5 py-3 font-mono text-lg font-semibold text-ink"
      >
        {/*
          The written-out day, not the ISO string. The value on the wire stays `yyyy-mm-dd`, but
          the field is read by a person deciding which day to block out, and "Mon, Aug 3" answers
          that at a glance where "2026-08-03" has to be decoded first.
        */}
        <span>{value ? formatLong(value) : '—'}</span>
        <FontAwesomeIcon icon={faCalendarDays} className="h-4 w-4 shrink-0" aria-hidden="true" />
      </button>

      {open && (
        <div
          id={panelId}
          role="dialog"
          aria-modal="false"
          aria-label={`Choose a ${label.toLowerCase()} date`}
          className="absolute left-0 top-full z-30 mt-1 w-[20.5rem] ui-edge border-line bg-paper p-3 shadow-[var(--dd-shadow)]"
        >
          <div className="flex items-center justify-between gap-2">
            <button
              type="button"
              onClick={() => setView(addMonths(view.year, view.month, -1))}
              disabled={!canGoBack}
              aria-label="Previous month"
              className="ui-control-icon flex items-center justify-center ui-edge border-line bg-paper text-ink disabled:cursor-not-allowed disabled:opacity-30"
            >
              <FontAwesomeIcon icon={faChevronLeft} className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
            {/* Announced politely so arrowing across a month boundary says where you landed. */}
            <p aria-live="polite" className="text-base font-bold ui-label text-ink">
              {formatMonth(view.year, view.month)}
            </p>
            <button
              type="button"
              onClick={() => setView(addMonths(view.year, view.month, 1))}
              aria-label="Next month"
              className="ui-control-icon flex items-center justify-center ui-edge border-line bg-paper text-ink"
            >
              <FontAwesomeIcon icon={faChevronRight} className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
          </div>

          <div className="mt-2 grid grid-cols-7 gap-1" aria-hidden="true">
            {WEEKDAY_LABELS.map((day) => (
              <span
                key={day}
                className="flex h-6 items-center justify-center font-mono text-[11px] font-bold ui-label text-ink/50"
              >
                {day}
              </span>
            ))}
          </div>

          {/*
            One tab stop, not thirty-one: the grid is entered once and travelled with the arrow
            keys, which is how a date grid is expected to behave and spares anyone tabbing through
            the form a month of stops.
          */}
          <div
            ref={gridRef}
            role="grid"
            aria-label={formatMonth(view.year, view.month)}
            onKeyDown={onGridKeyDown}
            className="mt-1 grid grid-cols-7 gap-1"
          >
            {cells.map((day, index) => {
              if (day === null) return <span key={`lead-${index}`} />;
              const iso = isoOf(view.year, view.month, day);
              const isChosen = iso === value;
              const isCursor = iso === cursor;
              const isOff = disabled(iso);
              return (
                <button
                  key={iso}
                  type="button"
                  role="gridcell"
                  data-focus={isCursor ? 'true' : undefined}
                  tabIndex={isCursor ? 0 : -1}
                  disabled={isOff}
                  aria-selected={isChosen}
                  aria-label={formatLong(iso)}
                  onClick={() => choose(iso)}
                  className={`flex h-11 items-center justify-center ui-edge font-mono text-base font-bold ui-label transition-transform ${
                    isChosen
                      ? 'border-ink bg-selected text-ink shadow-[var(--dd-shadow-sm)]'
                      : 'border-transparent text-ink hover:-translate-y-px hover:border-line hover:bg-paper-dim'
                  } disabled:cursor-not-allowed disabled:border-transparent disabled:text-ink/25 disabled:hover:translate-y-0 disabled:hover:bg-transparent`}
                >
                  {day}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
