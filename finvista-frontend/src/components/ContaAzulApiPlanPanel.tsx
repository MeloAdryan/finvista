import ContaAzulApiSyncPanel from "./ContaAzulApiSyncPanel";
import ContaAzulOriginLinkPanel from "./ContaAzulOriginLinkPanel";
import { useEffect, useRef, useState } from "react";
import {
  prepararPlanoApi,
  type PlanoApi,
} from "../services/contaAzulApiPlanService";
import type { RespostaDetalhe } from "../services/contaAzulParcelDetailService";

const rotulo: Record<PlanoApi["situacao"], string> = {
  BLOQUEADO: "Conferência necessária",
  REGISTRO_IDENTIFICADO: "Registro identificado pela origem e ID externo",
  CORRESPONDENCIA_POSSIVEL: "Possível correspondência — vínculo não confirmado",
  SEM_CORRESPONDENCIA:
    "Nenhuma correspondência encontrada pelas regras do plano",
};
const dinheiro = (v: number | null) =>
  v == null
    ? "Não informado"
    : v.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
interface Props {
  clienteId: number;
  parcelaId: string;
  aoAtualizar: (resposta: RespostaDetalhe) => void;
}

export default function ContaAzulApiPlanPanel({
  clienteId,
  parcelaId,
  aoAtualizar,
}: Props) {
  const [sincronizado, setSincronizado] = useState(false);
  const [plano, setPlano] = useState<PlanoApi | null>(null);
  const [erro, setErro] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef<AbortController | null>(null);
  useEffect(() => () => pending.current?.abort(), []);

  async function preparar() {
    pending.current?.abort();
    const controller = new AbortController();
    pending.current = controller;
    setBusy(true);
    setErro("");
    setPlano(null);
    setSincronizado(false);
    try {
      const value = await prepararPlanoApi(
        clienteId,
        parcelaId,
        controller.signal,
      );
      if (!controller.signal.aborted) {
        setPlano(value);
        // Atualiza também o detalhe: o backend consultou novamente a fonte.
        aoAtualizar({
          clienteId: value.clienteId,
          consultadoEm: value.consultadoEm,
          parcela: value.parcela,
        });
      }
    } catch (e) {
      if (!controller.signal.aborted)
        setErro(e instanceof Error ? e.message : "Falha ao preparar plano.");
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  return (
    <section
      className="ca-parcel-detail-category"
      aria-label="Plano de conferência pela API"
      aria-busy={busy}
    >
      <h4>Plano de conferência desta parcela</h4>
      <p>
        Reconsulta a Conta Azul e compara com os lançamentos da empresa
        selecionada. Este plano não grava lançamentos; a sincronização exige a
        prévia e a confirmação abaixo.
      </p>
      <button type="button" disabled={busy} onClick={() => void preparar()}>
        {busy
          ? "Preparando plano…"
          : plano
            ? "Preparar outro plano"
            : "Preparar plano desta parcela"}
      </button>
      {erro && (
        <p role="alert" className="ca-parcel-detail-error">
          {erro}
        </p>
      )}
      {plano && (
        <>
          <p role="status">
            <strong>
              {sincronizado
                ? "Operação de sincronização registrada no FinVista"
                : rotulo[plano.situacao]}
            </strong>
          </p>
          <p className="ca-parcel-detail-id">
            Identidade da API: {plano.identidadeApi}.{" "}
            {sincronizado
              ? "Vínculo registrado."
              : "Esta conferência não grava o vínculo."}
          </p>
          {!sincronizado && plano.avisos.length > 0 && (
            <ul>
              {plano.avisos.map((aviso, index) => (
                <li key={index}>{aviso}</li>
              ))}
            </ul>
          )}
          <p>
            {sincronizado
              ? "Gere outro plano para consultar o estado após a sincronização."
              : `${plano.totalCorrespondencias} correspondências encontradas. Exibindo até 20.`}
          </p>
          {!sincronizado && plano.correspondencias.length > 0 && (
            <ul>
              {plano.correspondencias.map((c) => (
                <li key={c.id}>
                  Registro #{c.id}: {c.descricao || "Sem descrição"} ·{" "}
                  {c.tipo || "Tipo não informado"} · {dinheiro(c.valor)} ·
                  origem: {c.origem || "Não informada"}
                </li>
              ))}
            </ul>
          )}
          <p>
            Plano preparado em{" "}
            {new Date(plano.consultadoEm).toLocaleString("pt-BR")}. É uma
            conferência, não uma autorização para gravar. Não confirma
            identidade entre registros semelhantes.
          </p>
          {(plano.situacao === "SEM_CORRESPONDENCIA" ||
            plano.situacao === "REGISTRO_IDENTIFICADO") && (
            <ContaAzulApiSyncPanel
              key={plano.consultadoEm}
              clienteId={clienteId}
              parcelaId={parcelaId}
              aoConcluir={() => setSincronizado(true)}
            />
          )}
          {plano.situacao === "CORRESPONDENCIA_POSSIVEL" && (
            <ContaAzulOriginLinkPanel
              key={plano.consultadoEm}
              plano={plano}
              parcelaId={parcelaId}
              aoConcluir={() => setSincronizado(true)}
            />
          )}
        </>
      )}
    </section>
  );
}
