# Payment API - Controle de Progresso

Documento de acompanhamento do desenvolvimento da API de Pagamentos Multi-Tenant com integracao Asaas.
Referencia completa: `API Pagamento Assas.md`

---

## Resumo Geral

| Fase | Nome | Duracao Estimada | Status |
|------|------|-----------------|--------|
| 0 | Setup e fundacoes de infraestrutura | 1 semana | CONCLUIDA |
| 1 | Multi-tenancy, seguranca e RLS | 2 semanas | CONCLUIDA |
| 2 | Cliente Asaas e fundacoes de resiliencia | 1 semana | CONCLUIDA |
| 3 | Idempotencia e Outbox (fundacoes transversais) | 1 semana | CONCLUIDA |
| 4 | Customers e Plans | 1-2 semanas | CONCLUIDA |
| 5 | Charges e validacao de transicao de estado | 2 semanas | CONCLUIDA |
| 6 | Webhook ingress + processamento tolerante a desordem | 2 semanas | NAO INICIADA |
| 7 | Subscriptions e parcelamentos | 2 semanas | NAO INICIADA |
| 8 | Mudanca de plano com pro-rata | 3 semanas | NAO INICIADA |
| 9 | Reconciliacao, relatorios e dashboards | 1-2 semanas | NAO INICIADA |
| 10 | Hardening, carga e producao | 2 semanas | NAO INICIADA |

---

## Fase 0 - Setup e Fundacoes (CONCLUIDA)

### Entregaveis
- [x] Repositorio Git + estrutura de pacotes
- [x] pom.xml com dependencias (Spring Boot 3.4.4, JPA, Flyway, Resilience4j, Springdoc, Testcontainers, jqwik, Lombok, MapStruct)
- [x] Dockerfile multi-stage (build + runtime, Alpine, Java 21, usuario non-root)
- [x] docker-compose.yml (app + Postgres 16 + Redis 7)
- [x] application.yml por profile (dev, staging, prod, test)
- [x] Flyway configurado com V1__init_schema.sql (13 tabelas) e V2__enable_rls.sql
- [x] springdoc-openapi em /swagger-ui.html
- [x] Logback com logstash-encoder (JSON estruturado)
- [x] @EnableScheduling habilitado

### Arquivos Principais
- `Dockerfile` - Build multi-stage com Eclipse Temurin 21
- `docker-compose.yml` - 3 servicos (app, postgres, redis) com healthchecks
- `docker/init-db.sql` - Role Flyway com BYPASSRLS
- `pom.xml` - Todas as dependencias do projeto
- `src/main/resources/application*.yml` - Configuracoes por ambiente

---

## Fase 1 - Multi-tenancy, Seguranca e RLS (CONCLUIDA)

### Entregaveis
- [x] Migration V1 com tabelas companies, users, audit_log (+ todas as demais)
- [x] Migration V2 habilitando RLS em 11 tabelas transacionais
- [x] Policies de RLS por tabela (tenant_isolation usando app.current_company_id)
- [x] TenantContext (ThreadLocal) + TenantContextInterceptor (HTTP header + JWT fallback)
- [x] TenantRlsAspect - SET LOCAL app.current_company_id em toda transacao
- [x] @CrossTenant annotation para bypass de RLS (jobs, admin)
- [x] Spring Security 6 + JWT (login, refresh)
- [x] Roles: HOLDING_ADMIN, COMPANY_ADMIN, COMPANY_OPERATOR, SYSTEM
- [x] Modulo company: CRUD completo + criptografia da asaas_api_key (Jasypt)
- [x] Endpoint POST /companies/{id}/test-connection (GET /myAccount no Asaas)
- [x] Modulo audit: AuditLog entity + AuditService + @Auditable aspect
- [x] Role de banco separada para Flyway (BYPASSRLS) e role da aplicacao (sem)
- [x] DevDataSeeder (perfil dev) - cria admin@holding.dev / admin123

