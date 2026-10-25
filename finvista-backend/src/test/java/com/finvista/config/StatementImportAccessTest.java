package com.finvista.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.controller.ContaAzulStatementController;
import com.finvista.service.*;
import jakarta.servlet.Filter;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StatementImportAccessTest {

    @Configuration
    @Import(ContaAzulStatementController.class)
    static class Config {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-22T15:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        ContaAzulStatementService reader() {
            return mock(ContaAzulStatementService.class);
        }

        @Bean
        ClienteContextService client() {
            var c = mock(ClienteContextService.class);
            when(c.getClienteAtualId()).thenReturn(7L);
            return c;
        }

        @Bean
        StatementImportService importer() throws Exception {
            var s = mock(StatementImportService.class);
            when(s.plan(any())).thenReturn(new StatementImportService.Preview(7L, "Teste", "extrato.xls", 2, 2, 0, List.of(), "file", "state"));
            when(s.apply(any(), any())).thenReturn(new StatementImportService.Imported(7L, 2, 0, 2));
            when(s.stored(anyLong())).thenReturn(new ContaAzulStatementService.Result(true, "Extratos", List.of()));
            return s;
        }
    }
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    ObjectMapper json = new ObjectMapper();
    String base = "/api/importacoes/conta-azul/extrato";
    MockMultipartFile file = new MockMultipartFile("arquivo", "extrato.xls", "application/octet-stream", new byte[]{1});

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().setActiveProfiles("prod");
        context.register(CsrfIntegrationTest.TestConfig.class, Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void cleanup() {
        context.close();
        SecurityContextHolder.clearContext();
    }

    MockHttpSession session(String role) {
        var session = new MockHttpSession();
        var sc = SecurityContextHolder.createEmptyContext();
        sc.setAuthentication(new UsernamePasswordAuthenticationToken("test@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sc);
        return session;
    }

    String csrf(MockHttpSession session) throws Exception {
        return json.readTree(mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    String plan(MockHttpSession s, String csrf) throws Exception {
        var response = mvc.perform(multipart(base + "/planejar").file(file).session(s).header("X-CSRF-TOKEN", csrf).param("clienteId", "7"))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void anonymousCannotReadLedger() throws Exception {
        mvc.perform(get(base + "/movimentos")).andExpect(status().isUnauthorized());
    }

    @Test
    void clientCannotReadOrWriteImportLedger() throws Exception {
        var s = session("CLIENTE");
        var token = csrf(s);
        mvc.perform(get(base + "/movimentos").session(s)).andExpect(status().isForbidden());
        mvc.perform(multipart(base + "/planejar").file(file).session(s).header("X-CSRF-TOKEN", token).param("clienteId", "7")).andExpect(status().isForbidden());
        verifyNoInteractions(context.getBean(StatementImportService.class));
    }

    @Test
    void adminWriteRequiresCsrf() throws Exception {
        mvc.perform(multipart(base + "/planejar").file(file).session(session("ADMIN")).param("clienteId", "7")).andExpect(status().isForbidden());
        verifyNoInteractions(context.getBean(StatementImportService.class));
    }

    @Test
    void explicitConfirmationRequired() throws Exception {
        var s = session("ADMIN");
        var csrf = csrf(s);
        var token = plan(s, csrf);
        mvc.perform(multipart(base + "/importar").file(file).session(s).header("X-CSRF-TOKEN", csrf).param("clienteId", "7").param("token", token).param("confirmado", "false"))
                .andExpect(status().isConflict());
        verify(context.getBean(StatementImportService.class), never()).apply(any(), any());
    }

    @Test
    void anotherCompanyCannotUsePreview() throws Exception {
        var s = session("ADMIN");
        var csrf = csrf(s);
        var token = plan(s, csrf);
        mvc.perform(multipart(base + "/importar").file(file).session(s).header("X-CSRF-TOKEN", csrf).param("clienteId", "8").param("token", token).param("confirmado", "true"))
                .andExpect(status().isConflict());
        verify(context.getBean(StatementImportService.class), never()).apply(any(), any());
    }

    @Test
    void confirmedAdminCanImportAndTokenCannotBeReused() throws Exception {
        var s = session("ADMIN");
        var csrf = csrf(s);
        var token = plan(s, csrf);
        mvc.perform(multipart(base + "/importar").file(file).session(s).header("X-CSRF-TOKEN", csrf).param("clienteId", "7").param("token", token).param("confirmado", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.incluidas").value(2));
        mvc.perform(multipart(base + "/importar").file(file).session(s).header("X-CSRF-TOKEN", csrf).param("clienteId", "7").param("token", token).param("confirmado", "true"))
                .andExpect(status().isConflict());
        verify(context.getBean(StatementImportService.class), times(1)).apply(any(), any());
    }
}
