# Supabase local do Accord

Projeto de desenvolvimento do serviço do espaço do casal (S2). **Nada aqui cria ou configura um
projeto hospedado**; isso exige autorização explícita do usuário. Não há e-mails: Confirm Email
fica desativado (`config.toml`, `[auth.email]`) e não há SMTP.

- `migrations/`: tabelas, RLS e funções (`create_space`, `generate_invite`, `join_with_code`,
  `push_operations`, `pull_changes` etc.). Nenhuma escrita direta nas tabelas; `anon` não executa
  nada.
- `tests/database/`: testes pgTAP de permissão, convite e envio (`supabase test db`).
- Portas na faixa 5532x para não colidir com outra instância local do Supabase na mesma máquina.
  Os serviços não usados (Studio, Realtime, Storage, Edge Functions, analytics) ficam desligados.

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

Projeto hospedado criado pelo usuário (ref `SEU_PROJECT_REF`). Em 08/11/2026 as 8 migrações
foram aplicadas com `supabase db push --db-url <connect string>` (senha do banco só no ambiente da
sessão, nunca em arquivo) e confirmado: RLS ligada nas 6 tabelas de `public`, `anon` sem EXECUTE nas
funções do Accord. **Não** usar `supabase config push`: o `config.toml` tem valores de
desenvolvimento (`site_url` local). Auth se ajusta no painel: **Confirm email desligado**, sem SMTP,
sem login anônimo (Confirm email desligado pelo usuário em 08/11/2026). Fumaça feita pelo usuário
em 08/11/2026 nos Galaxy S20 FE e S23 FE com o release: duas contas, espaço, convite, sincronização
nos dois sentidos e edição em modo avião reconciliada depois (banco: 2 usuários, 1 espaço, 2 membros,
4 registros). O roteiro completo do `accord-chf` (Doze, TalkBack, conflito offline etc.) segue por fazer.

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
