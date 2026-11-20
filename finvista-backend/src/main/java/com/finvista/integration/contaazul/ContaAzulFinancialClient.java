package com.finvista.integration.contaazul;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ContaAzulFinancialClient {

    public static final String BASE = "https://api-v2.contaazul.com";
    public static final int TAMANHO = 100;

    public enum Tipo {
        RECEITA("contas-a-receber"), DESPESA("contas-a-pagar");
        final String segmento;

        Tipo(String segmento) {
            this.segmento = segmento;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Classificacao(String id, String nome) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Parcela(String id, String descricao,
            @JsonProperty("data_vencimento") LocalDate vencimento,
            @JsonProperty("data_competencia") LocalDate competencia,
            String status, @JsonProperty("status_traduzido") String statusTraduzido,
            BigDecimal total, BigDecimal pago, @JsonProperty("nao_pago") BigDecimal pendente,
            List<Classificacao> categorias, @JsonProperty("centros_custo") List<Classificacao> centros,
            @JsonProperty("data_alteracao") String alteradoEm) {
        public Parcela(String id, String descricao, LocalDate vencimento, LocalDate competencia,
                String status, String statusTraduzido, BigDecimal total, BigDecimal pago, BigDecimal pendente,
                List<Classificacao> categorias, List<Classificacao> centros) {
            this(id, descricao, vencimento, competencia, status, statusTraduzido, total, pago, pendente, categorias, centros, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagina(@JsonProperty("itens_totais") Integer totalItens, List<Parcela> itens) {}
    private final RestClient http;

    @Autowired
    public ContaAzulFinancialClient(ContaAzulHttpTransport transport) { this(transport.client()); }

    public ContaAzulFinancialClient() { this(ContaAzulHttpTransport.shared()); }

    public ContaAzulFinancialClient(RestClient http) {
        this.http = http;
    }



    public Pagina consultar(String acesso, Tipo tipo, LocalDate inicio, LocalDate fim, int pagina) {
        return consultar(acesso, tipo, inicio, fim, pagina, false, inicio, fim, null, null);
    }

    public Pagina consultarPorCompetencia(String acesso, Tipo tipo, LocalDate inicio, LocalDate fim, LocalDate vencimentoInicio, LocalDate vencimentoFim, int pagina) {
        return consultar(acesso, tipo, inicio, fim, pagina, true, vencimentoInicio, vencimentoFim, null, null);
    }

    public Pagina consultarAlteradas(String acesso, Tipo tipo, LocalDate inicio, LocalDate fim, int pagina,
            java.time.Instant desde, java.time.Instant ate) {
        if (desde == null || ate == null || ate.isBefore(desde)) throw new IllegalArgumentException("Periodo de alteracoes invalido.");
        return consultar(acesso, tipo, inicio, fim, pagina, false, inicio, fim, desde, ate);
    }

    private Pagina consultar(String acesso, Tipo tipo, LocalDate inicio, LocalDate fim, int pagina, boolean competencia, LocalDate vencimentoInicio, LocalDate vencimentoFim,
            java.time.Instant desde, java.time.Instant ate) {
        String consulta = " consulta=" + tipo.name() + " pagina=" + pagina + " tamanho=" + TAMANHO
                + " vencimentoDe=" + vencimentoInicio + " vencimentoAte=" + vencimentoFim
                + " competencia=" + competencia + " competenciaDe=" + (competencia ? inicio : null)
                + " competenciaAte=" + (competencia ? fim : null) + " alteracaoDe=" + desde + " alteracaoAte=" + ate;
        try {
            String endpoint = BASE + "/v1/financeiro/eventos-financeiros/" + tipo.segmento + "/buscar";
            var builder = org.springframework.web.util.UriComponentsBuilder.fromUriString(endpoint)
                    .queryParam("pagina", pagina).queryParam("tamanho_pagina", TAMANHO)
                    .queryParam("data_vencimento_de", vencimentoInicio).queryParam("data_vencimento_ate", vencimentoFim);
            if (competencia) {
                builder.queryParam("data_competencia_de", inicio).queryParam("data_competencia_ate", fim);
            }
            if (desde != null) {
                var zone = java.time.ZoneId.of("America/Sao_Paulo");
                var format = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
                builder.queryParam("data_alteracao_de", format.format(desde.atZone(zone)))
                        .queryParam("data_alteracao_ate", format.format(ate.atZone(zone)));
            }
            var uri = builder.build().toUri();
            Pagina result = http.get().uri(uri).headers(h -> h.setBearerAuth(acesso)).retrieve().body(Pagina.class);
            validar(result, inicio, fim, competencia);
            return result;
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            String mensagem = code == 401 || code == 403
                    ? "A Conta Azul recusou o acesso. Renove ou autorize a conexão novamente."
                    : code == 429 ? "Limite de consultas da Conta Azul atingido. Aguarde antes de tentar novamente."
                    : "A Conta Azul não concluiu a consulta. Tente novamente mais tarde.";
            throw new ResponseStatusException(code == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY,
                    mensagem, ContaAzulAutoDiagnostic.causaSegura(e, consulta));
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "A consulta não retornou uma página válida. Nenhum lançamento foi importado.", ContaAzulAutoDiagnostic.causaSegura(e, consulta));
        }
    }

    private void validar(Pagina p, LocalDate inicio, LocalDate fim, boolean competencia) {
        if (p == null || p.totalItens() == null || p.totalItens() < 0 || p.itens() == null || p.itens().size() > TAMANHO || p.itens().size() > p.totalItens()) {
            throw new IllegalArgumentException();
        }
        var ids = new HashSet<String>();
        for (Parcela item : p.itens()) {
            if (item == null || item.id() == null || item.id().isBlank()) {
                throw new IllegalArgumentException();
            }
            if (!ids.add(item.id().toLowerCase(java.util.Locale.ROOT))) throw new PaginaInstavel();
            LocalDate dataFiltro = competencia ? item.competencia() : item.vencimento();
            if (dataFiltro != null && (dataFiltro.isBefore(inicio) || dataFiltro.isAfter(fim))) {
                throw new IllegalArgumentException();
            }


        }
    }

    static final class PaginaInstavel extends ResponseStatusException {
        PaginaInstavel() { super(HttpStatus.CONFLICT, "A paginação repetiu uma identidade de parcela; repita a janela."); }
    }

}
