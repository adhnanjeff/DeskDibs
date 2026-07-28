import { useId, useMemo, useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faMagnifyingGlass, faUserCheck, faXmark } from '@fortawesome/free-solid-svg-icons';
import type { Colleague } from '../../lib/colleagueSearch';
import { searchColleagues } from '../../lib/colleagueSearch';

interface FindColleagueProps {
  people: Colleague[];
  onLocate: (person: Colleague) => void;
  /** The person currently pinned on the map, if any. */
  located: Colleague | null;
  onClear: () => void;
}

/**
 * "Where is Priya sitting today?" — and its mirror, "who is in R5-A2?".
 *
 * Answers by name or seat code, then hands the pick back so the map can fly to that desk. Only
 * people with a booking for the day being viewed can be found, which is the honest answer: the
 * app knows who claimed a desk, not who is in the building.
 */
export function FindColleague({ people, onLocate, located, onClear }: FindColleagueProps) {
  const [query, setQuery] = useState('');
  const inputId = useId();
  const results = useMemo(() => searchColleagues(people, query), [people, query]);
  const searching = query.trim() !== '';

  return (
    <div className="ui-edge border-line bg-paper p-4 shadow-[var(--dd-shadow)]">
      <label htmlFor={inputId} className="eyebrow text-[11px] text-ink/60">
        Find a colleague
      </label>
      <p className="mb-2 font-mono text-[10px] ui-label text-ink/40">
        {people.length} seated today
      </p>

      <div className="flex items-center gap-2 ui-edge border-line bg-white px-2 py-1.5">
        <FontAwesomeIcon
          icon={faMagnifyingGlass}
          className="h-3.5 w-3.5 shrink-0 text-ink/45"
          aria-hidden="true"
        />
        <input
          id={inputId}
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Name or seat…"
          autoComplete="off"
          className="w-full min-w-0 bg-transparent text-sm font-semibold text-ink outline-none placeholder:font-normal placeholder:text-ink/35"
        />
      </div>

      {searching && (
        <ul className="mt-2 flex flex-col gap-1">
          {results.length === 0 && (
            <li className="px-1 py-2 text-xs font-semibold text-ink/50">
              Nobody by that name has a desk today.
            </li>
          )}
          {results.map((person) => (
            <li key={person.userId}>
              <button
                type="button"
                onClick={() => {
                  onLocate(person);
                  setQuery('');
                }}
                className="flex w-full items-center gap-2 ui-edge border-transparent px-2 py-1.5 text-left hover:border-ink hover:bg-selected"
              >
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold text-ink">
                    {person.name}
                  </span>
                  <span className="block font-mono text-[11px] ui-label text-ink/55">
                    {person.seatLabel}
                    {person.checkedIn && ' · in'}
                  </span>
                </span>
                {person.checkedIn && (
                  <FontAwesomeIcon
                    icon={faUserCheck}
                    className="h-3.5 w-3.5 shrink-0 text-seat-checked-in"
                    aria-hidden="true"
                    title="Checked in"
                  />
                )}
              </button>
            </li>
          ))}
        </ul>
      )}

      {located && (
        <div className="mt-3 flex items-center gap-2 ui-edge border-line bg-selected px-2.5 py-2">
          <span className="min-w-0 flex-1">
            <span className="block truncate text-xs font-bold ui-label text-ink">
              {located.name}
            </span>
            <span className="block font-mono text-[11px] ui-label text-ink/70">
              {located.seatLabel} · {located.tableLabel}
            </span>
          </span>
          <button
            type="button"
            onClick={onClear}
            aria-label={`Stop showing ${located.name} on the map`}
            className="shrink-0 ui-edge border-line p-1 text-ink hover:bg-ink hover:text-paper"
          >
            <FontAwesomeIcon icon={faXmark} className="h-3 w-3" aria-hidden="true" />
          </button>
        </div>
      )}
    </div>
  );
}
