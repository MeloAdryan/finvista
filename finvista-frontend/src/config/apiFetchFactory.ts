interface CsrfResponse {
  headerName: string;
  token: string;
}
type FetchFunction = (input: string, init?: RequestInit) => Promise<Response>;

/** Restrito à API do FinVista. Não altera o fetch global nem repete escritas. */
export function createApiFetch(
  apiUrl: string,
  transport: FetchFunction = fetch,
) {
  const base = apiUrl.replace(/\/+$/, "");
  const api = new URL(base, globalThis.location?.href || "http://localhost");
  const prefix = `${api.pathname.replace(/\/+$/, "")}/api/`;
  let pendingToken: Promise<CsrfResponse> | null = null;

  async function loadToken(): Promise<CsrfResponse> {
    const response = await transport(`${base}/api/auth/csrf`, {
      credentials: "include",
      cache: "no-store",
      redirect: "error",
    });
    if (!response.ok)
      throw new Error(
        "Não foi possível preparar a segurança da sessão. Confira a conexão e tente novamente.",
      );
    const data = (await response.json()) as CsrfResponse;
    if (
      data.headerName !== "X-CSRF-TOKEN" ||
      typeof data.token !== "string" ||
      !data.token
    ) {
      throw new Error(
        "Resposta de segurança inválida. Confira a versão do backend.",
      );
    }
    return data;
  }

  function token() {
    // Compartilha só a consulta em andamento. Não guarda token após login/logout.
    if (!pendingToken)
      pendingToken = loadToken().finally(() => {
        pendingToken = null;
      });
    return pendingToken;
  }

  return async function apiFetch(
    url: string,
    init: RequestInit = {},
  ): Promise<Response> {
    const target = new URL(url, api);
    if (target.origin !== api.origin || !target.pathname.startsWith(prefix)) {
      throw new Error(
        "A chamada protegida deve usar a API configurada do FinVista.",
      );
    }
    init.signal?.throwIfAborted();
    const method = (init.method || "GET").toUpperCase();
    const headers = new Headers(init.headers);
    const unsafe = !["GET", "HEAD", "OPTIONS"].includes(method);
    if (unsafe) {
      const csrf = await token();
      init.signal?.throwIfAborted();
      headers.set(csrf.headerName, csrf.token);
    }
    const response = await transport(url, {
      ...init,
      method,
      headers,
      credentials: "include",
      redirect: "error",
    });
    if (response.status === 403) {
      let code: unknown;
      try {
        code = (await response.clone().json()).codigo;
      } catch {
        /* preserva resposta original */
      }
      if (code === "CSRF_INVALID") {
        throw new Error(
          "A sessão de segurança mudou ou expirou. Atualize a página e confira a sessão antes de tentar novamente. A operação não foi repetida.",
        );
      }
    }
    return response;
  };
}
