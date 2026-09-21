package com.finvista.service;

import com.finvista.dto.CostCenterResponse;
import com.finvista.model.CostCenter;
import com.finvista.repository.CostCenterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CostCenterServiceTest {

    private CostCenterRepository costCenterRepository;
    private CostCenterService costCenterService;

    @BeforeEach
    void setUp() {
        costCenterRepository =
                mock(CostCenterRepository.class);

        costCenterService =
                new CostCenterService(
                        costCenterRepository
                );
    }

    @Test
    void deveListarCentrosDeCustoOrdenadosPeloRepository() {

        CostCenter primeiro =
                new CostCenter(
                        "Produção",
                        new BigDecimal("50000.00")
                );

        CostCenter segundo =
                new CostCenter(
                        "Administrativo",
                        new BigDecimal("20000.00")
                );

        when(
                costCenterRepository
                        .findAllByOrderByValorDesc()
        ).thenReturn(
                List.of(
                        primeiro,
                        segundo
                )
        );

        List<CostCenterResponse> resultado =
                costCenterService.listar();

        assertEquals(
                2,
                resultado.size()
        );

        assertEquals(
                "Produção",
                resultado.get(0).nome()
        );

        assertEquals(
                new BigDecimal("50000.00"),
                resultado.get(0).valor()
        );

        assertEquals(
                "Administrativo",
                resultado.get(1).nome()
        );

        assertEquals(
                new BigDecimal("20000.00"),
                resultado.get(1).valor()
        );
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistiremCentrosDeCusto() {

        when(
                costCenterRepository
                        .findAllByOrderByValorDesc()
        ).thenReturn(
                List.of()
        );

        List<CostCenterResponse> resultado =
                costCenterService.listar();

        assertEquals(
                0,
                resultado.size()
        );
    }
}