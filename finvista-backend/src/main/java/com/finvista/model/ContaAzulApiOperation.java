package com.finvista.model;

import com.finvista.dto.ContaAzulApiSyncResponse.Resultado;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conta_azul_api_operacoes")
public class ContaAzulApiOperation {

    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Cliente cliente;
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;
    @Column(name = "parcela_id", nullable = false, length = 36)
    private String parcelaId;
    @Column(nullable = false, length = 24)
    private String acao;
    @Column(name = "lancamento_id", nullable = false)
    private Long lancamentoId;
    @Column(name = "concluido_em", nullable = false)
    private Instant concluidoEm;
    @Column(columnDefinition = "text")
    private String antes;
    @Column(nullable = false, columnDefinition = "text")
    private String depois;
    @Column(name = "origem_snapshot", nullable = false, columnDefinition = "text")
    private String origemSnapshot;

    protected ContaAzulApiOperation() {
    }

    public ContaAzulApiOperation(UUID id, Cliente cliente, Long usuarioId, String parcelaId, String acao,
            Long lancamentoId, Instant concluidoEm, String antes, String depois, String origemSnapshot) {
        this.id = id;
        this.cliente = cliente;
        this.usuarioId = usuarioId;
        this.parcelaId = parcelaId;
        this.acao = acao;
        this.lancamentoId = lancamentoId;
        this.concluidoEm = concluidoEm;
        this.antes = antes;
        this.depois = depois;
        this.origemSnapshot = origemSnapshot;
    }

    public UUID getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getAntes() {
        return antes;
    }

    public String getDepois() {
        return depois;
    }

    public Long getLancamentoId() {
        return lancamentoId;
    }

    public Resultado resultado() {
        return new Resultado(id, cliente.getId(), parcelaId, acao, lancamentoId, concluidoEm);
    }
}
