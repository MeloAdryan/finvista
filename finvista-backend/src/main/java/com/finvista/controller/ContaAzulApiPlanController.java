package com.finvista.controller;

import com.finvista.integration.contaazul.*;
import com.finvista.service.ClienteContextService;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/integracoes/conta-azul")
public class ContaAzulApiPlanController {

    private final ContaAzulConnectionService conexoes;
    private final ContaAzulParcelDetailClient detalhes;
    private final ContaAzulApiPlanService planos;
    private final ClienteContextService contexto;

    public ContaAzulApiPlanController(ContaAzulConnectionService conexoes, ContaAzulParcelDetailClient detalhes,
            ContaAzulApiPlanService planos, ClienteContextService contexto) {
        this.conexoes = conexoes;
        this.detalhes = detalhes;
        this.planos = planos;
        this.contexto = contexto;
    }

    @GetMapping("/parcelas/{id}/plano")
    public ResponseEntity<ContaAzulApiPlanService.Plano> planejar(@PathVariable(name = "id") String id,
            @RequestParam(name = "clienteId") Long clienteId) {
        contexto.validarAdministrador();
        if (clienteId == null || clienteId <= 0 || id == null || !id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Informe empresa e ID válido da parcela.");
        }
        // Reconsulta a fonte: não recebe valores financeiros enviados pelo navegador.
        var acesso = conexoes.acessoParaConsulta(clienteId);
        var parcela = detalhes.consultar(acesso, id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(planos.planejar(clienteId, parcela));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> falha(ResponseStatusException erro) {
        return ResponseEntity.status(erro.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("mensagem", erro.getReason() == null ? "Não foi possível preparar o plano." : erro.getReason()));
    }
}
