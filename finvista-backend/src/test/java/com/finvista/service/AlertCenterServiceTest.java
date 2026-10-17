package com.finvista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.dto.FinancialAlertsResponse;
import com.finvista.dto.FinancialAlertsResponse.*;
import com.finvista.model.*;
import com.finvista.model.FinancialAlertRecord.Estado;
import com.finvista.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertCenterServiceTest {
    private final FinancialAlertsService regras = mock(FinancialAlertsService.class);
    private final FinancialAlertRecordRepository registros = mock(FinancialAlertRecordRepository.class);
    private final FinancialAlertReadRepository leituras = mock(FinancialAlertReadRepository.class);
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final ClienteContextService contexto = mock(ClienteContextService.class);
    private final FinancialReferenceService referencia = mock(FinancialReferenceService.class);
    private final Clock clock = mock(Clock.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final AlertCenterService service = new AlertCenterService(regras, registros, leituras, clientes, contexto, referencia, json, clock);
    private final Cliente cliente = new Cliente("Empresa de teste");
    private final Usuario usuario = mock(Usuario.class);
    private final LocalDate inicio = LocalDate.of(2026, 9, 1);
    private final LocalDate fim = LocalDate.of(2026, 9, 30);
    private final Instant agora = Instant.parse("2026-10-11T12:00:00Z");
    private final Map<String, FinancialAlertRecord> banco = new LinkedHashMap<>();

    @BeforeEach
    void configurar() {
        ReflectionTestUtils.setField(cliente, "id", 7L);
        when(contexto.getClienteAtualId()).thenReturn(7L);
        when(contexto.getUsuarioAutenticado()).thenReturn(usuario);
        when(usuario.getId()).thenReturn(23L);
        when(clientes.bloquearAtivoPorId(7L)).thenReturn(Optional.of(cliente));
        when(clock.instant()).thenReturn(agora);
        when(referencia.obterMesReferencia()).thenReturn(YearMonth.of(2026, 9));
        when(registros.findByClienteIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(eq(7L), any(), any()))
                .thenAnswer(invocation -> new ArrayList<>(banco.values()));
        when(registros.save(any())).thenAnswer(invocation -> {
            FinancialAlertRecord item = invocation.getArgument(0);
            if (item.getId() == null) ReflectionTestUtils.setField(item, "id", 100L + banco.size());
            banco.put(item.getChave(), item);
            return item;
        });
        when(registros.findByIdAndClienteId(anyLong(), eq(7L))).thenAnswer(invocation -> banco.values().stream()
                .filter(item -> item.getId().equals(invocation.getArgument(0))).findFirst());
        resposta(Comparacao.DISPONIVEL, alerta("META:7:1", Tipo.META_LIMITE, Gravidade.ATENCAO));
    }

    private Alerta alerta(String chave, Tipo tipo, Gravidade gravidade) {
        return new Alerta(chave, tipo, gravidade, 1L, "Aviso", "Regra atingida", inicio, fim,
                new BigDecimal("90"), new BigDecimal("100"), new BigDecimal("-10"), new BigDecimal("90"),
                new BigDecimal("80"), null, null, null, null, true);
    }
    private void resposta(Comparacao comparacao, Alerta... alertas) {
        when(regras.consultarCompleto(any(), any())).thenReturn(new FinancialAlertsResponse(7L, inicio, fim,
                null, alertas.length, List.of(alertas), comparacao));
    }
    private void atualizar() { service.atualizar(inicio, fim); }
    private FinancialAlertRecord primeiro() { return banco.values().iterator().next(); }

    @Test
    void reavaliarNaoDuplicaAvisoNemAumentaOcorrencias() {
        atualizar();
        atualizar();
        assertEquals(1, banco.size());
        assertEquals(1, primeiro().getOcorrencias());
        assertEquals(agora, primeiro().getNotificadoEm());
        verify(clientes, times(2)).bloquearAtivoPorId(7L);
    }

    @Test
    void centralRegistraTodosOsAvisosSemLimiteDoPainel() {
        resposta(Comparacao.DISPONIVEL, alerta("META:7:1", Tipo.META_LIMITE, Gravidade.ATENCAO),
                alerta("META:7:2", Tipo.META_LIMITE, Gravidade.ATENCAO),
                alerta("META:7:3", Tipo.META_LIMITE, Gravidade.ATENCAO),
                alerta("ORCAMENTO:7:1", Tipo.ORCAMENTO_EXCEDENTE, Gravidade.CRITICO));
        atualizar();
        assertEquals(4, banco.size());
    }

    @Test
    void regraAusenteResolveEReaparecimentoReativaMesmoRegistro() {
        atualizar();
        Long id = primeiro().getId();
        resposta(Comparacao.DISPONIVEL);
        when(clock.instant()).thenReturn(agora.plusSeconds(60));
        atualizar();
        assertEquals(Estado.RESOLVIDO, primeiro().getEstado());
        assertEquals(agora.plusSeconds(60), primeiro().getResolvidoEm());
        resposta(Comparacao.DISPONIVEL, alerta("META:7:1", Tipo.META_LIMITE, Gravidade.ATENCAO));
        when(clock.instant()).thenReturn(agora.plusSeconds(120));
        atualizar();
        assertEquals(id, primeiro().getId());
        assertEquals(Estado.ATIVO, primeiro().getEstado());
        assertEquals(2, primeiro().getOcorrencias());
        assertEquals(agora.plusSeconds(120), primeiro().getNotificadoEm());
    }

    @Test
    void semBaseNaoResolveAumentoAntigoComoSeEstivesseNormal() {
        resposta(Comparacao.DISPONIVEL, alerta("DESPESAS:7:1", Tipo.AUMENTO_DESPESAS, Gravidade.ATENCAO));
        atualizar();
        for (Comparacao comparacao : List.of(Comparacao.SEM_REGISTROS, Comparacao.BASE_NAO_POSITIVA, Comparacao.PERIODO_EM_ANDAMENTO)) {
            resposta(comparacao);
            when(clock.instant()).thenReturn(agora.plusSeconds(60));
            atualizar();
            assertEquals(Estado.ATIVO, primeiro().getEstado());
            assertEquals(agora, primeiro().getVerificadoEm());
        }
    }

    @Test
    void outroPeriodoNaoResolveAvisoDeAumentoExistente() {
        resposta(Comparacao.DISPONIVEL, alerta("DESPESAS:7:1", Tipo.AUMENTO_DESPESAS, Gravidade.ATENCAO));
        atualizar();
        when(regras.consultarCompleto(any(), any())).thenReturn(new FinancialAlertsResponse(7L, inicio.minusMonths(1), fim,
                null, 0, List.of(), Comparacao.DISPONIVEL));
        atualizar();
        assertEquals(Estado.ATIVO, primeiro().getEstado());
    }

    @Test
    void agravamentoNotificaNovamenteSemNovaLinha() {
        atualizar();
        resposta(Comparacao.DISPONIVEL, alerta("META:7:1", Tipo.META_LIMITE, Gravidade.CRITICO));
        when(clock.instant()).thenReturn(agora.plusSeconds(30));
        atualizar();
        assertEquals(1, banco.size());
        assertEquals(1, primeiro().getOcorrencias());
        assertEquals(agora.plusSeconds(30), primeiro().getNotificadoEm());
    }

    @Test
    void marcarLidoUsaUsuarioAtualENaoResolveAviso() {
        atualizar();
        when(leituras.findByAlertaIdAndUsuarioId(100L, 23L)).thenReturn(Optional.empty());
        service.marcarLido(100L);
        verify(leituras).save(any(FinancialAlertRead.class));
        assertEquals(Estado.ATIVO, primeiro().getEstado());
        verify(registros).findByIdAndClienteId(100L, 7L);
    }

    @Test
    void marcarLidoNovamenteEhIdempotente() {
        atualizar();
        var leitura = new FinancialAlertRead(primeiro(), usuario, agora);
        when(leituras.findByAlertaIdAndUsuarioId(100L, 23L)).thenReturn(Optional.of(leitura));
        service.marcarLido(100L);
        verify(leituras, never()).save(any());
    }

    @Test
    void alertaDeOutraEmpresaNaoPodeSerMarcado() {
        assertThrows(ResponseStatusException.class, () -> service.marcarLido(999L));
        verifyNoInteractions(leituras);
    }

    @Test
    @SuppressWarnings("unchecked")
    void leituraEhIndividualEReativacaoVoltaANaoLido() {
        atualizar();
        when(registros.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(primeiro()), PageRequest.of(0, 20), 1));
        when(leituras.findByAlertaClienteIdAndUsuarioIdAndAlertaIdIn(7L, 23L, List.of(100L)))
                .thenReturn(List.of(new FinancialAlertRead(primeiro(), usuario, agora)));
        assertTrue(service.listar(inicio, fim, null, null, null, null, 0, 20).registros().get(0).lido());
        when(usuario.getId()).thenReturn(24L);
        when(leituras.findByAlertaClienteIdAndUsuarioIdAndAlertaIdIn(7L, 24L, List.of(100L))).thenReturn(List.of());
        assertFalse(service.listar(inicio, fim, null, null, null, null, 0, 20).registros().get(0).lido());
        when(usuario.getId()).thenReturn(23L);
        resposta(Comparacao.DISPONIVEL);
        atualizar();
        resposta(Comparacao.DISPONIVEL, alerta("META:7:1", Tipo.META_LIMITE, Gravidade.ATENCAO));
        when(clock.instant()).thenReturn(agora.plusSeconds(60));
        atualizar();
        assertFalse(service.listar(inicio, fim, null, null, null, null, 0, 20).registros().get(0).lido());
    }

    @Test
    void rejeitaPaginacaoEDatasInvalidasAntesDeLerBanco() {
        assertThrows(IllegalArgumentException.class, () -> service.listar(inicio, null, null, null, null, null, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.listar(inicio, fim, null, null, null, null, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.listar(inicio, fim, null, null, null, null, 0, 51));
        verifyNoInteractions(registros);
    }

    @Test
    void trocaDeEmpresaImpedeAtualizacao() {
        when(contexto.getClienteAtualId()).thenReturn(7L, 8L);
        assertThrows(IllegalStateException.class, this::atualizar);
        verify(registros, never()).save(any());
    }
}
