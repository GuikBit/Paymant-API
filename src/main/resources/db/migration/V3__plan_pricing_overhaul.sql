-- =============================================================
-- V3: Reestruturacao de precos dos planos (Plan Pricing Overhaul)
-- Adiciona suporte a precos mensal/anual, promocoes, codigo slug,
-- ciclo na assinatura e ciclo na mudanca de plano.
-- =============================================================

-- =============================================================
-- 1. Novas colunas na tabela plans
--    Adiciona codigo (slug), precos mensal/anual, desconto anual
--    e campos de promocao (mensal e anual)
-- =============================================================

ALTER TABLE plans ADD COLUMN codigo VARCHAR(50);
ALTER TABLE plans ADD COLUMN preco_mensal NUMERIC(12,2);
ALTER TABLE plans ADD COLUMN preco_anual NUMERIC(12,2);
ALTER TABLE plans ADD COLUMN desconto_percentual_anual NUMERIC(5,2);

-- Campos de promocao mensal
ALTER TABLE plans ADD COLUMN promo_mensal_ativa BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE plans ADD COLUMN promo_mensal_preco NUMERIC(12,2);
ALTER TABLE plans ADD COLUMN promo_mensal_texto VARCHAR(100);
ALTER TABLE plans ADD COLUMN promo_mensal_inicio TIMESTAMP;
ALTER TABLE plans ADD COLUMN promo_mensal_fim TIMESTAMP;

-- Campos de promocao anual
ALTER TABLE plans ADD COLUMN promo_anual_ativa BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE plans ADD COLUMN promo_anual_preco NUMERIC(12,2);
ALTER TABLE plans ADD COLUMN promo_anual_texto VARCHAR(100);
ALTER TABLE plans ADD COLUMN promo_anual_inicio TIMESTAMP;
ALTER TABLE plans ADD COLUMN promo_anual_fim TIMESTAMP;

-- =============================================================
-- 2. Novas colunas na tabela subscriptions
--    Adiciona ciclo e preco efetivo da assinatura
-- =============================================================

ALTER TABLE subscriptions ADD COLUMN cycle VARCHAR(20);
ALTER TABLE subscriptions ADD COLUMN effective_price NUMERIC(12,2);

-- =============================================================
-- 3. Novas colunas na tabela subscription_plan_changes
--    Registra ciclo anterior e ciclo solicitado na mudanca de plano
-- =============================================================

ALTER TABLE subscription_plan_changes ADD COLUMN previous_cycle VARCHAR(20);
ALTER TABLE subscription_plan_changes ADD COLUMN requested_cycle VARCHAR(20);

-- =============================================================
-- 4. Migracao de dados dos planos existentes
--    Preenche preco_mensal, preco_anual e codigo a partir dos
--    dados atuais (value, cycle, name)
-- =============================================================

-- 4a. preco_mensal recebe o valor atual (value) para todos os planos
UPDATE plans SET preco_mensal = value;

-- 4b. preco_anual: para planos YEARLY usa o proprio value,
--     para os demais (MONTHLY, SEMIANNUALLY, etc.) calcula value * 12
UPDATE plans SET preco_anual = CASE
    WHEN cycle = 'YEARLY' THEN value
    ELSE value * 12
END;

-- 4c. Gerar codigo (slug) a partir do nome do plano
--     Converte acentos para ASCII, minusculo, substitui espacos por hifen,
--     remove caracteres especiais, e garante fallback para 'plano-{id}'

-- Habilitar extensao unaccent (necessaria para remover acentos)
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Gerar slug: unaccent -> lower -> espacos para hifen -> remover invalidos -> trim hifens
UPDATE plans SET codigo =
    TRIM(BOTH '-' FROM
        REGEXP_REPLACE(
            REGEXP_REPLACE(
                LOWER(unaccent(COALESCE(name, ''))),
                '[^a-z0-9]+', '-', 'g'
            ),
            '-{2,}', '-', 'g'
        )
    );

-- Fallback: se codigo ficou vazio (name era null ou so caracteres especiais), usar 'plano-{id}'
UPDATE plans SET codigo = 'plano-' || id WHERE codigo IS NULL OR codigo = '';

-- 4d. Tratar codigos duplicados dentro da mesma empresa (company_id)
--     Adiciona sufixo -N para duplicatas, mantendo o primeiro sem sufixo
DO $$
DECLARE
    rec RECORD;
    seq INT;
BEGIN
    FOR rec IN
        SELECT id, company_id, codigo,
               ROW_NUMBER() OVER (PARTITION BY company_id, codigo ORDER BY id) AS rn
        FROM plans
    LOOP
        IF rec.rn > 1 THEN
            seq := rec.rn - 1;
            UPDATE plans SET codigo = codigo || '-' || seq WHERE id = rec.id;
        END IF;
    END LOOP;
END;
$$;

-- =============================================================
-- 5. Migracao de dados das assinaturas existentes
--    Copia o ciclo e o preco do plano associado
-- =============================================================

-- 5a. Preenche cycle a partir do plano vinculado
UPDATE subscriptions
SET cycle = p.cycle
FROM plans p
WHERE subscriptions.plan_id = p.id;

-- 5b. Preenche effective_price com o valor (value) do plano vinculado
UPDATE subscriptions
SET effective_price = p.value
FROM plans p
WHERE subscriptions.plan_id = p.id;

-- =============================================================
-- 6. Aplicar constraints NOT NULL apos a migracao de dados
--    Garante integridade para novos registros
-- =============================================================

ALTER TABLE plans ALTER COLUMN codigo SET NOT NULL;
ALTER TABLE plans ALTER COLUMN preco_mensal SET NOT NULL;
ALTER TABLE subscriptions ALTER COLUMN cycle SET NOT NULL;
ALTER TABLE subscriptions ALTER COLUMN effective_price SET NOT NULL;

-- =============================================================
-- 7. Indice unico parcial para codigo do plano
--    Garante unicidade de codigo por empresa, ignorando planos
--    que foram soft-deleted
-- =============================================================

CREATE UNIQUE INDEX idx_plans_company_codigo
    ON plans(company_id, codigo)
    WHERE deleted_at IS NULL;

-- =============================================================
-- 8. Remover colunas antigas da tabela plans
--    value e cycle agora sao substituidos por preco_mensal,
--    preco_anual e subscriptions.cycle
-- =============================================================

ALTER TABLE plans DROP COLUMN value;
ALTER TABLE plans DROP COLUMN cycle;

-- =============================================================
-- 9. Converter ciclos QUARTERLY para SEMIANNUALLY nas assinaturas
--    QUARTERLY nao e mais suportado no novo modelo de precos
-- =============================================================

UPDATE subscriptions SET cycle = 'SEMIANNUALLY' WHERE cycle = 'QUARTERLY';

-- =============================================================
-- 10. Nota sobre RLS (Row-Level Security)
--     As politicas de RLS existentes em plans, subscriptions e
--     subscription_plan_changes continuam funcionando normalmente.
--     Apenas adicionamos/removemos colunas — a coluna company_id
--     e as politicas baseadas nela nao foram alteradas.
--     Nenhuma nova tabela foi criada, portanto nenhuma nova
--     politica de RLS e necessaria.
-- =============================================================
