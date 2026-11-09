import { test, beforeEach, after } from "node:test";
import assert from "node:assert/strict";

// Shim apenas de ambiente para executar o handler TypeScript com Node 22; nenhum segredo real.
globalThis.Deno = { env: { get: (name) => ({
  SUPABASE_URL: "http://servico-teste.invalid", SUPABASE_ANON_KEY: "public-test", SUPABASE_SERVICE_ROLE_KEY: "service-test",
})[name] } };
const { handle } = await import("./handler.ts");
const originalFetch = globalThis.fetch;
let calls;
beforeEach(() => {
  calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return new Response(JSON.stringify(url.endsWith("/auth/v1/user") ? { id: "person-a" } : { scope: "Personal" }), { status: 200 });
  };
});
after(() => { globalThis.fetch = originalFetch; });
const request = (body, authorization = "Bearer session-test") => new Request("http://app.invalid/privacy", {
  method: "POST", headers: authorization ? { Authorization: authorization } : {}, body: JSON.stringify(body),
});

test("não chama serviço sem sessão", async () => {
  assert.equal((await handle(request({ action: "export_personal" }, null))).status, 401);
  assert.equal(calls.length, 0);
});
test("titular vem da sessão verificada e resposta não fica em cache", async () => {
  const result = await handle(request({ action: "export_personal" }));
  assert.equal(result.status, 200);
  assert.equal(result.headers.get("Cache-Control"), "no-store");
  assert.equal(JSON.parse(calls[1].options.body).p_account, "person-a");
  assert.equal(calls[1].options.headers.Authorization, "Bearer service-test");
});
test("não aceita titular forjado no corpo", async () => {
  assert.equal((await handle(request({ action: "export_personal", account_id: "person-b" }))).status, 400);
  assert.equal(calls.length, 1);
});
test("sessão inválida nunca chega ao RPC administrativo", async () => {
  globalThis.fetch = async () => new Response("{}", { status: 401 });
  assert.equal((await handle(request({ action: "export_personal" }))).status, 401);
});
test("financeiro exige espaço e respeita recusa de associação", async () => {
  assert.equal((await handle(request({ action: "export_financial" }))).status, 400);
  globalThis.fetch = async (url) => new Response(JSON.stringify(url.endsWith("/auth/v1/user") ? { id: "person-a" } : { code: "42501", details: "não expor" }),
    { status: url.endsWith("/auth/v1/user") ? 200 : 403 });
  const result = await handle(request({ action: "export_financial", space_id: "space-b" }));
  assert.equal(result.status, 403);
  assert.equal((await result.text()).includes("não expor"), false);
});
test("falha de rede devolve erro recuperável sem detalhes", async () => {
  globalThis.fetch = async () => { throw Error("diagnóstico privado"); };
  const result = await handle(request({ action: "export_personal" }));
  assert.equal(result.status, 503);
  assert.equal((await result.text()).includes("diagnóstico"), false);
});
