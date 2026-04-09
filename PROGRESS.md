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
| 6 | Webhook ingress + processamento tolerante a desordem | 2 semanas | CONCLUIDA |
| 7 | Subscriptions e parcelamentos | 2 semanas | CONCLUIDA |
| 8 | Mudanca de plano com pro-rata | 3 semanas | CONCLUIDA |
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
- [x] GET /customers/{id}/credit-balance (implementado na Fase 8 - CustomerCreditLedger)
- [x] Bloqueio de soft delete de plano com assinaturas ativas (implementado na Fase 7)

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

### Testes (Fase 5) - 20+ testes

#### ChargeStatusTransitionTest (unit)
- [x] Tabela exaustiva de TODAS as transicoes validas via @ParameterizedTest
- [x] Tabela exaustiva de TODAS as transicoes invalidas via @ParameterizedTest
- [x] Nenhum status pode transitar para si mesmo
- [x] Transicao valida atualiza o status da charge
- [x] Transicao invalida -> IllegalStateTransitionException sem alterar status
- [x] Fluxo completo PIX: PENDING -> CONFIRMED -> RECEIVED -> REFUNDED
- [x] Fluxo boleto atrasado: PENDING -> OVERDUE -> RECEIVED
- [x] Fluxo chargeback: CONFIRMED -> CHARGEBACK -> REFUNDED
- [x] REFUNDED eh estado terminal
- [x] CANCELED eh estado terminal

#### ChargeServiceTest (unit/mockito)
- [x] Criar cobranca PIX com sucesso (sync Asaas + outbox + reconciliacao cruzada)
- [x] Cliente sem asaas_id lanca BusinessException
- [x] Cliente inexistente lanca ResourceNotFoundException
- [x] Cancelar cobranca PENDING com sucesso (sync Asaas + outbox)
- [x] Cancelar cobranca REFUNDED lanca IllegalStateTransitionException
- [x] Estornar cobranca CONFIRMED com sucesso
- [x] Criar cobranca boleto com sucesso
- [x] Criar cobranca cartao com sucesso
- [x] Parcelamento com menos de 2 parcelas lanca BusinessException

---

## Fase 6 - Webhook Ingress + Processamento (CONCLUIDA)

### Entregaveis
- [x] Entity WebhookEvent com mapeamento da tabela webhook_events (constraint unica asaas_event_id + company_id)
- [x] WebhookEventStatus enum (PENDING, PROCESSING, DEFERRED, PROCESSED, FAILED, DLQ)
- [x] AsaasEventType enum com mapeamento para ChargeStatus (17 tipos de evento suportados)
- [x] Endpoint POST /webhooks/asaas (validacao token via header asaas-access-token, persist raw payload, 200 imediato)
- [x] Idempotencia duravel: duplicatas capturadas pela constraint unica, respondidas com 200 OK
- [x] Worker WebhookProcessor (@Scheduled) com SELECT ... FOR UPDATE SKIP LOCKED (batch de 50)
- [x] Logica DEFERRED + backoff exponencial (5s, 30s, 2min, 10min, 1h)
- [x] DLQ apos 10 tentativas (configuravel via app.webhook.max-attempts)
- [x] WebhookEventHandler: roteamento de PAYMENT_* para transicoes de ChargeStatus + publicacao no outbox
- [x] Eventos outbox mapeados: ChargePaidEvent, ChargeConfirmedEvent, ChargeOverdueEvent, ChargeCanceledEvent, ChargeRefundedEvent, ChargeChargebackEvent
- [x] Eventos SUBSCRIPTION_* recebidos e persistidos (processamento completo na Fase 7)
- [x] Endpoint admin GET /api/v1/admin/webhooks?status=... (listagem paginada)
- [x] Endpoint admin GET /api/v1/admin/webhooks/summary (resumo com contagens por status)
- [x] Endpoint admin POST /api/v1/admin/webhooks/{eventId}/replay (reprocessar eventos FAILED/DLQ)
- [x] Reconciliacao cruzada: ao criar charge, eventos DEFERRED para o mesmo asaas_id sao acelerados (next_attempt_at = now)
- [x] Metricas: webhook_processing_duration_seconds (Timer), webhook_deferred_total (Counter), webhook_dlq_total (Counter)
- [x] Metricas Gauge: webhook_pending_count, webhook_deferred_count, webhook_dlq_count, webhook_lag_seconds

