---
name: awesome-design
description: Use VoltAgent's collection of website design references when the user requests Awesome Design, a brand-inspired interface, or comparison of visual styles from this collection.
---

## Escopo no Accord

Referência opcional para o trabalho de design solicitado. O produto é Android nativo em
Kotlin/Jetpack Compose; esta skill genérica não escolhe plataforma, biblioteca, fonte,
iconografia ou navegação. Seguir [AGENTS.md](../../../AGENTS.md) e as
[specs de tela](../../../docs/specs/design/README.md): fonte nativa, tokens semânticos, Lucide,
catálogo/logos bancários locais e acessibilidade Android. Não aplicar exigências de HTML,
React, CSS, DOM ou marketing a telas Compose. Para UI Android, usar a skill
[android-compose](../android-compose/SKILL.md). Buscar diretriz web externa somente se o
trabalho solicitado for de fato web; não baixar ativos nem escolher marca por este aviso.
As referências originais abaixo permanecem como material genérico e não são regras do produto.

# Awesome Design

This local adapter makes the upstream DESIGN.md collection available as a skill.
Source: https://github.com/VoltAgent/awesome-design-md
Snapshot: f6961238d5cddcf8042a74a70fc400ec67181abb

List the directories under `references/` to find available brands. Each contains a
`DESIGN.md` describing that site's palette, typography, components, and layout.
Read only the references relevant to the user's requested style. If the request
requires choosing a brand and the brief gives no direction, present a few suitable
options before applying one.

Use the selected reference to guide the requested design work. Adapt its patterns
to the project's existing components and semantic design tokens, following the
project's AGENTS.md and typography rules. Adding this library does not select a
brand or replace the project's design system. Copy a reference to the project root
only when the user asks to adopt it as the project's DESIGN.md.
