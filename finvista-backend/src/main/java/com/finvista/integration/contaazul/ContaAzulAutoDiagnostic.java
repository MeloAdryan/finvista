package com.finvista.integration.contaazul;

import java.util.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientResponseException;


final class ContaAzulAutoDiagnostic {
    private ContaAzulAutoDiagnostic() {}
    private static final List<String> CAMPOS = List.of(
            "data_vencimento_de", "data_vencimento_ate",
            "data_competencia_de", "data_competencia_ate",
            "data_alteracao_de", "data_alteracao_ate",
            "data_criacao_de", "data_criacao_ate",
            "pagina", "tamanho_pagina");

    private static final class FalhaHttp extends ResponseStatusException {
        private final String diagnostico;
        private final java.time.Instant retryAfter;

        private FalhaHttp(org.springframework.http.HttpStatusCode status, String diagnostico, java.time.Instant retryAfter) {
            super(status, "Falha HTTP na origem.");
            this.diagnostico = diagnostico;
            this.retryAfter = retryAfter;
        }
    }

    static RuntimeException causaSegura(Exception erro) {
        return causaSegura(erro, "");
    }

    static RuntimeException causaSegura(Exception erro, String consulta) {
        if (erro instanceof RestClientResponseException http) {
            return new FalhaHttp(http.getStatusCode(), consulta + validacaoSegura(http),
                    ContaAzulHttpTransport.retryAfter(http.getResponseHeaders() == null ? null : http.getResponseHeaders().getFirst("Retry-After"), java.time.Instant.now()));
        }
        if (erro instanceof org.springframework.web.client.ResourceAccessException) {
            return new org.springframework.web.client.ResourceAccessException("Falha de comunicação com a origem.");
        }
        return new IllegalArgumentException("Resposta da origem ausente ou inválida.");
    }

    private static String validacaoSegura(RestClientResponseException erro) {

        String corpo = erro.getResponseBodyAsString();
        if (corpo.length() > 16384) return " validacao=RESPOSTA_EXTENSA";
        String texto = java.text.Normalizer.normalize(corpo, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        var campos = CAMPOS.stream().filter(campo ->
                java.util.regex.Pattern.compile("(?<![a-z0-9_])" + campo + "(?![a-z0-9_])")
                        .matcher(texto).find()).toList();
        String categoria = "NAO_IDENTIFICADA";
        if (texto.contains("intervalo") || texto.contains("periodo")) categoria = "PERIODO";
        else if (texto.contains("data_") || texto.contains("date")) categoria = "DATA";
        else if (texto.contains("pagina") || texto.contains("page")) categoria = "PAGINACAO";
        var limites = new LinkedHashSet<Integer>();
        var matcher = java.util.regex.Pattern.compile("(?<![0-9])([0-9]{1,4})\\s*(?:dias?|days?)(?![a-z])").matcher(texto);
        while (matcher.find() && limites.size() < 4) {
            int dias = Integer.parseInt(matcher.group(1));
            if (dias > 0) limites.add(dias);
        }
        return " validacao=" + categoria + " campos=" + campos + " limitesDias=" + limites;
    }

    static boolean limiteDePeriodo365(Throwable erro) {
        var vistos = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable atual = erro; atual != null && vistos.size() < 8 && vistos.add(atual); atual = atual.getCause()) {
            if (atual instanceof FalhaHttp http && http.getStatusCode().value() == 400
                    && http.diagnostico.contains("validacao=PERIODO")
                    && http.diagnostico.contains("limitesDias=[365]")) return true;
        }
        return false;
    }

    static String resumir(Throwable erro) {
        var vistos = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var partes = new ArrayList<String>();
        for (Throwable t = erro; t != null && partes.size() < 8 && vistos.add(t); t = t.getCause()) {
            String parte = t.getClass().getName();
            if (t instanceof ResponseStatusException http) parte += " http=" + http.getStatusCode().value();
            else if (t instanceof RestClientResponseException http) parte += " http=" + http.getStatusCode().value();
            if (t instanceof FalhaHttp origem) parte += origem.diagnostico;
            if (t instanceof java.sql.SQLException sql && sql.getSQLState() != null
                    && sql.getSQLState().matches("[A-Z0-9]{5}")) parte += " sqlState=" + sql.getSQLState();
            int linhas = 0;
            for (var frame : t.getStackTrace()) {
                if (frame.getClassName().startsWith("com.finvista.") && linhas++ < 4) {
                    parte += " em=" + frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber();
                }
            }
            partes.add(parte);
        }
        return String.join(" <- ", partes);
    }

    static boolean http(Throwable erro, int status) {
        var vistos = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable t = erro; t != null && vistos.size() < 8 && vistos.add(t); t = t.getCause()) {
            if (t instanceof ResponseStatusException e && e.getStatusCode().value() == status) return true;
            if (t instanceof RestClientResponseException e && e.getStatusCode().value() == status) return true;
        }
        return false;
    }


    static java.time.Instant retryAfter(Throwable erro) {
        var vistos = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable t = erro; t != null && vistos.size() < 8 && vistos.add(t); t = t.getCause()) {
            if (t instanceof FalhaHttp e && e.retryAfter != null) return e.retryAfter;
            if (t instanceof RestClientResponseException e && e.getResponseHeaders() != null) {
                var at = ContaAzulHttpTransport.retryAfter(e.getResponseHeaders().getFirst("Retry-After"), java.time.Instant.now());
                if (at != null) return at;
            }
        }
        return null;
    }

}