### Pacotes Implementados
- `webhook/` - WebhookEvent, WebhookEventRepository, WebhookEventStatus, AsaasEventType, WebhookService, WebhookEventHandler, WebhookProcessor, WebhookController, WebhookAdminController, WebhookMetrics
- `webhook/dto/` - AsaasWebhookPayload, WebhookEventResponse, WebhookSummaryResponse

### Endpoints Implementados (Fase 6)

#### Webhook Publico (/api/v1/webhooks/asaas)
| Metodo | Endpoint | Descricao | Permissao |
|--------|----------|-----------|-----------|
| POST | /webhooks/asaas?companyId={id} | Receber webhook do Asaas | Publico (token validado) |

#### Admin - Webhooks (/api/v1/admin/webhooks)
| Metodo | Endpoint | Descricao | Permissao |
|--------|----------|-----------|-----------|
| GET | /admin/webhooks?status=... | Listar eventos por status | HOLDING_ADMIN, SYSTEM |
| GET | /admin/webhooks/summary | Resumo dos eventos webhook | HOLDING_ADMIN, SYSTEM |
| POST | /admin/webhooks/{eventId}/replay | Reprocessar evento FAILED/DLQ | HOLDING_ADMIN, SYSTEM |

### Fluxo de Processamento

1. **Recepcao (sincrona)**: Asaas envia POST -> valida token -> persiste em webhook_events (PENDING) -> retorna 200 OK
2. **Processamento (assincrono)**: Worker pega eventos PENDING/DEFERRED com FOR UPDATE SKIP LOCKED -> marca PROCESSING
3. **Lookup**: Busca charge pelo asaas_id do payload. Se nao encontrar -> DEFERRED com backoff
4. **Transicao**: Valida e executa transicao de estado da charge -> publica evento no outbox -> marca PROCESSED
5. **Erro**: Transicao invalida -> FAILED. Erro generico -> retry com backoff. Max tentativas -> DLQ
6. **Reconciliacao**: Charge criada -> busca eventos DEFERRED com mesmo asaas_id -> marca para retry imediato

### Testes (Fase 6) - 25+ testes

#### WebhookServiceTest (unit/mockito)
- [x] Payload valido com token correto persiste evento
- [x] Evento duplicado (constraint violation) retorna sem erro - 200 OK
- [x] Token invalido lanca BusinessException
- [x] Company sem webhook token aceita qualquer request
- [x] Company inexistente lanca ResourceNotFoundException
- [x] Replay de evento FAILED marca como PENDING
- [x] Replay de evento DLQ marca como PENDING
- [x] Replay de evento PROCESSED lanca BusinessException
- [x] Replay de evento inexistente lanca ResourceNotFoundException
- [x] Resumo retorna contagens corretas por status
- [x] Acelera eventos DEFERRED com asaasId correspondente

#### WebhookEventHandlerTest (unit/mockito)
- [x] PAYMENT_RECEIVED com charge existente -> transiciona para RECEIVED + outbox
- [x] PAYMENT_RECEIVED sem charge local -> retorna false (defer)
- [x] Transicao invalida (CANCELED -> RECEIVED) lanca IllegalStateTransitionException
- [x] PAYMENT_CONFIRMED transiciona para CONFIRMED
- [x] PAYMENT_OVERDUE transiciona para OVERDUE
- [x] PAYMENT_UPDATED (informacional) nao altera status
- [x] Tipo de evento desconhecido eh marcado como processado
- [x] SUBSCRIPTION_DELETED cancela assinatura ativa
- [x] SUBSCRIPTION_DELETED ignora assinatura ja cancelada
- [x] SUBSCRIPTION_UPDATED publica evento outbox sem alterar estado
- [x] Evento de subscription sem assinatura local -> defer

