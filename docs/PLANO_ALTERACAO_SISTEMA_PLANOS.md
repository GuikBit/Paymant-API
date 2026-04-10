# Plano de Alteracao - Sistema de Planos

> Documento de especificacao tecnica para validacao antes da implementacao.
> Data: 2026-04-10

---

## 1. Resumo das Mudancas

O sistema atual possui um modelo de plano com **preco unico** (`value`) vinculado a um **ciclo fixo** (`cycle`).
A nova modelagem transforma o plano em um **catalogo de precos multi-ciclo**, onde o mesmo plano oferece
precos para mensal, semestral e anual, com suporte a promocoes temporais e um codigo slug imutavel.

### O que muda

| Aspecto | Hoje | Depois |
|---------|------|--------|
| Preco | Campo unico `value` | `preco_mensal` + `preco_anual` (semestral = mensal * 6) |
| Ciclo | Fixo no plano (`cycle`) | Escolhido pelo cliente na assinatura |
| Identificacao | `name` (usado no versionamento) | `codigo` (slug imutavel) + `name` (editavel) |
| Promocoes | Nao existe | Promo mensal e anual com datas inicio/fim |
| Features | JSON livre | Estrutura `[{"text":"...", "included": true/false}]` |
| Limits | JSON livre | Mesma estrutura das features |
| Desconto anual | Nao existe | `desconto_percentual_anual` (exibicao + validacao) |

### O que NAO muda

- Soft delete com bloqueio por assinaturas ativas
- Sistema de versionamento (`cloneForNewVersion`)
- Multi-tenancy via RLS
- Audit log
- `trial_days`, `setup_fee`, `tier_order`
- Campos `active`, `version`, `deleted_at`, timestamps

### O que e IGNORADO do documento original

- Campos visuais: `cor`, `gradiente`, `icone`, `badge`, `cta`, `tagline`, `descricaoCompleta`, `popular`

---

## 2. Alteracoes na Entidade Plan

### 2.1. Campos removidos

| Campo | Motivo |
|-------|--------|
| `value` (BigDecimal) | Substituido por `preco_mensal` + `preco_anual` |
| `cycle` (PlanCycle) | Move para Subscription - o cliente escolhe o ciclo na hora de assinar |

### 2.2. Campos adicionados

| Campo | Tipo | Nullable | Default | Regras |
|-------|------|----------|---------|--------|
| `codigo` | VARCHAR(50) | NOT NULL | - | Slug imutavel. Unico por company. Regex: `^[a-z][a-z0-9-]*$`. Definido na criacao, nunca editavel. Assume o papel de `name` no versionamento. |
| `preco_mensal` | NUMERIC(12,2) | NOT NULL | - | Preco base mensal. Deve ser > 0. |
| `preco_anual` | NUMERIC(12,2) | NULL | - | Preco anual. Se informado, API valida: diferenca entre `preco_anual` e `(preco_mensal * 12) * (1 - desconto/100)` deve ser <= 5%. Se estiver dentro da margem, salva o valor enviado pelo frontend. Se ultrapassar, retorna erro. |
| `desconto_percentual_anual` | NUMERIC(5,2) | NULL | - | Percentual de desconto anual (0.00 a 100.00). Usado no frontend para calcular o preco anual e para exibicao ("economize X%"). |
| `promo_mensal_ativa` | BOOLEAN | NOT NULL | false | Flag de promocao mensal ativa. |
| `promo_mensal_preco` | NUMERIC(12,2) | NULL | - | Preco promocional mensal. Obrigatorio se `promo_mensal_ativa = true`. Deve ser < `preco_mensal`. |
| `promo_mensal_texto` | VARCHAR(100) | NULL | - | Texto da promocao mensal (ex: "Oferta de lancamento!"). |
| `promo_mensal_inicio` | TIMESTAMP | NULL | - | Data/hora inicio da promo mensal. Obrigatorio se `promo_mensal_ativa = true`. |
| `promo_mensal_fim` | TIMESTAMP | NULL | - | Data/hora fim da promo mensal. Obrigatorio se `promo_mensal_ativa = true`. Deve ser > `promo_mensal_inicio`. |
| `promo_anual_ativa` | BOOLEAN | NOT NULL | false | Flag de promocao anual ativa. |
| `promo_anual_preco` | NUMERIC(12,2) | NULL | - | Preco promocional anual. Obrigatorio se `promo_anual_ativa = true`. Deve ser < `preco_anual`. |
| `promo_anual_texto` | VARCHAR(100) | NULL | - | Texto da promocao anual. |
| `promo_anual_inicio` | TIMESTAMP | NULL | - | Data/hora inicio da promo anual. |
| `promo_anual_fim` | TIMESTAMP | NULL | - | Data/hora fim da promo anual. Deve ser > `promo_anual_inicio`. |

