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
    private final com.finvista.service.ContaAzulReconciliationService reconciliation=mock(com.finvista.service.ContaAzulReconciliationService.class);
    @BeforeEach void preparar() {var controller=new ContaAzulAutoController(state);org.springframework.test.util.ReflectionTestUtils.setField(controller,"reconciliacao",reconciliation);mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();}
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

    String linkPlan(org.springframework.mock.web.MockHttpSession session)throws Exception{
        var request=new com.finvista.service.ContaAzulReconciliationService.LinkRequest(7L,java.time.LocalDate.of(2026,9,1),java.time.LocalDate.of(2026,9,30),"extrato","api","VINCULAR","Recibo e conta bancaria conferidos");
        when(reconciliation.planejarVinculo(any())).thenReturn(new com.finvista.service.ContaAzulReconciliationService.LinkPlan(request,null,null,"a","f","s",9L));
        var response=mvc.perform(post("/api/integracoes/conta-azul/automatica/conferencia/planejar-vinculo").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":7}"))
                .andExpect(status().isOk()).andReturn();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("token").asText();
    }
    @Test void vinculoExigeConfirmacaoExplicita()throws Exception{
        var session=new org.springframework.mock.web.MockHttpSession();var token=linkPlan(session);
        mvc.perform(post("/api/integracoes/conta-azul/automatica/conferencia/confirmar-vinculo").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":7,\"token\":\""+token+"\",\"confirmado\":false}"))
                .andExpect(status().isConflict());verify(reconciliation,never()).confirmarVinculo(any());
    }
    @Test void tokenDeVinculoNaoPodeSerReutilizado()throws Exception{
        var session=new org.springframework.mock.web.MockHttpSession();var token=linkPlan(session);
        String body="{\"clienteId\":7,\"token\":\""+token+"\",\"confirmado\":true}";
        mvc.perform(post("/api/integracoes/conta-azul/automatica/conferencia/confirmar-vinculo").session(session).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/integracoes/conta-azul/automatica/conferencia/confirmar-vinculo").session(session).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());verify(reconciliation,times(1)).confirmarVinculo(any());
    }
    @Test void tokenDeOutraEmpresaNaoPodeConfirmar()throws Exception{
        var session=new org.springframework.mock.web.MockHttpSession();var token=linkPlan(session);
        mvc.perform(post("/api/integracoes/conta-azul/automatica/conferencia/confirmar-vinculo").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":8,\"token\":\""+token+"\",\"confirmado\":true}"))
                .andExpect(status().isConflict());verify(reconciliation,never()).confirmarVinculo(any());
    }

}
