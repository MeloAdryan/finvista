import { useEffect, useState } from "react";
import { listarClientes, type Cliente } from "../services/clientService";
import type { AuthUser } from "../services/authService";
export default function SidebarCompany({
  usuario,
  cliente,
  ocupado,
  onSelecionar,
}: {
  usuario: AuthUser;
  cliente: Cliente | null;
  ocupado: boolean;
  onSelecionar: (c: Cliente) => Promise<void>;
}) {
  const [carteira, setCarteira] = useState<Cliente[]>([]),
    [erro, setErro] = useState(""),
    [carregando, setCarregando] = useState(false),
    [tentativa, setTentativa] = useState(0);
  useEffect(() => {
    if (usuario.perfil !== "ADMIN") return;
    let vivo = true;
    listarClientes()
      .then((c) => {
        if (vivo) {
          setCarteira(c);
          setErro("");
        }
      })
      .catch(() => {
        if (vivo) setErro("Não foi possível carregar as empresas.");
      });
    return () => {
      vivo = false;
    };
  }, [usuario.perfil, tentativa]);
  useEffect(() => {
    const atualizar = () => setTentativa((v) => v + 1);
    window.addEventListener("finvista:carteira-atualizada", atualizar);
    return () =>
      window.removeEventListener("finvista:carteira-atualizada", atualizar);
  }, []);
  const nome = cliente?.nome ?? "Empresa vinculada";
  const opcoes =
    cliente && !carteira.some((c) => c.id === cliente.id)
      ? [cliente, ...carteira]
      : carteira;
  async function selecionar(id: number) {
    const c = opcoes.find((c) => c.id === id);
    if (!c || c.id === cliente?.id) return;
    setCarregando(true);
    setErro("");
    try {
      await onSelecionar(c);
    } catch (e) {
      setErro(
        e instanceof Error ? e.message : "Não foi possível trocar a empresa.",
      );
    } finally {
      setCarregando(false);
    }
  }
  return (
    <div className="sb-company" title={nome}>
      <span className="sb-company-avatar" aria-hidden="true">
        {nome.trim().slice(0, 2).toUpperCase()}
      </span>
      <div className="sb-company-info">
        <small>Empresa ativa</small>
        {usuario.perfil === "ADMIN" ? (
          <select
            aria-label="Trocar empresa ativa"
            value={cliente?.id ?? ""}
            disabled={ocupado || carregando || !opcoes.length}
            onChange={(e) => void selecionar(Number(e.target.value))}
          >
            {!cliente && <option value="">Selecione</option>}
            {opcoes.map((c) => (
              <option key={c.id} value={c.id}>
                {c.nome}
              </option>
            ))}
          </select>
        ) : (
          <strong>{nome}</strong>
        )}
      </div>
      {erro && (
        <div className="sb-company-error" role="alert">
          {erro}
          <button type="button" onClick={() => setTentativa((v) => v + 1)}>
            Atualizar empresas
          </button>
        </div>
      )}
    </div>
  );
}
