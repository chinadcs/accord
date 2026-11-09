# Supabase local do Accord

Projeto de desenvolvimento do serviço do espaço do casal (S2). **Nada aqui cria ou configura um
projeto hospedado**; isso exige autorização explícita do usuário. Não há e-mails: Confirm Email
fica desativado (`config.toml`, `[auth.email]`) e não há SMTP.

- `migrations/`: tabelas, RLS e funções (`create_space`, `generate_invite`, `join_with_code`,
  `push_operations`, `pull_changes` etc.). Nenhuma escrita direta nas tabelas; `anon` não executa
  nada.
- `tests/database/`: testes pgTAP de permissão, convite e envio (`supabase test db`).
- Portas na faixa 5532x para não colidir com outra instância local do Supabase na mesma máquina.
  Studio, Realtime, Storage e analytics ficam desligados. Edge Functions é habilitado para
  o endpoint `privacy` (ADR 0058), servido localmente por `supabase functions serve`.

## Exportação de dados (S8)

`supabase migration up --local` aplica a migração de exportação; `supabase functions serve`
serve o endpoint local. O CLI desta máquina serve todas as funções sem argumento de nome.
O endpoint valida a sessão no Auth e deriva o titular; `privacy_export` é restrita ao serviço.
`node --experimental-strip-types --test supabase/functions/privacy/handler.test.mjs` exercita
o handler com rede simulada (Node 22), e `SupabaseExportTest` verifica a integração real local.
Os testes desse endpoint exigem o processo de funções ativo; nenhum comando aqui faz deploy.

Os downloads são separados (pessoal/financeiro), com snapshot local e remoto identificados,
sem tokens, senhas, código de convite ou documento bancário completo. A integração futura do
ADR 0054 usa `privacy_consent` e `chat_collection_event`, ambas com `account_id`; enquanto
não existem, a exportação informa a ausência de armazenamento, sem inventar consentimento ou
coleta. O endpoint não concede SELECT dessas tabelas aos clientes.

## Exclusão recuperável (S8)

A migração `20261108210000` adiciona solicitações/avisos com RLS e RPCs exclusivas do serviço.
`privacy` verifica a sessão no Auth e confere senha nas ações de preparar/aprovar. O comprovante
aleatório persistido no AccessStore autoriza apenas continuar a própria exclusão já confirmada;
o banco guarda só o hash. Essa ação funciona depois de remover o Auth. `verify_jwt=false` delega
ao handler a autenticação; não autoriza exportação, aprovação ou consulta sem sessão válida.
Não registrar corpos de requisição, senhas ou comprovantes. `SupabasePrivacyLifecycleTest`
exercita Auth/Edge/SQL locais e remove suas fixtures; pgTAP verifica membros/versões/retomada.
As migrações e a função foram publicadas em 09/11/2026 após autorização explícita;
ver o [relatório S8](../docs/s8-implantacao-hospedada-2026-11-09.md).

```powershell
supabase start            # exige Docker em execução
supabase db reset         # aplica as migrações em um banco limpo
supabase test db          # pgTAP: permissões, convite, envio
supabase status -o json   # URL e chaves LOCAIS de desenvolvimento (não vão para o repositório)
supabase stop
```

A chave secreta (`service_role`/`SECRET_KEY`) existe só no ambiente do responsável pelo piloto e
destes testes locais. O aplicativo leva somente a URL do projeto e a chave pública (publishable).

## Projeto hospedado (Free) e release

**S8, 09/11/2026:** as migrações `20261108200000`, `20261108210000` e `20261109010000`
e a Edge Function `privacy` foram publicadas com autorização explícita. As 15 migrações
locais/remotas estão alinhadas; o serviço recebe protocolo **3** (cofrinhos). S20 FE e S23 FE
atualizados sem limpeza, concluindo `accord-0grs.6`. Protocolos
1/2 são recusados até atualizar o cliente. A configuração
`verify_jwt=false` de `privacy` depende da validação explícita no handler: sessão + reautenticação
nas ações normais; continuação de exclusão apenas com comprovante secreto persistido.
Fumaça HTTP com Auth/Edge/RPC passou local e hospedada, com fixtures removidas.
Evidências e limites no [relatório S8](../docs/s8-implantacao-hospedada-2026-11-09.md).
`node supabase/checks/s8-http.mjs --local` exercita o ambiente local; `--project-ref <ref>`
exige autorização explícita para criar/remover fixtures no ambiente escolhido. O script captura
as chaves pelo CLI em memória, sem imprimi-las. No Windows, se necessário, definir
`ACCORD_SUPABASE_CLI` com o caminho do executável do Supabase.

Projeto hospedado criado pelo usuário (ref `SEU_PROJECT_REF`). Em 08/11/2026 as 8 migrações
foram aplicadas com `supabase db push --db-url <connect string>` (senha do banco só no ambiente da
sessão, nunca em arquivo) e confirmado: RLS ligada nas 6 tabelas de `public`, `anon` sem EXECUTE nas
funções do Accord. **Não** usar `supabase config push`: o `config.toml` tem valores de
desenvolvimento (`site_url` local). Auth se ajusta no painel: **Confirm email desligado**, sem SMTP,
sem login anônimo (Confirm email desligado pelo usuário em 08/11/2026). Fumaça feita pelo usuário
em 08/11/2026 nos Galaxy S20 FE e S23 FE com o release: duas contas, espaço, convite, sincronização
nos dois sentidos e edição em modo avião reconciliada depois (banco: 2 usuários, 1 espaço, 2 membros,
4 registros). O roteiro completo do `accord-chf` (Doze, TalkBack, conflito offline etc.) segue por fazer.

Na continuidade autorizada de 08/11/2026 foram aplicadas as quatro migrações restantes
(`20261108120000` a `20261108150000`), com as 12 versões locais/remotas alinhadas e
fumaça transacional de avaliações aprovada no hospedado. O S20 FE foi atualizado;
o S23 FE não recebeu essa atualização. Desde a publicação S8 acima, o serviço exige protocolo 3.
P2 continua oculta no release. Ver [relatório da implantação](../docs/s7-implantacao-hospedada-2026-11-08.md).

O APK release lê `accord.release.supabase.url`, `accord.release.supabase.publishableKey` e a
assinatura (`accord.release.storeFile`, `storePassword`, `keyAlias`, `keyPassword`) de
`local.properties` ou das variáveis `ACCORD_RELEASE_*`; sem elas `:app:assembleRelease` falha. A
keystore fica fora do repositório (guardar cópia: perdê-la obriga a reinstalar o app e apaga os
dados locais).

```powershell
.\gradlew.bat :app:assembleRelease     # app\build\outputs\apk\release\app-release.apk
adb -s <serial> install -r app\build\outputs\apk\release\app-release.apk
```

## Procedimento: redefinir a senha de uma pessoa (piloto sem e-mails)

Quem esquece a senha recorre ao responsável pelo piloto, fora do aplicativo. Como o e-mail não
comprova posse, o responsável confirma a identidade por um canal próprio antes de agir e, num
ambiente confiável (nunca no celular), redefine a senha da conta pelo painel do serviço ou pela
Admin API com a chave secreta. Depois, a pessoa entra com a senha nova e a fila local recomeça.
Comportamento a verificar no serviço real: se a redefinição revoga as sessões existentes e se a
pessoa deve trocar a senha provisória (pergunta aberta 6 do S2).
