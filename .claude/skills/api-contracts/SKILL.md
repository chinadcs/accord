---
name: api-contracts
description: 'Desenhar ou revisar contratos de ações locais e sincronização do Accord: identidade, versão, idempotência,
  autorização, erro e recuperação. Aplicar quando um dado atravessar UI, SLM, persistência ou Supabase, sem pressupor
  API HTTP própria.'
---

# api-contracts — Accord

Ler [AGENTS.md](../../../AGENTS.md), [ARCHITECTURE.md](../../../ARCHITECTURE.md), o
[plano](../../../docs/plano-desenvolvimento.md) e a
[spec relevante](../../../docs/specs/design/README.md). Decisões posteriores prevalecem sobre
propostas anteriores. Não presumir aplicativo, serviço ou testes já implementados.

## Contratos a tornar explícitos

O plano §13.2 define o fluxo de ações estruturadas; a tela 26 define sync e conflitos.
Nenhuma rota, DTO, SDK, esquema ou status HTTP de produto já foi implementado. Escolher
transporte/contrato conforme o desenho aprovado, sem copiar o serviço de outro projeto.

Para cada ação, documentar intenção permitida, campos, referência de origem/alvo, validação,
efeito financeiro, identidade, versão esperada e resultado observável. Ação válida no formato
pode estar semanticamente errada: valor, total versus parcela e ambiguidades exigem verificações.

Sincronização precisa identificar operação, movimento e versão sem confundi-los. Resposta
perdida/reenvio retorna reconhecimento sem nova aplicação. Conflito preserva versões e exige
escolha; nova alteração durante revisão exige comparação atualizada. Não depender de relógios
ou mescla automática. Validar associação ao espaço no serviço.

Erros distinguem o que foi aplicado localmente, reconhecido pelo serviço e deixado pendente;
não inventar confirmação global. Exibir correção/retomada sem perder trabalho e sem dados
sensíveis no diagnóstico. A taxonomia concreta é uma decisão a documentar, não um contrato
herdado. Mostrar exemplo de sucesso, repetição, versão divergente e ausência de autorização.
