# DeskDibs — Office Seat Selection System

**Status:** Design approved pending review · **Date:** 2026-07-23
**Problem:** The new office runs hybrid (2 days WFO). Nobody has an assigned desk, so people arrive and scramble for seats, and some days there aren't enough. We need a live top-view floor map where anyone can claim a seat for a day, managers can hold blocks for their team, and nobody can ever end up double-booked.

---

## 1. Decisions locked

| Area | Decision | Why |
|---|---|---|
| Backend | Spring Boot 3.5.3 on **Java 21 LTS** + PostgreSQL 18 | Transactional integrity makes the double-booking problem *provably* correct, not just "usually fine". Java 21 (already installed) rather than the JDK 24 default — it removes the Lombok `edge-SNAPSHOT`, `mockito-subclass`, and `--add-opens` workarounds carried by the academy-management project |
| Database | **Plain PostgreSQL, not Supabase** | Supabase *is* Postgres + a platform (Auth, Realtime, PostgREST, RLS) — every piece of which our Spring backend already provides. Worse, its frontend-talks-to-DB model would give us two authorization systems that must never disagree. Schema is vanilla SQL, so it ports to Azure Database for PostgreSQL (the natural fit alongside Entra ID), RDS, or Neon unchanged |
| Frontend | React 19 + Vite + TypeScript + Tailwind + Motion | Best fit for an animation-heavy, gesture-driven seat map |
| Identity | **Dual-provider**: local dev login ⇄ Microsoft Entra ID (OIDC) behind one config flag | Build and demo today; flip to company SSO the day IT issues the app registration — zero rewrite |
| Teams | Managed in-app, optional Entra group import later | Org-chart groups rarely match who actually needs to sit together |
| Distribution | Responsive web app, kept Teams-tab-ready (iframe-safe + SDK seam) | Adding the Teams tab manifest later stays cheap; works on phones meanwhile |
| Booking unit | Whole day, one seat per person, bookable up to 14 days ahead | Fits the 2-day WFO rhythm; removes the 9am scramble |
| Team blocks | Soft reservation, auto-releases at 10:00 | Reserved-but-empty seats would waste scarce inventory |
| No-shows | Check-in by 11:00 or the seat auto-releases | With only 110 seats, "book just in case" is the failure mode to design against |

### Explicitly rejected

- **Redis / Kafka / distributed locks for seat claiming.** 150 concurrent requests is a rounding error for Postgres. A partial unique index does the job with zero extra infrastructure. Adding a lock service here would be architecture for its own sake.
- **Half-day AM/PM slots.** Doubles every edge case in the seat map for a use case the office may not even have. Revisit only if asked.
- **Optimistic-locking version columns on seats.** The booking table's unique index is the authoritative guard; a version column on `seat` would be a second, weaker copy of the same rule.

---

## 2. The core invariant

> **A seat has at most one ACTIVE booking on any given date. A person holds at most one ACTIVE booking on any given date.**

Both are enforced by the **database**, not by application code:

```sql
CREATE UNIQUE INDEX uq_seat_active_per_date
  ON booking (seat_id, booking_date) WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uq_user_active_per_date
  ON booking (user_id, booking_date) WHERE status = 'ACTIVE';
```

This is the single most important line in the system. No application bug, no lost race, no retry storm, and no future refactor can violate it, because the constraint lives below all of them.

### What happens when two people click the same seat

| | Alice | Bob |
|---|---|---|
| T0 | sees `R3-A2` free | sees `R3-A2` free |
| T1 | `POST /api/bookings` | `POST /api/bookings` |
| T2 | `INSERT` → Postgres serializes both on the unique index | `INSERT` blocks |
| T3 | **commits** | unique violation `23505` |
| T4 | `201 Created` | `409 SEAT_ALREADY_BOOKED { takenBy: "Alice M." }` |
| T5 | — | seat shakes, toast: *"Alice grabbed that one a second ago"*, map live-updates |

In practice Bob usually never gets that far: the WebSocket broadcast from T3 repaints his map before he finishes clicking.

**Idempotency.** Every claim carries a client-generated `Idempotency-Key` header, stored on the booking row under its own unique index. A double-click, a retried request, or a flaky-network resend returns the *original* result instead of a confusing 409 against yourself.

---

## 3. Data model

Layout is **data, not code** — when the real floor plan arrives we change seed rows or drag tables in an admin editor, never React components.

```
floor ──< zone ──< desk_table ──< seat ──< booking >── app_user >── team_member >── team
                                    └──< seat_reservation >────────────────────────┘
```

| Table | Key columns |
|---|---|
| `app_user` | `external_id` (Entra `oid`), `email`, `display_name`, `avatar_url`, `role` (EMPLOYEE/MANAGER/ADMIN), `active` |
| `team` | `name`, `manager_user_id` |
| `team_member` | `(team_id, user_id)` — many-to-many; people can sit with more than one group |
| `floor` / `zone` | `zone.name` = "Left Wing" / "Right Wing"; multi-floor costs nothing now and saves a migration later |
| `desk_table` | `label`, `capacity`, `pos_x`, `pos_y`, `rotation` |
| `seat` | `label`, `side` (A/B), `seat_index`, `accessible`, `status` (ACTIVE/DISABLED/BROKEN) |
| `booking` | `seat_id`, `user_id`, `booking_date`, `status` (ACTIVE/CANCELLED/RELEASED_NO_SHOW), `checked_in_at`, `idempotency_key` |
| `seat_reservation` | `seat_id`, `team_id`, `start_date`, `end_date`, `release_at_time` (default 10:00), `created_by` |

