import ContaAzulOriginReview from "./ContaAzulOriginReview";
import ContaAzulApiPlanPanel from "./ContaAzulApiPlanPanel";
import { useEffect, useState } from "react";
import {
  consultarDetalheParcela,
  ParcelDetailError,
  type RespostaDetalhe,
} from "../services/contaAzulParcelDetailService";
import "../styles/conta-azul-parcel-detail.css";

const dinheiro = (value: number | null | undefined) =>
  value == null
    ? "Não informado"
    : value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
const data = (value: string | null | undefined) =>
  value ? value.split("-").reverse().join("/") : "Não informada";
interface Props {
  clienteId: number;
  parcelaId: string;
  panelId: string;
  fechar: () => void;
}

export default function ContaAzulParcelDetailPanel({
  clienteId,
  parcelaId,
  panelId,
  fechar,
}: Props) {
  const [resposta, setResposta] = useState<RespostaDetalhe | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [erro, setErro] = useState("");
  const [carregando, setCarregando] = useState(true);
  // O efeito acompanha a consulta externa. Ao desmontar, cancela a requisição.
  useEffect(() => {
    const controller = new AbortController();
    consultarDetalheParcela(clienteId, parcelaId, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setResposta(value);
      })
      .catch((e: unknown) => {
        if (!controller.signal.aborted)
          setNotFound(e instanceof ParcelDetailError && e.status === 404);
        if (!controller.signal.aborted)
          setErro(e instanceof Error ? e.message : "Falha na consulta.");
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregando(false);
      });
    return () => controller.abort();
  }, [clienteId, parcelaId]);
  const p = resposta?.parcela;
  return (
    <section
      id={panelId}
      className="ca-parcel-detail"
      aria-label="Detalhes da parcela"
      aria-busy={carregando}
    >
      <div className="ca-parcel-detail-header">
        <h3>Detalhes da parcela</h3>
        <button type="button" onClick={fechar}>
          Fechar detalhes
        </button>
      </div>
      <p className="ca-parcel-detail-id">{parcelaId}</p>
      {carregando && <p role="status">Consultando detalhes…</p>}
      {erro && (
        <p role="alert" className="ca-parcel-detail-error">
          {erro}
        </p>
      )}
      {notFound && (
        <ContaAzulOriginReview
          key={`${clienteId}:${parcelaId}`}
          clienteId={clienteId}
          parcelaId={parcelaId}
        />
      )}
      {p && (
        <>
          <p>
            <strong>{p.descricao || "Sem descrição"}</strong>
          </p>
          <p>
            Status: {p.status || "Não informado"} · Vencimento:{" "}
            {data(p.data_vencimento)} · Competência do evento:{" "}
            {data(p.evento?.data_competencia)}.
          </p>
          <dl className="ca-parcel-detail-values">
            <div>
              <dt>Líquido da parcela</dt>
              <dd>
                {dinheiro(p.valor_liquido_conferido ?? p.valor_total_liquido)}
              </dd>
            </div>
            <div>
              <dt>Pago / recebido</dt>
              <dd>{dinheiro(p.valor_pago)}</dd>
            </div>
            <div>
              <dt>Pendente</dt>
              <dd>{dinheiro(p.nao_pago)}</dd>
            </div>
            <div>
              <dt>Bruto da composição</dt>
              <dd>{dinheiro(p.valor_composicao?.valor_bruto)}</dd>
            </div>
            <div>
              <dt>Líquido da composição</dt>
              <dd>{dinheiro(p.valor_composicao?.valor_liquido)}</dd>
            </div>
            <div>
              <dt>Juros</dt>
              <dd>{dinheiro(p.valor_composicao?.juros)}</dd>
            </div>
            <div>
              <dt>Multa</dt>
              <dd>{dinheiro(p.valor_composicao?.multa)}</dd>
            </div>
            <div>
              <dt>Desconto</dt>
              <dd>{dinheiro(p.valor_composicao?.desconto)}</dd>
            </div>
            <div>
              <dt>Taxa</dt>
              <dd>{dinheiro(p.valor_composicao?.taxa)}</dd>
            </div>
          </dl>
          {p.origem_valor_liquido === "COMPOSICAO_CONFERIDA" && (
            <p role="status">
              Líquido de {dinheiro(p.valor_liquido_conferido)} conferido pela
              composição da parcela e pelo saldo pago mais pendente. O campo
              principal da API veio sem valor. A conferência do líquido não
              grava lançamentos.
            </p>
          )}
          <h4>Rateios do evento financeiro</h4>
          <p>
            Evento: {p.evento?.id || "Não informado"} · Tipo:{" "}
            {p.evento?.tipo || "Não informado"} · Quantidade de parcelas:{" "}
            {p.evento?.condicao_pagamento?.quantidade_parcelas ??
              "Não informada"}
            .
          </p>
          <p>
            Os rateios abaixo são os originais do evento. A sincronização usa a
            distribuição apenas quando ela fecha com a parcela. Valores
            financeiros conferidos com classificação incompleta ficam em
            Categoria a revisar ou Centro a revisar, sem presumir proporções.
          </p>
          {!p.evento?.rateio?.length ? (
            <p>Nenhum rateio informado no detalhe.</p>
          ) : (
            <div className="ca-parcel-detail-rateios">
              {p.evento.rateio.map((r, index) => (
                <div className="ca-parcel-detail-category" key={index}>
                  <strong>
                    {r?.nome_categoria ||
                      r?.id_categoria ||
                      "Categoria não identificada"}
                  </strong>
                  <p>
                    Valor: {dinheiro(r?.valor)} · Bruto:{" "}
                    {dinheiro(r?.valor_bruto)}
                  </p>
                  {!r?.rateio_centro_custo?.length ? (
                    <p>Centros de custo não informados.</p>
                  ) : (
                    <ul>
                      {r?.rateio_centro_custo.map((c, n) => (
                        <li key={n}>
                          {c?.nome_centro_custo ||
                            c?.id_centro_custo ||
                            "Centro não identificado"}{" "}
                          — valor: {dinheiro(c?.valor)} · bruto:{" "}
                          {dinheiro(c?.valor_bruto)}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              ))}
            </div>
          )}
          <ContaAzulApiPlanPanel
            key={`${clienteId}:${parcelaId}`}
            clienteId={clienteId}
            parcelaId={parcelaId}
            aoAtualizar={setResposta}
          />
          <p>
            Detalhe consultado em{" "}
            {new Date(resposta.consultadoEm).toLocaleString("pt-BR")}. A
            consulta do detalhe não grava lançamentos; confira o resultado da
            sincronização no painel acima.
          </p>
        </>
      )}
    </section>
  );
}
