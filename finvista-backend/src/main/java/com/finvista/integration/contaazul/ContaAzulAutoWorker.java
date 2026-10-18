package com.finvista.integration.contaazul;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.finvista.integration.contaazul.ContaAzulAutoState.*;

@Service
public class ContaAzulAutoWorker {

    private final ContaAzulAutoState estado;
    private final ContaAzulAutoAccess acesso;
    private final ContaAzulFinancialClient financeiro;
    private final ContaAzulParcelDetailClient detalhes;

    public ContaAzulAutoWorker(ContaAzulAutoState estado, ContaAzulAutoAccess acesso,
            ContaAzulFinancialClient financeiro, ContaAzulParcelDetailClient detalhes) {
        this.estado = estado;
        this.acesso = acesso;
        this.financeiro = financeiro;
        this.detalhes = detalhes;
    }

    public void executar(Long cliente) {
        Reserva reserva = estado.assumir(cliente);
        if (reserva == null) {
            return;
        }
        try {
            for (int n = 0; n < 20; n++) {
                var item = estado.proximo(reserva);
                if (item != null) {
                    try {
                        limitar();
                        var parcela = detalhes.consultar(acesso.acesso(cliente), item.parcelaId);
                        estado.aplicar(reserva, item.id, parcela);
                    } catch (ResponseStatusException e) {
                        int status = e.getStatusCode().value();
                        if (status == 400 || status == 404 || status == 409) {
                            estado.pendencia(reserva, item.id,
                                    e.getReason() == null ? "A parcela exige conferência individual." : e.getReason());
                        } else {
                            throw e;
                        }
                    }
                } else {
                    var cursor = estado.cursor(reserva);
                    if (cursor.etapa() == 2) {
                        estado.devolver(reserva, true);
                        return;
                    }
                    limitar();
                    var tipo = cursor.etapa() == 0 ? ContaAzulFinancialClient.Tipo.RECEITA : ContaAzulFinancialClient.Tipo.DESPESA;
                    var pagina = financeiro.consultar(acesso.acesso(cliente), tipo, cursor.inicio(), cursor.fim(), cursor.pagina());
                    estado.pagina(reserva, pagina);
                }
            }
            estado.devolver(reserva, false);
        } catch (ReservaPerdida e) {
            // Outra instância, pausa ou alteração de configuração assumiu a execução.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            registrarFalha(reserva, "Execução interrompida. A fila será retomada automaticamente.", false);
        } catch (ResponseStatusException e) {
            boolean parcial = e.getStatusCode().value() == 409;
            registrarFalha(reserva, e.getStatusCode().value() == 429 ? "Limite de consultas atingido. Nova tentativa com espera progressiva."
                    : parcial ? "A listagem não pôde ser conferida integralmente. Reduza o período ou consulte os dados antes de repetir."
                            : "A Conta Azul não concluiu a consulta. Confira a conexão; a execução será retomada com espera progressiva.", parcial);
        } catch (Exception e) {
            registrarFalha(reserva, "Não foi possível concluir esta etapa. Confira a autorização e os registros; a fila será retomada com espera progressiva.", false);
        }
    }

    private void registrarFalha(Reserva reserva, String mensagem, boolean parcial) {
        try {
            estado.falhar(reserva, mensagem, parcial);
        } catch (ReservaPerdida ignored) {
        }
    }

    protected void limitar() throws InterruptedException {
        Thread.sleep(600);
    }
}
