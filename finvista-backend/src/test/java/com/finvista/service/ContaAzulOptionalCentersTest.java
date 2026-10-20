package com.finvista.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class ContaAzulOptionalCentersTest {
    private final ContaAzulConferenceService service = new ContaAzulConferenceService();
    private final LocalDate corte = LocalDate.of(2026,10,18);
    private MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("arquivo", "fonte.xls", "application/octet-stream", bytes); }

    private byte[] semColunas(boolean somenteValorCentro) throws Exception {
        var bytes = ContaAzulConferenceServiceTest.fixture("21/09/2026", "100,00", "0", false);
        try (var source = WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes)); var target = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var from = source.getSheetAt(0); var to = target.createSheet("Contas");
            var keep = new ArrayList<Integer>();
            for (var c : from.getRow(0)) {
                String header = c.getStringCellValue().toLowerCase(Locale.ROOT);
                if (header.startsWith("valor no centro de custo") || (!somenteValorCentro && header.startsWith("centro de custo"))) continue;
                keep.add(c.getColumnIndex());
            }
            for (var row : from) {
                var output = to.createRow(row.getRowNum());
                for (int i=0; i<keep.size(); i++) {
                    var value = row.getCell(keep.get(i));
                    if (value != null) output.createCell(i).setCellValue(new DataFormatter().formatCellValue(value));
                }
            }
            target.write(out); return out.toByteArray();
        }
    }
    @Test void categoriasAdjacentesSemCentroNaoSeMisturam() throws Exception {
        var result = service.conferir(file(semColunas(false)), corte);
        assertEquals(1, result.aceitas()); assertEquals(0, result.rejeitadas());
        var rates = result.linhas().get(0).rateios();
        assertEquals(2, rates.size());
        assertEquals("A", rates.get(0).categoria()); assertEquals("B", rates.get(1).categoria());
        assertEquals(0, rates.get(0).valorCategoria().compareTo(new BigDecimal("-60")));
        assertEquals(0, rates.get(1).valorCategoria().compareTo(new BigDecimal("-40")));
        for (var rate : rates) { assertNull(rate.centro()); assertNull(rate.valorCentro()); }
    }
    @Test void centroComColunaDeValorAusenteContinuaRejeitado() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.conferir(file(semColunas(true)), corte));
    }
    @Test void exportacaoRealSemCentroMantemDuasDespesas() throws Exception {
        String path = System.getProperty("finvista.excel.teste");
        Assumptions.assumeTrue(path != null);
        var arquivo = file(Files.readAllBytes(Path.of(path)));
        var result = service.conferir(arquivo, corte);
        assertEquals(2, result.aceitas()); assertEquals(0, result.rejeitadas());
        assertEquals(0, result.valorOriginal().compareTo(new BigDecimal("30")));
        var line = result.linhas().stream().filter(l -> l.descricao().equals("Teste conciliação Excel API")).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026,10,18), line.competencia());
        assertEquals(LocalDate.of(2026,10,25), line.vencimento());
        assertEquals(0, line.original().compareTo(new BigDecimal("20")));
        assertNull(line.rateios().get(0).centro()); assertNull(line.rateios().get(0).valorCentro());
        assertEquals(0, line.rateios().get(0).valorCategoria().compareTo(new BigDecimal("-20")));
        var imported = new ExcelImportService().processar(arquivo);
        assertEquals(2, imported.size());
        assertTrue(imported.stream().allMatch(t -> "CONTA_AZUL_EXCEL".equals(t.getOrigem())));
    }
}
