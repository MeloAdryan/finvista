import { API_URL } from "../config/api";

export interface FinancialReference {
  ano: number;
  mes: number;
  mesAno: string;
  dataReferencia: string;
}

export async function obterReferenciaFinanceira(): Promise<FinancialReference> {
  const response = await fetch(
    `${API_URL}/api/referencia-financeira`,
    {
      method: "GET",
      credentials: "include",
    },
  );

  if (!response.ok) {
    throw new Error(
      "Não foi possível carregar a referência financeira.",
    );
  }

  return response.json();
}