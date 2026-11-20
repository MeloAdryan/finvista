package com.finvista.integration.contaazul;

import com.finvista.model.ContaAzulConnection;
import com.finvista.repository.ContaAzulConnectionRepository;
import com.finvista.service.ClienteContextService;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContaAzulConnectionService {

    public record Status(Long clienteId, boolean habilitado, boolean configurado, boolean conectado, Instant expiraEm, String mensagem, String status, String ultimoErro) {}
    private final ContaAzulProperties p;
    private final ContaAzulConfiguration cfg;
    private final ContaAzulTokenCipher cipher;
    private final ContaAzulTokenClient tokens;
    private final ContaAzulConnectionRepository repo;
    private final ClienteContextService context;
    private final ContaAzulAutoAccess automatico;
    @org.springframework.beans.factory.annotation.Autowired
    private com.finvista.repository.ContaAzulAutoConfigRepository automacoes;

    public ContaAzulConnectionService(ContaAzulProperties p, ContaAzulConfiguration cfg, ContaAzulTokenCipher cipher, ContaAzulTokenClient tokens, ContaAzulConnectionRepository repo, ClienteContextService context) {
        this(p, cfg, cipher, tokens, repo, context, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ContaAzulConnectionService(ContaAzulProperties p, ContaAzulConfiguration cfg, ContaAzulTokenCipher cipher, ContaAzulTokenClient tokens, ContaAzulConnectionRepository repo, ClienteContextService context, ContaAzulAutoAccess automatico) {
        this.automatico = automatico;
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
        return new Status(id, p.isEnabled(), cfg.pronta(), c != null && !"REAUTORIZAR".equals(c.getStatus()), c == null ? null : c.getExpiresAt(), !p.isEnabled() ? "Conexão desabilitada; importação por arquivo disponível." : !cfg.pronta() ? "Configure as credenciais, callback e chave no backend." : c == null ? "A empresa ainda não autorizou a conexão." : "REAUTORIZAR".equals(c.getStatus()) ? c.getUltimoErro() : "Autorização armazenada. Acompanhe a automação e as pendências no painel de sincronização.", c == null ? "NAO_CONECTADA" : c.getStatus(), c == null ? null : c.getUltimoErro());
    }

    @Transactional
    public void conectar(Long clienteId, String code) {
        context.validarAdministrador();
        cfg.exigirAtiva();
        if (!clienteId.equals(context.getClienteAtualId())) {
            throw new IllegalStateException("A empresa selecionada mudou. Inicie a autorização novamente.");

        }
        var t = tokens.trocar(code);
        if(automacoes!=null) automacoes.bloquear(clienteId);
        var c = repo.bloquear(clienteId).orElseGet(() -> new ContaAzulConnection(clienteId));
        salvar(c, t);
        c.exigirConfirmacaoEmpresa();
        if(automacoes!=null) automacoes.bloquear(clienteId).ifPresent(a -> { a.habilitada=false; a.proximaEm=null; a.dono=null; a.reservaAte=null; });
    }

    public Status renovar() {
        context.validarAdministrador();
        cfg.exigirAtiva();
        Long id = context.getClienteAtualId();
        automatico.renovarAgora(id);
        if (!id.equals(context.getClienteAtualId())) throw new IllegalArgumentException("A empresa selecionada mudou. Atualize a consulta.");
        return status();
    }

    private void salvar(ContaAzulConnection c, ContaAzulTokenClient.Tokens t) {
        Instant now = Instant.now();
        c.atualizar(cipher.cifrar(t.access(), c.getClienteId(), "access"), cipher.cifrar(t.refresh(), c.getClienteId(), "refresh"), now.plusSeconds(t.expires()), now);
        repo.saveAndFlush(c);
    }



    public String acessoParaConsulta(Long clienteEsperado) {
        context.validarAdministrador();
        cfg.exigirAtiva();
        Long id = context.getClienteAtualId();
        if (!id.equals(clienteEsperado)) throw new IllegalArgumentException("A empresa selecionada mudou. Atualize a consulta.");
        var conexao = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Autorize esta empresa na Conta Azul antes de consultar."));
        if(conexao.getEmpresaConfirmadaEm()==null) throw new IllegalArgumentException("Verifique e confirme a empresa na conexão Conta Azul antes de consultar seus dados financeiros.");
        String token = automatico.acesso(id);
        if (!id.equals(context.getClienteAtualId())) throw new IllegalArgumentException("A empresa selecionada mudou. Atualize a consulta.");
        return token;
    }
}
