package com.finvista.integration.contaazul;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.finvista.integration.contaazul.ContaAzulAutoState.*;

@ Service

    public class ContaAzulAutoWorker {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ContaAzulAutoWorker.class);

        private final ContaAzulAutoState estado;
        private final ContaAzulAutoAccess acesso;
        private final ContaAzulFinancialClient financeiro;
        private final ContaAzulParcelDetailClient detalhes;

        private final ContaAzulMovementClient movimentos;

        @org.springframework.beans.factory.annotation.Autowired
        public ContaAzulAutoWorker(ContaAzulAutoState estado, ContaAzulAutoAccess acesso,
                ContaAzulFinancialClient financeiro, ContaAzulParcelDetailClient detalhes, ContaAzulMovementClient movimentos) {
            this.estado = estado;
            this.acesso = acesso;
            this.financeiro = financeiro;
            this.detalhes = detalhes;
            this.movimentos = movimentos;
        }

        public ContaAzulAutoWorker(ContaAzulAutoState estado, ContaAzulAutoAccess acesso,
                ContaAzulFinancialClient financeiro, ContaAzulParcelDetailClient detalhes) {
            this.estado = estado;
            this.acesso = acesso;
            this.financeiro = financeiro;
            this.detalhes = detalhes;
            this.movimentos = null;
        }

        public void executar(Long cliente) {
            Reserva reserva = estado.assumir(cliente);
            if (reserva == null) {
                return;
            }
            String etapa = "OBTER_ITEM";
            try {
                for (int n = 0; n < 20; n++) {
                    etapa = "OBTER_ITEM";
                    var item = estado.proximo(reserva);
                    if (item != null) {
                        try {
                            if (estado.reutilizarResumo(reserva, item.id)) continue;
                            limitar();
                            etapa = "OBTER_CURSOR";
                            if (estado.cursor(reserva).completa()) {
                                etapa = "ACESSO";
                                etapa = "CONSULTAR_PARCELA_COMPLETA";
                                var consulta = consultarAutorizado(cliente, token -> detalhes.consultarCompleto(token, item.parcelaId));
                                etapa = "ARQUIVAR_PARCELA";
                                estado.arquivar(reserva, "PARCELA", item.parcelaId, consulta.fonte());
                                etapa = "APLICAR_PARCELA";
                                estado.aplicar(reserva, item.id, consulta.parcela());
                            } else {
                                etapa = "ACESSO";
                                etapa = "CONSULTAR_PARCELA";
                                var parcela = consultarAutorizado(cliente, token -> detalhes.consultar(token, item.parcelaId));
                                etapa = "APLICAR_PARCELA";
                                estado.aplicar(reserva, item.id, parcela);
                            }
                        } catch (ResponseStatusException e) {
                            int status = e.getStatusCode().value();
                            if (status == 400 || status == 404 || status == 409) {
                                estado.pendencia(reserva, item.id,
                                        e.getReason() == null ? "A parcela exige conferência individual." : e.getReason());
                            } else if (("CONSULTAR_PARCELA".equals(etapa) || "CONSULTAR_PARCELA_COMPLETA".equals(etapa))
                                    && status >= 500 && !ContaAzulAutoDiagnostic.http(e, 401) && !ContaAzulAutoDiagnostic.http(e, 403)) {
                                if (!estado.falhaItem(reserva, item.id)) throw e;
                            } else {
                                throw e;
                            }
                        }
                    } else {
                        etapa = "OBTER_CURSOR";
                        var cursor = estado.cursor(reserva);
                        if (cursor.etapa() == (cursor.completa() ? 4 : 2)) {
                            etapa = "CONCLUIR_EXECUCAO";
                            estado.devolver(reserva, true);
                            return;
                        }
                        limitar();
                        if (cursor.completa() && cursor.etapa() >= 2) {
                            if (movimentos == null) {
                                throw new IllegalStateException("Cliente de movimentações indisponível.");
                            }
                            etapa = "ACESSO";
                            etapa = cursor.etapa() == 2 ? "CONSULTAR_TRANSFERENCIAS" : "CONSULTAR_SALDOS_INICIAIS";
                            var pagina = consultarAutorizado(cliente, token -> movimentos.consultar(token, cursor.etapa() == 2 ? "TRANSFERENCIA" : "SALDO_INICIAL", cursor.inicio(), cursor.fim(), cursor.pagina()));
                            etapa = "GRAVAR_MOVIMENTOS";
                            estado.paginaMovimentos(reserva, pagina);
                            continue;
                        }
                        var tipo = cursor.etapa() == 0 ? ContaAzulFinancialClient.Tipo.RECEITA : ContaAzulFinancialClient.Tipo.DESPESA;
                        etapa = "ACESSO";
                        etapa = cursor.etapa() == 0 ? "LISTAR_RECEITAS" : "LISTAR_DESPESAS";
                        var pagina = consultarAutorizado(cliente, token -> cursor.alteracoesDesde() == null
                                ? financeiro.consultar(token, tipo, cursor.inicio(), cursor.fim(), cursor.pagina())
                                : financeiro.consultarAlteradas(token, tipo, cursor.inicio(), cursor.fim(), cursor.pagina(), cursor.alteracoesDesde(), cursor.alteracoesAte()));
                        etapa = "GRAVAR_PAGINA";
                        estado.pagina(reserva, pagina);
                    }
                }
                etapa = "DEVOLVER_RESERVA";
                estado.devolver(reserva, false);
            } catch (ContaAzulReauthorizationRequired e) {
                // Connection state and pause were committed in their own transaction.
            } catch (ReservaPerdida e) {
                // Outra instância, pausa ou alteração de configuração assumiu a execução.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                registrarFalha(reserva, "Execução interrompida. A fila será retomada automaticamente.", false);
            } catch (ResponseStatusException e) {
                String referencia = diagnosticar(reserva, etapa, e);
                if (("LISTAR_RECEITAS".equals(etapa) || "LISTAR_DESPESAS".equals(etapa))
                        && ContaAzulAutoDiagnostic.limiteDePeriodo365(e)
                        && estado.dividirPeriodoParcelas(reserva)) {
                    return;
                }
                if (e instanceof ContaAzulFinancialClient.PaginaInstavel && estado.repetirPaginaInstavel(reserva)) return;
                boolean parcial = e.getStatusCode().value() == 409;
                registrarFalha(reserva, (e.getStatusCode().value() == 429 ? "Limite de consultas atingido. Nova tentativa com espera progressiva."
                        : parcial ? "A listagem não pôde ser conferida integralmente. Reduza o período ou consulte os dados antes de repetir."
                                : "A Conta Azul não concluiu a consulta. Confira a conexão; a execução será retomada com espera progressiva.") + " Referência: " + referencia + ".", parcial, ContaAzulAutoDiagnostic.retryAfter(e));
            } catch (Exception e) {
                String referencia = diagnosticar(reserva, etapa, e);
                registrarFalha(reserva, "Não foi possível concluir esta etapa. Confira a autorização e os registros; a fila será retomada com espera progressiva. Referência: " + referencia + ".", false, ContaAzulAutoDiagnostic.retryAfter(e));
            }
        }

        private String diagnosticar(Reserva reserva, String etapa, Exception erro) {
            String referencia = java.util.UUID.randomUUID().toString();
            log.warn("Automacao Conta Azul: referencia={} cliente={} execucao={} etapa={} causas={}",
                    referencia, reserva.clienteId(), reserva.execucaoId(), etapa,
                    ContaAzulAutoDiagnostic.resumir(erro));
            return referencia;
        }

        private void registrarFalha(Reserva reserva, String mensagem, boolean parcial) {
            try {
                estado.falhar(reserva, mensagem, parcial);
            } catch (ReservaPerdida ignored) {
            }
        }

        protected void limitar() throws InterruptedException {
            // Network pacing is shared at the HTTP boundary; cached items incur no fixed sleep.
        }

        private <T> T consultarAutorizado(Long cliente, java.util.function.Function<String, T> consulta) {
            String token = acesso.acesso(cliente);
            try {
                return consulta.apply(token);
            } catch (ResponseStatusException erro) {
                if (!ContaAzulAutoDiagnostic.http(erro, 401)) throw erro;
                String renovado = acesso.renovarAposRecusa(cliente, token);
                try {
                    return consulta.apply(renovado);
                } catch (ResponseStatusException segunda) {
                    if (ContaAzulAutoDiagnostic.http(segunda, 401)) acesso.invalidar(cliente);
                    throw segunda;
                }
            }
        }


        private void registrarFalha(Reserva reserva, String mensagem, boolean parcial, java.time.Instant retryAfter) {
            if (retryAfter == null) { registrarFalha(reserva, mensagem, parcial); return; }
            try { estado.falhar(reserva, mensagem, parcial, retryAfter); }
            catch (ReservaPerdida ignored) { }
        }

}
