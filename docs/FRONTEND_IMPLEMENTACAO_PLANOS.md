# Guia de Implementacao Frontend - Novo Sistema de Planos

> Documento tecnico para o time de frontend implementar as telas de gestao de planos,
> pricing page e fluxo de assinatura com base nas mudancas da API.
>
> Data: 2026-04-10

---

## 1. Resumo das Mudancas na API

O sistema de planos foi reestruturado. As principais mudancas que impactam o frontend:

- **Preco unico (`value`) foi substituido** por `precoMensal` + `precoAnual`
- **Ciclo (`cycle`) saiu do plano** e agora e escolhido pelo cliente na hora de assinar
- **Novo campo `codigo`** (slug imutavel) para identificar planos programaticamente
- **Promocoes com datas** para precos mensal e anual
- **Desconto percentual anual** para calculo e exibicao de economia
- **Features e limites** com nova estrutura JSON para exibicao nos cards

---

## 2. Endpoints da API

### 2.1. Gestao de Planos (Admin)

| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| `POST` | `/api/v1/plans` | Criar plano |
| `GET` | `/api/v1/plans` | Listar planos (paginado) |
| `GET` | `/api/v1/plans/{id}` | Buscar por ID |
| `GET` | `/api/v1/plans/codigo/{codigo}` | Buscar por codigo (slug) |
| `PUT` | `/api/v1/plans/{id}` | Atualizar plano |
| `PATCH` | `/api/v1/plans/{id}/activate` | Ativar plano |
| `PATCH` | `/api/v1/plans/{id}/deactivate` | Desativar plano |
| `DELETE` | `/api/v1/plans/{id}` | Soft delete |
| `POST` | `/api/v1/plans/{id}/new-version` | Criar nova versao |

### 2.2. Pricing (Publico/Semi-publico)

| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| `GET` | `/api/v1/plans/{id}/pricing` | Precos efetivos com promos aplicadas |

### 2.3. Assinatura

| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| `POST` | `/api/v1/subscriptions` | Criar assinatura (agora recebe `cycle`) |

---

## 3. Tela: Formulario de Criacao/Edicao de Plano (Admin)

### 3.1. Campos do formulario

#### Identificacao

| Campo | Tipo Input | Obrigatorio | Regras de Validacao | Notas |
|-------|-----------|-------------|---------------------|-------|
| `codigo` | `text` | Sim (criacao) | Regex: `^[a-z][a-z0-9-]*$`. Max 50 chars. | **Somente na criacao.** Desabilitar no formulario de edicao. Usar como slug (ex: `plano-basico`, `premium-plus`). Nao aceita maiusculas, espacos ou caracteres especiais. |
| `name` | `text` | Sim | Max 255 chars. | Nome de exibicao. Editavel a qualquer momento. |
| `description` | `textarea` | Nao | Sem limite pratico (TEXT). | Descricao do plano. |

#### Precos

| Campo | Tipo Input | Obrigatorio | Regras de Validacao | Notas |
|-------|-----------|-------------|---------------------|-------|
| `precoMensal` | `number` (decimal) | Sim | Deve ser > 0. | Preco base do plano. Todos os outros precos derivam deste. |
| `descontoPercentualAnual` | `number` (decimal) | Nao | 0 a 100. | Ao preencher, recalcular `precoAnual` automaticamente. |
| `precoAnual` | `number` (decimal) | Nao | Validado pela API com margem de 5%. | Pode ser preenchido manualmente ou calculado. Ver regra de calculo abaixo. |

**Regra de calculo do preco anual no frontend:**

```
precoAnual = precoMensal * 12

Se descontoPercentualAnual preenchido:
    precoAnual = (precoMensal * 12) * (1 - descontoPercentualAnual / 100)
```

**Comportamento esperado dos campos:**

