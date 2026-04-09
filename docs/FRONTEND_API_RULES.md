# Frontend x API - Guia Completo de Endpoints, Roles e Regras

Documento de referencia para o frontend. Define exatamente o que cada role pode fazer,
quais headers enviar, como tratar erros, e a ordem correta de desenvolvimento por fases.

---

## 1. Autenticacao e Headers Obrigatorios

### 1.1 Login

```
POST /api/v1/auth/login     (PUBLICO - sem token)
Body: { "email": "...", "password": "..." }
Response: { "accessToken": "...", "refreshToken": "...", "expiresIn": 86400000 }
```

Apos o login, decodifique o `accessToken` (base64 do payload JWT) para extrair:

```json
{
  "sub": "admin@holding.dev",
  "company_id": 1,
  "user_id": 1,
  "roles": "ROLE_HOLDING_ADMIN",
  "name": "Admin Dev",
  "exp": 1712800000
}
```

### 1.2 Headers em TODA request autenticada

```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**NAO envie** `X-Company-Id` manualmente. O backend extrai o `company_id` direto do JWT.
O header `X-Company-Id` so e usado internamente para chamadas de sistema (ROLE_SYSTEM).

### 1.3 Refresh Token

```
POST /api/v1/auth/refresh    (PUBLICO - sem token)
Body: { "refreshToken": "..." }
Response: { "accessToken": "...", "refreshToken": "...", "expiresIn": 86400000 }
```

O frontend deve interceptar respostas 401 e tentar refresh automaticamente.
Se o refresh tambem falhar, redirecionar para `/login`.

### 1.4 Idempotency-Key (somente POST de criacao)

```
Idempotency-Key: {uuid-v4}
```

Enviar em todas as requests POST que criam recursos (charges, subscriptions, customers, etc.).
Se o usuario clicar 2x no botao, a API retorna a mesma resposta sem duplicar.

---

## 2. Mapa de Roles

### 2.1 Roles existentes

| Role | Descricao | Escopo |
|------|-----------|--------|
| `ROLE_HOLDING_ADMIN` | Super admin da holding | Cross-tenant: ve e gerencia TODAS as empresas |
| `ROLE_COMPANY_ADMIN` | Admin de uma empresa | Single-tenant: gerencia tudo DENTRO da empresa |
| `ROLE_COMPANY_OPERATOR` | Operador de uma empresa | Single-tenant: operacoes do dia-a-dia, sem criar usuarios |
| `ROLE_SYSTEM` | Integracao M2M | Usado para webhooks admin e automacoes |

### 2.2 Hierarquia de criacao de usuarios

```
HOLDING_ADMIN  ─── cria ──→  COMPANY_ADMIN  (em qualquer empresa)
HOLDING_ADMIN  ─── cria ──→  COMPANY_OPERATOR (em qualquer empresa)
COMPANY_ADMIN  ─── cria ──→  COMPANY_ADMIN  (somente na propria empresa)
COMPANY_ADMIN  ─── cria ──→  COMPANY_OPERATOR (somente na propria empresa)
COMPANY_OPERATOR             NAO pode criar usuarios
```

### 2.3 O que cada role pode acessar no frontend

#### ROLE_HOLDING_ADMIN - Menu completo

```
Sidebar:
  ✅ Dashboard
  ✅ Empresas          (CRUD completo + credenciais Asaas)
  ✅ Clientes          (CRUD + restaurar excluidos + sync)
  ✅ Planos            (CRUD + versionar + ativar/desativar)
  ✅ Cobrancas         (CRUD + reembolso + cancelar + PIX/Boleto/CC)
  ✅ Assinaturas       (CRUD + pausar/retomar + mudar plano)
  ✅ Webhooks Admin    (listar + replay)
  ✅ Outbox Admin      (listar + retry)
  ✅ Reconciliacao     (executar + replay DLQ)
  ✅ Relatorios        (todos + export CSV)
  ✅ Usuarios          (criar em qualquer empresa)
```

#### ROLE_COMPANY_ADMIN - Sem area admin da holding

```
Sidebar:
  ✅ Dashboard
  ❌ Empresas          (403 - nao tem acesso)
  ✅ Clientes          (CRUD + restaurar excluidos + sync)
  ✅ Planos            (CRUD + versionar + ativar/desativar)
  ✅ Cobrancas         (CRUD + reembolso + cancelar + PIX/Boleto/CC)
  ✅ Assinaturas       (CRUD + pausar/retomar + mudar plano)
  ❌ Webhooks Admin    (403 - nao tem acesso)
  ❌ Outbox Admin      (403 - nao tem acesso)
  ❌ Reconciliacao     (403 - nao tem acesso)
  ✅ Relatorios        (todos + export CSV)
  ✅ Usuarios          (criar somente na propria empresa)
```

#### ROLE_COMPANY_OPERATOR - Somente operacoes

```
Sidebar:
  ✅ Dashboard
  ❌ Empresas          (403)
  ✅ Clientes          (criar, listar, ver, editar, excluir, sync)
  ⚠️ Clientes          (NAO pode restaurar excluidos - 403 no POST /{id}/restore)
  ✅ Planos            (CRUD + versionar + ativar/desativar)
  ✅ Cobrancas         (CRUD + reembolso + cancelar + PIX/Boleto/CC)
  ✅ Assinaturas       (CRUD + pausar/retomar + mudar plano)
  ❌ Webhooks Admin    (403)
  ❌ Outbox Admin      (403)
  ❌ Reconciliacao     (403)
  ✅ Relatorios        (todos + export CSV)
  ❌ Usuarios          (NAO pode criar - 403 no POST /auth/users)
```

---

## 3. Regras do Frontend por Tela

### REGRA GERAL DE VISIBILIDADE

```typescript
// No frontend, use estas funcoes para controlar o que mostrar:
const roles = decodeJwt(accessToken).roles.split(",");

