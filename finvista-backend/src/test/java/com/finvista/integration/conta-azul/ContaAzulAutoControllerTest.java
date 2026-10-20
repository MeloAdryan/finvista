package com.finvista.integration.contaazul;
import com.finvista.controller.ContaAzulAutoController;
import com.finvista.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

class ContaAzulAutoControllerTest {
    private final ContaAzulAutoState state=mock(ContaAzulAutoState.class);
    private MockMvc mvc;
    @BeforeEach void preparar() {mvc=MockMvcBuilders.standaloneSetup(new ContaAzulAutoController(state)).setControllerAdvice(new GlobalExceptionHandler()).build();}
    @Test void statusExigeEmpresa() throws Exception {mvc.perform(get("/api/integracoes/conta-azul/automatica")).andExpect(status().isBadRequest());verifyNoInteractions(state);}
    @Test void statusSemCacheEComPagina() throws Exception {
        mvc.perform(get("/api/integracoes/conta-azul/automatica").param("clienteId","7").param("paginaPendencias","2"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));verify(state).status(7L,2);
    }
    @Test void pausaNaoAceitaAlvoFinanceiroForjado() throws Exception {
        mvc.perform(post("/api/integracoes/conta-azul/automatica/pausar").contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":7,\"lancamentoId\":999}"))
                .andExpect(status().isBadRequest());verifyNoInteractions(state);
    }
    @Test void execucaoApenasAgendaEmpresaInformada() throws Exception {
        mvc.perform(post("/api/integracoes/conta-azul/automatica/executar").contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":7}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));verify(state).solicitar(7L);
    }
}
