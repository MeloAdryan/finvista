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

    public ContaAzulParcelDetail consultar(String acesso, String id) {
        try {
            var result = http.get()
                    .uri(ContaAzulFinancialClient.BASE + "/v1/financeiro/eventos-financeiros/parcelas/{id}", id)
                    .headers(headers -> headers.setBearerAuth(acesso))
                    .retrieve().body(ContaAzulParcelDetail.class);
            if (result == null || result.id() == null || !id.equalsIgnoreCase(result.id())) {
                throw new IllegalArgumentException("Parcela diferente da solicitada.");
            }
            validarRateios(result);
            return result;
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