1. Usuario preenche `precoMensal` = 100
2. Campo `precoAnual` calcula automaticamente: 100 * 12 = **1.200,00**
3. Usuario preenche `descontoPercentualAnual` = 20
4. Campo `precoAnual` recalcula: 1.200 * (1 - 0.20) = **960,00**
5. Usuario pode ajustar `precoAnual` manualmente (ex: 950,00)
6. A API aceita se a diferenca for <= 5% do valor calculado. Se nao, retorna erro.

> **Importante:** O campo `precoAnual` deve ser editavel mesmo apos o calculo automatico.
> O calculo automatico e uma sugestao, nao uma trava.

#### Preco Semestral

O preco semestral **nao e um campo do formulario**. Ele e calculado pela API:
- `precoSemestral = precoMensal * 6` (ou `promoMensalPreco * 6` se promo ativa)
- Exibido apenas na resposta do plano e no pricing.

#### Promocao Mensal

| Campo | Tipo Input | Obrigatorio | Regras de Validacao | Notas |
|-------|-----------|-------------|---------------------|-------|
| `promoMensalAtiva` | `toggle/switch` | Nao | Default: false | Ao ativar, exibir os campos abaixo. Ao desativar, manter valores (nao limpar). |
| `promoMensalPreco` | `number` (decimal) | Sim (se ativa) | Deve ser < `precoMensal`. | Preco promocional. |
| `promoMensalTexto` | `text` | Nao | Max 100 chars. | Ex: "Oferta de lancamento!", "Black Friday". |
| `promoMensalInicio` | `datetime-local` | Sim (se ativa) | - | Data/hora de inicio da promocao. |
| `promoMensalFim` | `datetime-local` | Sim (se ativa) | Deve ser > `promoMensalInicio`. | Data/hora de fim da promocao. |

#### Promocao Anual

| Campo | Tipo Input | Obrigatorio | Regras de Validacao | Notas |
|-------|-----------|-------------|---------------------|-------|
| `promoAnualAtiva` | `toggle/switch` | Nao | Default: false. Requer `precoAnual` preenchido. | Ao ativar, exibir os campos abaixo. |
| `promoAnualPreco` | `number` (decimal) | Sim (se ativa) | Deve ser < `precoAnual`. | Preco promocional anual. |
| `promoAnualTexto` | `text` | Nao | Max 100 chars. | Texto da promo anual. |
| `promoAnualInicio` | `datetime-local` | Sim (se ativa) | - | Data/hora de inicio. |
| `promoAnualFim` | `datetime-local` | Sim (se ativa) | Deve ser > `promoAnualInicio`. | Data/hora de fim. |

#### Configuracao

| Campo | Tipo Input | Obrigatorio | Regras de Validacao | Notas |
|-------|-----------|-------------|---------------------|-------|
| `trialDays` | `number` (inteiro) | Nao | >= 0. Default: 0. | Dias de trial gratuito. |
| `setupFee` | `number` (decimal) | Nao | >= 0. Default: 0.00. | Taxa de adesao. |
| `tierOrder` | `number` (inteiro) | Nao | Default: 0. | Ordem de exibicao na pricing page. Menor = primeiro. |

#### Features e Limites

| Campo | Tipo Input | Obrigatorio | Regras de Validacao | Notas |
|-------|-----------|-------------|---------------------|-------|
| `features` | Lista dinamica | Nao | JSON: `[{"text":"...", "included": true/false}]` | Cada item tem um `text` (string) e `included` (checkbox). |
| `limits` | Lista dinamica | Nao | JSON: `[{"text":"...", "included": true/false}]` | Mesma estrutura das features. |

**Componente sugerido para features/limits:**

Criar um componente de lista dinamica onde cada linha tem:
- Input de texto (descricao da feature/limite)
- Checkbox ou toggle (included: sim/nao)
- Botao de remover linha
- Botao "Adicionar" no final da lista
- Drag-and-drop para reordenar (opcional)

