---
status: proposed
recorded_at: 2026-11-03
decision_date: null
---

# ADR 0042 — Importação de CSV no formato próprio do Accord

## Contexto

A importação cobre OFX e PDF bancário com texto selecionável ([ADR 0019](0019-importacao-local-com-rastreabilidade.md), [ADR 0041](0041-leitura-de-pdf-por-layout-sobre-pdfbox-android.md)).
Em 03/11/2026 o usuário pediu para importar CSV. O repositório não tem nenhum CSV de banco e cada banco exporta colunas, separadores, datas e
sinais diferentes; a regra do produto é não anunciar compatibilidade com layout sem amostra. Perguntado entre mapeamento genérico, layout de um
banco e os dois, o usuário respondeu: "Mapeamento próprio para o Accord, que funcione para o nosso App. Prover um csv de exemplo de importação no docs".

## Decisão

Proposta, a aceitar pelo usuário. Definir um **CSV do Accord**, formato nosso e documentado ([guia](../importacao-csv.md), [exemplo](../modelos/accord-importacao-exemplo.csv)):

1. Colunas obrigatórias `data`, `descricao`, `valor`; opcionais `id`, `moeda`, `parcela`. O arquivo só é reconhecido como CSV do Accord pelo
   cabeçalho (o formato vem do conteúdo, como nos outros), nunca pela extensão. Colunas desconhecidas são ignoradas e não guardadas.
2. Leitor `CsvReader` em `:core`, sem I/O, que devolve o mesmo `ExtractionResult` do OFX (`SourceFormat.Csv`). O `id` ocupa o campo `FITID`,
   então reimportação, reconciliação e dúvidas já existentes valem sem alteração. Data fora de `AAAA-MM-DD`/`DD/MM/AAAA` e valor ambíguo
   (`1.234`) viram dúvida do movimento com o texto original preservado, sem adivinhar. Linha com número de colunas errado não é lida e deixa a cobertura parcial.
3. Moeda diferente de BRL bloqueia a confirmação, sem conversão. Entrada não vira renda sozinha.
4. **Colunas `categoria` e `origem`** (pedido do usuário em 03/11/2026, depois da primeira versão):
   - `categoria` é nome de categoria do espaço (`Mercado`, `Alimentação` ou `Principal > Filha`), casado por nome exato, sem acento nem caixa, nunca por
     semelhança. Casando com uma só categoria, vira a decisão de categoria do lote para aquela **despesa** ao iniciar o lote (precede o histórico e o
     sugeridor), editável na revisão, sem efeito no orçamento antes da confirmação. Isso abre uma **exceção** à regra "categoria nunca vem do arquivo",
     restrita a este formato nosso: o arquivo de banco continua sem categoria própria. Nome inexistente ou ambíguo é ignorado.
   - `origem` é o apelido de uma conta ou do cartão. Casando com uma só, pré-seleciona tipo e origem na tela de importar (a pessoa confere). Como um lote
     tem uma só origem, um arquivo com mais de uma é recusado em vez de atribuir tudo a uma delas. Dividir um arquivo em lotes por origem não foi feito.
5. Não há leitor de CSV de banco. Compatibilidade com algum banco continua não afirmada; um layout fixo de banco exige amostra real e novo registro.

## Consequências

O usuário (ou uma planilha dele) prepara o arquivo no modelo; o app não lê o CSV exportado direto pelo banco, e a tela diz isso. Sem `id`, cada movimento
fica sem identificador estável (o aviso "sem identificador" aparece) e a reimportação depende de data, valor e descrição, com mais dúvidas. O período exibido
vem da menor e da maior data lidas, não de uma declaração do arquivo. Mudar nomes ou semântica de colunas depois exige versionar o formato. Sem mudança de
esquema Room nem do protocolo: o formato é gravado como texto e o serviço não o valida.

## Fontes e evidências

- Pedido e resposta do usuário em 03/11/2026 (acima); tarefa Beads `accord-3m7k`.
- [Spec S4](../specs/S4-importacao-retomada-pendencias.md), seção do leitor de CSV; [guia do formato](../importacao-csv.md).
- Implementado e testado só com dados fictícios: `CsvReaderTest` (14), `ImportFileLoaderTest`, `CsvImportTest`. Nada foi exercitado em aparelho nem com CSV real de banco.
