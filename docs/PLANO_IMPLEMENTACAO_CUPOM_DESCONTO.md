# Plano de Implementacao - Sistema de Cupons de Desconto

> Documento de especificacao tecnica completo para implementacao do modulo de cupons de desconto.
> Data: 2026-04-10

---

## 1. Resumo

Sistema de cupons de desconto multi-tenant, escopado por empresa (`company_id`), com suporte a:
- Cupons para **assinaturas** (primeira cobranca ou recorrente com limite de meses)
- Cupons para **cobrancas avulsas** (uso unico por customer)
- Validacao publica (sem auth) e autenticada (com contexto do tenant)
- Desconto percentual (max 90%) ou valor fixo
- Restricoes por plano (`plan.codigo`), customer whitelist, ciclo de cobranca
- Controle 100% local — Asaas recebe apenas o valor final ja com desconto

---

## 2. Decisoes Arquiteturais

| Decisao | Escolha | Motivo |
|---------|---------|--------|
| Escopo | Por empresa (`company_id`) | Multi-tenant com RLS, igual ao resto do sistema |
| Nomenclatura | Ingles | Consistencia com o projeto (`coupon`, `discount_type`, etc.) |
| "Organizacao" do doc original | `customer` | O cliente pagador desta API |
| Segmentos | Ignorado | Nao existe campo de segmento na entidade `customer` |
| Aplicacao | `SUBSCRIPTION` ou `CHARGE` (nunca ambos) | Cupons sao especificos para cada contexto |
| Parcelamentos | Nao suportado | Decisao do usuario |
| Desconto maximo | 90% | Assinaturas/cobrancas nunca podem ter valor R$0,00 |
| Integracao Asaas | `value` ja com desconto aplicado | Campo `discount` do Asaas e para pagamento antecipado, nao serve |
| Expiracao recorrente | No webhook `PAYMENT_RECEIVED` | Apos N cobranças pagas, faz `PUT /subscriptions/{id}` no Asaas com valor cheio |
| Pausa/cancelamento | Remove cupom + atualiza Asaas | Cliente e avisado antes (consentimento obrigatorio) |
| Cobranca avulsa | Uso unico por customer | Mesmo cupom pode ser usado por customers diferentes |
| Auditoria | `@Auditable` + `@CreationTimestamp` | Padrao do projeto, sem campos `user_*` na entidade |

---

## 3. Entidades

### 3.1. Coupon (tabela `coupons`)

| Campo | Tipo DB | Java | Nullable | Default | Descricao |
|-------|---------|------|----------|---------|-----------|
| `id` | BIGSERIAL PK | Long | NOT NULL | auto | ID |
| `company_id` | BIGINT FK | Company | NOT NULL | - | Tenant (RLS) |
| `code` | VARCHAR(50) | String | NOT NULL | - | Codigo unico por empresa, armazenado UPPERCASE. Regex: `^[A-Z0-9_-]+$` |
| `description` | VARCHAR(255) | String | NULL | - | Descricao do cupom |
| `discount_type` | VARCHAR(20) | DiscountType enum | NOT NULL | - | `PERCENTAGE` ou `FIXED_AMOUNT` |
| `discount_value` | NUMERIC(12,2) | BigDecimal | NOT NULL | - | Valor do desconto. Se PERCENTAGE: 0.01 a 90.00. Se FIXED_AMOUNT: > 0. |
| `scope` | VARCHAR(20) | CouponScope enum | NOT NULL | - | `SUBSCRIPTION` ou `CHARGE` |
| `application_type` | VARCHAR(20) | CouponApplicationType enum | NOT NULL | `FIRST_CHARGE` | `FIRST_CHARGE` ou `RECURRING`. So se aplica quando scope = SUBSCRIPTION. Para CHARGE, sempre uso unico. |
| `recurrence_months` | INT | Integer | NULL | - | Meses de recorrencia (so para RECURRING). NULL = permanente. |
| `valid_from` | TIMESTAMP | LocalDateTime | NULL | - | Inicio da vigencia (null = imediato) |
| `valid_until` | TIMESTAMP | LocalDateTime | NULL | - | Fim da vigencia (null = sem prazo) |
| `max_uses` | INT | Integer | NULL | - | Limite global de utilizacoes (null = ilimitado) |
| `max_uses_per_customer` | INT | Integer | NULL | - | Limite por customer (null = ilimitado) |
| `usage_count` | INT | Integer | NOT NULL | 0 | Contador de utilizacoes (incrementado atomicamente) |
| `allowed_plans` | JSONB | String | NULL | - | Lista de `plan.codigo` permitidos. Ex: `["basico","premium"]`. NULL = todos. So para scope SUBSCRIPTION. |
| `allowed_customers` | JSONB | String | NULL | - | Whitelist de customer IDs. Ex: `[1,5,10]`. NULL = todos. |
| `allowed_cycle` | VARCHAR(20) | String | NULL | - | `MONTHLY`, `SEMIANNUALLY`, `YEARLY` ou NULL = todos. So para scope SUBSCRIPTION. |
| `active` | BOOLEAN | Boolean | NOT NULL | true | Flag ativo/inativo |
| `deleted_at` | TIMESTAMP | LocalDateTime | NULL | - | Soft delete |
| `created_at` | TIMESTAMP | LocalDateTime | NOT NULL | NOW() | Criacao |
| `updated_at` | TIMESTAMP | LocalDateTime | NOT NULL | NOW() | Atualizacao |

