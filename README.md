# ShipSmart Orchestrator — Spring Boot API

Core transactional backend for the ShipSmart shipping platform. Owns
quotes, saved options, booking redirects, and shipment requests.
Single writer to the Supabase Postgres database.

**Stack:** Spring Boot 3.4.4 · Java 17 · Gradle 8.12 · Spring Data JPA · PostgreSQL · Flyway (validate-mode) · Caffeine cache · Bucket4j rate limiting · Spring Security · Spring AOP · Micrometer + OpenTelemetry · SpringDoc OpenAPI · Supabase JWT (HS256)


---

## What this service owns

| Domain | Endpoints | Notes |
|---|---|---|
| Quotes | `POST /api/v1/quotes` | Generate shipping quotes for a shipment request. |
| Quotes (hydration) | `GET /api/v1/quotes?shipmentRequestId=…` | Re-generate quotes for an existing shipment. Used by the Python service for recommendation hydration. |
| Saved options | `GET/POST/DELETE /api/v1/saved-options` | Authenticated CRUD on user-saved shipping options. |
| Saved option analytics | `GET /api/v1/saved-options/analytics` | Authenticated — per-user groupings (carriers, tiers, top-N priced, saves-per-month, route frequency buckets). |
| Bookings | `POST /api/v1/bookings/redirect` | Carrier booking redirect with tracking. Requires `Idempotency-Key` header. |
| Shipments | `GET/POST/PATCH/DELETE /api/v1/shipments` | Authenticated CRUD on user shipments. POST requires `Idempotency-Key`; PATCH enforces `If-Match` (ETag/`version`) optimistic concurrency; DELETE is soft-delete. List supports `status` + `createdAfter` filters and pagination. |
| Provider inventory | `GET /api/v1/providers` | Authenticated — registered quote providers with priority + enabled flag. |
| Provider metrics | `GET /api/v1/providers/metrics`, `/metrics/{carrier}/recent` | Authenticated — per-carrier counters (`SUCCESS`/`TIMEOUT`/`ERROR`/`DISABLED`) + last-N call events. |
| Health | `GET /health`, `GET /api/v1/health`, `/actuator/health` | Root-level + prefixed liveness + Spring Actuator probes. |
| Actuator | `/actuator/info`, `/actuator/metrics`, `/actuator/caches`, `/actuator/prometheus` | Operational telemetry; Prometheus scrape endpoint exposed for ops. |
| API docs | `/swagger-ui.html`, `/v3/api-docs` | SpringDoc-generated OpenAPI 3 spec + Swagger UI. |

This is the **only** service that writes to Postgres. The Python service reads
from it via internal HTTP for recommendation hydration; it never touches the
DB directly.

---

## Architecture inside this service

```
HTTP request
   │
   ├─► CORS filter
   ├─► CorrelationIdFilter   ── assigns/echoes X-Request-Id, populates MDC (requestId, traceId, userId)
   ├─► BodyCachingFilter     ── buffers request body so idempotency hashing + downstream reads coexist
   ├─► RateLimitFilter       ── per-IP Bucket4j limits on /shipments, /quotes, /bookings (429 on overflow)
   ├─► JwtAuthFilter         ── validates Supabase HS256 JWT, extracts user_id into SecurityContext
   ├─► IdempotencyInterceptor── on @Idempotent endpoints, replays cached response for repeat Idempotency-Key
   │
   └─► Controller (web)
          │
          ├─► Service (business logic)             ◄── @Audited methods intercepted by AuditAspect (AOP),
          │      │                                     async write to audit_log via the `audit` executor pool
          │      ├─► QuoteProvider fanout (parallel, via `quote-provider` executor pool)
          │      │      └─► FedExProvider, mocks, …
          │      │
          │      └─► Repository (Spring Data JPA)
          │              │
          │              └─► Supabase Postgres
          │
          └─► DTO mapping → JSON response (ETag on shipment reads/writes)
```

### Key packages

