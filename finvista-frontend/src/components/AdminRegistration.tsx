import { useEffect, useState, type FormEvent } from "react";
import type { Cliente } from "../services/clientService";
import AdminClientManagement from "./AdminClientManagement";
import {
  criarEmpresa,
  criarUsuarioAdmin,
  listarUsuariosAdmin,
  listarEmpresasAdmin,
  type AdminUser,
  type NewUser,
} from "../services/adminRegistrationService";
import "../styles/admin-registration.css";
const vazio: NewUser = {
  nome: "",
  email: "",
  senha: "",
  perfil: "CLIENTE",
  clienteId: null,
};
export default function AdminRegistration({
  onVoltar,
  textoVoltar = "Voltar à seleção de clientes",
}: {
  onVoltar: () => void;
  textoVoltar?: string;
}) {
  const [clientes, setClientes] = useState<Cliente[]>([]),
    [usuarios, setUsuarios] = useState<AdminUser[]>([]);
  const [nomeEmpresa, setNomeEmpresa] = useState(""),
    [form, setForm] = useState<NewUser>({ ...vazio });
  const [carregando, setCarregando] = useState(true),
    [salvando, setSalvando] = useState(false),
    [erro, setErro] = useState(""),
    [sucesso, setSucesso] = useState(""),
    [revisao, setRevisao] = useState(0);
  useEffect(() => {
    let vivo = true;
    Promise.all([listarEmpresasAdmin(), listarUsuariosAdmin()])
      .then(([c, u]) => {
        if (vivo) {
          setClientes(c);
          setUsuarios(u);
          setErro("");
        }
      })
      .catch((e) => {
        if (vivo)
          setErro(
            e instanceof Error
              ? e.message
              : "Não foi possível carregar os cadastros.",
          );
      })
      .finally(() => {
        if (vivo) setCarregando(false);
      });
    return () => {
      vivo = false;
    };
  }, [revisao]);
  async function empresa(e: FormEvent) {
    e.preventDefault();
    setErro("");
    setSucesso("");
    setSalvando(true);
    try {
      const c = await criarEmpresa(nomeEmpresa.trim());
      setClientes((v) =>
        [...v, c].sort((a, b) => a.nome.localeCompare(b.nome)),
      );
      setNomeEmpresa("");
      setForm((v) => ({ ...v, perfil: "CLIENTE", clienteId: c.id }));
      setSucesso(
        `Empresa ${c.nome} cadastrada. Você já pode criar o usuário vinculado.`,
      );
    } catch (e) {
      setErro(e instanceof Error ? e.message : "Falha no cadastro.");
    } finally {
      setSalvando(false);
    }
  }
  async function usuario(e: FormEvent) {
    e.preventDefault();
    setErro("");
    setSucesso("");
    if (form.perfil === "CLIENTE" && !form.clienteId) {
      setErro("Escolha a empresa do usuário.");
      return;
    }
    if (new TextEncoder().encode(form.senha).length > 72) {
      setErro("A senha deve ter até 72 bytes em UTF-8.");
      return;
    }
    setSalvando(true);
    try {
      const u = await criarUsuarioAdmin({
        ...form,
        nome: form.nome.trim(),
        email: form.email.trim(),
        clienteId: form.perfil === "ADMIN" ? null : form.clienteId,
      });
      setUsuarios((v) =>
        [...v, u].sort((a, b) => a.nome.localeCompare(b.nome)),
      );
      setForm({ ...vazio });
      setSucesso(
        `Usuário ${u.nome} cadastrado. Ele já pode entrar com o e-mail e a senha definidos.`,
      );
    } catch (e) {
      setErro(e instanceof Error ? e.message : "Falha no cadastro.");
    } finally {
      setSalvando(false);
    }
  }
  return (
    <main className="ar-page">
      <header>
        <div>
          <span>ADMINISTRAÇÃO</span>
          <h1>Clientes e usuários</h1>
          <p>Cadastre empresas e vincule os acessos à empresa correta.</p>
        </div>
        <button type="button" onClick={onVoltar} disabled={salvando}>
          {textoVoltar}
        </button>
      </header>
      {erro && (
        <div role="alert" className="ar-error">
          {erro}{" "}
          <button
            type="button"
            disabled={salvando}
            onClick={() => {
              setCarregando(true);
              setRevisao((v) => v + 1);
            }}
          >
            Atualizar listagens
          </button>
        </div>
      )}
      {sucesso && (
        <p role="status" className="ar-success">
          {sucesso}
        </p>
      )}
      {carregando ? (
        <p role="status">Carregando cadastros...</p>
      ) : (
        <>
          <div className="ar-grid">
            <section>
              <h2>Nova empresa</h2>
              <form onSubmit={(e) => void empresa(e)}>
                <label>
                  Nome da empresa
                  <input
                    required
                    maxLength={150}
                    value={nomeEmpresa}
                    onChange={(e) => setNomeEmpresa(e.target.value)}
                  />
                </label>
                <button disabled={salvando} type="submit">
                  {salvando ? "Aguarde..." : "Cadastrar empresa"}
                </button>
              </form>
              <p>O cadastro cria uma empresa sem lançamentos financeiros.</p>
            </section>
            <section>
              <h2>Novo usuário</h2>
              <form onSubmit={(e) => void usuario(e)}>
                <label>
                  Nome
                  <input
                    required
                    maxLength={120}
                    autoComplete="name"
                    value={form.nome}
                    onChange={(e) => setForm({ ...form, nome: e.target.value })}
                  />
                </label>
                <label>
                  E-mail
                  <input
                    required
                    type="email"
                    maxLength={180}
                    autoComplete="off"
                    value={form.email}
                    onChange={(e) =>
                      setForm({ ...form, email: e.target.value })
                    }
                  />
                </label>
                <label>
                  Senha inicial
                  <input
                    required
                    type="password"
                    minLength={8}
                    maxLength={72}
                    autoComplete="new-password"
                    value={form.senha}
                    onChange={(e) =>
                      setForm({ ...form, senha: e.target.value })
                    }
                  />
                </label>
                <label>
                  Perfil
                  <select
                    value={form.perfil}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        perfil: e.target.value as NewUser["perfil"],
                        clienteId: null,
                      })
                    }
                  >
                    <option value="CLIENTE">
                      CLIENTE — acesso à empresa vinculada
                    </option>
                    <option value="ADMIN">
                      ADMIN — administração de toda a carteira
                    </option>
                  </select>
                </label>
                {form.perfil === "CLIENTE" ? (
                  <label>
                    Empresa
                    <select
                      required
                      value={form.clienteId ?? ""}
                      onChange={(e) =>
                        setForm({
                          ...form,
                          clienteId: e.target.value
                            ? Number(e.target.value)
                            : null,
                        })
                      }
                    >
                      <option value="">Selecione uma empresa</option>
                      {clientes
                        .filter((c) => c.ativo)
                        .map((c) => (
                          <option key={c.id} value={c.id}>
                            {c.nome} · #{c.id}
                          </option>
                        ))}
                    </select>
                  </label>
                ) : (
                  <p className="ar-admin-note">
                    Este perfil poderá acessar a carteira e cadastrar outros
                    usuários.
                  </p>
                )}
                <button type="submit" disabled={salvando}>
                  {salvando ? "Aguarde..." : "Cadastrar usuário"}
                </button>
              </form>
            </section>
          </div>
          <AdminClientManagement
            empresas={clientes}
            ocupado={salvando}
            aoOcupar={setSalvando}
            aoRecarregar={() => {
              setCarregando(true);
              setRevisao((v) => v + 1);
            }}
            aoAtualizar={(empresa) => {
              setClientes((v) =>
                v
                  .map((c) => (c.id === empresa.id ? empresa : c))
                  .sort((a, b) => a.nome.localeCompare(b.nome)),
              );
              setUsuarios((v) =>
                v.map((u) =>
                  u.clienteId === empresa.id
                    ? { ...u, clienteNome: empresa.nome }
                    : u,
                ),
              );
              if (!empresa.ativo)
                setForm((v) =>
                  v.clienteId === empresa.id ? { ...v, clienteId: null } : v,
                );
            }}
          />
          <section>
            <h2>Usuários cadastrados ({usuarios.length})</h2>
            <div className="ar-table">
              <table>
                <thead>
                  <tr>
                    <th>Nome</th>
                    <th>E-mail</th>
                    <th>Perfil</th>
                    <th>Empresa</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {usuarios.map((u) => (
                    <tr key={u.id}>
                      <td>{u.nome}</td>
                      <td>{u.email}</td>
                      <td>{u.perfil}</td>
                      <td>
                        {u.clienteNome ??
                          (u.perfil === "ADMIN"
                            ? "Carteira de clientes"
                            : "Sem vínculo")}
                      </td>
                      <td>{u.ativo ? "Ativo" : "Inativo"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!usuarios.length && <p>Nenhum usuário encontrado.</p>}
          </section>
        </>
      )}
    </main>
  );
}
