package com.finvista.integration.contaazul;

import com.finvista.controller.ContaAzulApiSyncController;
import com.finvista.dto.ContaAzulApiSyncResponse.*;
import com.finvista.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ContaAzulApiSyncControllerTest {

    private final String id = "45ff1a87-9826-4175-8b0b-519d68dfa9ef";
    private final String base = "/api/integracoes/conta-azul/parcelas/" + id + "/sincronizacao/";
    private ContaAzulApiSyncService service;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        service = mock(ContaAzulApiSyncService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ContaAzulApiSyncController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void previaTemContratoSemCache() throws Exception {
        when(service.preparar(id, 1L)).thenReturn(new Previa(1L, "Empresa", id, "INCLUIR", null,
                Instant.parse("2026-10-17T12:10:00Z"), "token", null, null, List.of()));
        mvc.perform(post(base + "previa").contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":1}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.clienteId").value(1)).andExpect(jsonPath("$.token").value("token"));
    }

    @Test
    void previaVinculoRecebeEmpresaEAlvoSemValoresFinanceiros() throws Exception {
        mvc.perform(post("/api/integracoes/conta-azul/parcelas/" + id + "/conciliacao/previa")
                .contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":1,\"lancamentoId\":100}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).prepararVinculo(id, 1L, 100L);
    }

    @Test
    void previaVinculoRejeitaOrigemOuValorForjado() throws Exception {
        mvc.perform(post("/api/integracoes/conta-azul/parcelas/" + id + "/conciliacao/previa")
                .contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":1,\"lancamentoId\":100,\"origem\":\"CONTA_AZUL_API\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void confirmarEncaminhaSomentePedidoControlado() throws Exception {
        mvc.perform(post(base + "confirmar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":1,\"token\":\"token\",\"confirmado\":true,\"confirmoNaoDuplicado\":true}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).confirmar(id, new Confirmacao(1L, "token", true, true));
    }

    @Test
    void rejeitaValoresFinanceirosDoNavegador() throws Exception {
        mvc.perform(post(base + "confirmar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":1,\"token\":\"token\",\"confirmado\":true,\"valor\":1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejeitaPreviaComAlvoForjado() throws Exception {
        mvc.perform(post(base + "previa").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":1,\"lancamentoId\":123}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void conflitoDevolveMensagemEImpedeCache() throws Exception {
        when(service.preparar(id, 1L)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Prepare outra prévia."));
        mvc.perform(post(base + "previa").contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":1}"))
                .andExpect(status().isConflict()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.mensagem").value("Prepare outra prévia."));
    }

    @Test
    void auditoriaExigeEmpresaNaConsulta() throws Exception {
        UUID op = UUID.randomUUID();
        mvc.perform(get("/api/integracoes/conta-azul/sincronizacoes/" + op)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void auditoriaEncaminhaEmpresaEImpedeCache() throws Exception {
        UUID op = UUID.randomUUID();
        mvc.perform(get("/api/integracoes/conta-azul/sincronizacoes/" + op).param("clienteId", "1"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).auditoria(op, 1L);
    }
}
