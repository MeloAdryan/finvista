package com.finvista.integration.contaazul;

import com.finvista.model.ContaAzulAutoItem;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ContaAzulAutoWorkerTest {

    private final ContaAzulAutoState state = mock(ContaAzulAutoState.class);
    private final ContaAzulAutoAccess access = mock(ContaAzulAutoAccess.class);
    private final ContaAzulFinancialClient financial = mock(ContaAzulFinancialClient.class);
    private final ContaAzulParcelDetailClient details = mock(ContaAzulParcelDetailClient.class);
    private final ContaAzulAutoState.Reserva reserva = new ContaAzulAutoState.Reserva(7L, UUID.randomUUID(), UUID.randomUUID());
    private ContaAzulAutoWorker worker;

    @BeforeEach
    void preparar() throws Exception {
        worker = spy(new ContaAzulAutoWorker(state, access, financial, details));
        doNothing().when(worker).limitar();
        when(state.assumir(7L)).thenReturn(reserva);
        when(access.acesso(7L)).thenReturn("access-teste");
        when(state.cursor(reserva)).thenReturn(new ContaAzulAutoState.Cursor(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-31"), 2, 1));
    }

    private ContaAzulAutoItem item() {
        var i = new ContaAzulAutoItem();
        i.id = UUID.randomUUID();
        i.clienteId = 7L;
        i.parcelaId = "9986f173-f531-4660-96ae-04b71c879264";
        return i;
    }

    @Test
    void semReservaNaoConsultaFonte() {
        when(state.assumir(7L)).thenReturn(null);
        worker.executar(7L);
        verifyNoInteractions(access, financial, details);
    }

    @Test
    void semFilaESemPaginasFinaliza() {
        worker.executar(7L);
        verify(state).devolver(reserva, true);
        verifyNoInteractions(access, financial, details);
    }

    @Test
    void erro429PreservaFilaEAgendaRepeticao() {
        var i = item();
        when(state.proximo(reserva)).thenReturn(i);
        when(details.consultar(any(), any())).thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS));
        worker.executar(7L);
        verify(state).falhar(eq(reserva), contains("Limite"), eq(false));
        verify(state, never()).pendencia(any(), any(), any());
    }

    @Test
    void conflitoDeParcelaViraPendenciaEContinua() {
        var i = item();
        when(state.proximo(reserva)).thenReturn(i, (ContaAzulAutoItem) null);
        when(details.consultar(any(), any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Vínculo exige revisão"));
        worker.executar(7L);
        verify(state).pendencia(reserva, i.id, "Vínculo exige revisão");
        verify(state).devolver(reserva, true);
    }

    @Test
    void paraDepoisDeVinteEtapasSemReiniciarFila() {
        when(state.proximo(reserva)).thenReturn(item());
        worker.executar(7L);
        verify(details, times(20)).consultar(any(), any());
        verify(state).devolver(reserva, false);
    }

    @Test
    void perdaDaReservaNaoSobrescreveStatusDaNovaInstancia() {
        when(state.proximo(reserva)).thenThrow(new ContaAzulAutoState.ReservaPerdida());
        worker.executar(7L);
        verifyNoInteractions(access, financial, details);
        verify(state, never()).falhar(any(), any(), anyBoolean());
    }

    @Test
    void paginasConsultadasUsamSomentePeriodoETipoDaReserva() {
        var cursor = new ContaAzulAutoState.Cursor(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-31"), 1, 3);
        when(state.cursor(reserva)).thenReturn(cursor, new ContaAzulAutoState.Cursor(cursor.inicio(), cursor.fim(), 2, 1));
        var pagina = new ContaAzulFinancialClient.Pagina(0, List.of());
        when(financial.consultar(any(), any(), any(), any(), anyInt())).thenReturn(pagina);
        worker.executar(7L);
        verify(financial).consultar("access-teste", ContaAzulFinancialClient.Tipo.DESPESA, cursor.inicio(), cursor.fim(), 3);
        verify(state).pagina(reserva, pagina);
    }

    @Test
    void completaPercorreReceitasDespesasTransferenciasESaldos() throws Exception {
        var source = mock(ContaAzulMovementClient.class);
        worker = spy(new ContaAzulAutoWorker(state, access, financial, details, source));
        doNothing().when(worker).limitar();
        var a = ContaAzulFullScope.INICIO;
        var b = ContaAzulFullScope.FIM;
        when(state.cursor(reserva)).thenReturn(new ContaAzulAutoState.Cursor(a, b, 0, 1, true), new ContaAzulAutoState.Cursor(a, b, 1, 1, true), new ContaAzulAutoState.Cursor(a, b, 2, 1, true), new ContaAzulAutoState.Cursor(a, b, 3, 1, true), new ContaAzulAutoState.Cursor(a, b, 4, 1, true));
        when(financial.consultar(any(), any(), any(), any(), anyInt())).thenReturn(new ContaAzulFinancialClient.Pagina(0, List.of()));
        var raw = new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"itens_totais\":0,\"itens\":[]}");
        when(source.consultar(any(), any(), any(), any(), anyInt())).thenReturn(new ContaAzulMovementClient.Pagina(0, List.of(), raw));
        worker.executar(7L);
        verify(financial).consultar("access-teste", ContaAzulFinancialClient.Tipo.RECEITA, a, b, 1);
        verify(financial).consultar("access-teste", ContaAzulFinancialClient.Tipo.DESPESA, a, b, 1);
        verify(source).consultar("access-teste", "TRANSFERENCIA", a, b, 1);
        verify(source).consultar("access-teste", "SALDO_INICIAL", a, b, 1);
        verify(state, times(2)).paginaMovimentos(eq(reserva), any());
        verify(state).devolver(reserva, true);
    }

    @Test
    void completaPreservaFonteAntesDeAplicarParcela() throws Exception {
        var source = mock(ContaAzulMovementClient.class);
        worker = spy(new ContaAzulAutoWorker(state, access, financial, details, source));
        doNothing().when(worker).limitar();
        var i = item();
        when(state.proximo(reserva)).thenReturn(i, (ContaAzulAutoItem) null);
        when(state.cursor(reserva)).thenReturn(new ContaAzulAutoState.Cursor(ContaAzulFullScope.INICIO, ContaAzulFullScope.FIM, 0, 1, true), new ContaAzulAutoState.Cursor(ContaAzulFullScope.INICIO, ContaAzulFullScope.FIM, 4, 1, true));
        var p = mock(ContaAzulParcelDetail.class);
        var raw = new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"campo_extra\":true}");
        when(details.consultarCompleto("access-teste", i.parcelaId)).thenReturn(new ContaAzulParcelDetailClient.Consulta(p, raw));
        worker.executar(7L);
        var order = inOrder(state);
        order.verify(state).arquivar(reserva, "PARCELA", i.parcelaId, raw);
        order.verify(state).aplicar(reserva, i.id, p);
        verify(details, never()).consultar(any(), any());
    }

    @Test
    void falhaDeAcessoGeraReferenciaSeguraEPreservaFila() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ContaAzulAutoWorker.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(state.proximo(reserva)).thenReturn(item());
            when(access.acesso(7L)).thenThrow(new IllegalStateException("refresh_token=NAO_EXIBIR"));
            worker.executar(7L);
            verify(state).falhar(eq(reserva), contains("Referência:"), eq(false));
            verify(state, never()).pendencia(any(), any(), any());
            verify(state, never()).aplicar(any(), any(), any());
            var line = appender.list.get(0).getFormattedMessage();
            assertTrue(line.contains("etapa=ACESSO"));
            assertTrue(line.contains("IllegalStateException"));
            assertFalse(line.contains("NAO_EXIBIR"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void falhaAoArquivarRegistraEtapaSemVazarConteudo() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ContaAzulAutoWorker.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var i = item();
            when(state.proximo(reserva)).thenReturn(i);
            when(state.cursor(reserva)).thenReturn(new ContaAzulAutoState.Cursor(ContaAzulFullScope.INICIO, ContaAzulFullScope.FIM, 0, 1, true));
            var raw = new com.fasterxml.jackson.databind.ObjectMapper().readTree("{}");
            when(details.consultarCompleto("access-teste", i.parcelaId)).thenReturn(new ContaAzulParcelDetailClient.Consulta(mock(ContaAzulParcelDetail.class), raw));
            doThrow(new IllegalStateException("password=NAO_EXIBIR")).when(state).arquivar(any(), any(), any(), any());
            worker.executar(7L);
            var line = appender.list.get(0).getFormattedMessage();
            assertTrue(line.contains("etapa=ARQUIVAR_PARCELA"));
            assertFalse(line.contains("NAO_EXIBIR"));
            verify(state).falhar(eq(reserva), contains("Referência:"), eq(false));
            verify(state, never()).aplicar(any(), any(), any());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