**Metodos da entidade:**
- `isValid()` → `active && deletedAt == null && isWithinPeriod() && !hasReachedGlobalLimit()`
- `isWithinPeriod()` → verifica `valid_from` e `valid_until` contra `now()`
- `hasReachedGlobalLimit()` → `max_uses != null && usage_count >= max_uses`

**Constraints:**
- `UNIQUE INDEX idx_coupons_company_code ON coupons(company_id, code) WHERE deleted_at IS NULL`
- RLS policy com `company_id`

### 3.2. CouponUsage (tabela `coupon_usages`)

| Campo | Tipo DB | Java | Nullable | Default | Descricao |
|-------|---------|------|----------|---------|-----------|
| `id` | BIGSERIAL PK | Long | NOT NULL | auto | ID |
| `company_id` | BIGINT FK | Company | NOT NULL | - | Tenant (RLS) |
| `coupon_id` | BIGINT FK | Coupon | NOT NULL | - | Cupom utilizado |
| `customer_id` | BIGINT FK | Customer | NOT NULL | - | Customer que usou |
| `subscription_id` | BIGINT | Long | NULL | - | Assinatura associada (se scope = SUBSCRIPTION) |
| `charge_id` | BIGINT | Long | NULL | - | Cobranca associada (se scope = CHARGE) |
| `original_value` | NUMERIC(12,2) | BigDecimal | NOT NULL | - | Valor antes do desconto |
| `discount_amount` | NUMERIC(12,2) | BigDecimal | NOT NULL | - | Valor do desconto aplicado |
| `final_value` | NUMERIC(12,2) | BigDecimal | NOT NULL | - | Valor final (original - desconto) |
| `plan_code` | VARCHAR(50) | String | NULL | - | Codigo do plano (para historico) |
| `cycle` | VARCHAR(20) | String | NULL | - | Ciclo da assinatura (para historico) |
| `used_at` | TIMESTAMP | LocalDateTime | NOT NULL | NOW() | Timestamp do uso |

**Constraint:** Tabela append-only (nunca update/delete).

### 3.3. Alteracoes em entidades existentes

#### Subscription (adicionar campos)

| Campo | Tipo | Descricao |
|-------|------|-----------|
| `coupon_id` | BIGINT FK (nullable) | Cupom atualmente aplicado |
| `coupon_code` | VARCHAR(50) (nullable) | Codigo do cupom (snapshot para historico) |
| `coupon_discount_amount` | NUMERIC(12,2) (nullable) | Valor do desconto aplicado |
| `coupon_uses_remaining` | INT (nullable) | Cobranças restantes com desconto. NULL = permanente. Decrementado a cada PAYMENT_RECEIVED. Quando chegar a 0, remove cupom e atualiza Asaas. |

> Esses campos sao removidos (setados NULL) quando: cupom expira, cliente pausa, ou cliente cancela.

#### Charge (adicionar campos)

| Campo | Tipo | Descricao |
|-------|------|-----------|
| `coupon_id` | BIGINT FK (nullable) | Cupom aplicado nesta cobranca |
| `coupon_code` | VARCHAR(50) (nullable) | Codigo do cupom (snapshot) |
| `discount_amount` | NUMERIC(12,2) (nullable) | Valor do desconto aplicado |
| `original_value` | NUMERIC(12,2) (nullable) | Valor original antes do desconto |

---

## 4. Enums

```java
public enum DiscountType {
    PERCENTAGE,
    FIXED_AMOUNT
}

public enum CouponScope {
    SUBSCRIPTION,  // Cupom para assinaturas
    CHARGE         // Cupom para cobrancas avulsas
}

public enum CouponApplicationType {
    FIRST_CHARGE,  // Desconto apenas na primeira cobranca
    RECURRING      // Desconto recorrente por N meses (ou permanente se recurrenceMonths = null)
}
```

