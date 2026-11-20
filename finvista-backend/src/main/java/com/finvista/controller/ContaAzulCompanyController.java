package com.finvista.controller;

import com.finvista.integration.contaazul.ContaAzulCompanyService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController
@RequestMapping("/api/integracoes/conta-azul/empresa")
public class ContaAzulCompanyController {

    private final ContaAzulCompanyService service;

    public ContaAzulCompanyController(ContaAzulCompanyService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> consultar() {
        return resposta(service.consultar());
    }

    @PostMapping(value = "/verificar", headers = "X-Finvista-Integration=1")
    public ResponseEntity<?> verificar() {
        return resposta(service.verificar());
    }

    @PostMapping(value = "/confirmar", headers = "X-Finvista-Integration=1")
    public ResponseEntity<?> confirmar(@RequestBody ContaAzulCompanyService.Confirmacao pedido) {
        return resposta(service.confirmar(pedido));
    }

    private ResponseEntity<?> resposta(Object value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }
}
