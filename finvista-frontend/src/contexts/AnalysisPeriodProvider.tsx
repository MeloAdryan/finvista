import { useCallback, useMemo, useState, type ReactNode } from "react";
import {
  AnalysisPeriodContext,
  type AnalysisPeriod,
} from "./analysisPeriodContext";

export default function AnalysisPeriodProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [periodo, setPeriodo] = useState<AnalysisPeriod>({});
  const atualizarPeriodo = useCallback((novo: AnalysisPeriod) => {
    const proximo = {
      inicio: novo.inicio,
      fim: novo.fim,
      categoria: novo.categoria || undefined,
    };
    setPeriodo((atual) =>
      atual.inicio === proximo.inicio &&
      atual.fim === proximo.fim &&
      atual.categoria === proximo.categoria
        ? atual
        : proximo,
    );
  }, []);
  const value = useMemo(
    () => ({ periodo, atualizarPeriodo }),
    [periodo, atualizarPeriodo],
  );
  return (
    <AnalysisPeriodContext.Provider value={value}>
      {children}
    </AnalysisPeriodContext.Provider>
  );
}