---

## 5. Calculo do Desconto

```java
public static CouponDiscountResult calculateDiscount(
    DiscountType type, BigDecimal discountValue, BigDecimal originalValue
) {
    BigDecimal discount;

    if (type == PERCENTAGE) {
        // Cap em 90% para nunca gerar valor R$0,00
        BigDecimal cappedPercent = discountValue.min(new BigDecimal("90"));
        discount = originalValue
            .multiply(cappedPercent)
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    } else {
        // FIXED_AMOUNT: nunca desconta mais que 90% do original
        BigDecimal maxDiscount = originalValue
            .multiply(new BigDecimal("0.90"))
            .setScale(2, RoundingMode.HALF_UP);
        discount = discountValue.min(maxDiscount);
    }

    BigDecimal finalValue = originalValue.subtract(discount);
    return new CouponDiscountResult(discount, finalValue);
}
```

> **Regra critica:** `finalValue` nunca pode ser <= 0. O desconto maximo e 90% do valor original.

---

## 6. Regras de Validacao (8 checks sequenciais)

Quando `validateCoupon()` e chamado:

| # | Check | Mensagem de erro | Publico | Autenticado |
|---|-------|------------------|---------|-------------|
| 1 | Cupom existe, ativo e nao deletado | "Coupon not found or inactive" | Sim | Sim |
| 2 | Dentro do periodo (valid_from/valid_until) | "Coupon is outside its validity period" | Sim | Sim |
| 3 | Limite global nao atingido | "Coupon has reached its maximum usage limit" | Sim | Sim |
| 4 | Limite por customer nao atingido | "Coupon has already been used the maximum times for this customer" | Nao | Sim |
| 5 | Scope compativel (SUBSCRIPTION ou CHARGE) | "Coupon is not valid for this operation type" | Sim | Sim |
| 6 | Plano esta na lista permitida (se scope = SUBSCRIPTION) | "Coupon is not valid for the selected plan" | Sim | Sim |
| 7 | Customer esta na whitelist (se allowed_customers preenchido) | "Coupon is not valid for this customer" | Nao | Sim |
| 8 | Ciclo compativel (se scope = SUBSCRIPTION e allowed_cycle preenchido) | "Coupon is not valid for the selected billing cycle" | Sim | Sim |

> **Publico:** validacao basica sem contexto de customer. Util para o frontend validar antes do checkout.
> **Autenticado:** validacao completa com checks por customer (limites, whitelist).

---

## 7. Endpoints

### 7.1. Admin CRUD (`/api/v1/coupons`)

| Metodo | Path | Descricao | Request | Response |
|--------|------|-----------|---------|----------|
| `POST` | `/` | Criar cupom | `CreateCouponRequest` | `CouponResponse` (201) |
| `GET` | `/` | Listar todos (paginado) | Pageable | `Page<CouponResponse>` |
| `GET` | `/active` | Listar vigentes | Pageable | `Page<CouponResponse>` |
| `GET` | `/{id}` | Buscar por ID | - | `CouponResponse` |
| `GET` | `/code/{code}` | Buscar por codigo | - | `CouponResponse` |
| `PUT` | `/{id}` | Atualizar | `UpdateCouponRequest` | `CouponResponse` |
| `DELETE` | `/{id}` | Desativar (soft delete) | - | 204 |
| `PATCH` | `/{id}/activate` | Reativar | - | `CouponResponse` |
| `GET` | `/{id}/usages` | Historico de uso (paginado) | Pageable | `Page<CouponUsageResponse>` |

### 7.2. Validacao publica (`/api/v1/coupons/validate`)

| Metodo | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| `POST` | `/public` | Nao | `ValidateCouponRequest` | `CouponValidationResponse` |

Checks executados: 1, 2, 3, 5, 6, 8 (sem checks por customer).

### 7.3. Validacao autenticada (`/api/v1/coupons/validate`)

| Metodo | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| `POST` | `/` | Sim | `ValidateCouponRequest` | `CouponValidationResponse` |

Checks executados: todos (1-8).

### 7.4. Aplicacao do cupom (campos adicionados nos requests existentes)

| Endpoint existente | Campo adicionado | Descricao |
|--------------------|------------------|-----------|
| `POST /api/v1/subscriptions` | `couponCode` (String, opcional) | Aplica cupom na assinatura |
| `POST /api/v1/charges/pix` | `couponCode` (String, opcional) | Aplica cupom na cobranca PIX |
| `POST /api/v1/charges/boleto` | `couponCode` (String, opcional) | Aplica cupom na cobranca Boleto |
| `POST /api/v1/charges/credit-card` | `couponCode` (String, opcional) | Aplica cupom na cobranca Cartao |
| `POST /api/v1/charges/undefined` | `couponCode` (String, opcional) | Aplica cupom na cobranca Undefined |

