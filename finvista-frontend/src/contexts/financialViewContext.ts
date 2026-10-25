import { createContext } from "react";
import type { ViewFilter } from "../services/financialViewService";

export const FinancialViewContext = createContext<ViewFilter | null>(null);

export const FinancialViewUpdateContext = createContext<
  ((filter: ViewFilter) => void) | null
>(null);
