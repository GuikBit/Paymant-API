# Integracao — Recebendo Webhooks da Payment API

Documento tecnico para implementar o **lado consumidor** que recebe eventos da Payment API. Cobre autenticacao, formato do payload, idempotencia, contrato de resposta, e catalogo de eventos. Decisoes de negocio (quem notificar, como renderizar na UI, etc.) ficam por conta do sistema consumidor.

---

## Sumario

1. [Arquitetura recomendada](#1-arquitetura-recomendada)
2. [Configurando o endpoint](#2-configurando-o-endpoint)
3. [Validacao de autenticidade](#3-validacao-de-autenticidade)
4. [Envelope canonico](#4-envelope-canonico)
5. [Catalogo de eventos e shape do `data`](#5-catalogo-de-eventos-e-shape-do-data)
6. [Idempotencia](#6-idempotencia)
7. [Contrato de resposta](#7-contrato-de-resposta)
8. [Retry e DLQ (lado Payment API)](#8-retry-e-dlq-lado-payment-api)
9. [Checklist de seguranca](#9-checklist-de-seguranca)
10. [Depuracao](#10-depuracao)
11. [Exemplo de implementacao](#11-exemplo-de-implementacao)

---

## 1. Arquitetura recomendada

O receptor do webhook **nao deve** executar a logica de negocio dentro da requisicao HTTP. O fluxo correto e:

```
Payment API  ──POST──>  [1] Endpoint HTTP receptor
                              ↓ valida token + persiste
                        [2] Fila/tabela interna (pending events)
                              ↓ worker assincrono
                        [3] Handler de negocio
                              ↓
                        [4] Acao (notificar cliente, atualizar tela, etc.)
```

Motivo: voce precisa responder em **menos de 10 segundos** com **2xx**, senao a Payment API entra em retry. Negocio async separado:

- **Mantem o timeout baixo** na rota HTTP (so valida + grava + responde 200).
- **Permite reprocessar** se o handler de negocio falhar sem reentregar do lado da Payment API.
- **Facilita auditoria** — voce tem o log bruto do que chegou.

Se ainda for pequena escala, dar pra fazer tudo sincrono, mas tenha em mente que qualquer trabalho lento (chamar API externa, gerar PDF, SMTP) estourara o timeout.

---

## 2. Configurando o endpoint

### 2.1. Do lado do seu sistema

Exponha uma rota HTTP POST publica (acessivel pela internet) em algum path do seu backend. Exemplo:

```
POST https://meu-sistema.com.br/webhooks/payment-api
```

Requisitos:
- **HTTPS** (em producao).
- Aceitar `Content-Type: application/json`.
- Responder em menos de 10s.
- Nao precisa de autenticacao Bearer a nivel de gateway/WAF — a autenticacao da Payment API vai no header `Authorization` do request.

### 2.2. Cadastro na Payment API

Fluxo de cadastro (ja documentado em `FRONTEND_WEBHOOK_SUBSCRIPTIONS.md`):

1. Chame `POST /api/v1/webhook-subscriptions` na Payment API com:
   - `name` — nome descritivo ("Notificacoes cliente final", "Painel admin")
   - `url` — a URL publica do seu endpoint
   - `eventTypes` — lista de eventos que quer receber ou `["*"]` pra todos
2. **A resposta contem `rawToken` UMA UNICA VEZ**. Guarde em secret manager / variavel de ambiente imediatamente — nao ha como recuperar depois.
3. Exemplo de armazenamento:
   ```bash
   PAYMENT_API_WEBHOOK_TOKEN=wht_abc123dXYZ456pqrSTU789longvalue...
   ```

Se perder o token, rotacione via `POST /api/v1/webhook-subscriptions/{id}/rotate-token` e atualize a variavel.

---

## 3. Validacao de autenticidade

Toda chamada da Payment API chega com estes headers:

| Header | Valor | Para que serve |
|---|---|---|
| `Authorization` | `Bearer <rawToken>` | Confirma que a chamada veio da Payment API |
| `Content-Type` | `application/json` | |
| `X-Token-Prefix` | `wht_abc123` (primeiros 12 chars do token) | Ajuda no debug; permite identificar qual token foi usado se voce tiver multiplos |
| `X-Event-Id` | `evt_4821` | Id do evento original na Payment API (estavel entre retries) |
| `X-Event-Type` | `ChargeCreatedEvent` | Mesmo valor do campo `type` no envelope |
| `X-Delivery-Id` | `142` | Id da tentativa de entrega especifica (muda a cada retry) |
| `X-Delivery-Attempt` | `1`, `2`, ... | Numero da tentativa (1-10) |

### 3.1. Algoritmo de validacao

```
1. Ler Authorization header.
2. Extrair o token apos "Bearer ".
3. Comparar com o token guardado na sua config.
4. Se diferente → responder 401 e nao processar.
5. Se igual → continuar.
```

**Usar comparacao de tempo-constante** se possivel (evita timing attack). A maioria das linguagens tem — por exemplo, `crypto.timingSafeEqual` em Node ou `MessageDigest.isEqual` em Java.

### 3.2. Proteger contra IP spoofing

Como o endpoint e publico, qualquer um pode tentar chamar. **O token e a unica defesa**. Nao dependa de IP allowlist da Payment API (o IP pode mudar).

---

## 4. Envelope canonico

Todo evento chega com este formato no body:

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
  "data": { ... }
}
```

### 4.1. Campos do envelope

| Campo | Tipo | Descricao |
|---|---|---|
| `id` | string | Identificador unico do evento. **Estavel** entre retries. Use pra idempotencia. |
| `type` | string | Nome do evento (ex: `ChargeCreatedEvent`). Duplica `X-Event-Type`. |
| `occurredAt` | ISO-8601 string | Quando o evento foi gerado na Payment API (sem timezone — UTC implicito). |
| `companyId` | number | ID da empresa (tenant) na Payment API. |
| `resource.type` | string | Tipo do recurso relacionado (`Charge`, `Subscription`, `Customer`, `SubscriptionPlanChange`). |
| `resource.id` | string | ID do recurso na Payment API. |
| `data` | object | Conteudo especifico do evento — shape varia pelo `type`. Ver proxima secao. |

### 4.2. Exemplo completo

```json
{
  "id": "evt_4821",
  "type": "ChargeCreatedEvent",
  "occurredAt": "2026-04-19T18:10:30.123",
  "companyId": 4,
  "resource": { "type": "Charge", "id": "58" },
  "data": {
    "id": 58,
    "companyId": 4,
    "customerId": 69,
    "subscriptionId": 58,
    "installmentId": null,
    "asaasId": "pay_cl99pg20n3oz9c9f",
    "billingType": "PIX",
    "value": 139.90,
    "dueDate": "2026-04-20",
    "status": "PENDING",
    "origin": "RECURRING",
    "externalReference": null,
    "pixQrcode": null,
    "pixCopyPaste": null,
    "boletoUrl": null,
    "invoiceUrl": "https://sandbox.asaas.com/i/cl99pg20n3oz9c9f",
    "installmentNumber": null,
    "couponCode": null,
    "discountAmount": null,
    "originalValue": null,
    "createdAt": "2026-04-19T18:10:30.123",
    "updatedAt": "2026-04-19T18:10:30.123"
  }
}
```

---

## 5. Catalogo de eventos e shape do `data`

> **Regra geral:** o shape do `data` e o mesmo DTO de resposta do recurso relacionado. Ex: `ChargePaidEvent.data` = `ChargeResponse`. Campos podem estar nulos quando nao aplicaveis (ex: `pixQrcode` so aparece em cobrancas PIX).

### 5.1. Eventos de Charge

Todos carregam `data` no shape do `ChargeResponse`:

| Campo | Tipo | Observacao |
|---|---|---|
| `id` | number | ID local na Payment API |
| `companyId` | number | Tenant |
| `customerId` | number | Cliente dono da cobranca |
| `subscriptionId` | number \| null | Se veio de uma assinatura |
| `installmentId` | number \| null | Se e parte de parcelamento |
| `asaasId` | string | ID do pagamento no Asaas |
| `billingType` | `PIX` \| `BOLETO` \| `CREDIT_CARD` \| `UNDEFINED` | |
| `value` | number (decimal) | Valor em R$ |
| `dueDate` | ISO date | |
| `status` | `PENDING` \| `CONFIRMED` \| `RECEIVED` \| `OVERDUE` \| `CANCELED` \| `REFUNDED` | |
| `origin` | `API` \| `RECURRING` \| `PLAN_CHANGE` \| ... | Origem da cobranca |
| `externalReference` | string \| null | Correlacao externa (ex: `plan_change_4`) |
| `pixQrcode`, `pixCopyPaste` | string \| null | Preenchidos em PIX |
| `boletoUrl` | string \| null | Preenchido em boleto |
| `invoiceUrl` | string | Link do Asaas pro pagamento |
| `installmentNumber` | number \| null | 1 de 12, etc. |
| `couponCode`, `discountAmount`, `originalValue` | | Se teve cupom aplicado |
| `createdAt`, `updatedAt` | ISO datetime | |

Eventos:

| `type` | Quando dispara |
|---|---|
| `ChargeCreatedEvent` | Cobranca criada no Asaas (via API ou via assinatura recorrente) |
| `ChargePaidEvent` | Cobranca foi confirmada/recebida (webhook do Asaas chegou) |
| `ChargeCanceledEvent` | Cobranca foi cancelada (manualmente ou sistema) |
| `ChargeRefundedEvent` | Estorno total ou parcial |

### 5.2. Eventos de Subscription

`data` e o `SubscriptionResponse`:

| Campo | Tipo |
|---|---|
| `id`, `companyId`, `customerId`, `planId` | number |
| `planName` | string |
| `asaasId` | string |
| `billingType` | enum billing |
| `effectivePrice` | number (preco efetivo com cupom aplicado) |
| `cycle` | `MONTHLY` \| `QUARTERLY` \| `SEMIANNUAL` \| `ANNUAL` |
| `currentPeriodStart`, `currentPeriodEnd` | ISO datetime \| null |
| `nextDueDate` | ISO date \| null |
| `status` | `ACTIVE` \| `PAUSED` \| `SUSPENDED` \| `CANCELED` \| ... |
| `couponCode`, `couponDiscountAmount`, `couponUsesRemaining` | Se tem cupom |
| `createdAt`, `updatedAt` | ISO datetime |

Eventos:

| `type` | Quando dispara |
|---|---|
| `SubscriptionCreatedEvent` | Nova assinatura criada |
| `SubscriptionPausedEvent` | Assinatura pausada (com ou sem cupom suspenso) |
| `SubscriptionResumedEvent` | Retomada apos pausa |
| `SubscriptionSuspendedEvent` | Suspensa por inadimplencia (maxima de cobrancas vencidas atingida) |
| `SubscriptionCanceledEvent` | Cancelamento definitivo |

### 5.3. Eventos de PlanChange

`data` e o `PlanChangeResponse`:

| Campo | Tipo |
|---|---|
| `id`, `subscriptionId`, `previousPlanId`, `requestedPlanId` | number |
| `previousPlanName`, `requestedPlanName` | string |
| `changeType` | `UPGRADE` \| `DOWNGRADE` \| `SIDEGRADE` |
| `policy` | `IMMEDIATE_PRORATA` \| `IMMEDIATE_NO_PRORATA` \| `END_OF_CYCLE` |
| `deltaAmount`, `prorationCredit`, `prorationCharge` | number (decimal) |
| `status` | `PENDING` \| `AWAITING_PAYMENT` \| `EFFECTIVE` \| `SCHEDULED` \| `CANCELED` |
| `chargeId` | number \| null — cobranca pro-rata criada |
| `creditLedgerId` | number \| null — credito gerado em downgrade |
| `scheduledFor` | ISO datetime \| null — quando a troca efetivara se agendada |
| `effectiveAt` | ISO datetime \| null — quando foi efetivada |
| `requestedBy` | string \| null |
| `requestedAt` | ISO datetime |
| `failureReason` | string \| null |

Eventos:

| `type` | Quando dispara |
|---|---|
| `PlanChangeScheduledEvent` | Troca agendada pro fim do ciclo (policy END_OF_CYCLE ou downgrade diferido) |
| `PlanChangePendingPaymentEvent` | Upgrade com policy IMMEDIATE_PRORATA — cobranca pro-rata criada, aguardando pagamento |
| `PlanChangedEvent` | Troca efetivamente concluida (plano novo ativo) |

### 5.4. Eventos de Customer

`data` e o `CustomerResponse`:

| Campo | Tipo |
|---|---|
| `id`, `companyId` | number |
| `asaasId` | string |
| `name`, `document` (CPF/CNPJ), `email`, `phone` | string |
| `address*` (street, number, complement, neighborhood, city, state, postalCode) | string \| null |
| `creditBalance` | number (decimal) |
| `createdAt`, `updatedAt` | ISO datetime |

Eventos:

| `type` | Quando dispara |
|---|---|
| `CustomerCreatedEvent` | Novo cliente cadastrado (via API ou import) |

### 5.5. WebhookTestEvent

Disparado pelo botao "Ping" no painel. `data` e um objeto livre de teste:

```json
{
  "data": {
    "message": "Este e um evento de teste enviado pelo botao de ping.",
    "subscriptionId": 7,
    "timestamp": "2026-04-19T18:10:30.123"
  }
}
```

Use pra validar que o endpoint esta recebendo. Nao dispare acao de negocio.

---

## 6. Idempotencia

**Voce deve tratar.** A Payment API pode reentregar o mesmo evento se:

- Seu endpoint respondeu lento (timeout > 10s) e a entrega contou como falha.
- Seu endpoint respondeu 2xx mas a Payment API perdeu a confirmacao (rede, crash).
- Reprocessamento manual via `/admin/webhooks/{id}/replay`.

### 6.1. Qual id usar?

- **`id` do envelope** (= `X-Event-Id` no header): **estavel** entre tentativas do mesmo evento.
- **`X-Delivery-Id`**: muda a cada tentativa. **NAO use pra idempotencia de negocio** — use so pra log/auditoria.

Regra: indexe por `eventId` do envelope.

### 6.2. Implementacao sugerida

Tabela interna simples:

```sql
CREATE TABLE received_webhooks (
    event_id VARCHAR(100) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);
```

Fluxo:
```
1. Receber request.
2. Validar token.
3. Ler envelope.id.
4. INSERT na tabela. Se conflito (event_id ja existe):
   - Se processing_status = PROCESSED → responder 200 idempotente.
   - Se processing_status = PENDING/FAILED → responder 200 (re-enfileirar) ou 202.
5. Caso contrario → enfileirar para processamento async, responder 200.
```

A chave primaria `event_id` garante que o mesmo evento nunca e processado duas vezes.

---

## 7. Contrato de resposta

| Status HTTP | Significado para a Payment API |
|---|---|
| **2xx** (200, 201, 202, 204) | Sucesso. Evento marcado como `DELIVERED`. Nao tenta mais. |
| **4xx** (exceto 408, 429) | Erro permanente do lado do receptor. Entra em retry mesmo assim (sera refeito ate 10x). |
| **408 Request Timeout**, **429 Too Many Requests** | Tratado como temporario. Retry com backoff. |
| **5xx** | Erro no receptor. Retry com backoff. |
| **Timeout (sem resposta em 10s)** | Tratado como falha. Retry com backoff. |
| **Erro de rede/SSL** | Retry com backoff. |

Recomendacoes:

- **Retorne 200 o mais rapido possivel** apos persistir. Rode a logica de negocio async.
- **Nao retorne 2xx se a validacao de token falhou** — retorne 401. Mas lembre que ate 4xx sera retentado, entao so configure corretamente do lado da Payment API (token, URL).
- **Body da resposta e opcional.** Payment API so olha o status code. Se quiser retornar algo (ex: `{ "ok": true, "deliveryId": "142" }`), fica no log do historico por ate 2KB.

---

## 8. Retry e DLQ (lado Payment API)

Para voce saber o que esperar sem precisar entrar no painel:

Schedule de tentativas (ate **10 tentativas totais**):

| Tentativa | Espera apos falha anterior |
|---|---|
| 1 | (imediato) |
| 2 | 5s |
| 3 | 30s |
| 4 | 2min |
| 5 | 10min |
| 6 | 1h |
| 7 | 1h |
| 8 | 1h |
| 9 | 1h |
| 10 | 1h |

Total maximo antes de desistir: ~6h20min.

Apos a 10a falha, o evento vai pra **DLQ** (Dead Letter Queue) no lado da Payment API. Nao havera nova tentativa automatica. Voce pode:
- Ver no painel de "Historico de entregas" do webhook.
- Reprocessar manualmente via `POST /admin/webhooks/{id}/replay` (apenas `HOLDING_ADMIN`/`SYSTEM`).

### 8.1. Implicacao pro seu sistema

Se seu endpoint estiver fora do ar por mais de ~6h, **voce perde eventos**. Monitore:
- Alertas de downtime do seu endpoint.
- Alertas de "muitos webhooks em DLQ" no lado da Payment API (pode expor metrica/endpoint pra voce puxar).

Boa pratica: ter um job de reconciliacao periodica que chama `GET /api/v1/charges?updatedAfter=...` pra comparar com o que voce ja processou e pegar o que eventualmente falhou.

---

## 9. Checklist de seguranca

- [ ] Endpoint **so em HTTPS** em producao.
- [ ] Token guardado em **secret manager** ou variavel de ambiente (nunca no codigo).
- [ ] Validacao do token com **comparacao tempo-constante**.
- [ ] **Nao logar o token** nos logs do seu sistema (mascarar com `****`).
- [ ] Nao retornar detalhes internos no body da resposta (nao exponha stack traces).
- [ ] Rate limit no seu endpoint se possivel — Payment API manda muitos eventos em picos (processamento em lote).
- [ ] Timeout generoso do lado do seu servidor HTTP (> 10s) pra nao cortar a conexao antes da Payment API.
- [ ] Rotacao periodica do token (trimestral?) via endpoint de rotate. Coordenar com deploy do consumer.
- [ ] Tamanho de body: Payment API envia ate ~10KB por evento. Configure seu body parser com `limit` adequado.

---

## 10. Depuracao

### Cenarios comuns

**Problema: nao chega nada no endpoint.**
1. Abra "Historico de entregas" no painel da Payment API → veja se ha attempts.
2. Se houver com status `FAILED`: olhe `responseStatus` e `errorMessage`.
3. Se o erro for `connect timed out` / `ConnectException`: sua URL nao esta acessivel a partir do servidor da Payment API. Verifique DNS, firewall, status do servico.
4. Se o erro for SSL: cert invalido/autossinado — use cert valido (Let's Encrypt).

**Problema: chega mas responde 401.**
1. Token no `Authorization` nao bate com o configurado. Compare os **primeiros 12 chars** com o `tokenPrefix` da subscription.
2. Se bater o prefix mas nao o valor completo: rotacione o token e atualize sua config.

**Problema: mesma cobranca notifica o cliente duas vezes.**
1. Idempotencia nao foi implementada. Ver secao 6.

**Problema: evento nao chega mas outros chegam.**
1. Verifique se o evento esta no `eventTypes` da sua subscription. Se usar lista especifica, falta adicionar. Alternativa: usar `["*"]`.

### Ferramentas

- **Botao "Ping"** no painel do webhook — manda um `WebhookTestEvent` sem valor real.
- **Historico de entregas** — mostra request body, response body (trecho), duracao, erro.
- **ngrok / Cloudflared Tunnel** — pra testar localmente expondo porta local via URL publica.

---

## 11. Exemplo de implementacao

Pseudo-codigo neutro. A ideia e igual em Java/Go/Python/Node/Ruby.

### 11.1. Route handler

```
POST /webhooks/payment-api:

    expectedToken = env("PAYMENT_API_WEBHOOK_TOKEN")

    auth = request.header("Authorization")
    token = auth.startsWith("Bearer ") ? auth.substring(7) : null
    if token == null or not timingSafeEquals(token, expectedToken):
        return 401

    envelope = json.parse(request.body)   // { id, type, occurredAt, companyId, resource, data }
    if envelope.id is null or envelope.type is null:
        return 400

    try:
        inserted = received_webhooks.insertIfNotExists({
            event_id:   envelope.id,
            event_type: envelope.type,
            payload:    request.body,
            received_at: now()
        })

        if inserted:
            enqueueForProcessing(envelope.id)   // fila interna: BullMQ, SQS, DB poll, etc.
        // se ja existia, e replay — nao enfileira de novo, mas responde ok

        return 200 { "ok": true, "eventId": envelope.id }

    catch DatabaseError as e:
        log.error("webhook persist failed", e)
        return 500   // Payment API retenta
```

### 11.2. Worker async

```
loop:
    eventId = takeFromQueue()
    record = received_webhooks.findById(eventId)
    envelope = json.parse(record.payload)

    try:
        switch envelope.type:
            case "ChargeCreatedEvent":       handleChargeCreated(envelope.data)
            case "ChargePaidEvent":          handleChargePaid(envelope.data)
            case "ChargeRefundedEvent":      handleChargeRefunded(envelope.data)
            case "SubscriptionCanceledEvent": handleSubCanceled(envelope.data)
            case "PlanChangedEvent":         handlePlanChanged(envelope.data)
            // ... demais
            case "WebhookTestEvent":         log.info("ping ok"); // no-op

        received_webhooks.update(eventId, {
            processing_status: "PROCESSED",
            processed_at: now()
        })

    catch e:
        log.error("event handler failed", envelope.type, e)
        received_webhooks.update(eventId, {
            processing_status: "FAILED",
            last_error: e.message
        })
        // nao aciona retry na Payment API — isso e responsabilidade sua agora.
        // Pode tentar de novo no proximo ciclo do worker, com backoff interno.
```

As funcoes `handleXxx` sao **onde voce coloca a logica de negocio** — disparar email, push, sync com painel admin, etc. Fica totalmente por conta do seu sistema.

---

## 12. Resumo rapido

| O que | Como |
|---|---|
| Cadastrar webhook | `POST /api/v1/webhook-subscriptions` — guardar `rawToken` imediatamente |
| Autenticar requests | Validar `Authorization: Bearer <rawToken>` em toda chamada recebida |
| Parse do body | Envelope `{ id, type, occurredAt, companyId, resource, data }` |
| Identificador estavel | `id` do envelope (= `X-Event-Id`) |
| Idempotencia | Indexar recebidos por `event_id`, ignorar duplicados |
| Resposta | **2xx em menos de 10s**, processar negocio async |
| Se falhar | Retry automatico ate 10 tentativas, ~6h20min janela total |
| Testar | Botao "Ping" no painel dispara `WebhookTestEvent` |

Duvidas ou ajustes no contrato podem ser tratados no time da Payment API. Este documento reflete o estado atual da API na versao com o modulo de Webhook Subscriptions (migration V10).
