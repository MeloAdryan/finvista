package com.finvista.service;

import com.finvista.dto.FinancialChangesResponse;
import com.finvista.model.*;
import com.finvista.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialChangesAllocationTest {
    @Test
    void rateiosExplicamMudancaMesmoQuandoTotalMensalNaoMuda() {
        conferir(false);
    }

    @Test
    void legadoERateiosConciliamComVariacaoTotalSemDuplicarContagem() {
        conferir(true);
    }

    private void conferir(boolean incluirLegado) {
        var repository = mock(FinancialTransactionRepository.class);
        var rates = mock(FinancialAllocationRepository.class);
        var contexto = mock(ClienteContextService.class);
        var referencia = mock(FinancialReferenceService.class);
        when(contexto.getClienteAtualId()).thenReturn(7L);
        when(referencia.obterMesReferencia()).thenReturn(YearMonth.of(2026, 10));
        var setembro = despesa(1L, "2026-09-16", "100", "Juros");
        var outubro = despesa(2L, "2026-10-16", "100", "Juros");
        var receita = despesa(3L, "2026-10-16", "500", "Vendas"); receita.setTipo("RECEITA");
        var futuro = despesa(4L, "2029-03-16", "500", "Outro");
        var transacoes = new ArrayList<>(List.of(setembro, outubro, receita, futuro));
        if (incluirLegado) transacoes.add(despesa(null, "2026-10-20", "20", "Material"));
        when(repository.findByClienteIdAndDataBetweenOrderByDataAsc(7L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31))).thenReturn(transacoes);
        when(rates.findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(7L, List.of(1L)))
                .thenReturn(List.of(parcela(setembro, 1, "Juros", "-10"), parcela(setembro, 2, "Móveis", "-90")));
        when(rates.findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(7L, List.of(2L)))
                .thenReturn(List.of(parcela(outubro, 1, "Juros", "-40"), parcela(outubro, 2, "Móveis", "-60")));
        var service = new FinancialChangesService(repository, contexto, referencia, new ExpenseAllocationService(rates));
        var resultado = service.obterMudancas();
        igual("100", resultado.despesaAnterior());
        igual(incluirLegado ? "120" : "100", resultado.despesaAtual());
        igual(incluirLegado ? "20" : "0", resultado.diferenca());
        igual(incluirLegado ? "20" : "0", resultado.variacaoPercentual());
        assertEquals(1, resultado.lancamentosAnterior());
        assertEquals(incluirLegado ? 2 : 1, resultado.lancamentosAtual());
        var juros = categoria(resultado, "Juros");
        igual("40", juros.valorAtual()); igual("10", juros.valorAnterior()); igual("30", juros.diferenca());
        var moveis = categoria(resultado, "Móveis");
        igual("60", moveis.valorAtual()); igual("90", moveis.valorAnterior()); igual("-30", moveis.diferenca());
        assertEquals(0, resultado.diferenca().compareTo(resultado.categorias().stream()
                .map(FinancialChangesResponse.CategoryChange::diferenca).reduce(BigDecimal.ZERO, BigDecimal::add)));
        verify(rates).findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(7L, List.of(1L));
        verify(rates).findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(7L, List.of(2L));
        verifyNoMoreInteractions(rates);
    }

    private FinancialTransaction despesa(Long id, String data, String valor, String categoria) {
        var t = spy(new FinancialTransaction(LocalDate.parse(data), "Parcela", "DESPESA",
                new BigDecimal(valor), categoria, "Administrativas", "CONTA_AZUL_EXCEL", null));
        when(t.getId()).thenReturn(id); return t;
    }
    private FinancialAllocation parcela(FinancialTransaction t, int bloco, String cat, String valor) {
        return new FinancialAllocation(t, bloco, cat, new BigDecimal(valor), "Administrativas", new BigDecimal(valor));
    }
    private FinancialChangesResponse.CategoryChange categoria(FinancialChangesResponse r, String cat) {
        return r.categorias().stream().filter(c -> cat.equals(c.categoria())).findFirst().orElseThrow();
    }
    private void igual(String esperado, BigDecimal valor) {
        assertEquals(0, new BigDecimal(esperado).compareTo(valor));
    }
}