#### WebhookProcessorTest (unit/mockito)
- [x] Evento processado com sucesso marca como PROCESSED
- [x] Recurso nao encontrado -> DEFERRED com backoff
- [x] DEFERRED apos max tentativas -> DLQ
- [x] Transicao invalida -> FAILED (nao retenta automaticamente)
- [x] Erro generico -> DEFERRED com retry
- [x] Sem eventos pendentes nao faz nada
- [x] Multiplos eventos processados em sequencia no mesmo batch
- [x] Timer de metricas eh registrado para cada evento

---

## Fase 7 - Subscriptions e Parcelamentos (CONCLUIDA)

### Entregaveis
- [x] Entity Subscription com @Version (optimistic lock) mapeando tabela subscriptions
- [x] SubscriptionStatus enum com mapa de transicoes validas (ACTIVE, PAUSED, SUSPENDED, CANCELED, EXPIRED)
- [x] SubscriptionRepository com findWithFilters, findByAsaasId, findActiveByPlanId, findActiveWithOverdueCharges
- [x] SubscriptionService completo: subscribe, cancel, pause, resume, updatePaymentMethod, suspend, findAll, findById, listCharges
- [x] Validacao de transicao de estado via transitionTo() com IllegalStateTransitionException
- [x] Integracao Asaas via AsaasGatewayService (createSubscription, cancelSubscription)
- [x] SubscriptionController com 9 endpoints REST
- [x] SubscriptionMapper para conversao entity -> DTO
- [x] DTOs: CreateSubscriptionRequest, UpdateSubscriptionRequest, UpdatePaymentMethodRequest, SubscriptionResponse
- [x] OverdueSubscriptionJob (@Scheduled, cron configuravel) - suspende assinaturas apos N cobrancas vencidas
- [x] Webhooks SUBSCRIPTION_CREATED, SUBSCRIPTION_UPDATED, SUBSCRIPTION_DELETED processados no WebhookEventHandler
- [x] AsaasWebhookPayload atualizado com SubscriptionData para deserializar eventos de subscription
- [x] Eventos outbox: SubscriptionCreatedEvent, SubscriptionCanceledEvent, SubscriptionSuspendedEvent, SubscriptionPausedEvent, SubscriptionResumedEvent, SubscriptionUpdatedEvent
- [x] Bloqueio de soft delete de plano com assinaturas ativas (PlanService atualizado)
- [x] Parcelamentos cartao + boleto ja implementados ponta a ponta (Fase 5 - Installment entity + ChargeService)

### Pacotes Implementados
- `subscription/` - Subscription, SubscriptionRepository, SubscriptionStatus, SubscriptionService, SubscriptionController, SubscriptionMapper, OverdueSubscriptionJob
- `subscription/dto/` - CreateSubscriptionRequest, UpdateSubscriptionRequest, UpdatePaymentMethodRequest, SubscriptionResponse

### Endpoints Implementados (Fase 7)

#### Assinaturas (/api/v1/subscriptions)
| Metodo | Endpoint | Descricao | Permissao |
|--------|----------|-----------|-----------|
| POST | /subscriptions | Criar nova assinatura | Autenticado |
| GET | /subscriptions | Listar com filtros (status, customerId) | Autenticado |
| GET | /subscriptions/{id} | Buscar assinatura por ID | Autenticado |
| GET | /subscriptions/{id}/charges | Listar cobrancas da assinatura | Autenticado |
| PUT | /subscriptions/{id} | Atualizar assinatura | Autenticado |
| PATCH | /subscriptions/{id}/payment-method | Alterar metodo de pagamento | Autenticado |
| DELETE | /subscriptions/{id} | Cancelar assinatura | Autenticado |
| POST | /subscriptions/{id}/pause | Pausar assinatura | Autenticado |
| POST | /subscriptions/{id}/resume | Retomar assinatura | Autenticado |

### Maquina de Estados (SubscriptionStatus)

