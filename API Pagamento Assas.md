# API de Pagamentos Multi-Tenant com Integração Asaas

**Projeto:** Plataforma central de pagamentos para holding com múltiplas empresas/sistemas
**Stack principal:** Java 21 + Spring Boot 3.x + PostgreSQL + Asaas API v3
**Modelo:** Multi-tenant (cada empresa da holding = um tenant com credenciais Asaas próprias)

---

## 1. Visão geral e princípios de arquitetura

A API atua como **camada de orquestração e abstração** entre os sistemas internos da holding e o gateway Asaas. Ela centraliza:

- Cadastro das empresas (tenants) e suas credenciais Asaas
- Cadastro de clientes pagadores (sincronizados com o Asaas)
- Planos de assinatura por empresa
- Assinaturas ativas, cobranças avulsas e parcelamentos
- Recebimento e processamento de webhooks do Asaas
- Conciliação financeira e relatórios

**Princípios:**

1. **Multi-tenancy por empresa com isolamento em duas camadas**: cada empresa tem sua própria `api_key` do Asaas. O `company_id` é resolvido por header (`X-Company-Id`) ou via JWT, propagado por toda a stack **e adicionalmente garantido pelo banco via Row-Level Security (RLS)** — ver seção 5.A.
2. **Idempotência híbrida e durável**: toda criação de recurso aceita um header `Idempotency-Key`. Redis atua como fast-path (verificação rápida), mas o **PostgreSQL é a fonte de verdade** via tabela `idempotency_keys` e constraints únicas — ver seção 10.2.
3. **Source of truth híbrido**: o Asaas é a fonte da verdade do status financeiro, mas o banco local mantém réplica para queries rápidas, relatórios e auditoria. Webhooks reconciliam o estado.
4. **Resiliência**: toda chamada externa passa por Resilience4j (retry + circuit breaker + timeout).
5. **Webhooks tolerantes a desordem**: o sistema **assume que eventos podem chegar fora de ordem** e implementa reprocessamento com delay + DLQ — ver seção 8.5.
6. **Integridade histórica via soft delete**: entidades referenciadas por registros financeiros (`customers`, `plans`) **nunca** são removidas fisicamente — ver seção 5.B.
7. **Eventos internos para integrações externas (Outbox Pattern)**: a API publica eventos de domínio em uma tabela `outbox`; sistemas externos (n8n, ERPs, BI) consomem essa fila — ver seção 3.9 e seção 9.
8. **Observabilidade obrigatória**: logs estruturados, tracing distribuído e métricas de negócio (cobranças criadas, taxa de aprovação, falhas de webhook etc.).
9. **Segurança**: credenciais Asaas criptografadas em repouso (Jasypt ou KMS), nunca logadas, nunca retornadas em respostas.

---

## 2. Stack tecnológica recomendada

| Camada | Tecnologia | Justificativa |
|---|---|---|
| Linguagem | Java 21 (LTS) | Virtual threads, pattern matching, records |
| Framework | Spring Boot 3.3+ | Padrão de mercado, ecossistema maduro |
| Web | Spring Web MVC + Bean Validation (Jakarta) | REST + validação declarativa |
| Persistência | Spring Data JPA + Hibernate 6 | Produtividade |
| Banco | PostgreSQL 16+ (com **RLS habilitado**) | JSONB para payloads, RLS para isolamento multi-tenant, advisory locks |
| Migrations | Flyway | Versionamento de schema (incluindo policies de RLS) |
| Segurança | Spring Security 6 + JWT (jjwt ou Nimbus) | Auth stateless |
| HTTP client | Spring `RestClient` (ou OpenFeign) | Cliente moderno e fluente para Asaas |
| Resiliência | Resilience4j | Retry, circuit breaker, rate limiter |
| Cache | Redis (Spring Data Redis) | Cache de planos, fast-path de idempotência, locks distribuídos |
| Mensageria interna (assíncrono) | RabbitMQ **ou** tabela `outbox` + worker | Processamento de webhooks, eventos de domínio, DLQ |
| Criptografia | Jasypt ou AWS KMS / HashiCorp Vault | Proteção das `api_key` Asaas |
| Documentação | springdoc-openapi (Swagger UI) | OpenAPI 3 automático |
| Testes | JUnit 5, Mockito, Testcontainers, WireMock, **jqwik** (property-based) | Cobertura completa incluindo invariantes do `ProrationCalculator` |
| Observabilidade | Micrometer + Prometheus + Grafana, Loki, OpenTelemetry | Métricas, logs, tracing |
| Build | Maven ou Gradle (Kotlin DSL) | — |
| Container | Docker + Docker Compose | Deploy padronizado |

**Bibliotecas auxiliares:**
- **MapStruct** — mapeamento DTO ↔ Entity sem boilerplate
- **Lombok** — redução de boilerplate (com cautela)
- **Problem Details (RFC 7807)** — padronização de erros via `ProblemDetail` do Spring 6

---

## 3. Módulos do sistema

### 3.1. Módulo `company` (Empresas / Tenants)
Gerencia as empresas da holding.
- CRUD de empresas (CNPJ, razão social, nome fantasia, contato)
- Armazenamento **criptografado** da `asaas_api_key` e `asaas_environment` (sandbox/production)
- Status (ativa, suspensa, inadimplente)
- Configurações por empresa: webhook secret, taxas, juros/multa padrão, dias de vencimento padrão, política de mudança de plano, política de validação de downgrade

### 3.2. Módulo `auth` (Autenticação e autorização)
- Login de usuários internos (admin da holding) e por empresa
- JWT com claims `company_id` e `roles`
- RBAC: `ROLE_HOLDING_ADMIN`, `ROLE_COMPANY_ADMIN`, `ROLE_COMPANY_OPERATOR`, `ROLE_SYSTEM` (para sistemas que consomem a API)
- API keys próprias da plataforma para integração M2M
- **Responsável por setar o `company_id` no contexto da sessão Postgres** (`SET LOCAL app.current_company_id = ...`) no início de cada transação — base para o RLS funcionar.

### 3.3. Módulo `customer` (Clientes pagadores)
- CRUD de clientes (PF/PJ) **escopado por empresa**
- Sincronização bidirecional com `/customers` do Asaas
- Validação de CPF/CNPJ
- Endereço para boleto
- **Soft delete** (`deleted_at`): exclusão é lógica para preservar integridade histórica de cobranças e faturas vinculadas — ver seção 5.B.

### 3.4. Módulo `plan` (Planos de assinatura)
- CRUD de planos por empresa
- Atributos: nome, descrição, valor, ciclo (`MONTHLY`, `QUARTERLY`, `SEMIANNUALLY`, `YEARLY`), trial, valor de setup, ativo/inativo, **`limits` (jsonb)**, **`features` (jsonb)**, **`tier_order` (int)**
- Versionamento de planos (mudança de preço não afeta assinaturas existentes)
- **Soft delete** (`deleted_at`): planos referenciados por assinaturas históricas nunca podem ser removidos fisicamente.

### 3.5. Módulo `subscription` (Assinaturas)
- Criar/cancelar/pausar/reativar assinaturas
- Vincula `customer` + `plan`
- Espelha o objeto `subscription` do Asaas
- Atualização de método de pagamento
- Histórico de cobranças geradas pela assinatura
- Tratamento de inadimplência (suspensão automática após N tentativas)
- Mudança de plano (upgrade/downgrade) — ver seção 11.A.

### 3.6. Módulo `charge` (Cobranças)
Coração transacional do sistema.
- Cobrança avulsa (única)
- Parcelamento (cartão ou boleto)
- Cobrança vinculada a assinatura
- Métodos: `PIX`, `CREDIT_CARD`, `DEBIT_CARD`, `BOLETO`, `UNDEFINED` (cliente escolhe)
- Estados: `PENDING`, `RECEIVED`, `CONFIRMED`, `OVERDUE`, `REFUNDED`, `CHARGEBACK`, `CANCELED`
- **Campo `origin`**: identifica a origem do pagamento — `WEB`, `PDV`, `RECURRING`, `API`, `BACKOFFICE`, `PLAN_CHANGE`. Permite conciliação e reporting separados por canal e é essencial para ecossistemas com ponto de venda físico.
- **Validação explícita de transição de estado**: ver seção 7.A.
- Operações: criar, consultar, cancelar, estornar, reenviar, gerar segunda via, gerar QR Code Pix, tokenizar cartão.