### Pacotes Implementados
- `auth/` - AuthController, AuthService, JwtService, JwtAuthenticationFilter, UserEntity, Role enum
- `company/` - CompanyController, CompanyService, Company entity, EncryptionService, enums (AsaasEnvironment, CompanyStatus, PlanChangePolicy, DowngradeValidationStrategy)
- `tenant/` - TenantContext, TenantContextInterceptor, TenantRlsAspect, @CrossTenant
- `audit/` - AuditLog, AuditService, AuditAspect, @Auditable
- `common/` - BaseEntity, GlobalExceptionHandler, BusinessException, ResourceNotFoundException, IllegalStateTransitionException
- `config/` - SecurityConfig, WebMvcConfig, JacksonConfig, OpenApiConfig, RedisConfig, DevDataSeeder

### Testes (Fase 1) - 9 testes, todos passando
- [x] RlsIsolationTest: empresa A nao enxerga dados da empresa B com RLS ativo
- [x] RlsIsolationTest: query sem company_id setado retorna vazio (RLS filtra tudo)
- [x] EncryptionTest: asaas_api_key cifrada no banco e clara em memoria
- [x] EncryptionTest: encrypt/decrypt de valor nulo retorna nulo
- [x] AuthSecurityTest: endpoint protegido sem token retorna 403
- [x] AuthSecurityTest: endpoint publico /actuator/health retorna 200
- [x] AuthSecurityTest: login com credenciais invalidas retorna 422
- [x] AuthSecurityTest: login com credenciais validas retorna tokens JWT
- [x] AuthSecurityTest: endpoint protegido com token valido retorna 200

---

## Fase 2 - Cliente Asaas e Fundacoes de Resiliencia (CONCLUIDA)

### Entregaveis
- [x] Estrutura integration/asaas/{client, dto, mapper, gateway}
- [x] AsaasClientFactory com interceptor que injeta access_token baseado no CompanyContext
- [x] AsaasCustomerClient (create, getById, update, list)
- [x] AsaasPaymentClient (create, getById, cancel, refund, getPixQrCode, getBoletoIdentificationField, list, listBySubscription)
- [x] AsaasSubscriptionClient (create, getById, cancel, list)
- [x] AsaasInstallmentClient (getById, listPayments, cancel)
- [x] AsaasGatewayService (fachada de dominio que nao vaza DTOs do Asaas)
- [x] AsaasMapper (converte entre DTOs Asaas e objetos de dominio)
- [x] Resilience4j: retry exponencial (3 tentativas) + circuit breaker (50% threshold) + timeouts (connect 3s, read 10s)
- [x] AsaasErrorHandler (tratamento padronizado de erros 4xx/5xx do Asaas)
- [x] AsaasApiException com ErrorDetail (code + description)
- [x] GlobalExceptionHandler atualizado para AsaasApiException (4xx -> 422, 5xx -> 502)
- [x] Metrica asaas_api_errors_total{resource=customer|payment|subscription|installment}

### Pacotes Implementados
- `integration/asaas/client/` - AsaasClientFactory, AsaasCustomerClient, AsaasPaymentClient, AsaasSubscriptionClient, AsaasInstallmentClient, AsaasApiException, AsaasErrorHandler
- `integration/asaas/dto/` - AsaasCustomerRequest/Response, AsaasPaymentRequest/Response, AsaasSubscriptionRequest/Response, AsaasInstallmentResponse, AsaasPixQrCodeResponse, AsaasBoletoResponse, AsaasAccountResponse, AsaasRefundRequest, AsaasErrorResponse, AsaasPageResponse
- `integration/asaas/mapper/` - AsaasMapper
- `integration/asaas/gateway/` - AsaasGatewayService, AsaasCustomerData/Result, AsaasPaymentData/Result, AsaasSubscriptionData/Result

### Configuracao Resilience4j (application.yml)
- Retry: 3 tentativas, backoff exponencial (multiplicador 2), apenas para erros de rede (ResourceAccessException, SocketTimeoutException, ConnectException). Erros 4xx do Asaas NAO sao retentados.
- Circuit Breaker: janela de 10 chamadas, threshold 50%, 30s em estado aberto, 3 chamadas em half-open
- Time Limiter: timeout de 15s

