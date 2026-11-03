# Modelos candidatos para o SLM no aparelho

Levantamento inicial de 24/10/2026, com seleção ativa refinada em 26/10/2026. São **hipóteses para a prova de conceito** (plano §13.3 e
`docs/specs/S3-chat-slm-modelo.md`), não decisão. Modelo, motor, quantização e hospedagem
continuam abertos; escolher exige ADR e aprovação do usuário. As tabelas do levantamento original
preservam a pesquisa da época; medições posteriores têm suas condições e limites nos relatórios da PoC.

## Seleção ativa da PoC (26/10/2026)

Após refinar os arquivos locais, o usuário determinou considerar **somente os oito GGUF
diretamente na raiz de `E:/Projects/ai-models`**, sem subpastas, pesos arquivados ou conversões
guardadas fora dessa raiz. O usuário explicitou que **não quer repetir a bateria para este refino**.
O catálogo compartilhado pelo PC, pela bancada Android e pelos scripts, [poc/assets/models.json](poc/assets/models.json),
contém apenas esta seleção, na ordem indicada. Os levantamentos e inventários seguintes preservam o histórico;
os outros arquivos GGUF e resultados anteriores não foram apagados. Esta seleção para continuar a PoC
não define o modelo de produção nem comprova qualidade ou desempenho nos dois celulares.

Após o diagnóstico, o usuário definiu **Qwen3.5-2B Q5_K_M como referência experimental** para
reavaliar a contribuição do SLM depois das [correções da preparação/validação](docs/poc/correcoes-preparacao-v4-2026-10-26.md).
Os oito candidatos abaixo continuam registrados. A futura qualificação (`accord-nsu.21`)
precisa demonstrar segurança e ganho completo sobre controle forte sem SLM antes da integração.
Nenhuma bateria dos modelos foi repetida para essas correções.

A [qualificação v2 posterior](docs/poc/qualificacao-v2-resultados-2026-10-26.md), autorizada em
seguida, mediu somente Qwen Q5 e controles em 120 frases novas: chamada única 96/120 completos
contra controle forte 98/120, ambos com dois efeitos errados. Não qualifica para avançar;
os oito candidatos abaixo permanecem registrados, sem adoção ou troca de pesos por esse resultado.

| Modelo | Quantização | ID no catálogo | Acerto completo anterior, chamada única |
| --- | --- | --- | --- |
| Bonsai 1,7B | Q1_0 | `bonsai-1-7b-q1-0` | 41/120 |
| Bonsai 4B | Q1_0 | `bonsai-4b-q1-0` | 45/120 |
| Expense Extraction 0,5B | Q4_K_M | `expense-extraction-0-5b-q4-k-m` | 44/120 |
| LFM2.5 1,2B Instruct | Q4_K_M | `lfm2-5-1-2b-instruct-q4-k-m` | 42/120 |
| LFM2.5 350M | Q4_K_M | `lfm2-5-350m-q4-k-m` | 39/120 |
| Pulse Qwen3-0.6B v4 | Q4_K_M | `pulse-qwen3-0.6b-q4km` | 44/120 |
| Qwen3.5-2B | Q5_K_M | `qwen3.5-2b-q5km` | 69/120 |
| Tucano2-qwen-0.5B Merge-FinBERT | F16 | `tucano2-qwen-0.5b-finbert-f16` | 47/120 |

As notas acima são apenas um recorte dos [resultados PC/CUDA já obtidos](docs/poc/repeticao-pc-modelos-novos-2026-10-26.md),
com os mesmos hashes; não houve nova inferência nem mudança nos relatórios congelados.
Nenhum desses resultados qualifica a integração. Todos os oito arquivos estão abaixo de 2 GB decimais;
isso não comprova consumo de RAM nem compatibilidade nos aparelhos. Origens/licenças ainda pendentes
estão identificadas no catálogo, sem presumir autorização de distribuição.

<a id="seleção-ativa-da-poc-25102026"></a>

### Histórico da seleção de 25/10/2026

A seleção anterior tinha seis candidatos (os cinco aprovados na run054 mais Pulse Q4_K_M) e
foi substituída pelo refino de 26/10. Seus arquivos e resultados permanecem preservados.
O Pulse Q4_K_M continua como candidato: na [run054](docs/poc/resultados-pc-private-todos-2026-10-25.md),
obteve 72% em categoria, abaixo dos 80% exigidos. Os outros cinco passaram nessa bateria privada;
Tucano2 F16 e Qwen3.5-2B Q4_K_M ainda não repetiram os conjuntos de 37 e 30 casos com o perfil 14.

