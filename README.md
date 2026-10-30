# High-Concurrency Entertainment Ticketing & Seat Allocation Engine

## Architecture

React talks only to the API Gateway. The Gateway validates every JWT, then forwards the caller's
identity to the microservices as headers so they never have to re-validate tokens themselves.
Each service owns its own MySQL database (no shared schema), and all inter-service calls
(Booking Service -> Seat Service) go through Eureka + Spring Cloud LoadBalancer by service name,
never by a hardcoded host/port.

| Component | Port | Folder | Database |
|---|---|---|---|
| Eureka Server | 8761 | `eureka-server (1)/eureka-server` | - |
| API Gateway | 8080 | `api-gateway/api-gateway` | - |
| Auth Service | 8081 | `auth-service/auth-service` | `ticketing_auth` |
| Event Service | 8082 | `event-service/event-service` | `ticketing_event` |
| Seat Service | 8083 | `seat-service/seat-service` | `ticketing_seat` |
| Booking Service | 8084 | `booking-service/booking-service` | `ticketing_booking` |
| React + Vite frontend | 5173 | `ticketing-frontend` | - |

## Prerequisites

- Java 17 (JDK)
- Maven (or use the included `mvnw` / `mvnw.cmd` wrapper in each service)
- Node.js 18+ and npm
- MySQL 8, running locally with a `root` user and a password you already have

## 1. Database setup

Create the four databases (empty — each service creates its own tables on first start via
`ddl-auto=update`):

```sql
CREATE DATABASE ticketing_auth;
CREATE DATABASE ticketing_event;
CREATE DATABASE ticketing_seat;
CREATE DATABASE ticketing_booking;
```

## 2. Environment variables

No password or secret is stored in this repository. Set these **before** starting each Java
service, in the same terminal/session you launch it from:

**PowerShell (Windows):**
```powershell
$env:MYSQL_PASSWORD = "your-mysql-root-password"
$env:JWT_SECRET      = "any-long-random-string"   # must be identical for Auth Service and Gateway
```

**bash/zsh:**
```bash
export MYSQL_PASSWORD="your-mysql-root-password"
export JWT_SECRET="any-long-random-string"
```

If you don't set `JWT_SECRET`, every service falls back to the same built-in default, so local
demos still work — but set it yourself before showing this to anyone else, and never commit
either value.

If you run each service from a separate IntelliJ Run Configuration, set both variables in that
configuration's "Environment variables" field (not in `application.properties`) so they apply
every time you run it.

### Creating an ADMIN account (optional, for managing events/seats)

Registration through the app always creates a `USER`. To get one `ADMIN` account for local
development, set these two variables before starting **Auth Service only**, then start it once:

```powershell
$env:ADMIN_EMAIL = "admin@example.com"
$env:ADMIN_PASSWORD = "choose-your-own-password"
```

On startup, Auth Service creates that one admin account if it doesn't already exist, then you can
remove the variables — the account persists in `ticketing_auth`. Log in with it via
`POST /auth/login` (Postman) to get an admin JWT; the React frontend has no admin screens, so use
that JWT directly against `POST/PUT/DELETE /events` and `POST /seats` in Postman.

## 3. Start order

1. MySQL, with the four databases created (step 1).
2. **Eureka Server** — wait until http://localhost:8761 loads and shows no instances yet.
3. **Auth**, **Event**, **Seat**, **Booking** services, any order — each needs `MYSQL_PASSWORD` set
   in its environment first. Watch each console for `Started ... Application` with no stack trace,
   and confirm on the Eureka dashboard that all four registered.
4. **API Gateway** — needs `JWT_SECRET` (or nothing, to use the shared default) set; start it last
   so it can resolve the other services through Eureka immediately.
5. **Frontend**:
   ```
   cd ticketing-frontend
   npm install
   npm run dev
   ```
   Open http://localhost:5173.

## 4. Demo data

Seats and events are created by an admin, not through the app's UI. After all services have
started **once** (so Hibernate has created the tables and the `seats.locked_by` /
`seats.booked_by` columns), run:

```
mysql -u root -p < demo-data/seed.sql
```

It only inserts rows — it never drops or deletes anything — creating two events with 16 available
seats each (rows A–B, seats 1–8). Safe to re-run.