### 3.7. Módulo `webhook` (Recepção de eventos Asaas)
- Endpoint público autenticado por token (configurado no painel Asaas)
- **Persistência bruta obrigatória**: o payload é gravado em `webhook_events` **antes de qualquer processamento**. Resposta `200 OK` é dada imediatamente após a persistência.
- **Idempotência durável**: constraint única `(asaas_event_id, company_id)` na tabela. Redis serve como fast-path.
- **Processamento assíncrono** por worker separado, que lê de `webhook_events` em status `PENDING`.
- **Tolerância a eventos fora de ordem**: se um evento depende de um recurso ainda inexistente (ex.: `PAYMENT_RECEIVED` antes de `PAYMENT_CREATED`), o processamento marca o evento como `DEFERRED` e agenda retry com backoff. Após N tentativas, vai para DLQ — ver seção 8.5.
- Atualização do estado local da `charge` / `subscription` via service layer (que valida transições — seção 7.A).
- Disparo de eventos internos via **Outbox Pattern** (seção 9), não diretamente via `ApplicationEventPublisher` (que é in-memory e se perde em crash).

### 3.8. Módulo `reconciliation` (Conciliação)
- Job agendado (Spring `@Scheduled` ou Quartz) que faz polling no Asaas em busca de cobranças com status divergente
- Detecção de webhooks perdidos
- **Reprocessamento automático de eventos em DLQ** (alimenta a fila de retry da seção 8.5)
- Relatório de divergências

### 3.9. Módulo `notification` (Eventos para canais externos)
**Mudança importante em relação à versão anterior:** este módulo **não implementa** motor de templates, retry e multi-canal dentro da API Java. A API Java apenas **publica eventos de domínio** (`ChargeCreatedEvent`, `ChargePaidEvent`, `ChargeOverdueEvent`, `PlanChangedEvent`, `SubscriptionCanceledEvent`, etc.) na tabela `outbox` (seção 9).

Um motor de automação externo (**n8n**, na infraestrutura da holding) consome esses eventos e executa as réguas de cobrança e notificação:
- Disparo de WhatsApp via Evolution API
- Envio de e-mail transacional
- Notificações push
- Integrações com CRMs / ERPs

**Vantagens dessa abordagem:**
- A equipe de operações ajusta réguas de cobrança sem deploy da API.
- Templates ficam fora do código Java.
- Falhas de canal externo não derrubam a API de pagamento.
- Reuso do stack de automação que a holding já mantém.

A API ainda pode disparar notificações **nativas do Asaas** (que tem motor próprio de e-mail/SMS) quando isso atender ao caso de uso.

### 3.10. Módulo `report` (Relatórios e dashboard)
- Faturamento por empresa / período / método / **origin**
- MRR, churn, LTV das assinaturas
- Inadimplência
- Exportação CSV/Excel

### 3.11. Módulo `audit` (Auditoria)
- Log estruturado de toda operação sensível (criação de cobrança, alteração de credenciais, cancelamento, mudança de plano)
- Tabela `audit_log` com `actor`, `action`, `entity`, `before`, `after`, `timestamp`

---

## 4. Integrações com o Asaas

### 4.1. Endpoints principais a consumir (Asaas API v3 — `https://api.asaas.com/v3`)

| Recurso Asaas | Endpoint | Uso no sistema |
|---|---|---|
| Customers | `POST/GET/PUT/DELETE /customers` | Sincronização de clientes |
| Payments | `POST/GET /payments` | Cobranças avulsas e parceladas |
| Payments (estorno) | `POST /payments/{id}/refund` | Estorno |
| Pix QR Code | `GET /payments/{id}/pixQrCode` | Pix dinâmico |
| Payment Book (carnê) | `GET /payments/{id}/identificationField` | Linha digitável de boleto |
| Subscriptions | `POST/GET/PUT/DELETE /subscriptions` | Assinaturas recorrentes |
| Subscription invoices | `GET /subscriptions/{id}/payments` | Cobranças geradas pela assinatura |
| Installments | `POST /installments` | Parcelamentos |
| Credit Card Tokenization | `POST /creditCard/tokenize` | Tokenizar cartão para reuso |
| Webhook config | Painel Asaas | Configuração de eventos |
| Anticipations | `POST /anticipations` | Antecipação de recebíveis (opcional) |
| Transfers | `POST /transfers` | Saques para conta bancária (opcional) |
| Account balance | `GET /finance/balance` | Saldo da empresa |

### 4.2. Cliente Asaas (design)

Criar uma interface `AsaasClient` por recurso:

```
AsaasCustomerClient
AsaasPaymentClient
AsaasSubscriptionClient
AsaasInstallmentClient
AsaasWebhookClient
```

Cada chamada recebe o `tenant context` (de onde vem a `api_key`) — implementado via `RequestInterceptor` do Feign ou `ClientHttpRequestInterceptor` do `RestClient`. A `api_key` é injetada no header `access_token`.

Encapsular toda a lógica em um `AsaasGatewayService` que expõe operações de domínio (`createPixCharge`, `createCreditCardCharge`, `createSubscription` etc.) e isola o resto da aplicação dos DTOs do Asaas.

### 4.3. Eventos de webhook do Asaas a tratar

- `PAYMENT_CREATED`
- `PAYMENT_AWAITING_RISK_ANALYSIS`
- `PAYMENT_APPROVED_BY_RISK_ANALYSIS`
- `PAYMENT_REPROVED_BY_RISK_ANALYSIS`
- `PAYMENT_AUTHORIZED`
- `PAYMENT_UPDATED`
- `PAYMENT_CONFIRMED` (cartão aprovado, antes do crédito em conta)
- `PAYMENT_RECEIVED` (valor caiu na conta)
- `PAYMENT_OVERDUE`
- `PAYMENT_DELETED`
- `PAYMENT_RESTORED`
- `PAYMENT_REFUNDED`
- `PAYMENT_CHARGEBACK_REQUESTED`
- `PAYMENT_CHARGEBACK_DISPUTE`
- `PAYMENT_DUNNING_RECEIVED`
- `SUBSCRIPTION_CREATED`
- `SUBSCRIPTION_UPDATED`
- `SUBSCRIPTION_DELETED`

**Boas práticas (referenciar seção 8.5 para detalhes):** validar token do webhook, persistir o payload bruto **antes** de processar, garantir idempotência por `(event.id, company_id)`, processar de forma assíncrona, responder `200 OK` rapidamente, tolerar eventos fora de ordem.

---

## 5. Modelo de dados (núcleo)

```
companies (
  id, cnpj, razao_social, asaas_api_key_encrypted, asaas_env, webhook_token,
  status, plan_change_policy, downgrade_validation_strategy, grace_period_days,
  created_at, updated_at
)

users (id, company_id, email, password_hash, roles, ...)

customers (
  id, company_id, asaas_id, name, document, email, phone, address,
  credit_balance NUMERIC(12,2),
  deleted_at TIMESTAMP NULL,    -- soft delete
  created_at, updated_at
)

plans (
  id, company_id, name, value, cycle, trial_days, active, version,
  limits jsonb, features jsonb, tier_order int,
  deleted_at TIMESTAMP NULL,    -- soft delete
  created_at, updated_at
)

subscriptions (
  id, company_id, customer_id, plan_id, asaas_id, billing_type,
  current_period_start, current_period_end, next_due_date, status,
  version BIGINT,               -- optimistic lock
  created_at, updated_at
)

charges (
  id, company_id, customer_id, subscription_id, asaas_id, billing_type,
  value, due_date, status,
  origin VARCHAR(20),           -- WEB, PDV, RECURRING, API, BACKOFFICE, PLAN_CHANGE
  external_reference VARCHAR(100),
  pix_qrcode, boleto_url, invoice_url,
  installment_id, installment_number,
  created_at, updated_at
)

installments (id, company_id, customer_id, asaas_id, total_value, installment_count, billing_type, ...)

webhook_events (
  id, company_id, asaas_event_id, event_type,
  payload jsonb,
  status VARCHAR(20),           -- PENDING, PROCESSING, DEFERRED, PROCESSED, FAILED, DLQ
  attempt_count INT DEFAULT 0,
  next_attempt_at TIMESTAMP,
  processed_at TIMESTAMP,
  last_error TEXT,
  received_at TIMESTAMP,
  CONSTRAINT uq_webhook_event UNIQUE (asaas_event_id, company_id)
)

idempotency_keys (
  id, company_id, key VARCHAR(100),
  endpoint VARCHAR(100),
  request_hash VARCHAR(64),
  response_status INT,
  response_body jsonb,
  created_at, expires_at,
  CONSTRAINT uq_idempotency UNIQUE (company_id, endpoint, key)
)

outbox (
  id, company_id, aggregate_type, aggregate_id,
  event_type VARCHAR(100),
  payload jsonb,
  status VARCHAR(20),           -- PENDING, PUBLISHED, FAILED
  attempt_count INT DEFAULT 0,
  created_at, published_at, last_error
)

customer_credit_ledger (definido em 11.A.4)

subscription_plan_changes (definido em 11.A.7)

audit_log (id, company_id, actor, action, entity, entity_id, before jsonb, after jsonb, created_at)
```

