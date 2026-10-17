package com.finvista.service;

import com.finvista.model.*;
import com.finvista.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiImportTransitionTest {

    @Test
    void importacaoGenericaNaoMisturaArquivosAposInicioDaAPI() {
        var repository = mock(FinancialTransactionRepository.class);
        var context = mock(ClienteContextService.class);
        var em = mock(EntityManager.class);
        var cliente = mock(Cliente.class);
        when(cliente.getId()).thenReturn(7L);
        when(cliente.getAtivo()).thenReturn(true);
        when(context.getClienteAtual()).thenReturn(cliente);
        when(em.find(Cliente.class, 7L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(cliente);
        when(repository.findByClienteIdAndOrigemOrderByIdAsc(7L, "CONTA_AZUL_API")).thenReturn(List.of(new FinancialTransaction()));
        var service = new FinancialImportService(repository, context, em);
        assertThrows(ResponseStatusException.class, () -> service.salvar(List.of(new FinancialTransaction())));
        verify(repository, never()).saveAll(any());
        verify(repository, never()).save(any());
    }

}
