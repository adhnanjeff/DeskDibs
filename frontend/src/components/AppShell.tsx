import { useEffect, useState, type ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faBars,
  faBuilding,
  faChair,
  faShapes,
  faRightFromBracket,
  faSliders,
  faTableList,
  faWaveSquare,
  faUsers,
  faXmark,
  type IconDefinition,
} from '@fortawesome/free-solid-svg-icons';
import { useAuth } from '../auth/AuthContext';
import { applyTheme, readStoredTheme, type ThemeName } from '../lib/theme';
import { AppFooter } from './AppFooter';

interface NavItem {
  to: string;
  label: string;
  icon: IconDefinition;
  visible: boolean;
}

const THEMES: ReadonlyArray<{ id: ThemeName; label: string; icon: IconDefinition }> = [
  { id: 'cool', label: 'Cool', icon: faShapes },
  { id: 'office', label: 'Office', icon: faBuilding },
];

/** Desktop nav: an underline on the active route, tracked-uppercase only in cool. */
const desktopNavClasses = ({ isActive }: { isActive: boolean }) =>
  `ui-label flex h-16 items-center border-b-[3px] px-1 text-sm font-semibold transition-colors ${
    isActive
      ? 'border-selected text-selected'
      : 'border-transparent text-paper/75 hover:text-white'
  }`;

const mobileNavClasses = ({ isActive }: { isActive: boolean }) =>
  `ui-label ui-control flex items-center gap-3 ui-edge px-3 py-2 text-sm font-semibold ${
    isActive
      ? 'border-selected bg-selected text-ink'
      : 'border-transparent text-paper/80 hover:border-paper/30'
  }`;

export function AppShell({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [theme, setTheme] = useState<ThemeName>(readStoredTheme);

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  const isManagerOrAdmin = user?.role === 'MANAGER' || user?.role === 'ADMIN';
  const isAdmin = user?.role === 'ADMIN';

  const navItems: NavItem[] = [
    { to: '/', label: 'Seat map', icon: faChair, visible: true },
    { to: '/my-bookings', label: 'My bookings', icon: faTableList, visible: true },
    { to: '/reservations', label: 'Reservations', icon: faUsers, visible: isManagerOrAdmin },
    { to: '/admin', label: 'Admin', icon: faSliders, visible: isAdmin },
    { to: '/api-view', label: 'API', icon: faWaveSquare, visible: isAdmin },
  ];
  const visibleNav = navItems.filter((item) => item.visible);

  return (
    <div className="flex min-h-svh flex-col bg-canvas">
      <header className="bg-ink text-paper">
        <div className="mx-auto flex h-16 max-w-[var(--dd-shell-max)] items-center justify-between gap-4 px-4">
          <div className="flex items-center gap-3">
            <button
              type="button"
              className="ui-control-icon ui-edge flex items-center justify-center border-paper/30 p-2 text-paper hover:bg-paper/10 md:hidden"
              aria-label={mobileNavOpen ? 'Close menu' : 'Open menu'}
              aria-expanded={mobileNavOpen}
              onClick={() => setMobileNavOpen((open) => !open)}
            >
              <FontAwesomeIcon
                icon={mobileNavOpen ? faXmark : faBars}
                className="h-5 w-5"
                aria-hidden="true"
              />
            </button>
            <span className="ui-wordmark flex items-center gap-2 text-base font-bold text-white">
              <span className="flex h-7 w-7 items-center justify-center bg-selected text-ink">
                <FontAwesomeIcon
                  icon={faChair}
                  className="h-3.5 w-3.5"
                  aria-hidden="true"
                />
              </span>
              DeskDibs
            </span>
          </div>

          <nav className="hidden h-16 items-center gap-6 md:flex" aria-label="Main">
            {visibleNav.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={desktopNavClasses}
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="flex items-center gap-3">
            <div
              className="hidden items-center gap-1 ui-edge border-paper/25 p-1 md:flex"
              role="group"
              aria-label="Interface theme"
            >
              {THEMES.map(({ id, label, icon }) => (
                <button
                  key={id}
                  type="button"
                  onClick={() => setTheme(id)}
                  className={`ui-label ui-edge flex items-center gap-1.5 px-2 py-1 text-[11px] font-semibold transition-colors ${
                    theme === id
                      ? 'bg-selected text-ink'
                      : 'border-transparent text-paper/70 hover:text-paper'
                  }`}
                  aria-pressed={theme === id}
                >
                  <FontAwesomeIcon icon={icon} className="h-3 w-3" aria-hidden="true" />
                  {label}
                </button>
              ))}
            </div>
            {user && (
              <div className="hidden text-right sm:block">
                <p className="text-sm font-semibold text-white">
                  {user.displayName}
                </p>
                <p className="font-mono text-[11px] ui-label text-paper/60">
                  {user.role}
                </p>
              </div>
            )}
            <button
              type="button"
              onClick={logout}
              className="ui-control flex items-center gap-2 ui-edge border-paper/30 px-3 py-2 text-sm font-semibold ui-label text-paper hover:border-danger hover:text-white"
              aria-label="Log out"
            >
              <FontAwesomeIcon
                icon={faRightFromBracket}
                className="h-4 w-4"
                aria-hidden="true"
              />
              <span className="hidden sm:inline">Log out</span>
            </button>
          </div>
        </div>

        {mobileNavOpen && (
          <nav
            className="flex flex-col gap-2 border-t-2 border-paper/20 px-4 py-3 md:hidden"
            aria-label="Main"
          >
            {visibleNav.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                onClick={() => setMobileNavOpen(false)}
                className={mobileNavClasses}
              >
                <FontAwesomeIcon
                  icon={item.icon}
                  className="h-4 w-4"
                  aria-hidden="true"
                />
                {item.label}
              </NavLink>
            ))}
          </nav>
        )}
      </header>

      <main className="mx-auto w-full max-w-[var(--dd-shell-max)] flex-1 px-4 py-6">
        {children}
      </main>

      <AppFooter />
    </div>
  );
}
