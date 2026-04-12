# Politica de Acesso — Guia de Implementacao Frontend

Documento tecnico para construcao do componente de configuracao da Politica de Acesso e do painel de verificacao de status financeiro de clientes.

---

## Sumario

1. [Tela de Configuracao da Politica](#1-tela-de-configuracao-da-politica)
2. [Componente de Status de Acesso do Cliente](#2-componente-de-status-de-acesso-do-cliente)
3. [Endpoints da API](#3-endpoints-da-api)
4. [Tipos e Interfaces](#4-tipos-e-interfaces)
5. [Exemplos de Requisicao](#5-exemplos-de-requisicao)

---

## 1. Tela de Configuracao da Politica

### Acesso

- **Rota sugerida:** `/settings/access-policy`
- **Menu:** Configuracoes > Politica de Acesso
- **Permissao:** Apenas `COMPANY_ADMIN` e `HOLDING_ADMIN`
- **Comportamento:** Ao abrir a tela, chamar `GET /api/v1/access-policy`. Se a empresa nunca configurou, o backend retorna os valores padrao automaticamente.

### Layout do Formulario

O formulario deve ser dividido em secoes logicas com cards ou accordions.

---

#### Secao 1: Regras de Bloqueio

##### Campo: Quantidade minima de cobrancas vencidas

| Propriedade | Valor |
|-------------|-------|
| Campo API | `maxOverdueCharges` |
| Tipo | Input numerico (inteiro) |
| Minimo | 1 |
| Default | 1 |
| Label | "Quantidade minima de cobrancas vencidas para bloquear" |
| Dica (tooltip) | "O cliente sera bloqueado quando atingir esta quantidade de cobrancas com status VENCIDO. Exemplo: se configurar 3, o cliente so sera bloqueado a partir da 3a cobranca vencida." |

##### Campo: Dias de tolerancia apos vencimento

| Propriedade | Valor |
|-------------|-------|
| Campo API | `overdueToleranceDays` |
| Tipo | Input numerico (inteiro) |
| Minimo | 0 |
| Default | 0 |
| Label | "Dias de tolerancia apos vencimento" |
| Dica (tooltip) | "Numero de dias que o sistema aguarda apos o vencimento antes de considerar a cobranca como pendencia para bloqueio. Exemplo: se configurar 5, uma cobranca que venceu ontem ainda nao conta como pendencia." |
| Complemento | Exibir texto auxiliar abaixo do campo: "0 = bloqueia imediatamente apos o vencimento" |

---

#### Secao 2: Tipos de Cobranca que Bloqueiam

Exibir como um grupo de switches/toggles:

##### Toggle: Cobrancas de assinatura

| Propriedade | Valor |
|-------------|-------|
| Campo API | `blockOnSubscriptionCharges` |
| Tipo | Switch (boolean) |
| Default | Ativado (true) |
| Label | "Bloquear por cobrancas de assinatura vencidas" |
| Descricao | "Considera cobrancas recorrentes vinculadas a assinaturas (mensalidades, anuidades, etc.)" |

##### Toggle: Cobrancas avulsas

| Propriedade | Valor |
|-------------|-------|
| Campo API | `blockOnStandaloneCharges` |
| Tipo | Switch (boolean) |
| Default | Desativado (false) |
| Label | "Bloquear por cobrancas avulsas vencidas" |
| Descricao | "Considera cobrancas independentes nao vinculadas a nenhuma assinatura (ex: taxas, servicos pontuais)" |

> **UX:** Se ambos os toggles estiverem desativados, exibir um alerta amarelo:
> "Atencao: nenhum tipo de cobranca esta configurado para bloqueio. O sistema nao ira bloquear clientes por cobrancas vencidas."

---

#### Secao 3: Bloqueios Adicionais

##### Toggle: Assinatura suspensa

| Propriedade | Valor |
|-------------|-------|
| Campo API | `blockOnSuspendedSubscription` |
| Tipo | Switch (boolean) |
| Default | Ativado (true) |
| Label | "Bloquear clientes com assinatura suspensa" |
| Descricao | "Bloqueia automaticamente o acesso se o cliente possuir pelo menos uma assinatura com status SUSPENSA (suspensao por inadimplencia)" |

##### Toggle: Saldo de credito negativo

| Propriedade | Valor |
|-------------|-------|
| Campo API | `blockOnNegativeCredit` |
| Tipo | Switch (boolean) |
| Default | Desativado (false) |
| Label | "Bloquear clientes com saldo de credito negativo" |
| Descricao | "Bloqueia o acesso se o saldo de credito do cliente estiver abaixo de zero" |

---

#### Secao 4: Mensagem de Bloqueio

##### Campo: Mensagem personalizada

| Propriedade | Valor |
|-------------|-------|
| Campo API | `customBlockMessage` |
| Tipo | Textarea |
| Maximo | 500 caracteres (limitacao do backend) |
| Default | Vazio (null) |
| Label | "Mensagem exibida ao cliente bloqueado" |
| Placeholder | "Ex: Entre em contato com o financeiro pelo telefone (11) 9999-0000 ou email financeiro@empresa.com" |
| Dica (tooltip) | "Esta mensagem sera retornada na resposta da API quando o cliente estiver bloqueado. Sistemas integrados podem exibir esta mensagem ao usuario final." |
| Contador | Exibir contador de caracteres: "125/500" |

> **UX:** Exibir um preview da mensagem abaixo do campo, estilizado como o cliente final veria:
> ```
> Preview:
> ┌──────────────────────────────────────────────┐
> │ ⚠ Acesso bloqueado                           │
> │ Entre em contato com o financeiro pelo        │
> │ telefone (11) 9999-0000                       │
> └──────────────────────────────────────────────┘
> ```

---

#### Secao 5: Cache

##### Campo: Tempo de cache (TTL)

| Propriedade | Valor |
|-------------|-------|
| Campo API | `cacheTtlMinutes` |
| Tipo | Input numerico (inteiro) |
| Minimo | 1 |
| Default | 5 |
| Label | "Tempo de cache (minutos)" |
| Dica (tooltip) | "Por quanto tempo o resultado da verificacao de acesso fica armazenado em cache. Valores menores significam verificacoes mais atualizadas, mas com maior carga no banco de dados." |
| Opcoes sugeridas | Pode usar um select com opcoes pre-definidas: 1, 5, 10, 15, 30 minutos |

---

#### Botao de Salvar

| Propriedade | Valor |
|-------------|-------|
| Label | "Salvar Configuracoes" |
| Metodo | `PUT /api/v1/access-policy` |
| Comportamento | Enviar apenas os campos alterados. Exibir toast de sucesso ou erro. |
| Loading | Exibir spinner no botao durante a requisicao |

---

### Fluxo Completo da Tela

```
1. Tela carrega
   └─> GET /api/v1/access-policy
       └─> Preenche o formulario com os valores retornados

2. Usuario altera campos
   └─> Habilitar botao "Salvar" apenas se houver alteracoes
   └─> Validar campos no frontend antes de enviar

3. Usuario clica em "Salvar"
   └─> PUT /api/v1/access-policy (body com campos alterados)
       ├─> 200 OK: Toast "Configuracoes salvas com sucesso"
       └─> 4xx/5xx: Toast de erro com mensagem do backend
```

---

### Wireframe da Tela

```
┌──────────────────────────────────────────────────────────────┐
│ Configuracoes > Politica de Acesso                           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─ Regras de Bloqueio ───────────────────────────────────┐  │
│  │                                                         │  │
│  │  Cobrancas vencidas para bloquear    [ 1      ]  ⓘ     │  │
│  │                                                         │  │
│  │  Dias de tolerancia apos vencimento  [ 0      ]  ⓘ     │  │
│  │  0 = bloqueia imediatamente apos o vencimento           │  │
│  │                                                         │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌─ Tipos de Cobranca ────────────────────────────────────┐  │
│  │                                                         │  │
│  │  Cobrancas de assinatura vencidas         [====ON ]     │  │
│  │  Cobrancas recorrentes (mensalidades...)                │  │
│  │                                                         │  │
│  │  Cobrancas avulsas vencidas               [ OFF===]     │  │
│  │  Cobrancas independentes (taxas, servicos pontuais)     │  │
│  │                                                         │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌─ Bloqueios Adicionais ─────────────────────────────────┐  │
│  │                                                         │  │
│  │  Assinatura suspensa                      [====ON ]     │  │
│  │  Bloqueia se possuir assinatura suspensa                │  │
│  │                                                         │  │
│  │  Saldo de credito negativo                [ OFF===]     │  │
│  │  Bloqueia se saldo de credito < 0                       │  │
│  │                                                         │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌─ Mensagem de Bloqueio ─────────────────────────────────┐  │
│  │                                                         │  │
│  │  ┌───────────────────────────────────────────────────┐  │  │
│  │  │ Entre em contato com o financeiro pelo            │  │  │
│  │  │ telefone (11) 9999-0000                           │  │  │
│  │  └───────────────────────────────────────────────────┘  │  │
│  │  125/500 caracteres                                     │  │
│  │                                                         │  │
│  │  Preview:                                               │  │
│  │  ┌─ ⚠ Acesso bloqueado ───────────────────────────┐    │  │
│  │  │ Entre em contato com o financeiro pelo          │    │  │
│  │  │ telefone (11) 9999-0000                         │    │  │
│  │  └─────────────────────────────────────────────────┘    │  │
│  │                                                         │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌─ Cache ────────────────────────────────────────────────┐  │
│  │                                                         │  │
│  │  Tempo de cache    [ 5 minutos       ▼]  ⓘ             │  │
│  │                                                         │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                              │
│                              [ Salvar Configuracoes ]        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. Componente de Status de Acesso do Cliente

### Onde exibir

Este componente pode ser exibido em dois lugares:

1. **Pagina de detalhe do cliente** — Card lateral ou aba "Status de Acesso"
2. **Listagem de clientes** — Coluna com badge (Liberado/Bloqueado)

### Endpoint

```
GET /api/v1/customers/{id}/access-status
```

### Card de Status (detalhe do cliente)

```
┌─ Status de Acesso ─────────────────────────────────────────┐
│                                                             │
│  ● BLOQUEADO                           Verificado as 10:30 │
│                                                             │
│  Motivos:                                                   │
│  • 2 cobranca(s) de assinatura vencidas ha mais de 5 dias  │
│  • 1 assinatura suspensa por inadimplencia                  │
│                                                             │
│  ┌─ Mensagem configurada ────────────────────────────────┐  │
│  │ Entre em contato com financeiro: (11) 9999-0000       │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─ Resumo Financeiro ───────────────────────────────────┐  │
│  │  Assinaturas ativas        1                          │  │
│  │  Assinaturas suspensas     1                          │  │
│  │  Cobrancas vencidas        2                          │  │
│  │  Valor total vencido       R$ 350,00                  │  │
│  │  Dias desde vencimento     18 dias                    │  │
│  │  Saldo de credito          R$ 0,00                    │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  [ 🔄 Verificar Novamente ]                                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Badge para listagem

| Status | Cor | Texto |
|--------|-----|-------|
| `allowed: true` | Verde | Liberado |
| `allowed: false` | Vermelho | Bloqueado |

```
┌──────┬──────────────┬────────────┬──────────────┐
│ ID   │ Nome         │ Documento  │ Acesso       │
├──────┼──────────────┼────────────┼──────────────┤
│ 42   │ Joao Silva   │ 123.456... │ 🔴 Bloqueado │
│ 43   │ Maria Santos │ 987.654... │ 🟢 Liberado  │
│ 44   │ Pedro Lima   │ 456.789... │ 🔴 Bloqueado │
└──────┴──────────────┴────────────┴──────────────┘
```

> **Nota sobre performance na listagem:** Nao chame o endpoint para cada cliente individualmente na listagem. Chame apenas quando o usuario abrir o detalhe do cliente, ou implemente um endpoint batch futuro.

### Comportamento do Botao "Verificar Novamente"

1. Chamar `GET /api/v1/customers/{id}/access-status`
2. O backend sempre recalcula e atualiza o cache
3. Atualizar o card com os novos dados
4. Exibir toast: "Status verificado com sucesso"

---

## 3. Endpoints da API

### 3.1 Consultar politica de acesso

```
GET /api/v1/access-policy

Headers:
  Authorization: Bearer <jwt_token>
  X-Company-Id: <company_id>

Response: 200 OK
```

```json
{
  "id": 1,
  "companyId": 1,
  "maxOverdueCharges": 1,
  "overdueToleranceDays": 0,
  "blockOnSuspendedSubscription": true,
  "blockOnStandaloneCharges": false,
  "blockOnSubscriptionCharges": true,
  "blockOnNegativeCredit": false,
  "customBlockMessage": null,
  "cacheTtlMinutes": 5,
  "createdAt": "2026-04-12T10:00:00",
  "updatedAt": "2026-04-12T10:00:00"
}
```

### 3.2 Atualizar politica de acesso

```
PUT /api/v1/access-policy

Headers:
  Authorization: Bearer <jwt_token>
  X-Company-Id: <company_id>
  Content-Type: application/json
```

**Body (atualizacao parcial — envie apenas os campos alterados):**

```json
{
  "maxOverdueCharges": 3,
  "overdueToleranceDays": 5,
  "blockOnStandaloneCharges": true,
  "customBlockMessage": "Entre em contato com financeiro: (11) 9999-0000"
}
```

```
Response: 200 OK (retorna a politica completa atualizada)
```

### 3.3 Verificar status de acesso do cliente

```
GET /api/v1/customers/{id}/access-status

Headers (via JWT):
  Authorization: Bearer <jwt_token>
  X-Company-Id: <company_id>

OU (via API Key):
  X-API-Key: pk_abc123...

Response: 200 OK
```

```json
{
  "customerId": 42,
  "customerName": "Joao Silva",
  "allowed": false,
  "reasons": [
    "2 cobranca(s) de assinatura vencida(s) ha mais de 5 dia(s)",
    "1 assinatura(s) suspensa(s) por inadimplencia"
  ],
  "customBlockMessage": "Entre em contato com financeiro: (11) 9999-0000",
  "summary": {
    "activeSubscriptions": 0,
    "suspendedSubscriptions": 1,
    "overdueCharges": 2,
    "totalOverdueValue": 350.00,
    "oldestOverdueDays": 18,
    "creditBalance": 0.00
  },
  "checkedAt": "2026-04-12T10:30:00"
}
```

**Quando `allowed: true`:**

```json
{
  "customerId": 43,
  "customerName": "Maria Santos",
  "allowed": true,
  "reasons": [],
  "customBlockMessage": null,
  "summary": {
    "activeSubscriptions": 1,
    "suspendedSubscriptions": 0,
    "overdueCharges": 0,
    "totalOverdueValue": 0.00,
    "oldestOverdueDays": 0,
    "creditBalance": 150.00
  },
  "checkedAt": "2026-04-12T10:30:00"
}
```

---

## 4. Tipos e Interfaces

### TypeScript

```typescript
// ============ POLITICA DE ACESSO ============

interface AccessPolicy {
  id: number;
  companyId: number;
  maxOverdueCharges: number;
  overdueToleranceDays: number;
  blockOnSuspendedSubscription: boolean;
  blockOnStandaloneCharges: boolean;
  blockOnSubscriptionCharges: boolean;
  blockOnNegativeCredit: boolean;
  customBlockMessage: string | null;
  cacheTtlMinutes: number;
  createdAt: string;
  updatedAt: string;
}

interface UpdateAccessPolicyRequest {
  maxOverdueCharges?: number;
  overdueToleranceDays?: number;
  blockOnSuspendedSubscription?: boolean;
  blockOnStandaloneCharges?: boolean;
  blockOnSubscriptionCharges?: boolean;
  blockOnNegativeCredit?: boolean;
  customBlockMessage?: string | null;
  cacheTtlMinutes?: number;
}

// ============ STATUS DE ACESSO ============

interface AccessStatusResponse {
  customerId: number;
  customerName: string;
  allowed: boolean;
  reasons: string[];
  customBlockMessage: string | null;
  summary: AccessSummary;
  checkedAt: string;
}

interface AccessSummary {
  activeSubscriptions: number;
  suspendedSubscriptions: number;
  overdueCharges: number;
  totalOverdueValue: number;
  oldestOverdueDays: number;
  creditBalance: number;
}
```

---

## 5. Exemplos de Requisicao

### Carregar configuracao ao abrir a tela

```typescript
async function loadAccessPolicy(): Promise<AccessPolicy> {
  const response = await fetch("/api/v1/access-policy", {
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Company-Id": String(companyId),
    },
  });
  return response.json();
}
```

### Salvar configuracao

```typescript
async function saveAccessPolicy(
  changes: UpdateAccessPolicyRequest
): Promise<AccessPolicy> {
  const response = await fetch("/api/v1/access-policy", {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Company-Id": String(companyId),
      "Content-Type": "application/json",
    },
    body: JSON.stringify(changes),
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || "Erro ao salvar configuracoes");
  }

  return response.json();
}
```

### Verificar status de acesso de um cliente

```typescript
async function checkCustomerAccess(
  customerId: number
): Promise<AccessStatusResponse> {
  const response = await fetch(
    `/api/v1/customers/${customerId}/access-status`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
        "X-Company-Id": String(companyId),
      },
    }
  );
  return response.json();
}
```

### Exemplo de uso no componente de detalhe do cliente

```typescript
// Ao carregar o detalhe do cliente
const [accessStatus, setAccessStatus] = useState<AccessStatusResponse | null>(
  null
);
const [loading, setLoading] = useState(false);

async function loadAccessStatus() {
  setLoading(true);
  try {
    const status = await checkCustomerAccess(customerId);
    setAccessStatus(status);
  } catch (error) {
    console.error("Erro ao verificar acesso:", error);
  } finally {
    setLoading(false);
  }
}

// Botao "Verificar Novamente"
async function handleRefresh() {
  await loadAccessStatus();
  toast.success("Status verificado com sucesso");
}
```

### Exemplo de uso do formulario de configuracao

```typescript
const [policy, setPolicy] = useState<AccessPolicy | null>(null);
const [form, setForm] = useState<UpdateAccessPolicyRequest>({});
const [hasChanges, setHasChanges] = useState(false);

// Carregar ao montar
useEffect(() => {
  loadAccessPolicy().then((data) => {
    setPolicy(data);
  });
}, []);

// Detectar mudancas
function handleChange(field: keyof UpdateAccessPolicyRequest, value: any) {
  setForm((prev) => ({ ...prev, [field]: value }));
  setHasChanges(true);
}

// Salvar
async function handleSave() {
  if (!hasChanges) return;
  const updated = await saveAccessPolicy(form);
  setPolicy(updated);
  setForm({});
  setHasChanges(false);
  toast.success("Configuracoes salvas com sucesso");
}
```

---

## Validacoes no Frontend

| Campo | Validacao | Mensagem de erro |
|-------|-----------|------------------|
| `maxOverdueCharges` | Inteiro >= 1 | "Informe um valor de pelo menos 1" |
| `overdueToleranceDays` | Inteiro >= 0 | "Informe um valor de pelo menos 0" |
| `customBlockMessage` | Maximo 500 caracteres | "Mensagem deve ter no maximo 500 caracteres" |
| `cacheTtlMinutes` | Inteiro >= 1 | "Informe um valor de pelo menos 1 minuto" |
| Toggles de tipo de cobranca | Ao menos um ativado (warning, nao bloqueante) | Exibir alerta amarelo, nao impedir salvar |