### Testes (Fase 2) - 8 testes, todos passando
- [x] AsaasErrorHandlerTest: resposta 200 nao dispara erro
- [x] AsaasErrorHandlerTest: erro 400 do Asaas eh parseado com detalhes
- [x] AsaasErrorHandlerTest: erro 404 tratado como client error
- [x] AsaasErrorHandlerTest: erro 500 tratado como server error
- [x] AsaasMapperTest: toCustomerResult retorna objeto de dominio, nao DTO do Asaas
- [x] AsaasMapperTest: toPaymentResult retorna objeto de dominio
- [x] AsaasMapperTest: toSubscriptionResult retorna objeto de dominio
- [x] AsaasMapperTest: toAsaasCustomerRequest converte dados de dominio para DTO Asaas

---

## Fase 3 - Idempotencia e Outbox (CONCLUIDA)

### Entregaveis
- [x] Tabela idempotency_keys (ja existia na V1)
- [x] Filtro Spring (IdempotencyFilter) intercepta POST com Idempotency-Key header
- [x] Redis fast-path -> Postgres source of truth -> executa handler -> salva resposta
- [x] Validacao de hash de body (mesma chave + body diferente -> 422)
- [x] Tabela outbox (ja existia na V1)
- [x] OutboxPublisher (insere na mesma transacao JPA)
- [x] OutboxRelay (worker @Scheduled que publica eventos via webhook HTTP)
- [x] Metricas: idempotency_hit_total{layer=redis|postgres}, outbox_lag_seconds, outbox_failed_total
- [x] Endpoint admin GET /api/v1/admin/outbox?status=... (listagem paginada)
- [x] Endpoint admin GET /api/v1/admin/outbox/summary (resumo com contagens)
- [x] Endpoint admin POST /api/v1/admin/outbox/{id}/retry (reprocessar evento)
- [x] IdempotencyCleanupJob - limpeza de chaves expiradas (a cada 1 hora)
- [x] OutboxMetrics - gauges para outbox_lag_seconds, outbox_pending_count, outbox_dlq_count

### Pacotes Implementados
- `idempotency/` - IdempotencyKey, IdempotencyKeyRepository, IdempotencyService, IdempotencyFilter, IdempotencyCleanupJob, IdempotencyConflictException, CachedResponse
- `outbox/` - OutboxEvent, OutboxEventRepository, OutboxPublisher, OutboxRelay, OutboxAdminController, OutboxAdminService, OutboxStatus, OutboxMetrics
- `outbox/dto/` - OutboxEventResponse, OutboxSummaryResponse

### Testes (Fase 3) - 10 testes, todos passando
- [x] IdempotencyServiceTest: primeiro request nao encontra resposta cacheada
- [x] IdempotencyServiceTest: segundo request identico retorna mesma resposta sem dupla escrita
- [x] IdempotencyServiceTest: mesma chave + body diferente -> 422 (IdempotencyConflictException)
- [x] IdempotencyServiceTest: chaves de empresas diferentes sao independentes
- [x] IdempotencyServiceTest: hash de body nulo nao causa erro
- [x] OutboxTest: evento publicado na mesma transacao persiste com status PENDING
- [x] OutboxTest: relay processa eventos pendentes e marca como PUBLISHED
- [x] OutboxTest: contagem de eventos por status funciona corretamente
- [x] OutboxTest: evento com falha apos N tentativas vai para DLQ

---

## Fase 4 - Customers e Plans (CONCLUIDA)

### Entregaveis
- [x] Entity Customer com soft delete (@SQLRestriction) + indice unico parcial (uq_customer_doc_active)
- [x] CustomerRepository com search, findByDocument, findByAsaasId, findByIdIncludingDeleted
- [x] CustomerService: create, update, softDelete, restore, findOrCreate, syncFromAsaas
- [x] Sincronizacao com /customers do Asaas (create e update via AsaasGatewayService)
- [x] CustomerController com endpoints REST completos (7 endpoints)
- [x] CustomerMapper para conversao entity -> DTO
- [x] Evento outbox: CustomerCreatedEvent publicado na criacao
- [x] Entity Plan com limits/features JSONB (@JdbcTypeCode), tier_order, soft delete
- [x] PlanRepository com findMaxVersionByCompanyAndName, findByIdIncludingDeleted
- [x] PlanService: create, update, activate, deactivate, softDelete, cloneForNewVersion
- [x] PlanController com endpoints REST completos (8 endpoints, incluindo new-version)
- [x] PlanMapper para conversao entity -> DTO
- [x] PlanCycle enum (MONTHLY, QUARTERLY, SEMIANNUALLY, YEARLY)

