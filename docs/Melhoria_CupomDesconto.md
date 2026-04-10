Levantamento Completo - Sistema de Cupons de Desconto                                                                                                                                     
                                      
  1. Entidades                                                                                                                                                                              
                                                                                                                                                                                            
  CupomDesconto (tabela cupom_desconto)                                                                                                                                                     
                                                                                                                                                                                            
  ┌────────────────────────┬──────────────────┬─────────────┬───────────────────────────────────────────┐                                                                                   
  │         Campo          │       Tipo       │ Obrigatório │                 Descrição                 │                                                                                   
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤                                                                                   
  │ id                     │ Long (PK)        │    auto     │ ID                                        │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ codigo                 │ String(50)       │     sim     │ Código único, armazenado UPPERCASE        │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ descricao              │ String(255)      │     não     │ Descrição do cupom                        │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ tipoDesconto           │ Enum             │     sim     │ PERCENTUAL ou VALOR_FIXO                  │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ valorDesconto          │ BigDecimal(10,2) │     sim     │ Valor ou percentual (>0)                  │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ dtInicio               │ LocalDateTime    │     não     │ Início da vigência                        │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ dtFim                  │ LocalDateTime    │     não     │ Fim da vigência                           │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ maxUtilizacoes         │ Integer          │     não     │ Limite global (null = ilimitado)          │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ maxUtilizacoesPorOrg   │ Integer          │     não     │ Limite por organização                    │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ totalUtilizado         │ Integer          │     sim     │ Contador de usos (default 0)              │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ planosPermitidos       │ JSONB            │     não     │ ["basico","premium"] ou null = todos      │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ segmentosPermitidos    │ JSONB            │     não     │ ["Barbearia","Salao"] ou null = todos     │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ organizacoesPermitidas │ JSONB            │     não     │ [1,5,10] ou null = todas                  │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ cicloCobranca          │ String(10)       │     não     │ MENSAL, ANUAL ou null = ambos             │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ tipoAplicacao          │ Enum             │     sim     │ PRIMEIRA_COBRANCA (default) ou RECORRENTE │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ mesesRecorrencia       │ Integer          │     não     │ Meses para RECORRENTE (null = infinito)   │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ ativo                  │ Boolean          │     sim     │ Soft delete (default true)                │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ dtCriacao              │ LocalDateTime    │    auto     │ @PrePersist                               │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ dtAtualizacao          │ LocalDateTime    │    auto     │ @PreUpdate                                │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ userCriacao            │ Long             │     não     │ Audit - quem criou                        │
  ├────────────────────────┼──────────────────┼─────────────┼───────────────────────────────────────────┤
  │ userAtualizacao        │ Long             │     não     │ Audit - quem atualizou                    │
  └────────────────────────┴──────────────────┴─────────────┴───────────────────────────────────────────┘

  Métodos da entidade:
  - isVigente() → true se ativo + dentro do período (dtInicio/dtFim)
  - atingiuLimiteGlobal() → true se maxUtilizacoes != null && totalUtilizado >= maxUtilizacoes

  ---
  CupomUtilizacao (tabela cupom_utilizacao)

  ┌───────────────┬────────────────────┬─────────────┬─────────────────────────┐
  │     Campo     │        Tipo        │ Obrigatório │        Descrição        │
  ├───────────────┼────────────────────┼─────────────┼─────────────────────────┤
  │ id            │ Long (PK)          │    auto     │                         │
  ├───────────────┼────────────────────┼─────────────┼─────────────────────────┤
  │ cupom         │ FK → CupomDesconto │     sim     │ Cupom usado             │
  ├───────────────┼────────────────────┼─────────────┼─────────────────────────┤
  │ organizacaoId │ Long               │     sim     │ Quem usou               │
  ├───────────────┼────────────────────┼─────────────┼─────────────────────────┤
  │ assinaturaId  │ Long               │     não     │ Assinatura associada    │
  ├───────────────┼────────────────────┼─────────────┼─────────────────────────┤
  │ cobrancaId    │ Long               │     não     │ Cobrança associada      │
  ├───────────────┼────────────────────┼─────────────┼─────────────────────────┤
  │ valorOriginal │ BigDecimal(10,2)   │     sim     │ Preço antes do desconto │
  ├───────────────┼────────────────────┼─────────────┼─────────────────────────┤
  │ valorDesconto │ BigDecimal(10,2)   │     sim     │ Valor do desconto       │
  ├───────────────┼────────────────────┼─────────────┼─────────────────────────┤
  │ valorFinal    │ BigDecimal(10,2)   │     sim     │ Preço final             │
  ├───────────────┼────────────────────┼─────────────┼─────────────────────────┤
  │ planoCodigo   │ String(50)         │     não     │ Plano usado             │
  ├───────────────┼────────────────────┼─────────────┼─────────────────────────┤
  │ cicloCobranca │ String(10)         │     não     │ MENSAL ou ANUAL         │
  ├───────────────┼────────────────────┼─────────────┼─────────────────────────┤
  │ dtUtilizacao  │ LocalDateTime      │     sim     │ Timestamp do uso        │
  └───────────────┴────────────────────┴─────────────┴─────────────────────────┘

  ---
  2. Enums

  TipoDesconto:       PERCENTUAL | VALOR_FIXO
  TipoAplicacaoCupom: PRIMEIRA_COBRANCA | RECORRENTE

  ---
  3. Endpoints

  Admin CRUD (/api/v1/admin/cupons)

  ┌────────┬───────────────────┬──────────────────┬────────────────────────┬────────────────────────────────┐
  │ Método │       Path        │    Descrição     │        Request         │            Response            │
  ├────────┼───────────────────┼──────────────────┼────────────────────────┼────────────────────────────────┤
  │ GET    │ /                 │ Listar todos     │ -                      │ List<CupomDescontoResponseDTO> │
  ├────────┼───────────────────┼──────────────────┼────────────────────────┼────────────────────────────────┤
  │ GET    │ /vigentes         │ Listar vigentes  │ -                      │ List<CupomDescontoResponseDTO> │
  ├────────┼───────────────────┼──────────────────┼────────────────────────┼────────────────────────────────┤
  │ GET    │ /{id}             │ Buscar por ID    │ -                      │ CupomDescontoResponseDTO       │
  ├────────┼───────────────────┼──────────────────┼────────────────────────┼────────────────────────────────┤
  │ POST   │ /                 │ Criar cupom      │ CupomDescontoCreateDTO │ CupomDescontoResponseDTO (201) │
  ├────────┼───────────────────┼──────────────────┼────────────────────────┼────────────────────────────────┤
  │ PUT    │ /{id}             │ Atualizar        │ CupomDescontoUpdateDTO │ CupomDescontoResponseDTO       │
  ├────────┼───────────────────┼──────────────────┼────────────────────────┼────────────────────────────────┤
  │ DELETE │ /{id}             │ Desativar (soft) │ -                      │ void                           │
  ├────────┼───────────────────┼──────────────────┼────────────────────────┼────────────────────────────────┤
  │ PATCH  │ /{id}/ativar      │ Reativar         │ -                      │ CupomDescontoResponseDTO       │
  ├────────┼───────────────────┼──────────────────┼────────────────────────┼────────────────────────────────┤
  │ GET    │ /{id}/utilizacoes │ Histórico de uso │ -                      │ List<CupomUtilizacaoDTO>       │
  └────────┴───────────────────┴──────────────────┴────────────────────────┴────────────────────────────────┘

  Validação pública (/api/v1/public/planos)

  ┌────────┬────────────────┬──────┬─────────────────┬───────────────────────────┐
  │ Método │      Path      │ Auth │     Request     │         Response          │
  ├────────┼────────────────┼──────┼─────────────────┼───────────────────────────┤
  │ POST   │ /validar-cupom │ Não  │ ValidarCupomDTO │ CupomValidacaoResponseDTO │
  └────────┴────────────────┴──────┴─────────────────┴───────────────────────────┘

  Validação autenticada (/api/v1/assinatura)

  ┌────────┬────────────────┬──────┬─────────────────┬───────────────────────────┐
  │ Método │      Path      │ Auth │     Request     │         Response          │
  ├────────┼────────────────┼──────┼─────────────────┼───────────────────────────┤
  │ POST   │ /validar-cupom │ Sim  │ ValidarCupomDTO │ CupomValidacaoResponseDTO │
  └────────┴────────────────┴──────┴─────────────────┴───────────────────────────┘

  Aplicação do cupom (campo codigoCupom opcional nos requests):

  - POST /api/v1/assinatura/escolher-plano
  - POST /api/v1/assinatura/reativar

  ---
  4. Regras de Validação (em ordem)

  Quando validarCupom() é chamado, 8 checks sequenciais:

  ┌─────┬────────────────────────────────────┬──────────────────────────────────────────────────────────────┐
  │  #  │               Check                │                       Mensagem de erro                       │
  ├─────┼────────────────────────────────────┼──────────────────────────────────────────────────────────────┤
  │ 1   │ Cupom existe e está ativo          │ "Cupom não encontrado ou inativo"                            │
  ├─────┼────────────────────────────────────┼──────────────────────────────────────────────────────────────┤
  │ 2   │ Dentro do período (dtInicio/dtFim) │ "Cupom fora do período de validade"                          │
  ├─────┼────────────────────────────────────┼──────────────────────────────────────────────────────────────┤
  │ 3   │ Limite global não atingido         │ "Cupom atingiu o limite máximo de utilizações"               │
  ├─────┼────────────────────────────────────┼──────────────────────────────────────────────────────────────┤
  │ 4   │ Limite por org não atingido*       │ "Cupom já utilizado o máximo de vezes para esta organização" │
  ├─────┼────────────────────────────────────┼──────────────────────────────────────────────────────────────┤
  │ 5   │ Plano está na lista permitida      │ "Cupom não é válido para o plano selecionado"                │
  ├─────┼────────────────────────────────────┼──────────────────────────────────────────────────────────────┤
  │ 6   │ Segmento da org está na lista*     │ "Cupom não é válido para o segmento da sua organização"      │
  ├─────┼────────────────────────────────────┼──────────────────────────────────────────────────────────────┤
  │ 7   │ Org está na whitelist*             │ "Cupom não é válido para esta organização"                   │
  ├─────┼────────────────────────────────────┼──────────────────────────────────────────────────────────────┤
  │ 8   │ Ciclo de cobrança compatível       │ "Cupom não é válido para o ciclo de cobrança selecionado"    │
  └─────┴────────────────────────────────────┴──────────────────────────────────────────────────────────────┘

  * Checks 4, 6 e 7 só executam no endpoint autenticado (não no público).

  ---
  5. Cálculo do Desconto

  PERCENTUAL:  desconto = valorOriginal × valorDesconto / 100  (HALF_UP, 2 casas)
  VALOR_FIXO:  desconto = min(valorDesconto, valorOriginal)    (nunca fica negativo)
  valorFinal = max(valorOriginal - desconto, 0)

  ---
  6. Fluxo de Aplicação do Cupom

  Na escolha de plano (escolherPlano / reativar):

  1. Valida cupom com todas as 8 regras
  2. Cria subscription no gateway (Asaas) com valor com desconto
  3. Salva referência na Assinatura: cupom, valorDesconto, cupomCodigo
  4. Registra uso na cupom_utilizacao + incrementa totalUtilizado

  Após pagamento confirmado (webhook):

  PRIMEIRA_COBRANCA:
  1. Atualiza subscription no Asaas para valor cheio
  2. Remove cupom da Assinatura (cupom=null, valorDesconto=null, cupomCodigo=null)
  3. Próximas cobranças usam preço normal

  RECORRENTE com mesesRecorrencia:
  1. Conta total de usos desse cupom pela org
  2. Se totalUsos >= mesesRecorrencia:
    - Atualiza Asaas para valor cheio
    - Remove cupom da Assinatura
  3. Se não atingiu: mantém o desconto

  RECORRENTE com mesesRecorrencia = null:
  - Desconto permanente, nunca remove

  ---
  7. JSON Modelo Completo

  {
    "criar_cupom_percentual_primeira_cobranca": {
      "codigo": "WELCOME50",
      "descricao": "50% de desconto na primeira mensalidade para novos clientes",
      "tipoDesconto": "PERCENTUAL",
      "valorDesconto": 50.0,
      "dtInicio": "2026-04-01T00:00:00",
      "dtFim": "2026-06-30T23:59:59",
      "maxUtilizacoes": 100,
      "maxUtilizacoesPorOrg": 1,
      "planosPermitidos": ["basico", "premium", "plus"],
      "segmentosPermitidos": null,
      "organizacoesPermitidas": null,
      "cicloCobranca": null,
      "tipoAplicacao": "PRIMEIRA_COBRANCA",
      "mesesRecorrencia": null
    },

    "criar_cupom_valor_fixo_primeira_cobranca": {
      "codigo": "DESC30REAIS",
      "descricao": "R$30 de desconto na primeira cobrança",
      "tipoDesconto": "VALOR_FIXO",
      "valorDesconto": 30.00,
      "dtInicio": "2026-04-01T00:00:00",
      "dtFim": "2026-12-31T23:59:59",
      "maxUtilizacoes": 500,
      "maxUtilizacoesPorOrg": 1,
      "planosPermitidos": null,
      "segmentosPermitidos": null,
      "organizacoesPermitidas": null,
      "cicloCobranca": "MENSAL",
      "tipoAplicacao": "PRIMEIRA_COBRANCA",
      "mesesRecorrencia": null
    },

    "criar_cupom_recorrente_3_meses": {
      "codigo": "FIDELIDADE20",
      "descricao": "20% de desconto nos 3 primeiros meses",
      "tipoDesconto": "PERCENTUAL",
      "valorDesconto": 20.0,
      "dtInicio": "2026-04-01T00:00:00",
      "dtFim": null,
      "maxUtilizacoes": null,
      "maxUtilizacoesPorOrg": 1,
      "planosPermitidos": ["premium", "plus"],
      "segmentosPermitidos": null,
      "organizacoesPermitidas": null,
      "cicloCobranca": null,
      "tipoAplicacao": "RECORRENTE",
      "mesesRecorrencia": 3
    },

    "criar_cupom_recorrente_permanente": {
      "codigo": "PARCEIRO2026",
      "descricao": "15% de desconto permanente para parceiros",
      "tipoDesconto": "PERCENTUAL",
      "valorDesconto": 15.0,
      "dtInicio": null,
      "dtFim": null,
      "maxUtilizacoes": 10,
      "maxUtilizacoesPorOrg": 1,
      "planosPermitidos": null,
      "segmentosPermitidos": null,
      "organizacoesPermitidas": [1, 5, 10, 22],
      "cicloCobranca": null,
      "tipoAplicacao": "RECORRENTE",
      "mesesRecorrencia": null
    },

    "criar_cupom_segmento_especifico": {
      "codigo": "BARBER30",
      "descricao": "30% para barbearias no plano anual",
      "tipoDesconto": "PERCENTUAL",
      "valorDesconto": 30.0,
      "dtInicio": "2026-04-01T00:00:00",
      "dtFim": "2026-04-30T23:59:59",
      "maxUtilizacoes": 50,
      "maxUtilizacoesPorOrg": 1,
      "planosPermitidos": ["premium"],
      "segmentosPermitidos": ["Barbearia"],
      "organizacoesPermitidas": null,
      "cicloCobranca": "ANUAL",
      "tipoAplicacao": "PRIMEIRA_COBRANCA",
      "mesesRecorrencia": null
    },

    "criar_cupom_sem_limites": {
      "codigo": "ILIMITADO10",
      "descricao": "10% sem limite de uso ou validade",
      "tipoDesconto": "PERCENTUAL",
      "valorDesconto": 10.0,
      "dtInicio": null,
      "dtFim": null,
      "maxUtilizacoes": null,
      "maxUtilizacoesPorOrg": null,
      "planosPermitidos": null,
      "segmentosPermitidos": null,
      "organizacoesPermitidas": null,
      "cicloCobranca": null,
      "tipoAplicacao": "PRIMEIRA_COBRANCA",
      "mesesRecorrencia": null
    },

    "validar_cupom_request": {
      "codigoCupom": "WELCOME50",
      "planoCodigo": "premium",
      "cicloCobranca": "MENSAL"
    },

    "validar_cupom_response_sucesso": {
      "valido": true,
      "mensagem": "Cupom válido",
      "tipoDesconto": "PERCENTUAL",
      "tipoAplicacao": "PRIMEIRA_COBRANCA",
      "percentualDesconto": 50.0,
      "valorDesconto": 74.95,
      "valorOriginal": 149.90,
      "valorComDesconto": 74.95
    },

    "validar_cupom_response_erro": {
      "valido": false,
      "mensagem": "Cupom não é válido para o plano selecionado",
      "tipoDesconto": null,
      "tipoAplicacao": null,
      "percentualDesconto": null,
      "valorDesconto": null,
      "valorOriginal": null,
      "valorComDesconto": null
    },

    "response_cupom_admin": {
      "id": 1,
      "codigo": "WELCOME50",
      "descricao": "50% de desconto na primeira mensalidade",
      "tipoDesconto": "PERCENTUAL",
      "valorDesconto": 50.0,
      "dtInicio": "2026-04-01T00:00:00",
      "dtFim": "2026-06-30T23:59:59",
      "maxUtilizacoes": 100,
      "maxUtilizacoesPorOrg": 1,
      "totalUtilizado": 23,
      "planosPermitidos": ["basico", "premium", "plus"],
      "segmentosPermitidos": null,
      "organizacoesPermitidas": null,
      "cicloCobranca": null,
      "tipoAplicacao": "PRIMEIRA_COBRANCA",
      "ativo": true,
      "vigente": true,
      "dtCriacao": "2026-04-01T10:30:00",
      "dtAtualizacao": "2026-04-05T14:20:00"
    },

    "response_utilizacao": {
      "id": 45,
      "cupomId": 1,
      "cupomCodigo": "WELCOME50",
      "organizacaoId": 15,
      "assinaturaId": 12,
      "cobrancaId": null,
      "valorOriginal": 149.90,
      "valorDesconto": 74.95,
      "valorFinal": 74.95,
      "planoCodigo": "premium",
      "cicloCobranca": "MENSAL",
      "dtUtilizacao": "2026-04-10T15:42:00"
    },

    "update_cupom": {
      "descricao": "50% - Promoção estendida até julho",
      "dtFim": "2026-07-31T23:59:59",
      "maxUtilizacoes": 200
    }
  }

  ---
  8. SQL das Tabelas

  -- Cupom de desconto
  CREATE TABLE cupom_desconto (
      id BIGSERIAL PRIMARY KEY,
      codigo VARCHAR(50) NOT NULL UNIQUE,
      descricao VARCHAR(255),
      tipo_desconto VARCHAR(20) NOT NULL,
      valor_desconto NUMERIC(10,2) NOT NULL,
      dt_inicio TIMESTAMP,
      dt_fim TIMESTAMP,
      max_utilizacoes INTEGER,
      max_utilizacoes_por_org INTEGER,
      total_utilizado INTEGER NOT NULL DEFAULT 0,
      planos_permitidos JSONB,
      segmentos_permitidos JSONB,
      organizacoes_permitidas JSONB,
      ciclo_cobranca VARCHAR(10),
      tipo_aplicacao VARCHAR(20) NOT NULL DEFAULT 'PRIMEIRA_COBRANCA',
      meses_recorrencia INTEGER,
      ativo BOOLEAN NOT NULL DEFAULT TRUE,
      dt_criacao TIMESTAMP NOT NULL DEFAULT NOW(),
      dt_atualizacao TIMESTAMP,
      user_criacao BIGINT,
      user_atualizacao BIGINT
  );

  CREATE INDEX idx_cupom_desconto_codigo ON cupom_desconto (codigo);
  CREATE INDEX idx_cupom_desconto_ativo ON cupom_desconto (ativo);

  -- Log de utilizações
  CREATE TABLE cupom_utilizacao (
      id BIGSERIAL PRIMARY KEY,
      cupom_id BIGINT NOT NULL REFERENCES cupom_desconto(id),
      organizacao_id BIGINT NOT NULL,
      assinatura_id BIGINT,
      cobranca_id BIGINT,
      valor_original NUMERIC(10,2) NOT NULL,
      valor_desconto NUMERIC(10,2) NOT NULL,
      valor_final NUMERIC(10,2) NOT NULL,
      plano_codigo VARCHAR(50),
      ciclo_cobranca VARCHAR(10),
      dt_utilizacao TIMESTAMP NOT NULL DEFAULT NOW()
  );

  CREATE INDEX idx_cupom_utilizacao_cupom ON cupom_utilizacao (cupom_id);
  CREATE INDEX idx_cupom_utilizacao_org ON cupom_utilizacao (organizacao_id);

  ---
  Esse é o mapa completo do sistema de cupons. Cobre: entidades, enums, DTOs (create/update/response/validação), endpoints (admin CRUD + validação pública e autenticada), todas as 8 regras
   de validação, cálculo de desconto, fluxo de aplicação com gateway de pagamento (PRIMEIRA_COBRANCA vs RECORRENTE), rastreamento de uso, e os JSONs prontos para cada cenário.