Na [bateria ampliada de 100 frases](docs/poc/resultados-pc-ampliada-2026-10-25.md), os seis
atenderam aos critérios com o perfil 14 e o validador 3 (run056). Essa aprovação se refere ao
novo conjunto sintético; o resultado privado anterior do Pulse Q4 permanece abaixo da meta.

## Critérios

- Rodar no Galaxy S20 FE (referência mínima, 6 GB de RAM total, bem menos livre para o app) e no
  S23 FE. Modelo que só roda no S23 FE não pode ser a base do produto.
- Meta de até 5 s do envio ao resultado visível de frase curta, com o modelo já carregado.
- Português do Brasil (frases financeiras do casal) e saída estruturada validada pelo app.
- Download: o usuário relaxou o teto de 2 GB decimais (24/10/2026), mas o plano e o AGENTS.md
  ainda registram 2 GB. A mudança precisa ser registrada antes de valer como decisão.
- Licença compatível com distribuir/baixar no app.

## Candidatos

| Modelo | Nota | Nota sem 5 s | Params | Licença | Português | Arquivo / runtime | Observações |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Qwen3.5-2B | **8** | **9** | 2B | Apache 2.0 | não nomeado (201 idiomas) | GGUF Q4_K_M 1,28 GB, Q5_K_M 1,44 GB, Q8_0 2,01 GB; llama.cpp (arquitetura `qwen35`) | Modo sem raciocínio é o padrão. Tem codificador de visão (mmproj separado, dispensável). Atenção: o cartão avisa de “loops de raciocínio” se ligar o thinking. |
| Qwen3-1.7B | **8** | **7** | 1,7B | Apache 2.0 | não nomeado (100+ idiomas) | GGUF (tamanho não conferido), llama.cpp | `/no_think` e `enable_thinking=False` funcionam. Contexto 32K. |
| Qwen2.5-1.5B-Instruct | **7** | **6** | 1,54B (1,31B sem embeddings) | Apache 2.0 | **listado** (29+ idiomas) | GGUF não conferido (a página aponta 352 versões quantizadas); llama.cpp via terceiros | Contexto 32.768; geração até 8192. Sem modo de raciocínio. O cartão cita saída estruturada/JSON como ponto forte da série, sem recurso dedicado. |
| Gemma 4 E2B | **7** | **8** | 2,3B efetivos (5,1B com embeddings) | Apache 2.0 | não nomeado (35+ idiomas de fábrica, 140+ no pré-treino) | LiteRT-LM `.litertlm` 2583 MB (versão só texto 2008 MB); pesos “a partir de 0,8 GB” com embeddings mapeados em memória | Contexto 128K nos cartões oficiais (32K na nota do LiteRT). Chamada de função nativa; thinking opcional. Os GGUF achados não servem (LM-Kit só F16, 9,31 GB; README vazio). |
| Qwen3-0.6B (já no plano) | **6** | **4** | 0,6B (0,44B sem embeddings) | Apache 2.0 | não nomeado (100+ idiomas) | GGUF (tamanho não conferido), llama.cpp | Linha de base do plano. Contexto 32K. Raciocínio híbrido ligado por padrão; `enable_thinking=False` desliga de vez. Sem raciocínio: temperatura 0,7, top_p 0,8, top_k 20; o cartão desaconselha decodificação gulosa. |
| Qwen3.5-0.8B | **6** | **5** | 0,8B | Apache 2.0 | não nomeado (201 idiomas) | GGUF (tamanhos não conferidos), llama.cpp | Cartão cita protótipo/ajuste fino. Não conferi os tamanhos dos GGUF. |
| Llama 3.2 1B Instruct (Q4_K_M, hugging-quants) | **5** | **4** | 1B | não indicada na página (conversão do Llama 3.2 1B Instruct da Meta; a licença do original não foi conferida) | não confirmado (selo “8 idiomas”, sem lista; o cartão do 3B lista português) | GGUF Q4_K_M **808 MB** (único arquivo do repositório); llama.cpp, Ollama, LM Studio | O repositório não informa contexto, licença nem uso de ferramentas. Verificar a licença no cartão original da Meta antes de usar. |
| Gemma 3 1B | **5** | **4** | 1B | Termos Gemma (acesso condicionado) | não nomeado (140+ idiomas) | GGUF (tamanho não conferido), llama.cpp/LiteRT | Contexto 32K. Exige aceitar os termos do Google. |
| SmolLM3-3B | **5** | **7** | 3B | Apache 2.0 | **listado** (6 idiomas nativos) | llama.cpp, ONNX, MLX, MLC, ExecuTorch (tamanho não conferido) | Raciocínio estendido ligado por padrão; `/no_think` desliga. Chamada de ferramentas via XML/Python. |
| Llama 3.2 3B Instruct | **4** | **7** | 3,21B | Llama 3.2 Community License | **listado oficialmente** | ExecuTorch SpinQuant 2435 MB, QLoRA 2529 MB (GGUF não conferido) | Licença personalizada: exige “Built with Llama”, aviso de atribuição e política de uso aceitável. Corte de conhecimento dez/2023. |
| Qwen3.5-4B | **4** | **6** | 4B (5B no HF) | Apache 2.0 | não nomeado (201 idiomas) | GGUF Q4_K_M 2,74 GB, Q5_K_M 3,14 GB; llama.cpp | Raciocínio é o padrão; desligar com `chat_template_kwargs: {enable_thinking: false}`. `/no_think` não é suportado. Provável só no S23 FE. |
| Phi-3.5 mini Instruct | **4** | **4** | 3,8B | MIT | **listado** (23 idiomas), mas o cartão avisa que foi treinado principalmente em inglês e que outros idiomas têm desempenho pior | GGUF não conferido (a página só aponta quantizações para llama.cpp, Ollama e LM Studio); há uma versão ONNX separada | Contexto 128K. Corte de conhecimento out/2023. MMLU multilíngue (5-shot) 55,4; sem número verificado para português. Sem modo de raciocínio. Formato de chat com `<\|system\|>`, `<\|user\|>`, `<\|assistant\|>`, `<\|end\|>`. O cartão não indica uso em decisões de crédito ou alocação de recursos sem avaliação adicional. |
| Phi-4-mini-instruct | **4** | **5** | 3,8B (“4B” no HF) | MIT | **listado** (23 idiomas), com o aviso de treino principalmente em inglês e desempenho pior em outros idiomas; a revisão de segurança (red-teaming) incluiu português | GGUF não conferido (a página aponta 202 quantizações). Versão ONNX oficial: int4 para CPU/celular e int4 para GPU, via ONNX Runtime GenAI (tamanhos não informados) | Contexto 128K; corte de conhecimento jun/2024. **Chamada de função suportada** (ferramentas em JSON entre `<\|tool\|>` e `<\|/tool\|>` no prompt de sistema); o cartão avisa que pode inventar nomes de funções. Sem modo de raciocínio. Multilingual MMLU 49,3 e MGSM 63,9 (tabela do cartão; sem número de português). Mesmo aviso do Phi-3.5 sobre decisões de crédito. |
| Qwen3-4B | **3** | **5** | 4B | Apache 2.0 | não nomeado (100+ idiomas) | GGUF (tamanho não conferido), llama.cpp | Mesmo controle de thinking. Provável só no S23 FE. |
| Gemma 3 4B | **2** | **5** | 4B | Termos Gemma (acesso condicionado) | não nomeado (140+ idiomas) | GGUF (tamanho não conferido) | Multimodal; contexto 128K. Provável só no S23 FE. |
| Gemma 3n E2B | **2** | **3** | 2B efetivos (6B brutos; “5B” no HF) | Termos Gemma (acesso condicionado) | não nomeado (140+ idiomas) | GGUF não conferido; só Transformers e vLLM/SGLang aparecem no cartão | Contexto 32K; entrada de texto, imagem, vídeo e áudio. Dados até jun/2024. Não encontrei no cartão arquivo para celular. Sucessor Gemma 4 E2B tem esse formato e licença melhor (Apache 2.0). |
| DeepSeek-R1-Distill-Qwen-1.5B | **2** | **2** | 1,5B | MIT (derivado do Qwen2.5-Math-1.5B, Apache 2.0) | não nomeado (benchmarks só em inglês e chinês) | GGUF não conferido (277 versões quantizadas) | Modelo de raciocínio longo. O cartão manda forçar `<think>` no início, não usar prompt de sistema e usar temperatura 0,5–0,7. Tende a ser lento e inadequado para extração curta em 5 s. Baixa prioridade. |
| Qwen2.5-1.5B (base) | **1** | **1** | 1,54B | Apache 2.0 | **listado** (29+ idiomas) | igual ao Instruct | O cartão não recomenda a base para conversa. Só serviria como ponto de partida para ajuste fino, que está fora do escopo inicial. |
| Phi-4-mini-reasoning | **1** | **1** | 3,8B (“4B” no HF) | MIT | **não listado**: o cartão declara só inglês e avisa que outros idiomas rendem pior (outra frase do mesmo cartão fala em compreensão multilíngue “similar à de modelos maiores”, sem números) | GGUF não conferido (a página aponta 44 quantizações para llama.cpp, Ollama, LM Studio e Jan); ONNX não citado para este modelo | Feito e testado **só para raciocínio matemático**, treinado com dados sintéticos de matemática. Contexto 128K; corte de conhecimento fev/2025. Exemplos do cartão geram até 32.768 tokens (temperatura 0,8, top_p 0,95), o que não combina com extração curta. Pouca capacidade de guardar fatos. Os mesmos avisos do Phi-3.5 sobre decisões de crédito. |

