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
    @org.springframework.beans.factory.annotation.Autowired
    private ContaAzulTokenJournal journal;
    @org.springframework.beans.factory.annotation.Autowired
    private com.finvista.repository.ContaAzulAutoConfigRepository automacoes;

    public ContaAzulAutoAccess(ContaAzulConnectionRepository conexoes, ContaAzulTokenCipher cipher,
            ContaAzulTokenClient tokens, ContaAzulConfiguration config, Clock clock) {
        this.conexoes = conexoes;
        this.cipher = cipher;
        this.tokens = tokens;
        this.config = config;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ContaAzulReauthorizationRequired.class)
    public String acesso(Long clienteId) {
        config.exigirAtiva();
        var c = conexoes.bloquear(clienteId).orElseThrow(() -> new IllegalStateException("A empresa precisa autorizar a conexão Conta Azul."));
        if ("REAUTORIZAR".equals(c.getStatus())) throw new ContaAzulReauthorizationRequired();
        recuperar(c);
        if (c.getExpiresAt() == null || !c.getExpiresAt().isAfter(clock.instant().plusSeconds(60))) {
            var t = renovarTokens(c, clienteId);
            guardar(c, t);
        }
        return cipher.decifrar(c.getAccessCipher(), clienteId, "access");
    }

    private ContaAzulTokenClient.Tokens renovarTokens(com.finvista.model.ContaAzulConnection c, Long clienteId) {
        try {
            journal.preparar(clienteId);
            return tokens.renovar(cipher.decifrar(c.getRefreshCipher(), clienteId, "refresh"));
        } catch (ContaAzulReauthorizationRequired erro) {
            ContaAzulConnectionLifecycle.reautorizar(c, automacoes, clock.instant());
            conexoes.saveAndFlush(c);
            throw erro;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ContaAzulReauthorizationRequired.class)
    public String renovarAposRecusa(Long clienteId, String recusado) {
        config.exigirAtiva();
        var c = conexoes.bloquear(clienteId).orElseThrow(ContaAzulReauthorizationRequired::new);
        if ("REAUTORIZAR".equals(c.getStatus())) throw new ContaAzulReauthorizationRequired();
        recuperar(c);
        String atual = cipher.decifrar(c.getAccessCipher(), clienteId, "access");
        if (!java.util.Objects.equals(atual, recusado)) return atual; // Another request refreshed first.
        var t = renovarTokens(c, clienteId);
        guardar(c, t);
        return t.access();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ContaAzulReauthorizationRequired.class)
    public void invalidar(Long clienteId) {
        var c = conexoes.bloquear(clienteId).orElseThrow(ContaAzulReauthorizationRequired::new);
        ContaAzulConnectionLifecycle.reautorizar(c, automacoes, clock.instant());
        conexoes.saveAndFlush(c);
        throw new ContaAzulReauthorizationRequired();
    }


    private void guardar(com.finvista.model.ContaAzulConnection c, ContaAzulTokenClient.Tokens tokens) {
        Long cliente = c.getClienteId();
        String ac = cipher.cifrar(tokens.access(), cliente, "access");
        String rc = cipher.cifrar(tokens.refresh(), cliente, "refresh");
        var pending = journal.gravar(c, ac, rc, clock.instant().plusSeconds(tokens.expires()), clock.instant());
        c.atualizar(ac, rc, clock.instant().plusSeconds(tokens.expires()), clock.instant());
        conexoes.saveAndFlush(c);
        journal.concluir(pending);
    }

    private void recuperar(com.finvista.model.ContaAzulConnection c) {
        var pending = journal.ler(c.getClienteId()).orElse(null);
        if (pending == null) return;
        if (pending.anteriores().contains(ContaAzulTokenJournal.hash(c.getRefreshCipher()))) {
            c.atualizar(pending.access(), pending.refresh(), pending.expires(), clock.instant());
            conexoes.saveAndFlush(c);
        }
        // A newer authorization may have replaced the credentials; never replay an unrelated old response.
        journal.concluir(pending);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ContaAzulReauthorizationRequired.class)
    public String renovarAgora(Long clienteId) {
        config.exigirAtiva();
        var c = conexoes.bloquear(clienteId).orElseThrow(ContaAzulReauthorizationRequired::new);
        if ("REAUTORIZAR".equals(c.getStatus())) throw new ContaAzulReauthorizationRequired();
        recuperar(c);
        var t = renovarTokens(c, clienteId);
        guardar(c, t);
        return t.access();
    }

}
