# AGENTS.md

This file provides shared guidance to Codex and Claude Code when working in this repository.
`CLAUDE.md` imports this file; Codex reads it directly. Keep project rules here so both
assistants use the same source. Read any nested `AGENTS.md` for the area being changed.

Local agent and skill customizations, when installed, are described in
`.claude/agents-roster.md`, including the Codex adapters and shared skill discovery. These
machine-local files remain gitignored; a checkout without them still uses this document.

## What this is

`worldforge-api` — a FastAPI service over a tabletop-RPG campaign corpus (Supabase/Postgres;
Cloudflare R2 for images), plus a Next.js reader in `web/` called the Almanac. Python 3.12,
`uv` for dependencies, Vercel for deployment (`api/` for the API, `web/` for the web app).

**The whole backend lives in `api/`** — `app/`, `scripts/`, `tests/`, `pyproject.toml`,
`uv.lock`, `.env`, `.venv`. Every `app/…`, `scripts/…` and `tests/…` path in this file and in
the docs is relative to `api/`, and every Python command runs from there (`settings.py` reads
`.env` from the working directory). `supabase/` stays at the repository root, shared by both
apps, and `supabase` commands run from the root. There are two `.env` files: `api/.env` for
the API, tests and scripts; the root `.env` for the Supabase CLI's Discord OAuth keys.

The constraint that shapes nearly every design decision: **a player must never see GM-only
content.** Secrecy is enforced twice by mechanisms that share no code, and each half is tested
with the other switched off.

`web/` has its own `CLAUDE.md` → `AGENTS.md`, written and re-added by `next dev`. Read it
before writing Next.js code; do not delete it from a diff, and do not duplicate it here.

## Read before changing anything in an area

| File                                                           | What it is                                                                                                                                                           |
| -------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `CONTRACTS.md`                                                 | Normative cross-spec contracts. "The constitution takes precedence."                                                                                                 |
| `STATE.md`                                                     | Decisions that are deliberate, still open, and "would otherwise be rediscovered as bugs."                                                                            |
| `README.md`                                                    | The long-form design record. `## Layout` is the module map; `### Changing the schema`, `## Row level security`, `## Test` and `## Errors` are the operational rules. |
| `docs/almanac.md`, `docs/almanac-api.md`, `docs/typography.md` | The Almanac layer, newer than README's Layout section.                                                                                                               |

Work is organised by **numbered specs** — 005 RLS, 006 attributes, 009 JWT, 010 profiles,
011 R2, 012 ingest, 013 reads, 014 assets, 015 the leak gate, 016 writes, 017 sign-in,
018 reader, 019 deployment, 020 dev gates. Acceptance items inside a spec are cited as
"criterion N". The numbers are the project's shared vocabulary throughout code, tests, docs and
commits, even though the `specs/` directory `CONTRACTS.md` points at is **not on disk**.

Also note: the Almanac work is committed, but `README.md` § Layout was written before it and
still does not list `app/domain/modules/almanac/` or its routes; `docs/almanac.md` and
`docs/almanac-api.md` are the map for that layer.

## Commands

```bash
cd api
uv sync                                                      # deps (creates api/.venv)
.venv/Scripts/python.exe -m uvicorn app.main:app --reload --port 8000
.venv/Scripts/python.exe -m pytest -q                        # whole suite
python -m pytest tests/api/test_entities.py -q                # one file
python -m pytest tests/api/test_entities.py::test_name -q     # one test
python -m pytest -q --ignore=tests/leak                       # what public CI runs
```

Database (needs Docker + Supabase CLI ≥ 2.81.3), from the repository root:

```bash
supabase start
supabase db reset     # migrations, THEN seed.sql — the seed sets worldforge_api's password
supabase status       # ports and keys; Postgres is on 54322
```

Use `db reset`, not `migration up`: `migration up` skips seeds, leaving the API's login with no
password, which looks like a broken build rather than a missing setup step. `supabase/seed.sql`
carries the one-line `alter role` to run by hand if you do.

Web (`cd web` — its own package and lockfile):

