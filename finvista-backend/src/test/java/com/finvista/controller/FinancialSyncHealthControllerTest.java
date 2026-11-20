package com.finvista.controller;

import com.finvista.service.*;
import com.finvista.repository.*;
import com.finvista.model.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialSyncHealthControllerTest {

    final ClienteContextService ctx = mock(ClienteContextService.class);
    final ClienteService clientes = mock(ClienteService.class);
    final ContaAzulAutoRunRepository runs = mock(ContaAzulAutoRunRepository.class);
    final ContaAzulAutoItemRepository itens = mock(ContaAzulAutoItemRepository.class);
    final ContaAzulConnectionRepository conexoes = mock(ContaAzulConnectionRepository.class);
    final Instant now = Instant.parse("2026-11-19T12:00:00Z");
    final FinancialSyncHealthController c = new FinancialSyncHealthController(ctx, clientes, runs, itens, conexoes, Clock.fixed(now, ZoneOffset.UTC));

    @BeforeEach
    void setup() {
        when(ctx.getClienteAtualId()).thenReturn(7L);
        when(conexoes.findById(7L)).thenReturn(Optional.of(new ContaAzulConnection(7L)));
    }

    @Test
    void missingSuccessfulRunWarnsWithoutPretendingZeroAge() {
        var r = c.consultar(false, null).getBody().get(0);
        assertNull(r.idadeSegundos());
        assertTrue(r.atrasada());
        assertEquals("SEM_CONFERENCIA", r.conferencia());
    }

    @Test
    void moreThan24HoursWarnsAndCountsLatestPendingParcels() {
        var run = new ContaAzulAutoRun();
        run.concluidoEm = now.minusSeconds(86401);
        run.conferenciaStatus = "DIVERGENTE";
        when(runs.findFirstByClienteIdAndStatusInOrderByConcluidoEmDesc(eq(7L), any())).thenReturn(Optional.of(run));
        when(itens.countPendenciasAtuais(7L)).thenReturn(3L);
        var r = c.consultar(false, null).getBody().get(0);
        assertTrue(r.atrasada());
        assertEquals(3, r.pendencias());
        assertEquals("DIVERGENTE", r.conferencia());
    }

    @Test
    void exactly24HoursDoesNotExceedThreshold() {
        var run = new ContaAzulAutoRun();
        run.concluidoEm = now.minusSeconds(86400);
        when(runs.findFirstByClienteIdAndStatusInOrderByConcluidoEmDesc(eq(7L), any())).thenReturn(Optional.of(run));
        assertFalse(c.consultar(false, null).getBody().get(0).atrasada());
    }

    @Test
    void groupAuthorizationIsCheckedBeforeReadingAnyFinancialMetadata() {
        when(clientes.listarEmpresasDoCliente(99L)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> c.consultar(true, 99L));
        verifyNoInteractions(runs, itens, conexoes);
    }

    @Test
    void missingSessionDoesNotReadRepositories() {
        when(ctx.getClienteAtualId()).thenThrow(new IllegalStateException("Sessão expirada"));
        assertThrows(IllegalStateException.class, () -> c.consultar(false, null));
        verifyNoInteractions(runs, itens, conexoes);
    }
}
       