package com.finvista.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

@Service
public class FinancialDataNormalizationService {

    private static final int TAMANHO_MAXIMO_CATEGORIA = 255;
    private static final int TAMANHO_MAXIMO_CENTRO_CUSTO = 255;

    private static final Map<String, String> CATEGORIAS_CANONICAS =
            Map.ofEntries(
                    Map.entry("VENDAS", "Vendas"),
                    Map.entry("COMBUSTIVEL", "Combustível"),
                    Map.entry("MANUTENCAO", "Manutenção"),
                    Map.entry("ENERGIA", "Energia"),
                    Map.entry("LOGISTICA", "Logística"),
                    Map.entry("INSUMOS", "Insumos"),
                    Map.entry("COMPRAS", "Compras"),
                    Map.entry("OPERACIONAL", "Operacional")
            );

    private static final Map<String, String> CENTROS_CUSTO_CANONICOS =
            Map.ofEntries(
                    Map.entry("COMERCIAL", "Comercial"),
                    Map.entry("PRODUCAO", "Produção"),
                    Map.entry("LOGISTICA", "Logística"),
                    Map.entry("ADMINISTRATIVO", "Administrativo")
            );

    public String normalizarCategoria(String valor) {

        String valorNormalizado =
                normalizar(
                        valor,
                        CATEGORIAS_CANONICAS
                );

        validarTamanho(
                valorNormalizado,
                TAMANHO_MAXIMO_CATEGORIA,
                "Categoria"
        );

        return valorNormalizado;
    }

    public String normalizarCentroCusto(String valor) {

        String valorNormalizado =
                normalizar(
                        valor,
                        CENTROS_CUSTO_CANONICOS
                );

        validarTamanho(
                valorNormalizado,
                TAMANHO_MAXIMO_CENTRO_CUSTO,
                "Centro de custo"
        );

        return valorNormalizado;
    }

    private String normalizar(
            String valor,
            Map<String, String> valoresCanonicos
    ) {

        if (valor == null || valor.isBlank()) {
            return null;
        }

        String valorLimpo =
                valor
                        .trim()
                        .replaceAll("\\s+", " ");

        String chave =
                removerAcentos(valorLimpo)
                        .toUpperCase(Locale.ROOT);

        return valoresCanonicos.getOrDefault(
                chave,
                valorLimpo
        );
    }

    private void validarTamanho(
            String valor,
            int tamanhoMaximo,
            String campo
    ) {

        if (valor == null) {
            return;
        }

        if (valor.length() > tamanhoMaximo) {
            throw new IllegalArgumentException(
                    campo
                            + " excede o limite de "
                            + tamanhoMaximo
                            + " caracteres."
            );
        }
    }

    private String removerAcentos(String valor) {

        return Normalizer
                .normalize(
                        valor,
                        Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}", "");
    }
}