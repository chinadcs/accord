---
status: accepted
recorded_at: 2026-11-09
decision_date: 2026-11-09
---

# ADR 0064 — Histórico publicável e privacidade da autoria

## Contexto

O [ADR 0063](0063-licenca-gplv3-e-documentacao-local.md) retirou do índice os documentos fora de
`docs/adr/` e `docs/modelos/`, mas os commits antigos ainda os continham. O usuário autorizou
limpar esse histórico e procurar conteúdo sensível ou pessoal antes do primeiro push.
Existem alterações locais de outras frentes e uma worktree de referência que devem ser preservadas.

## Decisão

Limpar as 23 branches locais numa cópia isolada, após criar e verificar um backup Git externo.
Retirar de todos os seus commits os caminhos de `docs/` fora de `adr/` e `modelos/`. Substituir
o nome do perfil local do Windows e o identificador do serviço do piloto por exemplos genéricos.
Preservar nome de autoria e copyright, usando o endereço
GitHub `noreply` público para autor e committer em lugar do e-mail pessoal. Configurar esse
endereço apenas neste repositório para os novos commits.

Aplicar as referências reescritas com comparação das referências anteriores, atualizar o índice
e preservar os arquivos de trabalho. Em `AGENTS.md`, que já tinha alterações de outra frente,
aplicar somente a substituição do perfil pessoal, preservando as demais mudanças. O backup,
os mapas de reescrita e relatórios detalhados ficam fora do repositório. A worktree de referência
passa a apontar para um repositório independente de recuperação, fora do principal, preservando
seu HEAD, índice, arquivos e estado local. Isso evita manter commits antigos alcançáveis pelo
principal através da worktree. Reflogs e backup privados conservam objetos antigos para
recuperação e não fazem parte da publicação.

O clone publicável deve ser auditado por suas próprias referências e objetos. Não usar o
backup ou a worktree histórica como origem de um push. A operação é local e não autoriza push,
publicação de Releases nem remoção da cópia de recuperação.

## Consequências e evidências

A inspeção inicial abrangeu 154 commits e 2.490 versões de arquivos, usando Gitleaks 8.30.1,
busca de padrões pessoais, revisão de metadados/mensagens e comparação exata com a credencial
local de assinatura. Não foram encontrados segredos operacionais confirmados. Dois alertas
do Gitleaks foram classificados como strings sintéticas em testes que verificam proteção de
diagnósticos. Fixtures bancárias, placeholders de configuração, endereços de teste e dados de
terceiros nos catálogos foram separados dos identificadores do ambiente pessoal encontrados.

Após a reescrita, as 23 branches continham 144 commits e 2.201 versões de arquivos, sem os
caminhos documentais excluídos, e-mail pessoal de autoria, perfil pessoal do Windows ou
identificador do projeto hospedado pesquisados. Esses números precedem o commit desta entrega.
O autor/copyright e contatos públicos de terceiros usados como referências não foram apagados.
A revisão por ferramentas e padrões não é garantia absoluta de ausência de dados sensíveis.

O primeiro build de clone limpo falhou porque `settings.gradle.kts` exigia as bancadas locais
ignoradas `poc` e `pocpc`. Elas passam a ser incluídas somente quando seu `build.gradle.kts`
existe, preservando os módulos do produto e a opção local. A suíte core também dependia de um
modelo fictício em `docs/poc/`, ausente do clone; seus seis exemplos passam a ficar em
`docs/modelos/frases-chat-exemplo.tsv`, sem coletar frases reais, mantendo o teste executável.
A validação do produto usa um clone sem esses acervos e sem credenciais privadas, com os
resultados no bead. Caches Python são ignorados para evitar versionar bytecode com caminhos locais.

Hashes históricos anteriores mudam e algumas revisões apenas documentais deixam de existir.
Referências históricas em documentos podem usar hashes anteriores; o mapa privado permite
recuperá-los. Os requisitos e decisões preservados nos ADRs não mudam por causa da reescrita.

## Fontes

- Usuário, 09/11/2026: “Faça isso. Veja se não tem nenhuma coisa sensivel ou pessoal também que acabou entrando aí”.
- `accord-vr9x.4`, parte de `accord-vr9x`: backup, mapas, relatórios e validação locais.
- [git-filter-repo 2.47.0](https://github.com/newren/git-filter-repo/releases/tag/v2.47.0).
- [Gitleaks 8.30.1](https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1), binário conferido contra checksum da release.
- [ADR 0063](0063-licenca-gplv3-e-documentacao-local.md): GPLv3 e recorte de documentação preservados.
