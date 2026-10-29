package com.finvista.integration.contaazul;



import com.fasterxml.jackson.databind.ObjectMapper;

import com.finvista.repository.*;

import com.finvista.service.ClienteContextService;

import java.math.BigDecimal;

import java.time.Instant;

import java.util.*;

import org.springframework.data.domain.PageRequest;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.server.ResponseStatusException;





@Service

public class ContaAzulImportDiagnosticService {

    private final ClienteContextService context;

    private final ContaAzulAutoRunRepository runs;

    private final ContaAzulAutoItemRepository items;

    private final ContaAzulSourceCaptureRepository sources;

    private final ContaAzulApiPlanService plans;

    private final ObjectMapper json;

    public ContaAzulImportDiagnosticService(ClienteContextService context, ContaAzulAutoRunRepository runs,

            ContaAzulAutoItemRepository items, ContaAzulSourceCaptureRepository sources,

            ContaAzulApiPlanService plans, ObjectMapper json) {

        this.context=context; this.runs=runs; this.items=items; this.sources=sources; this.plans=plans; this.json=json;

    }

    public record Pending(String parcelaId, String tipo, String descricao, String mensagemOriginal,

            Instant capturadoEm, String situacaoPlano, List<String> impedimentos,

            BigDecimal pagoInformado, List<ContaAzulParcelDetail.Baixa> baixas, ContaAzulParcelDetail parcelaArquivada) {}

    public record Result(Long clienteId, UUID execucaoId, String statusExecucao, List<Pending> pendencias, String aviso) {}

    private void access(Long cliente) {

        context.validarAdministrador();

        if (cliente==null || !cliente.equals(context.getClienteAtualId()))

            throw new IllegalArgumentException("A empresa selecionada mudou.");

    }

    @Transactional(readOnly=true)

    public Result diagnose(Long cliente, UUID runId) {

        access(cliente);

        var run=runs.findByIdAndClienteId(runId, cliente).orElseThrow(() ->

            new ResponseStatusException(HttpStatus.NOT_FOUND,"Execução não encontrada nesta empresa."));

        var rows=new ArrayList<Pending>();

        for(int page=0; ; page++) {

            var batch=items.findByExecucaoIdAndClienteIdAndStatusOrderByIdAsc(runId, cliente, "CONFERIR", PageRequest.of(page,100));

            for(var item:batch.getContent()) {

                var capture=sources.findFirstByClienteIdAndExecucaoIdAndTipoAndChave(cliente,runId,"PARCELA",item.parcelaId);

                var reasons=new LinkedHashSet<String>();

                String state="SEM_FONTE_ARQUIVADA"; Instant at=null; BigDecimal paid=null;

                List<ContaAzulParcelDetail.Baixa> payments=List.of();

                ContaAzulParcelDetail archived=null;

                if(capture.isEmpty()) reasons.add("Detalhes não arquivados nesta execução. A mensagem original deve ser conferida na parcela.");

                else {

                    at=capture.get().capturadoEm;

                    ContaAzulParcelDetail p=null;

                    try { p=json.readValue(capture.get().conteudo,ContaAzulParcelDetail.class); }

                    catch(Exception e) { state="FONTE_INVALIDA"; reasons.add("Não foi possível ler a parcela arquivada."); }

                    if(p!=null) {

                        archived=p;

                        if(p.id()==null || !item.parcelaId.equalsIgnoreCase(p.id()) || p.evento()==null || !Objects.equals(item.tipo,p.evento().tipo())) {

                            state="IDENTIDADE_DIVERGENTE"; reasons.add("A fonte arquivada não corresponde à parcela/tipo da fila.");

                        } else {

                            var plan=plans.analisar(cliente,p);

                            state=plan.situacao().name();

                            reasons.addAll(plan.avisos());

                            try { ContaAzulSettlementPolicy.conferir(p); }

                            catch(IllegalArgumentException e) { reasons.add(e.getMessage()); }

                            paid=p.pago(); payments=p.baixas()==null?List.of():p.baixas();

                        }

                    }

                }

                rows.add(new Pending(item.parcelaId,item.tipo,item.descricao,item.mensagem,at,state,List.copyOf(reasons),paid,payments,archived));

            }

            if(!batch.hasNext()) break;

            if(page>=999) throw new IllegalStateException("Diagnóstico acima do limite. Consulte por execução menor.");

        }

        access(cliente);

        return new Result(cliente,runId,run.status,List.copyOf(rows),

            "Leitura das pendências e fontes arquivadas desta execução, reavaliadas contra o cadastro atual. Não consulta o Conta Azul, não resolve pendências e não altera valores. Pago informado é da parcela inteira, não a diferença do período; valores só podem ser comparados pelas respectivas datas das baixas. Execuções em andamento podem mudar durante a consulta.");

    }

}

