package com.finvista.service;

import com.finvista.dto.*;
import com.finvista.dto.FinancialAlertsResponse.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialAlertsServiceTest {

    private final ClienteContextService contexto = mock(ClienteContextService.class);
    private final DashboardAnalysisService dashboard = mock(DashboardAnalysisService.class);
    private final SpendingGoalService metas = mock(SpendingGoalService.class);
    private final BudgetPipelineService orcamentos = mock(BudgetPipelineService.class);
    private final FinancialAlertsService service = new FinancialAlertsService(contexto, dashboard, metas,
            orcamentos, new BigDecimal("20"), new BigDecimal("50"));
    private final LocalDate inicio = LocalDate.of(2026, 9, 1);
    private final LocalDate fim = LocalDate.of(2026, 9, 30);

    @BeforeEach
    void configurar() {
        when(contexto.getClienteAtualId()).thenReturn(7L);
        when(metas.listarComSituacao()).thenReturn(List.of());
        when(orcamentos.listar()).thenReturn(List.of());
        analise("100", "100", 1, 1, fim);
    }

    private BigDecimal valor(String valor) {
        return new BigDecimal(valor);
    }

    private void analise(String atual, String anterior, int registrosAtual, int registrosAnterior, LocalDate ultimo) {
        var indicadores = new DashboardResponse(BigDecimal.ZERO, valor(atual), valor(atual).negate(), BigDecimal.ZERO,
                BigDecimal.ZERO, valor(anterior), valor(anterior).negate(), BigDecimal.ZERO, null, null, null);
        when(dashboard.consultar(any(), any(), any())).thenReturn(new DashboardAnalysisResponse(7L,
                inicio, ultimo, inicio.minusMonths(1), inicio.minusDays(1), LocalDate.of(2026, 10, 11),
                "Salários", indicadores, List.of(), List.of(), List.of(), List.of(),
                new DashboardAnalysisResponse.Registros(0, registrosAtual, 0, registrosAnterior)));
    }

    private SpendingGoalResponse meta(long id, String gasto, LocalDate primeiro, LocalDate ultimo) {
        return new SpendingGoalResponse(id, "MENSAL", primeiro, ultimo, valor("100"), valor(gasto), BigDecimal.ZERO,
                BigDecimal.ZERO, 80, "NORMAL", null, null, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
    }

    private BudgetPipelineResponse orcamento(long id, String utilizado) {
        return new BudgetPipelineResponse(id, "Administrativo", null, null, valor("100"), valor(utilizado),
                valor("100").subtract(valor(utilizado)), BigDecimal.ZERO, inicio, fim);
    }

    private FinancialAlertsResponse consultar() {
        return service.consultar(inicio, fim, "Salários");
    }

    @Test
    void metaUsaGastoTotalESeuLimiarComPrecisao() {
        when(metas.listarComSituacao()).thenReturn(List.of(meta(1, "79.999", inicio, fim), meta(2, "80", inicio, fim)));
        var resultado = consultar();
        assertEquals(1, resultado.total());
        assertEquals(2L, resultado.alertas().get(0).entidadeId());
        assertEquals(Gravidade.ATENCAO, resultado.alertas().get(0).gravidade());
        assertEquals(valor("80"), resultado.alertas().get(0).valorAtual());
        assertNull(resultado.alertas().get(0).categoria());
    }

    @Test
    void metaAcimaDoLimiteEhCriticaEIgualAoLimiteNaoEhExcedente() {
        when(metas.listarComSituacao()).thenReturn(List.of(meta(1, "100", inicio, fim), meta(2, "100.01", inicio, fim)));
        var alertas = consultar().alertas();
        assertEquals(Gravidade.CRITICO, alertas.get(0).gravidade());
        assertEquals(Gravidade.ATENCAO, alertas.get(1).gravidade());
    }

    @Test
    void orcamentoComExcedenteUsaPeriodoCompleto() {
        when(orcamentos.listar()).thenReturn(List.of(orcamento(1, "100"), orcamento(2, "120")));
        var alerta = consultar().alertas().get(0);
        assertEquals(Tipo.ORCAMENTO_EXCEDENTE, alerta.tipo());
        assertEquals(valor("20"), alerta.diferenca());
        assertEquals(inicio, alerta.inicio());
        assertTrue(alerta.encerrado());
    }

    @Test
    void metasFuturasEForaDoPeriodoNaoGeramAviso() {
        when(metas.listarComSituacao()).thenReturn(List.of(
                meta(1, "90", inicio.minusMonths(1), inicio.minusDays(1)),
                meta(2, "90", fim.plusMonths(2), fim.plusMonths(3))));
        assertEquals(0, consultar().total());
    }

    @Test
    void aumentoIgualAoLimiarEhAtencao() {
        analise("120", "100", 2, 1, fim);
        var alerta = consultar().alertas().get(0);
        assertEquals(Tipo.AUMENTO_DESPESAS, alerta.tipo());
        assertEquals(Gravidade.ATENCAO, alerta.gravidade());
        assertEquals("Salários", alerta.categoria());
        assertEquals(valor("20.00"), alerta.percentual());
        assertEquals(inicio.minusMonths(1), alerta.inicioAnterior());
        verify(dashboard).consultar(inicio, fim, "Salários");
    }

    @Test
    void aumentoCriticoEhPriorizado() {
        analise("150", "100", 2, 1, fim);
        assertEquals(Gravidade.CRITICO, consultar().alertas().get(0).gravidade());
    }

    @Test
    void aumentoAbaixoDoLimiarOuReducaoNaoGeramAviso() {
        analise("119.999", "100", 2, 1, fim);
        assertEquals(0, consultar().total());
        analise("80", "100", 2, 1, fim);
        assertEquals(0, consultar().total());
    }

    @Test
    void semRegistrosEmQualquerPeriodoNaoInventaAumento() {
        analise("150", "100", 0, 1, fim);
        assertEquals(Comparacao.SEM_REGISTROS, consultar().comparacao());
        analise("150", "100", 2, 0, fim);
        assertEquals(Comparacao.SEM_REGISTROS, consultar().comparacao());
        assertEquals(0, consultar().total());
    }

    @Test
    void totalAnteriorZeroOuNegativoNaoEhBasePercentual() {
        for (String anterior : List.of("0", "-100")) {
            analise("150", anterior, 2, 1, fim);
            assertEquals(Comparacao.BASE_NAO_POSITIVA, consultar().comparacao());
            assertEquals(0, consultar().total());
        }
    }

    @Test
    void periodoEmAndamentoOuFuturoNaoGeraAumento() {
        analise("200", "100", 2, 1, LocalDate.of(2026, 10, 31));
        assertEquals(Comparacao.PERIODO_EM_ANDAMENTO, consultar().comparacao());
        assertEquals(0, consultar().total());
    }

    @Test
    void limitaTresComOrdemEstavelESemDuplicarMeta() {
        var meta = meta(1, "90", inicio, fim);
        when(metas.listarComSituacao()).thenReturn(List.of(meta, meta));
        when(orcamentos.listar()).thenReturn(List.of(orcamento(2, "200"), orcamento(3, "140"), orcamento(4, "150")));
        var resultado = consultar();
        assertEquals(4, resultado.total());
        assertEquals(3, resultado.alertas().size());
        assertEquals(List.of(2L, 4L, 3L), resultado.alertas().stream().map(Alerta::entidadeId).toList());
        assertEquals(resultado.alertas(), consultar().alertas());
    }

    @Test
    void naoAceitaEmpresaDiferenteDaSessao() {
        when(contexto.getClienteAtualId()).thenReturn(8L);
        assertThrows(IllegalStateException.class, this::consultar);
        verifyNoInteractions(metas, orcamentos);
    }

    @Test
    void trocaDeEmpresaDuranteConsultaImpedeRetorno() {
        when(contexto.getClienteAtualId()).thenReturn(7L, 8L);
        assertThrows(IllegalStateException.class, this::consultar);
    }

    @Test
    void limitesConfiguradosPrecisamSerValidos() {
        assertThrows(IllegalArgumentException.class, () -> new FinancialAlertsService(contexto, dashboard, metas,
                orcamentos, BigDecimal.ZERO, valor("50")));
        assertThrows(IllegalArgumentException.class, () -> new FinancialAlertsService(contexto, dashboard, metas,
                orcamentos, valor("50"), valor("20")));
    }

    @Test
    void centralRecebeTodosOsAvisosEnquantoPainelMantemTres() {
        when(orcamentos.listar()).thenReturn(List.of(orcamento(1, "150"), orcamento(2, "160"),
                orcamento(3, "170"), orcamento(4, "180")));
        assertEquals(3, consultar().alertas().size());
        assertEquals(4, service.consultarCompleto(inicio, fim).alertas().size());
    }
}