> **Nao se aplica a:** parcelamentos (`/charges/credit-card/installments`, `/charges/boleto/installments`).

---

## 8. DTOs

### 8.1. CreateCouponRequest

```java
public record CreateCouponRequest(
    @NotBlank String code,                      // UPPERCASE, regex ^[A-Z0-9_-]+$
    String description,
    @NotNull DiscountType discountType,          // PERCENTAGE ou FIXED_AMOUNT
    @NotNull @Positive BigDecimal discountValue, // Se PERCENTAGE: max 90
    @NotNull CouponScope scope,                  // SUBSCRIPTION ou CHARGE
    CouponApplicationType applicationType,       // Default: FIRST_CHARGE. Ignorado se scope=CHARGE
    Integer recurrenceMonths,                    // So para RECURRING. Null = permanente
    LocalDateTime validFrom,
    LocalDateTime validUntil,
    Integer maxUses,                             // Null = ilimitado
    Integer maxUsesPerCustomer,                  // Null = ilimitado
    String allowedPlans,                         // JSONB: ["basico","premium"]. So para SUBSCRIPTION
    String allowedCustomers,                     // JSONB: [1,5,10]. Null = todos
    String allowedCycle                          // MONTHLY, SEMIANNUALLY, YEARLY. So para SUBSCRIPTION
)
```

### 8.2. UpdateCouponRequest

Todos os campos opcionais (null = nao alterar). `code` NAO pode ser alterado. `scope` NAO pode ser alterado (implicaria em quebrar usages existentes).

### 8.3. CouponResponse

```java
public record CouponResponse(
    Long id,
    Long companyId,
    String code,
    String description,
    DiscountType discountType,
    BigDecimal discountValue,
    CouponScope scope,
    CouponApplicationType applicationType,
    Integer recurrenceMonths,
    LocalDateTime validFrom,
    LocalDateTime validUntil,
    Integer maxUses,
    Integer maxUsesPerCustomer,
    Integer usageCount,
    String allowedPlans,
    String allowedCustomers,
    String allowedCycle,
    Boolean active,
    Boolean currentlyValid,  // Calculado: active + dentro do periodo + limite nao atingido
    LocalDateTime createdAt,
    LocalDateTime updatedAt
)
```

### 8.4. ValidateCouponRequest

```java
public record ValidateCouponRequest(
    @NotBlank String couponCode,
    @NotNull CouponScope scope,      // SUBSCRIPTION ou CHARGE
    String planCode,                  // Obrigatorio se scope = SUBSCRIPTION
    String cycle,                     // Obrigatorio se scope = SUBSCRIPTION
    BigDecimal value,                 // Valor sobre o qual calcular o desconto (opcional no publico)
    Long customerId                   // Obrigatorio no autenticado, ignorado no publico
)
```

### 8.5. CouponValidationResponse

```java
public record CouponValidationResponse(
    boolean valid,
    String message,
    DiscountType discountType,       // null se invalido
    CouponApplicationType applicationType, // null se invalido
    BigDecimal percentualDiscount,   // null se FIXED_AMOUNT ou invalido
    BigDecimal discountAmount,       // Valor do desconto calculado (null se value nao informado)
    BigDecimal originalValue,        // null se value nao informado
    BigDecimal finalValue            // null se value nao informado
)
```

### 8.6. CouponUsageResponse

```java
public record CouponUsageResponse(
    Long id,
    Long couponId,
    String couponCode,
    Long customerId,
    Long subscriptionId,
    Long chargeId,
    BigDecimal originalValue,
    BigDecimal discountAmount,
    BigDecimal finalValue,
    String planCode,
    String cycle,
    LocalDateTime usedAt
)
```

---

## 9. Fluxos de Negocio

### 9.1. Fluxo: Assinatura com cupom

```
1. Frontend chama POST /api/v1/coupons/validate (publico ou autenticado)
   → Valida cupom, retorna preview do desconto

2. Frontend chama POST /api/v1/subscriptions com couponCode
   → SubscriptionService.subscribe():
     a. Valida cupom (todos os 8 checks autenticados)
     b. Calcula effectivePrice via PlanService.getEffectivePrice(plan, cycle)
     c. Calcula desconto: CouponCalculator.calculateDiscount(coupon, effectivePrice)
     d. discountedPrice = effectivePrice - desconto
     e. Envia discountedPrice como `value` para o Asaas
     f. Salva Subscription com:
        - effectivePrice = preco original (sem desconto)
        - couponId, couponCode, couponDiscountAmount
        - couponUsesRemaining:
          * FIRST_CHARGE → 1
          * RECURRING com meses → mesesRecorrencia
          * RECURRING sem meses → null (permanente)
     g. Registra CouponUsage
     h. Incrementa coupon.usageCount atomicamente
```

