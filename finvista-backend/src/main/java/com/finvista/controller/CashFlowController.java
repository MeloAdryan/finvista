package com.finvista.controller;

import com.finvista.dto.CashFlowResponse;
import com.finvista.service.CashFlowService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fluxo-caixa")
public class CashFlowController {

        private final CashFlowService cashFlowService;

        public CashFlowController(
                        CashFlowService cashFlowService) {
                this.cashFlowService = cashFlowService;
        }

        @GetMapping
        public List<CashFlowResponse> getFluxoCaixa() {

                return cashFlowService
                                .obterFluxoCaixa();
        }
}