| Path | Purpose |
|---|---|
| `com.shipsmart.api.controller` | REST controllers (`Health`, `RootHealth`, `Shipment`, `Quote`, `SavedOption`, `SavedOptionAnalytics`, `Booking`, `ProviderMetrics`). |
| `com.shipsmart.api.service` | Business logic — `ShipmentService`, `QuoteService`, `QuoteFanoutService`, `SavedOptionService`, `SavedOptionAnalyticsService`, `BookingService`. |
| `com.shipsmart.api.service.provider` | Legacy carrier integrations — `ShippingProvider` interface with `FedExProvider` implementation (FedEx Rate API v1, OAuth2 token management). |
| `com.shipsmart.api.provider` | Strategy-based quote fanout: `QuoteProvider` interface (default-method `priority()`), `AbstractQuoteProvider` template, `QuoteProviderRegistry` (priority-sorted), `QuoteComparators` / `QuoteSortOption`, `FedExQuoteProviderAdapter`. |
| `com.shipsmart.api.provider.metrics` | Per-carrier call metrics — `ProviderCallOutcome` enum (with per-constant behavior), `ProviderCallEvent` record, `ProviderMetrics` (`EnumMap` counters + `ArrayDeque` ring buffer per carrier). |
| `com.shipsmart.api.cache` | In-memory LRU quote cache — `QuoteCacheKey` value object (`Comparable`, `equals`/`hashCode`), `QuoteCache` (`LinkedHashMap` access-order LRU + `ConcurrentHashMap` stats + `TreeMap` sorted view). Coexists with the Spring `CacheManager` (Caffeine) used for `quotesByShipmentId` / `shipmentById`. |
| `com.shipsmart.api.money` | `Money` value type — immutable, `Comparable`, flyweight cache of common whole-dollar values. |
| `com.shipsmart.api.repository` | Spring Data JPA repositories — `ShipmentRequestRepository` (+ `ShipmentRequestSpecifications` for filterable list), `SavedOptionRepository`, `RedirectTrackingRepository`, `IdempotencyKeyRepository`. |
| `com.shipsmart.api.domain` | JPA entities — `BaseEntity` (audit columns + `@Version`), `ShipmentRequest`, `ShipmentStatus` enum, `SavedOption`, `RedirectTracking`, `IdempotencyKey`, `AuditLog`. |
| `com.shipsmart.api.dto` | Request/response DTOs (`CreateShipmentRequest`, `PatchShipmentRequest`, `ShipmentSummaryDto`, quote/booking/saved-option DTOs, `BreakdownDto`, etc.). |
| `com.shipsmart.api.auth` | Supabase JWT validation filter (`JwtAuthFilter`, `SupabaseJwtVerifier`, `AuthHelper`). |
| `com.shipsmart.api.web` | Cross-cutting filters/interceptors — `CorrelationIdFilter` (request-id MDC), `RateLimitFilter` (Bucket4j per-IP), `BodyCachingFilter` + `CachedBodyRequestWrapper` (re-readable request body), `IdempotencyInterceptor` + `@Idempotent` annotation + `IdempotencyCleanupJob` (scheduled TTL sweep). |
| `com.shipsmart.api.audit` | AOP-based audit trail — `@Audited` annotation, `AuditAspect`, `AuditLogRepository`. Async writes to `audit_log` via the dedicated `audit` executor pool. |
| `com.shipsmart.api.startup` | `FlywayValidationRunner` — boot-time guard that refuses to start on pending migrations and logs schema state. |
| `com.shipsmart.api.config` | `SecurityConfig`, `AppConfig`, `WebMvcConfig`, `ExecutorConfig` (quote-provider + audit thread pools, MDC-aware), `OpenApiConfig`, `EnvLoader`. |
| `com.shipsmart.api.exception` | Global exception handler + typed exceptions (`ResourceNotFoundException`, `ResourceConflictException`, `OwnershipException`, `IdempotencyConflictException`, `RateLimitExceededException`). |

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

# ShipSmart-MCP (tool server) — wired for upcoming AI features. Empty URL = off.
SHIPSMART_MCP_URL=http://localhost:8001
SHIPSMART_MCP_API_KEY=

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

