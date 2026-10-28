-- Optional explicit migration if schema changes are managed outside Hibernate update.
-- Preserves existing records; null keeps legacy cursors compatible.
ALTER TABLE conta_azul_auto_execucoes ADD COLUMN IF NOT EXISTS movimento_inicio DATE;
