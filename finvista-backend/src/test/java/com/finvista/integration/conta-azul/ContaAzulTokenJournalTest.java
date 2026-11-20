package com.finvista.integration.contaazul;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.model.ContaAzulConnection;
import com.finvista.repository.ContaAzulConnectionRepository;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaAzulTokenJournalTest {
    @TempDir Path directory;
    final Instant now = Instant.parse("2026-11-18T12:00:00Z");
    ContaAzulTokenCipher cipher;
    ContaAzulTokenJournal journal;
    ContaAzulConnectionRepository repo;
    ContaAzulTokenClient tokens;
    String oldAccess, oldRefresh;
    @BeforeEach void setup() {
        var p = new ContaAzulProperties(); p.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        cipher = new ContaAzulTokenCipher(p);
        journal = journal();
        repo = mock(ContaAzulConnectionRepository.class); tokens = mock(ContaAzulTokenClient.class);
        oldAccess = cipher.cifrar("old-access", 7L, "access"); oldRefresh = cipher.cifrar("old-refresh", 7L, "refresh");
        when(tokens.renovar("old-refresh")).thenReturn(new ContaAzulTokenClient.Tokens("new-access", "new-refresh", 3600, "Bearer"));
    }
    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }
    ContaAzulTokenJournal journal() {
        return new ContaAzulTokenJournal(cipher, new ObjectMapper().findAndRegisterModules(), directory.toString());
    }
    ContaAzulConnection stored() {
        var c = new ContaAzulConnection(7L); c.atualizar(oldAccess, oldRefresh, now.minusSeconds(1), now.minusSeconds(3600)); return c;
    }
    ContaAzulAutoAccess service(ContaAzulTokenJournal journal) {
        var s = new ContaAzulAutoAccess(repo, cipher, tokens, mock(ContaAzulConfiguration.class), Clock.fixed(now, ZoneOffset.UTC));
        ReflectionTestUtils.setField(s, "journal", journal); return s;
    }
    @Test void failedDatabaseWriteKeepsNewCredentialsRecoverableAfterRestart() throws Exception {
        var before = stored(); when(repo.bloquear(7L)).thenReturn(Optional.of(before));
        doThrow(new IllegalStateException("database unavailable")).when(repo).saveAndFlush(any());
        assertThrows(IllegalStateException.class, () -> service(journal).acesso(7L));
        String disk = Files.readString(directory.resolve("7.journal"));
        assertFalse(disk.contains("new-refresh")); assertFalse(disk.contains("new-access"));
        // Simulate rollback and another process reading the old committed database row.
        var committed = stored(); when(repo.bloquear(7L)).thenReturn(Optional.of(committed));
        doAnswer(i -> i.getArgument(0)).when(repo).saveAndFlush(any());
        TransactionSynchronizationManager.initSynchronization();
        assertEquals("new-access", service(journal()).acesso(7L));
        assertEquals("new-refresh", cipher.decifrar(committed.getRefreshCipher(), 7L, "refresh"));
        assertEquals(oldRefresh, committed.getPreviousRefreshCipher());
        verify(tokens, times(1)).renovar("old-refresh");
        assertTrue(Files.exists(directory.resolve("7.journal")));
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCommit();
        assertFalse(Files.exists(directory.resolve("7.journal")));
    }
    @Test void aFlushDoesNotDeleteRecoveryBeforeCommit() {
        when(repo.bloquear(7L)).thenReturn(Optional.of(stored()));
        TransactionSynchronizationManager.initSynchronization();
        assertEquals("new-access", service(journal).acesso(7L));
        assertTrue(journal.ler(7L).isPresent());
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertTrue(journal.ler(7L).isPresent());
    }
    @Test void journalCannotBeReusedByAnotherCompany() throws Exception {
        when(repo.bloquear(7L)).thenReturn(Optional.of(stored()));
        service(journal).acesso(7L);
        Files.copy(directory.resolve("7.journal"), directory.resolve("8.journal"));
        assertThrows(IllegalStateException.class, () -> journal.ler(8L));
    }
    @Test void unavailableJournalPreventsRemoteRotation() throws Exception {
        var invalid = directory.resolve("not-a-directory"); Files.writeString(invalid, "test");
        var blocked = new ContaAzulTokenJournal(cipher, new ObjectMapper().findAndRegisterModules(), invalid.toString());
        when(repo.bloquear(7L)).thenReturn(Optional.of(stored()));
        assertThrows(IllegalStateException.class, () -> service(blocked).acesso(7L));
        verifyNoInteractions(tokens);
    }
}
