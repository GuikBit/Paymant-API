# Payment API - Sistema Web de Testes

Documento de especificacao completa para o frontend de testes da API de Pagamentos Multi-Tenant com integracao Asaas.

---

## 1. Stack Tecnologica

| Tecnologia | Versao | Finalidade |
|------------|--------|------------|
| **React** | 19.x | UI library |
| **Vite** | 6.x | Build tool + dev server |
| **TypeScript** | 5.x | Tipagem estatica |
| **Tailwind CSS** | 4.x | Utility-first styling |
| **React Router** | 7.x | Roteamento SPA |
| **TanStack Query** | 5.x | Server state, cache, mutations |
| **TanStack Table** | 8.x | Tabelas com sort, filter, paginacao |
| **React Hook Form** | 7.x | Formularios performaticos |
| **Zod** | 3.x | Validacao de schemas |
| **Zustand** | 5.x | Estado global (auth, tenant) |
| **Axios** | 1.x | HTTP client com interceptors |
| **Recharts** | 2.x | Graficos (MRR, revenue, churn) |
| **Framer Motion** | 12.x | Animacoes e transicoes |
| **Sonner** | 2.x | Toast notifications |
| **Lucide React** | 0.4x | Icones SVG |
| **date-fns** | 4.x | Manipulacao de datas |
| **clsx + tailwind-merge** | - | Merge condicional de classes |
| **@radix-ui/react-\*** | - | Primitivos acessiveis (Dialog, Dropdown, Tabs, Tooltip) |

### Estrutura do Projeto

```
payment-web/
├── public/
│   └── favicon.svg
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── routes.tsx
│   ├── api/
│   │   ├── client.ts              # Axios instance + interceptors
│   │   ├── auth.ts                # login, refresh, createUser
│   │   ├── companies.ts           # CRUD companies
│   │   ├── customers.ts           # CRUD customers
│   │   ├── plans.ts               # CRUD plans
│   │   ├── charges.ts             # CRUD charges + PIX/boleto/CC
│   │   ├── subscriptions.ts       # CRUD subscriptions
│   │   ├── plan-changes.ts        # preview, execute, cancel
│   │   ├── webhooks.ts            # admin webhook endpoints
│   │   ├── outbox.ts              # admin outbox endpoints
│   │   ├── reconciliation.ts      # reconciliation endpoints
│   │   └── reports.ts             # revenue, MRR, churn, overdue
│   ├── stores/
│   │   ├── auth-store.ts          # JWT tokens, user, roles
│   │   └── tenant-store.ts        # company selecionada, X-Company-Id
│   ├── hooks/
│   │   ├── use-auth.ts
│   │   ├── use-customers.ts
│   │   ├── use-plans.ts
│   │   ├── use-charges.ts
│   │   ├── use-subscriptions.ts
│   │   ├── use-reports.ts
│   │   └── use-pagination.ts
│   ├── components/
│   │   ├── ui/                    # Design system primitivos
│   │   │   ├── Button.tsx
│   │   │   ├── Input.tsx
│   │   │   ├── Select.tsx
│   │   │   ├── Badge.tsx
│   │   │   ├── Card.tsx
│   │   │   ├── DataTable.tsx
│   │   │   ├── Dialog.tsx
│   │   │   ├── Dropdown.tsx
│   │   │   ├── Tabs.tsx
│   │   │   ├── Tooltip.tsx
│   │   │   ├── Skeleton.tsx
│   │   │   ├── EmptyState.tsx
│   │   │   ├── StatusBadge.tsx
│   │   │   ├── CopyButton.tsx
│   │   │   ├── ConfirmDialog.tsx
│   │   │   ├── PageHeader.tsx
│   │   │   └── Pagination.tsx
│   │   ├── layout/
│   │   │   ├── AppLayout.tsx      # Sidebar + header + content
│   │   │   ├── Sidebar.tsx
│   │   │   ├── Header.tsx
│   │   │   ├── TenantSwitcher.tsx
│   │   │   └── BreadcrumbNav.tsx
│   │   ├── auth/
│   │   │   └── ProtectedRoute.tsx
│   │   ├── charts/
│   │   │   ├── RevenueChart.tsx
│   │   │   ├── MrrChart.tsx
│   │   │   └── ChurnChart.tsx
│   │   └── domain/                # Componentes especificos de dominio
│   │       ├── ChargeStatusFlow.tsx
│   │       ├── SubscriptionTimeline.tsx
│   │       ├── PlanChangePreview.tsx
│   │       ├── PixQrCodeViewer.tsx
│   │       ├── BoletoViewer.tsx
│   │       ├── CreditCardForm.tsx
│   │       ├── WebhookEventList.tsx
│   │       └── OutboxEventList.tsx
│   ├── pages/
│   │   ├── Login.tsx
│   │   ├── Dashboard.tsx
│   │   ├── companies/
│   │   │   ├── CompanyList.tsx
│   │   │   ├── CompanyForm.tsx
│   │   │   └── CompanyDetail.tsx
│   │   ├── customers/
│   │   │   ├── CustomerList.tsx
│   │   │   ├── CustomerForm.tsx
│   │   │   └── CustomerDetail.tsx
│   │   ├── plans/
│   │   │   ├── PlanList.tsx
│   │   │   ├── PlanForm.tsx
│   │   │   └── PlanDetail.tsx
│   │   ├── charges/
│   │   │   ├── ChargeList.tsx
│   │   │   ├── ChargeCreate.tsx
│   │   │   └── ChargeDetail.tsx
│   │   ├── subscriptions/
│   │   │   ├── SubscriptionList.tsx
│   │   │   ├── SubscriptionCreate.tsx
│   │   │   ├── SubscriptionDetail.tsx
│   │   │   └── PlanChange.tsx
│   │   ├── webhooks/
│   │   │   └── WebhookDashboard.tsx
│   │   ├── outbox/
│   │   │   └── OutboxDashboard.tsx
│   │   ├── reconciliation/
│   │   │   └── ReconciliationPanel.tsx
│   │   └── reports/
│   │       └── ReportsDashboard.tsx
│   ├── lib/
│   │   ├── utils.ts               # cn(), formatCurrency(), formatDate()
│   │   ├── constants.ts           # Enums, status colors, labels
│   │   └── validators.ts          # Zod schemas
│   └── types/
│       ├── auth.ts
│       ├── company.ts
│       ├── customer.ts
│       ├── plan.ts
│       ├── charge.ts
│       ├── subscription.ts
│       ├── plan-change.ts
│       ├── webhook.ts
│       ├── outbox.ts
│       ├── reconciliation.ts
│       └── report.ts
├── index.html
├── tailwind.config.ts
├── vite.config.ts
├── tsconfig.json
└── package.json
```

---

## 2. Design System

### 2.1 Paleta de Cores