| De | Para (transicoes validas) |
|----|--------------------------|
| ACTIVE | PAUSED, SUSPENDED, CANCELED, EXPIRED |
| PAUSED | ACTIVE, CANCELED |
| SUSPENDED | ACTIVE, CANCELED |
| CANCELED | (nenhuma) |
| EXPIRED | (nenhuma) |

### Configuracoes (application.yml)
- `app.subscription.max-overdue-charges`: numero maximo de cobrancas vencidas antes de suspender (default: 3)
- `app.subscription.overdue-check-cron`: cron para job de inadimplencia (default: 0 0 8 * * * - 8h diario)

### Testes (Fase 7) - 25+ testes

#### SubscriptionStatusTransitionTest (unit)
- [x] Tabela exaustiva de TODAS as transicoes validas via @ParameterizedTest
- [x] Tabela exaustiva de TODAS as transicoes invalidas via @ParameterizedTest
- [x] Nenhum status pode transitar para si mesmo
- [x] ACTIVE -> PAUSED funciona
- [x] PAUSED -> ACTIVE funciona (resume)
- [x] ACTIVE -> SUSPENDED funciona (inadimplencia)
- [x] SUSPENDED -> ACTIVE funciona (regularizacao)
- [x] CANCELED eh terminal
- [x] EXPIRED eh terminal
- [x] PAUSED -> SUSPENDED eh invalido
- [x] Transicao invalida nao altera status

#### SubscriptionServiceTest (unit/mockito)
- [x] Criar assinatura com sucesso (sync Asaas + outbox)
- [x] Cliente sem asaas_id lanca BusinessException
- [x] Plano inativo lanca BusinessException
- [x] Cancelar assinatura ACTIVE com sucesso (sync Asaas + outbox)
- [x] Cancelar assinatura ja CANCELED lanca excecao
- [x] Pausar assinatura ACTIVE com sucesso
- [x] Retomar assinatura PAUSED com sucesso
- [x] Pausar assinatura CANCELED lanca excecao
- [x] Retomar assinatura ACTIVE lanca excecao
- [x] Suspender assinatura ACTIVE por inadimplencia
- [x] Alterar metodo de pagamento de assinatura ativa
- [x] Alterar metodo de assinatura CANCELED lanca excecao
- [x] findById inexistente lanca ResourceNotFoundException

#### PlanSoftDeleteBlockTest (unit/mockito)
- [x] Soft delete de plano COM assinaturas ativas eh bloqueado
- [x] Soft delete de plano SEM assinaturas ativas funciona

#### Testes pendentes para integracao (requerem Postgres/Redis)
- [ ] Webhook SUBSCRIPTION_DELETED cancela assinatura local (integracao)
- [ ] Optimistic lock (@Version) impede atualizacao concorrente (integracao)
- [ ] OverdueSubscriptionJob suspende apos N cobrancas vencidas (integracao)

---

## Fase 8 - Mudanca de Plano com Pro-Rata (CONCLUIDA)

