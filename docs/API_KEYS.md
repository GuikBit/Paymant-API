# API Keys — Guia Completo

Guia para criacao, gerenciamento e uso de API Keys para integracao sistema-a-sistema com a API de Pagamento.

---

## Visao Geral

API Keys permitem que sistemas externos (ERP, CRM, e-commerce, etc.) acessem a API de pagamento sem precisar de login com email/senha. Cada API Key:

- Esta vinculada a uma **empresa** (tenant)
- Possui **roles** configuraveis (define o que o sistema externo pode fazer)
- Possui **expiracao** opcional
- Registra a **data do ultimo uso**
- Pode ser **revogada** a qualquer momento

> **Importante:** A chave completa (`rawKey`) e exibida **apenas uma vez**, no momento da criacao. Armazene-a em local seguro (variavel de ambiente, cofre de secrets, etc.). Apos a criacao, somente o prefixo da chave sera visivel para identificacao.

---

## Pre-requisitos

Para gerenciar API Keys voce precisa estar autenticado com um usuario que possua uma das roles:

- `ROLE_HOLDING_ADMIN` — Administrador geral da holding
- `ROLE_COMPANY_ADMIN` — Administrador da empresa

Todos os endpoints de gerenciamento estao em `/api/v1/api-keys`.

---

## 1. Criar uma API Key

### `POST /api/v1/api-keys`

### Headers

```
Authorization: Bearer <seu_jwt_token>
X-Company-Id: <id_da_empresa>
Content-Type: application/json
```

### Body

```json
{
  "name": "Integracao ERP",
  "description": "Chave usada pelo ERP para consultar clientes e cobrancas",
  "roles": ["ROLE_COMPANY_OPERATOR"],
  "expiresAt": "2027-12-31T23:59:59"
}
```

| Campo | Tipo | Obrigatorio | Descricao |
|-------|------|:-----------:|-----------|
| `name` | string | Sim | Nome identificador da key (ex: "ERP", "E-commerce", "BI") |
| `description` | string | Nao | Descricao do proposito da key |
| `roles` | array | Sim | Roles que esta key tera ao autenticar (ver tabela abaixo) |
| `expiresAt` | datetime | Nao | Data de expiracao. Se nulo, a key nao expira |

### Roles disponiveis

| Role | Permissoes |
|------|-----------|
| `ROLE_COMPANY_OPERATOR` | Leitura e operacoes basicas (clientes, cobrancas, assinaturas) |
| `ROLE_COMPANY_ADMIN` | Tudo do operator + gerenciamento de usuarios e configuracoes |
| `ROLE_HOLDING_ADMIN` | Acesso total a todas as empresas (usar com extrema cautela) |

> **Recomendacao:** Use sempre o **menor privilegio necessario**. Para integracoes que apenas consultam dados, use `ROLE_COMPANY_OPERATOR`.

### Exemplo com cURL

```bash
curl -X POST http://localhost:8080/api/v1/api-keys \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "X-Company-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Integracao ERP",
    "description": "Consulta de clientes e cobrancas pelo ERP",
    "roles": ["ROLE_COMPANY_OPERATOR"],
    "expiresAt": "2027-12-31T23:59:59"
  }'
```

### Resposta (201 Created)

```json
{
  "apiKey": {
    "id": 1,
    "companyId": 1,
    "keyPrefix": "pk_abc12",
    "name": "Integracao ERP",
    "description": "Consulta de clientes e cobrancas pelo ERP",
    "roles": ["ROLE_COMPANY_OPERATOR"],
    "active": true,
    "lastUsedAt": null,
    "expiresAt": "2027-12-31T23:59:59",
    "createdAt": "2026-04-11T20:00:00"
  },
  "rawKey": "pk_abc123defGHIjklMNOpqrSTUvwxYZ0123456789ABCDEFGHIJKLMNOP"
}
```

> **ATENCAO:** Copie e armazene o valor de `rawKey` imediatamente. Esta e a **unica vez** que ele sera exibido. O campo `rawKey` nao pode ser recuperado posteriormente.

---

## 2. Listar API Keys

### `GET /api/v1/api-keys`

Lista todas as API Keys da empresa do usuario autenticado. A chave completa nunca e exibida — apenas o `keyPrefix` para identificacao.

### Headers

```
Authorization: Bearer <seu_jwt_token>
X-Company-Id: <id_da_empresa>
```

### Query params (paginacao)

| Parametro | Tipo | Default | Descricao |
|-----------|------|---------|-----------|
| `page` | int | 0 | Numero da pagina (zero-based) |
| `size` | int | 20 | Registros por pagina |
| `sort` | string | — | Ordenacao. Ex: `createdAt,desc` |