const isHoldingAdmin   = roles.includes("ROLE_HOLDING_ADMIN");
const isCompanyAdmin   = roles.includes("ROLE_COMPANY_ADMIN");
const isCompanyOperator = roles.includes("ROLE_COMPANY_OPERATOR");

const canManageCompanies = isHoldingAdmin;
const canManageUsers     = isHoldingAdmin || isCompanyAdmin;
const canAccessAdmin     = isHoldingAdmin;
const canRestoreCustomer = isHoldingAdmin || isCompanyAdmin;
```

---

## 4. Endpoints por Fase de Desenvolvimento

---

### FASE 1 - Autenticacao e Estrutura Base

**Objetivo:** Login funcional, armazenamento do token, rotas protegidas, decode do JWT.

#### Endpoints utilizados:

```
POST /api/v1/auth/login          PUBLICO
POST /api/v1/auth/refresh        PUBLICO
```

#### Telas:
- **Login** (`/login`)
- **Layout base** (sidebar, header, rota protegida)

#### Regras:
1. Salvar `accessToken` e `refreshToken` (localStorage ou Zustand persist)
2. Decodificar JWT para extrair: `company_id`, `roles`, `name`, `user_id`
3. Montar sidebar baseado nas roles (ver secao 2.3)
4. Interceptor Axios:
   - Request: adicionar `Authorization: Bearer {token}`
   - Response 401: tentar refresh, se falhar -> logout
5. `ProtectedRoute`: se nao autenticado, redirect para `/login`
6. `RoleGuard`: se role insuficiente, mostrar "Acesso Negado" ou esconder rota

#### Erros possiveis:
| Status | Causa | Acao no Frontend |
|--------|-------|------------------|
| 422 | Email ou senha invalidos | Toast: "Email ou senha invalidos" |
| 422 | Refresh token invalido/expirado | Redirect para /login |

#### Exemplo de request:
```typescript
// Login
const { data } = await api.post("/api/v1/auth/login", {
  email: "admin@holding.dev",
  password: "admin123"
});
// data = { accessToken: "eyJ...", refreshToken: "eyJ...", expiresIn: 86400000 }

// Decodificar (sem lib externa)
const payload = JSON.parse(atob(data.accessToken.split(".")[1]));
// payload = { sub: "admin@holding.dev", company_id: 1, roles: "ROLE_HOLDING_ADMIN", ... }
```

---

### FASE 2 - Gerenciamento de Empresas (HOLDING_ADMIN only)

**Objetivo:** CRUD de empresas, configuracao de credenciais Asaas, teste de conexao.

#### Acesso: somente `ROLE_HOLDING_ADMIN`

Se o usuario logado NAO for HOLDING_ADMIN:
- Esconder "Empresas" do menu
- Se tentar acessar `/companies` diretamente, mostrar "Acesso Negado"

#### Endpoints:

```
GET    /api/v1/companies                         → Lista paginada
POST   /api/v1/companies                         → Criar empresa
GET    /api/v1/companies/{id}                     → Detalhe
PUT    /api/v1/companies/{id}                     → Atualizar
PUT    /api/v1/companies/{id}/credentials         → Configurar chave Asaas
POST   /api/v1/companies/{id}/test-connection     → Testar conexao
```

#### Telas:
- **CompanyList** (`/companies`)
- **CompanyForm** (`/companies/new` e `/companies/:id/edit`)
- **CompanyDetail** (`/companies/:id`)

#### Regras de negocio:

**Criar empresa:**
```typescript
POST /api/v1/companies
Headers: { Authorization: "Bearer {token}" }
Body: {
  "cnpj": "12345678000190",          // obrigatorio, 14-18 chars
  "razaoSocial": "Empresa X LTDA",   // obrigatorio
  "nomeFantasia": "Empresa X",       // opcional
  "email": "contato@empresax.com",   // opcional
  "phone": "11999999999",            // opcional
  "asaasApiKey": "...",              // opcional (pode configurar depois)
  "asaasEnv": "SANDBOX",            // SANDBOX ou PRODUCTION
  "planChangePolicy": "IMMEDIATE_PRORATA",    // opcional
  "downgradeValidationStrategy": "BLOCK",     // opcional
  "gracePeriodDays": 7                        // opcional
}
```

**Configurar credenciais Asaas (separado do CRUD):**
```typescript
PUT /api/v1/companies/{id}/credentials
Body: {
  "asaasApiKey": "$aact_YTU5...",    // obrigatorio
  "asaasEnv": "SANDBOX"              // obrigatorio: SANDBOX ou PRODUCTION
}
Response: 204 No Content
```

**Testar conexao:**
```typescript
POST /api/v1/companies/{id}/test-connection
Response: { "success": true, "message": "Conexao OK" }
    ou   { "success": false, "message": "API key invalida" }
```

#### Erros possiveis:
| Status | Causa | Acao |
|--------|-------|------|
| 403 | Usuario nao e HOLDING_ADMIN | "Acesso negado" |
| 404 | Empresa nao encontrada | "Empresa nao encontrada" |
| 422 | CNPJ duplicado ou invalido | Mostrar erro no campo |
| 502 | Asaas fora do ar (test-connection) | "Erro ao conectar com Asaas" |

#### Campos de politica (explicacao para o form):
```
PlanChangePolicy:
  IMMEDIATE_PRORATA    = "Mudanca imediata com calculo pro-rata"
  END_OF_CYCLE         = "Mudanca no fim do ciclo de cobranca"
  IMMEDIATE_NO_PRORATA = "Mudanca imediata sem ajuste de valor"

DowngradeValidationStrategy:
  BLOCK        = "Bloquear downgrade se condições não forem atendidas"
  SCHEDULE     = "Agendar downgrade para depois"
  GRACE_PERIOD = "Aplicar periodo de carencia antes do downgrade"

CompanyStatus:
  ACTIVE     = verde
  SUSPENDED  = amarelo
  DEFAULTING = vermelho
