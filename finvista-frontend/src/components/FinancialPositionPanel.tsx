import FinancialAccountsCard from "./FinancialAccountsCard";
import { useEffect, useState } from "react";
import { API_URL } from "../config/api";
import { brl, dataBR } from "../services/dashboardAnalysisService";
import "../styles/financial-position.css";

type Grupo = {
  vencidos: number;
  aVencer: number;
  venceHoje?: number | null;
  aVencerAposHoje?: number | null;
};
type Posicao = {
  clienteId: number;
  referencia: string;
  vencimentoInicio?: string | null;
  vencimentoFim?: string | null;
  receber: Grupo;
  pagar: Grupo;
  semValorAberto: number;
  semVencimento: number;
  cobertura?: {
    origem: string;
    semValorAberto: number;
    semVencimento: number;
    primeiraDataAnalise: string | null;
    ultimaDataAnalise: string | null;
  }[];
};

export default function FinancialPositionPanel({
  clienteId, inicio, fim,
}: {
  clienteId: number;
  inicio?: string;
  fim?: string;
}) {
  const [expandida, setExpandida] = useState(false);
  const [resultado,setResultado]=useState<{chave:string;valor:Posicao}|null>(null);
  const [falha,setFalha]=useState<{chave:string;mensagem:string}|null>(null);
  const [versao,setVersao]=useState(0);
  const chave=JSON.stringify([clienteId,inicio??null,fim??null,versao]);
  const dados=resultado?.chave===chave?resultado.valor:null;
  const erro=falha?.chave===chave?falha.mensagem:"";
  const carregando=!dados&&!erro;
  useEffect(()=>{
    const controller=new AbortController();
    void (async()=>{try{
      if(Boolean(inicio)!==Boolean(fim))throw new Error("Informe as duas datas do intervalo de vencimento.");
      const params=new URLSearchParams();if(inicio&&fim){params.set("inicio",inicio);params.set("fim",fim);}
      const resposta=await fetch(`${API_URL}/api/dashboard/posicao-financeira?${params}`,{credentials:"include",cache:"no-store",signal:controller.signal});
      if(!resposta.ok)throw new Error(resposta.status===401?"Entre novamente no FinVista.":"Não foi possível consultar os valores em aberto.");
      const next:Posicao=await resposta.json();
      if(next.clienteId!==clienteId)throw new Error("A empresa mudou. Atualize o painel.");
      if((next.vencimentoInicio??null)!==(inicio??null)||(next.vencimentoFim??null)!==(fim??null))throw new Error("O intervalo retornado não corresponde ao filtro. Confira a publicação do backend.");
      if(!controller.signal.aborted)setResultado({chave,valor:next});
    }catch(e){if(!controller.signal.aborted)setFalha({chave,mensagem:e instanceof Error?e.message:"Falha na consulta."});}})();
    return ()=>controller.abort();
  },[clienteId,inicio,fim,chave]);
  return (
    <section
      className="fp-panel"
      aria-label="Posição financeira"
      aria-busy={carregando}
    >
      <div className="fp-heading">
        <div>
          <h2>Contas em aberto</h2>
          <p>
            {dados
              ? `Referência: ${dataBR(dados.referencia)}`
              : "Consultando posição"}{" "}
            · {inicio && fim ? `Vencimentos: ${dataBR(inicio)} a ${dataBR(fim)}` : "Todos os vencimentos"}
          </p>
          <p>Saldos bancários: posição atual, independente do intervalo de vencimento.</p>
        </div>
        {dados && (
          <div className="exec-position-chips">
            <span>
              A receber {brl(dados.receber.vencidos + dados.receber.aVencer)}
            </span>
            <span className={dados.pagar.vencidos > 0 ? "exec-danger" : ""}>
              A pagar vencido {brl(dados.pagar.vencidos)}
            </span>
          </div>
        )}
        <button
          type="button"
          aria-expanded={expandida}
          aria-controls="exec-position-details"
          onClick={() => setExpandida((v) => !v)}
        >
          {expandida ? "Recolher ⌃" : "Expandir ⌄"}
        </button>
      </div>
      {erro && !expandida && <p role="alert">{erro}</p>}
      <div id="exec-position-details" hidden={!expandida}>
        <div className="exec-position-refresh">
          <button
            type="button"
            disabled={carregando}
            onClick={() => {
              setFalha(null);
              setResultado(null);
              setVersao((v) => v + 1);
            }}
          >
            {carregando ? "Consultando…" : "Atualizar"}
          </button>
        </div>
        {erro && <p role="alert">{erro}</p>}
        {dados && (dados.semValorAberto > 0 || dados.semVencimento > 0) && (
          <div className="fp-coverage-warning" role="status">
            <span className="fp-warning-icon" aria-hidden="true">
              !
            </span>
            <div>
              <strong>Posição parcial de contas a receber e a pagar</strong>
              <p>
                Há registros sem saldo em aberto ou vencimento. Os totais
                incluem apenas os dados suficientes. Confira os registros fora
                dos totais abaixo.
              </p>
            </div>
          </div>
        )}

        <div className="fp-grid">
          <FinancialAccountsCard
            key={`${clienteId}:${versao}`}
            clienteId={clienteId}
            versao={versao}
          />
          {(
            [
              ["A receber", dados?.receber],
              ["A pagar", dados?.pagar],
            ] as const
          ).map(([titulo, grupo]) => (
            <article
              className={`fp-card ${titulo === "A receber" ? "fp-receivable" : "fp-payable"}`}
              key={titulo}
            >
              <div className="fp-card-heading">
                <h3>{titulo}</h3>
                <span aria-hidden="true">
                  {titulo === "A receber" ? "↙" : "↗"}
                </span>
              </div>
              <dl>
                <div>
                  <dt>Vencidos</dt>
                  <dd
                    className={
                      grupo && grupo.vencidos > 0 ? "fp-overdue" : undefined
                    }
                  >
                    {grupo ? brl(grupo.vencidos) : "—"}
                  </dd>
                </div>
                {grupo?.venceHoje != null && (
                  <div>
                    <dt>Vence hoje</dt>
                    <dd>{brl(grupo.venceHoje)}</dd>
                  </div>
                )}
                <div>
                  <dt>
                    A vencer{grupo?.venceHoje == null ? " (inclui hoje)" : ""}
                  </dt>
                  <dd>
                    {grupo ? brl(grupo.aVencerAposHoje ?? grupo.aVencer) : "—"}
                  </dd>
                </div>
              </dl>
            </article>
          ))}
        </div>
        <details className="fp-method">
          <summary>Como interpretar estes valores</summary>
          <p className="fp-note">
            Saldos ainda em aberto, limitados ao intervalo de vencimento exibido.
            Vencidos são anteriores à data atual; vence hoje usa a data atual;
            a vencer contém datas posteriores. Se hoje estiver fora do intervalo,
            vence hoje fica zerado. O intervalo não reconstrói uma posição histórica.
          </p>
        </details>
        {dados && (dados.semValorAberto > 0 || dados.semVencimento > 0) && (
          <details className="fp-coverage">
            <summary>
              Conferir dados fora dos totais (
              {dados.semValorAberto + dados.semVencimento})
            </summary>
            <p className="fp-note">
              Sem saldo em aberto informado: {dados.semValorAberto}. Com saldo
              positivo, mas sem vencimento: {dados.semVencimento}. Os registros sem vencimento suficiente não podem ser atribuídos ao intervalo; os demais diagnósticos respeitam os vencimentos conhecidos do recorte.
            </p>
            {!!dados.cobertura?.length && (
              <ul>
                {dados.cobertura.map((c) => (
                  <li key={c.origem}>
                    <strong>
                      {c.origem === "NAO_INFORMADA"
                        ? "Origem não informada"
                        : c.origem}
                    </strong>
                    <span>
                      Sem saldo: {c.semValorAberto} · Sem vencimento:{" "}
                      {c.semVencimento}
                    </span>
                    {c.primeiraDataAnalise && c.ultimaDataAnalise && (
                      <span>
                        Datas de análise: {dataBR(c.primeiraDataAnalise)} a{" "}
                        {dataBR(c.ultimaDataAnalise)}
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            )}
            <p className="fp-note">
              Importações genéricas de CSV/Excel podem conter apenas valores
              contábeis. Para completar a posição, confira a origem e use uma
              exportação com valores em aberto e vencimentos, passando pelo
              plano de conciliação para evitar duplicidades.
            </p>
          </details>
        )}
      </div>
    </section>
  );
}
