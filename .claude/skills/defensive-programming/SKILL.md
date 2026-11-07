---
name: defensive-programming
description: Validar entradas não confiáveis e preservar trabalho no Accord, especialmente ações do SLM, arquivos
  bancários, convites e respostas de sincronização. Aplicar em fronteiras com dados externos, sem adicionar validações
  especulativas por toda parte.
---

# defensive-programming — Accord

Ler [AGENTS.md](../../../AGENTS.md), [ARCHITECTURE.md](../../../ARCHITECTURE.md), o
[plano](../../../docs/plano-desenvolvimento.md) e a
[spec relevante](../../../docs/specs/design/README.md). Decisões posteriores prevalecem sobre
propostas anteriores. Não presumir aplicativo, serviço ou testes já implementados.

## Fronteiras que exigem validação

SLM não é autoridade: validar conjunto de ações, dinheiro exato, datas, campos/referências,
alvo da correção e total versus parcela. Perguntar quando ambíguo; não usar confiança autodeclarada
para completar valor/categoria. Consultas são funções controladas, sem SQL livre.

Importação preserva moeda e originais. EUR não vira BRL automaticamente; formato/sinal/descrição
não provam renda ou cartão. FITID repetido divergente exige revisão; valor/data iguais não
justificam fundir compras legítimas. PDF sem texto utilizável oferece outro PDF/OFX, sem OCR no P0.

Convite/associação são validados no serviço, sem confiar no espaço informado pelo cliente.
Reenvio mantém identidade; conflito preserva versões. Resposta inválida, rede perdida ou processo
interrompido não podem indicar sucesso, apagar pendência ou reaplicar operação.

Definir limites de processamento conforme formato/amostras e recursos reais, sem inventar
números aprovados. Erros permitem corrigir/repetir e preservam evidência sem dados bancários
ou tokens nos logs. Demonstrar caminho válido e recusa efetiva para cada nova guarda relevante.