```

---

### FASE 3 - Gerenciamento de Usuarios

**Objetivo:** Criar usuarios para empresas com roles especificas.

#### Acesso: `ROLE_HOLDING_ADMIN` ou `ROLE_COMPANY_ADMIN`

#### Endpoint:

```
POST /api/v1/auth/users
```

#### Regras:

**HOLDING_ADMIN pode:**
- Criar usuario em QUALQUER empresa (passa qualquer `companyId`)
- Atribuir qualquer role

**COMPANY_ADMIN pode:**
- Criar usuario SOMENTE na propria empresa
- O `companyId` no body deve ser o mesmo `company_id` do JWT dele
- Se enviar um `companyId` diferente, o backend cria na empresa informada
  (ATENCAO: a API atual nao valida isso - o frontend deve forcar o companyId do JWT)

**COMPANY_OPERATOR:**
- NAO pode criar usuarios (403)

#### Request:
```typescript
POST /api/v1/auth/users
Body: {
  "companyId": 2,                              // obrigatorio
  "email": "joao@empresa.com",                 // obrigatorio, formato email
  "password": "senhaSegura123",                // obrigatorio, min 8 chars
  "name": "Joao Silva",                        // obrigatorio
  "roles": ["ROLE_COMPANY_ADMIN"]              // obrigatorio, Set de roles
}

Response (201):
{
  "id": 5,
  "companyId": 2,
  "email": "joao@empresa.com",
  "name": "Joao Silva",
  "roles": ["ROLE_COMPANY_ADMIN"],
  "active": true,
  "createdAt": "2026-04-09T10:00:00"
}
```

#### Regras do formulario por role do usuario logado:

```
Se HOLDING_ADMIN logado:
  - Campo "Empresa": SELECT com lista de empresas (GET /api/v1/companies)
  - Campo "Roles":   Todas as opcoes disponiveis
    [ ] ROLE_HOLDING_ADMIN
    [ ] ROLE_COMPANY_ADMIN
    [ ] ROLE_COMPANY_OPERATOR

Se COMPANY_ADMIN logado:
  - Campo "Empresa": HIDDEN (fixo = company_id do JWT, nao editavel)
  - Campo "Roles":   Somente roles da empresa
    [ ] ROLE_COMPANY_ADMIN
    [ ] ROLE_COMPANY_OPERATOR
