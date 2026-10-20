package com.finvista.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExcelContaAzulColumnsTest {

    private MockMultipartFile arquivo(String colunaAberto, String aberto, String data, boolean ajustes, boolean conflitante) throws Exception {
        try (var w = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var s = w.createSheet("Contas");
            var h = s.createRow(0);
            var r = s.createRow(1);
            var nomes = new ArrayList<>(List.of("Nome do fornecedor", "Data de competência", "Data de vencimento", "Descrição", "Valor original da parcela (R$)", "Valor pago da parcela (R$)", colunaAberto, "Categoria 1", "Valor na categoria 1"));
            var valores = new ArrayList<>(List.of("Fornecedor", "18/10/2026", data, "Compra teste", "100,00", "30,00", aberto, "Combustíveis", "-100,00"));
            if (ajustes) {
                nomes.addAll(List.of("Valor total da parcela em aberto (R$)", "Valor total pago da parcela (R$)", "Juros previsto (R$)", "Multa previsto (R$)", "Desconto previsto (R$)", "Juros realizado (R$)", "Multa realizado (R$)", "Desconto realizado (R$)", "Data do último pagamento"));
                valores.addAll(List.of("72,00", "31,00", "2,00", "0,00", "0,00", "1,00", "0,00", "0,00", "17/10/2026"));
            }
            if (conflitante) {
                nomes.add("Valor aberto da parcela (R$)");
                valores.add("99,00");
            }
            for (int i = 0; i < nomes.size(); i++) {
                h.createCell(i).setCellValue(nomes.get(i));
                r.createCell(i).setCellValue(valores.get(i));
            }
            w.write(out);
            return new MockMultipartFile("arquivo", "contas.xlsx", "application/octet-stream", out.toByteArray());
        }
    }

    @Test
    void reconheceCabecalhoOficialSemPerderSaldo() throws Exception {
        var t = new ExcelImportService().processar(arquivo("Valor da parcela em aberto (R$)", "70,00", "25/10/2026", false, false)).get(0);
        assertEquals(new BigDecimal("70.00"), t.getValorAberto());
        assertEquals(new BigDecimal("30.00"), t.getValorRealizado());
        assertEquals(LocalDate.of(2026, 10, 18), t.getDataCompetencia());
        assertEquals(LocalDate.of(2026, 10, 25), t.getDataVencimento());
    }

    @Test
    void preservaAliasesAntigos() throws Exception {
        for (String nome : List.of("Valor em aberto da parcela (R$)", "Valor aberto da parcela (R$)")) {
            assertEquals(new BigDecimal("70.00"), new ExcelImportService().processar(arquivo(nome, "70,00", "25/10/2026", false, false)).get(0).getValorAberto());
        }
    }

    @Test
    void preservaZeroInformadoEAusenciaSemInventarSaldo() throws Exception {
        var service = new ExcelImportService();
        assertEquals(0, service.processar(arquivo("Valor da parcela em aberto (R$)", "0,00", "25/10/2026", false, false)).get(0).getValorAberto().signum());
        assertNull(service.processar(arquivo("Valor da parcela em aberto (R$)", "-", "25/10/2026", false, false)).get(0).getValorAberto());
    }

    @Test
    void preservaTotaisAjustesEDataDeRealizacao() throws Exception {
        var t = new ExcelImportService().processar(arquivo("Valor da parcela em aberto (R$)", "70,00", "25/10/2026", true, false)).get(0);
        assertEquals(new BigDecimal("72.00"), t.getValorTotalAberto());
        assertEquals(new BigDecimal("31.00"), t.getValorTotalRealizado());
        assertEquals(new BigDecimal("2.00"), t.getJurosPrevisto());
        assertEquals(new BigDecimal("1.00"), t.getJurosRealizado());
        assertEquals(LocalDate.of(2026, 10, 17), t.getDataRealizacao());
    }

    @Test
    void rejeitaAliasesComValoresDivergentes() throws Exception {
        var erro = assertThrows(IllegalArgumentException.class, () -> new ExcelImportService().processar(arquivo("Valor da parcela em aberto (R$)", "70,00", "25/10/2026", false, true)));
        assertTrue(erro.getMessage().contains("divergentes"));
    }

    @Test
    void rejeitaDiaInexistenteSemCorrigirSilenciosamente() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new ExcelImportService().processar(arquivo("Valor da parcela em aberto (R$)", "70,00", "31/02/2026", false, false)));
    }

    @Test
    void datasAntigasEFuturasValidasPermanecemIguais() throws Exception {
        for (String data : List.of("17/04/2016", "16/03/2029")) {
            var t = new ExcelImportService().processar(arquivo("Valor da parcela em aberto (R$)", "70,00", data, false, false)).get(0);
            assertEquals(LocalDate.parse(data, java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")), t.getDataVencimento());
        }
    }

    @Test
    void excelNumericoPreservaDataDoCalendario() throws Exception {
        var f = arquivo("Valor da parcela em aberto (R$)", "70,00", "25/10/2026", false, false);
        try (var w = new XSSFWorkbook(f.getInputStream()); var out = new ByteArrayOutputStream()) {
            var c = w.getSheetAt(0).getRow(1).getCell(2);
            c.setCellValue(java.time.LocalDateTime.of(2026, 10, 25, 0, 0));
            var style = w.createCellStyle();
            style.setDataFormat(w.createDataFormat().getFormat("dd/mm/yyyy"));
            c.setCellStyle(style);
            w.write(out);
            var t = new ExcelImportService().processar(new MockMultipartFile("arquivo", "datas.xlsx", "application/octet-stream", out.toByteArray())).get(0);
            assertEquals(LocalDate.of(2026, 10, 25), t.getDataVencimento());
        }
    }

    @Test
    void confereExportRealAnteriorQuandoDisponivel() throws Exception {
        String nome = System.getProperty("finvista.excel.teste");
        Assumptions.assumeTrue(nome != null && Files.isRegularFile(Path.of(nome == null ? "" : nome)));
        var dados = new ExcelImportService().processar(new MockMultipartFile("arquivo", "visao_contas_a_pagar.xls", "application/octet-stream", Files.readAllBytes(Path.of(nome))));
        assertEquals(2, dados.size());
        assertEquals(0, dados.get(0).getValorAberto().compareTo(new BigDecimal("10")));
        assertEquals(0, dados.get(1).getValorAberto().compareTo(new BigDecimal("20")));
    }
}
