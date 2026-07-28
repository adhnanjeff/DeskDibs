/**
 * Theme selection. `cool` is the neo-brutalist Bauhaus house style, `office`
 * the PreCorr corporate standard. Both are light — this is a house-style
 * switch, not a light/dark switch.
 *
 * The value lives on <html data-theme>, which every token in index.css keys
 * off. Applied at boot (main.tsx) rather than in AppShell so that screens
 * rendered outside the shell — the login page — are themed too.
 */
export type ThemeName = 'cool' | 'office';

export const THEME_STORAGE_KEY = 'deskdibs-theme';

export const DEFAULT_THEME: ThemeName = 'cool';

export function readStoredTheme(): ThemeName {
  if (typeof window === 'undefined') return DEFAULT_THEME;
  try {
    return window.localStorage.getItem(THEME_STORAGE_KEY) === 'office'
      ? 'office'
      : DEFAULT_THEME;
  } catch {
    // Private mode / storage disabled — fall back rather than break boot.
    return DEFAULT_THEME;
  }
}

export function applyTheme(theme: ThemeName): void {
  if (typeof document === 'undefined') return;
  document.documentElement.dataset.theme = theme;
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // Persisting is best-effort; the in-page theme still applies.
  }
}
