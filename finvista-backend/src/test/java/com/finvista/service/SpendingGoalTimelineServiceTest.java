package com.finvista.service;

import com.finvista.model.*;
import com.finvista.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpendingGoalTimelineServiceTest {

    private final SpendingGoalService goals = mock(SpendingGoalService.class);
    private final FinancialTransactionRepository repository = mock(FinancialTransactionRepository.class);
    private final ExpenseAllocationService allocations = mock(ExpenseAllocationService.class);
    private final SpendingGoalTimelineService service = new SpendingGoalTimelineService(goals, repository, allocations, Clock.fixed(Instant.parse("2026-10-08T15:00:00Z"), ZoneOffset.UTC));

    private SpendingGoal preparar(String inicio, String fim) {
        var cliente = mock(Cliente.class);
        when(cliente.getId()).thenReturn(7L);
        var meta = new SpendingGoal("MENSAL", LocalDate.parse(inicio), LocalDate.parse(fim), new BigDecimal("100000"), 70);
        meta.setCliente(cliente);
        when(goals.buscarPorId(1L)).thenReturn(meta);
        return meta;
    }

    private void registros(SpendingGoal meta, String... pares) {
        List<FinancialTransaction> tx = new ArrayList<>();
        List<ExpenseAllocationService.ParteDetalhada> partes = new ArrayList<>();
        for (String par : pares) {
            var itens = par.split(":");
            var t = new FinancialTransaction();
            t.setTipo("DESPESA");
            t.setData(LocalDate.parse(itens[0]));
            t.setValor(new BigDecimal(itens[1]));
            tx.add(t);
            partes.add(new ExpenseAllocationService.ParteDetalhada(t, new ExpenseAllocationService.Parte("Serviços", "Administrativo", t.getValor())));
        }
        when(repository.findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(7L, "DESPESA", meta.getDataInicio(), meta.getDataFim())).thenReturn(tx);
        when(allocations.dividirDetalhado(7L, tx)).thenReturn(partes);
    }

    @Test
    void metaEncerradaPreservaPeriodoEValorFinal() {
        var m = preparar("2026-03-01", "2026-03-31");
        registros(m, "2026-03-04:14387.45");
        var r = service.consultar(1L);
        assertEquals("ENCERRADA", r.situacaoTemporal());
        assertEquals(new BigDecimal("14387.45"), r.totalRegistrado());
        assertNull(r.estimativaFim());
        assertEquals(r.totalRegistrado(), r.pontos().get(30).acumuladoRegistrado());
    }

    @Test
    void separaRegistroFuturoDaEstimativaSemSomarDuasVezes() {
        var m = preparar("2026-10-01", "2026-10-31");
        registros(m, "2026-10-02:800", "2026-10-20:200");
        var r = service.consultar(1L);
        assertEquals(8, r.diasDecorridos());
        assertEquals(new BigDecimal("800"), r.registradoAteHoje());
        assertEquals(new BigDecimal("200"), r.registradoDepoisHoje());
        assertEquals(new BigDecimal("3100.00"), r.estimativaFim());
        assertEquals(new BigDecimal("25.81"), r.ritmoIdealPercentual());
        assertNull(r.pontos().get(8).acumuladoRegistrado());
        assertEquals(new BigDecimal("800"), r.pontos().get(7).estimativa());
    }

    @Test
    void metaFuturaNaoTemConsumoAteHojeNemEstimativa() {
        var m = preparar("2026-11-01", "2026-11-30");
        registros(m, "2026-11-03:150");
        var r = service.consultar(1L);
        assertEquals("FUTURA", r.situacaoTemporal());
        assertEquals(0, r.diasDecorridos());
        assertEquals(0, r.registradoAteHoje().signum());
        assertNull(r.estimativaFim());
        assertTrue(r.pontos().stream().allMatch(p -> p.acumuladoRegistrado() == null && p.estimativa() == null));
    }

    @Test
    void respeitaCategoriaECentroSalvosNaMeta() {
        var m = preparar("2026-10-01", "2026-10-31");
        m.setCategoria(" Outra ");
        m.setCentroCusto("Administrativo");
        registros(m, "2026-10-02:800");
        var r = service.consultar(1L);
        assertEquals(0, r.totalRegistrado().signum());
        assertTrue(r.categorias().isEmpty());
    }

    @Test
    void saldoNegativoNaoGeraExtrapolacaoDeConsumo() {
        var m = preparar("2026-10-01", "2026-10-31");
        registros(m, "2026-10-02:-50");
        assertNull(service.consultar(1L).estimativaFim());
    }

    @Test
    void acessoNegadoNaoConsultaLancamentosDeOutraEmpresa() {
        when(goals.buscarPorId(1L)).thenThrow(new IllegalArgumentException("Meta não encontrada"));
        assertThrows(IllegalArgumentException.class, () -> service.consultar(1L));
        verifyNoInteractions(repository, allocations);
    }
}
