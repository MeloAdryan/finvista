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

@ Service 

    public class ContaAzulAutoState {

        public record Reserva(Long clienteId, UUID execucaoId, UUID dono) {}

        public record Cursor(LocalDate inicio, LocalDate fim, int etapa, int pagina, boolean completa) {

            public Cursor(LocalDate inicio, LocalDate fim, int etapa, int pagina) {
                this(inicio, fim, etapa, pagina, false);
            }
        }
        @org.springframework.beans.factory.annotation.Autowired
        private ContaAzulSourceCaptureRepository fontes;
        @org.springframework.beans.factory.annotation.Autowired
        private com.fasterxml.jackson.databind.ObjectMapper json;

        public static final class ReservaPerdida extends RuntimeException {
        }
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

        public ContaAzulAutoState(ContaAzulAutoConfigRepository configs, ContaAzulAutoRunRepository runs,
                ContaAzulAutoItemRepository itens, ClienteRepository clientes, UsuarioRepository usuarios,
                FinancialTransactionRepository transacoes, ContaAzulApiSyncService sync, ClienteContextService contexto,
                ContaAzulConfiguration integracao, Clock clock, @Value("${CONTA_AZUL_AUTO_ENABLED:false}") boolean habilitado) {
            this.configs = configs;
            this.runs = runs;
            this.itens = itens;
            this.clientes = clientes;
            this.usuarios = usuarios;
            this.transacoes = transacoes;
            this.sync = sync;
            this.contexto = contexto;
            this.integracao = integracao;
            this.clock = clock;
            this.habilitado = habilitado;
        }

        @Transactional(readOnly = true)
        public Status status(Long cliente, int pagina) {
            conferir(cliente);
            if (pagina < 1 || pagina > 100000) {
                throw new IllegalArgumentException("Página inválida.");
            }
            var c = configs.findById(cliente).orElse(null);
            LocalDate hoje = LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
            Config config = c == null ? new Config(cliente, false, hoje.minusDays(90), hoje.plusDays(90), 30, false, null, null)
                    : new Config(cliente, c.habilitada, Boolean.TRUE.equals(c.sincronizacaoCompleta) ? ContaAzulFullScope.INICIO : Boolean.TRUE.equals(c.periodoMovel) ? hoje.minusDays(90) : c.inicio, Boolean.TRUE.equals(c.sincronizacaoCompleta) ? ContaAzulFullScope.FIM : Boolean.TRUE.equals(c.periodoMovel) ? hoje.plusDays(90) : c.fim, c.intervaloMinutos, c.incluirNovas, c.proximaEm, c.alteradoEm, Boolean.TRUE.equals(c.periodoMovel), Boolean.TRUE.equals(c.sincronizacaoCompleta));
            var lista = runs.findTop10ByClienteIdOrderByCriadoEmDesc(cliente);
            List<Execucao> execucoes = lista.stream().map(r -> new Execucao(r.id, r.status, r.inicio, r.fim, r.criadoEm, r.concluidoEm,
                    r.incluidas, r.atualizadas, r.mantidas, r.pendentes, itens.countByExecucaoIdAndStatus(r.id, "EM_FILA"), r.mensagem)).toList();
            if (lista.isEmpty()) {
                return new Status(habilitado, config, execucoes, List.of(), 0, pagina);
            }
            var pendentes = itens.findByExecucaoIdAndClienteIdAndStatusOrderByIdAsc(lista.get(0).id, cliente, "CONFERIR", PageRequest.of(pagina - 1, 20));
            conferir(cliente);
            return new Status(habilitado, config, execucoes, pendentes.getContent().stream()
                    .map(i -> new Pendencia(i.parcelaId, i.tipo, i.descricao, i.mensagem, i.lancamentoId)).toList(), pendentes.getTotalElements(), pagina);
        }

        private Execucao resumo(ContaAzulAutoRun r) {
            return new Execucao(r.id, r.status, r.inicio, r.fim, r.criadoEm, r.concluidoEm,
                    r.incluidas, r.atualizadas, r.mantidas, r.pendentes, itens.countByExecucaoIdAndStatus(r.id, "EM_FILA"), r.mensagem);
        }

        @Transactional(readOnly = true)
        public Historico historico(Long cliente, int pagina) {
            conferir(cliente);
            if (pagina < 1 || pagina > 100000) {
                throw new IllegalArgumentException("Página inválida.");
            }
            var registros = runs.findByClienteIdOrderByCriadoEmDesc(cliente, PageRequest.of(pagina - 1, 20));
            var resultado = new Historico(registros.getContent().stream().map(this::resumo).toList(), registros.getTotalElements(), pagina);
            conferir(cliente);
            return resultado;
        }

        @Transactional(readOnly = true)
        public Revisao revisar(Long cliente, UUID execucao, int pagina) {
            conferir(cliente);
            if (pagina < 1 || pagina > 100000) {
                throw new IllegalArgumentException("Página inválida.");
            }
            var r = runs.findByIdAndClienteId(execucao, cliente).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Execução não encontrada nesta empresa."));
            var p = itens.findByExecucaoIdAndClienteIdAndStatusOrderByIdAsc(execucao, cliente, "CONFERIR", PageRequest.of(pagina - 1, 20));
            var resultado = new Revisao(resumo(r), p.getContent().stream().map(i -> new Pendencia(i.parcelaId, i.tipo, i.descricao, i.mensagem, i.lancamentoId)).toList(), p.getTotalElements(), pagina);
            conferir(cliente);
            return resultado;
        }

        @Transactional
        public void configurar(Configurar pedido) {
            Long usuario = conferir(pedido.clienteId());
            if (pedido.habilitada() && !habilitado) {
                throw new IllegalStateException("Habilite CONTA_AZUL_AUTO_ENABLED no backend antes de ativar a automação.");
            }
            LocalDate hoje = LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
            LocalDate inicio = pedido.sincronizacaoCompleta() ? ContaAzulFullScope.INICIO : pedido.periodoMovel() ? hoje.minusDays(90) : pedido.inicio();
            LocalDate fim = pedido.sincronizacaoCompleta() ? ContaAzulFullScope.FIM : pedido.periodoMovel() ? hoje.plusDays(90) : pedido.fim();
            if (inicio == null || fim == null || fim.isBefore(inicio)
                    || (!pedido.sincronizacaoCompleta() && ChronoUnit.DAYS.between(inicio, fim) > 365)
                    || !Set.of(30, 60, 360, 1440).contains(pedido.intervaloMinutos())) {
                throw new IllegalArgumentException("Escolha um período de até 366 dias e um intervalo disponível.");
            }
            if (pedido.habilitada() && (!pedido.confirmado() || (pedido.incluirNovas() && !pedido.confirmoHistorico()))) {
                throw new IllegalArgumentException("Confirme a autorização e a revisão do histórico antes de ativar inclusões automáticas.");
            }
            if (pedido.habilitada()) {
                integracao.exigirAtiva();
            }
            clientes.bloquearAtivoPorId(pedido.clienteId()).orElseThrow(() -> new IllegalArgumentException("Empresa indisponível."));
            var c = configs.bloquear(pedido.clienteId()).orElseGet(() -> {
                var novo = new ContaAzulAutoConfig();
                novo.clienteId = pedido.clienteId();
                return novo;
            });
            cancelar(c);
            c.habilitada = pedido.habilitada();
            c.incluirNovas = pedido.incluirNovas();
            c.inicio = inicio;
            c.fim = fim;
            c.periodoMovel = !pedido.sincronizacaoCompleta() && pedido.periodoMovel();
            c.sincronizacaoCompleta = pedido.sincronizacaoCompleta();
            c.intervaloMinutos = pedido.intervaloMinutos();
            c.autorizadoPor = usuario;
            c.revisao = UUID.randomUUID();
            c.alteradoEm = clock.instant();
            c.falhas = 0;
            c.proximaEm = c.habilitada ? clock.instant() : null;
            conferir(pedido.clienteId());
            configs.saveAndFlush(c);
        }

        @Transactional
        public void pausar(Long cliente) {
            conferir(cliente);
            clientes.bloquearAtivoPorId(cliente).orElseThrow(() -> new IllegalArgumentException("Empresa indisponível."));
            configs.bloquear(cliente).ifPresent(c -> {
                cancelar(c);
                c.habilitada = false;
                c.proximaEm = null;
                c.revisao = UUID.randomUUID();
                c.alteradoEm = clock.instant();
            });
        }

        @Transactional
        public void solicitar(Long cliente) {
            conferir(cliente);
            var c = configs.bloquear(cliente).orElseThrow(() -> new IllegalArgumentException("Configure a sincronização automática."));
            if (!c.habilitada) {
                throw new IllegalArgumentException("Ative a sincronização para agendar uma execução.");
            }
            c.proximaEm = clock.instant();
        }

        @Transactional(readOnly = true)
        public List<Long> disponiveis() {
            return configs.disponiveis(clock.instant(), PageRequest.of(0, 10));
        }

        @Transactional
        public Reserva assumir(Long cliente) {
            var c = configs.bloquear(cliente).orElse(null);
            Instant agora = clock.instant();
            if (c == null || !c.habilitada || c.proximaEm == null || c.proximaEm.isAfter(agora)
                    || (c.reservaAte != null && c.reservaAte.isAfter(agora))) {
                return null;
            }
            if (!autorizacaoAtiva(c)) {
                cancelar(c);
                c.habilitada = false;
                c.proximaEm = null;
                return null;
            }
            var r = c.execucaoId == null ? null : runs.findByIdAndClienteId(c.execucaoId, cliente).orElse(null);
            if (r == null || finalizada(r.status)) {
                r = new ContaAzulAutoRun();
                r.id = UUID.randomUUID();
                r.clienteId = cliente;
                r.autorizadoPor = c.autorizadoPor;
                r.revisao = c.revisao;
                LocalDate hoje = LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
                r.inicio = Boolean.TRUE.equals(c.sincronizacaoCompleta) ? ContaAzulFullScope.INICIO : Boolean.TRUE.equals(c.periodoMovel) ? hoje.minusDays(90) : c.inicio;
                r.fim = Boolean.TRUE.equals(c.sincronizacaoCompleta) ? ContaAzulFullScope.FIM : Boolean.TRUE.equals(c.periodoMovel) ? hoje.plusDays(90) : c.fim;
                r.incluirNovas = c.incluirNovas;
                r.sincronizacaoCompleta = Boolean.TRUE.equals(c.sincronizacaoCompleta);
                r.criadoEm = agora;
                r.status = "EM_ANDAMENTO";
                r.pagina = 1;
                runs.saveAndFlush(r);
                c.execucaoId = r.id;
                for (var t : transacoes.findByClienteIdAndOrigemOrderByIdAsc(cliente, "CONTA_AZUL_API")) {
                    if (Boolean.TRUE.equals(r.sincronizacaoCompleta) || (t.getDataVencimento() != null && !t.getDataVencimento().isBefore(r.inicio) && !t.getDataVencimento().isAfter(r.fim))) {
                        if (uuid(t.getDocumentoReferencia()) && ("RECEITA".equals(t.getTipo()) || "DESPESA".equals(t.getTipo()))) {
                            enfileirar(r, t.getDocumentoReferencia(), t.getTipo(), t.getDescricao(), false);
                        } else {
                            r.listagemMudou = true;
                            r.mensagem = "Há registros locais com identidade API inválida. Confira os vínculos.";
                        }
                    }
                }
            }
            r.status = "EM_ANDAMENTO";
            if (!r.listagemMudou) {
                r.mensagem = null;

            }
            c.dono = UUID.randomUUID();
            c.reservaAte = agora.plusSeconds(120);
            return new Reserva(cliente, r.id, c.dono);
        }

        @Transactional
        public ContaAzulAutoItem proximo(Reserva reserva) {
            validar(reserva);
            return itens.findFirstByExecucaoIdAndStatusOrderByIdAsc(reserva.execucaoId(), "EM_FILA").orElse(null);
        }

        @Transactional
        public Cursor cursor(Reserva reserva) {
            var c = validar(reserva);
            var r = execucao(c);
            if (Boolean.TRUE.equals(r.sincronizacaoCompleta) && r.etapa >= 2 && r.etapa <= 3) {
                prepararEscopoMovimentos(r);
                if (r.etapa == 4) return new Cursor(r.inicio, r.fim, r.etapa, r.pagina, true);
                var janela = janelaMovimentos(r);
                return new Cursor(janela.inicio(), janela.fim(), r.etapa, r.pagina, true);
            }
            return new Cursor(r.inicio, r.fim, r.etapa, r.pagina, Boolean.TRUE.equals(r.sincronizacaoCompleta));
        }

        @Transactional
        public void pagina(Reserva reserva, ContaAzulFinancialClient.Pagina pagina) {
            var c = validar(reserva);
            var r = execucao(c);
            if (r.etapa > 1) {
                throw new ReservaPerdida();
            }
            if (pagina == null || pagina.itens() == null || pagina.totalItens() == null || pagina.totalItens() < 0
                    || pagina.totalItens() > 100000 || pagina.itens().size() > 100 || r.pagina > 1000) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Listagem acima do limite ou inválida. Reduza o período e revise a consulta.");
            }
            if (r.totalEsperado == null) {
                r.totalEsperado = pagina.totalItens();
            } else if (!r.totalEsperado.equals(pagina.totalItens())) {
                r.listagemMudou = true;
            }
            int restantes = Math.max(0, pagina.totalItens() - (r.pagina - 1) * 100);
            if (pagina.itens().size() != Math.min(100, restantes)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A paginação retornou quantidade inconsistente. A execução ficou incompleta.");
            }
            Set<String> ids = new HashSet<>();
            for (var p : pagina.itens()) {
                if (p == null || !uuid(p.id()) || !ids.add(p.id().toLowerCase(Locale.ROOT))) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "A listagem contém identidade de parcela inválida ou repetida.");
                }
                enfileirar(r, p.id(), r.etapa == 0 ? "RECEITA" : "DESPESA", p.descricao(), true);
            }
            if ((long) r.pagina * 100 >= pagina.totalItens()) {
                r.etapa++;
                r.pagina = 1;
                r.totalEsperado = null;
            } else {
                r.pagina++;
            }
        }

        /**
         * Each source page is committed with its cursor; retrying does not
         * append it twice.
         */
        static record JanelaMovimentos(LocalDate inicio, LocalDate fim, boolean emJanelas) {}

        private static LocalDate inicioMovimentos(ContaAzulAutoRun r) {
            return r.movimentosEscopoInicio == null ? r.inicio : r.movimentosEscopoInicio;
        }
        private static LocalDate fimMovimentos(ContaAzulAutoRun r) {
            return r.movimentosEscopoFim == null ? r.fim : r.movimentosEscopoFim;
        }

        /** Adopt the narrower movement policy only between completed pages/windows. */
        private void prepararEscopoMovimentos(ContaAzulAutoRun r) {
            if (r.movimentoInicio != null && (r.movimentoInicio.isBefore(r.inicio) || r.movimentoInicio.isAfter(r.fim)))
                throw new IllegalStateException("Cursor de movimentações fora do alcance autorizado.");
            if ((r.movimentosEscopoInicio == null) != (r.movimentosEscopoFim == null))
                throw new IllegalStateException("Escopo de movimentações incompleto.");
            boolean primeiraPaginaLivre = r.pagina == 1 && r.totalEsperado == null;
            if (r.movimentosEscopoInicio == null) {
                if (!primeiraPaginaLivre) return; // Finish the already committed pagination unchanged.
                LocalDate hoje = LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
                r.movimentosEscopoInicio = r.inicio.isAfter(ContaAzulFullScope.INICIO) ? r.inicio : ContaAzulFullScope.INICIO;
                r.movimentosEscopoFim = r.fim.isBefore(hoje) ? r.fim : hoje;
            }
            if (!primeiraPaginaLivre) return;
            while (r.etapa >= 2 && r.etapa <= 3) {
                if (r.movimentoInicio == null || r.movimentoInicio.isBefore(r.movimentosEscopoInicio))
                    r.movimentoInicio = r.movimentosEscopoInicio;
                if (!r.movimentoInicio.isAfter(r.movimentosEscopoFim)) return;
                // Previously completed future/older scopes are preserved, never deleted or replayed.
                r.etapa++;
                r.movimentoInicio = null;
                r.pagina = 1;
                r.totalEsperado = null;
            }
        }

        /** Pure window calculation; legacy in-flight pages retain their original boundaries. */
        static JanelaMovimentos janelaMovimentos(ContaAzulAutoRun r) {
            if (r.inicio == null || r.fim == null || r.fim.isBefore(r.inicio))
                throw new IllegalStateException("Alcance da execução inválido.");
            if (r.movimentosEscopoInicio == null && r.movimentoInicio == null && (r.pagina > 1 || r.totalEsperado != null))
                return new JanelaMovimentos(r.inicio, r.fim, false);
            LocalDate inicio = r.movimentoInicio == null ? inicioMovimentos(r) : r.movimentoInicio;
            if (inicio.isBefore(inicioMovimentos(r)) || inicio.isAfter(fimMovimentos(r)))
                throw new IllegalStateException("Cursor de movimentações fora do alcance autorizado.");
            LocalDate fim = inicio.plusDays(29);
            if (fim.isAfter(fimMovimentos(r))) fim = fimMovimentos(r);
            return new JanelaMovimentos(inicio, fim, true);
        }

        @Transactional
        public void paginaMovimentos(Reserva reserva, ContaAzulMovementClient.Pagina pagina) {
            var c = validar(reserva);
            var r = execucao(c);
            if (!Boolean.TRUE.equals(r.sincronizacaoCompleta) || r.etapa < 2 || r.etapa > 3) {
                throw new ReservaPerdida();
            }
            if (pagina == null || pagina.itens() == null || pagina.fonte() == null || pagina.total() < 0 || pagina.total() > 100000
                    || r.pagina < 1 || r.pagina > 1000 || pagina.itens().size() > 100) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Fonte acima do limite de conferência ou inválida. Cobertura não confirmada.");
            }
            int etapaConsultada = r.etapa;
            prepararEscopoMovimentos(r);
            if (r.etapa != etapaConsultada) throw new ReservaPerdida();
            // Evaluate before setting totalEsperado, so a first page can select window mode.
            var janela = janelaMovimentos(r);
            if (r.totalEsperado == null) {
                r.totalEsperado = pagina.total();
            } else if (!r.totalEsperado.equals(pagina.total())) {
                r.listagemMudou = true;
            }
            int remaining = Math.max(0, pagina.total() - (r.pagina - 1) * 100);
            if (pagina.itens().size() != Math.min(100, remaining)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Fonte paginada incompleta.");
            }
            String tipo = r.etapa == 2 ? "TRANSFERENCIA" : "SALDO_INICIAL";
            String chave = janela.emJanelas()
                    ? "periodo:" + janela.inicio() + ":" + janela.fim() + ":pagina:" + r.pagina
                    : "pagina:" + r.pagina;
            arquivar(reserva, tipo, chave, pagina.fonte());
            if (!r.listagemMudou) {
                r.mensagem = "Coletando " + (r.etapa == 2 ? "transferências" : "saldos iniciais")
                        + ": " + janela.inicio() + " a " + janela.fim() + ", página " + r.pagina
                        + ". Faixa de movimentações: " + inicioMovimentos(r) + " a " + fimMovimentos(r)
                        + ". Parcelas futuras mantidas na listagem; a coleta ainda não terminou.";
            }
            if (janela.emJanelas()) r.movimentoInicio = janela.inicio();
            if ((long) r.pagina * 100 >= pagina.total()) {
                if (janela.emJanelas() && janela.fim().isBefore(fimMovimentos(r))) {
                    r.movimentoInicio = janela.fim().plusDays(1);
                } else {
                    r.etapa++;
                    r.movimentoInicio = null;
                }
                r.pagina = 1;
                r.totalEsperado = null;
            } else {
                r.pagina++;
            }
        }

        @Transactional
        public void arquivar(Reserva reserva, String tipo, String chave, com.fasterxml.jackson.databind.JsonNode conteudo) {
            var c = validar(reserva);
            var r = execucao(c);
            if (!Boolean.TRUE.equals(r.sincronizacaoCompleta)) {
                return;
            }
            if (!Set.of("PARCELA", "TRANSFERENCIA", "SALDO_INICIAL").contains(tipo) || chave == null || chave.length() > 160 || conteudo == null) {
                throw new IllegalArgumentException("Fonte inválida.");
            }
            var row = new ContaAzulSourceCapture();
            row.id = UUID.nameUUIDFromBytes((r.id + ":" + tipo + ":" + chave).getBytes(StandardCharsets.UTF_8));
            row.clienteId = r.clienteId;
            row.execucaoId = r.id;
            row.tipo = tipo;
            row.chave = chave;
            row.capturadoEm = clock.instant();
            try {
                row.conteudo = json.writeValueAsString(conteudo);
            } catch (Exception e) {
                throw new IllegalStateException("Não foi possível preservar a fonte.");
            }
            fontes.saveAndFlush(row);
        }

        public record Fonte(String tipo, String chave, Instant capturadoEm, com.fasterxml.jackson.databind.JsonNode dados) {}

        public record Fontes(Long clienteId, UUID execucaoId, String status, List<Fonte> registros, long total, int pagina) {}

        @Transactional(readOnly = true)
        public Fontes fontes(Long cliente, UUID execucao, String tipo, int pagina) {
            conferir(cliente);
            if (pagina < 1 || pagina > 100000 || !Set.of("PARCELA", "TRANSFERENCIA", "SALDO_INICIAL").contains(tipo)) {
                throw new IllegalArgumentException("Consulta inválida.");
            }
            var r = runs.findByIdAndClienteId(execucao, cliente).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Execução não encontrada nesta empresa."));
            var result = fontes.findByClienteIdAndExecucaoIdAndTipoOrderByChaveAsc(cliente, execucao, tipo, PageRequest.of(pagina - 1, 5));
            var rows = result.getContent().stream().map(row -> {
                try {
                    return new Fonte(row.tipo, row.chave, row.capturadoEm, json.readTree(row.conteudo));
                } catch (Exception e) {
                    throw new IllegalStateException("Fonte armazenada inválida.");
                }
            }).toList();
            conferir(cliente);
            return new Fontes(cliente, execucao, r.status, rows, result.getTotalElements(), pagina);
        }

        @Transactional
        public void aplicar(Reserva reserva, UUID itemId, ContaAzulParcelDetail parcela) {
            Cliente cliente = clientes.bloquearAtivoPorId(reserva.clienteId()).orElseThrow(ReservaPerdida::new);
            var c = validar(reserva);
            var r = execucao(c);
            var i = item(r, itemId);
            if (!"EM_FILA".equals(i.status)) {
                return;
            }
            if (parcela == null || !i.parcelaId.equalsIgnoreCase(parcela.id()) || parcela.evento() == null || !i.tipo.equals(parcela.evento().tipo())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A parcela ou o tipo retornado difere da fila desta empresa.");
            }
            var resultado = sync.aplicarAutomatico(cliente, r.autorizadoPor, i.id, parcela, r.incluirNovas);
            i.status = resultado.acao();
            i.lancamentoId = resultado.lancamentoId();
            i.concluidoEm = clock.instant();
            i.mensagem = null;
            switch (i.status) {
                case "INCLUIR" ->
                    r.incluidas++;
                case "ATUALIZAR" ->
                    r.atualizadas++;
                default ->
                    r.mantidas++;
            }
        }

        @Transactional
        public void pendencia(Reserva reserva, UUID itemId, String mensagem) {
            var c = validar(reserva);
            var r = execucao(c);
            var i = item(r, itemId);
            if (!"EM_FILA".equals(i.status)) {
                return;
            }
            i.status = "CONFERIR";
            i.mensagem = limitar(mensagem, 500);
            i.concluidoEm = clock.instant();
            r.pendentes++;
        }

        @Transactional
        public void devolver(Reserva reserva, boolean finalizar) {
            var c = validar(reserva);
            var r = execucao(c);
            if (finalizar) {
                if (r.etapa != (Boolean.TRUE.equals(r.sincronizacaoCompleta) ? 4 : 2) || itens.countByExecucaoIdAndStatus(r.id, "EM_FILA") != 0) {
                    throw new ReservaPerdida();
                }
                r.status = r.listagemMudou ? "PARCIAL" : r.pendentes > 0 ? "CONCLUIDA_COM_PENDENCIAS" : "CONCLUIDA";
                if (r.listagemMudou && r.mensagem == null) {
                    r.mensagem = "A listagem mudou durante a consulta. A cobertura não foi confirmada; outra varredura será feita no próximo ciclo.";
                }
                if (!r.listagemMudou) {
                    r.mensagem = null;
                }
                r.concluidoEm = clock.instant();
                c.proximaEm = clock.instant().plusSeconds(c.intervaloMinutos * 60L);
                c.falhas = 0;
            } else {
                c.proximaEm = clock.instant();
            }
            c.dono = null;
            c.reservaAte = null;
        }

        @Transactional
        public void falhar(Reserva reserva, String mensagem, boolean parcial) {
            var c = validar(reserva);
            var r = execucao(c);
            r.mensagem = limitar(mensagem, 500);
            c.falhas = Math.min(5, c.falhas + 1);
            c.proximaEm = clock.instant().plusSeconds(Math.min(60, 5L * (1L << (c.falhas - 1))) * 60);
            r.status = parcial ? "PARCIAL" : "AGUARDANDO_REPETICAO";
            if (parcial) {
                r.concluidoEm = clock.instant();
            }
            c.dono = null;
            c.reservaAte = null;
        }

        private ContaAzulAutoConfig validar(Reserva reserva) {
            var c = configs.bloquear(reserva.clienteId()).orElseThrow(ReservaPerdida::new);
            if (!c.habilitada || !Objects.equals(c.execucaoId, reserva.execucaoId()) || !Objects.equals(c.dono, reserva.dono())
                    || c.reservaAte == null || !c.reservaAte.isAfter(clock.instant()) || !autorizacaoAtiva(c)) {
                throw new ReservaPerdida();
            }
            var r = execucao(c);
            if (!Objects.equals(c.revisao, r.revisao) || finalizada(r.status)) {
                throw new ReservaPerdida();
            }
            c.reservaAte = clock.instant().plusSeconds(120);
            return c;
        }

        private ContaAzulAutoRun execucao(ContaAzulAutoConfig c) {
            return runs.findByIdAndClienteId(c.execucaoId, c.clienteId).orElseThrow(ReservaPerdida::new);
        }

        private ContaAzulAutoItem item(ContaAzulAutoRun r, UUID id) {
            var i = itens.findById(id).orElseThrow(ReservaPerdida::new);
            if (!Objects.equals(i.execucaoId, r.id) || !Objects.equals(i.clienteId, r.clienteId)) {
                throw new ReservaPerdida();

            }
            return i;
        }

        private void enfileirar(ContaAzulAutoRun r, String id, String tipo, String descricao, boolean listada) {
            id = id.toLowerCase(Locale.ROOT);
            UUID chave = UUID.nameUUIDFromBytes(("conta-azul-auto:" + r.id + ":" + id).getBytes(StandardCharsets.UTF_8));
            var existente = itens.findById(chave).orElse(null);
            if (existente != null) {
                if (listada && existente.listada) {
                    r.listagemMudou = true;
                }
                if (listada && !tipo.equals(existente.tipo)) {
                    r.listagemMudou = true;
                }
                existente.listada = existente.listada || listada;
                return;
            }
            var i = new ContaAzulAutoItem();
            i.id = chave;
            i.execucaoId = r.id;
            i.clienteId = r.clienteId;
            i.parcelaId = id;
            i.tipo = tipo;
            i.descricao = limitar(descricao, 255);
            i.status = "EM_FILA";
            i.listada = listada;
            itens.save(i);
        }

        private boolean autorizacaoAtiva(ContaAzulAutoConfig c) {
            return clientes.findByIdAndAtivoTrue(c.clienteId).isPresent() && c.autorizadoPor != null
                    && usuarios.findById(c.autorizadoPor).filter(u -> Boolean.TRUE.equals(u.getAtivo()) && "ADMIN".equals(u.getPerfil())).isPresent();
        }

        private Long conferir(Long cliente) {
            contexto.validarAdministrador();
            if (cliente == null || !cliente.equals(contexto.getClienteAtualId())) {
                throw new IllegalArgumentException("A empresa selecionada mudou. Atualize a tela.");
            }
            return contexto.getUsuarioAutenticado().getId();
        }

        private void cancelar(ContaAzulAutoConfig c) {
            if (c.execucaoId != null) {
                runs.findByIdAndClienteId(c.execucaoId, c.clienteId).filter(r -> !finalizada(r.status))
                        .ifPresent(r -> {
                            r.status = "CANCELADA";
                            r.concluidoEm = clock.instant();
                            r.mensagem = "Execução encerrada por pausa ou alteração da autorização.";
                        });
            }
            c.dono = null;
            c.reservaAte = null;
        }

        private boolean finalizada(String status) {
            return Set.of("CONCLUIDA", "CONCLUIDA_COM_PENDENCIAS", "PARCIAL", "CANCELADA").contains(status);
        }

        private boolean uuid(String id) {
            return id != null && id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        private String limitar(String texto, int max) {
            return texto == null ? null : texto.substring(0, Math.min(max, texto.length()));
        }
    }
     
