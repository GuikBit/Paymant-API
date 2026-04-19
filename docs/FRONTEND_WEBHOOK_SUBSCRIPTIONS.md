# Webhook Subscriptions — Guia Frontend

Documento tecnico para implementar as telas de cadastro e gerenciamento de webhooks do tenant. Esse modulo permite que cada empresa registre ate 10 URLs para receber eventos de dominio (cobrancas, assinaturas, mudancas de plano, etc.) da API de Pagamento.

---

## Sumario

1. [Conceitos](#1-conceitos)
2. [Autenticacao e permissoes](#2-autenticacao-e-permissoes)
3. [Endpoints da API](#3-endpoints-da-api)
4. [Tipos e interfaces](#4-tipos-e-interfaces)
5. [Telas a implementar](#5-telas-a-implementar)
6. [Formato do webhook que chega no cliente](#6-formato-do-webhook-que-chega-no-cliente)
7. [Tratamento de erros](#7-tratamento-de-erros)

---

## 1. Conceitos

- **Webhook subscription**: cadastro de uma URL + lista de eventos que a empresa quer receber. Uma empresa pode ter ate 10 ativos.
- **Token**: valor gerado pela API na criacao, exibido UMA UNICA VEZ ao usuario. Vai no header `Authorization: Bearer <token>` em toda chamada que a API fizer para a URL cadastrada. O sistema do cliente guarda esse valor e compara com o que chegar no header para validar que a chamada veio de voces.
- **Delivery attempt**: cada tentativa de entrega de um evento para uma URL. Fica gravada pra historico, com status HTTP, resposta e tempo de execucao. Entrega falha entra em retry com backoff (5s, 30s, 2min, 10min, 1h ate 10 tentativas).
- **Event catalog**: lista curada de eventos que podem ser assinados. Use `*` para receber todos.

---

## 2. Autenticacao e permissoes

Todas as chamadas precisam de `Authorization: Bearer <jwt>` ou `X-API-Key: <key>`.

| Operacao | Roles permitidas |
|---|---|
| Listar, ver detalhes, ver historico, ver catalogo de eventos | `HOLDING_ADMIN`, `COMPANY_ADMIN`, `COMPANY_OPERATOR` |
| Criar, atualizar, deletar, rotacionar token, ping | `HOLDING_ADMIN`, `COMPANY_ADMIN` |

No frontend, esconda os botoes de mutacao para `COMPANY_OPERATOR`.

---

## 3. Endpoints da API

Prefixo: `/api/v1/webhook-subscriptions`.

### 3.1. Criar webhook

`POST /api/v1/webhook-subscriptions`

**Request:**
```json
{
  "name": "Notificacoes do cliente final",
  "description": "App mobile recebe eventos de cobranca e assinatura",
  "url": "https://meu-app.com.br/webhooks/payment-api",
  "eventTypes": ["ChargeCreatedEvent", "ChargePaidEvent", "SubscriptionCanceledEvent"]
}
```

**Response 201:**
```json
{
  "subscription": {
    "id": 7,
    "companyId": 4,
    "name": "Notificacoes do cliente final",
    "description": "...",
    "url": "https://meu-app.com.br/webhooks/payment-api",
    "tokenPrefix": "wht_abc123",
    "eventTypes": ["ChargeCreatedEvent", "ChargePaidEvent", "SubscriptionCanceledEvent"],
    "active": true,
    "lastDeliveryAt": null,
    "lastSuccessAt": null,
    "createdAt": "2026-04-19T18:00:00",
    "updatedAt": "2026-04-19T18:00:00"
  },
  "rawToken": "wht_abc123dXYZ456pqrSTU789..."
}
```

> **ATENCAO:** `rawToken` aparece **somente aqui**. Nao e possivel recuperar depois. O frontend precisa mostrar em modal com botao "copiar" e avisar o usuario para guardar em lugar seguro.

**Erros:**
- `422` — URL invalida, evento fora do catalogo, ou limite de 10 ativos atingido.
- `403` — role insuficiente.

### 3.2. Listar webhooks da empresa

`GET /api/v1/webhook-subscriptions?page=0&size=20&sort=createdAt,desc`

**Response 200:**
```json
{
  "content": [
    {
      "id": 7,
      "companyId": 4,
      "name": "Notificacoes do cliente final",
      "description": "...",
      "url": "https://meu-app.com.br/webhooks/payment-api",
      "tokenPrefix": "wht_abc123",
      "eventTypes": ["ChargeCreatedEvent", "ChargePaidEvent"],
      "active": true,
      "lastDeliveryAt": "2026-04-19T18:05:12",
      "lastSuccessAt": "2026-04-19T18:05:12",
      "createdAt": "2026-04-19T18:00:00",
      "updatedAt": "2026-04-19T18:00:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

### 3.3. Detalhes

`GET /api/v1/webhook-subscriptions/{id}` — mesma estrutura do item individual acima.

### 3.4. Atualizar

`PUT /api/v1/webhook-subscriptions/{id}`

Envie apenas os campos que quer mudar (campos nulos/vazios sao ignorados):
```json
{
  "url": "https://nova-url.com/webhooks",
  "active": false,
  "eventTypes": ["*"]
}
```

### 3.5. Deletar

`DELETE /api/v1/webhook-subscriptions/{id}` — retorna 204. Deleta junto todo o historico de entregas (cascade).

### 3.6. Rotacionar token

`POST /api/v1/webhook-subscriptions/{id}/rotate-token`

**Response 200:**
```json
{
  "subscriptionId": 7,
  "tokenPrefix": "wht_new999",
  "rawToken": "wht_new999YYYaaa..."
}
```
Mesma regra do create: mostrar uma vez, avisar para guardar.

### 3.7. Ping (teste)

`POST /api/v1/webhook-subscriptions/{id}/ping`

Cria uma delivery attempt com evento `WebhookTestEvent` e payload fake. Retorna 202 com a attempt criada. O envio acontece dentro de ~5s no ciclo do scheduler — o front nao recebe o status da entrega em tempo real aqui; peca ao usuario para olhar o historico de entregas depois de alguns segundos.

**Response 202:**
```json
{
  "id": 142,
  "subscriptionId": 7,
  "eventType": "WebhookTestEvent",
  "eventId": "test_1713567890123",
  "url": "https://meu-app.com.br/webhooks/payment-api",
  "responseStatus": null,
  "responseBodyExcerpt": null,
  "durationMs": null,
  "attemptNumber": 1,
  "status": "PENDING",
  "errorMessage": null,
  "nextAttemptAt": "2026-04-19T18:10:30",
  "createdAt": "2026-04-19T18:10:30",
  "deliveredAt": null
}
```

Depois de 5-10s, o front pode chamar o historico (proximo endpoint) filtrando por esse `id` para mostrar o resultado.

### 3.8. Historico de entregas

`GET /api/v1/webhook-subscriptions/{id}/deliveries?page=0&size=20`

**Response 200:**
```json
{
  "content": [
    {
      "id": 142,
      "subscriptionId": 7,
      "eventType": "WebhookTestEvent",
      "eventId": "test_1713567890123",
      "url": "https://meu-app.com.br/webhooks/payment-api",
      "responseStatus": 200,
      "responseBodyExcerpt": "{\"ok\":true}",
      "durationMs": 234,
      "attemptNumber": 1,
      "status": "DELIVERED",
      "errorMessage": null,
      "nextAttemptAt": null,
      "createdAt": "2026-04-19T18:10:30",
      "deliveredAt": "2026-04-19T18:10:31"
    }
  ]
}
```

Status possiveis:
- `PENDING` — aguardando proxima tentativa
- `DELIVERED` — recebeu 2xx do endpoint
- `FAILED` — ultima tentativa falhou (vai tentar de novo)
- `DLQ` — atingiu 10 tentativas sem sucesso (nao tenta mais)

### 3.9. Catalogo de eventos

`GET /api/v1/webhook-subscriptions/event-types`

**Response 200:**
```json
[
  { "eventType": "CustomerCreatedEvent", "resourceType": "Customer", "description": "Novo cliente cadastrado" },
  { "eventType": "ChargeCreatedEvent", "resourceType": "Charge", "description": "Cobranca gerada..." },
  { "eventType": "ChargePaidEvent", "resourceType": "Charge", "description": "..." },
  { "eventType": "ChargeCanceledEvent", "resourceType": "Charge", "description": "..." },
  { "eventType": "ChargeRefundedEvent", "resourceType": "Charge", "description": "..." },
  { "eventType": "SubscriptionCreatedEvent", "resourceType": "Subscription", "description": "..." },
  { "eventType": "SubscriptionPausedEvent", "resourceType": "Subscription", "description": "..." },
  { "eventType": "SubscriptionResumedEvent", "resourceType": "Subscription", "description": "..." },
  { "eventType": "SubscriptionSuspendedEvent", "resourceType": "Subscription", "description": "..." },
  { "eventType": "SubscriptionCanceledEvent", "resourceType": "Subscription", "description": "..." },
  { "eventType": "PlanChangeScheduledEvent", "resourceType": "SubscriptionPlanChange", "description": "..." },
  { "eventType": "PlanChangedEvent", "resourceType": "SubscriptionPlanChange", "description": "..." },
  { "eventType": "PlanChangePendingPaymentEvent", "resourceType": "SubscriptionPlanChange", "description": "..." },
  { "eventType": "WebhookTestEvent", "resourceType": "Test", "description": "Evento de teste..." }
]
```

Alem desses, o valor especial `*` no campo `eventTypes` significa "todos os eventos disponiveis".

---

## 4. Tipos e interfaces

### TypeScript

```ts
type WebhookDeliveryStatus = 'PENDING' | 'DELIVERED' | 'FAILED' | 'DLQ';

interface WebhookSubscription {
  id: number;
  companyId: number;
  name: string;
  description: string | null;
  url: string;
  tokenPrefix: string;         // so prefixo, raw nao retorna mais
  eventTypes: string[];        // ["*"] ou ["ChargeCreatedEvent", ...]
  active: boolean;
  lastDeliveryAt: string | null;
  lastSuccessAt: string | null;
  createdAt: string;
  updatedAt: string;
}

interface CreateWebhookSubscriptionRequest {
  name: string;
  description?: string;
  url: string;
  eventTypes: string[];
}

interface UpdateWebhookSubscriptionRequest {
  name?: string;
  description?: string;
  url?: string;
  eventTypes?: string[];
  active?: boolean;
}

interface CreateWebhookSubscriptionResult {
  subscription: WebhookSubscription;
  rawToken: string;   // mostrar uma vez
}

interface RotateTokenResult {
  subscriptionId: number;
  tokenPrefix: string;
  rawToken: string;
}

interface WebhookDeliveryAttempt {
  id: number;
  subscriptionId: number;
  eventType: string;
  eventId: string;
  url: string;
  responseStatus: number | null;
  responseBodyExcerpt: string | null;
  durationMs: number | null;
  attemptNumber: number;
  status: WebhookDeliveryStatus;
  errorMessage: string | null;
  nextAttemptAt: string | null;
  createdAt: string;
  deliveredAt: string | null;
}

interface WebhookEventCatalogEntry {
  eventType: string;
  resourceType: string;
  description: string;
}
```

---

## 5. Telas a implementar

### 5.1. Lista de webhooks (`/settings/webhooks`)

**Elementos:**
- Botao **"+ Novo webhook"** (apenas ADMIN).
- Tabela:

| Coluna | Valor | Observacao |
|---|---|---|
| Nome | `name` | |
| URL | `url` | Truncar com "..." se longa. Tooltip com URL completa. |
| Eventos | `eventTypes.join(", ")` | Se for `["*"]`, exibir "Todos". |
| Status | "Ativo" / "Inativo" | Badge verde/cinza a partir de `active`. |
| Ultima entrega | `lastDeliveryAt` | Formato relativo ("ha 5 min"). Se `null`, "Nunca". |
| Ultima sucesso | `lastSuccessAt` | Idem. |
| Acoes | Ver detalhes, Editar (ADMIN), Deletar (ADMIN) | |

Paginacao padrao (20 por pagina).

### 5.2. Criar / Editar (modal ou pagina)

Campos:
- **Nome** (text, obrigatorio, max 100).
- **Descricao** (textarea, opcional, max 500).
- **URL** (text, obrigatorio, valida https/http).
- **Eventos** — multi-select com busca, carregado de `GET /event-types`. Opcao "Todos (*)" no topo.
- **Ativo** (toggle, apenas no editar).

Validacoes no front:
- URL deve ser valida com protocolo http ou https.
- eventTypes deve ter pelo menos um item ou `*`.

Ao salvar (criar):
- Fazer POST.
- Em sucesso, **abrir modal de "Token gerado"** com o `rawToken`. NAO navegar para outra tela ate o usuario confirmar que copiou.

### 5.3. Modal "Token gerado"

```
+--------------------------------------------------------+
| Token gerado com sucesso                               |
|                                                        |
| Copie e guarde esse token agora.                       |
| Ele nao sera exibido novamente.                        |
|                                                        |
| +--------------------------------------------+         |
| | wht_abc123dXYZ456pqrSTU789longvalue...     | [copy]  |
| +--------------------------------------------+         |
|                                                        |
| [x] Confirmo que guardei o token                       |
|                                                        |
|              [Fechar] (desabilitado ate check)         |
+--------------------------------------------------------+
```

### 5.4. Detalhes do webhook (`/settings/webhooks/:id`)

Layout em duas secoes:

**Secao 1 — Configuracao**
- Todos os campos do webhook.
- Token: mostra `tokenPrefix + "***"` (ex: `wht_abc123***`).
- Botoes:
  - **Testar (ping)** — ADMIN. Chama `POST /{id}/ping`, exibe toast "Teste enviado. Veja o resultado em alguns segundos no historico abaixo."
  - **Rotacionar token** — ADMIN. Abre modal de confirmacao antes ("Isso invalida o token atual. Sistemas usando o token antigo vao parar de validar ate voce atualizar. Deseja continuar?"). Em sucesso, mostra o modal "Token gerado".
  - **Editar** — ADMIN. Vai pra tela de editar.
  - **Deletar** — ADMIN. Modal de confirmacao ("Isso apaga o webhook e todo o historico. Deseja continuar?").

**Secao 2 — Historico de entregas**
- Tabela paginada (20 por pagina) de `GET /{id}/deliveries`:

| Coluna | Valor |
|---|---|
| Data | `createdAt` formatado (ex: "19/04/2026 18:10:30") |
| Evento | `eventType` |
| Status HTTP | `responseStatus` ou "-" |
| Status | Badge com cor: verde DELIVERED, amarelo PENDING, laranja FAILED, vermelho DLQ |
| Tentativa | `attemptNumber` |
| Duracao | `durationMs` ms |
| Acoes | "Ver detalhes" |

Ao clicar em "Ver detalhes", abre modal com:
- `eventId`, `url`, `eventType`, `createdAt`, `deliveredAt`, `nextAttemptAt`
- **Request body** (JSON formatado — o envelope que foi enviado)
- **Response body** (`responseBodyExcerpt` — ja truncado pelo backend)
- `errorMessage` se houver

Botao **"Atualizar"** no topo da tabela para refetch manual (util apos um ping).

---

## 6. Formato do webhook que chega no cliente

Quando a API dispara um evento pra URL cadastrada, o cliente recebe:

**Headers:**
```
POST /webhooks/payment-api HTTP/1.1
Content-Type: application/json
Authorization: Bearer wht_abc123dXYZ456...
X-Token-Prefix: wht_abc123
X-Event-Id: evt_4821
X-Event-Type: ChargeCreatedEvent
X-Delivery-Id: 142
X-Delivery-Attempt: 1
```

**Body (envelope canonico):**
```json
{
  "id": "evt_4821",
  "type": "ChargeCreatedEvent",
  "occurredAt": "2026-04-19T18:10:30.123",
  "companyId": 4,
  "resource": {
    "type": "Charge",
    "id": "58"
  },
  "data": {
    "id": 58,
    "customerId": 69,
    "subscriptionId": 58,
    "value": 139.90,
    "status": "PENDING",
    "billingType": "PIX",
    "dueDate": "2026-04-20",
    "invoiceUrl": "https://sandbox.asaas.com/i/cl99pg20n3oz9c9f"
  }
}
```

### Como o servidor do cliente valida

```js
// pseudo-codigo do consumer
const EXPECTED_TOKEN = process.env.PAYMENT_API_WEBHOOK_TOKEN; // o rawToken que foi guardado

app.post('/webhooks/payment-api', (req, res) => {
  const auth = req.headers.authorization || '';
  if (auth !== `Bearer ${EXPECTED_TOKEN}`) {
    return res.status(401).send();
  }

  // idempotencia: X-Delivery-Id e unico por tentativa
  const deliveryId = req.headers['x-delivery-id'];
  if (alreadyProcessed(deliveryId)) {
    return res.status(200).send({ ok: true, duplicate: true });
  }

  processEvent(req.body);
  res.status(200).send({ ok: true });
});
```

### Regras para o handler do cliente

- **Responder rapido** (< 10s). O timeout do caller e 10s; se estourar, entra em retry.
- **Responder 2xx** para sucesso. Qualquer coisa diferente aciona retry com backoff.
- **Idempotencia recomendada** via `X-Delivery-Id` (valor unico por tentativa) ou `X-Event-Id` (unico por evento, mesmo entre tentativas).

---

## 7. Tratamento de erros

Formato padrao (`ProblemDetail`) do backend:

```json
{
  "type": "https://api.holding.com.br/errors/business",
  "title": "Business Rule Violation",
  "status": 422,
  "detail": "URL precisa usar http ou https.",
  "instance": "/api/v1/webhook-subscriptions"
}
```

Mapa de erros comuns:

| Status | Cenario | Mensagem sugerida no front |
|---|---|---|
| 422 | URL invalida | Mostrar o `detail` ao lado do campo URL |
| 422 | Evento nao suportado | Mostrar no campo eventTypes |
| 422 | Limite de 10 webhooks ativos | "Voce atingiu o limite de 10 webhooks ativos. Desative ou remova algum antes de criar outro." |
| 403 | Usuario operator tentando operacao admin | "Voce nao tem permissao para esta acao." |
| 404 | Webhook nao encontrado | "Webhook nao encontrado. Pode ter sido removido." |

---

## 8. Fluxo sugerido do primeiro uso

1. Usuario vai em Configuracoes > Webhooks.
2. Clica em **+ Novo webhook**.
3. Preenche nome, URL do proprio sistema, marca os eventos que quer.
4. Ao salvar, recebe o `rawToken`. Copia.
5. Vai no codigo/config do proprio sistema, salva o token em variavel de ambiente.
6. Volta pra tela de detalhes do webhook, clica em **Testar (ping)**.
7. Espera 5-10s, atualiza o historico. Deve ver uma linha `DELIVERED` com status 200.
8. Se falhou (timeout, 401, 500), abre os detalhes da delivery para ver o erro e ajusta no proprio sistema.
