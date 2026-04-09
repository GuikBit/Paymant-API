# Politica LGPD - Payment API

## 1. Dados Pessoais Tratados

| Dado | Tabela | Base Legal | Finalidade |
|------|--------|-----------|------------|
| Nome completo | customers | Execucao contratual | Identificacao para cobranca |
| CPF/CNPJ | customers | Obrigacao legal (fiscal) | Emissao de cobrancas no Asaas |
| Email | customers | Execucao contratual | Notificacoes de cobranca |
| Telefone | customers | Execucao contratual | Contato para cobranca |
| Endereco | customers | Obrigacao legal (fiscal) | Emissao de boletos |
| IP de acesso | audit_log | Interesse legitimo | Seguranca e auditoria |
| Dados de pagamento | charges (via Asaas) | Execucao contratual | Processamento de pagamentos |

## 2. Armazenamento e Seguranca

- **Criptografia em repouso**: chaves de API Asaas criptografadas com Jasypt (AES-256)
- **Criptografia em transito**: TLS 1.2+ obrigatorio em todos os endpoints
- **Isolamento de dados**: Row-Level Security (RLS) no PostgreSQL por tenant
- **Mascaramento em logs**: CPF, CNPJ, cartao e email mascarados via PiiMaskingConverter
- **Controle de acesso**: JWT + roles (HOLDING_ADMIN, COMPANY_ADMIN, COMPANY_OPERATOR)
- **Auditoria**: toda operacao sensivel registrada em audit_log com actor, action, entity

## 3. Retencao de Dados

| Tipo de dado | Periodo de retencao | Justificativa |
|--------------|-------------------|---------------|
| Dados de clientes | Enquanto ativo + 5 anos | Obrigacao fiscal |
| Cobrancas | 10 anos | Obrigacao fiscal (CTN art. 173) |
| Logs de auditoria | 5 anos | Compliance e investigacao |
| Webhooks processados | 90 dias | Operacional (reconciliacao) |
| Idempotency keys | 24 horas | Operacional (deduplicacao) |
| Outbox events | 30 dias apos publicacao | Operacional (reprocessamento) |

## 4. Soft Delete

Clientes e planos utilizam soft delete (`deleted_at IS NOT NULL`) para:
- Manter integridade referencial com cobrancas e assinaturas
- Permitir restauracao dentro do periodo de retencao
- Nao aparecem em queries normais (filtro automatico via `@SQLRestriction`)

## 5. Direitos do Titular (LGPD Art. 18)

### Acesso aos dados
- `GET /api/v1/customers/{id}` - retorna dados cadastrais
- `GET /api/v1/customers/{id}/credit-balance` - retorna saldo e extrato

### Correcao
- `PUT /api/v1/customers/{id}` - atualiza dados cadastrais

### Eliminacao
- `DELETE /api/v1/customers/{id}` - soft delete do cliente
- Dados vinculados a obrigacoes fiscais (cobrancas) sao retidos conforme periodo legal
- Apos periodo de retencao, dados podem ser anonimizados via job de limpeza

### Portabilidade
- `GET /api/v1/reports/export/overdue` e demais endpoints de export CSV

## 6. Compartilhamento com Terceiros

| Terceiro | Dados compartilhados | Finalidade | Base legal |
|----------|---------------------|------------|------------|
| Asaas (gateway) | Nome, CPF/CNPJ, email, endereco | Processamento de pagamentos | Execucao contratual |

## 7. Encarregado (DPO)

Contato do encarregado de protecao de dados deve ser configurado conforme politica da holding.
