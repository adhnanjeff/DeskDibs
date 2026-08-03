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

/**
 * What somebody sees before they have ever touched the toggle. `office`, because this is
 * deployed inside an office whose house style it matches — `cool` is the opt-in, not the norm.
 */
export const DEFAULT_THEME: ThemeName = 'office';

export function readStoredTheme(): ThemeName {
  if (typeof window === 'undefined') return DEFAULT_THEME;
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    // Both names are matched explicitly. Testing only for the non-default one — which is what
    // this did while `cool` was the default — silently overrides anybody who deliberately chose
    // the other theme the moment the default changes, and a preference that ignores you is worse
    // than no preference at all.
    return stored === 'office' || stored === 'cool' ? stored : DEFAULT_THEME;
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
