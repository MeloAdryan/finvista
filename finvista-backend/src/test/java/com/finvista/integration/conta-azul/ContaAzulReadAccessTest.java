package com.finvista.integration.contaazul;

import com.finvista.model.ContaAzulConnection;
import com.finvista.repository.ContaAzulConnectionRepository;
import com.finvista.service.ClienteContextService;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaAzulReadAccessTest {

    ContaAzulProperties p;
    ContaAzulTokenCipher cipher;
    ContaAzulConnectionRepository repo;
    ClienteContextService ctx;
    ContaAzulConnectionService service;
    ContaAzulTokenClient tokens;

    @BeforeEach
    void setup() {
        p = new ContaAzulProperties();
        p.setEnabled(true);
        p.setClientId("id");
        p.setClientSecret("secret");
        p.setRedirectUri("https://example.com/api/integracoes/conta-azul/callback");
        p.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        cipher = new ContaAzulTokenCipher(p);
        repo = mock(ContaAzulConnectionRepository.class);
        ctx = mock(ClienteContextService.class);
        when(ctx.getClienteAtualId()).thenReturn(1L);
        tokens = mock(ContaAzulTokenClient.class);
        when(tokens.renovar("refresh")).thenReturn(new ContaAzulTokenClient.Tokens("novo-access", "novo-refresh", 3600, "Bearer"));
        var access = new ContaAzulAutoAccess(repo, cipher, tokens, new ContaAzulConfiguration(p), java.time.Clock.systemUTC());
        org.springframework.test.util.ReflectionTestUtils.setField(access, "journal", mock(ContaAzulTokenJournal.class));
        service = new ContaAzulConnectionService(p, new ContaAzulConfiguration(p), cipher, tokens, repo, ctx, access);
    }

    @Test
    void disabledDoesNotReadCredentials() {
        p.setEnabled(false);
        assertThrows(IllegalStateException.class, () -> service.acessoParaConsulta(1L));
        verifyNoInteractions(repo);
    }

    @Test
    void otherCompanyDoesNotReadCredentials() {
        assertThrows(IllegalArgumentException.class, () -> service.acessoParaConsulta(2L));
        verifyNoInteractions(repo);
    }

    @Test
    void missingConnectionRejected() {
        when(repo.findById(1L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.acessoParaConsulta(1L));
    }

    ContaAzulConnection record(Instant expiration) {
        var c = new ContaAzulConnection(1L);
        c.atualizar(cipher.cifrar("access", 1L, "access"), cipher.cifrar("refresh", 1L, "refresh"), expiration, Instant.now());
        c.identificar("external","05206246000138","Empresa",Instant.now());
        c.confirmarEmpresa(3L,Instant.now());
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        when(repo.bloquear(1L)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void unconfirmedCompanyCannotReadFinancialData() {
        var c=record(Instant.now().plusSeconds(3600));c.exigirConfirmacaoEmpresa();
        assertThrows(IllegalArgumentException.class,()->service.acessoParaConsulta(1L));
        verifyNoInteractions(tokens);
    }

    @Test
    void expiredRenewsAutomatically() {
        record(Instant.now().minusSeconds(1));
        assertEquals("novo-access", service.acessoParaConsulta(1L));
        verify(tokens).renovar("refresh");
        verify(repo).saveAndFlush(any());
    }

    @Test
    void imminentExpiryRenewsAutomatically() {
        record(Instant.now().plusSeconds(10));
        assertEquals("novo-access", service.acessoParaConsulta(1L));
        verify(tokens).renovar("refresh");
    }

    @Test
    void validAccessIsInternalOnlyAndNotSaved() {
        record(Instant.now().plusSeconds(3600));
        assertEquals("access", service.acessoParaConsulta(1L));
        verify(repo, never()).saveAndFlush(any());
    }
}