```

#### Erros possiveis:
| Status | Causa | Acao |
|--------|-------|------|
| 403 | OPERATOR tentando criar usuario | "Voce nao tem permissao" |
| 404 | companyId invalido | "Empresa nao encontrada" |
| 422 | Email ja cadastrado | "Email ja esta em uso" |
| 422 | Senha < 8 caracteres | Mostrar erro no campo |

---

### FASE 4 - Clientes (Customers)

**Objetivo:** CRUD de clientes, sincronizacao com Asaas, saldo de credito.

#### Acesso: qualquer usuario autenticado (HOLDING_ADMIN, COMPANY_ADMIN, COMPANY_OPERATOR)

Os dados retornados sao **automaticamente filtrados por empresa** (RLS).
O usuario so ve clientes da propria empresa, sem excecao.

#### Endpoints:

```
POST   /api/v1/customers                         → Criar cliente
GET    /api/v1/customers                          → Listar (paginado, com busca)
GET    /api/v1/customers/{id}                     → Detalhe
PUT    /api/v1/customers/{id}                     → Atualizar
DELETE /api/v1/customers/{id}                     → Excluir (soft delete)
POST   /api/v1/customers/{id}/restore             → Restaurar (ADMIN only)
POST   /api/v1/customers/{id}/sync                → Sincronizar com Asaas
GET    /api/v1/customers/{id}/credit-balance       → Saldo + historico ledger
```

#### Regras de permissao por endpoint:

| Endpoint | HOLDING_ADMIN | COMPANY_ADMIN | COMPANY_OPERATOR |
|----------|:---:|:---:|:---:|
| POST /customers | ✅ | ✅ | ✅ |
| GET /customers | ✅ | ✅ | ✅ |
| GET /customers/{id} | ✅ | ✅ | ✅ |
| PUT /customers/{id} | ✅ | ✅ | ✅ |
| DELETE /customers/{id} | ✅ | ✅ | ✅ |
| **POST /customers/{id}/restore** | **✅** | **✅** | **❌ 403** |
| POST /customers/{id}/sync | ✅ | ✅ | ✅ |
| GET /customers/{id}/credit-balance | ✅ | ✅ | ✅ |

#### No frontend:
```typescript
// Botao "Restaurar" so aparece se o usuario pode
const showRestoreButton = isHoldingAdmin || isCompanyAdmin;
```

#### Criar cliente:
```typescript
POST /api/v1/customers
Headers: { Authorization: "Bearer {token}", "Idempotency-Key": "uuid-v4" }
Body: {
  "name": "Maria Santos",                // obrigatorio
  "document": "12345678900",             // obrigatorio, CPF ou CNPJ
  "email": "maria@email.com",            // opcional
  "phone": "11999999999",                // opcional
  "addressStreet": "Rua A",              // opcional
  "addressNumber": "100",                // opcional
  "addressComplement": "Sala 1",         // opcional
  "addressNeighborhood": "Centro",       // opcional
  "addressCity": "Sao Paulo",            // opcional
  "addressState": "SP",                  // opcional
  "addressPostalCode": "01000000"        // opcional
}
```

**IMPORTANTE:** Ao criar um cliente, a API automaticamente:
1. Cria o cliente no Asaas (usando a chave API da empresa do JWT)
2. Salva o `asaasId` retornado
3. Se a empresa NAO tem chave Asaas configurada, retorna **erro 422**

#### Listar com busca:
```typescript
GET /api/v1/customers?search=maria&page=0&size=20&sort=name,asc
// "search" filtra por nome, email ou documento
```

#### Saldo de credito:
```typescript
GET /api/v1/customers/{id}/credit-balance?page=0&size=20
Response: {
  "balance": 150.00,
  "ledger": {
    "content": [
      {
        "id": 1,
        "type": "CREDIT",           // CREDIT ou DEBIT
        "amount": 200.00,
        "origin": "DOWNGRADE_PRORATA",  // DOWNGRADE_PRORATA, MANUAL_ADJUSTMENT, REFUND, CHARGE_APPLIED
        "description": "...",
        "createdAt": "2026-04-09T10:00:00"
      }
    ],
    "totalElements": 5,
    "totalPages": 1
  }
}
```

#### Erros possiveis:
| Status | Causa | Acao |
|--------|-------|------|
| 403 | OPERATOR tentando restaurar | "Voce nao tem permissao para restaurar" |
| 404 | Cliente nao encontrado (ou de outra empresa - RLS) | "Cliente nao encontrado" |
| 422 | CPF/CNPJ invalido | Mostrar erro no campo |
| 422 | Empresa sem chave Asaas | "Configure a chave API do Asaas primeiro" |
| 502 | Asaas fora do ar | "Erro ao comunicar com Asaas, tente novamente" |

---

### FASE 5 - Planos (Plans)

**Objetivo:** CRUD de planos, versionamento, ativar/desativar.

#### Acesso: qualquer usuario autenticado

Dados filtrados por empresa (RLS). Cada empresa tem seus proprios planos.

#### Endpoints:

```
POST   /api/v1/plans                    → Criar plano
GET    /api/v1/plans                    → Listar (paginado)
GET    /api/v1/plans/{id}               → Detalhe
PUT    /api/v1/plans/{id}               → Atualizar
PATCH  /api/v1/plans/{id}/activate      → Ativar
PATCH  /api/v1/plans/{id}/deactivate    → Desativar
DELETE /api/v1/plans/{id}               → Excluir (soft delete)
POST   /api/v1/plans/{id}/new-version   → Criar nova versao
```

#### Permissoes:

| Endpoint | HOLDING_ADMIN | COMPANY_ADMIN | COMPANY_OPERATOR |
|----------|:---:|:---:|:---:|
| Todos os endpoints acima | ✅ | ✅ | ✅ |

Nao ha restricao de role alem de estar autenticado.

#### Criar plano:
```typescript
POST /api/v1/plans
Body: {
  "name": "Plano Pro",                  // obrigatorio
  "description": "Plano profissional",  // opcional
  "value": 199.90,                      // obrigatorio, > 0
  "cycle": "MONTHLY",                   // obrigatorio: MONTHLY, QUARTERLY, SEMIANNUALLY, YEARLY
  "trialDays": 7,                       // opcional, default 0
  "setupFee": 50.00,                    // opcional
  "limits": "{\"maxUsers\": 50}",       // opcional, JSON como string
  "features": "{\"api\": true}",        // opcional, JSON como string
  "tierOrder": 2                        // opcional, para ordenar upgrade/downgrade
}
```

#### Nova versao (mantendo historico):
```typescript
POST /api/v1/plans/{id}/new-version
Body: { "name": "Plano Pro v2", "value": 249.90 }
// Cria um NOVO registro com version incrementado
// O plano original e mantido para assinaturas existentes
```

#### Valores de cycle para labels no frontend:
```
MONTHLY      → "Mensal"
QUARTERLY    → "Trimestral"
SEMIANNUALLY → "Semestral"
YEARLY       → "Anual"
```

---

### FASE 6 - Cobrancas (Charges)

**Objetivo:** Criar cobrancas PIX/Boleto/CC, listar, cancelar, reembolsar, QR Code, linha digitavel.

#### Acesso: qualquer usuario autenticado

#### Endpoints:

```
POST   /api/v1/charges/pix                          → Criar PIX
POST   /api/v1/charges/boleto                        → Criar Boleto
POST   /api/v1/charges/credit-card                   → Criar Cartao
POST   /api/v1/charges/credit-card/installments      → Parcelamento Cartao
POST   /api/v1/charges/boleto/installments           → Parcelamento Boleto
POST   /api/v1/charges/undefined                     → Tipo indefinido
GET    /api/v1/charges                               → Listar (filtros + paginacao)
GET    /api/v1/charges/{id}                          → Detalhe
GET    /api/v1/charges/{id}/pix-qrcode               → QR Code PIX
GET    /api/v1/charges/{id}/boleto-line              → Linha digitavel Boleto
POST   /api/v1/charges/{id}/refund                   → Reembolsar
DELETE /api/v1/charges/{id}                          → Cancelar
POST   /api/v1/charges/{id}/regenerate-boleto        → Regenerar boleto
POST   /api/v1/charges/{id}/received-in-cash         → Marcar recebido em dinheiro
POST   /api/v1/charges/{id}/resend-notification      → Reenviar notificacao
```

#### Permissoes:

| Endpoint | HOLDING_ADMIN | COMPANY_ADMIN | COMPANY_OPERATOR |
|----------|:---:|:---:|:---:|
| Todos os endpoints acima | ✅ | ✅ | ✅ |

#### Criar cobranca PIX:
```typescript
POST /api/v1/charges/pix
Headers: {
  Authorization: "Bearer {token}",
  "Idempotency-Key": "550e8400-e29b-41d4-a716-446655440000"  // UUID unico
}
Body: {
  "customerId": 1,                   // obrigatorio
  "value": 100.00,                   // obrigatorio, > 0
  "dueDate": "2026-04-15",          // obrigatorio, formato YYYY-MM-DD
  "description": "Mensalidade",      // opcional
  "externalReference": "REF-001"     // opcional
}
```

#### Criar cobranca Cartao de Credito:
```typescript
POST /api/v1/charges/credit-card
Body: {
  "customerId": 1,
  "value": 299.90,
  "dueDate": "2026-04-15",
  "description": "Plano anual",
  "creditCard": {                              // obrigatorio para CC
    "holderName": "JOAO SILVA",
    "number": "5162306342424242",              // sandbox: aceita este numero
    "expiryMonth": "12",
    "expiryYear": "2030",
    "ccv": "123"
  },
  "creditCardHolderInfo": {                    // obrigatorio para CC
    "name": "Joao Silva",
    "email": "joao@email.com",
    "cpfCnpj": "12345678900",
    "postalCode": "01000000",
    "addressNumber": "100",
    "phone": "11999999999"
  },
  "remoteIp": "192.168.1.1"                   // opcional
}
```

**Alternativa com token (cartao ja salvo):**
```typescript
POST /api/v1/charges/credit-card
Body: {
  "customerId": 1,
  "value": 299.90,
  "dueDate": "2026-04-15",
  "creditCardToken": "token_salvo_anteriormente"  // em vez de creditCard
}
```

#### Criar parcelamento:
```typescript
POST /api/v1/charges/credit-card/installments
Body: {
  "customerId": 1,
  "value": 600.00,                  // valor TOTAL
  "dueDate": "2026-04-15",
  "installmentCount": 3,           // obrigatorio, min 2
  "installmentValue": 200.00,      // valor de cada parcela
  "creditCard": { ... },
  "creditCardHolderInfo": { ... }
}
```

#### Listar com filtros:
```typescript
GET /api/v1/charges?status=PENDING&origin=WEB&dueDateFrom=2026-04-01&dueDateTo=2026-04-30&customerId=1&page=0&size=20
```

Filtros disponiveis:
```
status:       PENDING, CONFIRMED, RECEIVED, OVERDUE, REFUNDED, CHARGEBACK, CANCELED
origin:       WEB, PDV, RECURRING, API, BACKOFFICE, PLAN_CHANGE
dueDateFrom:  YYYY-MM-DD
dueDateTo:    YYYY-MM-DD
customerId:   Long
```

#### QR Code PIX (somente para cobrancas PIX):
```typescript
GET /api/v1/charges/{id}/pix-qrcode
Response: {
  "encodedImage": "data:image/png;base64,...",   // QR Code em base64
  "copyPaste": "00020126580014br.gov.bcb...",    // codigo copia-e-cola
  "expirationDate": "2026-04-15T23:59:59"
}
```

#### Linha digitavel Boleto:
```typescript
GET /api/v1/charges/{id}/boleto-line
Response: {
  "identificationField": "23793.38128 ...",   // linha digitavel
  "nossoNumero": "1234567",
  "barCode": "23793381280..."
}
```

#### Reembolso:
```typescript
POST /api/v1/charges/{id}/refund
Body: {
  "value": 50.00,                  // opcional - se omitido, reembolso total
  "description": "Cancelamento"    // opcional
}
```

#### Transicoes de status validas (para o frontend desabilitar botoes):
```
PENDING     → pode: Cancelar
CONFIRMED   → pode: Reembolsar, Cancelar
RECEIVED    → pode: Reembolsar
OVERDUE     → pode: Cancelar, Marcar Recebido, Regenerar Boleto
REFUNDED    → nenhuma acao (terminal)
CHARGEBACK  → pode: Reembolsar
CANCELED    → nenhuma acao (terminal)
```

#### Botoes condicionais por status:

```typescript
const chargeActions = {
  PENDING:    ["cancel", "resendNotification"],
  CONFIRMED:  ["refund", "cancel"],
  RECEIVED:   ["refund"],
  OVERDUE:    ["cancel", "receivedInCash", "regenerateBoleto", "resendNotification"],
  REFUNDED:   [],
  CHARGEBACK: ["refund"],
  CANCELED:   [],
};
```

#### Erros possiveis:
| Status | Causa | Acao |
|--------|-------|------|
| 404 | Cliente nao encontrado | "Cliente nao encontrado" |
| 409 | Idempotency-Key repetido com body diferente | "Requisicao duplicada com dados diferentes" |
| 422 | Valor <= 0, data passada, dados CC invalidos | Mostrar erros nos campos |
| 422 | Transicao de status invalida | "Acao nao permitida neste status" |
| 502 | Erro Asaas | "Erro ao comunicar com o gateway de pagamento" |

---

### FASE 7 - Assinaturas (Subscriptions)

**Objetivo:** Criar, listar, pausar, retomar, cancelar assinaturas. Alterar metodo de pagamento.

#### Acesso: qualquer usuario autenticado

#### Endpoints:

```
POST   /api/v1/subscriptions                           → Criar
GET    /api/v1/subscriptions                           → Listar (filtros)
GET    /api/v1/subscriptions/{id}                      → Detalhe
GET    /api/v1/subscriptions/{id}/charges              → Cobrancas da assinatura
PUT    /api/v1/subscriptions/{id}                      → Atualizar
PATCH  /api/v1/subscriptions/{id}/payment-method       → Mudar metodo pagamento
DELETE /api/v1/subscriptions/{id}                      → Cancelar
POST   /api/v1/subscriptions/{id}/pause                → Pausar
POST   /api/v1/subscriptions/{id}/resume               → Retomar
```

#### Permissoes:

| Endpoint | HOLDING_ADMIN | COMPANY_ADMIN | COMPANY_OPERATOR |
|----------|:---:|:---:|:---:|
| Todos os endpoints acima | ✅ | ✅ | ✅ |

#### Criar assinatura:
```typescript
POST /api/v1/subscriptions
Headers: { "Idempotency-Key": "uuid" }
Body: {
  "customerId": 1,                   // obrigatorio
  "planId": 3,                       // obrigatorio
  "billingType": "PIX",             // obrigatorio: PIX, BOLETO, CREDIT_CARD
  "nextDueDate": "2026-04-15",      // opcional
  "description": "Assinatura Pro",   // opcional
  "externalReference": "SUB-001",    // opcional

  // Somente se billingType = CREDIT_CARD:
  "creditCard": {
    "holderName": "JOAO SILVA",
    "number": "5162306342424242",
    "expiryMonth": "12",
    "expiryYear": "2030",
    "ccv": "123"
  },
  "creditCardHolderInfo": {
    "name": "Joao Silva",
    "email": "joao@email.com",
    "cpfCnpj": "12345678900",
    "postalCode": "01000000",
    "addressNumber": "100",
    "phone": "11999999999"
  }
}
```

#### Listar com filtros:
```typescript
GET /api/v1/subscriptions?status=ACTIVE&customerId=1&page=0&size=20
```

#### Transicoes de status (para botoes):
```
ACTIVE     → pode: Pausar, Cancelar, Mudar Plano, Mudar Pagamento
PAUSED     → pode: Retomar, Cancelar
SUSPENDED  → pode: Retomar, Cancelar
CANCELED   → nenhuma acao (terminal)
EXPIRED    → nenhuma acao (terminal)
```

```typescript
const subscriptionActions = {
  ACTIVE:    ["pause", "cancel", "changePlan", "changePayment"],
  PAUSED:    ["resume", "cancel"],
  SUSPENDED: ["resume", "cancel"],
  CANCELED:  [],
  EXPIRED:   [],
};
```

#### Alterar metodo de pagamento:
```typescript
PATCH /api/v1/subscriptions/{id}/payment-method
Body: {
  "billingType": "CREDIT_CARD",
  "creditCard": { ... },              // se CC
  "creditCardHolderInfo": { ... },    // se CC
  "creditCardToken": "...",           // alternativa ao creditCard
  "remoteIp": "..."
}
```

---

### FASE 8 - Mudanca de Plano (Plan Change)

**Objetivo:** Preview de mudanca, execucao, historico, cancelamento.

#### Acesso: qualquer usuario autenticado

#### Endpoints:

```
POST   /api/v1/subscriptions/{id}/preview-change?newPlanId={id}    → Preview
POST   /api/v1/subscriptions/{id}/change-plan                      → Executar mudanca
GET    /api/v1/subscriptions/{id}/plan-changes                     → Historico
DELETE /api/v1/subscriptions/{id}/plan-changes/{changeId}          → Cancelar pendente
```

#### Preview (SEMPRE chamar antes de executar):
```typescript
POST /api/v1/subscriptions/{subscriptionId}/preview-change?newPlanId=5
Response: {
  "subscriptionId": 1,
  "currentPlanId": 3,
  "currentPlanName": "Basico",
  "currentPlanValue": 99.90,
  "newPlanId": 5,
  "newPlanName": "Pro",
  "newPlanValue": 199.90,
  "changeType": "UPGRADE",                // UPGRADE, DOWNGRADE, SIDEGRADE
  "policy": "IMMEDIATE_PRORATA",          // politica da empresa
  "delta": 100.00,                        // diferenca de valor
  "prorationCredit": 33.30,              // credito pro-rata (dias restantes)
  "prorationCharge": 66.70               // valor a cobrar
}
```

#### Executar mudanca:
```typescript
POST /api/v1/subscriptions/{subscriptionId}/change-plan
Body: {
  "newPlanId": 5,                                       // obrigatorio
  "currentUsage": { "users": 8, "storage": "3GB" },   // opcional
  "requestedBy": "admin@empresa.com"                    // opcional
}
```

#### Status da mudanca de plano:
```
PENDING          → Aguardando processamento
AWAITING_PAYMENT → Cobranca pro-rata criada, aguardando pagamento
SCHEDULED        → Agendada para fim do ciclo (END_OF_CYCLE)
EFFECTIVE        → Concluida com sucesso (terminal)
FAILED           → Falhou (terminal)
CANCELED         → Cancelada (terminal)
```

---

### FASE 9 - Webhooks Admin (HOLDING_ADMIN only)

**Objetivo:** Monitorar webhooks recebidos do Asaas, replay de eventos.

#### Acesso: somente `ROLE_HOLDING_ADMIN` ou `ROLE_SYSTEM`

#### Endpoints:

```
GET    /api/v1/admin/webhooks                        → Listar por status
GET    /api/v1/admin/webhooks/summary                → Resumo (contadores)
POST   /api/v1/admin/webhooks/{eventId}/replay       → Reprocessar evento
```

#### Summary:
```typescript
GET /api/v1/admin/webhooks/summary
Response: {
  "pending": 5,
  "processing": 1,
  "deferred": 2,
  "processed": 1520,
  "failed": 3,
  "dlq": 1,
  "total": 1532
}
```

#### Listar por status:
```typescript
GET /api/v1/admin/webhooks?status=FAILED&page=0&size=20
// status obrigatorio: PENDING, PROCESSING, DEFERRED, PROCESSED, FAILED, DLQ
```

#### Erros:
| Status | Causa | Acao |
|--------|-------|------|
| 403 | Usuario nao e HOLDING_ADMIN/SYSTEM | "Acesso negado" |

---

### FASE 10 - Outbox Admin (HOLDING_ADMIN only)

**Objetivo:** Monitorar eventos de dominio (outbox pattern), retry de falhas.

#### Acesso: somente `ROLE_HOLDING_ADMIN`

#### Endpoints:

```
GET    /api/v1/admin/outbox                          → Listar por status
GET    /api/v1/admin/outbox/summary                  → Resumo + lag
POST   /api/v1/admin/outbox/{id}/retry               → Retry evento falho
```

#### Summary:
```typescript
GET /api/v1/admin/outbox/summary
Response: {
  "pending": 3,
  "published": 8900,
  "failed": 2,
  "dlq": 0,
  "lagSeconds": 1.5       // lag do evento pending mais antigo
}
```

#### Listar por status:
```typescript
GET /api/v1/admin/outbox?status=FAILED&page=0&size=20
// status default: PENDING. Valores: PENDING, PUBLISHED, FAILED, DLQ
```

---

### FASE 11 - Reconciliacao (HOLDING_ADMIN only)

**Objetivo:** Reconciliar dados locais com Asaas, replay de DLQ.

#### Acesso: somente `ROLE_HOLDING_ADMIN`

#### Endpoints:

```
POST   /api/v1/admin/reconciliation/charges              → Reconciliar cobrancas
POST   /api/v1/admin/reconciliation/subscriptions        → Reconciliar assinaturas
POST   /api/v1/admin/reconciliation/dlq/replay           → Reprocessar DLQ
```

#### Reconciliar cobrancas:
```typescript
POST /api/v1/admin/reconciliation/charges?daysBack=3
// daysBack: quantos dias retroativos verificar (default 3)

