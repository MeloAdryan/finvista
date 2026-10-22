package com.finvista.controller;

import com.finvista.dto.RealizedCashFlowResponse;
import com.finvista.service.RealizedCashFlowService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fluxo-caixa/realizado")
public class RealizedCashFlowController {

    private final RealizedCashFlowService service;

    public RealizedCashFlowController(RealizedCashFlowService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<RealizedCashFlowResponse> consultar(@RequestParam(defaultValue = "6") String periodo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.consultar(periodo));
    }
}
