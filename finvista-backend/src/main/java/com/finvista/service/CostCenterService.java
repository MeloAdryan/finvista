package com.finvista.service;

import com.finvista.dto.CostCenterResponse;
import com.finvista.model.CostCenter;
import com.finvista.repository.CostCenterRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CostCenterService {

    private final CostCenterRepository costCenterRepository;

    public CostCenterService(
            CostCenterRepository costCenterRepository
    ) {
        this.costCenterRepository =
                costCenterRepository;
    }

    public List<CostCenterResponse> listar() {

        return costCenterRepository
                .findAllByOrderByValorDesc()
                .stream()
                .map(this::criarResposta)
                .toList();
    }

    private CostCenterResponse criarResposta(
            CostCenter costCenter
    ) {
        return new CostCenterResponse(
                costCenter.getNome(),
                costCenter.getValor()
        );
    }
}