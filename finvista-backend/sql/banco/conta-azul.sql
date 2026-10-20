CREATE TABLE IF NOT EXISTS conta_azul_connections (
 cliente_id BIGINT PRIMARY KEY REFERENCES clientes(id),
 versao BIGINT,
 access_cipher TEXT NOT NULL,
 refresh_cipher TEXT NOT NULL,
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);