```bash
npm ci
npm run dev             # localhost:3000  (NOT 127.0.0.1 — origins are exact everywhere)
npm run lint
npx tsc --noEmit
npm test                # tsx --test tests/*.test.tsx — no backend needed
npm run build
npm run check:secrets   # scans .next/static/ for a leaked secret key; run AFTER build
npm run test:e2e        # playwright
```

Full local verification (`docs/almanac.md` § Verification), from `api/` — point both test
URLs at a **disposable** database first:

```bash
python -m scripts.verify_almanac_database
python -m pytest tests/domain tests/infra tests/api tests/db -q
python -m pytest tests/leak -q     # on a separate disposable corpus snapshot
```

Run the profile-authorization tests and the browser fixtures **sequentially** against one
disposable database — both create approved GM accounts. Run the leak gate after the browser
suite, never concurrently.

`.\dev-up.ps1` starts Docker, MinIO, Supabase, the API and the web app in one go;
`.\dev-urls.ps1` prints what is up. Both are gitignored local helpers.

**There is no Python linter or formatter configured** — no ruff, black, mypy or pre-commit.
Match the surrounding style; do not introduce one unasked.

Gates (020), from the repository root. The four scripts are bash, in `scripts/`:

```bash
just pr-size      # churn vs the base; WARN above 800 churn / 20 files, FAIL above 1500 / 30
just docs-check   # backticked paths, `just` recipes, links and ADRs that point at nothing
just vuln         # pip-audit over api/uv.lock + npm audit over web/; runtime deps only; network
just mutants "app.domain.shared.visibility*"   # mutmut over app/domain/, in Docker
```

On Windows `bash` in PowerShell is **WSL's launcher**, and the only distro here is
docker-desktop, which cannot run a script. The justfile runs Git Bash instead, found by walking
up from the `git` on PATH; `tests/tooling/` resolves it the same way. Call the scripts through
`just`, or through Git Bash directly — never through a bare `bash` from PowerShell.
`pr-size` measures against `HEAD` (the uncommitted work) unless `PR_SIZE_BASE` or an upstream
says otherwise. `just mutants` needs Docker running; `VAULT_ROOT=off just mutants` skips the
vault, and mutmut refuses to start while any selected test fails unmutated.

## Architecture

```
app/api/      HTTP: routers (one module per resource in path/), Pydantic wire DTOs in
              schemas/, deps.py, middlewares/       → may import domain and infra
app/domain/   pure Python: value objects (frozen dataclasses), Protocol ports, rules,
              predicates; the aggregates are mutable  → imports NOTHING outward
app/infra/    SQLAlchemy adapters, JWKS/httpx, aioboto3/PIL, the session seam
                                                    → may import domain
app/config/   settings.py + dependency.py, the composition root
```

"Frozen" describes the value objects, not everything in domain: `Entity`
(`app/domain/modules/entity/entity.py`), `Profile` (`app/domain/modules/user/profile.py`) and
`BaseEntity` (`app/domain/shared/base_entity.py`) are `@dataclass(eq=False)` and mutable,
compared by identity rather than by field.

### Two invariants enforced by tests, not by review

**1. `domain/` imports nothing outward** (`tests/test_layering.py` AST-parses every
`app/domain/**/*.py`):

```python
FORBIDDEN = ("app.api", "app.infra", "fastapi", "sqlalchemy",
             "boto3", "aioboto3", "botocore", "jwt", "httpx", "PIL")
```

Pydantic is allowed and used freely in domain. Every import is resolved to the module it loads
before it is matched, on a dot boundary: a relative import with enough dots to climb out of
`app.domain`, `from app import infra`, and a literal `importlib.import_module(...)` or
`__import__(...)` are all caught, and a loader given a computed name is flagged rather than
passed. Still unseen: `import app` followed by attribute access, an aliased loader, and
`from app import *`.

The split is by role, not by taste: _naming_ an object's R2 key is domain, _signing_ a URL or
decoding uploaded bytes is contact with the outside world and stays in infra. That is what lets
the read layer be tested against a dict instead of a bucket.

**2. The owner credential stays out of `app/`** (`tests/test_owner_connection.py` greps for the
literal string):

```python
assert modules_naming_the_owner_url() == ["app/config/settings.py"]
```

Two logins, and the whole point is that they never meet:

