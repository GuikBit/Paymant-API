# Guia de Implementacao Frontend - Sistema de Cupons de Desconto

> Documento tecnico para o time de frontend implementar as telas de gestao
> de cupons (admin), validacao de cupons (checkout) e aplicacao de cupons
> em assinaturas e cobrancas avulsas.
>
> Data: 2026-04-10

---

## 1. Resumo das Funcionalidades

O sistema de cupons permite:

- **Admin**: criar, editar, listar, desativar/reativar cupons e visualizar historico de uso
- **Cliente (checkout)**: validar cupom antes de assinar ou pagar, e aplicar no momento da compra
- Cupons sao **especificos**: ou para assinaturas (`SUBSCRIPTION`) ou para cobrancas avulsas (`CHARGE`)
- Desconto **percentual** (max 90%) ou **valor fixo**
- Restricoes por plano, ciclo, customer e limites de uso
- Cupons de assinatura podem ser **primeira cobranca** ou **recorrente** (N meses)

---

## 2. Endpoints da API

### 2.1. Admin CRUD (autenticado)

Base URL: `/api/v1/coupons`

| Metodo | Endpoint | Descricao | Request Body | Response |
|--------|----------|-----------|-------------|----------|
| `POST` | `/` | Criar cupom | `CreateCouponRequest` | `CouponResponse` (201) |
| `GET` | `/` | Listar todos (paginado) | `?page=0&size=20&sort=createdAt,desc` | `Page<CouponResponse>` |
| `GET` | `/active` | Listar vigentes (paginado) | `?page=0&size=20` | `Page<CouponResponse>` |
| `GET` | `/{id}` | Buscar por ID | - | `CouponResponse` |
| `GET` | `/code/{code}` | Buscar por codigo | - | `CouponResponse` |
| `PUT` | `/{id}` | Atualizar cupom | `UpdateCouponRequest` | `CouponResponse` |
| `DELETE` | `/{id}` | Desativar (soft delete) | - | 204 No Content |
| `PATCH` | `/{id}/activate` | Reativar cupom | - | `CouponResponse` |
| `GET` | `/{id}/usages` | Historico de uso (paginado) | `?page=0&size=20` | `Page<CouponUsageResponse>` |

### 2.2. Validacao de Cupons

Base URL: `/api/v1/coupons/validate`

| Metodo | Endpoint | Auth | Descricao | Request Body | Response |
|--------|----------|------|-----------|-------------|----------|
| `POST` | `/public` | Nao | Validacao basica (sem checks por customer) | `ValidateCouponRequest` | `CouponValidationResponse` |
| `POST` | `/` | Sim | Validacao completa (com limites por customer e whitelist) | `ValidateCouponRequest` | `CouponValidationResponse` |

### 2.3. Aplicacao de Cupom (campo `couponCode` nos requests existentes)

| Endpoint | Campo adicionado | Descricao |
|----------|------------------|-----------|
| `POST /api/v1/subscriptions` | `couponCode` (opcional) | Aplica cupom na assinatura |
| `POST /api/v1/charges/pix` | `couponCode` (opcional) | Aplica cupom na cobranca PIX |
| `POST /api/v1/charges/boleto` | `couponCode` (opcional) | Aplica cupom na cobranca Boleto |
| `POST /api/v1/charges/credit-card` | `couponCode` (opcional) | Aplica cupom na cobranca Cartao |
| `POST /api/v1/charges/undefined` | `couponCode` (opcional) | Aplica cupom na cobranca Undefined |

> **Nao se aplica a:** parcelamentos. A API retorna erro se tentar enviar `couponCode` em endpoints de installments.

### 2.4. Pausa e Cancelamento com Cupom

| Endpoint | Parametro adicionado | Descricao |
|----------|---------------------|-----------|
| `POST /api/v1/subscriptions/{id}/pause` | `?confirmCouponRemoval=true` | Se a assinatura tem cupom, obriga confirmacao |
| `DELETE /api/v1/subscriptions/{id}` | `?confirmCouponRemoval=true` | Se a assinatura tem cupom, obriga confirmacao |

---

## 3. Tela: Formulario de Criacao/Edicao de Cupom (Admin)

### 3.1. Campos do formulario

#### Identificacao

| Campo | Tipo Input | Obrigatorio | Regras de Validacao | Notas |
|-------|-----------|-------------|---------------------|-------|
| `code` | `text` | Sim (criacao) | Regex: `^[A-Z0-9_-]+$`. Max 50 chars. | **Somente na criacao.** Desabilitar no formulario de edicao. Sera convertido para UPPERCASE pela API automaticamente. Exibir preview em UPPERCASE enquanto o usuario digita. |
| `description` | `textarea` | Nao | Max 255 chars. | Descricao interna do cupom. |

