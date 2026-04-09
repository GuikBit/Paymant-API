# Runbook: Webhook DLQ Crescendo

## Sintomas
- Alerta `WebhookDlqNotEmpty` disparando (DLQ > 0 por mais de 1h)
- Metrica `webhook_dlq_count` > 0
- Eventos de webhook nao sendo processados

## Diagnostico
1. Verificar summary dos webhooks:
   ```
   GET /api/v1/admin/webhooks/summary
   ```
2. Listar eventos em DLQ:
   ```
   GET /api/v1/admin/webhooks?status=DLQ&size=20
   ```
3. Verificar o `lastError` dos eventos para entender a causa raiz
4. Causas comuns:
   - **Recurso nao encontrado**: charge/subscription ainda nao criada localmente (evento chegou fora de ordem)
   - **Transicao de estado invalida**: webhook tentando transitar para status incompativel
   - **Erro de parse**: payload inesperado do Asaas

## Acoes
### Se recurso nao encontrado (eventos fora de ordem)
1. Verificar se a charge/subscription existe no Asaas e criar localmente se necessario
2. Replay os eventos:
   ```
   POST /api/v1/admin/reconciliation/dlq/replay
   ```

### Se transicao invalida
1. Verificar status atual da charge/subscription no Asaas
2. Corrigir manualmente via reconciliacao:
   ```
   POST /api/v1/admin/reconciliation/charges?daysBack=7
   ```
3. Replay individual:
   ```
   POST /api/v1/admin/webhooks/{eventId}/replay
   ```

### Se erro de parse
1. Verificar payload do evento em DLQ
2. Pode indicar mudanca na API do Asaas - revisar `WebhookEventHandler`
3. Abrir issue para correcao e fazer replay apos fix

## Prevencao
- Monitorar metricas `webhook_deferred_count` como indicador antecipado
- Garantir que o `WebhookProcessor` esta rodando (checar logs a cada 3s)