|         | API runtime                                                                               | owner                                                 |
| ------- | ----------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| setting | `DATABASE_URL` (env alias `DB_URL`)                                                       | `DATABASE_URL_OWNER` (alias `OWNER_DB_URL`), optional |
| role    | `worldforge_api` — no BYPASSRLS, no table privileges, NOINHERIT member of `authenticated` | schema owner, BYPASSRLS                               |
| used by | every request                                                                             | `scripts/ingest_vault.py`, `retry_object_cleanup.py`, `verify_almanac_corpus.py`, migrations, `psql` |

A read path that forgets the session seam gets `permission denied` rather than being served
from outside every policy. It is a separate setting rather than a flag on the shared URL so
that forgetting it is a grep, not a missing argument.

### The request chain — `app/api/deps.py`

```
verify the token          → current_user      (a subject, and nothing else)
open one transaction      → request_session   (verified claims set on it)
provision or load the row → current_profile   (then enter the authenticated role)
enforce                   → require_approved / require_gm
```

**Authority comes from step three — a table read on every request — never from the token.**
`profiles.role` and `profiles.approved` are re-read per request, so an approval or a demotion
applies on the caller's very next request with the token they already hold. Discord metadata in
the token is decoration, written once on insert, and decides nothing.

Routes declare their door in the signature via the `Annotated` aliases `deps.py` exports —
`ApprovedProfile`, `GmProfile`, `RequestSession`, `EntityReads`, `EntityWrites`, `AssetOps` —
not with checks in the body.

### The RLS seam — `app/infra/database/alchemist/session.py`

The service is not PostgREST, so nothing establishes `auth.uid()` for it:

```sql
select set_config('request.jwt.claims', :claims, true);   -- set_request_claims
set local role authenticated;                             -- assume_authenticated
```

`SET LOCAL` is transaction-scoped, which is the safety argument under PgBouncer transaction
pooling. The identity is a **required** argument — there is no identity-free door into
`authenticated`. The two steps are split across `request_session` and `current_profile` because
the API's login is NOINHERIT and must stay outside `authenticated` long enough to call
`private.provision_profile()`. `private_read()` in `app/infra/adapters/private_reads.py` is the
narrow escape hatch that steps out and back for one statement.

One request is one transaction, and **callers never commit** — leaving the block commits. There
are two deliberate exceptions, both in `app/api/aggregate.py`: `entity_mutation` commits content
and history *before* the post-commit R2 object cleanup, and `bulk_cleanup` does the same for bulk
deletes, so that a provider outage cannot roll back a deletion whose references are already
gone. If the cleanup fails the response is `202 cleanup_pending` and
`scripts/retry_object_cleanup.py` finishes it. No route or repository adds a third.

### Dependency injection — `app/config/dependency.py`

The container holds only what is expensive to build and safe to share: `settings`, `engine`,
`session_factory`, `jwt_verifier` (one JWKS cache per container, budgeted to two fetches per
interval), `object_store` (the botocore client costs ~400 ms). **Everything per-request —
repositories, services — is a plain FastAPI `Depends` factory in `deps.py`, never a container
provider.** A singleton there would be a session shared between requests.

## Secrecy: the rule that matters most

Every GM-only fact is withheld twice, by two mechanisms that share no code:

1. **RLS in SQL**, written against `profiles` — `supabase/schemas/06`–`09` laid it down, and
   `14`–`17` rewrite it: `14_almanac_policies` (parent-visible child policies, the Almanac
   tables), `15_almanac_writes` (generated), `16_private_reads` (column grants and the
   `SECURITY DEFINER` readers), `17_table_tools`.
2. **`app/domain/shared/visibility.py`**, written against `Role` — one predicate,
   `may_see(is_secret, role)`, plus `filter_blocks` / `filter_entities` / `filter_links` /
   `filter_assets` / `filter_attributes`.

Working rules that follow:

- **Hidden and nonexistent are the same 404**, one branch, one fixed message — a 403 confirms
  the thing exists.
- **Filter before paging, and filter again after.** A page filtered after `limit` leaks how many
  rows were removed. Positions are **never renumbered**: a player's blocks are a subsequence
  with gaps.
- **Filter, then mint.** A presigned URL is a bearer grant nothing can withdraw; hiding an image
  stops new grants and cannot revoke issued ones.
