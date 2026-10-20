# Slotly

Multi-tenant booking & CRM platform for small service businesses: barbershops, beauty salons, tutors, clinics.
Each business (tenant) manages its staff and services; clients book time slots online.

**Live demo:** https://slotly-minx.onrender.com/app/register — sign up a business, add a master, a service and working hours.
Your clients then book at `https://slotly-minx.onrender.com/b/<your-slug>`; the API is browsable at [/swagger-ui.html](https://slotly-minx.onrender.com/swagger-ui.html).
(Free instance: the first request after a quiet period can take up to a minute while it wakes up.)

[![CI](https://github.com/oldsoska/slotly/actions/workflows/ci.yml/badge.svg)](https://github.com/oldsoska/slotly/actions/workflows/ci.yml)

> Status: **Stage 3 — staff dashboard (React).** See [ROADMAP](docs/ROADMAP.md).

## Highlights

- **No double-booking, guaranteed by the database.** A PostgreSQL `EXCLUDE USING gist` constraint on
  `(staff_id, tstzrange(starts_at, ends_at))` rejects overlapping bookings atomically — no race window between
  "check availability" and "insert". Covered by a test that fires 10 concurrent requests at one slot and
  asserts exactly one wins.
- **Free slots in one SQL query.** `GET /api/v1/availability` expands the working day into a 15-minute grid with
  `generate_series` and drops candidates that overlap a booking or time off via `NOT EXISTS`. The booking check
  reuses the `tstzrange &&` predicate of the exclusion constraint, so PostgreSQL serves it from that constraint's
  GiST index (`Index Scan using booking_no_overlap` in `EXPLAIN`).
- **Staff schedules** — weekly working hours in the business's local timezone plus one-off time off; bookings
  outside them are rejected.
- **Public booking page** at `/b/{slug}` — clients pick a service, master and time and book without an account.
  The confirmation link carries a random `cancel_token` (UUID) that lets the client view or cancel that one
  booking. The page is plain HTML/JS served by the backend and talks only to `/api/v1/public/**`.
- **Abuse protection on everything public** — token-bucket rate limits (Bucket4j, buckets in a bounded Caffeine
  cache) per client IP on sign-up, login, booking and cancellation; per email on login; per business on public
  bookings, so rotating IPs cannot flood one business; and at most 3 upcoming bookings per phone number. Limits
  answer `429` with `Retry-After`. The client IP comes from Tomcat's `RemoteIpValve`, which reads
  `X-Forwarded-For` right to left from trusted proxies only, so prepending fake addresses does not help.
- **Multi-tenancy** — every row is scoped by `tenant_id`, and the tenant comes from the signed JWT, never from the
  URL or body, so one business cannot address another's data. Cross-tenant access returns 404 (covered by tests).
- **Stateless JWT auth** on Spring Security's OAuth2 resource server — no hand-written filters. BCrypt passwords,
  roles `OWNER` / `STAFF`, and login that answers identically for an unknown email and a wrong password.
- **Schema-first** — Flyway migrations, Hibernate in `validate` mode, explicit indexes for hot queries.
- **Errors as RFC 9457 Problem Details** (`application/problem+json`).
- **Real integration tests** against PostgreSQL in Testcontainers, not H2.

## Architecture

```mermaid
flowchart LR
    UI[Dashboard SPA<br/>React + TS, /app] -->|REST + JWT| API
    Page[Booking page<br/>/b/slug] -->|public REST| API
    subgraph Backend [Spring Boot - modular monolith]
        API[REST API] --> Tenant[tenant]
        API --> Catalog[catalog<br/>staff, services]
        API --> Schedule[schedule<br/>hours, time off]
        API --> Booking[booking]
        API --> Avail[availability]
        API --> Store[storefront<br/>public booking]
    end
    Backend --> PG[(PostgreSQL)]
    Booking -.->|events, stage 4| Kafka[[Kafka]]
    Kafka -.-> Notifier[notification service<br/>Telegram / email]
    Backend -.->|cache, stage 4| Redis[(Redis)]
```

## Tech stack

Java 21 · Spring Boot 3.5 · Spring Security (JWT) · Spring Data JPA · PostgreSQL 16 · Flyway · Testcontainers · Docker · GitHub Actions ·
OpenAPI (Swagger UI) · Bucket4j.
Frontend: React 19 · TypeScript · Vite · React Router · TanStack Query · Vitest. Planned: Kafka, Redis, Prometheus + Grafana.

## Running locally

Requirements: JDK 21, Maven 3.9+, Docker.

```bash
docker compose up -d postgres
cd backend
mvn spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

Dashboard with hot reload (requires Node 20+), proxied to the backend above:

```bash
cd frontend
npm ci
npm run dev        # http://localhost:5173/app/
```

`npm run build` writes the SPA into `backend/src/main/resources/static/app`, so the jar then serves it at
http://localhost:8080/app/. Frontend tests: `npm test`; type check: `npm run typecheck`.

Run everything in containers: `docker compose --profile app up --build` — the root `Dockerfile` builds the SPA
with Node, packages it into the Spring Boot jar with Maven and runs it on a JRE-only image.

Tests (needs Docker running): `cd backend && mvn verify`.

## Deployment

The app runs on [Render](https://render.com) (Docker web service) with PostgreSQL on [Neon](https://neon.tech).
Infrastructure is described in [`render.yaml`](render.yaml):

- built from `backend/Dockerfile`, deployed automatically once GitHub Actions checks pass on `main`;
- `SPRING_PROFILES_ACTIVE=prod` loads [`application-prod.yml`](backend/src/main/resources/application-prod.yml),
  where secrets have no defaults, so a missing `JWT_SECRET` or database setting stops startup;
- `JWT_SECRET` is generated by Render; `DB_URL`, `DB_USER`, `DB_PASSWORD` are set once in the dashboard;
- heap is capped at 60% of the 512 MB instance (`JAVA_OPTS`), the app uses ~220 MB at idle;
- Render's health check calls `/actuator/health`, which also checks the database connection.

On the free plan the service sleeps after 15 minutes without traffic; the first request then takes about a minute.

## API example

```bash
# sign up a business with its owner; the response carries an access token
curl -X POST localhost:8080/api/v1/auth/register -H "Content-Type: application/json" \
  -d '{"businessName":"Barber Bros","slug":"barber-bros","email":"owner@example.com","password":"s3cret-pass"}'
TOKEN=...   # accessToken from the response (or POST /api/v1/auth/login)

# add a master and a service (OWNER only)
curl -X POST localhost:8080/api/v1/staff -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"fullName":"Ivan Petrov"}'
curl -X POST localhost:8080/api/v1/services -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Haircut","durationMinutes":45,"price":1500}'

# give the master a STAFF login
curl -X POST localhost:8080/api/v1/staff/1/account -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"email":"ivan@example.com","password":"s3cret-pass"}'

# working hours Mon-Fri 10:00-19:00 in the business's timezone; days not listed are days off
curl -X PUT localhost:8080/api/v1/staff/1/working-hours -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '[
    {"dayOfWeek":"MONDAY","start":"10:00","end":"19:00"},
    {"dayOfWeek":"TUESDAY","start":"10:00","end":"19:00"},
    {"dayOfWeek":"WEDNESDAY","start":"10:00","end":"19:00"},
    {"dayOfWeek":"THURSDAY","start":"10:00","end":"19:00"},
    {"dayOfWeek":"FRIDAY","start":"10:00","end":"19:00"}]'

