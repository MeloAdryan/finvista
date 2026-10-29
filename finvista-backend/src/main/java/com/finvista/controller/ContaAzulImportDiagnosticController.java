package com.finvista.controller;

import com.finvista.integration.contaazul.ContaAzulImportDiagnosticService;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integracoes/conta-azul/automatica/execucoes")
public class ContaAzulImportDiagnosticController {
    private final ContaAzulImportDiagnosticService service;

    public ContaAzulImportDiagnosticController(ContaAzulImportDiagnosticService service) {
        this.service = service;
    }

    @GetMapping("/{id}/diagnostico-pendencias")
    public ResponseEntity<ContaAzulImportDiagnosticService.Result> diagnostico(
            @RequestParam Long clienteId, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.diagnose(clienteId, id));
    }
}
