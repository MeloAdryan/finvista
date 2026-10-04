package com.finvista.controller;

import com.finvista.dto.CostCenterResponse;
import com.finvista.service.CostCenterService;
import com.finvista.service.FinancialReferenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/centros-custo")
public class CostCenterController {

    private final CostCenterService costCenterService;


    private final FinancialReferenceService financialReferenceService;

    public CostCenterController(
            CostCenterService costCenterService,
            FinancialReferenceService financialReferenceService
    ) {
        this.costCenterService = costCenterService;
        this.financialReferenceService = financialReferenceService;
    }

    @GetMapping
    public List<CostCenterResponse> getCentrosCusto(
            @RequestParam(defaultValue = "false") boolean historico
    ) {
        if (historico) {
            return costCenterService.listar();
        }

        return costCenterService.listarNoMes(
                financialReferenceService.obterMesReferencia()
        );
    }
    
    }