Response: {
  "executedAt": "2026-04-09T10:00:00",
  "totalChecked": 150,
  "divergencesFound": 3,
  "autoFixed": 2,
  "divergences": [
    {
      "entityType": "Charge",
      "localId": 42,
      "asaasId": "pay_abc123",
      "localStatus": "PENDING",
      "asaasStatus": "RECEIVED",
      "action": "AUTO_FIXED"
    }
  ]
}
```

#### Replay DLQ:
```typescript
POST /api/v1/admin/reconciliation/dlq/replay
Response: {
  "executedAt": "2026-04-09T10:00:00",
  "webhookEventsReplayed": 3,
  "outboxEventsReplayed": 1,
  "totalReplayed": 4
}
```

---

### FASE 12 - Relatorios e Export (todos os autenticados)

**Objetivo:** Receita, MRR, churn, inadimplencia + export CSV.

#### Acesso: qualquer usuario autenticado (dados filtrados por empresa via RLS)

#### Endpoints:

```
GET /api/v1/reports/revenue                              → Receita
GET /api/v1/reports/subscriptions/mrr                    → MRR/ARR
GET /api/v1/reports/subscriptions/churn                  → Churn
GET /api/v1/reports/overdue                              → Inadimplencia
GET /api/v1/reports/export/revenue                       → CSV receita
GET /api/v1/reports/export/mrr                           → CSV MRR
GET /api/v1/reports/export/churn                         → CSV churn
GET /api/v1/reports/export/overdue                       → CSV inadimplencia
```

#### Receita:
```typescript
GET /api/v1/reports/revenue?from=2026-01-01&to=2026-04-09&groupBy=origin
// from e to: obrigatorios, formato YYYY-MM-DD
// groupBy: "origin" (default) ou "billingType"

