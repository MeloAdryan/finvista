package com.finvista.integration.contaazul;

import com.finvista.repository.*;
import com.finvista.service.ClienteContextService;
import java.time.*;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class ContaAzulCompanyService {

    public record Identidade(Long clienteId, String id, String documento, String nome, Instant verificadaEm, Instant confirmadaEm) {}

    public record Confirmacao(Long clienteId, String id, String documento, boolean confirmo) {}
    private final ContaAzulConnectionRepository conexoes;
    private final ContaAzulAutoConfigRepository configs;
    private final ContaAzulAutoAccess access;
    private final ContaAzulCompanyClient client;
    private final ClienteContextService ctx;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    private ContaAzulTokenCipher cipher;
    private final TransactionTemplate tx;

    public ContaAzulCompanyService(ContaAzulConnectionRepository conexoes, ContaAzulAutoConfigRepository configs,
            ContaAzulAutoAccess access, ContaAzulCompanyClient client, ClienteContextService ctx, Clock clock, PlatformTransactionManager tm) {
        this.conexoes = conexoes;
        this.configs = configs;
        this.access = access;
        this.client = client;
        this.ctx = ctx;
        this.clock = clock;
        tx = new TransactionTemplate(tm);
    }

    public Identidade consultar() {
        ctx.validarAdministrador();
        Long id = ctx.getClienteAtualId();
        return tx.execute(t -> view(conexoes.findById(id).orElseThrow()));
    }

    public Identidade verificar() {
        ctx.validarAdministrador();
        Long id = ctx.getClienteAtualId();
        String token = access.acesso(id);
        ContaAzulCompanyClient.Empresa e;
        try {
            e = client.consultar(token);
        } catch (org.springframework.web.server.ResponseStatusException erro) {
            if (!ContaAzulAutoDiagnostic.http(erro, 401)) {
                throw erro;
            }
            token = access.renovarAposRecusa(id, token);
            e = client.consultar(token);
        }
        final var empresa = e;
        final String consultado = token;
        return tx.execute(t -> {
            ctx.validarAdministrador();
            exigir(id);
            configs.bloquear(id);
            var c = conexoes.bloquear(id).orElseThrow();
            // Reject a response from an OAuth authorization replaced while the HTTP call was running.
            String atual = cipher.decifrar(c.getAccessCipher(), id, "access");
            if (!Objects.equals(atual, consultado)) {
                throw new IllegalArgumentException("A conexão mudou. Verifique a empresa novamente.");
            }
            if ((!Objects.equals(empresa.id(), c.getEmpresaExternaId()) || !Objects.equals(empresa.documento(), c.getEmpresaDocumento())) && c.getEmpresaConfirmadaEm() != null) {
                pausar(id);
            }
            if (atual == null) {
                throw new IllegalStateException("Conexão indisponível.");
            }
            c.identificar(empresa.id(), empresa.documento(), empresa.nome(), clock.instant());
            conexoes.saveAndFlush(c);
            return view(c);
        });
    }

    public Identidade confirmar(Confirmacao p) {
        ctx.validarAdministrador();
        Long id = ctx.getClienteAtualId();
        return tx.execute(t -> {
            ctx.validarAdministrador();
            exigir(id);
            configs.bloquear(id);
            if (p == null || !Objects.equals(id, p.clienteId()) || !p.confirmo()) {
                throw new IllegalArgumentException("Confirme a empresa exibida.");
            }
            var c = conexoes.bloquear(id).orElseThrow();
            if (c.getEmpresaVerificadaEm() == null || c.getEmpresaVerificadaEm().isBefore(clock.instant().minusSeconds(600))
                    || !Objects.equals(p.id(), c.getEmpresaExternaId()) || !Objects.equals(p.documento(), c.getEmpresaDocumento())) {
                throw new IllegalArgumentException("Identificação mudou ou expirou. Consulte a empresa novamente.");
            }
            c.confirmarEmpresa(ctx.getUsuarioAutenticado().getId(), clock.instant());
            conexoes.saveAndFlush(c);
            return view(c);
        });
    }

    private void exigir(Long id) {
        if (!id.equals(ctx.getClienteAtualId())) {
            throw new IllegalArgumentException("A empresa selecionada mudou.");

        }}

    private void pausar(Long id) {
        configs.bloquear(id).ifPresent(c -> {
            c.habilitada = false;
            c.proximaEm = null;
            c.dono = null;
            c.reservaAte = null;
        });
    }

    private Identidade view(com.finvista.model.ContaAzulConnection c) {
        return new Identidade(c.getClienteId(), c.getEmpresaExternaId(), c.getEmpresaDocumento(), c.getEmpresaRazaoSocial(), c.getEmpresaVerificadaEm(), c.getEmpresaConfirmadaEm());
    }
}
