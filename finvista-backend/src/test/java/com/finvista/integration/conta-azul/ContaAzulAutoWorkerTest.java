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
}
