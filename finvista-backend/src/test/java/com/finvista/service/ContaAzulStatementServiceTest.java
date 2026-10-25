package com.finvista.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ContaAzulStatementServiceTest {

    private final ContaAzulStatementService service = new ContaAzulStatementService();

    private MockMultipartFile file(Workbook book, String... values) throws Exception {
        try (book; var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("Extrato Financeiro");
            var header = sheet.createRow(0);
            String[] names = {"Data movimento", "Descrição", "Tipo", "Origem do lançamento", "Valor (R$)",
                "Data de competência", "Data original de vencimento", "Categoria 1", "Centro de Custo 1"};
            for (int i = 0; i < names.length; i++) {
                header.createCell(i).setCellValue(names[i]);
            }
            var row = sheet.createRow(1);
            String[] data = {"01/09/2026", "Pagamento parcial", "Despesa", "Lançamento Financeiro", "-100,25",
                "01/08/2026", "30/08/2026", "Serviços", "Administrativo"};
            if (values.length > 0) {
                data[4] = values[0];
            }
            if (values.length > 1) {
                data[0] = values[1];
            }
            for (int i = 0; i < data.length; i++) {
                row.createCell(i).setCellValue(data[i]);
            }
            book.write(out);
            return new MockMultipartFile("arquivo", "extrato.xls", "application/octet-stream", out.toByteArray());
        }
    }

    @Test
    void readsOoxmlWithXlsExtensionAndPreservesSignedMovementAndDates() throws Exception {
        var result = service.inspect(file(new XSSFWorkbook()));
        assertTrue(result.reconhecido());
        var line = result.linhas().get(0);
        assertEquals("-100.25", line.valor().toPlainString());
        assertEquals(LocalDate.of(2026, 9, 1), line.movimento());
        assertEquals(LocalDate.of(2026, 8, 1), line.competencia());
        assertEquals(LocalDate.of(2026, 8, 30), line.vencimento());
        assertEquals(List.of("Serviços"), line.categorias());
        assertTrue(line.erros().isEmpty());
    }

    @Test
    void readsRealBinaryXls() throws Exception {
        assertTrue(service.inspect(file(new HSSFWorkbook())).reconhecido());
    }

    @Test
    void flagsInvalidMoneyInsteadOfInventingZero() throws Exception {
        var line = service.inspect(file(new XSSFWorkbook(), "não informado")).linhas().get(0);
        assertNull(line.valor());
        assertFalse(line.erros().isEmpty());
    }

    @Test
    void flagsImpossibleMovementDate() throws Exception {
        var line = service.inspect(file(new XSSFWorkbook(), "-100,25", "31/09/2026")).linhas().get(0);
        assertNull(line.movimento());
        assertFalse(line.erros().isEmpty());
    }

    @Test
    void doesNotMisidentifyPayablesAndDoesNotRejectTheirRepeatedHeaders() throws Exception {
        byte[] bytes;
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var row = book.createSheet().createRow(0);
            row.createCell(0).setCellValue("Data de vencimento");
            row.createCell(1).setCellValue("Centro de Custo 1");
            row.createCell(2).setCellValue("Centro de Custo 1");
            book.write(out);
            bytes = out.toByteArray();
        }
        assertFalse(service.inspect(new MockMultipartFile("arquivo", "contas.xls", "", bytes)).reconhecido());
    }

    @Test
    void guardsOldImportRoutesWithSpecificGuidance() throws Exception {
        var input = file(new XSSFWorkbook());
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new ExcelImportService().processar(input)).getMessage().contains("Extrato financeiro Conta Azul identificado"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new ContaAzulConferenceService().conferir(input, LocalDate.of(2026, 10, 22)))
                .getMessage().contains("Extrato financeiro Conta Azul identificado"));
    }

    @Test
    void rejectsSubcentAmounts() throws Exception {
        assertNull(service.inspect(file(new XSSFWorkbook(), "-1,234")).linhas().get(0).valor());
    }

    @Test
    void keepsCentersInTheirExportedCategoryGroup() throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet();
            var h = sheet.createRow(0);
            var row = sheet.createRow(1);
            String[] names = {"Data movimento", "Descrição", "Tipo", "Origem do lançamento", "Valor (R$)", "Categoria 1", "Valor na Categoria 1", "Centro de Custo 1", "Valor no Centro de Custo 1", "Categoria 2", "Valor na Categoria 2", "Centro de Custo 1", "Valor no Centro de Custo 1"};
            String[] values = {"01/09/2026", "Rateio", "Despesa", "Lançamento Financeiro", "-100,00", "Materiais", "-60,00", "Clínica", "-60,00", "Serviços", "-40,00", "Administração", "-40,00"};
            for (int i = 0; i < names.length; i++) {
                h.createCell(i).setCellValue(names[i]);
                row.createCell(i).setCellValue(values[i]);
            }
            book.write(out);
            var line = service.inspect(new MockMultipartFile("arquivo", "extrato.xls", "application/octet-stream", out.toByteArray())).linhas().get(0);
            assertEquals(2, line.rateios().size());
            assertEquals("Clínica", line.rateios().get(0).centro());
            assertEquals("Administração", line.rateios().get(1).centro());
            assertEquals("-40.00", line.rateios().get(1).valorCentro().toPlainString());
            assertTrue(line.erros().isEmpty());
        }
    }

    @Test
    void rejectsUnstructuredDuplicateCenters() throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var h = book.createSheet().createRow(0);
            String[] names = {"Data movimento", "Descrição", "Tipo", "Origem do lançamento", "Valor (R$)", "Centro de Custo 1", "Centro de Custo 1"};
            for (int i = 0; i < names.length; i++) {
                h.createCell(i).setCellValue(names[i]);
            
            }book.write(out);
            assertThrows(IllegalArgumentException.class, () -> service.inspect(new MockMultipartFile("arquivo", "extrato.xls", "application/octet-stream", out.toByteArray())));
        }
    }
}
