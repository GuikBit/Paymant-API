# Runbook: Asaas Fora do Ar

## Sintomas
- Alertas `AsaasCircuitBreakerOpen` ou `AsaasApiErrorRateHigh` disparando
- Erros 502/503 nos endpoints de criacao de charges e subscriptions
- Metrica `asaas_api_errors_total` crescendo
- Circuit breaker em estado OPEN

## Diagnostico
1. Verificar status do Asaas: https://status.asaas.com
2. Verificar metricas:
   ```
   curl http://localhost:8080/actuator/prometheus | grep asaas
   curl http://localhost:8080/actuator/health | jq .components.circuitBreakers
   ```
3. Verificar logs: `grep "AsaasApiException" /var/log/payment-api/*.log`

## Acoes Imediatas
1. **NAO reiniciar a aplicacao** - o circuit breaker protege contra cascata
2. Esperar o Asaas voltar - o circuit breaker tentara reconectar automaticamente (half-open a cada 30s)
3. Comunicar as equipes consumidoras que operacoes de pagamento estao temporariamente indisponiveis

## Acoes pos-incidente
1. Apos Asaas voltar, verificar se o circuit breaker fechou automaticamente
2. Executar reconciliacao manual:
   ```
   POST /api/v1/admin/reconciliation/charges?daysBack=1
   POST /api/v1/admin/reconciliation/subscriptions
   ```
3. Verificar webhook DLQ e replay se necessario:
   ```
   POST /api/v1/admin/reconciliation/dlq/replay
   ```

## Escalacao
- Se indisponibilidade > 1 hora: contatar suporte Asaas (suporte@asaas.com)
- Se cobrancas criticas pendentes: considerar operacao manual via painel Asaas
