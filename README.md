# DeskDibs

A hot-desk booking app for a hybrid office. The floor map is the interface: you look at the
plan, pick a desk, and it is yours for the day.

Built around one rule that the database enforces rather than the application:

> **A seat has at most one ACTIVE booking per date. A person holds at most one ACTIVE booking
> per date.**

That is two partial unique indexes in PostgreSQL, sitting underneath every code path:

```sql
CREATE UNIQUE INDEX uq_seat_active_per_date
  ON booking (seat_id, booking_date) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_user_active_per_date
  ON booking (user_id, booking_date) WHERE status = 'ACTIVE';
```

No service-layer lock, no `SELECT … FOR UPDATE` pre-check, no Redis, no queue. Concurrent claims
race at the constraint; the loser gets a clean `409` naming who won. The headline test fires
**150 simultaneous claims at one seat and asserts exactly one winner, 149 clean 409s, and zero
500s** — see `ConcurrentSeatClaimTest`.

> **New here, or explaining the app to somebody?** Read
> [**How DeskDibs works**](docs/how-deskdibs-works.md) — every rule, role, deadline and edge case
> in plain English, no code. This README is the developer's entry point; that one is the manual.

---

## Contents

- [What it does](#what-it-does)
- [Stack](#stack)
- [Running it locally](#running-it-locally)
- [Repository layout](#repository-layout)
- [Everyday commands](#everyday-commands)
- [Testing](#testing)
- [API](#api)
- [Office rules are configuration](#office-rules-are-configuration)
- [Security posture](#security-posture)
- [Theming](#theming)
- [Contributing](#contributing)
- [Known gaps](#known-gaps)

---

## What it does

**Employees** claim one desk per day, up to 14 days ahead, from a top-down floor map. A date
strip shows how full each day already is, so you can pick a quiet one before committing to the
commute. Check in on the day; if you do not by **11:00**, the desk goes back into the pool.

**Managers** hold blocks of desks so a team can sit together. Holds are *soft* — they stop being
enforced at **10:00** with no job and no state change — and reserving over existing bookings
reports partial success naming who already holds what, rather than force-cancelling.

**Admins** deactivate accounts and withdraw desks from service. Both actions release other
people's bookings, so both report exactly what they cost. There is also a live **API activity**
view: a flow diagram of requests hitting the server as they happen, useful for watching a seat
race play out.

Everything is live. Seat changes broadcast over STOMP to everyone looking at that date; if the
socket drops, the map falls back to a 15-second poll — slower, never wrong.

---

## Stack

| | |
|---|---|
| Java | **21 LTS** (Temurin 21.0.8) — *not* the JDK 24 default, see [Contributing](#contributing) |
| Backend | Spring Boot 3.5.3 |
| Database | PostgreSQL 18, plain — not Supabase |
| Migrations | Flyway (`ddl-auto: validate`, never `update`) |
| Frontend | React 19 + Vite + TypeScript (strict) + Tailwind v4 + Motion |
| 3D floor view | three.js via React Three Fiber |
| Icons | FontAwesome Free, imported individually |
| Realtime | STOMP over WebSocket |
| Tests | JUnit 5 + Testcontainers, Vitest, Playwright, k6 |

Node is pinned by `.nvmrc` (**22.23.1**) and enforced by `engine-strict`.

---

## Running it locally

### 1. Prerequisites

- JDK 21 (Temurin recommended)
- Node 22.12+ (`nvm use` reads `.nvmrc`)
- PostgreSQL 18, either local or via the bundled Compose file
- Docker — required for the backend test suite (Testcontainers)

### 2. Database

Either point at a local Postgres on 5432, or use the disposable one:

```bash
cp .env.docker.example .env.docker   # set the credentials
docker compose up -d
```

That runs Postgres on **5433** — deliberately, so it can never collide with a Homebrew Postgres
on 5432 — plus pgAdmin on <http://localhost:5050>.

### 3. Configuration

```bash
cp .env.example .env
```

Then set, at minimum:

- `DB_PASSWORD` — no default; the app fails loudly rather than guess
- `DEV_SEED_PASSWORD` — **blank means no seeding.** Set it to get the three dev accounts
  (`employee@`, `manager@`, `admin@deskdibs.local`). The password is never written to the log.
- `DESKDIBS_JWT_SECRET` — optional locally. Blank generates a random key per process, so logins
  work but every token dies at restart.

`.env` is gitignored. `.env.example` documents every variable and is committed with placeholders.

### 4. Run

```bash
cd backend  && ./mvnw spring-boot:run     # http://localhost:8080
cd frontend && npm install && npm run dev # http://localhost:5173
```

Flyway migrates on boot. Sign in with one of the seeded accounts.

---

## Repository layout

```
backend/     Spring Boot API, packaged by feature (booking/, seat/, team/, admin/, auth/,
             realtime/, telemetry/) — each with its own controller, service, repository, DTOs
frontend/    React app. lib/ holds the pure logic (floor geometry, seat state, week slicing);
             components/ stays presentational; hooks/ owns server state via TanStack Query
docs/        openapi.json (generated, not hand-written), Postman collection, security advisories
load/        k6 script for the 150-VU concurrency profile
PLAN.md      The approved design record: data model, booking rules, 14 enumerated edge cases
PRODUCT.md   Who this is for and what it must never do
DESIGN.md    Visual language
```

---

## Everyday commands

```bash
./mvnw spring-boot:run                  # backend, hot restart via devtools
./mvnw verify                           # full backend suite (needs Docker)
npm run dev                             # frontend, Vite HMR
npm run test                            # Vitest
npm run lint                            # ESLint, zero warnings tolerated
npm run build                           # tsc -b + production bundle
npm run generate:api                    # regenerate src/api/schema.ts from docs/openapi.json
psql -h localhost -U deskdibs -d deskdibs
```

---

## Testing

Backend integration tests run against **real PostgreSQL via Testcontainers**, never H2 — H2 does
not reproduce partial unique indexes, so an H2 suite would go green while proving nothing.

- **`ConcurrentSeatClaimTest`** — the headline: 150 threads, one seat, one winner. No
  `Thread.sleep` anywhere; the threads genuinely race.
- **`SeatMapControllerTest`** — asserts the map costs a bounded number of queries whether zero or
  twenty seats are booked, by counting the queries Hibernate executes. Note this counts JPQL
  executions, not JDBC statements, so it would not catch an N+1 introduced by a lazy association
  loading by primary key; the seat map builds its result from explicit queries, so the guard holds
  for this endpoint, but do not assume the technique generalises without checking.
- **`StompAdminTopicAuthorizationTest`** — an employee opening a socket cannot subscribe to the
  admin telemetry topic. Spring's simple broker authorizes nothing on its own, so this is the
  only thing standing between a signed-in employee and the whole office's traffic.
- **`frontend/e2e/seat-race.spec.ts`** — two browsers, one seat, via Playwright. Needs a running
  backend.
- **`load/seat-claim.js`** — k6 at 150 virtual users.

```bash
cd backend  && ./mvnw verify
cd frontend && npm run test
cd frontend && npm run test:e2e     # backend must be running
```

---

## API

Generated contract at `docs/openapi.json`; browse it at
<http://localhost:8080/swagger-ui.html> while the app runs. Both that and `/v3/api-docs` are
unauthenticated on purpose, so CI and the frontend can read them without a token.

| | |
|---|---|
| `POST /api/auth/login` | Local dev sign-in |
| `GET /api/auth/me` | The caller |
| `GET /api/seatmap` | Floor → zone → table → seat, with each seat's state for a date |
| `GET /api/seatmap/horizon` | How full each bookable day is, for the date strip |
| `POST /api/bookings` | Claim a seat (idempotent via `Idempotency-Key`) |
| `POST /api/bookings/move` | Move to another desk in one transaction |
| `DELETE /api/bookings/{id}` | Cancel |
| `POST /api/bookings/{id}/check-in` | Check in to today's booking |
| `GET /api/bookings/me` | Your bookings in a date range |
| `GET`, `POST /api/reservations` | Team holds |
| `DELETE /api/reservations/{id}` | Release a hold early |
| `GET /api/reservations/teams` | Teams you may hold for |
| `GET /api/admin/users` | Everyone (admin) |
| `PATCH /api/admin/users/{id}/active` | Account lifecycle (admin) |
| `PATCH /api/admin/seats/{id}/status` | Withdraw or restore a desk (admin) |

Realtime topics: `/topic/seatmap/{date}` for everyone; `/topic/admin/telemetry` for admins only,
enforced server-side on the SUBSCRIBE frame.

Regenerating the contract after a controller change:

```bash
curl -s http://localhost:8080/v3/api-docs | python3 -m json.tool > docs/openapi.json
cd frontend && npm run generate:api
```

---

## Office rules are configuration

Never constants buried in a service. All of these are environment variables, validated at
startup — a bad value stops the application rather than quietly changing what "today" means.

| Variable | Default | Meaning |
|---|---|---|
| `OFFICE_TIMEZONE` | `Asia/Kolkata` | The only definition of "today". The client clock is never trusted. |
| `BOOKING_HORIZON_DAYS` | `14` | How far ahead a desk can be claimed |
| `TEAM_BLOCK_RELEASE_TIME` | `10:00` | Team holds stop being enforced |
| `NO_SHOW_RELEASE_TIME` | `11:00` | Un-checked-in bookings return to the pool |
| `NO_SHOW_RELEASE_DAYS` | `MON-FRI` | Which days that release runs |
| `SAME_DAY_CUTOFF_TIME` | `11:00` | After this, the date strip stops offering today |
| `OFFICE_WORKING_DAYS` | `MONDAY…FRIDAY` | Enforced by the booking rules, not merely greyed out |

---

## Security posture

- **Deny by default.** The filter chain ends in `anyRequest().authenticated()`, so an endpoint
  added tomorrow is protected the moment it exists. Making something public is a reviewable diff.
- **Roles come from the database**, never from a claim on the incoming token. `AuthenticatedUser`
  is loaded from `app_user`; the JWT is demoted to the credentials slot as evidence of *who
  called* and nothing more.
- **Object-level authorization on every mutation** — does *this* caller own *this* booking, or
  manage the team of whoever does. Enforced server-side.
- **No secrets in source, logs, or git.** `DB_PASSWORD`, `DESKDIBS_JWT_SECRET` and
  `DEV_SEED_PASSWORD` have no defaults and fail closed.
- **CORS is exact-origin**, never a wildcard; the same origins gate the WebSocket endpoint.
- **Actuator exposes only** `health` and `info`.
- Parameterised queries throughout; per-user rate limiting on booking mutations.

Accepted dependency advisories, with reasoning, live in
[`docs/security-advisories.md`](docs/security-advisories.md). Treat anything not listed there as
unreviewed.

---

## Theming

Two themes, switched from the header, both light:

- **Cool** — the default. Neo-brutalist Bauhaus: warm paper, near-black ink, 2px borders, hard
  offset shadows, tracked uppercase.
- **Office** — the corporate house style. Blue-grey canvas, white cards, hairline borders, system
  font, dense rows.

Colour utilities resolve through CSS custom properties, so re-skinning is a token change rather
than a component change. Anything structural — radii, border widths, shadows, label casing — goes
through a `--dd-*` variable consumed by the `.ui-*` classes in `frontend/src/index.css`.

Seat states follow the convention people already know from cinema and airline seat pickers:
available is near-white and outlined, booked is **grey**, out of service is a darker grey. Colour
is never the only signal — every state pairs a distinct fill with a distinct icon and a text
label, and every pair is checked at WCAG AA.

---

## Contributing

The standards every change is held to:

1. **Branch before writing code.** `git checkout -b feat/<name>`.
2. **Constructor injection, DTOs at every boundary, `@Transactional` on services only, no
   business logic in controllers.**
3. **TypeScript strict, no `any`.** TanStack Query owns server state; never `useEffect` +
   `setState` to fetch.
4. **Never "protect" the uniqueness invariant in application code.** If you find yourself adding
   a lock, the design is being misread.
5. Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `chore:`).

**Use Java 21.** The build misbehaves confusingly on JDK 24: Lombok's annotation processor
no-ops and you get dozens of `cannot find symbol` errors in files you never touched.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

**Use Node 22.12+.** Below that, `npm run test` dies with `ERR_REQUIRE_ESM` out of jsdom, which
reads as a broken test suite rather than a wrong Node version. `engine-strict` turns that into an
honest error at install time.

---

## Known gaps

Stated plainly so nobody has to discover them:

- **The real floor plan has not arrived.** The seeded layout is interim, matching a reference
  photo. It currently seeds **102 desks** — `PLAN.md` and `PRODUCT.md` still describe a 110-seat
  interim layout, and have not been reconciled.
- **Microsoft Entra ID is wired but unused.** `AUTH_PROVIDER=entra` is implemented and refuses to
  start without a tenant and client id; local login is what runs today.
- The Teams tab seam is kept cheap, but the tab manifest is not built.
- Half-day AM/PM slots were considered and rejected for now.
- No pagination on list endpoints. Deliberate at this scale — the largest list is one row per
  employee — but it is the first thing to revisit as headcount grows.