### Exemplo com cURL

```bash
curl -X GET "http://localhost:8080/api/v1/api-keys?page=0&size=10&sort=createdAt,desc" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "X-Company-Id: 1"
```

### Resposta (200 OK)

```json
{
  "content": [
    {
      "id": 1,
      "companyId": 1,
      "keyPrefix": "pk_abc12",
      "name": "Integracao ERP",
      "description": "Consulta de clientes e cobrancas pelo ERP",
      "roles": ["ROLE_COMPANY_OPERATOR"],
      "active": true,
      "lastUsedAt": "2026-04-11T22:15:00",
      "expiresAt": "2027-12-31T23:59:59",
      "createdAt": "2026-04-11T20:00:00"
    },
    {
      "id": 2,
      "companyId": 1,
      "keyPrefix": "pk_xyz98",
      "name": "E-commerce",
      "description": null,
      "roles": ["ROLE_COMPANY_OPERATOR"],
      "active": false,
      "lastUsedAt": "2026-04-10T14:30:00",
      "expiresAt": null,
      "createdAt": "2026-03-01T10:00:00"
    }
  ],
  "pageable": { "pageNumber": 0, "pageSize": 10 },
  "totalElements": 2,
  "totalPages": 1
}
```

### Campos uteis para monitoramento

| Campo | O que indica |
|-------|-------------|
| `active` | `true` = em uso, `false` = revogada |
| `lastUsedAt` | Ultima vez que foi usada para autenticar. `null` = nunca usada |
| `expiresAt` | `null` = sem expiracao |
| `keyPrefix` | Primeiros caracteres da chave, para identificar qual e qual |

---

## 3. Revogar (Deletar) uma API Key

### `DELETE /api/v1/api-keys/{id}`

Revoga permanentemente uma API Key. Sistemas que estejam usando esta chave **perderao o acesso imediatamente**.

### Headers

```
Authorization: Bearer <seu_jwt_token>
X-Company-Id: <id_da_empresa>
```

### Exemplo com cURL

```bash
curl -X DELETE http://localhost:8080/api/v1/api-keys/2 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "X-Company-Id: 1"
```

### Resposta (204 No Content)

Sem corpo na resposta. A key foi revogada com sucesso.

### Erros possiveis

| Status | Motivo |
|--------|--------|
| 404 | API Key com este ID nao encontrada |
| 400 | API Key ja estava revogada |
| 403 | Usuario sem permissao (role insuficiente) |

---

## 4. Usar a API Key em Outro Sistema

Com a `rawKey` em maos, o sistema externo pode autenticar em qualquer endpoint da API enviando o header `X-API-Key`.

### Header de autenticacao

```
X-API-Key: pk_abc123defGHIjklMNOpqrSTUvwxYZ0123456789ABCDEFGHIJKLMNOP
```

> **Nota:** Ao usar API Key, os headers `Authorization` e `X-Company-Id` **nao sao necessarios**. A empresa e as permissoes sao resolvidas automaticamente a partir da key.

### Exemplos de uso

#### Listar clientes

```bash
curl -X GET http://localhost:8080/api/v1/customers \
  -H "X-API-Key: pk_abc123defGHIjklMNOpqrSTUvwxYZ0123456789ABCDEFGHIJKLMNOP"
```

#### Buscar cobranca por ID

```bash
curl -X GET http://localhost:8080/api/v1/charges/42 \
  -H "X-API-Key: pk_abc123defGHIjklMNOpqrSTUvwxYZ0123456789ABCDEFGHIJKLMNOP"
```

#### Criar uma cobranca

```bash
curl -X POST http://localhost:8080/api/v1/charges \
  -H "X-API-Key: pk_abc123defGHIjklMNOpqrSTUvwxYZ0123456789ABCDEFGHIJKLMNOP" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 10,
    "billingType": "PIX",
    "value": 150.00,
    "dueDate": "2026-05-01",
    "description": "Mensalidade maio/2026"
  }'
```

#### Consultar dashboard

```bash
curl -X GET http://localhost:8080/api/v1/reports/dashboard \
  -H "X-API-Key: pk_abc123defGHIjklMNOpqrSTUvwxYZ0123456789ABCDEFGHIJKLMNOP"
```

### Exemplo em linguagens de programacao

#### Python

```python
import requests

API_KEY = "pk_abc123defGHIjklMNOpqrSTUvwxYZ0123456789ABCDEFGHIJKLMNOP"
BASE_URL = "http://localhost:8080/api/v1"

headers = {"X-API-Key": API_KEY}

# Listar clientes
response = requests.get(f"{BASE_URL}/customers", headers=headers)
clientes = response.json()

# Criar cobranca
payload = {
    "customerId": 10,
    "billingType": "PIX",
    "value": 150.00,
    "dueDate": "2026-05-01",
    "description": "Mensalidade maio/2026"
}
response = requests.post(f"{BASE_URL}/charges", json=payload, headers=headers)
cobranca = response.json()
```