“Português: não nomeado” significa que a página lista só um total de idiomas. A qualidade em
português financeiro precisa ser medida em qualquer caso.

**Nota (0–10):** julgamento meu, antes de qualquer medição, de quão promissor o modelo é como base
do produto **no S20 FE**. A tabela está ordenada da maior para a menor nota; empates ficam por
potencial de qualidade. Pesam, nesta ordem aproximada: caber em RAM e velocidade no S20 FE (estimada);
português declarado; funcionar sem raciocínio longo (os 5 s); maturidade do suporte no motor do plano
(llama.cpp); licença e facilidade de distribuir o download. Nota alta não é decisão: a prova de conceito
pode inverter a ordem, e modelos de 4B, nota baixa aqui, podem ser os melhores no S23 FE.

**Nota sem 5 s (0–10):** mesma ideia, mas **ignorando a meta de 5 s**: velocidade e raciocínio longo
deixam de pesar. Continuam pesando a RAM do S20 FE (um modelo que o Android encerra não serve),
português declarado, qualidade esperada, maturidade do suporte no motor e licença. Por isso os modelos
maiores (3B–4B) e os que trazem raciocínio ligado sobem, e os menores (0,6B–1B) descem. A tabela segue
ordenada pela primeira nota. Ordem pela nota sem 5 s: Qwen3.5-2B (9); Gemma 4 E2B (8); Qwen3-1.7B,
SmolLM3-3B e Llama 3.2 3B (7); Qwen2.5-1.5B-Instruct e Qwen3.5-4B (6); Qwen3.5-0.8B, Qwen3-4B,
Gemma 3 4B e Phi-4-mini-instruct (5); Qwen3-0.6B, Llama 3.2 1B, Gemma 3 1B e Phi-3.5 mini (4); Gemma 3n E2B (3);
DeepSeek-R1-Distill (2); Qwen2.5-1.5B base e Phi-4-mini-reasoning (1). Também é julgamento meu, sem medição, e o tempo
de resposta continua sendo requisito do produto: esta coluna mostra o que mudaria se a meta caísse.

