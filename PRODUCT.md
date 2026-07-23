# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

**Primary: hybrid-office employees.** They come in roughly two days a week and have no assigned desk. Today they arrive and scramble for a seat, and on busy days there aren't enough. They need to know — before they commit to a commute — whether a seat exists and which one is theirs.

**Managers.** Hold blocks of seats so their team can sit together on a shared day.

**Admins.** Maintain the floor layout, seat status (active / disabled / broken), and user lifecycle.

## Product Purpose

DeskDibs turns a live top-view floor map into the single authority on who sits where on a given day. Anyone can claim a seat up to 14 days ahead, managers can hold blocks for their team, and nobody can ever end up double-booked.

Success is the disappearance of the 9am scramble: people arrive knowing they have a seat, and scarce desks stay in circulation instead of being hoarded "just in case."

## Positioning

Most booking tools promise no double-booking as a feature claim. DeskDibs makes it a **structural property**: two partial unique indexes in PostgreSQL — one seat per date, one booking per person per date — sit *below* all application code. No application bug, lost race, retry storm, or future refactor can violate them.

A neighboring product that enforces the same rule in application code cannot truthfully make that claim.

## Operating Context

- New office running hybrid, ~2 days work-from-office. **110 seats total** across a Left Wing and Right Wing, long tables with a glass divider, three seats per side.
- Seat labels read positionally: `R3-A2` = Right table 3, side A, position 2.
- Booking is **whole-day**, one seat per person, bookable within `[today, today + 14]`.
- **Check-in by 11:00** or the seat auto-releases as a no-show.
- **Team blocks are soft** and stop being enforced at 10:00 — no job, no state change.
- All dates are `DATE` in a configured office timezone. The **client clock is never trusted** for cutoffs or "today."
- Identity is dual-provider behind one config flag: local dev login today, Microsoft Entra ID (OIDC) the day company IT issues the app registration.
- Delivered as a responsive web app, kept Teams-tab-ready (iframe-safe, SDK seam). It works on phones meanwhile.

## Capabilities and Constraints

**Confirmed capabilities:** claim / cancel a seat; idempotent claims via a client-generated `Idempotency-Key`; team soft reservations with partial-success reporting; automatic no-show release at 11:00; live seat-map updates; a 14-day date strip showing per-day fill percentage so people can pick a quiet day.

**Technical constraints:**
- Spring Boot 3.4 (Java 21) + plain PostgreSQL 18. Deliberately *not* Supabase — a frontend-talks-to-DB model would create two authorization systems that must never disagree.
- React 19 + Vite + TypeScript + Tailwind + Motion. Seat map is **SVG**, not positioned divs.
- Real-time over STOMP/WebSocket on `/topic/seatmap/{date}`; degrades to a 15-second poll when the socket is unavailable — slower, never wrong.
- **Layout is data, not code.** Seats derive position from their table's `pos_x/pos_y/rotation` plus side and index. Moving a table moves its seats for free; a new floor plan is seed rows or an admin editor, never a React change.
- Target load is 150 concurrent requests — comfortably inside one Spring Boot instance. No Redis, Kafka, distributed locks, queue, or cache tier. This was explicitly rejected as architecture for its own sake.

**Deliberately undecided / out of scope:**
- Half-day AM/PM slots — rejected for now; revisit only if the office asks.
- Entra group import for teams — possible later; teams are managed in-app because org-chart groups rarely match who needs to sit together.
- The Teams tab manifest itself — the seam is kept cheap, but the tab is not built.
- The **real floor plan has not arrived.** The 110-seat layout is interim, matching a reference photo.

## Brand Commitments

**Confirmed: none.** No company name, logo, palette, or existing design language constrains this product. Future design work establishes the visual world from scratch rather than inheriting one.

## Evidence on Hand

- `PLAN.md` — the approved design record: locked decisions, data model, booking rules, 14 enumerated edge cases, testing strategy, and build phases.
- `docker-compose.yml` — disposable PostgreSQL 18 on port 5433, deliberately chosen so it can never collide with a Homebrew Postgres on 5432.

**Absences future work must not fabricate:** there is no logo, brand copy, or marketing content. There are no real users, bookings, testimonials, or usage metrics yet. The office reference photo informing the interim layout is referenced but not present in this repo. Seat labels and the 110-seat arrangement are seed data, not observed truth.

## Product Principles

1. **Correctness lives below the application.** When a rule matters, push it into the database constraint, not the service layer. The invariant is the product.
2. **Design against scarcity, not abundance.** With 110 seats for more people than that, "book just in case" is the failure mode. Check-in deadlines and soft team blocks exist to keep desks circulating.
3. **Never silently take someone's seat.** Manager power is bounded: reserving over existing bookings reports partial success naming who holds what. The system does not force-cancel to satisfy authority.
4. **Degrade slower, never wrong.** Every fallback trades latency for correctness — socket to poll, optimistic UI to server truth, client clock to office timezone.
5. **A lost race is a designed moment.** Losing a seat by a second is the most common unhappy path; it gets a named winner and an honest explanation, not a generic error.

## Accessibility & Inclusion

**Binding standard: WCAG 2.2 AA.** This is a hard constraint future design work may never trade away.

- Seat states (Available · Yours · Occupied · Team-reserved · Checked-in · Disabled · Pending) must be distinguishable by **shape and pattern, not color alone**.
- Full keyboard navigation of the seat map. The occupant card that appears on hover must appear identically on keyboard focus.
- An ARIA live region announces seat changes as they broadcast.
- Visible focus rings throughout; `prefers-reduced-motion` respected by every animation.
- Accessible seats are a first-class data fact (`seat.accessible`), not a visual annotation.
