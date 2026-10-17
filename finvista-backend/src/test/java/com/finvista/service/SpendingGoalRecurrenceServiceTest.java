package com.finvista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.controller.SpendingGoalRecurrenceController;
import com.finvista.dto.SpendingGoalRecurrenceRequest;
import com.finvista.model.Cliente;
import com.finvista.model.SpendingGoal;
import com.finvista.repository.SpendingGoalRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SpendingGoalRecurrenceServiceTest {

    private final SpendingGoalRepository repository = mock(SpendingGoalRepository.class);
    private final ClienteContextService context = mock(ClienteContextService.class);
    private final SpendingGoalRecurrenceService service = new SpendingGoalRecurrenceService(repository, context);

    @BeforeEach
    void administrador() {
        when(context.isAdmin()).thenReturn(true);
    }

    private SpendingGoalRecurrenceRequest pedido(String tipo, String inicio, String fim, String valor) {
        return new SpendingGoalRecurrenceRequest(" Operações ", tipo, LocalDate.parse(inicio), LocalDate.parse(fim),
                new BigDecimal(valor), 80, " Materiais ", " Operacional ");
    }

    @Test
    void anoComDozeMesesTemLimitesIndependentes() {
        var previa = service.previa(pedido("MENSAL", "2026-01-01", "2026-12-31", "10000"));
        assertEquals(12, previa.periodos().size());
        assertEquals(new BigDecimal("120000.00"), previa.limiteTotal());
        for (var periodo : previa.periodos()) {
            assertEquals(new BigDecimal("10000.00"), periodo.limite());
            assertFalse(periodo.proporcional());
        }
        verifyNoInteractions(repository);
    }

    @Test
    void mesParcialUsaDiasInclusivosEArredondamentoMonetario() {
        var previa = service.previa(pedido("MENSAL", "2026-01-15", "2026-02-10", "1000"));
        assertEquals(2, previa.periodos().size());
        assertEquals(17, previa.periodos().get(0).dias());
        assertEquals(31, previa.periodos().get(0).diasPeriodoCompleto());
        assertEquals(new BigDecimal("548.39"), previa.periodos().get(0).limite());
        assertEquals(new BigDecimal("357.14"), previa.periodos().get(1).limite());
        assertEquals(new BigDecimal("905.53"), previa.limiteTotal());
        assertTrue(previa.periodos().get(0).proporcional());
    }

    @Test
    void fevereiroBissextoTem29Dias() {
        var periodo = service.previa(pedido("MENSAL", "2028-02-15", "2028-02-29", "2900")).periodos().get(0);
        assertEquals(29, periodo.diasPeriodoCompleto());
        assertEquals(new BigDecimal("1500.00"), periodo.limite());
    }

    @Test
    void trimestreSegueJaneiroAbrilJulhoOutubro() {
        var previa = service.previa(pedido("TRIMESTRAL", "2026-02-15", "2026-07-31", "9000"));
        assertEquals(3, previa.periodos().size());
        assertEquals(LocalDate.parse("2026-03-31"), previa.periodos().get(0).fim());
        assertEquals(new BigDecimal("4500.00"), previa.periodos().get(0).limite());
        assertEquals(new BigDecimal("9000.00"), previa.periodos().get(1).limite());
        assertEquals(LocalDate.parse("2026-04-01"), previa.periodos().get(1).inicio());
    }

    @Test
    void semestreEAnoSeguemCalendario() {
        var semestre = service.previa(pedido("SEMESTRAL", "2026-01-01", "2026-12-31", "5000"));
        assertEquals(2, semestre.periodos().size());
        assertEquals(LocalDate.parse("2026-06-30"), semestre.periodos().get(0).fim());
        var ano = service.previa(pedido("ANUAL", "2027-01-01", "2028-12-31", "12000"));
        assertEquals(2, ano.periodos().size());
        assertEquals(366, ano.periodos().get(1).dias());
        assertEquals(new BigDecimal("24000.00"), ano.limiteTotal());
    }

    @Test
    void semRecorrenciaAplicaLimiteUmaVezAoIntervaloLivre() {
        var previa = service.previa(pedido("SEM_RECORRENCIA", "2026-01-15", "2027-06-04", "5000"));
        assertEquals(1, previa.periodos().size());
        assertFalse(previa.periodos().get(0).proporcional());
        assertEquals(new BigDecimal("5000.00"), previa.limiteTotal());
    }

    @Test
    void umDiaContaUmDiaSemLacunasOuSobreposicaoNaViradaDoAno() {
        var previa = service.previa(pedido("MENSAL", "2026-12-31", "2027-01-01", "3100"));
        assertEquals(2, previa.periodos().size());
        assertEquals(new BigDecimal("200.00"), previa.limiteTotal());
        assertEquals(previa.periodos().get(0).fim().plusDays(1), previa.periodos().get(1).inicio());
    }

    @Test
    void rejeitaPeriodoTipoValorOuAlertaInvalidosSemGravar() {
        for (var pedido : List.of(
                pedido("MENSAL", "2026-02-01", "2026-01-01", "100"),
                pedido("DIARIA", "2026-01-01", "2026-01-31", "100"),
                pedido("MENSAL", "2026-01-01", "2026-01-31", "0"),
                pedido("MENSAL", "2026-01-01", "2026-01-31", "1.001"),
                pedido("MENSAL", "2026-01-01", "2026-01-31", "10000000000000"),
                pedido("MENSAL", "2026-01-01", "2036-01-01", "100"),
                pedido("MENSAL", "2026-01-01", "2026-01-01", "0.01"))) {
            assertThrows(IllegalArgumentException.class, () -> service.criar(pedido));
        }
        var invalido = new SpendingGoalRecurrenceRequest("Nome", "MENSAL", LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"), BigDecimal.TEN, 101, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.criar(invalido));
        assertThrows(IllegalArgumentException.class, () -> service.criar(null));
        verifyNoInteractions(repository);
    }

    @Test
    void limitaA120PeriodosERejeitaNomeAusente() {
        assertEquals(120, service.previa(pedido("MENSAL", "2026-01-01", "2035-12-31", "100")).periodos().size());
        assertThrows(IllegalArgumentException.class,
                () -> service.previa(pedido("MENSAL", "2026-01-15", "2036-01-14", "100")));
        var p = pedido("MENSAL", "2026-01-01", "2026-12-31", "100");
        assertThrows(IllegalArgumentException.class, () -> service.previa(new SpendingGoalRecurrenceRequest(
                " ", p.recorrencia(), p.dataInicio(), p.dataFim(), p.valorLimite(), 80, null, null)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void criacaoUsaClienteDaSessaoEMesmaSerieParaTodosOsPeriodos() {
        var cliente = new Cliente("Empresa selecionada");
        when(context.getClienteAtual()).thenReturn(cliente);
        when(repository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        var criacao = service.criar(pedido("MENSAL", "2026-01-15", "2026-03-31", "1000"));
        ArgumentCaptor<List<SpendingGoal>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        var metas = captor.getValue();
        assertEquals(3, metas.size());
        assertEquals(3, criacao.periodosIds().size());
        for (var meta : metas) {
            assertSame(cliente, meta.getCliente());
            assertEquals(criacao.serieId(), meta.getSerieId());
            assertEquals("Operações", meta.getNome());
            assertEquals("Materiais", meta.getCategoria());
            assertEquals("Operacional", meta.getCentroCusto());
            assertEquals(new BigDecimal("1000.00"), meta.getLimitePeriodo());
            assertNull(meta.getId());
        }
        assertEquals(new BigDecimal("548.39"), metas.get(0).getValorLimite());
        verify(repository).flush();
    }

    @Test
    void clienteComumNaoPodeSimularCriarOuExcluir() {
        when(context.isAdmin()).thenReturn(false);
        var pedido = pedido("MENSAL", "2026-01-01", "2026-12-31", "100");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, () -> service.previa(pedido)).getStatusCode());
        assertThrows(ResponseStatusException.class, () -> service.criar(pedido));
        assertThrows(ResponseStatusException.class, () -> service.excluirSerie("00000000-0000-0000-0000-000000000001"));
        verifyNoInteractions(repository);
    }

    @Test
    void exclusaoDeSerieNaoAcessaOutraEmpresa() {
        String id = "00000000-0000-0000-0000-000000000001";
        when(context.getClienteAtualId()).thenReturn(4L);
        when(repository.findBySerieIdAndClienteIdOrderByDataInicioAsc(id, 4L)).thenReturn(List.of());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.excluirSerie(id)).getStatusCode());
        verify(repository, never()).deleteAll(any());
    }

    @Test
    void excluirSerieRemoveApenasPeriodosDaConsultaRestrita() {
        String id = "00000000-0000-0000-0000-000000000001";
        when(context.getClienteAtualId()).thenReturn(4L);
        var metas = List.of(new SpendingGoal(), new SpendingGoal());
        when(repository.findBySerieIdAndClienteIdOrderByDataInicioAsc(id, 4L)).thenReturn(metas);
        service.excluirSerie(id);
        verify(repository).deleteAll(metas);
        verify(repository).flush();
    }

    @Test
    void jsonNaoAceitaIdClienteOuPeriodosForjados() {
        var mapper = new ObjectMapper().findAndRegisterModules();
        for (String campo : List.of("id", "clienteId", "serieId", "periodos")) {
            assertThrows(Exception.class, () -> mapper.readValue("{\"" + campo + "\":1}", SpendingGoalRecurrenceRequest.class));
        }
    }

    @Test
    void endpointsDePreviaCriacaoEExclusaoRetornamStatusCorretos() throws Exception {
        var mocked = mock(SpendingGoalRecurrenceService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SpendingGoalRecurrenceController(mocked)).build();
        String json = "{\"nome\":\"Operações\",\"recorrencia\":\"MENSAL\",\"dataInicio\":\"2026-01-01\",\"dataFim\":\"2026-12-31\",\"valorLimite\":1000,\"percentualAlerta\":80}";
        mvc.perform(post("/api/metas-gastos/recorrencias/previa").contentType("application/json").content(json)).andExpect(status().isOk());
        mvc.perform(post("/api/metas-gastos/recorrencias").contentType("application/json").content(json)).andExpect(status().isCreated());
        mvc.perform(delete("/api/metas-gastos/recorrencias/00000000-0000-0000-0000-000000000001")).andExpect(status().isNoContent());
    }
}