Exemplo visual:
```
[x] Ate 10 usuarios              [Remover]
[x] Suporte prioritario          [Remover]
[ ] API ilimitada                 [Remover]
[x] Relatorios avancados          [Remover]
         [+ Adicionar feature]
```

O JSON enviado para a API:
```json
[
  {"text": "Ate 10 usuarios", "included": true},
  {"text": "Suporte prioritario", "included": true},
  {"text": "API ilimitada", "included": false},
  {"text": "Relatorios avancados", "included": true}
]
```

> Os itens com `included: false` aparecem riscados no card de pricing (feature que o plano NAO tem).

### 3.2. Payload de criacao (POST /api/v1/plans)

```json
{
  "codigo": "premium-plus",
  "name": "Premium Plus",
  "description": "Plano completo para empresas em crescimento",
  "precoMensal": 199.90,
  "precoAnual": 1919.04,
  "descontoPercentualAnual": 20.0,
  "promoMensalAtiva": true,
  "promoMensalPreco": 149.90,
  "promoMensalTexto": "Oferta de lancamento!",
  "promoMensalInicio": "2026-04-10T00:00:00",
  "promoMensalFim": "2026-05-10T23:59:59",
  "promoAnualAtiva": false,
  "promoAnualPreco": null,
  "promoAnualTexto": null,
  "promoAnualInicio": null,
  "promoAnualFim": null,
  "trialDays": 7,
  "setupFee": 0,
  "tierOrder": 2,
  "features": "[{\"text\":\"Ate 50 usuarios\",\"included\":true},{\"text\":\"API ilimitada\",\"included\":true},{\"text\":\"Suporte 24/7\",\"included\":false}]",
  "limits": "[{\"text\":\"50 usuarios\",\"included\":true},{\"text\":\"10.000 requisicoes/dia\",\"included\":true}]"
}
```

> **Atencao:** `features` e `limits` sao enviados como **string JSON** (stringify do array), nao como array direto.

### 3.3. Payload de edicao (PUT /api/v1/plans/{id})

Mesma estrutura, **sem o campo `codigo`** (imutavel). Apenas os campos preenchidos sao atualizados (campos `null` sao ignorados pela API).

### 3.4. Erros de validacao da API

| Erro | Quando ocorre | Como tratar no frontend |
|------|---------------|------------------------|
| `"Ja existe um plano com o codigo 'xxx' para esta empresa."` | Codigo duplicado na criacao | Mostrar erro no campo `codigo` |
| `"Preco promocional mensal (X) deve ser menor que o preco mensal (Y)."` | Promo >= preco base | Mostrar erro no campo `promoMensalPreco` |
| `"Data de fim da promocao mensal deve ser posterior a data de inicio."` | Fim <= inicio | Mostrar erro nos campos de data |
| `"Preco anual deve estar definido quando a promocao anual esta ativa."` | Promo anual sem preco anual | Mostrar aviso para preencher `precoAnual` primeiro |
| `"Preco anual (X) esta fora da margem de 5% do valor esperado (Y)."` | Preco anual diverge do calculado | Mostrar erro no campo `precoAnual` com o valor esperado |

---

## 4. Tela: Pricing Page (Cliente Final)

### 4.1. Endpoint recomendado

Usar `GET /api/v1/plans/{id}/pricing` para cada plano, ou `GET /api/v1/plans` para listar todos e calcular no frontend.

**Resposta do endpoint de pricing:**

```json
{
  "id": 1,
  "codigo": "premium-plus",
  "name": "Premium Plus",
  "precoMensal": 149.90,
  "precoSemestral": 899.40,
  "precoAnual": 1919.04,
  "descontoPercentualAnual": 20.0,
  "promoMensal": {
    "ativa": true,
    "preco": 149.90,
    "texto": "Oferta de lancamento!",
    "validaAte": "2026-05-10T23:59:59"
  },
  "promoAnual": {
    "ativa": false,
    "preco": null,
    "texto": null,
    "validaAte": null
  },
  "features": "[{\"text\":\"Ate 50 usuarios\",\"included\":true},{\"text\":\"API ilimitada\",\"included\":true},{\"text\":\"Suporte 24/7\",\"included\":false}]",
  "limits": "[{\"text\":\"50 usuarios\",\"included\":true},{\"text\":\"10.000 requisicoes/dia\",\"included\":true}]"
}
```