## Dados de desempenho disponíveis

Só o Gemma 4 E2B tem números publicados (LiteRT-LM, 1024 tokens de prefill, 256 de geração,
contexto 2048, 4 threads de CPU). Eles são de um Galaxy S26 Ultra, muito mais rápido que o S20 FE,
então **não valem** como previsão para os aparelhos do projeto:

- S26 Ultra CPU: prefill 557 tok/s, geração 46,9 tok/s, primeiro token em 1,8 s, 1733 MB de RAM.
- S26 Ultra GPU: prefill 3808 tok/s, geração 52,1 tok/s, primeiro token em 0,3 s, 676 MB.

Os demais candidatos não têm números de celular nas páginas lidas.

## Notas por modelo para o Galaxy S20 FE

Leitura de viabilidade, **sem medição**. Estimativas de memória são “arquivo do modelo mais cache de
contexto” e valem só para ordenar a prova de conceito. Premissas: 6 GB de RAM total, onde o app
provavelmente terá uns 2–3 GB utilizáveis antes de o Android encerrar processos (chute, a medir);
inferência em CPU (aceleração por GPU/NPU neste aparelho não foi verificada); queda de desempenho por
aquecimento em uso contínuo. Variante (4G/5G, processador), versão do Android e espaço livre do aparelho
ainda precisam ser conferidos.

- **Qwen3.5-2B (nota 8; sem 5 s: 9):** melhor aposta de qualidade que ainda cabe: Q4_K_M de 1,28 GB deixa margem; Q5_K_M
  (1,44 GB) também. A geração em CPU deve ser a questão para os 5 s; medir prefill e geração separados.
  Mesma ressalva de suporte do llama.cpp ao `qwen35`.
- **Qwen3-1.7B (nota 8; sem 5 s: 7):** deve caber com folga (Q4 em torno de 1 GB, estimativa). Bom ponto intermediário se o
  Qwen3.5-2B esbarrar no suporte do motor; precisa de `enable_thinking=False`.
- **Qwen2.5-1.5B-Instruct (nota 7; sem 5 s: 6):** ~1 GB estimado; cabe. Sem modo de raciocínio, então não gasta tokens
  pensando, o que ajuda nos 5 s. É de geração anterior, mas lista português e é o caminho mais simples.
- **Gemma 4 E2B (nota 7; sem 5 s: 8):** arquivo de 2583 MB (espaço e download), mas a RAM só de texto pode ficar perto de
  0,8–1,7 GB pelos números do LiteRT (medidos em aparelho muito mais novo). Dúvidas no S20 FE:
  compatibilidade do LiteRT-LM com o Android e o processador dele, e se a GPU ajuda. Candidato
  interessante, mas depende do teste e muda o motor.
