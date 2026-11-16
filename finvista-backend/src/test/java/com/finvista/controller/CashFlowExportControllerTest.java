package com.finvista.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CashFlowExportControllerTest {

    @ParameterizedTest
    @ValueSource(strings = {"xls", "xlsx"})
    void arquivoExcelPreservaMesesEValoresNumericos(String formato) throws Exception {
        var mapper = new ObjectMapper();
        List<List<JsonNode>> linhas = mapper.readValue("""
                [
                  ["Mês", "Receitas", "Despesas", "Resultado", "Acumulado", "Histórico", "Base anterior"],
                  ["out/2026", 25751.25, 10551.75, 15199.50, 15199.50, 16199.50, 1000.00],
                  ["nov/2026", 0, 150.75, -150.75, 15048.75, 16048.75, 16199.50]
                ]
                """, new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        var resposta = new CashFlowExportController().exportar(formato, linhas);
        assertTrue(resposta.getHeaders().getContentDisposition().getFilename().endsWith("." + formato));
        try (var arquivo = WorkbookFactory.create(new ByteArrayInputStream(resposta.getBody()))) {
            var planilha = arquivo.getSheetAt(0);
            assertEquals(3, planilha.getPhysicalNumberOfRows());
            assertEquals("Mês", planilha.getRow(0).getCell(0).getStringCellValue());
            assertEquals("out/2026", planilha.getRow(1).getCell(0).getStringCellValue());
            assertEquals(CellType.NUMERIC, planilha.getRow(1).getCell(1).getCellType());
            assertEquals(25_751.25, planilha.getRow(1).getCell(1).getNumericCellValue());
            assertEquals(-150.75, planilha.getRow(2).getCell(3).getNumericCellValue());
        }
    }
}


     
           