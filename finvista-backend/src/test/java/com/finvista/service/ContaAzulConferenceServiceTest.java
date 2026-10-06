package com.finvista.service;

import com.finvista.service.ContaAzulConferenceService;
import com.finvista.dto.importacao.ContaAzulConferenceResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.nio.file.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;

class ContaAzulConferenceServiceTest {

    static final ContaAzulConferenceService SERVICE = new ContaAzulConferenceService();
    static final LocalDate CUT = LocalDate.of(2026, 10, 4);

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void money(BigDecimal actual, String expected) {
        check(actual.compareTo(new BigDecimal(expected)) == 0, "Valor inesperado: " + actual + " esperado " + expected);
    }

    static ContaAzulConferenceResponse read(String path) throws Exception {
        return SERVICE.conferir(new MockMultipartFile("arquivo", "fonte.xls", "application/octet-stream", Files.readAllBytes(Path.of(path))), CUT);
    }

    @org.junit.jupiter.api.Test
    void confereArquivosReaisQuandoInformados() throws Exception {
        String folder = System.getenv("FINVISTA_FONTES_TESTE");
        org.junit.jupiter.api.Assumptions.assumeTrue(folder != null && !folder.isBlank());
        var r = read(Path.of(folder, "visao_contas_a_receber (49).xls").toString());
        check(r.aceitas() == 534 && r.rejeitadas() == 0, "Receber: quantidade");
        money(r.valorOriginal(), "373370.24");
        money(r.valorTotalRealizado(), "368736.54");
        money(r.valorTotalAberto(), "0");
        var split = r.linhas().stream().filter(l -> l.numero() == 283).findFirst().orElseThrow();
        check(split.rateios().size() == 2, "Receber: rateio adicional");
        money(split.rateios().get(1).valorCategoria(), "350");
        var p = read(Path.of(folder, "visao_contas_a_pagar (39).xls").toString());
        check(p.aceitas() == 944 && p.rejeitadas() == 0, "Pagar: quantidade");
        money(p.valorOriginal(), "1207824.67");
        money(p.valorTotalRealizado(), "561680");
        money(p.valorTotalAberto(), "646030.17");
        split = p.linhas().stream().filter(l -> l.numero() == 95).findFirst().orElseThrow();
        check(split.rateios().size() == 2, "Pagar: rateio adicional");
        money(split.rateios().get(1).valorCategoria(), "-5416.14");
        check(split.rateios().get(1).centro().equals("Despesas Administrativas"), "Segundo centro repetido");
        check(p.linhas().stream().filter(l -> l.avisos().contains("Rateios de centro não fecham com o original.")).count() == 16, "Divergências centro pagar");
        check(r.linhas().stream().filter(l -> l.avisos().contains("Rateios de centro não fecham com o original.")).count() == 19, "Divergências centro receber");
        check(p.linhas().stream().filter(l -> l.avisos().contains("Saldo aberto com vencimento anterior à data de corte.")).count() == 25, "Vencidos");
        System.out.println("PASS: dois arquivos reais; totais, blocos repetidos, divergências e vencimentos conferidos.");
    }

    static byte[] fixture(String competence, String total, String open, boolean formula) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Contas");
            Row h = s.createRow(0), r = s.createRow(1);
            String[] headers = {"Nome do fornecedor", "Data de competência", "Data de vencimento", "Descrição", "Valor original da parcela (R$)", "Valor pago da parcela (R$)", "Valor da parcela em aberto (R$)", "Juros realizado (R$)", "Multa realizado (R$)", "Desconto realizado (R$)", "Valor total pago da parcela (R$)", "Juros previsto (R$)", "Multa previsto (R$)", "Desconto previsto (R$)", "Valor total da parcela em aberto (R$)", "Categoria 1", "Valor na Categoria 1", "Centro de Custo 1", "Valor no Centro de Custo 1", "Categoria 2", "Valor na Categoria 2", "Centro de Custo 1", "Valor no Centro de Custo 1"};
            String[] values = {"Fornecedor", competence, "30/09/2026", "Parcela", "100,00", "100,00", open, "0", "0", "0", total, "0", "0", "0", "0", "A", "-60,00", "Centro A", "-60,00", "B", "-40,00", "Centro B", "-40,00"};
            for (int c = 0; c < headers.length; c++) {
                h.createCell(c).setCellValue(headers[c]);
                if (values[c] != null) {
                    r.createCell(c).setCellValue(values[c]);
            
                }}
            if (formula) {
                r.getCell(10).setCellFormula("100+0");
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    static ContaAzulConferenceResponse test(String date, String total, String open) throws Exception {
        return SERVICE.conferir(new MockMultipartFile("arquivo", "amostra.xlsx", "application/octet-stream", fixture(date, total, open, false)), CUT);
    }

    @org.junit.jupiter.api.Test
    void validaAmostras() throws Exception {
        var good = test("01/09/2026", "100,00", "0");
        check(good.aceitas() == 1 && good.linhas().get(0).rateios().size() == 2, "Amostra válida");
        check(test("31/02/2026", "100,00", "0").rejeitadas() == 1, "Data impossível aceita");
        var bad = test("01/09/2026", "999,00", "0");
        check(bad.rejeitadas() == 1, "Ajustes inconsistentes aceitos");
        money(bad.valorOriginal(), "0");
        check(test("01/09/2026", "100,00", null).rejeitadas() == 1, "Ausência tratada como zero");
        check(test("01/09/2026", "abc", "0").rejeitadas() == 1, "Texto inválido aceito");
        check(!test("01/01/2029", "100,00", "0").linhas().get(0).avisos().isEmpty(), "Futuro não sinalizado");
        try {
            SERVICE.conferir(new MockMultipartFile("arquivo", "amostra.xlsx", "application/octet-stream", fixture("01/09/2026", "100", "0", true)), CUT);
            throw new AssertionError("Fórmula aceita");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("Fórmula"), "Erro de fórmula");
        }
        System.out.println("PASS: validações de data, ajustes, campos ausentes, números, futuro e fórmula.");
    }
}