- **Qwen3-0.6B (nota 6; sem 5 s: 4):** o mais folgado em RAM e velocidade. Risco é a qualidade em português e na extração;
  serve de piso de comparação.
- **Qwen3.5-0.8B (nota 6; sem 5 s: 5):** também folgado. Não carregar o codificador de visão (mmproj). Verificar se a versão do
  llama.cpp que formos integrar já suporta a arquitetura `qwen35` (híbrida, com atenção linear) em ARM Android.
- **Llama 3.2 1B (Q4_K_M, 808 MB) (nota 5; sem 5 s: 4):** cabe sem esforço e deve ser rápido. A dúvida é o acerto em
  português e na extração estruturada com 1B. Confirmar licença.
- **Gemma 3 1B (nota 5; sem 5 s: 4):** leve (~0,8 GB estimados). Português a medir. Acesso condicionado aos termos do Google:
  o download automático dentro do app exige resolver a hospedagem e a licença (decisão em aberto).
- **SmolLM3-3B (nota 5; sem 5 s: 7):** ~1,9 GB estimados em Q4; limítrofe como o Llama 3.2 3B. Raciocínio ligado por padrão,
  precisa de `/no_think`; lista português.
- **Llama 3.2 3B (nota 4; sem 5 s: 7):** ~2 GB estimados em Q4; limítrofe no S20 FE e provavelmente lento em CPU para os 5 s.
  Vale como teste de estresse porque lista português. Licença própria, com atribuição.
- **Qwen3.5-4B (nota 4; sem 5 s: 6):** Q4_K_M de 2,74 GB mais cache deve passar de ~3 GB, perto do que o Android tolera num
  celular de 6 GB; risco alto de o sistema encerrar o app. Provavelmente fora no S20 FE; testar só no S23 FE.
  Vem com raciocínio ligado e precisa ser desligado.
- **Phi-3.5 mini (nota 4; sem 5 s: 4):** ~2,2–2,4 GB estimados em Q4; limítrofe no S20 FE e provavelmente lento em CPU para
  os 5 s, como os de ~3B e 4B. Pontos a favor: licença MIT, sem raciocínio longo e português listado. Contra:
  o próprio cartão diz que idiomas diferentes do inglês rendem pior; a qualidade em português financeiro é a dúvida.
  Teste de estresse no S20 FE e candidato provável no S23 FE.
- **Phi-4-mini-instruct (nota 4; sem 5 s: 5):** ~2,2–2,4 GB estimados em Q4, limítrofe no S20 FE como o Phi-3.5.
  Parece a melhor opção da família Phi para este caso: lista português, suporta chamada de função e não gasta
  tokens com raciocínio longo. O motor é a dúvida: a versão oficial para celular é ONNX Runtime GenAI, fora do
  llama.cpp do plano (há quantizações GGUF da comunidade, não conferidas). Os números multilíngues do próprio
  cartão são modestos (Multilingual MMLU 49,3; na mesma tabela o Phi-3.5 mini aparece com 51,8), então o
  ganho sobre o Phi-3.5 em português não está provado. Vale como teste de estresse no S20 FE e candidato no S23 FE.
- **Qwen3-4B (nota 3; sem 5 s: 5):** mesma situação do Qwen3.5-4B (~2,5 GB estimados): arriscado no S20 FE, teste no S23 FE.
- **Gemma 3 4B (nota 2; sem 5 s: 5):** ~2,5 GB estimados, arriscado no S20 FE como os outros 4B; mesma questão de acesso
  condicionado.
- **Gemma 3n E2B (nota 2; sem 5 s: 3):** o cartão não traz arquivo para celular e mostra só Transformers, então não há
  caminho claro para rodar no S20 FE. Preferir o Gemma 4 E2B, que tem versão LiteRT-LM.
- **DeepSeek-R1-Distill-Qwen-1.5B (nota 2; sem 5 s: 2):** cabe em RAM, mas o raciocínio longo gera muitos tokens antes da
  resposta e deve estourar os 5 s numa CPU de celular. Sem português declarado. Improvável.
- **Qwen2.5-1.5B (base) (nota 1; sem 5 s: 1):** sem interesse para o S20 FE nem para o produto; o cartão não recomenda para
  conversa. Só para ajuste fino, fora do escopo inicial.
- **Phi-4-mini-reasoning (nota 1; sem 5 s: 1):** ~2,2–2,4 GB estimados em Q4, limítrofe no S20 FE como os outros
  de ~4B. O problema não é só o tempo: o modelo é só para matemática, declara apenas inglês e gera cadeias de
  raciocínio muito longas. Para registrar lançamentos em português, a tarefa não é a dele. Útil, no máximo, como
  referência de raciocínio passo a passo, que o produto não pede (o app calcula os valores).