#### Tipo de Desconto

| Campo | Tipo Input | Obrigatorio | Regras de Validacao | Notas |
|-------|-----------|-------------|---------------------|-------|
| `discountType` | `select` | Sim | `PERCENTAGE` ou `FIXED_AMOUNT` | Ao trocar, ajustar label e placeholder do campo `discountValue`. |
| `discountValue` | `number` (decimal) | Sim | > 0. Se PERCENTAGE: max 90. | Label dinamico: "Percentual (%)" ou "Valor (R$)" conforme `discountType`. |

#### Escopo e Tipo de Aplicacao

| Campo | Tipo Input | Obrigatorio | Regras de Validacao | Notas |
|-------|-----------|-------------|---------------------|-------|
| `scope` | `select` | Sim (criacao) | `SUBSCRIPTION` ou `CHARGE` | **Somente na criacao.** Desabilitar no formulario de edicao. Ao selecionar `CHARGE`, esconder campos de plano, ciclo e tipo de aplicacao. |
| `applicationType` | `select` | Nao | `FIRST_CHARGE` ou `RECURRING`. Default: `FIRST_CHARGE`. | **Visivel apenas se scope = SUBSCRIPTION.** Se scope = CHARGE, ignorado (sempre uso unico). |
| `recurrenceMonths` | `number` (inteiro) | Nao | > 0. Visivel apenas se `applicationType = RECURRING`. | Numero de meses com desconto. Deixar vazio = desconto permanente. |

#### Vigencia

| Campo | Tipo Input | Obrigatorio | Regras de Validacao | Notas |
|-------|-----------|-------------|---------------------|-------|
| `validFrom` | `datetime-local` | Nao | - | Inicio da vigencia. Vazio = imediato (valido desde a criacao). |
| `validUntil` | `datetime-local` | Nao | Deve ser > `validFrom` se ambos preenchidos. | Fim da vigencia. Vazio = sem prazo. |

#### Limites de Uso

| Campo | Tipo Input | Obrigatorio | Regras de Validacao | Notas |
|-------|-----------|-------------|---------------------|-------|
| `maxUses` | `number` (inteiro) | Nao | > 0. | Limite global de utilizacoes. Vazio = ilimitado. Exibir no form: "Usos: {usageCount} / {maxUses}". |
| `maxUsesPerCustomer` | `number` (inteiro) | Nao | > 0. | Limite por customer. Vazio = ilimitado. |

#### Restricoes (visivel apenas se scope = SUBSCRIPTION)

| Campo | Tipo Input | Obrigatorio | Regras de Validacao | Notas |
|-------|-----------|-------------|---------------------|-------|
| `allowedPlans` | Multi-select ou chips | Nao | Array de codigos (slugs) de planos. | Buscar planos ativos via `GET /api/v1/plans` e popular o select. Enviar como JSON string: `["basico","premium"]`. Vazio = todos os planos. |
| `allowedCycle` | `select` | Nao | `MONTHLY`, `SEMIANNUALLY`, `YEARLY` ou vazio. | Vazio = todos os ciclos. |

#### Restricoes por Customer

| Campo | Tipo Input | Obrigatorio | Regras de Validacao | Notas |
|-------|-----------|-------------|---------------------|-------|
| `allowedCustomers` | Multi-select ou chips com busca | Nao | Array de IDs de customers. | Buscar customers via `GET /api/v1/customers`. Enviar como JSON string: `[1,5,10]`. Vazio = todos. Util para cupons exclusivos. |

### 3.2. Comportamento condicional do formulario

```
Ao selecionar scope:
  SUBSCRIPTION:
    - Mostrar: applicationType, recurrenceMonths, allowedPlans, allowedCycle
    - applicationType default: FIRST_CHARGE
  CHARGE:
    - Esconder: applicationType, recurrenceMonths, allowedPlans, allowedCycle
    - applicationType sera ignorado pela API

Ao selecionar applicationType:
  FIRST_CHARGE:
    - Esconder: recurrenceMonths
  RECURRING:
    - Mostrar: recurrenceMonths (com hint: "Vazio = permanente")

Ao selecionar discountType:
  PERCENTAGE:
    - Label do discountValue: "Percentual de desconto (%)"
    - Placeholder: "Ex: 20"
    - Validacao max: 90
  FIXED_AMOUNT:
    - Label do discountValue: "Valor do desconto (R$)"
    - Placeholder: "Ex: 30.00"
    - Sem limite max (API garante que nunca desconta mais que 90%)
```

### 3.3. Payload de criacao (POST /api/v1/coupons)

