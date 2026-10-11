package com.finvista.controller;

import com.finvista.integration.contaazul.*;
import com.finvista.service.ClienteContextService;
import jakarta.servlet.http.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integracoes/conta-azul")
public class ContaAzulOAuthController {

    private static final String PENDENTE = "FINVISTA_CONTA_AZUL_OAUTH";

    public record Pendente(String state, Long clienteId, Long usuarioId, long expiraEm) implements java.io.Serializable {}
    private final ContaAzulProperties p;
    private final ContaAzulConfiguration cfg;
    private final ContaAzulOAuthService oauth;
    private final ContaAzulConnectionService connections;
    private final ClienteContextService context;
    private final SecureRandom random = new SecureRandom();

    public ContaAzulOAuthController(ContaAzulProperties p, ContaAzulConfiguration cfg, ContaAzulOAuthService oauth, ContaAzulConnectionService connections, ClienteContextService context) {
        this.p = p;
        this.cfg = cfg;
        this.oauth = oauth;
        this.connections = connections;
        this.context = context;
    }

    @GetMapping("/status")
    public ResponseEntity<ContaAzulConnectionService.Status> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(connections.status());
    }

    @PostMapping(value = "/autorizacao", headers = "X-Finvista-Integration=1")
    public ResponseEntity<Map<String, String>> iniciar(HttpServletRequest request) {
        context.validarAdministrador();
        cfg.exigirAtiva();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        request.getSession(true).setAttribute(PENDENTE, new Pendente(state, context.getClienteAtualId(), context.getUsuarioAutenticado().getId(), Instant.now().plusSeconds(600).getEpochSecond()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("authorizationUrl", oauth.gerarUrlAutorizacao(p.getRedirectUri(), state)));
    }

    @PostMapping(value = "/renovar", headers = "X-Finvista-Integration=1")
    public ResponseEntity<ContaAzulConnectionService.Status> renovar() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(connections.renovar());
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(HttpServletRequest request, @RequestParam(name = "code", required = false) String code, @RequestParam(name = "state", required = false) String state, @RequestParam(name = "error", required = false) String error) {
        context.validarAdministrador();
        HttpSession session = request.getSession(false);
        Pendente pending = null;
        if (session != null) {
            synchronized (session) {
                Object v = session.getAttribute(PENDENTE);
                session.removeAttribute(PENDENTE);
                if (v instanceof Pendente valor) {
                    pending = valor;

                }
            }
        }
        if (pending == null || state == null || !MessageDigest.isEqual(pending.state().getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8)) || pending.expiraEm() <= Instant.now().getEpochSecond() || !pending.usuarioId().equals(context.getUsuarioAutenticado().getId()) || !pending.clienteId().equals(context.getClienteAtualId())) {
            throw new IllegalArgumentException("Autorização inválida, expirada ou contexto alterado. Inicie novamente pelo FinVista.");
        }
        if (error != null) {
            throw new IllegalArgumentException("Autorização não concluída na Conta Azul.");

        }
        if (code == null || code.isBlank() || code.length() > 4096) {
            throw new IllegalArgumentException("Código de autorização inválido.");

        }
        connections.conectar(pending.clienteId(), code);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer").contentType(MediaType.TEXT_PLAIN).body("Conta Azul autorizada para a empresa selecionada. Volte ao FinVista e atualize o status. Nenhum lançamento foi importado automaticamente.");
    }
}
