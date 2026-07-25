import { useEffect, useState, type ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faBars,
  faChair,
  faRightFromBracket,
  faTableList,
  faUsers,
  faXmark,
  type IconDefinition,
} from '@fortawesome/free-solid-svg-icons';
import { useAuth } from '../auth/AuthContext';
import { AppFooter } from './AppFooter';

interface NavItem {
  to: string;
  label: string;
  icon: IconDefinition;
  visible: boolean;
}

type ThemeName = 'cool' | 'office';

const THEME_STORAGE_KEY = 'deskdibs-theme';

const getInitialTheme = (): ThemeName => {
  if (typeof window === 'undefined') return 'cool';
  const saved = window.localStorage.getItem(THEME_STORAGE_KEY);
  return saved === 'office' ? 'office' : 'cool';
};

/** Desktop nav: uppercase Space Grotesk with a yellow underline on the active route. */
const desktopNavClasses = ({ isActive }: { isActive: boolean }) =>
  `flex h-16 items-center border-b-[3px] px-1 text-sm font-semibold uppercase tracking-wide transition-colors ${
    isActive
      ? 'border-bauhaus-yellow text-bauhaus-yellow'
      : 'border-transparent text-paper/75 hover:text-white'
  }`;

const mobileNavClasses = ({ isActive }: { isActive: boolean }) =>
  `flex items-center gap-3 border-2 px-3 py-2 text-sm font-semibold uppercase tracking-wide ${
    isActive
      ? 'border-bauhaus-yellow bg-bauhaus-yellow text-ink'
      : 'border-transparent text-paper/80 hover:border-paper/30'
  }`;

export function AppShell({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [theme, setTheme] = useState<ThemeName>(getInitialTheme);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    window.localStorage.setItem(THEME_STORAGE_KEY, theme);
  }, [theme]);

  const isManagerOrAdmin = user?.role === 'MANAGER' || user?.role === 'ADMIN';

  const navItems: NavItem[] = [
    { to: '/', label: 'Seat map', icon: faChair, visible: true },
    { to: '/my-bookings', label: 'My bookings', icon: faTableList, visible: true },
    { to: '/reservations', label: 'Reservations', icon: faUsers, visible: isManagerOrAdmin },
  ];
  const visibleNav = navItems.filter((item) => item.visible);

  return (
    <div className="flex min-h-svh flex-col bg-paper">
      <header className="bg-ink text-paper">
        <div className="mx-auto flex h-16 max-w-[1500px] items-center justify-between gap-4 px-4">
          <div className="flex items-center gap-3">
            <button
              type="button"
              className="border-2 border-paper/30 p-2 text-paper hover:bg-paper/10 md:hidden"
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
            <span className="flex items-center gap-2 font-mono text-base font-bold uppercase tracking-[0.14em] text-white">
              <span className="flex h-7 w-7 items-center justify-center bg-bauhaus-yellow text-ink">
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
            <div className="hidden items-center gap-2 border-2 border-paper/25 p-1 md:flex">
              <button
                type="button"
                onClick={() => setTheme('office')}
                className={`px-2 py-1 text-[11px] font-semibold uppercase tracking-wide transition-colors ${
                  theme === 'office'
                    ? 'bg-bauhaus-yellow text-ink'
                    : 'text-paper/70 hover:text-paper'
                }`}
                aria-pressed={theme === 'office'}
              >
                Office
              </button>
              <button
                type="button"
                onClick={() => setTheme('cool')}
                className={`px-2 py-1 text-[11px] font-semibold uppercase tracking-wide transition-colors ${
                  theme === 'cool'
                    ? 'bg-bauhaus-yellow text-ink'
                    : 'text-paper/70 hover:text-paper'
                }`}
                aria-pressed={theme === 'cool'}
              >
                Cool
              </button>
            </div>
            {user && (
              <div className="hidden text-right sm:block">
                <p className="text-sm font-semibold text-white">
                  {user.displayName}
                </p>
                <p className="font-mono text-[11px] uppercase tracking-wider text-paper/60">
                  {user.role}
                </p>
              </div>
            )}
            <button
              type="button"
              onClick={logout}
              className="flex items-center gap-2 border-2 border-paper/30 px-3 py-2 text-sm font-semibold uppercase tracking-wide text-paper hover:border-bauhaus-red hover:text-white"
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

      <main className="mx-auto w-full max-w-[1500px] flex-1 px-4 py-6">
        {children}
      </main>

      <AppFooter />
    </div>
  );
}
