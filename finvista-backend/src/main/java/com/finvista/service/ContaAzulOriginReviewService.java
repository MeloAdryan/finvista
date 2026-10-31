package com.finvista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.integration.contaazul.*;
import com.finvista.model.*;
import com.finvista.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;


@Service
public class ContaAzulOriginReviewService {
    public record Request(Long clienteId, String parcelaId, String motivo, String substitutaId) {}
    public record Plan(Long clienteId, String parcelaId, Long lancamentoId, String descricao,
            BigDecimal principal, BigDecimal aberto, String situacao, String motivo, String substitutaId,
            String revisao, Long usuarioId) {}
    private final ClienteContextService ctx;
    private final FinancialTransactionRepository tx;
    private final ContaAzulApiOperationRepository ops;
    private final ContaAzulConnectionService connections;
    private final ContaAzulParcelDetailClient details;
    private final ObjectMapper json;
    private final Clock clock;
    public ContaAzulOriginReviewService(ClienteContextService ctx, FinancialTransactionRepository tx,
            ContaAzulApiOperationRepository ops, ContaAzulConnectionService connections,
            ContaAzulParcelDetailClient details, ObjectMapper json, Clock clock) {
        this.ctx=ctx;this.tx=tx;this.ops=ops;this.connections=connections;this.details=details;this.json=json;this.clock=clock;
    }
    private static void id(String value) {
        if(value==null || !value.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("Informe o identificador da parcela.");
    }
    private static boolean zero(BigDecimal value) { return value!=null && value.signum()==0; }
    private String encode(Object value) {
        try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Não foi possível registrar a revisão.",e);}
    }
    private String state(FinancialTransaction t) {
        var values=Arrays.asList(t.getId(),t.getDocumentoReferencia(),t.getDescricao(),t.getTipo(),t.getSituacao(),
                t.getDataCompetencia(),t.getDataVencimento(),t.getValor(),t.getValorRealizado(),t.getValorTotalRealizado(),
                t.getValorTotalAberto(),t.getObservacoes());
        String[] names={"id","documentoReferencia","descricao","tipo","situacao","dataCompetencia","dataVencimento", "valor","valorRealizado","valorTotalRealizado","valorTotalAberto","observacoes"};
        Map<String,Object> fields=new LinkedHashMap<>();
        for(int i=0;i<names.length;i++)fields.put(names[i],values.get(i));
        return encode(fields);
    }
    private FinancialTransaction find(String id, Long client) {
        var found=tx.findByClienteIdAndOrigemOrderByIdAsc(client,"CONTA_AZUL_API").stream()
                .filter(t->id.equalsIgnoreCase(t.getDocumentoReferencia())).toList();
        if(found.size()!=1)throw new IllegalArgumentException("A parcela deve possuir um único vínculo API nesta empresa.");
        return found.get(0);
    }
    @Transactional(readOnly=true)
    public Plan plan(Request request) {
        ctx.validarAdministrador();
        if(!Objects.equals(ctx.getClienteAtualId(),request.clienteId())) throw new IllegalArgumentException("A empresa mudou.");
        id(request.parcelaId());
        String reason=request.motivo()==null?"":request.motivo().trim();
        if(reason.length()<15 || reason.length()>1000)throw new IllegalArgumentException("Descreva a conferência do documento na origem (15 a 1000 caracteres).");
        var t=find(request.parcelaId(),request.clienteId());
        if(FinancialStatusPolicy.cancelado(t.getSituacao()))throw new IllegalArgumentException("Esta parcela já está cancelada no FinVista.");
        // Financial history cannot be removed through this unpaid-title workflow.
        if(!zero(t.getValorTotalRealizado()) || !zero(t.getValorRealizado()) || t.getDataRealizacao()!=null)
            throw new IllegalArgumentException("Há pagamento ou histórico insuficiente. Revise baixas e estornos; esta ação só aceita parcelas sem pagamento comprovado.");
        if(t.getValor()==null || t.getValor().signum()<0 || t.getValorTotalAberto()==null || t.getValorTotalAberto().signum()<0)
            throw new IllegalArgumentException("Principal ou saldo em aberto não conferido. Revise os valores antes de confirmar a remoção.");
        var previous=ops.findFirstByClienteIdAndLancamentoIdOrderByConcluidoEmDescIdDesc(request.clienteId(),t.getId())
                .orElseThrow(()->new IllegalArgumentException("Não há captura auditada para conferir esta parcela."));
        try {
            var p=json.readValue(previous.getOrigemSnapshot(),ContaAzulParcelDetail.class);
            if(!request.parcelaId().equalsIgnoreCase(p.id()) || !zero(p.pago()) || p.baixas()==null || !p.baixas().isEmpty())
                throw new IllegalArgumentException("A captura não comprova ausência de baixas. Revise o histórico de pagamentos.");
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Captura auditada ilegível.");}
        String replacement=request.substitutaId()==null?"":request.substitutaId().trim();
        var token=connections.acessoParaConsulta(request.clienteId());
        if(!replacement.isEmpty()) {
            id(replacement);
            if(replacement.equalsIgnoreCase(request.parcelaId()))throw new IllegalArgumentException("A substituta deve ser outra parcela.");
            var other=find(replacement,request.clienteId());
            var live=details.consultar(token,replacement);
            if(FinancialStatusPolicy.cancelado(other.getSituacao()) || !Objects.equals(t.getTipo(),other.getTipo())
                    || !replacement.equalsIgnoreCase(live.id()) || live.evento()==null || !Objects.equals(t.getTipo(),live.evento().tipo()))
                throw new IllegalArgumentException("Confira a identidade e o tipo da substituta nesta empresa.");
        }
        try {
            details.consultar(token,request.parcelaId());
            throw new IllegalArgumentException("A parcela voltou a estar disponível. Sincronize os dados atuais; não confirme remoção.");
        }catch(ResponseStatusException e){
            if(e.getStatusCode().value()!=404)throw e;
        }
        if(!Objects.equals(ctx.getClienteAtualId(),request.clienteId()))throw new IllegalArgumentException("A empresa mudou.");
        return new Plan(request.clienteId(),request.parcelaId(),t.getId(),t.getDescricao(),t.getValor(),t.getValorTotalAberto(),
                t.getSituacao(),reason,replacement,StatementImportService.hash(state(t)),ctx.getUsuarioAutenticado().getId());
    }
    @Transactional
    public void apply(Plan expected) {
        // Context obtains the company write lock used by synchronization and imports.
        ctx.getClienteAtual();
        var current=plan(new Request(expected.clienteId(),expected.parcelaId(),expected.motivo(),expected.substitutaId()));
        if(!current.equals(expected))throw new ResponseStatusException(HttpStatus.CONFLICT,"A parcela ou o usuário mudou. Prepare outra revisão.");
        var t=find(expected.parcelaId(),expected.clienteId());
        var previous=ops.findFirstByClienteIdAndLancamentoIdOrderByConcluidoEmDescIdDesc(expected.clienteId(),t.getId()).orElseThrow();
        String before=state(t);
        t.setSituacao("CANCELADO");
        String note="Revisão de origem confirmada em "+clock.instant()+" pelo usuário #"+expected.usuarioId()+". "+expected.motivo()
                +(expected.substitutaId().isEmpty()?"":" Substituta conferida: "+expected.substitutaId())+". Nenhum movimento do extrato foi alterado.";
        t.setObservacoes((t.getObservacoes()==null?"":t.getObservacoes()+"\n")+note);
        tx.saveAndFlush(t);
        ops.saveAndFlush(new ContaAzulApiOperation(UUID.randomUUID(),t.getCliente(),expected.usuarioId(),expected.parcelaId(),
                "REMOVER_ORIGEM",t.getId(),clock.instant(),before,state(t),previous.getOrigemSnapshot()));
    }
}