## Por que cada grupo

- **Primeira rodada (cabem no S20 FE em tese):** Qwen3-0.6B, Qwen3.5-0.8B, Qwen3.5-2B, Qwen3-1.7B,
  Qwen2.5-1.5B-Instruct, Llama 3.2 1B (Q4_K_M, 808 MB), Gemma 3 1B, Gemma 4 E2B, Gemma 3n E2B.
- **Só S23 FE (teto de RAM do S20 FE):** Qwen3.5-4B, Qwen3-4B, Gemma 3 4B, Phi-3.5 mini, Phi-4-mini-instruct, Llama 3.2 3B, SmolLM3-3B.
  Entram para mostrar se o ganho de qualidade compensa. Os de ~3B podem caber no S20 FE; medir.
- **Baixa prioridade:** DeepSeek-R1-Distill-Qwen-1.5B (raciocínio longo, sem português declarado),
  Phi-4-mini-reasoning (só matemática, só inglês) e Qwen2.5-1.5B base (não indicado para conversa).
  Mantidos na lista para registro.
- **Para quem o português importa mais:** Llama 3.2 3B, SmolLM3-3B, Phi-3.5 mini e Phi-4-mini-instruct (com a ressalva de
  desempenho pior fora do inglês) e Qwen2.5-1.5B(-Instruct) listam o idioma explicitamente; os outros só dizem “100+/140+/201 idiomas”.

## Observações para a prova de conceito

- Gemma 4 E2B depende do LiteRT-LM (outro motor, fora do llama.cpp do plano). Escolhê-lo
  muda o motor e deve ser avaliado junto, não como troca isolada de modelo.
- Modelos com raciocínio ligado por padrão (Qwen3.5-4B, SmolLM3) precisam do modo sem raciocínio
  para caber nos 5 s; verifique se o motor escolhido repassa essa opção.
- Saída estruturada: nenhum cartão garante JSON válido. O plano já prevê gramática/esquema
  (llama.cpp) e validação no app.
- Medir por modelo e aparelho: RAM real (pico), tempo até o resultado, acerto nas frases do casal,
  aquecimento e bateria. Medir carga do modelo e download separadamente.

## Descartados nesta rodada

- Llama 3.1 8B, Qwen 7B, Mistral Nemo 12B: não cabem na RAM do S20 FE (estimativa, Q4 de ~4,4 a ~7 GB).
- Pythia e MobileLLaMA-2.7B: antigos, sem bom ajuste de instrução nem português.
- Gemini Nano (AICore): depende de suporte do fabricante; não confirmado para S20 FE e S23 FE.

## Pistas não verificadas

- Falcon 3 (1B e 3B): um artigo (Tucano 2) o cita com dados de treino em português. Não li o cartão.
- Tucano 2 (modelos abertos para português): não avaliei tamanho, licença nem formato.

## Arquivos GGUF locais

Pasta fora do repositório: `E:\Projects\ai-models` (não versionar). Conferido em 24/10/2026: os 7 arquivos
esperados estavam lá, sem outros (em 25/10/2026 entraram mais onze; veja abaixo). Todos começam com o cabeçalho `GGUF` versão 3. Os SHA-256 foram
calculados aqui e **não foram comparados** com os publicados no Hugging Face (não busquei esses valores);
servem para detectar corrupção ou troca depois. Todos ficam abaixo do teto de 2.000.000.000 bytes que a
spec S3 ainda exige (item 7); o maior é o Qwen3-1.7B Q8_0, com 1.834.426.016 bytes.

| Arquivo | Candidato | Quant. | Bytes | SHA-256 | Origem esperada |
| --- | --- | --- | --- | --- | --- |
| `Qwen3.5-2B-Q4_K_M.gguf` | Qwen3.5-2B | Q4_K_M | 1.280.835.840 | `aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223` | unsloth/Qwen3.5-2B-GGUF |
| `Qwen3.5-2B-Q5_K_M.gguf` | Qwen3.5-2B | Q5_K_M | 1.435.238.656 | `1885b3a9195f8cc09da9a7a7a75afdc1e8d5cbf9fc4a499c3961dddea37098ac` | unsloth/Qwen3.5-2B-GGUF |
| `Qwen3-1.7B-Q4_K_M.gguf` | Qwen3-1.7B | Q4_K_M | 1.107.409.472 | `b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897` | unsloth/Qwen3-1.7B-GGUF |
| `Qwen3-1.7B-Q8_0.gguf` | Qwen3-1.7B | Q8_0 | 1.834.426.016 | `061b54daade076b5d3362dac252678d17da8c68f07560be70818cace6590cb1a` | Qwen/Qwen3-1.7B-GGUF ou unsloth (mesmo tamanho exibido) |
| `Qwen3-0.6B-Q4_K_M.gguf` | Qwen3-0.6B | Q4_K_M | 396.705.472 | `ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a` | unsloth/Qwen3-0.6B-GGUF |
| `Qwen3-0.6B-Q8_0.gguf` | Qwen3-0.6B | Q8_0 | 639.446.688 | `9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031` | Qwen/Qwen3-0.6B-GGUF ou unsloth (mesmo tamanho exibido) |
| `llama-3.2-1b-instruct-q4_k_m.gguf` | Llama 3.2 1B Instruct | Q4_K_M | 807.690.656 | `1d0e9419ec4e12aef73ccf4ffd122703e94c48344a96bc7c5f0f2772c2152ce3` | hugging-quants/Llama-3.2-1B-Instruct-Q4_K_M-GGUF |

