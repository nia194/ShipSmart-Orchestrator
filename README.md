# ShipSmart — Orchestrator (Java / Spring Boot API)

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot) [![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/) [![Gradle](https://img.shields.io/badge/Gradle-8.12-02303A?logo=gradle&logoColor=white)](https://gradle.org/) [![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Supabase-336791?logo=postgresql&logoColor=white)](https://supabase.com/) [![Flyway](https://img.shields.io/badge/Flyway-Validate%20Mode-CC0200?logo=flyway&logoColor=white)](https://flywaydb.org/) [![Deploy: Render](https://img.shields.io/badge/Deploy-Render-46E3B7?logo=render&logoColor=white)](https://render.com/) [![License](https://img.shields.io/badge/License-See%20LICENSE-blue)](/nia194/ShipSmart-Orchestrator/blob/main/LICENSE)

Core transactional backend for the ShipSmart shipping platform. Owns quotes, saved options, booking redirects, and shipment requests. **Single writer to the Supabase Postgres database** — every other service reads through this one.

**Stack:** Spring Boot 3.4.4 · Java 17 · Gradle 8.12 · Spring Data JPA · PostgreSQL · Flyway (validate-mode) · Caffeine cache · Bucket4j rate limiting · Spring Security · Spring AOP · Micrometer + OpenTelemetry · SpringDoc OpenAPI · Supabase JWT (HS256)

---

## Table of contents

- [The ShipSmart ecosystem](#the-shipsmart-ecosystem)
- [What this service owns](#what-this-service-owns)
- [Architecture inside this service](#architecture-inside-this-service)
- [Key packages](#key-packages)
- [Running locally](#running-locally)
- [Configuration reference](#configuration-reference)
- [Auth](#auth)
- [Deployment (Render)](#deployment-render)
- [Cross-service contract](#cross-service-contract)
- [Tests](#tests)
- [Operational notes](#operational-notes)
- [License](#license)

---

## The ShipSmart ecosystem

This service is one of six sibling repositories. Clone them as siblings of this directory when working on the full system. All six are also mirrored together in **[ShipSmart](https://github.com/nia194/ShipSmart)** — the umbrella repository that snapshots each component at its latest stable milestone.

| Repo | Role | Stack |
|------|------|-------|
| [ShipSmart-Web](https://github.com/nia194/ShipSmart-Web) | React SPA — user-facing UI | React 19, Vite, TypeScript |
| **[ShipSmart-Orchestrator](https://github.com/nia194/ShipSmart-Orchestrator)** _(this repo)_ | Java transactional API — **single writer** to Supabase Postgres; quotes, bookings, saved options, carrier integration | Spring Boot 3.4, Java 17 |
| [ShipSmart-API](https://github.com/nia194/ShipSmart-API) | Python AI/orchestration service — RAG, advisors, recommendations, compliance (UC2), multi-agent workflow (UC3/UC4) | FastAPI, Python 3.13 |
| [ShipSmart-MCP](https://github.com/nia194/ShipSmart-MCP) | MCP tool server — `validate_address`, `get_quote_preview` (provider-pluggable) | FastAPI + MCP |
| [ShipSmart-Infra](https://github.com/nia194/ShipSmart-Infra) | Supabase migrations + edge functions, deployment configs, docs | Supabase, Render blueprints |
| [ShipSmart-Test](https://github.com/nia194/ShipSmart-Test) | Cross-repo integration harness — contract + live e2e suites, cross-service Postman collection | Python 3.13, pytest |

```
            ┌──────────────────────────────┐
            │       ShipSmart-Web          │
            │       React SPA · Vite       │
            └──────────────┬───────────────┘
                           │  Authorization: Bearer <Supabase JWT>
              ┌────────────┴────────────┐
              ▼                         ▼
┌──────────────────────────────┐   ┌──────────────────────────────┐
│  ShipSmart-Orchestrator      │◀──│        ShipSmart-API         │
│  (this repo)                 │   │       Python / FastAPI       │
│  Java / Spring Boot          │   │   RAG · advisors · recs      │
│  Sole writer to Postgres     │   │   Forwards JWT to Java for   │
│  Carrier integration (FedEx) │   │   quote hydration            │
└──────────────┬───────────────┘   └──────────────┬───────────────┘
               │                                  │
               │              ┌───────────────────┘
               │              ▼
               │   ┌──────────────────────────────┐
               │   │        ShipSmart-MCP         │
               │   │   shipping tools (HTTP/MCP)  │
               │   │   validate_address, quotes   │
               │   └──────────────────────────────┘
               ▼
┌──────────────────────────────┐
│   Supabase Postgres + Auth   │
└──────────────────────────────┘
```

**This service is the only writer to Postgres.** The Python service reads from it over internal HTTP for recommendation hydration; it never touches the database directly. Migrations live in [ShipSmart-Infra](https://github.com/nia194/ShipSmart-Infra) and are mirrored here for Flyway validation at boot.

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

### Cross-cutting concerns at a glance

| Concern | Mechanism | Where it lives |
|---|---|---|
| Authentication | Supabase HS256 JWT verified per request | `JwtAuthFilter`, `SupabaseJwtVerifier` |
| Correlation / tracing | `X-Request-Id` + W3C `traceparent`; MDC-aware executors | `CorrelationIdFilter`, `ExecutorConfig` |
| Rate limiting | Bucket4j per-IP buckets, in-memory | `RateLimitFilter` |
| Idempotency | `Idempotency-Key` replay via `idempotency_keys` table | `IdempotencyInterceptor`, `@Idempotent`, `IdempotencyCleanupJob` |
| Optimistic concurrency | JPA `@Version` exposed as `ETag` / `If-Match` | `BaseEntity`, shipment controller/service |
| Audit trail | AOP `@Audited` → async write to `audit_log` | `AuditAspect`, `audit` executor pool |
| Carrier fanout | Priority-sorted provider registry, parallel calls | `QuoteProviderRegistry`, `quote-provider` executor pool |
| Observability | Micrometer + OpenTelemetry; Prometheus scrape | `/actuator/prometheus`, `MANAGEMENT_OTLP_TRACING_ENDPOINT` |
| Schema safety | Flyway in validate mode; pending migrations are fatal at boot | `FlywayValidationRunner` |

---

## Key packages

| Path | Purpose |
|---|---|
| `com.shipsmart.api.controller` | REST controllers (`Health`, `RootHealth`, `Shipment`, `Quote`, `SavedOption`, `SavedOptionAnalytics`, `Booking`, `ProviderMetrics`). |
| `com.shipsmart.api.service` | Business logic — `ShipmentService`, `QuoteService`, `QuoteFanoutService`, `SavedOptionService`, `SavedOptionAnalyticsService`, `BookingService`. |
| `com.shipsmart.api.service.provider` | Legacy carrier integrations — `ShippingProvider` interface with `FedExProvider` implementation (FedEx Rate API v1, OAuth2 token management). |
| `com.shipsmart.api.provider` | Strategy-based quote fanout: `QuoteProvider` interface (default-method `priority()`), `AbstractQuoteProvider` template, `QuoteProviderRegistry` (priority-sorted), `QuoteComparators` / `QuoteSortOption`, `FedExQuoteProviderAdapter`. |
| `com.shipsmart.api.provider.metrics` | Per-carrier call metrics — `ProviderCallOutcome` enum (with per-constant behavior), `ProviderCallEvent` record, `ProviderMetrics` (`EnumMap` counters + `ArrayDeque` ring buffer per carrier). |
| `com.shipsmart.api.cache` | In-memory LRU quote cache — `QuoteCacheKey` value object (`Comparable`, `equals`/`hashCode`), `QuoteCache` (`LinkedHashMap` access-order LRU + `ConcurrentHashMap` stats + `TreeMap` sorted view). Coexists with the Spring `CacheManager` (Caffeine) used for `quotesByShipmentId` / `shipmentById`. |
| `com.shipsmart.api.repository` | Spring Data JPA repositories — `ShipmentRequestRepository` (+ `ShipmentRequestSpecifications` for filterable list), `SavedOptionRepository`, `RedirectTrackingRepository`, `IdempotencyKeyRepository`. |
| `com.shipsmart.api.domain` | JPA entities — `BaseEntity` (audit columns + `@Version`), `ShipmentRequest`, `ShipmentStatus` enum, `SavedOption`, `RedirectTracking`, `IdempotencyKey`, `AuditLog`. |
| `com.shipsmart.api.dto` | Request/response DTOs (`CreateShipmentRequest`, `PatchShipmentRequest`, `ShipmentSummaryDto`, quote/booking/saved-option DTOs, `BreakdownDto`, etc.). |
| `com.shipsmart.api.auth` | Supabase JWT validation filter (`JwtAuthFilter`, `SupabaseJwtVerifier`, `AuthHelper`). |
| `com.shipsmart.api.web` | Cross-cutting filters/interceptors — `CorrelationIdFilter` (request-id MDC), `RateLimitFilter` (Bucket4j per-IP), `BodyCachingFilter` + `CachedBodyRequestWrapper` (re-readable request body), `IdempotencyInterceptor` + `@Idempotent` annotation + `IdempotencyCleanupJob` (scheduled TTL sweep). |
| `com.shipsmart.api.audit` | AOP-based audit trail — `@Audited` annotation, `AuditAspect`, `AuditLogRepository`. Async writes to `audit_log` via the dedicated `audit` executor pool. |
| `com.shipsmart.api.startup` | `FlywayValidationRunner` — boot-time guard that refuses to start on pending migrations and logs schema state; warns and skips (instead of failing boot) when Flyway is disabled. |
| `com.shipsmart.api.config` | `SecurityConfig`, `AppConfig`, `WebMvcConfig`, `ExecutorConfig` (quote-provider + audit thread pools, MDC-aware), `OpenApiConfig`, `EnvLoader`. |
| `com.shipsmart.api.exception` | Global exception handler + typed exceptions (`ResourceNotFoundException`, `ResourceConflictException`, `OwnershipException`, `IdempotencyConflictException`, `RateLimitExceededException`). |

---

## Running locally

### Prerequisites

- **Java 17+** (LTS). The toolchain is set to 17 in `build.gradle`.
- **Gradle 8.12** via the wrapper (`./gradlew`) — no host install required.
- **Docker** (optional, but required to run the Testcontainers-backed repository integration tests).

### Configure

```bash
cp .env.example .env
```

Required environment variables (see [`.env.example`](./.env.example)):

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

FedEx credentials come from the [FedEx Developer Portal](https://developer.fedex.com/).

> **Heads-up:** without the database variables the service will fail to start. Without `SUPABASE_JWT_SECRET`, every authenticated request will be rejected with `401`.

### Run

```bash
./gradlew bootRun
```

Service comes up on `http://localhost:8080`. Verify:

```bash
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/actuator/health
```

Browse the live OpenAPI spec at `http://localhost:8080/swagger-ui.html`.

### Build a JAR

```bash
./gradlew clean bootJar
java -jar build/libs/shipsmart-api-java-0.1.0-SNAPSHOT.jar
```

---

## Configuration reference

These have sensible defaults — override via env or `application.yml` only when you need to.

| Property | Default | Purpose |
|---|---|---|
| `shipsmart.quote-cache.max-entries` | `256` | LRU cap on cached fanout responses (legacy in-memory `QuoteCache`). |
| `shipsmart.quote-cache.ttl-seconds` | `120` | Cached-response freshness window (legacy `QuoteCache`). |
| `shipsmart.provider-metrics.recent-events` | `50` | Ring-buffer size for `GET /api/v1/providers/metrics/{carrier}/recent`. |
| `shipsmart.rate-limit.enabled` | `true` | Master switch for the Bucket4j per-IP rate limiter. |
| `shipsmart.rate-limit.shipments-per-minute` | `20` | Per-IP cap for `/api/v1/shipments` writes. |
| `shipsmart.rate-limit.quotes-per-minute` | `30` | Per-IP cap for `/api/v1/quotes`. |
| `shipsmart.rate-limit.bookings-per-minute` | `10` | Per-IP cap for `/api/v1/bookings/redirect`. |
| `shipsmart.idempotency.enabled` | `true` | Honour `Idempotency-Key` on `@Idempotent` endpoints (POST `/shipments`, POST `/bookings/redirect`). |
| `shipsmart.idempotency.ttl-hours` | `24` | Retention for stored idempotency responses; `IdempotencyCleanupJob` sweeps expired rows. |
| `shipsmart.executor.quote-provider.{core-pool-size,max-pool-size,queue-capacity}` | `4 / 8 / 100` | Thread pool used to fan provider quotes out in parallel. |
| `shipsmart.executor.audit.{core-pool-size,max-pool-size,queue-capacity}` | `2 / 2 / 500` | Thread pool used by `AuditAspect` for async `audit_log` writes. |
| `SPRING_FLYWAY_ENABLED` | `true` | Toggle Flyway (validate-mode) at boot. Disable only for ad-hoc local runs against a non-Postgres DB — the validation runner then warns and skips instead of failing to boot. |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | `0.0` | OpenTelemetry trace sampling rate. Bump to `1.0` for full sampling in dev. |
| `MANAGEMENT_OTLP_TRACING_ENDPOINT` | *(unset)* | OTLP collector endpoint; leave unset to keep the exporter off. |

---

## Auth

The frontend obtains a Supabase access token (HS256) and sends it as `Authorization: Bearer <token>`. `JwtAuthFilter` validates the signature using `SUPABASE_JWT_SECRET`, extracts `sub` as the user ID, and populates `SecurityContextHolder` for downstream controllers.

The Python service **reuses the same token** when calling Java internally (e.g., for recommendation hydration), so user-scoped queries continue to work without re-issuing credentials. There is no separate service-to-service token.

Endpoints under `/actuator/**`, `/health`, `/api/v1/health`, `/swagger-ui/**`, and `/v3/api-docs/**` are public; everything else is JWT-gated.

---

## Deployment (Render)

Deployed to **Render** via [`render.yaml`](./render.yaml). The production profile (`application-production.yml`) enforces `REQUIRE_JWT_SECRET=true` and tightens logging.

```bash
# Build command (Render)
./gradlew build -x test

# Start command (Render)
java -jar build/libs/shipsmart-api-java-0.1.0-SNAPSHOT.jar
```

Set all secrets (database, Supabase, FedEx) in the Render dashboard — they are marked `sync: false` in `render.yaml` and must never be committed.

The companion blueprints for the other services live in [ShipSmart-Infra](https://github.com/nia194/ShipSmart-Infra); deploy them together when promoting a release.

---

## Cross-service contract

| Caller | Endpoint | Used by |
|---|---|---|
| Frontend → Java | `GET/POST/PATCH/DELETE /api/v1/shipments` | Shipment dashboard. POST sends `Idempotency-Key`; PATCH sends `If-Match` from the prior `ETag`. |
| Frontend → Java | `POST /api/v1/quotes` | Quote comparison page. |
| Frontend → Java | `GET/POST/DELETE /api/v1/saved-options` | Saved options page. |
| Frontend → Java | `GET /api/v1/saved-options/analytics` | Saved-options analytics widgets (per-user groupings, top-priced, route-frequency buckets). |
| Frontend → Java | `POST /api/v1/bookings/redirect` | Booking flow. |
| Ops → Java | `GET /api/v1/providers`, `/api/v1/providers/metrics`, `/metrics/{carrier}/recent` | Carrier fanout observability — priority, enabled flag, per-outcome counters, last-N events. |
| **Python → Java** | `GET /api/v1/quotes?shipmentRequestId=…` | Recommendation hydration when the frontend posts only `shipment_request_id`. |
| **Python → Java** | `GET /api/v1/saved-options` | Reserved for future advisor enrichment. |
| **Java → MCP** | `POST /tools/list`, `POST /tools/call` | Reserved for upcoming AI-assist features. Wired via `shipsmart.mcp.base-url` / `SHIPSMART_MCP_URL`; no runtime call sites yet. See [`docs/mcp-integration.md`](docs/mcp-integration.md). |

When changing any of the contracts above, update them in lockstep:

- **Java DTOs** ↔ **Python client** in `ShipSmart-API/app/services/java_client.py`
- **Java DTOs** ↔ **Frontend types** in `ShipSmart-Web/src/lib/*-api.ts` and `ShipSmart-Web/src/shared/types/`
- **MCP contract** — source of truth lives in the [ShipSmart-MCP](https://github.com/nia194/ShipSmart-MCP) repo

---

## Tests

```bash
./gradlew test
```

**88 tests across 18 classes** — JUnit 5 with Spring Boot Test (81 run, 7 skip). Most tests run on H2 in-memory with PostgreSQL compatibility mode; the repository integration test (`ShipmentRequestRepositoryIT`, 7 tests) runs against real Postgres via **Testcontainers** and **self-skips** when no Docker daemon is reachable, so the rest of the suite stays green on a laptop without Docker.

Notable classes:

- **`ShipSmartApiApplicationTests`** — a real `@SpringBootTest` full-context boot on the H2 test profile. This is the cheap guard for boot-time wiring regressions that `@WebMvcTest`/`@DataJpaTest` slices can't catch (e.g. a `@Component` with two constructors and no `@Autowired` hint — see *Startup & boot* below). It exercises the same bean graph the live `java -jar` boot does, without Docker.
- **`QuoteCacheTest`** — LRU eviction + TTL staleness (via an injected `Clock`) + hit/miss/eviction counters.
- **`QuoteFanoutServiceTest`** — the cache short-circuit, parallel provider merge + cache-fill, and canonical sorting (providers mocked).
- **`SupabaseJwtVerifierTest`** — the HS256 path (valid / expired / wrong-secret / missing-sub) the local stack + ShipSmart-Test e2e rely on.
- **`FlywayValidationRunnerTest`** — the boot-time schema guard's three paths: skip-with-a-warning when Flyway is disabled (`SPRING_FLYWAY_ENABLED=false`), pass-through when nothing is pending, and fail-fast on a pending migration.

Run a single test class:

```bash
./gradlew test --tests "com.shipsmart.api.service.QuoteServiceTest"
```

> Build/run on **JDK 17** (the Gradle wrapper is 8.12). JDK 25 + wrapper 8.12 fails at `:test` task creation.

### API collection (Postman)

[`postman/ShipSmart-Orchestrator.postman_collection.json`](./postman/ShipSmart-Orchestrator.postman_collection.json)
is a runnable, asserted walk of the live API: the health probes, the shipments lifecycle
(`401` unauthenticated → create with `Idempotency-Key` → owner read + list → cross-user
`404` → unknown-id `404`), and the provider inventory. A collection-level pre-request
script mints the two test users' Supabase-style HS256 JWTs from the environment's
`SUPABASE_JWT_SECRET` — no manual tokens needed — and a collection-level guard fails any
request that returns a 5xx or takes over 5s. Import it with
[`postman/environments/local.postman_environment.json`](./postman/environments/local.postman_environment.json)
(its defaults match `ShipSmart-Test`'s self-contained stack; point `SUPABASE_JWT_SECRET`
at your own boot's secret otherwise), or run it headless:

```bash
npx newman run postman/ShipSmart-Orchestrator.postman_collection.json \
  -e postman/environments/local.postman_environment.json \
  --env-var "SUPABASE_JWT_SECRET=<your local secret>"
```

### Formatting & CI

Code style is enforced by **Spotless** (Google Java Format, AOSP style; see `build.gradle`).
`spotlessCheck` is wired into `check`, so it also runs as part of `./gradlew build` — a
mis-formatted file fails the build. Auto-format before committing:

```bash
./gradlew spotlessApply
```

CI (`.github/workflows/ci.yml`) is a single `./gradlew build --no-daemon` step: Spotless
format check + compile + the full test suite.

---

## Operational notes

### Startup & boot

- **`Failed to determine driver class` / startup hangs:** `DATABASE_URL` is missing or malformed (must be a JDBC URL — `jdbc:postgresql://…`).
- **Full-context boot under `java -jar`:** the `local` profile samples traces at 1.0 for dev visibility but ships the OTLP exporter **disabled** (`management.otlp.tracing.export.enabled=false` in `application-local.yml`), so a local boot needs no OTLP collector and no override env — set a real `MANAGEMENT_OTLP_TRACING_ENDPOINT` (and re-enable that export flag) to ship spans. Every Spring `@Component` with more than one constructor must annotate its injection constructor with `@Autowired` (e.g. `QuoteCache`), otherwise the full context fails with *"No default constructor found"* — the `@SpringBootTest` in `ShipSmartApiApplicationTests` guards this in CI. Both are exercised end-to-end by `ShipSmart-Test`'s live Java e2e.
- **Migrations:** Supabase remains the source of truth — migrations live in `ShipSmart-Infra/supabase/migrations/` and are applied via `supabase db push`. The Java service ships a **mirror** of those migrations under `src/main/resources/db/migration/` (`V1__baseline.sql`, `V2__interview_upgrade.sql`) and runs Flyway in **validate mode** (`spring.flyway.validate-on-migrate=true`, `baseline-on-migrate=true`). `FlywayValidationRunner` makes any pending migration **fatal at boot** and logs applied/total counts. Disable with `SPRING_FLYWAY_ENABLED=false` only when running against a non-Postgres dev DB — Flyway is injected as an `ObjectProvider`, so the runner then logs a warning and skips validation instead of crashing on the missing bean.

### Auth & CORS

- **All requests `401`:** `SUPABASE_JWT_SECRET` is wrong — it must match the project's signing secret exactly (Supabase dashboard → Settings → API).
- **CORS blocked from frontend:** add the frontend origin to `CORS_ALLOWED_ORIGINS` (comma-separated).

### Carrier integration

- **FedEx quotes empty:** check `FEDEX_CLIENT_ID`, `FEDEX_CLIENT_SECRET`, and `FEDEX_ACCOUNT_NUMBER` are set. Verify `FEDEX_BASE_URL` points to the correct environment (sandbox vs production).
- **Provider priority:** `QuoteProvider#priority()` (lower = earlier) controls fanout order. FedEx overrides to `10` so real-time carrier calls dispatch before mocks. Startup logs the full list: `carrier=ENABLED@p<priority>`.
- **Quote cache:** identical `(origin, destination, dropOffDate, expectedDeliveryDate, weight-bucket-kg, items)` tuples served within `shipsmart.quote-cache.ttl-seconds` hit the in-process LRU and skip the carrier fanout entirely. Clear it by restarting the service — the cache is not distributed.

### Request lifecycle guarantees

- **Idempotency:** `POST /api/v1/shipments` and `POST /api/v1/bookings/redirect` require an `Idempotency-Key` request header. The first response is persisted to `idempotency_keys` and replayed verbatim for repeats within `shipsmart.idempotency.ttl-hours`. `IdempotencyCleanupJob` sweeps expired rows on a schedule.
- **Optimistic concurrency:** shipment reads/writes return an `ETag` derived from the JPA `@Version` column. `PATCH /api/v1/shipments/{id}` honours `If-Match`; a stale value yields `409 Conflict`.
- **Rate limiting:** Bucket4j per-IP, in-memory. Defaults: 20/min shipments, 30/min quotes, 10/min bookings (configurable). Overflow → `429` with a `RateLimitExceededException` body. Toggle off with `SHIPSMART_RATE_LIMIT_ENABLED=false`.

### Observability

- **Correlation IDs:** every request gets/echoes `X-Request-Id` and surfaces in the log pattern as `[requestId] [traceId] [userId]`. The `quote-provider` and `audit` executors are MDC-aware so async work keeps the same context.
- **Audit log:** methods annotated `@Audited` are intercepted by `AuditAspect` and written to the `audit_log` table asynchronously through the dedicated `audit` thread pool. Failures here never propagate to the request.
- **Metrics & traces:** Prometheus scrape at `/actuator/prometheus`; OpenTelemetry tracing is wired via Micrometer. The OTLP exporter is **off by default** (`MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0.0`) — flip the sampling probability and set `MANAGEMENT_OTLP_TRACING_ENDPOINT` to send spans to a collector.
- **API spec:** SpringDoc serves the OpenAPI 3 document at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

---

## License

See [LICENSE](./LICENSE) for the full text. ShipSmart-Orchestrator is distributed under a proprietary license — © 2026 Nia. All rights reserved.