### 9.2. Fluxo: Cobranca avulsa com cupom

```
1. Frontend chama POST /api/v1/coupons/validate
   → Valida cupom com scope = CHARGE

2. Frontend chama POST /api/v1/charges/pix (ou boleto, cartao, undefined) com couponCode
   → ChargeService.createCharge():
     a. Valida cupom (scope deve ser CHARGE)
     b. Verifica se customer ja usou este cupom em cobranca avulsa (uso unico por customer)
     c. Calcula desconto sobre o valor da cobranca
     d. Envia valor com desconto como `value` para o Asaas
     e. Salva Charge com:
        - value = valor com desconto
        - originalValue = valor original
        - couponId, couponCode, discountAmount
     f. Registra CouponUsage
     g. Incrementa coupon.usageCount
```

### 9.3. Fluxo: Webhook PAYMENT_RECEIVED (expiracao do cupom recorrente)

```
Quando webhook PAYMENT_RECEIVED chega para uma subscription com cupom:

1. WebhookProcessor identifica a subscription
2. Verifica se subscription.couponId != null
3. Se couponUsesRemaining != null:
   a. Decrementa couponUsesRemaining
   b. Se couponUsesRemaining == 0:
      - Remove cupom da subscription (couponId=null, couponCode=null, etc.)
      - Calcula effectivePrice cheio via PlanService.getEffectivePrice(plan, cycle)
      - Faz PUT /subscriptions/{asaasId} no Asaas com value = effectivePrice
      - Log: "Coupon {code} expired for subscription {id}, Asaas updated to full price"
   c. Se couponUsesRemaining > 0:
      - Manter desconto, nao atualizar Asaas
4. Se couponUsesRemaining == null (permanente):
   - Nao fazer nada, desconto continua
```

### 9.4. Fluxo: Pausa da assinatura (perde cupom)

```
Quando SubscriptionService.pause() e chamado:

1. Verificar se subscription.couponId != null
2. Se tem cupom:
   a. NÃO pausar ainda
   b. Retornar resposta com flag `hasCouponWarning = true` e mensagem:
      "Ao pausar a assinatura, o cupom {code} sera removido. Deseja continuar?"
3. Frontend exibe confirmacao ao cliente
4. Cliente confirma → chama pause com flag `confirmCouponRemoval = true`
5. Service:
   a. Remove cupom da subscription (couponId=null, etc.)
   b. Faz PUT /subscriptions/{asaasId} no Asaas com value = effectivePrice cheio
   c. Pausa a assinatura normalmente
   d. Log: "Coupon {code} removed from subscription {id} due to pause"
```

### 9.5. Fluxo: Cancelamento da assinatura (perde cupom)

```
Mesmo fluxo da pausa:

1. Verificar cupom
2. Se tem cupom → retornar warning
3. Cliente confirma → chama cancel com confirmCouponRemoval = true
4. Remove cupom, atualiza Asaas com preco cheio, depois cancela

Nota: Atualizar o Asaas com preco cheio ANTES de cancelar garante
que se o cancelamento falhar, o preco ja esta correto.
```

---

## 10. Migration SQL (V4)