### Fine-tunes financeiros (acrescentados em 25/10/2026)

Onze arquivos novos em `E:\Projects\ai-models`, além dos sete acima, todos com cabeçalho GGUF válido. Bytes e SHA-256 foram
calculados aqui em 25/10/2026 e **não foram comparados** com os publicados. Origem e licença vêm de
[docs/poc/modelos-financeiros.md](docs/poc/modelos-financeiros.md) (pesquisa sem download; nada foi medido). São
**candidatos a medir, não decisão**, e nenhum demonstra interpretar lançamentos em pt-BR no contrato do Accord: vários
fazem outra tarefa (consulta de despesas, conselho em inglês, sentimento, parser em sueco). Estilo de prompt: lido do
`tokenizer.chat_template` do GGUF (Llama 3 ou ChatML); onde o autor não usa o bloco `<think>` vazio, a entrada do
catálogo avisa em `note`, porque o estilo disponível (`ChatMlNoThink`) é só o mais próximo.

| Arquivo | Candidato | Quant. | Bytes | Até 2 GB | SHA-256 | Origem esperada |
| --- | --- | --- | --- | --- | --- | --- |
| `Qwen3-0.6B-pulse-v4-Q4_K_M.gguf` | Pulse Qwen3-0.6B v4 | Q4_K_M | 396.704.544 | sim | `a6a847907ee2950cfa3392909ebd1d1e90c14ad0a4111c5a1aa9c46f170d5521` | PulseOS/pulse-local-qwen3-0.6b |
| `Qwen3-0.6B-pulse-v4-Q8_0.gguf` | Pulse Qwen3-0.6B v4 | Q8_0 | 639.446.816 | sim | `88a5c748cdf15d4aac4e89ba2629664b7bc0b9ca5c3b41ffa7a58ac9f9178690` | PulseOS/pulse-local-qwen3-0.6b |
| `spent-qwen3-0.6b-Q4_K_M.gguf` | SPENT Qwen3-0.6B | Q4_K_M | 396.704.960 | sim | `bea3867c8a3ee6c8efed3af59deb028ffad15721f1bdd14c4483aa8bd17fd515` | sanzh4rr/spent-qwen3-0.6b-expense-parser |
| `spent-qwen3-0.6b-v3-Q4_K_M.gguf` | SPENT Qwen3-0.6B v3 | Q4_K_M | 396.704.992 | sim | `0618ef539b9187fb59cec119483a887257b24bb2c1c9d0e462771b0a66fdcd62` | sanzh4rr/spent-qwen3-0.6b-expense-parser |
| `Qwen3-voice-assistant-slm-0.6B.gguf` | Distil Banking Qwen3-0.6B (voice assistant) | F32 | 2.390.146.560 | **NÃO** | `fb82efa9e2e1156805bf261ed29e39395b709eb2d8a65dfa9dcd74bf80e8717f` | distil-labs/distil-qwen3-0.6b-voice-assistant-banking-gguf |
| `Distil-expenses-Llama-3.2-1B-Instruct-f16.gguf` | Distil-expenses Llama-3.2-1B | F16 | 2.479.595.392 | **NÃO** | `61edc53422441dde53804890fadd0bc4c000b3915dc5cec3e5b1dffaa4f33ba5` | distil-labs/Distil-expenses-Llama-3.2-1B-Instruct (SafeTensors convertido localmente para GGUF f16) |
| `finance-specialist-v7-Q4_K_M.gguf` | Finance Specialist v7 (Llama-3.2-1B) | Q4_K_M | 807.690.624 | sim | `acb4901b264d2b6fd51deb14aae7a7c539dd17a8b9c1a222a0e1dc3818cd36a0` | Venkat9990/finance-specialist-v7 |
| `Personal-Finance-R2-f16.gguf` | Personal-Finance-R2 (Qwen3-1.7B) | F16 | 3.447.349.504 | **NÃO** | `bc52462a05bec138c0eb0c1ad148b4d8519adf4b43290d986bc8ba288c942e85` | khazarai/Personal-Finance-R2 (GGUF F16 de terceiro: Rustamshry/Personal-Finance-R2-GGUF) |
| `transaction-qwen3.5-2b-v2.1-Q5_K_M.gguf` | transaction Qwen3.5-2B v2.1 | Q5_K_M | 1.411.120.544 | sim | `ac97a57f9bc689c81547e92ec9dd877e0ef499cb370f8a2c8aafa2337cd199b4` | indrapurnayasa/transaction-qwen3.5-2b-v2.1-GGUF |
| `transaction-qwen3.5-2b-v2.1-f16.gguf` | transaction Qwen3.5-2B v2.1 | F16 | 3.775.708.576 | **NÃO** | `b4b8cdb0cb789e726df7b011d4338471c411e09eab52e830547b9d5c9d7ffca1` | indrapurnayasa/transaction-qwen3.5-2b-v2.1 (origem exata do F16 não conferida) |
| `Tucano2-qwen-0.5b-Merge-FinBERT-f16.gguf` | Tucano2-qwen-0.5B Merge-FinBERT | F16 | 984.450.304 | sim | `689bb4dcd3dee389a94dc24d6a4798b6caa8ef95f09db67762d0e1ee0f29d5ce` | Assaoka/Tucano2-qwen-0.5b-Merge-FinBERT (convertido localmente para GGUF f16) |

