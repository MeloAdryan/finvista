package com.finvista.integration.contaazul;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.repository.FinancialTransactionRepository;
import com.finvista.service.ClienteContextService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaAzulParcelAmountTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final String json = """
        {
          "id":"9986f173-f531-4660-96ae-04b71c879264",
          "descricao":"Venda de teste", "status":"PENDENTE", "data_vencimento":"2026-10-21",
          "valor_total_liquido":null, "valor_pago":0.00, "nao_pago":50.00,
          "valor_composicao":{"valor_bruto":50.00,"valor_liquido":50.00,
            "juros":0.00,"multa":0.00,"desconto":0.00,"taxa":0.00},
          "evento":{"id":"evento-teste","tipo":"RECEITA","data_competencia":"2026-09-21",
            "condicao_pagamento":{"quantidade_parcelas":1},
            "rateio":[{"id_categoria":"categoria-teste","nome_categoria":"Receitas a Identificar",
              "valor":50.00,"valor_bruto":50.00,"rateio_centro_custo":[]}]}
        }
        """;

    private ContaAzulParcelDetail ler(String data) throws Exception {
        return mapper.readValue(data, ContaAzulParcelDetail.class);
    }

    @Test
    void respostaRealConfere50SemAlterarCampoOriginal() throws Exception {
        var p = ler(json);
        assertNull(p.liquido());
        assertEquals(new BigDecimal("50.00"), p.liquidoConferido());
        assertEquals("COMPOSICAO_CONFERIDA", p.origemValorLiquido());
        var resposta = mapper.readTree(mapper.writeValueAsString(p));
        assertTrue(resposta.get("valor_total_liquido").isNull());
        assertEquals(0, new BigDecimal("50").compareTo(resposta.get("valor_liquido_conferido").decimalValue()));
    }

    @Test
    void campoPrincipalPrevaleceMesmoSeDivergirDaComposicao() throws Exception {
        var p = ler(json.replace("\"valor_total_liquido\":null", "\"valor_total_liquido\":60"));
        assertEquals(new BigDecimal("60"), p.liquidoConferido());
        assertEquals("VALOR_TOTAL_LIQUIDO", p.origemValorLiquido());
        assertEquals(ContaAzulApiPlanService.Situacao.BLOQUEADO, planejar(p).situacao());
    }

    @Test
    void divergenciasAusenciasAjustesOuEventosComMaisParcelasContinuamBloqueados() throws Exception {
        for (String data : List.of(
                json.replace("\"nao_pago\":50.00", "\"nao_pago\":49.00"),
                json.replace("\"valor_pago\":0.00", "\"valor_pago\":null"),
                json.replace("\"valor_bruto\":50.00", "\"valor_bruto\":51.00"),
                json.replace("\"valor_liquido\":50.00", "\"valor_liquido\":null"),
                json.replace("\"valor_pago\":0.00", "\"valor_pago\":-1.00"),
                json.replace("\"nao_pago\":50.00", "\"nao_pago\":50.001"),
                json.replace("\"juros\":0.00", "\"juros\":1.00"),
                json.replace("\"multa\":0.00", "\"multa\":1.00"),
                json.replace("\"desconto\":0.00", "\"desconto\":1.00"),
                json.replace("\"taxa\":0.00", "\"taxa\":1.00"),
                json.replace("\"taxa\":0.00", "\"taxa\":null"),
                json.replace("\"quantidade_parcelas\":1", "\"quantidade_parcelas\":2"),
                json.replace("\"tipo\":\"RECEITA\"", "\"tipo\":\"OUTRO\""))) {
            var p = ler(data);
            assertNull(p.liquidoConferido());
            assertEquals("NAO_CONFERIDO", p.origemValorLiquido());
            assertEquals(ContaAzulApiPlanService.Situacao.BLOQUEADO, planejar(p).situacao());
        }
    }

    @Test
    void statusEspeciaisEQuitacaoIncompativelNaoSaoPresumidos() throws Exception {
        for (String status : List.of("CANCELADO", "RENEGOCIADO", "PERDIDO", "RECEBIDO_PARCIAL", "QUITADO", "DESCONHECIDO")) {
            assertNull(ler(json.replace("\"status\":\"PENDENTE\"", "\"status\":\"" + status + "\"")).liquidoConferido());
        }
    }

    @Test
    void perdaOuRenegociacaoImpedemConferenciaMesmoComStatusNormal() throws Exception {
        for (String campo : List.of("perda", "renegociacao")) {
            var p = ler(json.replace("\"descricao\":", "\"" + campo + "\":{\"valor\":0},\"descricao\":"));
            assertNull(p.liquidoConferido());
            assertFalse(mapper.readTree(mapper.writeValueAsString(p)).has(campo));
        }
    }

    @Test
    void quitadaEEmAbertoAceitamSomenteDistribuicaoDeSaldoCompativel() throws Exception {
        var quitada = ler(json.replace("\"status\":\"PENDENTE\"", "\"status\":\"QUITADO\"")
                .replace("\"valor_pago\":0.00", "\"valor_pago\":50.00").replace("\"nao_pago\":50.00", "\"nao_pago\":0.00"));
        assertEquals(new BigDecimal("50.00"), quitada.liquidoConferido());
        var parcial = ler(json.replace("\"valor_pago\":0.00", "\"valor_pago\":10.00")
                .replace("\"nao_pago\":50.00", "\"nao_pago\":40.00"));
        assertNull(parcial.liquidoConferido());
    }

    @Test
    void planoConfereValoresMasNaoDeclaraRegistroNovoNemGrava() throws Exception {
        var p = planejar(ler(json));
        assertEquals(ContaAzulApiPlanService.Situacao.SEM_CORRESPONDENCIA, p.situacao());
        assertTrue(p.avisos().stream().anyMatch(a -> a.contains("não comprova")));
    }

    private ContaAzulApiPlanService.Plano planejar(ContaAzulParcelDetail parcela) {
        var repository = mock(FinancialTransactionRepository.class);
        var context = mock(ClienteContextService.class);
        when(context.getClienteAtualId()).thenReturn(1L);
        when(repository.findByClienteId(1L)).thenReturn(List.of());
        var plano = new ContaAzulApiPlanService(repository, context).planejar(1L, parcela);
        verify(repository).findByClienteId(1L);
        verifyNoMoreInteractions(repository);
        return plano;
    }
}
