import { createContext, useContext } from "react";

export interface AnalysisPeriod {
  inicio?: string;
  fim?: string;
  categoria?: string;
}

export interface AnalysisPeriodState {
  periodo: AnalysisPeriod;
  atualizarPeriodo: (periodo: AnalysisPeriod) => void;
}

export const AnalysisPeriodContext = createContext<AnalysisPeriodState | null>(
  null,
);

export function useAnalysisPeriod() {
  const context = useContext(AnalysisPeriodContext);
  if (!context) {
    throw new Error("As análises precisam de AnalysisPeriodProvider.");
  }
  return context;
}
