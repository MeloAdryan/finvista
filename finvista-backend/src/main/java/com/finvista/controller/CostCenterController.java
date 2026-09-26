package com.finvista.controller;

import com.finvista.dto.CostCenterResponse;
import com.finvista.service.CostCenterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/centros-custo")
public class CostCenterController {

    private final CostCenterService costCenterService;

    public CostCenterController(
            CostCenterService costCenterService) {
        this.costCenterService = costCenterService;
    }

    @GetMapping
    public List<CostCenterResponse> getCentrosCusto() {
        return costCenterService.listar();
    }
}