### 2.3. Campos alterados

| Campo | Antes | Depois |
|-------|-------|--------|
| `features` | JSON livre (String) | JSONB com estrutura `[{"text": "string", "included": boolean}]` |
| `limits` | JSON livre (String) | JSONB com mesma estrutura `[{"text": "string", "included": boolean}]` |

### 2.4. Entity Java resultante (campos relevantes)

```java
@Entity
@Table(name = "plans")
public class Plan {
    // --- Identificacao ---
    Long id;
    Company company;
    String codigo;       // NOVO - slug imutavel, unico por company
    String name;         // EXISTENTE - nome de exibicao, editavel

    // --- Precos ---
    BigDecimal precoMensal;               // NOVO - substitui value
    BigDecimal precoAnual;                // NOVO - nullable
    BigDecimal descontoPercentualAnual;   // NOVO - nullable

    // --- Promo Mensal ---
    Boolean promoMensalAtiva;     // NOVO - default false
    BigDecimal promoMensalPreco;  // NOVO
    String promoMensalTexto;      // NOVO
    LocalDateTime promoMensalInicio; // NOVO
    LocalDateTime promoMensalFim;    // NOVO

    // --- Promo Anual ---
    Boolean promoAnualAtiva;      // NOVO - default false
    BigDecimal promoAnualPreco;   // NOVO
    String promoAnualTexto;       // NOVO
    LocalDateTime promoAnualInicio; // NOVO
    LocalDateTime promoAnualFim;    // NOVO

    // --- Configuracao (mantidos) ---
    String description;
    Integer trialDays;
    BigDecimal setupFee;
    Boolean active;
    Integer version;
    String limits;      // JSONB - nova estrutura
    String features;    // JSONB - nova estrutura
    Integer tierOrder;

    // --- Controle (mantidos) ---
    LocalDateTime deletedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
```

---

## 3. Alteracoes na Entidade Subscription

### 3.1. Campo adicionado

| Campo | Tipo | Nullable | Regras |
|-------|------|----------|--------|
| `cycle` | VARCHAR(20) / PlanCycle enum | NOT NULL | MONTHLY, SEMIANNUALLY, YEARLY. Escolhido pelo cliente na criacao da assinatura. |
| `effective_price` | NUMERIC(12,2) | NOT NULL | Preco efetivo que o cliente paga neste ciclo. Calculado no momento da assinatura com base no plano + ciclo + promo vigente. Fica "congelado" na subscription para historico. |

### 3.2. Logica de calculo do preco efetivo na criacao da assinatura

