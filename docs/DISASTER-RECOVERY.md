# Plano de Disaster Recovery - Payment API

## 1. RPO e RTO

| Metrica | Objetivo | Estrategia |
|---------|----------|-----------|
| RPO (Recovery Point Objective) | 1 hora | Backup incremental a cada hora + PITR |
| RTO (Recovery Time Objective) | 4 horas | Restore automatizado + validacao |

## 2. Componentes Criticos

| Componente | Prioridade | Estrategia de DR |
|-----------|-----------|-----------------|
| PostgreSQL 16 | P0 | Backup diario full + WAL archiving (PITR) |
| Redis 7 | P1 | Rebuild a partir do PostgreSQL (cache quente) |
| Aplicacao Java | P1 | Docker image versionada no registry |
| Asaas (externo) | N/A | Nao controlamos — circuit breaker protege |

## 3. Backup do PostgreSQL

### Backup automatizado
```bash
# Backup diario full (cron 2:00 AM)
pg_dump -Fc -U payment_flyway -d payment_db > /backup/payment_db_$(date +%Y%m%d).dump

# WAL archiving para PITR (configurar postgresql.conf)
archive_mode = on
archive_command = 'cp %p /archive/%f'
```

### Retencao de backups
- Diarios: 30 dias
- Semanais: 12 semanas
- Mensais: 12 meses

## 4. Procedimento de Restore

### 4.1 Restore completo
```bash
# 1. Parar a aplicacao
docker compose stop app

# 2. Restaurar banco
pg_restore -U postgres -d payment_db_restore /backup/payment_db_YYYYMMDD.dump

# 3. Verificar roles
psql -U postgres -d payment_db_restore -c "SELECT rolname, rolbypassrls FROM pg_roles WHERE rolname LIKE 'payment_%';"

# 4. Verificar RLS
psql -U postgres -d payment_db_restore -c "SELECT tablename, policyname FROM pg_policies WHERE schemaname='public';"

# 5. Validar integridade
psql -U payment_app -d payment_db_restore -c "SET app.current_company_id = '1'; SELECT COUNT(*) FROM charges;"

# 6. Trocar banco e reiniciar
docker compose up app
```

### 4.2 Point-in-Time Recovery (PITR)
```bash
# Restaurar ate um momento especifico
pg_restore --target-time="2026-04-09 14:30:00" ...
```

## 5. Validacao do DR (trimestral)

### Checklist de validacao
- [ ] Restaurar backup mais recente em ambiente isolado
- [ ] Verificar que migrations Flyway passam sem erro
- [ ] Verificar que RLS esta ativo e policies funcionam
- [ ] Executar health check da aplicacao (`/actuator/health`)
- [ ] Verificar que dados de pelo menos 2 tenants estao isolados
- [ ] Verificar que ledger de credito esta consistente:
  ```sql
  SELECT c.id, c.credit_balance,
         COALESCE(SUM(CASE WHEN l.type='CREDIT' THEN l.amount ELSE -l.amount END), 0)
  FROM customers c LEFT JOIN customer_credit_ledger l ON l.customer_id = c.id
  GROUP BY c.id HAVING c.credit_balance != COALESCE(SUM(CASE WHEN l.type='CREDIT' THEN l.amount ELSE -l.amount END), 0);
  ```
- [ ] Executar reconciliacao de charges dos ultimos 7 dias
- [ ] Documentar resultado e tempo gasto

## 6. Cenarios de Falha

| Cenario | Impacto | Acao |
|---------|---------|------|
| Perda do banco principal | Total | PITR para momento anterior a falha |
| Corrupcao de dados em 1 tenant | Parcial | Restore seletivo com filtro por company_id |
| Perda do Redis | Baixo | Redis se reconstroi automaticamente (cache) |
| Container da app crash | Baixo | Docker restart policy: always |
| Datacenter fora do ar | Total | Restore em datacenter secundario |

## 7. Contatos de Emergencia

| Funcao | Contato |
|--------|---------|
| DBA | (configurar) |
| DevOps | (configurar) |
| Tech Lead | (configurar) |
| Suporte Asaas | suporte@asaas.com |