Os cinco arquivos F16/F32 (`Distil-expenses`, `Personal-Finance-R2`, `transaction ... f16`, `Qwen3-voice-assistant` e o
`Tucano2 ... f16`) são conversões ou envios sem quantização. No catálogo ativo de seis modelos,
apenas Tucano2 F16 permanece entre eles; está abaixo do teto e segue para a triagem nos celulares.
Distil Banking F32 permanece no inventário e nos resultados históricos; excede 2.000.000.000 bytes.
Os arquivos `.log` e as
pastas de conversão em `E:\Projects\ai-models` são resíduos de conversão local e não entram no catálogo.

Faltam na pasta, em relação à primeira rodada: Qwen2.5-1.5B-Instruct e Gemma 3 1B (ainda sem link
de GGUF conferido). A comparação Q4 contra Q8/Q5 do mesmo modelo mostra quanto a quantização custa
em acerto e em tempo.

## Fontes

- Gemma 4 E2B: https://huggingface.co/google/gemma-4-E2B
- Gemma 4 E2B, LiteRT-LM: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- Gemma 4 E2B, GGUF F16 (LM-Kit): https://huggingface.co/lm-kit/gemma-4-e2b-instruct-lmk
- Gemma 4, cartão oficial: https://ai.google.dev/gemma/docs/core/model_card_4
- Qwen3.5: https://huggingface.co/Qwen/Qwen3.5-0.8B , https://huggingface.co/Qwen/Qwen3.5-2B , https://huggingface.co/Qwen/Qwen3.5-4B
- Qwen3.5, GGUF: https://huggingface.co/unsloth/Qwen3.5-2B-GGUF , https://huggingface.co/unsloth/Qwen3.5-4B-GGUF
- Qwen3: https://huggingface.co/Qwen/Qwen3-0.6B , https://huggingface.co/Qwen/Qwen3-1.7B , https://huggingface.co/Qwen/Qwen3-4B
- Qwen2.5: https://huggingface.co/Qwen/Qwen2.5-1.5B , https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct
- Gemma 3: https://huggingface.co/google/gemma-3-1b-it , https://huggingface.co/google/gemma-3-4b-it
- Gemma 3n: https://huggingface.co/google/gemma-3n-E2B
- Llama 3.2 1B, GGUF Q4_K_M: https://huggingface.co/hugging-quants/Llama-3.2-1B-Instruct-Q4_K_M-GGUF
- DeepSeek-R1-Distill-Qwen-1.5B: https://huggingface.co/deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B
- Phi-3.5 mini: https://huggingface.co/microsoft/Phi-3.5-mini-instruct
- Phi-4-mini-instruct: https://huggingface.co/microsoft/Phi-4-mini-instruct
- Phi-4-mini-instruct, ONNX: https://huggingface.co/microsoft/Phi-4-mini-instruct-onnx
- Phi-4-mini-reasoning: https://huggingface.co/microsoft/Phi-4-mini-reasoning
- Llama 3.2 3B: https://huggingface.co/meta-llama/Llama-3.2-3B-Instruct
- SmolLM3: https://huggingface.co/HuggingFaceTB/SmolLM3-3B