- **Links have three conditions**, not one: secret link, unresolved target, _or_ secret target —
  a public link pointing at a secret entity hands a player that entity's slug.
- **Player DTOs have no `is_secret` field at all**, and GM/player variants are separate classes
  not related by inheritance. A secret row reaching a player DTO raises `RedactionError` (a
  `RuntimeError`, deliberately not a `DomainError`) → 500.
- **Never `select *` in a read path.** `vault_path`, `content_hash`, `r2_key` are never loaded
  by read repositories: a value not in the domain object cannot reach a log or a DTO.
- New content **defaults to secret** until published, and `is_secret=false` cannot override a
  detected secret — that is a 422.
- **RLS hides rows, not JSON keys.** Public attribute keys come from the reviewed registry in
  `app/domain/modules/attributes/registry.py`; declaring a field _is_ the review.

`tests/db/` imports **no application code at all** — that is what lets it say which of the two
layers held.

## Adding an endpoint

Copy the `entities` slice; it is the reference implementation:

`app/api/path/entities.py` → `app/api/schemas/entities.py` →
`app/domain/modules/entity/{entity,service,repository}.py` →
`app/infra/adapters/entities/repository.py` → registered in `app/main.py:create_app`.

Conventions: ports are `typing.Protocol` in domain, implemented as `Sql<X>Repository` in infra
without inheriting; use cases are `<X>Service`; DTOs are `<X>View` / `Gm<X>View` /
`<X>CreateRequest`; error strings are module-level constants; package `__init__.py` files are
curated public APIs with `__all__`. Read and write are **separate ports** over the same session,
so a read path cannot acquire an R2 key by importing something convenient.

Newer Almanac routes (`almanac.py`, `almanac_records.py`, `atlas.py`, `features.py`,
`keeper.py`, `mentions.py`, `party.py`, `relations.py`) use `RequestSession` with inline
`text()` SQL rather than a Protocol port. The strict split is fully
observed in `entity`, `asset` and `user`; follow whichever the module you are in uses.

Reuse rather than reinvent: `app/api/pagination.py` (filter-bound opaque keyset cursors),
`app/domain/modules/user/access.py` (`ensure_approved`, `ensure_approved_gm`),
`app/domain/shared/visibility.py`, and `registry.validate_attributes` — _the_ single entry
point, called by both the ingest script and the write API, asserted to be the same function
object by `tests/domain/test_shared_validation.py`.

Entity mutations are wrapped by the router-level dependency in `app/api/aggregate.py`, which
locks the parent `FOR UPDATE`, checks `If-Match` (428 absent / 412 stale), advances the
revision, sets the strong `ETag` (`"<entity-uuid>:<revision>"`) and writes history. Don't
hand-roll that per route.

Errors: raise a `DomainError` subclass from `app/domain/shared/exceptions.py`;
`app/api/middlewares/error_handler.py` maps it by walking the MRO (`NotFoundError`→404,
`ConflictError`→409, `ValidationError`→422, `AuthenticationError`→401,
`PermissionDeniedError`→403, `PreconditionRequired`→428, `StaleRevision`→412). **Route handlers
contain no `try`/`except HTTPException`.** Domain error messages are the API contract — except
401, which is always one fixed body, and 500, which says only "Internal server error".

## Changing the database schema

`supabase/schemas/` is the source of truth. `supabase/migrations/` is **generated**.

```bash
supabase db diff -f <name>
supabase migration up
supabase db diff              # must print "No schema changes found"
supabase db advisors --local  # no errors
```

- **Never hand-write a migration; never iterate with `apply_migration`** — it writes a history
  entry per call, and `db diff` then produces empty or conflicting output forever.
- `db diff`, not `db pull` — `pull` reads schema _out of_ a database, which is backwards here.
- `db diff` re-emits `10_profile_routines.sql` as `CREATE OR REPLACE` every run. Tool noise;
  drop those statements from the generated migration.
- **Two `schemas/` files are script-generated — do not hand-edit**:
  `12_attribute_registry.sql` (`python -m scripts.generate_public_registry`) and
  `15_almanac_writes.sql` (`python -m scripts.generate_almanac_policies`).
