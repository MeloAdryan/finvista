BEGIN;

CREATE TABLE IF NOT EXISTS conta_azul_auto_config (
    cliente_id BIGINT PRIMARY KEY REFERENCES clientes(id) ON DELETE CASCADE,
    habilitada BOOLEAN NOT NULL DEFAULT FALSE,
    incluir_novas BOOLEAN NOT NULL DEFAULT FALSE,
    inicio DATE NOT NULL,
    fim DATE NOT NULL,
    intervalo_minutos INTEGER NOT NULL,
    autorizado_por BIGINT NOT NULL,
    revisao UUID NOT NULL,
    alterado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    proxima_em TIMESTAMP WITH TIME ZONE,
    execucao_id UUID,
    dono UUID,
    reserva_ate TIMESTAMP WITH TIME ZONE,
    falhas INTEGER NOT NULL DEFAULT 0,
    versao BIGINT,
    CONSTRAINT ck_conta_azul_auto_periodo CHECK (fim>=inicio AND fim-inicio<=365),
    CONSTRAINT ck_conta_azul_auto_intervalo CHECK (intervalo_minutos IN (30,60,360,1440))
);

CREATE TABLE IF NOT EXISTS conta_azul_auto_execucoes (
    id UUID PRIMARY KEY,
    cliente_id BIGINT NOT NULL REFERENCES clientes(id) ON DELETE CASCADE,
    autorizado_por BIGINT NOT NULL,
    revisao UUID NOT NULL,
    inicio DATE NOT NULL,
    fim DATE NOT NULL,
    incluir_novas BOOLEAN NOT NULL,
    status VARCHAR(255) NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    concluido_em TIMESTAMP WITH TIME ZONE,
    etapa INTEGER NOT NULL DEFAULT 0,
    pagina INTEGER NOT NULL DEFAULT 1,
    total_esperado INTEGER,
    listagem_mudou BOOLEAN NOT NULL DEFAULT FALSE,
    mensagem VARCHAR(500),
    incluidas INTEGER NOT NULL DEFAULT 0,
    atualizadas INTEGER NOT NULL DEFAULT 0,
    mantidas INTEGER NOT NULL DEFAULT 0,
    pendentes INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_conta_azul_auto_exec_cliente UNIQUE(id,cliente_id)
);

CREATE TABLE IF NOT EXISTS conta_azul_auto_itens (
    id UUID PRIMARY KEY,
    execucao_id UUID NOT NULL,
    cliente_id BIGINT NOT NULL,
    parcela_id VARCHAR(255) NOT NULL,
    tipo VARCHAR(255) NOT NULL,
    descricao VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    listada BOOLEAN NOT NULL DEFAULT FALSE,
    mensagem VARCHAR(500),
    lancamento_id BIGINT,
    concluido_em TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_conta_azul_auto_item_execucao FOREIGN KEY(execucao_id,cliente_id)
        REFERENCES conta_azul_auto_execucoes(id,cliente_id) ON DELETE CASCADE,
    CONSTRAINT uk_conta_azul_auto_item UNIQUE(execucao_id,parcela_id)
);

CREATE INDEX IF NOT EXISTS idx_conta_azul_auto_agenda ON conta_azul_auto_config(proxima_em) WHERE habilitada;
CREATE INDEX IF NOT EXISTS idx_conta_azul_auto_historico ON conta_azul_auto_execucoes(cliente_id,criado_em DESC);
CREATE INDEX IF NOT EXISTS idx_conta_azul_auto_fila ON conta_azul_auto_itens(execucao_id,status,id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_conta_azul_auto_exec_cliente ON conta_azul_auto_execucoes(id,cliente_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_conta_azul_auto_item ON conta_azul_auto_itens(execucao_id,parcela_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='conta_azul_auto_config'::regclass AND conname='ck_conta_azul_auto_periodo') THEN
        ALTER TABLE conta_azul_auto_config ADD CONSTRAINT ck_conta_azul_auto_periodo CHECK(fim>=inicio AND fim-inicio<=365);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='conta_azul_auto_config'::regclass AND conname='ck_conta_azul_auto_intervalo') THEN
        ALTER TABLE conta_azul_auto_config ADD CONSTRAINT ck_conta_azul_auto_intervalo CHECK(intervalo_minutos IN(30,60,360,1440));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='conta_azul_auto_itens'::regclass AND conname='fk_conta_azul_auto_item_execucao') THEN
        ALTER TABLE conta_azul_auto_itens ADD CONSTRAINT fk_conta_azul_auto_item_execucao FOREIGN KEY(execucao_id,cliente_id) REFERENCES conta_azul_auto_execucoes(id,cliente_id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='conta_azul_auto_config'::regclass AND contype='f' AND confrelid='clientes'::regclass) THEN
        ALTER TABLE conta_azul_auto_config ADD CONSTRAINT fk_conta_azul_auto_config_cliente FOREIGN KEY(cliente_id) REFERENCES clientes(id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='conta_azul_auto_execucoes'::regclass AND contype='f' AND confrelid='clientes'::regclass) THEN
        ALTER TABLE conta_azul_auto_execucoes ADD CONSTRAINT fk_conta_azul_auto_run_cliente FOREIGN KEY(cliente_id) REFERENCES clientes(id) ON DELETE CASCADE;
    END IF;
END $$;

COMMIT;