```
Se cycle == MONTHLY:
    Se plano.promoMensalAtiva == true
       E agora >= plano.promoMensalInicio
       E agora <= plano.promoMensalFim:
        effectivePrice = plano.promoMensalPreco
    Senao:
        effectivePrice = plano.precoMensal

Se cycle == SEMIANNUALLY:
    precoBase = (preco mensal efetivo, considerando promo mensal se ativa) * 6
    effectivePrice = precoBase

Se cycle == YEARLY:
    Se plano.promoAnualAtiva == true
       E agora >= plano.promoAnualInicio
       E agora <= plano.promoAnualFim:
        effectivePrice = plano.promoAnualPreco
    Senao:
        effectivePrice = plano.precoAnual
        Se plano.precoAnual == null:
            effectivePrice = plano.precoMensal * 12
            Se plano.descontoPercentualAnual != null:
                effectivePrice = effectivePrice * (1 - desconto/100)
```

> **Nota:** O semestral usa o preco mensal (com promo se aplicavel) * 6, sem desconto proprio.

---

## 4. Alteracoes no PlanCycle Enum

```java
public enum PlanCycle {
    MONTHLY,       // Mantido
    SEMIANNUALLY,  // Mantido
    YEARLY         // Mantido (substitui QUARTERLY que nao sera mais usado)
}
```

> **Decisao:** Remover `QUARTERLY` do enum. Se existirem dados com QUARTERLY no banco, criar migration de dados para migrar para SEMIANNUALLY ou tratar como legado.

---

## 5. Alteracoes no PlanService

### 5.1. create()

- Validar `codigo`: regex `^[a-z][a-z0-9-]*$`, unico por `company_id`
- Validar `preco_mensal` > 0
- Se `preco_anual` informado, validar margem de 5% contra calculo `(precoMensal * 12) * (1 - descontoAnual/100)`
- Se `promo_mensal_ativa == true`, exigir `promo_mensal_preco`, `promo_mensal_inicio`, `promo_mensal_fim`
- Se `promo_anual_ativa == true`, exigir `promo_anual_preco`, `promo_anual_inicio`, `promo_anual_fim`
- Validar `promo_mensal_preco < preco_mensal` e `promo_anual_preco < preco_anual`
- Validar `promo_*_fim > promo_*_inicio`

### 5.2. update()

- `codigo` NAO pode ser alterado (ignorar ou retornar erro se enviado diferente)
- Demais campos seguem mesmas validacoes do create
- Se alterar `promo_mensal_ativa` para false, limpar campos de promo mensal (ou manter para historico?)
  - **Recomendacao:** Manter os valores, apenas setar `ativa = false`. Permite reativar sem recriar.

### 5.3. cloneForNewVersion()

- Buscar por `company_id + codigo` (em vez de `company_id + name`)
- Novo plano herda `codigo` do original (imutavel, mesmo slug)
- Demais campos podem ser sobrescritos

### 5.4. Novo metodo: getEffectivePrice(Plan plan, PlanCycle cycle)

Metodo utilitario que calcula o preco efetivo dado um plano e um ciclo escolhido, considerando promos vigentes.
Sera usado pela SubscriptionService na criacao e pelo PlanChangeService na proracao.

```java
public BigDecimal getEffectivePrice(Plan plan, PlanCycle cycle) {
    LocalDateTime now = LocalDateTime.now();

    return switch (cycle) {
        case MONTHLY -> {
            if (isPromoActive(plan.getPromoMensalAtiva(), plan.getPromoMensalInicio(), plan.getPromoMensalFim(), now)) {
                yield plan.getPromoMensalPreco();
            }
            yield plan.getPrecoMensal();
        }
        case SEMIANNUALLY -> {
            BigDecimal monthlyPrice;
            if (isPromoActive(plan.getPromoMensalAtiva(), plan.getPromoMensalInicio(), plan.getPromoMensalFim(), now)) {
                monthlyPrice = plan.getPromoMensalPreco();
            } else {
                monthlyPrice = plan.getPrecoMensal();
            }
            yield monthlyPrice.multiply(BigDecimal.valueOf(6));
        }
        case YEARLY -> {
            if (isPromoActive(plan.getPromoAnualAtiva(), plan.getPromoAnualInicio(), plan.getPromoAnualFim(), now)) {
                yield plan.getPromoAnualPreco();
            }
            if (plan.getPrecoAnual() != null) {
                yield plan.getPrecoAnual();
            }
            // Fallback: calcula a partir do mensal
            BigDecimal anual = plan.getPrecoMensal().multiply(BigDecimal.valueOf(12));
            if (plan.getDescontoPercentualAnual() != null) {
                BigDecimal fator = BigDecimal.ONE.subtract(
                    plan.getDescontoPercentualAnual().divide(BigDecimal.valueOf(100))
                );
                anual = anual.multiply(fator);
            }
            yield anual;
        }
    };
}
```