### Pacotes Implementados
- `customer/` - Customer, CustomerRepository, CustomerService, CustomerController, CustomerMapper
- `customer/dto/` - CreateCustomerRequest, UpdateCustomerRequest, CustomerResponse
- `plan/` - Plan, PlanRepository, PlanService, PlanController, PlanMapper, PlanCycle
- `plan/dto/` - CreatePlanRequest, UpdatePlanRequest, PlanResponse

### Endpoints Implementados

#### Clientes (/api/v1/customers)
| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| POST | /customers | Cadastrar novo cliente (sync Asaas) |
| GET | /customers | Listar clientes (com busca por nome/doc/email) |
| GET | /customers/{id} | Buscar cliente por ID |
| PUT | /customers/{id} | Atualizar dados do cliente (sync Asaas) |
| DELETE | /customers/{id} | Soft delete |
| POST | /customers/{id}/restore | Restaurar cliente (admin) |
| POST | /customers/{id}/sync | Resync com Asaas |

#### Planos (/api/v1/plans)
| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| POST | /plans | Criar novo plano |
| GET | /plans | Listar planos |
| GET | /plans/{id} | Buscar plano por ID |
| PUT | /plans/{id} | Atualizar plano |
| PATCH | /plans/{id}/activate | Ativar plano |
| PATCH | /plans/{id}/deactivate | Desativar plano |
| DELETE | /plans/{id} | Soft delete |
| POST | /plans/{id}/new-version | Criar nova versao com preco alterado |

### Pendente para fases futuras
- [ ] GET /customers/{id}/credit-balance (Fase 8 - CustomerCreditLedger)
- [ ] Bloqueio de soft delete de plano com assinaturas ativas (Fase 7)

---

## Fase 5 - Charges e Validacao de Transicao de Estado (CONCLUIDA)

### Entregaveis
- [x] ChargeStatus enum com mapa de transicoes validas e canTransitionTo()
- [x] ChargeOrigin enum (WEB, PDV, RECURRING, API, BACKOFFICE, PLAN_CHANGE)
- [x] BillingType enum (PIX, CREDIT_CARD, DEBIT_CARD, BOLETO, UNDEFINED)
- [x] Entity Charge com transitionTo() validado, origin, external_reference
- [x] Entity Installment + InstallmentRepository
- [x] ChargeRepository com findWithFilters (status, origin, dueDate range, customerId)
- [x] ChargeService: createPixCharge, createBoletoCharge, createCreditCardCharge, createUndefinedCharge, createCreditCardInstallments, createBoletoInstallments, cancel, refund, getPixQrCode, getBoletoLine
- [x] ChargeController com 12 endpoints REST (descricoes em portugues)
- [x] ChargeMapper para conversao entity -> DTO
- [x] Eventos outbox: ChargeCreatedEvent, ChargeRefundedEvent, ChargeCanceledEvent
- [x] Integracao Asaas via AsaasGatewayService (create, cancel, refund, getPixQrCode, getBoletoIdentificationField)
- [x] Tokenizacao de cartao via Asaas (PAN nunca persistido localmente, apenas creditCardToken)
- [x] Suporte a parcelamento (Installment entity vinculada a charges)

### Pacotes Implementados
- `charge/` - Charge, ChargeRepository, ChargeService, ChargeController, ChargeMapper, ChargeStatus, ChargeOrigin, BillingType
- `charge/dto/` - CreateChargeRequest, RefundRequest, ChargeResponse, PixQrCodeResponse, BoletoLineResponse
- `installment/` - Installment, InstallmentRepository

### Endpoints Implementados (Fase 5)

| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| POST | /charges/pix | Criar cobranca PIX |
| POST | /charges/boleto | Criar cobranca por boleto |
| POST | /charges/credit-card | Criar cobranca por cartao |
| POST | /charges/credit-card/installments | Parcelamento no cartao |
| POST | /charges/boleto/installments | Parcelamento por boleto |
| POST | /charges/undefined | Cobranca com metodo indefinido |
| GET | /charges | Listar com filtros (status, origin, dueDate, customer) |
| GET | /charges/{id} | Buscar cobranca por ID |
| GET | /charges/{id}/pix-qrcode | Obter QR Code PIX |
| GET | /charges/{id}/boleto-line | Obter linha digitavel do boleto |
| POST | /charges/{id}/refund | Estornar cobranca (total ou parcial) |
| DELETE | /charges/{id} | Cancelar cobranca |

### Maquina de Estados (ChargeStatus)

| De | Para (transicoes validas) |
|----|--------------------------|
| PENDING | CONFIRMED, RECEIVED, OVERDUE, CANCELED |
| CONFIRMED | RECEIVED, REFUNDED, CHARGEBACK, CANCELED |
| RECEIVED | REFUNDED, CHARGEBACK |
| OVERDUE | RECEIVED, CONFIRMED, CANCELED |
| REFUNDED | (nenhuma) |
| CHARGEBACK | REFUNDED |
| CANCELED | (nenhuma) |