### Entregaveis
- [x] Entity SubscriptionPlanChange com transitionTo() e maquina de estados (PENDING, AWAITING_PAYMENT, EFFECTIVE, SCHEDULED, FAILED, CANCELED)
- [x] Entity CustomerCreditLedger (append-only, saldo cacheado em customers.credit_balance)
- [x] PlanChangeStatus enum com mapa de transicoes validas
- [x] PlanChangeType enum (UPGRADE, DOWNGRADE, SIDEGRADE)
- [x] PlanChangePolicy enum (IMMEDIATE_PRORATA, END_OF_CYCLE, IMMEDIATE_NO_PRORATA)
- [x] CreditLedgerType enum (CREDIT, DEBIT) e CreditLedgerOrigin enum (DOWNGRADE_PRORATA, MANUAL_ADJUSTMENT, REFUND, CHARGE_APPLIED)
- [x] ProrationCalculator (puro, sem dependencias) com formula BigDecimal HALF_EVEN 2 casas
- [x] PlanLimitsValidator com SPI - valida uso atual vs limites do plano destino (estrategias: BLOCK, SCHEDULE, GRACE_PERIOD)
- [x] CustomerCreditLedgerService com lock pessimista (SELECT FOR UPDATE) na linha do customer
- [x] CustomerRepository.findByIdWithLock() com @Lock(PESSIMISTIC_WRITE)
- [x] PlanChangeService completo: previewChange, requestChange, confirmAfterPayment, cancelChange, processScheduledChanges
- [x] PlanChangeController com 4 endpoints REST (preview-change, change-plan, plan-changes, cancel)
- [x] PlanChangeMapper para conversao entity -> DTO
- [x] ScheduledPlanChangeJob (@Scheduled cron diario) para processar mudancas END_OF_CYCLE
- [x] Integracao webhook: PAYMENT_RECEIVED de cobranca Delta chama confirmAfterPayment automaticamente
- [x] Eventos outbox: PlanChangedEvent, PlanChangePendingPaymentEvent, PlanChangeScheduledEvent
- [x] Endpoint GET /customers/{id}/credit-balance (saldo + extrato do ledger)
- [x] Upgrade: gera cobranca avulsa de Delta (cartao = imediato, PIX/boleto = AWAITING_PAYMENT)
- [x] Downgrade: gera credito no ledger do cliente (nunca reembolso em dinheiro)
- [x] Sidegrade: troca plano sem impacto financeiro

### Pacotes Implementados
- `planchange/` - SubscriptionPlanChange, SubscriptionPlanChangeRepository, PlanChangeStatus, PlanChangeType, PlanChangePolicy, PlanChangeService, PlanChangeController, PlanChangeMapper, ProrationCalculator, PlanLimitsValidator, ScheduledPlanChangeJob
- `planchange/dto/` - RequestPlanChangeRequest, PlanChangePreviewResponse, PlanChangeResponse
- `creditledger/` - CustomerCreditLedger, CustomerCreditLedgerRepository, CustomerCreditLedgerService, CreditLedgerType, CreditLedgerOrigin

### Endpoints Implementados (Fase 8)

#### Mudanca de Plano (/api/v1/subscriptions/{subscriptionId})
| Metodo | Endpoint | Descricao | Permissao |
|--------|----------|-----------|-----------|
| POST | /subscriptions/{id}/preview-change?newPlanId=... | Preview do pro-rata | Autenticado |
| POST | /subscriptions/{id}/change-plan | Solicitar mudanca de plano | Autenticado |
| GET | /subscriptions/{id}/plan-changes | Historico de mudancas | Autenticado |
| DELETE | /subscriptions/{id}/plan-changes/{changeId} | Cancelar mudanca pendente | Autenticado |

#### Saldo de Credito (/api/v1/customers/{id})
| Metodo | Endpoint | Descricao | Permissao |
|--------|----------|-----------|-----------|
| GET | /customers/{id}/credit-balance | Saldo + extrato do ledger | Autenticado |

### Formula Pro-Rata
```
creditUnused = V_atual * (D_restantes / D_total)
newCost      = V_novo  * (D_restantes / D_total)
Delta        = newCost - creditUnused

Delta > 0 -> UPGRADE (cobranca avulsa)
Delta < 0 -> DOWNGRADE (credito no ledger)
Delta = 0 -> SIDEGRADE (troca sem impacto)
```

### Maquina de Estados (PlanChangeStatus)

| De | Para (transicoes validas) |
|----|--------------------------|
| PENDING | AWAITING_PAYMENT, EFFECTIVE, SCHEDULED, FAILED, CANCELED |
| AWAITING_PAYMENT | EFFECTIVE, FAILED, CANCELED |
| SCHEDULED | EFFECTIVE, FAILED, CANCELED |
| EFFECTIVE | (nenhuma) |
| FAILED | (nenhuma) |
| CANCELED | (nenhuma) |

