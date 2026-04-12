 Análise Completa: O que o sistema já tem vs. O que falta

  O que JÁ existe (10 fases concluídas)

  ┌────────────────────────────────────────────────┬──────────┐
  │                     Módulo                     │  Status  │
  ├────────────────────────────────────────────────┼──────────┤
  │ Multi-tenancy com RLS no PostgreSQL            │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ JWT Auth + Roles (4 níveis)                    │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ CRUD de Empresas (tenants)                     │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ CRUD de Clientes + sync Asaas                  │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ CRUD de Planos (versioning, promo, ciclos)     │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ Cobranças (PIX, Boleto, Cartão, Parcelamento)  │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ Assinaturas (lifecycle, suspensão automática)  │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ Webhooks Asaas (retry, DLQ, backoff)           │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ Mudança de plano com pro-rata                  │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ Cupons de desconto                             │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ Outbox pattern (eventos de domínio)            │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ Idempotência (Redis + Postgres)                │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ Auditoria (@Auditable)                         │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ Reconciliação automática                       │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ Relatórios (MRR, ARR, Churn, Receita, Overdue) │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ Resiliência (retry, circuit breaker, timeout)  │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ Rate limiting                                  │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ API Keys para integração M2M                   │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ Política de acesso / bloqueio financeiro       │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ Importação de dados do Asaas                   │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ Dashboard consolidado                          │ Completo │
  ├────────────────────────────────────────────────┼──────────┤
  │ Export CSV (receita, mrr, churn, overdue)      │ Completo │
  └────────────────────────────────────────────────┴──────────┘

  ---
  O que FALTA — funcionalidades esperadas para uma API de pagamento
   de holding

  1. Notificações (módulo existe mas está vazio)

  O diretório notification/ existe mas não tem nenhum arquivo. Para
   uma holding, é essencial:

  - Notificação por email — Cobrança criada, cobrança vencida,
  cobrança paga, assinatura suspensa, cliente bloqueado
  - Templates configuráveis por empresa — Cada empresa da holding
  tem seu branding
  - Notificação por webhook para sistemas integrados — Hoje o
  outbox publica eventos, mas não há um registro de "webhook
  endpoints" que cada empresa configura para receber esses eventos
  - Notificação interna (in-app) — Para o painel administrativo
  mostrar alertas

  2. Gestão de Usuários mais robusta

  Hoje o sistema tem apenas criar e listar. Falta:

  - Editar usuário — Alterar nome, email, roles, status
  (ativar/desativar)
  - Resetar senha — Admin reseta, ou flow de "esqueci minha senha"
  - Desativar/reativar usuário
  - Log de login — Histórico de acessos por usuário
  - Convite por email — Admin convida usuário que recebe link para
  definir senha

  3. Notas Fiscais (NF-e / NFS-e)

  Integração com emissão de notas fiscais. Muitas holdings
  precisam:

  - Emissão automática ao receber pagamento
  - Integração com serviço de NF (Enotas, Focus NFe, ou o próprio
  Asaas que tem emissão de NF)
  - Consulta e download de NFs emitidas
  - Configuração de dados fiscais por empresa (CNPJ, inscrição
  municipal, regime tributário)

  4. Régua de Cobrança configurável

  Hoje o sistema suspende após X cobranças vencidas, mas falta uma
  régua completa:

  - Ações automáticas por dias de atraso — Ex: D+3 envia email, D+7
   envia SMS, D+15 bloqueia, D+30 suspende, D+60 cancela
  - Configuração por empresa — Cada empresa define sua própria
  régua
  - Integração com o módulo de notificação — Para disparar as ações

  5. Transferências e Split de Pagamento

  Para holdings com múltiplas empresas:

  - Split de pagamento — Uma cobrança é dividida entre a holding e
  a empresa (o Asaas suporta split)
  - Transferências entre contas — A holding transfere para
  subcontas
  - Taxa da holding — Percentual ou valor fixo que a holding retém
  de cada transação
  - Relatório de repasses — Quanto foi repassado para cada empresa

  6. Painel da Holding (visão consolidada cross-tenant)

  O dashboard atual é por empresa. Falta uma visão da holding:

  - Dashboard cross-tenant — MRR total de todas as empresas,
  comparativo entre empresas
  - Ranking de empresas — Por receita, inadimplência, churn
  - Alertas cross-tenant — Empresa X tem churn alto, Empresa Y tem
  muitas cobranças vencidas
  - Relatório consolidado — Exportar dados de todas as empresas

  7. Extrato Financeiro / Movimentação

  - Extrato por cliente — Todas as movimentações (cobranças pagas,
  créditos, estornos)
  - Extrato por empresa — Entradas, saídas, saldo
  - Demonstrativo mensal — Receita bruta, descontos, estornos,
  receita líquida, taxas Asaas

  8. Retry / Retentativa de Cobrança

  - Retry automático de cobrança falha — Cartão recusado → tentar
  novamente em X dias
  - Dunning management — Estratégia de retentativa configurável (o
  Asaas tem parte disso, mas a orquestração local falta)
  - Fallback de método — Cartão falhou → gerar boleto
  automaticamente

  9. Cancelamento e Downgrade Self-Service

  - Portal do cliente — Endpoint público (via API Key) para o
  cliente solicitar cancelamento
  - Pesquisa de cancelamento — Coletar motivo antes de cancelar
  - Oferta de retenção — Antes de cancelar, oferecer desconto ou
  downgrade
  - Período de carência no cancelamento — Cancelamento agendado
  para fim do ciclo

  10. Multi-moeda e Internacionalização

  Se a holding opera em outros países:

  - Suporte a múltiplas moedas — Hoje tudo é BRL
  - Formatação de valores por locale
  - Integração com outros gateways além do Asaas (Stripe,
  MercadoPago)

  11. Limites e Quotas de Uso

  - Controle de consumo por plano — O campo limits (JSONB) existe
  no Plan mas não há enforcement
  - Endpoint de verificação de quota — Sistema externo consulta se
  o cliente ainda tem cota
  - Overage billing — Cobrar excedente quando ultrapassa o limite
  do plano

  12. Logs e Auditoria expandida

  - Audit log consultável via API — Hoje o audit grava mas não há
  endpoint para consultar
  - Filtros de auditoria — Por entidade, ação, período, usuário
  - Export de audit log

  13. Métricas pendentes (listadas no PROGRESS.md)

  Métricas que foram planejadas mas não implementadas:

  - charges_created_total{method, company, origin}
  - charges_paid_total
  - plan_changes_total
  - plan_change_proration_amount
  - customer_credit_balance_total

  14. Testes de integração pendentes

  Listados no PROGRESS.md como pendentes:

  - Webhook SUBSCRIPTION_DELETED cancela assinatura local
  - Optimistic lock (@Version) impede atualização concorrente

  ---
  Priorização sugerida

  ┌────────────┬───────────────────────┬───────────────────────┐
  │ Prioridade │    Funcionalidade     │     Justificativa     │
  ├────────────┼───────────────────────┼───────────────────────┤
  │            │ Notificações (email + │ Sem isso o sistema    │
  │    Alta    │  webhook              │ não comunica eventos  │
  │            │ configurável)         │ aos stakeholders      │
  ├────────────┼───────────────────────┼───────────────────────┤
  │            │ Régua de cobrança     │ Core de uma API de    │
  │    Alta    │ configurável          │ pagamento — automação │
  │            │                       │  de inadimplência     │
  ├────────────┼───────────────────────┼───────────────────────┤
  │            │ Gestão de usuários    │ Básico de qualquer    │
  │    Alta    │ (editar, desativar,   │ sistema multi-usuário │
  │            │ reset senha)          │                       │
  ├────────────┼───────────────────────┼───────────────────────┤
  │            │ Endpoint de consulta  │ Os dados são gravados │
  │    Alta    │ de audit log          │  mas ninguém consegue │
  │            │                       │  consultar            │
  ├────────────┼───────────────────────┼───────────────────────┤
  │            │ Split de pagamento /  │ Diferencial           │
  │   Média    │ taxa da holding       │ competitivo para      │
  │            │                       │ holding               │
  ├────────────┼───────────────────────┼───────────────────────┤
  │            │ Dashboard             │ A holding precisa ver │
  │   Média    │ cross-tenant (visão   │  o todo               │
  │            │ holding)              │                       │
  ├────────────┼───────────────────────┼───────────────────────┤
  │   Média    │ Extrato financeiro    │ Transparência         │
  │            │ por cliente/empresa   │ financeira            │
  ├────────────┼───────────────────────┼───────────────────────┤
  │   Média    │ Retry de cobrança /   │ Recuperação de        │
  │            │ dunning               │ receita               │
  ├────────────┼───────────────────────┼───────────────────────┤
  │            │ Limites e quotas de   │ O campo existe mas    │
  │   Média    │ uso (enforcement do   │ não faz nada          │
  │            │ limits JSONB)         │                       │
  ├────────────┼───────────────────────┼───────────────────────┤
  │   Baixa    │ Notas fiscais         │ Depende de integração │
  │            │                       │  externa              │
  ├────────────┼───────────────────────┼───────────────────────┤
  │   Baixa    │ Portal self-service   │ Nice-to-have          │
  │            │ (cancelamento)        │                       │
  ├────────────┼───────────────────────┼───────────────────────┤
  │   Baixa    │ Multi-moeda           │ Só se operar fora do  │
  │            │                       │ Brasil                │
  ├────────────┼───────────────────────┼───────────────────────┤
  │   Baixa    │ Métricas pendentes do │ Observabilidade       │
  │            │  PROGRESS.md          │ refinada              │
  └────────────┴───────────────────────┴───────────────────────┘

  ---
  Quer que eu implemente alguma dessas funcionalidades? Posso
  começar pelas de alta prioridade.