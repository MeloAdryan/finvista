import { API_URL } from "../config/api";

export interface ExpenseDistributionData {
  categoria: string;
  valor: number;
}

export async function getExpenseDistribution(): Promise<
  ExpenseDistributionData[]
> {
  const response = await fetch(`${API_URL}/api/distribuicao-despesas`, {
    method: "GET",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error("Erro ao carregar a distribuição das despesas");
  }

  return response.json();
}

export async function getCategoriesByCostCenter(
  centroCusto: string,
): Promise<string[]> {
  const centroCustoNormalizado = centroCusto.trim();

  if (!centroCustoNormalizado) {
    return [];
  }

  const params = new URLSearchParams({
    centroCusto: centroCustoNormalizado,
  });

  const response = await fetch(
    `${API_URL}/api/distribuicao-despesas/categorias?${params.toString()}`,
    {
      method: "GET",
      credentials: "include",
    },
  );

  if (!response.ok) {
    throw new Error(
      "Não foi possível carregar as categorias do centro de custo.",
    );
  }

  return response.json();
}