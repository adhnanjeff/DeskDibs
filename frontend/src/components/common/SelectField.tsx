import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCheck, faChevronDown } from '@fortawesome/free-solid-svg-icons';

/**
 * A select whose drop-down belongs to the page rather than to the operating system.
 *
 * <p>Same reason as {@link DateField}: a native {@code <select>}'s menu is browser chrome. It can
 * be given a themed <em>box</em> — that is what `appearance-none` was doing — but the list that
 * opens out of it is drawn by the OS, in the OS's font, at the OS's row height, and no stylesheet
 * reaches it. On the office theme that meant a carefully specified form sprouting a macOS menu
 * halfway through.
 *
 * <p>Rebuilt as a listbox, which also lets the rows clear the 44px touch floor and carry a tick on
 * the chosen one. The trade is that every affordance the browser gave away for free — roving
 * focus, type-ahead, escape-to-close — now has to be written down; they are, below.
 */
export interface SelectOption {
  value: string;
  label: string;
}

interface SelectFieldProps {
  label: string;
  value: string;
  options: SelectOption[];
  disabled?: boolean;
  /** Shown in the closed field when nothing is selected yet. */
  placeholder?: string;
  onChange: (value: string) => void;
}

export function SelectField({
  label,
  value,
  options,
  disabled = false,
  placeholder = 'Select…',
  onChange,
}: SelectFieldProps) {
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const labelId = useId();
  const listId = useId();

  const selectedIndex = options.findIndex((option) => option.value === value);
  const selected = selectedIndex >= 0 ? options[selectedIndex] : null;

  const openList = useCallback(() => {
    setActiveIndex(selectedIndex >= 0 ? selectedIndex : 0);
    setOpen(true);
  }, [selectedIndex]);

  const closeList = useCallback((returnFocus: boolean) => {
    setOpen(false);
    if (returnFocus) triggerRef.current?.focus();
  }, []);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [open]);

  // Focus follows the active row, so the list scrolls to it and a screen reader reads it out.
  useEffect(() => {
    if (!open) return;
    listRef.current?.querySelector<HTMLLIElement>('[data-active="true"]')?.focus();
  }, [open, activeIndex]);

  const commit = (index: number) => {
    const option = options[index];
    if (!option) return;
    onChange(option.value);
    closeList(true);
  };

  const onListKeyDown = (event: React.KeyboardEvent) => {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        setActiveIndex((i) => Math.min(options.length - 1, i + 1));
        break;
      case 'ArrowUp':
        event.preventDefault();
        setActiveIndex((i) => Math.max(0, i - 1));
        break;
      case 'Home':
        event.preventDefault();
        setActiveIndex(0);
        break;
      case 'End':
        event.preventDefault();
        setActiveIndex(options.length - 1);
        break;
      case 'Enter':
      case ' ':
        event.preventDefault();
        commit(activeIndex);
        break;
      case 'Escape':
        event.preventDefault();
        event.stopPropagation();
        closeList(true);
        break;
      case 'Tab':
        setOpen(false);
        break;
      default:
        // Type-ahead: the one native behaviour people miss most, because it is how anybody with a
        // long team list actually navigates one.
        if (event.key.length === 1) {
          const from = options.findIndex((option, i) =>
            i > activeIndex && option.label.toLowerCase().startsWith(event.key.toLowerCase()));
          const wrapped = from >= 0
            ? from
            : options.findIndex((option) => option.label.toLowerCase().startsWith(event.key.toLowerCase()));
          if (wrapped >= 0) {
            event.preventDefault();
            setActiveIndex(wrapped);
          }
        }
    }
  };

  const onTriggerKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      openList();
    }
  };

  return (
    <div ref={rootRef} className="relative">
      <span id={labelId} className="block text-sm font-bold ui-label text-ink">
        {label}
      </span>
      <button
        ref={triggerRef}
        type="button"
        disabled={disabled}
        onClick={() => (open ? closeList(false) : openList())}
        onKeyDown={onTriggerKeyDown}
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-labelledby={labelId}
        className="ui-control mt-1.5 flex w-full items-center justify-between gap-2 ui-edge border-line bg-white px-3.5 py-3 text-lg font-semibold text-ink disabled:cursor-not-allowed disabled:opacity-50"
      >
        <span className={selected ? '' : 'text-ink/40'}>{selected?.label ?? placeholder}</span>
        <FontAwesomeIcon icon={faChevronDown} className="h-4 w-4 shrink-0" aria-hidden="true" />
      </button>

      {open && (
        <ul
          ref={listRef}
          id={listId}
          role="listbox"
          aria-labelledby={labelId}
          onKeyDown={onListKeyDown}
          className="absolute left-0 top-full z-30 mt-1 max-h-72 w-full overflow-y-auto ui-edge border-line bg-paper p-1 shadow-[var(--dd-shadow)]"
        >
          {options.map((option, index) => {
            const isSelected = option.value === value;
            return (
              <li
                key={option.value}
                role="option"
                aria-selected={isSelected}
                data-active={index === activeIndex ? 'true' : undefined}
                tabIndex={index === activeIndex ? 0 : -1}
                onClick={() => commit(index)}
                onMouseEnter={() => setActiveIndex(index)}
                className={`ui-control flex cursor-pointer items-center justify-between gap-2 px-3 py-2.5 text-base font-semibold text-ink ${
                  isSelected ? 'bg-selected' : 'hover:bg-paper-dim'
                }`}
              >
                {option.label}
                {isSelected && (
                  <FontAwesomeIcon icon={faCheck} className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
