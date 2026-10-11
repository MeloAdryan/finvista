package com.finvista.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.controller.AuthController;
import com.finvista.controller.CsrfController;
import com.finvista.dto.auth.*;
import com.finvista.service.*;
import jakarta.servlet.Filter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CsrfIntegrationTest {

    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    ObjectMapper json = new ObjectMapper();

    record Token(MockHttpSession session, String value) {}

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, CsrfController.class, AuthController.class, Writes.class})
    static class TestConfig {

        @Bean
        AuthService authService() {
            var service = mock(AuthService.class);
            when(service.login(any())).thenReturn(new LoginResponse(1L, "Admin", "admin@example.test", "ADMIN"));
            return service;
        }

        @Bean
        UsuarioService usuarioService() {
            return mock(UsuarioService.class);
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            var cors = new CorsConfiguration();
            cors.setAllowedOrigins(List.of("https://finvista.example"));
            cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            cors.setAllowedHeaders(List.of("*"));
            cors.setAllowCredentials(true);
            var source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/api/**", cors);
            return source;
        }
    }

    @RestController
    static class Writes {

        final AtomicInteger calls = new AtomicInteger();

        @RequestMapping(value = {"/api/admin/teste", "/api/importacoes/teste", "/api/metas-gastos"},
                method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
        String write() {
            calls.incrementAndGet();
            return "ok";
        }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void cleanup() {
        context.close();
        SecurityContextHolder.clearContext();
    }

    Token token(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) {
            request.session(session);
        }
        var result = mvc.perform(request).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertEquals("X-CSRF-TOKEN", body.get("headerName").asText());
        return new Token((MockHttpSession) result.getRequest().getSession(false), body.get("token").asText());
    }

    void role(MockHttpSession session, String perfil) {
        var security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication(new UsernamePasswordAuthenticationToken("usuario@example.test", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, security);
    }

    void rejected(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CSRF_INVALID"));
        assertEquals(0, context.getBean(Writes.class).calls.get());
    }

    MockHttpServletRequestBuilder login() {
        return post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@example.test\",\"senha\":\"senha-de-teste\"}");
    }

    @Test
    void tokenDisponivelSemLogin() throws Exception {
        var t = token(null);
        assertNotNull(t.session());
        assertFalse(t.value().isBlank());
    }

    @Test
    void loginSemTokenNaoAlcancaServico() throws Exception {
        rejected(login());
        verifyNoInteractions(context.getBean(AuthService.class));
    }

    @Test
    void tokenIncorretoBloqueiaEscrita() throws Exception {
        var t = token(null);
        role(t.session(), "ADMIN");
        rejected(post("/api/admin/teste").session(t.session()).header("X-CSRF-TOKEN", "incorreto"));
    }

    @Test
    void adminSemTokenTambemBloqueado() throws Exception {
        var t = token(null);
        role(t.session(), "ADMIN");
        rejected(post("/api/admin/teste").session(t.session()));
    }

    @Test
    void tokenDeOutraSessaoBloqueado() throws Exception {
        var a = token(null);
        var b = token(null);
        role(b.session(), "ADMIN");
        rejected(post("/api/admin/teste").session(b.session()).header("X-CSRF-TOKEN", a.value()));
    }

    @Test
    void tokenValidoPermiteEscritaAdmin() throws Exception {
        var t = token(null);
        role(t.session(), "ADMIN");
        mvc.perform(post("/api/admin/teste").session(t.session()).header("X-CSRF-TOKEN", t.value())).andExpect(status().isOk());
        assertEquals(1, context.getBean(Writes.class).calls.get());
    }

    @Test
    void tokenValidoNaoConcedePerfilAdmin() throws Exception {
        var t = token(null);
        role(t.session(), "CLIENTE");
        mvc.perform(post("/api/admin/teste").session(t.session()).header("X-CSRF-TOKEN", t.value())).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
        assertEquals(0, context.getBean(Writes.class).calls.get());
    }

    @Test
    void metodosDeAlteracaoExigemToken() throws Exception {
        var t = token(null);
        role(t.session(), "ADMIN");
        for (var method : List.of("PUT", "PATCH", "DELETE")) {
            rejected(request(org.springframework.http.HttpMethod.valueOf(method), "/api/metas-gastos").session(t.session()));
        }
    }

    @Test
    void uploadComTokenNoCabecalhoAceito() throws Exception {
        var t = token(null);
        role(t.session(), "ADMIN");
        mvc.perform(multipart("/api/importacoes/teste").file(new MockMultipartFile("arquivo", "teste.csv", "text/csv", "teste".getBytes())).session(t.session()).header("X-CSRF-TOKEN", t.value())).andExpect(status().isOk());
    }

    @Test
    void uploadSemTokenBloqueado() throws Exception {
        var t = token(null);
        role(t.session(), "ADMIN");
        rejected(multipart("/api/importacoes/teste").file(new MockMultipartFile("arquivo", "teste.csv", "text/csv", "teste".getBytes())).session(t.session()));
    }

    @Test
    void loginRenovaSessaoETokenAnteriorNaoFunciona() throws Exception {
        var old = token(null);
        var result = mvc.perform(login().session(old.session()).header("X-CSRF-TOKEN", old.value())).andExpect(status().isOk()).andReturn();
        var current = (MockHttpSession) result.getRequest().getSession(false);
        assertTrue(old.session().isInvalid());
        assertNotEquals(old.session().getId(), current.getId());
        rejected(post("/api/admin/teste").session(current).header("X-CSRF-TOKEN", old.value()));
        var fresh = token(current);
        mvc.perform(post("/api/admin/teste").session(current).header("X-CSRF-TOKEN", fresh.value())).andExpect(status().isOk());
    }

    @Test
    void logoutSemTokenNaoInvalidaSessao() throws Exception {
        var t = token(null);
        role(t.session(), "ADMIN");
        rejected(post("/api/auth/logout").session(t.session()));
        assertFalse(t.session().isInvalid());
    }

    @Test
    void logoutComTokenInvalidaSessao() throws Exception {
        var t = token(null);
        role(t.session(), "ADMIN");
        mvc.perform(post("/api/auth/logout").session(t.session()).header("X-CSRF-TOKEN", t.value())).andExpect(status().isNoContent());
        assertTrue(t.session().isInvalid());
    }

    @Test
    void getSemTokenMantemRegraDeAutenticacao() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void corsPermitePreflightDoFrontend() throws Exception {
        mvc.perform(options("/api/admin/teste").header("Origin", "https://finvista.example").header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "x-csrf-token,content-type")).andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://finvista.example"));
    }

    @Test
    void corsNaoLiberaLeituraDoTokenParaOutraOrigem() throws Exception {
        mvc.perform(get("/api/auth/csrf").header("Origin", "https://outra.example")).andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
