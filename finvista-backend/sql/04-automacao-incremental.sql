-- Optional migration when schema is managed outside Hibernate ddl-auto=update.
-- No financial records or active execution cursors are updated.
BEGIN;
ALTER TABLE conta_azul_auto_config ADD COLUMN IF NOT EXISTS alteracoes_confirmadas_ate TIMESTAMP WITH TIME ZONE;
ALTER TABLE conta_azul_auto_config ADD COLUMN IF NOT EXISTS historico_revisado_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE conta_azul_auto_execucoes ADD COLUMN IF NOT EXISTS incremental BOOLEAN;
ALTER TABLE conta_azul_auto_execucoes ADD COLUMN IF NOT EXISTS alteracoes_desde TIMESTAMP WITH TIME ZONE;
ALTER TABLE conta_azul_auto_execucoes ADD COLUMN IF NOT EXISTS alteracoes_ate TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_ca_auto_fila ON conta_azul_auto_itens (execucao_id,status,id);
COMMIT;
