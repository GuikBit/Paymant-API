# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Multi-tenant Payment API integrating with the Asaas payment gateway. Built with Java 21, Spring Boot 3.4.4, PostgreSQL 16 (with Row-Level Security), and Redis 7. Manages customers, plans, subscriptions, charges (PIX/Boleto/Credit Card), webhooks, and plan changes with pro-rata calculations.

## Key Project Documents

- **`API Pagamento Assas.md`** — Documento de levantamento e especificacao completa do projeto. Contem todas as regras de negocio, requisitos, fluxos de integracao com o Asaas, e decisoes arquiteturais. Deve ser consultado como fonte primaria para entender o "por que" de cada decisao.
- **`PROGRESS.md`** — Controle de desenvolvimento fase a fase (Fase 0 a Fase 10). Registra o que foi entregue, o que esta pendente, e os arquivos criados em cada etapa. Deve ser consultado para entender o estado atual do projeto e o que falta implementar (Fases 9 e 10 ainda nao iniciadas).

## Build & Run Commands

```bash
# Build (skip tests)
./mvnw clean package -DskipTests

# Run tests (requires Docker for TestContainers)
./mvnw test

# Run a single test class
./mvnw test -Dtest=ChargeServiceTest

# Run a single test method
./mvnw test -Dtest=ChargeServiceTest#shouldCreateCharge

# Start full stack (app + Postgres + Redis)
docker compose up --build

# Start only dependencies
docker compose up postgres redis
```

Environment variables: copy `.env.example` to `.env` and fill in secrets. Key vars: `DB_URL`, `DB_PASSWORD`, `REDIS_HOST`, `JWT_SECRET`, `JASYPT_PASSWORD`, `ASAAS_BASE_URL`.

## Architecture

### Multi-Tenancy via PostgreSQL RLS

Tenant isolation is enforced at the database level. `TenantContextInterceptor` extracts `company_id` from the HTTP header (or JWT fallback) into a `ThreadLocal` (`TenantContext`). `TenantRlsAspect` then sets `app.current_company_id` via `SET LOCAL` before every repository call. The `@CrossTenant` annotation bypasses RLS for admin operations. Two DB roles: `payment_app` (no BYPASSRLS) and `payment_flyway` (with BYPASSRLS for migrations).

### Domain Modules (Vertical Slices)

Each domain module follows the pattern: Controller -> Service -> Repository, with MapStruct mappers and DTOs. Key modules:

- **auth** - JWT authentication (JwtService, JwtAuthenticationFilter), roles: HOLDING_ADMIN, COMPANY_ADMIN, COMPANY_OPERATOR, SYSTEM
- **company** - Tenant config, Jasypt-encrypted Asaas API keys (`EncryptionService`)
- **customer** - CRUD with soft-delete, credit balance tracking
- **plan** - Subscription plans with JSONB limits/features, version control, soft-delete
- **subscription** - Lifecycle management, `OverdueSubscriptionJob` (daily 8 AM cron)
- **charge** - Charges with state machine (ChargeStatus enum), supports PIX/Boleto/Credit Card/Installments
- **planchange** - Plan upgrades/downgrades with `ProrationCalculator`, `ScheduledPlanChangeJob` (daily 1 AM), policies: IMMEDIATE_PRORATA, END_OF_CYCLE, IMMEDIATE_NO_PRORATA
- **webhook** - Asaas webhook ingress with backoff retry schedule, DLQ handling, `WebhookProcessor` (scheduled)
- **creditledger** - Credit/debit audit trail for prorations
- **installment** - Installment plan entities

### Cross-Cutting Concerns

- **Idempotency**: `IdempotencyFilter` intercepts POST requests with `Idempotency-Key` header. Redis fast-path with Postgres fallback (`IdempotencyService`). Hourly cleanup job.
- **Outbox Pattern**: `OutboxPublisher` writes domain events atomically within transactions. `OutboxRelay` (scheduled every 5s) delivers via HTTP webhooks. Admin endpoints for monitoring.
- **Audit**: `@Auditable(action="...", entity="...")` annotation with `AuditAspect` interceptor.
- **Resilience**: Resilience4j on Asaas API calls — retry (3 attempts, 2x backoff, network errors only), circuit breaker (50% threshold, 30s open), time limiter.

### Asaas Integration Layer (`integration/asaas/`)

- **client/**: `AsaasClientFactory` creates per-company `RestClient` with decrypted API keys. Separate clients: `AsaasCustomerClient`, `AsaasPaymentClient`, `AsaasSubscriptionClient`, `AsaasInstallmentClient`. `AsaasErrorHandler` parses 4xx/5xx responses.
- **gateway/**: `AsaasGatewayService` is the domain-facing facade.
- **mapper/**: `AsaasMapper` converts between Asaas DTOs and domain objects.

### Database Migrations

Flyway manages two migration files in `src/main/resources/db/migration/`:
- `V1__init_schema.sql` - 13 tables with indexes
- `V2__enable_rls.sql` - RLS policies on 11 transactional tables

### Test Organization

Tests are organized by development phase (`phase1/` through `phase7/`) in `src/test/java/.../payments/`. `AbstractIntegrationTest` is the base class using TestContainers (PostgreSQL). Technologies: JUnit 5, TestContainers, jqwik (property-based), WireMock (HTTP mocking), Spring Security Test.

### Observability

- Prometheus metrics at `/actuator/prometheus` (webhook, outbox, idempotency, Asaas error counters)
- Health at `/actuator/health`
- Swagger UI at `/swagger-ui.html`
- Structured JSON logging via Logstash encoder

## Spring Profiles

- **dev**: DB pool 10, debug logging, Asaas sandbox, `DevDataSeeder` active
- **staging**: DB pool 20, info logging
- **prod**: DB pool 30, warn logging, production Asaas
- **test**: Used by `AbstractIntegrationTest`
