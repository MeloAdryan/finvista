package com.finvista.service;

import com.finvista.controller.FinancialDetailController;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialDetailServiceTest {

    private FinancialTransactionRepository repository;
    private ClienteContextService contexto;
    private FinancialReferenceService referencia;
    private FinancialDetailService service;
    private static final YearMonth MES = YearMonth.of(2026, 10);

    @BeforeEach
    void preparar() {
        repository = mock(FinancialTransactionRepository.class);
        contexto = mock(ClienteContextService.class);
        referencia = mock(FinancialReferenceService.class);
        when(contexto.getClienteAtualId()).thenReturn(7L);
        when(referencia.obterMesReferencia()).thenReturn(MES);
        service = new FinancialDetailService(repository, contexto, referencia);
    }

    @Test
    void listaFechaComDashboardSemTrocarValorPorOriginalOuPago() {
        var despesa = item(1L, "DESPESA", "100");
        despesa.setValorOriginal(new BigDecimal("999"));
        despesa.setValorRealizado(BigDecimal.ZERO);
        despesa.setSituacao("Em aberto");
        var ajuste = item(2L, " despesa ", "-20");
        var receita = item(3L, "RECEITA", "30");
        var invalido = item(4L, "DESPESA", "90");
        invalido.setValor(null);
        var futuro = item(5L, "DESPESA", "90");
        futuro.setData(LocalDate.of(2029, 3, 16));
        dados(7L, List.of(despesa, ajuste, receita, invalido, futuro));
        var resultado = service.consultar("DESPESA", MES, 0, 20);
        var resumo = new FinancialTransactionAggregationService(repository, contexto)
                .obterResumoMensal(MES.atDay(1), MES.atEndOfMonth()).get(0);
        igual("80", resultado.total());
        assertEquals(0, resultado.total().compareTo(resumo.despesa()));
        assertEquals(2, resultado.quantidadeLancamentos());
        igual("100", resultado.itens().get(0).valorContabilizado());
        igual("999", resultado.itens().get(0).valorOriginal());
        igual("0", resultado.itens().get(0).principalRealizado());
        igual("30", service.consultar("RECEITA", MES, 0, 20).total());
    }

    @Test
    void paginacaoMantemTotalDaListaInteiraEOrdenacaoPorId() {
        dados(7L, List.of(item(5L, "DESPESA", "5"), item(1L, "DESPESA", "1"),
                item(3L, "DESPESA", "3"), item(2L, "DESPESA", "2"), item(4L, "DESPESA", "4")));
        var resultado = service.consultar("DESPESA", MES, 1, 2);
        igual("15", resultado.total());
        assertEquals(5, resultado.quantidadeLancamentos());
        assertEquals(3, resultado.totalPaginas());
        assertEquals(List.of(3L, 4L), resultado.itens().stream().map(i -> i.id()).toList());
        assertTrue(service.consultar("DESPESA", MES, Integer.MAX_VALUE, 50).itens().isEmpty());
    }

    @Test
    void consultaUsaClienteDoContextoEMesDeReferenciaQuandoOmitido() {
        dados(7L, List.of(item(1L, "DESPESA", "10")));
        var resultado = service.consultar("DESPESA", null, 0, 20);
        assertEquals(7L, resultado.clienteId());
        assertEquals("2026-10", resultado.mes());
        verify(repository).findByClienteIdAndDataBetweenOrderByDataAsc(7L, MES.atDay(1), MES.atEndOfMonth());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void mudarClienteConsultaApenasOsDadosDoNovoContexto() {
        dados(7L, List.of(item(1L, "DESPESA", "10")));
        dados(8L, List.of(item(2L, "DESPESA", "20")));
        igual("10", service.consultar("DESPESA", MES, 0, 20).total());
        when(contexto.getClienteAtualId()).thenReturn(8L);
        var resultado = service.consultar("DESPESA", MES, 0, 20);
        igual("20", resultado.total());
        assertEquals(8L, resultado.clienteId());
        assertEquals(2L, resultado.itens().get(0).id());
    }

    @Test
    void semLancamentosRetornaTotalZeroEListaVazia() {
        dados(7L, List.of());
        var resultado = service.consultar("RECEITA", MES, 0, 20);
        igual("0", resultado.total());
        assertEquals(0, resultado.totalPaginas());
        assertTrue(resultado.itens().isEmpty());
    }

    @Test
    void parametrosInvalidosNaoConsultamRepositorio() {
        assertThrows(IllegalArgumentException.class, () -> service.consultar("OUTRO", MES, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.consultar("DESPESA", MES, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.consultar("DESPESA", MES, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.consultar("DESPESA", MES, 0, 51));
        verifyNoInteractions(repository);
    }

    @Test
    void mesInvalidoNaRotaRetorna400SemConsultarServico() {
        var simulado = mock(FinancialDetailService.class);
        var controller = new FinancialDetailController(simulado);
        var erro = assertThrows(ResponseStatusException.class,
                () -> controller.consultar("DESPESA", "2026-13", 0, 20));
        assertEquals(400, erro.getStatusCode().value());
        verifyNoInteractions(simulado);
    }

    private FinancialTransaction item(Long id, String tipo, String valor) {
        var t = spy(new FinancialTransaction(MES.atDay(16), "Despesa", tipo,
                new BigDecimal(valor), "Categoria principal", "Centro", "TESTE", null));
        when(t.getId()).thenReturn(id);
        return t;
    }

    private void dados(Long cliente, List<FinancialTransaction> valores) {
        when(repository.findByClienteIdAndDataBetweenOrderByDataAsc(cliente, MES.atDay(1), MES.atEndOfMonth()))
                .thenReturn(valores);
    }

    private void igual(String esperado, BigDecimal valor) {
        assertEquals(0, new BigDecimal(esperado).compareTo(valor));
    }
}


     
           
         
           
         
         
         
           
         
        
    
    