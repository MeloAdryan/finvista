BEGIN;

CREATE UNIQUE INDEX IF NOT EXISTS uk_lancamento_conta_azul_api
    ON lancamentos_financeiros (cliente_id, lower(btrim(documento_referencia)))
    WHERE origem = 'CONTA_AZUL_API' AND documento_referencia IS NOT NULL;

CREATE TABLE IF NOT EXISTS conta_azul_api_operacoes (
    id UUID PRIMARY KEY,
    cliente_id BIGINT NOT NULL REFERENCES clientes(id) ON DELETE CASCADE,
    usuario_id BIGINT NOT NULL,
    parcela_id VARCHAR(36) NOT NULL,
    acao VARCHAR(24) NOT NULL,
    lancamento_id BIGINT NOT NULL,
    concluido_em TIMESTAMP WITH TIME ZONE NOT NULL,
    antes TEXT,
    depois TEXT NOT NULL,
    origem_snapshot TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_conta_azul_api_operacoes_cliente
    ON conta_azul_api_operacoes (cliente_id, concluido_em);

COMMIT;