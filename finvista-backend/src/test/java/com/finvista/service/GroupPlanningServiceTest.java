package com.finvista.service;

import com.finvista.dto.ClienteResponse;
import com.finvista.model.Budget;
import com.finvista.service.FinancialTransactionAggregationService.MonthlyFinancialSummary;
import com.finvista.service.FinancialViewService.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupPlanningServiceTest {

    private final ClienteService clientes = mock(ClienteService.class);
    private final FinancialViewService financeiro = mock(FinancialViewService.class);
    private final BudgetPipelineService orcamentos = mock(BudgetPipelineService.class);
    private final FinancialTransactionAggregationService aggregation = mock(FinancialTransactionAggregationService.class);
    private final ContaAzulReconciliationService conferencia = mock(ContaAzulReconciliationService.class);
    private final GroupPlanningService grupos = new GroupPlanningService(clientes, financeiro, orcamentos,
            new ProjectionPlanningService(aggregation, Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)),
            conferencia);
    private final LocalDate inicio = LocalDate.of(2026, 10, 1);
    private final LocalDate fim = inicio.withDayOfMonth(31);

    private List<ClienteResponse> vinculadas() {
        return List.of(new ClienteResponse(1L, "Embraseg", true, null),
                new ClienteResponse(2L, "Embrasol", true, null));
    }

    @Test
    void cadaOrcamentoUsaApenasOsLancamentosDaEmpresa() {
        when(clientes.listarEmpresasDoCliente(20L)).thenReturn(vinculadas());
        var filtro = new Filtro(Criterio.COMPETENCIA, Periodo.PERSONALIZADO, Situacao.TODAS, inicio, inicio, fim, null, null);
        var orcamento = new Budget("Estrutura", null, "Aluguel", new BigDecimal("4000.00"), inicio, fim);
        for (long id : List.of(1L, 2L)) {
            var linha = new Linha(id, null, "Aluguel", "DESPESA", inicio, inicio, null,
                    "Aluguel", "Administrativo", BigDecimal.valueOf(id * 3000));
            when(financeiro.consultarEmpresa(filtro, id, false)).thenReturn(new Resposta(id, filtro,
                    new Faixa(inicio, fim), inicio, FinancialViewService.total(List.of(linha)), List.of(),
                    List.of(linha), Map.of(), List.of(), List.of(), List.of(), List.of(), "PRINCIPAL", ""));
            when(orcamentos.configuracoesDaEmpresa(id)).thenReturn(List.of(orcamento));
        }
        var resultado = grupos.orcamentos(20L, filtro);
        assertEquals(List.of(1L, 2L), resultado.stream().map(GroupPlanningService.OrcamentoEmpresa::empresaId).toList());
        assertEquals(new BigDecimal("3000"), resultado.get(0).limite().consumoPeriodo());
        assertEquals(new BigDecimal("6000"), resultado.get(1).limite().consumoPeriodo());
    }

    @Test
    void projecaoSomaRegistrosAntesDeAplicarOAlvoDoGrupo() {
        when(clientes.listarEmpresasVinculadas()).thenReturn(vinculadas());
        var mes = YearMonth.from(inicio);
        var inicioHistorico = mes.minusMonths(3).atDay(1);
        var fimProjecao = mes.plusMonths(5).atEndOfMonth();
        when(aggregation.obterResumoMensal(1L, inicioHistorico, fimProjecao))
                .thenReturn(List.of(new MonthlyFinancialSummary(mes, new BigDecimal("70"), new BigDecimal("10"))));
        when(aggregation.obterResumoMensal(2L, inicioHistorico, fimProjecao))
                .thenReturn(List.of(new MonthlyFinancialSummary(mes, new BigDecimal("40"), new BigDecimal("20"))));
        var outubro = grupos.projetar(null, new BigDecimal("100"), null, null).meses().get(0);
        assertEquals(new BigDecimal("110"), outubro.receitaRegistrada());
        assertEquals(new BigDecimal("110"), outubro.receitaSimulada());
        assertEquals(new BigDecimal("30"), outubro.despesaRegistrada());
    }

    @Test
    void usuarioSemAcessoNaoAlcancaAConferencia() {
        when(clientes.listarEmpresasDoCliente(20L)).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> grupos.conferir(20L, inicio, fim));
        verifyNoInteractions(conferencia);
    }

    @Test
    void conferenciaMantemAsPendenciasEmCadaEmpresa() {
        when(clientes.listarEmpresasVinculadas()).thenReturn(vinculadas());
        for (long id : List.of(1L, 2L)) {
            var resultado = mock(ContaAzulReconciliationService.Resultado.class);
            when(resultado.resumo()).thenReturn(new ContaAzulReconciliationService.Resumo(0, 0, 0, 0,
                    (int) id, 0, 0, true, 0));
            when(resultado.detalhes()).thenReturn(new FinancialMovementMatcher.Result(List.of(), List.of(), List.of(), List.of()));
            when(resultado.cobertura()).thenReturn(new ContaAzulReconciliationService.Cobertura(
                    "BAIXAS_ARMAZENADAS", null, null, (int) id, false, false));
            when(conferencia.conferirEmpresa(id, inicio, fim, true)).thenReturn(resultado);
        }
        var resultado = grupos.conferir(null, inicio, fim);
        assertEquals(List.of(1, 2), resultado.stream().map(GroupPlanningService.ConferenciaEmpresa::pendenciasEmpresa).toList());
        assertEquals(List.of(1, 2), resultado.stream().map(GroupPlanningService.ConferenciaEmpresa::divergencias).toList());
    }
}
