package com.finvista.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.dto.importacao.ContaAzulConferenceResponse;
import com.finvista.dto.importacao.ContaAzulConferenceResponse.Linha;
import com.finvista.dto.importacao.ContaAzulConferenceResponse.Rateio;
import com.finvista.dto.importacao.ContaAzulSyncResponse.*;
import com.finvista.model.*;
import com.finvista.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;

@Service
public class ContaAzulSyncService {

    private static final String ORIGEM = "CONTA_AZUL_EXCEL";
    private final ContaAzulConferenceService conferencia;
    private final ExcelImportService excel;
    private final FinancialTransactionRepository transacoes;
    private final FinancialAllocationRepository rateios;
    private final ContaAzulSyncBatchRepository lotes;
    private final ClienteContextService contexto;
    private final EntityManager em;
    private final ObjectMapper json;

    public ContaAzulSyncService(ContaAzulConferenceService conferencia, ExcelImportService excel,
            FinancialTransactionRepository transacoes, FinancialAllocationRepository rateios,
            ContaAzulSyncBatchRepository lotes, ClienteContextService contexto,
            EntityManager em, ObjectMapper json) {
        this.conferencia = conferencia;
        this.excel = excel;
        this.transacoes = transacoes;
        this.rateios = rateios;
        this.lotes = lotes;
        this.contexto = contexto;
        this.em = em;
        this.json = json;
    }

    private record Entrada(Linha linha, FinancialTransaction dados) {

    }

    private record Leitura(String hash, ContaAzulConferenceResponse conferencia, List<Entrada> entradas) {

    }

    @Transactional(readOnly = true)
    public Plano planejar(MultipartFile arquivo, LocalDate corte) throws IOException {
        Long clienteId = contexto.getClienteAtual().getId();
        Leitura leitura = ler(arquivo, corte);
        List<FinancialTransaction> existentes = existentes(clienteId);
        List<FinancialAllocation> alocacoes = alocacoes(clienteId);
        return plano(leitura, existentes, alocacoes, clienteId, corte);
    }

