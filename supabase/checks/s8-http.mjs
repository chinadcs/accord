// Fumaça S8 com Auth/Edge/RPC reais e fixtures isoladas. Nunca imprime chaves ou respostas.
// Local: node supabase/checks/s8-http.mjs --local
// Hospedado (exige autorização): node supabase/checks/s8-http.mjs --project-ref <ref>
import { execFileSync } from 'node:child_process';
import { randomUUID, randomBytes } from 'node:crypto';

const args = process.argv.slice(2);
const local = args.length === 1 && args[0] === '--local';
const ref = args.length === 2 && args[0] === '--project-ref' ? args[1] : null;
if (!local && !/^[a-z]{20}$/.test(ref ?? '')) throw new Error('Informe --local ou --project-ref <ref>.');
const cli = process.env.ACCORD_SUPABASE_CLI || 'supabase';
const config = JSON.parse(execFileSync(cli, local ? ['status', '-o', 'json'] :
  ['projects', 'api-keys', '--project-ref', ref, '--reveal', '-o', 'json'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }));
const base = local ? config.API_URL : `https://${ref}.supabase.co`;
const publicKey = local ? config.ANON_KEY : config.find(k => k.name === 'anon')?.api_key;
const secret = local ? config.SERVICE_ROLE_KEY : config.find(k => k.name === 'service_role')?.api_key;
if (!publicKey || !secret) throw new Error('Credenciais do ambiente indisponíveis.');
const users = [], spaces = [], jobs = [];
let assertions = 0;
function check(value, label) { if (!value) throw new Error(label); assertions++; }
async function call(path, body, token, { method = 'POST', admin = false } = {}) {
  const key = admin ? secret : publicKey;
  const response = await fetch(base + path, {
    method, signal: AbortSignal.timeout(30000),
    headers: { apikey: key, ...(token || admin ? { Authorization: `Bearer ${admin ? secret : token}` } : {}), 'Content-Type': 'application/json' },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const text = await response.text();
  let data; try { data = JSON.parse(text); } catch { data = null; }
  return { status: response.status, data };
}
async function ok(path, body, token, options) {
  const r = await call(path, body, token, options);
  check(r.status >= 200 && r.status < 300, `HTTP ${r.status} em ${path.split('?')[0]}`);
  return r.data;
}
const rpc = (name, body, token) => ok('/rest/v1/rpc/' + name, body, token);
const privacy = (body, token) => ok('/functions/v1/privacy', body, token);
async function user() {
  const email = `s8-smoke-${randomUUID()}@exemplo.test`, password = randomBytes(24).toString('hex');
  const created = await ok('/auth/v1/admin/users', { email, password, email_confirm: true }, null, { admin: true });
  users.push(created.id);
  const signed = await ok('/auth/v1/token?grant_type=password', { email, password });
  check(signed.user.id === created.id && !!signed.access_token, 'Login da fixture');
  return { id: created.id, email, password, token: signed.access_token, refresh: signed.refresh_token };
}
async function space(owner) {
  const id = randomUUID(); spaces.push(id);
  const made = await rpc('create_space', { p_space: id, p_name: 'S8 fumaça fictícia' }, owner.token);
  check(made.spaceId === id, 'Criação do espaço isolado'); return id;
}
const change = (type, id, state, version = null) => ({ type, id, state: JSON.stringify(state), baseVersion: version, tombstone: false });
const operation = (s, changes, protocolVersion = 3) => ({ operationId: randomUUID(), spaceId: s, protocolVersion,
  installationId: 's8-deploy-smoke', localTimeMillis: Date.now(), changes });
const push = (u, s, op) => rpc('push_operations', { p_space: s, p_operations: [op] }, u.token);
try {
  check((await call('/functions/v1/privacy', { action: 'export_personal' })).status === 401, 'Exportação sem sessão bloqueada');
  check((await call('/functions/v1/privacy', { action: 'status' }, 'invalid')).status === 401, 'Token inválido bloqueado');
  const a = await user(), b = await user(), outsider = await user(), s = await space(a);
  const invite = await rpc('generate_invite', { p_space: s }, a.token);
  check((await rpc('join_with_code', { p_code: invite.code }, b.token)).spaceId === s, 'Segundo membro');
  const accountId = randomUUID(), pocketId = randomUUID(), moveId = randomUUID();
  const day = Math.floor(Date.now() / 86400000) - 1;
  const account = { id: accountId, name: 'Conta fictícia', initialBalanceCents: 100000, balanceCutoffEpochDay: day, isWallet: false };
  const pocket = { id: pocketId, accountId, name: 'Reserva fictícia', createdBy: a.id,
    initialCents: 50000, cutoffEpochDay: day, goalCents: null, deadlineEpochDay: null, archived: false };
  const seed = operation(s, [change('Account', accountId, account), change('SavingsPocket', pocketId, pocket)]);
  check((await push(a, s, seed)).results[0].result === 'applied', 'Cofrinho protocolo 3');
  check((await push(a, s, seed)).results[0].repeated === true, 'Reenvio idempotente');
  for (const v of [1, 2]) check((await push(a, s, operation(s, [], v))).results[0].reason === 'ProtocolUnsupported', `Protocolo ${v} recusado`);
  check((await push(outsider, s, operation(s, []))).error === 'not_member', 'Não membro não envia');
  const move = { id: moveId, pocketId, accountId, direction: 'Deposit', authorId: a.id, amountCents: 20000, epochDay: day, voided: false };
  const deposit = operation(s, [change('Account', accountId, account, 1), change('SavingsPocket', pocketId, pocket, 1), change('SavingsMove', moveId, move)]);
  check((await push(a, s, deposit)).results[0].result === 'applied', 'Guardar com versões atômicas');
  check((await push(a, s, deposit)).results[0].repeated === true, 'Guardar repetido não duplica');
  const stale = operation(s, [change('Account', accountId, account, 1), change('SavingsPocket', pocketId, pocket, 1)]);
  check((await push(b, s, stale)).results[0].result === 'conflict', 'Versão antiga abre conflito');
  const badId = randomUUID();
  const bad = operation(s, [change('Account', accountId, account, 2), change('SavingsPocket', pocketId, pocket, 2),
    change('SavingsMove', badId, { ...move, id: badId, amountCents: 99999999 })]);
  check((await push(a, s, bad)).results[0].result !== 'applied', 'Saldo insuficiente recusado');
  const pulled = await rpc('pull_changes', { p_space: s, p_after: 0, p_limit: 200 }, b.token);
  check(pulled.changes.filter(c => c.type === 'SavingsMove').length === 1, 'Segundo membro lê um único movimento');
  const personal = (await privacy({ action: 'export_personal' }, a.token)).data;
  const personalText = JSON.stringify(personal);
  check(personal.profile.accountId === a.id && !personalText.includes(b.email) && !personalText.includes(a.token) && !personalText.includes(a.refresh), 'Exportação própria sem tokens');
  const financial = (await privacy({ action: 'export_financial', space_id: s }, b.token)).data;
  check(financial.entities.filter(e => e.type === 'SavingsPocket').length === 1 && financial.entities.filter(e => e.type === 'SavingsMove').length === 1, 'Exportação inclui cofrinho/movimento');
  check((await call('/functions/v1/privacy', { action: 'export_financial', space_id: s }, outsider.token)).status === 403, 'Exportação alheia bloqueada');
  check((await call('/functions/v1/privacy', { action: 'export_personal', account_id: b.id }, a.token)).status === 400, 'Titular forjado bloqueado');
  check((await call('/rest/v1/rpc/privacy_export', { p_account: a.id, p_scope: 'Personal' }, a.token)).status === 403, 'RPC administrativa bloqueada ao cliente');
  const deletion = randomUUID(), proof = randomBytes(32).toString('hex'); jobs.push(deletion);
  check((await call('/functions/v1/privacy', { action: 'prepare_account_deletion', request_id: deletion, proof, password: 'senha-errada' }, a.token)).status === 403, 'Senha incorreta bloqueia exclusão');
  check((await privacy({ action: 'prepare_account_deletion', request_id: deletion, proof, password: a.password }, a.token)).data.status === 'Prepared', 'Exclusão autorizada preparada');
  check((await push(a, s, operation(s, []))).error === 'not_member', 'Token antigo sem acesso após preparo');
  check((await call('/functions/v1/privacy', { action: 'continue_account_deletion', request_id: deletion, proof: 'b'.repeat(64) })).status === 403, 'Comprovante errado bloqueado');
  for (let i = 0; i < 2; i++) check((await privacy({ action: 'continue_account_deletion', request_id: deletion, proof })).data.status === 'Completed', 'Exclusão retomável sem sessão');
  check((await call(`/auth/v1/admin/users/${a.id}`, undefined, null, { method: 'GET', admin: true })).status === 404, 'Auth da fixture removido');
  const kept = (await privacy({ action: 'export_financial', space_id: s }, b.token)).data;
  check(kept.entities.length === 3 && kept.entities.filter(e => ['SavingsPocket', 'SavingsMove'].includes(e.type)).every(e => !e.state.includes(a.id)), 'Finanças preservadas com autoria removida');
  const c = await user(), s2 = await space(c), i2 = await rpc('generate_invite', { p_space: s2 }, c.token);
  await rpc('join_with_code', { p_code: i2.code }, outsider.token);
  const sd = randomUUID(); jobs.push(sd);
  check((await privacy({ action: 'request_space_deletion', request_id: sd, space_id: s2, password: c.password }, c.token)).data.status === 'AwaitingApprovals', 'Exclusão aguarda outro membro');
  check((await privacy({ action: 'approve_space_deletion', request_id: sd, password: outsider.password }, outsider.token)).data.status === 'Completed', 'Aprovação unânime conclui exclusão');
  check((await push(c, s2, operation(s2, []))).error === 'not_member', 'Espaço excluído sem acesso');
  check((await privacy({ action: 'export_personal' }, c.token)).data.profile.accountId === c.id, 'Conta preservada após excluir espaço');
  check((await privacy({ action: 'status' }, outsider.token)).data.closedSpaces.some(x => x.spaceId === s2), 'Aviso para limpeza do aparelho offline');
  console.log(`s8_http: ${local ? 'local' : ref}: ${assertions} verificações aprovadas`);
} finally {
  // Apenas UUIDs criados por esta execução; nenhuma seleção geral ou por conteúdo pessoal.
  for (const s of spaces) {
    const invites = await ok(`/rest/v1/space_invites?space_id=eq.${s}&select=id`, undefined, null, { method: 'GET', admin: true });
    for (const i of invites) await ok(`/rest/v1/space_invite_uses?invite_id=eq.${i.id}`, undefined, null, { method: 'DELETE', admin: true });
    for (const t of ['space_invites', 'applied_operations', 'space_entities', 'space_members'])
      await ok(`/rest/v1/${t}?space_id=eq.${s}`, undefined, null, { method: 'DELETE', admin: true });
    await ok(`/rest/v1/spaces?id=eq.${s}`, undefined, null, { method: 'DELETE', admin: true });
  }
  for (const id of jobs) for (const t of ['privacy_account_deletion', 'privacy_space_deletion'])
    await ok(`/rest/v1/${t}?id=eq.${id}`, undefined, null, { method: 'DELETE', admin: true });
  for (const id of users) {
    for (const t of ['space_invite_uses', 'invite_attempts', 'privacy_space_notice'])
      await ok(`/rest/v1/${t}?account_id=eq.${id}`, undefined, null, { method: 'DELETE', admin: true });
    const removed = await call(`/auth/v1/admin/users/${id}`, undefined, null, { method: 'DELETE', admin: true });
    check([200, 204, 404].includes(removed.status), 'Limpeza Auth');
    const remains = await call(`/auth/v1/admin/users/${id}`, undefined, null, { method: 'GET', admin: true });
    check(remains.status === 404, 'Sem fixture Auth residual');
  }
  for (const s of spaces) for (const t of ['spaces', 'space_members', 'space_entities', 'applied_operations', 'space_invites']) {
    const rows = await ok(`/rest/v1/${t}?${t === 'spaces' ? 'id' : 'space_id'}=eq.${s}&select=*`, undefined, null, { method: 'GET', admin: true });
    check(rows.length === 0, 'Sem fixture financeira residual');
  }
  console.log('s8_http: limpeza das fixtures confirmada');
}
