package com.finvista.service;

import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialChangesServiceTest {
    private FinancialTransactionRepository repository;
    private ClienteContextService contexto;
    private FinancialReferenceService referencia;
    private FinancialChangesService service;
    private final LocalDate inicio = LocalDate.of(2026, 9, 1);
    private final LocalDate fim = LocalDate.of(2026, 10, 31);

    @BeforeEach
    void configurar() {
        repository = mock(FinancialTransactionRepository.class);
        contexto = mock(ClienteContextService.class);
        referencia = mock(FinancialReferenceService.class);
        when(contexto.getClienteAtualId()).thenReturn(7L);
        when(referencia.obterMesReferencia()).thenReturn(YearMonth.of(2026, 10));
        service = new FinancialChangesService(repository, contexto, referencia);
    }

    private FinancialTransaction item(String data, String tipo, String categoria, String valor) {
        var registro = new FinancialTransaction();
        if (data != null) registro.setData(LocalDate.parse(data));
        registro.setTipo(tipo);
        registro.setCategoria(categoria);
        if (valor != null) registro.setValor(new BigDecimal(valor));
        return registro;
    }

    private void registros(FinancialTransaction... itens) {
        when(repository.findByClienteIdAndDataBetweenOrderByDataAsc(7L, inicio, fim))
                .thenReturn(List.of(itens));
    }

    private void igual(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual));
    }

    @Test
    void deveConciliarVariacoesComTotaisEConsultarSomenteClienteAtual() {
        registros(
            item("2026-09-01", "DESPESA", "Marketing", "100.00"),
            item("2026-10-31", " despesa ", "Marketing", "150.00"),
            item("2026-09-20", "DESPESA", "Software", "80.00"),
            item("2026-10-20", "DESPESA", "Software", "60.00"),
            item("2026-10-02", "RECEITA", "Vendas", "900.00"));
        var resposta = service.obterMudancas();
        igual("180", resposta.despesaAnterior());
        igual("210", resposta.despesaAtual());
        igual("30", resposta.diferenca());
        igual("16.67", resposta.variacaoPercentual());
        igual("30", resposta.categorias().stream()
                .map(categoria -> categoria.diferenca())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals("Marketing", resposta.categorias().get(0).categoria());
        assertEquals(2, resposta.lancamentosAnterior());
        assertEquals(2, resposta.lancamentosAtual());
        verify(repository).findByClienteIdAndDataBetweenOrderByDataAsc(7L, inicio, fim);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void deveExibirMudancasQueSeCompensamMesmoSemVariacaoTotal() {
        registros(
            item("2026-09-10", "DESPESA", "A", "100"),
            item("2026-10-10", "DESPESA", "A", "150"),
            item("2026-09-10", "DESPESA", "B", "100"),
            item("2026-10-10", "DESPESA", "B", "50"),
            item("2026-09-10", "DESPESA", "C", "20"),
            item("2026-10-10", "DESPESA", "C", "20"));
        var resposta = service.obterMudancas();
        igual("0", resposta.diferenca());
        assertEquals(2, resposta.categorias().size());
    }

    @Test
    void deveTratarBaseZeroESemCategoriaSemInventarPercentual() {
        registros(item("2026-10-10", "DESPESA", " ", "90"));
        var resposta = service.obterMudancas();
        assertNull(resposta.variacaoPercentual());
        assertEquals(0, resposta.lancamentosAnterior());
        igual("90", resposta.diferenca());
        assertEquals("Sem categoria", resposta.categorias().get(0).categoria());
        igual("0", resposta.categorias().get(0).valorAnterior());
    }

    @Test
    void deveRepresentarCategoriaQueDeixouDeTerDespesas() {
        registros(item("2026-09-10", "DESPESA", "Encerrada", "90"));
        var resposta = service.obterMudancas();
        igual("-90", resposta.diferenca());
        igual("-100", resposta.variacaoPercentual());
        igual("0", resposta.categorias().get(0).valorAtual());
        assertEquals(0, resposta.lancamentosAtual());
    }

    @Test
    void deveIgnorarCamposInvalidosTransferenciasEDatasForaDoPeriodo() {
        registros(
            item(null, "DESPESA", "A", "10"),
            item("2026-10-10", null, "A", "10"),
            item("2026-10-10", "DESPESA", "A", null),
            item("2026-10-10", "TRANSFERENCIA", "A", "10"),
            item("2026-08-10", "DESPESA", "A", "10"));
        var resposta = service.obterMudancas();
        assertTrue(resposta.categorias().isEmpty());
        assertEquals(0, resposta.lancamentosAtual());
        assertEquals(0, resposta.lancamentosAnterior());
        igual("0", resposta.diferenca());
        assertNull(resposta.variacaoPercentual());
    }
}