# API Reference — Payment API

> Documento de integração com descrição detalhada de todos os endpoints agrupados por controller. Para integração sistema-a-sistema, use API Keys (`X-API-Key`). Para usuários, use JWT (`Authorization: Bearer`).

## Convenções gerais

### Autenticação
Dois mecanismos mutuamente exclusivos:
- **JWT Bearer** — `Authorization: Bearer <token>` obtido via `POST /api/v1/auth/login`.
- **API Key** — `X-API-Key: pk_...` criada via `POST /api/v1/api-keys`. Só o hash SHA-256 fica persistido; guarde a chave bruta.

### Multi-tenant (RLS)
Todas as rotas de domínio exigem o header `X-Company-Id: <id>` (exceto as explicitamente públicas). O valor é gravado em `ThreadLocal` e aplicado como `SET LOCAL app.current_company_id` antes de cada query, ativando Row-Level Security no Postgres. Requests cross-tenant retornam 404.

### Idempotência
POSTs que criam recursos monetários (`/charges`, `/subscriptions`, `/plan-changes`) aceitam o header `Idempotency-Key: <uuid>`. A mesma chave devolve a mesma resposta (Redis fast-path + fallback Postgres). Recomendado para evitar cobranças duplicadas em retries.

### Roles
- `ROLE_HOLDING_ADMIN` — admin global (visualiza todas as empresas).
- `ROLE_COMPANY_ADMIN` — admin de uma empresa.
- `ROLE_COMPANY_OPERATOR` — operações do dia-a-dia (sem gestão de acessos).
- `ROLE_SYSTEM` — integrações sistema-a-sistema.

### Códigos HTTP comuns
| Status | Significado |
|---|---|
| 200 | OK |
| 201 | Created |
| 204 | No Content |
| 400 | Payload inválido (Bean Validation) |
| 401 | Não autenticado / token expirado |
| 403 | Autenticado mas sem permissão |
| 404 | Recurso inexistente ou cross-tenant |
| 409 | Conflito de estado / idempotência |
| 422 | Regra de negócio violada |
| 502 | Falha no gateway Asaas |

### Endpoints públicos (sem autenticação)
- `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`
- `POST /api/v1/webhooks/asaas` (validação via token do Asaas)
- `POST /api/v1/coupons/validate/public` (precisa do header `X-Company-Id`)
- `GET /actuator/health`, `/actuator/info`, `/actuator/prometheus`
- `/swagger-ui/**`, `/v3/api-docs/**`

### Base URL
`https://<host>` — Swagger UI em `/swagger-ui.html`.

---

## Sumário