## 5. Demo flow

Register, log in, open **Events**, choose an event, click an available seat, **Lock seat**
(10-minute countdown), **Book seat**, see the confirmation ticket, open **My bookings**, log out.
Open a second browser (or private window) with a second account to see a seat you locked show as
"held by another user" there, and to trigger the 409 conflict message if both try to lock it.

## 6. How identity and roles work

1. `POST /auth/login` returns a JWT containing `userId`, `name`, `sub` (email) and `role`
   (`USER` or `ADMIN`).
2. The Gateway validates the JWT on every route except `/auth/login` and `/auth/register`, strips
   any `X-User-Id` / `X-User-Email` / `X-User-Role` headers sent by the client, and sets all three
   from the validated token.
3. Seat, Event and Booking services read the caller's identity only from those headers — never
   from the request body, a query string, or a client-supplied header. Event/seat *mutations*
   additionally require `X-User-Role: ADMIN`; everything else works for any authenticated user.

Because of this, calls made **directly** to ports 8081–8084 (e.g. from Postman, bypassing the
Gateway) must set these headers by hand. Tokens issued before this project added `userId`/`role`
to the JWT are rejected with 401 — log in again after updating.

## 7. API (through the Gateway)

| Method and path | Auth | Notes |
|---|---|---|
| `POST /auth/register` | public | 400 invalid input, 409 email already registered |
| `POST /auth/login` | public | 200 returns the JWT as text, 401 invalid credentials |
| `GET /events`, `GET /events/{id}` | any user | 404 when the event does not exist |
| `POST/PUT/DELETE /events...` | ADMIN | 403 for a non-admin |
| `GET /seats/event/{eventId}` | any user | Expired locks released first; each seat has `status`, `lockedBy`, `lockSecondsRemaining` |
| `POST /seats` | ADMIN | 403 for a non-admin |
| `PUT /seats/lock/{eventId}/{seatNumber}` | any user | 409 when booked or locked by someone else |
| `PUT /seats/release/{eventId}/{seatNumber}` | any user | Only the lock owner; 409 otherwise |
| `PUT /seats/compensate/{eventId}/{seatNumber}` | internal | Called only by Booking Service on save failure; not used by the frontend |
| `POST /bookings?eventId=&seatNumber=` | any user | 201; 409 not locked by you / lock expired / already booked; 404 seat not found; 502/503 seat service issue |
| `GET /bookings/my` | any user | The caller's own bookings |
| `GET /bookings/user/{userId}` | any user | 403 unless it is the caller's own id |
| `GET /bookings/event/{eventId}` | any user | Only the caller's own bookings for that event |

## 8. Concurrency

Lock, book, release and compensate are each **one conditional SQL `UPDATE`** in
`SeatRepository` (e.g. `... WHERE status = 'AVAILABLE' OR (status='LOCKED' AND lockedUntil < now)`).
MySQL's row lock decides the single winner; every other simultaneous request updates zero rows and
gets 409. Booking additionally requires the caller to hold a still-valid lock
(`lockedBy = caller AND lockedUntil >= now`), so only the lock owner can ever book.

Run the race test against the **real, running stack** (pick a seat that is currently AVAILABLE):

```
node tests/concurrency-test.mjs 1 A1 6
```
It fires 6 users at the same seat simultaneously and asserts exactly one gets the lock, that a
non-owner cannot book it, and that two simultaneous booking attempts from the winner create
exactly one row.

**Important:** if you run this script against the mock server used during development (not your
real stack), it is not evidence of anything — Node's mock is single-threaded and cannot race.
Only a run against the real MySQL-backed services proves the concurrency guarantee.

## 9. Failure safety: booking save failure after the seat is booked

Seat Service and Booking Service have separate databases, so the "mark seat BOOKED" and "save the
booking row" writes cannot share one ACID transaction — this is a normal microservices trade-off,
not a bug. Booking Service now handles it explicitly: if saving the booking row throws after the
seat call already returned `BOOKED`, it calls `PUT /seats/compensate/{eventId}/{seatNumber}`
(scoped to that same seat + user) to revert the seat to `AVAILABLE`, and returns 503 to the caller
("seat has been released, try again"). If that compensating call also fails, the seat is left
`BOOKED` with no booking row — this is a genuine, documented limitation of a two-database design
without a distributed transaction coordinator (deliberately not added here, to keep the
architecture as simple as the assignment calls for). In that rare case, an admin would need to
inspect the seat manually.