```sql
-- V4__coupon_system.sql

-- 1. Tabela de cupons
CREATE TABLE coupons (
    id                      BIGSERIAL       PRIMARY KEY,
    company_id              BIGINT          NOT NULL REFERENCES companies(id),
    code                    VARCHAR(50)     NOT NULL,
    description             VARCHAR(255),
    discount_type           VARCHAR(20)     NOT NULL,    -- PERCENTAGE, FIXED_AMOUNT
    discount_value          NUMERIC(12,2)   NOT NULL,
    scope                   VARCHAR(20)     NOT NULL,    -- SUBSCRIPTION, CHARGE
    application_type        VARCHAR(20)     NOT NULL DEFAULT 'FIRST_CHARGE', -- FIRST_CHARGE, RECURRING
    recurrence_months       INT,
    valid_from              TIMESTAMP,
    valid_until             TIMESTAMP,
    max_uses                INT,
    max_uses_per_customer   INT,
    usage_count             INT             NOT NULL DEFAULT 0,
    allowed_plans           JSONB,
    allowed_customers       JSONB,
    allowed_cycle           VARCHAR(20),
    active                  BOOLEAN         NOT NULL DEFAULT true,
    deleted_at              TIMESTAMP,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_coupons_company_code ON coupons(company_id, code) WHERE deleted_at IS NULL;
CREATE INDEX idx_coupons_company ON coupons(company_id);
CREATE INDEX idx_coupons_active ON coupons(company_id, active) WHERE deleted_at IS NULL;

-- 2. Tabela de utilizacoes (append-only)
CREATE TABLE coupon_usages (
    id                BIGSERIAL       PRIMARY KEY,
    company_id        BIGINT          NOT NULL REFERENCES companies(id),
    coupon_id         BIGINT          NOT NULL REFERENCES coupons(id),
    customer_id       BIGINT          NOT NULL REFERENCES customers(id),
    subscription_id   BIGINT,
    charge_id         BIGINT,
    original_value    NUMERIC(12,2)   NOT NULL,
    discount_amount   NUMERIC(12,2)   NOT NULL,
    final_value       NUMERIC(12,2)   NOT NULL,
    plan_code         VARCHAR(50),
    cycle             VARCHAR(20),
    used_at           TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_coupon_usages_coupon ON coupon_usages(coupon_id);
CREATE INDEX idx_coupon_usages_customer ON coupon_usages(customer_id);
CREATE INDEX idx_coupon_usages_company ON coupon_usages(company_id);

-- 3. Campos de cupom na subscription
ALTER TABLE subscriptions ADD COLUMN coupon_id BIGINT REFERENCES coupons(id);
ALTER TABLE subscriptions ADD COLUMN coupon_code VARCHAR(50);
ALTER TABLE subscriptions ADD COLUMN coupon_discount_amount NUMERIC(12,2);
ALTER TABLE subscriptions ADD COLUMN coupon_uses_remaining INT;

-- 4. Campos de cupom na charge
ALTER TABLE charges ADD COLUMN coupon_id BIGINT REFERENCES coupons(id);
ALTER TABLE charges ADD COLUMN coupon_code VARCHAR(50);
ALTER TABLE charges ADD COLUMN discount_amount NUMERIC(12,2);
ALTER TABLE charges ADD COLUMN original_value NUMERIC(12,2);

-- 5. RLS policies para as novas tabelas
ALTER TABLE coupons ENABLE ROW LEVEL SECURITY;
ALTER TABLE coupons FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON coupons
    USING (company_id = current_setting('app.current_company_id')::bigint);

ALTER TABLE coupon_usages ENABLE ROW LEVEL SECURITY;
ALTER TABLE coupon_usages FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON coupon_usages
    USING (company_id = current_setting('app.current_company_id')::bigint);

-- 6. Grants para role da aplicacao (sem BYPASSRLS)
GRANT SELECT, INSERT, UPDATE ON coupons TO payment_app;
GRANT SELECT, INSERT ON coupon_usages TO payment_app;
GRANT USAGE, SELECT ON SEQUENCE coupons_id_seq TO payment_app;
GRANT USAGE, SELECT ON SEQUENCE coupon_usages_id_seq TO payment_app;
```

---

## 11. Estrutura de Pacotes

```
br.com.holding.payments.coupon/
  Coupon.java                    (Entity)
  CouponUsage.java               (Entity)
  CouponRepository.java          (Repository)
  CouponUsageRepository.java     (Repository)
  CouponService.java             (Service - CRUD + validacao + aplicacao)
  CouponCalculator.java          (Pure calculator - calculo de desconto)
  CouponMapper.java              (Mapper)
  CouponController.java          (Controller - admin CRUD)
  CouponValidationController.java (Controller - validacao publica + autenticada)
  DiscountType.java              (Enum)
  CouponScope.java               (Enum)
  CouponApplicationType.java     (Enum)
  dto/
    CreateCouponRequest.java
    UpdateCouponRequest.java
    CouponResponse.java
    ValidateCouponRequest.java
    CouponValidationResponse.java
    CouponUsageResponse.java
```

---

