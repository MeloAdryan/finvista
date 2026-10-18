package com.finvista.integration.contaazul;

import com.finvista.repository.ContaAzulConnectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;

@Service
public class ContaAzulAutoAccess {

    private final ContaAzulConnectionRepository conexoes;
    private final ContaAzulTokenCipher cipher;
    private final ContaAzulTokenClient tokens;
    private final ContaAzulConfiguration config;
    private final Clock clock;

    public ContaAzulAutoAccess(ContaAzulConnectionRepository conexoes, ContaAzulTokenCipher cipher,
            ContaAzulTokenClient tokens, ContaAzulConfiguration config, Clock clock) {
        this.conexoes = conexoes;
        this.cipher = cipher;
        this.tokens = tokens;
        this.config = config;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String acesso(Long clienteId) {
        config.exigirAtiva();
        var c = conexoes.bloquear(clienteId).orElseThrow(() -> new IllegalStateException("A empresa precisa autorizar a conexão Conta Azul."));
        if (c.getExpiresAt() == null || !c.getExpiresAt().isAfter(clock.instant().plusSeconds(60))) {
            var t = tokens.renovar(cipher.decifrar(c.getRefreshCipher(), clienteId, "refresh"));
            c.atualizar(cipher.cifrar(t.access(), clienteId, "access"), cipher.cifrar(t.refresh(), clienteId, "refresh"),
                    clock.instant().plusSeconds(t.expires()), clock.instant());
            conexoes.saveAndFlush(c);
        }
        return cipher.decifrar(c.getAccessCipher(), clienteId, "access");
    }
}