## 10. Tests

### Actually executed in this environment (no MySQL, Maven, or network here)
- **Compilation** of Seat, Event and Booking service source against the real Spring Boot 4.1.1 /
  Spring Cloud jars extracted from the built `booking-service` jar. Passed after each change.
  Auth Service and the Gateway's JWT filter could only be partially compile-checked (jjwt and
  Spring Security jars were not available offline); the new `AdminBootstrap` class compiled
  cleanly, and the pre-existing partial-compile gaps are unrelated to this change.
- The four new/changed **Seat** HQL update queries were parsed and validated by a real Hibernate
  `SessionFactory` against the `Seat` entity mapping (no live database).
- **Frontend**: the full flow (register, login, deep-linking, two-user seat locking, the 409 race,
  lock expiry, booking, isolated "my bookings", 401/expired-token handling, logout) passed 27/27 in
  real Chromium, against a mock gateway that mirrors the documented contract — not your real
  Java/MySQL stack.
- The concurrency script (`tests/concurrency-test.mjs`) was sanity-run against that same mock to
  confirm the script itself works. **This does not verify MySQL concurrency** — see the warning in
  section 8.

### NOT executed — please run these yourself
| # | Test | Command / steps |
|---|---|---|
| 1 | Register | Postman `POST /auth/register` |
| 2 | Login | Postman `POST /auth/login` |
| 3 | Invalid login | Same with a wrong password → expect 401 |
| 4 | JWT protected route | `GET /events` with a valid Bearer token |
| 5 | Invalid JWT | `GET /events` with a garbage token → expect 401 |
| 6 | Expired JWT | Wait 1h past login, or shorten expiry temporarily → expect 401 |
| 7 | Event 404 | `GET /events/99999` → expect 404 |
| 8 | Seat retrieval | `GET /seats/event/1` |
| 9 | Seat lock | `PUT /seats/lock/1/A1` |
| 10 | Duplicate lock | Same call again, different user → expect 409 |
| 11 | Lock expiry | Wait for the lock to expire, then `GET /seats/event/1`, seat is AVAILABLE again |
| 12 | Ownership | User B tries to book/release a seat locked by User A → expect 409 |
| 13 | Booking | `POST /bookings?eventId=1&seatNumber=A1` after locking it → expect 201 |
| 14 | Duplicate booking | Repeat the same call → expect 409 |
| 15 | My bookings / ownership | `GET /bookings/my` as two different users → each sees only their own |
| 16 | Logout | Click Log out in the UI → protected pages redirect to login |
| 17 | Gateway routing | Confirm `/auth`, `/events`, `/seats`, `/bookings` all resolve through 8080 |
| 18 | CORS/preflight | Open the app at both `localhost:5173` and `127.0.0.1:5173` in a real browser and confirm no CORS errors in devtools |
| 19 | **Concurrency (critical)** | `node tests/concurrency-test.mjs 1 A1 6` against the real running stack — must show exactly one `HTTP 200`, five `HTTP 409` |
| 20 | Admin authorization | Log in as a normal user, try `POST /events` → expect 403; log in as the bootstrap admin, same call → expect 201 |
| 21 | Frontend build | `cd ticketing-frontend && npm install && npm run build` on your machine (not run here — the shipped `node_modules` contains platform-specific binaries) |

## 11. Remaining limitations

- The frontend has no admin screens; event/seat management is Postman-only by design for this
  submission.
- If the compensating call in section 9 itself fails, the seat can be left `BOOKED` with no
  booking row — a genuine limitation of two independent databases without a distributed
  transaction coordinator.
- Services trust the `X-User-Id` / `X-User-Role` headers, so ports 8081–8084 must never be exposed
  outside the machine/network the Gateway runs on.
- `JWT_SECRET` falls back to a shared default if unset, which is fine for a local demo but must be
  set to a real secret (and kept out of git) for anything beyond that.
