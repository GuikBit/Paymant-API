# Runbook: Mudanca de Plano Travada em AWAITING_PAYMENT

## Sintomas
- `subscription_plan_changes` com status `AWAITING_PAYMENT` ha muito tempo
- Cliente pagou mas plano nao foi alterado
- Webhook `PAYMENT_RECEIVED` da cobranca Delta nao processado

## Diagnostico
1. Encontrar mudancas travadas:
   ```sql
   SELECT pc.id, pc.subscription_id, pc.change_type, pc.status,
          pc.delta_amount, pc.charge_id, pc.requested_at,
          c.status AS charge_status, c.asaas_id AS charge_asaas_id
   FROM subscription_plan_changes pc
   LEFT JOIN charges c ON pc.charge_id = c.id
   WHERE pc.status = 'AWAITING_PAYMENT'
   ORDER BY pc.requested_at ASC;
   ```

2. Verificar se a cobranca Delta foi paga no Asaas:
   - Buscar pelo `asaas_id` da charge no painel Asaas
   - Ou via API: `GET /payments/{asaas_id}`

3. Verificar webhook DLQ para o evento de pagamento:
   ```sql
   SELECT * FROM webhook_events
   WHERE payload::text LIKE '%{asaas_id}%'
   ORDER BY received_at DESC;
   ```

## Acoes
### Se cobranca foi paga no Asaas mas webhook nao chegou
1. Reconciliar a charge:
   ```
   POST /api/v1/admin/reconciliation/charges?daysBack=7
   ```
2. Se a charge ficou como RECEIVED, o `WebhookEventHandler` deveria chamar `confirmAfterPayment`
3. Se nao funcionou, chamar manualmente (via debug ou SQL direto):
   ```sql
   -- 1. Atualizar a charge para RECEIVED
   UPDATE charges SET status = 'RECEIVED' WHERE id = :charge_id;
   -- 2. Atualizar a mudanca de plano
   UPDATE subscription_plan_changes SET status = 'EFFECTIVE', effective_at = NOW()
   WHERE id = :plan_change_id;
   -- 3. Atualizar o plano da assinatura
   UPDATE subscriptions SET plan_id = :new_plan_id WHERE id = :subscription_id;
   ```

### Se cobranca nao foi paga (expirou)
1. Cancelar a mudanca de plano:
   ```
   DELETE /api/v1/subscriptions/{subscriptionId}/plan-changes/{changeId}
   ```
2. O cliente permanece no plano atual

### Se webhook chegou mas falhou no processamento
1. Replay o evento:
   ```
   POST /api/v1/admin/webhooks/{eventId}/replay
   ```

## Prevencao
- Monitorar mudancas em AWAITING_PAYMENT ha mais de 48h via query agendada
- Considerar job para auto-cancelar mudancas com charge expirada
