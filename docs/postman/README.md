# Testing DeskDibs with Postman

## 1. Start the backend

```bash
cd backend
export AUTH_PROVIDER=local
mvn spring-boot:run
```

This reads `.env` at the repo root for `DB_PASSWORD` etc. — make sure that file has
`DEV_SEED_USERS=true` and `DEV_SEED_PASSWORD` set to something (already done if you pulled the
latest `.env`). Wait for:

```
Started DeskDibsApplication in ... seconds
```

Confirm it's up:

```bash
curl http://localhost:8080/actuator/health
```

Three demo accounts exist automatically, one per role — all sharing `DEV_SEED_PASSWORD`:

| Email | Role |
|---|---|
| `employee@deskdibs.local` | EMPLOYEE |
| `manager@deskdibs.local` | MANAGER |
| `admin@deskdibs.local` | ADMIN |

They're seeded by `DevUserSeeder` on boot, only when `AUTH_PROVIDER=local` — never in production.

**Restarting the app invalidates every token.** `DESKDIBS_JWT_SECRET` is blank in dev, so a fresh
random signing key is generated each boot. That's fine — just re-run the login requests below.

## 2. Import into Postman

- **Import → File** → `docs/postman/DeskDibs.postman_collection.json`
- **Import → File** → `docs/postman/DeskDibs.postman_environment.json`
- Select **DeskDibs Local** as the active environment (top-right dropdown)

## 3. How the token flow works

DeskDibs is a **stateless bearer-token API** — no cookies, no server-side session. Every request
past login carries `Authorization: Bearer <token>`.

```
POST /api/auth/login  {email, password}
        │
        ▼
  { "accessToken": "eyJ...", "user": {...} }
        │
        ▼
  every other request:  Authorization: Bearer eyJ...
```

The three **Login as …** requests in the **1. Auth** folder each carry a *Tests* script that
automatically saves the returned token into the environment (`employeeToken`, `managerToken`,
`adminToken`) — you never copy-paste a token by hand. Run all three once after each restart, then
everything else in the collection just works.

## 4. Run order

1. **1. Auth** → run all three logins (populates the three tokens)
2. **2. Seat Map → Get seat map for a date** — this also auto-picks a free seat into `{{seatId}}`
   for every later request to reuse
3. **3. Bookings** — run top to bottom. It claims a seat as the employee, then deliberately tries
   the *same* seat as the manager to show the 409, checks in, then shows a stranger being refused
   before the real owner cancels
4. **4. Reservations** — needs `{{teamId}}` to exist first (see below)

## 5. The one gap: there's no "create a team" endpoint yet

Team management was never in scope for any phase so far — `ReservationController` assumes a team
already exists. To test the reservations folder, create one directly:

```bash
psql -h localhost -U deskdibs -d deskdibs
```

```sql
-- Find the manager's user id (seeded by DevUserSeeder)
SELECT id, email FROM app_user WHERE email = 'manager@deskdibs.local';

-- Create a team managed by that user (replace 2 with the id above)
INSERT INTO team (name, manager_user_id) VALUES ('Platform', 2) RETURNING id;
```

Set the returned id as `teamId` in the Postman environment, then run the **4. Reservations**
folder.

## 6. Reading a response

Every success and every error come back as JSON with the same shape for errors:

```json
{
  "code": "SEAT_ALREADY_BOOKED",
  "message": "This seat is already booked for that date.",
  "path": "/api/bookings",
  "timestamp": "...",
  "details": {
    "seatLabel": "R3-A2",
    "takenByDisplayName": "Dev Employee"
  }
}
```

`code` is stable and safe to branch on in code; `details` carries exactly the structured facts
PLAN.md calls for — who holds a contested seat, which seat you already have, which team is
blocking you. Never a stack trace, never a raw SQL message.

## 7. What Postman can't easily show you: the WebSocket broadcast

Claiming a seat also broadcasts a live update to every open floor map over STOMP
(`/topic/seatmap/{date}`), authenticated by the same bearer token on the STOMP `CONNECT` frame.
Postman's WebSocket request type speaks raw WebSocket, not STOMP framing, so demonstrating this
properly needs a STOMP-aware client — this is already covered by an automated test
(`RealtimeBroadcastTest`) that opens a real STOMP connection and asserts a claim arrives on the
topic. Treat that test, not Postman, as the source of truth for the realtime behaviour; Postman is
the right tool for the REST surface above.

## 8. Full endpoint list

See `docs/openapi.json`, or browse it live and interactively while the app is running:

```
http://localhost:8080/swagger-ui.html
```
