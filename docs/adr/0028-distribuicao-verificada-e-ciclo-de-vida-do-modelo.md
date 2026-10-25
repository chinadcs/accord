---
status: proposed
recorded_at: 2026-10-24
decision_date: null
---

# ADR 0028 — Distribuição verificada e ciclo de vida do modelo no aparelho

## Contexto

O modelo é baixado separadamente, com teto de 2 GB decimais, tamanho real e progresso visíveis, e o registro
manual nunca depende dele ([ADR 0004](0004-distribuicao-separada-do-modelo.md)). Hospedagem, motor, quantização
e autenticidade do manifesto continuam abertos até a Etapa 0 nos dois aparelhos. Era preciso desenhar instalação
e execução de modo testável sem escolher nenhum deles.

## Decisão

Proposta de implementação:

- **Duas máquinas ortogonais**: instalação (`ModelManager`) e execução (`ModelRuntime`). "Pronto" só existe
  depois de verificar tamanho e SHA-256 de cada arquivo e ativar por troca atômica; instalação interrompida
  é limpa ou retomada ao abrir, nunca apresentada como pronta.
- **Manifesto** com arquivos, bytes, hash, motor e espaço de instalação; total acima de 2.000.000.000 bytes é
  rejeitado, sem dividir pesos. Autenticidade do manifesto (fixado no build ou remoto assinado) segue aberta.
- **Fonte de distribuição** é uma porta com fake; a hospedagem real não existe. Retomada por intervalo só se a
  hospedagem aceitar e o validador do conteúdo não mudou; senão a tela explica que é preciso recomeçar.
- **Atualização** mantém a versão ativa até a nova estar verificada. Sem espaço para as duas, oferece remover a
  atual (o Chat fica sem modelo até a nova). Remover descarrega o motor antes de apagar e não toca em lançamentos
  nem histórico. Fora do Wi-Fi o app pergunta (baixar agora ou esperar); se o Wi-Fi acaba no meio, pausa e pergunta.
- **Execução** carrega sob demanda, uma inferência por vez, libera após período sem uso (valor a calibrar), em
  segundo plano prolongado e a pedido de memória. Sem motor integrado, o Chat guarda a mensagem ("sem modelo").

## Consequências

A máquina de estados e a integridade estão cobertas por testes de JVM com arquivos reais. Ficam abertos, e
registrados no Beads: motor, modelo, quantização, hospedagem, autenticidade, download em segundo plano pelo
sistema (hoje só com o processo vivo) e calibragem. Compatibilidade de API: o gerenciador usa só `java.nio.file`
disponível desde o Android 8, porque `Stream.toList` e `Files.readString` quebram em aparelhos mais antigos.

## Fontes e evidências

- [S3](../specs/S3-chat-slm-modelo.md) §§2, 10 e 11; [tela 25](../specs/design/25-modelo-no-aparelho.md);
  memória `decisao-23-10-download-modelo`; plano §13.
- Implementação em `core.model` e `app/ui/model`; testes JVM e instrumentados com fonte e motor fake. Não há
  download real, motor real nem medição nos aparelhos. Aceite pendente (`accord-awb`).