```
// Tema escuro como padrao (dark-first)
// Suporte a light mode via class strategy do Tailwind

Background:
  --bg-primary:    #0A0A0F       (quase preto, fundo principal)
  --bg-secondary:  #12121A       (cards, sidebar)
  --bg-tertiary:   #1A1A2E       (inputs, hover states)
  --bg-elevated:   #222240       (dropdowns, tooltips)

Surfaces:
  --surface-1:     #16162A       (card default)
  --surface-2:     #1E1E38       (card hover)
  --surface-3:     #262650       (selected/active)

Borders:
  --border-subtle:   #2A2A4A     (divisores leves)
  --border-default:  #3A3A5C     (inputs, cards)
  --border-focus:    #6366F1     (focus ring - indigo)

Text:
  --text-primary:    #F1F1F6     (titulos, texto principal)
  --text-secondary:  #A1A1B5     (labels, descricoes)
  --text-tertiary:   #6B6B80     (placeholders, hints)
  --text-inverse:    #0A0A0F     (texto em botoes primarios)

Brand / Accent:
  --accent-primary:   #6366F1    (indigo-500 - acoes primarias)
  --accent-hover:     #818CF8    (indigo-400 - hover)
  --accent-muted:     #6366F120  (indigo com 12% opacity - bg sutil)

Status Colors:
  --status-success:   #22C55E    (green-500 - RECEIVED, ACTIVE, EFFECTIVE)
  --status-warning:   #F59E0B    (amber-500 - PENDING, OVERDUE, AWAITING)
  --status-error:     #EF4444    (red-500 - FAILED, CANCELED, DLQ)
  --status-info:      #3B82F6    (blue-500 - CONFIRMED, PROCESSING)
  --status-neutral:   #6B7280    (gray-500 - EXPIRED, terminal states)

Billing Type Colors:
  --pix:              #00D4AA    (teal - PIX)
  --boleto:           #F59E0B    (amber - Boleto)
  --credit-card:      #8B5CF6    (violet - Cartao de credito)
  --undefined:        #6B7280    (gray - Indefinido)
```

### 2.2 Tipografia

```
Font Stack:
  --font-sans: "Inter", "SF Pro Display", -apple-system, system-ui, sans-serif
  --font-mono: "JetBrains Mono", "Fira Code", "Cascadia Code", monospace

Escala:
  text-xs:    12px / 16px  (labels de badge, metadata)
  text-sm:    14px / 20px  (corpo secundario, tabelas)
  text-base:  16px / 24px  (corpo principal)
  text-lg:    18px / 28px  (subtitulos de secao)
  text-xl:    20px / 28px  (titulos de card)
  text-2xl:   24px / 32px  (titulos de pagina)
  text-3xl:   30px / 36px  (metricas grandes - dashboard)
  text-4xl:   36px / 40px  (numeros hero - MRR, receita total)

Font Weight:
  font-normal: 400  (corpo)
  font-medium: 500  (labels, subtitulos)
  font-semibold: 600 (titulos, botoes)
  font-bold: 700    (metricas, numeros destaque)
```

### 2.3 Espacamento e Grid

```
Espacamento base: 4px (Tailwind default)

Layout:
  Sidebar width: 260px (colapsavel para 72px com icones)
  Header height: 64px
  Content max-width: 1440px
  Content padding: 24px (p-6)
  Card padding: 24px (p-6)
  Card gap: 16px (gap-4)
  Section gap: 32px (gap-8)

Breakpoints:
  sm:  640px   (mobile landscape)
  md:  768px   (tablet)
  lg:  1024px  (laptop)
  xl:  1280px  (desktop)
  2xl: 1536px  (widescreen)
```

### 2.4 Componentes Base

#### Button

```
Variantes:
  primary:     bg-indigo-500 hover:bg-indigo-400 text-white
  secondary:   bg-surface-2 hover:bg-surface-3 text-text-primary border border-border-default
  ghost:       bg-transparent hover:bg-surface-1 text-text-secondary
  danger:      bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/20
  success:     bg-green-500/10 hover:bg-green-500/20 text-green-400

Tamanhos:
  sm: h-8 px-3 text-sm rounded-md
  md: h-10 px-4 text-sm rounded-lg       (padrao)
  lg: h-12 px-6 text-base rounded-lg
  icon: h-10 w-10 rounded-lg             (apenas icone)

Estados:
  disabled:  opacity-50 cursor-not-allowed
  loading:   spinner animado substituindo conteudo
  focus:     ring-2 ring-indigo-500/50 ring-offset-2 ring-offset-bg-primary
```

#### Input

```
Base: h-10 bg-bg-tertiary border border-border-default rounded-lg px-3
      text-text-primary placeholder:text-text-tertiary
      focus:border-accent-primary focus:ring-1 focus:ring-accent-primary/50

Com label:    Label em text-sm font-medium text-text-secondary mb-1.5
Com erro:     border-red-500 + mensagem em text-xs text-red-400 mt-1
Com icone:    Icone posicionado absolute left-3, input com pl-10
Com addon:    Grupo flex com prefix/suffix em bg-surface-1
```

#### Badge / StatusBadge

```
Base: inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium

Status mapping (ChargeStatus):
  PENDING:     bg-amber-500/10 text-amber-400 border border-amber-500/20
  CONFIRMED:   bg-blue-500/10 text-blue-400 border border-blue-500/20
  RECEIVED:    bg-green-500/10 text-green-400 border border-green-500/20
  OVERDUE:     bg-orange-500/10 text-orange-400 border border-orange-500/20
  REFUNDED:    bg-purple-500/10 text-purple-400 border border-purple-500/20
  CHARGEBACK:  bg-red-500/10 text-red-400 border border-red-500/20
  CANCELED:    bg-gray-500/10 text-gray-400 border border-gray-500/20

Status mapping (SubscriptionStatus):
  ACTIVE:      bg-green-500/10 text-green-400
  PAUSED:      bg-amber-500/10 text-amber-400
  SUSPENDED:   bg-orange-500/10 text-orange-400
  CANCELED:    bg-red-500/10 text-red-400
  EXPIRED:     bg-gray-500/10 text-gray-400

Com dot animado: Bolinha 6px com animacao pulse para PENDING/PROCESSING
```

#### Card

```
Base: bg-surface-1 rounded-xl border border-border-subtle p-6
Hover: hover:border-border-default transition-colors duration-200
Interactive: cursor-pointer hover:bg-surface-2
Destaque: border-l-4 border-l-indigo-500 (cards de metricas)
```

#### DataTable

```
Estrutura:
  - Header fixo com bg-bg-secondary sticky top-0
  - Colunas sortaveis com indicador de seta
  - Hover nas linhas: bg-surface-1/50
  - Zebra striping sutil: even:bg-surface-1/30
  - Loading: skeleton rows animados
  - Empty state: ilustracao + mensagem + CTA

Paginacao:
  - Botoes prev/next com contadores
  - Selector de page size (10, 20, 50)
  - Indicador "Mostrando X-Y de Z"
  - Posicao: fixo no bottom do card
```

### 2.5 Animacoes e Transicoes (Framer Motion)

```tsx
// Transicao entre paginas (React Router)
const pageVariants = {
  initial: { opacity: 0, y: 12 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.3, ease: "easeOut" } },
  exit:    { opacity: 0, y: -8, transition: { duration: 0.15 } }
};

// Entrada de cards em lista (stagger)
const listVariants = {
  animate: { transition: { staggerChildren: 0.05 } }
};
const itemVariants = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.2 } }
};

// Modal / Dialog
const dialogVariants = {
  initial: { opacity: 0, scale: 0.95 },
  animate: { opacity: 1, scale: 1, transition: { duration: 0.2 } },
  exit:    { opacity: 0, scale: 0.95, transition: { duration: 0.15 } }
};

// Sidebar collapse
const sidebarVariants = {
  expanded: { width: 260, transition: { duration: 0.3, ease: "easeInOut" } },
  collapsed: { width: 72, transition: { duration: 0.3, ease: "easeInOut" } }
};

// Toast notification (Sonner)
// Entra de cima (y: -20 -> 0), sai para direita (x: 0 -> 100%)

// Skeleton shimmer: background gradient animado via CSS
// @keyframes shimmer { 0% { bg-pos: -200% } 100% { bg-pos: 200% } }

// Status badge pulse (PENDING/PROCESSING):
// Dot com animate-pulse (opacity 0 -> 100% loop 2s)

// Hover em linhas de tabela: bg-color transition 150ms
// Focus ring: ring transition 150ms
// Botao click: scale(0.98) 100ms
```

