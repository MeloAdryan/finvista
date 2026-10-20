package com.finvista.integration.contaazul;
import com.finvista.model.ContaAzulConnection;
import com.finvista.repository.ContaAzulConnectionRepository;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ContaAzulAutoAccessTest {
    private final ContaAzulConnectionRepository repo=mock(ContaAzulConnectionRepository.class);
    private final ContaAzulTokenCipher cipher=mock(ContaAzulTokenCipher.class);
    private final ContaAzulTokenClient tokens=mock(ContaAzulTokenClient.class);
    private final ContaAzulConfiguration cfg=mock(ContaAzulConfiguration.class);
    private final Instant agora=Instant.parse("2026-10-17T12:00:00Z");
    private ContaAzulConnection connection;
    private ContaAzulAutoAccess service;
    @BeforeEach void preparar() {
        connection=new ContaAzulConnection(7L);connection.atualizar("ac","rc",agora.plusSeconds(3600),agora);
        when(repo.bloquear(7L)).thenReturn(Optional.of(connection));
        when(cipher.decifrar("ac",7L,"access")).thenReturn("access-teste");
        when(cipher.decifrar("rc",7L,"refresh")).thenReturn("refresh-teste");
        service=new ContaAzulAutoAccess(repo,cipher,tokens,cfg,Clock.fixed(agora,ZoneOffset.UTC));
    }
    @Test void acessoValidoNaoRenova() {assertEquals("access-teste",service.acesso(7L));verifyNoInteractions(tokens);verify(repo,never()).saveAndFlush(any());}
    @Test void renovaESalvaTambemORefreshRotacionado() {
        connection.atualizar("ac","rc",agora.plusSeconds(30),agora);
        when(tokens.renovar("refresh-teste")).thenReturn(new ContaAzulTokenClient.Tokens("novo-access","novo-refresh",3600,"Bearer"));
        when(cipher.cifrar("novo-access",7L,"access")).thenReturn("novo-ac-cipher");
        when(cipher.cifrar("novo-refresh",7L,"refresh")).thenReturn("novo-rc-cipher");
        when(cipher.decifrar("novo-ac-cipher",7L,"access")).thenReturn("novo-access");
        assertEquals("novo-access",service.acesso(7L));
        assertEquals("novo-rc-cipher",connection.getRefreshCipher());assertEquals(agora.plusSeconds(3600),connection.getExpiresAt());
        verify(repo).saveAndFlush(connection);
    }
    @Test void falhaDeRenovacaoPreservaCredenciaisLocais() {
        connection.atualizar("ac","rc",agora.minusSeconds(1),agora);
        when(tokens.renovar(any())).thenThrow(new IllegalStateException("Falha de autorização"));
        assertThrows(IllegalStateException.class,()->service.acesso(7L));
        assertEquals("rc",connection.getRefreshCipher());verify(repo,never()).saveAndFlush(any());
    }
    @Test void outraEmpresaNaoReutilizaToken() {assertThrows(IllegalStateException.class,()->service.acesso(8L));verifyNoInteractions(cipher,tokens);}
    @Test void renovacaoUsaTransacaoIndependenteDaGravacaoFinanceira() throws Exception {
        var annotation=ContaAzulAutoAccess.class.getMethod("acesso",Long.class).getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertEquals(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,annotation.propagation());
    }

    @Test void tokenExpiradoRenovaUmaVezEReinicioUsaCredenciaisRotacionadas() {
        connection.atualizar("ac", "rc", agora.minusSeconds(1), agora);
        when(tokens.renovar("refresh-teste")).thenReturn(new ContaAzulTokenClient.Tokens("novo-access", "novo-refresh", 3600, "Bearer"));
        when(cipher.cifrar("novo-access",7L,"access")).thenReturn("novo-ac");
        when(cipher.cifrar("novo-refresh",7L,"refresh")).thenReturn("novo-rc");
        when(cipher.decifrar("novo-ac",7L,"access")).thenReturn("novo-access");
        assertEquals("novo-access", service.acesso(7L));
        var reiniciado = new ContaAzulAutoAccess(repo, cipher, tokens, cfg, Clock.fixed(agora.plusSeconds(120), ZoneOffset.UTC));
        assertEquals("novo-access", reiniciado.acesso(7L));
        verify(tokens, times(1)).renovar("refresh-teste");
        verify(repo, times(1)).saveAndFlush(connection);
        assertEquals("novo-rc", connection.getRefreshCipher());
    }

}
