---
name: hexagonal-architecture
description: Separar responsabilidades do Accord entre interface, SLM, validação, motor financeiro, persistência,
  importação e sincronização. Usar quando desenhar dependências ou isolar regras para teste; não impor uma estrutura
  de módulos ainda não escolhida.
---

# hexagonal-architecture — Accord

Ler [AGENTS.md](../../../AGENTS.md), [ARCHITECTURE.md](../../../ARCHITECTURE.md), o
[plano](../../../docs/plano-desenvolvimento.md) e a
[spec relevante](../../../docs/specs/README.md). Decisões posteriores prevalecem sobre
propostas anteriores. Não presumir aplicativo, serviço ou testes já implementados.

## Direção de dependência

O plano §13.1 e ARCHITECTURE.md definem responsabilidades, não pastas Kotlin existentes.
Regras financeiras devem ser executáveis sem Compose, inferência, banco ou rede. Interfaces
para capacidades externas são úteis quando permitem esse isolamento ou substituição real;
não extrair abstração genérica sem necessidade demonstrada.

UI apresenta intenção/estado. SLM propõe campos. Validador decide se a ação é aplicável e o
motor calcula. Persistência guarda dados e fila; adaptadores fazem I/O de modelo, arquivos e
Supabase. Uma ação do modelo não pode escrever direto no banco pulando validação.

Definir como alteração local/fila permanecem consistentes e como erro/reenvio mantêm identidade.
Autorização do espaço é verificada no serviço; isolamento de camadas no celular não a substitui.

Na revisão, acompanhar uma ação do Chat, do manual e da importação até seus efeitos e confirmar
que usam as mesmas regras. Evitar um módulo Gradle por responsabilidade, DI obrigatória ou
API própria só para reproduzir um diagrama. Registrar a estrutura real quando implementada.