> **Nota:** Os precos no endpoint de pricing ja consideram promocoes vigentes.
> `precoMensal` no pricing = preco promocional SE a promo estiver ativa e dentro do periodo.

### 4.2. Seletor de ciclo (Toggle Mensal / Semestral / Anual)

A pricing page deve ter um seletor de ciclo no topo:

```
[ Mensal ]  [ Semestral ]  [ Anual - Economize 20%! ]
```

Ao trocar o ciclo, os cards atualizam o preco exibido:

| Ciclo selecionado | Preco exibido no card | Origem |
|--------------------|----------------------|--------|
| Mensal | `precoMensal` (ou `promoMensal.preco` se ativa) | Direto da API |
| Semestral | `precoSemestral` | Direto da API (ja calculado) |
| Anual | `precoAnual` (ou `promoAnual.preco` se ativa) | Direto da API |

**Texto de economia no toggle anual:**
```
Se descontoPercentualAnual != null:
    "Economize {descontoPercentualAnual}%"
```

### 4.3. Card do plano

Estrutura sugerida do card:

```
┌─────────────────────────────┐
│        Premium Plus         │  ← name
│                             │
│    R$ 149,90 /mes           │  ← preco do ciclo selecionado
│    ~~R$ 199,90~~            │  ← preco original (se promo ativa, riscado)
│    Oferta de lancamento!    │  ← promoMensal.texto (se promo ativa)
│                             │
│    [ Assinar agora ]        │  ← CTA
│                             │
│  ✓ Ate 50 usuarios          │  ← feature included: true
│  ✓ API ilimitada            │  ← feature included: true
│  ✗ Suporte 24/7             │  ← feature included: false (riscado/cinza)
│                             │
└─────────────────────────────┘
```

**Regras de exibicao:**

1. **Preco riscado:** Mostrar somente quando ha promo ativa para o ciclo selecionado
   - Mensal: se `promoMensal.ativa == true` → riscar `precoMensal` original, mostrar `promoMensal.preco`
   - Anual: se `promoAnual.ativa == true` → riscar `precoAnual` original, mostrar `promoAnual.preco`
   - Semestral: se promo mensal ativa → o `precoSemestral` ja reflete a promo (API calcula)

2. **Texto da promo:** Exibir `promoMensal.texto` ou `promoAnual.texto` conforme o ciclo

3. **Countdown da promo (opcional):** `promoMensal.validaAte` ou `promoAnual.validaAte` pode ser usado para exibir "Faltam X dias!"

4. **Features:** Parsear o JSON `features`, iterar e exibir:
   - `included: true` → icone check verde + texto normal
   - `included: false` → icone X vermelho + texto riscado/cinza

5. **Limites:** Mesma logica das features, pode ser exibido em secao separada ou junto

6. **Ordenacao:** Usar `tierOrder` para ordernar os cards (menor = primeiro/esquerda)

### 4.4. Uso do campo `codigo` no frontend

O `codigo` serve como identificador estavel para:
- Aplicar estilos especificos por plano (ex: `plan-card--premium-plus`)
- Deep links: `/planos/premium-plus`
- Feature flags condicionais: `if (plan.codigo === 'enterprise') showCustomBanner()`
- Analytics: rastrear conversao por plano

---

## 5. Tela: Fluxo de Assinatura

### 5.1. Mudanca no request de criacao de assinatura

**Antes:** A API determinava o ciclo a partir do plano.
**Agora:** O frontend DEVE enviar o campo `cycle` escolhido pelo cliente.

