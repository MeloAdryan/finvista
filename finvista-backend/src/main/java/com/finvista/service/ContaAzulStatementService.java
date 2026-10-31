package com.finvista.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ContaAzulStatementService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    public static final String IMPORT_GUIDANCE = "Extrato financeiro Conta Azul identificado. "
            + "Use a conferência de extrato em Importar arquivo para consultar as movimentações. "
            + "Para criar ou atualizar parcelas, exporte Financeiro > Contas a pagar ou Contas a receber. "
            + "Uma movimentação do extrato não informa necessariamente o valor integral da parcela nem seu saldo aberto.";

    public record Allocation(String categoria, String centro, BigDecimal valor, BigDecimal valorCentro) {}

    public record Line(int numero, LocalDate movimento, LocalDate competencia, LocalDate vencimento,
            String descricao, String tipo, String origem, String conta, String situacao,
            BigDecimal valor, List<String> categorias, List<String> centros, List<String> erros,
            List<Allocation> rateios, Map<String, String> campos) {}

    public record Result(boolean reconhecido, String arquivo, List<Line> linhas, LocalDate hoje) {

        public Result(boolean reconhecido, String arquivo, List<Line> linhas) {
            this(reconhecido, arquivo, linhas, LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")));
        }
    }

    public static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static boolean isStatement(Collection<String> headers) {
        Set<String> names = new HashSet<>();
        headers.forEach(h -> names.add(normalize(h)));
        return names.containsAll(List.of("data movimento", "valor (r$)", "origem do lancamento", "tipo"));
    }

    public Result inspect(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Selecione um arquivo Excel preenchido.");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).matches(".*\\.xlsx?$")) {
            throw new IllegalArgumentException("Selecione um arquivo XLS ou XLSX.");
        }
        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("pt-BR"));
        try (Workbook book = WorkbookFactory.create(file.getInputStream())) {
            if (book.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("Planilha ausente.");
            }
            Sheet sheet = book.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                throw new IllegalArgumentException("Cabeçalho ausente.");
            }
            Map<String, Integer> columns = new LinkedHashMap<>();
            List<String> duplicates = new ArrayList<>();
            for (Cell cell : header) {
                String key = normalize(formatter.formatCellValue(cell));
                // Conta Azul restarts center numbering at 1 within each category group.
                // Recognize only the exported four-column group; other duplicates remain errors.
                int col = cell.getColumnIndex();
                if (key.equals("centro de custo 1") && col >= 2) {
                    String category = normalize(formatter.formatCellValue(header.getCell(col - 2)));
                    String amount = normalize(formatter.formatCellValue(header.getCell(col - 1)));
                    if (category.matches("categoria [1-9]\\d*")
                            && amount.equals("valor na " + category)) {
                        key = "centro de custo " + category.substring("categoria ".length());
                    }
                } else if (key.equals("valor no centro de custo 1") && col >= 3) {
                    String category = normalize(formatter.formatCellValue(header.getCell(col - 3)));
                    String amount = normalize(formatter.formatCellValue(header.getCell(col - 2)));
                    String center = normalize(formatter.formatCellValue(header.getCell(col - 1)));
                    if (category.matches("categoria [1-9]\\d*")
                            && amount.equals("valor na " + category) && center.equals("centro de custo 1")) {
                        key = "valor no centro de custo " + category.substring("categoria ".length());
                    }
                }
                if (!key.isBlank() && columns.putIfAbsent(key, cell.getColumnIndex()) != null) {
                    duplicates.add(key);
                }
            }
            if (!isStatement(columns.keySet())) {
                return new Result(false, name, List.of());
            }
            if (!duplicates.isEmpty()) {
                throw new IllegalArgumentException("Cabeçalho repetido: " + duplicates.get(0));
            }
            if (book.getNumberOfSheets() != 1) {
                throw new IllegalArgumentException("A conferência de extrato exige uma única planilha.");
            }
            if (!columns.containsKey("descricao")) {
                throw new IllegalArgumentException("Coluna ausente: Descrição.");
            }
            List<Line> lines = new ArrayList<>();
            for (int r = header.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                boolean empty = true;
                for (Cell cell : row) {
                    if (!formatter.formatCellValue(cell).isBlank()) {
                        empty = false;
                        break;
                    }
                }
                if (empty) {
                    continue;
                }
                List<String> errors = new ArrayList<>();
                LocalDate movement = date(row, columns.get("data movimento"), formatter, true, errors);
                LocalDate competence = date(row, columns.get("data de competencia"), formatter, false, errors);
                LocalDate due = date(row, columns.get("data original de vencimento"), formatter, false, errors);
                BigDecimal value = money(row, columns.get("valor (r$)"), formatter, errors);
                String description = text(row, columns.get("descricao"), formatter);
                if (description.isBlank()) {
                    errors.add("Descrição ausente.");
                }
                List<String> categories = new ArrayList<>(), centers = new ArrayList<>();
                columns.forEach((key, index) -> {
                    String text = text(row, index, formatter);
                    if (!text.isBlank() && key.matches("categoria \\d+")) {
                        categories.add(text);
                    }
                    if (!text.isBlank() && key.matches("centro de custo \\d+")) {
                        centers.add(text);
                    }
                });
                List<Allocation> allocations = new ArrayList<>();
                Map<String, String> fields = new LinkedHashMap<>();
                for (var entry : columns.entrySet()) {
                    String key = entry.getKey();
                    fields.put(key, text(row, entry.getValue(), formatter));
                    if (key.matches("categoria \\d+") && !fields.get(key).isBlank()) {
                        String n = key.substring("categoria ".length());
                        Integer amount = columns.get("valor na categoria " + n);
                        Integer centerAmount = columns.get("valor no centro de custo " + n);
                        allocations.add(new Allocation(fields.get(key), text(row, columns.get("centro de custo " + n), formatter),
                                text(row, amount, formatter).isBlank() ? null : money(row, amount, formatter, errors),
                                text(row, centerAmount, formatter).isBlank() ? null : money(row, centerAmount, formatter, errors)));
                    }
                }
                lines.add(new Line(r + 1, movement, competence, due, description,
                        text(row, columns.get("tipo"), formatter), text(row, columns.get("origem do lancamento"), formatter),
                        text(row, columns.get("conta bancaria"), formatter), text(row, columns.get("situacao"), formatter),
                        value, List.copyOf(categories), List.copyOf(centers), List.copyOf(errors),
                        List.copyOf(allocations), Collections.unmodifiableMap(fields)));
            }
            return new Result(true, name, List.copyOf(lines));
        }
    }

    private static String text(Row row, Integer index, DataFormatter formatter) {
        return index == null ? "" : formatter.formatCellValue(row.getCell(index)).trim();
    }

    private static LocalDate date(Row row, Integer index, DataFormatter formatter, boolean required, List<String> errors) {
        String value = text(row, index, formatter);
        if (value.isBlank()) {
            if (required) {
                errors.add("Data de movimento ausente.");

            }
            return null;
        }
        Cell cell = row.getCell(index);
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            return LocalDate.parse(value, DATE);
        } catch (RuntimeException e) {
            errors.add("Data inválida: " + value);
            return null;
        }
    }

    private static BigDecimal money(Row row, Integer index, DataFormatter formatter, List<String> errors) {
        Cell cell = index == null ? null : row.getCell(index);
        try {
            if (cell == null || cell.getCellType() == CellType.FORMULA) {
                throw new IllegalArgumentException();
            }
            BigDecimal value;
            if (cell.getCellType() == CellType.NUMERIC) {
                value = BigDecimal.valueOf(cell.getNumericCellValue());
            } else {
                String raw = formatter.formatCellValue(cell).replace("R$", "").replace("\u00a0", "").replace(" ", "").trim();
                if (raw.contains(",")) {
                    raw = raw.replace(".", "").replace(',', '.');
                }
                value = new BigDecimal(raw);
            }
            value = value.setScale(2, RoundingMode.UNNECESSARY);
            if (value.precision() > 15) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (RuntimeException e) {
            errors.add("Valor de movimento ausente, inválido ou com fração de centavo.");
            return null;
        }
    }
}
