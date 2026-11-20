BEGIN;
ALTER TABLE conta_azul_connections ADD COLUMN IF NOT EXISTS empresa_externa_id varchar(100);
ALTER TABLE conta_azul_connections ADD COLUMN IF NOT EXISTS empresa_documento varchar(30);
ALTER TABLE conta_azul_connections ADD COLUMN IF NOT EXISTS empresa_razao_social varchar(250);
ALTER TABLE conta_azul_connections ADD COLUMN IF NOT EXISTS empresa_verificada_em timestamptz;
ALTER TABLE conta_azul_connections ADD COLUMN IF NOT EXISTS empresa_confirmada_em timestamptz;
ALTER TABLE conta_azul_connections ADD COLUMN IF NOT EXISTS empresa_confirmada_por bigint;
ALTER TABLE conta_azul_auto_execucoes ADD COLUMN IF NOT EXISTS conferencia_status varchar(40);
ALTER TABLE conta_azul_auto_execucoes ADD COLUMN IF NOT EXISTS conferencia_json text;
ALTER TABLE conta_azul_auto_execucoes ADD COLUMN IF NOT EXISTS conferido_em timestamptz;
CREATE INDEX IF NOT EXISTS idx_ca_empresa_externa ON conta_azul_connections(empresa_externa_id);
CREATE INDEX IF NOT EXISTS idx_ca_ultima_conclusao ON conta_azul_auto_execucoes(cliente_id,concluido_em DESC) WHERE status IN ('CONCLUIDA','CONCLUIDA_COM_PENDENCIAS');
CREATE INDEX IF NOT EXISTS idx_ca_conferencia_vencimento ON lancamentos_financeiros(cliente_id,data_vencimento) WHERE origem='CONTA_AZUL_API';
-- Uma identificação antiga nunca é confirmada por suposição. Reative cada empresa após conferi-la na interface.
UPDATE conta_azul_auto_config a SET habilitada=false,proxima_em=null,dono=null,reserva_ate=null
WHERE EXISTS (SELECT 1 FROM conta_azul_connections c WHERE c.cliente_id=a.cliente_id AND c.empresa_confirmada_em IS NULL);
COMMIT;