**Valores aceitos para `cycle`:**
- `MONTHLY` - Mensal
- `SEMIANNUALLY` - Semestral
- `YEARLY` - Anual

**Payload de criacao:**

```json
{
  "customerId": 123,
  "planId": 1,
  "billingType": "PIX",
  "cycle": "MONTHLY",
  "nextDueDate": "2026-04-15",
  "description": "Assinatura Premium Plus",
  "externalReference": "ref-123"
}
```

### 5.2. Resposta da assinatura

A resposta agora inclui `effectivePrice` (preco efetivo congelado) e `cycle`:

```json
{
  "id": 456,
  "companyId": 1,
  "customerId": 123,
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
  "createdAt": "2026-04-10T12:00:00",
  "updatedAt": "2026-04-10T12:00:00"
}
```

> **Nota:** `effectivePrice` e o preco que o cliente paga naquele ciclo, congelado no momento
> da assinatura. Se o plano mudar de preco depois, o `effectivePrice` da assinatura existente NAO muda.

### 5.3. Fluxo do usuario na tela de assinatura

```
1. Pricing page: usuario ve os planos e seleciona um ciclo (Mensal/Semestral/Anual)
2. Clica em "Assinar" em um card
3. Tela de checkout:
   - Exibe resumo: nome do plano, ciclo escolhido, preco efetivo
   - Seleciona forma de pagamento (PIX, Boleto, Cartao)
   - Confirma
4. Frontend envia POST /api/v1/subscriptions com:
   - planId (do plano escolhido)
   - cycle (do toggle selecionado: MONTHLY, SEMIANNUALLY ou YEARLY)
   - billingType (forma de pagamento)
5. API calcula o effectivePrice e cria a assinatura
```

---

## 6. Tela: Mudanca de Plano

### 6.1. Impacto visual

Na tela de mudanca de plano do cliente, agora e possivel:
- Trocar de plano (mesmo ciclo)
- Trocar de ciclo (mesmo plano)
- Trocar de plano E ciclo ao mesmo tempo

### 6.2. Preview da mudanca

Antes de confirmar, chamar `GET /api/v1/subscriptions/{id}/plan-changes/preview?newPlanId={id}` para exibir:

```
┌─────────────────────────────────────────┐
│  Mudanca de plano                       │
│                                         │
│  Plano atual:  Basic (R$ 100,00/mes)    │
│  Novo plano:   Premium (R$ 200,00/mes)  │
│                                         │
│  Tipo: UPGRADE                          │
│  Credito periodo nao usado: R$ 50,00    │
│  Cobranca pro-rata: R$ 150,00           │
│                                         │
│  [ Cancelar ]  [ Confirmar mudanca ]    │
└─────────────────────────────────────────┘
```

---

## 7. Campos Removidos (Breaking Changes)

Os seguintes campos **nao existem mais** na API e devem ser removidos do frontend:

| Campo antigo | Substituido por |
|-------------|-----------------|
| `plan.value` | `plan.precoMensal`, `plan.precoAnual`, `plan.precoSemestral` |
| `plan.cycle` | `subscription.cycle` (escolhido na assinatura) |
| `subscription.value` | `subscription.effectivePrice` |

### Checklist de migracao no frontend

- [ ] Remover referencias a `plan.value` em todos os componentes
- [ ] Remover referencias a `plan.cycle` em todos os componentes
- [ ] Substituir `subscription.value` por `subscription.effectivePrice`
- [ ] Adicionar seletor de ciclo na pricing page
- [ ] Adicionar campo `cycle` no request de criacao de assinatura
- [ ] Adicionar campo `codigo` no formulario de criacao de plano
- [ ] Desabilitar campo `codigo` no formulario de edicao
- [ ] Implementar calculo automatico do preco anual
- [ ] Implementar secao de promocoes no formulario admin
- [ ] Implementar exibicao de features com `included` true/false
- [ ] Implementar exibicao de limites com `included` true/false
- [ ] Implementar preco riscado quando promo ativa
- [ ] Implementar texto e countdown de promocao
- [ ] Ajustar tipagem TypeScript/interfaces para novos campos

