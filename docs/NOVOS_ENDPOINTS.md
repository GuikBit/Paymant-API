# Novos Endpoints - Documentacao de Uso

---

## 1. Listar Usuarios

### `GET /api/v1/auth/users`

Retorna a lista paginada de usuarios do sistema. O comportamento varia conforme a role do usuario autenticado.

### Regras de acesso

| Role | Comportamento |
|------|---------------|
| `HOLDING_ADMIN` | Visualiza **todos** os usuarios de **todas** as empresas |
| `COMPANY_ADMIN` | Visualiza **apenas** os usuarios da **sua propria empresa** |
| Outras roles | **403 Forbidden** |

### Headers obrigatorios

```
Authorization: Bearer <access_token>
X-Company-Id: <company_id>
```

### Query params (paginacao)

| Parametro | Tipo | Default | Descricao |
|-----------|------|---------|-----------|
| `page` | int | 0 | Numero da pagina (zero-based) |
| `size` | int | 20 | Quantidade de registros por pagina |
| `sort` | string | - | Campo e direcao. Ex: `name,asc` |

### Exemplo de request

```bash
curl -X GET "http://localhost:8080/api/v1/auth/users?page=0&size=10&sort=name,asc" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "X-Company-Id: 1"
```

### Exemplo de response (200 OK)

```json
{
  "content": [
    {
      "id": 1,
      "companyId": 1,
      "companyName": "Minha Empresa LTDA",
      "email": "admin@empresa.com",
      "name": "Joao Silva",
      "roles": ["ROLE_COMPANY_ADMIN"],
      "active": true,
      "createdAt": "2026-03-15T10:30:00"
    },
    {
      "id": 2,
      "companyId": 1,
      "companyName": "Minha Empresa LTDA",
      "email": "operador@empresa.com",
      "name": "Maria Santos",
      "roles": ["ROLE_COMPANY_OPERATOR"],
      "active": true,
      "createdAt": "2026-03-20T14:00:00"
    }
  ],
  "pageable": { "pageNumber": 0, "pageSize": 10 },
  "totalElements": 2,
  "totalPages": 1,
  "last": true,
  "first": true
}
```

### Particularidades

- O campo `companyName` exibe o `nomeFantasia` da empresa. Se nao houver nome fantasia cadastrado, exibe a `razaoSocial`.
- A filtragem por empresa do `COMPANY_ADMIN` usa o `company_id` extraido do JWT (nao do header), garantindo que o usuario nunca consiga ver dados de outra empresa.
- Usuarios inativos (`active: false`) tambem sao retornados na listagem.

---

## 2. Obter Dados da Minha Empresa

### `GET /api/v1/companies/me`

Retorna os dados cadastrais da empresa vinculada ao usuario autenticado. Criado para que o frontend possa exibir o nome real da empresa em formularios e telas (ex: criacao de usuario).

### Regras de acesso

| Role | Comportamento |
|------|---------------|
| Qualquer usuario autenticado | **200 OK** com os dados da empresa |
| Sem autenticacao | **401 Unauthorized** |

### Headers obrigatorios

```
Authorization: Bearer <access_token>
X-Company-Id: <company_id>
```

### Exemplo de request

```bash
curl -X GET "http://localhost:8080/api/v1/companies/me" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "X-Company-Id: 3"
```

### Exemplo de response (200 OK)

```json
{
  "id": 3,
  "cnpj": "12.345.678/0001-90",
  "razaoSocial": "Empresa Exemplo Servicos LTDA",
  "nomeFantasia": "Empresa Exemplo",
  "email": "contato@empresaexemplo.com",
  "phone": "(11) 99999-0000",
  "asaasEnv": "SANDBOX",
  "hasAsaasKey": true,
  "status": "ACTIVE",
  "planChangePolicy": "IMMEDIATE_PRORATA",
  "downgradeValidationStrategy": "BLOCK",
  "gracePeriodDays": 0,
  "createdAt": "2026-01-10T08:00:00",
  "updatedAt": "2026-04-01T15:30:00"
}
```

### Particularidades

- Este endpoint **nao** exige role especifica — qualquer usuario logado pode acessar.
- O `company_id` e extraido do `TenantContext` (que vem do JWT), entao o usuario so consegue ver a propria empresa.
- O campo `hasAsaasKey` indica se a empresa possui chave da API Asaas configurada (nao expoe a chave em si).
- Use `nomeFantasia` como nome de exibicao no frontend. Se for `null`, use `razaoSocial` como fallback.

### Uso recomendado no frontend

Na tela de criacao de usuario como `COMPANY_ADMIN`, chame este endpoint ao carregar a pagina para preencher o campo "Empresa" com o nome real:

```ts
// Ao montar o componente de criacao de usuario
const response = await fetch("/api/v1/companies/me", {
  headers: { "Authorization": `Bearer ${token}`, "X-Company-Id": companyId }
});
const company = await response.json();

// Usar no select/campo de empresa
const companyLabel = company.nomeFantasia || company.razaoSocial;
```

---

## 3. Dashboard Geral da Organizacao

### `GET /api/v1/reports/dashboard`

Endpoint consolidado que retorna todos os indicadores necessarios para popular um dashboard completo da organizacao em uma unica chamada.

### Regras de acesso

