# Runbook: Divergencia de Saldo no Ledger

## Sintomas
- Saldo em `customers.credit_balance` diverge da soma do `customer_credit_ledger`
- Cliente reporta saldo incorreto
- Mudanca de plano com credito nao refletido

## Diagnostico
1. Comparar saldo cacheado vs soma do ledger:
   ```sql
   SELECT c.id, c.name, c.credit_balance AS saldo_cache,
          COALESCE(SUM(CASE WHEN l.type = 'CREDIT' THEN l.amount
                            WHEN l.type = 'DEBIT' THEN -l.amount
                            ELSE 0 END), 0) AS saldo_calculado
   FROM customers c
   LEFT JOIN customer_credit_ledger l ON l.customer_id = c.id
   GROUP BY c.id, c.name, c.credit_balance
   HAVING c.credit_balance != COALESCE(SUM(CASE WHEN l.type = 'CREDIT' THEN l.amount
                                                WHEN l.type = 'DEBIT' THEN -l.amount
                                                ELSE 0 END), 0);
   ```

2. Verificar se houve operacoes concorrentes sem lock:
   ```sql
   SELECT * FROM customer_credit_ledger
   WHERE customer_id = :id ORDER BY created_at DESC LIMIT 20;
   ```

## Acoes
1. **Corrigir o saldo cacheado** para refletir a soma real do ledger:
   ```sql
   UPDATE customers SET credit_balance = (
       SELECT COALESCE(SUM(CASE WHEN type = 'CREDIT' THEN amount
                               WHEN type = 'DEBIT' THEN -amount END), 0)
       FROM customer_credit_ledger WHERE customer_id = customers.id
   )
   WHERE id = :customer_id;
   ```

2. **Se falta entrada no ledger** (ex: credito de downgrade nao foi registrado):
   - Verificar `subscription_plan_changes` para mudancas de plano sem `credit_ledger_id`
   - Inserir entrada corretiva manualmente com origin `MANUAL_ADJUSTMENT`

## Prevencao
- O `CustomerCreditLedgerService` usa `SELECT FOR UPDATE` (lock pessimista)
- Toda operacao de credito/debito DEVE usar `findByIdWithLock()`
- Nunca alterar `credit_balance` diretamente sem registrar no ledger
