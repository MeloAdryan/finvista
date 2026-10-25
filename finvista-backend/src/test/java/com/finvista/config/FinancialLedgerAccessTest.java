package com.finvista.config;

import com.finvista.controller.FinancialLedgerController;
import com.finvista.service.FinancialLedgerService;
import jakarta.servlet.Filter;
import java.time.*;
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

class FinancialLedgerAccessTest {

    @Configuration
    @Import(FinancialLedgerController.class)
    static class LedgerConfig {

        @Bean
        FinancialLedgerService financialLedgerService() {
            var s = mock(FinancialLedgerService.class);
            when(s.consultar(any(), any())).thenAnswer(i -> new FinancialLedgerService.Result(7L, i.getArgument(0), i.getArgument(1), null, null, List.of(), List.of(), List.of(), true, List.of()));
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
        context.register(CsrfIntegrationTest.TestConfig.class, LedgerConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void cleanup() {
        context.close();
        SecurityContextHolder.clearContext();
    }

    MockHttpSession session() {
        var s = new MockHttpSession();
        var sc = SecurityContextHolder.createEmptyContext();
        sc.setAuthentication(new UsernamePasswordAuthenticationToken("teste@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));
        s.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sc);
        return s;
    }

    @Test
    void anonymousCannotRead() throws Exception {
        mvc.perform(get("/api/analises/financeira/caixa").param("inicio", "2026-09-01").param("fim", "2026-09-30")).andExpect(status().isUnauthorized());
        verifyNoInteractions(context.getBean(FinancialLedgerService.class));
    }

    @Test
    void authenticatedClientCannotSelectCompanyInQuery() throws Exception {
        mvc.perform(get("/api/analises/financeira/caixa").session(session()).param("inicio", "2026-09-01").param("fim", "2026-09-30").param("clienteId", "999")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.clienteId").value(7));
    }

    @Test
    void missingDateIsRejected() throws Exception {
        mvc.perform(get("/api/analises/financeira/caixa").session(session()).param("inicio", "2026-09-01")).andExpect(status().isBadRequest());
        verifyNoInteractions(context.getBean(FinancialLedgerService.class));
    }
}



    
    
    

        
         
               
                        
            
        
        
    
    
     
          
        
        
         
        
           
    
    
     
        
        
    
     
           
           
          
         
        
    
    
     
          
        
    
    
     
            
    
    
     
         
        
    