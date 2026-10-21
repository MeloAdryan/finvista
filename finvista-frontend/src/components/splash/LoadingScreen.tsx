import { useEffect, useState } from "react";
import LogoFV from "./LogoFV";
import "./splash.css";

type Props = { messages?: readonly string[]; intervalMs?: number };
const DEFAULT_MESSAGES = [
  "Carregando seus dados…",
  "Conectando ao servidor…",
  "A conexão está demorando um pouco mais. Aguarde…",
];

export default function LoadingScreen({
  messages = DEFAULT_MESSAGES,
  intervalMs = 4000,
}: Props) {
  const textos = messages.length ? messages : DEFAULT_MESSAGES;
  const [indice, setIndice] = useState(0);
  const atual = Math.min(indice, textos.length - 1);
  useEffect(() => {
    if (atual >= textos.length - 1) return;
    const timer = window.setTimeout(
      () => setIndice((n) => n + 1),
      Math.max(250, intervalMs),
    );
    return () => window.clearTimeout(timer);
  }, [atual, textos.length, intervalMs]);
  return (
    <main className="fv-screen fv-loading" aria-label="Carregando FinVista">
      <div className="fv-col">
        <div className="fv-logo">
          <div className="fv-halo" aria-hidden="true" />
          <LogoFV size={112} />
        </div>
        <div className="fv-track" aria-hidden="true">
          <i />
        </div>
        <p
          className="fv-msg"
          role="status"
          aria-live="polite"
          aria-atomic="true"
        >
          {textos[atual]}
        </p>
        {atual === textos.length - 1 && (
          <button
            type="button"
            className="fv-retry"
            onClick={() => window.location.reload()}
          >
            Recarregar página
          </button>
        )}
      </div>
    </main>
  );
}
