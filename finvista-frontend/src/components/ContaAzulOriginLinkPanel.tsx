import { useState } from "react";
import type { PlanoApi } from "../services/contaAzulApiPlanService";
import ContaAzulApiSyncPanel from "./ContaAzulApiSyncPanel";

export default function ContaAzulOriginLinkPanel({
  plano,
  parcelaId,
  aoConcluir,
}: {
  plano: PlanoApi;
  parcelaId: string;
  aoConcluir: () => void;
}) {
  const [escolhido, setEscolhido] = useState("");
  const [concluido, setConcluido] = useState(false);
  const candidatos = plano.correspondencias.filter(
    (c) => c.origem === "CONTA_AZUL_EXCEL",
  );
  return (
    <section className="ca-api-sync" aria-label="Conciliação Excel e API">
      <h4>Conferir lançamento já importado por Excel</h4>
      <p>
        Escolha explicitamente o lançamento que representa esta parcela. O
        vínculo mantém o registro existente e não cria outra receita ou despesa.
        A prévia verifica descrição, tipo, valor, competência e vencimento;
        divergências ou baixas impedem o vínculo nesta entrega.
      </p>
      {candidatos.length > 0 ? (
        <label>
          Lançamento Excel para conferir
          <select
            value={escolhido}
            disabled={concluido}
            onChange={(e) => setEscolhido(e.target.value)}
          >
            <option value="">Selecione um lançamento</option>
            {candidatos.map((c) => (
              <option key={c.id} value={c.id}>
                #{c.id} · {c.descricao} ·{" "}
                {c.valor?.toLocaleString("pt-BR", {
                  style: "currency",
                  currency: "BRL",
                })}{" "}
                · competência {c.competencia?.split("-").reverse().join("/")} ·
                vencimento {c.vencimento?.split("-").reverse().join("/")}
              </option>
            ))}
          </select>
        </label>
      ) : (
        <p>
          Nenhum candidato Excel nesta página. Correspondências manuais exigem
          revisão específica; não serão convertidas automaticamente.
        </p>
      )}
      {plano.totalCorrespondencias > plano.correspondencias.length && (
        <p>
          A lista está limitada às primeiras correspondências. Revise os
          registros antes de escolher; não há seleção automática.
        </p>
      )}
      {escolhido && (
        <ContaAzulApiSyncPanel
          key={escolhido}
          clienteId={plano.clienteId}
          parcelaId={parcelaId}
          vincularId={Number(escolhido)}
          aoConcluir={() => {
            setConcluido(true);
            aoConcluir();
          }}
        />
      )}
      {concluido && (
        <p>
          Prepare outro plano para consultar o vínculo pela API e, se
          necessário, revisar uma atualização financeira.
        </p>
      )}
    </section>
  );
}
