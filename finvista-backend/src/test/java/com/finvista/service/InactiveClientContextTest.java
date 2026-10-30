package com.finvista.service;

import com.finvista.model.*;
import com.finvista.repository.ClienteRepository;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InactiveClientContextTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("teste@example.test", null, List.of()));
    }

    @Test
    void usuarioClienteNaoAcessaEmpresaInativa() {
        autenticar();
        var usuarios = mock(UsuarioService.class);
        var clientes = mock(ClienteRepository.class);
        var usuario = new Usuario("Usuário", "teste@example.test", "hash", "CLIENTE");
        var empresa = new Cliente("Empresa");
        empresa.setAtivo(false);
        usuario.setCliente(empresa);
        when(usuarios.buscarPorEmail("teste@example.test")).thenReturn(Optional.of(usuario));
        ((org.springframework.security.authentication.AbstractAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).setDetails(AuthSessionProof.session(usuarios.buscarPorEmail("teste@example.test").orElseThrow()));
        var contexto = new ClienteContextService(usuarios, clientes, new MockHttpServletRequest());
        assertThrows(IllegalStateException.class, contexto::getClienteAtual);
        verifyNoInteractions(clientes);
    }

    @Test
    void adminPerdeSelecaoDeEmpresaInativaNaProximaConsulta() {
        autenticar();
        var usuarios = mock(UsuarioService.class);
        var clientes = mock(ClienteRepository.class);
        when(usuarios.buscarPorEmail("teste@example.test")).thenReturn(Optional.of(new Usuario("Admin", "teste@example.test", "hash", "ADMIN")));
        var request = new MockHttpServletRequest();
        request.getSession().setAttribute(ClienteContextService.CLIENTE_SELECIONADO_SESSION_KEY, 1L);
        when(clientes.findByIdAndAtivoTrue(1L)).thenReturn(Optional.empty());
        ((org.springframework.security.authentication.AbstractAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).setDetails(AuthSessionProof.session(usuarios.buscarPorEmail("teste@example.test").orElseThrow()));
        var contexto = new ClienteContextService(usuarios, clientes, request);
        assertThrows(IllegalStateException.class, contexto::getClienteAtual);
        assertNull(request.getSession().getAttribute(ClienteContextService.CLIENTE_SELECIONADO_SESSION_KEY));
    }
}
