package com.finvista.integration.contaazul;
import com.finvista.model.*;
import com.finvista.repository.*;
import com.finvista.service.ClienteContextService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ContaAzulCompanyServiceTest {
    final ContaAzulConnectionRepository repo=mock(ContaAzulConnectionRepository.class);
    final ContaAzulAutoConfigRepository configs=mock(ContaAzulAutoConfigRepository.class);
    final ContaAzulAutoAccess access=mock(ContaAzulAutoAccess.class);
    final ContaAzulCompanyClient client=mock(ContaAzulCompanyClient.class);
    final ClienteContextService ctx=mock(ClienteContextService.class);
    final ContaAzulTokenCipher cipher=mock(ContaAzulTokenCipher.class);
    final Instant now=Instant.parse("2026-11-19T12:00:00Z");
    ContaAzulConnection connection;ContaAzulCompanyService service;
    @BeforeEach void setup(){
        var tm=mock(PlatformTransactionManager.class);when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service=new ContaAzulCompanyService(repo,configs,access,client,ctx,Clock.fixed(now,ZoneOffset.UTC),tm);
        ReflectionTestUtils.setField(service,"cipher",cipher);
        when(ctx.getClienteAtualId()).thenReturn(7L);var user=mock(Usuario.class);when(user.getId()).thenReturn(3L);when(ctx.getUsuarioAutenticado()).thenReturn(user);
        connection=new ContaAzulConnection(7L);connection.atualizar("encrypted","refresh",now.plusSeconds(3600),now);
        when(repo.bloquear(7L)).thenReturn(Optional.of(connection));when(repo.findById(7L)).thenReturn(Optional.of(connection));
        when(access.acesso(7L)).thenReturn("token");when(cipher.decifrar("encrypted",7L,"access")).thenReturn("token");
        when(client.consultar("token")).thenReturn(new ContaAzulCompanyClient.Empresa("external","05206246000138","Empresa"));
    }
    @Test void lookupDoesNotConfirmAutomatically(){
        var e=service.verificar();assertEquals("external",e.id());assertNull(e.confirmadaEm());
    }
    @Test void explicitFreshMatchingCompanyCanBeConfirmed(){
        service.verificar();var e=service.confirmar(new ContaAzulCompanyService.Confirmacao(7L,"external","05206246000138",true));
        assertEquals(now,e.confirmadaEm());
    }
    @Test void anotherTenantCannotConfirm(){
        service.verificar();assertThrows(IllegalArgumentException.class,()->service.confirmar(new ContaAzulCompanyService.Confirmacao(2L,"external","05206246000138",true)));
        assertNull(connection.getEmpresaConfirmadaEm());
    }
    @Test void expiredProofCannotConfirm(){
        connection.identificar("external","05206246000138","Empresa",now.minusSeconds(601));
        assertThrows(IllegalArgumentException.class,()->service.confirmar(new ContaAzulCompanyService.Confirmacao(7L,"external","05206246000138",true)));
    }
    @Test void changedAuthorizationRejectsInflightResponse(){
        when(cipher.decifrar("encrypted",7L,"access")).thenReturn("other-token");
        assertThrows(IllegalArgumentException.class,()->service.verificar());assertNull(connection.getEmpresaExternaId());
    }
    @Test void nonAdminIsRejectedBeforeCallingProvider(){
        doThrow(new IllegalStateException("Acesso negado")).when(ctx).validarAdministrador();
        assertThrows(IllegalStateException.class,()->service.verificar());verifyNoInteractions(access,client);
    }
    @Test void differentCompanyPausesAutomation(){
        connection.identificar("old","11111111000111","Antiga",now);connection.confirmarEmpresa(3L,now);
        var cfg=new ContaAzulAutoConfig();cfg.habilitada=true;cfg.proximaEm=now;when(configs.bloquear(7L)).thenReturn(Optional.of(cfg));
        service.verificar();assertFalse(cfg.habilitada);assertNull(connection.getEmpresaConfirmadaEm());
    }
}
