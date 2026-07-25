# DeskDibs frontend

React 19 + Vite + TypeScript (strict) + Tailwind CSS + TanStack Query, talking
to the DeskDibs backend over a typed client generated from the frozen OpenAPI
contract at `../docs/openapi.json`.

This is the **Phase 5 shell**: routing, auth, design system, and skeleton
loaders. The interactive SVG seat map is Phase 6.

## Setup

```bash
npm install
cp .env.example .env   # defaults to http://localhost:8080; edit if your backend runs elsewhere
npm run dev
```

The backend must be running separately (see the repo root `README.md` /
`docs/postman/README.md`) — this app has no mock-server mode outside tests.

## Regenerating the typed API client

Whenever `docs/openapi.json` changes, regenerate `src/api/schema.ts`:

```bash
npm run generate:api
```

This runs `openapi-typescript` against the contract and rewrites the file.
Never hand-edit `src/api/schema.ts` — it's regenerated, not authored, and is
excluded from ESLint/Prettier for that reason.

The runtime client (`src/api/client.ts`) wraps `openapi-fetch` with the
generated types, attaches the bearer token from the session store to every
request, and reacts to a 401 by clearing the session and notifying
`AuthContext` to redirect to `/login`.

## Commands

```bash
npm run dev            # Vite dev server with HMR
npm run build           # tsc -b (typecheck) && vite build
npm run lint             # ESLint, zero warnings allowed
npm run format:check    # Prettier check
npm run format           # Prettier write
npm run test              # Vitest (MSW-mocked, no backend needed)
npm run test:watch      # Vitest in watch mode
npm run generate:api    # regenerate src/api/schema.ts from docs/openapi.json
```

## Structure

```
src/
  api/            typed client (openapi-fetch + generated schema.ts) and error helpers
  auth/           AuthContext/AuthProvider, ProtectedRoute, RequireRole, session storage
  components/     shared presentational components (AppShell, SeatStateBadge, error boundary, ...)
  components/seatmap/  the Phase 5 skeleton loader + placeholder grouped-list view
  hooks/          TanStack Query hooks (useSeatMap, useMyBookings)
  lib/            seat-state design-token registry shared by the badge and (later) the map
  pages/          route-level pages
  test/           Vitest setup, MSW handlers/fixtures, a renderWithProviders test helper
```

## Auth notes

- The access token lives in `sessionStorage`, not `localStorage` — it's
  cleared the instant the tab closes, shrinking the window an XSS payload
  could exfiltrate a live token. A production-grade fix (httpOnly cookie +
  refresh-token flow) is future work; the backend does not yet expose a
  refresh endpoint to build one against. See the comment in
  `src/auth/tokenStorage.ts`.
- Role checks (`RequireRole`) read the role from the already-authenticated
  user object returned by the backend — never from a client-editable token
  claim. The backend independently authorizes every mutation; client-side
  gating here is a UX convenience, not the security boundary.

## Known environment quirks (this machine)

- **Node 22.9.0** is below Vite 8's stated minimum (`^20.19.0 || >=22.12.0`).
  Everything above still builds, tests, and runs correctly — Vite only prints
  a warning — but if you hit a genuinely new failure, check the Node version
  first.
- Vite 8 resolves to a rolldown-based build. On Apple Silicon, npm's optional
  dependency resolution sometimes fails to install
  `@rolldown/binding-darwin-arm64` on a fresh clone (a known npm bug:
  npm/cli#4828). It's pinned as an `optionalDependency` here; if `npm run
build` fails with `Cannot find native binding`, run
  `npm install --no-save @rolldown/binding-darwin-arm64` and retry.