---

## 6. Alteracoes no ProrationCalculator

### 6.1. Problema atual

O calculador recebe `currentPlanValue` e `newPlanValue` como BigDecimal simples. Funciona quando ambos
os planos tem o mesmo ciclo. Com ciclos diferentes (mensal -> anual), os valores nao sao comparaveis diretamente.

### 6.2. Solucao: normalizar para valor diario

Para comparar planos de ciclos diferentes, normalizar ambos para um **valor diario**:

```
MONTHLY:      valorDiario = preco / 30
SEMIANNUALLY: valorDiario = preco / 180
YEARLY:       valorDiario = preco / 365
```

### 6.3. Nova assinatura do metodo

```java
public static ProrationResult calculate(
    BigDecimal currentEffectivePrice,  // preco efetivo atual (ja com promo se aplicavel)
    PlanCycle currentCycle,            // NOVO - ciclo atual
    BigDecimal newEffectivePrice,      // preco efetivo novo
    PlanCycle newCycle,                // NOVO - ciclo novo
    LocalDate periodStart,
    LocalDate periodEnd,
    LocalDate changeDate
)
```

### 6.4. Logica de calculo com mudanca de ciclo

```
Exemplo: Cliente paga R$100/mes, usou 15 de 30 dias. Quer mudar para anual R$1.000/ano.

1. Credito nao utilizado do ciclo atual:
   creditUnused = R$100 * (15 / 30) = R$50.00

2. Custo do novo ciclo (YEARLY):
   newCost = R$1.000 (valor total do novo ciclo anual)

3. Delta:
   delta = newCost - creditUnused = R$1.000 - R$50 = R$950.00

4. Resultado: prorationCharge = R$950.00
```

> **Nota:** O novo periodo comeca a partir da `changeDate` com duracao completa do novo ciclo.
> Nao se faz proporcionalidade no novo ciclo - o cliente paga o ciclo inteiro e recebe credito do ciclo anterior nao utilizado.

### 6.5. Backward compatibility

O metodo antigo (sem cycles) pode ser mantido como overload que assume mesmo ciclo para ambos.

---

## 7. Alteracoes no SubscriptionPlanChange

### 7.1. Campos adicionados

| Campo | Tipo | Motivo |
|-------|------|--------|
| `previous_cycle` | VARCHAR(20) | Registrar o ciclo anterior para historico/auditoria |
| `requested_cycle` | VARCHAR(20) | Registrar o ciclo solicitado |

### 7.2. Tipos de mudanca expandidos

Hoje o `PlanChangeType` e determinado apenas pela comparacao de valor. Com ciclos diferentes, a logica fica:

```
- Mesmo plano, ciclo diferente:    CYCLE_CHANGE (novo valor no enum)
- Plano diferente, preco maior:    UPGRADE
- Plano diferente, preco menor:    DOWNGRADE
- Plano diferente, preco igual:    SIDEGRADE
```

> Para comparacao de preco entre ciclos diferentes, usar o **valor diario normalizado**.

---

## 8. Migration SQL (V3)