- Three migrations are deliberately hand-assembled and say so: the API runtime role (a role is a
  cluster object no declarative file can declare), the privilege revoke, and the table-tools
  migration. See `STATE.md`.
- The files-versus-migrations drift check is **currently unavailable** (CLI ≥ 2.117). The
  substitute is `supabase db reset`, then `tests/db/` — those tests assert the privileges the
  files describe.
- `supabase db reset` before trusting a green privilege test; an existing volume keeps grants
  from older image defaults.

## Tests

`pyproject.toml` sets `asyncio_mode = "auto"` — write a bare `async def test_…`, **never**
`@pytest.mark.asyncio`. Loop scope is per-function, so engines are built per test.

| Tier                                                       | Needs                                                                                        | Gate                         |
| ---------------------------------------------------------- | -------------------------------------------------------------------------------------------- | ---------------------------- |
| `tests/domain/`                                            | nothing; two thirds walks the real vault                                                     | `VAULT_ROOT`                 |
| `tests/infra/`                                             | live Postgres (`test_r2.py` mostly doesn't)                                                  | both test URLs, `R2_*`       |
| `tests/api/`                                               | live Postgres (except `test_jwt.py`, `test_entity_schemas.py`, `test_entity_type_routes.py`) | both test URLs               |
| `tests/db/`                                                | live Postgres, raw asyncpg, **imports no app code**                                          | both test URLs               |
| `tests/leak/`                                              | the imported campaign corpus                                                                 | both test URLs, `VAULT_ROOT` |
| `tests/web/serve.py`                                       | not pytest — the uvicorn app Playwright launches on :8018                                    | both test URLs               |
| `tests/test_layering.py`, `tests/test_owner_connection.py` | nothing — they parse source                                                                  | —                            |
| `tests/tooling/`                                           | git and Git Bash — 020's gate scripts, run against throwaway repositories                    | Git Bash found               |

**The env-var rule**, restated in four conftests and in CI: `TEST_DATABASE_URL` and
`TEST_API_DATABASE_URL` are read **by those exact names and never derived from `DB_URL`**.
Unset → skip. Set to a non-loopback host → **raise**, not skip. `VAULT_ROOT` is the same shape:
unset skips, not-a-directory raises, because a mistyped root that walked nothing would look
exactly like a clean run. `TEST_DATABASE_URL` is the **owner** (fixtures seed committed rows,
since the app opens its own connections); `TEST_API_DATABASE_URL` is the **service login**, so
the suite measures the credential production uses.

Traps worth knowing before you write a test:

- **`tmp_path` is unusable on this machine** — `%TEMP%\pytest-of-SEU_USUARIO` has an ACL that denies
  access, so every test requesting it errors during setup. Use the `scratch` fixture in
  `tests/domain/conftest.py`.
- **The root `tests/conftest.py` `client` fixture builds the app from `.env`**, not from the test
  database. Every DB-backed API module shadows it with its own. Copy the skeleton from
  `tests/api/test_entities.py` rather than taking the root fixture.
- Override the container, not FastAPI:
  `app.state.container.jwt_verifier.override(providers.Object(...))`. Wrap the client in
  `app.router.lifespan_context(app)` or the engine is never disposed, and pass
  `raise_app_exceptions=False` so an unhandled exception arrives as the 500 a real client sees.
- **Everything written into the shared local database carries a per-run slug suffix and its own
  entity type.** That database also holds the 390-entity imported vault, one of which is
  genuinely called `sirion`. Clean up by UUID; **never truncate**.
- To disable RLS for a bypass experiment, apply a **disposable permissive policy**
  (`alter policy "read_visible" … using (true)`). Dropping the policy fails _closed_ and makes
  the experiment prove nothing.
- Monkeypatch the **importing module's** namespace, not the definition site.

Reuse rather than rebuild: `tests/api/tokens.py` (`SigningKey`, `auth`, `NOWHERE` — an
in-process keypair and JWKS, because the local stack signs HS256 and this service verifies JWKS
only), `tests/api/campaign.py` (`open_campaign`, `disposable_url`, `libpq`, `sqlalchemy_url`,
and the fixture campaign builders), `tests/support/object_store.py` (`FakeObjectStore` — a real
fake with expiring signed URLs and deliberately no notion of a reader),
`tests/support/party_state.py`, `tests/support/routes.py` (`api_routes`, `mutating_routes` — the
single route walker), `tests/support/editor_client.py` (`edit()` injects `If-Match`),
`tests/support/unrestricted_repository.py`.

**The leak gate** (`tests/leak/`) sweeps the whole imported corpus once into a session-scoped
`Sweep`, then runs nine _synchronous_ checks over the captured responses. It needs the GM's real
campaign, which is not in this repo and must not be. On a runner with no `CAMPAIGN_DUMP_URL` the
CI job **fails loudly rather than skipping** — "missing external verification is pending, never
silently green." `test_the_suite_can_fail.py` breaks it four ways on purpose to prove the two
layers are independent: domain filter off → still green, RLS bypassed → still green, both → red.
`test_vault_expectations.py` re-derives the truth from the authored Markdown with a deliberately
simpler scanner, so it must not import the parser.

**Every guard ships a companion that proves it bites** — "a guard that has never been observed
failing is not known to work." `README.md` § _Tests that prove themselves_ has the full table.
Add the companion when you add the guard. Likewise, every negative assertion needs its positive
half: "sees 0 secret rows" alone is equally satisfied by `using (false)`.

## Web (`web/`)

Next 16 + React 19, Tailwind v4, Supabase Auth via Discord OAuth. Read `web/AGENTS.md` first.

- **Three Supabase clients, three modules, never mixed**: `lib/supabase/client.ts` (browser),
  `server.ts` (Server Components / Actions / Route Handlers), `proxy.ts` (Next 16's middleware —
  the only one that can persist a refreshed session). Mixing them produces a session that works
  in `next dev` and is empty on a server render. In the proxy, nothing may `await` between
  `createServerClient` and the auth call, and `supabaseResponse` must be returned as constructed
  or the `Set-Cookie` headers are dropped.
- **Which auth call where**: `getClaims()` in the proxy; `getUser()` (network-verified) on a page
  that decides what to render; `getSession()` in `lib/api.ts` and nowhere else, to attach a
  bearer token for the API to verify.
- **Data fetching — reuse these, don't reinvent**: `lib/api.ts` (`apiFetch`/`apiJson`, always
  `cache: "no-store"`, Supabase client passed as a parameter, no unauthenticated fallback);
  `lib/reader.ts` (`server-only`; `getReader()` is `React.cache`d per render, `readCampaign()`
  returns `null` for an unapproved profile rather than throwing); `lib/mutations.ts`
  (`campaignRequest` sets `If-Match`; **every user-facing error string goes through
  `problem()`**); `lib/entities.ts` / `lib/almanac.ts` for URL and ETag builders.