---

## 3. Roteamento e Navegacao

### 3.1 Mapa de Rotas

```
/login                                    -> Login.tsx
/                                         -> redirect /dashboard
/dashboard                                -> Dashboard.tsx

/companies                                -> CompanyList.tsx
/companies/new                            -> CompanyForm.tsx (create)
/companies/:id                            -> CompanyDetail.tsx
/companies/:id/edit                       -> CompanyForm.tsx (edit)

/customers                                -> CustomerList.tsx
/customers/new                            -> CustomerForm.tsx (create)
/customers/:id                            -> CustomerDetail.tsx
/customers/:id/edit                       -> CustomerForm.tsx (edit)

/plans                                    -> PlanList.tsx
/plans/new                                -> PlanForm.tsx (create)
/plans/:id                                -> PlanDetail.tsx
/plans/:id/edit                           -> PlanForm.tsx (edit)

/charges                                  -> ChargeList.tsx
/charges/new                              -> ChargeCreate.tsx
/charges/:id                              -> ChargeDetail.tsx

/subscriptions                            -> SubscriptionList.tsx
/subscriptions/new                        -> SubscriptionCreate.tsx
/subscriptions/:id                        -> SubscriptionDetail.tsx
/subscriptions/:id/change-plan            -> PlanChange.tsx

/admin/webhooks                           -> WebhookDashboard.tsx
/admin/outbox                             -> OutboxDashboard.tsx
/admin/reconciliation                     -> ReconciliationPanel.tsx

/reports                                  -> ReportsDashboard.tsx
```

### 3.2 Sidebar - Estrutura de Menu

```
Logo/Brand (topo)
─────────────────────
PRINCIPAL
  Dashboard          (LayoutDashboard icon)
  Clientes           (Users icon)
  Planos             (Package icon)
  Cobrancas          (Receipt icon)
  Assinaturas        (RefreshCw icon)

ADMINISTRACAO
  Empresas           (Building2 icon)         [HOLDING_ADMIN only]
  Webhooks           (Webhook icon)           [HOLDING_ADMIN only]
  Outbox             (Send icon)              [HOLDING_ADMIN only]
  Reconciliacao      (GitCompare icon)        [HOLDING_ADMIN only]

ANALYTICS
  Relatorios         (BarChart3 icon)
─────────────────────
Tenant Switcher (bottom)
  [Logo empresa] Empresa Atual
  Dropdown para trocar tenant
```

### 3.3 Breadcrumb

```
Padrao: Home > Secao > Detalhe/Acao

Exemplos:
  Dashboard
  Clientes > Lista
  Clientes > Joao Silva > Editar
  Cobrancas > Nova Cobranca
  Assinaturas > SUB-123 > Mudar Plano
  Admin > Webhooks
```

---

## 4. Telas Detalhadas

### 4.1 Login (`/login`)

```
Layout:
  Tela inteira, dividida em 2 colunas (desktop):
    Esquerda (50%): Gradiente indigo-to-purple com branding
      - Logo grande centralizado
      - Titulo: "Payment API"
      - Subtitulo: "Multi-Tenant Payment Management"
      - Ilustracao abstrata de ondas/grid (CSS only)
    Direita (50%): Formulario de login centralizado

Formulario:
  - Input: Email (icon Mail)
  - Input: Password (icon Lock, toggle visibility)
  - Checkbox: "Lembrar-me" (salva refresh token)
  - Button: "Entrar" (full width, primary)

Validacao (Zod):
  - email: z.string().email("Email invalido")
  - password: z.string().min(1, "Senha obrigatoria")

Fluxo:
  1. POST /api/v1/auth/login { email, password }
  2. Salva accessToken + refreshToken no Zustand (persist localStorage)
  3. Decodifica JWT -> extrai roles, company_id, name
  4. Redirect para /dashboard

Erro:
  - Toast vermelho "Credenciais invalidas" (sonner)
  - Input de password fica com borda vermelha + shake animation

Animacao:
  - Formulario entra com fadeIn + slideUp (300ms)
  - Botao "Entrar": loading spinner durante request
  - Background da esquerda: gradiente com subtle movement (CSS animation)
```

### 4.2 Dashboard (`/dashboard`)

```
Layout: Grid responsivo de metricas + graficos

Secao 1 - KPI Cards (grid 4 colunas):
  Card 1: "Receita (30d)"
    - Valor grande em BRL (R$ XX.XXX,XX)
    - Comparativo com periodo anterior (+X%)
    - Icone: DollarSign
    - Cor: green
    - API: GET /api/v1/reports/revenue?from=30d_ago&to=today

  Card 2: "MRR"
    - Valor mensal recorrente
    - ARR abaixo em texto menor
    - Icone: TrendingUp
    - Cor: indigo
    - API: GET /api/v1/reports/subscriptions/mrr

  Card 3: "Assinaturas Ativas"
    - Numero total
    - Icone: RefreshCw
    - Cor: blue
    - API: GET /api/v1/subscriptions?status=ACTIVE (total do Page)

  Card 4: "Cobrancas Vencidas"
    - Numero total + valor total
    - Icone: AlertTriangle
    - Cor: red (destaque se > 0)
    - API: GET /api/v1/reports/overdue

Secao 2 - Graficos (grid 2 colunas):
  Grafico Esquerda: "Receita por Origem" (Recharts BarChart)
    - Barras empilhadas por ChargeOrigin (WEB, PDV, RECURRING, API, etc)
    - Periodo selecionavel: 7d, 30d, 90d
    - Tooltip com valor formatado
    - API: GET /api/v1/reports/revenue?groupBy=origin

  Grafico Direita: "Taxa de Churn" (Recharts AreaChart)
    - Linha de churn rate ao longo do tempo
    - Area preenchida com gradiente vermelho transparente
    - Periodo selecionavel
    - API: GET /api/v1/reports/subscriptions/churn

Secao 3 - Status Operacional (grid 3 colunas):
  Mini-Card: "Webhooks"
    - Contadores: Pending | Processing | Failed | DLQ
    - Barra de progresso por status
    - API: GET /api/v1/admin/webhooks/summary

  Mini-Card: "Outbox"
    - Contadores: Pending | Published | Failed | DLQ
    - Lag em segundos
    - API: GET /api/v1/admin/outbox/summary

  Mini-Card: "Ultima Reconciliacao"
    - Data/hora da ultima execucao
    - Divergencias encontradas
    - Botao "Executar Agora"

Secao 4 - Atividade Recente (tabela):
  Ultimas 10 cobrancas criadas/pagas
  Colunas: Cliente | Tipo | Valor | Status | Data
  Link para detalhe ao clicar
  API: GET /api/v1/charges?sort=createdAt,desc&size=10

Animacoes:
  - KPI cards: stagger fadeIn (cada card 50ms delay)
  - Numeros: count-up animation (0 -> valor final em 800ms)
  - Graficos: draw animation dos paths (1s)
  - Refresh: skeleton shimmer durante loading
```

