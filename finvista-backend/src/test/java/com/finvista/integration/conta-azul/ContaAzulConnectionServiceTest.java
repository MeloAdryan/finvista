package com.finvista.integration.contaazul;

import com.finvista.model.ContaAzulConnection;
import com.finvista.repository.ContaAzulConnectionRepository;
import com.finvista.service.ClienteContextService;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaAzulConnectionServiceTest {

    ContaAzulProperties p;
    ContaAzulTokenCipher cipher;
    ContaAzulTokenClient tokens;
    ContaAzulConnectionRepository repo;
    ClienteContextService ctx;
    ContaAzulConnectionService service;

    @BeforeEach
    void setup() {
        p = new ContaAzulProperties();
        p.setEnabled(true);
        p.setClientId("id");
        p.setClientSecret("secret");
        p.setRedirectUri("https://example.com/api/integracoes/conta-azul/callback");
        p.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        cipher = new ContaAzulTokenCipher(p);
        tokens = mock(ContaAzulTokenClient.class);
        repo = mock(ContaAzulConnectionRepository.class);
        ctx = mock(ClienteContextService.class);
        when(ctx.getClienteAtualId()).thenReturn(1L);
        var access = new ContaAzulAutoAccess(repo, cipher, tokens, new ContaAzulConfiguration(p), java.time.Clock.systemUTC());
        org.springframework.test.util.ReflectionTestUtils.setField(access, "journal", mock(ContaAzulTokenJournal.class));
        service = new ContaAzulConnectionService(p, new ContaAzulConfiguration(p), cipher, tokens, repo, ctx, access);
    }

    @Test
    void storesEncryptedRotatedRefresh() {
        var c = new ContaAzulConnection(1L);
        c.atualizar(cipher.cifrar("a", 1L, "access"), cipher.cifrar("old", 1L, "refresh"), Instant.now(), Instant.now());
        when(repo.bloquear(1L)).thenReturn(Optional.of(c));
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        when(tokens.renovar("old")).thenReturn(new ContaAzulTokenClient.Tokens("a-new", "r-new", 3600, "Bearer"));
        service.renovar();
        assertEquals("r-new", cipher.decifrar(c.getRefreshCipher(), 1L, "refresh"));
        assertEquals("a-new", cipher.decifrar(c.getAccessCipher(), 1L, "access"));
        verify(repo).saveAndFlush(c);
        assertTrue(c.getExpiresAt().isAfter(Instant.now().plusSeconds(3500)));
    }

    @Test
    void changedCompanyMakesNoProviderCall() {
        assertThrows(IllegalStateException.class, () -> service.conectar(2L, "code"));
        verifyNoInteractions(tokens, repo);
    }

    @Test
    void disabledMakesNoProviderCall() {
        p.setEnabled(false);
        assertThrows(IllegalStateException.class, () -> service.conectar(1L, "code"));
        verifyNoInteractions(tokens, repo);
    }

    @Test
    void nonAdminMakesNoProviderCall() {
        doThrow(new IllegalStateException("Acesso negado")).when(ctx).validarAdministrador();
        assertThrows(IllegalStateException.class, () -> service.renovar());
        verifyNoInteractions(tokens, repo);
    }

    @Test void statusExplicaReautorizacaoENovaConexaoLimpaErro() {
        var c = new ContaAzulConnection(1L);
        c.atualizar("ac", "rc", Instant.now(), Instant.now());
        c.reautorizar(Instant.now());
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        assertEquals("REAUTORIZAR", service.status().status());
        assertFalse(service.status().conectado());
        assertNotNull(service.status().ultimoErro());
        when(repo.bloquear(1L)).thenReturn(Optional.of(c));
        when(tokens.trocar("code")).thenReturn(new ContaAzulTokenClient.Tokens("novo", "refresh", 3600, "Bearer"));
        service.conectar(1L, "code");
        assertEquals("ATIVA", service.status().status());
        assertTrue(service.status().conectado());
        assertNull(service.status().ultimoErro());
    }

}
