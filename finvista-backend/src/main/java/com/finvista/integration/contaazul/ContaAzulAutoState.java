package com.finvista.integration.contaazul;

import com.finvista.dto.ContaAzulAutoResponse.*;
import com.finvista.model.*;
import com.finvista.repository.*;
import com.finvista.service.ClienteContextService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;

@Service
public class ContaAzulAutoState {
    public record Reserva(Long clienteId,UUID execucaoId,UUID dono) {}
    public record Cursor(LocalDate inicio,LocalDate fim,int etapa,int pagina) {}
    public static final class ReservaPerdida extends RuntimeException {}
    private final ContaAzulAutoConfigRepository configs;
    private final ContaAzulAutoRunRepository runs;
    private final ContaAzulAutoItemRepository itens;
    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;
    private final FinancialTransactionRepository transacoes;
    private final ContaAzulApiSyncService sync;
    private final ClienteContextService contexto;
    private final ContaAzulConfiguration integracao;
    private final Clock clock;
    private final boolean habilitado;

    public ContaAzulAutoState(ContaAzulAutoConfigRepository configs,ContaAzulAutoRunRepository runs,
            ContaAzulAutoItemRepository itens,ClienteRepository clientes,UsuarioRepository usuarios,
            FinancialTransactionRepository transacoes,ContaAzulApiSyncService sync,ClienteContextService contexto,
            ContaAzulConfiguration integracao,Clock clock,@Value("${CONTA_AZUL_AUTO_ENABLED:false}") boolean habilitado) {
        this.configs=configs;this.runs=runs;this.itens=itens;this.clientes=clientes;this.usuarios=usuarios;
        this.transacoes=transacoes;this.sync=sync;this.contexto=contexto;this.integracao=integracao;this.clock=clock;
        this.habilitado=habilitado;
    }

    @Transactional(readOnly=true)
    public Status status(Long cliente,int pagina) {
        conferir(cliente);
        if(pagina<1 || pagina>100000) throw new IllegalArgumentException("Página inválida.");
        var c=configs.findById(cliente).orElse(null);
        LocalDate hoje=LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
        Config config=c==null?new Config(cliente,false,hoje.minusDays(90),hoje.plusDays(90),30,false,null,null)
                :new Config(cliente,c.habilitada,Boolean.TRUE.equals(c.periodoMovel)?hoje.minusDays(90):c.inicio,Boolean.TRUE.equals(c.periodoMovel)?hoje.plusDays(90):c.fim,c.intervaloMinutos,c.incluirNovas,c.proximaEm,c.alteradoEm,Boolean.TRUE.equals(c.periodoMovel));
        var lista=runs.findTop10ByClienteIdOrderByCriadoEmDesc(cliente);
        List<Execucao> execucoes=lista.stream().map(r->new Execucao(r.id,r.status,r.inicio,r.fim,r.criadoEm,r.concluidoEm,
                r.incluidas,r.atualizadas,r.mantidas,r.pendentes,itens.countByExecucaoIdAndStatus(r.id,"EM_FILA"),r.mensagem)).toList();
        if(lista.isEmpty()) return new Status(habilitado,config,execucoes,List.of(),0,pagina);
        var pendentes=itens.findByExecucaoIdAndClienteIdAndStatusOrderByIdAsc(lista.get(0).id,cliente,"CONFERIR",PageRequest.of(pagina-1,20));
        conferir(cliente);
        return new Status(habilitado,config,execucoes,pendentes.getContent().stream()
                .map(i->new Pendencia(i.parcelaId,i.tipo,i.descricao,i.mensagem,i.lancamentoId)).toList(),pendentes.getTotalElements(),pagina);
    }