- [Autenticação (AuthController)](#autenticacao-authcontroller)
- [API Keys (ApiKeyController)](#api-keys-apikeycontroller)
- [Política de Acesso (AccessPolicyController)](#politica-de-acesso-accesspolicycontroller)
- [Validação de Acesso do Cliente (CustomerAccessController)](#validacao-de-acesso-do-cliente-customeraccesscontroller)
- [Empresas (CompanyController)](#companycontroller)
- [Clientes (CustomerController)](#customercontroller)
- [Planos (PlanController)](#plancontroller)
- [Assinaturas (SubscriptionController)](#subscriptioncontroller)
- [Cobranças (ChargeController)](#cobrancas-chargecontroller)
- [Mudança de Plano (PlanChangeController)](#planchangecontroller)
- [Cupons (CouponController)](#cupons-couponcontroller)
- [Validação de Cupom (CouponValidationController)](#validacao-de-cupom-couponvalidationcontroller)
- [Webhooks Asaas (WebhookController)](#webhookcontroller)
- [Admin de Webhooks (WebhookAdminController)](#webhookadmincontroller)
- [Outbox Admin (OutboxAdminController)](#outboxadmincontroller)
- [Reconciliação (ReconciliationController)](#reconciliationcontroller)
- [Relatórios (ReportController)](#reportcontroller)
- [Export de Relatórios (ReportExportController)](#reportexportcontroller)
- [Importação de Dados (DataImportController)](#dataimportcontroller)

---

## Autenticacao (AuthController)

### POST /api/v1/auth/login

**Path:** `POST /api/v1/auth/login`

**Autenticação/Autorização:** Público (listado em `permitAll` do `SecurityConfig`).

**Headers:** nenhum obrigatório.

**Request (LoginRequest):**

| Campo | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| email | String | sim | @NotBlank, @Email | E-mail do usuário |
| password | String | sim | @NotBlank | Senha em texto-puro |

**Regras de negócio:**
- Usuário deve existir e estar `active=true`.
- Senha comparada via `BCryptPasswordEncoder`.
- Por segurança, qualquer falha retorna a mesma mensagem genérica.
- Access token expira em 24h (`app.jwt.expiration-ms`).
- Refresh token expira em 7d (`app.jwt.refresh-expiration-ms`).

**Exemplo request:**
```json
{"email": "admin@empresa.com.br", "password": "SenhaSegura123!"}
```

**Response (LoginResponse):**

| Campo | Tipo | Descrição |
|---|---|---|
| accessToken | String | JWT a enviar em `Authorization: Bearer ...` |
| refreshToken | String | Token de renovação |
| expiresIn | Long | Tempo de expiração do access em ms |
| tokenType | String | Sempre `Bearer` |

**Exemplo response:**
```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "expiresIn": 86400000,
  "tokenType": "Bearer"
}
```

**Códigos HTTP:** 200 OK · 400 campos ausentes/e-mail inválido · 401 credenciais inválidas.

**Observações:** não há diferenciação entre "usuário não existe" e "senha errada". Não há rate limit embutido.

---

### POST /api/v1/auth/refresh

**Path:** `POST /api/v1/auth/refresh`

**Autenticação:** Público.

**Request (RefreshRequest):**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| refreshToken | String | sim (@NotBlank) | Refresh JWT recebido no login |

**Regras:**
- Refresh deve estar dentro da validade e associado a usuário ativo.
- Gera novo par access+refresh (rotação).

**Exemplo request:**
```json
{"refreshToken": "eyJhbGciOi..."}
```

**Response:** idem `LoginResponse`.

**Códigos HTTP:** 200 · 400 · 401 (expirado/revogado/usuário inativo).

---

### POST /api/v1/auth/users

**Path:** `POST /api/v1/auth/users`

**Autorização:** JWT obrigatório. Roles: `HOLDING_ADMIN` ou `COMPANY_ADMIN`.

**Request (CreateUserRequest):**

| Campo | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| companyId | Long | sim | @NotNull | Empresa do usuário |
| email | String | sim | @NotBlank, @Email | E-mail único |
| password | String | sim | @NotBlank, @Size(min=8) | Senha em texto-puro |
| name | String | sim | @NotBlank | Nome completo |
| roles | Set&lt;Role&gt; | sim | @NotNull | Roles: `ROLE_HOLDING_ADMIN`, `ROLE_COMPANY_ADMIN`, `ROLE_COMPANY_OPERATOR`, `ROLE_SYSTEM` |

**Regras:**
- E-mail único globalmente.
- Empresa deve existir (404).
- Senha hasheada com BCrypt.
- Usuário criado com `active=true`.

**Exemplo request:**
```json
{
  "companyId": 3,
  "email": "operador@empresa.com.br",
  "password": "MinhaSenha@2026",
  "name": "Maria Silva",
  "roles": ["ROLE_COMPANY_OPERATOR"]
}
```

**Response (UserResponse):** `id`, `companyId`, `companyName`, `email`, `name`, `roles`, `active`, `createdAt`.

**Códigos HTTP:** 201 · 400 · 401 · 403 · 404 (empresa) · 409 (e-mail duplicado).

---

### GET /api/v1/auth/users

**Path:** `GET /api/v1/auth/users?page=0&size=20&sort=createdAt,desc`

**Autorização:** JWT; `HOLDING_ADMIN` (todas empresas) ou `COMPANY_ADMIN` (apenas sua).

**Regras:**
- Paginação padrão: 20/página.
- `COMPANY_ADMIN` filtrado por TenantContext.

**Response:** `Page<UserResponse>` padrão Spring Data.

**Códigos HTTP:** 200 · 401 · 403.

---

## API Keys (ApiKeyController)

### POST /api/v1/api-keys

**Path:** `POST /api/v1/api-keys`

**Autorização:** JWT ou API Key. Roles: `HOLDING_ADMIN` ou `COMPANY_ADMIN`.

**Request (CreateApiKeyRequest):**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| name | String | sim (@NotBlank) | Nome identificador |
| description | String | não | Descrição livre |
| roles | Set&lt;Role&gt; | sim (@NotNull) | Subset de roles permitidos |
| expiresAt | LocalDateTime | não | Expiração (null = nunca) |

**Regras:**
- Chave bruta gerada com `SecureRandom` (48 bytes), prefixada com `pk_`.
- Apenas o **hash SHA-256** é persistido. A chave bruta só aparece no payload de resposta; não é recuperável depois.
- Prefixo (primeiros 8 chars) armazenado para exibição.
- Vinculada à empresa do chamador (TenantContext).

**Exemplo request:**
```json
{
  "name": "Integracao portal externo",
  "description": "Uso do time de pagamentos",
  "roles": ["ROLE_COMPANY_OPERATOR"],
  "expiresAt": "2027-04-12T23:59:59"
}
```

**Response (CreateApiKeyResponse):**
- `apiKey`: `ApiKeyResponse` com `id`, `companyId`, `keyPrefix`, `name`, `description`, `roles`, `active`, `lastUsedAt`, `expiresAt`, `createdAt`.
- `rawKey`: **a chave completa**, salvar na hora (não volta a aparecer).

**Códigos HTTP:** 201 · 400 · 401 · 403.

---

### GET /api/v1/api-keys

**Path:** `GET /api/v1/api-keys?page=&size=&sort=`

**Autorização:** JWT ou API Key; `HOLDING_ADMIN` ou `COMPANY_ADMIN`. Sempre filtra pela empresa do chamador.

**Response:** `Page<ApiKeyResponse>` (sem `rawKey`). Chaves revogadas/expiradas aparecem com `active=false` ou `expiresAt` passado.

---

### DELETE /api/v1/api-keys/{id}

**Path:** `DELETE /api/v1/api-keys/{id}`

**Autorização:** JWT ou API Key; `HOLDING_ADMIN` ou `COMPANY_ADMIN`.

**Regras:**
- Soft-delete (seta `active=false`).
- Revogar chave já revogada retorna 400.
- Tentativa cross-tenant retorna 404.

**Códigos HTTP:** 204 · 400 · 401 · 403 · 404.

---

## Política de Acesso (AccessPolicyController)

### GET /api/v1/access-policy

**Path:** `GET /api/v1/access-policy`

**Autorização:** JWT; `HOLDING_ADMIN` ou `COMPANY_ADMIN`. Política é sempre a da empresa do chamador.

**Regras:** se ainda não existir, é criada com defaults (`maxOverdueCharges=1`, `overdueToleranceDays=0`, `blockOnSuspendedSubscription=true`, `blockOnSubscriptionCharges=true`, demais=false, `cacheTtlMinutes=5`).

**Response (AccessPolicyResponse):** `id`, `companyId`, `maxOverdueCharges`, `overdueToleranceDays`, `blockOnSuspendedSubscription`, `blockOnStandaloneCharges`, `blockOnSubscriptionCharges`, `blockOnNegativeCredit`, `customBlockMessage`, `cacheTtlMinutes`, `createdAt`, `updatedAt`.

**Códigos HTTP:** 200 · 401 · 403.

---

### PUT /api/v1/access-policy

**Path:** `PUT /api/v1/access-policy`

**Autorização:** JWT; `HOLDING_ADMIN` ou `COMPANY_ADMIN`.

**Request (UpdateAccessPolicyRequest, todos opcionais):**

| Campo | Tipo | Validação |
|---|---|---|
| maxOverdueCharges | Integer | @Min(1) |
| overdueToleranceDays | Integer | @Min(0) |
| blockOnSuspendedSubscription | Boolean | — |
| blockOnStandaloneCharges | Boolean | — |
| blockOnSubscriptionCharges | Boolean | — |
| blockOnNegativeCredit | Boolean | — |
| customBlockMessage | String | blank → null |
| cacheTtlMinutes | Integer | @Min(1) |

**Regras:**
- Comportamento PATCH: só atualiza campos não-nulos.
- `customBlockMessage` vazio vira null.
- Mudanças **não** invalidam cache existente (aguarda TTL).

**Exemplo request:**
```json
{
  "maxOverdueCharges": 3,
  "overdueToleranceDays": 5,
  "blockOnStandaloneCharges": true,
  "customBlockMessage": "Acesso bloqueado por inadimplência. Entre em contato com o suporte."
}
```

**Response:** idem GET.

**Códigos HTTP:** 200 · 400 · 401 · 403.

---

## Validação de Acesso do Cliente (CustomerAccessController)

### GET /api/v1/customers/{id}/access-status

**Path:** `GET /api/v1/customers/{id}/access-status`

**Autorização:** JWT ou API Key (qualquer role autenticada).

**Regras (ordem de avaliação):**
1. `blockOnSubscriptionCharges` + quantidade de cobranças de assinatura vencidas há > `overdueToleranceDays` dias ≥ `maxOverdueCharges`.
2. `blockOnStandaloneCharges` + cobranças avulsas vencidas ≥ `maxOverdueCharges`.
3. `blockOnSuspendedSubscription` + há alguma assinatura suspensa.
4. `blockOnNegativeCredit` + saldo de crédito negativo.
- Resultado cacheado em Redis por `cacheTtlMinutes` minutos.
- Mudança de status (`ALLOWED` ↔ `BLOCKED`) publica evento `CustomerAccessStatusChanged` no Outbox.

**Response (AccessStatusResponse):**

| Campo | Tipo | Descrição |
|---|---|---|
| customerId | Long | ID do cliente |
| customerName | String | Nome |
| allowed | Boolean | Resultado |
| reasons | List&lt;String&gt; | Motivos de bloqueio (vazio se allowed) |
| customBlockMessage | String | Mensagem customizada (null se allowed) |
| summary | AccessSummary | `activeSubscriptions`, `suspendedSubscriptions`, `overdueCharges`, `totalOverdueValue`, `oldestOverdueDays`, `creditBalance` |
| checkedAt | LocalDateTime | Timestamp |

**Exemplo response (bloqueado):**
```json
{
  "customerId": 42,
  "customerName": "Acme Corp Ltda.",
  "allowed": false,
  "reasons": [
    "2 cobranca(s) de assinatura vencida(s) ha mais de 3 dia(s)",
    "1 assinatura(s) suspensa(s)"
  ],
  "customBlockMessage": "Acesso bloqueado por inadimplência.",
  "summary": {
    "activeSubscriptions": 2,
    "suspendedSubscriptions": 1,
    "overdueCharges": 3,
    "totalOverdueValue": 5500.00,
    "oldestOverdueDays": 12,
    "creditBalance": 0.00
  },
  "checkedAt": "2026-04-12T16:20:30"
}
```

**Códigos HTTP:** 200 · 401 · 404.

**Observações:** se Redis cair, a verificação continua funcionando (fallback); o evento de mudança de status só dispara em trocas reais.


---

Perfeito! Agora tenho todas as informações necessárias. Vou gerar a documentação completa:

## Documentação de Integração - API REST Spring Boot

## CompanyController

### POST /api/v1/companies

**Método HTTP e Path Completo**
```
POST /api/v1/companies
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: `@PreAuthorize("hasRole('HOLDING_ADMIN')")`
- Acesso: Apenas administradores da holding
- Header X-Company-Id: Não requerido (operação no escopo global)

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `Content-Type: application/json`

**DTO de Request - CreateCompanyRequest**

| Campo | Tipo | Obrigatório | Validação | Descrição |
|-------|------|-------------|-----------|-----------|
| `cnpj` | String | Sim | @NotBlank, @Size(14-18) | CNPJ da empresa (com formatação 00.000.000/0000-00 ou sem formatação) |
| `razaoSocial` | String | Sim | @NotBlank | Razão social da empresa |
| `nomeFantasia` | String | Não | - | Nome fantasia para exibição |
| `email` | String | Não | - | Email de contato da empresa |
| `phone` | String | Não | - | Telefone de contato |
| `asaasApiKey` | String | Não | - | Chave da API Asaas (será criptografada com Jasypt) |
| `asaasEnv` | Enum | Não | - | Ambiente Asaas: SANDBOX ou PRODUCTION (padrão: SANDBOX) |
| `planChangePolicy` | Enum | Não | - | Política de mudança de plano: IMMEDIATE_PRORATA, END_OF_CYCLE, IMMEDIATE_NO_PRORATA (padrão: IMMEDIATE_PRORATA) |
| `downgradeValidationStrategy` | Enum | Não | - | Estratégia de validação de downgrade: BLOCK, SCHEDULE, GRACE_PERIOD (padrão: BLOCK) |
| `gracePeriodDays` | Integer | Não | - | Período de graça em dias para cobrança de atraso (padrão: 0) |

**Regras de Negócio**
- CNPJ deve ser único no sistema (validação `existsByCnpj()`)
- Chave API é armazenada de forma criptografada usando Jasypt (EncryptionService)
- Valores padrão são aplicados para asaasEnv, planChangePolicy, downgradeValidationStrategy, gracePeriodDays
- A empresa é auditada (action: COMPANY_CREATE, entity: Company)

**JSON de Request de Exemplo**
```json
{
  "cnpj": "12345678901234",
  "razaoSocial": "Acme Corporation LTDA",
  "nomeFantasia": "Acme",
  "email": "contato@acme.com.br",
  "phone": "+5511987654321",
  "asaasApiKey": "aak_xxx_yyy_zzz",
  "asaasEnv": "PRODUCTION",
  "planChangePolicy": "IMMEDIATE_PRORATA",
  "downgradeValidationStrategy": "GRACE_PERIOD",
  "gracePeriodDays": 5
}
```

**DTO de Response - CompanyResponse**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | Long | ID único da empresa |
| `cnpj` | String | CNPJ cadastrado |
| `razaoSocial` | String | Razão social |
| `nomeFantasia` | String | Nome fantasia |
| `email` | String | Email de contato |
| `phone` | String | Telefone de contato |
| `asaasEnv` | Enum | Ambiente Asaas configurado (SANDBOX/PRODUCTION) |
| `hasAsaasKey` | Boolean | Indica se há chave API configurada (nunca retorna a chave decriptada) |
| `status` | Enum | Status: ACTIVE, SUSPENDED, DEFAULTING |
| `planChangePolicy` | Enum | Política de mudança de plano |
| `downgradeValidationStrategy` | Enum | Estratégia de validação de downgrade |
| `gracePeriodDays` | Integer | Dias de graça configurados |
| `createdAt` | LocalDateTime | Data/hora de criação |
| `updatedAt` | LocalDateTime | Data/hora da última atualização |

**JSON de Response de Exemplo**
```json
{
  "id": 1,
  "cnpj": "12345678901234",
  "razaoSocial": "Acme Corporation LTDA",
  "nomeFantasia": "Acme",
  "email": "contato@acme.com.br",
  "phone": "+5511987654321",
  "asaasEnv": "PRODUCTION",
  "hasAsaasKey": true,
  "status": "ACTIVE",
  "planChangePolicy": "IMMEDIATE_PRORATA",
  "downgradeValidationStrategy": "GRACE_PERIOD",
  "gracePeriodDays": 5,
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T10:30:00"
}
```

**Códigos HTTP Possíveis**
- `201 Created`: Empresa criada com sucesso
- `400 Bad Request`: CNPJ já registrado no sistema ou falha na validação
- `401 Unauthorized`: JWT inválido ou ausente
- `403 Forbidden`: Usuário não possui role HOLDING_ADMIN

**Observações/Edge Cases**
- Chave API é criptografada e nunca retornada em plaintext (apenas flag `hasAsaasKey`)
- CNPJ é único e imutável após criação
- Empresa inicia com status ACTIVE automaticamente


---

## Cobranças (ChargeController)

Base path: `/api/v1/charges`

Controller responsável pelo ciclo de vida das cobranças avulsas e parceladas, integrando com o Asaas via `AsaasGatewayService`. Suporta PIX, Boleto, Cartão de Crédito (avulso e parcelado) e cobranças com método indefinido. Todas as cobranças passam por uma **state machine** (`ChargeStatus`) e publicam eventos no outbox.

**Enums principais:**
- `ChargeStatus`: `PENDING`, `CONFIRMED`, `RECEIVED`, `OVERDUE`, `REFUNDED`, `REFUND_IN_PROGRESS`, `CANCELED`, `FAILED`, `CHARGEBACK_REQUESTED`, `CHARGEBACK_DISPUTE`, `AWAITING_CHARGEBACK_REVERSAL`, `DUNNING_REQUESTED`, `DUNNING_RECEIVED`
- `BillingType`: `PIX`, `BOLETO`, `CREDIT_CARD`, `UNDEFINED`
- `ChargeOrigin`: `API`, `SUBSCRIPTION`, `INSTALLMENT`, `MANUAL`, `PLAN_CHANGE`

**Autenticação/autorização (todos endpoints):** JWT Bearer via `Authorization: Bearer <token>`. Roles: `COMPANY_ADMIN` ou `COMPANY_OPERATOR`. Header `X-Company-Id` obrigatório (usado por `TenantContextInterceptor` para RLS). Operações administrativas de escrita também exigem `HOLDING_ADMIN` quando rodam em contexto cross-tenant.

---

### POST /api/v1/charges/pix

1. **HTTP:** `POST /api/v1/charges/pix`
2. **Autenticação:** JWT (`COMPANY_ADMIN` ou `COMPANY_OPERATOR`). `X-Company-Id` obrigatório.
3. **Headers:**
   - `Authorization: Bearer <JWT>` (obrigatório)
   - `X-Company-Id: <id>` (obrigatório, tenant RLS)
   - `Idempotency-Key: <uuid>` (obrigatório — POST de cobrança é interceptado pelo `IdempotencyFilter`)
   - `Content-Type: application/json`
4. **DTO de request — `CreateChargeRequest`:**
   - `customerId` (Long, **obrigatório**, `@NotNull`) — ID interno do customer
   - `value` (BigDecimal, **obrigatório**, `@NotNull @Positive`) — valor da cobrança
   - `dueDate` (LocalDate, **obrigatório**, `@NotNull`) — ISO `YYYY-MM-DD`
   - `description` (String, opcional)
   - `externalReference` (String, opcional) — referência externa do cliente
   - `origin` (ChargeOrigin, opcional — default `API`)
   - `couponCode` (String, opcional) — código do cupom (scope `CHARGE`)
   - Campos de cartão/parcelamento ignorados neste endpoint
5. **Regras de negócio:**
   - Valida customer existente e ativo do tenant.
   - Se `couponCode` informado, aciona `CouponService.validateAuthenticated` (scope=`CHARGE`) — todos os checks 1 a 8 (existência, vigência, limite global, limite por customer, whitelist de plans, whitelist de customers, plano permitido, ciclo permitido). Em caso de `PERCENTAGE` limite 90%.
   - Aplica desconto no `value`, persiste `originalValue` e `discountAmount`.
   - Chama `AsaasGatewayService.createCharge(PIX)` — resiliente via Resilience4j (3 retries, backoff 2x, circuit breaker 50%/30s).
   - Persiste charge local com status inicial `PENDING` e `asaasId`.
   - Registra `CouponUsage` (se cupom aplicado).
   - Publica `ChargeCreatedEvent` no outbox.
6. **JSON exemplo:**
```json
{
  "customerId": 1001,
  "value": 150.00,
  "dueDate": "2026-05-01",
  "description": "Cobranca PIX - Servico X",
  "externalReference": "ORDER-12345",
  "origin": "API",
  "couponCode": "BEMVINDO10"
}
```
7. **DTO de response — `ChargeResponse`:** `id` (Long), `companyId`, `customerId`, `subscriptionId` (nullable), `installmentId` (nullable), `asaasId` (String), `billingType` (BillingType), `value` (BigDecimal — valor final), `dueDate`, `status` (ChargeStatus), `origin`, `externalReference`, `pixQrcode` (base64), `pixCopyPaste`, `boletoUrl` (null em PIX), `invoiceUrl`, `installmentNumber`, `couponCode`, `discountAmount`, `originalValue`, `createdAt`, `updatedAt`.
8. **JSON resposta:**
```json
{
  "id": 5001,
  "companyId": 10,
  "customerId": 1001,
  "subscriptionId": null,
  "installmentId": null,
  "asaasId": "pay_8732918273",
  "billingType": "PIX",
  "value": 135.00,
  "dueDate": "2026-05-01",
  "status": "PENDING",
  "origin": "API",
  "externalReference": "ORDER-12345",
  "pixQrcode": "iVBORw0KGgoAAAANSUhEUgAA...",
  "pixCopyPaste": "00020101021226830014br.gov.bcb.pix...",
  "boletoUrl": null,
  "invoiceUrl": "https://sandbox.asaas.com/i/8732918273",
  "installmentNumber": null,
  "couponCode": "BEMVINDO10",
  "discountAmount": 15.00,
  "originalValue": 150.00,
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T10:30:00"
}
```
9. **HTTP codes:** `201 Created`, `400` (validação), `401`, `403`, `404` (customer), `409` (idempotency replay), `422` (cupom inválido), `502` (Asaas indisponível após retries).
10. **Observações:** Idempotência via `Idempotency-Key` (Redis → Postgres fallback). Webhook Asaas correlaciona via `asaasId`. Retries só em erros de rede (HTTP 5xx/timeout).

---

### POST /api/v1/charges/boleto

1. **HTTP:** `POST /api/v1/charges/boleto`
2. **Autenticação:** idem PIX.
3. **Headers:** idem PIX (Idempotency-Key obrigatório).
4. **DTO:** `CreateChargeRequest` — os mesmos campos obrigatórios (`customerId`, `value`, `dueDate`); `description`, `externalReference`, `couponCode` opcionais.
5. **Regras de negócio:**
   - Mesmas regras de validação do PIX mais cálculo de data de vencimento do boleto.
   - `AsaasGatewayService.createCharge(BOLETO)` retorna `bankSlipUrl` e campos de linha digitável.
   - Charge persistida com `billingType=BOLETO`, status `PENDING`.
6. **JSON exemplo:**
```json
{
  "customerId": 1001,
  "value": 299.90,
  "dueDate": "2026-05-10",
  "description": "Mensalidade abril",
  "externalReference": "INV-202604"
}
```
7. **Response:** `ChargeResponse` — `boletoUrl` preenchido; `pixQrcode`/`pixCopyPaste` nulos.
8. **JSON resposta:**
```json
{
  "id": 5002,
  "companyId": 10,
  "customerId": 1001,
  "asaasId": "pay_9911",
  "billingType": "BOLETO",
  "value": 299.90,
  "dueDate": "2026-05-10",
  "status": "PENDING",
  "origin": "API",
  "boletoUrl": "https://sandbox.asaas.com/b/pdf/9911",
  "invoiceUrl": "https://sandbox.asaas.com/i/9911",
  "originalValue": 299.90,
  "createdAt": "2026-04-12T10:35:00",
  "updatedAt": "2026-04-12T10:35:00"
}
```
9. **HTTP codes:** `201`, `400`, `401`, `403`, `404`, `409`, `422`, `502`.
10. **Observações:** Use `GET /{id}/boleto-line` para obter linha digitável. Boleto vencido pode ser regenerado.

---

### POST /api/v1/charges/credit-card

1. **HTTP:** `POST /api/v1/charges/credit-card`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`, `Idempotency-Key`, `Content-Type`.
4. **DTO — `CreateChargeRequest`:** obrigatórios `customerId`, `value`, `dueDate`. Um dos dois grupos:
   - `creditCardToken` (String) — token Asaas já salvo, OU
   - `creditCard` (CreditCardInfo: `holderName`, `number`, `expiryMonth`, `expiryYear`, `ccv`) + `creditCardHolderInfo` (CreditCardHolderInfo: `name`, `email`, `cpfCnpj`, `postalCode`, `addressNumber`, `phone`) + `remoteIp` (IP do titular).
5. **Regras de negócio:**
   - PAN **nunca é persistido** localmente — somente token Asaas.
   - `AsaasGatewayService.tokenize` (se PAN enviado) e em seguida `createCharge(CREDIT_CARD)`.
   - Cobrança é autorizada/capturada imediatamente → status tipicamente `CONFIRMED` ou `RECEIVED` após resposta.
   - State machine: `PENDING` → `CONFIRMED` → `RECEIVED` (ou `FAILED`).
   - Aplica cupom se informado.
6. **JSON exemplo (com PAN):**
```json
{
  "customerId": 1001,
  "value": 89.90,
  "dueDate": "2026-04-12",
  "description": "Compra avulsa",
  "creditCard": {
    "holderName": "JOAO DA SILVA",
    "number": "4111111111111111",
    "expiryMonth": "12",
    "expiryYear": "2028",
    "ccv": "123"
  },
  "creditCardHolderInfo": {
    "name": "Joao da Silva",
    "email": "joao@example.com",
    "cpfCnpj": "12345678901",
    "postalCode": "01310100",
    "addressNumber": "1000",
    "phone": "11999998888"
  },
  "remoteIp": "191.156.20.10"
}
```
**JSON exemplo (com token):**
```json
{
  "customerId": 1001,
  "value": 89.90,
  "dueDate": "2026-04-12",
  "creditCardToken": "tok_abc123xyz",
  "remoteIp": "191.156.20.10"
}
```
7. **Response:** `ChargeResponse` com `billingType=CREDIT_CARD`. `boletoUrl`/PIX nulos.
8. **JSON resposta:**
```json
{
  "id": 5003,
  "asaasId": "pay_cc_5533",
  "billingType": "CREDIT_CARD",
  "value": 89.90,
  "status": "CONFIRMED",
  "invoiceUrl": "https://sandbox.asaas.com/i/cc_5533",
  "createdAt": "2026-04-12T10:40:00"
}
```
9. **HTTP codes:** `201`, `400`, `401`, `403`, `404`, `409`, `422` (cartão recusado), `502`.
10. **Observações:** Nunca loga PAN/CCV. Tokenização é idempotente. Chargebacks chegam via webhook e mudam status para `CHARGEBACK_REQUESTED`.

---

### POST /api/v1/charges/credit-card/installments

1. **HTTP:** `POST /api/v1/charges/credit-card/installments`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** idem credit-card.
4. **DTO — `CreateChargeRequest`:** obrigatórios `customerId`, `value` (valor total), `dueDate` (primeira parcela), `installmentCount` (≥ 2), `installmentValue` (valor de cada parcela, `value = installmentCount × installmentValue`). Dados de cartão (PAN ou token).
5. **Regras:**
   - Valida `installmentCount ≥ 2` e coerência `value ≈ installmentCount * installmentValue`.
   - Cria `Installment` local e N `Charge` vinculados por `installmentId`.
   - Asaas retorna `installment` com N `payments`.
   - Cada parcela herda `origin=INSTALLMENT` e `installmentNumber` (1..N).
6. **JSON exemplo:**
```json
{
  "customerId": 1001,
  "value": 1200.00,
  "dueDate": "2026-05-01",
  "installmentCount": 12,
  "installmentValue": 100.00,
  "creditCardToken": "tok_abc123xyz",
  "remoteIp": "191.156.20.10",
  "description": "Produto parcelado"
}
```
7. **Response:** `ChargeResponse` da primeira parcela (com `installmentId` e `installmentNumber=1`).
8. **JSON resposta:**
```json
{
  "id": 5010,
  "installmentId": 700,
  "asaasId": "pay_inst_1_12",
  "billingType": "CREDIT_CARD",
  "value": 100.00,
  "installmentNumber": 1,
  "status": "CONFIRMED",
  "originalValue": 1200.00
}
```
9. **HTTP codes:** `201`, `400` (installmentCount < 2), `401`, `403`, `404`, `409`, `422`, `502`.
10. **Observações:** Consultar parcelas via `GET /api/v1/charges?installmentId=...` não suportado aqui — use filtro por `customerId` ou endpoint de installment.

---

### POST /api/v1/charges/boleto/installments

1. **HTTP:** `POST /api/v1/charges/boleto/installments`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** idem (Idempotency-Key obrigatório).
4. **DTO:** `CreateChargeRequest` com `installmentCount ≥ 2`, `installmentValue`, `value` total; sem dados de cartão.
5. **Regras:** gera N boletos, cada um com `dueDate` incrementado mensalmente a partir de `dueDate`.
6. **JSON exemplo:**
```json
{
  "customerId": 1001,
  "value": 600.00,
  "dueDate": "2026-05-01",
  "installmentCount": 6,
  "installmentValue": 100.00,
  "description": "Parcelamento boleto 6x"
}
```
7. **Response:** `ChargeResponse` com `billingType=BOLETO` e `installmentNumber=1`.
8. **JSON resposta:**
```json
{
  "id": 5020,
  "installmentId": 701,
  "billingType": "BOLETO",
  "value": 100.00,
  "installmentNumber": 1,
  "status": "PENDING",
  "boletoUrl": "https://sandbox.asaas.com/b/pdf/inst_1"
}
```
9. **HTTP codes:** `201`, `400`, `401`, `403`, `404`, `409`, `422`, `502`.
10. **Observações:** Cada parcela pode ser paga/regenerada independentemente.

---

### POST /api/v1/charges/undefined

1. **HTTP:** `POST /api/v1/charges/undefined`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** idem.
4. **DTO:** `CreateChargeRequest` obrigatórios `customerId`, `value`, `dueDate`.
5. **Regras:** cliente escolhe método no link Asaas (`invoiceUrl`). `billingType=UNDEFINED`.
6. **JSON exemplo:**
```json
{
  "customerId": 1001,
  "value": 50.00,
  "dueDate": "2026-04-25",
  "description": "Cliente escolhe metodo"
}
```
7. **Response:** `ChargeResponse` com `invoiceUrl` preenchido.
8. **JSON resposta:**
```json
{
  "id": 5030,
  "billingType": "UNDEFINED",
  "invoiceUrl": "https://sandbox.asaas.com/i/undef_5030",
  "status": "PENDING"
}
```
9. **HTTP codes:** `201`, `400`, `401`, `403`, `404`, `409`, `502`.
10. **Observações:** Quando o cliente paga, webhook atualiza `billingType` real e status.

---

### GET /api/v1/charges

1. **HTTP:** `GET /api/v1/charges`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`.
4. **Query params:** `status` (ChargeStatus), `origin` (ChargeOrigin), `dueDateFrom` (ISO date), `dueDateTo` (ISO date), `customerId` (Long), `page`, `size`, `sort`. Todos opcionais.
5. **Regras:** RLS filtra por `companyId`. Ordenação default por `createdAt DESC`.
6. **Exemplo:** `GET /api/v1/charges?status=PENDING&dueDateFrom=2026-04-01&dueDateTo=2026-04-30&page=0&size=20`
7. **Response:** `Page<ChargeResponse>`.
8. **JSON resposta:**
```json
{
  "content": [ { "id": 5001, "status": "PENDING", "value": 135.00 } ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```
9. **HTTP codes:** `200`, `401`, `403`.
10. **Observações:** Usa Spring Data `Pageable`, paginação 0-based.

---

### GET /api/v1/charges/{id}

1. **HTTP:** `GET /api/v1/charges/{id}`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`.
4. **Path:** `id` (Long).
5. **Regras:** RLS garante escopo tenant. 404 se não encontrado no tenant.
6. **Exemplo:** `GET /api/v1/charges/5001`
7. **Response:** `ChargeResponse`.
8. **JSON resposta:** idem POST PIX.
9. **HTTP codes:** `200`, `401`, `403`, `404`.
10. **Observações:** Leitura não exige idempotency.

---

### GET /api/v1/charges/{id}/pix-qrcode

1. **HTTP:** `GET /api/v1/charges/{id}/pix-qrcode`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`.
4. **Path:** `id`.
5. **Regras:** charge precisa ter `billingType=PIX`. QR code é cacheado após primeira consulta. Chama `AsaasGatewayService.getPixQrCode`.
6. **Exemplo:** `GET /api/v1/charges/5001/pix-qrcode`
7. **Response — `PixQrCodeResponse`:** `encodedImage` (base64 PNG), `copyPaste` (BR Code string), `expirationDate` (ISO string).
8. **JSON resposta:**
```json
{
  "encodedImage": "iVBORw0KGgoAAAANSUhEUgAA...",
  "copyPaste": "00020101021226830014br.gov.bcb.pix...",
  "expirationDate": "2026-05-01T23:59:59"
}
```
9. **HTTP codes:** `200`, `401`, `403`, `404`, `422` (não é PIX), `502`.
10. **Observações:** Se Asaas falhar, retorna 502 com `Asaas error handler`.

---

### GET /api/v1/charges/{id}/boleto-line

1. **HTTP:** `GET /api/v1/charges/{id}/boleto-line`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`.
4. **Path:** `id`.
5. **Regras:** charge precisa ter `billingType=BOLETO`.
6. **Exemplo:** `GET /api/v1/charges/5002/boleto-line`
7. **Response — `BoletoLineResponse`:** `identificationField` (linha digitável), `nossoNumero`, `barCode`.
8. **JSON resposta:**
```json
{
  "identificationField": "23793.38128 60001.234567 12345.678901 2 98730000029990",
  "nossoNumero": "0000123456",
  "barCode": "23792987300000299903381260001234567123456789012"
}
```
9. **HTTP codes:** `200`, `401`, `403`, `404`, `422`, `502`.

---

### POST /api/v1/charges/{id}/refund

1. **HTTP:** `POST /api/v1/charges/{id}/refund`
2. **Autenticação:** JWT `COMPANY_ADMIN` (apenas admin pode estornar) + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`, `Idempotency-Key`, `Content-Type`.
4. **DTO — `RefundRequest`:** `value` (BigDecimal, opcional — se nulo estorna total), `description` (String, opcional).
5. **Regras:**
   - State machine: apenas `CONFIRMED` ou `RECEIVED` podem ser estornados.
   - Se parcial, `value ≤ charge.value`.
   - Chama `AsaasGatewayService.refund`. Status transiciona para `REFUND_IN_PROGRESS` ou `REFUNDED`.
   - Publica `ChargeRefundedEvent` no outbox.
6. **JSON exemplo (total):**
```json
{ "description": "Cliente desistiu" }
```
**JSON exemplo (parcial):**
```json
{ "value": 50.00, "description": "Reembolso parcial" }
```
7. **Response:** `ChargeResponse` atualizado.
8. **JSON resposta:**
```json
{ "id": 5003, "status": "REFUNDED", "value": 89.90 }
```
9. **HTTP codes:** `200`, `400`, `401`, `403`, `404`, `409` (estado inválido), `422`, `502`.
10. **Observações:** Valor do estorno compensa no caixa do Asaas em D+30. Status final `REFUNDED` chega por webhook.

---

### DELETE /api/v1/charges/{id}

1. **HTTP:** `DELETE /api/v1/charges/{id}`
2. **Autenticação:** JWT `COMPANY_ADMIN` + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`.
4. **Path:** `id`.
5. **Regras:** apenas `PENDING`, `CONFIRMED`, `OVERDUE` podem ser canceladas. Chama `AsaasGatewayService.cancel`. Status → `CANCELED`. Publica `ChargeCanceledEvent`.
6. **Exemplo:** `DELETE /api/v1/charges/5001`
7. **Response:** `ChargeResponse`.
8. **JSON resposta:**
```json
{ "id": 5001, "status": "CANCELED" }
```
9. **HTTP codes:** `200`, `401`, `403`, `404`, `409`, `502`.

---

### POST /api/v1/charges/{id}/regenerate-boleto

1. **HTTP:** `POST /api/v1/charges/{id}/regenerate-boleto`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`, `Idempotency-Key`.
4. **Body:** vazio.
5. **Regras:** charge precisa ser `BOLETO`, status `PENDING` ou `OVERDUE`. Gera nova linha digitável e URL.
6. **Exemplo:** `POST /api/v1/charges/5002/regenerate-boleto`
7. **Response:** `ChargeResponse` com `boletoUrl` e linha atualizada.
8. **JSON resposta:**
```json
{ "id": 5002, "boletoUrl": "https://sandbox.asaas.com/b/pdf/new_9911", "status": "PENDING" }
```
9. **HTTP codes:** `200`, `401`, `403`, `404`, `409`, `422`, `502`.

---

### POST /api/v1/charges/{id}/received-in-cash

1. **HTTP:** `POST /api/v1/charges/{id}/received-in-cash`
2. **Autenticação:** JWT `COMPANY_ADMIN` + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`, `Idempotency-Key`.
4. **Body:** vazio.
5. **Regras:** apenas para `PENDING`/`OVERDUE`. Status → `RECEIVED`. Publica `ChargePaidEvent`. Não sincroniza financeiro com Asaas (apenas marca recebido).
6. **Exemplo:** `POST /api/v1/charges/5002/received-in-cash`
7. **Response:** `ChargeResponse`.
8. **JSON resposta:**
```json
{ "id": 5002, "status": "RECEIVED" }
```
9. **HTTP codes:** `200`, `401`, `403`, `404`, `409`, `502`.

---

### POST /api/v1/charges/{id}/resend-notification

1. **HTTP:** `POST /api/v1/charges/{id}/resend-notification`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`.
4. **Body:** vazio.
5. **Regras:** publica `ChargeNotificationResendEvent` no outbox (processado pelo n8n externo).
6. **Exemplo:** `POST /api/v1/charges/5001/resend-notification`
7. **Response:** `ChargeResponse`.
8. **JSON resposta:**
```json
{ "id": 5001, "status": "PENDING" }
```
9. **HTTP codes:** `200`, `401`, `403`, `404`.
10. **Observações:** apenas publica evento — o envio real é responsabilidade do consumidor outbox.

---

## Mudança de Plano (PlanChangeController)

Base path: `/api/v1/subscriptions/{subscriptionId}`

Controller responsável por upgrades, downgrades e sidegrades de assinatura. Utiliza `ProrationCalculator` e respeita a `PlanChangePolicy` configurada na `Company`.

**Enums:**
- `PlanChangePolicy`: `IMMEDIATE_PRORATA` (mudança imediata com cálculo de crédito/débito pró-rata pelos dias restantes), `END_OF_CYCLE` (agendada para o próximo ciclo, sem cobrança extra), `IMMEDIATE_NO_PRORATA` (imediata sem ajuste financeiro).
- `PlanChangeType`: `UPGRADE`, `DOWNGRADE`, `SIDEGRADE`.
- `PlanChangeStatus`: `PENDING`, `SCHEDULED`, `COMPLETED`, `CANCELED`, `FAILED`.

**Autenticação (todos endpoints):** JWT `COMPANY_ADMIN` + `X-Company-Id`. Escrita restrita ao admin da empresa; leitura também a `COMPANY_OPERATOR`.

---

### POST /api/v1/subscriptions/{subscriptionId}/preview-change

1. **HTTP:** `POST /api/v1/subscriptions/{subscriptionId}/preview-change?newPlanId={id}`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`.
4. **Query:** `newPlanId` (Long, obrigatório).
5. **Regras:**
   - Valida que a assinatura pertence ao tenant e que `newPlanId` é um Plano ativo do tenant e distinto do atual.
   - Classifica como `UPGRADE` (novo > atual), `DOWNGRADE` (novo < atual) ou `SIDEGRADE` (igual valor).
   - `ProrationCalculator` computa dias restantes do ciclo, proporção usada/não usada.
     - `prorationCredit` = valor não consumido do plano atual (downgrade ou todos os casos com política PRORATA).
     - `prorationCharge` = valor proporcional do novo plano.
     - `delta = prorationCharge - prorationCredit`.
   - **Não persiste** — apenas simula.
6. **Exemplo:** `POST /api/v1/subscriptions/900/preview-change?newPlanId=42`
7. **Response — `PlanChangePreviewResponse`:** `subscriptionId`, `currentPlanId`, `currentPlanName`, `currentPlanValue` (BigDecimal), `newPlanId`, `newPlanName`, `newPlanValue`, `changeType` (PlanChangeType), `policy` (PlanChangePolicy), `delta`, `prorationCredit`, `prorationCharge`.
8. **JSON resposta:**
```json
{
  "subscriptionId": 900,
  "currentPlanId": 10,
  "currentPlanName": "Basic Mensal",
  "currentPlanValue": 99.90,
  "newPlanId": 42,
  "newPlanName": "Pro Mensal",
  "newPlanValue": 199.90,
  "changeType": "UPGRADE",
  "policy": "IMMEDIATE_PRORATA",
  "delta": 50.00,
  "prorationCredit": 49.95,
  "prorationCharge": 99.95
}
```
9. **HTTP codes:** `200`, `400`, `401`, `403`, `404` (sub ou plano), `409` (plano igual/inativo).
10. **Observações:** Útil para UI mostrar ao usuário antes de confirmar a troca.

---

### POST /api/v1/subscriptions/{subscriptionId}/change-plan

1. **HTTP:** `POST /api/v1/subscriptions/{subscriptionId}/change-plan`
2. **Autenticação:** JWT `COMPANY_ADMIN` + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`, `Idempotency-Key`, `Content-Type`.
4. **DTO — `RequestPlanChangeRequest`:**
   - `newPlanId` (Long, **obrigatório**, `@NotNull`)
   - `currentUsage` (Map<String,Integer>, opcional) — uso atual por métrica (ex.: `{"users": 12}`) para downgrade validar limites do novo plano
   - `requestedBy` (String, opcional) — identificação de auditoria (e-mail do operador)
5. **Regras de negócio:**
   - Valida que o novo plano aceita o `currentUsage` (não pode fazer downgrade se uso > limite do plano destino).
   - Aplica política da `Company`:
     - **IMMEDIATE_PRORATA**: troca imediata; se `delta > 0` cria cobrança `PLAN_CHANGE`; se `delta < 0` cria entrada em `credit_ledger`.
     - **END_OF_CYCLE**: persiste `PlanChange` com `status=SCHEDULED` e `scheduledFor=nextCycleStart`. Executado por `ScheduledPlanChangeJob` (cron diário 01:00).
     - **IMMEDIATE_NO_PRORATA**: troca imediata sem ajuste financeiro.
   - Atualiza subscription no Asaas (novo valor).
   - Publica `PlanChangedEvent` no outbox.
   - Registra em `credit_ledger` todos os movimentos.
6. **JSON exemplo:**
```json
{
  "newPlanId": 42,
  "currentUsage": { "users": 8, "storageGb": 15 },
  "requestedBy": "admin@acme.com"
}
```
7. **Response — `PlanChangeResponse`:** `id`, `subscriptionId`, `previousPlanId`, `previousPlanName`, `requestedPlanId`, `requestedPlanName`, `changeType`, `policy`, `deltaAmount`, `prorationCredit`, `prorationCharge`, `status` (PlanChangeStatus), `chargeId` (nullable), `creditLedgerId` (nullable), `scheduledFor` (nullable), `effectiveAt` (nullable), `requestedBy`, `requestedAt`, `failureReason` (nullable).
8. **JSON resposta:**
```json
{
  "id": 77,
  "subscriptionId": 900,
  "previousPlanId": 10,
  "previousPlanName": "Basic Mensal",
  "requestedPlanId": 42,
  "requestedPlanName": "Pro Mensal",
  "changeType": "UPGRADE",
  "policy": "IMMEDIATE_PRORATA",
  "deltaAmount": 50.00,
  "prorationCredit": 49.95,
  "prorationCharge": 99.95,
  "status": "COMPLETED",
  "chargeId": 5100,
  "creditLedgerId": 321,
  "scheduledFor": null,
  "effectiveAt": "2026-04-12T10:55:00",
  "requestedBy": "admin@acme.com",
  "requestedAt": "2026-04-12T10:55:00",
  "failureReason": null
}
```
9. **HTTP codes:** `201`, `400` (usage excede), `401`, `403`, `404`, `409` (plano inativo/igual), `422` (regra negócio), `502`.
10. **Observações:** Idempotente via `Idempotency-Key`. Erros do Asaas resetam o status para `FAILED` com `failureReason`.

---

### GET /api/v1/subscriptions/{subscriptionId}/plan-changes

1. **HTTP:** `GET /api/v1/subscriptions/{subscriptionId}/plan-changes`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`.
4. **Query:** `page` (default 0), `size` (default 20), `sort`.
5. **Regras:** retorna histórico paginado ordenado por `requestedAt DESC`.
6. **Exemplo:** `GET /api/v1/subscriptions/900/plan-changes?page=0&size=20`
7. **Response:** `Page<PlanChangeResponse>`.
8. **JSON resposta:**
```json
{
  "content": [ { "id": 77, "status": "COMPLETED", "changeType": "UPGRADE" } ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20
}
```
9. **HTTP codes:** `200`, `401`, `403`, `404`.

---

### DELETE /api/v1/subscriptions/{subscriptionId}/plan-changes/{changeId}

1. **HTTP:** `DELETE /api/v1/subscriptions/{subscriptionId}/plan-changes/{changeId}`
2. **Autenticação:** JWT `COMPANY_ADMIN` + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`.
4. **Path:** `subscriptionId`, `changeId`.
5. **Regras:** apenas `PENDING` ou `SCHEDULED` podem ser canceladas. Status → `CANCELED`. Reverte crédito se já lançado. Publica `PlanChangeCanceledEvent`.
6. **Exemplo:** `DELETE /api/v1/subscriptions/900/plan-changes/77`
7. **Response:** `PlanChangeResponse`.
8. **JSON resposta:**
```json
{ "id": 77, "status": "CANCELED", "effectiveAt": null }
```
9. **HTTP codes:** `200`, `401`, `403`, `404`, `409` (já completa).
10. **Observações:** Tentar cancelar mudança `COMPLETED` retorna 409. `ScheduledPlanChangeJob` pula itens cancelados.

---

## Cupons (CouponController)

Base path: `/api/v1/coupons`

Gerencia cupons de desconto por empresa. Suporta desconto percentual (máx 90%) ou valor fixo, escopo `SUBSCRIPTION` ou `CHARGE`, restrições por plano, customer, ciclo e aplicação em primeira cobrança ou recorrente.

**Enums:**
- `CouponScope`: `SUBSCRIPTION`, `CHARGE`.
- `DiscountType`: `PERCENTAGE` (limite 90%), `FIXED_AMOUNT`.
- `CouponApplicationType`: `FIRST_CHARGE` (aplica só à primeira cobrança da assinatura), `RECURRING` (aplica durante N meses `recurrenceMonths`, ou ilimitado se nulo).

**Autenticação:** JWT `COMPANY_ADMIN` para escrita; leitura aceita `COMPANY_OPERATOR`. `X-Company-Id` obrigatório.

---

### POST /api/v1/coupons

1. **HTTP:** `POST /api/v1/coupons`
2. **Autenticação:** JWT `COMPANY_ADMIN` + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`, `Content-Type: application/json`.
4. **DTO — `CreateCouponRequest`:**
   - `code` (String, **obrigatório**, `@NotBlank`, `@Pattern ^[A-Z0-9_-]+$`) — upper-case, único por company.
   - `description` (String, opcional)
   - `discountType` (DiscountType, **obrigatório**) — `PERCENTAGE`/`FIXED_AMOUNT`
   - `discountValue` (BigDecimal, **obrigatório**, `@Positive`) — se `PERCENTAGE` máx 90
   - `scope` (CouponScope, **obrigatório**) — `SUBSCRIPTION`/`CHARGE`
   - `applicationType` (CouponApplicationType, opcional — default `FIRST_CHARGE` para subscription)
   - `recurrenceMonths` (Integer, opcional) — apenas se `RECURRING`; null = ilimitado
   - `validFrom` (LocalDateTime, opcional)
   - `validUntil` (LocalDateTime, opcional)
   - `maxUses` (Integer, opcional) — limite global
   - `maxUsesPerCustomer` (Integer, opcional)
   - `allowedPlans` (String CSV, opcional) — whitelist de plan codes
   - `allowedCustomers` (String CSV, opcional) — whitelist de customer IDs
   - `allowedCycle` (String, opcional) — `MONTHLY`/`QUARTERLY`/`YEARLY`
5. **Regras:**
   - Code é armazenado upper-case, únicidade por `companyId`.
   - `PERCENTAGE` rejeita `discountValue > 90`.
   - Conflito se code já existe.
6. **JSON exemplo (SUBSCRIPTION completo):**
```json
{
  "code": "BLACKFRIDAY25",
  "description": "Black Friday 2026",
  "discountType": "PERCENTAGE",
  "discountValue": 25.00,
  "scope": "SUBSCRIPTION",
  "applicationType": "RECURRING",
  "recurrenceMonths": 3,
  "validFrom": "2026-11-20T00:00:00",
  "validUntil": "2026-11-30T23:59:59",
  "maxUses": 1000,
  "maxUsesPerCustomer": 1,
  "allowedPlans": "PRO,ENTERPRISE",
  "allowedCustomers": null,
  "allowedCycle": "MONTHLY"
}
```
**JSON exemplo (CHARGE, fixed):**
```json
{
  "code": "DESC50",
  "discountType": "FIXED_AMOUNT",
  "discountValue": 50.00,
  "scope": "CHARGE",
  "maxUses": 100
}
```
7. **Response — `CouponResponse`:** `id`, `companyId`, `code`, `description`, `discountType`, `discountValue`, `scope`, `applicationType`, `recurrenceMonths`, `validFrom`, `validUntil`, `maxUses`, `maxUsesPerCustomer`, `usageCount` (Integer), `allowedPlans`, `allowedCustomers`, `allowedCycle`, `active` (Boolean), `currentlyValid` (Boolean — derivado), `createdAt`, `updatedAt`.
8. **JSON resposta:**
```json
{
  "id": 88,
  "companyId": 10,
  "code": "BLACKFRIDAY25",
  "discountType": "PERCENTAGE",
  "discountValue": 25.00,
  "scope": "SUBSCRIPTION",
  "applicationType": "RECURRING",
  "recurrenceMonths": 3,
  "validFrom": "2026-11-20T00:00:00",
  "validUntil": "2026-11-30T23:59:59",
  "maxUses": 1000,
  "maxUsesPerCustomer": 1,
  "usageCount": 0,
  "allowedPlans": "PRO,ENTERPRISE",
  "allowedCycle": "MONTHLY",
  "active": true,
  "currentlyValid": false,
  "createdAt": "2026-04-12T11:00:00",
  "updatedAt": "2026-04-12T11:00:00"
}
```
9. **HTTP codes:** `201`, `400`, `401`, `403`, `409` (code duplicado), `422` (percent > 90).
10. **Observações:** `currentlyValid=true` quando `active=true` E `validFrom ≤ now ≤ validUntil` E `usageCount < maxUses`.

---

### GET /api/v1/coupons

1. **HTTP:** `GET /api/v1/coupons`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`.
4. **Query:** `page`, `size`, `sort`.
5. **Regras:** lista todos cupons do tenant (ativos e inativos).
6. **Exemplo:** `GET /api/v1/coupons?page=0&size=20`
7. **Response:** `Page<CouponResponse>`.
8. **JSON:** paginação padrão.
9. **HTTP codes:** `200`, `401`, `403`.

---

### GET /api/v1/coupons/active

1. **HTTP:** `GET /api/v1/coupons/active`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** idem.
4. **Query:** `page`, `size`, `sort`.
5. **Regras:** filtra `active=true` e `now ∈ [validFrom, validUntil]`.
6. **Exemplo:** `GET /api/v1/coupons/active`
7. **Response:** `Page<CouponResponse>`.
8. **JSON:** paginação padrão.
9. **HTTP codes:** `200`, `401`, `403`.

---

### GET /api/v1/coupons/{id}

1. **HTTP:** `GET /api/v1/coupons/{id}`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** idem.
4. **Path:** `id`.
5. **Regras:** RLS por tenant.
6. **Exemplo:** `GET /api/v1/coupons/88`
7. **Response:** `CouponResponse`.
8. **JSON:** idem create.
9. **HTTP codes:** `200`, `401`, `403`, `404`.

---

### GET /api/v1/coupons/code/{code}

1. **HTTP:** `GET /api/v1/coupons/code/{code}`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** idem.
4. **Path:** `code` (String — case-insensitive — normaliza para upper).
5. **Regras:** busca por `code` dentro do tenant.
6. **Exemplo:** `GET /api/v1/coupons/code/BLACKFRIDAY25`
7. **Response:** `CouponResponse`.
8. **JSON:** idem.
9. **HTTP codes:** `200`, `401`, `403`, `404`.

---

### PUT /api/v1/coupons/{id}

1. **HTTP:** `PUT /api/v1/coupons/{id}`
2. **Autenticação:** JWT `COMPANY_ADMIN` + `X-Company-Id`.
3. **Headers:** `Authorization`, `X-Company-Id`, `Content-Type`.
4. **DTO — `UpdateCouponRequest`:** mesmos campos de Create **exceto** `code` e `scope` (imutáveis). Todos opcionais (partial update).
5. **Regras:** `code`/`scope` não editáveis. `discountValue` com mesmo limite 90%. Atualizar `validFrom/validUntil` afeta `currentlyValid`.
6. **JSON exemplo:**
```json
{
  "description": "Black Friday estendido",
  "discountValue": 30.00,
  "validUntil": "2026-12-05T23:59:59",
  "maxUses": 2000
}
```
7. **Response:** `CouponResponse`.
8. **JSON:** idem.
9. **HTTP codes:** `200`, `400`, `401`, `403`, `404`, `422`.

---

### DELETE /api/v1/coupons/{id}

1. **HTTP:** `DELETE /api/v1/coupons/{id}`
2. **Autenticação:** JWT `COMPANY_ADMIN` + `X-Company-Id`.
3. **Headers:** idem.
4. **Path:** `id`.
5. **Regras:** soft-delete — `active=false`. Usos existentes preservados.
6. **Exemplo:** `DELETE /api/v1/coupons/88`
7. **Response:** `204 No Content` (sem body).
8. **JSON:** vazio.
9. **HTTP codes:** `204`, `401`, `403`, `404`.

---

### PATCH /api/v1/coupons/{id}/activate

1. **HTTP:** `PATCH /api/v1/coupons/{id}/activate`
2. **Autenticação:** JWT `COMPANY_ADMIN` + `X-Company-Id`.
3. **Headers:** idem.
4. **Path:** `id`.
5. **Regras:** reativa cupom (`active=true`). Não altera `usageCount`.
6. **Exemplo:** `PATCH /api/v1/coupons/88/activate`
7. **Response:** `CouponResponse`.
8. **JSON:** idem.
9. **HTTP codes:** `200`, `401`, `403`, `404`.

---

### GET /api/v1/coupons/{id}/usages

1. **HTTP:** `GET /api/v1/coupons/{id}/usages`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** idem.
4. **Path:** `id`. Query: `page`, `size`.
5. **Regras:** retorna histórico de aplicações.
6. **Exemplo:** `GET /api/v1/coupons/88/usages?page=0&size=50`
7. **Response — `Page<CouponUsageResponse>`:** cada item com `id`, `couponId`, `couponCode`, `customerId`, `subscriptionId` (nullable), `chargeId` (nullable), `originalValue`, `discountAmount`, `finalValue`, `planCode`, `cycle`, `usedAt`.
8. **JSON resposta:**
```json
{
  "content": [
    {
      "id": 9001,
      "couponId": 88,
      "couponCode": "BLACKFRIDAY25",
      "customerId": 1001,
      "subscriptionId": 900,
      "chargeId": 5500,
      "originalValue": 199.90,
      "discountAmount": 49.97,
      "finalValue": 149.93,
      "planCode": "PRO",
      "cycle": "MONTHLY",
      "usedAt": "2026-04-12T11:30:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```
9. **HTTP codes:** `200`, `401`, `403`, `404`.

---

## Validação de Cupons (CouponValidationController)

Base path: `/api/v1/coupons/validate`

Dois endpoints: um **público** (sem autenticação) com validações básicas e um **autenticado** com todos os 8 checks. O SecurityConfig libera `/api/v1/coupons/validate/public` com `permitAll`.

**Checks aplicados pelo `CouponService`:**
1. **Existência** — coupon code existe no tenant
2. **Ativo** — `active=true`
3. **Vigência** — `validFrom ≤ now ≤ validUntil`
4. **Limite global** — `usageCount < maxUses`
5. **Escopo compatível** — request.scope == coupon.scope
6. **Plano permitido** — se `allowedPlans` definido, `planCode` deve estar na CSV
7. **Ciclo permitido** — se `allowedCycle` definido, `cycle` deve bater
8. **Limite por customer** (só autenticado) — usages do customer < `maxUsesPerCustomer`
9. **Whitelist customers** (só autenticado) — se `allowedCustomers` definido, `customerId` deve estar na CSV

O endpoint **público** executa 1-7. O **autenticado** executa 1-9.

Para `PERCENTAGE`: `discountAmount = value * discountValue / 100` (máx 90%). Para `FIXED_AMOUNT`: `discountAmount = min(discountValue, value)`. `finalValue = value - discountAmount` (nunca negativo).

---

### POST /api/v1/coupons/validate/public

1. **HTTP:** `POST /api/v1/coupons/validate/public`
2. **Autenticação:** **permitAll** (sem JWT). Porém `X-Company-Id` continua obrigatório para resolver o tenant e aplicar RLS na busca do cupom.
3. **Headers:**
   - `X-Company-Id: <id>` (obrigatório)
   - `Content-Type: application/json`
   - `Authorization`: **não** requerido
4. **DTO — `ValidateCouponRequest`:**
   - `couponCode` (String, **obrigatório**, `@NotBlank`)
   - `scope` (CouponScope, **obrigatório**, `@NotNull`) — `SUBSCRIPTION`/`CHARGE`
   - `planCode` (String, opcional) — necessário para check de plano permitido
   - `cycle` (String, opcional) — `MONTHLY`/`QUARTERLY`/`YEARLY`
   - `value` (BigDecimal, opcional) — para calcular desconto final
   - `customerId` (Long, opcional — **ignorado** no endpoint público)
5. **Regras:** aplica checks 1 a 7 somente. Se inválido, retorna `valid=false` com `message`. Se válido, preenche `discountAmount`, `finalValue` etc. Não incrementa `usageCount`.
6. **JSON exemplo:**
```json
{
  "couponCode": "BLACKFRIDAY25",
  "scope": "SUBSCRIPTION",
  "planCode": "PRO",
  "cycle": "MONTHLY",
  "value": 199.90
}
```
7. **Response — `CouponValidationResponse`:** `valid` (boolean), `message` (String — motivo da rejeição, null se válido), `discountType`, `applicationType`, `percentualDiscount` (BigDecimal — valor percentual se PERCENTAGE), `discountAmount`, `originalValue`, `finalValue`.
8. **JSON resposta (válido):**
```json
{
  "valid": true,
  "message": null,
  "discountType": "PERCENTAGE",
  "applicationType": "RECURRING",
  "percentualDiscount": 25.00,
  "discountAmount": 49.97,
  "originalValue": 199.90,
  "finalValue": 149.93
}
```
**JSON resposta (inválido):**
```json
{
  "valid": false,
  "message": "Cupom fora do periodo de vigencia",
  "discountType": null,
  "applicationType": null,
  "percentualDiscount": null,
  "discountAmount": null,
  "originalValue": null,
  "finalValue": null
}
```
9. **HTTP codes:** `200` (mesmo quando `valid=false`), `400` (body inválido), `404` (cupom inexistente pode retornar `200 valid=false` em vez de 404 — depende da impl).
10. **Observações:** Destinado a checkout público. Não exige autenticação — use com cautela para evitar enumeração de códigos.

---

### POST /api/v1/coupons/validate

1. **HTTP:** `POST /api/v1/coupons/validate`
2. **Autenticação:** JWT `COMPANY_ADMIN`/`COMPANY_OPERATOR` + `X-Company-Id`.
3. **Headers:** `Authorization: Bearer <JWT>`, `X-Company-Id`, `Content-Type`.
4. **DTO — `ValidateCouponRequest`:** idem público, mas `customerId` é relevante (usado nos checks 8 e 9).
5. **Regras:** executa checks 1 a 9 (inclusive limite por customer e whitelist de customers). Não incrementa `usageCount`.
6. **JSON exemplo:**
```json
{
  "couponCode": "BLACKFRIDAY25",
  "scope": "SUBSCRIPTION",
  "planCode": "PRO",
  "cycle": "MONTHLY",
  "value": 199.90,
  "customerId": 1001
}
```
7. **Response:** `CouponValidationResponse` (mesma estrutura).
8. **JSON resposta (inválido por customer):**
```json
{
  "valid": false,
  "message": "Cliente ja utilizou o cupom o numero maximo de vezes",
  "discountType": null,
  "applicationType": null,
  "percentualDiscount": null,
  "discountAmount": null,
  "originalValue": null,
  "finalValue": null
}
```
9. **HTTP codes:** `200`, `400`, `401`, `403`.
10. **Observações:** Recomendado no fluxo de criação de assinatura/cobrança interno. Validação real ocorre novamente na hora de aplicar (em `SubscriptionService`/`ChargeService`) para evitar race condition em `maxUses`.


---


### GET /api/v1/companies

**Método HTTP e Path Completo**
```
GET /api/v1/companies
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: `@PreAuthorize("hasRole('HOLDING_ADMIN')")`
- Acesso: Apenas administradores da holding
- Header X-Company-Id: Não requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)

**Query Parameters**
- `page` (Int, padrão 0): Número da página
- `size` (Int, padrão 20): Quantidade por página
- `sort` (String): Campo e direção (ex: nomeFantasia,asc)

**DTO de Response**
- List of CompanyResponse (paginado)

**Regras de Negócio**
- Retorna apenas empresas ativas (não deletadas)
- Suporta paginação e ordenação

**JSON de Request de Exemplo**
```
GET /api/v1/companies?page=0&size=20&sort=nomeFantasia,asc
```

**JSON de Response de Exemplo**
```json
{
  "content": [
    {
      "id": 1,
      "cnpj": "12345678901234",
      "razaoSocial": "Acme Corporation LTDA",
      "nomeFantasia": "Acme",
      "email": "contato@acme.com.br",
      "phone": "+5511987654321",
      "asaasEnv": "PRODUCTION",
      "hasAsaasKey": true,
      "status": "ACTIVE",
      "planChangePolicy": "IMMEDIATE_PRORATA",
      "downgradeValidationStrategy": "GRACE_PERIOD",
      "gracePeriodDays": 5,
      "createdAt": "2026-04-12T10:30:00",
      "updatedAt": "2026-04-12T10:30:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {"sorted": true, "direction": "ASC"}
  },
  "totalElements": 1,
  "totalPages": 1
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Lista retornada com sucesso
- `401 Unauthorized`: JWT inválido ou ausente
- `403 Forbidden`: Usuário não possui role HOLDING_ADMIN

---

### GET /api/v1/companies/{id}

**Método HTTP e Path Completo**
```
GET /api/v1/companies/{id}
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: `@PreAuthorize("hasRole('HOLDING_ADMIN')")`
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Não requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)

**DTO de Response**
- CompanyResponse (vide POST /companies)

**Regras de Negócio**
- Retorna os dados completos de uma empresa específica

**JSON de Response de Exemplo**
```json
{
  "id": 1,
  "cnpj": "12345678901234",
  "razaoSocial": "Acme Corporation LTDA",
  "nomeFantasia": "Acme",
  "email": "contato@acme.com.br",
  "phone": "+5511987654321",
  "asaasEnv": "PRODUCTION",
  "hasAsaasKey": true,
  "status": "ACTIVE",
  "planChangePolicy": "IMMEDIATE_PRORATA",
  "downgradeValidationStrategy": "GRACE_PERIOD",
  "gracePeriodDays": 5,
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T10:30:00"
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Empresa encontrada
- `401 Unauthorized`: JWT inválido ou ausente
- `403 Forbidden`: Usuário não possui role HOLDING_ADMIN
- `404 Not Found`: Empresa não existe

---

### PUT /api/v1/companies/{id}

**Método HTTP e Path Completo**
```
PUT /api/v1/companies/{id}
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: `@PreAuthorize("hasRole('HOLDING_ADMIN')")`
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Não requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `Content-Type: application/json`

**DTO de Request - UpdateCompanyRequest**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `razaoSocial` | String | Não | Razão social (atualizável) |
| `nomeFantasia` | String | Não | Nome fantasia (atualizável) |
| `email` | String | Não | Email de contato (atualizável) |
| `phone` | String | Não | Telefone (atualizável) |
| `status` | Enum | Não | Status: ACTIVE, SUSPENDED, DEFAULTING |
| `planChangePolicy` | Enum | Não | Política de mudança: IMMEDIATE_PRORATA, END_OF_CYCLE, IMMEDIATE_NO_PRORATA |
| `downgradeValidationStrategy` | Enum | Não | Estratégia: BLOCK, SCHEDULE, GRACE_PERIOD |
| `gracePeriodDays` | Integer | Não | Dias de graça |

**Regras de Negócio**
- Atualização parcial (apenas campos enviados são modificados)
- CNPJ não é atualizável (imutável)
- Chave API não é atualizada neste endpoint (usar PUT /credentials)
- Auditada (action: COMPANY_UPDATE, entity: Company)

**JSON de Request de Exemplo**
```json
{
  "nomeFantasia": "Acme Brasil",
  "email": "novo@acme.com.br",
  "status": "SUSPENDED",
  "gracePeriodDays": 10
}
```

**DTO de Response**
- CompanyResponse (vide POST /companies)

**JSON de Response de Exemplo**
```json
{
  "id": 1,
  "cnpj": "12345678901234",
  "razaoSocial": "Acme Corporation LTDA",
  "nomeFantasia": "Acme Brasil",
  "email": "novo@acme.com.br",
  "phone": "+5511987654321",
  "asaasEnv": "PRODUCTION",
  "hasAsaasKey": true,
  "status": "SUSPENDED",
  "planChangePolicy": "IMMEDIATE_PRORATA",
  "downgradeValidationStrategy": "GRACE_PERIOD",
  "gracePeriodDays": 10,
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T11:45:00"
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Empresa atualizada com sucesso
- `400 Bad Request`: Dados inválidos
- `401 Unauthorized`: JWT inválido ou ausente
- `403 Forbidden`: Usuário não possui role HOLDING_ADMIN
- `404 Not Found`: Empresa não existe

---

### PUT /api/v1/companies/{id}/credentials

**Método HTTP e Path Completo**
```
PUT /api/v1/companies/{id}/credentials
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: `@PreAuthorize("hasRole('HOLDING_ADMIN')")`
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Não requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `Content-Type: application/json`

**DTO de Request - UpdateCredentialsRequest**

| Campo | Tipo | Obrigatório | Validação | Descrição |
|-------|------|-------------|-----------|-----------|
| `asaasApiKey` | String | Sim | @NotBlank | Chave da API Asaas (será criptografada) |
| `asaasEnv` | Enum | Sim | @NotNull | Ambiente: SANDBOX ou PRODUCTION |

**Regras de Negócio**
- Chave API é criptografada com Jasypt antes de armazenar
- Apenas as credenciais Asaas são atualizadas
- Auditada (action: COMPANY_UPDATE_CREDENTIALS, entity: Company)

**JSON de Request de Exemplo**
```json
{
  "asaasApiKey": "aak_new_key_xxx",
  "asaasEnv": "SANDBOX"
}
```

**DTO de Response**
- Void (204 No Content)

**Códigos HTTP Possíveis**
- `204 No Content`: Credenciais atualizadas com sucesso
- `400 Bad Request`: Chave ou ambiente inválido
- `401 Unauthorized`: JWT inválido ou ausente
- `403 Forbidden`: Usuário não possui role HOLDING_ADMIN
- `404 Not Found`: Empresa não existe

---

### POST /api/v1/companies/{id}/test-connection

**Método HTTP e Path Completo**
```
POST /api/v1/companies/{id}/test-connection
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: `@PreAuthorize("hasRole('HOLDING_ADMIN')")`
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Não requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)

**Regras de Negócio**
- Faz chamada ao endpoint `/myAccount` da API Asaas
- Usa a chave API decriptada da empresa
- Respeita o ambiente configurado (sandbox/production)
- Retorna mapa simples com status de sucesso/falha

**JSON de Response de Exemplo**
```json
{
  "success": true,
  "message": "Conexao realizada com sucesso"
}
```

ou em caso de falha:

```json
{
  "success": false,
  "message": "Falha na conexao"
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Teste executado (com sucesso ou falha indicado no JSON)
- `400 Bad Request`: Empresa não possui chave API configurada
- `401 Unauthorized`: JWT inválido ou ausente
- `403 Forbidden`: Usuário não possui role HOLDING_ADMIN
- `404 Not Found`: Empresa não existe

---

### GET /api/v1/companies/me

**Método HTTP e Path Completo**
```
GET /api/v1/companies/me
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: `@PreAuthorize("isAuthenticated()") `- qualquer usuário autenticado
- Header X-Company-Id: Requerido para recuperar a empresa do usuário

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório, extraído do TenantContext)

**Regras de Negócio**
- Retorna os dados da empresa vinculada ao usuário autenticado (via TenantContext.getRequiredCompanyId())
- Qualquer usuário autenticado pode acessar seus próprios dados da empresa

**JSON de Response de Exemplo**
```json
{
  "id": 1,
  "cnpj": "12345678901234",
  "razaoSocial": "Acme Corporation LTDA",
  "nomeFantasia": "Acme",
  "email": "contato@acme.com.br",
  "phone": "+5511987654321",
  "asaasEnv": "PRODUCTION",
  "hasAsaasKey": true,
  "status": "ACTIVE",
  "planChangePolicy": "IMMEDIATE_PRORATA",
  "downgradeValidationStrategy": "GRACE_PERIOD",
  "gracePeriodDays": 5,
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T10:30:00"
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Dados retornados com sucesso
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Company não encontrada para o usuário

---

## CustomerController

### POST /api/v1/customers

**Método HTTP e Path Completo**
```
POST /api/v1/customers
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Sem `@PreAuthorize` explícito (autenticado é suficiente)
- Acesso: Qualquer usuário autenticado da empresa
- Header X-Company-Id: Requerido para isolar dados por tenant

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `Content-Type: application/json`
- `X-Company-Id: <company_id>` (obrigatório)

**DTO de Request - CreateCustomerRequest**

| Campo | Tipo | Obrigatório | Validação | Descrição |
|-------|------|-------------|-----------|-----------|
| `name` | String | Sim | @NotBlank | Nome completo do cliente |
| `document` | String | Sim | @NotBlank | CPF ou CNPJ (sem formatação) |
| `email` | String | Não | - | Email para contato |
| `phone` | String | Não | - | Telefone com DDD |
| `addressStreet` | String | Não | - | Rua/Avenida |
| `addressNumber` | String | Não | - | Número do endereço |
| `addressComplement` | String | Não | - | Complemento (apto, bloco, etc) |
| `addressNeighborhood` | String | Não | - | Bairro |
| `addressCity` | String | Não | - | Cidade |
| `addressState` | String | Não | - | Estado (UF, ex: SP, RJ) |
| `addressPostalCode` | String | Não | - | CEP (ex: 12345678 ou 12345-678) |

**Regras de Negócio**
- Documento (CPF/CNPJ) deve ser único entre clientes ativos da empresa
- Cliente é automaticamente sincronizado com Asaas (criação de customer no gateway)
- Caso Asaas falhe, a transação é revertida
- Evento de auditoria é registrado (action: CUSTOMER_CREATE, entity: Customer)
- Evento de integração é publicado (CustomerCreatedEvent)
- Escopo: X-Company-Id extraído do TenantContext

**JSON de Request de Exemplo**
```json
{
  "name": "João da Silva",
  "document": "12345678901",
  "email": "joao@example.com",
  "phone": "11987654321",
  "addressStreet": "Rua das Flores",
  "addressNumber": "123",
  "addressComplement": "Apto 42",
  "addressNeighborhood": "Centro",
  "addressCity": "São Paulo",
  "addressState": "SP",
  "addressPostalCode": "01234567"
}
```

**DTO de Response - CustomerResponse**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | Long | ID único do cliente |
| `companyId` | Long | ID da empresa vinculada |
| `asaasId` | String | ID do cliente no Asaas (retornado após sincronização) |
| `name` | String | Nome do cliente |
| `document` | String | CPF/CNPJ |
| `email` | String | Email |
| `phone` | String | Telefone |
| `addressStreet` | String | Rua |
| `addressNumber` | String | Número |
| `addressComplement` | String | Complemento |
| `addressNeighborhood` | String | Bairro |
| `addressCity` | String | Cidade |
| `addressState` | String | Estado |
| `addressPostalCode` | String | CEP |
| `creditBalance` | BigDecimal | Saldo de crédito (pré-pagamento) |
| `createdAt` | LocalDateTime | Data/hora de criação |
| `updatedAt` | LocalDateTime | Data/hora da última atualização |

**JSON de Response de Exemplo**
```json
{
  "id": 1,
  "companyId": 1,
  "asaasId": "cus_123456",
  "name": "João da Silva",
  "document": "12345678901",
  "email": "joao@example.com",
  "phone": "11987654321",
  "addressStreet": "Rua das Flores",
  "addressNumber": "123",
  "addressComplement": "Apto 42",
  "addressNeighborhood": "Centro",
  "addressCity": "São Paulo",
  "addressState": "SP",
  "addressPostalCode": "01234567",
  "creditBalance": "0.00",
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T10:30:00"
}
```

**Códigos HTTP Possíveis**
- `201 Created`: Cliente criado com sucesso
- `400 Bad Request`: Documento já cadastrado ou dados inválidos
- `401 Unauthorized`: JWT inválido ou ausente
- `500 Internal Server Error`: Falha na sincronização com Asaas

**Observações/Edge Cases**
- Documento fica bloqueado para recadastro enquanto cliente está ativo
- Soft delete libera o documento para novo cadastro
- Asaas é fonte de verdade para sincronização; falhas de API revertem a transação

---

### GET /api/v1/customers

**Método HTTP e Path Completo**
```
GET /api/v1/customers
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**Query Parameters**
- `search` (String, opcional): Busca por nome, documento ou email (case-insensitive)
- `page` (Int, padrão 0): Número da página
- `size` (Int, padrão 20): Quantidade por página
- `sort` (String): Campo e direção (ex: name,asc)

**Regras de Negócio**
- Retorna apenas clientes ativos (não deletados)
- Busca usa LIKE no banco (se search for informado)
- Isolado por company (X-Company-Id)

**JSON de Request de Exemplo**
```
GET /api/v1/customers?search=joao&page=0&size=20
```

**JSON de Response de Exemplo**
```json
{
  "content": [
    {
      "id": 1,
      "companyId": 1,
      "asaasId": "cus_123456",
      "name": "João da Silva",
      "document": "12345678901",
      "email": "joao@example.com",
      "phone": "11987654321",
      "addressStreet": "Rua das Flores",
      "addressNumber": "123",
      "addressComplement": "Apto 42",
      "addressNeighborhood": "Centro",
      "addressCity": "São Paulo",
      "addressState": "SP",
      "addressPostalCode": "01234567",
      "creditBalance": "0.00",
      "createdAt": "2026-04-12T10:30:00",
      "updatedAt": "2026-04-12T10:30:00"
    }
  ],
  "pageable": {"pageNumber": 0, "pageSize": 20},
  "totalElements": 1,
  "totalPages": 1
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Lista retornada
- `401 Unauthorized`: JWT inválido ou ausente

---

### GET /api/v1/customers/{id}

**Método HTTP e Path Completo**
```
GET /api/v1/customers/{id}
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**DTO de Response**
- CustomerResponse (vide POST /customers)

**Regras de Negócio**
- Retorna erro 404 se cliente deletado ou não existe

**JSON de Response de Exemplo**
```json
{
  "id": 1,
  "companyId": 1,
  "asaasId": "cus_123456",
  "name": "João da Silva",
  "document": "12345678901",
  "email": "joao@example.com",
  "phone": "11987654321",
  "addressStreet": "Rua das Flores",
  "addressNumber": "123",
  "addressComplement": "Apto 42",
  "addressNeighborhood": "Centro",
  "addressCity": "São Paulo",
  "addressState": "SP",
  "addressPostalCode": "01234567",
  "creditBalance": "0.00",
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T10:30:00"
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Cliente encontrado
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Cliente não existe ou foi deletado

---

### PUT /api/v1/customers/{id}

**Método HTTP e Path Completo**
```
PUT /api/v1/customers/{id}
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `Content-Type: application/json`
- `X-Company-Id: <company_id>` (obrigatório)

**DTO de Request - UpdateCustomerRequest**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `name` | String | Não | Novo nome |
| `email` | String | Não | Novo email |
| `phone` | String | Não | Novo telefone |
| `addressStreet` | String | Não | Nova rua |
| `addressNumber` | String | Não | Novo número |
| `addressComplement` | String | Não | Novo complemento |
| `addressNeighborhood` | String | Não | Novo bairro |
| `addressCity` | String | Não | Nova cidade |
| `addressState` | String | Não | Novo estado |
| `addressPostalCode` | String | Não | Novo CEP |

**Regras de Negócio**
- Atualização parcial (apenas campos enviados)
- Documento não é atualizável
- Alterações são sincronizadas automaticamente com Asaas (se asaasId existir)
- Auditada (action: CUSTOMER_UPDATE, entity: Customer)

**JSON de Request de Exemplo**
```json
{
  "email": "novo@example.com",
  "phone": "11999999999"
}
```

**DTO de Response**
- CustomerResponse (vide POST /customers)

**Códigos HTTP Possíveis**
- `200 OK`: Cliente atualizado
- `400 Bad Request`: Dados inválidos
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Cliente não existe

---

### DELETE /api/v1/customers/{id}

**Método HTTP e Path Completo**
```
DELETE /api/v1/customers/{id}
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**Regras de Negócio**
- Soft delete (exclusão lógica)
- Registro é mantido no banco com data de exclusão
- Documento fica disponível para recadastro
- Não é removido do Asaas (apenas marcado como deletado localmente)
- Auditada (action: CUSTOMER_SOFT_DELETE, entity: Customer)

**DTO de Response**
- Void (204 No Content)

**Códigos HTTP Possíveis**
- `204 No Content`: Cliente deletado com sucesso
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Cliente não existe

---

### POST /api/v1/customers/{id}/restore

**Método HTTP e Path Completo**
```
POST /api/v1/customers/{id}/restore
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: `@PreAuthorize("hasAnyRole('HOLDING_ADMIN', 'COMPANY_ADMIN')")`
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**Regras de Negócio**
- Reverte soft delete
- Falha se documento já está em uso por outro cliente ativo
- Apenas HOLDING_ADMIN ou COMPANY_ADMIN podem restaurar
- Auditada (action: CUSTOMER_RESTORE, entity: Customer)

**DTO de Response**
- CustomerResponse (vide POST /customers)

**JSON de Response de Exemplo**
```json
{
  "id": 1,
  "companyId": 1,
  "asaasId": "cus_123456",
  "name": "João da Silva",
  "document": "12345678901",
  "email": "joao@example.com",
  "phone": "11987654321",
  "addressStreet": "Rua das Flores",
  "addressNumber": "123",
  "addressComplement": "Apto 42",
  "addressNeighborhood": "Centro",
  "addressCity": "São Paulo",
  "addressState": "SP",
  "addressPostalCode": "01234567",
  "creditBalance": "0.00",
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T10:45:00"
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Cliente restaurado
- `400 Bad Request`: Cliente não está deletado ou documento já em uso
- `401 Unauthorized`: JWT inválido ou ausente
- `403 Forbidden`: Usuário não é HOLDING_ADMIN nem COMPANY_ADMIN
- `404 Not Found`: Cliente não existe

---

### POST /api/v1/customers/{id}/sync

**Método HTTP e Path Completo**
```
POST /api/v1/customers/{id}/sync
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**Regras de Negócio**
- Forca resincronizacao com Asaas
- Atualiza nome, email e telefone a partir dos dados retornados por Asaas
- Falha se cliente não possui asaasId
- Útil para reverter mudancas locais ou sincronizar informacoes desatualizadas

**DTO de Response**
- CustomerResponse (vide POST /customers)

**JSON de Response de Exemplo**
```json
{
  "id": 1,
  "companyId": 1,
  "asaasId": "cus_123456",
  "name": "João Silva Updated",
  "document": "12345678901",
  "email": "joao.silva@asaas.com",
  "phone": "11999888777",
  "addressStreet": "Rua das Flores",
  "addressNumber": "123",
  "addressComplement": "Apto 42",
  "addressNeighborhood": "Centro",
  "addressCity": "São Paulo",
  "addressState": "SP",
  "addressPostalCode": "01234567",
  "creditBalance": "0.00",
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T11:00:00"
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Sincronizacao concluida
- `400 Bad Request`: Cliente não possui asaasId
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Cliente não existe

---

### GET /api/v1/customers/{id}/credit-balance

**Método HTTP e Path Completo**
```
GET /api/v1/customers/{id}/credit-balance
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**Query Parameters**
- `page` (Int, padrão 0): Número da página
- `size` (Int, padrão 20): Quantidade por página (padrão vem com @PageableDefault(size=20))

**Regras de Negócio**
- Retorna saldo atual de crédito (pré-pagamento)
- Retorna ledger append-only com histórico de movimentacoes
- Ledger inclui: adições, descontos, devoluções, etc

**JSON de Response de Exemplo**
```json
{
  "balance": "150.50",
  "ledger": {
    "content": [
      {
        "id": 1,
        "customerId": 1,
        "type": "CREDIT",
        "amount": "100.00",
        "reason": "INITIAL_TOPUP",
        "relatedSubscriptionId": null,
        "relatedChargeId": null,
        "description": "Crédito inicial adicionado",
        "createdAt": "2026-04-10T10:00:00"
      },
      {
        "id": 2,
        "customerId": 1,
        "type": "DEBIT",
        "amount": "50.00",
        "reason": "SUBSCRIPTION_CHARGE",
        "relatedSubscriptionId": 1,
        "relatedChargeId": 1,
        "description": "Cobrança de assinatura Plano Pro",
        "createdAt": "2026-04-11T14:30:00"
      },
      {
        "id": 3,
        "customerId": 1,
        "type": "CREDIT",
        "amount": "100.50",
        "reason": "TOPUP",
        "relatedSubscriptionId": null,
        "relatedChargeId": null,
        "description": "Recarga de crédito",
        "createdAt": "2026-04-12T09:15:00"
      }
    ],
    "pageable": {"pageNumber": 0, "pageSize": 20},
    "totalElements": 3,
    "totalPages": 1
  }
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Saldo e ledger retornados
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Cliente não existe

---

## PlanController

### POST /api/v1/plans

**Método HTTP e Path Completo**
```
POST /api/v1/plans
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `Content-Type: application/json`
- `X-Company-Id: <company_id>` (obrigatório)

**DTO de Request - CreatePlanRequest**

| Campo | Tipo | Obrigatório | Validação | Descrição |
|-------|------|-------------|-----------|-----------|
| `name` | String | Sim | @NotBlank | Nome do plano (ex: "Plano Pro") |
| `description` | String | Não | - | Descricao breve |
| `codigo` | String | Sim | @NotBlank, @Pattern([a-z][a-z0-9-]*) | Slug único imutável (ex: "plano-pro") |
| `precoMensal` | BigDecimal | Sim | @NotNull, @Positive | Preço mensal (obrigatório, ex: 99.90) |
| `precoAnual` | BigDecimal | Não | - | Preço anual (opcional, validado contra margem de 5%) |
| `descontoPercentualAnual` | BigDecimal | Não | - | Desconto percentual anual (ex: 10 para 10%) |
| `promoMensalAtiva` | Boolean | Não | - | Flag se promoção mensal está ativa |
| `promoMensalPreco` | BigDecimal | Não | - | Preço promocional mensal (obrigatório se promoMensalAtiva=true) |
| `promoMensalTexto` | String | Não | - | Texto da promoção mensal (ex: "50% off") |
| `promoMensalInicio` | LocalDateTime | Não | - | Início da promoção mensal (obrigatório se ativa) |
| `promoMensalFim` | LocalDateTime | Não | - | Fim da promoção mensal (obrigatório se ativa) |
| `promoAnualAtiva` | Boolean | Não | - | Flag se promoção anual está ativa |
| `promoAnualPreco` | BigDecimal | Não | - | Preço promocional anual (obrigatório se promoAnualAtiva=true) |
| `promoAnualTexto` | String | Não | - | Texto da promoção anual |
| `promoAnualInicio` | LocalDateTime | Não | - | Início da promoção anual (obrigatório se ativa) |
| `promoAnualFim` | LocalDateTime | Não | - | Fim da promoção anual (obrigatório se ativa) |
| `trialDays` | Integer | Não | - | Dias de trial/graça (padrão: 0) |
| `setupFee` | BigDecimal | Não | - | Taxa de configuração (padrão: 0.00) |
| `limits` | String | Não | - | JSON array com limites em JSONB format: [{"text":"Limite de requisições","included":true},...] |
| `features` | String | Não | - | JSON array com features em JSONB format: [{"text":"Feature X","included":true},...] |
| `tierOrder` | Integer | Não | - | Ordem de exibição na tier (padrão: 0) |

**Regras de Negócio**
- Código deve ser único por empresa (validação `existsByCodigoAndCompanyId()`)
- Código é imutável após criação (slug)
- precoMensal é obrigatório, precoAnual é opcional
- Se precoAnual informado, é validado contra margem mínima de 5% do preço mensal anualizado
- Promoções (mensal/anual): validação em grupo (todos os campos obrigatórios se ativa=true)
- Preço promocional deve ser menor que preço base
- Data de fim deve ser posterior a data de início
- Features e limits usam formato JSONB (armazenado como texto, mas estruturado)
- Plano inicia ativo por padrão (active=true)
- Auditada (action: PLAN_CREATE, entity: Plan)

**JSON de Request de Exemplo**
```json
{
  "name": "Plano Pro",
  "description": "Melhor para times pequenos",
  "codigo": "plano-pro",
  "precoMensal": 99.90,
  "precoAnual": 999.00,
  "descontoPercentualAnual": 15,
  "promoMensalAtiva": true,
  "promoMensalPreco": 49.90,
  "promoMensalTexto": "50% de desconto por 3 meses",
  "promoMensalInicio": "2026-04-01T00:00:00",
  "promoMensalFim": "2026-06-30T23:59:59",
  "promoAnualAtiva": false,
  "trialDays": 7,
  "setupFee": 0.00,
  "features": "[{\"text\":\"API Completa\",\"included\":true},{\"text\":\"Suporte 24/7\",\"included\":false}]",
  "limits": "[{\"text\":\"10.000 requisições/mês\",\"included\":true}]",
  "tierOrder": 2
}
```

**DTO de Response - PlanResponse**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | Long | ID único do plano |
| `companyId` | Long | ID da empresa |
| `name` | String | Nome do plano |
| `description` | String | Descrição |
| `codigo` | String | Slug único |
| `precoMensal` | BigDecimal | Preço mensal |
| `precoAnual` | BigDecimal | Preço anual |
| `descontoPercentualAnual` | BigDecimal | Desconto anual em % |
| `precoSemestral` | BigDecimal | Preço semestral (calculado) |
| `promoMensalAtiva` | Boolean | Promoção mensal ativa |
| `promoMensalPreco` | BigDecimal | Preço promo mensal |
| `promoMensalTexto` | String | Texto promo mensal |
| `promoMensalInicio` | LocalDateTime | Início promo mensal |
| `promoMensalFim` | LocalDateTime | Fim promo mensal |
| `promoAnualAtiva` | Boolean | Promoção anual ativa |
| `promoAnualPreco` | BigDecimal | Preço promo anual |
| `promoAnualTexto` | String | Texto promo anual |
| `promoAnualInicio` | LocalDateTime | Início promo anual |
| `promoAnualFim` | LocalDateTime | Fim promo anual |
| `trialDays` | Integer | Dias de trial |
| `setupFee` | BigDecimal | Taxa de setup |
| `active` | Boolean | Plano ativo |
| `version` | Integer | Versão do plano (para histórico) |
| `limits` | String | JSON de limites |
| `features` | String | JSON de features |
| `tierOrder` | Integer | Ordem de tier |
| `createdAt` | LocalDateTime | Data criação |
| `updatedAt` | LocalDateTime | Data atualização |

**JSON de Response de Exemplo**
```json
{
  "id": 1,
  "companyId": 1,
  "name": "Plano Pro",
  "description": "Melhor para times pequenos",
  "codigo": "plano-pro",
  "precoMensal": 99.90,
  "precoAnual": 999.00,
  "descontoPercentualAnual": 15,
  "precoSemestral": 599.40,
  "promoMensalAtiva": true,
  "promoMensalPreco": 49.90,
  "promoMensalTexto": "50% de desconto por 3 meses",
  "promoMensalInicio": "2026-04-01T00:00:00",
  "promoMensalFim": "2026-06-30T23:59:59",
  "promoAnualAtiva": false,
  "promoAnualPreco": null,
  "promoAnualTexto": null,
  "promoAnualInicio": null,
  "promoAnualFim": null,
  "trialDays": 7,
  "setupFee": 0.00,
  "active": true,
  "version": 1,
  "features": "[{\"text\":\"API Completa\",\"included\":true},{\"text\":\"Suporte 24/7\",\"included\":false}]",
  "limits": "[{\"text\":\"10.000 requisições/mês\",\"included\":true}]",
  "tierOrder": 2,
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T10:30:00"
}
```

**Códigos HTTP Possíveis**
- `201 Created`: Plano criado com sucesso
- `400 Bad Request`: Código duplicado, validações falharam (margem anual, preço promo, datas)
- `401 Unauthorized`: JWT inválido ou ausente

**Observações/Edge Cases**
- Código deve seguir padrão lowercase + números + hífen, começando com letra
- precoAnual = precoMensal * 12 com tolerância de ±5%
- Preços promocionais devem ser menores que preços base
- Promover vigência é validada com data/hora atual (NOW())

---

### GET /api/v1/plans

**Método HTTP e Path Completo**
```
GET /api/v1/plans
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**Query Parameters**
- `page` (Int, padrão 0): Número da página
- `size` (Int, padrão 20): Quantidade por página
- `sort` (String): Campo e direção (ex: precoMensal,desc ou tierOrder,asc)

**Regras de Negócio**
- Retorna apenas planos ativos (não deletados) da empresa
- Escopo isolado por X-Company-Id

**JSON de Response de Exemplo**
```json
{
  "content": [
    {
      "id": 1,
      "companyId": 1,
      "name": "Plano Pro",
      "description": "Melhor para times pequenos",
      "codigo": "plano-pro",
      "precoMensal": 99.90,
      "precoAnual": 999.00,
      "descontoPercentualAnual": 15,
      "precoSemestral": 599.40,
      "promoMensalAtiva": true,
      "promoMensalPreco": 49.90,
      "promoMensalTexto": "50% de desconto por 3 meses",
      "promoMensalInicio": "2026-04-01T00:00:00",
      "promoMensalFim": "2026-06-30T23:59:59",
      "promoAnualAtiva": false,
      "trialDays": 7,
      "setupFee": 0.00,
      "active": true,
      "version": 1,
      "features": "[{\"text\":\"API Completa\",\"included\":true}]",
      "limits": "[{\"text\":\"10.000 requisições/mês\",\"included\":true}]",
      "tierOrder": 2,
      "createdAt": "2026-04-12T10:30:00",
      "updatedAt": "2026-04-12T10:30:00"
    }
  ],
  "pageable": {"pageNumber": 0, "pageSize": 20},
  "totalElements": 1,
  "totalPages": 1
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Lista retornada
- `401 Unauthorized`: JWT inválido ou ausente

---

### GET /api/v1/plans/{id}

**Método HTTP e Path Completo**
```
GET /api/v1/plans/{id}
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**DTO de Response**
- PlanResponse (vide POST /plans)

**Regras de Negócio**
- Retorna erro 404 se plano deletado ou não existe

**JSON de Response de Exemplo**
```json
{
  "id": 1,
  "companyId": 1,
  "name": "Plano Pro",
  "description": "Melhor para times pequenos",
  "codigo": "plano-pro",
  "precoMensal": 99.90,
  "precoAnual": 999.00,
  "descontoPercentualAnual": 15,
  "precoSemestral": 599.40,
  "promoMensalAtiva": true,
  "promoMensalPreco": 49.90,
  "promoMensalTexto": "50% de desconto por 3 meses",
  "promoMensalInicio": "2026-04-01T00:00:00",
  "promoMensalFim": "2026-06-30T23:59:59",
  "promoAnualAtiva": false,
  "trialDays": 7,
  "setupFee": 0.00,
  "active": true,
  "version": 1,
  "features": "[{\"text\":\"API Completa\",\"included\":true}]",
  "limits": "[{\"text\":\"10.000 requisições/mês\",\"included\":true}]",
  "tierOrder": 2,
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T10:30:00"
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Plano encontrado
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Plano não existe ou foi deletado

---

### PUT /api/v1/plans/{id}

**Método HTTP e Path Completo**
```
PUT /api/v1/plans/{id}
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `Content-Type: application/json`
- `X-Company-Id: <company_id>` (obrigatório)

**DTO de Request - UpdatePlanRequest**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `name` | String | Não | Novo nome |
| `description` | String | Não | Nova descrição |
| `precoMensal` | BigDecimal | Não | Novo preço mensal |
| `precoAnual` | BigDecimal | Não | Novo preço anual |
| `descontoPercentualAnual` | BigDecimal | Não | Novo desconto anual % |
| `promoMensalAtiva` | Boolean | Não | Ativar/desativar promo mensal |
| `promoMensalPreco` | BigDecimal | Não | Novo preço promo mensal |
| `promoMensalTexto` | String | Não | Novo texto promo mensal |
| `promoMensalInicio` | LocalDateTime | Não | Novo início promo mensal |
| `promoMensalFim` | LocalDateTime | Não | Novo fim promo mensal |
| `promoAnualAtiva` | Boolean | Não | Ativar/desativar promo anual |
| `promoAnualPreco` | BigDecimal | Não | Novo preço promo anual |
| `promoAnualTexto` | String | Não | Novo texto promo anual |
| `promoAnualInicio` | LocalDateTime | Não | Novo início promo anual |
| `promoAnualFim` | LocalDateTime | Não | Novo fim promo anual |
| `trialDays` | Integer | Não | Novo período trial |
| `setupFee` | BigDecimal | Não | Nova taxa setup |
| `limits` | String | Não | Novo JSON de limites |
| `features` | String | Não | Novo JSON de features |
| `tierOrder` | Integer | Não | Nova ordem tier |

**Regras de Negócio**
- Atualização parcial (apenas campos enviados)
- Código (slug) é imutável - não pode ser alterado
- Assinaturas existentes continuam com versão antiga do plano
- Re-valida promoções e preços ao atualizar
- ATENÇÃO: Use POST /{id}/new-version para mudanças de preço que não devem afetar assinaturas existentes
- Auditada (action: PLAN_UPDATE, entity: Plan)

**JSON de Request de Exemplo**
```json
{
  "precoMensal": 119.90,
  "promoMensalAtiva": false,
  "tierOrder": 3
}
```

**DTO de Response**
- PlanResponse (vide POST /plans)

**Códigos HTTP Possíveis**
- `200 OK`: Plano atualizado
- `400 Bad Request`: Validações falharam
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Plano não existe

---

### PATCH /api/v1/plans/{id}/activate

**Método HTTP e Path Completo**
```
PATCH /api/v1/plans/{id}/activate
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**Regras de Negócio**
- Ativa um plano (active=true)
- Plano ativo fica disponível para novas assinaturas
- Idempotente (executar múltiplas vezes não causa erro)
- Auditada (action: PLAN_ACTIVATE, entity: Plan)

**DTO de Response**
- PlanResponse (vide POST /plans) com active=true

**JSON de Response de Exemplo**
```json
{
  "id": 1,
  "companyId": 1,
  "name": "Plano Pro",
  "description": "Melhor para times pequenos",
  "codigo": "plano-pro",
  "precoMensal": 99.90,
  "precoAnual": 999.00,
  "descontoPercentualAnual": 15,
  "precoSemestral": 599.40,
  "promoMensalAtiva": true,
  "promoMensalPreco": 49.90,
  "promoMensalTexto": "50% de desconto por 3 meses",
  "promoMensalInicio": "2026-04-01T00:00:00",
  "promoMensalFim": "2026-06-30T23:59:59",
  "promoAnualAtiva": false,
  "trialDays": 7,
  "setupFee": 0.00,
  "active": true,
  "version": 1,
  "features": "[{\"text\":\"API Completa\",\"included\":true}]",
  "limits": "[{\"text\":\"10.000 requisições/mês\",\"included\":true}]",
  "tierOrder": 2,
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T11:00:00"
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Plano ativado
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Plano não existe

---

### PATCH /api/v1/plans/{id}/deactivate

**Método HTTP e Path Completo**
```
PATCH /api/v1/plans/{id}/deactivate
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**Regras de Negócio**
- Desativa um plano (active=false)
- Assinaturas existentes continuam funcionando
- Impede novas assinaturas com este plano
- Idempotente
- Auditada (action: PLAN_DEACTIVATE, entity: Plan)

**DTO de Response**
- PlanResponse (vide POST /plans) com active=false

**Códigos HTTP Possíveis**
- `200 OK`: Plano desativado
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Plano não existe

---

### DELETE /api/v1/plans/{id}

**Método HTTP e Path Completo**
```
DELETE /api/v1/plans/{id}
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**Regras de Negócio**
- Soft delete (exclusão lógica)
- Bloqueado se houver assinaturas ativas vinculadas (verifica countByPlanIdAndStatus com SubscriptionStatus.ACTIVE)
- Mantém registro no banco para integridade histórica
- Auditada (action: PLAN_SOFT_DELETE, entity: Plan)

**DTO de Response**
- Void (204 No Content)

**Códigos HTTP Possíveis**
- `204 No Content`: Plano deletado com sucesso
- `400 Bad Request`: Plano possui assinaturas ativas
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Plano não existe

---

### POST /api/v1/plans/{id}/new-version

**Método HTTP e Path Completo**
```
POST /api/v1/plans/{id}/new-version
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `Content-Type: application/json`
- `X-Company-Id: <company_id>` (obrigatório)

**DTO de Request - UpdatePlanRequest**
(Mesmo que PUT /plans/{id})

**Regras de Negócio**
- Cria nova versão do plano com mesmo código (slug imutável)
- Plano original é desativado automaticamente (active=false)
- Novo plano é criado ativo (active=true)
- Assinaturas existentes continuam vinculadas a versão antiga
- Novas assinaturas usam nova versão (via código)
- Version é incrementado (findMaxVersionByCompanyAndCodigo + 1)
- Útil para mudanças de preço sem impactar assinaturas em andamento
- Auditada (action: PLAN_NEW_VERSION, entity: Plan)

**JSON de Request de Exemplo**
```json
{
  "precoMensal": 129.90,
  "precoAnual": 1299.00
}
```

**DTO de Response**
- PlanResponse (vide POST /plans) com novo version e active=true

**JSON de Response de Exemplo**
```json
{
  "id": 2,
  "companyId": 1,
  "name": "Plano Pro",
  "description": "Melhor para times pequenos",
  "codigo": "plano-pro",
  "precoMensal": 129.90,
  "precoAnual": 1299.00,
  "descontoPercentualAnual": 15,
  "precoSemestral": 779.40,
  "promoMensalAtiva": true,
  "promoMensalPreco": 49.90,
  "promoMensalTexto": "50% de desconto por 3 meses",
  "promoMensalInicio": "2026-04-01T00:00:00",
  "promoMensalFim": "2026-06-30T23:59:59",
  "promoAnualAtiva": false,
  "trialDays": 7,
  "setupFee": 0.00,
  "active": true,
  "version": 2,
  "features": "[{\"text\":\"API Completa\",\"included\":true}]",
  "limits": "[{\"text\":\"10.000 requisições/mês\",\"included\":true}]",
  "tierOrder": 2,
  "createdAt": "2026-04-12T11:15:00",
  "updatedAt": "2026-04-12T11:15:00"
}
```

**Códigos HTTP Possíveis**
- `201 Created`: Nova versão criada
- `400 Bad Request`: Validações falharam
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Plano original não existe

**Observações/Edge Cases**
- Plano original fica com version=1, novo fica com version=2, etc
- Múltiplas versões do mesmo código (via plan.codigo) coexistem no DB
- Apenas uma versão por código fica active=true (a mais recente)

---

### GET /api/v1/plans/codigo/{codigo}

**Método HTTP e Path Completo**
```
GET /api/v1/plans/codigo/{codigo}
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `codigo` (String, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**Regras de Negócio**
- Retorna o plano ativo (active=true) com o código informado
- Busca: findByCodigoAndCompanyIdAndActiveTrue()
- Útil para clientes buscarem plano por slug imutável

**DTO de Response**
- PlanResponse (vide POST /plans)

**JSON de Response de Exemplo**
```json
{
  "id": 2,
  "companyId": 1,
  "name": "Plano Pro",
  "description": "Melhor para times pequenos",
  "codigo": "plano-pro",
  "precoMensal": 129.90,
  "precoAnual": 1299.00,
  "descontoPercentualAnual": 15,
  "precoSemestral": 779.40,
  "promoMensalAtiva": true,
  "promoMensalPreco": 49.90,
  "promoMensalTexto": "50% de desconto por 3 meses",
  "promoMensalInicio": "2026-04-01T00:00:00",
  "promoMensalFim": "2026-06-30T23:59:59",
  "promoAnualAtiva": false,
  "trialDays": 7,
  "setupFee": 0.00,
  "active": true,
  "version": 2,
  "features": "[{\"text\":\"API Completa\",\"included\":true}]",
  "limits": "[{\"text\":\"10.000 requisições/mês\",\"included\":true}]",
  "tierOrder": 2,
  "createdAt": "2026-04-12T11:15:00",
  "updatedAt": "2026-04-12T11:15:00"
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Plano encontrado
- `400 Bad Request`: Plano com código não encontrado ou inativo
- `401 Unauthorized`: JWT inválido ou ausente

---

### GET /api/v1/plans/{id}/pricing

**Método HTTP e Path Completo**
```
GET /api/v1/plans/{id}/pricing
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**Regras de Negócio**
- Retorna preços efetivos considerando promoções vigentes
- Calcula para cada ciclo: MONTHLY, SEMIANNUALLY (6x mensal), YEARLY
- Validação: promo está vigente se agora está entre promoInicio e promoFim
- Útil para frontend montar cards de pricing

**DTO de Response - PlanPricingResponse**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | Long | ID do plano |
| `codigo` | String | Código do plano |
| `name` | String | Nome |
| `precoMensal` | BigDecimal | Preço efetivo mensal (com promo ou base) |
| `precoSemestral` | BigDecimal | Preço efetivo semestral (6x mensal ou calculado) |
| `precoAnual` | BigDecimal | Preço efetivo anual (com promo ou base ou calculado de mensal) |
| `descontoPercentualAnual` | BigDecimal | Desconto anual % |
| `promoMensal` | PromoPricing | Info promo mensal (ativa, preço, texto, válido até) |
| `promoAnual` | PromoPricing | Info promo anual (ativa, preço, texto, válido até) |
| `features` | String | JSON de features |
| `limits` | String | JSON de limites |

PromoPricing:
| Campo | Tipo | Descrição |
|-------|------|-----------|
| `ativa` | Boolean | Se promo está vigente agora |
| `preco` | BigDecimal | Preço promocional (null se não ativa) |
| `texto` | String | Texto descritivo da promo (null se não ativa) |
| `validaAte` | LocalDateTime | Data/hora de fim (null se não ativa) |

**JSON de Response de Exemplo**
```json
{
  "id": 2,
  "codigo": "plano-pro",
  "name": "Plano Pro",
  "precoMensal": 49.90,
  "precoSemestral": 299.40,
  "precoAnual": 1299.00,
  "descontoPercentualAnual": 15,
  "promoMensal": {
    "ativa": true,
    "preco": 49.90,
    "texto": "50% de desconto por 3 meses",
    "validaAte": "2026-06-30T23:59:59"
  },
  "promoAnual": {
    "ativa": false,
    "preco": null,
    "texto": null,
    "validaAte": null
  },
  "features": "[{\"text\":\"API Completa\",\"included\":true}]",
  "limits": "[{\"text\":\"10.000 requisições/mês\",\"included\":true}]"
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Pricing retornado
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Plano não existe

---

## SubscriptionController

### POST /api/v1/subscriptions

**Método HTTP e Path Completo**
```
POST /api/v1/subscriptions
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `Content-Type: application/json`
- `X-Company-Id: <company_id>` (obrigatório)
- `Idempotency-Key` (recomendado): Para garantir idempotência

**DTO de Request - CreateSubscriptionRequest**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `customerId` | Long | Sim | ID do cliente |
| `planId` | Long | Sim | ID do plano |
| `billingType` | Enum | Sim | Tipo de cobrança: PIX, CREDIT_CARD, DEBIT_CARD, BOLETO, UNDEFINED |
| `cycle` | Enum | Sim | Ciclo: MONTHLY, SEMIANNUALLY, YEARLY |
| `nextDueDate` | LocalDate | Não | Próxima data de cobrança (padrão: amanhã) |
| `description` | String | Não | Descrição customizada (padrão: nome do plano) |
| `externalReference` | String | Não | Referência externa para rastreamento |
| `creditCard` | Object | Não | Dados do cartão (se billingType=CREDIT_CARD) |
| `creditCard.holderName` | String | Não | Nome do titular |
| `creditCard.number` | String | Não | Número do cartão (sem espaços, apenas dígitos) |
| `creditCard.expiryMonth` | String | Não | Mês (MM, ex: 12) |
| `creditCard.expiryYear` | String | Não | Ano (YYYY, ex: 2026) |
| `creditCard.ccv` | String | Não | Código CVV (3-4 dígitos) |
| `creditCardHolderInfo` | Object | Não | Dados adicionais do titular |
| `creditCardHolderInfo.name` | String | Não | Nome completo |
| `creditCardHolderInfo.email` | String | Não | Email |
| `creditCardHolderInfo.cpfCnpj` | String | Não | CPF/CNPJ do titular |
| `creditCardHolderInfo.postalCode` | String | Não | CEP |
| `creditCardHolderInfo.addressNumber` | String | Não | Número do endereço |
| `creditCardHolderInfo.phone` | String | Não | Telefone |
| `creditCardToken` | String | Não | Token do cartão (se usar tokenização) |
| `remoteIp` | String | Não | IP remoto (para fraude detection) |
| `couponCode` | String | Não | Código de cupom/desconto (case-insensitive) |

**Regras de Negócio**
- Cliente deve existir e possuir asaasId (sincronizado com Asaas)
- Plano deve existir e estar ativo (active=true)
- Preço efetivo é calculado via planService.getEffectivePrice(plan, cycle)
- Cupom (se informado):
  - Validação: código existe, está ativo, cliente pode usar
  - Desconto é calculado conforme tipo (FIRST_CHARGE ou RECURRENCE)
  - Uso é registrado após assinatura ser salva
- Assinatura é criada no Asaas primeiro (via AsaasGatewayService)
- Assinaturas passadas são sincronizadas (charges que Asaas retorna)
- Evento de auditoria registrado (action: SUBSCRIPTION_CREATE, entity: Subscription)
- Evento de integração publicado (SubscriptionCreatedEvent)
- Status inicial: ACTIVE
- Escopo: X-Company-Id

**JSON de Request de Exemplo**
```json
{
  "customerId": 1,
  "planId": 2,
  "billingType": "CREDIT_CARD",
  "cycle": "MONTHLY",
  "nextDueDate": "2026-05-01",
  "description": "Assinatura Pro do João",
  "externalReference": "EXT-12345",
  "creditCard": {
    "holderName": "JOAO DA SILVA",
    "number": "4532015112830366",
    "expiryMonth": "12",
    "expiryYear": "2026",
    "ccv": "123"
  },
  "creditCardHolderInfo": {
    "name": "João da Silva",
    "email": "joao@example.com",
    "cpfCnpj": "12345678901",
    "postalCode": "01234567",
    "addressNumber": "123",
    "phone": "11987654321"
  },
  "remoteIp": "192.168.1.1",
  "couponCode": "PROMOCAO2026"
}
```

**DTO de Response - SubscriptionResponse**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | Long | ID único da assinatura |
| `companyId` | Long | ID da empresa |
| `customerId` | Long | ID do cliente |
| `planId` | Long | ID do plano |
| `planName` | String | Nome do plano (para referência) |
| `asaasId` | String | ID da assinatura no Asaas |
| `billingType` | Enum | Tipo de cobrança |
| `effectivePrice` | BigDecimal | Preço efetivo (após cupom e promoções) |
| `cycle` | String | Ciclo (MONTHLY/SEMIANNUALLY/YEARLY) |
| `currentPeriodStart` | LocalDateTime | Início do período atual |
| `currentPeriodEnd` | LocalDateTime | Fim do período atual (null se não calculado) |
| `nextDueDate` | LocalDate | Próxima data de cobrança |
| `status` | Enum | Status: ACTIVE, PAUSED, SUSPENDED, CANCELED, EXPIRED |
| `couponCode` | String | Código do cupom (se aplicável) |
| `couponDiscountAmount` | BigDecimal | Valor do desconto do cupom |
| `couponUsesRemaining` | Integer | Usos restantes do cupom (null=permanente) |
| `createdAt` | LocalDateTime | Data/hora de criação |
| `updatedAt` | LocalDateTime | Data/hora da última atualização |

**JSON de Response de Exemplo**
```json
{
  "id": 1,
  "companyId": 1,
  "customerId": 1,
  "planId": 2,
  "planName": "Plano Pro",
  "asaasId": "sub_123456",
  "billingType": "CREDIT_CARD",
  "effectivePrice": 99.90,
  "cycle": "MONTHLY",
  "currentPeriodStart": "2026-04-12T00:00:00",
  "currentPeriodEnd": "2026-05-12T23:59:59",
  "nextDueDate": "2026-05-01",
  "status": "ACTIVE",
  "couponCode": "PROMOCAO2026",
  "couponDiscountAmount": "20.00",
  "couponUsesRemaining": 1,
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T10:30:00"
}
```

**Códigos HTTP Possíveis**
- `201 Created`: Assinatura criada com sucesso
- `400 Bad Request`: Validações falharam (cliente inativo, plano inativo, cupom inválido, etc)
- `401 Unauthorized`: JWT inválido ou ausente
- `500 Internal Server Error`: Falha na sincronização com Asaas

**Observações/Edge Cases**
- nextDueDate padrão é amanhã (LocalDate.now().plusDays(1)) se não informado
- Cupom com tipo FIRST_CHARGE: couponUsesRemaining=1
- Cupom com recurrenceMonths: couponUsesRemaining=N
- Cupom sem limite: couponUsesRemaining=null (desconto permanente)
- Charges são sincronizadas após criação de assinatura (fetch de Asaas)

---

### GET /api/v1/subscriptions

**Método HTTP e Path Completo**
```
GET /api/v1/subscriptions
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**Query Parameters**
- `status` (Enum, opcional): Filtrar por status (ACTIVE, PAUSED, SUSPENDED, CANCELED, EXPIRED)
- `customerId` (Long, opcional): Filtrar por cliente
- `page` (Int, padrão 0): Número da página
- `size` (Int, padrão 20): Quantidade por página (padrão @PageableDefault(size=20))
- `sort` (String): Campo e direção

**Regras de Negócio**
- Filtros são opcionais e combinados com AND
- Isolado por X-Company-Id
- Retorna apenas assinaturas não deletadas

**JSON de Request de Exemplo**
```
GET /api/v1/subscriptions?status=ACTIVE&customerId=1&page=0&size=20
```

**JSON de Response de Exemplo**
```json
{
  "content": [
    {
      "id": 1,
      "companyId": 1,
      "customerId": 1,
      "planId": 2,
      "planName": "Plano Pro",
      "asaasId": "sub_123456",
      "billingType": "CREDIT_CARD",
      "effectivePrice": 99.90,
      "cycle": "MONTHLY",
      "currentPeriodStart": "2026-04-12T00:00:00",
      "currentPeriodEnd": "2026-05-12T23:59:59",
      "nextDueDate": "2026-05-01",
      "status": "ACTIVE",
      "couponCode": "PROMOCAO2026",
      "couponDiscountAmount": "20.00",
      "couponUsesRemaining": 1,
      "createdAt": "2026-04-12T10:30:00",
      "updatedAt": "2026-04-12T10:30:00"
    }
  ],
  "pageable": {"pageNumber": 0, "pageSize": 20},
  "totalElements": 1,
  "totalPages": 1
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Lista retornada
- `401 Unauthorized`: JWT inválido ou ausente

---

### GET /api/v1/subscriptions/{id}

**Método HTTP e Path Completo**
```
GET /api/v1/subscriptions/{id}
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**DTO de Response**
- SubscriptionResponse (vide POST /subscriptions)

**Regras de Negócio**
- Retorna erro 404 se assinatura não existe

**JSON de Response de Exemplo**
```json
{
  "id": 1,
  "companyId": 1,
  "customerId": 1,
  "planId": 2,
  "planName": "Plano Pro",
  "asaasId": "sub_123456",
  "billingType": "CREDIT_CARD",
  "effectivePrice": 99.90,
  "cycle": "MONTHLY",
  "currentPeriodStart": "2026-04-12T00:00:00",
  "currentPeriodEnd": "2026-05-12T23:59:59",
  "nextDueDate": "2026-05-01",
  "status": "ACTIVE",
  "couponCode": "PROMOCAO2026",
  "couponDiscountAmount": "20.00",
  "couponUsesRemaining": 1,
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T10:30:00"
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Assinatura encontrada
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Assinatura não existe

---

### GET /api/v1/subscriptions/{id}/charges

**Método HTTP e Path Completo**
```
GET /api/v1/subscriptions/{id}/charges
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**Query Parameters**
- `page` (Int, padrão 0): Número da página
- `size` (Int, padrão 20): Quantidade por página (padrão @PageableDefault(size=20))
- `sort` (String): Campo e direção

**Regras de Negócio**
- Retorna todas as cobrancas (charges) vinculadas a assinatura
- Isolado por subscriptionId
- Valida existência da assinatura antes de retornar

**DTO de Response**
- Page<ChargeResponse>

ChargeResponse:
| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | Long | ID da cobrança |
| `subscriptionId` | Long | ID da assinatura |
| `asaasId` | String | ID no Asaas |
| `billingType` | Enum | Tipo (PIX, CREDIT_CARD, etc) |
| `value` | BigDecimal | Valor |
| `dueDate` | LocalDate | Data de vencimento |
| `status` | Enum | Status (PENDING, CONFIRMED, RECEIVED, OVERDUE, REFUNDED, CHARGEBACK) |
| `origin` | Enum | Origem (RECURRING, MANUAL, ADJUSTMENT) |
| `externalReference` | String | Referência externa |
| `invoiceUrl` | String | URL da nota fiscal |
| `boletoUrl` | String | URL do boleto |
| `createdAt` | LocalDateTime | Data criação |
| `updatedAt` | LocalDateTime | Data atualização |

**JSON de Response de Exemplo**
```json
{
  "content": [
    {
      "id": 1,
      "subscriptionId": 1,
      "asaasId": "pay_123456",
      "billingType": "CREDIT_CARD",
      "value": 99.90,
      "dueDate": "2026-05-01",
      "status": "CONFIRMED",
      "origin": "RECURRING",
      "externalReference": "EXT-12345-001",
      "invoiceUrl": "https://asaas.com/invoice/xyz",
      "boletoUrl": null,
      "createdAt": "2026-04-12T10:30:00",
      "updatedAt": "2026-04-12T10:30:00"
    }
  ],
  "pageable": {"pageNumber": 0, "pageSize": 20},
  "totalElements": 1,
  "totalPages": 1
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Lista de charges retornada
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Assinatura não existe

---

### PUT /api/v1/subscriptions/{id}

**Método HTTP e Path Completo**
```
PUT /api/v1/subscriptions/{id}
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `Content-Type: application/json`
- `X-Company-Id: <company_id>` (obrigatório)

**DTO de Request - UpdateSubscriptionRequest**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `billingType` | Enum | Não | Novo tipo de cobrança |
| `nextDueDate` | LocalDate | Não | Próxima data de cobrança |
| `description` | String | Não | Descrição customizada |
| `externalReference` | String | Não | Referência externa |

**Regras de Negócio**
- Atualização parcial
- Não pode atualizar assinatura CANCELED
- Auditada (action: SUBSCRIPTION_UPDATE, entity: Subscription)

**JSON de Request de Exemplo**
```json
{
  "nextDueDate": "2026-05-15",
  "billingType": "PIX"
}
```

**DTO de Response**
- SubscriptionResponse (vide POST /subscriptions)

**Códigos HTTP Possíveis**
- `200 OK`: Assinatura atualizada
- `400 Bad Request`: Assinatura cancelada ou dados inválidos
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Assinatura não existe

---

### PATCH /api/v1/subscriptions/{id}/payment-method

**Método HTTP e Path Completo**
```
PATCH /api/v1/subscriptions/{id}/payment-method
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `Content-Type: application/json`
- `X-Company-Id: <company_id>` (obrigatório)

**DTO de Request - UpdatePaymentMethodRequest**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `billingType` | Enum | Sim | Novo tipo de cobrança (PIX, CREDIT_CARD, DEBIT_CARD, BOLETO, UNDEFINED) |
| `creditCard` | Object | Não | Novos dados do cartão (se billingType=CREDIT_CARD) |
| `creditCard.holderName` | String | Não | Nome do titular |
| `creditCard.number` | String | Não | Número do cartão |
| `creditCard.expiryMonth` | String | Não | Mês de expiração |
| `creditCard.expiryYear` | String | Não | Ano de expiração |
| `creditCard.ccv` | String | Não | CVV |
| `creditCardHolderInfo` | Object | Não | Dados adicionais do titular |
| `creditCardHolderInfo.name` | String | Não | Nome completo |
| `creditCardHolderInfo.email` | String | Não | Email |
| `creditCardHolderInfo.cpfCnpj` | String | Não | CPF/CNPJ |
| `creditCardHolderInfo.postalCode` | String | Não | CEP |
| `creditCardHolderInfo.addressNumber` | String | Não | Número |
| `creditCardHolderInfo.phone` | String | Não | Telefone |
| `creditCardToken` | String | Não | Token do cartão (tokenização) |
| `remoteIp` | String | Não | IP remoto |

**Regras de Negócio**
- Não pode atualizar assinatura CANCELED
- billingType é obrigatório
- Auditada (action: SUBSCRIPTION_UPDATE_PAYMENT_METHOD, entity: Subscription)

**JSON de Request de Exemplo**
```json
{
  "billingType": "CREDIT_CARD",
  "creditCard": {
    "holderName": "JOAO DA SILVA",
    "number": "4532015112830366",
    "expiryMonth": "12",
    "expiryYear": "2027",
    "ccv": "456"
  },
  "creditCardHolderInfo": {
    "name": "João da Silva",
    "email": "joao.novo@example.com",
    "cpfCnpj": "12345678901",
    "postalCode": "01234567",
    "addressNumber": "456",
    "phone": "11988888888"
  }
}
```

**DTO de Response**
- SubscriptionResponse (vide POST /subscriptions)

**Códigos HTTP Possíveis**
- `200 OK`: Método de pagamento atualizado
- `400 Bad Request`: Assinatura cancelada ou dados inválidos
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Assinatura não existe

---

### DELETE /api/v1/subscriptions/{id}

**Método HTTP e Path Completo**
```
DELETE /api/v1/subscriptions/{id}
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido
- Query Parameter: `confirmCouponRemoval` (Boolean, padrão false)

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**Query Parameters**
- `confirmCouponRemoval` (Boolean, padrão false): Confirmação de remoção de cupom

**Regras de Negócio**
- Cancela assinatura (status -> CANCELED)
- Se assinatura possui cupom aplicado, requer confirmCouponRemoval=true
- Cupom é removido permanentemente da assinatura ao cancelar
- Cancela também no Asaas (se asaasId existir)
- Estado final é CANCELED (não pode fazer mais transições)
- Auditada (action: SUBSCRIPTION_CANCEL, entity: Subscription)
- Evento publicado (SubscriptionCanceledEvent)

**JSON de Request de Exemplo**
```
DELETE /api/v1/subscriptions/1?confirmCouponRemoval=true
```

**DTO de Response**
- SubscriptionResponse (vide POST /subscriptions) com status=CANCELED

**JSON de Response de Exemplo**
```json
{
  "id": 1,
  "companyId": 1,
  "customerId": 1,
  "planId": 2,
  "planName": "Plano Pro",
  "asaasId": "sub_123456",
  "billingType": "CREDIT_CARD",
  "effectivePrice": 99.90,
  "cycle": "MONTHLY",
  "currentPeriodStart": "2026-04-12T00:00:00",
  "currentPeriodEnd": "2026-05-12T23:59:59",
  "nextDueDate": "2026-05-01",
  "status": "CANCELED",
  "couponCode": null,
  "couponDiscountAmount": null,
  "couponUsesRemaining": null,
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T11:30:00"
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Assinatura cancelada
- `400 Bad Request`: Assinatura possui cupom e confirmCouponRemoval=false
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Assinatura não existe

**Observações/Edge Cases**
- Estado CANCELED é terminal (não permite transições posteriores)
- Cupom é removido com reason="CANCELED"
- Cobrancas existentes continuam no histórico
- Cancelamento no Asaas é realizado sincronamente

---

### POST /api/v1/subscriptions/{id}/pause

**Método HTTP e Path Completo**
```
POST /api/v1/subscriptions/{id}/pause
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido
- Query Parameter: `confirmCouponRemoval` (Boolean, padrão false)

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**Query Parameters**
- `confirmCouponRemoval` (Boolean, padrão false): Confirmação de remoção de cupom

**Regras de Negócio**
- Pausa assinatura (status -> PAUSED)
- Se assinatura possui cupom, requer confirmCouponRemoval=true
- Cupom é removido permanentemente ao pausar
- Assinatura pausada pode ser retomada (via POST /resume)
- Não cancela no Asaas (apenas marca como PAUSED localmente)
- Auditada (action: SUBSCRIPTION_PAUSE, entity: Subscription)
- Evento publicado (SubscriptionPausedEvent)

**JSON de Request de Exemplo**
```
POST /api/v1/subscriptions/1/pause?confirmCouponRemoval=true
```

**DTO de Response**
- SubscriptionResponse (vide POST /subscriptions) com status=PAUSED

**JSON de Response de Exemplo**
```json
{
  "id": 1,
  "companyId": 1,
  "customerId": 1,
  "planId": 2,
  "planName": "Plano Pro",
  "asaasId": "sub_123456",
  "billingType": "CREDIT_CARD",
  "effectivePrice": 99.90,
  "cycle": "MONTHLY",
  "currentPeriodStart": "2026-04-12T00:00:00",
  "currentPeriodEnd": "2026-05-12T23:59:59",
  "nextDueDate": "2026-05-01",
  "status": "PAUSED",
  "couponCode": null,
  "couponDiscountAmount": null,
  "couponUsesRemaining": null,
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T11:35:00"
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Assinatura pausada
- `400 Bad Request`: Assinatura possui cupom e confirmCouponRemoval=false
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Assinatura não existe

**Observações/Edge Cases**
- Cupom é removido com reason="PAUSED"
- Pausada -> pode voltar a ACTIVE via resume()
- Transições permitidas a partir de PAUSED: ACTIVE ou CANCELED

---

### POST /api/v1/subscriptions/{id}/resume

**Método HTTP e Path Completo**
```
POST /api/v1/subscriptions/{id}/resume
```

**Autenticação/Autorização**
- Autenticação: JWT Bearer Token obrigatória
- Autorização: Qualquer usuário autenticado
- Path Variable: `id` (Long, obrigatório)
- Header X-Company-Id: Requerido

**Headers Relevantes**
- `Authorization: Bearer <jwt_token>` (obrigatório)
- `X-Company-Id: <company_id>` (obrigatório)

**Regras de Negócio**
- Retoma assinatura pausada (status -> ACTIVE)
- Apenas pode ser chamado a partir de PAUSED
- Reinicia cobranças recorrentes
- Auditada (action: SUBSCRIPTION_RESUME, entity: Subscription)
- Evento publicado (SubscriptionResumedEvent)

**DTO de Response**
- SubscriptionResponse (vide POST /subscriptions) com status=ACTIVE

**JSON de Response de Exemplo**
```json
{
  "id": 1,
  "companyId": 1,
  "customerId": 1,
  "planId": 2,
  "planName": "Plano Pro",
  "asaasId": "sub_123456",
  "billingType": "CREDIT_CARD",
  "effectivePrice": 99.90,
  "cycle": "MONTHLY",
  "currentPeriodStart": "2026-04-12T00:00:00",
  "currentPeriodEnd": "2026-05-12T23:59:59",
  "nextDueDate": "2026-05-01",
  "status": "ACTIVE",
  "couponCode": null,
  "couponDiscountAmount": null,
  "couponUsesRemaining": null,
  "createdAt": "2026-04-12T10:30:00",
  "updatedAt": "2026-04-12T11:40:00"
}
```

**Códigos HTTP Possíveis**
- `200 OK`: Assinatura retomada
- `400 Bad Request`: Assinatura não está pausada
- `401 Unauthorized`: JWT inválido ou ausente
- `404 Not Found`: Assinatura não existe

**Observações/Edge Cases**
- Resume a partir de PAUSED volta a ACTIVE
- Assinatura recupera seu nextDueDate anterior
- Cupom não é restaurado (foi removido ao pausar)
- Próxima cobrança segue conforme configurado

---

---

Perfeito! Agora tenho o suficiente para gerar a documentação. Vou compilar todos os dados:

## WebhookController

### POST /api/v1/webhooks/asaas

**Método HTTP e Path**
```
POST /api/v1/webhooks/asaas?companyId={companyId}
```

**Autenticação/Autorização**
- **Permissão**: `permitAll` (público, sem autenticação)
- **Validação**: Valida token de acesso via header `asaas-access-token` contra o token configurado da empresa
- **Nota**: Se nenhum token estiver configurado na empresa, o endpoint aceita qualquer valor
- **Requisito de header**: `X-Company-Id` não é obrigatório (passado como query param)

**Headers Relevantes**
- `asaas-access-token` (opcional): Token de autenticação do webhook Asaas. Validado contra `Company.webhookToken`
- `Content-Type`: `application/json` (obrigatório para o payload)

**DTO de Request**
```
Raw JSON String (payload bruto do Asaas)

Campo: payload
- Tipo: String (JSON não-estruturado)
- Obrigatoriedade: Obrigatório
- Validação: Deve ser JSON válido; é feita extração de `id` e `event`
- Exemplo: {"id":"evt_123","event":"PAYMENT_RECEIVED","payment":{...},"subscription":{...}}

Estrutura esperada (desserializada internamente em AsaasWebhookPayload):
- id: String (obrigatório) - ID único do evento no Asaas
- event: String (obrigatório) - Tipo de evento (ex: PAYMENT_RECEIVED, PAYMENT_FAILED, SUBSCRIPTION_CREATED)
- payment?: Payment
  - id: String - ID do pagamento
  - customer: String - ID do cliente
  - subscription: String - ID da assinatura associada
  - installment: String - ID do parcelamento
  - billingType: String - Tipo de cobrança (PIX, CREDIT_CARD, DEBIT_CARD, BOLETO)
  - value: BigDecimal - Valor da cobrança
  - dueDate: String (ISO date) - Data de vencimento
  - status: String - Status do pagamento (PENDING, CONFIRMED, RECEIVED, OVERDUE, REFUNDED, etc.)
  - externalReference: String - Referência externa
  - invoiceUrl: String - URL do recibo
  - bankSlipUrl: String - URL do boleto
  - description: String - Descrição
- subscription?: SubscriptionData
  - id: String - ID da assinatura
  - customer: String - ID do cliente
  - billingType: String - Tipo de cobrança
  - value: BigDecimal - Valor recorrente
  - nextDueDate: String (ISO date) - Próxima data de cobrança
  - cycle: String - Ciclo de cobrança (MONTHLY, SEMIANNUALLY, YEARLY)
  - status: String - Status (ACTIVE, INACTIVE, EXPIRED)
  - deleted: Boolean - Indica se foi deletado
```

**Regras de Negócio**
1. **Ingresso de Webhook**:
   - Payload é persisted como string bruto na tabela `webhook_event`
   - É extraído o `id` e `event` do JSON para indexação
   - Evento recebe status inicial `PENDING`
   - Um constraint de unicidade (`uq_webhook_event`) evita duplicatas por `(asaasEventId, companyId)`

2. **Deduplicação**:
   - Se um webhook com o mesmo `asaasEventId` chegar novamente, é ignorado silenciosamente (log INFO)
   - Retorna 200 OK mesmo para duplicatas

3. **Processamento Assíncrono**:
   - O endpoint retorna 200 OK imediatamente, sem processar o evento
   - Um job em background (webhook processor) processa eventos com status `PENDING`
   - Tenta processar N vezes (máximo configurável via `app.webhook.max-attempts`, padrão: 10)

4. **Retry Schedule**:
   - Falhas são registradas com `nextAttemptAt` calculado com backoff exponencial
   - Evento transita entre `PENDING` → `PROCESSING` → `DEFERRED` (se aguardando recursos) → `PROCESSED`
   - Se falhar X vezes, vai para `FAILED`; após N mais tentativas, vai para `DLQ` (Dead Letter Queue)

5. **Aceleração de Deferred**:
   - Se um webhook estava `DEFERRED` aguardando criação de uma cobrança e a cobrança é criada, o evento é acelerado para `READY` para ser reprocessado

6. **Validação de Token**:
   - Se `company.webhookToken` está configurado, valida igualdade exata com o header `asaas-access-token`
   - Token inválido retorna HTTP 400 com erro "Token de webhook invalido"

**JSON de Request Exemplo**
```json
POST /api/v1/webhooks/asaas?companyId=1 HTTP/1.1
Content-Type: application/json
asaas-access-token: secret_token_xyz

{
  "id": "evt_650f80da5cd46f001adc2e72",
  "event": "PAYMENT_RECEIVED",
  "payment": {
    "id": "pay_123456",
    "customer": "cust_789",
    "subscription": "sub_456",
    "installment": null,
    "billingType": "PIX",
    "value": 100.50,
    "dueDate": "2025-04-15",
    "status": "RECEIVED",
    "externalReference": "INV-001",
    "invoiceUrl": "https://asaas.com/invoice/...",
    "bankSlipUrl": null,
    "description": "Fatura mensal"
  },
  "subscription": null
}
```

**DTO de Response**
```
Void (corpo vazio, apenas status HTTP)
```

**HTTP Response Exemplo**
```
HTTP/1.1 200 OK
Content-Length: 0
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | Evento recebido e persistido com sucesso; também retorna 200 para duplicatas |
| **400 Bad Request** | Token de webhook inválido, JSON malformado, `companyId` inválido, ou empresa não encontrada |
| **401 Unauthorized** | Não aplicável (endpoint é público) |
| **500 Internal Server Error** | Erro ao persistir no banco (raro, pois duplicatas são tratadas) |

**Observações/Edge Cases**
1. **Replay/Replay Attack**:
   - Não há proteção contra replay por design; a deduplicação por `asaasEventId` previne processamento duplicado
   - Se o mesmo evento chegar twice, só o primeiro é processado, o segundo é ignorado

2. **Assinatura HMAC**:
   - O endpoint NÃO valida assinatura criptográfica (HMAC-SHA256)
   - Confia apenas no token em `asaas-access-token` header
   - Se implementação de HMAC for desejada, seria necessário validar assinatura antes de aceitar

3. **Idempotência**:
   - Totalmente idempotente: múltiplas chamadas com mesmo payload resultam no mesmo estado
   - Deduplicação via constraint DB garante isso

4. **Payload Dinâmico**:
   - Alguns campos podem estar nulls (subscription pode estar vazio se for só payment, etc.)
   - Uso de `@JsonIgnoreProperties(ignoreUnknown = true)` permite evolução do schema Asaas

5. **Escalabilidade**:
   - Endpoint é rápido (insert + return 200) e não bloqueia em I/O externo
   - Processamento real ocorre em background job

---

## WebhookAdminController

### GET /api/v1/admin/webhooks

**Método HTTP e Path**
```
GET /api/v1/admin/webhooks?status={status}&page={page}&size={size}
```

**Autenticação/Autorização**
- **Roles Requeridas**: `HOLDING_ADMIN` ou `SYSTEM`
- **Header X-Company-Id**: Não obrigatório, mas se presente, pode ser considerado pelo filtro de contexto de tenant
- **Nota**: Endpoint retorna webhooks de todas as empresas (cross-tenant)

**Headers Relevantes**
- `Authorization`: Bearer token JWT (obrigatório)
- `X-Company-Id`: Optional, para rate limiting
- `Accept`: `application/json`

**DTO de Request**
```
Query Parameters:

status: WebhookEventStatus
  - Tipo: Enum
  - Obrigatoriedade: Obrigatório
  - Valores válidos: PENDING, PROCESSING, DEFERRED, PROCESSED, FAILED, DLQ
  - Descrição: Filtra eventos pelo status

page: Integer
  - Tipo: Integer
  - Obrigatoriedade: Opcional (padrão: 0)
  - Validação: >= 0
  - Descrição: Número da página (zero-indexed)

size: Integer
  - Tipo: Integer
  - Obrigatoriedade: Opcional (padrão: 20)
  - Validação: > 0
  - Descrição: Quantidade de registros por página
```

**Regras de Negócio**
1. **Paginação**:
   - Resultado é paginado com tamanho padrão de 20 registros
   - Total de registros é retornado no objeto `Page`

2. **Filtragem por Status**:
   - Retorna apenas eventos com o status exato especificado
   - Não retorna eventos de outros status

3. **Cross-Tenant**:
   - Retorna webhooks de TODAS as empresas (não limitado a uma tenant específica)
   - Útil para administradores monitorarem toda a plataforma

**JSON de Request Exemplo**
```
GET /api/v1/admin/webhooks?status=FAILED&page=0&size=20 HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

**DTO de Response**
```
Page<WebhookEventResponse>
{
  "content": [WebhookEventResponse, ...],
  "pageable": {...},
  "totalPages": 5,
  "totalElements": 92,
  "last": false,
  "size": 20,
  "number": 0,
  "numberOfElements": 20,
  "first": true,
  "empty": false
}

WebhookEventResponse:
- id: Long - ID do evento no banco
- companyId: Long - ID da empresa
- asaasEventId: String - ID único do evento no Asaas
- eventType: String - Tipo do evento (ex: PAYMENT_RECEIVED)
- status: WebhookEventStatus - Status atual (PENDING, PROCESSING, DEFERRED, PROCESSED, FAILED, DLQ)
- attemptCount: Integer - Número de tentativas de processamento
- nextAttemptAt: LocalDateTime - Próxima data de tentativa (null se processado ou não agendado)
- processedAt: LocalDateTime - Quando foi finalizado o processamento (null se ainda pendente)
- lastError: String - Mensagem do último erro (null se sem erro)
- receivedAt: LocalDateTime - Quando o webhook foi recebido (criado)
```

**JSON de Response Exemplo**
```json
{
  "content": [
    {
      "id": 1,
      "companyId": 5,
      "asaasEventId": "evt_650f80da5cd46f001adc2e72",
      "eventType": "PAYMENT_RECEIVED",
      "status": "FAILED",
      "attemptCount": 3,
      "nextAttemptAt": "2025-04-13T14:30:00",
      "processedAt": null,
      "lastError": "Customer not found in local database",
      "receivedAt": "2025-04-13T14:15:00"
    },
    {
      "id": 2,
      "companyId": 7,
      "asaasEventId": "evt_650f80da5cd46f001adc2e73",
      "eventType": "SUBSCRIPTION_CANCELED",
      "status": "FAILED",
      "attemptCount": 5,
      "nextAttemptAt": "2025-04-14T02:00:00",
      "processedAt": null,
      "lastError": "Invalid subscription status transition",
      "receivedAt": "2025-04-13T10:22:00"
    }
  ],
  "pageable": {
    "sort": { "empty": true, "sorted": false, "unsorted": true },
    "offset": 0,
    "pageSize": 20,
    "pageNumber": 0,
    "paged": true,
    "unpaged": false
  },
  "totalPages": 5,
  "totalElements": 92,
  "last": false,
  "size": 20,
  "number": 0,
  "numberOfElements": 2,
  "first": true,
  "empty": false
}
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | Listagem retornada com sucesso |
| **400 Bad Request** | Status inválido, page/size inválido |
| **401 Unauthorized** | Token JWT expirado ou inválido |
| **403 Forbidden** | Usuário não tem role HOLDING_ADMIN ou SYSTEM |
| **500 Internal Server Error** | Erro no banco de dados |

**Observações/Edge Cases**
1. **Status Inválido**:
   - Se o status não for um enum válido, Spring retorna 400 com mensagem de erro

2. **Página Além do Limite**:
   - Se solicitar página 100 mas só existem 5 páginas, retorna 200 OK com `content` vazio

3. **Performance em Cross-Tenant**:
   - Grande volume de webhooks pode tornar paginação lenta
   - Recomenda-se buscar status mais críticos primeiro (FAILED, DLQ)

---

### GET /api/v1/admin/webhooks/summary

**Método HTTP e Path**
```
GET /api/v1/admin/webhooks/summary
```

**Autenticação/Autorização**
- **Roles Requeridas**: `HOLDING_ADMIN` ou `SYSTEM`
- **Header X-Company-Id**: Opcional
- **Nota**: Retorna resumo de TODAS as empresas (cross-tenant)

**Headers Relevantes**
- `Authorization`: Bearer token JWT (obrigatório)
- `Accept`: `application/json`

**DTO de Request**
```
Sem parâmetros
```

**Regras de Negócio**
1. **Agregação de Dados**:
   - Calcula contagem de eventos por status em uma única query eficiente
   - Tota = pending + processing + deferred + processed + failed + dlq

2. **Cross-Tenant**:
   - Suma todos os webhooks de todas as empresas

**JSON de Request Exemplo**
```
GET /api/v1/admin/webhooks/summary HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

**DTO de Response**
```
WebhookSummaryResponse:
- pending: Long - Quantidade de eventos aguardando processamento
- processing: Long - Quantidade em processamento ativo
- deferred: Long - Quantidade adiados (aguardando recurso)
- processed: Long - Quantidade processados com sucesso
- failed: Long - Quantidade com falha (pode ser retentada)
- dlq: Long - Quantidade em Dead Letter Queue (falha permanente)
- total: Long - Total geral (soma de todos)
```

**JSON de Response Exemplo**
```json
{
  "pending": 15,
  "processing": 2,
  "deferred": 8,
  "processed": 1240,
  "failed": 12,
  "dlq": 3,
  "total": 1280
}
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | Resumo retornado com sucesso |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem role apropriada |
| **500 Internal Server Error** | Erro ao contar eventos |

**Observações/Edge Cases**
1. **Uso para Monitoramento**:
   - Campo `dlq` deve ser monitorado; qualquer valor > 0 indica problema
   - `failed` com valor alto indica retry loop ativo

2. **Alertas**:
   - Recomenda-se alerta se `dlq > 5` ou `failed > 50`

---

### POST /api/v1/admin/webhooks/{eventId}/replay

**Método HTTP e Path**
```
POST /api/v1/admin/webhooks/{eventId}/replay
```

**Autenticação/Autorização**
- **Roles Requeridas**: `HOLDING_ADMIN` ou `SYSTEM`
- **Header X-Company-Id**: Opcional

**Headers Relevantes**
- `Authorization`: Bearer token JWT (obrigatório)
- `Content-Type`: `application/json`

**DTO de Request**
```
Path Parameter:

eventId: Long
  - Tipo: Long
  - Obrigatoriedade: Obrigatório
  - Validação: > 0, deve existir no banco
  - Descrição: ID do evento no banco de dados

Body: Vazio
```

**Regras de Negócio**
1. **Validação de Status**:
   - Só permite replay de eventos com status `FAILED` ou `DLQ`
   - Tenta replay de outros status retorna erro 400

2. **Transição de Estado**:
   - Marca evento com `markReadyForRetry()`:
     - Status volta para `PENDING`
     - `attemptCount` é resetado para 0
     - `nextAttemptAt` é calculado para imediato
     - `lastError` é mantido para auditoria

3. **Reprocessamento**:
   - Webhook processor pickará o evento na próxima iteração

**JSON de Request Exemplo**
```
POST /api/v1/admin/webhooks/123/replay HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: application/json
```

**DTO de Response**
```
WebhookEventResponse (mesmo da listagem)
```

**JSON de Response Exemplo**
```json
{
  "id": 123,
  "companyId": 5,
  "asaasEventId": "evt_650f80da5cd46f001adc2e72",
  "eventType": "PAYMENT_RECEIVED",
  "status": "PENDING",
  "attemptCount": 0,
  "nextAttemptAt": "2025-04-13T14:15:05",
  "processedAt": null,
  "lastError": "Customer not found in local database",
  "receivedAt": "2025-04-13T14:15:00"
}
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | Evento marcado para retry com sucesso |
| **400 Bad Request** | Status do evento não é FAILED ou DLQ |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem role apropriada |
| **404 Not Found** | Evento não encontrado |
| **500 Internal Server Error** | Erro ao atualizar evento |

**Observações/Edge Cases**
1. **Replay Múltiplo**:
   - É possível chamar replay múltiplas vezes para o mesmo evento
   - Cada chamada reseta contadores, portanto use com cautela

2. **Causa do Erro Original**:
   - Replay não resolve o problema raiz; o erro `lastError` é mantido
   - Admin deve corrigir a causa (ex: criar cliente no banco) antes de replay

---

## OutboxAdminController

### GET /api/v1/admin/outbox

**Método HTTP e Path**
```
GET /api/v1/admin/outbox?status={status}&page={page}&size={size}
```

**Autenticação/Autorização**
- **Roles Requeridas**: `HOLDING_ADMIN`
- **Header X-Company-Id**: Opcional
- **Nota**: Cross-tenant, retorna outbox de todas as empresas

**Headers Relevantes**
- `Authorization`: Bearer token JWT (obrigatório)
- `Accept`: `application/json`

**DTO de Request**
```
Query Parameters:

status: OutboxStatus
  - Tipo: Enum
  - Obrigatoriedade: Opcional (padrão: PENDING)
  - Valores válidos: PENDING, PUBLISHED, FAILED, DLQ
  - Descrição: Status do evento

page: Integer
  - Tipo: Integer
  - Obrigatoriedade: Opcional (padrão: 0)
  - Descrição: Número da página

size: Integer
  - Tipo: Integer
  - Obrigatoriedade: Opcional
  - Descrição: Registros por página
```

**Regras de Negócio**
1. **Outbox Pattern**:
   - Tabela `outbox` armazena eventos de domínio aguardando publicação em sistemas externos
   - Sistema de relay processa eventos `PENDING` e os publica em destinos (n8n, BI, ERP)

2. **Status do Outbox**:
   - `PENDING`: Evento aguardando publicação
   - `PUBLISHED`: Publicado com sucesso
   - `FAILED`: Falha na publicação, será retentado automaticamente
   - `DLQ`: Falha após N tentativas, requer intervenção manual

3. **Paginação**:
   - Resultado paginado, padrão 20 itens por página

**JSON de Request Exemplo**
```
GET /api/v1/admin/outbox?status=FAILED&page=0&size=20 HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

**DTO de Response**
```
Page<OutboxEventResponse>

OutboxEventResponse:
- id: Long - ID do evento no banco
- companyId: Long - ID da empresa dona do evento
- aggregateType: String - Tipo de agregado (ex: "Customer", "Charge", "Subscription")
- aggregateId: String - ID do agregado
- eventType: String - Tipo de evento de domínio (ex: "ChargeCreated", "SubscriptionCanceled")
- payload: String - JSON serializado do evento (contém dados para publicação)
- status: OutboxStatus - Status atual (PENDING, PUBLISHED, FAILED, DLQ)
- attemptCount: Integer - Número de tentativas de envio
- createdAt: LocalDateTime - Quando o evento foi criado
- publishedAt: LocalDateTime - Quando foi publicado com sucesso (null se não publicado)
- lastError: String - Mensagem do último erro (null se sem erro)
```

**JSON de Response Exemplo**
```json
{
  "content": [
    {
      "id": 1,
      "companyId": 5,
      "aggregateType": "Charge",
      "aggregateId": "charge_123",
      "eventType": "ChargeStatusChanged",
      "payload": "{\"chargeId\":\"charge_123\",\"status\":\"RECEIVED\",\"value\":100.50}",
      "status": "FAILED",
      "attemptCount": 2,
      "createdAt": "2025-04-13T14:15:00",
      "publishedAt": null,
      "lastError": "Connection timeout to n8n webhook"
    }
  ],
  "totalPages": 1,
  "totalElements": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true
}
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | Eventos retornados com sucesso |
| **400 Bad Request** | Status inválido |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem role HOLDING_ADMIN |
| **500 Internal Server Error** | Erro no banco |

**Observações/Edge Cases**
1. **Payload Grande**:
   - Campo `payload` pode conter JSON grande; não é truncado na resposta
   - Performance pode sofrer com muitos eventos e payloads grandes

2. **Lag em PENDING**:
   - Se há muitos eventos `PENDING`, o sistema pode estar congestionado
   - Monitorar lag é importante para alertar sobre problemas

---

### GET /api/v1/admin/outbox/summary

**Método HTTP e Path**
```
GET /api/v1/admin/outbox/summary
```

**Autenticação/Autorização**
- **Roles Requeridas**: `HOLDING_ADMIN`
- **Header X-Company-Id**: Opcional

**Headers Relevantes**
- `Authorization`: Bearer token JWT (obrigatório)
- `Accept`: `application/json`

**DTO de Request**
```
Sem parâmetros
```

**Regras de Negócio**
1. **Agregação**:
   - Calcula contagem por status
   - Calcula lag: tempo entre `createdAt` do evento mais antigo `PENDING` e agora
   - Lag ajuda a identificar congestionamento

**JSON de Request Exemplo**
```
GET /api/v1/admin/outbox/summary HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

**DTO de Response**
```
OutboxSummaryResponse:
- pending: Long - Eventos aguardando publicação
- published: Long - Publicados com sucesso
- failed: Long - Falhas (com retry)
- dlq: Long - Dead Letter Queue
- lagSeconds: Double - Segundos de delay do evento mais antigo PENDING (null se nenhum PENDING)
```

**JSON de Response Exemplo**
```json
{
  "pending": 42,
  "published": 1523,
  "failed": 5,
  "dlq": 2,
  "lagSeconds": 125.5
}
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | Resumo retornado |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem role apropriada |
| **500 Internal Server Error** | Erro no banco |

**Observações/Edge Cases**
1. **Lag Alto**:
   - `lagSeconds > 300` indica que há eventos aguardando > 5 minutos
   - Pode indicar problema no relay ou destino indisponível

2. **DLQ Não Zero**:
   - Qualquer evento em DLQ requer investigação manual

---

### POST /api/v1/admin/outbox/{id}/retry

**Método HTTP e Path**
```
POST /api/v1/admin/outbox/{id}/retry
```

**Autenticação/Autorização**
- **Roles Requeridas**: `HOLDING_ADMIN`
- **Header X-Company-Id**: Opcional

**Headers Relevantes**
- `Authorization`: Bearer token JWT (obrigatório)

**DTO de Request**
```
Path Parameter:

id: Long
  - Tipo: Long
  - Obrigatoriedade: Obrigatório
  - Descrição: ID do evento no banco

Body: Vazio
```

**Regras de Negócio**
1. **Validação**:
   - Só permite retry de status `FAILED` ou `DLQ`
   - Tenta retry de outros status retorna erro

2. **Transição**:
   - Status: `FAILED/DLQ` → `PENDING`
   - `attemptCount` → 0
   - `lastError` → null
   - `publishedAt` → null

3. **Relay Processará**:
   - OutboxRelay processador pegará o evento na próxima iteração

**JSON de Request Exemplo**
```
POST /api/v1/admin/outbox/1/retry HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: application/json
```

**DTO de Response**
```
Void (HTTP 204 No Content)
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **204 No Content** | Evento marcado para retry |
| **400 Bad Request** | Status não é FAILED ou DLQ |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem role apropriada |
| **404 Not Found** | Evento não encontrado |
| **500 Internal Server Error** | Erro ao atualizar |

**Observações/Edge Cases**
1. **Retry Múltiplo**:
   - Chamar retry múltiplas vezes reseta contadores cada vez
   - Cuidado com retry loops

2. **Causa da Falha**:
   - Admin deve investigar `lastError` antes de fazer retry
   - Exemplo: se destino (n8n) estava off, ligar-o antes de retry

---

## ReconciliationController

### POST /api/v1/admin/reconciliation/charges

**Método HTTP e Path**
```
POST /api/v1/admin/reconciliation/charges?daysBack={daysBack}
```

**Autenticação/Autorização**
- **Roles Requeridas**: `HOLDING_ADMIN`
- **Header X-Company-Id**: Obrigatório (usado para extrair `companyId` do TenantContext)
- **Nota**: Reconcilia apenas a empresa do contexto

**Headers Relevantes**
- `Authorization`: Bearer token JWT (obrigatório)
- `X-Company-Id`: Company ID (obrigatório, extrai de TenantContext)
- `Content-Type`: `application/json`

**DTO de Request**
```
Query Parameters:

daysBack: Integer
  - Tipo: Integer
  - Obrigatoriedade: Opcional (padrão: 3)
  - Validação: > 0
  - Descrição: Número de dias no passado para reconciliar
```

**Regras de Negócio**
1. **Reconciliação**:
   - Busca cobranças criadas desde `LocalDate.now().minusDays(daysBack)`
   - Para cada cobrança com `asaasId`, faz fetch do status em Asaas
   - Compara status local com status Asaas

2. **Auto-Fix**:
   - Se status diverge mas é uma transição válida (ex: `PENDING` → `CONFIRMED`), auto-corrige
   - Se transição é inválida (ex: `RECEIVED` → `PENDING`), marca para revisão manual
   - Auto-correções são logged em INFO

3. **Contadores de Divergência**:
   - Incrementa métrica Micrometer `reconciliation_divergences_total` para cada divergência encontrada

4. **Paginação Interna**:
   - Processa em batches de 500 cobranças

**JSON de Request Exemplo**
```
POST /api/v1/admin/reconciliation/charges?daysBack=7 HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
X-Company-Id: 5
Content-Type: application/json
```

**DTO de Response**
```
ReconciliationResult:
- executedAt: LocalDateTime - Quando a reconciliação foi executada
- totalChecked: Integer - Total de cobranças verificadas
- divergencesFound: Integer - Quantidade de divergências encontradas
- autoFixed: Integer - Quantidade auto-corrigidas
- divergences: List<Divergence>
  - entityType: String - "Charge"
  - localId: Long - ID da cobrança local
  - asaasId: String - ID no Asaas
  - localStatus: String - Status local
  - asaasStatus: String - Status em Asaas
  - action: String - "AUTO_FIXED" ou "MANUAL_REVIEW_NEEDED"
```

**JSON de Response Exemplo**
```json
{
  "executedAt": "2025-04-13T14:30:00",
  "totalChecked": 245,
  "divergencesFound": 3,
  "autoFixed": 2,
  "divergences": [
    {
      "entityType": "Charge",
      "localId": 123,
      "asaasId": "pay_456",
      "localStatus": "PENDING",
      "asaasStatus": "CONFIRMED",
      "action": "AUTO_FIXED"
    },
    {
      "entityType": "Charge",
      "localId": 124,
      "asaasId": "pay_457",
      "localStatus": "RECEIVED",
      "asaasStatus": "PENDING",
      "action": "MANUAL_REVIEW_NEEDED"
    }
  ]
}
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | Reconciliação completada |
| **400 Bad Request** | `daysBack` inválido, empresa não encontrada |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem role HOLDING_ADMIN |
| **500 Internal Server Error** | Erro ao chamar Asaas API ou banco |

**Observações/Edge Cases**
1. **Sem Asaas ID**:
   - Cobranças criadas localmente sem link ao Asaas são ignoradas (skip)

2. **Erro Ao Buscar em Asaas**:
   - Se Asaas API falhar para uma cobrança, é registrado como divergência com `action: "FETCH_FAILED: ..."`

3. **Data Passada**:
   - Se especificar `daysBack=0`, reconcilia apenas do hoje (pode ser vazio)

---

### POST /api/v1/admin/reconciliation/subscriptions

**Método HTTP e Path**
```
POST /api/v1/admin/reconciliation/subscriptions
```

**Autenticação/Autorização**
- **Roles Requeridas**: `HOLDING_ADMIN`
- **Header X-Company-Id**: Obrigatório

**Headers Relevantes**
- `Authorization`: Bearer token JWT (obrigatório)
- `X-Company-Id`: Company ID (obrigatório)
- `Content-Type`: `application/json`

**DTO de Request**
```
Body: Vazio
```

**Regras de Negócio**
1. **Reconciliação**:
   - Busca TODAS as assinaturas da empresa (não limitado por data)
   - Para cada com `asaasId`, faz fetch do Asaas
   - Compara status local com Asaas

2. **Auto-Fix**:
   - Transições válidas são auto-corrigidas
   - Transições inválidas marcam para revisão manual

3. **Paginação Interna**:
   - Processa em batches de 500

**JSON de Request Exemplo**
```
POST /api/v1/admin/reconciliation/subscriptions HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
X-Company-Id: 5
Content-Type: application/json
```

**DTO de Response**
```
ReconciliationResult (mesma estrutura de charges)
```

**JSON de Response Exemplo**
```json
{
  "executedAt": "2025-04-13T14:35:00",
  "totalChecked": 87,
  "divergencesFound": 1,
  "autoFixed": 1,
  "divergences": [
    {
      "entityType": "Subscription",
      "localId": 45,
      "asaasId": "sub_123",
      "localStatus": "ACTIVE",
      "asaasStatus": "EXPIRED",
      "action": "AUTO_FIXED"
    }
  ]
}
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | Reconciliação completada |
| **400 Bad Request** | Empresa não encontrada |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem role apropriada |
| **500 Internal Server Error** | Erro no banco ou Asaas |

**Observações/Edge Cases**
1. **Sem Asaas ID**:
   - Assinaturas locais sem link ao Asaas são ignoradas

2. **Volume Grande**:
   - Se muitas assinaturas, pode levar tempo; considerar job de background

---

### POST /api/v1/admin/reconciliation/dlq/replay

**Método HTTP e Path**
```
POST /api/v1/admin/reconciliation/dlq/replay
```

**Autenticação/Autorização**
- **Roles Requeridas**: `HOLDING_ADMIN`
- **Header X-Company-Id**: Não obrigatório (afeta todas as tenants)

**Headers Relevantes**
- `Authorization`: Bearer token JWT (obrigatório)
- `Content-Type`: `application/json`

**DTO de Request**
```
Body: Vazio
```

**Regras de Negócio**
1. **Replay em Massa**:
   - Marca TODOS os eventos em DLQ (webhook + outbox) para reprocessamento
   - Transitions: `DLQ` → `PENDING`
   - Reseta `attemptCount` para 0

2. **Processamento em Batch**:
   - Processa em páginas de 200 eventos
   - Incrementa métrica `reconciliation_dlq_replay_total` para cada evento

3. **Cross-Tenant**:
   - Afeta DLQ de TODAS as empresas

**JSON de Request Exemplo**
```
POST /api/v1/admin/reconciliation/dlq/replay HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
X-Company-Id: 5 (não necessário)
Content-Type: application/json
```

**DTO de Response**
```
DlqReplayResult:
- executedAt: LocalDateTime - Quando foi executado
- webhookEventsReplayed: Long - Quantidade de webhook events reprocessados
- outboxEventsReplayed: Long - Quantidade de outbox events reprocessados
- totalReplayed: Long - Total geral
```

**JSON de Response Exemplo**
```json
{
  "executedAt": "2025-04-13T14:40:00",
  "webhookEventsReplayed": 5,
  "outboxEventsReplayed": 2,
  "totalReplayed": 7
}
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | DLQ replay iniciado |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem role apropriada |
| **500 Internal Server Error** | Erro ao atualizar eventos |

**Observações/Edge Cases**
1. **Cauteloso**:
   - Usar com cautela pois replaya TUDO em DLQ de uma vez
   - Se problema raiz não foi corrigido, eventos falharão novamente

2. **Sem Garantia**:
   - Replay não garante sucesso; só reinicia processamento
   - Monitorar logs para ver quais eventos falham novamente

3. **Idempotência**:
   - Chamar replay múltiplas vezes é idempotente (eventos já PENDING não são afetados)

---

## ReportController

**Nota Importante**: O `ReportController` não possui autenticação explícita em seus endpoints; devem ter `@PreAuthorize` adicionado ou estar protegidos por um filtro global. Por enquanto, documentando conforme código atual sugere que estão autenticados (por requisição do cliente).

### GET /api/v1/reports/revenue

**Método HTTP e Path**
```
GET /api/v1/reports/revenue?from={from}&to={to}&groupBy={groupBy}
```

**Autenticação/Autorização**
- **Roles Requeridas**: Presumível `COMPANY_ADMIN` ou `HOLDING_ADMIN` (não explícito no código)
- **Header X-Company-Id**: Presumível obrigatório para filtrar por empresa
- **Nota**: Sem `@PreAuthorize`, verifica-se via TenantContext

**Headers Relevantes**
- `Authorization`: Bearer token JWT (presumível obrigatório)
- `X-Company-Id`: Company ID (presumível)
- `Accept`: `application/json`

**DTO de Request**
```
Query Parameters:

from: LocalDate
  - Tipo: LocalDate
  - Obrigatoriedade: Obrigatório
  - Formato: ISO date (YYYY-MM-DD)
  - Validação: Deve ser data válida <= today
  - Descrição: Data de início do período

to: LocalDate
  - Tipo: LocalDate
  - Obrigatoriedade: Obrigatório
  - Formato: ISO date (YYYY-MM-DD)
  - Validação: Deve ser >= from
  - Descrição: Data de fim do período

groupBy: String
  - Tipo: String
  - Obrigatoriedade: Opcional (padrão: "origin")
  - Valores válidos: "method", "day", "origin"
  - Descrição: Campo de agrupamento (método de pagamento, dia, origem da cobrança)
```

**Regras de Negócio**
1. **Filtro por Company**:
   - Restringe a dados da empresa do contexto (via TenantContext)

2. **Agrupamento**:
   - `method`: Agrupa por billingType (PIX, CREDIT_CARD, etc.)
   - `day`: Agrupa por data de recebimento (por dia)
   - `origin`: Agrupa por origen da cobrança (API, DASHBOARD, IMPORT, etc.)

3. **Status de Cobrança**:
   - Inclui apenas cobranças com status `RECEIVED`

**JSON de Request Exemplo**
```
GET /api/v1/reports/revenue?from=2025-04-01&to=2025-04-13&groupBy=method HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
X-Company-Id: 5
```

**DTO de Response**
```
List<RevenueReportEntry>

RevenueReportEntry:
- groupKey: String - Nome do grupo (ex: "PIX", "2025-04-05", "API")
- chargeCount: Long - Quantidade de cobranças nesse grupo
- totalValue: BigDecimal - Valor total em 2 casas decimais
```

**JSON de Response Exemplo**
```json
[
  {
    "groupKey": "PIX",
    "chargeCount": 45,
    "totalValue": 2345.67
  },
  {
    "groupKey": "CREDIT_CARD",
    "chargeCount": 23,
    "totalValue": 1234.50
  },
  {
    "groupKey": "BOLETO",
    "chargeCount": 12,
    "totalValue": 567.89
  }
]
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | Relatório gerado |
| **400 Bad Request** | Datas inválidas, formato errado |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem acesso (sem role ou empresa errada) |
| **500 Internal Server Error** | Erro no banco |

**Observações/Edge Cases**
1. **Período Grande**:
   - Períodos muito longos podem retornar muitos registros se agrupado por dia
   - Recomenda-se limitar a períodos razoáveis (< 6 meses)

2. **Dados Vazios**:
   - Se nenhuma cobrança no período, retorna lista vazia `[]`

3. **Precisão Decimal**:
   - `totalValue` é sempre 2 casas decimais

---

### GET /api/v1/reports/subscriptions/mrr

**Método HTTP e Path**
```
GET /api/v1/reports/subscriptions/mrr
```

**Autenticação/Autorização**
- **Roles Requeridas**: Presumível `COMPANY_ADMIN` ou `HOLDING_ADMIN`
- **Header X-Company-Id**: Presumível obrigatório

**Headers Relevantes**
- `Authorization`: Bearer token JWT (presumível)
- `X-Company-Id`: Company ID
- `Accept`: `application/json`

**DTO de Request**
```
Sem parâmetros
```

**Regras de Negócio**
1. **MRR (Monthly Recurring Revenue)**:
   - Soma do `effectivePrice` de todas as assinaturas `ACTIVE` da empresa
   - Representa receita recorrente mensal esperada

2. **ARR (Annual Recurring Revenue)**:
   - MRR × 12

3. **Contagem**:
   - Conta apenas assinaturas com status `ACTIVE`

**JSON de Request Exemplo**
```
GET /api/v1/reports/subscriptions/mrr HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
X-Company-Id: 5
```

**DTO de Response**
```
MrrReport:
- calculatedAt: LocalDateTime - Quando foi calculado
- activeSubscriptions: Long - Quantidade de assinaturas ativas
- mrr: BigDecimal - Valor em 2 casas decimais
- arr: BigDecimal - Valor em 2 casas decimais
```

**JSON de Response Exemplo**
```json
{
  "calculatedAt": "2025-04-13T14:45:00",
  "activeSubscriptions": 150,
  "mrr": "12500.50",
  "arr": "150006.00"
}
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | MRR/ARR calculados |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem acesso |
| **500 Internal Server Error** | Erro no banco |

**Observações/Edge Cases**
1. **Sem Assinaturas Ativas**:
   - Se nenhuma assinatura ativa, retorna MRR = 0, ARR = 0

2. **Assinaturas Sem Preço**:
   - Assinaturas com `effectivePrice` nulo são tratadas como 0

---

### GET /api/v1/reports/subscriptions/churn

**Método HTTP e Path**
```
GET /api/v1/reports/subscriptions/churn?from={from}&to={to}
```

**Autenticação/Autorização**
- **Roles Requeridas**: Presumível `COMPANY_ADMIN` ou `HOLDING_ADMIN`
- **Header X-Company-Id**: Presumível obrigatório

**Headers Relevantes**
- `Authorization`: Bearer token JWT (presumível)
- `X-Company-Id`: Company ID
- `Accept`: `application/json`

**DTO de Request**
```
Query Parameters:

from: LocalDate
  - Tipo: LocalDate
  - Obrigatoriedade: Obrigatório
  - Formato: ISO date (YYYY-MM-DD)

to: LocalDate
  - Tipo: LocalDate
  - Obrigatoriedade: Obrigatório
  - Formato: ISO date (YYYY-MM-DD)
  - Validação: >= from
```

**Regras de Negócio**
1. **Churn Rate**:
   - Percentual de assinaturas canceladas no período em relação às ativas no início
   - Fórmula: `(canceledInPeriod / activeAtStart) × 100`

2. **Cálculos**:
   - `canceledInPeriod`: Assinaturas com status `EXPIRED` que foram transicionadas durante `[from, to]`
   - `activeAtStart`: Assinaturas com status `ACTIVE` em `from`

**JSON de Request Exemplo**
```
GET /api/v1/reports/subscriptions/churn?from=2025-04-01&to=2025-04-13 HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
X-Company-Id: 5
```

**DTO de Response**
```
ChurnReport:
- calculatedAt: LocalDateTime - Quando foi calculado
- periodStart: LocalDate - Data de início
- periodEnd: LocalDate - Data de fim
- canceledInPeriod: Long - Quantidade cancelada
- activeAtStart: Long - Quantidade ativa no início
- churnRate: BigDecimal - Percentual (2 casas decimais, ex: 5.50%)
```

**JSON de Response Exemplo**
```json
{
  "calculatedAt": "2025-04-13T14:50:00",
  "periodStart": "2025-04-01",
  "periodEnd": "2025-04-13",
  "canceledInPeriod": 8,
  "activeAtStart": 150,
  "churnRate": "5.33"
}
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | Churn calculado |
| **400 Bad Request** | Datas inválidas |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem acesso |
| **500 Internal Server Error** | Erro no banco |

**Observações/Edge Cases**
1. **Zero Ativas no Início**:
   - Se `activeAtStart = 0`, `churnRate = 0` (evita divisão por zero)

2. **Período Futuro**:
   - Se datas são futuras, retorna churn 0

---

### GET /api/v1/reports/overdue

**Método HTTP e Path**
```
GET /api/v1/reports/overdue
```

**Autenticação/Autorização**
- **Roles Requeridas**: Presumível `COMPANY_ADMIN` ou `HOLDING_ADMIN`
- **Header X-Company-Id**: Presumível obrigatório

**Headers Relevantes**
- `Authorization`: Bearer token JWT (presumível)
- `X-Company-Id`: Company ID
- `Accept`: `application/json`

**DTO de Request**
```
Sem parâmetros
```

**Regras de Negócio**
1. **Filtro**:
   - Retorna clientes com cobranças atrasadas (status `OVERDUE`)
   - Agrupado por cliente

2. **Agregação**:
   - Soma quantidade e valor total de cobranças atrasadas por cliente

**JSON de Request Exemplo**
```
GET /api/v1/reports/overdue HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
X-Company-Id: 5
```

**DTO de Response**
```
List<OverdueReportEntry>

OverdueReportEntry:
- customerId: Long - ID do cliente
- customerName: String - Nome do cliente
- overdueCount: Long - Quantidade de cobranças atrasadas
- totalOverdueValue: BigDecimal - Valor total em atraso
```

**JSON de Response Exemplo**
```json
[
  {
    "customerId": 101,
    "customerName": "Empresa ABC Ltda",
    "overdueCount": 3,
    "totalOverdueValue": 5432.10
  },
  {
    "customerId": 105,
    "customerName": "PJ XYZ Serviços",
    "overdueCount": 1,
    "totalOverdueValue": 1200.00
  }
]
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | Relatório gerado |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem acesso |
| **500 Internal Server Error** | Erro no banco |

**Observações/Edge Cases**
1. **Sem Atrasados**:
   - Se nenhuma cobrança atrasada, retorna lista vazia `[]`

2. **Ordenação**:
   - Resultado não especifica ordenação; considerar ordenar por `totalOverdueValue` DESC no cliente

---

### GET /api/v1/reports/dashboard

**Método HTTP e Path**
```
GET /api/v1/reports/dashboard
```

**Autenticação/Autorização**
- **Roles Requeridas**: Presumível `COMPANY_ADMIN` ou `HOLDING_ADMIN`
- **Header X-Company-Id**: Presumível obrigatório

**Headers Relevantes**
- `Authorization`: Bearer token JWT (presumível)
- `X-Company-Id`: Company ID
- `Accept`: `application/json`

**DTO de Request**
```
Sem parâmetros
```

**Regras de Negócio**
1. **Dados Agregados**:
   - Combina múltiplos relatórios em uma única resposta
   - Útil para popular dashboard em frontend

2. **Períodos**:
   - MRR/ARR: Todos os tempos (assinaturas ativas agora)
   - Mês Atual: Do dia 1 do mês até hoje
   - Churn do Mês: Cancelamentos no mês

3. **Top Inadimplentes**:
   - Limitado aos 10 clientes com maior valor em atraso

**JSON de Request Exemplo**
```
GET /api/v1/reports/dashboard HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
X-Company-Id: 5
```

**DTO de Response**
```
DashboardResponse:
- calculatedAt: LocalDateTime - Quando foi calculado

// Receita Recorrente
- mrr: BigDecimal - Monthly Recurring Revenue
- arr: BigDecimal - Annual Recurring Revenue

// Totais
- totalCustomers: Long - Quantidade de clientes
- totalActiveSubscriptions: Long - Assinaturas ativas
- totalCharges: Long - Total de cobranças (todos os tempos)
- totalOverdueCharges: Long - Cobranças atrasadas
- totalOverdueValue: BigDecimal - Valor total em atraso

// Mês Atual
- revenueCurrentMonth: BigDecimal - Receita do mês
- chargesReceivedCurrentMonth: Long - Cobranças recebidas

// Churn do Mês Atual
- canceledCurrentMonth: Long - Cancelamentos no mês
- churnRateCurrentMonth: BigDecimal - Taxa de churn (%)

// Receita por Método (mês atual)
- revenueByMethod: List<RevenueReportEntry> - Agrupado por método de pagamento

// Top Clientes Inadimplentes
- topOverdueCustomers: List<OverdueReportEntry> - Top 10 por valor em atraso
```

**JSON de Response Exemplo**
```json
{
  "calculatedAt": "2025-04-13T15:00:00",
  "mrr": "12500.50",
  "arr": "150006.00",
  "totalCustomers": 250,
  "totalActiveSubscriptions": 180,
  "totalCharges": 5432,
  "totalOverdueCharges": 12,
  "totalOverdueValue": "9876.50",
  "revenueCurrentMonth": "15234.80",
  "chargesReceivedCurrentMonth": 340,
  "canceledCurrentMonth": 5,
  "churnRateCurrentMonth": "2.78",
  "revenueByMethod": [
    {
      "groupKey": "PIX",
      "chargeCount": 200,
      "totalValue": "8900.00"
    },
    {
      "groupKey": "CREDIT_CARD",
      "chargeCount": 120,
      "totalValue": "5234.80"
    }
  ],
  "topOverdueCustomers": [
    {
      "customerId": 101,
      "customerName": "Empresa ABC",
      "overdueCount": 3,
      "totalOverdueValue": "5432.10"
    },
    {
      "customerId": 102,
      "customerName": "PJ XYZ",
      "overdueCount": 1,
      "totalOverdueValue": "3500.00"
    }
  ]
}
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | Dashboard populado |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem acesso |
| **500 Internal Server Error** | Erro no banco |

**Observações/Edge Cases**
1. **Tempo de Execução**:
   - Dashboard faz múltiplas queries; pode ser lento em bases grandes
   - Considerar cachear resultado por N minutos

2. **Sem Dados**:
   - Retorna 200 com zeros em todos os campos se nenhum dado

---

## ReportExportController

### GET /api/v1/reports/export/revenue

**Método HTTP e Path**
```
GET /api/v1/reports/export/revenue?from={from}&to={to}&groupBy={groupBy}
```

**Autenticação/Autorização**
- **Roles Requeridas**: Presumível `COMPANY_ADMIN` ou `HOLDING_ADMIN`
- **Header X-Company-Id**: Presumível obrigatório
- **Nota**: Sem `@PreAuthorize`, protegido por TenantContext

**Headers Relevantes**
- `Authorization`: Bearer token JWT (presumível)
- `X-Company-Id`: Company ID
- `Accept`: `text/csv` (recomendado)

**DTO de Request**
```
Query Parameters (idênticos ao /reports/revenue):

from: LocalDate (obrigatório)
to: LocalDate (obrigatório)
groupBy: String (opcional, padrão: "origin")
```

**Regras de Negócio**
1. **Formato**:
   - Retorna CSV com encoding UTF-8
   - Header: `Content-Type: text/csv; charset=UTF-8`
   - Disposition: `attachment; filename="revenue_YYYY-MM-DD_YYYY-MM-DD.csv"`

2. **Colunas**:
   - `Agrupamento`: Valor do groupKey
   - `Quantidade`: chargeCount
   - `Valor Total`: totalValue (sem formatação, ponto como separador decimal)

**CSV de Request Exemplo**
```
GET /api/v1/reports/export/revenue?from=2025-04-01&to=2025-04-13&groupBy=method HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
X-Company-Id: 5
Accept: text/csv
```

**CSV de Response Exemplo**
```
Agrupamento,Quantidade,Valor Total
PIX,45,2345.67
CREDIT_CARD,23,1234.50
BOLETO,12,567.89
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | CSV gerado e enviado |
| **400 Bad Request** | Datas inválidas |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem acesso |
| **500 Internal Server Error** | Erro ao gerar CSV |

**Observações/Edge Cases**
1. **Nomes com Vírgula**:
   - Se `groupKey` contiver vírgula, não há escape; considerar quote CSV

2. **Grande Volume**:
   - Se muitos registros, arquivo pode ser grande; considerar streaming ou compressão

---

### GET /api/v1/reports/export/mrr

**Método HTTP e Path**
```
GET /api/v1/reports/export/mrr
```

**Autenticação/Autorização**
- **Roles Requeridas**: Presumível `COMPANY_ADMIN` ou `HOLDING_ADMIN`
- **Header X-Company-Id**: Presumível obrigatório

**Headers Relevantes**
- `Authorization`: Bearer token JWT (presumível)
- `X-Company-Id`: Company ID
- `Accept`: `text/csv`

**DTO de Request**
```
Sem parâmetros
```

**Regras de Negócio**
1. **Formato**:
   - CSV com encoding UTF-8
   - Uma linha de dados (header + dados)

2. **Colunas**:
   - `Calculado Em`: calculatedAt (ISO datetime)
   - `Assinaturas Ativas`: activeSubscriptions
   - `MRR`: mrr
   - `ARR`: arr

**CSV de Request Exemplo**
```
GET /api/v1/reports/export/mrr HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
X-Company-Id: 5
Accept: text/csv
```

**CSV de Response Exemplo**
```
Calculado Em,Assinaturas Ativas,MRR,ARR
2025-04-13T15:00:00,150,12500.50,150006.00
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | CSV gerado |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem acesso |
| **500 Internal Server Error** | Erro |

**Observações/Edge Cases**
1. **Sempre Uma Linha**:
   - Resultado é determinístico, sempre retorna 1 linha de dados

---

### GET /api/v1/reports/export/churn

**Método HTTP e Path**
```
GET /api/v1/reports/export/churn?from={from}&to={to}
```

**Autenticação/Autorização**
- **Roles Requeridas**: Presumível `COMPANY_ADMIN` ou `HOLDING_ADMIN`
- **Header X-Company-Id**: Presumível obrigatório

**Headers Relevantes**
- `Authorization`: Bearer token JWT (presumível)
- `X-Company-Id`: Company ID
- `Accept`: `text/csv`

**DTO de Request**
```
Query Parameters:

from: LocalDate (obrigatório)
to: LocalDate (obrigatório)
```

**Regras de Negócio**
1. **Formato**:
   - CSV com header e 1 linha de dados
   - Encoding UTF-8

2. **Colunas**:
   - `Periodo Inicio`: periodStart (ISO date)
   - `Periodo Fim`: periodEnd (ISO date)
   - `Cancelamentos`: canceledInPeriod
   - `Ativas no Inicio`: activeAtStart
   - `Taxa Churn (%)`: churnRate

**CSV de Request Exemplo**
```
GET /api/v1/reports/export/churn?from=2025-04-01&to=2025-04-13 HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
X-Company-Id: 5
Accept: text/csv
```

**CSV de Response Exemplo**
```
Periodo Inicio,Periodo Fim,Cancelamentos,Ativas no Inicio,Taxa Churn (%)
2025-04-01,2025-04-13,8,150,5.33
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | CSV gerado |
| **400 Bad Request** | Datas inválidas |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem acesso |
| **500 Internal Server Error** | Erro |

---

### GET /api/v1/reports/export/overdue

**Método HTTP e Path**
```
GET /api/v1/reports/export/overdue
```

**Autenticação/Autorização**
- **Roles Requeridas**: Presumível `COMPANY_ADMIN` ou `HOLDING_ADMIN`
- **Header X-Company-Id**: Presumível obrigatório

**Headers Relevantes**
- `Authorization`: Bearer token JWT (presumível)
- `X-Company-Id`: Company ID
- `Accept`: `text/csv`

**DTO de Request**
```
Sem parâmetros
```

**Regras de Negócio**
1. **Formato**:
   - CSV com header + múltiplas linhas
   - Encoding UTF-8

2. **Colunas**:
   - `ID Cliente`: customerId
   - `Nome Cliente`: customerName (entre aspas se contiver especiais)
   - `Qtd Cobranças Atrasadas`: overdueCount
   - `Valor Total Atrasado`: totalOverdueValue

**CSV de Request Exemplo**
```
GET /api/v1/reports/export/overdue HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
X-Company-Id: 5
Accept: text/csv
```

**CSV de Response Exemplo**
```
ID Cliente,Nome Cliente,Qtd Cobranças Atrasadas,Valor Total Atrasado
101,"Empresa ABC Ltda",3,5432.10
105,"PJ XYZ Serviços",1,1200.00
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | CSV gerado |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem acesso |
| **500 Internal Server Error** | Erro |

**Observações/Edge Cases**
1. **Nomes com Aspas**:
   - Nomes com aspas duplas internas são escapados (`"` → `""`)
   - Nomes com vírgula ou quebra de linha são envolvidos em aspas

---

## DataImportController

### POST /api/v1/import/asaas

**Método HTTP e Path**
```
POST /api/v1/import/asaas
```

**Autenticação/Autorização**
- **Roles Requeridas**: `HOLDING_ADMIN` ou `COMPANY_ADMIN`
- **Header X-Company-Id**: Obrigatório (extrai de TenantContext)
- **Nota**: Importa dados para a empresa do contexto

**Headers Relevantes**
- `Authorization`: Bearer token JWT (obrigatório)
- `X-Company-Id`: Company ID (obrigatório)
- `Content-Type`: `application/json`

**DTO de Request**
```
Body: Vazio (sem parâmetros)

Nota: A empresa deve ter configurado:
  - Chave API do Asaas (campo apiKey em Company)
  - Account ID do Asaas (para fazer requisições)
```

**Regras de Negócio**
1. **Escopo de Importação**:
   - Busca clientes do Asaas (paginado, 100 por página)
   - Busca assinaturas (paginado)
   - Busca cobranças/pagamentos (paginado)

2. **Deduplicação**:
   - Para clientes: Verifica se `asaasId` ou `document` já existe
   - Se existir, pula (skipped)
   - Registra erro se documento é obrigatório mas vazio

3. **Validações**:
   - Cliente: cpfCnpj e name obrigatórios
   - Assinatura: customer (cliente) deve existir localmente
   - Cobrança: customer deve existir localmente

4. **Parcelamentos**:
   - Se cobrança tem `installment`, cria registro de parcelamento se não existir

5. **Plano Placeholder**:
   - Se não existir plano `IMPORTED_ASAAS`, cria automaticamente
   - Assinaturas importadas vinculam a esse plano (marcar depois para reatribuição)

6. **Status Mapping**:
   - Mapeia status Asaas para status locais (ex: `PENDING` → PENDING, `RECEIVED` → RECEIVED)

**JSON de Request Exemplo**
```
POST /api/v1/import/asaas HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
X-Company-Id: 5
Content-Type: application/json
```

**DTO de Response**
```
ImportResult:
- customersImported: Integer - Clientes criados
- customersSkipped: Integer - Clientes existentes (pulados)
- subscriptionsImported: Integer - Assinaturas criadas
- subscriptionsSkipped: Integer - Assinaturas existentes
- chargesImported: Integer - Cobranças criadas
- chargesSkipped: Integer - Cobranças existentes
- installmentsImported: Integer - Parcelamentos criados
- installmentsSkipped: Integer - Parcelamentos existentes
- errors: List<String> - Mensagens de erro (ignoradas durante processamento)
- startedAt: LocalDateTime - Quando começou
- finishedAt: LocalDateTime - Quando terminou
```

**JSON de Response Exemplo**
```json
{
  "customersImported": 142,
  "customersSkipped": 23,
  "subscriptionsImported": 89,
  "subscriptionsSkipped": 12,
  "chargesImported": 567,
  "chargesSkipped": 145,
  "installmentsImported": 34,
  "installmentsSkipped": 8,
  "errors": [
    "Cliente cust_123 ignorado: cpfCnpj nulo ou vazio",
    "Assinatura sub_456 ignorada: cliente cust_789 nao encontrado localmente",
    "Erro ao importar cobranca pay_999: java.lang.NullPointerException"
  ],
  "startedAt": "2025-04-13T15:10:00",
  "finishedAt": "2025-04-13T15:15:30"
}
```

**Códigos HTTP Possíveis**
| Código | Cenário |
|--------|---------|
| **200 OK** | Importação completada (com ou sem erros) |
| **400 Bad Request** | Empresa não encontrada, API key não configurada |
| **401 Unauthorized** | Token inválido |
| **403 Forbidden** | Sem role apropriada |
| **500 Internal Server Error** | Erro não-tratado (falha na conexão com Asaas, etc.) |
| **503 Service Unavailable** | Asaas API indisponível |

**Observações/Edge Cases**
1. **Longa Execução**:
   - Importação pode levar minutos em bases grandes (muitos clientes/cobranças)
   - Recomenda-se rodar como job assíncrono em produção

2. **Erros Parciais**:
   - Se 1000 clientes e falhar em 100, importa 900 e retorna erros dos 100
   - Não faz rollback de sucessos para manter progresso

3. **API Key Obrigatória**:
   - Se empresa não tem `apiKey` configurada, retorna 400

4. **Idempotência Parcial**:
   - Primeira chamada importa 100 clientes
   - Segunda chamada não re-importa (deduplicação), mas tenta importar novos
   - Seguro chamar múltiplas vezes

5. **Mapeamento de Status**:
   - Status Asaas `RECEIVED_IN_CASH` mapeia para `RECEIVED` local
   - Status Asaas `CHARGEBACK_REQUESTED` mapeia para `CHARGEBACK` local

6. **Ciclos de Assinatura**:
   - Mapeia `MONTHLY` → MONTHLY, `SEMIANNUALLY` → SEMIANNUALLY, `YEARLY` → YEARLY
   - Outros valores padrão para MONTHLY

7. **Plano Importado**:
   - Plano criado é `inactive` (false)
   - Admin deve criar planos reais e reatribuir assinaturas depois

---

## Resumo de Autenticação por Controller

| Controller | Endpoint | Autenticação | Roles | X-Company-Id |
|---|---|---|---|---|
| WebhookController | POST /api/v1/webhooks/asaas | Token de webhook | permitAll | Não (query param) |
| WebhookAdminController | GET /api/v1/admin/webhooks | JWT | HOLDING_ADMIN, SYSTEM | Não |
| WebhookAdminController | GET /api/v1/admin/webhooks/summary | JWT | HOLDING_ADMIN, SYSTEM | Não |
| WebhookAdminController | POST /api/v1/admin/webhooks/{id}/replay | JWT | HOLDING_ADMIN, SYSTEM | Não |
| OutboxAdminController | GET /api/v1/admin/outbox | JWT | HOLDING_ADMIN | Não |
| OutboxAdminController | GET /api/v1/admin/outbox/summary | JWT | HOLDING_ADMIN | Não |
| OutboxAdminController | POST /api/v1/admin/outbox/{id}/retry | JWT | HOLDING_ADMIN | Não |
| ReconciliationController | POST /api/v1/admin/reconciliation/charges | JWT | HOLDING_ADMIN | Sim (obrigatório) |
| ReconciliationController | POST /api/v1/admin/reconciliation/subscriptions | JWT | HOLDING_ADMIN | Sim (obrigatório) |
| ReconciliationController | POST /api/v1/admin/reconciliation/dlq/replay | JWT | HOLDING_ADMIN | Não (cross-tenant) |
| ReportController | GET /api/v1/reports/revenue | JWT | Presumível COMPANY_ADMIN+ | Sim (presumível) |
| ReportController | GET /api/v1/reports/subscriptions/mrr | JWT | Presumível COMPANY_ADMIN+ | Sim (presumível) |
| ReportController | GET /api/v1/reports/subscriptions/churn | JWT | Presumível COMPANY_ADMIN+ | Sim (presumível) |
| ReportController | GET /api/v1/reports/overdue | JWT | Presumível COMPANY_ADMIN+ | Sim (presumível) |
| ReportController | GET /api/v1/reports/dashboard | JWT | Presumível COMPANY_ADMIN+ | Sim (presumível) |
| ReportExportController | GET /api/v1/reports/export/revenue | JWT | Presumível COMPANY_ADMIN+ | Sim (presumível) |
| ReportExportController | GET /api/v1/reports/export/mrr | JWT | Presumível COMPANY_ADMIN+ | Sim (presumível) |
| ReportExportController | GET /api/v1/reports/export/churn | JWT | Presumível COMPANY_ADMIN+ | Sim (presumível) |
| ReportExportController | GET /api/v1/reports/export/overdue | JWT | Presumível COMPANY_ADMIN+ | Sim (presumível) |
| DataImportController | POST /api/v1/import/asaas | JWT | HOLDING_ADMIN, COMPANY_ADMIN | Sim (obrigatório) |