### Testes Pendentes (Fase 8)
- [ ] ProrationCalculator: property-based tests com jqwik
- [ ] ProrationCalculator: 20+ cenarios unitarios (upgrade, downgrade, sidegrade, dia 1, ultimo dia, etc.)
- [ ] PlanChangeService: preview, request, confirm, cancel
- [ ] PlanLimitsValidator: BLOCK, SCHEDULE, GRACE_PERIOD
- [ ] CustomerCreditLedgerService: addCredit, debitCredit, saldo insuficiente
- [ ] Concorrencia: dois change-plan simultaneos -> apenas um vence
- [ ] Webhook fora de ordem para cobrancas de Delta

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

### Webhooks (/api/v1/webhooks/asaas)
| Metodo | Endpoint | Descricao | Permissao |
|--------|----------|-----------|-----------|
| POST | /webhooks/asaas?companyId={id} | Receber webhook do Asaas | Publico (token validado) |

### Admin - Webhooks (/api/v1/admin/webhooks)
| Metodo | Endpoint | Descricao | Permissao |
|--------|----------|-----------|-----------|
| GET | /admin/webhooks?status=... | Listar eventos por status | HOLDING_ADMIN, SYSTEM |
| GET | /admin/webhooks/summary | Resumo dos eventos webhook | HOLDING_ADMIN, SYSTEM |
| POST | /admin/webhooks/{eventId}/replay | Reprocessar evento FAILED/DLQ | HOLDING_ADMIN, SYSTEM |

### Assinaturas (/api/v1/subscriptions)
| Metodo | Endpoint | Descricao | Permissao |
|--------|----------|-----------|-----------|
| POST | /subscriptions | Criar nova assinatura | Autenticado |
| GET | /subscriptions | Listar com filtros | Autenticado |
| GET | /subscriptions/{id} | Buscar por ID | Autenticado |
| GET | /subscriptions/{id}/charges | Listar cobrancas | Autenticado |
| PUT | /subscriptions/{id} | Atualizar | Autenticado |
| PATCH | /subscriptions/{id}/payment-method | Alterar metodo pagamento | Autenticado |
| DELETE | /subscriptions/{id} | Cancelar | Autenticado |
| POST | /subscriptions/{id}/pause | Pausar | Autenticado |
| POST | /subscriptions/{id}/resume | Retomar | Autenticado |

### Mudanca de Plano (/api/v1/subscriptions/{subscriptionId})
| Metodo | Endpoint | Descricao | Permissao |
|--------|----------|-----------|-----------|
| POST | /subscriptions/{id}/preview-change?newPlanId=... | Preview pro-rata | Autenticado |
| POST | /subscriptions/{id}/change-plan | Solicitar mudanca | Autenticado |
| GET | /subscriptions/{id}/plan-changes | Historico mudancas | Autenticado |
| DELETE | /subscriptions/{id}/plan-changes/{changeId} | Cancelar mudanca | Autenticado |

### Saldo de Credito (/api/v1/customers/{id})
| Metodo | Endpoint | Descricao | Permissao |
|--------|----------|-----------|-----------|
| GET | /customers/{id}/credit-balance | Saldo + extrato | Autenticado |

### Endpoints Pendentes (Fase 9)
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

### Metricas Webhook (Fase 6)

| Metrica | Tipo | Descricao |
|---------|------|-----------|
| webhook_processing_duration_seconds | Timer | Tempo de processamento de um evento webhook |
| webhook_deferred_total | Counter | Total de eventos adiados (recurso nao encontrado) |
| webhook_dlq_total | Counter | Total de eventos movidos para DLQ |
| webhook_pending_count | Gauge | Quantidade de eventos pendentes |
| webhook_deferred_count | Gauge | Quantidade de eventos DEFERRED |
| webhook_dlq_count | Gauge | Quantidade de eventos na DLQ |
| webhook_lag_seconds | Gauge | Idade em segundos do evento pendente/deferred mais antigo |

### Metricas Pendentes (Fase 9)
- charges_created_total{method, company, origin}
- charges_paid_total
- plan_changes_total
- plan_change_proration_amount
- customer_credit_balance_total