```sql
-- V3__plan_pricing_overhaul.sql

-- 1. Adicionar novos campos ao plano
ALTER TABLE plans ADD COLUMN codigo VARCHAR(50);
ALTER TABLE plans ADD COLUMN preco_mensal NUMERIC(12,2);
ALTER TABLE plans ADD COLUMN preco_anual NUMERIC(12,2);
ALTER TABLE plans ADD COLUMN desconto_percentual_anual NUMERIC(5,2);

ALTER TABLE plans ADD COLUMN promo_mensal_ativa BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE plans ADD COLUMN promo_mensal_preco NUMERIC(12,2);
ALTER TABLE plans ADD COLUMN promo_mensal_texto VARCHAR(100);
ALTER TABLE plans ADD COLUMN promo_mensal_inicio TIMESTAMP;
ALTER TABLE plans ADD COLUMN promo_mensal_fim TIMESTAMP;

ALTER TABLE plans ADD COLUMN promo_anual_ativa BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE plans ADD COLUMN promo_anual_preco NUMERIC(12,2);
ALTER TABLE plans ADD COLUMN promo_anual_texto VARCHAR(100);
ALTER TABLE plans ADD COLUMN promo_anual_inicio TIMESTAMP;
ALTER TABLE plans ADD COLUMN promo_anual_fim TIMESTAMP;

-- 2. Migrar dados existentes: value -> preco_mensal, gerar codigo a partir do name
UPDATE plans SET
    preco_mensal = value,
    preco_anual = CASE
        WHEN cycle = 'YEARLY' THEN value
        ELSE value * 12
    END,
    codigo = LOWER(REGEXP_REPLACE(REPLACE(name, ' ', '-'), '[^a-z0-9-]', '', 'g'))
WHERE preco_mensal IS NULL;

-- 3. Tornar campos obrigatorios apos migracao de dados
ALTER TABLE plans ALTER COLUMN codigo SET NOT NULL;
ALTER TABLE plans ALTER COLUMN preco_mensal SET NOT NULL;

-- 4. Criar constraint de unicidade: codigo unico por company
CREATE UNIQUE INDEX idx_plans_company_codigo ON plans(company_id, codigo) WHERE deleted_at IS NULL;

-- 5. Remover campos antigos
ALTER TABLE plans DROP COLUMN value;
ALTER TABLE plans DROP COLUMN cycle;

-- 6. Adicionar cycle e effective_price na subscription
ALTER TABLE subscriptions ADD COLUMN cycle VARCHAR(20);
ALTER TABLE subscriptions ADD COLUMN effective_price NUMERIC(12,2);

-- 7. Migrar dados da subscription: herdar cycle do plano antigo
-- (rodar ANTES de dropar cycle do plans, ou usar backup)
-- Nota: esta UPDATE precisa ser feita antes do DROP COLUMN cycle na tabela plans.
-- Se a migration for executada em ordem, mover os passos 6-7 para antes do passo 5.

-- 8. Adicionar campos no plan_change
ALTER TABLE subscription_plan_changes ADD COLUMN previous_cycle VARCHAR(20);
ALTER TABLE subscription_plan_changes ADD COLUMN requested_cycle VARCHAR(20);

-- 9. Remover QUARTERLY do enum (se existir dados)
-- UPDATE plans SET cycle = 'SEMIANNUALLY' WHERE cycle = 'QUARTERLY'; (ja removido cycle do plans)
UPDATE subscriptions SET cycle = 'SEMIANNUALLY' WHERE cycle = 'QUARTERLY';
```

> **IMPORTANTE:** A ordem dos passos 5, 6, 7 precisa ser ajustada na implementacao real.
> O cycle deve ser migrado para subscriptions ANTES de ser removido de plans.

---

## 9. Validacao do preco anual (regra dos 5%)

Quando o frontend envia `preco_anual`, a API verifica:

