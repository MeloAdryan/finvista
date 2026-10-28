-- Optional explicit migration if schema is managed outside Hibernate update.
BEGIN;
ALTER TABLE conta_azul_auto_execucoes ADD COLUMN IF NOT EXISTS movimentos_escopo_inicio DATE;
ALTER TABLE conta_azul_auto_execucoes ADD COLUMN IF NOT EXISTS movimentos_escopo_fim DATE;
COMMIT;