# free start times for the service on a date (optionally &staffId=1)
curl "localhost:8080/api/v1/availability?serviceId=1&date=2026-12-01" -H "Authorization: Bearer $TOKEN"

# book a slot (409 Conflict if it overlaps another booking or falls outside working hours)
curl -X POST localhost:8080/api/v1/bookings -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"staffId":1,"serviceId":1,"clientName":"Anna","clientPhone":"+79990001122","startsAt":"2026-12-01T07:00:00Z"}'

# what a client sees — no token needed
curl localhost:8080/api/v1/public/businesses/barber-bros
curl "localhost:8080/api/v1/public/businesses/barber-bros/availability?serviceId=1&date=2026-12-01"
curl -X POST localhost:8080/api/v1/public/businesses/barber-bros/bookings -H "Content-Type: application/json" \
  -d '{"staffId":1,"serviceId":1,"clientName":"Anna","clientPhone":"+79990001122","startsAt":"2026-12-01T08:00:00Z"}'
# the response holds cancelToken; the client keeps https://.../b/barber-bros?booking=<cancelToken>
curl -X POST localhost:8080/api/v1/public/bookings/<cancelToken>/cancel
```

In Swagger UI press **Authorize** and paste the token to try endpoints from the browser.

## Design decisions

| Decision | Why |
|---|---|
| Modular monolith over microservices | One deployable keeps the domain consistent and transactions simple; modules (`tenant`, `catalog`, `booking`) have clear boundaries and can be extracted later. Only notifications become a separate service, because they are naturally async. |
| Overlap check in the DB, not in Java | A `SELECT … then INSERT` check is racy under concurrency. The exclusion constraint is atomic and cannot be bypassed by another code path. |
| Shared schema with `tenant_id` | Cheapest multi-tenancy model to operate; isolation is enforced in every repository query (`findByIdAndTenantId`). Row-Level Security is on the roadmap as defence in depth. |
| HS256 JWT signed with a shared secret | One service issues and verifies tokens, so a symmetric key is enough and keeps setup simple. Moving to RS256 with a JWKS endpoint (or Keycloak) is a config change in `SecurityConfig` once a second service must verify tokens. |
| Tenant resolved from the token via `CurrentUser` | Controllers cannot forget the check: they never see a tenant id the client chose. |
| `TIMESTAMPTZ` + `Instant` everywhere | Stores absolute moments; the tenant's timezone is applied only for display and working hours. |
| Working hours as local `TIME`, bookings as `TIMESTAMPTZ` | "10:00–19:00" means local time all year round, including across DST changes; it is converted to instants per date with the tenant's zone. |
| Availability computed in SQL rather than in Java | One round trip per staff member instead of loading every booking into memory; the database already has the index that answers "does this range overlap anything". |
| Client access by an unguessable token, not an account | Asking clients to register kills conversion. A UUIDv4 has 122 random bits, so the link is the credential; it grants access to one booking only and is never returned by staff endpoints. |
| Booking page as static HTML/JS for now | Gives a clickable demo today without a frontend build; the React app planned for stage 3 will use the same public API. |
| Several independent limits instead of one per-IP limit | An IP limit alone is beaten by rotating addresses; the per-business and per-phone limits do not depend on the client's address at all. |
| In-memory rate-limit buckets | One instance runs today, so memory is the simplest correct store. With more instances the same `RateLimiter` would keep buckets in Redis (Bucket4j has a Redis backend). |
| Dashboard served by Spring Boot from `/app` | One origin for the SPA and the API: no CORS, cookies or second deployment, and the free Render instance keeps hosting everything. The client-side routes are listed in `SpaController` so a reload of `/app/staff` still returns the app. |
| JWT in `sessionStorage`, not `localStorage` or a cookie | Survives a reload but not closing the tab, and is not shared across tabs. A cookie would need CSRF protection; an `HttpOnly` cookie plus refresh tokens is the next step if sessions must outlive the tab. |
| Server state in TanStack Query, no global store | Almost all state is server data; Query gives caching, refetching and invalidation per resource (`queryKeys`), and local UI state stays in components. |
