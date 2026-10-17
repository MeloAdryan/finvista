package com.finvista.service;

import com.finvista.controller.SpendingGoalController;
import com.finvista.model.SpendingGoal;
import com.finvista.repository.FinancialTransactionRepository;
import com.finvista.repository.SpendingGoalRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpendingGoalDeletionTest {

    private final SpendingGoalRepository repository = mock(SpendingGoalRepository.class);
    private final FinancialTransactionRepository transactions = mock(FinancialTransactionRepository.class);
    private final ClienteContextService context = mock(ClienteContextService.class);
    private final SpendingGoalService service = new SpendingGoalService(
            repository, transactions, new FinancialCalculationService(), context);

    @Test
    void clienteNaoPodeExcluirMesmoInformandoIdValido() {
        when(context.isAdmin()).thenReturn(false);
        var error = assertThrows(ResponseStatusException.class, () -> service.excluir(7L));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(repository, transactions);
    }

    @Test
    void metaAusenteOuDeOutraEmpresaNaoEExcluida() {
        when(context.isAdmin()).thenReturn(true);
        when(context.getClienteAtualId()).thenReturn(12L);
        when(repository.findByIdAndClienteId(7L, 12L)).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class, () -> service.excluir(7L));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(repository, never()).delete(any(SpendingGoal.class));
        verify(repository, never()).deleteById(anyLong());
        verifyNoInteractions(transactions);
    }

    @Test
    void administradorExcluiSomenteMetaDoClienteSelecionado() {
        when(context.isAdmin()).thenReturn(true);
        when(context.getClienteAtualId()).thenReturn(12L);
        var goal = new SpendingGoal();
        when(repository.findByIdAndClienteId(7L, 12L)).thenReturn(Optional.of(goal));
        service.excluir(7L);
        verify(repository).delete(goal);
        verify(repository).flush();
        verifyNoInteractions(transactions);
    }

    @Test
    void endpointRetorna204SemCorpo() throws Exception {
        var mockedService = mock(SpendingGoalService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SpendingGoalController(mockedService)).build();
        mvc.perform(delete("/api/metas-gastos/7")).andExpect(status().isNoContent());
        verify(mockedService).excluir(7L);
    }

    @Test
    void endpointPropagaProibicaoENaoEncontrado() throws Exception {
        var mockedService = mock(SpendingGoalService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SpendingGoalController(mockedService)).build();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(mockedService).excluir(7L);
        mvc.perform(delete("/api/metas-gastos/7")).andExpect(status().isForbidden());
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(mockedService).excluir(8L);
        mvc.perform(delete("/api/metas-gastos/8")).andExpect(status().isNotFound());
    }
}