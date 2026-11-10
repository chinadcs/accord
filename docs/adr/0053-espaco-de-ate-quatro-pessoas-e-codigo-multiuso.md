---
status: accepted
recorded_at: 2026-11-08
decision_date: 2026-11-08
---

# ADR 0053 — Espaço de até quatro pessoas e código de convite multiuso

Complemento de 09/11/2026: o [ADR 0065](0065-convite-pela-camera-externa-sem-leitor-proprietario.md)
substitui somente a leitura integrada pelo Google por leitura externa/copiar/colar. As demais
decisões deste ADR continuam aceitas. O trecho original sobre o leitor abaixo é histórico.

## Contexto

O [ADR 0014](0014-espaco-igualitario-e-convite.md) limita o espaço a duas pessoas e o código a uso único. Em
08/11/2026 o usuário pediu espaço de até 4 pessoas, entrada por QR Code e primeiros passos próprios para
quem entra por convite. O restante do ADR 0014 continua: contas individuais, poderes iguais, sem gastos
privados, validação no serviço/banco.

## Decisão

- Até **4 pessoas**, direitos iguais.
- **Um código ativo por vez**, de 8 caracteres, multiuso: serve até o espaço lotar, validade de 3 dias,
  invalidável, com contagem de usos e "restam N vagas". A validação e o consumo ficam no serviço (função
  SQL), nunca só no cliente; migração nova, sem editar a antiga.
- **QR Code** com o mesmo código; leitura pelo leitor de código do Google (sem permissão de câmera); a
  digitação continua. Gerar o QR exige uma biblioteca pequena (ZXing core) e ler exige
  `play-services-code-scanner`: ambas passam por avaliação de licença, tamanho e versão fixada
  ([ADR 0021](0021-recursos-visuais-locais-e-versoes-fixadas.md)). O leitor depende dos Google Play Services.
- **Convidado**: fluxo reduzido ("Você entrou em [espaço]", mostra o que existe, pede só a conta padrão e a
  permissão de notificação; pula categorias, cartão, limites e convite).
- **Alertas** vão a todos os membros. **Remover membro e sair do espaço ficam fora**, como pendência
  (`accord-algp`).

## Consequências

Substitui o ADR 0014 no limite de 2 e no uso único. Códigos multiuso ampliam a janela de abuso se vazarem:
mitigada pela validade curta, invalidação e limite de vagas. Alertas e cota semanal por categoria deixam de
ser "dos dois" e passam a ser "de todos" (AGENTS.md). Sem remoção de membro, um convidado errado só se
resolve fora do app. Os testes pgTAP de permissões e de convite mudam.

## Fontes e evidências

- [Plano](../specs/plans/plano-melhorias-2026-11.md), etapa 2; Beads `accord-0vdp`, `accord-algp`.
- Decisão do usuário em 08/11/2026; aceito em 08/11/2026 ("Aceito todos os ADRs"). Nada implementado.