**Exemplo: Cupom percentual para assinatura, primeira cobranca:**
```json
{
  "code": "WELCOME50",
  "description": "50% de desconto na primeira mensalidade para novos clientes",
  "discountType": "PERCENTAGE",
  "discountValue": 50.0,
  "scope": "SUBSCRIPTION",
  "applicationType": "FIRST_CHARGE",
  "recurrenceMonths": null,
  "validFrom": "2026-04-01T00:00:00",
  "validUntil": "2026-06-30T23:59:59",
  "maxUses": 100,
  "maxUsesPerCustomer": 1,
  "allowedPlans": "[\"basico\",\"premium\",\"plus\"]",
  "allowedCustomers": null,
  "allowedCycle": null
}
```

**Exemplo: Cupom valor fixo para cobranca avulsa:**
```json
{
  "code": "DESC30REAIS",
  "description": "R$30 de desconto na cobranca",
  "discountType": "FIXED_AMOUNT",
  "discountValue": 30.00,
  "scope": "CHARGE",
  "applicationType": null,
  "recurrenceMonths": null,
  "validFrom": "2026-04-01T00:00:00",
  "validUntil": "2026-12-31T23:59:59",
  "maxUses": 500,
  "maxUsesPerCustomer": 1,
  "allowedPlans": null,
  "allowedCustomers": null,
  "allowedCycle": null
}
```

**Exemplo: Cupom recorrente 3 meses:**
```json
{
  "code": "FIDELIDADE20",
  "description": "20% de desconto nos 3 primeiros meses",
  "discountType": "PERCENTAGE",
  "discountValue": 20.0,
  "scope": "SUBSCRIPTION",
  "applicationType": "RECURRING",
  "recurrenceMonths": 3,
  "validFrom": null,
  "validUntil": null,
  "maxUses": null,
  "maxUsesPerCustomer": 1,
  "allowedPlans": "[\"premium\",\"plus\"]",
  "allowedCustomers": null,
  "allowedCycle": null
}
```

**Exemplo: Cupom permanente para customers especificos:**
```json
{
  "code": "PARCEIRO2026",
  "description": "15% permanente para parceiros",
  "discountType": "PERCENTAGE",
  "discountValue": 15.0,
  "scope": "SUBSCRIPTION",
  "applicationType": "RECURRING",
  "recurrenceMonths": null,
  "validFrom": null,
  "validUntil": null,
  "maxUses": 10,
  "maxUsesPerCustomer": 1,
  "allowedPlans": null,
  "allowedCustomers": "[1,5,10,22]",
  "allowedCycle": null
}
```

### 3.4. Payload de edicao (PUT /api/v1/coupons/{id})

Mesma estrutura **sem `code` e `scope`** (imutaveis). Campos `null` sao ignorados.

```json
{
  "description": "50% - Promocao estendida ate julho",
  "validUntil": "2026-07-31T23:59:59",
  "maxUses": 200
}
```

### 3.5. Resposta da API (CouponResponse)

```json
{
  "id": 1,
  "companyId": 1,
  "code": "WELCOME50",
  "description": "50% de desconto na primeira mensalidade",
  "discountType": "PERCENTAGE",
  "discountValue": 50.0,
  "scope": "SUBSCRIPTION",
  "applicationType": "FIRST_CHARGE",
  "recurrenceMonths": null,
  "validFrom": "2026-04-01T00:00:00",
  "validUntil": "2026-06-30T23:59:59",
  "maxUses": 100,
  "maxUsesPerCustomer": 1,
  "usageCount": 23,
  "allowedPlans": "[\"basico\",\"premium\",\"plus\"]",
  "allowedCustomers": null,
  "allowedCycle": null,
  "active": true,
  "currentlyValid": true,
  "createdAt": "2026-04-01T10:30:00",
  "updatedAt": "2026-04-05T14:20:00"
}
```

> **Campo `currentlyValid`**: calculado pela API. `true` se o cupom esta ativo, dentro do periodo de validade e nao atingiu o limite global. Usar para exibir badge "Vigente" / "Expirado" na listagem.

### 3.6. Erros de validacao da API

| Erro | Quando ocorre | Como tratar no frontend |
|------|---------------|------------------------|
| `"Ja existe um cupom com o codigo 'XXX' para esta empresa."` | Codigo duplicado | Mostrar erro no campo `code` |
| `"Desconto percentual nao pode ser maior que 90%."` | PERCENTAGE > 90 | Mostrar erro no campo `discountValue` |
| `"Data de fim deve ser posterior a data de inicio."` | validUntil <= validFrom | Mostrar erro no campo `validUntil` |

---

## 4. Tela: Listagem de Cupons (Admin)

### 4.1. Tabela

