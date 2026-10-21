import { useCallback, useEffect, useRef } from "react";
import LogoFV from "./LogoFV";
import "./splash.css";

type Props = {
  onContinue: () => void;
  autoContinueMs?: number;
  buttonLabel?: string;
  nome?: string;
};
export default function WelcomeScreen({
  onContinue,
  autoContinueMs = 4500,
  buttonLabel = "Entrar no painel",
  nome,
}: Props) {
  const continuou = useRef(false);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const continuar = useCallback(() => {
    if (continuou.current) return;
    continuou.current = true;
    onContinue();
  }, [onContinue]);
  useEffect(() => {
    buttonRef.current?.focus();
    if (autoContinueMs <= 0) return;
    const timer = window.setTimeout(continuar, autoContinueMs);
    return () => window.clearTimeout(timer);
  }, [autoContinueMs, continuar]);
  return (
    <main className="fv-screen fv-welcome" aria-labelledby="fv-welcome-title">
      <div className="fv-grid" aria-hidden="true" />
      <div className="fv-col">
        <div className="fv-logo">
          <LogoFV size={104} />
          <div className="fv-ring" aria-hidden="true" />
        </div>
        {nome && <p className="fv-user">Olá, {nome.trim().split(/\s+/)[0]}</p>}
        <p className="fv-eyebrow">Bem-vindo ao</p>
        <h1 className="fv-big" id="fv-welcome-title">
          Fin<b>Vista</b>
        </h1>
        <p className="fv-tag">Financial Intelligence</p>
        <button
          ref={buttonRef}
          type="button"
          className="fv-go"
          onClick={continuar}
        >
          {buttonLabel} <span aria-hidden="true">→</span>
        </button>
      </div>
    </main>
  );
}