### Optional tuning

These have sensible defaults — override via env / `application.yml` if you need to.

| Property | Default | Purpose |
|---|---|---|
| `shipsmart.quote-cache.max-entries` | `256` | LRU cap on cached fanout responses (legacy in-memory `QuoteCache`). |
| `shipsmart.quote-cache.ttl-seconds` | `120` | Cached-response freshness window (legacy `QuoteCache`). |
| `shipsmart.provider-metrics.recent-events` | `50` | Ring-buffer size for `GET /api/v1/providers/metrics/{carrier}/recent`. |
| `shipsmart.cache.quotes-ttl` | `PT10M` | Caffeine TTL for `quotesByShipmentId` (Spring `CacheManager`). |
| `shipsmart.cache.shipment-ttl` | `PT2M` | Caffeine TTL for `shipmentById`. |
| `shipsmart.rate-limit.enabled` | `true` | Master switch for the Bucket4j per-IP rate limiter. |
| `shipsmart.rate-limit.shipments-per-minute` | `20` | Per-IP cap for `/api/v1/shipments` writes. |
| `shipsmart.rate-limit.quotes-per-minute` | `30` | Per-IP cap for `/api/v1/quotes`. |
| `shipsmart.rate-limit.bookings-per-minute` | `10` | Per-IP cap for `/api/v1/bookings/redirect`. |
| `shipsmart.idempotency.enabled` | `true` | Honour `Idempotency-Key` on `@Idempotent` endpoints (POST /shipments, POST /bookings/redirect). |
| `shipsmart.idempotency.ttl-hours` | `24` | Retention for stored idempotency responses; `IdempotencyCleanupJob` sweeps expired rows. |
| `shipsmart.executor.quote-provider.{core-pool-size,max-pool-size,queue-capacity}` | `4 / 8 / 100` | Thread pool used to fan provider quotes out in parallel. |
| `shipsmart.executor.audit.{core-pool-size,max-pool-size,queue-capacity}` | `2 / 2 / 500` | Thread pool used by `AuditAspect` for async `audit_log` writes. |
| `SPRING_FLYWAY_ENABLED` | `true` | Toggle Flyway (validate-mode) at boot. Disable only for ad-hoc local runs against a non-Postgres DB. |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | `0.0` | OpenTelemetry trace sampling rate. Bump to `1.0` for full sampling in dev. |
| `MANAGEMENT_OTLP_TRACING_ENDPOINT` | *(unset)* | OTLP collector endpoint; leave unset to keep the exporter off. |


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
| Frontend → Java | `GET/POST/PATCH/DELETE /api/v1/shipments` | Shipment dashboard. POST sends `Idempotency-Key`; PATCH sends `If-Match` from the prior `ETag`. |
| Frontend → Java | `POST /api/v1/quotes` | Quote comparison page |
| Frontend → Java | `GET/POST/DELETE /api/v1/saved-options` | Saved options page |
| Frontend → Java | `GET /api/v1/saved-options/analytics` | Saved-options analytics widgets (per-user groupings, top-priced, route-frequency buckets). |
| Frontend → Java | `POST /api/v1/bookings/redirect` | Booking flow |
| Ops → Java | `GET /api/v1/providers`, `/api/v1/providers/metrics`, `/metrics/{carrier}/recent` | Carrier fanout observability — priority, enabled flag, per-outcome counters, last-N events. |
| **Python → Java** | `GET /api/v1/quotes?shipmentRequestId=…` | Recommendation hydration when frontend posts only `shipment_request_id` |
| **Python → Java** | `GET /api/v1/saved-options` | Reserved for future advisor enrichment |
| **Java → MCP** | `POST /tools/list`, `POST /tools/call` | Reserved for upcoming AI-assist features. Config is wired via `shipsmart.mcp.base-url` / `SHIPSMART_MCP_URL`; no runtime call sites yet. See [`docs/mcp-integration.md`](docs/mcp-integration.md). |