- Pages are `async` Server Components and stay thin; interactivity lives in `components/` behind
  `"use client"`. Route groups share one shell — the difference is the streaming boundary:
  `(reader)` has `loading.tsx`, `(catalog)` and `(dashboard)` finish their reads before
  rendering so filters work without JavaScript. Every page re-checks `profile.approved`
  independently, because pages execute independently of layouts.
- Markdown is `react-markdown` + `remark-gfm` + **`rehype-sanitize` with raw HTML disabled**, and
  wikilinks resolve only from API-supplied destinations — no slug is ever guessed.
- `npm run check:secrets` scans `.next/static/` for `sb_secret_` **with key material** and for
  `service_role` JWTs. A secret key in a client bundle is a published credential: rotate it, then
  find the `NEXT_PUBLIC_` prefix that put it there. Only three `NEXT_PUBLIC_` vars are
  legitimate: `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `API_URL`.
- Playwright needs `api/.venv` and a live local Postgres — it launches
  `python -m tests.web.serve` (the real app, real JWT verification, real RLS; only JWKS and the
  object store are doubles). Traces are off on purpose: browser responses contain the campaign.
- `web/app/theme.css` is the single source of truth for typography. Use the semantic classes and
  tokens; never raw `font-size`/`font-family`/`font-weight`/`line-height`/`letter-spacing` or
  arbitrary typography utilities, and don't add a size step for a one-off. See
  `docs/typography.md` and the live `/typography` route.

## Conventions

- **Commits**: `NNN: lowercase clause, and a second clause naming the surprise`. No
  Conventional-Commits type, no scope, no trailing period, no trailers. The body is long prose
  (~95 columns, no bullets), third person, and gives the _measurement_ behind each decision —
  concrete numbers, named files and test names, plus what was deliberately **not** done.
  E.g. `016: the write API, and the clock it starts on the vault`.
- **Comments explain why, at length**, cite the spec number, and record what was measured. That
  is the house style, not clutter — match the density rather than stripping it.
- **`STATE.md` ships with the work.** When you make a deliberate trade-off or leave a known gap,
  add an entry: a heading stating the problem, then `**Since:** … · **Closed by:** …`
  (optionally `**Mitigated by:**` / `**Must be closed before:**`). When it closes, **delete the
  entry** rather than annotating it as resolved — git remembers.
- Prefer restating a rule in a second module over importing it, where importing would make the
  test a tautology or force a package to take a dependency it exists to avoid.
- **A new setting is two edits**: a `Settings` field in `app/config/settings.py` and a line in
  `api/.env.example`. Every current field appears in that file (`DATABASE_URL` and
  `DATABASE_URL_OWNER` under their `.env` aliases, `DB_URL` and `OWNER_DB_URL`), so a setting
  present in one and absent from the other is the drift, not a new convention.

<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:1105d646 -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

**Architecture in one line:** issues live in a local Dolt DB; sync uses `refs/dolt/data` on your git remote; `.beads/issues.jsonl` is a passive export. See https://github.com/gastownhall/beads/blob/main/docs/core-concepts/sync-concepts.md for details and anti-patterns.

## Agent Context Profiles

The managed Beads block is task-tracking guidance, not permission to override repository, user, or orchestrator instructions.

- **Conservative (default)**: Use `bd` for task tracking. Do not run git commits, git pushes, or Dolt remote sync unless explicitly asked. At handoff, report changed files, validation, and suggested next commands.
- **Minimal**: Keep tool instruction files as pointers to `bd prime`; use the same conservative git policy unless active instructions say otherwise.
- **Team-maintainer**: Only when the repository explicitly opts in, agents may close beads, run quality gates, commit, and push as part of session close. A current "do not commit" or "do not push" instruction still wins.

## Session Completion

This protocol applies when ending a Beads implementation workflow. It is subordinate to explicit user, repository, and orchestrator instructions.

1. **File issues for remaining work** - Create beads for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **Handle git/sync by active profile**:
   ```bash
   # Conservative/minimal/default: report status and proposed commands; wait for approval.
   git status

   # Team-maintainer opt-in only, unless current instructions forbid it:
   git pull --rebase
   git push
   git status
   ```
5. **Hand off** - Summarize changes, validation, issue status, and any blocked sync/commit/push step

**Critical rules:**
- Explicit user or orchestrator instructions override this Beads block.
- Do not commit or push without clear authority from the active profile or the current user request.
- If a required sync or push is blocked, stop and report the exact command and error.
<!-- END BEADS INTEGRATION -->

<!-- BEGIN BEADS CODEX SETUP: generated by bd setup codex -->
## Beads Issue Tracker

Use Beads (`bd`) for durable task tracking in repositories that include it. Use the `beads` skill at `.agents/skills/beads/SKILL.md` (project install) or `~/.agents/skills/beads/SKILL.md` (global install) for Beads workflow guidance, then use the `bd` CLI for issue operations.

### Quick Reference

```bash
bd ready                # Find available work
bd show <id>            # View issue details
bd update <id> --claim  # Claim work
bd close <id>           # Complete work
bd prime                # Refresh Beads context
```

### Rules

- Use `bd` for all task tracking; do not create markdown TODO lists.
- Run `bd prime` when Beads context is missing or stale. Codex 0.129.0+ can load Beads context automatically through native hooks; use `/hooks` to inspect or toggle them.
- Keep persistent project memory in Beads via `bd remember`; do not create ad hoc memory files.

**Architecture in one line:** issues live in a local Dolt DB; sync uses `refs/dolt/data` on your git remote; `.beads/issues.jsonl` is a passive export. See https://github.com/gastownhall/beads/blob/main/docs/core-concepts/sync-concepts.md for details and anti-patterns.
<!-- END BEADS CODEX SETUP -->
