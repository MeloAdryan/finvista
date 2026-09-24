import { useEffect, useState } from "react";

import {
  getExpenseDistribution,
  type ExpenseDistributionData,
} from "../services/expenseDistributionService";

import ExpenseDistributionChart from "../charts/ExpenseDistributionChart";

import "../styles/dashboard.css";

function Expenses() {
  const [distribuicaoDespesas, setDistribuicaoDespesas] = useState<
    ExpenseDistributionData[]
  >([]);

  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    async function carregarDespesas() {
      try {
        setCarregando(true);
        setErro(null);

        const dados = await getExpenseDistribution();

        setDistribuicaoDespesas(dados);
      } catch (error) {
        console.error("Erro ao carregar despesas:", error);

        if (error instanceof Error) {
          setErro(error.message);
        } else {
          setErro("Não foi possível carregar as despesas.");
        }
      } finally {
        setCarregando(false);
      }
    }

    void carregarDespesas();
  }, []);

  const formatarMoeda = (valor: number) => {
    return valor.toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
    });
  };

  const formatarPercentual = (valor: number) => {
    return valor.toLocaleString("pt-BR", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  };

  const totalDistribuicaoDespesas = distribuicaoDespesas.reduce(
    (total, item) => total + item.valor,
    0,
  );

  if (carregando) {
    return <p>Carregando despesas...</p>;
  }

  if (erro) {
    return <p>{erro}</p>;
  }

  return (
    <div className="dashboard">
      {/* DISTRIBUIÇÃO DAS DESPESAS */}

      <section id="despesas" className="expense-distribution-section">
        <ExpenseDistributionChart dados={distribuicaoDespesas} />
      </section>

      <section className="expense-distribution-table-card">
        <h2>Detalhamento das despesas</h2>

        <div className="table-wrapper">
          <table className="expense-distribution-table">
            <thead>
              <tr>
                <th>Categoria</th>
                <th>Valor</th>
                <th>Participação</th>
              </tr>
            </thead>

            <tbody>
              {distribuicaoDespesas.map((item) => {
                const percentual =
                  totalDistribuicaoDespesas > 0
                    ? (item.valor / totalDistribuicaoDespesas) * 100
                    : 0;

                return (
                  <tr key={item.categoria}>
                    <td>{item.categoria}</td>

                    <td>{formatarMoeda(item.valor)}</td>

                    <td>{formatarPercentual(percentual)}%</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

export default Expenses;