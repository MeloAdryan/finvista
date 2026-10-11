import { useEffect, useRef, useState } from "react";
import type { AuthUser } from "../services/authService";
import type { Cliente } from "../services/clientService";
import AdminRegistration from "./AdminRegistration";
function ToolDialog({
  tipo,
  usuario,
  cliente,
  recolhida,
  onRecolher,
  onFechar,
}: {
  tipo: "ajuda" | "configuracoes";
  usuario: AuthUser;
  cliente: Cliente | null;
  recolhida: boolean;
  onRecolher: () => void;
  onFechar: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const [cadastros, setCadastros] = useState(false);
  useEffect(() => {
    const d = ref.current;
    const anterior = document.activeElement;
    d?.showModal();
    return () => {
      d?.close();
      if (anterior instanceof HTMLElement) anterior.focus();
    };
  }, []);
  return (
    <dialog className="sb-dialog" ref={ref} onCancel={onFechar}>
      <header>
        <h2>{tipo === "ajuda" ? "Ajuda" : "Configurações"}</h2>
        <button type="button" onClick={onFechar} aria-label="Fechar janela">
          ✕
        </button>
      </header>
      {tipo === "ajuda" ? (
        <div className="sb-dialog-content">
          <h3>Filtros e gráficos</h3>
          <p>
            Escolha o período e aplique os filtros. Clicar em um centro ou
            categoria filtra os dados da página; use a opção de limpar seleção
            para voltar.
          </p>
          <h3>Valores exatos</h3>
          <p>
            Abra “Ver como tabela” ou exporte CSV para conferir os valores.
            Lançamentos em aberto podem estar incluídos na data de análise; o
            resultado não equivale a saldo bancário confirmado.
          </p>
          <h3>Empresas e acessos</h3>
          <p>
            ADMIN troca a empresa pelo seletor no topo do menu. CLIENTE acessa a
            empresa vinculada ao seu usuário.
          </p>
          <h3>Metas e projeção</h3>
          <p>
            Metas usam o seu próprio período e alerta configurado. Na projeção,
            receitas complementares e saldo inicial são hipóteses da simulação.
          </p>
        </div>
      ) : cadastros ? (
        <AdminRegistration
          onVoltar={() => {
            setCadastros(false);
            window.dispatchEvent(new Event("finvista:carteira-atualizada"));
          }}
        />
      ) : (
        <div className="sb-dialog-content">
          <p>
            <strong>Usuário:</strong> {usuario.nome} ·{" "}
            {usuario.perfil === "ADMIN" ? "Admin" : "Cliente"}
          </p>
          <p>
            <strong>Empresa:</strong> {cliente?.nome ?? "Empresa vinculada"}
          </p>
          <button type="button" onClick={onRecolher}>
            {recolhida ? "Expandir" : "Recolher"} menu no computador
          </button>
          {usuario.perfil === "ADMIN" && (
            <button type="button" onClick={() => setCadastros(true)}>
              Gerenciar cadastros de clientes e usuários
            </button>
          )}
          <p>
            As preferências do menu valem nesta sessão. Alterações de empresa
            são feitas no seletor da sidebar.
          </p>
        </div>
      )}
    </dialog>
  );
}
export default function SidebarTools({
  usuario,
  cliente,
  recolhida,
  onRecolher,
  onSair,
}: {
  usuario: AuthUser;
  cliente: Cliente | null;
  recolhida: boolean;
  onRecolher: () => void;
  onSair: () => Promise<void>;
}) {
  const [janela, setJanela] = useState<"ajuda" | "configuracoes" | null>(null),
    [saindo, setSaindo] = useState(false);
  return (
    <div className="finvista-sidebar-bottom sb-bottom">
      <div className="sb-utilities">
        <button type="button" title="Ajuda" onClick={() => setJanela("ajuda")}>
          <span aria-hidden="true">ⓘ</span>
          <span className="sb-text">Ajuda</span>
        </button>
        <button
          type="button"
          title="Configurações"
          onClick={() => setJanela("configuracoes")}
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
          <span className="sb-text">Configurações</span>
        </button>
      </div>
      <div className="sb-user">
        <span className="sb-user-avatar" title={usuario.nome}>
          {usuario.nome.trim().slice(0, 2).toUpperCase()}
        </span>
        <div className="sb-user-info">
          <strong>{usuario.nome}</strong>
          <small>{usuario.perfil === "ADMIN" ? "Admin" : "Cliente"}</small>
        </div>
        <button
          type="button"
          title="Sair do FinVista"
          aria-label="Sair do FinVista"
          disabled={saindo}
          onClick={() => {
            setSaindo(true);
            void onSair().finally(() => setSaindo(false));
          }}
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M9 4H4v16h5M12 12h9m-4-4 4 4-4 4" />
          </svg>
        </button>
      </div>
      {janela && (
        <ToolDialog
          tipo={janela}
          usuario={usuario}
          cliente={cliente}
          recolhida={recolhida}
          onRecolher={onRecolher}
          onFechar={() => setJanela(null)}
        />
      )}
    </div>
  );
}