### 4.3 Empresas (HOLDING_ADMIN)

#### CompanyList (`/companies`)

```
Header:
  Titulo: "Empresas"
  Botao: "+ Nova Empresa" (primary)

Tabela:
  Colunas: CNPJ | Razao Social | Ambiente Asaas | Status | Chave Asaas | Criado em | Acoes
  Status badge com cores:
    ACTIVE -> green
    SUSPENDED -> amber
    DEFAULTING -> red
  Chave Asaas: icone check (verde) ou X (vermelho) baseado em hasAsaasKey
  Acoes: dropdown com Editar, Configurar Credenciais, Testar Conexao

API: GET /api/v1/companies (paginado)
```

#### CompanyForm (`/companies/new` e `/companies/:id/edit`)

```
Formulario em 2 secoes (Tabs ou accordion):

Tab 1 - Dados da Empresa:
  - CNPJ (mask: XX.XXX.XXX/XXXX-XX)
  - Razao Social
  - Nome Fantasia
  - Email
  - Telefone (mask)

Tab 2 - Configuracao Asaas:
  - Chave API Asaas (input password com toggle)
  - Ambiente: SANDBOX | PRODUCTION (select com badges coloridos)
  - Botao "Testar Conexao" -> POST /companies/{id}/test-connection
    - Sucesso: toast verde "Conexao OK - Conta: {nome}"
    - Falha: toast vermelho com mensagem de erro

Tab 3 - Politicas:
  - Politica de Mudanca de Plano: IMMEDIATE_PRORATA | END_OF_CYCLE | IMMEDIATE_NO_PRORATA (radio group com descricao)
  - Estrategia de Downgrade: BLOCK | SCHEDULE | GRACE_PERIOD (radio group)
  - Dias de Carencia (grace period): number input (visivel se GRACE_PERIOD selecionado)

Validacao Zod:
  cnpj: z.string().min(14).max(18)
  razaoSocial: z.string().min(1)
  asaasApiKey: z.string().optional()
  asaasEnv: z.enum(["SANDBOX", "PRODUCTION"]).optional()
```

### 4.4 Clientes

#### CustomerList (`/customers`)

```
Header:
  Titulo: "Clientes"
  Busca: input de pesquisa (debounce 300ms) -> query param "search"
  Botao: "+ Novo Cliente" (primary)

Tabela:
  Colunas: Nome | Documento | Email | Telefone | Saldo Credito | Asaas ID | Acoes
  Saldo Credito: formatado BRL, verde se > 0
  Asaas ID: monospace com CopyButton
  Acoes: Ver | Editar | Sincronizar | Excluir

Filtros: (dropdown inline)
  - Todos / Apenas ativos / Apenas excluidos

API: GET /api/v1/customers?search={term}&page={n}&size={n}
```

#### CustomerDetail (`/customers/:id`)

```
Layout em 2 colunas:

Coluna Esquerda (60%):
  Card "Dados Pessoais":
    - Avatar circular com iniciais
    - Nome, Documento, Email, Telefone
    - Endereco completo
    - Botoes: Editar | Sincronizar com Asaas | Excluir (danger)

  Card "Cobrancas Recentes":
    - Mini tabela: Valor | Tipo | Status | Vencimento
    - Link "Ver todas" -> /charges?customerId={id}

  Card "Assinaturas":
    - Lista de assinaturas do cliente
    - Status badge + nome do plano + valor
    - Link "Ver todas" -> /subscriptions?customerId={id}

Coluna Direita (40%):
  Card "Saldo de Credito":
    - Valor grande em destaque (R$ X.XXX,XX)
    - Icone de wallet

  Card "Historico de Credito (Ledger)":
    - Timeline vertical de movimentacoes
    - Cada item: tipo (CREDIT/DEBIT) + valor + origem + data
    - CREDIT: texto verde com seta para cima
    - DEBIT: texto vermelho com seta para baixo
    - Paginacao scroll infinito

API: GET /api/v1/customers/{id}
API: GET /api/v1/customers/{id}/credit-balance
API: GET /api/v1/charges?customerId={id}
API: GET /api/v1/subscriptions?customerId={id}
```

#### CustomerForm (`/customers/new` e `/customers/:id/edit`)

```
Formulario em secoes:

Secao 1 - Identificacao:
  - Nome (required)
  - CPF/CNPJ (mask automatica baseada no tamanho)
  - Email
  - Telefone (mask)

Secao 2 - Endereco:
  - CEP (com busca automatica via ViaCEP - opcional)
  - Rua
  - Numero
  - Complemento
  - Bairro
  - Cidade
  - Estado (select UFs)

Botoes:
  - "Cancelar" (secondary) -> volta para lista
  - "Salvar" (primary) -> POST ou PUT

Animacao:
  - Secoes com accordion animado
  - Sucesso: redirect com toast verde
```

### 4.5 Planos

#### PlanList (`/plans`)

```
Header:
  Titulo: "Planos"
  Toggle: "Ativos" | "Inativos" | "Todos" (tabs inline)
  Botao: "+ Novo Plano" (primary)

Exibicao: Grid de cards (nao tabela) - 3 colunas

Cada card de plano:
  ┌─────────────────────────────────────┐
  │ [Badge: MONTHLY]     [v2]          │
  │                                     │
  │ Plano Basico                        │
  │ Descricao do plano aqui             │
  │                                     │
  │ R$ 99,90 /mes                       │
  │                                     │
  │ Trial: 7 dias | Setup: R$ 50,00     │
  │                                     │
  │ Tier Order: 1                       │
  │                                     │
  │ [Editar] [Nova Versao] [Desativar]  │
  └─────────────────────────────────────┘

  Card ativo:   border-left verde
  Card inativo: opacity-60, border-left gray

API: GET /api/v1/plans
```

#### PlanForm (`/plans/new` e `/plans/:id/edit`)

```
Secao 1 - Informacoes Basicas:
  - Nome (required)
  - Descricao (textarea)
  - Valor (R$) - input monetario com mascara
  - Ciclo: MONTHLY | QUARTERLY | SEMIANNUALLY | YEARLY (select com descricao)
  - Tier Order (number - para ordenacao de upgrade/downgrade)

Secao 2 - Configuracoes Opcionais:
  - Dias de Trial (number, default 0)
  - Taxa de Setup (R$, default 0)

Secao 3 - Limites e Features (JSON editors):
  - Limites: editor JSON com syntax highlight
    Exemplo: { "maxUsers": 10, "maxStorage": "5GB" }
  - Features: editor JSON
    Exemplo: { "support": "email", "api": true }

Botao especial em edicao:
  "Criar Nova Versao" -> POST /plans/{id}/new-version
  Tooltip: "Cria uma nova versao mantendo o historico"
```

### 4.6 Cobrancas

#### ChargeList (`/charges`)

```
Header:
  Titulo: "Cobrancas"
  Filtros inline (row de selects/inputs):
    - Status: all | PENDING | CONFIRMED | RECEIVED | OVERDUE | REFUNDED | CANCELED
    - Tipo: all | PIX | BOLETO | CREDIT_CARD
    - Origem: all | WEB | PDV | RECURRING | API | BACKOFFICE | PLAN_CHANGE
    - Data inicio (date picker)
    - Data fim (date picker)
    - Cliente (search select)
  Botao: "+ Nova Cobranca" (primary, dropdown com opcoes)

Tabela:
  Colunas: ID | Cliente | Tipo | Valor | Vencimento | Status | Origem | Asaas ID | Acoes
  Tipo com icone colorido:
    PIX: icone QrCode + teal
    BOLETO: icone FileText + amber
    CREDIT_CARD: icone CreditCard + violet
  Status: StatusBadge component
  Acoes: Ver | Cancelar | Reembolsar

API: GET /api/v1/charges?status={}&billingType={}&origin={}&dueDateFrom={}&dueDateTo={}&customerId={}&page={}&size={}
```

