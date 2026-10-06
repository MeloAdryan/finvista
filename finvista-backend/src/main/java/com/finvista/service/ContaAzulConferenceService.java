package com.finvista.service;

import com.finvista.dto.importacao.ContaAzulConferenceResponse;
import com.finvista.dto.importacao.ContaAzulConferenceResponse.Linha;
import com.finvista.dto.importacao.ContaAzulConferenceResponse.Rateio;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ContaAzulConferenceService {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
    private static final DateTimeFormatter DATA = DateTimeFormatter
            .ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern CATEGORIA = Pattern.compile("categoria \\d+");

    public ContaAzulConferenceResponse conferir(MultipartFile arquivo, LocalDate corte)
            throws IOException {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Selecione um arquivo Excel preenchido.");
        }
        if (corte == null) {
            throw new IllegalArgumentException("Informe a data de corte.");
        }
        // Estado da leitura é local: requisições concorrentes não compartilham formatter.
        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("pt-BR"));
        try (Workbook workbook = WorkbookFactory.create(arquivo.getInputStream())) {
            if (workbook.getNumberOfSheets() != 1) {
                throw new IllegalArgumentException("A conferência exige uma única planilha; nenhum dado foi gravado.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                throw new IllegalArgumentException("Cabeçalho ausente.");
            }
            List<String> nomes = new ArrayList<>();
            Map<String, Integer> colunas = new HashMap<>();
            List<Integer> blocos = new ArrayList<>();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                String nome = texto(header.getCell(i), formatter);
                nome = nome == null ? "" : nome.toLowerCase(Locale.ROOT);
                nomes.add(nome);
                colunas.putIfAbsent(nome, i);
                if (CATEGORIA.matcher(nome).matches()) {
                    blocos.add(i);
                }
            }
            for (String obrigatoria : List.of("data de competência", "data de vencimento",
                    "descrição", "valor original da parcela (r$)")) {
                if (!colunas.containsKey(obrigatoria)) {
                    throw new IllegalArgumentException("Coluna obrigatória ausente: " + obrigatoria);
                }
            }
            boolean receber = colunas.containsKey("identificador do cliente") || colunas.containsKey("nome do cliente");
            boolean pagar = colunas.containsKey("identificador do fornecedor") || colunas.containsKey("nome do fornecedor");
            if (receber == pagar) {
                throw new IllegalArgumentException("Layout Conta Azul ausente ou ambíguo.");
            }
            if (blocos.isEmpty()) {
                throw new IllegalArgumentException("Nenhum bloco de categoria encontrado.");
            }
            for (int inicio : blocos) {
                if (inicio + 3 >= nomes.size()
                        || !nomes.get(inicio + 1).startsWith("valor na categoria ")
                        || !nomes.get(inicio + 2).startsWith("centro de custo ")
                        || !nomes.get(inicio + 3).startsWith("valor no centro de custo ")) {
                    throw new IllegalArgumentException("Estrutura de rateio não reconhecida na coluna " + (inicio + 1));
                }
            }
            List<Linha> linhas = new ArrayList<>();
            BigDecimal original = BigDecimal.ZERO, realizado = BigDecimal.ZERO, aberto = BigDecimal.ZERO;
            int rejeitadas = 0, avisadas = 0;
            for (int r = header.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (vazia(row, formatter)) {
                    continue;
                }
                List<String> avisos = new ArrayList<>(), erros = new ArrayList<>();
                LocalDate competencia = data(row, colunas.get("data de competência"), formatter, erros);
                LocalDate vencimento = data(row, colunas.get("data de vencimento"), formatter, erros);
                LocalDate prevista = data(row, colunas.get("data prevista"), formatter, erros);
                LocalDate ultimo = data(row, colunas.get("data do último pagamento"), formatter, erros);
                String descricao = campo(row, colunas, "descrição", formatter);
                if (competencia == null) {
                    erros.add("Competência não informada.");
                }
                if (vencimento == null) {
                    erros.add("Vencimento não informado.");
                }
                if (descricao == null) {
                    erros.add("Descrição não informada.");
                }
                BigDecimal vo = numero(row, colunas, formatter, erros, "valor original da parcela (r$)");
                BigDecimal vr = numero(row, colunas, formatter, erros, receber ? "valor recebido da parcela (r$)" : "valor pago da parcela (r$)");
                BigDecimal va  = numero(row, colunas, formatter, erros, "valor da parcela em aberto (r$)");
                BigDecimal jr = numero(row, colunas, formatter, erros, "juros realizado (r$)");
                BigDecimal mr = numero(row, colunas, formatter, erros, "multa realizado (r$)");
                BigDecimal dr = numero(row, colunas, formatter, erros, "desconto realizado (r$)");
                BigDecimal tr = numero(row, colunas, formatter, erros, receber ? "valor total recebido da parcela (r$)" : "valor total pago da parcela (r$)");
                BigDecimal jp = numero(row, colunas, formatter, erros, "juros previsto (r$)");
                BigDecimal mp = numero(row, colunas, formatter, erros, "multa previsto (r$)");
                BigDecimal dp = numero(row, colunas, formatter, erros, "desconto previsto (r$)");
                BigDecimal ta = numero(row, colunas, formatter, erros, "valor total da parcela em aberto (r$)");
                if (vo != null && vr != null && va  != null) {
                    comparar(vo, vr.add(va), erros, "Original difere de realizado + aberto.");
                }
                if (vr != null && jr != null && mr != null && dr != null && tr != null) {
                    comparar(tr, vr.add(jr).add(mr).subtract(dr), erros, "Total realizado diverge dos ajustes.");
                }
                if (va  != null && jp != null && mp != null && dp != null && ta != null) {
                    comparar(ta, va.add(jp).add(mp).subtract(dp), erros, "Total aberto diverge dos ajustes.");
                }
                List<Rateio> rateios = new ArrayList<>();
                BigDecimal somaCategoria = BigDecimal.ZERO, somaCentro = BigDecimal.ZERO;
                for (int i = 0; i < blocos.size(); i++) {
                    int c = blocos.get(i);
                    if (texto(row.getCell(c), formatter) == null && texto(row.getCell(c + 1), formatter) == null
                            && texto(row.getCell(c + 2), formatter) == null && texto(row.getCell(c + 3), formatter) == null) {
                        continue;
                    }
                    String categoria = texto(row.getCell(c), formatter), centro = texto(row.getCell(c + 2), formatter);
                    BigDecimal vc = decimal(row.getCell(c + 1), formatter, erros, "Valor da categoria, bloco " + (i + 1));
                    BigDecimal vcc = decimal(row.getCell(c + 3), formatter, erros, "Valor do centro, bloco " + (i + 1));
                    if (categoria == null) {
                        avisos.add("Sem categoria no bloco " + (i + 1));
                    }
                    if (vc == null) {
                        erros.add("Valor da categoria ausente no bloco " + (i + 1));
                    }
                    if (centro == null) {
                        avisos.add("Sem centro no bloco " + (i + 1));
                    }
                    if (centro != null && vcc == null) {
                        avisos.add("Centro informado sem valor no bloco " + (i + 1));
                    }
                    rateios.add(new Rateio(i + 1, categoria, vc, centro, vcc));
                    // Somar sinais antes de comparar: não apagar um estorno com abs por bloco.
                    if (vc != null) {
                        somaCategoria = somaCategoria.add(vc);
                    }
                    if (vcc != null) {
                        somaCentro = somaCentro.add(vcc);
                    }
                }
                if (vo != null) {
                    comparar(vo.abs(), somaCategoria.abs(), erros, "Rateios de categoria não fecham com o original.");
                    comparar(vo.abs(), somaCentro.abs(), avisos, "Rateios de centro não fecham com o original.");
                }
                if (competencia != null && competencia.isAfter(corte)) {
                    avisos.add("Competência posterior à data de corte.");
                }
                if (competencia != null && competencia.isBefore(corte.minusYears(5))) {
                    avisos.add("Competência anterior a cinco anos: conferir origem.");
                }
                if (va  != null && va.signum() > 0 && vencimento != null && vencimento.isBefore(corte)) {
                    avisos.add("Saldo aberto com vencimento anterior à data de corte.");
                }
                if (!erros.isEmpty()) {
                    rejeitadas++; 
                }else {
                    original = original.add(vo);
                    realizado = realizado.add(tr);
                    aberto = aberto.add(ta);
                }
                if (!avisos.isEmpty()) {
                    avisadas++;
                }
                linhas.add(new Linha(r + 1, competencia, vencimento, prevista, ultimo, descricao,
                        campo(row, colunas, "situação", formatter), vo, vr, va, jr, mr, dr, tr,
                        jp, mp, dp, ta, List.copyOf(rateios), List.copyOf(avisos), List.copyOf(erros)));
            }
            return new ContaAzulConferenceResponse(arquivo.getOriginalFilename(), receber ? "RECEITA" : "DESPESA",
                    corte, linhas.size(), linhas.size() - rejeitadas, rejeitadas, avisadas,
                    original, realizado, aberto, List.copyOf(linhas));
        } catch (org.apache.poi.EncryptedDocumentException e) {
            throw new IllegalArgumentException("Arquivo protegido por senha.");
        }
    }

    private String texto(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.FORMULA) {
            throw new IllegalArgumentException("Fórmula na célula " + cell.getAddress() + ": exporte valores para conferir.");
        }
        if (cell.getCellType() == CellType.ERROR) {
            throw new IllegalArgumentException("Erro Excel na célula " + cell.getAddress());
        }
        String t = formatter.formatCellValue(cell).replace('\u00a0', ' ').trim();
        return t.isEmpty() || t.equals("-") ? null : t;
    }

    private String campo(Row row, Map<String, Integer> columns, String name, DataFormatter f) {
        Integer i = columns.get(name);
        return i == null ? null : texto(row.getCell(i), f);
    }

    private boolean vazia(Row row, DataFormatter f) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            if (texto(cell, f) != null) {
                return false;
            }
        }
        return true;
    }

    private BigDecimal numero(Row row, Map<String, Integer> cols, DataFormatter f, List<String> erros, String nome) {
        Integer index = cols.get(nome);
        BigDecimal value = index == null ? null : decimal(row.getCell(index), f, erros, nome);
        if (value == null) {
            erros.add("Campo financeiro ausente: " + nome);
        }
        return value;
    }

    private BigDecimal decimal(Cell cell, DataFormatter f, List<String> erros, String nome) {
        String t = texto(cell, f);
        if (t == null) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            t = t.replace("R$", "").replace(" ", "");
            if (t.contains(",")) {
                t = t.replace(".", "").replace(',', '.');
            }
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            erros.add("Número inválido em " + nome);
            return null;
        }
    }

    private LocalDate data(Row row, Integer index, DataFormatter f, List<String> erros) {
        if (index == null) {
            return null;
        }
        Cell cell = row.getCell(index);
        String t = texto(cell, f);
        if (t == null) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            return LocalDate.parse(t, DATA);
        } catch (RuntimeException e) {
            erros.add("Data inválida na coluna " + (index + 1));
            return null;
        }
    }

    private void comparar(BigDecimal a, BigDecimal b, List<String> mensagens, String mensagem) {
        if (a.subtract(b).abs().compareTo(TOLERANCIA) > 0) {
            mensagens.add(mensagem);
        }
    }
}