| Coluna | Origem | Formato | Notas |
|--------|--------|---------|-------|
| Codigo | `code` | Texto UPPERCASE, badge/chip | Clicar abre detalhe |
| Descricao | `description` | Texto truncado | - |
| Tipo | `discountType` | "Percentual" ou "Valor Fixo" | - |
| Desconto | `discountValue` | "50%" ou "R$ 30,00" conforme tipo | - |
| Escopo | `scope` | Badge: "Assinatura" ou "Cobranca" | Cores diferentes por escopo |
| Aplicacao | `applicationType` | "1a Cobranca" ou "Recorrente (3 meses)" | Incluir `recurrenceMonths` se aplicavel |
| Vigencia | `validFrom` / `validUntil` | "01/04 - 30/06/2026" ou "Sem prazo" | - |
| Usos | `usageCount` / `maxUses` | "23 / 100" ou "23 / ilimitado" | Barra de progresso opcional |
| Status | `currentlyValid` + `active` | Badge: "Vigente", "Expirado", "Desativado" | Verde/vermelho/cinza |
| Acoes | - | Editar, Desativar/Reativar, Ver usos | Dropdown ou icones |

### 4.2. Filtros sugeridos

- Status: Todos / Vigentes / Expirados / Desativados
- Escopo: Todos / Assinatura / Cobranca
- Busca por codigo ou descricao

### 4.3. Logica de status

```typescript
function getCouponStatus(coupon: CouponResponse): 'active' | 'expired' | 'disabled' {
  if (!coupon.active) return 'disabled';
  if (!coupon.currentlyValid) return 'expired';
  return 'active';
}
```

---

## 5. Tela: Historico de Uso (Admin)

Acessivel via `GET /api/v1/coupons/{id}/usages`

### 5.1. Tabela

| Coluna | Origem | Formato |
|--------|--------|---------|
| Data | `usedAt` | "10/04/2026 15:42" |
| Customer | `customerId` | ID ou nome (buscar separadamente) |
| Tipo | `subscriptionId` / `chargeId` | "Assinatura #12" ou "Cobranca #45" |
| Plano | `planCode` | Codigo do plano |
| Ciclo | `cycle` | "Mensal" / "Semestral" / "Anual" |
| Valor Original | `originalValue` | "R$ 149,90" |
| Desconto | `discountAmount` | "- R$ 74,95" |
| Valor Final | `finalValue` | "R$ 74,95" |

### 5.2. Resposta da API (CouponUsageResponse)

```json
{
  "id": 45,
  "couponId": 1,
  "couponCode": "WELCOME50",
  "customerId": 15,
  "subscriptionId": 12,
  "chargeId": null,
  "originalValue": 149.90,
  "discountAmount": 74.95,
  "finalValue": 74.95,
  "planCode": "premium",
  "cycle": "MONTHLY",
  "usedAt": "2026-04-10T15:42:00"
}
```

---

## 6. Tela: Validacao de Cupom (Checkout do Cliente)

### 6.1. Componente de input de cupom

O componente de cupom deve aparecer no checkout de **assinatura** e de **cobranca avulsa**.

```
┌──────────────────────────────────────────────┐
│  Tem um cupom de desconto?                   │
│                                              │
│  ┌─────────────────────┐  ┌──────────┐      │
│  │ Digite o codigo...  │  │  Aplicar │      │
│  └─────────────────────┘  └──────────┘      │
│                                              │
│  ✓ Cupom WELCOME50 aplicado!                 │ ← sucesso
│    50% de desconto na primeira cobranca      │
│    Valor original: R$ 149,90                 │
│    Desconto: - R$ 74,95                      │
│    Valor final: R$ 74,95                     │
│                              [Remover cupom] │
│                                              │
└──────────────────────────────────────────────┘
```

### 6.2. Fluxo de validacao

```
1. Usuario digita o codigo do cupom e clica "Aplicar"
2. Frontend normaliza para UPPERCASE
3. Determina o scope baseado no contexto:
   - Se esta no checkout de assinatura: scope = "SUBSCRIPTION"
   - Se esta no checkout de cobranca avulsa: scope = "CHARGE"

4. Se usuario NAO esta logado:
   → POST /api/v1/coupons/validate/public
   Body: {
     "couponCode": "WELCOME50",
     "scope": "SUBSCRIPTION",
     "planCode": "premium",           // slug do plano selecionado
     "cycle": "MONTHLY",              // ciclo selecionado
     "value": 149.90                  // preco efetivo (para calcular preview)
   }

5. Se usuario ESTA logado:
   → POST /api/v1/coupons/validate
   Body: {
     "couponCode": "WELCOME50",
     "scope": "SUBSCRIPTION",
     "planCode": "premium",
     "cycle": "MONTHLY",
     "value": 149.90,
     "customerId": 15                 // ID do customer logado
   }

6. Resposta de sucesso:
   {
     "valid": true,
     "message": "Cupom valido",
     "discountType": "PERCENTAGE",
     "applicationType": "FIRST_CHARGE",
     "percentualDiscount": 50.0,
     "discountAmount": 74.95,
     "originalValue": 149.90,
     "finalValue": 74.95
   }

7. Resposta de erro:
   {
     "valid": false,
     "message": "Cupom nao e valido para o plano selecionado",
     "discountType": null,
     ...todos os campos null
   }
```