    @Transactional
    public void configurar(Configurar pedido) {
        Long usuario=conferir(pedido.clienteId());
        if(pedido.habilitada() && !habilitado) throw new IllegalStateException("Habilite CONTA_AZUL_AUTO_ENABLED no backend antes de ativar a automação.");
        LocalDate hoje=LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
        LocalDate inicio=pedido.periodoMovel()?hoje.minusDays(90):pedido.inicio();
        LocalDate fim=pedido.periodoMovel()?hoje.plusDays(90):pedido.fim();
        if(inicio==null || fim==null || fim.isBefore(inicio)
                || ChronoUnit.DAYS.between(inicio,fim)>365
                || !Set.of(30,60,360,1440).contains(pedido.intervaloMinutos()))
            throw new IllegalArgumentException("Escolha um período de até 366 dias e um intervalo disponível.");
        if(pedido.habilitada() && (!pedido.confirmado() || (pedido.incluirNovas() && !pedido.confirmoHistorico())))
            throw new IllegalArgumentException("Confirme a autorização e a revisão do histórico antes de ativar inclusões automáticas.");
        if(pedido.habilitada()) integracao.exigirAtiva();
        clientes.bloquearAtivoPorId(pedido.clienteId()).orElseThrow(()->new IllegalArgumentException("Empresa indisponível."));
        var c=configs.bloquear(pedido.clienteId()).orElseGet(()->{var novo=new ContaAzulAutoConfig();novo.clienteId=pedido.clienteId();return novo;});
        cancelar(c);
        c.habilitada=pedido.habilitada();c.incluirNovas=pedido.incluirNovas();c.inicio=inicio;c.fim=fim;c.periodoMovel=pedido.periodoMovel();
        c.intervaloMinutos=pedido.intervaloMinutos();c.autorizadoPor=usuario;c.revisao=UUID.randomUUID();c.alteradoEm=clock.instant();
        c.falhas=0;c.proximaEm=c.habilitada?clock.instant():null;
        conferir(pedido.clienteId());configs.saveAndFlush(c);
    }

    @Transactional
    public void pausar(Long cliente) {
        conferir(cliente);
        clientes.bloquearAtivoPorId(cliente).orElseThrow(()->new IllegalArgumentException("Empresa indisponível."));
        configs.bloquear(cliente).ifPresent(c->{cancelar(c);c.habilitada=false;c.proximaEm=null;c.revisao=UUID.randomUUID();c.alteradoEm=clock.instant();});
    }

    @Transactional
    public void solicitar(Long cliente) {
        conferir(cliente);
        var c=configs.bloquear(cliente).orElseThrow(()->new IllegalArgumentException("Configure a sincronização automática."));
        if(!c.habilitada) throw new IllegalArgumentException("Ative a sincronização para agendar uma execução.");
        c.proximaEm=clock.instant();
    }

    @Transactional(readOnly=true)
    public List<Long> disponiveis() { return configs.disponiveis(clock.instant(),PageRequest.of(0,10)); }

    @Transactional
    public Reserva assumir(Long cliente) {
        var c=configs.bloquear(cliente).orElse(null);
        Instant agora=clock.instant();
        if(c==null || !c.habilitada || c.proximaEm==null || c.proximaEm.isAfter(agora)
                || (c.reservaAte!=null && c.reservaAte.isAfter(agora))) return null;
        if(!autorizacaoAtiva(c)) {cancelar(c);c.habilitada=false;c.proximaEm=null;return null;}
        var r=c.execucaoId==null?null:runs.findByIdAndClienteId(c.execucaoId,cliente).orElse(null);
        if(r==null || finalizada(r.status)) {
            r=new ContaAzulAutoRun();r.id=UUID.randomUUID();r.clienteId=cliente;r.autorizadoPor=c.autorizadoPor;
            r.revisao=c.revisao;
            LocalDate hoje=LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
            r.inicio=Boolean.TRUE.equals(c.periodoMovel)?hoje.minusDays(90):c.inicio;
            r.fim=Boolean.TRUE.equals(c.periodoMovel)?hoje.plusDays(90):c.fim;r.incluirNovas=c.incluirNovas;
            r.criadoEm=agora;r.status="EM_ANDAMENTO";r.pagina=1;
            runs.saveAndFlush(r);c.execucaoId=r.id;
            for(var t:transacoes.findByClienteIdAndOrigemOrderByIdAsc(cliente,"CONTA_AZUL_API")) {
                if(t.getDataVencimento()!=null && !t.getDataVencimento().isBefore(r.inicio) && !t.getDataVencimento().isAfter(r.fim)) {
                    if(uuid(t.getDocumentoReferencia()) && ("RECEITA".equals(t.getTipo()) || "DESPESA".equals(t.getTipo())))
                        enfileirar(r,t.getDocumentoReferencia(),t.getTipo(),t.getDescricao(),false);
                    else {r.listagemMudou=true;r.mensagem="Há registros locais com identidade API inválida. Confira os vínculos.";}
                }
            }
        }
        r.status="EM_ANDAMENTO";if(!r.listagemMudou) r.mensagem=null;c.dono=UUID.randomUUID();c.reservaAte=agora.plusSeconds(120);
        return new Reserva(cliente,r.id,c.dono);
    }

