package com.finvista.service;

import com.finvista.dto.admin.*;
import com.finvista.model.*;
import com.finvista.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminRegistrationServiceTest {

    @Mock
    ClienteContextService context;
    @Mock
    ClienteRepository clients;
    @Mock
    UsuarioRepository users;
    @Mock
    UsuarioService creator;
    AdminRegistrationService service;

    @BeforeEach
    void init() {
        service = new AdminRegistrationService(context, clients, users, creator);
    }

    @Test
    void clienteNaoPodeCriarEmpresa() {
        doThrow(new IllegalStateException("ADMIN necessário")).when(context).validarAdministrador();
        assertThrows(IllegalStateException.class, () -> service.criarCliente(new AdminClientRequest("Empresa")));
        verifyNoInteractions(clients);
    }

    @Test
    void clienteNaoPodeCriarUsuario() {
        doThrow(new IllegalStateException("ADMIN necessário")).when(context).validarAdministrador();
        assertThrows(IllegalStateException.class, () -> service.criarUsuario(new AdminUserRequest("Ana", "a@exemplo.com", "Senha123", "CLIENTE", 1L)));
        verifyNoInteractions(clients, users, creator);
    }

    @Test
    void clienteNaoPodeListarUsuarios() {
        doThrow(new IllegalStateException("ADMIN necessário")).when(context).validarAdministrador();
        assertThrows(IllegalStateException.class, service::listarUsuarios);
        verifyNoInteractions(users);
    }

    @Test
    void criarEmpresaSemDadosFinanceiros() {
        when(clients.save(any())).thenAnswer(i -> i.getArgument(0));
        var c = service.criarCliente(new AdminClientRequest(" Empresa "));
        assertEquals("Empresa", c.nome());
        assertTrue(c.ativo());
        verifyNoInteractions(users, creator);
    }

    @Test
    void usuarioClientePrecisaEmpresaAtiva() {
        assertThrows(IllegalArgumentException.class, () -> service.criarUsuario(new AdminUserRequest("Ana", "a@exemplo.com", "Senha123", "CLIENTE", null)));
        when(clients.findByIdAndAtivoTrue(2L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.criarUsuario(new AdminUserRequest("Ana", "a@exemplo.com", "Senha123", "CLIENTE", 2L)));
        verifyNoInteractions(creator, users);
    }

    @Test
    void vinculaUsuarioAntesDeResponder() {
        Cliente c = new Cliente("Empresa");
        when(clients.findByIdAndAtivoTrue(2L)).thenReturn(Optional.of(c));
        Usuario u = new Usuario("Ana", "a@exemplo.com", "hash", "CLIENTE");
        when(creator.criarUsuario("Ana", "a@exemplo.com", "Senha123", "CLIENTE")).thenReturn(u);
        when(users.save(u)).thenReturn(u);
        var r = service.criarUsuario(new AdminUserRequest("Ana", " A@EXEMPLO.COM ", "Senha123", "CLIENTE", 2L));
        assertSame(c, u.getCliente());
        assertEquals("Empresa", r.clienteNome());
        assertEquals("CLIENTE", r.perfil());
    }

    @Test
    void adminNaoTemEmpresaFixa() {
        Usuario u = new Usuario("Admin", "a@exemplo.com", "hash", "ADMIN");
        when(creator.criarUsuario("Admin", "a@exemplo.com", "Senha123", "ADMIN")).thenReturn(u);
        when(users.save(u)).thenReturn(u);
        assertNull(service.criarUsuario(new AdminUserRequest("Admin", "a@exemplo.com", "Senha123", "ADMIN", null)).clienteId());
        assertThrows(IllegalArgumentException.class, () -> service.criarUsuario(new AdminUserRequest("Admin", "a@exemplo.com", "Senha123", "ADMIN", 2L)));
    }

    @Test
    void recusaPerfilInvalidoESenhaAcimaDoLimiteEmBytes() {
        assertThrows(IllegalArgumentException.class, () -> service.criarUsuario(new AdminUserRequest("Ana", "a@exemplo.com", "Senha123", "ROOT", null)));
        assertThrows(IllegalArgumentException.class, () -> service.criarUsuario(new AdminUserRequest("Ana", "a@exemplo.com", "á".repeat(37), "ADMIN", null)));
        verifyNoInteractions(creator, users);
    }

    @Test
    void listagemUsaDtoSemCredenciais() {
        when(users.findAll()).thenReturn(List.of(new Usuario("Ana", "a@exemplo.com", "hash-secreto", "ADMIN")));
        var r = service.listarUsuarios();
        assertEquals(1, r.size());
        assertFalse(r.toString().contains("hash-secreto"));
    }
}
