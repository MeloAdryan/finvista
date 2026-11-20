package com.finvista.integration.contaazul;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import java.nio.file.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

class ContaAzulOperationPostgresIT {
    @Test void migrationIsIdempotentAndTenantDataRemainSeparate() throws Exception {
        try(var pg=new PostgreSQLContainer<>("postgres:18-alpine").withEnv("PGDATA","/var/lib/postgresql/data")) {
            pg.start();
            try(var c=DriverManager.getConnection(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());var s=c.createStatement()) {
                s.execute("create table conta_azul_connections(cliente_id bigint primary key)");
                s.execute("create table conta_azul_auto_execucoes(id uuid primary key,cliente_id bigint,status varchar(40),concluido_em timestamptz)");
                s.execute("create table conta_azul_auto_config(cliente_id bigint primary key,habilitada boolean,proxima_em timestamptz,dono uuid,reserva_ate timestamptz)");
                s.execute("create table lancamentos_financeiros(id bigint primary key,cliente_id bigint,origem varchar(100),data_vencimento date)");
                s.execute("create table conta_azul_auto_itens(id uuid primary key,cliente_id bigint,parcela_id varchar(100),status varchar(40),concluido_em timestamptz)");
                String migration = Files.readString(Path.of("sql", "MIGRACAO-SEGURANCA-OPERACAO.sql"));
                s.execute(migration);s.execute(migration);
                s.execute("insert into conta_azul_connections(cliente_id,empresa_externa_id,empresa_documento) values (2,'same','05206246000138'),(7,'same','05206246000138')");
                s.execute("update conta_azul_connections set empresa_confirmada_em=now(),empresa_confirmada_por=1 where cliente_id=7");
                try(var r=s.executeQuery("select count(*) from conta_azul_connections where cliente_id=2 and empresa_confirmada_em is null")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
                s.execute("insert into conta_azul_auto_execucoes(id,cliente_id,conferencia_json) values ('00000000-0000-0000-0000-000000000001',7,'[]')");
                try(var r=s.executeQuery("select jsonb_array_length(conferencia_json::jsonb) from conta_azul_auto_execucoes where cliente_id=7")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
                s.execute("""
                    insert into conta_azul_auto_itens values
                    ('00000000-0000-0000-0000-000000000011',7,'a','CONFERIR','2026-11-18T12:00:00Z'),
                    ('00000000-0000-0000-0000-000000000012',7,'a','SEM_ALTERACAO','2026-11-19T12:00:00Z'),
                    ('00000000-0000-0000-0000-000000000013',7,'b','CONFERIR','2026-11-19T12:00:00Z'),
                    ('00000000-0000-0000-0000-000000000014',2,'b','CONFERIR','2026-11-19T12:00:00Z')
                    """);
                String pendingSql=com.finvista.repository.ContaAzulAutoItemRepository.class
                    .getMethod("countPendenciasAtuais",Long.class)
                    .getAnnotation(org.springframework.data.jpa.repository.Query.class).value().replace(":cliente","7");
                try(var r=s.executeQuery(pendingSql)){assertTrue(r.next());assertEquals(1,r.getLong(1));}
            }
        }
    }
}