## 12. CouponService - Metodos Principais

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    // --- CRUD ---
    PlanResponse create(CreateCouponRequest request);
    CouponResponse findById(Long id);
    CouponResponse findByCode(String code);
    Page<CouponResponse> findAll(Pageable pageable);
    Page<CouponResponse> findActive(Pageable pageable);
    CouponResponse update(Long id, UpdateCouponRequest request);
    void softDelete(Long id);
    CouponResponse activate(Long id);
    Page<CouponUsageResponse> getUsages(Long id, Pageable pageable);

    // --- Validacao ---
    CouponValidationResponse validatePublic(ValidateCouponRequest request);
    CouponValidationResponse validateAuthenticated(ValidateCouponRequest request);

    // --- Aplicacao ---
    CouponApplicationResult applyToSubscription(Coupon coupon, Customer customer,
        Plan plan, PlanCycle cycle, BigDecimal effectivePrice);
    CouponApplicationResult applyToCharge(Coupon coupon, Customer customer,
        BigDecimal chargeValue);

    // --- Expiracao ---
    void handlePaymentReceived(Subscription subscription);
    void removeCouponFromSubscription(Subscription subscription, String reason);

    // --- Helpers ---
    void incrementUsageCount(Long couponId);  // Atomico: UPDATE SET usage_count = usage_count + 1
    long countUsagesByCustomer(Long couponId, Long customerId);
    boolean hasCustomerUsedForCharge(Long couponId, Long customerId);
}
```

---

## 13. Alteracoes em Services Existentes

### 13.1. SubscriptionService.subscribe()

Apos calcular `effectivePrice`, antes de enviar ao Asaas:

```
Se request.couponCode != null:
    coupon = couponService.findActiveByCode(couponCode)
    result = couponService.applyToSubscription(coupon, customer, plan, cycle, effectivePrice)
    valueForAsaas = result.finalValue
    subscription.setCouponId(coupon.getId())
    subscription.setCouponCode(coupon.getCode())
    subscription.setCouponDiscountAmount(result.discountAmount)
    subscription.setCouponUsesRemaining(calculateUsesRemaining(coupon))
Senao:
    valueForAsaas = effectivePrice
```

### 13.2. SubscriptionService.pause()

```
Se subscription.couponId != null E !request.confirmCouponRemoval:
    throw BusinessException com warning sobre perda do cupom
    (ou retornar response com flag hasCouponWarning)

Se subscription.couponId != null E request.confirmCouponRemoval:
    couponService.removeCouponFromSubscription(subscription, "PAUSED")
    // Isso atualiza Asaas com preco cheio

Prosseguir com pausa normal
```

### 13.3. SubscriptionService.cancel()

Mesma logica da pausa.

### 13.4. ChargeService.createCharge()

```
Se request.couponCode != null:
    coupon = couponService.findActiveByCode(couponCode)
    result = couponService.applyToCharge(coupon, customer, request.value)
    charge.setValue(result.finalValue)
    charge.setOriginalValue(request.value)
    charge.setCouponId(coupon.getId())
    charge.setCouponCode(coupon.getCode())
    charge.setDiscountAmount(result.discountAmount)
    valueForAsaas = result.finalValue
Senao:
    valueForAsaas = request.value
```

### 13.5. WebhookProcessor (PAYMENT_RECEIVED)

```
Apos processar o pagamento normalmente:

Se subscription != null E subscription.couponId != null:
    couponService.handlePaymentReceived(subscription)