Response: [
  { "groupKey": "RECURRING", "chargeCount": 120, "totalValue": 45000.00 },
  { "groupKey": "WEB",       "chargeCount": 30,  "totalValue": 12000.00 }
]
```

#### MRR/ARR:
```typescript
GET /api/v1/reports/subscriptions/mrr
Response: {
  "calculatedAt": "2026-04-09T10:00:00",
  "activeSubscriptions": 85,
  "mrr": 25000.00,
  "arr": 300000.00
}
```

#### Churn:
```typescript
GET /api/v1/reports/subscriptions/churn?from=2026-01-01&to=2026-04-09
Response: {
  "calculatedAt": "2026-04-09T10:00:00",
  "periodStart": "2026-01-01",
  "periodEnd": "2026-04-09",
  "canceledInPeriod": 5,
  "activeAtStart": 80,
  "churnRate": 6.25
}
```

#### Inadimplencia:
```typescript
GET /api/v1/reports/overdue
Response: [
  {
    "customerId": 1,
    "customerName": "Maria Santos",
    "overdueCount": 3,
    "totalOverdueValue": 597.00
  }
]
```

#### Export CSV:
```typescript
// Todos retornam Content-Type: text/csv com Content-Disposition: attachment
GET /api/v1/reports/export/revenue?from=2026-01-01&to=2026-04-09&groupBy=origin
GET /api/v1/reports/export/mrr
GET /api/v1/reports/export/churn?from=2026-01-01&to=2026-04-09
GET /api/v1/reports/export/overdue

