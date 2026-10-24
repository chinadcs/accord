# Catálogo de agentes — Accord

Revisão: 24/10/2026. [AGENTS.md](../AGENTS.md) define regras de trabalho;
[ARCHITECTURE.md](../ARCHITECTURE.md) resume o estado atual. Fontes de produto:
[plano](../docs/plano-desenvolvimento.md), [índice](../docs/specs/design/README.md) e tela afetada.
Decisões posteriores do plano §§14 e 16 prevalecem; [ADRs](../docs/adr/README.md) preservam
motivos e autoridade. Há código/testes de S1 e partes de S2; implementação não aprova proposta
nem comprova implantação hospedada ou medição nos aparelhos.

Este catálogo organiza responsabilidades. Não autoriza disparar agentes: delegação depende
das instruções ativas da sessão e do usuário. Quando não for autorizada/disponível, a conversa
principal pode aplicar a perspectiva pertinente diretamente. Escolher revisão proporcional ao
risco e ao escopo, sem exigir todos os papéis em toda mudança.

## Papéis compartilhados

| Papel | Responsabilidade | Usar quando |
| --- | --- | --- |
| [coordinator](agents/coordinator.md) | Ordenar frentes/dependências no estado real | Trabalho com várias frentes e coordenação autorizada |
| [product-manager](agents/product-manager.md) | Refinar escopo, aceite e tarefas no Beads | Planejamento, dependências ou handoff |
| [architect](agents/architect.md) | Resolver desenho preservando decisões de produto | Novo domínio, contrato, persistência, sync, permissão ou integração |
| [code-implementer](agents/code-implementer.md) | Entregar fatia vertical implementada e validada | Funcionalidade Android/dados com desenho suficiente |
| [frontend-android](agents/frontend-android.md) | Interface Compose, navegação, estados e acessibilidade | Mudança de tela/componente/fluxo Android |
| [qa-engineer](agents/qa-engineer.md) | Definir como provar comportamento e recuperação | Estratégia de teste, amostra, aparelho ou caso de falha |
| [code-reviewer](agents/code-reviewer.md) | Revisar correção e responsabilidades | Mudança de comportamento, dinheiro, estado ou sync |
| [security-auditor](agents/security-auditor.md) | Auditar autorização e privacidade | Identidade, convite, acesso ao espaço, segredo ou dado externo |
| [production-risk](agents/production-risk.md) | Avaliar operação em aparelho/rede/serviço | Inferência, download, fila, importação, alertas ou falha de serviço |
| [code-quality-auditor](agents/code-quality-auditor.md) | Revisar manutenção e força dos testes | Mudança estrutural ou complexidade relevante |
| [docs-auditor](agents/docs-auditor.md) | Conferir fontes e instruções sem editar | Alteração documental, renome ou alegação de implementação |
| [dependency-bumper](agents/dependency-bumper.md) | Avaliar versão, compatibilidade e custo | Atualização/dependência necessária ao escopo |
| [tech-writer](agents/tech-writer.md) | Escrever documentação/handoff preciso | Texto solicitado com evidência e referências |
| [adr-writer](agents/adr-writer.md) | Redigir/revisar decisões estruturais com fontes e autoridade | Nova escolha, recuperação histórica ou substituição de ADR |
| [session-learnings-curator](agents/session-learnings-curator.md) | Persistir aprendizado e corrigir drift da sessão | Encerramento com conhecimento durável |

`frontend-android` substitui o papel herdado `frontend-next`. Não há camada web no produto
planejado. A descrição antiga ainda pode aparecer numa sessão que carregou agentes antes da
atualização; recarregar/reiniciar a descoberta do runtime antes de usar o papel renomeado.

## Fronteiras relevantes para revisão

- **Integridade financeira:** parcelas/fatura sem duplicação; datas de boleto/estorno;
  limite compartilhado/divisões; alcance distinto de parcela e recorrência.
- **Identidade e acesso:** contas próprias, mesmo acesso no espaço e associação/convite
  validados pelo serviço. Piloto sem e-mails, sem chave administrativa no celular.
- **Persistência e concorrência:** banco/fila consistentes, IDs estáveis, idempotência,
  versões e escolha explícita de conflito. Realtime não substitui protocolo.
- **Privacidade:** Chat local, sem IA remota automática e sem documentos completos no serviço;
  originais dos movimentos preservados sem reter PDF/OFX permanentemente.
- **Operação móvel:** S20 FE/S23 FE, qualidade e RAM medidas, download até 2 GB decimais,
  meta carregada até 5 s, manual e recuperação sempre disponíveis.
- **Alertas e custo:** um evento por categoria principal/semana para ambos, America/Sao_Paulo;
  serviço Free sem promessa de gratuidade permanente ou cobrança automática.

## Skills do projeto

As instruções canônicas ficam em `.claude/skills/`. As entradas em `.agents/skills/` são
Markdown gerado com nome/descrição para descoberta e link para a fonte; não são junctions
nem cópias independentes do corpo. Resolver links relativos a partir da fonte canônica.

