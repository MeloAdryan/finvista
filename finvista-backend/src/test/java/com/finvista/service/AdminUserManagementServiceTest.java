package com.finvista.service;

import com.finvista.dto.admin.*;
import com.finvista.model.*;
import com.finvista.repository.*;
import jakarta.persistence.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminUserManagementServiceTest {

    final ClienteContextService ctx = mock(ClienteContextService.class);
    final ClienteRepository clients = mock(ClienteRepository.class);
    final UsuarioRepository users = mock(UsuarioRepository.class);
    final EntityManager em = mock(EntityManager.class);
    final CadastroReferenceGuard refs = mock(CadastroReferenceGuard.class);
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    final UsuarioService creator = new UsuarioService(users, encoder);
    final AdminRegistrationService service = new AdminRegistrationService(ctx, clients, users, creator);
    Usuario u, actor;
    Cliente company;

    @BeforeEach
    void init() {
        company = new Cliente("Ricker");
        ReflectionTestUtils.setField(company, "id", 2L);
        u = new Usuario("Ricker", "ricker@example.test", encoder.encode("SenhaAntiga123"), "CLIENTE");
        u.setCliente(company);
        ReflectionTestUtils.setField(u, "id", 10L);
        actor = new Usuario("Contador", "admin@example.test", "hash", "ADMIN");
        ReflectionTestUtils.setField(actor, "id", 99L);
        when(ctx.getUsuarioAutenticado()).thenReturn(actor);
        ReflectionTestUtils.setField(service, "em", em);
        ReflectionTestUtils.setField(service, "references", refs);
        var query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Usuario.class))).thenReturn(query);
        when(query.setLockMode(any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(actor));
        when(em.find(Usuario.class, 10L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(u);
        when(users.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    AdminUserUpdateRequest request(String password, String confirm) {
        return new AdminUserUpdateRequest("Ricker atualizado", "novo@example.test", true, AuthSessionProof.revision(u), password, confirm);
    }

    AdminDeleteRequest remove() {
        return new AdminDeleteRequest("EXCLUIR", AuthSessionProof.revision(u), null, null);
    }

    @Test
    void editaEmailSemMudarEmpresaPerfilOuSenha() {
        String old = u.getSenhaHash();
        var result = service.atualizarUsuario(10L, request(null, null));
        assertEquals("novo@example.test", result.email());
        assertSame(company, u.getCliente());
        assertEquals("CLIENTE", u.getPerfil());
        assertEquals(old, u.getSenhaHash());
        assertNotNull(result.revisao());
    }

    @Test
    void senhaRedefinidaUsaBCryptERevogaProvaAntiga() {
        String stamp = AuthSessionProof.session(u);
        service.atualizarUsuario(10L, request("NovaSenha123", "NovaSenha123"));
        assertTrue(encoder.matches("NovaSenha123", u.getSenhaHash()));
        assertNotEquals("NovaSenha123", u.getSenhaHash());
        assertFalse(AuthSessionProof.valid(u, stamp));
    }

    @Test
    void senhaIncorretaOuMaiorQue72BytesNaoSalva() {
        assertThrows(IllegalArgumentException.class, () -> service.atualizarUsuario(10L, request("1234567", "1234567")));
        assertThrows(IllegalArgumentException.class, () -> service.atualizarUsuario(10L, request("á".repeat(37), "á".repeat(37))));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void confirmacaoDeSenhaObrigatoria() {
        assertThrows(IllegalArgumentException.class, () -> service.atualizarUsuario(10L, request("NovaSenha123", "outra")));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void revisaoAntigaNaoSobrescreveAlteracao() {
        var r = request(null, null);
        u.setNome("Outro contador alterou");
        assertThrows(ResponseStatusException.class, () -> service.atualizarUsuario(10L, r));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void emailDuplicadoNaoSalva() {
        when(users.existsByEmailIgnoreCaseAndIdNot("novo@example.test", 10L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.atualizarUsuario(10L, request(null, null)));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void usuarioComHistoricoSoPodeSerDesativado() {
        when(refs.usuario(10L)).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.excluirUsuario(10L, remove()));
        verify(users, never()).delete(any());
        var r = new AdminUserUpdateRequest(u.getNome(), u.getEmail(), false, AuthSessionProof.revision(u), null, null);
        service.atualizarUsuario(10L, r);
        assertFalse(u.getAtivo());
    }

    @Test
    void usuarioSemHistoricoExigeConfirmacaoAtual() {
        assertThrows(IllegalArgumentException.class, () -> service.excluirUsuario(10L, new AdminDeleteRequest("", AuthSessionProof.revision(u), null, null)));
        verify(users, never()).delete(any());
        service.excluirUsuario(10L, remove());
        verify(users).delete(u);
        verify(users).flush();
    }

    @Test
    void propriaContaNaoPodeSerExcluidaOuDesativada() {
        when(ctx.getUsuarioAutenticado()).thenReturn(u);
        assertThrows(IllegalArgumentException.class, () -> service.excluirUsuario(10L, remove()));
        assertThrows(IllegalArgumentException.class, () -> service.atualizarUsuario(10L, new AdminUserUpdateRequest(u.getNome(), u.getEmail(), false, AuthSessionProof.revision(u), null, null)));
    }

    @Test
    void ultimoAdminAtivoPreservado() {
        u.setPerfil("ADMIN");
        assertThrows(IllegalArgumentException.class, () -> service.excluirUsuario(10L, remove()));
        verify(users, never()).delete(any());
    }

    @Test
    void clienteNaoPodeAlterarNemExcluirUsuarios() {
        doThrow(new IllegalStateException("ADMIN necessário")).when(ctx).validarAdministrador();
        assertThrows(IllegalStateException.class, () -> service.atualizarUsuario(10L, request(null, null)));
        assertThrows(IllegalStateException.class, () -> service.excluirUsuario(10L, remove()));
        verifyNoInteractions(em);
    }

    @Test
    void alterarEmailNaoReligaContaADiferenteEmpresa() {
        var before = u.getCliente().getId();
        service.atualizarUsuario(10L, request(null, null));
        assertEquals(before, u.getCliente().getId());
        verifyNoInteractions(clients);
    }
}