Toda tabela transacional possui `company_id` para isolamento multi-tenant — garantido em **três níveis**: filtro de aplicação, RLS no banco (5.A) e constraints únicas que incluem `company_id`.

---

## 5.A. Row-Level Security (RLS) — isolamento multi-tenant no banco

O risco de um `WHERE company_id = ?` esquecido em uma query nativa, em um relatório, em um job de migração ou em um endpoint admin é alto demais para um sistema financeiro. O **PostgreSQL Row-Level Security** transforma "disciplina do desenvolvedor" em "garantia do banco de dados".

### Como funciona

1. Toda tabela transacional tem RLS habilitado:
   ```sql
   ALTER TABLE charges ENABLE ROW LEVEL SECURITY;
   ALTER TABLE charges FORCE ROW LEVEL SECURITY;
   ```
2. Uma policy filtra automaticamente por uma variável de sessão:
   ```sql
   CREATE POLICY tenant_isolation ON charges
     USING (company_id = current_setting('app.current_company_id')::bigint);
   ```
3. No início de **toda transação** Spring, um interceptor seta a variável:
   ```sql
   SET LOCAL app.current_company_id = '<id>';
   ```
4. A partir desse ponto, qualquer `SELECT`, `UPDATE`, `DELETE` na tabela só enxerga linhas do tenant — Hibernate, JDBC, query nativa, view, qualquer coisa.

### Implementação no Spring

- Componente `TenantContextInterceptor` (HandlerInterceptor) extrai o `company_id` do JWT/header e armazena em um `ThreadLocal` (via classe `TenantContext`).
- `DataSource` decorator ou `AspectJ aspect` em volta dos métodos `@Transactional` executa `SET LOCAL` antes da primeira query.
- Para jobs e workers (sem request HTTP), o componente que dispara o job é responsável por setar o tenant explicitamente antes de abrir transação.
- **Role do banco usado pela aplicação não pode ter `BYPASSRLS`.** Roles administrativas (migrations, DBA) usam um usuário separado.

### Operações cross-tenant legítimas

Algumas operações precisam ler múltiplos tenants (relatórios consolidados da holding, jobs de reconciliação global). Estratégias:

- Usar uma role de banco separada com `BYPASSRLS`, acessada apenas por código explicitamente marcado como `@CrossTenant`.
- Ou setar `app.current_company_id` em loop, um tenant por vez.

A escolha padrão é a primeira, com auditoria obrigatória de todo uso da role `BYPASSRLS`.

### Custos e mitigação

- **Debug fica menos óbvio**: queries "somem" resultados se o contexto não for setado. Mitigação: log de warning toda vez que uma query roda sem `app.current_company_id` definido (verificável via `current_setting('app.current_company_id', true) IS NULL`).
- **Migrações Flyway** rodam com role separada e RLS desativado para a sessão.
- **Testes de integração** com Testcontainers devem cobrir cenário de "tenant errado não enxerga dados".

---

## 5.B. Soft delete em entidades referenciadas

Entidades que aparecem em registros financeiros históricos (`customers`, `plans`) **nunca** podem sofrer `DELETE` físico. Um boleto emitido há 6 meses precisa continuar apontando para um cliente íntegro.

### Implementação

1. Coluna `deleted_at TIMESTAMP NULL` nas entidades afetadas.
2. No Hibernate 6, anotação `@SoftDelete` (nativa) ou `@SQLRestriction("deleted_at IS NULL")` para filtrar automaticamente nas queries da JPA.
3. **Índices únicos** (CPF, e-mail, etc.) precisam virar **índices parciais** no Postgres para permitir recadastro:
   ```sql
   CREATE UNIQUE INDEX uq_customer_doc_active
     ON customers (company_id, document)
     WHERE deleted_at IS NULL;
   ```
4. O método `delete()` no service vira `softDelete()`, que apenas seta `deleted_at = now()` e grava em `audit_log`.
5. Endpoint admin opcional `POST /customers/{id}/restore` para reverter.
6. Queries de relatório histórico que precisam ver dados deletados usam um repository alternativo (`@SQLRestriction` ignorado via `@FilterDef` ou query nativa).

### O que fazer com o Asaas

Quando o `customer` é soft-deletado localmente, **não chamar** `DELETE /customers/{id}` no Asaas — apenas marcar localmente. Se a empresa quiser remover do Asaas também, fazer separadamente após verificar que não há cobranças pendentes lá.

---

## 6. Principais funções / endpoints REST

Prefixo: `/api/v1`. Header obrigatório nos endpoints transacionais: `X-Company-Id` (ou derivado do JWT). Header opcional em todos os `POST` críticos: `Idempotency-Key`.

### 6.1. Empresas (apenas holding admin)
- `POST   /companies` — cadastrar empresa
- `GET    /companies` — listar
- `GET    /companies/{id}` — detalhar
- `PUT    /companies/{id}` — atualizar
- `PUT    /companies/{id}/credentials` — atualizar credenciais Asaas
- `POST   /companies/{id}/test-connection` — testar `api_key` no Asaas

### 6.2. Clientes
- `POST   /customers`
- `GET    /customers?search=...&page=...`
- `GET    /customers/{id}`
- `PUT    /customers/{id}`
- `DELETE /customers/{id}` — **soft delete**
- `POST   /customers/{id}/restore` — admin only
- `POST   /customers/{id}/sync` — força resync com Asaas
- `GET    /customers/{id}/credit-balance` — saldo + extrato

### 6.3. Planos
- `POST   /plans`
- `GET    /plans`
- `GET    /plans/{id}`
- `PUT    /plans/{id}`
- `PATCH  /plans/{id}/activate` / `/deactivate`
- `DELETE /plans/{id}` — **soft delete** (bloqueado se houver assinaturas ativas)

### 6.4. Assinaturas
- `POST   /subscriptions`
- `GET    /subscriptions?customerId=...&status=...`
- `GET    /subscriptions/{id}`
- `GET    /subscriptions/{id}/charges`
- `PUT    /subscriptions/{id}`
- `PATCH  /subscriptions/{id}/payment-method`
- `DELETE /subscriptions/{id}`
- `POST   /subscriptions/{id}/pause` / `/resume`
- `POST   /subscriptions/{id}/preview-change` (ver 11.A)
- `POST   /subscriptions/{id}/change-plan` (ver 11.A)
- `GET    /subscriptions/{id}/plan-changes`
- `DELETE /subscriptions/{id}/plan-changes/{changeId}`

### 6.5. Cobranças
- `POST   /charges/pix`
- `POST   /charges/boleto`
- `POST   /charges/credit-card`
- `POST   /charges/credit-card/installments`
- `POST   /charges/boleto/installments`
- `POST   /charges/undefined`
- `GET    /charges?status=...&dueDateFrom=...&dueDateTo=...&origin=...`
- `GET    /charges/{id}`
- `GET    /charges/{id}/pix-qrcode`
- `GET    /charges/{id}/boleto-line`
- `POST   /charges/{id}/refund`
- `DELETE /charges/{id}`
- `POST   /charges/{id}/resend-notification`

> Todos os `POST` de criação aceitam o campo `origin` no body (default `API` se não informado).

### 6.6. Webhook
- `POST   /webhooks/asaas` — endpoint público (token validado)
- `POST   /webhooks/asaas/replay/{eventId}` — admin, força reprocessamento

### 6.7. Conciliação e relatórios
- `POST   /reconciliation/run`
- `POST   /reconciliation/dlq/replay` — reprocessa DLQ
- `GET    /reports/revenue?from=...&to=...&groupBy=method|day|company|origin`
- `GET    /reports/subscriptions/mrr`
- `GET    /reports/subscriptions/churn`
- `GET    /reports/overdue`

---

## 7. Funções de domínio (camada de serviço)