```java
BigDecimal calculado = precoMensal.multiply(BigDecimal.valueOf(12));
if (descontoPercentualAnual != null) {
    calculado = calculado.multiply(
        BigDecimal.ONE.subtract(descontoPercentualAnual.divide(BigDecimal.valueOf(100)))
    );
}

BigDecimal diferenca = precoAnual.subtract(calculado).abs();
BigDecimal margemPermitida = calculado.multiply(new BigDecimal("0.05"));

if (diferenca.compareTo(margemPermitida) > 0) {
    throw new BusinessException(
        "Preco anual informado (R$ " + precoAnual +
        ") diverge mais de 5% do valor calculado (R$ " + calculado +
        "). Verifique os valores."
    );
}
// Se dentro da margem, salva o valor do frontend
```

---

## 10. Alteracoes nos DTOs

### 10.1. CreatePlanRequest (novo)

```java
public record CreatePlanRequest(
    @NotBlank String codigo,                      // NOVO - obrigatorio
    @NotBlank String name,
    String description,
    @NotNull @Positive BigDecimal precoMensal,    // substitui value
    BigDecimal precoAnual,                         // opcional
    BigDecimal descontoPercentualAnual,            // opcional

    // Promo mensal
    Boolean promoMensalAtiva,
    BigDecimal promoMensalPreco,
    String promoMensalTexto,
    LocalDateTime promoMensalInicio,
    LocalDateTime promoMensalFim,

    // Promo anual
    Boolean promoAnualAtiva,
    BigDecimal promoAnualPreco,
    String promoAnualTexto,
    LocalDateTime promoAnualInicio,
    LocalDateTime promoAnualFim,

    // Config (mantidos)
    Integer trialDays,
    BigDecimal setupFee,
    String limits,                                // JSONB nova estrutura
    String features,                              // JSONB nova estrutura
    Integer tierOrder
) {}
```

### 10.2. UpdatePlanRequest (novo)

Todos os campos opcionais (null = nao alterar). **`codigo` NAO aparece aqui** (imutavel).

### 10.3. PlanResponse (novo)

Inclui todos os campos novos. Remove `value` e `cycle`. Adiciona campo calculado:

```java
BigDecimal precoSemestral    // calculado: precoMensal (ou promoMensal se ativa) * 6
```

### 10.4. CreateSubscriptionRequest (alteracao)

Adicionar campo obrigatorio:

```java
@NotNull PlanCycle cycle   // MONTHLY, SEMIANNUALLY, YEARLY
```

---

## 11. Alteracoes no PlanRepository

```java
// Substituir findMaxVersionByCompanyAndName por:
@Query("SELECT MAX(p.version) FROM Plan p WHERE p.company.id = :companyId AND p.codigo = :codigo")
Optional<Integer> findMaxVersionByCompanyAndCodigo(Long companyId, String codigo);

// Substituir existsByNameAndCompanyId por:
boolean existsByCodigoAndCompanyId(String codigo, Long companyId);

// Novo: buscar plano ativo por codigo
Optional<Plan> findByCodigoAndCompanyIdAndActiveTrue(String codigo, Long companyId);
```

---

## 12. Endpoint novo: GET /api/v1/plans/{codigo}/pricing

Endpoint publico (ou semi-publico) que retorna o pricing completo de um plano para o frontend montar o card:

```json
{
  "codigo": "premium",
  "name": "Premium",
  "precoMensal": 99.90,
  "precoSemestral": 599.40,
  "precoAnual": 959.04,
  "descontoPercentualAnual": 20.0,
  "promoMensal": {
    "ativa": true,
    "preco": 79.90,
    "texto": "Oferta de lancamento!",
    "validaAte": "2026-05-01T23:59:59"
  },
  "promoAnual": {
    "ativa": false
  },
  "features": [
    {"text": "Ate 10 usuarios", "included": true},
    {"text": "Suporte prioritario", "included": true},
    {"text": "API ilimitada", "included": false}
  ],
  "limits": [
    {"text": "10 usuarios", "included": true},
    {"text": "1000 requisicoes/dia", "included": true}
  ]
}
```

---

## 13. Impacto em outros modulos

