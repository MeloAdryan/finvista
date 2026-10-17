package com.finvista.controller;

import com.finvista.config.SecurityConfig;
import com.finvista.service.FinancialAlertsService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinancialAlertsAccessTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, FinancialAlertsController.class})
    static class Config {

        @Bean
        FinancialAlertsService financialAlertsService() {
            return mock(FinancialAlertsService.class);
        }
    }

    @BeforeEach
    void configurar() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void encerrar() {
        context.close();
        SecurityContextHolder.clearContext();
    }

    private MockHttpSession sessao(String perfil) {
        var session = new MockHttpSession();
        var security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication(new UsernamePasswordAuthenticationToken("usuario", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, security);
        return session;
    }

    @Test
    void exigeAutenticacao() throws Exception {
        mvc.perform(get("/api/alertas-financeiros")).andExpect(status().isUnauthorized());
        verifyNoInteractions(context.getBean(FinancialAlertsService.class));
    }

    @Test
    void clientePodeConsultar() throws Exception {
        mvc.perform(get("/api/alertas-financeiros").session(sessao("USUARIO"))).andExpect(status().isOk());
        verify(context.getBean(FinancialAlertsService.class)).consultar(null, null, null);
    }

    @Test
    void adminPodeConsultar() throws Exception {
        mvc.perform(get("/api/alertas-financeiros").session(sessao("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void rejeitaDataInvalida() throws Exception {
        mvc.perform(get("/api/alertas-financeiros").param("inicio", "data-invalida")
                .session(sessao("USUARIO"))).andExpect(status().isBadRequest());
        verifyNoInteractions(context.getBean(FinancialAlertsService.class));
    }
}