### CompanyService
- `register(CompanyData)`
- `updateAsaasCredentials(companyId, apiKey, env)` — criptografa e valida com `GET /myAccount`
- `enable / disable / suspend`

### CustomerService
- `create(companyId, dto)` — cria local + cria no Asaas → grava `asaas_id`
- `update`, `softDelete`, `restore`
- `findOrCreate(document)` — usa CPF/CNPJ como chave natural
- `syncFromAsaas(asaasId)`

### PlanService
- `create / update / activate / deactivate / softDelete`
- `clonePlanForNewVersion`

### SubscriptionService
- `subscribe(companyId, customerId, planId, billingType, cardData?)`
- `cancel(subscriptionId, reason)`
- `changePlan(subscriptionId, newPlanId, prorate)` — delega a `PlanChangeService`
- `updatePaymentMethod`
- `handleOverdueSubscription` (job)
- `listChargesOf(subscriptionId)`

### ChargeService
- `createPixCharge(req)`
- `createBoletoCharge(req)`
- `createCreditCardCharge(req)`
- `createCreditCardInstallments(req, installmentCount)`
- `createBoletoInstallments(req, installmentCount)`
- `cancel(chargeId)`
- `refund(chargeId, value?)`
- `regenerateBoleto(chargeId)`
- `getPixQrCode(chargeId)`
- `markAsReceivedInCash(chargeId)`
- Toda transição de status passa por `ChargeStateValidator` (seção 7.A).

### WebhookService
- `receive(rawPayload, headers)` — valida token, persiste em `webhook_events`, retorna 200 imediatamente
- `process(eventId)` — idempotente, valida transição, atualiza estado, escreve no `outbox`
- `defer(eventId, reason, delaySeconds)` — para eventos fora de ordem
- `replayFailedEvents()`
- `moveToDLQ(eventId)`

### ReconciliationService
- `reconcileChargesSince(date)`
- `reconcileSubscriptions()`
- `replayDLQ()`

### PlanChangeService (ver 11.A)

### AsaasGatewayService (fachada do client)
Métodos espelhando as operações do Asaas, sempre recebendo `CompanyContext`.

### OutboxPublisher (ver seção 9)
- `publish(event)` — escreve em `outbox` na mesma transação do agregado
- Worker: `OutboxRelay` — lê pendentes, publica, marca como `PUBLISHED`

---

## 7.A. Validação de transição de estado (sem framework)

Transições inválidas em entidades financeiras (`REFUNDED → CONFIRMED`, `CANCELED → RECEIVED`) são bugs catastróficos: contabilidade quebra silenciosamente. Não vale o peso de uma máquina de estados completa (Spring State Machine etc.) — basta uma validação explícita e centralizada.

### Implementação

Para cada entidade que tem estados (`Charge`, `Subscription`, `PlanChange`), declarar um mapa estático de transições válidas e um método `transitionTo()`:

```java
public enum ChargeStatus {
  PENDING, CONFIRMED, RECEIVED, OVERDUE, REFUNDED, CHARGEBACK, CANCELED;

  private static final Map<ChargeStatus, Set<ChargeStatus>> ALLOWED = Map.of(
    PENDING,    Set.of(CONFIRMED, RECEIVED, OVERDUE, CANCELED),
    CONFIRMED,  Set.of(RECEIVED, REFUNDED, CHARGEBACK, CANCELED),
    RECEIVED,   Set.of(REFUNDED, CHARGEBACK),
    OVERDUE,    Set.of(RECEIVED, CONFIRMED, CANCELED),
    REFUNDED,   Set.of(),
    CHARGEBACK, Set.of(REFUNDED),
    CANCELED,   Set.of()
  );

  public boolean canTransitionTo(ChargeStatus target) {
    return ALLOWED.getOrDefault(this, Set.of()).contains(target);
  }
}
```

Na entidade ou no service:

```java
public void transitionTo(ChargeStatus target, String reason, String actor) {
  if (!this.status.canTransitionTo(target)) {
    throw new IllegalStateTransitionException(this.id, this.status, target);
  }
  this.status = target;
  auditLog.record(actor, "CHARGE_STATUS_CHANGE", this.id, oldStatus, target, reason);
}
```

### Vantagens

- 30 linhas de código, zero dependência nova.
- Falha **rápido e ruidoso** em vez de silencioso.
- Fácil de testar exaustivamente (tabela de cenários).
- O webhook handler chama `transitionTo()` em vez de `setStatus()` direto, e eventos com transição inválida vão para DLQ com motivo claro.

---

## 8. Fluxos críticos (sequência)

### 8.1. Criação de cobrança Pix
1. Sistema cliente → `POST /charges/pix` com `Idempotency-Key`
2. Middleware checa `idempotency_keys` (Redis fast-path → Postgres). Se já existe → retorna resposta cacheada.
3. `ChargeService` valida payload, resolve `Customer`
4. `AsaasGatewayService.createPayment` envia ao Asaas
5. Resposta do Asaas é persistida em `charges` com `origin = API` (ou o que veio no body)
6. Grava resposta em `idempotency_keys`
7. `AsaasGatewayService.getPixQrCode` busca QR Code
8. Resposta ao cliente: `{id, qrCode, copyPaste, expirationDate}`
9. Quando o pagador paga → Asaas envia `PAYMENT_RECEIVED` → webhook persiste, processa, valida transição, atualiza, escreve `ChargePaidEvent` no `outbox` → n8n consome e dispara notificação.

### 8.2. Assinatura com cartão de crédito
1. `POST /subscriptions` com dados do cartão (ou token salvo)
2. Tokenizar cartão via Asaas — **nunca persistir PAN localmente**
3. Criar `subscription` no Asaas
4. Persistir local + retornar primeira cobrança
5. Asaas gera novas cobranças → webhooks `PAYMENT_CREATED` e `PAYMENT_CONFIRMED` mantêm estado local

### 8.3. Parcelamento em cartão
1. `POST /charges/credit-card/installments`
2. Asaas cria um `installment` e N cobranças
3. Persistir `installments` + N `charges` vinculadas
4. Acompanhar cada parcela via webhook

### 8.4. Tratamento de inadimplência
1. Webhook `PAYMENT_OVERDUE` chega
2. `WebhookService` valida transição → atualiza para `OVERDUE`
3. Escreve `ChargeOverdueEvent` no `outbox`
4. n8n consome o evento, dispara régua de cobrança (WhatsApp + e-mail) conforme política da empresa
5. Após N falhas consecutivas → `SubscriptionService.suspend`

### 8.5. Processamento de webhook tolerante a desordem

Sistemas distribuídos não garantem ordem. `PAYMENT_RECEIVED` pode chegar **antes** de `PAYMENT_CREATED` por latência de rede ou retry do Asaas. O sistema precisa lidar com isso sem perder eventos nem corromper estado.

**Sequência:**

1. **Recepção (síncrona, < 100ms)**:
   - Endpoint público recebe POST do Asaas.
   - Valida token.
   - Insere em `webhook_events` com `status = PENDING`. A constraint `(asaas_event_id, company_id)` garante idempotência: duplicatas geram conflito e são respondidas com `200 OK` sem reprocessar.
   - Responde `200 OK` imediatamente.

2. **Processamento (assíncrono, worker separado)**:
   - Worker pega eventos com `status = PENDING` e `next_attempt_at <= now()`, ordenados por `received_at`.
   - Pega lock pessimista na linha (`SELECT ... FOR UPDATE SKIP LOCKED`) para permitir múltiplos workers em paralelo sem colisão.
   - Marca `status = PROCESSING`.

3. **Lookup do recurso**:
   - Para um evento `PAYMENT_*`, busca a `charge` pelo `asaas_id` do payload.
   - **Se não encontrar**: o evento chegou antes do recurso existir. Não é erro — é desordem.
     - `status = DEFERRED`
     - `attempt_count++`
     - `next_attempt_at = now() + backoff(attempt_count)` — backoff exponencial: 5s, 30s, 2min, 10min, 1h
     - `last_error = "Resource not found yet, deferred"`
     - Continua para o próximo evento.

4. **Processamento normal**:
   - Resolve a transição via `ChargeStateValidator`.
   - Se transição inválida: `status = FAILED`, grava erro, gera alerta. Não tenta de novo automaticamente (precisa de intervenção humana ou de chegada de outro evento).
   - Se válida: atualiza entidade, escreve no `outbox`, marca `status = PROCESSED, processed_at = now()`.

