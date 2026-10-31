package com.finvista.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.controller.ContaAzulOriginReviewController;
import com.finvista.service.ContaAzulOriginReviewService;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
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

class ContaAzulOriginReviewAccessTest {

    static final String ID = "02d6d764-de48-472c-ade0-6b2d3475feb5";

    @Configuration
    @Import(ContaAzulOriginReviewController.class)
    static class Config {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-30T15:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        ContaAzulOriginReviewService service() {
            var s = mock(ContaAzulOriginReviewService.class);
            when(s.plan(any())).thenReturn(new ContaAzulOriginReviewService.Plan(2L, ID, 1393L, "DARF", new BigDecimal("722.39"), new BigDecimal("722.39"), "PENDENTE", "Conferido no documento original", "", "revisao", 9L));
            return s;
        }
    }
    AnnotationConfigWebApplicationContext ctx;
    MockMvc mvc;
    ObjectMapper json = new ObjectMapper();
    final String base = "/api/integracoes/conta-azul/revisao-origem";

    @BeforeEach
    void setup() {
        ctx = new AnnotationConfigWebApplicationContext();
        ctx.setServletContext(new MockServletContext());
        ctx.getEnvironment().setActiveProfiles("prod");
        ctx.register(CsrfIntegrationTest.TestConfig.class, Config.class);
        ctx.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(ctx).addFilters(ctx.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void clean() {
        ctx.close();
        SecurityContextHolder.clearContext();
    }

    MockHttpSession session(String role) {
        var s = new MockHttpSession();
        var sc = SecurityContextHolder.createEmptyContext();
        sc.setAuthentication(new UsernamePasswordAuthenticationToken("contador@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        s.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sc);
        return s;
    }

    String csrf(MockHttpSession s) throws Exception {
        return json.readTree(mvc.perform(get("/api/auth/csrf").session(s)).andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    String body() {
        return "{\"clienteId\":2,\"parcelaId\":\"" + ID + "\",\"motivo\":\"Conferido no documento original\"}";
    }

    String plan(MockHttpSession s, String csrf) throws Exception {
        return json.readTree(mvc.perform(post(base + "/planejar").session(s).header("X-CSRF-TOKEN", csrf).contentType("application/json").content(body())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    String confirm(String token, long company, boolean confirmed) {
        return "{\"token\":\"" + token + "\",\"clienteId\":" + company + ",\"parcelaId\":\"" + ID + "\",\"confirmado\":" + confirmed + "}";
    }

    @Test
    void clienteNaoPodePrepararNemConfirmar() throws Exception {
        var s = session("CLIENTE");
        var c = csrf(s);
        for (String action : List.of("planejar", "confirmar")) {
            mvc.perform(post(base + "/" + action).session(s).header("X-CSRF-TOKEN", c).contentType("application/json").content(body())).andExpect(status().isForbidden());
        
        }verifyNoInteractions(ctx.getBean(ContaAzulOriginReviewService.class));
    }

    @Test
    void adminPrecisaDeCsrf() throws Exception {
        mvc.perform(post(base + "/planejar").session(session("ADMIN")).contentType("application/json").content(body())).andExpect(status().isForbidden());
        verifyNoInteractions(ctx.getBean(ContaAzulOriginReviewService.class));
    }

    @Test
    void semConfirmacaoNaoAltera() throws Exception {
        var s = session("ADMIN");
        var c = csrf(s);
        var token = plan(s, c);
        mvc.perform(post(base + "/confirmar").session(s).header("X-CSRF-TOKEN", c).contentType("application/json").content(confirm(token, 2, false))).andExpect(status().isConflict());
        verify(ctx.getBean(ContaAzulOriginReviewService.class), never()).apply(any());
    }

    @Test
    void outraEmpresaNaoPodeUsarPrevia() throws Exception {
        var s = session("ADMIN");
        var c = csrf(s);
        var token = plan(s, c);
        mvc.perform(post(base + "/confirmar").session(s).header("X-CSRF-TOKEN", c).contentType("application/json").content(confirm(token, 3, true))).andExpect(status().isConflict());
        verify(ctx.getBean(ContaAzulOriginReviewService.class), never()).apply(any());
    }

    @Test
    void confirmaUmaVezSemReaproveitarToken() throws Exception {
        var s = session("ADMIN");
        var c = csrf(s);
        var token = plan(s, c);
        mvc.perform(post(base + "/confirmar").session(s).header("X-CSRF-TOKEN", c).contentType("application/json").content(confirm(token, 2, true))).andExpect(status().isOk());
        mvc.perform(post(base + "/confirmar").session(s).header("X-CSRF-TOKEN", c).contentType("application/json").content(confirm(token, 2, true))).andExpect(status().isConflict());
        verify(ctx.getBean(ContaAzulOriginReviewService.class), times(1)).apply(any());
    }
}
