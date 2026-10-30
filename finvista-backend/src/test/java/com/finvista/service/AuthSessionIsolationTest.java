package com.finvista.service;

import com.finvista.model.*;
import com.finvista.repository.ClienteRepository;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSessionIsolationTest {

    Usuario user;
    Cliente ricker, other;
    UsuarioService users;
    ClienteRepository clients;
    MockHttpServletRequest request;
    ClienteContextService ctx;

    @BeforeEach
    void init() {
        ricker = new Cliente("Ricker");
        other = new Cliente("Teste");
        ReflectionTestUtils.setField(ricker, "id", 2L);
        ReflectionTestUtils.setField(other, "id", 3L);
        user = new Usuario("Ricker", "ricker@example.test", "hash-original", "CLIENTE");
        ReflectionTestUtils.setField(user, "id", 10L);
        user.setCliente(ricker);
        users = mock(UsuarioService.class);
        clients = mock(ClienteRepository.class);
        when(users.buscarPorEmail("ricker@example.test")).thenReturn(Optional.of(user));
        request = new MockHttpServletRequest();
        ctx = new ClienteContextService(users, clients, request);
        authenticate();
    }

    void authenticate() {
        var auth = new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of());
        auth.setDetails(AuthSessionProof.session(user));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        org.springframework.transaction.support.TransactionSynchronizationManager.clear();
    }

    @Test
    void clienteIgnoraEmpresaSelecionadaDeOutraSessao() {
        request.getSession().setAttribute(ClienteContextService.CLIENTE_SELECIONADO_SESSION_KEY, 3L);
        assertEquals(2L, ctx.getClienteAtualId());
        verifyNoInteractions(clients);
    }

    @Test
    void clienteNaoPodeSelecionarOutraEmpresa() {
        assertThrows(IllegalStateException.class, () -> ctx.selecionarCliente(3L));
        assertEquals(2L, ctx.getClienteAtualId());
        verifyNoInteractions(clients);
    }

    @Test
    void novaSenhaInvalidaSessaoAntiga() {
        var session = (org.springframework.mock.web.MockHttpSession) request.getSession();
        user.setSenhaHash("novo-hash");
        assertThrows(ResponseStatusException.class, ctx::getClienteAtual);
        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void trocaDoVinculoNaoPermiteSessaoAntigaAcessarNovaEmpresa() {
        user.setCliente(other);
        assertThrows(ResponseStatusException.class, ctx::getClienteAtual);
        verifyNoInteractions(clients);
    }

    @Test
    void sessaoAnteriorAoRecursoExigeNovoLogin() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()));
        assertThrows(ResponseStatusException.class, ctx::getClienteAtual);
    }

    @Test
    void novaAutenticacaoAceitaCadastroAtual() {
        user.setSenhaHash("novo-hash");
        authenticate();
        assertEquals(2L, ctx.getClienteAtualId());
    }

    @Test
    void adminPossuiSelecaoPorSessao() {
        user.setPerfil("ADMIN");
        user.setCliente(null);
        authenticate();
        when(clients.findByIdAndAtivoTrue(2L)).thenReturn(Optional.of(ricker));
        when(clients.findByIdAndAtivoTrue(3L)).thenReturn(Optional.of(other));
        var request2 = new MockHttpServletRequest();
        var ctx2 = new ClienteContextService(users, clients, request2);
        ctx.selecionarCliente(2L);
        ctx2.selecionarCliente(3L);
        assertEquals(2L, ctx.getClienteAtualId());
        assertEquals(3L, ctx2.getClienteAtualId());
    }

    @Test
    void desativarEReativarNaoRessuscitaSessaoAntiga() {
        user.setAtivo(false);
        user.setAtivo(true);
        assertThrows(ResponseStatusException.class, ctx::getClienteAtual);
    }

    @Test
    void emailAlteradoERestauradoNaoRessuscitaSessaoAntiga() {
        String email = user.getEmail();
        user.setEmail("novo@example.test");
        user.setEmail(email);
        assertThrows(ResponseStatusException.class, ctx::getClienteAtual);
    }

    @Test
    void usuarioRemovidoPerdeSessao() {
        var old = (org.springframework.mock.web.MockHttpSession) request.getSession();
        when(users.buscarPorEmail(user.getEmail())).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, ctx::getClienteAtual);
        assertTrue(old.isInvalid());
    }

    @Test
    void escritaBloqueiaUsuarioEEmpresaContraExclusaoConcorrente() {
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        when(users.bloquearPorEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(clients.bloquearAtivoPorId(2L)).thenReturn(Optional.of(ricker));
        assertEquals(2L, ctx.getClienteAtualId());
        verify(users, atLeastOnce()).bloquearPorEmail(user.getEmail());
        verify(clients).bloquearAtivoPorId(2L);
    }

    @Test
    void leituraNaoAdicionaLocksDeEscrita() {
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        org.springframework.transaction.support.TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertEquals(2L, ctx.getClienteAtualId());
        verify(users, never()).bloquearPorEmail(anyString());
        verifyNoInteractions(clients);
    }

    @Test
    void empresaRemovidaDuranteEscritaNaoRecebeNovoRegistro() {
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        when(users.bloquearPorEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(clients.bloquearAtivoPorId(2L)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, ctx::getClienteAtual);
    }
}
