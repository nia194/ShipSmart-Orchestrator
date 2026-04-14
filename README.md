# ShipSmart — Spring Boot API (`api-java`)

Core transactional backend for the ShipSmart shipping platform. Owns
shipments, quotes, saved options, and booking redirects. Single writer
to the Supabase Postgres database.

**Stack:** Spring Boot 3.4.4 · Java 17 · Gradle 8.12 · Spring Data JPA · PostgreSQL · Lightweight Supabase JWT

---

## What this service owns

| Domain | Endpoints | Notes |
|---|---|---|
| Shipments | `/api/v1/shipments` | Create / fetch shipment requests. |
| Quotes | `/api/v1/quotes?shipmentRequestId=…` | Generate service quotes for a shipment. Read by Python's recommendation hydration path. |
| Saved options | `/api/v1/saved-options` | Authenticated CRUD on user-saved shipping options. |
| Bookings | `/api/v1/bookings/redirect` | Carrier booking redirect with tracking. |
| Health | `/api/v1/health`, `/actuator/health` | Liveness + Spring Actuator probes. |

This is the **only** service that writes to Postgres. Python reads from
it via internal HTTP for recommendation hydration; it never touches the
DB directly.

---

## Architecture inside this service

```
HTTP request
   │
   ├─► CORS filter
   ├─► JwtAuthFilter   ── validates Supabase HS256 JWT, extracts user_id
   │
   └─► Controller (web)
          │
          ├─► Service (business logic)
          │      │
          │      └─► Repository (Spring Data JPA)
          │              │
          │              └─► Supabase Postgres
          │
          └─► DTO mapping → JSON response
```

### Key packages

| Path | Purpose |
|---|---|
| `com.shipsmart.api.controller` | REST controllers (`Health`, `Shipment`, `Quote`, `SavedOption`, `Booking`). |
| `com.shipsmart.api.service` | Business logic (quote generation, saved option CRUD, booking redirect). |
| `com.shipsmart.api.repository` | Spring Data JPA repositories. |
| `com.shipsmart.api.entity` | JPA entities (`ShipmentRequest`, `Quote`, `SavedOption`, `Booking`). |
| `com.shipsmart.api.dto` | Request/response DTOs. |
| `com.shipsmart.api.auth` | Supabase JWT validation filter. |
| `com.shipsmart.api.config` | Spring Security, CORS, JPA config. |

---

## Running locally

### Prerequisites

- **Java 17+** (LTS). The toolchain is set to 17 in `build.gradle`.
- **Gradle 8.12** via the wrapper (`./gradlew`).

### Configure

```bash
cp .env.example .env
```

Required env vars (see `.env`):

```env
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=local
DATABASE_URL=jdbc:postgresql://db.<project>.supabase.co:5432/postgres?sslmode=require
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=<your supabase db password>
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_ANON_KEY=<anon key>
SUPABASE_SERVICE_ROLE_KEY=<service role key>
SUPABASE_JWT_SECRET=<JWT secret from Supabase dashboard>
CORS_ALLOWED_ORIGINS=http://localhost:5173
INTERNAL_PYTHON_API_URL=http://localhost:8000
```

All five Supabase values come from the Supabase dashboard:

- **Settings → Database** — connection user/password
- **Settings → API** — anon key, service-role key, JWT secret

Without these the service will fail to start (DataSource init fails) or
will reject every authenticated request (JWT signature mismatch).

### Run

```bash
./gradlew bootRun
```

Service comes up on `http://localhost:8080`. Verify:

```bash
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/actuator/health
```

### Build a JAR

```bash
./gradlew clean bootJar
java -jar build/libs/shipsmart-api-java-0.1.0-SNAPSHOT.jar
```

This is the build that Render runs in production (see `render.yaml`).

---

## Auth

Frontend obtains a Supabase access token (HS256) and sends it as
`Authorization: Bearer <token>`. `JwtAuthFilter` validates the
signature using `SUPABASE_JWT_SECRET`, extracts `sub` as the user ID,
and populates `SecurityContextHolder` for downstream controllers.

The Python service reuses this same token when calling Java internally
(e.g., for recommendation hydration), so user-scoped queries continue
to work.

---

## Cross-service contract

| Caller | Endpoint | Used by |
|---|---|---|
| Frontend → Java | `POST /api/v1/quotes` | Quote comparison page |
| Frontend → Java | `GET/POST/DELETE /api/v1/saved-options` | Saved options page |
| Frontend → Java | `POST /api/v1/bookings/redirect` | Booking flow |
| **Python → Java** | `GET /api/v1/quotes?shipmentRequestId=…` | Recommendation hydration when frontend posts only `shipment_request_id` |
| **Python → Java** | `GET /api/v1/saved-options` | Reserved for future advisor enrichment |

When changing any of the contracts above, update both
`ShipSmart-API/app/services/java_client.py` and the frontend's
`ShipSmart-Web/src/lib/*-api.ts` modules.

---

## Tests

```bash
./gradlew test
```

Tests use JUnit 5 and Spring Boot Test. The test profile uses H2
in-memory (see `src/test/resources/application-test.yml` if present).

---

## Operational notes

- **Startup hangs / `Failed to determine driver class`**: `DATABASE_URL` is missing or malformed.
- **All requests 401**: `SUPABASE_JWT_SECRET` is wrong (must match the project's signing secret exactly).
- **CORS blocked from frontend**: add the frontend origin to `CORS_ALLOWED_ORIGINS` (comma-separated).
- **Migrations**: SQL migrations live in `supabase/migrations/` at the repo root and are applied via `supabase db push`. The Java service does **not** run Flyway/Liquibase — schema is owned by Supabase migrations.
