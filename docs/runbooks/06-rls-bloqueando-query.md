# Runbook: RLS Bloqueando Query Legitima

## Sintomas
- Query retorna zero resultados quando deveria retornar dados
- Erro "permission denied" em operacoes de leitura/escrita
- Jobs agendados nao encontram registros para processar
- Logs com WARNING "Tenant context not set"

## Diagnostico
1. Verificar se `app.current_company_id` esta setado na sessao:
   ```sql
   SHOW app.current_company_id;
   ```
   Se retornar `0` ou vazio, RLS esta filtrando tudo.

2. Verificar se o usuario esta usando a role correta:
   ```sql
   SELECT current_user, current_setting('app.current_company_id');
   ```

3. Verificar as policies ativas:
   ```sql
   SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual
   FROM pg_policies WHERE schemaname = 'public';
   ```

## Causas Comuns

### Job/Worker sem TenantContext
- Jobs `@Scheduled` rodam fora de contexto HTTP
- **Solucao**: usar `@CrossTenant` ou setar `TenantContext.setCompanyId()` explicitamente
  ```java
  @Scheduled(cron = "...")
  @CrossTenant(reason = "Job runs across all companies")
  public void myJob() {
      for (Company company : companies) {
          try {
              TenantContext.setCompanyId(company.getId());
              // ... processar
          } finally {
              TenantContext.clear();
          }
      }
  }
  ```

### Interceptor excluindo path errado
- Verificar `WebMvcConfig.addInterceptors()` para paths excluidos
- Paths excluidos do `TenantContextInterceptor` nao terao `company_id` setado

### Admin/Cross-tenant sem @CrossTenant
- Operacoes admin que precisam acessar dados de multiplos tenants
- Adicionar `@CrossTenant(reason = "...")` ao metodo

## Acoes de Emergencia
Se uma query critica precisa rodar imediatamente ignorando RLS:
```sql
-- CUIDADO: usar apenas em emergencia, com a role de Flyway
SET ROLE payment_flyway;
-- executar query aqui
RESET ROLE;
```

## Prevencao
- Todo novo job deve incluir `TenantContext.setCompanyId()` no loop de empresas
- Testes de isolamento RLS devem ser executados em CI (`RlsIsolationTest`)
- Nunca usar `payment_flyway` em codigo de aplicacao
