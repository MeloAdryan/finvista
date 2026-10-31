package com.finvista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.integration.contaazul.*;
import com.finvista.model.*;
import com.finvista.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaAzulOriginReviewServiceTest {

    final ClienteContextService ctx = mock(ClienteContextService.class);
    final FinancialTransactionRepository tx = mock(FinancialTransactionRepository.class);
    final ContaAzulApiOperationRepository ops = mock(ContaAzulApiOperationRepository.class);
    final ContaAzulConnectionService access = mock(ContaAzulConnectionService.class);
    final ContaAzulParcelDetailClient details = mock(ContaAzulParcelDetailClient.class);
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final Clock clock = Clock.fixed(Instant.parse("2026-10-30T15:00:00Z"), ZoneOffset.UTC);
    final String id = "02d6d764-de48-472c-ade0-6b2d3475feb5";
    FinancialTransaction t;
    Cliente company;
    Usuario user;
    ContaAzulOriginReviewService service;

    @BeforeEach
    void setup() {
        company = new Cliente("Ricker");
        ReflectionTestUtils.setField(company, "id", 2L);
        user = new Usuario("Contador", "contador@example.test", "hash", "ADMIN");
        ReflectionTestUtils.setField(user, "id", 9L);
        t = new FinancialTransaction(LocalDate.of(2026, 10, 1), "DARF", "DESPESA", new BigDecimal("722.39"), "Impostos", "Geral", "CONTA_AZUL_API", id);
        t.setCliente(company);
        t.setSituacao("PENDENTE");
        t.setValorTotalRealizado(BigDecimal.ZERO);
        t.setValorRealizado(BigDecimal.ZERO);
        t.setValorTotalAberto(new BigDecimal("722.39"));
        ReflectionTestUtils.setField(t, "id", 1393L);
        when(ctx.getClienteAtualId()).thenReturn(2L);
        when(ctx.getClienteAtual()).thenReturn(company);
        when(ctx.getUsuarioAutenticado()).thenReturn(user);
        when(tx.findByClienteIdAndOrigemOrderByIdAsc(2L, "CONTA_AZUL_API")).thenReturn(List.of(t));
        String snapshot = "{\"id\":\"" + id + "\",\"valor_pago\":0,\"baixas\":[]}";
        when(ops.findFirstByClienteIdAndLancamentoIdOrderByConcluidoEmDescIdDesc(2L, 1393L)).thenReturn(Optional.of(new ContaAzulApiOperation(UUID.randomUUID(), company, 9L, id, "INCLUIR", 1393L, clock.instant(), null, "{}", snapshot)));
        when(access.acessoParaConsulta(2L)).thenReturn("token-teste");
        when(details.consultar("token-teste", id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        service = new ContaAzulOriginReviewService(ctx, tx, ops, access, details, json, clock);
    }

    ContaAzulOriginReviewService.Request request() {
        return new ContaAzulOriginReviewService.Request(2L, id, "Contador conferiu a remoção no documento original.", null);
    }

    @Test
    void consulta404NaoCancelaSozinha() {
        var plan = service.plan(request());
        assertEquals("PENDENTE", t.getSituacao());
        assertEquals(new BigDecimal("722.39"), plan.aberto());
        verify(tx, never()).saveAndFlush(any());
    }

    @Test
    void confirmacaoAuditaSemApagarValores() {
        var plan = service.plan(request());
        service.apply(plan);
        assertEquals("CANCELADO", t.getSituacao());
        assertEquals(new BigDecimal("722.39"), t.getValorTotalAberto());
        assertTrue(t.getObservacoes().contains("usuário #9"));
        verify(ops).saveAndFlush(any());
        assertTrue(FinancialOpenItems.read(List.of(t), LocalDate.of(2026, 10, 30)).isEmpty());
        verify(details, times(2)).consultar("token-teste", id);
    }

    @Test
    void pagoOuBaixaParcialImpedeCancelamentoManual() {
        t.setValorTotalRealizado(BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, () -> service.plan(request()));
        assertEquals("PENDENTE", t.getSituacao());
        verifyNoInteractions(details);
    }

    @Test
    void saldoAbertoDesconhecidoNaoViraZero() {
        t.setValorTotalAberto(null);
        assertThrows(IllegalArgumentException.class, () -> service.plan(request()));
        assertEquals("PENDENTE", t.getSituacao());
        verifyNoInteractions(details);
    }

    @Test
    void semCapturaNaoInventaAusenciaDePagamento() {
        when(ops.findFirstByClienteIdAndLancamentoIdOrderByConcluidoEmDescIdDesc(2L, 1393L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.plan(request()));
        verifyNoInteractions(details);
    }

    @Test
    void servidorIndisponivelNaoComprovaRemocao() {
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY)).when(details).consultar("token-teste", id);
        assertThrows(ResponseStatusException.class, () -> service.plan(request()));
        assertEquals("PENDENTE", t.getSituacao());
    }

    @Test
    void reaparecimentoAntesDaConfirmacaoBloqueia() {
        var p = service.plan(request());
        doReturn(null).when(details).consultar("token-teste", id);
        assertThrows(IllegalArgumentException.class, () -> service.apply(p));
        assertEquals("PENDENTE", t.getSituacao());
        verify(tx, never()).saveAndFlush(any());
    }

    @Test
    void outraEmpresaNaoPodeSerConsultada() {
        when(ctx.getClienteAtualId()).thenReturn(3L);
        assertThrows(IllegalArgumentException.class, () -> service.plan(request()));
        verifyNoInteractions(tx, details);
    }

    @Test
    void alteracaoConcorrenteBloqueiaConfirmacao() {
        var p = service.plan(request());
        t.setValorTotalAberto(BigDecimal.TEN);
        assertThrows(ResponseStatusException.class, () -> service.apply(p));
        assertEquals("PENDENTE", t.getSituacao());
        verify(tx, never()).saveAndFlush(any());
    }

    @Test
    void perfilClienteNaoPodeRevisar() {
        doThrow(new IllegalArgumentException("Somente administrador")).when(ctx).validarAdministrador();
        assertThrows(IllegalArgumentException.class, () -> service.plan(request()));
        verifyNoInteractions(tx, details);
    }
}