    @Transactional(rollbackFor = Exception.class)
    public Resultado aplicar(MultipartFile arquivo, LocalDate corte, String token,
            List<Decisao> decisoes) throws IOException {
        if (decisoes == null || decisoes.isEmpty()) {
            throw new IllegalArgumentException("Informe decisões explícitas para as linhas que deseja processar.");
        }
        Long clienteId = contexto.getClienteAtual().getId();
        // Serializa as sincronizações deste cliente, inclusive a criação de parcelas novas.
        Cliente cliente = em.find(Cliente.class, clienteId, LockModeType.PESSIMISTIC_WRITE);
        if (cliente == null || !Boolean.TRUE.equals(cliente.getAtivo())) {
            throw new IllegalArgumentException("Cliente indisponível.");
        }
        Leitura leitura = ler(arquivo, corte);
        List<FinancialTransaction> existentes = existentes(clienteId);
        List<FinancialAllocation> alocacoes = alocacoes(clienteId);
        Plano atual = plano(leitura, existentes, alocacoes, clienteId, corte);
        if (token == null || !atual.token().equals(token)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Arquivo, data de corte ou dados existentes mudaram. Gere e revise outro plano.");
        }
        Map<Integer, Entrada> porLinha = new HashMap<>();
        for (Entrada e : leitura.entradas()) {
            porLinha.put(e.linha().numero(), e);
        }
        Map<Integer, Item> itens = new HashMap<>();
        for (Item item : atual.itens()) {
            itens.put(item.linha(), item);
        }
        Map<Long, FinancialTransaction> porId = new HashMap<>();
        for (FinancialTransaction existente : existentes) {
            porId.put(existente.getId(), existente);
        }
        Set<Integer> linhasEscolhidas = new HashSet<>();
        Set<Long> idsEscolhidos = new HashSet<>();
        // Validar TODAS as decisões antes de alterar qualquer entidade.
        for (Decisao d : decisoes) {
            if (d == null || !porLinha.containsKey(d.linha()) || !linhasEscolhidas.add(d.linha())) {
                throw new IllegalArgumentException("Linha inexistente ou repetida nas decisões.");
            }
            if ("ATUALIZAR".equals(d.acao())) {
                if (d.lancamentoId() == null || !itens.get(d.linha()).candidatos().contains(d.lancamentoId())
                        || !idsEscolhidos.add(d.lancamentoId())) {
                    throw new IllegalArgumentException("Atualização sem candidato válido, ou ID usado em mais de uma linha.");
                }
            } else if ("CRIAR".equals(d.acao())) {
                if (d.lancamentoId() != null || !itens.get(d.linha()).candidatos().isEmpty()) {
                    throw new IllegalArgumentException("Há candidato existente. Confira antes de criar outra parcela.");
                }
            } else if ("IGNORAR".equals(d.acao())) {
                if (d.lancamentoId() != null) {
                    throw new IllegalArgumentException("IGNORAR não recebe ID.");
                }
            } else {
                throw new IllegalArgumentException("Ação inválida: use ATUALIZAR, CRIAR ou IGNORAR.");
            }
        }
        int criados = 0, atualizados = 0, mantidos = 0, pendentes = leitura.entradas().size();
        List<Object> auditoria = new ArrayList<>();
        for (Decisao d : decisoes) {
            if ("IGNORAR".equals(d.acao())) {
                continue;
            }
            Entrada entrada = porLinha.get(d.linha());
            FinancialTransaction destino = "CRIAR".equals(d.acao())
                    ? new FinancialTransaction() : porId.get(d.lancamentoId());
            List<Rateio> anteriores = "CRIAR".equals(d.acao()) ? List.of() : rateiosDo(destino, alocacoes);
            Object antes = "CRIAR".equals(d.acao()) ? null : estado(destino, anteriores);
            preencher(destino, entrada.dados());
            destino.setCliente(cliente);
            Object depois = estado(destino, entrada.linha().rateios());
            pendentes--;
            if (antes != null && serializar(antes).equals(serializar(depois))) {
                mantidos++;
                continue;
            }
            destino = transacoes.save(destino);
            if (antes == null) {
                criados++;
            } else {
                atualizados++;
                rateios.deleteByLancamentoIdAndLancamentoClienteId(destino.getId(), clienteId);
                // As exclusões devem chegar ao banco antes de recriar o mesmo bloco único.
                rateios.flush();
            }
            List<FinancialAllocation> novos = new ArrayList<>();
            for (Rateio r : entrada.linha().rateios()) {
                novos.add(new FinancialAllocation(destino,
                        r.bloco(), r.categoria(), r.valorCategoria(), r.centro(), r.valorCentro()));
            }
            rateios.saveAll(novos);
            Map<String, Object> mudanca = new LinkedHashMap<>();
            mudanca.put("linha", d.linha());
            mudanca.put("id", destino.getId());
            mudanca.put("antes", antes);
            mudanca.put("depois", depois);
            auditoria.add(mudanca);
        }
        Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("decisoes", decisoes);
        registro.put("mudancas", auditoria);
        registro.put("pendentes", pendentes);
        registro.put("mantidos", mantidos);
        ContaAzulSyncBatch lote = lotes.save(new ContaAzulSyncBatch(cliente, leitura.hash(), token,
                contexto.getUsuarioAutenticado().getEmail(), corte, serializar(registro)));
        return new Resultado(lote.getId(), criados, atualizados, mantidos, pendentes);
    }

    private List<FinancialTransaction> existentes(Long id) {
        return transacoes.findByClienteIdAndOrigemOrderByIdAsc(id, ORIGEM);
    }

