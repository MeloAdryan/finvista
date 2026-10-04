package com.finvista.service;

import com.finvista.controller.CostCenterController;
import com.finvista.controller.ExpenseDistributionController;
import com.finvista.dto.CostCenterResponse;
import com.finvista.dto.ExpenseDistributionResponse;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class FinancialAnalysisPeriodTest {

    @Test
    void deveSomarOMesSemPerderDespesasSemClassificacao() {
        FinancialTransactionRepository repository = mock(FinancialTransactionRepository.class);
        ClienteContextService contexto = mock(ClienteContextService.class);
        when(contexto.getClienteAtualId()).thenReturn(7L);
        YearMonth mes = YearMonth.of(2026, 9);

        FinancialTransaction classificada = lancamento("2026-09-01", "DESPESA", "100.00", "Salários", "Operacional");
        FinancialTransaction semClassificacao = lancamento("2026-09-30", " despesa ", "20.00", null, null);
        FinancialTransaction receita = lancamento("2026-09-02", "RECEITA", "999.00", "Vendas", "Comercial");
        FinancialTransaction foraDoMes = lancamento("2026-08-31", "DESPESA", "500.00", "Salários", "Operacional");
        FinancialTransaction semValor = lancamento("2026-09-03", "DESPESA", null, "Salários", "Operacional");
        FinancialTransaction semData = lancamento(null, "DESPESA", "300.00", "Salários", "Operacional");

        when(repository.findByClienteIdAndDataBetweenOrderByDataAsc(
                7L, mes.atDay(1), mes.atEndOfMonth()
        )).thenReturn(List.of(classificada, semClassificacao, receita, foraDoMes, semValor, semData));

        List<ExpenseDistributionResponse> categorias =
                new ExpenseDistributionService(repository, contexto).listarNoMes(mes);
        List<CostCenterResponse> centros =
                new CostCenterService(repository, contexto).listarNoMes(mes);

        assertEquals(2, categorias.size());
        assertEquals(2, centros.size());
        assertEquals("Sem categoria", categorias.get(1).categoria());
        assertEquals("Sem centro de custo", centros.get(1).nome());
        assertEquals(new BigDecimal("120.00"), categorias.stream()
                .map(ExpenseDistributionResponse::valor).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(new BigDecimal("120.00"), centros.stream()
                .map(CostCenterResponse::valor).reduce(BigDecimal.ZERO, BigDecimal::add));
        verify(repository, times(2)).findByClienteIdAndDataBetweenOrderByDataAsc(
                7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)
        );
        verify(repository, never()).findByClienteIdAndTipoOrderByDataDesc(anyLong(), anyString());
    }

    @Test
    void deveUsarReferenciaNosDoisEndpointsEPreservarHistorico() {
        FinancialReferenceService referencia = mock(FinancialReferenceService.class);
        ExpenseDistributionService despesas = mock(ExpenseDistributionService.class);
        CostCenterService centros = mock(CostCenterService.class);
        YearMonth mes = YearMonth.of(2026, 9);
        when(referencia.obterMesReferencia()).thenReturn(mes);

        ExpenseDistributionController despesasController = new ExpenseDistributionController(despesas, referencia);
        CostCenterController centrosController = new CostCenterController(centros, referencia);

        despesasController.getDistribuicaoDespesas(false);
        centrosController.getCentrosCusto(false);
        verify(despesas).listarNoMes(mes);
        verify(centros).listarNoMes(mes);

        despesasController.getDistribuicaoDespesas(true);
        centrosController.getCentrosCusto(true);
        verify(despesas).listar();
        verify(centros).listar();
        verify(referencia, times(2)).obterMesReferencia();
    }

    @Test
    void deveRetornarListasVaziasQuandoMesNaoTemLancamentos() {
        FinancialTransactionRepository repository = mock(FinancialTransactionRepository.class);
        ClienteContextService contexto = mock(ClienteContextService.class);
        when(contexto.getClienteAtualId()).thenReturn(7L);
        YearMonth mes = YearMonth.of(2026, 9);
        when(repository.findByClienteIdAndDataBetweenOrderByDataAsc(
                7L, mes.atDay(1), mes.atEndOfMonth()
        )).thenReturn(List.of());

        assertEquals(List.of(), new ExpenseDistributionService(repository, contexto).listarNoMes(mes));
        assertEquals(List.of(), new CostCenterService(repository, contexto).listarNoMes(mes));
    }

    private FinancialTransaction lancamento(
            String data, String tipo, String valor, String categoria, String centro
    ) {
        FinancialTransaction lancamento = new FinancialTransaction();
        lancamento.setData(data == null ? null : LocalDate.parse(data));
        lancamento.setTipo(tipo);
        lancamento.setValor(valor == null ? null : new BigDecimal(valor));
        lancamento.setCategoria(categoria);
        lancamento.setCentroCusto(centro);
        return lancamento;
    }
}