### 6.3. Regras do componente

- **Revalidar ao trocar plano/ciclo:** se o usuario ja aplicou um cupom e depois troca o plano ou ciclo, revalidar automaticamente (o cupom pode nao ser valido para o novo plano/ciclo)
- **Mostrar tipo de aplicacao:** se `applicationType = "FIRST_CHARGE"`, exibir "Desconto valido apenas na primeira cobranca". Se `RECURRING`, exibir "Desconto por X meses" (ou "Desconto permanente" se `recurrenceMonths` nulo)
- **Input UPPERCASE:** converter o texto para UPPERCASE conforme o usuario digita
- **Remover cupom:** botao "Remover cupom" limpa o campo e recalcula o preco original
- **Desabilitar campo apos aplicar:** apos cupom validado com sucesso, desabilitar o input e mostrar o resumo. Habilitar apenas ao clicar "Remover"

### 6.4. Validacao para cobranca avulsa

```
POST /api/v1/coupons/validate (autenticado)
Body: {
  "couponCode": "DESC30REAIS",
  "scope": "CHARGE",
  "planCode": null,               // nao se aplica
  "cycle": null,                  // nao se aplica
  "value": 200.00,                // valor da cobranca
  "customerId": 15
}
```

---

## 7. Aplicacao do Cupom no Checkout

### 7.1. Assinatura

Ao criar a assinatura, enviar o `couponCode` no request:

```json
POST /api/v1/subscriptions
{
  "customerId": 15,
  "planId": 1,
  "billingType": "PIX",
  "cycle": "MONTHLY",
  "nextDueDate": "2026-04-15",
  "couponCode": "WELCOME50"
}
```

**Resposta:** A `SubscriptionResponse` agora inclui campos de cupom:

```json
{
  "id": 456,
  "companyId": 1,
  "customerId": 15,
  "planId": 1,
  "planName": "Premium Plus",
  "asaasId": "sub_abc123",
  "billingType": "PIX",
  "effectivePrice": 149.90,
  "cycle": "MONTHLY",
  "currentPeriodStart": "2026-04-15T00:00:00",
  "currentPeriodEnd": null,
  "nextDueDate": "2026-04-15",
  "status": "ACTIVE",
  "couponCode": "WELCOME50",
  "couponDiscountAmount": 74.95,
  "couponUsesRemaining": 1,
  "createdAt": "2026-04-10T12:00:00",
  "updatedAt": "2026-04-10T12:00:00"
}
```

> **Nota:** `effectivePrice` e o preco cheio (sem desconto). O desconto e rastreado separadamente em `couponDiscountAmount`. O valor enviado ao gateway de pagamento e `effectivePrice - couponDiscountAmount`.

### 7.2. Cobranca avulsa

```json
POST /api/v1/charges/pix
{
  "customerId": 15,
  "value": 200.00,
  "dueDate": "2026-04-20",
  "description": "Servico avulso",
  "couponCode": "DESC30REAIS"
}
```

**Resposta:** A `ChargeResponse` agora inclui campos de cupom:

```json
{
  "id": 789,
  "companyId": 1,
  "customerId": 15,
  "subscriptionId": null,
  "installmentId": null,
  "asaasId": "pay_xyz789",
  "billingType": "PIX",
  "value": 170.00,
  "dueDate": "2026-04-20",
  "status": "PENDING",
  "origin": "API",
  "externalReference": null,
  "pixQrcode": "00020126...",
  "pixCopyPaste": "00020126...",
  "boletoUrl": null,
  "invoiceUrl": "https://...",
  "installmentNumber": null,
  "couponCode": "DESC30REAIS",
  "discountAmount": 30.00,
  "originalValue": 200.00,
  "createdAt": "2026-04-10T12:00:00",
  "updatedAt": "2026-04-10T12:00:00"
}
```

> **Nota:** `value` ja e o valor com desconto. `originalValue` e o valor antes do desconto.

---

## 8. Fluxo: Pausa e Cancelamento com Cupom Ativo

### 8.1. Deteccao

Ao exibir detalhes de uma assinatura, verificar se `couponCode != null`. Se sim, mostrar badge "Cupom ativo: WELCOME50".

