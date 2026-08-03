import { beforeEach, describe, expect, it } from 'vitest';
import { DEFAULT_THEME, THEME_STORAGE_KEY, applyTheme, readStoredTheme } from './theme';

/**
 * The house-style switch. Small surface, but one behaviour here is worth pinning: an explicit
 * choice has to survive a change of default. The first version of this only tested for the
 * non-default name, so the day the default moved, everybody who had deliberately chosen the other
 * theme would have been silently switched back — a preference that ignores you.
 */
describe('theme preference', () => {
  beforeEach(() => {
    localStorage.clear();
    delete document.documentElement.dataset.theme;
  });

  it('starts on the office house style when nobody has chosen', () => {
    expect(readStoredTheme()).toBe('office');
    expect(DEFAULT_THEME).toBe('office');
  });

  it.each(['cool', 'office'] as const)('remembers an explicit choice of %s', (theme) => {
    applyTheme(theme);

    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe(theme);
    expect(readStoredTheme()).toBe(theme);
    expect(document.documentElement.dataset.theme).toBe(theme);
  });

  it('keeps a stored non-default choice rather than falling back to the default', () => {
    // The regression this file exists for: `cool` is not the default any more, and must still win.
    localStorage.setItem(THEME_STORAGE_KEY, 'cool');

    expect(readStoredTheme()).toBe('cool');
  });

  it('falls back when the stored value is not a theme we ship', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'midnight');

    expect(readStoredTheme()).toBe(DEFAULT_THEME);
  });
});