// No frontend, use download via blob:
const response = await api.get("/api/v1/reports/export/revenue", {
  params: { from: "2026-01-01", to: "2026-04-09" },
  responseType: "blob"
});
const url = URL.createObjectURL(response.data);
const a = document.createElement("a");
a.href = url;
a.download = "receita.csv";
a.click();
```

---

## 5. Resumo: Matriz de Acesso Completa

### Endpoints que retornam 403 por role:

| Endpoint | H_ADMIN | C_ADMIN | C_OPERATOR |
|----------|:---:|:---:|:---:|
| **POST /auth/users** | ✅ | ✅ | ❌ 403 |
| **GET /companies** | ✅ | ❌ 403 | ❌ 403 |
| **POST /companies** | ✅ | ❌ 403 | ❌ 403 |
| **PUT /companies/{id}** | ✅ | ❌ 403 | ❌ 403 |
| **PUT /companies/{id}/credentials** | ✅ | ❌ 403 | ❌ 403 |
| **POST /companies/{id}/test-connection** | ✅ | ❌ 403 | ❌ 403 |
| **POST /customers/{id}/restore** | ✅ | ✅ | ❌ 403 |
| **GET /admin/webhooks** | ✅ | ❌ 403 | ❌ 403 |
| **GET /admin/webhooks/summary** | ✅ | ❌ 403 | ❌ 403 |
| **POST /admin/webhooks/{id}/replay** | ✅ | ❌ 403 | ❌ 403 |
| **GET /admin/outbox** | ✅ | ❌ 403 | ❌ 403 |
| **GET /admin/outbox/summary** | ✅ | ❌ 403 | ❌ 403 |
| **POST /admin/outbox/{id}/retry** | ✅ | ❌ 403 | ❌ 403 |
| **POST /admin/reconciliation/charges** | ✅ | ❌ 403 | ❌ 403 |
| **POST /admin/reconciliation/subscriptions** | ✅ | ❌ 403 | ❌ 403 |
| **POST /admin/reconciliation/dlq/replay** | ✅ | ❌ 403 | ❌ 403 |

### Todos os outros endpoints: ✅ para QUALQUER usuario autenticado

(customers CRUD, plans CRUD, charges CRUD, subscriptions CRUD, plan changes, reports)
Os dados sao filtrados automaticamente por empresa via RLS.

---

## 6. Checklist de Implementacao por Fase

```
FASE 1 - Auth
  [ ] Tela de login
  [ ] Armazenar tokens (Zustand + persist)
  [ ] Decodificar JWT -> extrair roles, company_id, name
  [ ] Interceptor Axios (Authorization header)
  [ ] Interceptor Axios (refresh automatico em 401)
  [ ] ProtectedRoute (redirect /login)
  [ ] RoleGuard (esconder/bloquear rotas por role)
  [ ] Sidebar dinâmica baseada na role
  [ ] Logout (limpar tokens)