---

## 8. Tipos TypeScript Sugeridos

```typescript
// Plano
interface Plan {
  id: number;
  companyId: number;
  name: string;
  description: string | null;
  codigo: string;
  precoMensal: number;
  precoAnual: number | null;
  descontoPercentualAnual: number | null;
  precoSemestral: number; // calculado pela API
  promoMensalAtiva: boolean;
  promoMensalPreco: number | null;
  promoMensalTexto: string | null;
  promoMensalInicio: string | null; // ISO datetime
  promoMensalFim: string | null;
  promoAnualAtiva: boolean;
  promoAnualPreco: number | null;
  promoAnualTexto: string | null;
  promoAnualInicio: string | null;
  promoAnualFim: string | null;
  trialDays: number;
  setupFee: number;
  active: boolean;
  version: number;
  limits: string | null;    // JSON string - parsear com JSON.parse()
  features: string | null;  // JSON string - parsear com JSON.parse()
  tierOrder: number;
  createdAt: string;
  updatedAt: string;
}

// Feature/Limite (apos JSON.parse do campo features ou limits)
interface PlanFeature {
  text: string;
  included: boolean;
}

// Pricing (endpoint /pricing)
interface PlanPricing {
  id: number;
  codigo: string;
  name: string;
  precoMensal: number;
  precoSemestral: number;
  precoAnual: number;
  descontoPercentualAnual: number | null;
  promoMensal: PromoPricing;
  promoAnual: PromoPricing;
  features: string | null;
  limits: string | null;
}

interface PromoPricing {
  ativa: boolean;
  preco: number | null;
  texto: string | null;
  validaAte: string | null; // ISO datetime
}

// Ciclo de cobranca
type PlanCycle = 'MONTHLY' | 'SEMIANNUALLY' | 'YEARLY';

// Criar plano
interface CreatePlanRequest {
  codigo: string;
  name: string;
  description?: string;
  precoMensal: number;
  precoAnual?: number;
  descontoPercentualAnual?: number;
  promoMensalAtiva?: boolean;
  promoMensalPreco?: number;
  promoMensalTexto?: string;
  promoMensalInicio?: string;
  promoMensalFim?: string;
  promoAnualAtiva?: boolean;
  promoAnualPreco?: number;
  promoAnualTexto?: string;
  promoAnualInicio?: string;
  promoAnualFim?: string;
  trialDays?: number;
  setupFee?: number;
  limits?: string;    // JSON.stringify do array de PlanFeature
  features?: string;  // JSON.stringify do array de PlanFeature
  tierOrder?: number;
}

// Atualizar plano (sem codigo)
type UpdatePlanRequest = Omit<CreatePlanRequest, 'codigo'>;

// Criar assinatura
interface CreateSubscriptionRequest {
  customerId: number;
  planId: number;
  billingType: 'PIX' | 'BOLETO' | 'CREDIT_CARD';
  cycle: PlanCycle;  // NOVO - obrigatorio
  nextDueDate?: string;
  description?: string;
  externalReference?: string;
  creditCard?: CreditCardData;
  creditCardHolderInfo?: CreditCardHolderData;
  creditCardToken?: string;
  remoteIp?: string;
}

// Resposta da assinatura
interface SubscriptionResponse {
  id: number;
  companyId: number;
  customerId: number;
  planId: number;
  planName: string;
  asaasId: string;
  billingType: string;
  effectivePrice: number;  // RENOMEADO de value
  cycle: string;           // VINDO DA ASSINATURA, nao do plano
  currentPeriodStart: string | null;
  currentPeriodEnd: string | null;
  nextDueDate: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
}
```

---

## 9. Helpers Uteis para o Frontend

