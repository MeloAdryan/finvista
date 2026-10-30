package com.finvista.controller;

import com.finvista.dto.auth.LoginRequest;
import com.finvista.dto.auth.LoginResponse;
import com.finvista.model.Usuario;
import com.finvista.service.AuthService;
import com.finvista.service.UsuarioService;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerSessionTest {

    AuthService auth;
    UsuarioService usuarios;
    AuthController controller;
    LoginRequest credenciais;
    LoginResponse usuario;

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
        auth = mock(AuthService.class);
        usuarios = mock(UsuarioService.class);
        controller = new AuthController(auth, usuarios);
        credenciais = new LoginRequest("admin@example.test", "senha-apenas-do-teste");
        usuario = new LoginResponse(1L, "Administrador", credenciais.email(), "ADMIN");
        when(auth.autenticar(credenciais)).thenReturn(new AuthService.Authenticated(usuario, "proof-do-teste"));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void novoLoginInvalidaIdAnteriorEDescartaContextoDaEmpresa() {
        var request = new MockHttpServletRequest();
        var anterior = new MockHttpSession();
        anterior.setAttribute("FINVISTA_CLIENTE_SELECIONADO_ID", 42L);
        anterior.setAttribute("estado-oauth-antigo", "valor-de-teste");
        request.setSession(anterior);
        var response = controller.login(credenciais, request);
        var nova = request.getSession(false);
        assertTrue(anterior.isInvalid());
        assertNotEquals(anterior.getId(), nova.getId());
        assertNull(nova.getAttribute("FINVISTA_CLIENTE_SELECIONADO_ID"));
        assertNull(nova.getAttribute("estado-oauth-antigo"));
        assertSame(usuario, response.getBody());
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @Test
    void loginSemSessaoSalvaSomenteContextoAutenticado() {
        var request = new MockHttpServletRequest();
        controller.login(credenciais, request);
        var session = request.getSession(false);
        var contexto = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertSame(contexto, SecurityContextHolder.getContext());
        assertTrue(contexto.getAuthentication().isAuthenticated());
        assertEquals(credenciais.email(), contexto.getAuthentication().getName());
        assertTrue(contexto.getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        assertEquals(1, java.util.Collections.list(session.getAttributeNames()).size());
    }

    @Test
    void senhaInvalidaNaoCriaSessao() {
        when(auth.autenticar(credenciais)).thenThrow(new IllegalArgumentException("Credenciais inválidas"));
        var request = new MockHttpServletRequest();
        assertThrows(IllegalArgumentException.class, () -> controller.login(credenciais, request));
        assertNull(request.getSession(false));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void tentativaInvalidaNaoInvalidaSessaoExistente() {
        when(auth.autenticar(credenciais)).thenThrow(new IllegalArgumentException("Credenciais inválidas"));
        var request = new MockHttpServletRequest();
        var anterior = new MockHttpSession();
        anterior.setAttribute("FINVISTA_CLIENTE_SELECIONADO_ID", 42L);
        request.setSession(anterior);
        assertThrows(IllegalArgumentException.class, () -> controller.login(credenciais, request));
        assertFalse(anterior.isInvalid());
        assertSame(anterior, request.getSession(false));
        assertEquals(42L, anterior.getAttribute("FINVISTA_CLIENTE_SELECIONADO_ID"));
    }

    @Test
    void logoutInvalidaSessaoELimpaAutenticacao() {
        var request = new MockHttpServletRequest();
        controller.login(credenciais, request);
        var session = (MockHttpSession) request.getSession(false);
        var response = controller.logout(request);
        assertTrue(session.isInvalid());
        assertNull(request.getSession(false));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(204, response.getStatusCode().value());
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @Test
    void logoutSemSessaoNaoCriaOutra() {
        var request = new MockHttpServletRequest();
        assertEquals(204, controller.logout(request).getStatusCode().value());
        assertNull(request.getSession(false));
    }

    @Test
    void meNaoAutenticadoNaoPodeSerArmazenadoEmCache() {
        var response = controller.me(null);
        assertEquals(401, response.getStatusCode().value());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        verifyNoInteractions(usuarios);
    }

    @Test
    void meConsultaUsuarioAtualENaoExpõeSenha() {
        var autenticacao = new UsernamePasswordAuthenticationToken(credenciais.email(), null, java.util.List.of());
        var registro = new Usuario("Administrador", credenciais.email(), "hash-de-teste", "ADMIN");
        when(usuarios.buscarPorEmail(credenciais.email())).thenReturn(Optional.of(registro));
        autenticacao.setDetails(com.finvista.service.AuthSessionProof.session(registro));
        var response = controller.me(autenticacao);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(credenciais.email(), response.getBody().email());
        assertEquals("ADMIN", response.getBody().perfil());
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }
}
