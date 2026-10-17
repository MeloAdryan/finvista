package com.finvista.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.config.SecurityConfig;
import com.finvista.service.AlertCenterService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertCenterAccessTest {

    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, AlertCenterController.class, CsrfController.class})
    static class Config {

        @Bean
        AlertCenterService alertCenterService() {
            return mock(AlertCenterService.class);
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

    private MockHttpSession sessao() {
        var sessao = new MockHttpSession();
        var autenticacao = SecurityContextHolder.createEmptyContext();
        autenticacao.setAuthentication(new UsernamePasswordAuthenticationToken("cliente@example.test", null,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));
        sessao.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, autenticacao);
        return sessao;
    }

    private String token(MockHttpSession session) throws Exception {
        var resposta = mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(resposta).get("token").asText();
    }

    @Test
    void centralExigeLogin() throws Exception {
        mvc.perform(get("/api/alertas-financeiros/central")).andExpect(status().isUnauthorized());
        verifyNoInteractions(context.getBean(AlertCenterService.class));
    }

    @Test
    void clienteConsultaSemReceberPermissaoAdmin() throws Exception {
        mvc.perform(get("/api/alertas-financeiros/central").session(sessao())).andExpect(status().isOk());
        verify(context.getBean(AlertCenterService.class)).listar(null, null, null, null, null, null, 0, 20);
    }

    @Test
    void reavaliacaoSemCsrfEhBloqueada() throws Exception {
        mvc.perform(post("/api/alertas-financeiros/central/reavaliar").session(sessao())
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(context.getBean(AlertCenterService.class));
    }

    @Test
    void reavaliacaoComCsrfEhPermitida() throws Exception {
        var session = sessao();
        mvc.perform(post("/api/alertas-financeiros/central/reavaliar").session(session)
                .header("X-CSRF-TOKEN", token(session)).contentType("application/json").content("{}"))
                .andExpect(status().isOk());
        verify(context.getBean(AlertCenterService.class)).atualizar(null, null);
    }

    @Test
    void leituraExigeCsrf() throws Exception {
        var session = sessao();
        mvc.perform(post("/api/alertas-financeiros/central/9/lido").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/alertas-financeiros/central/9/lido").session(session)
                .header("X-CSRF-TOKEN", token(session))).andExpect(status().isNoContent());
        verify(context.getBean(AlertCenterService.class)).marcarLido(9L);
    }

    @Test
    void gravidadeInvalidaNaoChegaAoServico() throws Exception {
        mvc.perform(get("/api/alertas-financeiros/central").session(sessao()).param("gravidade", "INVENTADO"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(context.getBean(AlertCenterService.class));
    }
}
