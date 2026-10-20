package com.finvista.integration.contaazul;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;

class ContaAzulAutoSchedulerTest {

    @Test
    void desativacaoGlobalNaoConsultaFila() {
        var state = mock(ContaAzulAutoState.class);
        var worker = mock(ContaAzulAutoWorker.class);
        new ContaAzulAutoScheduler(state, worker, false).processar();
        verifyNoInteractions(state, worker);
    }

    @Test
    void falhaDeUmaEmpresaNaoImpedeAsDemais() {
        var state = mock(ContaAzulAutoState.class);
        var worker = mock(ContaAzulAutoWorker.class);
        when(state.disponiveis()).thenReturn(List.of(7L, 8L));
        doThrow(new IllegalStateException()).when(worker).executar(7L);
        new ContaAzulAutoScheduler(state, worker, true).processar();
        verify(worker).executar(8L);
    }
}