### 8.2. Fluxo de pausa

```
1. Usuario clica "Pausar assinatura"
2. Frontend verifica se subscription.couponCode != null
3. Se tem cupom:
   a. Primeira chamada (sem confirmacao):
      POST /api/v1/subscriptions/{id}/pause
      → API retorna erro 400:
      "A assinatura possui o cupom 'WELCOME50' aplicado.
       Ao pausar, o cupom sera removido permanentemente.
       Envie confirmCouponRemoval=true para confirmar."

   b. Frontend exibe modal de confirmacao:
      ┌─────────────────────────────────────────────────┐
      │  ⚠ Aviso: Cupom sera removido                   │
      │                                                  │
      │  A assinatura possui o cupom WELCOME50 aplicado. │
      │  Ao pausar, o cupom sera removido                │
      │  permanentemente e nao podera ser reaplicado.    │
      │                                                  │
      │  [ Cancelar ]  [ Pausar mesmo assim ]            │
      └─────────────────────────────────────────────────┘

   c. Se confirmar:
      POST /api/v1/subscriptions/{id}/pause?confirmCouponRemoval=true
      → API remove cupom, atualiza preco no Asaas, e pausa

4. Se NAO tem cupom:
   POST /api/v1/subscriptions/{id}/pause
   → Pausa normalmente
```

### 8.3. Fluxo de cancelamento

Exatamente igual ao de pausa, mas com `DELETE /api/v1/subscriptions/{id}`:

```
Sem confirmacao:
DELETE /api/v1/subscriptions/{id}
→ Erro 400 se tem cupom

Com confirmacao:
DELETE /api/v1/subscriptions/{id}?confirmCouponRemoval=true
→ Remove cupom e cancela
```

---

## 9. Exibicao de Cupom nos Detalhes

### 9.1. Detalhes da assinatura

Se `couponCode` presente:

```
┌──────────────────────────────────────────┐
│  Cupom aplicado                          │
│                                          │
│  Codigo:    FIDELIDADE20                 │
│  Desconto:  R$ 29,98 por cobranca       │
│  Cobranças restantes com desconto: 2     │
│                                          │
│  Preco cheio:  R$ 149,90 /mes           │
│  Com desconto: R$ 119,92 /mes           │
└──────────────────────────────────────────┘
```

Campos usados:
- `couponCode` → codigo exibido
- `couponDiscountAmount` → valor do desconto
- `couponUsesRemaining` → cobranças restantes (null = "Permanente")
- `effectivePrice` → preco cheio
- `effectivePrice - couponDiscountAmount` → preco com desconto

### 9.2. Detalhes da cobranca

Se `couponCode` presente:

```
Valor original:  R$ 200,00    (originalValue)
Desconto:        - R$ 30,00   (discountAmount)  [Cupom: DESC30REAIS]
Valor cobrado:   R$ 170,00    (value)
```

---

## 10. Tipos TypeScript

