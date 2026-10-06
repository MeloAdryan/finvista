package com.finvista.service;

import com.finvista.dto.CostClassificationDto;
import com.finvista.model.*;
import com.finvista.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CostCenterAnalysisServiceTest {

    @Test
    void deveCombinarFiltrosEManterNaoClassificadosNoTotal() {
        var tx = mock(FinancialTransactionRepository.class);
        var repo = mock(CostCategoryClassificationRepository.class);
        var ctx = mock(ClienteContextService.class);
        var ref = mock(FinancialReferenceService.class);
        when(ctx.getClienteAtualId()).thenReturn(7L);
        when(ref.obterMesReferencia()).thenReturn(YearMonth.of(2026, 9));
        var c = new CostCategoryClassification();
        c.setClienteId(7L);
        c.setCategoriaChave("das");
        c.setComportamento(CostBehavior.VARIAVEL);
        c.setNatureza(CostNature.IMPOSTOS);
        when(repo.findByClienteId(7L)).thenReturn(List.of(c));
        LocalDate inicio = LocalDate.of(2026, 9, 1), fim = LocalDate.of(2026, 9, 30);
        var imposto = despesa(" DAS ", "100.00", inicio);
        var semClassificacao = despesa("Material", "20.00", fim);
        var fora = despesa("DAS", "999.00", inicio.minusDays(1));
        var receita = despesa("DAS", "500.00", inicio);
        receita.setTipo("RECEITA");
        when(tx.findByClienteIdAndDataBetweenOrderByDataAsc(7L, inicio, fim))
                .thenReturn(List.of(imposto, semClassificacao, fora, receita));
        var service = new CostCenterAnalysisService(tx, repo, ctx, ref);
        assertEquals(new BigDecimal("120.00"), service.analisar(null, null, null, null).total());
        assertEquals(new BigDecimal("100.00"), service.analisar(inicio, fim, CostBehavior.VARIAVEL, CostNature.IMPOSTOS).total());
        assertEquals(BigDecimal.ZERO, service.analisar(inicio, fim, CostBehavior.FIXO, CostNature.IMPOSTOS).total());
        assertEquals(new BigDecimal("20.00"), service.analisar(inicio, fim, CostBehavior.NAO_CLASSIFICADO, null).total());
        verify(repo, times(4)).findByClienteId(7L);
        verify(tx, times(4)).findByClienteIdAndDataBetweenOrderByDataAsc(7L, inicio, fim);
        verify(repo, never()).findByClienteId(8L);
    }

    @Test
    void deveRecusarDatasIncompletasOuInvertidas() {
        var tx = mock(FinancialTransactionRepository.class);
        var repo = mock(CostCategoryClassificationRepository.class);
        var service = new CostCenterAnalysisService(tx, repo, mock(ClienteContextService.class), mock(FinancialReferenceService.class));
        LocalDate dia = LocalDate.of(2026, 9, 1);
        assertThrows(IllegalArgumentException.class, () -> service.analisar(dia, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.analisar(dia, dia.minusDays(1), null, null));
        verifyNoInteractions(tx, repo);
    }

    @Test
    void clienteNaoPodeSalvarClassificacao() {
        var tx = mock(FinancialTransactionRepository.class);
        var repo = mock(CostCategoryClassificationRepository.class);
        var ctx = mock(ClienteContextService.class);
        doThrow(new IllegalStateException("Apenas administradores")).when(ctx).validarAdministrador();
        var service = new CostCenterAnalysisService(tx, repo, ctx, mock(FinancialReferenceService.class));
        assertThrows(IllegalStateException.class, () -> service.salvar(new CostClassificationDto("DAS", CostBehavior.VARIAVEL, CostNature.IMPOSTOS)));
        verifyNoInteractions(tx, repo);
    }

    @Test
    void deveSalvarSomenteCategoriaDoClienteSelecionado() {
        var tx = mock(FinancialTransactionRepository.class);
        var repo = mock(CostCategoryClassificationRepository.class);
        var ctx = mock(ClienteContextService.class);
        when(ctx.getClienteAtualId()).thenReturn(7L);
        when(tx.findAllByClienteIdOrderByDataDesc(7L)).thenReturn(List.of(despesa("DAS", "100.00", LocalDate.of(2026, 9, 1))));
        when(repo.findByClienteIdAndCategoriaChave(7L, "das")).thenReturn(Optional.empty());
        var service = new CostCenterAnalysisService(tx, repo, ctx, mock(FinancialReferenceService.class));
        service.salvar(new CostClassificationDto(" DAS ", CostBehavior.VARIAVEL, CostNature.IMPOSTOS));
        verify(repo).save(argThat(c -> c.getClienteId().equals(7L) && c.getCategoriaChave().equals("das") && c.getNatureza() == CostNature.IMPOSTOS));
        assertThrows(IllegalArgumentException.class, () -> service.salvar(new CostClassificationDto("Categoria de outro cliente", CostBehavior.FIXO, CostNature.TAXAS)));
        verify(repo, times(1)).save(any());
        verify(ctx, times(2)).validarAdministrador();
    }

    private FinancialTransaction despesa(String categoria, String valor, LocalDate data) {
        var tx = new FinancialTransaction();
        tx.setCategoria(categoria);
        tx.setValor(new BigDecimal(valor));
        tx.setData(data);
        tx.setTipo("DESPESA");
        return tx;
    }
}