5. **DLQ (Dead Letter Queue)**:
   - Após `attempt_count >= MAX_ATTEMPTS` (ex.: 10) de `DEFERRED`, o evento vai para `status = DLQ`.
   - Alerta operacional dispara.
   - Endpoint admin `POST /webhooks/asaas/replay/{eventId}` permite reprocessamento manual.
   - Job de reconciliação (3.8) faz polling no Asaas para tentar resolver casos órfãos.

6. **Reconciliação cruzada**:
   - Quando uma `charge` é criada (via webhook `PAYMENT_CREATED` ou via API), o `WebhookService` faz uma checagem: existe algum evento `DEFERRED` para esse `asaas_id`? Se sim, marca `next_attempt_at = now()` para acelerar o processamento.

**Por que não usar `ApplicationEventPublisher` direto:** ele é in-memory. Se a JVM cair entre o `process()` e o consumidor do evento, o evento se perde. O outbox persiste antes, garante.

---

## 9. Outbox Pattern — eventos de domínio confiáveis

Toda mudança de estado relevante (cobrança paga, assinatura cancelada, plano alterado, cliente criado) precisa virar um evento que sistemas externos (n8n, BI, ERPs da holding) consomem. Fazer isso via `ApplicationEventPublisher` ou push direto para fila externa **dentro da transação de negócio** é frágil:

- Push para fila falha → transação ainda commita → evento perdido.
- Transação falha → push já foi feito → evento fantasma.

**Solução: Outbox Pattern.**

### Como funciona

1. Tabela `outbox` (definida em 5).
2. Toda operação de domínio que precisa publicar evento **escreve no `outbox` na mesma transação JPA** que altera o agregado:
   ```java
   @Transactional
   public void markAsPaid(Long chargeId) {
     Charge c = repo.findById(chargeId).orElseThrow();
     c.transitionTo(RECEIVED, "webhook", "asaas");
     outbox.publish("ChargePaidEvent", c.getId(), c.toEventPayload());
   }
   ```
3. Como é a mesma transação Postgres, **ou as duas coisas commitam, ou nenhuma**. Garantia atômica.
4. Worker `OutboxRelay` (Spring `@Scheduled` ou loop dedicado) lê eventos com `status = PENDING`, publica para o destino real (RabbitMQ / webhook HTTP de saída para n8n / Kafka), marca como `PUBLISHED`.
5. Falha de publicação → retry com backoff, log, e após N falhas vai para `FAILED` e gera alerta.

### Integração com n8n

O caminho mais simples: o `OutboxRelay` chama um **webhook HTTP do n8n** com o payload do evento. n8n já tem retry, fila e UI. Nenhuma fila de mensageria adicional precisa existir nesta primeira versão.

Quando o volume justificar, trocar o destino para RabbitMQ/Kafka sem mudar o produtor.

### Eventos publicados (lista inicial)

- `CustomerCreatedEvent`
- `ChargeCreatedEvent`
- `ChargePaidEvent`
- `ChargeOverdueEvent`
- `ChargeRefundedEvent`
- `ChargeCanceledEvent`
- `SubscriptionCreatedEvent`
- `SubscriptionCanceledEvent`
- `SubscriptionSuspendedEvent`
- `PlanChangedEvent`
- `PlanChangePendingPaymentEvent`

---

## 10. Resiliência e operação

### 10.1. Resilience4j
- Retry exponencial (3 tentativas) + circuit breaker no `AsaasClient`
- Timeouts explícitos: connect 3s, read 10s
- Bulkhead por endpoint crítico do Asaas

### 10.2. Idempotência híbrida (Redis + Postgres)

Idempotência **não pode** depender só de Redis: eviction, restart e falha de réplica podem perder a chave em momento crítico.

**Estratégia em camadas:**

1. **Camada 1 — Redis (fast-path)**:
   - Chave: `idem:{company_id}:{endpoint}:{idempotency_key}`
   - TTL: 24h
   - Valor: hash da request + status code + corpo da resposta serializado.
   - Hit → retorna direto sem tocar no banco. Latência ~1ms.

2. **Camada 2 — Postgres (durável, fonte de verdade)**:
   - Tabela `idempotency_keys` com constraint única `(company_id, endpoint, key)`.
   - **Toda criação grava primeiro no Postgres**, depois popula o Redis como cache.
   - Miss no Redis → consulta o Postgres → repopula o Redis → retorna.

3. **Camada 3 — Constraint de negócio**:
   - Para webhooks: `UNIQUE (asaas_event_id, company_id)` na tabela `webhook_events`. Mesmo que tudo acima falhe, a duplicata é rejeitada pelo banco.
   - Para `external_reference` em cobranças vindas de operações sensíveis (mudança de plano, etc.), idem.

**Comparação de hash da request**: o `idempotency_keys` guarda um hash do body. Se vier a mesma chave com body diferente → erro 422 (request mal formada), em vez de retornar a resposta antiga silenciosamente.

### 10.3. Locks no `customer_credit_ledger`

A tabela `customer_credit_ledger` é uma conta-corrente. Duas operações simultâneas tentando abater o mesmo crédito caracterizam clássica race condition.

**Decisão: lock pessimista** (`SELECT ... FOR UPDATE`) na linha `customers` (ou em uma linha de "saldo cacheado") antes de calcular o saldo e gravar o débito.

Por que pessimista e não otimista (`@Version`):
- Sob contenção real (job noturno processando 500 cobranças), retry storm de optimistic locking degrada performance.
- Pessimistic é previsível: a segunda transação espera, não falha.
- O escopo do lock é curto (uma operação de débito), então não há risco prático de deadlock.

Implementação:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM Customer c WHERE c.id = :id")
Customer lockForCreditOperation(@Param("id") Long id);
```

Para `subscription` (mudança de plano), usa-se **optimistic** com `@Version`, pois contenção é rara (dois pedidos simultâneos de mudança de plano na mesma assinatura é cenário excepcional, e falhar com 409 é resposta aceitável).

### 10.4. Outros
- **Dead Letter Queue** para webhooks (seção 8.5)
- **Health checks** (`/actuator/health`) com indicadores customizados (DB, Redis, Asaas, Outbox lag)
- **Métricas Prometheus**: `charges_created_total{method, company, origin}`, `charges_paid_total`, `webhook_processing_duration_seconds`, `webhook_deferred_total`, `webhook_dlq_total`, `outbox_lag_seconds`, `asaas_api_errors_total`, `idempotency_hit_total{layer=redis|postgres}`
- **Tracing** OpenTelemetry com correlação `traceId` em logs
- **Backups** diários do Postgres + PITR

---

## 11. Estrutura de pacotes sugerida

```
br.com.holding.payments
├── config            (Spring config, security, resilience, openapi)
├── common            (errors, utils, pagination)
├── idempotency       (filter, service, repository)
├── tenant            (CompanyContext, interceptors, RLS interceptor)
├── outbox            (entity, relay, publisher)
├── company
├── auth
├── customer
├── plan
├── subscription
├── charge
│   └── statemachine  (ChargeStatus, transition validator)
├── installment
├── webhook
│   ├── ingress       (controller + persist)
│   └── processing    (worker, deferral, DLQ)
├── reconciliation
├── notification      (apenas listeners do outbox / dispatcher para n8n)
├── report
├── audit
└── integration
    └── asaas
        ├── client
        ├── dto
        ├── mapper
        └── gateway
