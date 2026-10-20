package com.finvista.integration.contaazul;

import com.finvista.model.ContaAzulConnection;
import com.finvista.repository.ContaAzulConnectionRepository;
import com.finvista.service.ClienteContextService;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContaAzulConnectionService {

    public record Status(Long clienteId, boolean habilitado, boolean configurado, boolean conectado, Instant expiraEm, String mensagem) {}
    private final ContaAzulProperties p;
    private final ContaAzulConfiguration cfg;
    private final ContaAzulTokenCipher cipher;
    private final ContaAzulTokenClient tokens;
    private final ContaAzulConnectionRepository repo;
    private final ClienteContextService context;

    public ContaAzulConnectionService(ContaAzulProperties p, ContaAzulConfiguration cfg, ContaAzulTokenCipher cipher, ContaAzulTokenClient tokens, ContaAzulConnectionRepository repo, ClienteContextService context) {
        this.p = p;
        this.cfg = cfg;
        this.cipher = cipher;
        this.tokens = tokens;
        this.repo = repo;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Status status() {
        context.validarAdministrador();
        Long id = context.getClienteAtualId();
        var c = repo.findById(id).orElse(null);
        return new Status(id, p.isEnabled(), cfg.pronta(), c != null, c == null ? null : c.getExpiresAt(), !p.isEnabled() ? "Conexão desabilitada; importação por arquivo disponível." : !cfg.pronta() ? "Configure as credenciais, callback e chave no backend." : c == null ? "A empresa ainda não autorizou a conexão." : "Autorização armazenada. Acompanhe a automação e as pendências no painel de sincronização.");
    }

    @Transactional
    public void conectar(Long clienteId, String code) {
        context.validarAdministrador();
        cfg.exigirAtiva();
        if (!clienteId.equals(context.getClienteAtualId())) {
            throw new IllegalStateException("A empresa selecionada mudou. Inicie a autorização novamente.");

        }
        var t = tokens.trocar(code);
        var c = repo.bloquear(clienteId).orElseGet(() -> new ContaAzulConnection(clienteId));
        salvar(c, t);
    }

    @Transactional
    public Status renovar() {
        context.validarAdministrador();
        cfg.exigirAtiva();
        Long id = context.getClienteAtualId();
        var c = repo.bloquear(id).orElseThrow(() -> new IllegalArgumentException("Empresa ainda não conectada."));
        salvar(c, tokens.renovar(cipher.decifrar(c.getRefreshCipher(), id, "refresh")));
        return status();
    }

    private void salvar(ContaAzulConnection c, ContaAzulTokenClient.Tokens t) {
        Instant now = Instant.now();
        c.atualizar(cipher.cifrar(t.access(), c.getClienteId(), "access"), cipher.cifrar(t.refresh(), c.getClienteId(), "refresh"), now.plusSeconds(t.expires()), now);
        repo.saveAndFlush(c);
    }

     

    @Transactional(readOnly = true)
    public String acessoParaConsulta(Long clienteEsperado) {
    context.validarAdministrador();
        cfg.exigirAtiva();
        Long id = context.getClienteAtualId();
        if (!id.equals(clienteEsperado)) {

    throw new IllegalArgumentException("A empresa selecionada mudou. Atualize a consulta.");
        }
        var c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Autorize esta empresa na Conta Azul antes de consultar."));
        if (c.getExpiresAt() == null || !c.getExpiresAt().isAfter(Instant.now().plusSeconds(30))) {
            throw new IllegalArgumentException("O acesso precisa ser renovado. Use Renovar acesso no painel da conexão.");
        }
        return cipher.decifrar(c.getAccessCipher(), id, "access");
    }
}
