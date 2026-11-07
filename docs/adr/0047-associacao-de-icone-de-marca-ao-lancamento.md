---
status: accepted
recorded_at: 2026-11-07
decision_date: 2026-11-07
---

# ADR 0047 — Associação de ícone de marca ao lançamento

## Contexto

O accord-csy ([guia](../branding/vendors-2026-10/README.md)) entregou logos locais só para Uber e Spotify, reconhecidos pela descrição como palavra inteira e derivados na tela, sem dado persistido.
O usuário pediu (07/11/2026) que **todas** as marcas do Simple Icons funcionem no app, sejam reconhecidas pelo nome do lançamento, possam ser **escolhidas num seletor com busca** e que o app
**pergunte** se a escolha vale para os próximos lançamentos com o mesmo nome. Desde o [ADR 0036](0036-interpretador-por-regras-no-p0-e-slm-opcional.md) o Chat do P0 não tem modelo; a "IA local"
do pedido original (accord-nq8) passa a ser reconhecimento determinístico por regras.

## Decisão

1. **Catálogo local completo.** O conjunto é o Simple Icons na versão fixada **16.35.0** ([ADR 0021](0021-recursos-visuais-locais-e-versoes-fixadas.md)), embarcado como um único ativo de texto
   (slug, título, cor, apelidos, origem de licença/diretrizes e o caminho do desenho em 24 × 24), gerado por script a partir da versão fixada, com o SHA-256 da origem registrado. Sem rede, CDN, JavaScript ou
   biblioteca nova em execução. Os logos de bancos já embarcados (logos-bancos-br, por ISPB) entram como segunda fonte, e os ícones Lucide como terceira (genéricos).
2. **Reconhecimento por regras, conservador.** Frase inteira de título, slug ou apelido, sem acento, caixa ou pontuação. Nunca reconhece sozinho títulos de até 3 caracteres nem palavras comuns de uma
   lista curada; duas marcas distintas no mesmo nome não escolhem nenhuma; a frase mais longa vence uma contida nela. Quem não é reconhecido continua buscável no seletor. Nada além do texto do
   lançamento é evidência; categoria e produto não escolhem marca.
3. **Escolha manual por seletor com busca**, em três seções (Marcas, Bancos, Genéricos), aberto no **Detalhe do lançamento** e no **recibo do Chat**. Ao escolher, o app pergunta: **só este lançamento** ou
   **este e os próximos com o mesmo nome**. A segunda vale para **todos** os lançamentos com o nome, passados e futuros, e é aplicada na exibição, sem gravar nada em cada lançamento.
4. **Agregado `IconAssociation`, sincronizado** (ADR 0024, modelo do [ADR 0038](0038-categorias-de-renda.md)): escopo `Name` (nome normalizado por `NameKey`) ou `Entry` (identidade do lançamento), origem
   (`brand`, `bank`, `generic`), chave do ícone e remoção lógica. Identidade determinística por escopo e chave; edição concorrente vira conflito de versão com as duas versões, sem última edição vence.
   Comandos `SetIconAssociation` e `ClearIconAssociation`, com versão esperada. **Não entram no Desfazer** (decidido na implementação): escolher de novo ou remover já é o caminho de volta, e o aviso de
   Desfazer ficaria sobre uma escolha puramente visual. A fachada confere só o **formato** da chave de cada origem (slug, ISPB de 8 dígitos ou nome Lucide; sem URL, caminho ou texto livre) e recusa lançamento
   inexistente; a pertença ao catálogo embarcado é conferida na exibição, e uma chave que o aparelho não conhece (de uma versão mais nova) cai no ícone padrão.
5. **Precedência na exibição:** escolha do lançamento > regra por nome > reconhecimento automático > ícone padrão. O ícone é visual: não muda categoria, valores, datas, identidade nem dados importados.
6. **Direitos de marca.** Aviso único (uso nominativo para identificar o estabelecimento citado, sem parceria ou suporte de importação) e, no catálogo, a origem de licença/diretrizes que o Simple Icons
   informar para cada marca. A licença CC0 do projeto não concede direitos sobre cada marca.

## Consequências

- Um tipo novo no protocolo e no serviço (`IconAssociation` em `accord_private.shared_types()`), esquema Room 12 e migração Supabase; sem compatibilidade retroativa, como nos ADRs 0038/0039 (dois aparelhos atualizados juntos).
- O ativo cresce (milhares de caminhos, estimativa de poucos MB); a carga e a busca devem ficar fora da thread de interface. Medir o tamanho do APK e o tempo de busca.
- Só o nome normalizado do lançamento (já compartilhado) e a chave do ícone sincronizam; Chat continua só no aparelho de origem.
- Regras por nome com colisão de nomes de lançamentos diferentes aplicam o mesmo ícone: a pessoa pode usar "só este lançamento" ou remover a regra.
- A lista de palavras comuns e o limite de 3 caracteres são ajustes de precisão; reconhecimento ruim é corrigido pela escolha manual. Revisar se o uso real mostrar acertos errados frequentes.
- Reabrir o SLM (ADR 0036) não muda este ADR: uma proposta de chave de modelo, se existir, passaria pela mesma lista branca do catálogo.

## Fontes e evidências

- Pedido e respostas do usuário em 07/11/2026 neste Bead: accord-nq8 (sincronizada nos dois celulares; vale para todos com o mesmo nome; marcas, bancos e genéricos com busca;
  reconhecimento conservador; Detalhe e recibo do Chat; aviso global com origem por marca).
- [ADR 0021](0021-recursos-visuais-locais-e-versoes-fixadas.md), [ADR 0024](0024-protocolo-por-agregados-e-cursor.md), [ADR 0036](0036-interpretador-por-regras-no-p0-e-slm-opcional.md),
  [ADR 0038](0038-categorias-de-renda.md), [ADR 0039](0039-edicao-movimentacao-e-remocao-de-categorias.md).
- Aceito pelo usuário; **implementação ainda não existe** (filhos accord-nq8.2 a .5). Validação nos aparelhos segue em `accord-qsb`.
