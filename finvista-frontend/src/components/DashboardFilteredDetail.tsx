import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import {
  brl,
  dataBR,
  exportarCSV,
  type DashboardAnalysis,
} from "../services/dashboardAnalysisService";
export default function DashboardFilteredDetail({
  analise,
  tipo,
  fechar,
}: {
  analise: DashboardAnalysis;
  tipo: "RECEITA" | "DESPESA";
  fechar: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [pagina, setPagina] = useState(0);
  const itens = analise.itens.filter((i) => i.tipo === tipo);
  const total = itens.reduce((s, i) => s + i.valorSelecionado, 0);
  useEffect(() => {
    const elemento = dialog.current;
    const focus = document.activeElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    elemento?.showModal();
    return () => {
      elemento?.close();
      document.body.style.overflow = overflow;
      if (focus instanceof HTMLElement && focus.isConnected) focus.focus();
    };
  }, []);
  return createPortal(
    <dialog
      ref={dialog}
      className="df-detail"
      aria-labelledby="df-detail-title"
      onCancel={fechar}
      onClick={(e) => {
        if (e.target === dialog.current) fechar();
      }}
    >
      <header>
        <h2 id="df-detail-title">
          Lançamentos de {tipo === "RECEITA" ? "receita" : "despesa"}
        </h2>
        <button type="button" onClick={fechar} autoFocus>
          Fechar
        </button>
      </header>
      <p>
        {dataBR(analise.inicio)} até {dataBR(analise.fim)} ·{" "}
        {analise.categoria ?? "Todas as categorias"}
      </p>
      <p>
        <strong>Total: {brl(total)}</strong> · {itens.length} lançamentos
      </p>
      <p>
        O valor selecionado soma apenas os rateios da categoria escolhida. Sem
        filtro, é o valor contabilizado integral. Não representa somente
        pagamentos ou recebimentos.
      </p>
      <button
        type="button"
        onClick={() =>
          exportarCSV("lancamentos-painel.csv", [
            [
              "ID",
              "Data",
              "Descrição",
              "Categorias selecionadas",
              "Valor selecionado BRL",
              "Valor integral BRL",
            ],
            ...itens.map((i) => [
              i.id,
              i.data,
              i.descricao,
              i.categorias.join(" | "),
              i.valorSelecionado,
              i.valorContabilizadoIntegral,
            ]),
          ])
        }
      >
        Exportar todos em CSV
      </button>
      {!itens.length ? (
        <p>Nenhum lançamento no período e categoria selecionados.</p>
      ) : (
        <div className="df-table-scroll">
          <table>
            <thead>
              <tr>
                <th>Data</th>
                <th>Lançamento</th>
                <th>Selecionado</th>
                <th>Integral</th>
              </tr>
            </thead>
            <tbody>
              {itens.slice(pagina * 20, pagina * 20 + 20).map((i) => (
                <tr key={i.id}>
                  <td>{dataBR(i.data)}</td>
                  <td>
                    #{i.id} · {i.descricao}
                    <small>{i.categorias.join(" · ")}</small>
                  </td>
                  <td>{brl(i.valorSelecionado)}</td>
                  <td>{brl(i.valorContabilizadoIntegral)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <nav>
        <button
          type="button"
          disabled={pagina === 0}
          onClick={() => setPagina((p) => p - 1)}
        >
          Anterior
        </button>
        <span>
          Página {pagina + 1} de {Math.max(1, Math.ceil(itens.length / 20))}
        </span>
        <button
          type="button"
          disabled={(pagina + 1) * 20 >= itens.length}
          onClick={() => setPagina((p) => p + 1)}
        >
          Próxima
        </button>
      </nav>
    </dialog>,
    document.body,
  );
}
