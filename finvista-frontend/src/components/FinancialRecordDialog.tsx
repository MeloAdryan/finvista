import "../styles/financial-management.css";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import {
  managementGet,
  money,
  dateLabel,
  type RecordDetail,
} from "../services/financialManagementService";
export default function FinancialRecordDialog({
  clienteId,
  params,
  onClose,
}: {
  clienteId: number;
  params: Record<string, string>;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const [data, setData] = useState<RecordDetail | null>(null),
    [error, setError] = useState("");
  const query = JSON.stringify(params);
  useEffect(() => {
    ref.current?.showModal();
    const c = new AbortController();
    managementGet<RecordDetail>(
      "detalhe",
      clienteId,
      JSON.parse(query) as Record<string, string>,
      c.signal,
    )
      .then((x) => {
        if (!c.signal.aborted) setData(x);
      })
      .catch((e: unknown) => {
        if (!c.signal.aborted)
          setError(e instanceof Error ? e.message : "Falha ao consultar.");
      });
    return () => c.abort();
  }, [clienteId, query]);
  return createPortal(
    <dialog
      ref={ref}
      className="fv-record-dialog"
      onCancel={(e) => {
        e.stopPropagation();
        onClose();
      }}
      aria-labelledby="fv-record-title"
    >
      <header>
        <h2 id="fv-record-title">Composição do registro</h2>
        <button type="button" onClick={onClose}>
          Fechar
        </button>
      </header>
      {error ? (
        <p role="alert">{error}</p>
      ) : !data ? (
        <p role="status">Consultando composição…</p>
      ) : (
        <>
          <h3>{data.descricao}</h3>
          <p>
            {data.tipo} · {data.origem} · {data.situacao}
          </p>
          <p>
            Documento: {data.documento || "Não informado"} · Critério:{" "}
            {data.criterio}
          </p>
          <p>
            Competência: {dateLabel(data.competencia)} · Vencimento:{" "}
            {dateLabel(data.vencimento)}
          </p>
          <dl>
            <dt>Valor usado no recorte</dt>
            <dd>{money(data.valorSelecionado)}</dd>
            <dt>Principal original</dt>
            <dd>{money(data.principal)}</dd>
            <dt>Líquido realizado cadastrado</dt>
            <dd>{money(data.realizado)}</dd>
            <dt>Líquido ainda aberto</dt>
            <dd>{money(data.aberto)}</dd>
          </dl>
          <h3>Rateios usados no indicador</h3>
          {!data.rateios.length ? (
            <p>
              Não se aplica à posição atual. Consulte o detalhamento por
              critério para ver os rateios.
            </p>
          ) : (
            <div className="fv-management-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Data</th>
                    <th>Categoria</th>
                    <th>Centro</th>
                    <th>Valor</th>
                  </tr>
                </thead>
                <tbody>
                  {data.rateios.map((r, i) => (
                    <tr key={i}>
                      <td>{dateLabel(r.data)}</td>
                      <td>{r.categoria}</td>
                      <td>{r.centro}</td>
                      <td>{money(r.valor)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <h3>Baixas auditadas</h3>
          {!data.baixas.length ? (
            <p>Nenhuma baixa individual auditada disponível nesta fonte.</p>
          ) : (
            <div className="fv-management-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Data</th>
                    <th>Baixa</th>
                    <th>Principal</th>
                    <th>Líquido</th>
                    <th>Seleção</th>
                  </tr>
                </thead>
                <tbody>
                  {data.baixas.map((b, i) => (
                    <tr key={`${b.id}-${i}`}>
                      <td>{dateLabel(b.data)}</td>
                      <td>{b.id}</td>
                      <td>{money(b.principal)}</td>
                      <td>{money(b.liquido)}</td>
                      <td>{b.selecionada ? "Movimento selecionado" : ""}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {data.arquivo && (
            <p>
              Arquivo: {data.arquivo} · Incluído em{" "}
              {data.importadoEm
                ? new Date(data.importadoEm).toLocaleString("pt-BR")
                : "data não informada"}
            </p>
          )}
          {data.avisos.map((x) => (
            <p key={x} className="fv-management-notice">
              {x}
            </p>
          ))}
        </>
      )}
    </dialog>,
    document.body,
  );
}