    private List<FinancialAllocation> alocacoes(Long id) {
        return rateios.findByLancamentoClienteIdAndLancamentoOrigemOrderByLancamentoIdAscBlocoAsc(id, ORIGEM);
    }

    private Leitura ler(MultipartFile arquivo, LocalDate corte) throws IOException {
        ContaAzulConferenceResponse conf = conferencia.conferir(arquivo, corte);
        if (conf.rejeitadas() > 0 || conf.linhas().isEmpty()) {
            throw new IllegalArgumentException("O arquivo contém linhas inválidas ou está sem dados. Use a conferência para revisar.");
        }
        List<FinancialTransaction> dados;
        // Reutiliza somente a leitura dos metadados da contraparte e referências.
        synchronized (excel) {
            dados = excel.processar(arquivo);
        }
        if (dados.size() != conf.linhas().size()) {
            throw new IllegalArgumentException("Leitores discordam sobre a quantidade de linhas. Nenhum dado foi salvo.");
        }
        List<Entrada> entradas = new ArrayList<>();
        for (int i = 0; i < dados.size(); i++) {
            FinancialTransaction tx = dados.get(i);
            Linha l = conf.linhas().get(i);
            if (!ORIGEM.equals(tx.getOrigem())) {
                throw new IllegalArgumentException("Somente arquivos Conta Azul nesta rota.");
            }
            tx.setData(l.competencia());
            tx.setDataCompetencia(l.competencia());
            tx.setDataVencimento(l.vencimento());
            tx.setDataPrevista(l.prevista());
            tx.setDataRealizacao(l.ultimoPagamento());
            tx.setValor(l.original());
            tx.setValorOriginal(l.original());
            tx.setValorRealizado(l.realizado());
            tx.setValorAberto(l.aberto());
            tx.setJurosRealizado(l.jurosRealizado());
            tx.setMultaRealizada(l.multaRealizada());
            tx.setDescontoRealizado(l.descontoRealizado());
            tx.setValorTotalRealizado(l.totalRealizado());
            tx.setJurosPrevisto(l.jurosPrevisto());
            tx.setMultaPrevista(l.multaPrevista());
            tx.setDescontoPrevisto(l.descontoPrevisto());
            tx.setValorTotalAberto(l.totalAberto());
            tx.setSituacao(l.situacao());
            entradas.add(new Entrada(l, tx));
        }
        return new Leitura(hash(arquivo.getBytes()), conf, entradas);
    }

