---
status: accepted
recorded_at: 2026-11-03
decision_date: 2026-11-03
---

# ADR 0041 — Leitura de PDF por layout validado, sobre PdfBox-Android

## Contexto

O [ADR 0019](0019-importacao-local-com-rastreabilidade.md) manda ler PDF bancário com texto selecionável localmente, e a [spec S4](../specs/plans/S4-importacao-retomada-pendencias.md)
(seam de leitores) deixava em aberto a **biblioteca de extração de texto** e mandava habilitar cada layout só depois de validado com amostra real. Em 03/11/2026 o usuário entregou
uma fatura real do cartão Itaú em PDF, a primeira amostra desse tipo. O arquivo tem texto, duas colunas e totais declarados em cada seção
([nota técnica](../poc/leitor-pdf-fatura-itau-2026-11-03.md)).

## Decisão

Escolha técnica implementada e testada; aceita pelo usuário em 03/11/2026 ("Aceita o adr41").

1. **Separar extração e interpretação.** `PdfTextExtractor` (bytes para texto por página) é um adaptador sobre biblioteca; `PdfLayoutReader` e cada `PdfLayout` interpretam o texto, são
   puros, ficam em `:core` e testam na JVM sem aparelho.
2. **Biblioteca: PdfBox-Android 2.0.27.0**, versão fixada, licença Apache-2.0, extração local sem rede e sem OCR. Os testes JVM usam o Apache PDFBox 2.0.37 (mesma linha de código),
   só em teste. Extrai-se **na ordem do fluxo de conteúdo**, não por posição, porque faturas de duas colunas se misturam por posição.
3. **Layout só com amostra real.** O único habilitado é `itau-cartao-fatura-v1`, verificado com **um** documento. Texto sem layout conhecido é "layout não reconhecido", nunca extração
   genérica. O aplicativo diz isso ao usuário e recomenda o OFX.
4. **Verificação pelos totais do próprio documento.** A soma de cada seção lida é comparada com o total que o documento declara; divergência ou linha ilegível vira cobertura parcial com aviso,
   nunca aceite silencioso. Isso substitui a confiança no formato por uma prova em cada leitura.
5. **Regras de leitura** (detalhadas na nota técnica): ano pela data de emissão, linhas sem data usam a emissão e declaram isso, pagamentos e próximas parcelas não são movimentos, dados pessoais
   do titular nunca são guardados. As escolhas marcadas como proposta na nota dependem do usuário.

## Consequências

- Funciona offline e preserva a privacidade (nada sai do aparelho; o arquivo não é guardado).
- **A biblioteca está parada desde janeiro de 2023.** Funcionou com a amostra, mas não recebe correções; a troca é barata porque só o adaptador a conhece. Ela traz BouncyCastle (criptografia de PDF)
  como dependência transitiva: o tamanho no APK não foi medido, e excluir essas dependências seria uma otimização futura (PDF protegido já é recusado antes).
- Compatibilidade de um único exemplo: um redesenho da fatura, outro produto (Itaú Black, outro emissor) ou o extrato da conta em PDF exigem novos layouts e novas amostras.
- A reconciliação trata linhas dessa fatura sem inventar nada: parcelamento de fatura, créditos e parcelas não registradas viram dúvidas, que a fila de pendências (S4.5) apresenta.
- PDF digitalizado (imagem) segue fora do P0: o extrator devolve "sem texto utilizável".

## Fontes e evidências

- [S4](../specs/plans/S4-importacao-retomada-pendencias.md), [ADR 0019](0019-importacao-local-com-rastreabilidade.md), [ADR 0040](0040-lote-de-importacao-confirmado-e-confirmacao-atomica.md),
  [nota técnica](../poc/leitor-pdf-fatura-itau-2026-11-03.md). Beads `accord-c9m.6`.
- Versões conferidas no Maven Central em 03/11/2026: `com.tom-roush:pdfbox-android` 2.0.27.0 (última, de 02/01/2023); `org.apache.pdfbox:pdfbox` 2.0.37 e 3.0.8 (a linha 3 depende de
  `java.awt`, que o Android não tem: conhecimento geral, não testado aqui; por isso a linha 2.0 é a que se usa nos dois lados).
- Aceite: usuário, 03/11/2026. Os pontos que a nota técnica marcava como proposta (data de emissão, rótulo de categoria como sugestão, parcelamento de fatura como dúvida) foram confirmados por ele no mesmo dia. Implementação: `core.importing.PdfLayoutReader`, `ItauCardInvoiceLayout`, `app.accord.ui.imports.AndroidPdfTextExtractor`. Testes: 16 sintéticos,
  3 com a amostra real (locais), 2 no emulador com a amostra real. Nada disso comprova compatibilidade geral nem validação nos dois celulares.
