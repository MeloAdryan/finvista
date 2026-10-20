package com.finvista.service;

import com.finvista.integration.contaazul.*;
import com.finvista.repository.ContaAzulConnectionRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialAccountsServiceTest {

    ClienteContextService contexto;
    ContaAzulConnectionRepository conexoes;
    ContaAzulAutoAccess acesso;
    ContaAzulAccountsClient api;
    Clock clock;
    FinancialAccountsService service;
    final Instant agora = Instant.parse("2026-10-19T12:00:00Z");

    @BeforeEach
    void setup() {
        contexto = mock(ClienteContextService.class);
        conexoes = mock(ContaAzulConnectionRepository.class);
        acesso = mock(ContaAzulAutoAccess.class);
        api = mock(ContaAzulAccountsClient.class);
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(agora);
        when(contexto.getClienteAtualId()).thenReturn(7L);
        when(conexoes.existsById(7L)).thenReturn(true);
        when(acesso.acesso(7L)).thenReturn("token7");
        service = new FinancialAccountsService(contexto, conexoes, acesso, api, clock);
    }

    ContaAzulAccountsClient.Conta conta(String id, String tipo) {
        return new ContaAzulAccountsClient.Conta(id, id, tipo, true);
    }

    @Test
    void somaSomenteBancosCaixaAplicacoesSemCartaoOuCobranca() {
        when(api.listar("token7")).thenReturn(List.of(conta("a", "CONTA_CORRENTE"), conta("b", "POUPANCA"), conta("c", "CARTAO_CREDITO"), conta("d", "COBRANCAS_CONTA_AZUL")));
        when(api.saldo("token7", "a")).thenReturn(new BigDecimal("100.20"));
        when(api.saldo("token7", "b")).thenReturn(new BigDecimal("-20.10"));
        var r = service.consultar();
        assertEquals(new BigDecimal("80.10"), r.saldo());
        assertEquals(2, r.contasExcluidas());
        assertEquals(agora, r.consultadoEm());
        assertEquals("DISPONIVEL", r.estado());
        verify(api, never()).saldo("token7", "c");
    }

    @Test
    void naoCompartilhaCacheEntreClientes() {
        when(api.listar("token7")).thenReturn(List.of());
        service.consultar();
        when(contexto.getClienteAtualId()).thenReturn(8L);
        when(conexoes.existsById(8L)).thenReturn(true);
        when(acesso.acesso(8L)).thenReturn("token8");
        when(api.listar("token8")).thenReturn(List.of());
        assertEquals(8L, service.consultar().clienteId());
        verify(acesso).acesso(8L);
        verify(api).listar("token8");
    }

    @Test
    void reutilizaCacheEAposVencimentoAtualiza() {
        when(api.listar("token7")).thenReturn(List.of());
        service.consultar();
        service.consultar();
        verify(api, times(1)).listar("token7");
        when(clock.instant()).thenReturn(agora.plusSeconds(121));
        service.consultar();
        verify(api, times(2)).listar("token7");
    }

    @Test
    void semConexaoNaoConsultaApi() {
        when(conexoes.existsById(7L)).thenReturn(false);
        var r = service.consultar();
        assertEquals("NAO_CONECTADO", r.estado());
        assertNull(r.saldo());
        verifyNoInteractions(acesso, api);
    }

    @Test
    void falhaParcialNuncaRetornaTotalParcialOuZero() {
        when(api.listar("token7")).thenReturn(List.of(conta("a", "CAIXINHA"), conta("b", "CONTA_CORRENTE")));
        when(api.saldo("token7", "a")).thenReturn(BigDecimal.TEN);
        when(api.saldo("token7", "b")).thenThrow(new IllegalStateException("segredo"));
        var r = service.consultar();
        assertNull(r.saldo());
        assertTrue(r.contas().isEmpty());
        assertFalse(r.mensagem().contains("segredo"));
    }

    @Test
    void erroAposSucessoPreservaSaldoComHorarioAntigoEAviso() {
        when(api.listar("token7")).thenReturn(List.of(conta("a", "CAIXINHA")));
        when(api.saldo("token7", "a")).thenReturn(BigDecimal.TEN);
        service.consultar();
        when(clock.instant()).thenReturn(agora.plusSeconds(121));
        when(api.listar("token7")).thenThrow(new IllegalStateException());
        var r = service.consultar();
        assertEquals(BigDecimal.TEN, r.saldo());
        assertTrue(r.desatualizado());
        assertEquals(agora, r.consultadoEm());
    }

    @Test
    void conexaoRemovidaDescartaSaldoEmCache() {
        when(api.listar("token7")).thenReturn(List.of(conta("a", "CAIXINHA")));
        when(api.saldo("token7", "a")).thenReturn(BigDecimal.TEN);
        service.consultar();
        when(conexoes.existsById(7L)).thenReturn(false);
        assertNull(service.consultar().saldo());
    }

    @Test
    void distingueFalhaDeRenovacaoSemExporMensagemOriginal() {
        when(acesso.acesso(7L)).thenThrow(new IllegalStateException("client_secret=nao-expor"));
        var r = service.consultar();
        assertEquals("ACESSO_NAO_RENOVADO", r.diagnostico().codigo());
        assertEquals("ACESSO", r.diagnostico().etapa());
        assertFalse(r.mensagem().contains("nao-expor"));
        assertNull(r.saldo());
        verifyNoInteractions(api);
    }

    @Test
    void distinguePermissaoNegadaDeFalhaDeRede() {
        when(api.listar("token7")).thenThrow(new ContaAzulAccountsException("HTTP_EXTERNO", "LISTAGEM", 403));
        var r = service.consultar();
        assertEquals(403, r.diagnostico().httpStatus());
        assertTrue(r.mensagem().contains("permissões"));
        when(clock.instant()).thenReturn(agora.plusSeconds(31));
        doThrow(new ContaAzulAccountsException("REDE_OU_TEMPO", "LISTAGEM", null)).when(api).listar("token7");
        var rede = service.consultar();
        assertEquals("REDE_OU_TEMPO", rede.diagnostico().codigo());
        assertTrue(rede.mensagem().contains("conexão"));
    }

    @Test
    void preservaDiagnosticoDaContaSemSaldoSemTransformarEmZero() {
        when(api.listar("token7")).thenReturn(List.of(conta("a", "CAIXINHA")));
        when(api.saldo("token7", "a")).thenThrow(new ContaAzulAccountsException("SALDO_AUSENTE", "SALDO", null));
        var r = service.consultar();
        assertNull(r.saldo());
        assertEquals("SALDO_AUSENTE", r.diagnostico().codigo());
        assertEquals("SALDO", r.diagnostico().etapa());
    }

    @Test
    void sucessoPosteriorRemoveDiagnosticoAntigo() {
        when(api.listar("token7")).thenThrow(new ContaAzulAccountsException("HTTP_EXTERNO", "LISTAGEM", 429));
        service.consultar();
        when(clock.instant()).thenReturn(agora.plusSeconds(31));
        doReturn(List.of()).when(api).listar("token7");
        var r = service.consultar();
        assertNull(r.diagnostico());
        assertEquals("SEM_CONTAS", r.estado());
    }

}