#### ChargeCreate (`/charges/new`)

```
Wizard de 3 etapas com progress bar:

Etapa 1 - Tipo de Cobranca:
  Grid de cards selecionaveis (radio visual):
  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
  │    [QR]      │ │    [Doc]     │ │   [Card]     │
  │    PIX       │ │   BOLETO     │ │   CARTAO     │
  │  Instantaneo │ │  3 dias uteis│ │  Credito     │
  └──────────────┘ └──────────────┘ └──────────────┘

  Sub-opcoes (se CREDIT_CARD ou BOLETO selecionado):
    [ ] Pagamento parcelado
    Se sim: Numero de parcelas (min 2)

Etapa 2 - Dados da Cobranca:
  - Cliente (search select com autocomplete)
    Ao selecionar, mostra mini-card do cliente
  - Valor (R$)
  - Data de vencimento (date picker, min: hoje)
  - Descricao (opcional)
  - Referencia externa (opcional)

  Se CREDIT_CARD:
    Secao "Dados do Cartao":
      - Nome no cartao
      - Numero (mask: XXXX XXXX XXXX XXXX)
      - Validade mes/ano (2 selects)
      - CVV (3 digitos)
      - Ou: Token do cartao (input alternativo)

    Secao "Dados do Titular":
      - Nome
      - Email
      - CPF/CNPJ
      - CEP
      - Numero do endereco
      - Telefone

Etapa 3 - Confirmacao:
  Resumo visual de todos os dados:
    Tipo | Cliente | Valor | Vencimento | Parcelas (se aplicavel)
  Input: Idempotency-Key (auto-gerado UUID, editavel)
  Botao: "Criar Cobranca" (primary, com loading)

Fluxo API:
  PIX:                POST /api/v1/charges/pix
  BOLETO:             POST /api/v1/charges/boleto
  CREDIT_CARD:        POST /api/v1/charges/credit-card
  CC Parcelado:       POST /api/v1/charges/credit-card/installments
  BOLETO Parcelado:   POST /api/v1/charges/boleto/installments

Headers: { "Idempotency-Key": "{uuid}" }

Sucesso:
  Toast verde + redirect para ChargeDetail
  Se PIX: mostra QR Code imediatamente
  Se BOLETO: mostra linha digitavel

Animacao:
  - Transicao entre etapas: slide horizontal
  - Progress bar: width animado
  - Card de tipo: scale + glow ao selecionar
```

#### ChargeDetail (`/charges/:id`)

```
Layout:

Header:
  Titulo: "Cobranca #123"
  Badges: StatusBadge + BillingTypeBadge
  Botoes de acao (condicionais por status):
    PENDING:    [Cancelar] [Reenviar Notificacao]
    CONFIRMED:  [Reembolsar] [Cancelar]
    RECEIVED:   [Reembolsar]
    OVERDUE:    [Marcar como Recebido] [Cancelar] [Regenerar Boleto]

Card principal (2 colunas):
  Esquerda:
    - Cliente: nome com link
    - Valor: R$ XX,XX
    - Vencimento: DD/MM/YYYY
    - Origem: badge
    - Descricao
    - Referencia externa
    - Asaas ID (monospace + CopyButton)
    - Criado em / Atualizado em

  Direita (condicional por tipo):
    Se PIX:
      Componente PixQrCodeViewer:
        - QR Code renderizado (imagem base64)
        - Copia e cola: input readonly com CopyButton
        - Data de expiracao
        - API: GET /charges/{id}/pix-qrcode

    Se BOLETO:
      Componente BoletoViewer:
        - Linha digitavel com CopyButton
        - Nosso numero
        - Codigo de barras
        - Link para boleto PDF (invoiceUrl)
        - API: GET /charges/{id}/boleto-line

    Se CREDIT_CARD:
      - Status do pagamento
      - Numero da parcela (se parcelado)

Card "Fluxo de Status":
  Componente ChargeStatusFlow:
    Visualizacao horizontal dos estados possiveis
    Estado atual destacado com cor e glow
    Estados futuros em cinza tracejado
    Transicoes como setas entre estados

    PENDING -> CONFIRMED -> RECEIVED
                    \-> REFUNDED
                    \-> CHARGEBACK
         \-> OVERDUE -> RECEIVED
         \-> CANCELED

Card "Dialogo de Reembolso" (Dialog):
  - Tipo: Total ou Parcial (toggle)
  - Se parcial: input de valor (max: valor da cobranca)
  - Descricao (opcional)
  - Botao "Confirmar Reembolso" (danger)
  - API: POST /charges/{id}/refund { value, description }

API: GET /api/v1/charges/{id}
```

### 4.7 Assinaturas

#### SubscriptionList (`/subscriptions`)

```
Header:
  Titulo: "Assinaturas"
  Filtros:
    - Status: all | ACTIVE | PAUSED | SUSPENDED | CANCELED | EXPIRED
    - Cliente (search select)
  Botao: "+ Nova Assinatura"

Tabela:
  Colunas: ID | Cliente | Plano | Tipo Pgto | Valor | Proximo Venc. | Status | Acoes
  Acoes: Ver | Pausar/Retomar | Cancelar | Mudar Plano
```

#### SubscriptionCreate (`/subscriptions/new`)

```
Formulario:

Secao 1 - Cliente e Plano:
  - Cliente (search select)
  - Plano (select com preview do card do plano selecionado)
    Ao selecionar: mostra nome, valor, ciclo, features

Secao 2 - Pagamento:
  - Tipo de pagamento: PIX | BOLETO | CREDIT_CARD (card select visual)
  - Proxima data de vencimento (date picker, opcional)
  - Descricao (opcional)
  - Referencia externa (opcional)

  Se CREDIT_CARD:
    Mesmos campos de cartao do ChargeCreate

Botao: "Criar Assinatura" (primary)
API: POST /api/v1/subscriptions
```

#### SubscriptionDetail (`/subscriptions/:id`)

```
Layout:

Header:
  Titulo: "Assinatura #123 - Plano Premium"
  StatusBadge grande
  Botoes:
    ACTIVE:    [Pausar] [Cancelar] [Mudar Plano]
    PAUSED:    [Retomar] [Cancelar]
    SUSPENDED: [Reativar] [Cancelar]

Card "Informacoes":
  2 colunas:
    - Cliente (link)
    - Plano (link)
    - Tipo de pagamento
    - Valor
    - Ciclo
    - Inicio do periodo atual
    - Fim do periodo atual
    - Proximo vencimento
    - Asaas ID

Card "Timeline da Assinatura":
  Componente SubscriptionTimeline:
    Timeline vertical mostrando:
    - Criacao
    - Mudancas de plano
    - Pausas/retomadas
    - Cobrancas geradas

Card "Cobrancas":
  Tabela paginada de charges da subscription
  API: GET /api/v1/subscriptions/{id}/charges

Card "Historico de Mudancas de Plano":
  Tabela de plan changes
  Colunas: Plano Anterior | Novo Plano | Tipo | Politica | Status | Data
  API: GET /api/v1/subscriptions/{id}/plan-changes

Card "Atualizar Metodo de Pagamento" (Dialog):
  - Novo tipo: PIX | BOLETO | CREDIT_CARD
  - Se cartao: formulario de cartao
  - API: PATCH /subscriptions/{id}/payment-method

APIs:
  GET /api/v1/subscriptions/{id}
  GET /api/v1/subscriptions/{id}/charges
  GET /api/v1/subscriptions/{id}/plan-changes
  POST /api/v1/subscriptions/{id}/pause
  POST /api/v1/subscriptions/{id}/resume
  DELETE /api/v1/subscriptions/{id}
```

