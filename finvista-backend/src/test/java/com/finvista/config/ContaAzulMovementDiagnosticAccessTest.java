package com.finvista.config;
import com.finvista.controller.ContaAzulMovementDiagnosticController;
import com.finvista.integration.contaazul.*;
import com.finvista.service.ClienteContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import java.util.*;
import java.nio.charset.StandardCharsets;
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
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ContaAzulMovementDiagnosticAccessTest {
 static final String URL="/api/integracoes/conta-azul/diagnostico-movimentos";
 @Configuration @Import(ContaAzulMovementDiagnosticController.class)
 static class Config {
  @Bean ClienteContextService contextService(){var s=mock(ClienteContextService.class);when(s.getClienteAtualId()).thenReturn(7L);return s;}
  @Bean ContaAzulAutoAccess access(){var a=mock(ContaAzulAutoAccess.class);when(a.acesso(7L)).thenReturn("TOKEN_NAO_EXIBIR");return a;}
  @Bean ContaAzulMovementClient movements(){return mock(ContaAzulMovementClient.class);}
 }
 AnnotationConfigWebApplicationContext context;MockMvc mvc;ContaAzulMovementClient movements;
 @BeforeEach void setup(){context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.getEnvironment().setActiveProfiles("prod");context.register(CsrfIntegrationTest.TestConfig.class,Config.class);context.refresh();mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();movements=context.getBean(ContaAzulMovementClient.class);}
 @AfterEach void cleanup(){context.close();SecurityContextHolder.clearContext();}
 MockHttpSession session(String role){var s=new MockHttpSession();var sc=SecurityContextHolder.createEmptyContext();sc.setAuthentication(new UsernamePasswordAuthenticationToken("teste@example.test",null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));s.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,sc);return s;}
 org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(){return get(URL).param("inicio","2026-09-01").param("fim","2026-09-30");}
 @Test void anonymousDenied()throws Exception{mvc.perform(request()).andExpect(status().isUnauthorized());verifyNoInteractions(movements,context.getBean(ContaAzulAutoAccess.class));}
 @Test void customerDenied()throws Exception{mvc.perform(request().session(session("CLIENTE"))).andExpect(status().isForbidden());verifyNoInteractions(movements,context.getBean(ContaAzulAutoAccess.class));}
 @Test void administratorUsesSelectedCompanyAndReturnsOnlyCounts()throws Exception{
  var source=new ObjectMapper().readTree("{\"id\":\"NAO_EXIBIR_REGISTRO\"}");
  when(movements.consultar(anyString(),anyString(),any(),any(),anyInt())).thenReturn(new ContaAzulMovementClient.Pagina(123,List.of(source),source));
  var body=mvc.perform(request().session(session("ADMIN")).param("clienteId","999")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.clienteId").value(7)).andExpect(jsonPath("$.consultaAceita").value(true)).andExpect(jsonPath("$.totalInformado").value(123)).andReturn().getResponse().getContentAsString();
  assertFalse(body.contains("TOKEN_NAO_EXIBIR"));assertFalse(body.contains("NAO_EXIBIR_REGISTRO"));
  verify(context.getBean(ContaAzulAutoAccess.class)).acesso(7L);
 }
 @Test void refusesBroadRangeBeforeCallingErp()throws Exception{mvc.perform(get(URL).param("inicio","1900-01-01").param("fim","2200-12-31").session(session("ADMIN"))).andExpect(status().isBadRequest());verifyNoInteractions(movements,context.getBean(ContaAzulAutoAccess.class));}
 @Test void reportsProviderStatusAndAllowlistedValidationWithoutBody()throws Exception{
  var bytes="{\"error\":\"Intervalo data_inicio data_fim maximo 30 dias TOKEN_NAO_EXIBIR email=pessoa@example.test\"}".getBytes(StandardCharsets.UTF_8);
  var original=HttpClientErrorException.create(HttpStatus.BAD_REQUEST,"SECRET",HttpHeaders.EMPTY,bytes,StandardCharsets.UTF_8);
  when(movements.consultar(anyString(),anyString(),any(),any(),anyInt())).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY,"SECRET",original));
  var body=mvc.perform(request().session(session("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.consultaAceita").value(false)).andExpect(jsonPath("$.httpOriginal").value(400)).andExpect(jsonPath("$.classificacao").value("VALIDACAO_PERIODO")).andExpect(jsonPath("$.limitesEmDias[0]").value(30)).andReturn().getResponse().getContentAsString();
  assertFalse(body.contains("SECRET"));assertFalse(body.contains("TOKEN_NAO_EXIBIR"));assertFalse(body.contains("pessoa@example.test"));assertTrue(body.contains("data_inicio"));
 }
 @Test void missingDateRejected()throws Exception{mvc.perform(get(URL).param("inicio","2026-09-01").session(session("ADMIN"))).andExpect(status().isBadRequest());verifyNoInteractions(movements,context.getBean(ContaAzulAutoAccess.class));}
}
