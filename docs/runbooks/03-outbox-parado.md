# Runbook: Outbox Parado / Lag Alto

## Sintomas
- Alerta `OutboxLagHigh` disparando (lag > 5 minutos)
- Metrica `outbox_lag_seconds` crescendo
- Metrica `outbox_pending_count` acumulando
- n8n nao recebendo eventos de dominio

## Diagnostico
1. Verificar lag e contadores:
   ```
   GET /api/v1/admin/outbox/summary
   curl http://localhost:8080/actuator/prometheus | grep outbox
   ```
2. Verificar se o `OutboxRelay` esta rodando (logs a cada 5s):
   ```
   grep "OutboxRelay" /var/log/payment-api/*.log | tail -20
   ```
3. Verificar se o webhook-url esta configurado:
   ```
   echo $OUTBOX_WEBHOOK_URL
   ```
4. Causas comuns:
   - **webhook-url vazio**: relay nao sabe para onde enviar
   - **Destino (n8n) fora do ar**: relay falha ao entregar
   - **Scheduling desabilitado**: `@EnableScheduling` removido acidentalmente
   - **Lock contention**: `FOR UPDATE SKIP LOCKED` falhando por transacoes longas

## Acoes
### webhook-url nao configurado
1. Definir `OUTBOX_WEBHOOK_URL` no ambiente e reiniciar

### Destino fora do ar
1. Verificar conectividade com o destino
2. Eventos continuam acumulando em PENDING e serao entregues quando destino voltar
3. Apos normalizar, verificar se DLQ cresceu:
   ```
   GET /api/v1/admin/outbox?status=DLQ
   ```
4. Retry de eventos DLQ:
   ```
   POST /api/v1/admin/outbox/{id}/retry
   ```

### Lock contention
1. Verificar queries longas no Postgres:
   ```sql
   SELECT pid, now() - pg_stat_activity.query_start AS duration, query
   FROM pg_stat_activity WHERE state = 'active' AND duration > interval '30 seconds';
   ```
2. Se transacoes travadas, considerar `pg_terminate_backend(pid)` com cautela

## Prevencao
- Configurar alerta em `outbox_pending_count > 50`
- Garantir que o destino do webhook tem health check