### Testes Pendentes (Fase 5)
- [ ] Tabela exaustiva de transicoes validas/invalidas para ChargeStatus
- [ ] Transicao invalida -> IllegalStateTransitionException + nada gravado
- [ ] Todos os fluxos de criacao com WireMock simulando Asaas
- [ ] Idempotencia ponta a ponta nos endpoints POST /charges/*

---

## Fase 6 - Webhook Ingress + Processamento (NAO INICIADA)

### Entregaveis Pendentes
- [ ] Entity WebhookEvent
- [ ] Endpoint POST /webhooks/asaas (validacao token, persist raw, 200 imediato)
- [ ] Worker WebhookProcessor com SELECT ... FOR UPDATE SKIP LOCKED
- [ ] Logica DEFERRED + backoff exponencial (5s, 30s, 2min, 10min, 1h)
- [ ] DLQ apos N tentativas
- [ ] Endpoint admin POST /webhooks/asaas/replay/{eventId}
- [ ] Reconciliacao cruzada (DEFERRED -> pronto quando recurso criado)
- [ ] Metricas: webhook_processing_duration_seconds, webhook_deferred_total, webhook_dlq_total

---

## Fase 7 - Subscriptions e Parcelamentos (NAO INICIADA)

### Entregaveis Pendentes
- [ ] Entity Subscription com @Version (optimistic lock)
- [ ] SubscriptionService completo
- [ ] Validacao de transicao de estado
- [ ] Job handleOverdueSubscription (suspender apos N falhas)
- [ ] Webhooks SUBSCRIPTION_*
- [ ] Eventos outbox: SubscriptionCreatedEvent, SubscriptionCanceledEvent, SubscriptionSuspendedEvent
- [ ] Parcelamentos cartao + boleto ponta a ponta

---

## Fase 8 - Mudanca de Plano com Pro-Rata (NAO INICIADA)

### Entregaveis Pendentes
- [ ] Entity SubscriptionPlanChange
- [ ] Entity CustomerCreditLedger (append-only)
- [ ] ProrationCalculator (puro, sem dependencias)
- [ ] PlanLimitsValidator com SPI
- [ ] CustomerCreditLedgerService com lock pessimista
- [ ] PlanChangeService (previewChange, requestChange, confirmAfterPayment, cancelChange, processScheduledChanges)
- [ ] Endpoints (preview-change, change-plan, plan-changes, cancel)
- [ ] Job diario processScheduledChanges
- [ ] Integracao webhook (pagamento Delta recebido -> confirmar)
- [ ] Eventos outbox: PlanChangedEvent, PlanChangePendingPaymentEvent

---

## Fase 9 - Reconciliacao, Relatorios e Dashboards (NAO INICIADA)

### Entregaveis Pendentes
- [ ] Job de reconciliacao (charges, subscriptions)
- [ ] Replay DLQ
- [ ] Relatorios: revenue, MRR, churn, overdue (com filtro por origin)
- [ ] Export CSV/Excel
- [ ] Dashboards Grafana
- [ ] Alertas Prometheus

---

## Fase 10 - Hardening, Carga e Producao (NAO INICIADA)

### Entregaveis Pendentes
- [ ] Testes de carga (k6/Gatling)
- [ ] Pen test
- [ ] Revisao de logs para vazamento de PII
- [ ] Documentacao OpenAPI completa
- [ ] Runbooks operacionais
- [ ] Plano de disaster recovery
- [ ] Politica LGPD
- [ ] Treinamento de equipes

---

## Correcoes Aplicadas Durante o Desenvolvimento

| Data | Problema | Correcao |
|------|----------|---------|
| 2026-04-08 | mvnw com CRLF nao executava no container Alpine | Adicionado `sed -i 's/\r$//' mvnw` no Dockerfile |
| 2026-04-08 | App no Docker nao conectava ao Postgres (localhost) | Adicionado DB_HOST/REDIS_HOST variaveis de ambiente |
| 2026-04-08 | CORS nao configurado - Swagger nao funcionava | Adicionado CorsConfigurationSource no SecurityConfig |
| 2026-04-08 | DevDataSeeder criava empresa mas nao o usuario (SET LOCAL desnecessario) | Removido SET LOCAL, check por existsByEmail em vez de count |
| 2026-04-08 | TenantRlsAspect rodava antes da transacao (@Order errado) | Ajustado para @Order(1) + @EnableTransactionManagement(order=0) |
| 2026-04-08 | SET LOCAL com bind parameter ($1) nao suportado pelo PostgreSQL | Trocado para concatenacao direta (seguro pois companyId e Long) |
| 2026-04-08 | TenantRlsAspect nao respeitava @CrossTenant | Adicionado check isCrossTenant() no aspect |
| 2026-04-08 | TenantContextInterceptor com TODO pendente | Ajustado para respeitar JWT ja setado pelo JwtAuthenticationFilter |
| 2026-04-08 | CORS com allowedOrigins=* fixo | Externalizado para app.cors.allowed-origins configuravel por ambiente |
| 2026-04-08 | OutboxRelay sem aviso quando webhook-url nao configurado | Adicionado @PostConstruct com log.warn na startup |
| 2026-04-08 | payment_app era superuser com BYPASSRLS (RLS nao funcionava) | Separado postgres (superuser) de payment_app (NOSUPERUSER NOBYPASSRLS) no docker-compose e init-db.sql |
| 2026-04-08 | Default app.current_company_id = '' causava erro bigint cast | Alterado para '0' que converte para bigint mas nao corresponde a nenhuma empresa |
| 2026-04-08 | Colunas JSONB (response_body, payload) recebiam varchar | Adicionado @JdbcTypeCode(SqlTypes.JSON) nas entities IdempotencyKey e OutboxEvent |
| 2026-04-08 | OutboxPublisher serializava String payload com aspas extras | Adicionado check `payload instanceof String` para evitar dupla serializacao |

---

## Endpoints REST Implementados

### Autenticacao (/api/v1/auth)
| Metodo | Endpoint | Descricao | Permissao |
|--------|----------|-----------|-----------|
| POST | /auth/login | Realizar login | Publico |
| POST | /auth/refresh | Renovar access token | Publico |
| POST | /auth/users | Criar novo usuario | HOLDING_ADMIN, COMPANY_ADMIN |

### Empresas (/api/v1/companies)
| Metodo | Endpoint | Descricao | Permissao |
|--------|----------|-----------|-----------|
| POST | /companies | Cadastrar nova empresa | HOLDING_ADMIN |
| GET | /companies | Listar todas as empresas | HOLDING_ADMIN |
| GET | /companies/{id} | Buscar empresa por ID | HOLDING_ADMIN |
| PUT | /companies/{id} | Atualizar dados da empresa | HOLDING_ADMIN |
| PUT | /companies/{id}/credentials | Atualizar credenciais Asaas | HOLDING_ADMIN |
| POST | /companies/{id}/test-connection | Testar conexao com API Asaas | HOLDING_ADMIN |

### Admin - Outbox (/api/v1/admin/outbox)
| Metodo | Endpoint | Descricao | Permissao |
|--------|----------|-----------|-----------|
| GET | /admin/outbox | Listar eventos por status | HOLDING_ADMIN |
| GET | /admin/outbox/summary | Resumo do outbox | HOLDING_ADMIN |
| POST | /admin/outbox/{id}/retry | Reprocessar evento com falha | HOLDING_ADMIN |

### Clientes (/api/v1/customers)
| Metodo | Endpoint | Descricao | Permissao |
|--------|----------|-----------|-----------|
| POST | /customers | Cadastrar novo cliente | Autenticado |
| GET | /customers | Listar clientes | Autenticado |
| GET | /customers/{id} | Buscar cliente por ID | Autenticado |
| PUT | /customers/{id} | Atualizar cliente | Autenticado |
| DELETE | /customers/{id} | Soft delete | Autenticado |
| POST | /customers/{id}/restore | Restaurar cliente | HOLDING_ADMIN, COMPANY_ADMIN |
| POST | /customers/{id}/sync | Resync com Asaas | Autenticado |

### Planos (/api/v1/plans)
| Metodo | Endpoint | Descricao | Permissao |
|--------|----------|-----------|-----------|
| POST | /plans | Criar plano | Autenticado |
| GET | /plans | Listar planos | Autenticado |
| GET | /plans/{id} | Buscar plano | Autenticado |
| PUT | /plans/{id} | Atualizar plano | Autenticado |
| PATCH | /plans/{id}/activate | Ativar plano | Autenticado |
| PATCH | /plans/{id}/deactivate | Desativar plano | Autenticado |
| DELETE | /plans/{id} | Soft delete | Autenticado |
| POST | /plans/{id}/new-version | Nova versao do plano | Autenticado |

### Cobrancas (/api/v1/charges)
| Metodo | Endpoint | Descricao | Permissao |
|--------|----------|-----------|-----------|
| POST | /charges/pix | Criar cobranca PIX | Autenticado |
| POST | /charges/boleto | Criar cobranca boleto | Autenticado |
| POST | /charges/credit-card | Criar cobranca cartao | Autenticado |
| POST | /charges/credit-card/installments | Parcelamento cartao | Autenticado |
| POST | /charges/boleto/installments | Parcelamento boleto | Autenticado |
| POST | /charges/undefined | Metodo indefinido | Autenticado |
| GET | /charges | Listar com filtros | Autenticado |
| GET | /charges/{id} | Buscar por ID | Autenticado |
| GET | /charges/{id}/pix-qrcode | QR Code PIX | Autenticado |
| GET | /charges/{id}/boleto-line | Linha digitavel | Autenticado |
| POST | /charges/{id}/refund | Estornar | Autenticado |
| DELETE | /charges/{id} | Cancelar | Autenticado |

### Endpoints Pendentes (Fases 6-9)
- /api/v1/subscriptions (CRUD + pause/resume + change-plan)
- /api/v1/webhooks/asaas (ingress + replay)
- /api/v1/reconciliation (run + dlq replay)
- /api/v1/reports (revenue, mrr, churn, overdue)

---

## Metricas Implementadas

| Metrica | Tipo | Descricao |
|---------|------|-----------|
| idempotency_hit_total{layer=redis} | Counter | Hits de idempotencia no Redis |
| idempotency_hit_total{layer=postgres} | Counter | Hits de idempotencia no Postgres |
| outbox_failed_total | Counter | Eventos do outbox que falharam |
| outbox_lag_seconds | Gauge | Idade em segundos do evento pendente mais antigo |
| outbox_pending_count | Gauge | Quantidade de eventos pendentes |
| outbox_dlq_count | Gauge | Quantidade de eventos na DLQ |

| asaas_api_errors_total{resource=customer} | Counter | Erros na API Asaas (customer) |
| asaas_api_errors_total{resource=payment} | Counter | Erros na API Asaas (payment) |
| asaas_api_errors_total{resource=subscription} | Counter | Erros na API Asaas (subscription) |
| asaas_api_errors_total{resource=installment} | Counter | Erros na API Asaas (installment) |

### Metricas Pendentes (Fases 5-9)
- charges_created_total{method, company, origin}
- charges_paid_total
- webhook_processing_duration_seconds
- webhook_deferred_total
- webhook_dlq_total
- asaas_api_errors_total
- plan_changes_total
- plan_change_proration_amount
- customer_credit_balance_total
