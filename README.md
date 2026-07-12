# ShipSmart — Orchestrator (Java / Spring Boot API)

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Gradle](https://img.shields.io/badge/Gradle-8.12-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![Flyway](https://img.shields.io/badge/Flyway-validate%20mode-CC0200?logo=flyway&logoColor=white)](https://flywaydb.org/)
[![JWT](https://img.shields.io/badge/Auth-JWKS%2FRS256-0A7EA4)](#security)
[![Tests](https://img.shields.io/badge/tests-111%20incl.%20Testcontainers-3FB950?logo=junit5&logoColor=white)](#tests)
[![Live](https://img.shields.io/badge/Live-%2Fapi%2Fv1%2Fhealth-46E3B7?logo=render&logoColor=white)](https://shipsmart.onrender.com/api/v1/health)
[![License](https://img.shields.io/badge/License-See%20LICENSE-blue)](./LICENSE)

> The **system of record** of the ShipSmart platform — and the reason its AI
> can't lie. Owns quotes, bookings, saved options, shipments, and tracking
> behind **JWKS-verified authentication**, optimistic locking, idempotency, and
> **`AiClaimGuard`**: every AI-assisted booking is re-derived from stored state,
> and **Java wins on price.** The model advises; this service decides.

**Single writer to Supabase Postgres** — every other service reads through this
one. Where the FastAPI sibling is probabilistic and stateless, this service is
deterministic and stateful.

**Stack:** Spring Boot 3.4.4 · Java 17 · Gradle 8.12 · Spring Data JPA ·
PostgreSQL · Flyway (validate) · Caffeine · Bucket4j · Spring Security ·
Spring AOP · Micrometer → OpenTelemetry (OTLP) + Prometheus · SpringDoc ·
Testcontainers

**Live:** [`GET /api/v1/health`](https://shipsmart.onrender.com/api/v1/health) ·
[`GET /actuator/health`](https://shipsmart.onrender.com/actuator/health)
*(Swagger is deliberately 401 in production; Render free tier cold-starts
~30–60 s).*

---

## Table of contents

- [The ShipSmart ecosystem](#the-shipsmart-ecosystem)
- [Engineering highlights](#engineering-highlights)
- [The trust boundary: AiClaimGuard](#the-trust-boundary-aiclaimguard)
- [Architecture](#architecture)
- [Security](#security)
- [Reliability & correctness](#reliability--correctness)
- [Performance](#performance)
- [Observability & audit](#observability--audit)
- [Running locally](#running-locally)
- [Configuration reference](#configuration-reference)
- [Tests](#tests)
- [License](#license)

---

## The ShipSmart ecosystem

One of six sibling repositories — clone them as siblings of this directory. All
six are also mirrored together in
**[ShipSmart](https://github.com/nia194/ShipSmart)** — the umbrella repository
that snapshots each component at a pinned commit (see its `COMPONENTS.yml`).

| Repo | Role | Stack |
|------|------|-------|
| [ShipSmart-Web](https://github.com/nia194/ShipSmart-Web) | React SPA — search-first UI | React 19, Vite, TS |
| **[ShipSmart-Orchestrator](https://github.com/nia194/ShipSmart-Orchestrator)** *(this repo)* | Java system of record — single Postgres writer, AI trust boundary | Spring Boot 3.4, Java 17 |
| [ShipSmart-API](https://github.com/nia194/ShipSmart-API) | Python AI layer — RAG, guardrails, agents, SSE streaming | FastAPI, Python 3.13 |
| [ShipSmart-MCP](https://github.com/nia194/ShipSmart-MCP) | Read-only MCP tool server | FastAPI + MCP |
| [ShipSmart-Infra](https://github.com/nia194/ShipSmart-Infra) | Supabase schema, RLS, WORM ledger, edge functions | Supabase, Deno |
| [ShipSmart-Test](https://github.com/nia194/ShipSmart-Test) | Cross-repo contracts + evals + e2e | Python 3.13, pytest |

---

## Engineering highlights

| | Capability | Why it's interesting |
|---|---|---|
| 🚧 | **`AiClaimGuard` trust boundary** | Four invariants re-derive every AI-assisted booking from stored state; on any mismatch the booking is refused, and on acceptance the **authoritative total is always the stored quote's** — never the AI's. |
| 🔑 | **Real JWKS auth** | `SupabaseJwtVerifier` validates **RS256 against the issuer's JWKS**; HS256 only as legacy/test fallback; prod is fail-closed (`require-jwt-secret: true`). |
| ⚡ | **Scatter-gather quoting** | Carriers queried in parallel on a bounded executor with per-call timeouts — total latency ≈ slowest carrier; failures surface as **`QuoteTrust`-tagged partials**, never silent gaps. |
| 🔁 | **Idempotency subsystem** | `@Idempotent` + key store + body hash: retried writes replay the stored response; payload mismatch ⇒ conflict; a scheduled cleanup job purges expired keys. |
| 🔒 | **Optimistic locking** | JPA `@Version` + ETag/`If-Match` ⇒ HTTP 412 on stale writes — concurrent editors can't clobber each other. |
| 🧾 | **AOP audit** | `@Audited` + `AuditAspect` persist domain events as a cross-cutting concern — auditing can't be forgotten on new endpoints. |
| 🗃️ | **Schema discipline** | Flyway (validate-on-migrate) + Hibernate `ddl-auto: validate` + a `FlywayValidationRunner`: drift fails startup, not a 2 a.m. query. |
| 📈 | **Ops surfaces** | Per-provider metrics (`ProviderMetricsController`), saved-option analytics, Prometheus registry, OTel tracing, MDC correlation ids. |

---

## The trust boundary: AiClaimGuard

An AI-assisted booking must survive four invariants — each failure a distinct,
auditable refusal:

1. **No unquoted bookings** — must reference a live `StoredQuote` this server produced.
2. **Live quote only** — expired quotes are refused, never honoured at a stale price.
3. **Explicit human confirmation** — an AI proposal is not consent.
4. **Java wins on price** — the AI-stated total is re-validated against the
   stored quote; acceptance returns the **stored** total.

Plus the policy-aware refusal: an AI "this looks compliant" is downgraded to a
refusal unless the deterministic compliance checker verified it. Pure,
deterministic (injected `Clock`), exhaustively unit-tested.

## Architecture

```
  JWT ──▶ CorrelationIdFilter ▶ JwtAuthFilter (JWKS/RS256) ▶ RateLimitFilter
      ▶ BodyCachingFilter ▶ IdempotencyInterceptor
      ▶ controllers (/api/v1: quotes · bookings · saved-options(+analytics) ·
        shipments · provider-metrics · health)
      ▶ services (@Transactional · @Cacheable · @Audited)
        QuoteService · QuoteFanoutService · BookingService · SavedOptionService ·
        ShipmentService · AiClaimGuard · IdempotencyCleanupJob
      ▶ QuoteProvider port ─ registry ─ FedEx adapter (sandbox default) + mock
      ▶ Spring Data JPA + Specifications ─ Flyway-migrated Postgres
```

Immutable `record` DTOs throughout (`ShippingServiceDto` + `QuoteTrust`,
`TrackingStatusDto` + `TrackingStatus` enum); typed error responses via
`GlobalExceptionHandler`; row-ownership checks (`OwnershipException`) on every
user-scoped entity.

## Security

- JWKS/RS256 primary verification; Spring Security per-route rules; CSRF off
  (stateless bearer API) with configured CORS.
- **Prod fail-closed:** `require-jwt-secret: true`; error responses carry no
  stacktraces/messages; actuator exposure limited to
  `health,info,metrics,caches,prometheus`; **Swagger returns 401 in prod**.

## Reliability & correctness

- Optimistic locking (`@Version` + ETag) · idempotent writes + cleanup job ·
  `@Transactional` boundaries · Flyway validate + startup schema check ·
  deterministic sorting (`QuoteSortOption` comparators — labels are code, the
  model only explains them).

## Performance

- Parallel fan-out (`QuoteFanoutService`, bounded `ThreadPoolTaskExecutor`,
  per-call timeouts).
- Two-tier caching: Spring **Caffeine** (`quotesByShipmentId`, `shipmentById` —
  `maximumSize=5000, expireAfterWrite=120s`) + a bespoke `QuoteCache` (LRU/TTL).
- **Bucket4j** token buckets: shipments **20/min** · quotes **30/min** ·
  bookings **10/min** (env-tunable), typed 429s.

## Observability & audit

Micrometer tracing → OTLP exporter + Prometheus registry; `X-Request-Id` into
MDC on every log line and propagated outbound; `@Audited` AOP events into
`AuditLog`; provider call outcomes into `ProviderMetrics`.

## Running locally

```bash
./gradlew bootRun          # http://localhost:8080 (local profile)
./gradlew test             # 111 tests (Testcontainers suites need Docker)
./gradlew clean bootJar    # production jar
```

Gradle 8.12 via the wrapper — no host install required. JDK 17 toolchain.

## Configuration reference

| Env | Effect |
|---|---|
| `SUPABASE_JWT_SECRET` / `REQUIRE_JWT_SECRET` | JWT verification; prod sets `require-jwt-secret: true` |
| `SHIPSMART_RATE_LIMIT_ENABLED` / `_SHIPMENTS` / `_QUOTES` / `_BOOKINGS` | Bucket4j limits (defaults 20/30/10 per min) |
| `SHIPSMART_IDEMPOTENCY_ENABLED` | idempotency keys on `POST /shipments`, `POST /bookings/redirect` |
| `spring.cache.caffeine.spec` | `maximumSize=5000,expireAfterWrite=120s` |
| profiles | `application-local.yml` vs `application-production.yml` (fail-closed) |

## Tests

**111 `@Test` across 21 classes** — JUnit 5 + AssertJ unit tests with injected
`Clock`, plus **Testcontainers** integration tests against real ephemeral
Postgres (no H2 look-alikes). **Spotless** (google-java-format) is wired into
`check`/`build` — formatting is enforced, not hoped. Cross-repo: the §5.6 trust
boundary and DTO wire shapes are asserted by **ShipSmart-Test** in CI.

## License

See [LICENSE](./LICENSE).
