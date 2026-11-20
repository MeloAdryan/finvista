package com.finvista.integration.contaazul;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.model.ContaAzulConnection;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;


@Component
public class ContaAzulTokenJournal {
    record Pending(Long cliente, Set<String> anteriores, String access, String refresh, Instant expires, Instant criado) {
        @Override public String toString() { return "Pending[credenciais omitidas]"; }
    }
    private final Path directory;
    private final ContaAzulTokenCipher cipher;
    private final ObjectMapper json;
    private static final java.util.concurrent.ConcurrentHashMap<Path, java.util.concurrent.locks.ReentrantLock> LOCKS = new java.util.concurrent.ConcurrentHashMap<>();
    @FunctionalInterface private interface Action<T> { T run() throws Exception; }

    public ContaAzulTokenJournal(ContaAzulTokenCipher cipher, ObjectMapper json,
            @Value("${CONTA_AZUL_TOKEN_JOURNAL_DIR:${java.io.tmpdir}/finvista-token-journal}") String directory) {
        this.cipher = cipher;
        this.json = json;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    private Path path(Long cliente) {
        if (cliente == null || cliente <= 0) throw new IllegalArgumentException("Empresa inválida.");
        return directory.resolve(cliente + ".journal");
    }

    Optional<Pending> ler(Long cliente) {
        if (!Files.exists(path(cliente))) return Optional.empty();
        return locked(cliente, () -> lerSemTrava(cliente));
    }

    private Optional<Pending> lerSemTrava(Long cliente) {
        Path file = path(cliente);
        if (!Files.exists(file)) return Optional.empty();
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 262144) throw new IllegalStateException();
            String text = cipher.decifrar(Files.readString(file, StandardCharsets.UTF_8), cliente, "refresh-journal");
            Pending p = json.readValue(text, Pending.class);
            if (!cliente.equals(p.cliente()) || p.anteriores() == null || p.access() == null || p.refresh() == null
                    || p.expires() == null || p.criado() == null) throw new IllegalStateException();
            return Optional.of(p);
        } catch (Exception e) { throw new IllegalStateException("Não foi possível ler a recuperação cifrada da conexão Conta Azul."); }
    }

    void preparar(Long cliente) {

        Path temp = null;
        try {
            Files.createDirectories(directory);
            temp = Files.createTempFile(directory, cliente + "-check-", ".tmp");
            try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
        } catch (Exception e) { throw new IllegalStateException("O diretório de recuperação de tokens não está disponível. Confira CONTA_AZUL_TOKEN_JOURNAL_DIR."); }
        finally { if (temp != null) try { Files.deleteIfExists(temp); } catch (Exception ignored) { } }
    }

    Pending gravar(ContaAzulConnection c, String access, String refresh, Instant expires, Instant now) {
        return locked(c.getClienteId(), () -> gravarSemTrava(c, access, refresh, expires, now));
    }

    private Pending gravarSemTrava(ContaAzulConnection c, String access, String refresh, Instant expires, Instant now) {
        var anteriores = new LinkedHashSet<String>();
        var existente = lerSemTrava(c.getClienteId()).orElse(null);
        if (existente != null && (existente.refresh().equals(c.getRefreshCipher())
                || existente.anteriores().contains(hash(c.getRefreshCipher())))) anteriores.addAll(existente.anteriores());
        anteriores.add(hash(c.getRefreshCipher()));
        Pending pending = new Pending(c.getClienteId(), Set.copyOf(anteriores), access, refresh, expires, now);
        Path temp = null;
        try {
            preparar(c.getClienteId());
            byte[] sealed = cipher.cifrar(json.writeValueAsString(pending), c.getClienteId(), "refresh-journal").getBytes(StandardCharsets.UTF_8);
            temp = Files.createTempFile(directory, c.getClienteId() + "-", ".tmp");
            try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap(sealed);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(temp, path(c.getClienteId()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return pending;
        } catch (Exception e) { throw new IllegalStateException("Não foi possível preservar a renovação da conexão. Confira o volume de recuperação de tokens."); }
        finally { if (temp != null) try { Files.deleteIfExists(temp); } catch (Exception ignored) { } }
    }

    void concluir(Pending pending) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return; // A flush alone is not a commit.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    locked(pending.cliente(), () -> {
                        var atual = lerSemTrava(pending.cliente()).orElse(null);
                        if (pending.equals(atual)) Files.deleteIfExists(path(pending.cliente()));
                        return null;
                    });
                } catch (Exception e) {

                    org.slf4j.LoggerFactory.getLogger(ContaAzulTokenJournal.class).warn("Recuperação de token aguardando limpeza; empresa={}", pending.cliente());
                }
            }
        });
    }

    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Não foi possível conferir a identidade da credencial."); }
    }

    private <T> T locked(Long cliente, Action<T> action) {
        Path lockFile = directory.resolve(cliente + ".lock");
        var guard = LOCKS.computeIfAbsent(lockFile, ignored -> new java.util.concurrent.locks.ReentrantLock());
        guard.lock();
        try {
            Files.createDirectories(directory);
            try (var channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    var lock = channel.lock()) { return action.run(); }
        } catch (Exception e) { throw new IllegalStateException("Não foi possível proteger o registro de recuperação da conexão Conta Azul."); }
        finally { guard.unlock(); }
    }
}
