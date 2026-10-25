package com.finvista.controller;

import java.io.IOException;
import com.finvista.service.ClienteContextService;
import com.finvista.service.ContaAzulStatementService;
import com.finvista.service.StatementImportService;
import jakarta.servlet.http.HttpSession;
import java.time.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/importacoes/conta-azul/extrato")
public class ContaAzulStatementController {
    private final ContaAzulStatementService service;
    private final ClienteContextService context;
    private final StatementImportService importer;
    private final Clock clock;
    private static final String SESSION_KEY = "FINVISTA_EXTRATO_PREVIA";
    private record Pending(String token, Instant expires, StatementImportService.Preview preview) {}
    public record Plan(String token, Instant validade, Long clienteId, String clienteNome, String arquivo,
            int total, int novas, int existentes, java.util.List<String> conflitos) {}
    public ContaAzulStatementController(ContaAzulStatementService service, ClienteContextService context,
            StatementImportService importer, Clock clock) {
        this.service = service;
        this.context = context;
        this.importer = importer;
        this.clock = clock;
    }
    @PostMapping("/conferencia")
    public ContaAzulStatementService.Result inspect(@RequestParam("arquivo") MultipartFile file) {
        context.getClienteAtual();
        try { return service.inspect(file); }
        catch (IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    e instanceof IllegalArgumentException ? e.getMessage() : "Não foi possível ler o arquivo Excel.");
        }
    }
    @GetMapping("/movimentos")
    public org.springframework.http.ResponseEntity<ContaAzulStatementService.Result> stored(@RequestParam Long clienteId) {
        try {
            return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(importer.stored(clienteId));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
    @PostMapping("/planejar")
    public Plan plan(@RequestParam("arquivo") MultipartFile file, @RequestParam Long clienteId, HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        try {
            if (!clienteId.equals(context.getClienteAtualId())) throw new IllegalArgumentException("A empresa mudou. Atualize a página.");
            var p = importer.plan(file);
            if (!clienteId.equals(p.clienteId())) throw new IllegalArgumentException("A empresa mudou. Gere outra prévia.");
            var pending = new Pending(UUID.randomUUID().toString(), clock.instant().plusSeconds(600), p);
            session.setAttribute(SESSION_KEY, pending);
            return new Plan(pending.token(), pending.expires(), p.clienteId(), p.clienteNome(), p.arquivo(),
                    p.total(), p.novas(), p.existentes(), p.conflitos());
        } catch (IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
    @PostMapping("/importar")
    public StatementImportService.Imported apply(@RequestParam("arquivo") MultipartFile file,
            @RequestParam String token, @RequestParam Long clienteId, @RequestParam boolean confirmado, HttpSession session) {
        var saved = session.getAttribute(SESSION_KEY);
        if (!(saved instanceof Pending p) || !confirmado || !p.token().equals(token)
                || !p.expires().isAfter(clock.instant()) || !clienteId.equals(p.preview().clienteId())
                || !clienteId.equals(context.getClienteAtualId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Prévia expirada, empresa alterada ou confirmação ausente. Gere outra prévia.");
        try {
            var result = importer.apply(file, p.preview());
            session.removeAttribute(SESSION_KEY);
            return result;
        } catch (IOException | IllegalArgumentException e) {
            session.removeAttribute(SESSION_KEY);
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
