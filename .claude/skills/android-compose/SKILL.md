---
name: android-compose
description: 'Implementar ou revisar telas Android Kotlin/Jetpack Compose do Accord conforme as specs: navegação,
  estado recuperável, ciclo de vida, acessibilidade e trabalho local. Usar para mudanças de interface Android, não
  para escolher modelo ou protocolo de sincronização.'
---

# android-compose — Accord

Ler [AGENTS.md](../../../AGENTS.md), [ARCHITECTURE.md](../../../ARCHITECTURE.md), o
[plano](../../../docs/plano-desenvolvimento.md) e a
[spec relevante](../../../docs/specs/design/README.md). Decisões posteriores prevalecem sobre
propostas anteriores. Não presumir aplicativo, serviço ou testes já implementados.

## Aplicar a tela especificada

Ler a spec e os destinos relacionados. Chat é início; Chat, Orçamento, Lançamentos, Revisar e
Mais são as raízes. Detalhes e painéis voltam preservando filtros, seleção, texto e contexto.
Não transformar P1/P2 em requisito oculto de uma tela P0.

Distinguir estado de UI, dado financeiro persistido e confirmação de sincronização. Recriação
da tela/processo não pode reaplicar ação confirmada nem apagar trabalho. A política concreta
de restauração/persistência deve ser definida com a implementação, sem presumir biblioteca.

Inferência, extração e sincronização não bloqueiam a thread da interface. Cancelar preserva
mensagem/rascunho; modelo ausente ou download oferece manual. Meta de 5 s não é timeout automático.
Exibir progresso real e estado conhecido, sem sucesso antes da conclusão nem zero para desconhecido.

## Aparência e acesso

Fonte nativa, tokens semânticos e temas claro/escuro. Referências adaptáveis: corpo 16 sp,
títulos 20–24 sp, margem 16 dp, ritmo 4/8 dp. Alvos 48 × 48 dp, contraste texto 4,5:1 e gráficos
3:1. Validar TalkBack, rótulos/foco, fontes ampliadas, teclado, retrato/paisagem e movimento reduzido.

Lucide pela adaptação comunitária Compose Icons Android, versão a verificar. Catálogo/logos
logos-bancos-br locais, com origem e ISPB/COMPE; logo mantém cores/proporção nos dois temas,
sem tintura de ação. Ausência usa símbolo bancário genérico com nome. Sem CDN em uso.

Ao revisar, registrar tela/estado, cenário reproduzível e validação executada. Não declarar
acessibilidade ou desempenho em celulares a partir de compilação/preview apenas.
