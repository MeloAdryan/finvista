package com.finvista.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.time.*;
import java.util.*;

public final class ContaAzulAutoResponse {

    private ContaAzulAutoResponse() {
    }

    public record Configurar(Long clienteId, boolean habilitada, LocalDate inicio, LocalDate fim, int intervaloMinutos,
            boolean incluirNovas, boolean confirmado, boolean confirmoHistorico, boolean periodoMovel) {

        public Configurar(Long clienteId, boolean habilitada, LocalDate inicio, LocalDate fim, int intervaloMinutos,
                boolean incluirNovas, boolean confirmado, boolean confirmoHistorico) {
            this(clienteId, habilitada, inicio, fim, intervaloMinutos, incluirNovas, confirmado, confirmoHistorico, false);
        }

        @JsonAnySetter
        public void rejeitar(String nome, Object valor) {
            throw new IllegalArgumentException("Campo não permitido: " + nome);
        }
    }

    public record Pedido(Long clienteId) {

        @JsonAnySetter
        public void rejeitar(String nome, Object valor) {
            throw new IllegalArgumentException("Campo não permitido: " + nome);
        }
    }

    public record Config(Long clienteId, boolean habilitada, LocalDate inicio, LocalDate fim, int intervaloMinutos,
            boolean incluirNovas, Instant proximaEm, Instant alteradoEm, boolean periodoMovel) {

        public Config(Long clienteId, boolean habilitada, LocalDate inicio, LocalDate fim, int intervaloMinutos,
                boolean incluirNovas, Instant proximaEm, Instant alteradoEm) {
            this(clienteId, habilitada, inicio, fim, intervaloMinutos, incluirNovas, proximaEm, alteradoEm, false);
        }
    }

    public record Execucao(UUID id, String status, LocalDate inicio, LocalDate fim, Instant criadoEm, Instant concluidoEm,
            int incluidas, int atualizadas, int mantidas, int pendentes, long emFila, String mensagem) {}

    public record Pendencia(String parcelaId, String tipo, String descricao, String mensagem, Long lancamentoId) {}

    public record Historico(List<Execucao> execucoes, long total, int pagina) {}

    public record Revisao(Execucao execucao, List<Pendencia> pendencias, long total, int pagina) {}

    public record Status(boolean processadorHabilitado, Config configuracao, List<Execucao> execucoes, List<Pendencia> pendencias, long totalPendencias, int paginaPendencias) {}
}
