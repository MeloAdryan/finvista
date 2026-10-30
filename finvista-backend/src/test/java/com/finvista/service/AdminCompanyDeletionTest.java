package com.finvista.service;

import com.finvista.dto.admin.*;
import com.finvista.model.*;
import com.finvista.repository.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminCompanyDeletionTest {

    @Test
    void apenasEmpresaSemVinculosPodeSerExcluida() {
        var ctx = mock(ClienteContextService.class);
        var repo = mock(ClienteRepository.class);
        var em = mock(EntityManager.class);
        var guard = mock(CadastroReferenceGuard.class);
        var s = new AdminClientManagementService(ctx, repo, mock(AdminClientChangeRepository.class), em);
        ReflectionTestUtils.setField(s, "references", guard);
        var c = new Cliente("Teste");
        ReflectionTestUtils.setField(c, "id", 3L);
        when(em.find(Cliente.class, 3L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(c);
        when(guard.empresa(3L)).thenReturn(true);
        var pedido = new AdminDeleteRequest("EXCLUIR", null, "Teste", true);
        assertThrows(ResponseStatusException.class, () -> s.excluir(3L, pedido));
        verify(repo, never()).delete(any());
        when(guard.empresa(3L)).thenReturn(false);
        s.excluir(3L, pedido);
        verify(repo).delete(c);
        verify(repo).flush();
    }

    @Test
    void confirmacaoObsoletaNaoRemoveEmpresa() {
        var ctx = mock(ClienteContextService.class);
        var repo = mock(ClienteRepository.class);
        var em = mock(EntityManager.class);
        var s = new AdminClientManagementService(ctx, repo, mock(AdminClientChangeRepository.class), em);
        when(em.find(Cliente.class, 3L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(new Cliente("Novo nome"));
        assertThrows(IllegalArgumentException.class, () -> s.excluir(3L, new AdminDeleteRequest("EXCLUIR", null, "Antigo nome", true)));
        verifyNoInteractions(repo);
    }
}