FASE 2 - Empresas (HOLDING_ADMIN)
  [ ] Listar empresas (tabela paginada)
  [ ] Criar empresa
  [ ] Editar empresa
  [ ] Configurar credenciais Asaas (form separado)
  [ ] Testar conexao Asaas
  [ ] Esconder menu "Empresas" para non-HOLDING_ADMIN

FASE 3 - Usuarios
  [ ] Form de criacao de usuario
  [ ] Se HOLDING_ADMIN: select de empresa + todas as roles
  [ ] Se COMPANY_ADMIN: empresa fixa + roles da empresa
  [ ] Esconder botao para COMPANY_OPERATOR

FASE 4 - Clientes
  [ ] Listar clientes (tabela com busca)
  [ ] Criar cliente
  [ ] Editar cliente
  [ ] Excluir cliente (soft delete com confirmacao)
  [ ] Restaurar cliente (somente ADMIN - esconder botao)
  [ ] Sincronizar com Asaas
  [ ] Visualizar saldo de credito + historico ledger

FASE 5 - Planos
  [ ] Listar planos (cards ou tabela)
  [ ] Criar plano
  [ ] Editar plano
  [ ] Ativar/Desativar plano
  [ ] Criar nova versao
  [ ] Excluir plano

FASE 6 - Cobrancas
  [ ] Wizard de criacao (tipo -> dados -> confirmacao)
  [ ] Listar com filtros (status, tipo, origem, datas, cliente)
  [ ] Detalhe com QR Code PIX / Linha Boleto
  [ ] Cancelar cobranca
  [ ] Reembolsar (total/parcial)
  [ ] Regenerar boleto
  [ ] Marcar recebido em dinheiro
  [ ] Reenviar notificacao
  [ ] Botoes condicionais por status
  [ ] Enviar Idempotency-Key

FASE 7 - Assinaturas
  [ ] Criar assinatura (cliente + plano + tipo pagamento)
  [ ] Listar com filtros
  [ ] Detalhe com cobrancas vinculadas
  [ ] Pausar / Retomar
  [ ] Cancelar
  [ ] Alterar metodo de pagamento
  [ ] Botoes condicionais por status

FASE 8 - Mudanca de Plano
  [ ] Tela de selecao de novo plano
  [ ] Preview com calculo pro-rata
  [ ] Confirmacao e execucao
  [ ] Historico de mudancas
  [ ] Cancelar mudanca pendente

FASE 9 - Webhooks Admin (HOLDING_ADMIN)
  [ ] Dashboard com summary (contadores)
  [ ] Listar por status
  [ ] Replay de evento
  [ ] Esconder menu para non-HOLDING_ADMIN

FASE 10 - Outbox Admin (HOLDING_ADMIN)
  [ ] Dashboard com summary + lag
  [ ] Listar por status
  [ ] Retry de evento falho

FASE 11 - Reconciliacao (HOLDING_ADMIN)
  [ ] Executar reconciliacao de cobrancas
  [ ] Executar reconciliacao de assinaturas
  [ ] Replay DLQ
  [ ] Exibir resultado com divergencias

FASE 12 - Relatorios
  [ ] Receita (grafico + tabela + filtros)
  [ ] MRR/ARR (cards)
  [ ] Churn (grafico + cards)
  [ ] Inadimplencia (tabela)
  [ ] Export CSV de cada relatorio

FASE 13 - Dashboard
  [ ] KPI cards (receita, MRR, assinaturas ativas, vencidas)
  [ ] Graficos (receita por origem, churn)
  [ ] Status operacional (webhooks, outbox) - somente HOLDING_ADMIN
  [ ] Atividade recente (ultimas cobrancas)
```

---

## 7. Formato de Erros da API

Todas as respostas de erro seguem este formato:

```json
{
  "timestamp": "2026-04-09T10:00:00.000+00:00",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Invalid email or password",
  "path": "/api/v1/auth/login"
}
```

Para erros de validacao (campos invalidos):
```json
{
  "timestamp": "...",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Validation failed",
  "path": "/api/v1/customers",
  "fieldErrors": {
    "name": "must not be blank",
    "document": "must not be blank"
  }
}
```

Para erros do Asaas:
```json
{
  "timestamp": "...",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Asaas API error",
  "path": "/api/v1/charges/pix",
  "asaasErrors": [
    { "code": "invalid_value", "description": "O campo value deve ser maior que zero" }
  ]
}
```

### Tratamento no frontend:
```typescript
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const data = error.response?.data;
    const status = error.response?.status;

    if (status === 401) {
      // Tentar refresh token
    } else if (status === 403) {
      toast.error("Voce nao tem permissao para esta acao");
    } else if (status === 404) {
      toast.error("Recurso nao encontrado");
    } else if (status === 422 && data?.fieldErrors) {
      // Mapear para erros do formulario (React Hook Form setError)
      Object.entries(data.fieldErrors).forEach(([field, msg]) => {
        form.setError(field, { message: msg as string });
      });
    } else if (status === 422 && data?.asaasErrors) {
      const msgs = data.asaasErrors.map(e => e.description).join(". ");
      toast.error(`Erro Asaas: ${msgs}`);
    } else if (status === 422) {
      toast.error(data?.message || "Erro de validacao");
    } else if (status === 429) {
      toast.warning("Limite de requisicoes excedido. Aguarde um momento.");
    } else if (status === 502) {
      toast.error("Erro ao comunicar com o gateway de pagamento");
    } else {
      toast.error("Erro inesperado. Tente novamente.");
    }

    return Promise.reject(error);
  }
);
```
