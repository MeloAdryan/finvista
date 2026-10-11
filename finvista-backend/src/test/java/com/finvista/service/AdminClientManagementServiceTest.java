package com.finvista.service;

import com.finvista.dto.admin.AdminClientUpdateRequest;
import com.finvista.model.*;
import com.finvista.repository.*;
import jakarta.persistence.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminClientManagementServiceTest {

    ClienteContextService contexto;
    ClienteRepository clientes;
    AdminClientChangeRepository historico;
    EntityManager em;
    AdminClientManagementService service;
    Cliente empresa;

    @BeforeEach
    void setup() {
        contexto = mock(ClienteContextService.class);
        clientes = mock(ClienteRepository.class);
        historico = mock(AdminClientChangeRepository.class);
        em = mock(EntityManager.class);
        service = new AdminClientManagementService(contexto, clientes, historico, em);
        empresa = new Cliente("Empresa");
        when(em.find(Cliente.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(empresa);
        var autor = mock(Usuario.class);
        when(autor.getId()).thenReturn(7L);
        when(autor.getNome()).thenReturn("Administrador");
        when(contexto.getUsuarioAutenticado()).thenReturn(autor);
    }

    AdminClientUpdateRequest pedido(String nome, boolean ativo) {
        return new AdminClientUpdateRequest(nome, ativo, "Empresa", true);
    }

    @Test
    void listaIncluiEmpresasInativasOrdenadas() {
        var inativa = new Cliente("Beta");
        inativa.setAtivo(false);
        when(clientes.findAll()).thenReturn(List.of(inativa, new Cliente("Alfa")));
        var lista = service.listar();
        assertEquals("Alfa", lista.get(0).nome());
        assertFalse(lista.get(1).ativo());
        verify(contexto).validarAdministrador();
        verifyNoInteractions(historico);
    }

    @Test
    void clienteNaoPodeListar() {
        doThrow(new IllegalStateException()).when(contexto).validarAdministrador();
        assertThrows(IllegalStateException.class, service::listar);
        verifyNoInteractions(clientes, historico, em);
    }

    @Test
    void clienteNaoPodeAlterar() {
        doThrow(new IllegalStateException()).when(contexto).validarAdministrador();
        assertThrows(IllegalStateException.class, () -> service.atualizar(1L, pedido("Nova", true)));
        assertEquals("Empresa", empresa.getNome());
        verifyNoInteractions(clientes, historico);
    }

    @Test
    void renomeiaERegistraAntesEDepoisComAutor() {
        var resposta = service.atualizar(1L, pedido(" Nova empresa ", true));
        assertEquals("Nova empresa", resposta.nome());
        var captor = ArgumentCaptor.forClass(AdminClientChange.class);
        verify(historico).save(captor.capture());
        var registro = captor.getValue();
        assertEquals(1L, registro.getClienteId());
        assertEquals(7L, registro.getUsuarioId());
        assertEquals("Administrador", registro.getUsuarioNome());
        assertEquals("Empresa", registro.getNomeAnterior());
        assertEquals("Nova empresa", registro.getNomeNovo());
        assertTrue(registro.getAtivoAnterior());
        assertTrue(registro.getAtivoNovo());
        assertNotNull(registro.getRegistradoEm());
        verify(clientes).save(empresa);
        verifyNoMoreInteractions(clientes, historico);
    }

    @Test
    void desativaSemExcluirEmpresa() {
        service.atualizar(1L, pedido("Empresa", false));
        assertFalse(empresa.getAtivo());
        verify(clientes).save(empresa);
        verifyNoMoreInteractions(clientes);
    }

    @Test
    void reativaEmpresaInativa() {
        empresa.setAtivo(false);
        var pedido = new AdminClientUpdateRequest("Empresa", true, "Empresa", false);
        assertTrue(service.atualizar(1L, pedido).ativo());
        verify(historico).save(any());
    }

    @Test
    void formularioSemMudancaNaoGravaNemGeraHistorico() {
        service.atualizar(1L, pedido("Empresa", true));
        verifyNoInteractions(clientes, historico);
    }

    @Test
    void nomeMudouEnquantoFormularioEstavaAberto() {
        empresa.setNome("Nome alterado");
        var erro = assertThrows(ResponseStatusException.class, () -> service.atualizar(1L, pedido("Nova", true)));
        assertEquals(409, erro.getStatusCode().value());
        assertEquals("Nome alterado", empresa.getNome());
        verifyNoInteractions(clientes, historico);
    }

    @Test
    void statusMudouEnquantoFormularioEstavaAberto() {
        empresa.setAtivo(false);
        var erro = assertThrows(ResponseStatusException.class, () -> service.atualizar(1L, pedido("Nova", true)));
        assertEquals(409, erro.getStatusCode().value());
        assertFalse(empresa.getAtivo());
        verifyNoInteractions(clientes, historico);
    }

    @Test
    void empresaInexistenteRetorna404() {
        when(em.find(Cliente.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.atualizar(1L, pedido("Nova", true))).getStatusCode().value());
        verifyNoInteractions(clientes, historico);
    }

    @Test
    void nomesVaziosOuLongosRecusadosAntesDoLock() {
        assertThrows(IllegalArgumentException.class, () -> service.atualizar(1L, pedido(" ", true)));
        assertThrows(IllegalArgumentException.class, () -> service.atualizar(1L, pedido("a".repeat(151), true)));
        verify(em, never()).find(any(), any(), any(LockModeType.class));
        verifyNoInteractions(clientes, historico);
    }

    @Test
    void statusAusenteNaoViraFalse() {
        assertThrows(IllegalArgumentException.class, () -> service.atualizar(1L, new AdminClientUpdateRequest("Nova", null, "Empresa", true)));
        verifyNoInteractions(clientes, historico);
    }

    @Test
    void estadoAnteriorObrigatorio() {
        assertThrows(IllegalArgumentException.class, () -> service.atualizar(1L, new AdminClientUpdateRequest("Nova", true, null, null)));
        verifyNoInteractions(clientes, historico);
    }

    @Test
    void idInvalidoRecusado() {
        assertThrows(IllegalArgumentException.class, () -> service.atualizar(0L, pedido("Nova", true)));
        verifyNoInteractions(clientes, historico);
    }

    @Test
    void falhaDeAuditoriaPropagaParaTransacao() {
        when(historico.save(any())).thenThrow(new IllegalStateException("Falha de persistência"));
        assertThrows(IllegalStateException.class, () -> service.atualizar(1L, pedido("Nova", true)));
        verify(historico).save(any());
    }

    @Test
    void usaLockAntesDeSalvar() {
        service.atualizar(1L, pedido("Nova", true));
        var ordem = inOrder(em, clientes, historico);
        ordem.verify(em).find(Cliente.class, 1L, LockModeType.PESSIMISTIC_WRITE);
        ordem.verify(clientes).save(empresa);
        ordem.verify(historico).save(any());
    }
}