### 4.8 Mudanca de Plano (`/subscriptions/:id/change-plan`)

```
Wizard de 2 etapas:

Etapa 1 - Selecao do Novo Plano:
  Grid de cards de planos disponiveis (exceto o atual)
  Card do plano atual destacado com label "Atual"
  Cada card mostra: nome, valor, ciclo, features

  Ao selecionar um plano:
    Componente PlanChangePreview (inline, abaixo da grid):
      API: POST /subscriptions/{id}/preview-change?newPlanId={id}

      Exibe:
      ┌────────────────────────────────────────────────────┐
      │ PREVIEW DA MUDANCA                                 │
      │                                                    │
      │ Plano Atual:    Basico      R$ 99,90/mes          │
      │ Novo Plano:     Premium     R$ 199,90/mes         │
      │                                                    │
      │ Tipo:           UPGRADE     (badge verde)          │
      │ Politica:       IMMEDIATE_PRORATA                  │
      │                                                    │
      │ ─────────────────────────────────────────          │
      │ Diferenca:         R$ 100,00                       │
      │ Credito Pro-rata: -R$  33,30                       │
      │ Cobranca:          R$  66,70                       │
      │ ─────────────────────────────────────────          │
      └────────────────────────────────────────────────────┘

Etapa 2 - Confirmacao:
  Resumo completo
  Checkbox: "Confirmo a mudanca de plano"
  Input opcional: currentUsage (JSON) e requestedBy
  Botao: "Confirmar Mudanca" (primary)

  API: POST /subscriptions/{id}/change-plan
       Body: { newPlanId, currentUsage, requestedBy }

Animacao:
  - Cards de plano: hover scale(1.02) + shadow
  - Preview: slideDown com height animation
  - Sucesso: confetti sutil (upgrade) ou checkmark animation
```

### 4.9 Webhooks Admin (`/admin/webhooks`)

```
Layout:

Secao 1 - Resumo (cards metricas):
  6 cards em grid:
    Pending | Processing | Deferred | Processed | Failed | DLQ
  Cada card com numero grande + icone
  API: GET /api/v1/admin/webhooks/summary

Secao 2 - Filtro por Status:
  Tabs: PENDING | PROCESSING | DEFERRED | PROCESSED | FAILED | DLQ

Secao 3 - Lista de Eventos:
  Tabela:
    Colunas: ID | Asaas Event ID | Tipo | Status | Tentativas | Proximo Retry | Erro | Acoes
    Acoes: Replay (botao com icone RefreshCw)
      Confirmacao antes de replay
      API: POST /admin/webhooks/{eventId}/replay

  Linha expandivel (click):
    Payload JSON formatado com syntax highlight
    Historico de tentativas

Animacao:
  - Cards de metricas: count-up
  - Tab switch: slide horizontal do conteudo
  - Replay: spinner no botao + badge update animado
```

### 4.10 Outbox Admin (`/admin/outbox`)

```
Layout similar ao Webhook Dashboard:

Secao 1 - Resumo:
  4 cards: Pending | Published | Failed | DLQ
  Card extra: "Lag" com numero em segundos (destaque amarelo se > 30s)

Secao 2 - Filtro:
  Tabs: PENDING | PUBLISHED | FAILED | DLQ

Secao 3 - Lista:
  Tabela:
    Colunas: ID | Aggregate Type | Aggregate ID | Event Type | Status | Tentativas | Criado em | Publicado em | Erro
    Acoes: Retry (se FAILED/DLQ)
      API: POST /admin/outbox/{id}/retry

  Linha expandivel:
    Payload JSON
```

### 4.11 Reconciliacao (`/admin/reconciliation`)

```
Layout:

Secao 1 - Acoes (3 cards com botoes):
  Card "Reconciliar Cobrancas":
    Input: Dias retroativos (default 3)
    Botao: "Executar" (primary)
    API: POST /admin/reconciliation/charges?daysBack={n}

  Card "Reconciliar Assinaturas":
    Botao: "Executar" (primary)
    API: POST /admin/reconciliation/subscriptions

  Card "Replay DLQ":
    Botao: "Executar" (warning)
    API: POST /admin/reconciliation/dlq/replay

Secao 2 - Resultado (aparece apos execucao):
  Card resultado:
    - Executado em: datetime
    - Total verificado: N
    - Divergencias encontradas: N (badge red se > 0)
    - Auto-corrigidas: N (badge green)

  Tabela de divergencias (se houver):
    Colunas: Tipo Entidade | ID Local | Asaas ID | Status Local | Status Asaas | Acao Tomada
    Cada linha com icone de alerta

Animacao:
  - Botao de execucao: loading com progress indeterminado
  - Resultado: fadeIn com highlight
  - Divergencias: listagem com stagger
```

### 4.12 Relatorios (`/reports`)

```
Layout com Tabs:

Tab 1 - Receita:
  Filtros: Data inicio | Data fim | Agrupar por (origin, billingType)
  Grafico: BarChart empilhado (Recharts)
  Tabela abaixo: grupo | qtd cobrancas | valor total
  Botao: "Exportar CSV" (download)
  API: GET /reports/revenue
  API: GET /reports/export/revenue

Tab 2 - MRR/ARR:
  Cards: MRR | ARR | Assinaturas Ativas
  Grafico: LineChart de evolucao MRR (se dados historicos disponiveis)
  Botao: "Exportar CSV"
  API: GET /reports/subscriptions/mrr
  API: GET /reports/export/mrr

Tab 3 - Churn:
  Filtros: Data inicio | Data fim
  Cards: Taxa de Churn | Canceladas no Periodo | Ativas no Inicio
  Grafico: AreaChart vermelho com taxa de churn
  Botao: "Exportar CSV"
  API: GET /reports/subscriptions/churn
  API: GET /reports/export/churn

Tab 4 - Inadimplencia:
  Tabela: Cliente | Qtd Vencidas | Valor Total Vencido
  Ordenacao por valor total desc
  Linhas com valor > R$ 1.000: destaque vermelho
  Botao: "Exportar CSV"
  API: GET /reports/overdue
  API: GET /reports/export/overdue
```

---

## 5. Integracao com a API

### 5.1 Axios Client (`api/client.ts`)

```typescript
// Configuracao base
const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "http://localhost:8080",
  timeout: 30000,
  headers: { "Content-Type": "application/json" }
});

// Request interceptor
api.interceptors.request.use((config) => {
  const { accessToken } = useAuthStore.getState();
  const { companyId } = useTenantStore.getState();

  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  if (companyId) {
    config.headers["X-Company-Id"] = companyId;
  }
  return config;
});

// Response interceptor - refresh automatico
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      const { refreshToken, setTokens, logout } = useAuthStore.getState();
      if (refreshToken) {
        try {
          const { data } = await axios.post(`${baseURL}/api/v1/auth/refresh`, {
            refreshToken
          });
          setTokens(data.accessToken, data.refreshToken);
          error.config.headers.Authorization = `Bearer ${data.accessToken}`;
          return api(error.config); // retry original request
        } catch {
          logout();
          window.location.href = "/login";
        }
      }
    }
    return Promise.reject(error);
  }
);
```