When changing any of the contracts above, update both
`ShipSmart-API/app/services/java_client.py` and the frontend's
`ShipSmart-Web/src/lib/*-api.ts` modules. For the MCP contract, the
source of truth lives in the **ShipSmart-MCP** repo.

---

## Tests

```bash
./gradlew test
```


69 tests across 14 files — JUnit 5 with Spring Boot Test, plus
Awaitility for async assertions. Most tests use H2 in-memory with
PostgreSQL compatibility mode; repository integration tests
(e.g. `ShipmentRequestRepositoryIT`) run against real Postgres via
**Testcontainers** (`spring-boot-testcontainers`,
`testcontainers:postgresql`), so Docker must be available locally
to run the full suite.


---

## Operational notes

- **Startup hangs / `Failed to determine driver class`**: `DATABASE_URL` is missing or malformed.
- **All requests 401**: `SUPABASE_JWT_SECRET` is wrong (must match the project's signing secret exactly).
- **CORS blocked from frontend**: add the frontend origin to `CORS_ALLOWED_ORIGINS` (comma-separated).

- **FedEx quotes empty**: check `FEDEX_CLIENT_ID`, `FEDEX_CLIENT_SECRET`, and `FEDEX_ACCOUNT_NUMBER` are set. Verify `FEDEX_BASE_URL` points to the correct environment (sandbox vs production).
- **Quote cache**: identical `(origin, destination, dropOffDate, expectedDeliveryDate, weight-bucket-kg, items)` tuples served within `shipsmart.quote-cache.ttl-seconds` hit the in-process LRU and skip the carrier fanout entirely. Clear it by restarting the service; the cache is not distributed.
- **Provider priority**: `QuoteProvider#priority()` (lower = earlier) controls fanout order. FedEx overrides to `10` so real-time carrier calls dispatch before mocks. Startup logs the full list: `carrier=ENABLED@p<priority>`.
- **Migrations**: Supabase remains the source of truth — migrations live in `ShipSmart-Infra/supabase/migrations/` and are applied via `supabase db push`. The Java service ships a **mirror** of those migrations under `src/main/resources/db/migration/` (`V1__baseline.sql`, `V2__interview_upgrade.sql`) and runs Flyway in **validate mode** (`spring.flyway.validate-on-migrate=true`, `baseline-on-migrate=true`). `FlywayValidationRunner` makes any pending migration **fatal at boot** and logs applied/total counts. Disable with `SPRING_FLYWAY_ENABLED=false` if running against a non-Postgres dev DB.
- **Idempotency**: `POST /api/v1/shipments` and `POST /api/v1/bookings/redirect` require an `Idempotency-Key` request header. The first response is persisted to `idempotency_keys` and replayed verbatim for repeats within `shipsmart.idempotency.ttl-hours`. `IdempotencyCleanupJob` sweeps expired rows on a schedule.
- **Optimistic concurrency**: shipment reads/writes return an `ETag` derived from the JPA `@Version` column. `PATCH /api/v1/shipments/{id}` honours `If-Match`; a stale value yields 409.
- **Rate limiting**: Bucket4j per-IP, in-memory. Defaults: 20/min shipments, 30/min quotes, 10/min bookings (configurable). Overflow → 429 with a `RateLimitExceededException` body. Toggle off with `SHIPSMART_RATE_LIMIT_ENABLED=false`.
- **Correlation IDs**: every request gets/echoes `X-Request-Id` and surfaces in the log pattern as `[requestId] [traceId] [userId]`. The `quote-provider` and `audit` executors are MDC-aware so async work keeps the same context.
- **Audit log**: methods annotated `@Audited` are intercepted by `AuditAspect` and written to the `audit_log` table asynchronously through the dedicated `audit` thread pool. Failures here never propagate to the request.
- **Observability**: Prometheus scrape at `/actuator/prometheus`; OpenTelemetry tracing is wired via Micrometer. The OTLP exporter is **off by default** (`MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0.0`); flip the sampling probability and set `MANAGEMENT_OTLP_TRACING_ENDPOINT` to send spans to a collector.
- **OpenAPI**: SpringDoc serves the spec at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.