| Role | Comportamento |
|------|---------------|
| Qualquer usuario autenticado | **200 OK** (dados filtrados pelo tenant/empresa via RLS) |
| Sem autenticacao | **401 Unauthorized** |

### Headers obrigatorios

```
Authorization: Bearer <access_token>
X-Company-Id: <company_id>
```

### Exemplo de request

```bash
curl -X GET "http://localhost:8080/api/v1/reports/dashboard" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "X-Company-Id: 1"
```

### Exemplo de response (200 OK)

```json
{
  "calculatedAt": "2026-04-11T18:30:00",

  "mrr": 15750.00,
  "arr": 189000.00,

  "totalCustomers": 342,
  "totalActiveSubscriptions": 285,
  "totalCharges": 4520,
  "totalOverdueCharges": 18,
  "totalOverdueValue": 3240.50,

  "revenueCurrentMonth": 12300.00,
  "chargesReceivedCurrentMonth": 198,

  "canceledCurrentMonth": 5,
  "churnRateCurrentMonth": 1.75,

  "revenueByMethod": [
    { "groupKey": "PIX", "chargeCount": 120, "totalValue": 7500.00 },
    { "groupKey": "CREDIT_CARD", "chargeCount": 55, "totalValue": 3200.00 },
    { "groupKey": "BOLETO", "chargeCount": 23, "totalValue": 1600.00 }
  ],

  "topOverdueCustomers": [
    {
      "customerId": 42,
      "customerName": "Cliente Exemplo SA",
      "overdueCount": 3,
      "totalOverdueValue": 1500.00
    },
    {
      "customerId": 87,
      "customerName": "Outro Cliente LTDA",
      "overdueCount": 2,
      "totalOverdueValue": 840.50
    }
  ]
}
```

### Descricao dos campos

| Campo | Tipo | Descricao |
|-------|------|-----------|
| `calculatedAt` | datetime | Momento exato do calculo |
| `mrr` | decimal | Monthly Recurring Revenue — soma de `effective_price - coupon_discount_amount` de todas as assinaturas ativas |
| `arr` | decimal | Annual Recurring Revenue — `MRR x 12` |
| `totalCustomers` | long | Total de clientes ativos (nao soft-deleted) |
| `totalActiveSubscriptions` | long | Total de assinaturas com status `ACTIVE` |
| `totalCharges` | long | Total geral de cobrancas |
| `totalOverdueCharges` | long | Cobrancas com status `OVERDUE` |
| `totalOverdueValue` | decimal | Soma dos valores das cobrancas em atraso |
| `revenueCurrentMonth` | decimal | Receita confirmada/recebida no mes corrente |
| `chargesReceivedCurrentMonth` | long | Quantidade de cobrancas confirmadas/recebidas no mes |
| `canceledCurrentMonth` | long | Assinaturas canceladas no mes corrente |
| `churnRateCurrentMonth` | decimal | Taxa de churn do mes em percentual (ex: `1.75` = 1,75%) |
| `revenueByMethod` | array | Receita do mes agrupada por metodo de pagamento (PIX, BOLETO, CREDIT_CARD) |
| `topOverdueCustomers` | array | Top 10 clientes com maior valor em atraso |

### Particularidades

- **MRR com desconto de cupom**: O calculo do MRR subtrai o `coupon_discount_amount` de cada assinatura ativa, refletindo o valor real cobrado.
- **Periodo do mes**: Os dados de "mes corrente" consideram do dia 1 do mes atual ate o dia de hoje (`LocalDate.now()`).
- **Churn rate**: Calculado como `(cancelados no mes / ativos no inicio do mes) x 100`. Retorna `0` se nao havia assinaturas ativas no inicio do mes.
- **Multi-tenancy**: Todos os dados sao automaticamente filtrados por empresa via Row-Level Security (RLS) do PostgreSQL. Cada empresa so ve seus proprios dados.
- **Top inadimplentes**: Limitado aos 10 clientes com maior valor total em atraso, ordenados por `totalOverdueValue` decrescente.
- **Performance**: Este endpoint executa multiplas queries em uma unica transacao read-only. Para dashboards com refresh automatico, recomenda-se intervalo minimo de 30 segundos entre chamadas.

### Uso recomendado no frontend

```ts
const response = await fetch("/api/v1/reports/dashboard", {
  headers: { "Authorization": `Bearer ${token}`, "X-Company-Id": companyId }
});
const dashboard = await response.json();

// Cards principais
setMrr(dashboard.mrr);
setArr(dashboard.arr);
setTotalCustomers(dashboard.totalCustomers);
setActiveSubscriptions(dashboard.totalActiveSubscriptions);
setRevenueMonth(dashboard.revenueCurrentMonth);
setChurnRate(dashboard.churnRateCurrentMonth);

// Grafico de receita por metodo
setPieChartData(dashboard.revenueByMethod);

// Tabela de inadimplentes
setOverdueTable(dashboard.topOverdueCustomers);
```

---

## Resumo dos Endpoints

| Metodo | Endpoint | Role minima | Descricao |
|--------|----------|-------------|-----------|
| `GET` | `/api/v1/auth/users` | `COMPANY_ADMIN` | Listar usuarios (filtrado por role) |
| `GET` | `/api/v1/companies/me` | Autenticado | Dados da minha empresa |
| `GET` | `/api/v1/reports/dashboard` | Autenticado | Dashboard consolidado |
