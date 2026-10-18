package com.finvista.integration.contaazul;

import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import com.finvista.service.ClienteContextService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContaAzulApiPlanService {

    public static final String ORIGEM_API = "CONTA_AZUL_API";

    public enum Situacao {
        BLOQUEADO, REGISTRO_IDENTIFICADO, CORRESPONDENCIA_POSSIVEL, SEM_CORRESPONDENCIA
    }

    public record Correspondencia(Long id, String descricao, String tipo, LocalDate competencia,
            LocalDate vencimento, BigDecimal valor, String origem) {}

    public record Plano(Long clienteId, String identidadeApi, Situacao situacao,
            Instant consultadoEm, ContaAzulParcelDetail parcela, List<String> avisos,
            List<Correspondencia> correspondencias, int totalCorrespondencias) {}

    private final FinancialTransactionRepository lancamentos;
    private final ClienteContextService contexto;

    public ContaAzulApiPlanService(FinancialTransactionRepository lancamentos, ClienteContextService contexto) {
        this.lancamentos = lancamentos;
        this.contexto = contexto;
    }

    @Transactional(readOnly = true)
    public Plano planejar(Long clienteId, ContaAzulParcelDetail parcela) {
        contexto.validarAdministrador();
        if (clienteId == null || !clienteId.equals(contexto.getClienteAtualId())) {
            throw new IllegalArgumentException("A empresa selecionada mudou. Prepare outro plano.");
        }
        var plano = analisar(clienteId, parcela);
        if (!clienteId.equals(contexto.getClienteAtualId())) {
            throw new IllegalArgumentException("A empresa selecionada mudou durante a análise.");
        }
        return plano;
    }

    Plano analisar(Long clienteId, ContaAzulParcelDetail parcela) {
        if (parcela == null || parcela.id() == null) {
            throw new IllegalArgumentException("Parcela inválida.");
        }
        var avisos = new ArrayList<String>();
        validar(parcela, avisos);
        var existentes = lancamentos.findByClienteId(clienteId);
        var identificados = existentes.stream().filter(l -> ORIGEM_API.equals(l.getOrigem())
                && parcela.id().equalsIgnoreCase(texto(l.getDocumentoReferencia()))).toList();
        List<FinancialTransaction> encontrados;
        Situacao situacao;
        if (!identificados.isEmpty()) {
            encontrados = identificados;
            situacao = Situacao.REGISTRO_IDENTIFICADO;
            if (identificados.size() > 1) {
                avisos.add("Há mais de um lançamento com a identidade externa desta parcela.");
            }
            if (identificados.stream().anyMatch(l -> parcela.evento() == null
                    || !Objects.equals(l.getTipo(), parcela.evento().tipo()))) {
                avisos.add("O tipo do registro identificado difere do tipo informado pela API.");
            }
        } else {
            encontrados = existentes.stream().filter(l -> possivel(l, parcela)).toList();
            situacao = encontrados.isEmpty() ? Situacao.SEM_CORRESPONDENCIA : Situacao.CORRESPONDENCIA_POSSIVEL;
        }
        if (!avisos.isEmpty()) {
            situacao = Situacao.BLOQUEADO;
        }
        if (identificados.isEmpty() && !encontrados.isEmpty()) {
            avisos.add("Sem vínculo externo confirmado. Descrição e data semelhantes não autorizam atualização nem criação automática.");
        }
        if (encontrados.isEmpty()) {
            avisos.add("Nenhuma correspondência encontrada por estas regras. Isso não comprova que a parcela seja nova.");
        }
        return new Plano(clienteId, clienteId + ":" + ORIGEM_API + ":" + parcela.id().toLowerCase(Locale.ROOT),
                situacao, Instant.now(), parcela, List.copyOf(avisos),
                encontrados.stream().limit(20).map(l -> new Correspondencia(l.getId(), l.getDescricao(), l.getTipo(),
                        l.getDataCompetencia(), l.getDataVencimento(), l.getValor(), l.getOrigem())).toList(), encontrados.size());
    }

    private boolean possivel(FinancialTransaction l, ContaAzulParcelDetail p) {
        if (p.evento() == null || !Objects.equals(l.getTipo(), p.evento().tipo())) {
            return false;
        }
        if (!texto(l.getDescricao()).equalsIgnoreCase(texto(p.descricao()))) {
            return false;
        }
        return (p.vencimento() != null && p.vencimento().equals(l.getDataVencimento()))
                || (p.evento().competencia() != null && p.evento().competencia().equals(l.getDataCompetencia()));
    }

    private void validar(ContaAzulParcelDetail p, List<String> avisos) {
        if (texto(p.descricao()).isEmpty() || texto(p.descricao()).length() > 255) {
            avisos.add("Descrição ausente ou maior que 255 caracteres.");
        }
        if (p.vencimento() == null) {
            avisos.add("Vencimento não informado.");
        }
        var evento = p.evento();
        if (evento == null) {
            avisos.add("Evento financeiro não informado.");
            return;
        }
        if (texto(evento.id()).isEmpty() || evento.competencia() == null) {
            avisos.add("Identificador ou competência do evento não informado.");
        }
        if (!Set.of("RECEITA", "DESPESA").contains(texto(evento.tipo()))) {
            avisos.add("Tipo financeiro não reconhecido.");
        }
        if (!Set.of("PENDENTE", "EM_ABERTO", "QUITADO", "RECEBIDO", "RECEBIDO_PARCIAL", "ATRASADO").contains(texto(p.status()))) {
            avisos.add("Situação da parcela precisa de conferência específica.");
        }
        if (evento.condicao() == null || !Integer.valueOf(1).equals(evento.condicao().quantidade())) {
            avisos.add("Rateios do evento não estão confirmados para uma única parcela. Não será presumida uma divisão.");
        }
        if (!dinheiro(p.liquidoConferido()) || !dinheiro(p.pago()) || !dinheiro(p.pendente())) {
            avisos.add("Valores da parcela ausentes, negativos ou fora da precisão aceita.");
            if (p.liquido() == null && p.liquidoConferido() == null) {
                avisos.add("Líquido principal ausente. A conferência pela composição exige uma única parcela, valores consistentes, ajustes explicitamente zerados e situação sem baixa parcial, perda ou renegociação.");
            }
        } else if (p.pago().add(p.pendente()).compareTo(p.liquidoConferido()) != 0) {
            avisos.add("Pago mais pendente difere do líquido da parcela.");
        }
        if (p.composicao() == null || !dinheiro(p.composicao().bruto()) || !dinheiro(p.composicao().liquido())) {
            avisos.add("Composição de valores incompleta.");
        } else if (p.liquidoConferido() != null && p.composicao().liquido().compareTo(p.liquidoConferido()) != 0) {
            avisos.add("Líquido da composição difere do líquido da parcela.");
        }
        if (evento.rateio() == null || evento.rateio().isEmpty()) {
            avisos.add("Rateios de categoria não informados.");
            return;
        }
        var ids = new HashSet<String>();
        BigDecimal liquido = BigDecimal.ZERO, bruto = BigDecimal.ZERO;
        boolean somavel = true;
        for (var r : evento.rateio()) {
            if (r == null) {
                avisos.add("Rateio de categoria inválido.");
                somavel = false;
                continue;
            }
            if (!nome(r.categoriaNome()) || texto(r.categoriaId()).isEmpty() || !ids.add(texto(r.categoriaId()).toLowerCase(Locale.ROOT))) {
                avisos.add("Categoria sem identificação/nome válido ou repetida.");
            }
            if (!dinheiro(r.valor()) || !dinheiro(r.bruto())) {
                avisos.add("Valores de categoria incompletos.");
                somavel = false;
            } else {
                liquido = liquido.add(r.valor());
                bruto = bruto.add(r.bruto());
            }
            validarCentros(r, avisos);
        }
        if (somavel && p.liquidoConferido() != null && liquido.compareTo(p.liquidoConferido()) != 0) {
            avisos.add("Soma líquida das categorias difere do líquido da parcela.");
        }
        if (somavel && p.composicao() != null && p.composicao().bruto() != null && bruto.compareTo(p.composicao().bruto()) != 0) {
            avisos.add("Soma bruta das categorias difere do bruto da composição.");
        }
    }

    private void validarCentros(ContaAzulParcelDetail.Rateio r, List<String> avisos) {
        if (r.centros() == null) {
            avisos.add("Distribuição por centros de custo não informada.");
            return;
        }
        if (r.centros().isEmpty()) {
            return; // Categoria explicitamente sem centro.

        }
        var ids = new HashSet<String>();
        BigDecimal liquido = BigDecimal.ZERO, bruto = BigDecimal.ZERO;
        boolean somavel = true;
        for (var c : r.centros()) {
            if (c == null) {
                avisos.add("Centro de custo inválido.");
                somavel = false;
                continue;
            }
            if (!nome(c.centroNome()) || texto(c.centroId()).isEmpty() || !ids.add(texto(c.centroId()).toLowerCase(Locale.ROOT))) {
                avisos.add("Centro sem identificação/nome válido ou repetido na categoria.");
            }
            if (!dinheiro(c.valor()) || !dinheiro(c.bruto())) {
                avisos.add("Valores de centro de custo incompletos; nenhuma proporção será presumida.");
                somavel = false;
            } else {
                liquido = liquido.add(c.valor());
                bruto = bruto.add(c.bruto());
            }
        }
        if (somavel && r.valor() != null && liquido.compareTo(r.valor()) != 0) {
            avisos.add("Soma líquida dos centros difere da categoria.");
        }
        if (somavel && r.bruto() != null && bruto.compareTo(r.bruto()) != 0) {
            avisos.add("Soma bruta dos centros difere da categoria.");
        }
    }

    private boolean dinheiro(BigDecimal valor) {
        return valor != null && valor.signum() >= 0 && valor.stripTrailingZeros().scale() <= 2 && valor.compareTo(new BigDecimal("9999999999999.99")) <= 0;
    }

    private boolean nome(String valor) {
        return !texto(valor).isEmpty() && texto(valor).length() <= 255;
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
