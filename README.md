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

> **Metric convention:** structural counts/configs are facts (111 tests, rate
> limits 20/30/10 per min, Caffeine `max 5000 / 120s`); latency figures are
> **(target)** budgets, never measured production metrics.

---

## Table of contents

- [The ShipSmart ecosystem](#the-shipsmart-ecosystem)
- [Architecture (HLD)](#architecture-hld)
- [Request flow](#request-flow)
- [The trust boundary: AiClaimGuard](#the-trust-boundary-aiclaimguard)
- [Scatter-gather quoting](#scatter-gather-quoting)
- [Concurrency & idempotency](#concurrency--idempotency)
- [Object design (OOD)](#object-design-ood)
- [Data model (ER)](#data-model-er)
- [Caching](#caching)
- [Security](#security)
- [Performance & availability](#performance--availability)
- [Deployment topology](#deployment-topology)
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

## Architecture (HLD)

**Figure 1 — container/component view.** Every mutating path crosses auth,
rate limiting, idempotency, and ownership checks before a service runs;
auditing is aspect-driven so it cannot be forgotten on new endpoints.

```mermaid
flowchart TB
    CLIENT["Web / FastAPI"] -->|Bearer JWT| FC
    subgraph FC["Servlet filter chain"]
        F1[CorrelationIdFilter] --> F2["JwtAuthFilter (SupabaseJwtVerifier: JWKS/RS256)"] --> F3["RateLimitFilter (Bucket4j)"] --> F4["BodyCachingFilter"] --> F5["IdempotencyInterceptor (@Idempotent)"]
    end
    FC --> CT
    subgraph CT["Controllers (/api/v1)"]
        C1[Quote]
        C2[Booking]
        C3["SavedOption + Analytics"]
        C4[Shipment]
        C5[ProviderMetrics]
        C6[Health]
    end
    CT --> SV
    subgraph SV["Services (@Transactional · @Cacheable · @Audited)"]
        S1[QuoteService]
        S2["QuoteFanoutService (scatter-gather)"]
        S3[BookingService]
        S4[SavedOptionService]
        S5[ShipmentService]
        S6["AiClaimGuard (trust boundary)"]
        S7["IdempotencyCleanupJob (scheduled)"]
    end
    SV --> PR
    subgraph PR["Provider layer (Strategy)"]
        Q1["QuoteProvider port"] --> Q2[QuoteProviderRegistry] --> Q3["FedExQuoteProviderAdapter (sandbox) + mock"]
    end
    SV --> RP["Spring Data JPA + Specifications"]
    RP --> DB[("Postgres — Flyway V1/V2, ddl-auto: validate")]
    SV --> AOP["AuditAspect (@Audited) -> AuditLog"]
    SV -.-> OBS["Actuator + Micrometer -> OTel OTLP + Prometheus"]
```

---

## Request flow

**Figure 2 — ingress to response, with every gate annotated.**

```mermaid
flowchart LR
    A[Request] --> B["CorrelationIdFilter: X-Request-Id -> MDC"]
    B --> C{"JWKS/RS256 verify ok?"}
    C -->|no| E401[401]
    C -->|yes| D{"rate-limit bucket has tokens?"}
    D -->|no| E429["429 typed error"]
    D -->|yes| E["BodyCachingFilter (hashable body)"]
    E --> F{"@Idempotent? key seen?"}
    F -->|replay| G["stored response returned"]
    F -->|new| H["Controller -> Service (@Transactional)"]
    H --> I{"ownership check"}
    I -->|not owner| E403["OwnershipException"]
    I -->|owner| J["repository / provider work"]
    J --> K["@Audited aspect -> AuditLog"]
    K --> L["typed response (+ ETag where applicable)"]
```

---

## The trust boundary: AiClaimGuard

**Figure 3 — an AI-assisted booking, re-derived from stored state.**

```mermaid
sequenceDiagram
    participant AI as AI layer (advisory claim)
    participant BC as BookingController
    participant G as AiClaimGuard
    participant DB as StoredQuote
    AI->>BC: booking request (quoteRef, aiStatedTotal, confirmed?)
    BC->>G: validate(claim)
    G->>DB: load StoredQuote(quoteRef)
    alt no stored quote
        G--xBC: REFUSE — unquoted booking
    else expired
        G--xBC: REFUSE — stale price never honoured
    else not human-confirmed
        G--xBC: REFUSE — AI proposal is not consent
    else totals mismatch
        G--xBC: REFUSE — Java wins on price
    else all invariants hold
        G-->>BC: proceed — authoritative total = STORED quote's
        BC->>BC: idempotent write under optimistic lock
    end
```

**Figure 4 — the decision tree: each failed invariant is a distinct, auditable
refusal.**

```mermaid
flowchart TB
    A["AI-assisted booking claim"] --> B{"references StoredQuote?"}
    B -->|no| R1["REFUSE: unquoted"]
    B -->|yes| C{"quote still live?"}
    C -->|no| R2["REFUSE: expired quote"]
    C -->|yes| D{"explicit human confirmation?"}
    D -->|no| R3["REFUSE: proposal is not consent"]
    D -->|yes| E{"AI total == stored total?"}
    E -->|no| R4["REFUSE: price mismatch — Java wins"]
    E -->|yes| F{"AI claims compliant?"}
    F -->|"yes, unverified"| R5["DOWNGRADE to refusal: only the deterministic checker may clear"]
    F -->|"verified / n-a"| OK["PROCEED — return STORED total"]
```

Pure and deterministic (injected `Clock`), exhaustively unit-tested.

---

## Scatter-gather quoting

**Figure 5 — parallel carriers; total latency ≈ slowest, not sum.** Failures
surface as **`QuoteTrust`-tagged partials**, never silent gaps.

```mermaid
sequenceDiagram
    participant C as Client
    participant QS as QuoteService
    participant FO as QuoteFanoutService
    participant P1 as Carrier A
    participant P2 as Carrier B
    participant CA as Caffeine/QuoteCache
    C->>QS: GET quotes(shipmentId)
    QS->>CA: cache lookup
    alt hit
        CA-->>QS: cached quotes (trust: cached)
    else miss
        QS->>FO: fanout(request)
        par carrier A
            FO->>P1: quote() on bounded executor, timeout armed
            P1-->>FO: prices
        and carrier B
            FO->>P2: quote()
            P2--xFO: timeout / error
        end
        FO-->>QS: normalized quotes + QuoteTrust per provider (live/estimated/mock/cached) + provider_status
        QS->>CA: populate (expireAfterWrite 120s)
    end
    QS-->>C: options — degraded carriers visible, never silent
```

---

## Concurrency & idempotency

**Figure 6 — optimistic locking: `@Version` + ETag.**

```mermaid
sequenceDiagram
    participant U1 as Writer 1
    participant U2 as Writer 2
    participant S as ShipmentService
    U1->>S: GET shipment -> ETag v5
    U2->>S: GET shipment -> ETag v5
    U1->>S: PATCH If-Match v5
    S-->>U1: 200 — version -> v6
    U2->>S: PATCH If-Match v5
    S--xU2: 412 Precondition Failed (stale)
```

**Figure 7 — idempotent retry: replay-safe writes.**

```mermaid
sequenceDiagram
    participant C as Client (retrying)
    participant I as IdempotencyInterceptor
    participant K as idempotency_keys
    C->>I: POST /bookings/redirect (Idempotency-Key k1)
    I->>K: store(k1, body-hash) + execute
    I-->>C: 201 response persisted
    C->>I: same POST again (k1)
    I->>K: k1 found, body-hash matches
    I-->>C: replayed stored response (no double execution)
    C->>I: k1 with DIFFERENT body
    I--xC: IdempotencyConflictException
    Note over K: scheduled IdempotencyCleanupJob purges expired keys
```

---

## Object design (OOD)

**Figure 8 — provider seam, record DTOs, exception taxonomy.** DTOs are
immutable `record`s; entities inherit `@Version` from `BaseEntity`; JPA
**Specifications** compose type-safe filtered queries instead of string JPQL.

```mermaid
classDiagram
    class QuoteProvider {
        <<interface>>
        +getQuotes(request) ProviderQuote[]
    }
    QuoteProvider <|.. AbstractQuoteProvider
    AbstractQuoteProvider <|-- FedExQuoteProviderAdapter
    class QuoteProviderRegistry {
        +providers() List~QuoteProvider~
    }
    QuoteProviderRegistry o-- QuoteProvider
    class QuoteFanoutService {
        +fanout(request)
        -executor: ThreadPoolTaskExecutor
    }
    QuoteFanoutService ..> QuoteProviderRegistry
    class ShippingServiceDto {
        <<record>>
        +price
        +eta
        +trust: QuoteTrust
    }
    class QuoteTrust {
        <<record>>
        +source: live|estimated|mock|cached
        +freshness
        +provider_status
    }
    ShippingServiceDto *-- QuoteTrust
    class TrackingStatusDto {
        <<record>>
        +status: TrackingStatus
        +events
    }
    class BaseEntity {
        +id
        +version (@Version)
    }
    class GlobalExceptionHandler {
        <<RestControllerAdvice>>
    }
    class Exceptions {
        ResourceNotFound
        ResourceConflict
        RateLimitExceeded
        Ownership
        IdempotencyConflict
    }
    GlobalExceptionHandler ..> Exceptions
```

---

## Data model (ER)

**Figure 9 — persisted entities (key fields).** Flyway owns this schema
(`V1__baseline`, `V2__interview_upgrade`); Hibernate runs `ddl-auto: validate`
plus a `FlywayValidationRunner`, so drift fails startup. *(Field lists are
representative — the migrations are authoritative.)*

```mermaid
erDiagram
    SHIPMENT_REQUEST ||--o{ QUOTE : "has quotes"
    SHIPMENT_REQUEST {
        uuid id PK
        uuid user_id "ownership"
        bigint version "optimistic lock"
        string status
    }
    QUOTE {
        uuid id PK
        uuid shipment_request_id FK
        string carrier
        numeric total
        timestamptz expires_at "live-quote invariant"
        string trust_source
    }
    QUOTE ||--o{ REDIRECT_TRACKING : "booked via"
    REDIRECT_TRACKING {
        uuid id PK
        uuid quote_id FK
        uuid user_id
        string outcome
    }
    SAVED_OPTION {
        uuid id PK
        uuid user_id "ownership"
        jsonb option
    }
    IDEMPOTENCY_KEY {
        string key PK
        string body_hash
        jsonb stored_response
        timestamptz expires_at "cleanup job"
    }
    AUDIT_LOG {
        uuid id PK
        string action "@Audited aspect"
        uuid actor
        timestamptz at
    }
```

---

## Caching

**Figure 10 — two-tier cache + the trust taxonomy.**

```mermaid
flowchart LR
    REQ[Quote request] --> C1{"Caffeine: quotesByShipmentId (max 5000, 120s)"}
    C1 -->|hit| OUT["serve — trust: cached"]
    C1 -->|miss| C2{"bespoke QuoteCache (LRU/TTL, QuoteCacheKey)"}
    C2 -->|hit| OUT
    C2 -->|miss| FAN["parallel fan-out (bounded executor, timeouts)"]
    FAN --> TAG["QuoteTrust per provider + provider_status"]
    TAG --> FILL["populate caches"] --> OUT
```

---

## Security

- **JWKS/RS256** primary verification (`SupabaseJwtVerifier`); HS256 only as
  legacy/test fallback; Spring Security per-route rules; CSRF off (stateless
  bearer API) with configured CORS.
- **Prod fail-closed:** `require-jwt-secret: true`; error responses carry no
  stacktraces/messages; actuator limited to
  `health,info,metrics,caches,prometheus`; **Swagger 401 in prod**.
- **Row-ownership authorization** (`OwnershipException`) on every user-scoped
  entity — object-level access control, not just authentication.

| Threat | Control |
|---|---|
| Forged identity | JWKS/RS256 verification |
| Cross-user data access | ownership checks per entity |
| Replayed/double writes | idempotency keys + body hash |
| Brute-force / abuse | Bucket4j buckets (20/30/10 per min) |
| Info leakage via errors | prod stacktraces/messages `never`; Swagger 401 |
| AI-invented price/clearance | AiClaimGuard invariants (Fig. 3–4) |

---

## Performance & availability

**Latency budget (target):**

| Path | Budget *(target)* |
|---|---|
| Cache hit | < 30 ms |
| Single-carrier quote | < 1.5 s |
| Full fan-out (parallel) | ≈ slowest carrier, < 2 s |
| Booking validation (AiClaimGuard) | < 50 ms |

```mermaid
xychart-beta
    title "Quote path latency in ms (target)"
    x-axis ["cache-hit", "single-carrier", "fan-out", "guard"]
    y-axis "ms (target)" 0 --> 2000
    bar [30, 1500, 2000, 50]
```

**Degradation matrix (coded behaviors, facts):**

| Failure | Behavior |
|---|---|
| One carrier down/slow | per-call timeout → trust-tagged partial results |
| Stale concurrent write | HTTP 412 via ETag/@Version |
| Duplicate write retry | idempotent replay; payload mismatch → conflict |
| Schema drift | startup fails (validate mode + FlywayValidationRunner) |
| Missing JWT secret (prod) | boot refuses (`require-jwt-secret: true`) |

---

## Deployment topology

**Figure 11 — production layout.**

```mermaid
flowchart LR
    W["Render: shipsmart-web"] -->|JWT| J["Render: shipsmart (this, Java)"]
    A["Render: shipsmart-api-python"] -->|httpx| J
    J --> DB[("Supabase Postgres — Flyway-managed tables")]
    OPS[Operator] -.->|"/api/v1/health · /actuator/health · /actuator/prometheus"| J
```

Profiles: `application-local.yml` vs `application-production.yml` — prod
requires the JWT secret, suppresses stacktraces, keeps actuator exposure
minimal.

---

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
| `SUPABASE_JWT_SECRET` / `REQUIRE_JWT_SECRET` | JWT verification; prod fail-closed |
| `SHIPSMART_RATE_LIMIT_ENABLED` / `_SHIPMENTS` / `_QUOTES` / `_BOOKINGS` | Bucket4j limits (defaults 20/30/10 per min) |
| `SHIPSMART_IDEMPOTENCY_ENABLED` | idempotency keys on the write endpoints |
| `spring.cache.caffeine.spec` | `maximumSize=5000,expireAfterWrite=120s` |

## Tests

**111 `@Test` across 21 classes** — JUnit 5 + AssertJ unit tests with injected
`Clock`, plus **Testcontainers** integration tests against real ephemeral
Postgres. **Spotless** (google-java-format) is wired into `check`/`build`.
Cross-repo: the §5.6 trust boundary and DTO wire shapes are asserted by
**ShipSmart-Test** in CI.

## License

See [LICENSE](./LICENSE).
