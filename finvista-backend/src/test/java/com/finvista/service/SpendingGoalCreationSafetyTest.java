package com.finvista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.dto.SpendingGoalCreateRequest;
import com.finvista.model.*;
import com.finvista.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.finvista.controller.SpendingGoalController;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpendingGoalCreationSafetyTest {

    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    String valido = "{\"tipo\":\"MENSAL\",\"dataInicio\":\"2026-10-01\",\"dataFim\":\"2026-10-31\",\"valorLimite\":1000,\"percentualAlerta\":80,\"categoria\":\"Serviços\",\"centroCusto\":\"Operações\"}";

    @Test
    void dtoCriaEntidadeNovaSemIdOuProprietario() throws Exception {
        var pedido = mapper.readValue(valido, SpendingGoalCreateRequest.class);
        var meta = pedido.novaMeta();
        assertNull(meta.getId());
        assertNull(meta.getCliente());
        assertEquals("Serviços", meta.getCategoria());
        assertEquals("Operações", meta.getCentroCusto());
        assertNotSame(meta, pedido.novaMeta());
    }

    @Test
    void rejeitaIdClienteEOutrosCamposMesmoComJacksonTolerante() {
        mapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        for (String extra : new String[]{"\"id\":123", "\"cliente\":{\"id\":2}", "\"clienteId\":2"}) {
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                    () -> mapper.readValue(valido.substring(0, valido.length() - 1) + "," + extra + "}", SpendingGoalCreateRequest.class));
        }
    }

    @Test
    void chamadaInternaComIdNuncaGrava() throws Exception {
        var repository = mock(SpendingGoalRepository.class);
        var context = mock(ClienteContextService.class);
        var service = new SpendingGoalService(repository, mock(FinancialTransactionRepository.class),
                new FinancialCalculationService(), context);
        var meta = mapper.readValue("{\"id\":987}", SpendingGoal.class);
        assertThrows(IllegalArgumentException.class, () -> service.salvar(meta));
        verifyNoInteractions(repository, context);
    }

    @Test
    void endpointComIdOuClienteRetorna400SemGravacao() throws Exception {
        var service = mock(SpendingGoalService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SpendingGoalController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        for (String campo : new String[]{"\"id\":987", "\"cliente\":{\"id\":2}"}) {
            mvc.perform(post("/api/metas-gastos").contentType("application/json")
                    .content(valido.substring(0, valido.length() - 1) + "," + campo + "}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void endpointValidoUsaClienteDaSessaoENaoIdDoPedido() throws Exception {
        var repository = mock(SpendingGoalRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var context = mock(ClienteContextService.class);
        var cliente = new Cliente("Empresa autenticada");
        when(context.getClienteAtual()).thenReturn(cliente);
        var service = new SpendingGoalService(repository, mock(FinancialTransactionRepository.class),
                new FinancialCalculationService(), context);
        var mvc = MockMvcBuilders.standaloneSetup(new SpendingGoalController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        mvc.perform(post("/api/metas-gastos").contentType("application/json").content(valido))
                .andExpect(status().isCreated());
        verify(repository).save(argThat(meta -> meta.getId() == null && meta.getCliente() == cliente));
    }
}