Seats derive their screen position from their table's `pos_x/pos_y/rotation` plus `side` + `seat_index`. Moving a table moves its seats for free.

### Interim layout (110 seats)

Matching the reference photo: long tables, a glass divider down the length, three seats per side.

| Zone | Tables | Capacity | Seats |
|---|---|---|---|
| Right Wing | `R1`–`R10` | 6 each | 60 |
| Left Wing | `L1`–`L7` | 6 each | 42 |
| Left Wing | `L8` | 8 | 8 |
| | | **Total** | **110** ✓ |

Labels read as `R3-A2` = Right table 3, side A, position 2.

---

## 4. Booking rules

**Claiming a seat** (one transaction):
1. Validate date is within `[today, today + 14]`.
2. Reject if seat is `DISABLED`/`BROKEN`.
3. If a team reservation covers this seat/date **and** now is before its release time **and** the user isn't in that team → `403 SEAT_RESERVED_FOR_TEAM`.
4. `INSERT` the booking. The database settles any race.

**Team reservations** are soft. Past `release_at_time` (10:00) the claim check simply stops enforcing them — no job, no state change, nothing to go wrong. Managers reserving seats that are *already booked* get a partial-success report (*"4 of 6 reserved; R2-A1 and R2-A3 are taken by Priya and Sam"*). The system never silently cancels someone's booking to satisfy a manager.

**No-show release** runs at 11:00 each working day: today's `ACTIVE` bookings with no `checked_in_at` flip to `RELEASED_NO_SHOW` and broadcast as newly available.

All dates are `DATE` in a configured office timezone. **The client clock is never trusted** for cutoffs or "today".

---

## 5. Edge cases

| # | Case | Handling |
|---|---|---|
| 1 | Two users, same seat | DB unique index → one wins, other gets 409 naming the winner |
| 2 | Double-click / network retry | `Idempotency-Key` returns the original result |
| 3 | Second seat on a day you already booked | 409 + *"You have R2-A3 that day. Move here instead?"* (atomic cancel+book) |
| 4 | Past date, or beyond 14 days | 400 with the allowed range |
| 5 | Disabled or broken seat | 409, seat rendered non-interactive |
| 6 | Outsider claims a team-reserved seat | 403 naming the team and the release time |
| 7 | Manager reserves already-booked seats | Partial success report; never force-cancels |
| 8 | Cancelling someone else's booking | 403 — object-level check on every mutation |
| 9 | WebSocket drops | Auto-reconnect, then refetch the full day snapshot (never replay missed events) |
| 10 | Tab left open overnight | Date-rollover detection forces a refetch |
| 11 | Concurrent cancel + claim on one seat | Cancel removes the row from the partial index; DB orders the two |
| 12 | Employee leaves / deactivated | Future bookings released, SSO login refused |
| 13 | Floor plan changes with live bookings | Seats soft-deleted; affected bookings flagged and users notified |
| 14 | Scripted request-thrashing | Per-user token bucket, 30 booking ops/min |

---

## 6. Real-time & load

Clients subscribe to `/topic/seatmap/{date}` over STOMP/WebSocket. Every create, cancel, and auto-release broadcasts `SeatStatusChanged { seatId, status, occupant }`. If the socket is unavailable the UI degrades to a 15-second poll — slower, never wrong.

**On the 150-concurrent requirement:** this is comfortably inside a single Spring Boot instance's default envelope (Tomcat 200 threads, Hikari pool of 20, booking transactions ~2ms). No horizontal scaling, no queue, no cache tier needed. It gets a proving test rather than an architecture:

> **Headline test:** 150 threads claim the *same* seat simultaneously → assert exactly **1** ACTIVE booking, 149 clean 409s, zero 500s.

---

## 7. Frontend

**Seat map** is SVG (scales cleanly, precise hover targets, pan/zoom on mobile) rather than positioned divs.

**States** are distinguished by shape *and* pattern, not colour alone: Available · Yours · Occupied · Team-reserved · Checked-in · Disabled · Pending.

**Hover** reveals occupant name, avatar, team, and check-in status — and the same card appears on keyboard focus, so it works without a mouse.

**Motion**, kept purposeful:
- Skeleton loader shimmers the *actual table outlines*, so the layout doesn't jump when data lands
- Tables stagger in on load
- Optimistic claim: seat pulses "pending" instantly, ripples green on success, **shakes red** on a lost race
- Someone else's claim fades in live

**Date strip** shows the next 14 days with your booked days marked and a fill-percentage bar per day, so people can see which day is quiet before committing.

**Accessibility:** full keyboard navigation of the map, ARIA live region announcing seat changes, visible focus rings, `prefers-reduced-motion` respected throughout.

