package com.finvista.integration.contaazul;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.dto.ContaAzulApiSyncResponse.*;
import com.finvista.model.*;
import com.finvista.repository.*;
import com.finvista.service.ClienteContextService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service

public class ContaAzulApiSyncService {

    private static final String ORIGEM = ContaAzulApiPlanService.ORIGEM_API;
    private final ContaAzulConnectionService conexoes;
    private final ContaAzulParcelDetailClient parcelas;
    private final ContaAzulApiPlanService planos;
    private final FinancialTransactionRepository transacoes;
    private final FinancialAllocationRepository rateios;
    private final ContaAzulApiOperationRepository operacoes;
    private final ClienteRepository clientes;
    private final ClienteContextService contexto;
    private final ContaAzulProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public ContaAzulApiSyncService(ContaAzulConnectionService conexoes, ContaAzulParcelDetailClient parcelas,
            ContaAzulApiPlanService planos, FinancialTransactionRepository transacoes,
            FinancialAllocationRepository rateios, ContaAzulApiOperationRepository operacoes,
            ClienteRepository clientes, ClienteContextService contexto, ContaAzulProperties properties,
            ObjectMapper json, Clock clock) {
        this.conexoes = conexoes;
        this.parcelas = parcelas;
        this.planos = planos;
        this.transacoes = transacoes;
        this.rateios = rateios;
        this.operacoes = operacoes;
        this.clientes = clientes;
        this.contexto = contexto;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    private record Assinatura(UUID operacaoId, Long clienteId, Long usuarioId, String parcelaId,
            long expiraEm, String fonte, String banco, String acao, Long lancamentoId) {}

    private record Estado(List<FinancialTransaction> registros, List<FinancialAllocation> alocacoes) {}

    private record Proposta(String acao, FinancialTransaction destino, Resumo antes, Resumo depois,
            List<Rateio> rateios, List<String> avisos) {}

    @Transactional(readOnly = true)
    public Previa preparar(String parcelaId, Long clienteId) {
        return preparar(parcelaId, clienteId, null);
    }

    @Transactional(readOnly = true)
    public Previa prepararVinculo(String parcelaId, Long clienteId, Long lancamentoId) {
        if (lancamentoId == null || lancamentoId <= 0) {
            throw new IllegalArgumentException("Escolha o lançamento Excel para conferir o vínculo.");
        }
        return preparar(parcelaId, clienteId, lancamentoId);
    }

    private Previa preparar(String parcelaId, Long clienteId, Long vincularId) {
        Long usuario = conferir(clienteId, null);
        String id = id(parcelaId);
        var parcela = consultar(clienteId, id);
        var estado = estado(clienteId);
        var proposta = propor(clienteId, parcela, estado, vincularId);
        Instant expira = clock.instant().plusSeconds(600);
        var assinatura = new Assinatura(UUID.randomUUID(), clienteId, usuario, id, expira.getEpochSecond(),
                hash(parcela), hash(estado(estado)), proposta.acao(), proposta.destino() == null ? null : proposta.destino().getId());
        conferir(clienteId, usuario);
        return new Previa(clienteId, contexto.getClienteAtual().getNome(), id, proposta.acao(), assinatura.lancamentoId(),
                expira, assinar(assinatura), proposta.antes(), proposta.depois(), proposta.avisos());
    }

    @Transactional(rollbackFor = Exception.class)
    public Resultado confirmar(String parcelaId, Confirmacao pedido) {
        if (pedido == null || !pedido.confirmado()) {
            throw new IllegalArgumentException("Revise a prévia e confirme explicitamente a operação.");
        }
        Long usuario = conferir(pedido.clienteId(), null);
        var assinatura = ler(pedido.token());
        String id = id(parcelaId);
        if (!Objects.equals(assinatura.clienteId(), pedido.clienteId()) || !Objects.equals(assinatura.usuarioId(), usuario)
                || !id.equals(assinatura.parcelaId())) {
            throw conflito("A prévia pertence a outro usuário, cliente ou parcela.");
        }
        if ("INCLUIR".equals(assinatura.acao()) && !pedido.confirmoNaoDuplicado()) {
            throw new IllegalArgumentException("Confirme que revisou os lançamentos existentes e que esta parcela não está cadastrada.");
        }
        var anterior = operacoes.findByIdAndClienteId(assinatura.operacaoId(), pedido.clienteId());
        if (anterior.isPresent()) {
            return anterior.get().resultado();
        }
        if (clock.instant().getEpochSecond() >= assinatura.expiraEm()) {
            throw conflito("Prévia expirada. Prepare e revise outra prévia.");
        }
        var parcela = consultar(pedido.clienteId(), id);
        if (!hash(parcela).equals(assinatura.fonte())) {
            throw conflito("A parcela mudou na Conta Azul. Prepare e revise outra prévia.");
        }
        Cliente cliente = clientes.bloquearAtivoPorId(pedido.clienteId())
                .orElseThrow(() -> conflito("Cliente indisponível."));
        conferir(pedido.clienteId(), usuario);
        anterior = operacoes.findByIdAndClienteId(assinatura.operacaoId(), pedido.clienteId());
        if (anterior.isPresent()) {
            return anterior.get().resultado();
        }
        if (clock.instant().getEpochSecond() >= assinatura.expiraEm()) {
            throw conflito("Prévia expirada durante a consulta. Prepare outra prévia.");
        }
        var estado = estado(pedido.clienteId());
        if (!hash(estado(estado)).equals(assinatura.banco())) {
            throw conflito("Os dados do FinVista mudaram. Prepare e revise outra prévia.");
        }
        boolean vinculo = "VINCULAR_EXCEL".equals(assinatura.acao());
        var proposta = propor(pedido.clienteId(), parcela, estado, vinculo ? assinatura.lancamentoId() : null);
        Long alvo = proposta.destino() == null ? null : proposta.destino().getId();
        if (!proposta.acao().equals(assinatura.acao()) || !Objects.equals(alvo, assinatura.lancamentoId())) {
            throw conflito("A ação ou o vínculo mudou. Prepare outra prévia.");
        }
        FinancialTransaction destino = proposta.destino() == null ? new FinancialTransaction() : proposta.destino();
        String antes = proposta.destino() == null ? null : escrever(snapshot(destino, alocacoes(destino, estado.alocacoes())));
        if (vinculo) {
            destino.setOrigem(ORIGEM);
            destino.setDocumentoReferencia(id);
        } else {
            preencher(destino, cliente, parcela, proposta.rateios());
        }
        String depois = escrever(snapshot(destino, proposta.rateios()));
        String acao = antes != null && antes.equals(depois) && !baixasMudaram(destino, parcela) ? "SEM_ALTERACAO" : proposta.acao();
        if (!"SEM_ALTERACAO".equals(acao)) {
            destino = transacoes.saveAndFlush(destino);
            if (!vinculo) {
                rateios.deleteByLancamentoIdAndLancamentoClienteId(destino.getId(), cliente.getId());
                rateios.flush();
                List<FinancialAllocation> novos = new ArrayList<>();
                int bloco = 1;
                for (var r : proposta.rateios()) {
                    novos.add(new FinancialAllocation(destino, bloco++, r.categoria(), r.valor(), r.centro(), r.valorCentro()));
                }
                rateios.saveAll(novos);
                rateios.flush();
            }
        }
        conferir(cliente.getId(), usuario);
        var registro = new ContaAzulApiOperation(assinatura.operacaoId(), cliente, usuario, id, acao, destino.getId(),
                clock.instant(), antes, depois, escrever(parcela));
        operacoes.saveAndFlush(registro);
        return registro.resultado();
    }

    @Transactional(readOnly = true)
    public Auditoria auditoria(UUID operacaoId, Long clienteId) {
        conferir(clienteId, null);
        var registro = operacoes.findByIdAndClienteId(operacaoId, clienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Operação não encontrada para esta empresa."));
        try {
            return new Auditoria(registro.resultado(), registro.getUsuarioId(),
                    registro.getAntes() == null ? null : json.readTree(registro.getAntes()), json.readTree(registro.getDepois()));
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível ler a auditoria da operação.");
        }
    }

    Resultado aplicarAutomatico(Cliente cliente, Long operador, UUID operacaoId, ContaAzulParcelDetail parcela, boolean incluirNovas) {
        String id = id(parcela.id());
        var anterior = operacoes.findByIdAndClienteId(operacaoId, cliente.getId());
        if (anterior.isPresent()) {
            return anterior.get().resultado();
        }
        if (com.finvista.service.FinancialStatusPolicy.cancelado(parcela.status())) {
            return cancelarConfirmado(cliente, operador, operacaoId, parcela);
        }
        var estado = estado(cliente.getId());
        var proposta = propor(cliente.getId(), parcela, estado, null, true);
        if ("INCLUIR".equals(proposta.acao()) && !incluirNovas) {
            throw conflito("Inclusão automática de novas parcelas desativada. Revise e sincronize individualmente ou autorize a inclusão na configuração.");
        }
        var destino = proposta.destino() == null ? new FinancialTransaction() : proposta.destino();
        String antes = proposta.destino() == null ? null : escrever(snapshot(destino, alocacoes(destino, estado.alocacoes())));
        preencher(destino, cliente, parcela, proposta.rateios());
        String depois = escrever(snapshot(destino, proposta.rateios()));
        String acao = antes != null && antes.equals(depois) && !baixasMudaram(destino, parcela) ? "SEM_ALTERACAO" : proposta.acao();
        if ("SEM_ALTERACAO".equals(acao)) {
            return new Resultado(operacaoId, cliente.getId(), id, acao, destino.getId(), clock.instant());
        }
        if (!"SEM_ALTERACAO".equals(acao)) {
            destino = transacoes.saveAndFlush(destino);
            rateios.deleteByLancamentoIdAndLancamentoClienteId(destino.getId(), cliente.getId());
            rateios.flush();
            List<FinancialAllocation> novos = new ArrayList<>();
            int bloco = 1;
            for (var r : proposta.rateios()) {
                novos.add(new FinancialAllocation(destino, bloco++, r.categoria(), r.valor(), r.centro(), r.valorCentro()));
            }
            rateios.saveAll(novos);
            rateios.flush();
        }
        var registro = new ContaAzulApiOperation(operacaoId, cliente, operador, id, acao, destino.getId(), clock.instant(), antes, depois, escrever(parcela));
        operacoes.saveAndFlush(registro);
        return registro.resultado();
    }

    private boolean baixasMudaram(FinancialTransaction t, ContaAzulParcelDetail p) {
        if (t.getId() == null || t.getCliente() == null) {
            return false;
        }
        var ultima = operacoes.findFirstByClienteIdAndLancamentoIdOrderByConcluidoEmDescIdDesc(t.getCliente().getId(), t.getId());
        if (ultima.isEmpty()) {
            return true;
        }
        try {
            var anterior = json.readValue(ultima.get().getOrigemSnapshot(), ContaAzulParcelDetail.class);
            return !Objects.equals(baixasOrdenadas(anterior), baixasOrdenadas(p))
                    || anterior.evento() == null || p.evento() == null
                    || !Objects.equals(anterior.evento().rateio(), p.evento().rateio())
                    || !Objects.equals(anterior.perda(), p.perda()) || !Objects.equals(anterior.renegociacao(), p.renegociacao())
                    || !Objects.equals(anterior.contaId(), p.contaId()) || !Objects.equals(anterior.conta(), p.conta());
        } catch (Exception e) {
            return true;
        }
    }

    private List<ContaAzulParcelDetail.Baixa> baixasOrdenadas(ContaAzulParcelDetail p) {
        return p.baixas() == null ? List.of() : p.baixas().stream().sorted(Comparator.comparing(ContaAzulParcelDetail.Baixa::id)).toList();
    }

    private Resultado cancelarConfirmado(Cliente cliente, Long usuario, UUID operacaoId, ContaAzulParcelDetail p) {
        if (p.evento() == null || p.evento().tipo() == null) {
            throw conflito("Cancelamento sem identificação do evento exige revisão.");
        }
        var estado = estado(cliente.getId());
        var identificados = estado.registros().stream().filter(t -> ORIGEM.equals(t.getOrigem()) && p.id().equalsIgnoreCase(t.getDocumentoReferencia())).toList();
        if (identificados.size() != 1) {
            throw conflito("Parcela cancelada sem vínculo API único. Nenhum registro foi criado ou excluído.");
        }
        var destino = identificados.get(0);
        if (!p.evento().tipo().equals(destino.getTipo())) {
            throw conflito("Cancelamento com tipo diferente do lançamento.");
        }
        try { ContaAzulSettlementPolicy.conferir(p); }
        catch (IllegalArgumentException e) { throw conflito("Cancelamento sem financeiro conferido: " + e.getMessage()); }
        var atuais = alocacoes(destino, estado.alocacoes());
        String antes = escrever(snapshot(destino, atuais));
        preencher(destino, cliente, p, atuais);
        if (antes.equals(escrever(snapshot(destino, atuais))) && !baixasMudaram(destino, p))
            return new Resultado(operacaoId, cliente.getId(), p.id(), "SEM_ALTERACAO", destino.getId(), clock.instant());
        transacoes.saveAndFlush(destino);
        var registro = new ContaAzulApiOperation(operacaoId, cliente, usuario, p.id(), "ATUALIZAR", destino.getId(), clock.instant(), antes, escrever(snapshot(destino, atuais)), escrever(p));
        operacoes.saveAndFlush(registro);
        return registro.resultado();
    }

    private ContaAzulParcelDetail consultar(Long cliente, String id) {
        return parcelas.consultar(conexoes.acessoParaConsulta(cliente), id);
    }

    private Long conferir(Long cliente, Long usuario) {
        contexto.validarAdministrador();
        Long atual = contexto.getUsuarioAutenticado().getId();
        if (cliente == null || !cliente.equals(contexto.getClienteAtualId()) || (usuario != null && !usuario.equals(atual))) {
            throw conflito("A sessão, o usuário ou a empresa selecionada mudou. Prepare outra prévia.");
        }
        return atual;
    }

    private String id(String valor) {
        if (valor == null || !valor.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Identificador de parcela inválido.");
        }
        return valor.toLowerCase(Locale.ROOT);
    }

    private Proposta propor(Long cliente, ContaAzulParcelDetail p, Estado estado, Long vincularId) {
        return propor(cliente, p, estado, vincularId, false);
    }

    private Proposta propor(Long cliente, ContaAzulParcelDetail p, Estado estado, Long vincularId, boolean automatico) {
        var plano = automatico ? planos.analisar(cliente, p, estado.registros()) : planos.planejar(cliente, p);
        if (plano.situacao() == ContaAzulApiPlanService.Situacao.BLOQUEADO) {
            throw conflito("Impedimentos: " + String.join(" | ", plano.avisos()));
        }
        if (vincularId == null && plano.situacao() == ContaAzulApiPlanService.Situacao.CORRESPONDENCIA_POSSIVEL) {
            throw conflito("Há possíveis lançamentos existentes. O vínculo com registros de arquivo ou cadastro manual exige conferência específica; a inclusão foi bloqueada.");
        }
        ContaAzulSettlementPolicy.Valores financeiro;
        try {
            financeiro = ContaAzulSettlementPolicy.conferir(p);
        } catch (IllegalArgumentException e) {
            throw conflito(e.getMessage());
        }
        if (financeiro.original().signum() <= 0) {
            throw conflito("O principal deve ser positivo.");
        }
        var identificados = estado.registros().stream().filter(t -> ORIGEM.equals(t.getOrigem())
                && p.id().equalsIgnoreCase(t.getDocumentoReferencia())).toList();
        if (vincularId != null) {
            if (!identificados.isEmpty()) {
                throw conflito("A parcela já possui vínculo API. Gere outro plano.");
            }
            var excel = estado.registros().stream().filter(t -> Objects.equals(t.getId(), vincularId)
                    && "CONTA_AZUL_EXCEL".equals(t.getOrigem())).findFirst()
                    .orElseThrow(() -> conflito("O lançamento Excel não está disponível nesta empresa."));
            validarVinculo(excel, p, financeiro);
            var atuais = alocacoes(excel, estado.alocacoes());
            var resumo = resumo(excel, atuais);
            return new Proposta("VINCULAR_EXCEL", excel, resumo, resumo, atuais,
                    List.of("Confirme que este lançamento Excel e esta parcela da API representam a mesma operação.",
                            "O vínculo mantém valores, datas, metadados e rateios do lançamento. Somente a origem e a referência externa passam ao controle da API.",
                            "As futuras importações Excel protegerão esta referência histórica. Após o vínculo, a sincronização pela API e a automação autorizada podem atualizar este mesmo registro, incluindo valores e rateios da fonte."));
        }
        if (identificados.size() > 1 || (plano.situacao() == ContaAzulApiPlanService.Situacao.REGISTRO_IDENTIFICADO && identificados.size() != 1)) {
            throw conflito("O vínculo externo não é único. Revise os registros existentes.");
        }
        if (identificados.isEmpty() && estado.registros().stream().anyMatch(t -> ORIGEM.equals(t.getOrigem())
                && (t.getDocumentoReferencia() == null || t.getDocumentoReferencia().isBlank()))) {
            throw conflito("Há registros de API sem identidade externa. Revise-os antes de incluir parcelas.");
        }
        var destino = identificados.isEmpty() ? null : identificados.get(0);
        if (estado.registros().stream().anyMatch(t -> !ORIGEM.equals(t.getOrigem()) && sobrepoe(t, p))) {
            throw conflito("Há um lançamento de outra origem com referência ou valor e datas compatíveis. O vínculo precisa ser conferido antes de sincronizar.");
        }
        if (destino != null && !p.evento().tipo().equals(destino.getTipo())) {
            throw conflito("O tipo da parcela mudou. O vínculo precisa de revisão.");
        }
        List<Rateio> novos = new ArrayList<>();
        Set<String> nomesCategorias = new HashSet<>();
        boolean categoriasConferidas = planos.avisosClassificacao(p, false).isEmpty();
        if (!categoriasConferidas) {
            novos.add(new Rateio("Categoria a revisar", financeiro.original(), "Centro a revisar", financeiro.original()));
        }
        for (var categoria : categoriasConferidas ? p.evento().rateio() : List.<ContaAzulParcelDetail.Rateio>of()) {
            if (!nomesCategorias.add(categoria.categoriaNome().trim().toLowerCase(Locale.ROOT))) {
                throw conflito("Categorias de mesmo nome exigem conferência específica.");
            }
            if (!planos.avisosCentros(categoria).isEmpty()) {
                novos.add(new Rateio(categoria.categoriaNome().trim(), moeda(categoria.bruto()), "Centro a revisar", moeda(categoria.bruto())));
            } else if (categoria.centros().isEmpty()) {
                novos.add(new Rateio(categoria.categoriaNome().trim(), moeda(categoria.bruto()), null, null));
            } else {
                Set<String> centros = new HashSet<>();
                for (var centro : categoria.centros()) {
                    if (!centros.add(centro.centroNome().trim().toLowerCase(Locale.ROOT))) {
                        throw conflito("Centros com nomes repetidos exigem conferência específica.");
                    }
                    novos.add(new Rateio(categoria.categoriaNome().trim(), moeda(centro.bruto()), centro.centroNome().trim(), moeda(centro.bruto())));
                }
            }
        }
        var ordenados = ordenar(novos);
        Resumo antes = destino == null ? null : resumo(destino, alocacoes(destino, estado.alocacoes()));
        var depois = new Resumo(p.descricao(), p.evento().tipo(), p.evento().competencia(), p.vencimento(),
                moeda(p.composicao().bruto()), financeiro.totalPago(), financeiro.totalAberto(), ordenados, detalhe(financeiro));
        var avisos = new ArrayList<String>(plano.avisos());
        avisos.addAll(destino == null ? List.of("A ausência de correspondências não comprova que a parcela seja nova. Confira os lançamentos da empresa antes de confirmar.")
                        : List.of("Atualização restrita ao registro já identificado pelo ID externo da API."));
        return new Proposta(destino == null ? "INCLUIR" : "ATUALIZAR", destino, antes, depois, ordenados, List.copyOf(avisos));
    }

    private void preencher(FinancialTransaction t, Cliente cliente, ContaAzulParcelDetail p, List<Rateio> r) {
        t.setCliente(cliente);
        t.setData(p.evento().competencia());
        t.setDataCompetencia(p.evento().competencia());
        t.setDataVencimento(p.vencimento());
        t.setDescricao(p.descricao());
        t.setTipo(p.evento().tipo());
        t.setValor(moeda(p.composicao().bruto()));
        t.setValorOriginal(moeda(p.composicao().bruto()));
        var f = ContaAzulSettlementPolicy.conferir(p);
        t.setDataRealizacao(f.ultimaBaixa());
        t.setValorRealizado(f.principalPago());
        t.setValorAberto(f.principalAberto());
        t.setValorTotalRealizado(f.totalPago());
        t.setValorTotalAberto(f.totalAberto());
        t.setJurosRealizado(f.jurosPago());
        t.setMultaRealizada(f.multaPaga());
        t.setDescontoRealizado(f.descontoPago());
        t.setJurosPrevisto(f.jurosAberto());
        t.setMultaPrevista(f.multaAberta());
        t.setDescontoPrevisto(f.descontoAberto());
        t.setOrigem(ORIGEM);
        t.setDocumentoReferencia(p.id().toLowerCase(Locale.ROOT));
        t.setSituacao(p.status());
        t.setCategoria(r.stream().map(Rateio::categoria).distinct().count() == 1 ? r.get(0).categoria() : null);
        t.setCentroCusto(r.stream().map(Rateio::centro).distinct().count() == 1 ? r.get(0).centro() : null);
    }

    private boolean sobrepoe(FinancialTransaction t, ContaAzulParcelDetail p) {
        if (p.id().equalsIgnoreCase(t.getDocumentoReferencia())) {
            return true;
        }
        BigDecimal valor = t.getValorOriginal() == null ? t.getValor() : t.getValorOriginal();
        LocalDate competencia = t.getDataCompetencia() == null ? t.getData() : t.getDataCompetencia();
        return p.evento().tipo().equals(t.getTipo()) && valor != null && valor.compareTo(p.composicao().bruto()) == 0
                && (p.evento().competencia().equals(competencia) || p.vencimento().equals(t.getDataVencimento()));
    }

    private void validarVinculo(FinancialTransaction t, ContaAzulParcelDetail p,
            ContaAzulSettlementPolicy.Valores f) {
        LocalDate competencia = t.getDataCompetencia() == null ? t.getData() : t.getDataCompetencia();
        BigDecimal original = t.getValorOriginal() == null ? t.getValor() : t.getValorOriginal();
        if (!Objects.equals(t.getTipo(), p.evento().tipo()) || !Objects.equals(competencia, p.evento().competencia())
                || !Objects.equals(t.getDataVencimento(), p.vencimento())
                || t.getDescricao() == null || !t.getDescricao().trim().equalsIgnoreCase(p.descricao().trim())
                || original == null || original.compareTo(f.original()) != 0
                || t.getValor() == null || t.getValor().compareTo(original) != 0) {
            throw conflito("O vínculo exige descrição, tipo, principal original, competência e vencimento compatíveis. Confira as diferenças antes de vincular.");
        }
        conferirCampoVinculo("principal realizado", t.getValorRealizado(), f.principalPago());
        conferirCampoVinculo("principal em aberto", t.getValorAberto(), f.principalAberto());
        conferirCampoVinculo("total realizado", t.getValorTotalRealizado(), f.totalPago());
        conferirCampoVinculo("total em aberto", t.getValorTotalAberto(), f.totalAberto());
        conferirCampoVinculo("juros realizados", t.getJurosRealizado(), f.jurosPago());
        conferirCampoVinculo("multa realizada", t.getMultaRealizada(), f.multaPaga());
        conferirCampoVinculo("desconto realizado", t.getDescontoRealizado(), f.descontoPago());
        conferirCampoVinculo("juros em aberto", t.getJurosPrevisto(), f.jurosAberto());
        conferirCampoVinculo("multa em aberto", t.getMultaPrevista(), f.multaAberta());
        conferirCampoVinculo("desconto em aberto", t.getDescontoPrevisto(), f.descontoAberto());
        if (!Objects.equals(t.getDataRealizacao(), f.ultimaBaixa())) {
            throw conflito("A data da última baixa está ausente ou difere entre o Excel e a API. Confira a origem antes de vincular; o vínculo não altera datas.");
        }
    }

    private void conferirCampoVinculo(String campo, BigDecimal local, BigDecimal fonte) {
        if (local == null) {
            throw conflito("Campo " + campo + " não informado no registro Excel. Recupere os campos financeiros ausentes antes de preparar outro vínculo.");
        }
        if (local.compareTo(fonte) != 0) {
            throw conflito("Campo " + campo + " diverge entre o Excel e a API. Confira as diferenças antes de vincular; o vínculo não altera valores.");
        }
    }

    private Estado estado(Long cliente) {
        var registros = transacoes.findByClienteId(cliente).stream().sorted(Comparator.comparing(FinancialTransaction::getId)).toList();
        var ids = registros.stream().map(FinancialTransaction::getId).toList();
        var alocacoes = ids.isEmpty() ? List.<FinancialAllocation>of()
                : rateios.findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(cliente, ids);
        return new Estado(registros, alocacoes);
    }

    private Object estado(Estado estado) {
        return estado.registros().stream().map(t -> Arrays.asList(t.getId(), snapshot(t, alocacoes(t, estado.alocacoes())))).toList();
    }

    private List<Rateio> alocacoes(FinancialTransaction t, List<FinancialAllocation> lista) {
        return ordenar(lista.stream().filter(r -> Objects.equals(r.getLancamento().getId(), t.getId()))
                .map(r -> new Rateio(r.getCategoria(), moeda(r.getValorCategoria()), r.getCentro(), moeda(r.getValorCentro()))).toList());
    }

    private List<Rateio> ordenar(List<Rateio> lista) {
        return lista.stream().sorted(Comparator.comparing(Rateio::categoria, Comparator.nullsFirst(String::compareTo))
                .thenComparing(Rateio::centro, Comparator.nullsFirst(String::compareTo))
                .thenComparing(r -> r.valor().toPlainString())).toList();
    }

    private Resumo resumo(FinancialTransaction t, List<Rateio> lista) {
        return new Resumo(t.getDescricao(), t.getTipo(), t.getDataCompetencia(), t.getDataVencimento(),
                moeda(t.getValor()), moeda(t.getValorTotalRealizado()), moeda(t.getValorTotalAberto()), lista,
                new Financeiro(moeda(t.getValorRealizado()), moeda(t.getValorAberto()), moeda(t.getJurosRealizado()),
                        moeda(t.getMultaRealizada()), moeda(t.getDescontoRealizado()), moeda(t.getJurosPrevisto()),
                        moeda(t.getMultaPrevista()), moeda(t.getDescontoPrevisto()), t.getDataRealizacao()));
    }

    private Financeiro detalhe(ContaAzulSettlementPolicy.Valores f) {
        return new Financeiro(f.principalPago(), f.principalAberto(), f.jurosPago(), f.multaPaga(),
                f.descontoPago(), f.jurosAberto(), f.multaAberta(), f.descontoAberto(), f.ultimaBaixa(), f.taxaPaga(), f.taxaAberta(), f.perda(), f.renegociado());
    }

    private Object snapshot(FinancialTransaction t, List<Rateio> lista) {
        List<Object> valores = Arrays.asList(t.getData(), t.getDataCompetencia(), t.getDataVencimento(), t.getDataPrevista(), t.getDataRealizacao(),
                t.getDescricao(), t.getTipo(), moeda(t.getValor()), moeda(t.getValorOriginal()), moeda(t.getValorRealizado()),
                moeda(t.getValorAberto()), moeda(t.getJurosRealizado()), moeda(t.getMultaRealizada()), moeda(t.getDescontoRealizado()),
                moeda(t.getJurosPrevisto()), moeda(t.getMultaPrevista()), moeda(t.getDescontoPrevisto()), moeda(t.getValorTotalRealizado()),
                moeda(t.getValorTotalAberto()), t.getCategoria(), t.getCentroCusto(), t.getOrigem(), t.getDocumentoReferencia(),
                t.getEntidadeExternaId(), t.getEntidadeNome(), t.getCodigoReferencia(), t.getSituacao(), t.getRecorrencia(),
                t.getQuantidadeRecorrencia(), t.getAgendado(), t.getFormaMovimentacao(), t.getContaBancaria(), t.getNotaFiscal(), t.getObservacoes(), lista);
        String[] nomes = {"data", "dataCompetencia", "dataVencimento", "dataPrevista", "dataRealizacao", "descricao", "tipo", "valor", "valorOriginal", "valorRealizado", "valorAberto", "jurosRealizado", "multaRealizada", "descontoRealizado", "jurosPrevisto", "multaPrevista", "descontoPrevisto", "valorTotalRealizado", "valorTotalAberto", "categoria", "centroCusto", "origem", "documentoReferencia", "entidadeExternaId", "entidadeNome", "codigoReferencia", "situacao", "recorrencia", "quantidadeRecorrencia", "agendado", "formaMovimentacao", "contaBancaria", "notaFiscal", "observacoes", "rateios"};
        Map<String, Object> saida = new LinkedHashMap<>();
        for (int i = 0; i < nomes.length; i++) {
            saida.put(nomes[i], valores.get(i));
        }
        return saida;
    }

    private String assinar(Assinatura valor) {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(escrever(valor).getBytes(StandardCharsets.UTF_8));
        return "v1." + payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac(payload));
    }

    private Assinatura ler(String token) {
        try {
            if (token == null || token.length() > 4096) {
                throw new IllegalArgumentException();
            }
            String[] partes = token.split("\\.");
            if (partes.length != 3 || !"v1".equals(partes[0])
                    || !MessageDigest.isEqual(mac(partes[1]), Base64.getUrlDecoder().decode(partes[2]))) {
                throw new IllegalArgumentException();
            }
            return json.readValue(Base64.getUrlDecoder().decode(partes[1]), Assinatura.class);
        } catch (Exception e) {
            throw conflito("Prévia inválida. Prepare outra prévia.");
        }
    }

    private byte[] mac(String payload) {
        try {
            byte[] chave = Base64.getDecoder().decode(properties.getEncryptionKey());
            if (chave.length != 32) {
                throw new IllegalArgumentException();
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chave, "HmacSHA256"));
            return mac.doFinal(("finvista:conta-azul-sync:v1:" + payload).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível assinar a prévia. Confira a configuração da integração.");
        }
    }

    private String hash(Object valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(escrever(valor).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível conferir o estado da sincronização.");
        }
    }

    private String escrever(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível registrar a conferência.");
        }
    }

    private static BigDecimal moeda(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2);
    }

    private static boolean zero(BigDecimal valor) {
        return valor != null && valor.signum() == 0;
    }

    private static ResponseStatusException conflito(String mensagem) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensagem);
    }
}