### 5.2 Idempotency

```typescript
// Automaticamente adicionado em POST requests para endpoints de criacao
api.interceptors.request.use((config) => {
  if (config.method === "post" && !config.headers["Idempotency-Key"]) {
    config.headers["Idempotency-Key"] = crypto.randomUUID();
  }
  return config;
});
```

### 5.3 TanStack Query - Patterns

```typescript
// Query padrao com paginacao
const useCharges = (filters: ChargeFilters, page: number) =>
  useQuery({
    queryKey: ["charges", filters, page],
    queryFn: () => chargeApi.list({ ...filters, page, size: 20 }),
    placeholderData: keepPreviousData, // evita flash durante paginacao
  });

// Mutation com invalidacao
const useCreateCharge = () =>
  useMutation({
    mutationFn: chargeApi.createPix,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["charges"] });
      toast.success("Cobranca criada com sucesso");
    },
    onError: (err: AxiosError<ApiError>) => {
      toast.error(err.response?.data?.message || "Erro ao criar cobranca");
    }
  });
```

### 5.4 Error Handling

```typescript
// A API retorna erros no formato:
interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  // Para erros de validacao:
  fieldErrors?: Record<string, string>;
  // Para erros Asaas:
  asaasErrors?: Array<{ code: string; description: string }>;
}

// Componente ErrorBanner para exibir erros de forma amigavel
// Toast para erros transientes (network, 5xx)
// Inline validation errors mapeados para os campos do form
```

---

## 6. Gestao de Estado

### 6.1 Auth Store (Zustand + persist)

```typescript
interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: {
    id: number;
    email: string;
    name: string;
    companyId: number;
    roles: string[];
  } | null;
  isAuthenticated: boolean;

  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  setTokens: (access: string, refresh: string) => void;
  hasRole: (role: string) => boolean;
  isHoldingAdmin: () => boolean;
}
```

### 6.2 Tenant Store (Zustand)

```typescript
interface TenantState {
  companyId: number | null;
  companyName: string | null;
  companies: CompanyResponse[]; // lista para HOLDING_ADMIN

  setCompany: (id: number, name: string) => void;
  loadCompanies: () => Promise<void>;
}
```

---

## 7. TypeScript Types

### 7.1 Enums (constantes)

```typescript
// types/enums.ts

export const ChargeStatus = {
  PENDING: "PENDING",
  CONFIRMED: "CONFIRMED",
  RECEIVED: "RECEIVED",
  OVERDUE: "OVERDUE",
  REFUNDED: "REFUNDED",
  CHARGEBACK: "CHARGEBACK",
  CANCELED: "CANCELED",
} as const;

export const BillingType = {
  PIX: "PIX",
  BOLETO: "BOLETO",
  CREDIT_CARD: "CREDIT_CARD",
  DEBIT_CARD: "DEBIT_CARD",
  UNDEFINED: "UNDEFINED",
} as const;

export const ChargeOrigin = {
  WEB: "WEB",
  PDV: "PDV",
  RECURRING: "RECURRING",
  API: "API",
  BACKOFFICE: "BACKOFFICE",
  PLAN_CHANGE: "PLAN_CHANGE",
} as const;

export const SubscriptionStatus = {
  ACTIVE: "ACTIVE",
  PAUSED: "PAUSED",
  SUSPENDED: "SUSPENDED",
  CANCELED: "CANCELED",
  EXPIRED: "EXPIRED",
} as const;

export const PlanCycle = {
  MONTHLY: "MONTHLY",
  QUARTERLY: "QUARTERLY",
  SEMIANNUALLY: "SEMIANNUALLY",
  YEARLY: "YEARLY",
} as const;

export const PlanChangePolicy = {
  IMMEDIATE_PRORATA: "IMMEDIATE_PRORATA",
  END_OF_CYCLE: "END_OF_CYCLE",
  IMMEDIATE_NO_PRORATA: "IMMEDIATE_NO_PRORATA",
} as const;

export const PlanChangeType = {
  UPGRADE: "UPGRADE",
  DOWNGRADE: "DOWNGRADE",
  SIDEGRADE: "SIDEGRADE",
} as const;

export const PlanChangeStatus = {
  PENDING: "PENDING",
  AWAITING_PAYMENT: "AWAITING_PAYMENT",
  SCHEDULED: "SCHEDULED",
  EFFECTIVE: "EFFECTIVE",
  FAILED: "FAILED",
  CANCELED: "CANCELED",
} as const;

export const WebhookEventStatus = {
  PENDING: "PENDING",
  PROCESSING: "PROCESSING",
  DEFERRED: "DEFERRED",
  PROCESSED: "PROCESSED",
  FAILED: "FAILED",
  DLQ: "DLQ",
} as const;

export const OutboxStatus = {
  PENDING: "PENDING",
  PUBLISHED: "PUBLISHED",
  FAILED: "FAILED",
  DLQ: "DLQ",
} as const;

export const CompanyStatus = {
  ACTIVE: "ACTIVE",
  SUSPENDED: "SUSPENDED",
  DEFAULTING: "DEFAULTING",
} as const;

export const Role = {
  ROLE_HOLDING_ADMIN: "ROLE_HOLDING_ADMIN",
  ROLE_COMPANY_ADMIN: "ROLE_COMPANY_ADMIN",
  ROLE_COMPANY_OPERATOR: "ROLE_COMPANY_OPERATOR",
  ROLE_SYSTEM: "ROLE_SYSTEM",
} as const;
```

### 7.2 Labels e Cores (constantes de UI)

```typescript
// lib/constants.ts

export const CHARGE_STATUS_CONFIG: Record<string, { label: string; color: string; bg: string }> = {
  PENDING:    { label: "Pendente",      color: "text-amber-400",   bg: "bg-amber-500/10" },
  CONFIRMED:  { label: "Confirmada",    color: "text-blue-400",    bg: "bg-blue-500/10" },
  RECEIVED:   { label: "Recebida",      color: "text-green-400",   bg: "bg-green-500/10" },
  OVERDUE:    { label: "Vencida",       color: "text-orange-400",  bg: "bg-orange-500/10" },
  REFUNDED:   { label: "Reembolsada",   color: "text-purple-400",  bg: "bg-purple-500/10" },
  CHARGEBACK: { label: "Chargeback",    color: "text-red-400",     bg: "bg-red-500/10" },
  CANCELED:   { label: "Cancelada",     color: "text-gray-400",    bg: "bg-gray-500/10" },
};

export const BILLING_TYPE_CONFIG: Record<string, { label: string; icon: string; color: string }> = {
  PIX:         { label: "PIX",             icon: "QrCode",    color: "text-teal-400" },
  BOLETO:      { label: "Boleto",          icon: "FileText",  color: "text-amber-400" },
  CREDIT_CARD: { label: "Cartao Credito",  icon: "CreditCard",color: "text-violet-400" },
  DEBIT_CARD:  { label: "Cartao Debito",   icon: "CreditCard",color: "text-cyan-400" },
  UNDEFINED:   { label: "Indefinido",      icon: "HelpCircle",color: "text-gray-400" },
};

export const SUBSCRIPTION_STATUS_CONFIG: Record<string, { label: string; color: string; bg: string }> = {
  ACTIVE:    { label: "Ativa",      color: "text-green-400",  bg: "bg-green-500/10" },
  PAUSED:    { label: "Pausada",    color: "text-amber-400",  bg: "bg-amber-500/10" },
  SUSPENDED: { label: "Suspensa",   color: "text-orange-400", bg: "bg-orange-500/10" },
  CANCELED:  { label: "Cancelada",  color: "text-red-400",    bg: "bg-red-500/10" },
  EXPIRED:   { label: "Expirada",   color: "text-gray-400",   bg: "bg-gray-500/10" },
};

export const PLAN_CYCLE_LABELS: Record<string, string> = {
  MONTHLY: "Mensal",
  QUARTERLY: "Trimestral",
  SEMIANNUALLY: "Semestral",
  YEARLY: "Anual",
};

export const PLAN_CHANGE_TYPE_CONFIG: Record<string, { label: string; color: string; icon: string }> = {
  UPGRADE:   { label: "Upgrade",   color: "text-green-400",  icon: "ArrowUp" },
  DOWNGRADE: { label: "Downgrade", color: "text-red-400",    icon: "ArrowDown" },
  SIDEGRADE: { label: "Sidegrade", color: "text-blue-400",   icon: "ArrowRight" },
};
```

