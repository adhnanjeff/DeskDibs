# How DeskDibs works

A complete description of the product: who can do what, what every rule is, and what happens in
each case — including the awkward ones. Written so you can answer any question about the app
without reading the code.

Every number and rule below is configuration or code as it stands today. Where a value is
configurable, the setting name is given so you can check what a particular deployment is running.

- [1. What the app is for](#1-what-the-app-is-for)
- [2. Who can do what](#2-who-can-do-what)
- [3. The office's clock](#3-the-offices-clock)
- [4. The floor](#4-the-floor)
- [5. Booking a desk](#5-booking-a-desk)
- [6. Two people, one desk](#6-two-people-one-desk)
- [7. Check-in](#7-check-in)
- [8. The no-show release](#8-the-no-show-release)
- [9. Cancelling and moving](#9-cancelling-and-moving)
- [10. Team holds](#10-team-holds)
- [11. Administration](#11-administration)
- [12. Live updates](#12-live-updates)
- [13. Security](#13-security)
- [14. The interface](#14-the-interface)
- [15. Every refusal, and what it means](#15-every-refusal-and-what-it-means)
- [16. Known gaps](#16-known-gaps)
- [17. Quick answers](#17-quick-answers)

---

## 1. What the app is for

The office has fewer desks than people, and nobody has an assigned one. DeskDibs answers three
questions:

1. **Is it worth going in?** — how full each day already is.
2. **Where do I sit?** — pick a specific desk on a plan of the actual floor.
3. **Where is everyone else?** — find a colleague, or sit with your team.

It books **desks only**. There is no meeting-room booking, no parking, no catering. The meeting
rooms drawn on the floor plan are context so you can orient yourself — they are not bookable, and
clicking them does nothing.

---

## 2. Who can do what

Three roles. A person has exactly one, stored in the database — never claimed by the browser.

| | Employee | Manager | Admin |
|---|---|---|---|
| See the floor plan and who is sitting where | ✅ | ✅ | ✅ |
| Book, cancel, move **their own** desk | ✅ | ✅ | ✅ |
| Check in to their own booking | ✅ | ✅ | ✅ |
| Cancel/move a desk for **someone on a team they manage** | ❌ | ✅ | ✅ |
| Cancel/move **anyone's** desk | ❌ | ❌ | ✅ |
| Hold a block of desks for a team | ❌ | ✅ | ✅ |
| Release a team hold early | ❌ | ✅ | ✅ |
| Activate / deactivate accounts | ❌ | ❌ | ✅ |
| Take a desk out of service | ❌ | ❌ | ✅ |
| Day reports (who sat where) | ❌ | ❌ | ✅ |
| Live API activity view | ❌ | ❌ | ✅ |

Three things are worth calling out because they surprise people:

**Nobody can check in on someone else's behalf — not even an admin.** Check-in is a statement that
*you* are in the building. A manager vouching for a report would make the number meaningless, so the
code refuses it for every role.

**A manager's power is scoped to their own team, not to "being a manager".** The check is the stored
manager-of-their-team relationship. A manager of Finance cannot touch a Marketing person's booking.

**Managers can hold desks only for teams they actually manage.** Being a manager is not enough — the
server re-checks which teams are yours on every request.

---

## 3. The office's clock

Every date and every deadline resolves **server-side, in the office timezone**. The browser's clock
is never trusted for anything that decides an outcome — someone with a wrong laptop clock, or
travelling, gets the same answers as everyone else.

| Setting | Default | What it controls |
|---|---|---|
| `OFFICE_TIMEZONE` | `Asia/Kolkata` | The only definition of "today" |
| `OFFICE_WORKING_DAYS` | `MON–FRI` | Which days a desk may be booked at all |
| `BOOKING_HORIZON_DAYS` | `14` | How far ahead you can book |
| `CHECK_IN_OPENS_TIME` | `07:00` | Earliest you may check in |
| `NO_SHOW_RELEASE_TIME` | `11:00` | When un-checked-in desks go back to the pool |
| `NO_SHOW_RELEASE_DAYS` | `MON–FRI` | Which days that job runs |
| `SAME_DAY_CUTOFF_TIME` | `11:00` | When today is marked "under way" in the date strip |
| `TEAM_BLOCK_RELEASE_TIME` | `10:00` | Default hour a team hold lapses |

So a normal weekday looks like this:

```
00:00 ─────── 07:00 ─────────── 10:00 ────── 11:00 ──────────────── 23:59
              check-in opens    team holds   no-show release runs
                                lapse        today marked "under way"
                                             (still bookable)
```

---

## 4. The floor

One floor, **102 bookable desks**, arranged as floor → zone → table → seat. Desk labels read
`R3-A2`: table `R3`, side `A`, seat `2`.

Every desk on the map is in one of four states **for the date you are looking at**:

| State | Means | Can you take it? |
|---|---|---|
| **Available** | Free | Yes |
| **Booked, not in** | Someone has it, hasn't confirmed arrival | No |
| **Checked in** | Someone has it and is here | No |
| **Yours** | You have it that day | It's already yours |
| **Team hold** | Held for a team until that block lapses | Only if you're on that team |
| **Out of service** | Disabled or broken | No |

The floor plan also draws meeting rooms, cabins, the pantry, the server room, stairs, the fire exit
and the balcony. None of these are bookable.

Colour is never the only signal — every state carries an icon and a text label too, so the map
works for colour-blind users and in screenshots.

---

## 5. Booking a desk

**Pick a day → pick a desk → Book now.** One desk per person per day.

The server checks four things, in this order, and stops at the first failure:

1. **Is the date in range?** Not in the past, not beyond the 14-day horizon.
2. **Is the office open?** Weekends are refused — not merely greyed out. A weekend the interface
   hides is a weekend a scripted request could still book, so the rule lives on the server.
3. **Is the desk in service?** Disabled and broken desks are refused.
4. **Is it held for a team you're not on?** See [team holds](#10-team-holds).

Then it inserts the booking. **The insert itself is the availability check** — see the next section.

### Which days you can pick

The date strip shows **today plus the next 14 days**, weekends included and marked closed. Every day
is present; there are no gaps to interpret.

**Today never disappears from the strip**, even late in the day. Past `SAME_DAY_CUTOFF_TIME` (11:00)
it is marked as *under way* — but it stays bookable, deliberately. The no-show release hands desks
back at exactly that hour, and those desks are for whoever walks in late. Closing the day would
strand every one of them.

### What each day tile tells you

- How many desks are free — **already accounting for team holds**, so the strip and the floor never
  disagree
- A fill bar for a fast scan of the whole fortnight
- A tick if you already hold a desk that day
- "Closed" for weekends

---

## 6. Two people, one desk

This is the question the whole design turns on, so here is the exact answer.

**Two people who press Book at the same instant: exactly one wins, and the database decides.**

There is no "check if free, then book". That pattern has a gap between the check and the write, and
under load two requests both pass the check. Instead the app just inserts, and the database refuses
the second one:

```sql
CREATE UNIQUE INDEX uq_seat_active_per_date
    ON booking (seat_id, booking_date) WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uq_user_active_per_date
    ON booking (user_id, booking_date) WHERE status = 'ACTIVE';
```

The first index makes a double-booked desk **impossible to represent**. Not unlikely — impossible.
No application bug, no race, no retry storm, and no direct API call can create two live bookings for
one desk on one date, because the database will not store it.

The second does the same for "one person, two desks on one day".

They are **partial** indexes — only `status = 'ACTIVE'` rows are covered. That is what lets a
cancelled or released booking stay in the table as history while instantly freeing the desk.

**What the loser sees:** their tile shakes and flips to occupied, with the winner's name. Not an
error dialog — losing a race is the system working, not a fault.

This is covered by tests that run **150 threads at one desk** and assert exactly one booking exists
and 149 refusals each name the winner.

### Double-clicking, and flaky connections

- **The Book button disables itself while the request is in flight**, so an impatient double-click
  cannot send two requests.
- **You can never end up with two desks**, because of `uq_user_active_per_date`.
- Every booking request carries an **Idempotency-Key**, so a repeated identical request replays the
  original result instead of failing. (See [known gaps](#16-known-gaps) — this is not fully
  effective today.)

---

## 7. Check-in

Check-in is how you say *"I'm here"*. Without it, your desk is given away at 11:00.

**Window: 07:00 → 11:00**, office time, on the day of the booking.

- Before 07:00 → refused. Check-in is meant to be evidence you are in the building; open from
  midnight it would prove nothing.
- After 11:00 → the release has already taken the booking, so there is nothing left to check in to.

**Where:** a card at the top of the seat map that appears only when you have an unchecked booking for
today — *"Today you have R10-A1 — check in by 11:00 or it is released"* with an **I'm here** button.
It is also on My bookings. When you have nothing to do, nothing is shown.

**Rules:**

- Only the owner may check in. Not a manager, not an admin.
- Only today's booking. Tomorrow's is refused until tomorrow.
- Only once — a second attempt is refused rather than silently overwriting your arrival time.
- Only an active booking. A cancelled or already-released one cannot be checked into.

---

## 8. The no-show release

At **11:00, Monday to Friday**, one scheduled job runs: every booking for today that nobody checked
into is set to `RELEASED_NO_SHOW`, and the desk goes straight back into the pool.

- The desk is claimable **immediately** — the released row leaves the partial unique index, so
  someone walking in at 11:30 can take it.
- The row is kept as history, not deleted. You can see on My bookings that it was released, and
  why.
- Nothing else in the system changes. There is no locking, no state machine, nothing to go wrong.

**Why it exists:** without it, people who book optimistically and then work from home would hold
desks all day that others could have used.

---

## 9. Cancelling and moving

**Cancel** frees the desk immediately for anyone else. The row stays as history with status
`CANCELLED`.

**Move** changes which desk you have on a day. It is deliberately **one operation, not
cancel-then-book**:

- Both halves run in a single transaction. If the desk you're moving to gets taken in the meantime,
  **you keep the one you had.** A cancel-then-book from the browser could lose you the new desk
  *and* the old one.
- Moving to the desk you already hold does nothing and succeeds.
- Moving when you hold nothing that day is just a booking.

**If you try to book a second desk on a day you already have one**, the app doesn't just refuse —
the refusal names the desk that's in the way and offers to **move you there instead**, one click, no
extra round trip.

---

## 10. Team holds

A manager can reserve a block of desks so a team can sit together on a busy day.

**How to:** Reservations → pick the team → pick the dates → tap desks on the floor plan → Hold
desks.

### The two rules that matter

**1. A hold never takes a desk from anyone.** If someone already has a booking on any day in the
range, that desk is **left exactly as it is** and reported back to you — naming who holds it and on
which day — so you can pick a different one. The system never force-cancels a booking to make room.
A partly-successful hold is a normal outcome, not an error.

**2. Holds are soft, and lapse on a clock.** Every hold has a release time (default **10:00**). Past
that hour, on the day it covers, it simply stops being enforced. **No job runs, no status changes,
the row is untouched** — the check that enforces holds just stops returning it.

This is the single most common source of "why did my reservation vanish?" It didn't. It lapsed on
schedule, exactly as designed, so a reserved-but-empty desk doesn't waste a scarce desk all day. The
Reservations screen says so on the block itself: *"Released at 10:00 — anyone may sit here."*

### Who can book a held desk

- **Anyone on the holding team** — that's who the hold is for.
- **Nobody else**, until it lapses. They get a refusal naming the team and the release time.
- **After it lapses** — anyone at all.

A manager can also release a hold early, per desk or as a whole block.

---

## 11. Administration

### People

- See everyone, their role, and whether the account is live.
- **Deactivate an account** — it is refused at login, and every desk it still holds from today
  onward is handed back. The response lists exactly which bookings were released, so the consequence
  is visible rather than silent. Those get status `RELEASED_USER_DEACTIVATED` — distinct from
  cancelled, because nobody gave them up.
- An admin cannot deactivate their own account.
- Reactivating restores access but does **not** give back the desks.

### Desks

- Take a desk **out of service** (disabled or broken). Every booking on it from today onward is
  released with status `RELEASED_SEAT_REMOVED`, and the people affected see the reason on their own
  bookings page.
- Put it back into service.
- The desk row is never deleted, so booking history survives floor-plan changes.

### Reports

**Admin → Reports.** Pick a date, get who sat where: desk, person, email, team, status, and arrival
time — plus counts of booked, turned up, no-shows and cancelled. Downloadable as CSV.

It deliberately includes rows the floor map cannot show — cancelled bookings and no-shows. The map
answers *"what can I book"*; the report answers *"what happened"*, and a cancellation is part of that
answer.

### API view

A live diagram of requests hitting the server, for watching the system work during a busy morning.
Admin-only, and the server refuses the subscription for anyone else.

---

## 12. Live updates

The seat map updates **live**, without refreshing. When anyone books, cancels, moves or checks in,
everyone looking at that date sees the desk change within a moment.

If the live connection drops, the map **falls back to polling every 15 seconds**. Slower, never
wrong. A map that has silently stopped updating is the worst possible failure here — it looks
exactly like an office where nobody is booking anything.

---

## 13. Security

- **Every request is authenticated.** No anonymous access to any office data.
- **Authorization is checked on the server, per object** — not per role. "Can this person act on
  *this* booking" is re-asked from the database every time; the browser's opinion is never trusted.
- **Roles come from the database**, never from a token claim a client could forge.
- **Live subscriptions are authorized too.** The admin telemetry topic refuses non-admin
  subscribers — a message broker that authenticates the connection but not the subscription is a
  common way to leak data.
- **Booking actions are rate-limited** per account (default 30/minute) as a politeness limit. It is
  never what prevents double booking — that is the database index, and turning the limit off cannot
  produce a double booking.
- **Error responses never leak internals.** No stack traces, no SQL, no exception messages —
  each refusal has a stable machine-readable code and a written-for-humans message.

---

## 14. The interface

**Two themes**, switchable in the header, remembered per browser:

- **Office** *(default)* — the PreCorr corporate standard: white surfaces, neutral greys, blue for
  actions.
- **Cool** — a high-contrast Bauhaus style with hard shadows and square corners.

Both are light. This is a house-style switch, not a light/dark switch.

**Accessibility:** every control reaches the 44px touch target on phones and touch screens, every
colour pair meets WCAG AA contrast, every seat carries an icon and text label as well as a colour,
and the whole app works from the keyboard — including the calendar (arrow keys move, Enter picks,
Escape closes) and the team picker (arrows, type-ahead, Enter).

---

## 15. Every refusal, and what it means

| Code | What happened | What to do |
|---|---|---|
| `DATE_OUTSIDE_BOOKING_WINDOW` | Past date, or beyond 14 days | Pick a date in range |
| `DATE_NOT_A_WORKING_DAY` | Weekend | Pick a weekday |
| `SEAT_NOT_BOOKABLE` | Desk is disabled or broken | Pick another desk |
| `SEAT_RESERVED_FOR_TEAM` | Held for a team you're not on | Another desk, or wait for the release time |
| `SEAT_ALREADY_BOOKED` | Someone won the race | Pick another desk — the winner is named |
| `ALREADY_BOOKED_THAT_DAY` | You already have a desk that day | Use the offered Move |
| `CHECK_IN_NOT_FOR_TODAY` | That booking isn't for today | Check in on the day |
| `CHECK_IN_NOT_OPEN_YET` | Before 07:00 | Come back after check-in opens |
| `ALREADY_CHECKED_IN` | You already did | Nothing |
| `BOOKING_NOT_ACTIVE` | Cancelled or already released | Book again |
| `BOOKING_ACCESS_DENIED` | Not yours, and you don't manage them | Nothing |
| `IDEMPOTENCY_KEY_CONFLICT` | Key reused for a different request | Retry as a fresh request |

---

## 16. Known gaps

Honest list. None of these can cause a double booking.

**The idempotency key doesn't do its job.** The mechanism exists end to end, but the browser
generates a **fresh key on every attempt**, so a retry never matches the original. Effect: if the
server commits your booking and the response is lost, clicking again gives you a confusing *"you
already have R5-A1"* — the very message the key exists to prevent. You are never double-booked; it
just reads badly.

**Check-in has no proximity evidence.** It is a self-declaration. The 07:00 window stops midnight
check-in from home, but somebody could still confirm from anywhere at 09:00.

**Everyone can see who has and hasn't arrived.** The map shows each occupied desk's owner *and*
whether they've checked in, to every signed-in person. Read as a presence indicator this is useful;
read as an attendance board it's employee monitoring. Worth a deliberate decision.

**Check-in times are kept indefinitely.** There is no retention or purge policy.

**Team management has no UI.** Teams live in the database and are changed by migration; there is no
endpoint to create one.

**A hold shows only the first day's availability.** When holding across a range, the map shows the
start date. A desk booked on a later day in the range is reported back in the partial-success
result rather than shown up front.

---

## 17. Quick answers

**"Can two people get the same desk?"**
No. Not unlikely — impossible. A database unique index makes it unrepresentable; the second insert
is rejected by the database itself.

**"What if I book and my internet drops?"**
Either it saved or it didn't; you'll never get two desks. Reload and look at My bookings.

**"When do I have to check in by?"**
11:00. From 07:00 onwards. After 11:00 the desk goes back to the pool.

**"Can I still book a desk this afternoon?"**
Yes. Today stays bookable all day — that's the point of releasing no-shows at 11:00.

**"My team reservation disappeared."**
It lapsed at its release time (default 10:00), as designed, so the desks aren't wasted. It is still
listed on the Reservations screen, marked released.

**"Can I reserve a desk someone already booked?"**
No, and the app will not take it from them. It tells you who has it and on which day.

**"Can my manager book for me?"**
They can cancel or move your booking, but they cannot check in for you.

**"Who can see where I'm sitting?"**
Everyone signed in — that's the Find a colleague feature. They can also see whether you've checked
in.

**"How far ahead can I book?"**
14 days, weekdays only.

**"What happens if a desk breaks?"**
An admin takes it out of service; anyone holding it from today onward is released and told why.
