package com.finvista.integration.contaazul;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ContaAzulAutoDiagnosticTest {

    @Test
    void omiteMensagensEConteudoDasCausas() {
        var e = new IllegalStateException("password=SECRET", new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer SECRET"));
        var s = ContaAzulAutoDiagnostic.resumir(e);
        assertTrue(s.contains("http=401"));
        assertTrue(s.contains("IllegalStateException"));
        assertFalse(s.contains("SECRET"));
        assertFalse(s.contains("Bearer"));
    }

    @Test
    void informaSqlStateSemExporSql() {
        var s = ContaAzulAutoDiagnostic.resumir(new java.sql.SQLException("select 'SECRET'", "23505"));
        assertTrue(s.contains("sqlState=23505"));
        assertFalse(s.contains("SECRET"));
    }

    @Test
    void limitaCadeiaCiclica() {
        var a = new Exception("SECRET");
        var b = new Exception("SECRET", a);
        a.initCause(b);
        assertTrue(ContaAzulAutoDiagnostic.resumir(a).length() < 3000);
    }

    @Test
    void ignoraSqlStateInvalido() {
        assertFalse(ContaAzulAutoDiagnostic.resumir(new java.sql.SQLException("SECRET", "SECRET")).contains("SECRET"));
    }

    @Test
    void preservaFalhaDeTransporteSemEnderecoOuCredenciais() {
        var erro = new org.springframework.web.client.ResourceAccessException("SECRET https://private.example");
        var seguro = ContaAzulAutoDiagnostic.causaSegura(erro);
        assertInstanceOf(org.springframework.web.client.ResourceAccessException.class, seguro);
        assertFalse(seguro.toString().contains("SECRET"));
        assertFalse(seguro.toString().contains("private.example"));
        assertNull(seguro.getCause());
    }

    @Test
    void preservaFalhaDeValidacaoSemConteudoFinanceiro() {
        var seguro = ContaAzulAutoDiagnostic.causaSegura(new IllegalArgumentException("SECRET"));
        assertInstanceOf(IllegalArgumentException.class, seguro);
        assertFalse(seguro.toString().contains("SECRET"));
    }

    @Test
    void extraiValidacaoSemCopiarCorpoOuValoresSensiveis() {
        var origem = org.springframework.web.client.HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "SECRET", org.springframework.http.HttpHeaders.EMPTY,
                ("{\"error\":\"O intervalo de data_alteracao_de e data_alteracao_ate "
                        + "nao pode passar de 365 dias. SECRET CNPJ 12345678901234\"}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.StandardCharsets.UTF_8);
        var resumo = ContaAzulAutoDiagnostic.resumir(ContaAzulAutoDiagnostic.causaSegura(origem));
        assertTrue(resumo.contains("http=400"));
        assertTrue(resumo.contains("validacao=PERIODO"));
        assertTrue(resumo.contains("data_alteracao_de"));
        assertTrue(resumo.contains("limitesDias=[365]"));
        assertFalse(resumo.contains("SECRET"));
        assertFalse(resumo.contains("12345678901234"));
    }

    @Test
    void corpoExtensoTemDiagnosticoLimitado() {
        var origem = org.springframework.web.client.HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "SECRET", org.springframework.http.HttpHeaders.EMPTY,
                "SECRET".repeat(4000).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.StandardCharsets.UTF_8);
        var resumo = ContaAzulAutoDiagnostic.resumir(ContaAzulAutoDiagnostic.causaSegura(origem));
        assertTrue(resumo.contains("RESPOSTA_EXTENSA"));
        assertFalse(resumo.contains("SECRET"));
    }
}