---

## 8. Responsividade

### Breakpoints de Layout

```
Mobile (< 768px):
  - Sidebar vira drawer (overlay com backdrop)
  - Hamburger menu no header
  - Tabelas viram cards empilhados
  - KPI cards: 2 por linha
  - Formularios: single column
  - Graficos: full width, altura reduzida

Tablet (768px - 1024px):
  - Sidebar colapsada por padrao (72px, so icones)
  - KPI cards: 2 por linha
  - Tabelas com scroll horizontal
  - Formularios: 2 colunas onde possivel

Desktop (> 1024px):
  - Sidebar expandida
  - KPI cards: 4 por linha
  - Tabelas full width
  - Formularios: layout adequado
  - Detalhes em 2 colunas
```

---

## 9. Seguranca Frontend

```
1. JWT armazenado em localStorage (com persist do Zustand)
   - accessToken: curta duracao (24h)
   - refreshToken: longa duracao (7d)

2. Refresh automatico via interceptor (transparente)

3. Protecao de rotas:
   - ProtectedRoute: verifica isAuthenticated
   - RoleGuard: verifica roles para rotas admin
   - Redirect para /login se nao autenticado

4. Sanitizacao de inputs (React ja faz escape por padrao)

5. CSRF: nao necessario (API stateless com JWT)

6. Rate limit visual: mostrar headers X-RateLimit-Remaining
   Warning toast quando < 10 requests restantes

7. Variaveis sensiveis apenas em .env (VITE_API_URL)
```

---

## 10. Configuracao de Ambiente

### `.env`

```bash
VITE_API_URL=http://localhost:8080
```

### `vite.config.ts`

```typescript
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 3000,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
```

### `tailwind.config.ts`

```typescript
import type { Config } from "tailwindcss";

export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        bg: {
          primary: "#0A0A0F",
          secondary: "#12121A",
          tertiary: "#1A1A2E",
          elevated: "#222240",
        },
        surface: {
          1: "#16162A",
          2: "#1E1E38",
          3: "#262650",
        },
        border: {
          subtle: "#2A2A4A",
          DEFAULT: "#3A3A5C",
          focus: "#6366F1",
        },
        text: {
          primary: "#F1F1F6",
          secondary: "#A1A1B5",
          tertiary: "#6B6B80",
        },
      },
      fontFamily: {
        sans: ["Inter", "SF Pro Display", "system-ui", "sans-serif"],
        mono: ["JetBrains Mono", "Fira Code", "monospace"],
      },
      animation: {
        "shimmer": "shimmer 2s linear infinite",
        "count-up": "countUp 0.8s ease-out",
      },
      keyframes: {
        shimmer: {
          "0%": { backgroundPosition: "-200% 0" },
          "100%": { backgroundPosition: "200% 0" },
        },
      },
    },
  },
  plugins: [],
} satisfies Config;
```

---

## 11. Fluxos de Teste Recomendados

Ordem sugerida para testar a API de ponta a ponta com o Asaas Sandbox:

### Fluxo 1 - Setup Inicial
1. Login com admin@holding.dev / admin123
2. Criar empresa com CNPJ valido
3. Configurar API Key do Asaas Sandbox
4. Testar conexao
5. Criar usuario COMPANY_ADMIN para a empresa

### Fluxo 2 - Cadastros Base
1. Criar 3+ clientes com CPF valido
2. Criar 3+ planos (Basico, Pro, Enterprise) com valores e ciclos diferentes
3. Definir tier order para ordenacao de upgrade/downgrade

### Fluxo 3 - Cobrancas Avulsas
1. Criar cobranca PIX -> verificar QR Code
2. Criar cobranca Boleto -> verificar linha digitavel
3. Criar cobranca Cartao (sandbox aceita 5162 3063 4242 4242)
4. Criar cobranca parcelada (cartao e boleto)
5. Testar cancelamento
6. Testar reembolso total e parcial
7. Verificar idempotencia (repetir POST com mesmo Idempotency-Key)

### Fluxo 4 - Assinaturas
1. Criar assinatura com plano Basico (PIX)
2. Criar assinatura com plano Pro (Boleto)
3. Verificar cobrancas geradas automaticamente
4. Pausar assinatura -> verificar status
5. Retomar assinatura -> verificar status
6. Cancelar assinatura

### Fluxo 5 - Mudanca de Plano
1. Selecionar assinatura ativa
2. Preview: Basico -> Pro (upgrade)
3. Executar mudanca com IMMEDIATE_PRORATA
4. Verificar credito/debito no ledger do cliente
5. Preview: Pro -> Basico (downgrade)
6. Verificar comportamento conforme politica da empresa

### Fluxo 6 - Webhooks
1. Monitorar dashboard de webhooks
2. Verificar eventos recebidos do Asaas (pagamento confirmado, etc)
3. Testar replay de evento
4. Verificar DLQ

### Fluxo 7 - Reconciliacao
1. Executar reconciliacao de cobrancas
2. Executar reconciliacao de assinaturas
3. Verificar divergencias
4. Replay DLQ se necessario

### Fluxo 8 - Relatorios
1. Verificar receita por periodo e agrupamento
2. Verificar MRR e ARR
3. Verificar taxa de churn
4. Verificar inadimplencia
5. Exportar CSV de cada relatorio

---

## 12. Comandos de Setup

```bash
# Criar projeto
npm create vite@latest payment-web -- --template react-ts
cd payment-web

# Dependencias principais
npm install react-router-dom @tanstack/react-query @tanstack/react-table
npm install react-hook-form @hookform/resolvers zod
npm install zustand axios recharts
npm install framer-motion sonner lucide-react
npm install @radix-ui/react-dialog @radix-ui/react-dropdown-menu
npm install @radix-ui/react-tabs @radix-ui/react-tooltip
npm install @radix-ui/react-select @radix-ui/react-checkbox
npm install date-fns clsx tailwind-merge

# Tailwind CSS (Vite)
npm install -D tailwindcss @tailwindcss/vite
npm install -D @tailwindcss/forms @tailwindcss/typography

# Dev tools
npm install -D @tanstack/react-query-devtools
npm install -D @types/node
```
