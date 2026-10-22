
CREATE INDEX IF NOT EXISTS idx_fv_api_snapshot_atual
    ON conta_azul_api_operacoes (cliente_id, lancamento_id, concluido_em DESC);
CREATE INDEX IF NOT EXISTS idx_fv_auto_historico
    ON conta_azul_auto_execucoes (cliente_id, criado_em DESC);
CREATE INDEX IF NOT EXISTS idx_fv_auto_revisao
    ON conta_azul_auto_itens (execucao_id, cliente_id, status, id);