```typescript
// === Enums ===

type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT';
type CouponScope = 'SUBSCRIPTION' | 'CHARGE';
type CouponApplicationType = 'FIRST_CHARGE' | 'RECURRING';

// === DTOs de Cupom ===

interface CreateCouponRequest {
  code: string;                           // UPPERCASE, regex ^[A-Z0-9_-]+$
  description?: string;
  discountType: DiscountType;
  discountValue: number;                  // > 0, max 90 se PERCENTAGE
  scope: CouponScope;                     // SUBSCRIPTION ou CHARGE
  applicationType?: CouponApplicationType; // Default: FIRST_CHARGE. Ignorado se scope=CHARGE
  recurrenceMonths?: number;              // So para RECURRING
  validFrom?: string;                     // ISO datetime
  validUntil?: string;                    // ISO datetime
  maxUses?: number;
  maxUsesPerCustomer?: number;
  allowedPlans?: string;                  // JSON string: '["basico","premium"]'
  allowedCustomers?: string;              // JSON string: '[1,5,10]'
  allowedCycle?: string;                  // 'MONTHLY' | 'SEMIANNUALLY' | 'YEARLY'
}

// Sem code e scope (imutaveis)
type UpdateCouponRequest = Omit<CreateCouponRequest, 'code' | 'scope'>;

interface CouponResponse {
  id: number;
  companyId: number;
  code: string;
  description: string | null;
  discountType: DiscountType;
  discountValue: number;
  scope: CouponScope;
  applicationType: CouponApplicationType;
  recurrenceMonths: number | null;
  validFrom: string | null;
  validUntil: string | null;
  maxUses: number | null;
  maxUsesPerCustomer: number | null;
  usageCount: number;
  allowedPlans: string | null;           // JSON string - parsear com JSON.parse()
  allowedCustomers: string | null;       // JSON string - parsear com JSON.parse()
  allowedCycle: string | null;
  active: boolean;
  currentlyValid: boolean;               // Calculado pela API
  createdAt: string;
  updatedAt: string;
}

// === Validacao ===

interface ValidateCouponRequest {
  couponCode: string;
  scope: CouponScope;
  planCode?: string;                     // Obrigatorio se scope = SUBSCRIPTION
  cycle?: string;                        // Obrigatorio se scope = SUBSCRIPTION
  value?: number;                        // Para calcular preview do desconto
  customerId?: number;                   // Obrigatorio no endpoint autenticado
}

interface CouponValidationResponse {
  valid: boolean;
  message: string;
  discountType: DiscountType | null;
  applicationType: CouponApplicationType | null;
  percentualDiscount: number | null;
  discountAmount: number | null;
  originalValue: number | null;
  finalValue: number | null;
}

// === Historico de Uso ===

interface CouponUsageResponse {
  id: number;
  couponId: number;
  couponCode: string;
  customerId: number;
  subscriptionId: number | null;
  chargeId: number | null;
  originalValue: number;
  discountAmount: number;
  finalValue: number;
  planCode: string | null;
  cycle: string | null;
  usedAt: string;
}

// === Campos adicionados nos DTOs existentes ===

// Em CreateSubscriptionRequest, adicionar:
interface CreateSubscriptionRequest {
  // ... campos existentes ...
  couponCode?: string;                   // Opcional
}

// Em SubscriptionResponse, campos adicionados:
interface SubscriptionResponse {
  // ... campos existentes ...
  couponCode: string | null;
  couponDiscountAmount: number | null;
  couponUsesRemaining: number | null;    // null = permanente
}

// Em CreateChargeRequest, adicionar:
interface CreateChargeRequest {
  // ... campos existentes ...
  couponCode?: string;                   // Opcional. NAO aceito em installments.
}

// Em ChargeResponse, campos adicionados:
interface ChargeResponse {
  // ... campos existentes ...
  couponCode: string | null;
  discountAmount: number | null;
  originalValue: number | null;          // Valor antes do desconto
}
```

---

## 11. Helpers Uteis para o Frontend

```typescript
// Formatar valor do desconto para exibicao
function formatDiscount(coupon: CouponResponse): string {
  if (coupon.discountType === 'PERCENTAGE') {
    return `${coupon.discountValue}%`;
  }
  return formatPrice(coupon.discountValue);
}

// Formatar preco em BRL
function formatPrice(value: number): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL'
  }).format(value);
}

// Label do tipo de aplicacao
function applicationTypeLabel(coupon: CouponResponse): string {
  if (coupon.scope === 'CHARGE') return 'Uso unico';
  if (coupon.applicationType === 'FIRST_CHARGE') return '1a cobranca';
  if (coupon.recurrenceMonths) return `${coupon.recurrenceMonths} meses`;
  return 'Permanente';
}

// Label do escopo
function scopeLabel(scope: CouponScope): string {
  return scope === 'SUBSCRIPTION' ? 'Assinatura' : 'Cobranca avulsa';
}

// Status do cupom
function getCouponStatus(coupon: CouponResponse): {
  label: string;
  color: 'green' | 'red' | 'gray';
} {
  if (!coupon.active) return { label: 'Desativado', color: 'gray' };
  if (!coupon.currentlyValid) return { label: 'Expirado', color: 'red' };
  return { label: 'Vigente', color: 'green' };
}

// Barra de progresso de usos
function usageProgress(coupon: CouponResponse): {
  text: string;
  percent: number | null;
} {
  if (coupon.maxUses == null) {
    return { text: `${coupon.usageCount} usos`, percent: null };
  }
  return {
    text: `${coupon.usageCount} / ${coupon.maxUses}`,
    percent: (coupon.usageCount / coupon.maxUses) * 100
  };
}

// Parsear allowedPlans/allowedCustomers
function parseJsonField<T>(json: string | null): T[] {
  if (!json) return [];
  try { return JSON.parse(json); }
  catch { return []; }
}

// Calcular preview do desconto localmente (para UX rapida antes de chamar API)
function previewDiscount(
  discountType: DiscountType,
  discountValue: number,
  originalValue: number
): { discountAmount: number; finalValue: number } {
  let discount: number;
  if (discountType === 'PERCENTAGE') {
    const capped = Math.min(discountValue, 90);
    discount = Math.round(originalValue * capped / 100 * 100) / 100;
  } else {
    const maxDiscount = originalValue * 0.9;
    discount = Math.min(discountValue, maxDiscount);
  }
  const finalValue = Math.max(originalValue - discount, originalValue * 0.1);
  return {
    discountAmount: Math.round(discount * 100) / 100,
    finalValue: Math.round(finalValue * 100) / 100
  };
}

// Label do cupom na assinatura
function couponRemainingLabel(usesRemaining: number | null): string {
  if (usesRemaining == null) return 'Permanente';
  if (usesRemaining === 1) return '1 cobranca restante';
  return `${usesRemaining} cobrancas restantes`;
}
```