| Skill | Assunto | Papéis que a carregam |
| --- | --- | --- |
| [beads](skills/beads/SKILL.md) | Tracker e conhecimento persistente | coordinator, product-manager |
| [adr-writing](skills/adr-writing/SKILL.md) | Método de registro, revisão e substituição de ADRs | adr-writer, architect, tech-writer, code-reviewer, docs-auditor, session-learnings-curator |
| [ooda-loop](skills/ooda-loop/SKILL.md) | Coordenação por evidência | coordinator |
| [discovery-techniques](skills/discovery-techniques/SKILL.md) | Valor, decisões e fatias verificáveis | product-manager |
| [domain-driven-design](skills/domain-driven-design/SKILL.md) | Conceitos e invariantes financeiros | architect |
| [hexagonal-architecture](skills/hexagonal-architecture/SKILL.md) | Responsabilidades e dependências | architect, code-implementer, code-reviewer |
| [api-contracts](skills/api-contracts/SKILL.md) | Ações locais e sync, sem API própria presumida | architect |
| [android-compose](skills/android-compose/SKILL.md) | UI Android, estado e acessibilidade | code-implementer, code-reviewer, frontend-android |
| [tdd](skills/tdd/SKILL.md) | Testes por comportamento e critérios | code-implementer, qa-engineer |
| [mutation-testing](skills/mutation-testing/SKILL.md) | Força de testes de regras específicas | qa-engineer |
| [refactoring](skills/refactoring/SKILL.md) | Estrutura com comportamento preservado | code-quality-auditor |
| [supabase-rls](skills/supabase-rls/SKILL.md) | Autorização por espaço no serviço | security-auditor |
| [security-trust-boundary](skills/security-trust-boundary/SKILL.md) | Identidade, segredo e privacidade | security-auditor |
| [defensive-programming](skills/defensive-programming/SKILL.md) | Entrada externa e validação | security-auditor |
| [resilience-engineering](skills/resilience-engineering/SKILL.md) | Falhas, interrupção e repetição | production-risk |
| [twelve-factor](skills/twelve-factor/SKILL.md) | Configuração de app/serviço | production-risk |
| [observability](skills/observability/SKILL.md) | Diagnóstico mínimo sem conteúdo privado | production-risk |
| [reliability](skills/reliability/SKILL.md) | Integridade e recuperação observáveis | production-risk |

Cinco referências genéricas continuam disponíveis sob demanda: `awesome-design`, `brandkit`,
`design-taste-frontend`, `redesign-existing-projects` e `web-design-guidelines`. Não são
pré-requisitos da interface Android. Orientações de HTML/React/CSS/marketing só se aplicam a
trabalho dessa natureza solicitado; fonte nativa, Lucide, catálogo local e specs prevalecem.
`android-compose` substitui a skill herdada `fastapi-patterns` para a plataforma deste produto.

## Adaptação entre Claude e Codex

- Claude lê os corpos em `.claude/agents/*.md`; seu frontmatter configura modelo/ferramentas
  Claude e pré-carregamento de skills. Não pressupor plugins de terceiros instalados.
- Codex lê `.codex/agents/*.toml`, que direciona ao mesmo corpo e às skills canônicas.
  Mantido o mapeamento existente: Claude `opus` → `gpt-6-astra`, demais → `gpt-6-sol`.
  A revisão de produto não muda modelos, permissões ou esforço de raciocínio da sessão.
- Nomes de ferramentas Claude expressam capacidades; usar equivalentes realmente disponíveis
  no runtime. Não tentar invocar Skill/WebFetch se a ferramenta não existir.
- Depois de mudar nome, descrição ou lista de skills de agente ou metadados de skill, executar
  da raiz `python .codex/sync-agents.py`; verificar com
  `python .codex/sync-agents.py --check`. Requer Python 3.10+ e PyYAML, sem ambiente de app.
- Testes do gerador: `python -m unittest discover -s .codex -p "test_*.py"`. Usam árvores
  temporárias para provar geração, drift e proteções sem tocar nas instruções do projeto.
- Gerador valida fontes e referências antes de escrever, não sobrescreve arquivo manual e
  sinaliza obsoleto gerado sem removê-lo. Renome/remoção exige revisão explícita do arquivo.
- `.agents/skills/beads/agents/openai.yaml` mantém metadados de UI do Beads; não contém outro
  fluxo de tarefas. Corpos de skill permanecem na fonte canônica.
- `.claude/settings.json` e `.codex/hooks.json` carregam contexto Beads; não são autorização
  para commit, serviço remoto ou subagente. Configurações do runtime ficam preservadas.
- Estas pastas existem na árvore atual e não estão ignoradas pelo `.gitignore` atual.
  Parte das fontes já era não rastreada; não presumir instalação privada ou arquivos commitados.

Decisões estruturais seguem o guia de ADRs; architect fornece justificativa, escritores registram,
revisores conferem aderência e curador verifica o registro no encerramento. ADR não é aprovação
por outro agente. O papel pode ser aplicado na conversa principal; descoberta nova exige recarga
conforme o runtime, sem presumir que esta sessão já conheça `adr-writer` como subagente.

Usar `bd` para tarefas e `bd remember` para conhecimento durável com referências, sem memória privada de
runtime, `MEMORY.md` ou tracker Markdown. Perfil conservador/local: relatar aceite, checks,
pendências e git status; commit, publicação, mensagens externas e operações remotas exigem
pedido explícito. Autorizações já dadas continuam válidas, sem pedir a mesma permissão novamente.
