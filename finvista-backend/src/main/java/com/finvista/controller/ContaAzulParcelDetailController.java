package com.finvista.controller;

import com.finvista.integration.contaazul.*;
import com.finvista.service.ClienteContextService;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/integracoes/conta-azul")
public class ContaAzulParcelDetailController {

    public record Resposta(Long clienteId, Instant consultadoEm, ContaAzulParcelDetail parcela) {}
    private final ContaAzulConnectionService conexoes;
    private final ContaAzulParcelDetailClient parcelas;
    private final ClienteContextService contexto;

    public ContaAzulParcelDetailController(ContaAzulConnectionService conexoes,
            ContaAzulParcelDetailClient parcelas, ClienteContextService contexto) {
        this.conexoes = conexoes;
        this.parcelas = parcelas;
        this.contexto = contexto;
    }

    @GetMapping("/parcelas/{id}")
    public ResponseEntity<Resposta> consultar(
            @PathVariable(name = "id") String id,
            @RequestParam(name = "clienteId") Long clienteId) {
        contexto.validarAdministrador();

        if (clienteId == null || clienteId <= 0 || id == null
                || !id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Informe empresa e identificador válido da parcela.");
        }
        String acesso = conexoes.acessoParaConsulta(clienteId);
        var detalhe = parcelas.consultar(acesso, id);
        if (!clienteId.equals(contexto.getClienteAtualId())) {
            throw new IllegalArgumentException("A empresa selecionada mudou. Consulte novamente.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Resposta(clienteId, Instant.now(), detalhe));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> falhaConsulta(ResponseStatusException erro) {
        return ResponseEntity.status(erro.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("mensagem", erro.getReason() == null ? "Falha ao consultar detalhes." : erro.getReason()));
    }
}