```

---

## 11.A. Mudança de plano (Upgrade / Downgrade) com cálculo pro-rata

> **Função crítica.** Cada empresa da holding terá entre 2 e 4 planos de assinatura, cada um com seus próprios **limites de uso** (ex.: nº de usuários, nº de agendamentos/mês, nº de unidades, volume de mensagens) e **features exclusivas**. Quando o cliente migra entre planos, o sistema precisa calcular **exatamente** o que já foi pago, o que ainda precisa ser pago (ou creditado), respeitar a forma de pagamento ativa e reagir corretamente tanto no banco local quanto no Asaas.

### 11.A.1. Conceitos e premissas

1. **Plano** tem: `valor`, `ciclo` (MONTHLY, QUARTERLY, etc.), `limites` (estruturados), `features` (lista de chaves), `versão`.
2. **Assinatura** tem: `plano atual`, `data de início do ciclo atual`, `data do próximo vencimento`, `método de pagamento`, `status`.
3. **Ciclo de cobrança** é o intervalo entre o último pagamento confirmado (`current_period_start`) e o próximo vencimento (`current_period_end`). Toda matemática pro-rata acontece dentro desse intervalo.
4. **Upgrade** = mudar para plano de valor maior. Gera cobrança imediata da diferença ou apenas eleva o valor a partir do próximo ciclo (configurável).
5. **Downgrade** = mudar para plano de valor menor. Em geral **não** gera reembolso em dinheiro — gera **crédito** que abate cobranças futuras, ou só passa a valer no próximo ciclo.
6. **Sidegrade** = troca entre planos de mesmo valor. Aplica a regra de features/limites imediatamente, sem efeito financeiro.
7. **Política por empresa** (configurável em `companies.plan_change_policy`):
   - `IMMEDIATE_PRORATA` — aplica imediatamente e cobra/credita a diferença pro-rata
   - `END_OF_CYCLE` — agenda a troca para o próximo vencimento
   - `IMMEDIATE_NO_PRORATA` — aplica imediatamente sem ajuste financeiro

### 11.A.2. Fórmula pro-rata

Sejam:
- `V_atual` = valor do plano atual
- `V_novo` = valor do plano novo
- `D_total` = dias totais do ciclo atual
- `D_restantes` = dias restantes até `current_period_end`
- `Credito_nao_usado` = `V_atual * (D_restantes / D_total)`
- `Custo_novo_proporcional` = `V_novo * (D_restantes / D_total)`

**Diferença:**
```
Delta = Custo_novo_proporcional - Credito_nao_usado
```

- `Delta > 0` → **upgrade**: gerar cobrança avulsa de `Delta`
- `Delta < 0` → **downgrade**: gerar `account_credit` de `|Delta|`
- `Delta == 0` → apenas troca features/limites

A `current_period_end` **não muda**.

> **Arredondamento:** sempre `BigDecimal` com `RoundingMode.HALF_EVEN` e 2 casas decimais. **Nunca `double`**.

### 11.A.3. Regras por método de pagamento

| Método ativo | Upgrade (Delta > 0) | Downgrade (Delta < 0) |
|---|---|---|
| **Cartão de crédito** | Cobrança imediata. Recusa → reverte. | Gera `account_credit`. |
| **Pix** | Cobrança Pix vencimento curto, estado `PENDING_UPGRADE_PAYMENT`. | Gera `account_credit`. |
| **Boleto** | Boleto curto, mesma lógica de pendência. | Gera `account_credit`. |
| **Débito** | Igual ao crédito. | Igual ao crédito. |

### 11.A.4. Conta-corrente do cliente (`customer_credit_ledger`)

```
customer_credit_ledger (
  id, company_id, customer_id,
  type ENUM(CREDIT, DEBIT),
  origin ENUM(DOWNGRADE_PRORATA, MANUAL_ADJUSTMENT, REFUND, CHARGE_APPLIED),
  reference_id,
  amount NUMERIC(12,2),
  balance_after NUMERIC(12,2),
  created_at, created_by,
  description
)
```

- **Append-only**: nunca atualizar/deletar.
- Saldo = soma `CREDIT - DEBIT`, cacheado em `customers.credit_balance`.
- **Acesso ao saldo é sempre via lock pessimista** (`SELECT ... FOR UPDATE`) na linha `customers` correspondente — ver seção 10.3.
- Crédito **nunca vira dinheiro** automaticamente.

### 11.A.5. Validação de limites no downgrade

Antes de aceitar um downgrade, validar uso atual contra limites do plano menor.

**Estratégias por empresa:**
1. **BLOCK** — recusa, retorna lista de violações.
2. **SCHEDULE** — efetiva no fim do ciclo.
3. **GRACE_PERIOD** — efetiva imediatamente, dá N dias para ajustar.

`PlanLimitsValidator` consulta `currentUsage` via SPI/event do sistema cliente.

### 11.A.6. Versionamento e idempotência

- Tabela `subscription_plan_changes` registra cada solicitação.
- Endpoint aceita `Idempotency-Key`.
- Mudanças `SCHEDULED` são processadas por job no vencimento.

### 11.A.7. Modelo de dados (acréscimos)

```
subscription_plan_changes (
  id, company_id, subscription_id,
  previous_plan_id, requested_plan_id,
  change_type ENUM(UPGRADE, DOWNGRADE, SIDEGRADE),
  policy ENUM(IMMEDIATE_PRORATA, END_OF_CYCLE, IMMEDIATE_NO_PRORATA),
  delta_amount NUMERIC(12,2),
  proration_credit NUMERIC(12,2),
  proration_charge NUMERIC(12,2),
  status ENUM(PENDING, AWAITING_PAYMENT, EFFECTIVE, SCHEDULED, FAILED, CANCELED),
  charge_id (FK charges, nullable),
  credit_ledger_id (FK customer_credit_ledger, nullable),
  scheduled_for timestamp,
  effective_at timestamp,
  requested_by, requested_at,
  failure_reason text
)
```

### 11.A.8 a 11.A.15 — endpoints, services, integração Asaas, fluxos passo-a-passo, casos de borda, testes e métricas

(Mantidos integralmente da versão anterior do documento; nenhuma alteração além da incorporação da validação de transição via `PlanChangeStatus.canTransitionTo()` — seção 7.A — e do uso obrigatório de lock pessimista no `customer_credit_ledger` — seção 10.3.)

**Recap rápido dos pontos críticos:**
- `previewChange` → `requestChange` → (cobrança Delta paga) → `confirmAfterPayment`
- Cobertura de testes próxima de 100%, incluindo property-based no `ProrationCalculator` (jqwik)
- Casos de borda: dois upgrades seguidos, downgrade com saldo > próxima cobrança, expiração do Pix do Delta, plano desativado entre preview e confirm, etc.
- Métricas: `plan_changes_total`, `plan_change_proration_amount`, `plan_change_payment_pending_seconds`, `customer_credit_balance_total`

---

## 12. Roadmap detalhado de implementação

Cada fase tem objetivo claro, entregáveis verificáveis, critério de saída ("definition of done") e dependências. As fases são sequenciais nas dependências, mas algumas tarefas podem ser paralelizadas.

### **Fase 0 — Setup e fundações de infraestrutura** (1 semana)

**Objetivo:** ter um projeto Spring Boot rodando localmente e em staging, com toda a infraestrutura básica funcionando.

**Entregáveis:**
- Repositório Git + estrutura de pacotes (seção 11)
- `pom.xml` / `build.gradle` com dependências base (Spring Boot, JPA, Flyway, Resilience4j, Springdoc, Testcontainers, jqwik, Lombok, MapStruct)
- `Dockerfile` + `docker-compose.yml` (app + Postgres 16 + Redis)
- Pipeline CI básico (build + testes unitários) — GitHub Actions ou GitLab CI
- `application.yml` por profile (dev, staging, prod) com config externalizada
- Flyway configurado, primeira migration `V1__init.sql` vazia ou com schema base
- `springdoc-openapi` em `/swagger-ui.html`
- Logback estruturado em JSON

**Definition of done:**
- `mvn verify` passa
- `docker compose up` sobe app + banco + redis
- `/actuator/health` retorna 200
- Swagger acessível

---

### **Fase 1 — Multi-tenancy, segurança e RLS** (2 semanas)

**Objetivo:** garantir o isolamento entre empresas no nível do banco antes de qualquer dado financeiro entrar em cena. **Esta fase é a base de todo o resto** — fazer depois é caro.

**Entregáveis:**
- Migration com tabelas `companies`, `users`, `audit_log`
- Migration habilitando RLS nas tabelas que vão receber `company_id` (ainda que vazias)
- Policies de RLS para cada tabela transacional
- `TenantContext` (ThreadLocal) + `TenantContextInterceptor` (HTTP)
- `DataSource` decorator que executa `SET LOCAL app.current_company_id` em toda transação
- Spring Security 6 + JWT (login, refresh, logout)
- Roles (`HOLDING_ADMIN`, `COMPANY_ADMIN`, `COMPANY_OPERATOR`, `SYSTEM`)
- Módulo `company`: CRUD completo + criptografia da `asaas_api_key` (Jasypt + chave em variável de ambiente)
- Endpoint `POST /companies/{id}/test-connection` (chama `GET /myAccount` no Asaas)
- Módulo `audit`: tabela + service + aspecto que loga toda operação sensível
- Role de banco separada para Flyway (com `BYPASSRLS`) e role da aplicação (sem)

**Testes obrigatórios:**
- Teste de integração com Testcontainers que cria duas empresas, cria dados em cada, e prova que uma **não enxerga** os dados da outra mesmo com query nativa
- Teste de tentativa de acesso sem `company_id` setado → query retorna vazio + warning logado
- Teste de criptografia: `asaas_api_key` no banco está cifrada, em memória está clara

**Definition of done:**
- RLS validado por testes automatizados
- Login + JWT funcionando
- Cadastro de empresa funciona ponta a ponta com criptografia e teste de conexão

---

### **Fase 2 — Cliente Asaas e fundações de resiliência** (1 semana)

**Objetivo:** ter um cliente HTTP robusto e testável para o Asaas, isolado do resto da aplicação.

**Entregáveis:**
- Estrutura `integration/asaas/{client,dto,mapper,gateway}`
- Interceptor que injeta `access_token` baseado no `CompanyContext`
- Implementação de `AsaasCustomerClient`, `AsaasPaymentClient`, `AsaasSubscriptionClient`, `AsaasInstallmentClient`
- `AsaasGatewayService` (fachada de domínio)
- Resilience4j: retry exponencial + circuit breaker + timeouts
- Mock do Asaas via WireMock para testes
- Tratamento padronizado de erros do Asaas → exceções da aplicação

**Definition of done:**
- Testes de integração com WireMock cobrindo: sucesso, 4xx, 5xx, timeout, circuit breaker abrindo
- `AsaasGatewayService` não vaza DTO do Asaas para o resto da aplicação

---

### **Fase 3 — Idempotência e Outbox (fundações transversais)** (1 semana)

**Objetivo:** ter os dois mecanismos transversais prontos antes que qualquer endpoint financeiro seja escrito.

**Entregáveis:**
- Tabela `idempotency_keys`
- Filtro Spring que intercepta `POST` com `Idempotency-Key` (Redis fast-path → Postgres → executa handler → grava resposta)
- Validação de hash de body (mesma chave + body diferente → 422)
- Tabela `outbox`
- `OutboxPublisher` (insere na mesma transação)
- `OutboxRelay` (worker `@Scheduled` que publica eventos pendentes via webhook HTTP para n8n)
- Métricas: `idempotency_hit_total`, `outbox_lag_seconds`, `outbox_failed_total`
- Endpoint admin para inspecionar outbox (`GET /admin/outbox?status=...`)

**Testes:**
- Idempotência: dois requests idênticos retornam exatamente a mesma resposta sem dupla escrita
- Idempotência: mesma chave + body diferente → 422
- Outbox: simular crash entre commit e publish → relay publica no próximo ciclo
- Outbox: falha no destino → retry com backoff → DLQ após N tentativas

---

### **Fase 4 — Customers e Plans** (1–2 semanas)

**Objetivo:** entidades de cadastro com soft delete e sincronização Asaas.

**Entregáveis:**
- Tabela `customers` com `deleted_at` + índice único parcial
- `CustomerService` com `create`, `update`, `softDelete`, `restore`, `findOrCreate`, `syncFromAsaas`
- Sincronização bidirecional com `/customers` do Asaas
- Tabela `plans` com `limits jsonb`, `features jsonb`, `tier_order`, `deleted_at`
- `PlanService` com versionamento (`clonePlanForNewVersion`)
- Eventos no outbox: `CustomerCreatedEvent`
- Endpoints REST completos das seções 6.2 e 6.3

**Testes:**
- Soft delete preserva integridade referencial
- Recadastro de CPF após soft delete funciona (índice parcial)
- Sync bidirecional Asaas com WireMock

---

### **Fase 5 — Charges e validação de transição de estado** (2 semanas)

**Objetivo:** o coração transacional do sistema — cobranças avulsas funcionando ponta a ponta, com estados validados.

**Entregáveis:**
- Tabela `charges` com `origin`, `external_reference`, `version`
- Enum `ChargeStatus` com `canTransitionTo()` (seção 7.A)
- `ChargeService` com todos os métodos da seção 7
- Endpoints `POST /charges/pix`, `/boleto`, `/credit-card`, `/credit-card/installments`, `/boleto/installments`, `/undefined`
- `GET /charges` com filtros incluindo `origin`
- Operações de cancelamento, refund, regenerate boleto, get pix qr
- Tabela `installments` + integração
- Eventos no outbox: `ChargeCreatedEvent`, `ChargeRefundedEvent`, `ChargeCanceledEvent`
- Tokenização de cartão (sem persistir PAN)

**Testes:**
- Tabela exaustiva de transições válidas/inválidas para `ChargeStatus`
- Tentativa de transição inválida → exceção + nada gravado
- Todos os fluxos de criação com WireMock simulando Asaas
- Idempotência ponta a ponta nos endpoints `POST /charges/*`

---

### **Fase 6 — Webhook ingress + processamento tolerante a desordem** (2 semanas)

**Objetivo:** receber eventos do Asaas de forma confiável, mesmo fora de ordem.

**Entregáveis:**
- Tabela `webhook_events` com constraint única e campos de retry
- Endpoint `POST /webhooks/asaas` (validação de token, persistência bruta, resposta 200 imediata)
- Worker `WebhookProcessor` que processa eventos com `SELECT ... FOR UPDATE SKIP LOCKED`
- Lógica de `DEFERRED` + backoff exponencial (seção 8.5)
- Movimentação para DLQ após N tentativas
- Endpoint admin `POST /webhooks/asaas/replay/{eventId}`
- Eventos publicados no outbox conforme conversão (`PAYMENT_RECEIVED` → `ChargePaidEvent`, etc.)
- Métricas `webhook_processing_duration_seconds`, `webhook_deferred_total`, `webhook_dlq_total`
- Reconciliação cruzada: ao criar uma `charge`, marcar eventos `DEFERRED` desse `asaas_id` como prontos para retry

**Testes:**
- Recepção de evento duplicado → ignorado pela constraint, 200 OK
- `PAYMENT_RECEIVED` antes de `PAYMENT_CREATED` → DEFERRED → reprocessa após delay → sucesso
- Transição inválida do payload → FAILED + alerta
- Múltiplos workers consumindo em paralelo sem colisão
- Replay manual via endpoint admin

---

### **Fase 7 — Subscriptions e parcelamentos** (2 semanas)

**Objetivo:** assinaturas recorrentes funcionando, com tratamento de inadimplência.

**Entregáveis:**
- Tabela `subscriptions` com `version` (optimistic lock)
- `SubscriptionService` completo (`subscribe`, `cancel`, `pause`, `resume`, `updatePaymentMethod`)
- Validação de transição de estado (`SubscriptionStatus.canTransitionTo()`)
- Job `handleOverdueSubscription` (suspende após N tentativas)
- Webhooks `SUBSCRIPTION_*` processados
- Eventos no outbox: `SubscriptionCreatedEvent`, `SubscriptionCanceledEvent`, `SubscriptionSuspendedEvent`
- Parcelamentos cartão e boleto ponta a ponta

---

### **Fase 8 — Mudança de plano com pro-rata (seção 11.A)** (3 semanas)

**Objetivo:** a função mais complexa do sistema, totalmente testada.

**Entregáveis:**
- Tabelas `subscription_plan_changes` e `customer_credit_ledger`
- `ProrationCalculator` (puro, sem dependências)
- `PlanLimitsValidator` com SPI para consumir uso atual do sistema cliente
- `CustomerCreditLedgerService` com lock pessimista (seção 10.3)
- `PlanChangeService` (`previewChange`, `requestChange`, `confirmAfterPayment`, `cancelChange`, `processScheduledChanges`)
- Endpoints da seção 11.A.8
- Job diário para `processScheduledChanges`
- Integração com `WebhookService` (ao receber `PAYMENT_RECEIVED` de uma cobrança vinculada a `plan_change`, chama `confirmAfterPayment`)
- Eventos no outbox: `PlanChangedEvent`, `PlanChangePendingPaymentEvent`
- Métricas e auditoria específicas (seção 11.A.15)

**Testes (cobertura próxima de 100%):**
- Property-based tests do `ProrationCalculator` com jqwik
- Tabela de 20+ cenários unitários
- Testes de concorrência: dois `change-plan` simultâneos → apenas um vence
- Todos os 10 casos de borda da seção 11.A.13
- Webhook fora de ordem para cobranças de Delta

---

### **Fase 9 — Reconciliação, relatórios e dashboards** (1–2 semanas)

**Entregáveis:**
- Job de reconciliação (`reconcileChargesSince`, `reconcileSubscriptions`)
- Job de replay de DLQ
- Relatórios da seção 6.7 (revenue, MRR, churn, overdue) com agrupamento por `origin`
- Exportação CSV/Excel
- Dashboards Grafana base (volume de cobranças, taxa de aprovação, lag do outbox, DLQ size, latência do Asaas)
- Alertas Prometheus (DLQ > 0 por mais de 1h, outbox lag > 5min, taxa de erro > 5%, circuit breaker aberto)

---

### **Fase 10 — Hardening, carga e produção** (2 semanas)

**Entregáveis:**
- Testes de carga (k6 ou Gatling) simulando picos de criação de cobrança e enxurradas de webhooks
- Pen test (interno ou contratado)
- Revisão de logs em busca de PII vazada
- Documentação OpenAPI revisada e publicada
- Runbooks operacionais escritos:
  - "Asaas fora do ar"
  - "Webhook caiu / DLQ crescendo"
  - "Divergência de saldo no ledger"
  - "Mudança de plano travada em AWAITING_PAYMENT"
  - "Outbox parado / lag alto"
  - "RLS bloqueando query legítima"
- Plano de disaster recovery validado (restore de backup em ambiente isolado)
- Política de LGPD documentada e revisada
- Treinamento das equipes consumidoras da API

---

### Resumo de cronograma estimado

| Fase | Duração | Acumulado |
|----|----|----|
| 0 — Setup | 1 sem | 1 sem |
| 1 — Multi-tenant + RLS | 2 sem | 3 sem |
| 2 — Cliente Asaas | 1 sem | 4 sem |
| 3 — Idempotência + Outbox | 1 sem | 5 sem |
| 4 — Customers + Plans | 1–2 sem | ~7 sem |
| 5 — Charges | 2 sem | ~9 sem |
| 6 — Webhooks tolerantes | 2 sem | ~11 sem |
| 7 — Subscriptions | 2 sem | ~13 sem |
| 8 — Mudança de plano | 3 sem | ~16 sem |
| 9 — Reconciliação + relatórios | 1–2 sem | ~18 sem |
| 10 — Hardening | 2 sem | ~20 sem |

**Total estimado: ~5 meses** para uma entrega completa e robusta. Algumas fases podem ser paralelizadas com mais devs (Fase 4 + Fase 2, por exemplo).

---

## 13. Checklist final antes de produção

### Segurança e credenciais
- [ ] `asaas_api_key` criptografada em repouso (Jasypt + chave em vault, nunca em código nem em `application.yml` versionado)
- [ ] Todos os secrets em vault (Vault, AWS Secrets Manager, Doppler) — incluindo JWT secret, chave Jasypt, credenciais de banco, token de webhook
- [ ] TLS 1.2+ obrigatório em todos os endpoints
- [ ] Rate limiting por `company_id` e por IP configurado (Bucket4j ou gateway)
- [ ] CORS restrito aos domínios oficiais da holding
- [ ] Política LGPD documentada (consentimento, base legal, retenção, endpoint de exclusão)
- [ ] Pen test executado e correções aplicadas

### Multi-tenancy e RLS
- [ ] RLS habilitado em **todas** as tabelas com `company_id`
- [ ] Policy de isolamento aplicada em todas elas
- [ ] Role da aplicação **sem** `BYPASSRLS`
- [ ] Role do Flyway separada, com `BYPASSRLS`, usada apenas em migrations
- [ ] Teste automatizado de cross-tenant rodando em CI
- [ ] Toda operação `@CrossTenant` documentada e auditada
- [ ] Warning logado quando uma query roda sem `app.current_company_id` setado

### Integridade de dados
- [ ] Soft delete implementado em `customers` e `plans`
- [ ] Índices únicos parciais (`WHERE deleted_at IS NULL`) para permitir recadastro
- [ ] `customer_credit_ledger` é append-only (sem `UPDATE`/`DELETE` no código)
- [ ] Lock pessimista no acesso ao saldo de crédito
- [ ] Optimistic lock (`@Version`) em `subscriptions`
- [ ] Validação de transição de estado em `Charge`, `Subscription`, `PlanChange`
- [ ] Todos os valores monetários em `BigDecimal` com `HALF_EVEN`, nunca `double`

### Idempotência e webhooks
- [ ] Tabela `idempotency_keys` com constraint única
- [ ] Redis como fast-path, Postgres como fonte de verdade
- [ ] Validação de hash de body em chaves repetidas
- [ ] Idempotência cobrindo todos os `POST` críticos (`/charges/*`, `/subscriptions/*`, `/customers`, `/change-plan`)
- [ ] `webhook_events` com constraint única `(asaas_event_id, company_id)`
- [ ] Endpoint público de webhook responde em < 200ms (apenas persiste)
- [ ] Worker de processamento separado, com `SKIP LOCKED`
- [ ] Eventos `DEFERRED` reprocessados com backoff exponencial
- [ ] DLQ funcionando + alerta quando `dlq_size > 0`
- [ ] Endpoint admin de replay manual testado

### Outbox e eventos
- [ ] Tabela `outbox` em uso
- [ ] `OutboxPublisher` chamado dentro da mesma transação dos agregados
- [ ] `OutboxRelay` rodando como worker
- [ ] Métrica `outbox_lag_seconds` com alerta
- [ ] n8n configurado para consumir os webhooks do relay
- [ ] Réguas de cobrança (WhatsApp, e-mail) montadas no n8n e testadas

### Asaas e resiliência
- [ ] Credenciais Asaas (sandbox e produção) testadas via `POST /companies/{id}/test-connection`
- [ ] Webhook configurado no painel Asaas com token forte e único por empresa
- [ ] Retry + circuit breaker + timeout configurados em todos os clients
- [ ] Bulkhead nos endpoints críticos do Asaas
- [ ] Tratamento explícito de erros 4xx vs 5xx do Asaas

### Observabilidade
- [ ] Logs estruturados em JSON, sem PII (CPF/CNPJ/cartão mascarados)
- [ ] `traceId` propagado em logs e respostas
- [ ] Métricas Prometheus expostas em `/actuator/prometheus`
- [ ] Métricas críticas: `charges_created_total`, `charges_paid_total`, `webhook_processing_duration_seconds`, `webhook_dlq_total`, `outbox_lag_seconds`, `asaas_api_errors_total`, `idempotency_hit_total`, `plan_changes_total`, `customer_credit_balance_total`
- [ ] Dashboards Grafana publicados (volume, latência, erros, lag, DLQ)
- [ ] Alertas configurados:
  - DLQ > 0 por mais de 1h
  - Outbox lag > 5min
  - Taxa de erro 5xx > 5%
  - Circuit breaker do Asaas aberto
  - Saldo de crédito divergente entre `ledger` e `customers.credit_balance`

### Testes
- [ ] Cobertura > 80% em services críticos
- [ ] Cobertura > 95% no `ProrationCalculator` e `PlanChangeService`
- [ ] Property-based tests no `ProrationCalculator` (jqwik)
- [ ] Testes de integração com Testcontainers (Postgres real, com RLS ativo)
- [ ] Testes de integração com WireMock simulando Asaas
- [ ] Testes de webhook fora de ordem
- [ ] Testes de concorrência em mudança de plano
- [ ] Testes de carga (k6/Gatling) com pico esperado + 50% de margem

### Operação
- [ ] Migrations Flyway versionadas e revisadas
- [ ] Backup diário do Postgres + PITR validado
- [ ] Restore de backup testado em ambiente isolado
- [ ] Health checks customizados (DB, Redis, Asaas, outbox lag)
- [ ] Documentação OpenAPI completa publicada
- [ ] Runbooks operacionais escritos e revisados:
  - Asaas fora do ar
  - DLQ crescendo
  - Outbox parado
  - Divergência no ledger
  - Mudança de plano travada
  - RLS bloqueando query legítima
- [ ] Política de rotação de credenciais Asaas documentada
- [ ] Sistemas consumidores treinados / com SDK ou exemplos prontos

---

**Próximos passos recomendados:** validar este escopo, priorizar Fase 0 + Fase 1 (que destravam tudo), e em seguida posso gerar (a) o `pom.xml` / `build.gradle` inicial com todas as dependências, (b) o esqueleto de pacotes e classes base (config, security, multi-tenant, RLS interceptor, AsaasClient, Outbox, Idempotency), ou (c) o detalhamento de uma fase específica com código de exemplo. É só pedir.