    @Transactional
    public ContaAzulAutoItem proximo(Reserva reserva) {
        validar(reserva);
        return itens.findFirstByExecucaoIdAndStatusOrderByIdAsc(reserva.execucaoId(),"EM_FILA").orElse(null);
    }

    @Transactional
    public Cursor cursor(Reserva reserva) {
        var c=validar(reserva);var r=execucao(c);
        return new Cursor(r.inicio,r.fim,r.etapa,r.pagina);
    }

    @Transactional
    public void pagina(Reserva reserva,ContaAzulFinancialClient.Pagina pagina) {
        var c=validar(reserva);var r=execucao(c);
        if(r.etapa>1) throw new ReservaPerdida();
        if(pagina==null || pagina.itens()==null || pagina.totalItens()==null || pagina.totalItens()<0
                || pagina.totalItens()>100000 || pagina.itens().size()>100 || r.pagina>1000)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Listagem acima do limite ou inválida. Reduza o período e revise a consulta.");
        if(r.totalEsperado==null) r.totalEsperado=pagina.totalItens();
        else if(!r.totalEsperado.equals(pagina.totalItens())) r.listagemMudou=true;
        int restantes=Math.max(0,pagina.totalItens()-(r.pagina-1)*100);
        if(pagina.itens().size()!=Math.min(100,restantes))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A paginação retornou quantidade inconsistente. A execução ficou incompleta.");
        Set<String> ids=new HashSet<>();
        for(var p:pagina.itens()) {
            if(p==null || !uuid(p.id()) || !ids.add(p.id().toLowerCase(Locale.ROOT)))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"A listagem contém identidade de parcela inválida ou repetida.");
            enfileirar(r,p.id(),r.etapa==0?"RECEITA":"DESPESA",p.descricao(),true);
        }
        if((long)r.pagina*100>=pagina.totalItens()) {r.etapa++;r.pagina=1;r.totalEsperado=null;}
        else r.pagina++;
    }

    @Transactional
    public void aplicar(Reserva reserva,UUID itemId,ContaAzulParcelDetail parcela) {
        Cliente cliente=clientes.bloquearAtivoPorId(reserva.clienteId()).orElseThrow(ReservaPerdida::new);
        var c=validar(reserva);var r=execucao(c);var i=item(r,itemId);
        if(!"EM_FILA".equals(i.status)) return;
        if(parcela==null || !i.parcelaId.equalsIgnoreCase(parcela.id()) || parcela.evento()==null || !i.tipo.equals(parcela.evento().tipo()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A parcela ou o tipo retornado difere da fila desta empresa.");
        var resultado=sync.aplicarAutomatico(cliente,r.autorizadoPor,i.id,parcela,r.incluirNovas);
        i.status=resultado.acao();i.lancamentoId=resultado.lancamentoId();i.concluidoEm=clock.instant();i.mensagem=null;
        switch(i.status) {case "INCLUIR"->r.incluidas++;case "ATUALIZAR"->r.atualizadas++;default->r.mantidas++;}
    }

    @Transactional
    public void pendencia(Reserva reserva,UUID itemId,String mensagem) {
        var c=validar(reserva);var r=execucao(c);var i=item(r,itemId);
        if(!"EM_FILA".equals(i.status)) return;
        i.status="CONFERIR";i.mensagem=limitar(mensagem,500);i.concluidoEm=clock.instant();r.pendentes++;
    }

    @Transactional
    public void devolver(Reserva reserva,boolean finalizar) {
        var c=validar(reserva);var r=execucao(c);
        if(finalizar) {
            if(r.etapa!=2 || itens.countByExecucaoIdAndStatus(r.id,"EM_FILA")!=0) throw new ReservaPerdida();
            r.status=r.listagemMudou?"PARCIAL":r.pendentes>0?"CONCLUIDA_COM_PENDENCIAS":"CONCLUIDA";
            if(r.listagemMudou && r.mensagem==null) r.mensagem="A listagem mudou durante a consulta. A cobertura não foi confirmada; outra varredura será feita no próximo ciclo.";
            if(!r.listagemMudou) r.mensagem=null;
            r.concluidoEm=clock.instant();c.proximaEm=clock.instant().plusSeconds(c.intervaloMinutos*60L);c.falhas=0;
        } else c.proximaEm=clock.instant();
        c.dono=null;c.reservaAte=null;
    }

    @Transactional
    public void falhar(Reserva reserva,String mensagem,boolean parcial) {
        var c=validar(reserva);var r=execucao(c);r.mensagem=limitar(mensagem,500);
        c.falhas=Math.min(5,c.falhas+1);
        c.proximaEm=clock.instant().plusSeconds(Math.min(60,5L*(1L<<(c.falhas-1)))*60);
        r.status=parcial?"PARCIAL":"AGUARDANDO_REPETICAO";
        if(parcial) r.concluidoEm=clock.instant();
        c.dono=null;c.reservaAte=null;
    }

    private ContaAzulAutoConfig validar(Reserva reserva) {
        var c=configs.bloquear(reserva.clienteId()).orElseThrow(ReservaPerdida::new);
        if(!c.habilitada || !Objects.equals(c.execucaoId,reserva.execucaoId()) || !Objects.equals(c.dono,reserva.dono())
                || c.reservaAte==null || !c.reservaAte.isAfter(clock.instant()) || !autorizacaoAtiva(c)) throw new ReservaPerdida();
        var r=execucao(c);
        if(!Objects.equals(c.revisao,r.revisao) || finalizada(r.status)) throw new ReservaPerdida();
        c.reservaAte=clock.instant().plusSeconds(120);return c;
    }
    private ContaAzulAutoRun execucao(ContaAzulAutoConfig c) {return runs.findByIdAndClienteId(c.execucaoId,c.clienteId).orElseThrow(ReservaPerdida::new);}
    private ContaAzulAutoItem item(ContaAzulAutoRun r,UUID id) {
        var i=itens.findById(id).orElseThrow(ReservaPerdida::new);
        if(!Objects.equals(i.execucaoId,r.id) || !Objects.equals(i.clienteId,r.clienteId)) throw new ReservaPerdida();return i;
    }
    private void enfileirar(ContaAzulAutoRun r,String id,String tipo,String descricao,boolean listada) {
        id=id.toLowerCase(Locale.ROOT);
        UUID chave=UUID.nameUUIDFromBytes(("conta-azul-auto:"+r.id+":"+id).getBytes(StandardCharsets.UTF_8));
        var existente=itens.findById(chave).orElse(null);
        if(existente!=null) {
            if(listada && existente.listada) r.listagemMudou=true;
            if(listada && !tipo.equals(existente.tipo)) r.listagemMudou=true;
            existente.listada=existente.listada||listada;return;
        }
        var i=new ContaAzulAutoItem();i.id=chave;i.execucaoId=r.id;i.clienteId=r.clienteId;
        i.parcelaId=id;i.tipo=tipo;i.descricao=limitar(descricao,255);i.status="EM_FILA";i.listada=listada;itens.save(i);
    }
    private boolean autorizacaoAtiva(ContaAzulAutoConfig c) {
        return clientes.findByIdAndAtivoTrue(c.clienteId).isPresent() && c.autorizadoPor!=null
                && usuarios.findById(c.autorizadoPor).filter(u->Boolean.TRUE.equals(u.getAtivo()) && "ADMIN".equals(u.getPerfil())).isPresent();
    }
    private Long conferir(Long cliente) {
        contexto.validarAdministrador();
        if(cliente==null || !cliente.equals(contexto.getClienteAtualId())) throw new IllegalArgumentException("A empresa selecionada mudou. Atualize a tela.");
        return contexto.getUsuarioAutenticado().getId();
    }
    private void cancelar(ContaAzulAutoConfig c) {
        if(c.execucaoId!=null) runs.findByIdAndClienteId(c.execucaoId,c.clienteId).filter(r->!finalizada(r.status))
                .ifPresent(r->{r.status="CANCELADA";r.concluidoEm=clock.instant();r.mensagem="Execução encerrada por pausa ou alteração da autorização.";});
        c.dono=null;c.reservaAte=null;
    }
    private boolean finalizada(String status) {return Set.of("CONCLUIDA","CONCLUIDA_COM_PENDENCIAS","PARCIAL","CANCELADA").contains(status);}
    private boolean uuid(String id) {return id!=null && id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    private String limitar(String texto,int max) {return texto==null?null:texto.substring(0,Math.min(max,texto.length()));}
}