    private Plano plano(Leitura leitura, List<FinancialTransaction> existentes,
            List<FinancialAllocation> alocacoes, Long clienteId, LocalDate corte) {
        List<Item> itens = new ArrayList<>();
        Map<String, List<FinancialTransaction>> indice = new HashMap<>();
        for (FinancialTransaction tx : existentes) {
            indice.computeIfAbsent(serializar(ancora(tx)), k -> new ArrayList<>()).add(tx);
        }
        Map<String, Integer> repeticoes = new HashMap<>();
        for (Entrada e : leitura.entradas()) {
            repeticoes.merge(serializar(ancora(e.dados())), 1, Integer::sum);
        }
        for (Entrada e : leitura.entradas()) {
            List<Long> ids = indice.getOrDefault(serializar(ancora(e.dados())), List.of()).stream().filter(tx -> candidato(tx, e.dados()))
                    .map(FinancialTransaction::getId).sorted().toList();
            List<String> avisos = new ArrayList<>(e.linha().avisos());
            if (repeticoes.get(serializar(ancora(e.dados()))) > 1) {
                avisos.add("Mais de uma linha no arquivo tem os mesmos campos de correspondência. Confira a multiplicidade.");
            }
            avisos.add("Candidato é uma correspondência possível; não existe ID externo único neste arquivo.");
            itens.add(new Item(e.linha().numero(), e.linha().descricao(), valor(e.linha().original()),
                    ids.isEmpty() ? "CONFERIR_NOVO" : ids.size() == 1 ? "CONFERIR_EXISTENTE" : "AMBIGUO", ids, List.copyOf(avisos)));
        }
        List<Object> estados = new ArrayList<>();
        existentes.stream().sorted(Comparator.comparing(FinancialTransaction::getId)).forEach(tx
                -> estados.add(Arrays.asList(tx.getId(), estado(tx, rateiosDo(tx, alocacoes)))));
        String token = hash(serializar(Arrays.asList(clienteId, corte.toString(), leitura.hash(), estados))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new Plano(leitura.conferencia().arquivo(), corte, token, itens.size(), List.copyOf(itens));
    }

    // Uma âncora de pesquisa, não uma identidade garantida. Pagamento/status não entram aqui.
    private Object ancora(FinancialTransaction tx) {
        return Arrays.asList(tx.getDataCompetencia() == null ? tx.getData() : tx.getDataCompetencia(),
                texto(tx.getTipo()), texto(tx.getDescricao()), valor(tx.getValorOriginal() == null ? tx.getValor() : tx.getValorOriginal()));
    }

    private boolean candidato(FinancialTransaction a, FinancialTransaction b) {
        if (!serializar(ancora(a)).equals(serializar(ancora(b)))) {
            return false;
        }
        String ai = texto(a.getEntidadeExternaId()), bi = texto(b.getEntidadeExternaId());
        if (ai != null && bi != null) {
            return ai.equals(bi);
        }
        String an = texto(a.getEntidadeNome()), bn = texto(b.getEntidadeNome());
        return an == null || bn == null || an.equals(bn);
    }

    private List<Rateio> rateiosDo(FinancialTransaction tx, List<FinancialAllocation> alocacoes) {
        return alocacoes.stream().filter(a -> Objects.equals(a.getLancamento().getId(), tx.getId()))
                .sorted(Comparator.comparingInt(FinancialAllocation::getBloco))
                .map(a -> new Rateio(a.getBloco(), a.getCategoria(), a.getValorCategoria(), a.getCentro(), a.getValorCentro())).toList();
    }

    private Object estado(FinancialTransaction tx, List<Rateio> lista) {
        List<Object> valores = Arrays.asList(tx.getData(), tx.getDataCompetencia(), tx.getDataVencimento(), tx.getDataPrevista(),
                tx.getDataRealizacao(), tx.getDescricao(), tx.getTipo(), valor(tx.getValor()), valor(tx.getValorOriginal()),
                valor(tx.getValorRealizado()), valor(tx.getValorAberto()), valor(tx.getJurosRealizado()), valor(tx.getMultaRealizada()),
                valor(tx.getDescontoRealizado()), valor(tx.getJurosPrevisto()), valor(tx.getMultaPrevista()), valor(tx.getDescontoPrevisto()),
                valor(tx.getValorTotalRealizado()), valor(tx.getValorTotalAberto()), tx.getCategoria(), tx.getCentroCusto(), tx.getOrigem(),
                tx.getDocumentoReferencia(), tx.getEntidadeExternaId(), tx.getEntidadeNome(), tx.getCodigoReferencia(), tx.getSituacao(),
                tx.getRecorrencia(), tx.getQuantidadeRecorrencia(), tx.getAgendado(), tx.getFormaMovimentacao(), tx.getContaBancaria(),
                tx.getNotaFiscal(), tx.getObservacoes());
        List<Object> rateiosNormalizados = lista.stream().sorted(Comparator.comparingInt(Rateio::bloco))
                .map(r -> (Object) Arrays.asList(r.bloco(), r.categoria(), valor(r.valorCategoria()), r.centro(), valor(r.valorCentro()))).toList();
        String[] nomes = {"data", "dataCompetencia", "dataVencimento", "dataPrevista", "dataRealizacao",
            "descricao", "tipo", "valor", "valorOriginal", "valorRealizado", "valorAberto", "jurosRealizado",
            "multaRealizada", "descontoRealizado", "jurosPrevisto", "multaPrevista", "descontoPrevisto",
            "valorTotalRealizado", "valorTotalAberto", "categoria", "centroCusto", "origem", "documentoReferencia",
            "entidadeExternaId", "entidadeNome", "codigoReferencia", "situacao", "recorrencia", "quantidadeRecorrencia",
            "agendado", "formaMovimentacao", "contaBancaria", "notaFiscal", "observacoes"};
        Map<String, Object> parcela = new LinkedHashMap<>();
        for (int i = 0; i < nomes.length; i++) {
            parcela.put(nomes[i], valores.get(i));
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("parcela", parcela);
        snapshot.put("rateios", rateiosNormalizados);
        return snapshot;
    }

    private void preencher(FinancialTransaction a, FinancialTransaction b) {
        a.setData(b.getData());
        a.setDataCompetencia(b.getDataCompetencia());
        a.setDataVencimento(b.getDataVencimento());
        a.setDataPrevista(b.getDataPrevista());
        a.setDataRealizacao(b.getDataRealizacao());
        a.setDescricao(b.getDescricao());
        a.setTipo(b.getTipo());
        a.setValor(b.getValor());
        a.setValorOriginal(b.getValorOriginal());
        a.setValorRealizado(b.getValorRealizado());
        a.setValorAberto(b.getValorAberto());
        a.setJurosRealizado(b.getJurosRealizado());
        a.setMultaRealizada(b.getMultaRealizada());
        a.setDescontoRealizado(b.getDescontoRealizado());
        a.setJurosPrevisto(b.getJurosPrevisto());
        a.setMultaPrevista(b.getMultaPrevista());
        a.setDescontoPrevisto(b.getDescontoPrevisto());
        a.setValorTotalRealizado(b.getValorTotalRealizado());
        a.setValorTotalAberto(b.getValorTotalAberto());
        a.setCategoria(b.getCategoria());
        a.setCentroCusto(b.getCentroCusto());
        a.setOrigem(b.getOrigem());
        a.setDocumentoReferencia(b.getDocumentoReferencia());
        a.setEntidadeExternaId(b.getEntidadeExternaId());
        a.setEntidadeNome(b.getEntidadeNome());
        a.setCodigoReferencia(b.getCodigoReferencia());
        a.setSituacao(b.getSituacao());
        a.setRecorrencia(b.getRecorrencia());
        a.setQuantidadeRecorrencia(b.getQuantidadeRecorrencia());
        a.setAgendado(b.getAgendado());
        a.setFormaMovimentacao(b.getFormaMovimentacao());
        a.setContaBancaria(b.getContaBancaria());
        a.setNotaFiscal(b.getNotaFiscal());
        a.setObservacoes(b.getObservacoes());
    }

    private String texto(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String valor(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String serializar(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível gerar o controle da importação.", e);
        }
    }

    private String hash(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(readOnly = true)
public Candidato consultarCandidato(Long id) {
    Long clienteId = contexto.getClienteAtual().getId();

    FinancialTransaction lancamento = existentes(clienteId)
            .stream()
            .filter(item -> Objects.equals(item.getId(), id))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Registro não encontrado neste cliente."
            ));

    return new Candidato(
            lancamento.getId(),
            lancamento.getDescricao(),
            lancamento.getDataCompetencia() == null
                    ? lancamento.getData()
                    : lancamento.getDataCompetencia(),
            lancamento.getDataVencimento(),
            lancamento.getValorOriginal() == null
                    ? lancamento.getValor()
                    : lancamento.getValorOriginal(),
            lancamento.getValorRealizado(),
            lancamento.getValorAberto(),
            lancamento.getSituacao(),
            lancamento.getEntidadeNome(),
            lancamento.getCodigoReferencia(),
            lancamento.getCategoria(),
            lancamento.getCentroCusto()
    );
}
}
