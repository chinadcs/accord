// ADR 0058. Sem SDK/dependências; conta derivada exclusivamente da validação do token no Auth.
const base = Deno.env.get("SUPABASE_URL")!;
const publicKey = Deno.env.get("SUPABASE_ANON_KEY")!;
const secret = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

function reply(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

async function rpc(name: string, params: unknown): Promise<Response> {
  return await fetch(`${base}/rest/v1/rpc/${name}`, {
    method: "POST", headers: { apikey: secret, Authorization: `Bearer ${secret}`, "Content-Type": "application/json" },
    body: JSON.stringify(params),
  });
}

function lifecycle(data: unknown): Response {
  return reply(200, { data: { format: "accord-privacy-lifecycle", version: 1, ...(data as object) } });
}

export async function handle(request: Request): Promise<Response> {
  if (request.method !== "POST") return reply(405, { error: "method_not_allowed" });
  try {
    const text = await request.text();
    if (text.length > 32_768) return reply(413, { error: "request_too_large" });
    const body = JSON.parse(text);
    if (!body || Array.isArray(body) || typeof body !== "object" ||
        Object.keys(body).some((key) => !["action", "space_id", "request_id", "proof", "password"].includes(key))) {
      return reply(400, { error: "invalid_request" });
    }
    // Comprovante limitado à exclusão já autorizada: funciona depois da remoção da própria sessão.
    if (body.action === "continue_account_deletion") {
      if (typeof body.request_id !== "string" || typeof body.proof !== "string" || body.proof.length < 64) {
        return reply(400, { error: "invalid_request" });
      }
      const purge = await rpc("privacy_purge_account", { p_id: body.request_id, p_proof: body.proof });
      if (!purge.ok) return reply(purge.status === 403 ? 403 : 503, { error: "deletion_not_available" });
      const job = await purge.json();
      if (job.status === "Completed") return lifecycle({ requestId: body.request_id, status: "Completed" });
      if (typeof job.accountId !== "string") return reply(503, { error: "service_unavailable" });
      const remove = await fetch(`${base}/auth/v1/admin/users/${encodeURIComponent(job.accountId)}`, {
        method: "DELETE", headers: { apikey: secret, Authorization: `Bearer ${secret}` },
      });
      if (!remove.ok && remove.status !== 404) return lifecycle({ requestId: body.request_id, status: "AuthPending" });
      const complete = await rpc("privacy_complete_account", { p_id: body.request_id, p_proof: body.proof });
      if (!complete.ok) return lifecycle({ requestId: body.request_id, status: "AuthPending" });
      const result = await complete.json();
      return lifecycle({ requestId: body.request_id, status: result.status });
    }
    const authorization = request.headers.get("Authorization");
    if (!authorization?.startsWith("Bearer ")) return reply(401, { error: "session_required" });
    const authResponse = await fetch(`${base}/auth/v1/user`, {
      headers: { apikey: publicKey, Authorization: authorization },
    });
    if (!authResponse.ok) return reply(401, { error: "session_required" });
    const user = await authResponse.json();
    if (typeof user.id !== "string") return reply(401, { error: "session_required" });
    if (["prepare_account_deletion", "request_space_deletion", "request_space_reset", "approve_space_deletion"].includes(body.action)) {
      if (typeof body.password !== "string" || !body.password || typeof user.email !== "string") {
        return reply(400, { error: "password_required" });
      }
      const reauth = await fetch(`${base}/auth/v1/token?grant_type=password`, {
        method: "POST", headers: { apikey: publicKey, "Content-Type": "application/json" },
        body: JSON.stringify({ email: user.email, password: body.password }),
      });
      if (!reauth.ok) return reply(403, { error: "invalid_credentials" });
      const fresh = await reauth.json();
      if (fresh.user?.id !== user.id || typeof fresh.access_token !== "string") return reply(403, { error: "invalid_credentials" });
      // Elimina apenas a sessão temporária criada para conferir a senha.
      await fetch(`${base}/auth/v1/logout?scope=local`, {
        method: "POST", headers: { apikey: publicKey, Authorization: `Bearer ${fresh.access_token}` },
      }).catch(() => undefined);
    }
    if (body.action === "status") {
      const status = await rpc("privacy_status", { p_account: user.id });
      if (!status.ok) return reply(503, { error: "service_unavailable" });
      return lifecycle(await status.json());
    }
    if (body.action === "prepare_account_deletion") {
      if (typeof body.request_id !== "string" || typeof body.proof !== "string" || body.proof.length < 64) return reply(400, { error: "invalid_request" });
      const prepared = await rpc("privacy_prepare_account", { p_account: user.id, p_id: body.request_id, p_proof: body.proof });
      if (!prepared.ok) return reply(prepared.status === 403 ? 403 : 503, { error: "deletion_not_available" });
      return lifecycle(await prepared.json());
    }
    if (["request_space_deletion", "request_space_reset", "approve_space_deletion", "cancel_space_deletion"].includes(body.action)) {
      if (typeof body.request_id !== "string") return reply(400, { error: "invalid_request" });
      const result = await rpc("privacy_space_action", {
        p_account: user.id, p_action: body.action, p_id: body.request_id, p_space: body.space_id ?? null,
      });
      if (!result.ok) return reply(result.status === 403 ? 403 : 503, { error: "not_authorized" });
      return lifecycle(await result.json());
    }
    const scope = body.action === "export_personal" ? "Personal" : body.action === "export_financial" ? "Financial" : null;
    if (!scope || (scope === "Financial" && typeof body.space_id !== "string")) {
      return reply(400, { error: "invalid_request" });
    }
    const data = await rpc("privacy_export", { p_account: user.id, p_scope: scope, p_space: body.space_id ?? null });
    if (!data.ok) {
      const cause = await data.json().catch(() => ({}));
      return reply(cause.code === "42501" ? 403 : 503, { error: cause.code === "42501" ? "not_authorized" : "service_unavailable" });
    }
    return reply(200, { data: await data.json() });
  } catch (error) {
    // Nenhum corpo, token, texto pessoal ou diagnóstico interno é registrado/devolvido.
    return reply(error instanceof SyntaxError ? 400 : 503, { error: error instanceof SyntaxError ? "invalid_request" : "service_unavailable" });
  }
}