#### Node.js / TypeScript

```typescript
const API_KEY = "pk_abc123defGHIjklMNOpqrSTUvwxYZ0123456789ABCDEFGHIJKLMNOP";
const BASE_URL = "http://localhost:8080/api/v1";

const headers = {
  "X-API-Key": API_KEY,
  "Content-Type": "application/json",
};

// Listar clientes
const clientes = await fetch(`${BASE_URL}/customers`, { headers })
  .then((res) => res.json());

// Criar cobranca
const cobranca = await fetch(`${BASE_URL}/charges`, {
  method: "POST",
  headers,
  body: JSON.stringify({
    customerId: 10,
    billingType: "PIX",
    value: 150.0,
    dueDate: "2026-05-01",
    description: "Mensalidade maio/2026",
  }),
}).then((res) => res.json());
```

#### C# / .NET

```csharp
using var client = new HttpClient();
client.BaseAddress = new Uri("http://localhost:8080/api/v1/");
client.DefaultRequestHeaders.Add("X-API-Key",
    "pk_abc123defGHIjklMNOpqrSTUvwxYZ0123456789ABCDEFGHIJKLMNOP");

// Listar clientes
var clientes = await client.GetFromJsonAsync<Page<Customer>>("customers");

// Criar cobranca
var response = await client.PostAsJsonAsync("charges", new {
    customerId = 10,
    billingType = "PIX",
    value = 150.00,
    dueDate = "2026-05-01",
    description = "Mensalidade maio/2026"
});
```

#### PHP

```php
$apiKey = "pk_abc123defGHIjklMNOpqrSTUvwxYZ0123456789ABCDEFGHIJKLMNOP";
$baseUrl = "http://localhost:8080/api/v1";

$opts = [
    "http" => [
        "header" => "X-API-Key: $apiKey\r\nContent-Type: application/json",
    ]
];
$context = stream_context_create($opts);

// Listar clientes
$clientes = json_decode(file_get_contents("$baseUrl/customers", false, $context));
```

---

## 5. Respostas de Erro

Quando a autenticacao via API Key falha, a API retorna:

| Situacao | Status HTTP | Causa |
|----------|:-----------:|-------|
| Header `X-API-Key` ausente e sem JWT | 401 | Nenhuma autenticacao fornecida |
| Key invalida (hash nao encontrado) | 401 | Chave nao existe ou esta incorreta |
| Key revogada (`active = false`) | 401 | Key foi revogada via `DELETE` |
| Key expirada (`expiresAt` no passado) | 401 | Key passou da data de expiracao |
| Role insuficiente para o endpoint | 403 | Key nao tem a role necessaria |

---

## 6. Boas Praticas

### Seguranca

- **Nunca** exponha a `rawKey` em codigo-fonte, logs ou repositorios Git
- Armazene a key em **variaveis de ambiente** ou em um **gerenciador de secrets** (AWS Secrets Manager, HashiCorp Vault, Azure Key Vault, etc.)
- Crie keys com **o menor privilegio necessario** (`ROLE_COMPANY_OPERATOR` para integracao de leitura)
- Defina **data de expiracao** para keys temporarias ou de parceiros
- **Revogue imediatamente** keys que possam ter sido comprometidas

### Monitoramento

- Consulte `lastUsedAt` periodicamente para identificar keys que nao estao sendo usadas
- Revogue keys inativas ha muito tempo
- Monitore o campo `active` para garantir que keys revogadas nao estao sendo usadas

### Organizacao

- Use nomes descritivos: `"ERP Producao"`, `"BI Dashboard"`, `"E-commerce Staging"`
- Preencha a `description` com o proposito e o sistema responsavel
- Crie **uma key por sistema** — nao compartilhe a mesma key entre sistemas diferentes
- Ao rotacionar keys: crie a nova key primeiro, atualize o sistema externo, depois revogue a antiga

---

## 7. Resumo Rapido

| Acao | Metodo | Endpoint | Autenticacao |
|------|--------|----------|-------------|
| Criar key | `POST` | `/api/v1/api-keys` | JWT (admin) |
| Listar keys | `GET` | `/api/v1/api-keys` | JWT (admin) |
| Revogar key | `DELETE` | `/api/v1/api-keys/{id}` | JWT (admin) |
| Usar key | — | Qualquer endpoint | Header `X-API-Key` |