---

## 8. Security floor

- All queries parameterised via JPA — no string-built SQL, ever
- Object-level authorization on every mutation (*does this user own this booking?*), server-side
- JWT validated server-side; **role claims from the client are never trusted**
- Entra client secret via environment variable only. `.env.example` committed, `.env` gitignored. No secret ever reaches source or logs
- CORS pinned to known origins; CSP `frame-ancestors` opened to Teams domains only when the Teams tab is enabled
- Fail closed on auth

---

## 9. Testing

| Layer | Approach |
|---|---|
| Domain | Unit tests on booking rules |
| Integration | Testcontainers Postgres — real constraints, real races |
| **Concurrency** | 150 threads → 1 winner (§6) |
| Load | k6, 150 virtual users |
| Frontend | Vitest + React Testing Library |
| E2E | Playwright — **two browser contexts clicking one seat**, asserting the loser sees the shake and the winner's name |

---

## 10. Build phases

Each phase is a self-contained subagent task with its own tests.

| # | Phase | Depends on | Model |
|---|---|---|---|
| 0 | Repo scaffold, `git init`, docker-compose Postgres, CI | — | Sonnet |
| 1 | Schema, Flyway migrations, 110-seat layout seed | 0 | **Opus** |
| 2 | Booking engine: claim/cancel, idempotency, concurrency tests | 1 | **Opus** |
| 3 | Dual-provider auth + object-level authorization | 1 | **Opus** |
| 4 | REST API + OpenAPI contract + WebSocket broadcast | 2, 3 | Sonnet |
| 5 | Frontend shell, design system, skeleton loaders | 4 (contract) | Sonnet |
| 6 | SVG seat map, interactions, animations | 5 | Sonnet |
| 7 | Manager team-reservation UI + admin | 6 | Sonnet |
| 8 | Scheduled no-show release job | 2 | Sonnet |
| 9 | Load test, E2E race test, hardening pass | all | **Opus** |

Opus takes the phases where a mistake is invisible until production — invariants, concurrency, and authorization. Sonnet takes the phases where the spec is concrete and the feedback loop is fast. Phases 5–6 can run in parallel with 4 against the frozen OpenAPI contract.

---

## 11. Build, dependencies & workflow

**Toolchain:** Java 21 LTS (Temurin 21.0.8) · Maven 3.9.11 · Node 22.9 · PostgreSQL 18.1

**Backend dependencies** — carried over from the academy-management project except where noted:

`spring-boot-starter-web` · `-data-jpa` · `-validation` · `-security` · `-actuator` · `-cache` + `caffeine` · `-websocket` *(new — STOMP live updates)* · `-oauth2-resource-server` *(new — validates Entra tokens)* · `-oauth2-client` *(new — Entra OIDC login)* · `postgresql` · `flyway-core` + `flyway-database-postgresql` · `springdoc-openapi-starter-webmvc-ui` · `lombok 1.18.38` *(stable, **not** `edge-SNAPSHOT`)* · `spring-boot-devtools` *(hot restart)* · `bucket4j-core` *(new — per-user rate limiting)* · `spring-boot-starter-test` · `spring-security-test` · `testcontainers` (postgresql, junit-jupiter) + `spring-boot-testcontainers` *(new)*

**Deliberately excluded:** `stripe-java` (no payments) · `aws-sdk s3` (no file storage) · `jjwt` (Spring Security already bundles Nimbus — one JWT library, not two) · `mockito-subclass` and surefire `--add-opens` (unnecessary on Java 21) · `starter-mail` (Teams notifications would serve better; revisit later) · **`h2`**

**Why H2 is excluded entirely, not merely re-scoped to `test`:** the anti-double-booking guarantee *is* a Postgres partial unique index. H2 does not reproduce it faithfully, so an H2-backed test suite would go green while proving nothing about the one behaviour this system must never get wrong. Integration tests run on real Postgres via Testcontainers, which needs the Docker daemon running.

**Hot reload:** `spring-boot-devtools` gives a ~1–2s restart (not true hot-swap — in-memory state is lost). Restarts drop WebSocket connections, which is harmless because §5 #9 already requires client reconnect-and-resnapshot. The React frontend uses Vite HMR, which is instant and state-preserving.

**Git workflow:** every feature or feature set gets its own `feat/<name>` branch. Claude creates the branch and writes the code, then stops. **The user reviews, commits, pushes, and opens/merges the PR** — Claude never commits, pushes, or merges to `main`.

**What reaches GitHub:** source, `PLAN.md`, `.env.example`, `docker-compose.yml`, build config. Everything else — `.env`, build output, `node_modules/`, IDE files, agent tooling state, OS cruft — is gitignored.

---

## 12. Open items

- **Entra app registration** (tenant ID, client ID, redirect URI) — needed from IT before SSO can be switched on. Development proceeds on the local provider until then.
- **Real floor plan** — the interim 110-seat layout is seed data, replaceable without code changes.
- **Office timezone + working days** — assumed `Asia/Kolkata`, Mon–Fri, until confirmed.
