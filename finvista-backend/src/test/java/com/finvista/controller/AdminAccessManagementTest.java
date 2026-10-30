package com.finvista.controller;

import com.finvista.config.SecurityConfig;
import com.finvista.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminAccessManagementTest {

    AnnotationConfigWebApplicationContext ctx;
    MockMvc mvc;
    MockHttpSession session;
    String token;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, CsrfController.class, AdminRegistrationController.class, AdminClientManagementController.class})
    static class Config {

        @Bean
        AdminRegistrationService users() {
            return mock(AdminRegistrationService.class);
        }

        @Bean
        AdminClientManagementService companies() {
            return mock(AdminClientManagementService.class);
        }
    }

    @BeforeEach
    void init() throws Exception {
        ctx = new AnnotationConfigWebApplicationContext();
        ctx.setServletContext(new MockServletContext());
        ctx.register(Config.class);
        ctx.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(ctx).addFilters(ctx.getBean("springSecurityFilterChain", Filter.class)).build();
        var result = mvc.perform(get("/api/auth/csrf")).andReturn();
        session = (MockHttpSession) result.getRequest().getSession(false);
        token = new ObjectMapper().readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    @AfterEach
    void close() {
        ctx.close();
        SecurityContextHolder.clearContext();
    }

    void role(String role) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("teste@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    String body() {
        return "{\"nome\":\"Ricker\",\"email\":\"ricker@example.test\",\"ativo\":true,\"revisao\":\"revisao-teste\"}";
    }

    @Test
    void clienteNaoPodeEditarCredenciais() throws Exception {
        role("CLIENTE");
        mvc.perform(put("/api/admin/usuarios/10").session(session).header("X-CSRF-TOKEN", token).header("X-Finvista-Admin", "1").contentType("application/json").content(body())).andExpect(status().isForbidden());
        verifyNoInteractions(ctx.getBean(AdminRegistrationService.class));
    }

    @Test
    void clienteNaoPodeExcluirUsuario() throws Exception {
        role("CLIENTE");
        mvc.perform(delete("/api/admin/usuarios/10").session(session).header("X-CSRF-TOKEN", token).header("X-Finvista-Admin", "1").contentType("application/json").content("{\"confirmacao\":\"EXCLUIR\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(ctx.getBean(AdminRegistrationService.class));
    }

    @Test
    void clienteNaoPodeExcluirEmpresa() throws Exception {
        role("CLIENTE");
        mvc.perform(delete("/api/admin/clientes/2").session(session).header("X-CSRF-TOKEN", token).header("X-Finvista-Admin", "1").contentType("application/json").content("{\"confirmacao\":\"EXCLUIR\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(ctx.getBean(AdminClientManagementService.class));
    }

    @Test
    void adminSemCsrfNaoEdita() throws Exception {
        role("ADMIN");
        mvc.perform(put("/api/admin/usuarios/10").session(session).header("X-Finvista-Admin", "1").contentType("application/json").content(body())).andExpect(status().isForbidden());
        verifyNoInteractions(ctx.getBean(AdminRegistrationService.class));
    }

    @Test
    void adminComCsrfEdita() throws Exception {
        role("ADMIN");
        mvc.perform(put("/api/admin/usuarios/10").session(session).header("X-CSRF-TOKEN", token).header("X-Finvista-Admin", "1").contentType("application/json").content(body())).andExpect(status().isOk());
        verify(ctx.getBean(AdminRegistrationService.class)).atualizarUsuario(eq(10L), any());
    }

    @Test
    void emailInvalidoNaoChegaAoServico() throws Exception {
        role("ADMIN");
        mvc.perform(put("/api/admin/usuarios/10").session(session).header("X-CSRF-TOKEN", token).header("X-Finvista-Admin", "1").contentType("application/json").content(body().replace("ricker@example.test", "invalido"))).andExpect(status().isBadRequest());
        verifyNoInteractions(ctx.getBean(AdminRegistrationService.class));
    }
}
