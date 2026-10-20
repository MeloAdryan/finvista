package com.finvista.config;

import jakarta.servlet.Filter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LocalCallbackSecurityTest {
    private AnnotationConfigWebApplicationContext context(String... profiles) {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().setActiveProfiles(profiles);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("relay", Map.of("finvista.conta-azul.local-callback-relay", true)));
        context.register(CsrfIntegrationTest.TestConfig.class);
        context.refresh();
        return context;
    }

    @Test void somenteGetDoCallbackPodePassarSemSessaoEmDev() throws Exception {
        try (var context = context("dev")) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
            // Sem controlador nesta fixture: 404 comprova que passou pelo filtro.
            mvc.perform(get("/api/integracoes/conta-azul/callback")).andExpect(status().isNotFound());
            mvc.perform(get("/api/integracoes/conta-azul/status")).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/integracoes/conta-azul/autorizacao")).andExpect(status().isForbidden());
            mvc.perform(post("/api/integracoes/conta-azul/callback")).andExpect(status().isForbidden());
        }
    }

    @Test void producaoContinuaExigindoSessao() throws Exception {
        try (var context = context("dev", "prod")) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
            mvc.perform(get("/api/integracoes/conta-azul/callback")).andExpect(status().isUnauthorized());
        }
    }
}
