// ADR 0058. Sem SDK/dependências; conta derivada exclusivamente da validação do token no Auth.
const base = Deno.env.get("SUPABASE_URL")!;
const publicKey = Deno.env.get("SUPABASE_ANON_KEY")!;
const secret = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

function reply(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

export async function handle(request: Request): Promise<Response> {
  if (request.method !== "POST") return reply(405, { error: "method_not_allowed" });
  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return reply(401, { error: "session_required" });
  try {
    const authResponse = await fetch(`${base}/auth/v1/user`, {
      headers: { apikey: publicKey, Authorization: authorization },
    });
    if (!authResponse.ok) return reply(401, { error: "session_required" });
    const user = await authResponse.json();
    if (typeof user.id !== "string") return reply(401, { error: "session_required" });
    const text = await request.text();
    if (text.length > 32_768) return reply(413, { error: "request_too_large" });
    const body = JSON.parse(text);
    if (!body || Array.isArray(body) || typeof body !== "object" ||
        Object.keys(body).some((key) => !["action", "space_id"].includes(key))) {
      return reply(400, { error: "invalid_request" });
    }
    const scope = body.action === "export_personal" ? "Personal" : body.action === "export_financial" ? "Financial" : null;
    if (!scope || (scope === "Financial" && typeof body.space_id !== "string")) {
      return reply(400, { error: "invalid_request" });
    }
    const data = await fetch(`${base}/rest/v1/rpc/privacy_export`, {
      method: "POST",
      headers: { apikey: secret, Authorization: `Bearer ${secret}`, "Content-Type": "application/json" },
      body: JSON.stringify({ p_account: user.id, p_scope: scope, p_space: body.space_id ?? null }),
    });
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

