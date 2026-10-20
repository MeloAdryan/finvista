import { useEffect, useId, useRef, type ReactNode } from "react";
import type { SpendingGoal } from "../services/spendingGoalService";
import { periodoMeta } from "../services/goalPresentation";
import { createPortal } from "react-dom";
import "../styles/goal-expanded.css";

export default function GoalDetailDialog({
  meta,
  aoFechar,
  children,
  rotuloFechar = "Fechar e voltar às metas",
}: {
  meta: SpendingGoal;
  aoFechar: () => void;
  children: ReactNode;
  rotuloFechar?: string;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titulo = useId();
  useEffect(() => {
    const element = dialog.current;
    const anterior = document.activeElement;
    const overflow = document.body.style.overflow;
    element?.showModal();
    document.body.style.overflow = "hidden";
    return () => {
      element?.close();
      document.body.style.overflow = overflow;
      if (anterior instanceof HTMLElement && anterior.isConnected)
        anterior.focus();
    };
  }, []);
  return createPortal(
    <dialog
      ref={dialog}
      className="goal-detail-dialog goal-progress-new"
      aria-labelledby={titulo}
      onCancel={(event) => {
        event.preventDefault();
        aoFechar();
      }}
    >
      <header className="goal-detail-header">
        <div>
          <span>ACOMPANHAMENTO DA META · {periodoMeta(meta)}</span>
          <h2 id={titulo}>Meta de gastos #{meta.id}</h2>
          <p>
            {meta.dataInicio.split("-").reverse().join("/")} —{" "}
            {meta.dataFim.split("-").reverse().join("/")}
            {meta.centroCusto && ` · ${meta.centroCusto}`}
            {meta.categoria && ` · ${meta.categoria}`}
          </p>
        </div>
        <button type="button" autoFocus onClick={aoFechar}>
          {rotuloFechar}
        </button>
      </header>
      <div className="goal-detail-body">{children}</div>
    </dialog>,
    document.body,
  );
}
