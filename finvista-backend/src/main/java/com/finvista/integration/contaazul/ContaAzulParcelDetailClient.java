package com.finvista.integration.contaazul;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;




@Component
public class ContaAzulParcelDetailClient {

    private final RestClient http;

    @Autowired
    public ContaAzulParcelDetailClient() {
        this(criarHttp());
    }

    public ContaAzulParcelDetailClient(RestClient http) {
        this.http = http;
    }

    private static RestClient criarHttp() {
        var factory = new SimpleClientHttpRequestFactory();
    

        factory.setConnectTimeout(5000);
        factory.setReadTimeout(20000);
        return RestClient.builder().requestFactory(factory).build();
    }

    public record Consulta(ContaAzulParcelDetail parcela,com.fasterxml.jackson.databind.JsonNode fonte) {}
    public ContaAzulParcelDetail consultar(String acesso,String id){return consultarCompleto(acesso,id).parcela();}
    public Consulta consultarCompleto(String acesso, String id) {
        try {
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
            var raw = http.get()
                    .uri(ContaAzulFinancialClient.BASE + "/v1/financeiro/eventos-financeiros/parcelas/{id}", id)
                    .headers(headers -> headers.setBearerAuth(acesso))
                    .retrieve().body(com.fasterxml.jackson.databind.JsonNode.class);
            var result=mapper.treeToValue(raw,ContaAzulParcelDetail.class);
            if (result == null || result.id() == null || !id.equalsIgnoreCase(result.id())) {
                throw new IllegalArgumentException("Parcela diferente da solicitada.");
            }
            // The dedicated settlement endpoint is authoritative even when detail embeds an empty/partial list.
            if (result.pago() != null && result.pago().signum() > 0) {
                var baixasRaw = http.get()
                        .uri(ContaAzulFinancialClient.BASE + "/v1/financeiro/eventos-financeiros/parcelas/{id}/baixa", id)
                        .headers(headers -> headers.setBearerAuth(acesso))
                        .retrieve().body(com.fasterxml.jackson.databind.JsonNode.class);
                if(baixasRaw==null||!baixasRaw.isArray())throw new IllegalArgumentException("Baixas incompletas.");
                var baixas=mapper.treeToValue(baixasRaw,ContaAzulParcelDetail.Baixa[].class);
                ((com.fasterxml.jackson.databind.node.ObjectNode)raw).set("baixas",baixasRaw);
                if (baixas == null || baixas.length > 1000) throw new IllegalArgumentException("Baixas incompletas.");
                result = new ContaAzulParcelDetail(result.id(), result.descricao(), result.status(), result.vencimento(),
                        result.liquido(), result.pago(), result.pendente(), result.composicao(), result.evento(),
                        result.perda(), result.renegociacao(), java.util.Arrays.asList(baixas));
            }
            validarRateios(result);
            return new Consulta(result,raw);
        } catch (RestClientResponseException erro) {
            int status = erro.getStatusCode().value();
            if (status == 429) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Limite de consultas da Conta Azul atingido. Aguarde antes de tentar novamente.");
            }
            if (status == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "A parcela não está mais disponível na Conta Azul. Atualize a listagem.");
            }
            if (status == 401 || status == 403) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "A Conta Azul recusou o acesso. Renove ou autorize a conexão novamente.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "A Conta Azul não concluiu a consulta dos detalhes.");
        } catch (Exception erro) {
               throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Os detalhes retornados não são válidos. Nenhum lançamento foi importado.");
        }
    } 

    private void validarRateios(ContaAzulParcelDetail detalhe) {
        if (detalhe.evento() == null || detalhe.evento().rateio() == null) {
            return;
        }
        for (var rateio : detalhe.evento().rateio()) {
            if (rateio == null) {
                throw new IllegalArgumentException();
            }
            if (rateio.centros() != null && rateio.centros().stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException();
            }
        }
    }
}
