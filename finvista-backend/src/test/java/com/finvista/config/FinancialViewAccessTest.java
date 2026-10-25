package com.finvista.config;

import com.finvista.controller.FinancialViewController;
import com.finvista.service.FinancialViewService;
import com.finvista.service.FinancialViewService.*;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
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

class FinancialViewAccessTest {

    @Configuration
    @Import(FinancialViewController.class)
    static class ViewConfig {

        @Bean
        FinancialViewService financialViewService() {
            var s = mock(FinancialViewService.class);
            when(s.consultar(any())).thenAnswer(inv -> {
                Filtro f = inv.getArgument(0);
                var d = LocalDate.of(2026, 10, 22);
                return new Resposta(7L, f, new Faixa(d, d), d, new Total(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), List.of(), List.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), "PRINCIPAL", "");
            });
            return s;
        }
    }
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().setActiveProfiles("prod");
        context.register(CsrfIntegrationTest.TestConfig.class, ViewConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void cleanup() {
        context.close();
        SecurityContextHolder.clearContext();
    }

    MockHttpSession session(String role) {
        var s = new MockHttpSession();
        var sc = SecurityContextHolder.createEmptyContext();
        sc.setAuthentication(new UsernamePasswordAuthenticationToken("teste@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        s.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sc);
        return s;
    }

    @Test
    void anonymousCannotRead() throws Exception {
        mvc.perform(get("/api/analises/financeira")).andExpect(status().isUnauthorized());
        verifyNoInteractions(context.getBean(FinancialViewService.class));
    }

    @Test
    void clientCanReadWithoutAdminPrivileges() throws Exception {
        mvc.perform(get("/api/analises/financeira").session(session("CLIENTE"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.clienteId").value(7));
    }

    @Test
    void adminCanReadAndCannotChooseAnotherCompanyInQuery() throws Exception {
        mvc.perform(get("/api/analises/financeira").session(session("ADMIN")).param("clienteId", "999")).andExpect(status().isOk()).andExpect(jsonPath("$.clienteId").value(7));
        verify(context.getBean(FinancialViewService.class)).consultar(any(Filtro.class));
    }

    @Test
    void invalidCriterionRejected() throws Exception {
        mvc.perform(get("/api/analises/financeira").session(session("CLIENTE")).param("criterio", "OUTRO")).andExpect(status().isBadRequest());
        verifyNoInteractions(context.getBean(FinancialViewService.class));
    }

    @Test
    void invalidDateRejected() throws Exception {
        mvc.perform(get("/api/analises/financeira").session(session("ADMIN")).param("inicio", "2026-02-30")).andExpect(status().isBadRequest());
        verifyNoInteractions(context.getBean(FinancialViewService.class));
    }
}