---

## 12. Checklist de Implementacao

### Admin

- [ ] Formulario de criacao de cupom com comportamento condicional por scope/applicationType/discountType
- [ ] Campo `code` em UPPERCASE, desabilitado na edicao
- [ ] Campo `scope` desabilitado na edicao
- [ ] Multi-select de planos permitidos (buscar via `GET /api/v1/plans`)
- [ ] Multi-select de customers permitidos (buscar via `GET /api/v1/customers`)
- [ ] Formulario de edicao (sem `code` e `scope`)
- [ ] Listagem com filtros (status, escopo, busca)
- [ ] Badges de status (Vigente/Expirado/Desativado)
- [ ] Barra de progresso de usos
- [ ] Acoes: Editar, Desativar/Reativar
- [ ] Tela de historico de uso com tabela paginada
- [ ] Botao "Desativar" com confirmacao
- [ ] Botao "Reativar"

### Checkout (Cliente)

- [ ] Componente de input de cupom no checkout de assinatura
- [ ] Componente de input de cupom no checkout de cobranca avulsa
- [ ] Validacao via endpoint publico (pre-login) ou autenticado (pos-login)
- [ ] Input UPPERCASE enquanto digita
- [ ] Exibicao do resumo do desconto apos validacao
- [ ] Revalidar ao trocar plano ou ciclo
- [ ] Botao "Remover cupom"
- [ ] Enviar `couponCode` no request de criacao de assinatura
- [ ] Enviar `couponCode` no request de criacao de cobranca avulsa
- [ ] NAO enviar `couponCode` em parcelamentos

### Detalhes e Gestao

- [ ] Exibir cupom ativo nos detalhes da assinatura
- [ ] Exibir cupom aplicado nos detalhes da cobranca
- [ ] Modal de confirmacao ao pausar assinatura com cupom
- [ ] Modal de confirmacao ao cancelar assinatura com cupom
- [ ] Enviar `?confirmCouponRemoval=true` apos confirmacao

### Tipagem

- [ ] Atualizar interfaces TypeScript para `SubscriptionResponse` (novos campos de cupom)
- [ ] Atualizar interfaces TypeScript para `ChargeResponse` (novos campos de cupom)
- [ ] Atualizar interfaces TypeScript para `CreateSubscriptionRequest` (campo `couponCode`)
- [ ] Atualizar interfaces TypeScript para `CreateChargeRequest` (campo `couponCode`)
- [ ] Criar interfaces para todos os DTOs de cupom

---

## 13. Resumo Visual das Telas

```
┌──────────────────────────────────────────────────────┐
│  1. ADMIN: Listagem de Cupons                        │
│     - Tabela paginada com filtros                    │
│     - Badges de status (Vigente/Expirado/Desativado) │
│     - Barra de progresso de usos                     │
│     - Acoes: Editar, Desativar/Reativar, Ver usos    │
├──────────────────────────────────────────────────────┤
│  2. ADMIN: Formulario de Cupom                       │
│     - Campos condicionais por scope/tipo             │
│     - Code UPPERCASE, imutavel na edicao             │
│     - Multi-select de planos e customers             │
├──────────────────────────────────────────────────────┤
│  3. ADMIN: Historico de Uso                          │
│     - Tabela paginada por cupom                      │
│     - Valor original → desconto → valor final        │
├──────────────────────────────────────────────────────┤
│  4. CHECKOUT: Input de Cupom (Assinatura)            │
│     - Validacao publica/autenticada                  │
│     - Preview do desconto                            │
│     - Revalidar ao trocar plano/ciclo                │
├──────────────────────────────────────────────────────┤
│  5. CHECKOUT: Input de Cupom (Cobranca Avulsa)       │
│     - Validacao autenticada                          │
│     - Preview do desconto                            │
│     - Uso unico por customer                         │
├──────────────────────────────────────────────────────┤
│  6. DETALHES: Assinatura com Cupom                   │
│     - Badge "Cupom ativo: WELCOME50"                 │
│     - Cobranças restantes com desconto               │
│     - Modal de aviso ao pausar/cancelar              │
├──────────────────────────────────────────────────────┤
│  7. DETALHES: Cobranca com Cupom                     │
│     - Valor original riscado                         │
│     - Desconto destacado                             │
│     - Valor final                                    │
└──────────────────────────────────────────────────────┘
```
