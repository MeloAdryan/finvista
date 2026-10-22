-- Aplicar na producao antes de publicar esta versao. Nao ativa nem amplia autorizacoes existentes.
BEGIN;
ALTER TABLE conta_azul_auto_config ADD COLUMN IF NOT EXISTS periodo_movel BOOLEAN DEFAULT FALSE;
COMMIT;