```typescript
// Formatar preco em BRL
function formatPrice(value: number): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL'
  }).format(value);
}

// Parsear features/limits do plano
function parseFeatures(json: string | null): PlanFeature[] {
  if (!json) return [];
  try {
    return JSON.parse(json);
  } catch {
    return [];
  }
}

// Obter preco exibido para um ciclo (usando resposta do /pricing)
function getDisplayPrice(pricing: PlanPricing, cycle: PlanCycle): {
  price: number;
  originalPrice: number | null;
  promoText: string | null;
} {
  switch (cycle) {
    case 'MONTHLY': {
      const promo = pricing.promoMensal;
      if (promo.ativa && promo.preco != null) {
        return {
          price: promo.preco,
          originalPrice: pricing.precoMensal, // sera exibido riscado
          promoText: promo.texto
        };
      }
      return { price: pricing.precoMensal, originalPrice: null, promoText: null };
    }
    case 'SEMIANNUALLY': {
      // Semestral ja considera promo mensal no calculo da API
      return { price: pricing.precoSemestral, originalPrice: null, promoText: null };
    }
    case 'YEARLY': {
      const promo = pricing.promoAnual;
      if (promo.ativa && promo.preco != null) {
        return {
          price: promo.preco,
          originalPrice: pricing.precoAnual,
          promoText: promo.texto
        };
      }
      return { price: pricing.precoAnual, originalPrice: null, promoText: null };
    }
  }
}

// Calcular preco anual a partir do mensal + desconto
function calcPrecoAnual(precoMensal: number, descontoPercent?: number): number {
  const anual = precoMensal * 12;
  if (descontoPercent && descontoPercent > 0) {
    return anual * (1 - descontoPercent / 100);
  }
  return anual;
}

// Label do ciclo para exibicao
function cycleLabel(cycle: PlanCycle): string {
  const labels: Record<PlanCycle, string> = {
    'MONTHLY': 'mes',
    'SEMIANNUALLY': 'semestre',
    'YEARLY': 'ano'
  };
  return labels[cycle];
}

// Sufixo de preco: "R$ 149,90 /mes"
function priceWithCycle(price: number, cycle: PlanCycle): string {
  return `${formatPrice(price)} /${cycleLabel(cycle)}`;
}

// Verificar se promo esta ativa agora (client-side)
function isPromoActiveNow(promo: PromoPricing): boolean {
  if (!promo.ativa || !promo.validaAte) return false;
  return new Date() <= new Date(promo.validaAte);
}
```

---

## 10. Resumo Visual das Telas Impactadas

```
┌──────────────────────────────────────────────────────┐
│  1. ADMIN: Formulario de Plano                       │
│     - Novo campo: codigo (slug, somente criacao)     │
│     - Precos: mensal + anual (com calculo auto)      │
│     - Secao de promocoes (mensal + anual)            │
│     - Features e limites como lista dinamica         │
├──────────────────────────────────────────────────────┤
│  2. CLIENTE: Pricing Page                            │
│     - Toggle de ciclo (Mensal/Semestral/Anual)       │
│     - Cards com preco do ciclo selecionado           │
│     - Preco riscado + texto quando promo ativa       │
│     - Features com check/X por included              │
│     - Badge "Economize X%" no toggle anual           │
├──────────────────────────────────────────────────────┤
│  3. CLIENTE: Checkout / Assinatura                   │
│     - Enviar cycle no request (NOVO)                 │
│     - Exibir effectivePrice no resumo                │
├──────────────────────────────────────────────────────┤
│  4. CLIENTE: Mudanca de Plano                        │
│     - Preview mostra credito + cobranca pro-rata     │
│     - Permite troca de ciclo (mensal -> anual)       │
├──────────────────────────────────────────────────────┤
│  5. ADMIN/CLIENTE: Detalhes da Assinatura            │
│     - Exibir effectivePrice (nao mais value)         │
│     - Exibir cycle da assinatura                     │
└──────────────────────────────────────────────────────┘
```
