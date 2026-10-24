---
name: web-design-guidelines
description: Review UI code for Web Interface Guidelines compliance. Use when asked to "review my UI", "check accessibility", "audit design", "review UX", or "check my site against best practices".
metadata:
  author: vercel
  version: "1.0.0"
  argument-hint: <file-or-pattern>
---

## Escopo no Accord

Referência opcional para o trabalho de design solicitado. O produto é Android nativo em
Kotlin/Jetpack Compose; esta skill genérica não escolhe plataforma, biblioteca, fonte,
iconografia ou navegação. Seguir [AGENTS.md](../../../AGENTS.md) e as
[specs de tela](../../../docs/specs/README.md): fonte nativa, tokens semânticos, Lucide,
catálogo/logos bancários locais e acessibilidade Android. Não aplicar exigências de HTML,
React, CSS, DOM ou marketing a telas Compose. Para UI Android, usar a skill
[android-compose](../android-compose/SKILL.md). Buscar diretriz web externa somente se o
trabalho solicitado for de fato web; não baixar ativos nem escolher marca por este aviso.
As referências originais abaixo permanecem como material genérico e não são regras do produto.

# Web Interface Guidelines

Review files for compliance with Web Interface Guidelines.

## How It Works

1. Fetch the latest guidelines from the source URL below
2. Read the specified files (or prompt user for files/pattern)
3. Check against all rules in the fetched guidelines
4. Output findings in the terse `file:line` format

## Guidelines Source

Fetch fresh guidelines before each review:

```
https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md
```

Use WebFetch to retrieve the latest rules. The fetched content contains all the rules and output format instructions.

## Usage

When a user provides a file or pattern argument:
1. Fetch guidelines from the source URL above
2. Read the specified files
3. Apply all rules from the fetched guidelines
4. Output findings using the format specified in the guidelines

If no files specified, ask the user which files to review.