| Modulo | Impacto | Alteracao necessaria |
|--------|---------|---------------------|
| **SubscriptionService** | Criacao de assinatura precisa receber `cycle` e calcular `effective_price` | Alterar `createSubscription()` para usar `getEffectivePrice()` |
| **PlanChangeService** | Proracao com ciclos diferentes | Alterar para passar cycles ao `ProrationCalculator` |
| **ScheduledPlanChangeJob** | Idem | Idem |
| **AsaasGatewayService** | Enviar preco correto ao Asaas baseado no cycle escolhido | Alterar mapeamento de criacao de subscription |
| **AsaasMapper** | Mapear cycle para o campo equivalente do Asaas | Verificar campos do Asaas |
| **WebhookProcessor** | Sem impacto direto | - |
| **OverdueSubscriptionJob** | Sem impacto direto | - |
| **Outbox/Idempotency** | Sem impacto | - |

---

## 14. Ordem de implementacao sugerida

| Etapa | Descricao | Dependencias |
|-------|-----------|--------------|
| 1 | Migration SQL V3 (novos campos, migracao dados, remocao campos antigos) | Nenhuma |
| 2 | Atualizar entidade Plan.java (novos campos, remover value/cycle) | Etapa 1 |
| 3 | Atualizar PlanCycle enum (remover QUARTERLY) | Etapa 2 |
| 4 | Criar DTOs novos (CreatePlanRequest, UpdatePlanRequest, PlanResponse) | Etapa 2 |
| 5 | Implementar validacoes no PlanService (codigo, precos, promos, margem 5%) | Etapas 2-4 |
| 6 | Implementar `getEffectivePrice()` | Etapa 5 |
| 7 | Atualizar PlanRepository (queries por codigo) | Etapa 2 |
| 8 | Atualizar PlanController + endpoint de pricing | Etapas 4-7 |
| 9 | Atualizar Subscription entity (adicionar cycle, effective_price) | Etapa 1 |
| 10 | Atualizar SubscriptionService (receber cycle, calcular preco) | Etapas 6, 9 |
| 11 | Atualizar ProrationCalculator (suporte a ciclos diferentes) | Etapa 6 |
| 12 | Atualizar PlanChangeService e SubscriptionPlanChange entity | Etapa 11 |
| 13 | Atualizar integracao Asaas (enviar preco/cycle corretos) | Etapas 10, 12 |
| 14 | Testes unitarios e de integracao | Todas |

---

## 15. Riscos e pontos de atencao

1. **Migracao de dados:** Planos existentes com `cycle=QUARTERLY` precisam de tratamento especial. Verificar se existem assinaturas ativas com QUARTERLY antes de remover.

2. **Preco semestral sem desconto proprio:** O semestral e sempre `mensal * 6` (com promo se ativa). Se no futuro quiser desconto semestral, sera necessario adicionar campos.

3. **Promo expirada:** Promos com data passada continuam no banco com `ativa = true`. Opcoes:
   - Job agendado para desativar promos expiradas (recomendado)
   - Validar sempre no `getEffectivePrice()` pela data (ja previsto na logica acima)
   - **Recomendacao:** Ambos. O job limpa, mas o calculo valida por seguranca.

4. **Versioning com codigo:** O `cloneForNewVersion` agora busca por `codigo` em vez de `name`. Como `codigo` e imutavel e unico por company, isso e mais seguro que o modelo atual.

5. **Preco anual NULL:** Se o operador nao informar `preco_anual`, o sistema calcula `mensal * 12 - desconto`. Se tambem nao informar desconto, `preco_anual = mensal * 12`. O frontend pode exibir "R$ X/ano" mesmo sem o campo preenchido.

6. **Mudanca de ciclo (mensal -> anual) no PlanChange:** O `previousPlan` e `requestedPlan` podem ser o MESMO plano. O que muda e o `previous_cycle` e `requested_cycle`. O tipo seria `CYCLE_CHANGE`.