```

---

## 14. Alteracoes nos DTOs Existentes

### 14.1. CreateSubscriptionRequest

Adicionar campo:
```java
String couponCode  // Opcional
```

### 14.2. CreateChargeRequest

Adicionar campo:
```java
String couponCode  // Opcional
```

### 14.3. SubscriptionResponse

Adicionar campos:
```java
String couponCode              // Null se sem cupom
BigDecimal couponDiscountAmount // Null se sem cupom
Integer couponUsesRemaining     // Null se sem cupom ou permanente
```

### 14.4. ChargeResponse

Adicionar campos:
```java
String couponCode
BigDecimal discountAmount
BigDecimal originalValue
```

### 14.5. PauseSubscriptionRequest (NOVO)

Atualmente `pause()` nao recebe body. Sera necessario criar:

```java
public record PauseSubscriptionRequest(
    Boolean confirmCouponRemoval  // Default: false
) {}
```

Ou alternativamente, usar query param: `POST /subscriptions/{id}/pause?confirmCouponRemoval=true`

### 14.6. CancelSubscriptionRequest (NOVO ou adaptar)

Mesma logica: `DELETE /subscriptions/{id}?confirmCouponRemoval=true`

---

## 15. Validacoes do CouponService.create()

```
1. code: UPPERCASE, regex ^[A-Z0-9_-]+$, unico por company
2. discountType obrigatorio
3. discountValue > 0
4. Se PERCENTAGE: discountValue <= 90
5. scope obrigatorio (SUBSCRIPTION ou CHARGE)
6. Se scope = CHARGE: applicationType e ignorado (sempre uso unico)
7. Se scope = SUBSCRIPTION e applicationType = RECURRING: recurrenceMonths pode ser null (permanente)
8. Se validUntil preenchido e validFrom preenchido: validUntil > validFrom
9. Se maxUsesPerCustomer preenchido: maxUsesPerCustomer > 0
10. Se allowedPlans preenchido: verificar que sao codigos de planos existentes
11. Se allowedCycle preenchido: deve ser MONTHLY, SEMIANNUALLY ou YEARLY
```

---

## 16. Impacto em Outros Modulos

| Modulo | Impacto | Alteracao |
|--------|---------|-----------|
| **subscription** | Alto | Novos campos na entity, service e DTOs. Logica de cupom em subscribe/pause/cancel. |
| **charge** | Alto | Novos campos na entity, service e DTOs. Logica de cupom em createCharge. |
| **webhook** | Medio | WebhookProcessor precisa chamar `handlePaymentReceived()` para expirar cupons. |
| **planchange** | Baixo | Ao mudar de plano, verificar se cupom ainda e compativel com o novo plano. Se nao, remover cupom. |
| **integration/asaas** | Nenhum | Asaas recebe valor ja com desconto. Nenhuma mudanca nos DTOs do Asaas. |
| **outbox** | Baixo | Novos eventos: `CouponAppliedEvent`, `CouponExpiredEvent`, `CouponRemovedEvent`. |
| **report** | Baixo | Novas queries para relatorio de cupons (total de descontos, cupons mais usados, etc.). |

---

## 17. Ordem de Implementacao

| Etapa | Descricao | Dependencias |
|-------|-----------|--------------|
| 1 | Migration V4 (tabelas + campos + RLS) | Nenhuma |
| 2 | Enums: DiscountType, CouponScope, CouponApplicationType | Nenhuma |
| 3 | Entidades: Coupon, CouponUsage | Etapa 1, 2 |
| 4 | Atualizar entidades: Subscription (novos campos cupom), Charge (novos campos cupom) | Etapa 1 |
| 5 | CouponCalculator (pure, sem dependencias) | Etapa 2 |
| 6 | Repositories: CouponRepository, CouponUsageRepository | Etapa 3 |
| 7 | DTOs (todos: create, update, response, validate) | Etapa 2 |
| 8 | CouponMapper | Etapa 3, 7 |
| 9 | CouponService (CRUD + validacao + aplicacao + expiracao) | Etapas 3-8 |
| 10 | CouponController (admin CRUD) | Etapa 9 |
| 11 | CouponValidationController (publico + autenticado) | Etapa 9 |
| 12 | Alterar SubscriptionService (subscribe, pause, cancel) | Etapa 4, 9 |
| 13 | Alterar SubscriptionMapper e DTOs de resposta | Etapa 4 |
| 14 | Alterar ChargeService (createCharge) | Etapa 4, 9 |
| 15 | Alterar ChargeMapper e DTOs de resposta | Etapa 4 |
| 16 | Alterar WebhookProcessor (handlePaymentReceived) | Etapa 9 |
| 17 | Alterar PlanChangeService (verificar compatibilidade de cupom) | Etapa 9 |
| 18 | Outbox events (CouponAppliedEvent, CouponExpiredEvent, CouponRemovedEvent) | Etapa 9 |
| 19 | Testes unitarios e de integracao | Todas |

---

## 18. Riscos e Pontos de Atencao

1. **Race condition no usage_count**: O incremento de `usage_count` deve ser atomico (`UPDATE coupons SET usage_count = usage_count + 1 WHERE id = ? AND usage_count < max_uses`). Usar `@Modifying @Query` no repository com `RETURNING` para garantir atomicidade.

2. **Race condition no couponUsesRemaining**: Mesmo cuidado. Usar optimistic locking (`@Version`) da Subscription ou lock pessimista.

3. **Webhook fora de ordem**: Se `PAYMENT_RECEIVED` chegar antes da subscription ser salva, o handler nao encontra a subscription. O sistema ja trata isso com `DEFERRED` status e retry — manter esse comportamento.

4. **Cupom expirado entre validacao e aplicacao**: O frontend valida, o usuario demora 10 minutos, o cupom expira. A aplicacao deve revalidar no momento do `subscribe()` / `createCharge()`.

5. **Mudanca de plano com cupom ativo**: Se o cliente muda para um plano que nao esta na `allowed_plans` do cupom, o cupom deve ser removido automaticamente. Notificar via outbox event.

6. **Preco minimo**: Garantir que `finalValue > 0` em todas as situacoes. O cap de 90% no percentual e o `min()` no valor fixo cobrem isso, mas adicionar validacao extra como safety net.

7. **Asaas update apos expiracao**: Se o `PUT /subscriptions/{id}` no Asaas falhar, manter o cupom removido localmente mas agendar retry. Nao bloquear o fluxo do webhook por falha no Asaas.
