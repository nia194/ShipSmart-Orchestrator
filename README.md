# ShipSmart Orchestrator — Spring Boot API

Core transactional backend for the ShipSmart shipping platform. Owns
quotes, saved options, booking redirects, and shipment requests.
Single writer to the Supabase Postgres database.

**Stack:** Spring Boot 3.4.4 · Java 17 · Gradle 8.12 · Spring Data JPA · PostgreSQL · Supabase JWT (HS256)


---

## What this service owns

| Domain | Endpoints | Notes |
|---|---|---|
| Quotes | `POST /api/v1/quotes` | Generate shipping quotes for a shipment request. |
| Quotes (hydration) | `GET /api/v1/quotes?shipmentRequestId=…` | Re-generate quotes for an existing shipment. Used by the Python service for recommendation hydration. |
| Saved options | `GET/POST/DELETE /api/v1/saved-options` | Authenticated CRUD on user-saved shipping options. |
| Bookings | `POST /api/v1/bookings/redirect` | Carrier booking redirect with tracking. |
| Shipments | `/api/v1/shipments` | Create / list / get shipment requests. *(stub — not yet implemented)* |
| Health | `GET /api/v1/health`, `/actuator/health` | Liveness + Spring Actuator probes. |

This is the **only** service that writes to Postgres. The Python service reads
from it via internal HTTP for recommendation hydration; it never touches the
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
          │      ├─► ShippingProvider (carrier integration, e.g. FedExProvider)
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
| `com.shipsmart.api.service.provider` | Carrier integrations — `ShippingProvider` interface with `FedExProvider` implementation (FedEx Rate API v1, OAuth2 token management). |
| `com.shipsmart.api.repository` | Spring Data JPA repositories. |
| `com.shipsmart.api.domain` | JPA entities (`ShipmentRequest`, `SavedOption`, `RedirectTracking`). |
| `com.shipsmart.api.dto` | Request/response DTOs. |
| `com.shipsmart.api.auth` | Supabase JWT validation filter (`JwtAuthFilter`, `SupabaseJwtVerifier`, `AuthHelper`). |
| `com.shipsmart.api.config` | Spring Security, CORS, app config, env loading. |
| `com.shipsmart.api.exception` | Global exception handler. |

---

## Running locally

### Prerequisites

- **Java 17+** (LTS). The toolchain is set to 17 in `build.gradle`.
- **Gradle 8.12** via the wrapper (`./gradlew`).

### Configure

```bash
cp .env.example .env
```


Required env vars (see `.env.example`):

```env
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=local
REQUIRE_JWT_SECRET=false

# Database (Supabase PostgreSQL)
DATABASE_URL=jdbc:postgresql://db.<project>.supabase.co:5432/postgres?sslmode=require
DATABASE_USERNAME=<your supabase db user>
DATABASE_PASSWORD=<your supabase db password>

# Supabase
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_SERVICE_ROLE_KEY=<service role key>
SUPABASE_JWT_SECRET=<JWT secret from Supabase dashboard>

# CORS & inter-service
CORS_ALLOWED_ORIGINS=http://localhost:5173
INTERNAL_PYTHON_API_URL=http://localhost:8000

# FedEx API
FEDEX_BASE_URL=https://apis.fedex.com
FEDEX_CLIENT_ID=<your fedex client id>
FEDEX_CLIENT_SECRET=<your fedex client secret>
FEDEX_ACCOUNT_NUMBER=<your fedex account number>
```

Supabase values come from the Supabase dashboard:

- **Settings → Database** — connection user/password
- **Settings → API** — service-role key, JWT secret

FedEx values come from the [FedEx Developer Portal](https://developer.fedex.com/).

Without the database variables the service will fail to start. Without
`SUPABASE_JWT_SECRET`, authenticated requests will be rejected.


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

=======
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


=======
## Deployment

Deployed to **Render** via `render.yaml`. The production profile
(`application-production.yml`) enforces `REQUIRE_JWT_SECRET=true` and
tightens logging.

```bash
# Build command (Render)
./gradlew build -x test

# Start command (Render)
java -jar build/libs/shipsmart-api-java-0.1.0-SNAPSHOT.jar
```

Set all secrets (database, Supabase, FedEx) in the Render dashboard —
they are marked `sync: false` in `render.yaml` and must never be committed.

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


=======
36 tests — JUnit 5 with Spring Boot Test. The test profile
(`application-test.yml`) uses H2 in-memory with PostgreSQL
compatibility mode.


---

## Operational notes

- **Startup hangs / `Failed to determine driver class`**: `DATABASE_URL` is missing or malformed.
- **All requests 401**: `SUPABASE_JWT_SECRET` is wrong (must match the project's signing secret exactly).
- **CORS blocked from frontend**: add the frontend origin to `CORS_ALLOWED_ORIGINS` (comma-separated).

=======
- **FedEx quotes empty**: check `FEDEX_CLIENT_ID`, `FEDEX_CLIENT_SECRET`, and `FEDEX_ACCOUNT_NUMBER` are set. Verify `FEDEX_BASE_URL` points to the correct environment (sandbox vs production).
- **Migrations**: SQL migrations live in `supabase/migrations/` at the repo root and are applied via `supabase db push`. The Java service does **not** run Flyway/Liquibase — schema is owned by Supabase migrations.
