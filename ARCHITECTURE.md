# Arquitetura do Accord

Revisão: 24/10/2026. O repositório contém aplicativo Android/Compose, motor Kotlin, persistência
Room, protocolo/fake/adaptador remoto, migrações Supabase e testes. S1 está fechado em
`accord-9tl`, com pendências em `accord-abv`; S2 está entregue em `accord-rsw` (testes JVM,
Supabase local e emulador), com validação nos aparelhos em `accord-chf` e decisões abertas em
`accord-smv`. A existência desses arquivos não comprova implantação hospedada, validação nos dois
celulares ou aceite das propostas de implementação.

## Fontes e autoridade

- [AGENTS.md](AGENTS.md): regras de trabalho dos assistentes.
- [Plano de desenvolvimento](docs/plano-desenvolvimento.md): requisitos, etapas e aceite.
  Decisões posteriores das seções 14 e 16 prevalecem sobre propostas anteriores.
- [Índice das telas](docs/specs/design/README.md): navegação, prioridades e regras comuns de UX.
- [Especificações 01–38](docs/specs/design/README.md#4-índice-dos-arquivos): estados, recuperação,
  acessibilidade, aceite e dependências de cada tela. IDs identificam telas, não versões de API.
- [ADRs](docs/adr/README.md): decisões, motivos e histórico; status de decisão é separado da
  situação de implementação. ADRs propostos não alteram requisitos aprovados.

A documentação de produto é a referência; esta arquitetura a resume. Propostas de implementação
não transformam escolhas abertas em requisitos aprovados. Divergências sem solução nessas fontes
exigem registrar a questão no Beads e esclarecer a decisão afetada.

## Decisões confirmadas e escolhas abertas

| Tema | Confirmado | Ainda depende de implementação ou validação |
| --- | --- | --- |
| Aplicativo | Android nativo, Kotlin e Jetpack Compose | Módulos/build existem; desenho concreto da fachada proposto no ADR 0022 e validação nos aparelhos pendente |
| Aparelhos | Galaxy S20 FE como referência mínima e Galaxy S23 FE; S20 FE com 6 GB de RAM total informados | Variantes, Android, espaço livre, RAM do S23 FE e consumo real |
| Inferência | SLM no aparelho, sem fallback automático para IA remota | Modelo, motor, quantização e qualidade em português |
| Distribuição | Download separado no primeiro uso, até 2 GB decimais por celular | Hospedagem, tamanho efetivo, retomada e espaço instalado |
| Desempenho | Meta de até 5 s do envio ao resultado visível de frase curta, com modelo carregado | Medições nos dois celulares; carga inicial, RAM, bateria e aquecimento |
| Compartilhamento | Supabase hospedado no plano Free, Auth e PostgreSQL | Esquema, RPC, RLS e protocolo locais existem; propostas nos ADRs 0023/0024; hospedagem e validações finais não comprovadas aqui |
| Dados locais | Banco/fila persistidos; Room com BundledSQLiteDriver; Chat local | Room implementado (ADR 0016); política detalhada de acesso local e Chat de S3 pendentes |
| Importação | OFX estruturado e PDF bancário com texto selecionável, processados localmente | Bibliotecas e compatibilidade com fatura/extrato Itaú em BRL |
| Interface | Lucide e catálogo/logotipos locais de logos-bancos-br, versão estável fixada; [tokens](docs/design-tokens.md) e [ícones](docs/icones-categorias.md) decididos | Ativos incorporados; revisão visual nos temas e aparelhos pendente |

Qwen3-0.6B quantizado, llama.cpp/GGUF e LiteRT-LM são candidatos do plano §13.3, sem aprovação
por benchmark. Formato de um candidato não implica compatibilidade com outro motor. Download
máximo: 2.000.000.000 bytes para o conjunto necessário; não define RAM ou espaço instalado.
A meta de 5 s exclui download, carga inicial e sincronização e não impõe cancelamento automático.

## Mapa atual do repositório

```text
accord/
  AGENTS.md, CLAUDE.md       regras compartilhadas; CLAUDE.md importa AGENTS.md
  ARCHITECTURE.md            mapa atual, decisões e responsabilidades planejadas
  core/                      módulo Kotlin JVM sem Android: motor financeiro, contrato da fachada (S1);
                             core.chat (proposta, validador, coordenador, bateria fictícia) e
                             core.model (manifesto, instalação verificada, execução) do S3
  persistence/               Room: fachada local, operações, sessão e motor de sincronização; histórico
                             do Chat (tabelas chat_*, só no aparelho)
  remote/                    fake e adaptador Supabase HTTP (JVM)
  supabase/                  configuração local, migrações e testes pgTAP; sem prova de implantação hospedada
  app/                       Android: tema com os tokens, navegação das 5 raízes, telas Compose (S1.8);
                             telas implementadas: Orçamento, categoria, Lançamentos, detalhe, registro e
                             edição, ações vinculadas (pagar, receber, estorno, cancelar parcelas),
                             Compromissos, Cartão e faturas, Rendas, Mais, Contas, Categorias, Limites.
                             S2: Acesso, Espaço, Primeiros passos, Sincronização e Escolher versão (conflito),
                             indicadores e envio em segundo plano (WorkManager).
                             S3: Chat (04) com recibo, perguntas, falhas e registro manual com o que foi
                             entendido; Modelo no aparelho (25); histórico do Chat em Mais (limpar, exportar,
                             importar). Sem motor integrado o Chat guarda a mensagem ("sem modelo").
                             Revisar só lista os conflitos de edição.
                             Assets locais: catálogo de bancos e logos (assets/banks, logos-bancos-br)
  gradle/libs.versions.toml  versões fixadas; wrapper Gradle 9.8.2 (gradlew)
  docs/
    plano-desenvolvimento.md requisitos, decisões e critérios de aceite
    specs/                  specs de desenvolvimento S1–S7
      design/               índice e telas 01–38
    adr/                    decisões, propostas, guia e template
  .beads/                   tarefas e conhecimento persistente via bd
  .claude/                  agentes, skills e hooks
  .agents/                  entradas de descoberta de skills para Codex
  .codex/                   adaptadores de agentes, configuração e hooks
```

Existem `:core`, `:persistence`, `:remote` e `:app` (comandos em AGENTS.md). Detalhes implementados
da fachada, RPC e protocolo permanecem propostas nos ADRs 0022–0024. Projeções financeiras
assumidas em S1 estão no ADR 0025. O contrato do Chat e o histórico local (0027), a distribuição e o
ciclo de vida do modelo (0028) e a troca de forma de pagamento (0029) são propostas implementadas no S3.
Esta revisão documenta o estado; não aprova essas propostas nem escolhe framework de DI, modelo/motor,
hospedagem dos pesos ou leitor bancário. Nenhuma medição de modelo foi feita nos aparelhos (Etapa 0)
e o download real, o motor e o download em segundo plano pelo sistema não existem.

## Responsabilidades e fluxo local

O fluxo abaixo é conceitual e inclui S3/S4 ainda planejados. As responsabilidades do plano
§13.1 não obrigam um módulo Gradle por linha. Manter regras
financeiras testáveis sem inferência, rede ou componentes de interface.

| Responsabilidade | Papel |
| --- | --- |
| Interface Compose | Receber texto e ações, apresentar recibos, dúvidas, totais e estados reais |
| Interpretador local | Propor intenção e campos estruturados com contexto curto e relevante |
| Validador de ações | Verificar formato, valores, referências, alvo de correção e ambiguidade |
| Motor financeiro | Calcular dinheiro, parcelas, faturas, limites e previsões por regras explícitas |
| Persistência local | Preservar registros, rascunhos, pendências, configurações e operações por enviar |
| Sincronização | Enviar/receber dados autorizados com identidade, idempotência e controle de versão |
| Importação local | Extrair OFX/PDF, preparar prévia e preservar decisões de conferência |

```mermaid
flowchart TD
    Chat[Chat no Android] --> SLM[Interpretação local]
    SLM --> Validador[Validação da ação]
    Manual[Registro manual] --> Validador
    Arquivo[OFX ou PDF textual] --> Previa[Extração local e revisão]
    Previa --> Validador
    Validador -->|válida e clara| Motor[Motor financeiro determinístico]
    Validador -->|dúvida ou erro| Pendencia[Esclarecimento ou pendência recuperável]
    Motor --> Local[Banco local e fila persistida]
    Local --> Recibo[Resultado com editar e desfazer]
    Local <-->|dados financeiros com rede e autenticação| Sync[Sincronização]
    Sync <--> Supabase[Supabase Auth e PostgreSQL]
    Chat --> Historico[Histórico somente no aparelho]
```

O SLM propõe; o aplicativo valida e executa. Saída bem formada não prova interpretação correta.
Consultas usam funções controladas, sem SQL livre do modelo. O motor usa centavos ou decimal
exato; totais não são calculados pelo SLM a partir do prompt. Ações claras válidas salvam sem
confirmação extra, com editar/desfazer; dúvidas preservam o trabalho e pedem esclarecimento.
Dúvida apenas sobre fatura perto do fechamento permite registro provisório “a conferir”.

Inferência e extração não bloqueiam a interface. Falha, demora, cancelamento ou modelo ausente
preservam mensagem e alternativa manual. Download mostra tamanho real e progresso; instalação
incompleta não é apresentada como modelo pronto. Atualizar modelo não reinterpreta dados salvos.

## Modelo financeiro e invariantes

Vocabulário do plano §§3–5 e do índice das telas; não é descrição exaustiva do esquema implementado.

| Conceito | Regra que precisa permanecer verdadeira |
| --- | --- |
| Espaço do casal | Duas contas individuais, mesmo acesso de criação/edição e orçamento compartilhado |
| Conta bancária | Banco e apelido compartilhados; conta padrão é preferência individual de preenchimento |
| Lançamento | Identidade estável, autor e, quando conhecido, quem gastou; correção não cria cópia |
| Compra parcelada | Compra e parcelas vinculadas; cada parcela ocupa sua fatura sem somar o total novamente |
| Recorrência | Série sem término obrigatório, distinta de parcelamento finito |
| Fatura/pagamento | Cartão usa mês de vencimento da fatura atribuída; quitação não gera nova despesa |
| Limite | Categoria principal com divisão opcional entre subcategorias, parte do mesmo total |
| Pendência | Dúvida ou conflito recuperável; rascunho inválido não é despesa efetivada |
| Intenção P2 | Compartilhada, separada de compromisso; conversão explícita gera um único vínculo |

- **Datas:** Pix usa a movimentação. Boleto não pago é previsto no vencimento; pago ocupa só
  o mês do pagamento efetivo, retirando a previsão anterior e preservando as duas datas.
  Cartão usa fatura atribuída; fechamento dia 8 e vencimento dia 15 são configuração inicial,
  sem garantir atribuição do emissor. Atividade P1 usa data original, sem outra despesa.
  Um cartão é o escopo inicial, sem gestão de múltiplos cartões por suposição.
- **Limites:** soma destinada às subcategorias não supera o principal. Sem distribuir difere de
  restante após gastos; subcategoria sem divisão não recebe limite zero presumido. Excesso não
  amplia nem redistribui limites. Cada edição escolhe só o mês ou esse mês e os seguintes,
  preservando anteriores, categorias não editadas e configuração futura numa edição pontual.
  Reorganizar subcategoria com divisão exige revisar a distribuição envolvida antes de confirmar,
  sem transferência silenciosa. Limite não definido difere de zero; omitir percentual sem sentido.
- **Correções:** categoria/descrição de compra parcelada mudam em todas as suas parcelas,
  inclusive anteriores. Recorrência escolhe só a ocorrência ou esta e as próximas. Não aplicar
  esses alcances por suposição a valores, datas, cancelamentos ou pagamentos. Preservar
  pagamentos, identidades e originais importados; recalcular períodos afetados.
- **Créditos:** estorno confirmado reduz categoria original no período do crédito, preserva
  compra e não vira renda. Futuras canceladas só saem após confirmar não cobrança, sem crédito
  fictício. No P0, quitação da fatura é integral e conferida; parcial/divergente mantém valor
  real para revisão. Encargos confirmados ficam em Serviços > Tarifas e juros, corrigíveis,
  sem projeção bancária inventada.
- **Totais:** renda prevista não é saldo confirmado; pago não se soma ao comprometido; conhecidos
  e novos variáveis estimados são separados. Assinaturas ficam em Assinaturas; compras variadas
  usam categoria predominante. Simulações/intenções não alteram orçamento real.

## Autenticação, autorização e sincronização

Fontes: telas [01](docs/specs/design/01-acesso.md), [02](docs/specs/design/02-espaco-do-casal.md) e
[26](docs/specs/design/26-sincronizacao.md). Supabase Auth usa e-mail/senha. Piloto sem envio de
confirmação, recuperação, OTP, magic link ou convite por e-mail. Planejar Confirm Email
desativado, sem SMTP. E-mail identifica login sem comprovar posse; ajuda orienta atendimento
manual fora do app, em ambiente confiável.

A segunda conta entra por código de convite gerado no aplicativo. Autorização do espaço e
validação/consumo do convite ocorrem no serviço/banco; alterar referência no cliente não concede
acesso. Ambos têm os mesmos direitos no espaço, sem hierarquia administrativa no produto.
Credenciais administrativas nunca entram no aplicativo. Há testes de permissão locais para
associados e não associados; execução histórica está no Beads, sem reexecução nesta revisão.
Isso não comprova permissões de um projeto hospedado.

Banco e fila preservam trabalho offline conforme autorização local, cuja política detalhada
continua aberta. Sincronização exige sessão válida, identidade estável por operação,
idempotência e controle de versões no serviço. Resposta perdida seguida de reenvio não reaplica
operação reconhecida. O desenho técnico deve garantir consistência entre alteração local e fila.

Conflitos preservam as duas versões para escolha explícita, sem última edição vence ou mesclagem
automática. A escolha mantém identidade; nova alteração concorrente exige comparação atualizada.
Versões não viram duas despesas. Realtime, se adotado, não substitui fila/persistência/protocolo.
Mensagens e PDFs/OFX completos não são sincronizados; intenções salvas entram apenas em P2.

Mostrar salvo no aparelho, aguardando envio, reconhecido e conflito separadamente. Último sucesso
não prova ausência de registros offline no outro celular. Sessão expirada, serviço pausado ou
quota não descartam operações. Se o Free exigir pagamento, rever com o usuário sem habilitar
cobrança automática nem prometer gratuidade permanente. Condições comerciais devem ser
reverificadas quando uma decisão depender delas; este texto registra a escolha de produto.

## Importação e rastreabilidade

Fluxo: [selecionar](docs/specs/design/15-importar-arquivo.md) →
[revisar](docs/specs/design/16-revisar-importacao.md) → [resultado](docs/specs/design/17-resultado-importacao.md).
OFX usa parser estruturado local; PDF usa extração local de texto dos movimentos. SLM pode ajudar
classificação sem substituir extração/conferência. OCR e PDF digitalizado/imagem ficam fora do P0.

Sem cópia permanente do PDF/OFX: persistir extraídos, campos originais dos movimentos, metadados
e decisões; descartar temporários ao concluir/cancelar leitura sem alterar o arquivo externo.
Retomar revisão usa dados persistidos; reler exige nova seleção. Reimportar não duplica;
coincidência de valor/data não funde compras legítimas. FITID repetido divergente exige revisão.

Itaú é prioridade de validação de fatura e extrato em BRL, sem suporte comprovado e sem promessa
de ambos os formatos para ambos os tipos. A inspeção documentada de `example.ofx` tem dez
movimentos em EUR e FITID repetido; não comprova origem Itaú ou compatibilidade BRL. Moeda não
suportada bloqueia efetivação, preservando moeda da prévia sem conversão automática. A amostra
não está no repositório. Débito de quitação do extrato vincula à fatura sem duplicar despesas.

## Previsões, alertas e evolução

Previsões explicam conhecidos e novos variáveis sem projetar novamente parcelas/contas já
incluídas; dados incompletos reduzem confiança. No máximo um evento de alerta por categoria
principal por semana, destinado aos dois, respeitando preferências/permissões dos aparelhos.
Janela em America/Sao_Paulo: segunda 00h até segunda seguinte 00h, fim exclusivo. Renovar não
agenda envio; editar limite/recalcular não renova cota. Notificar offline primeiro foi confirmado;
coordenação concreta entre aparelhos continua pendente de implementação em S5 (ADR 0020).

São 28 telas P0, cinco P1 e cinco P2. P0 entrega registrar, acompanhar, importar, conferir e
retomar. P1 acrescenta revisão semanal, atividade, assinaturas e comparações; P2, intenções,
simulação e pistas. Não criar dependência escondida de P1/P2 para concluir P0. Revisão P1 abre
última semana completa de segunda a domingo no fuso de referência; retomar preserva período,
sem alterar limites ou janela dos alertas. Conversão P2 cria um compromisso vinculado por intenção,
inclusive com tentativas nos dois aparelhos; dispensar pista não exclui nem estorna despesas.

## Interface e validação futura

Chat é a tela inicial. Raízes: Chat, Orçamento, Lançamentos, Revisar e Mais. Detalhes preservam
contexto ao voltar. Seguir cada spec, linguagem pt-BR clara e sem julgamento, temas claro/escuro,
fonte nativa e tokens semânticos. Corpo 16 sp, títulos 20–24 sp, margens 16 dp e ritmo 4/8 dp
são referências adaptáveis, não alturas rígidas. Validar TalkBack, texto ampliado, teclado,
retrato/paisagem e movimento reduzido; toque 48 × 48 dp, contraste de texto 4,5:1 e gráficos
3:1, com alternativa à cor.

Lucide usa adaptação comunitária Compose Icons Android, versão a verificar. Catálogo/ativos de
logos-bancos-br ficam locais, com origem e ISPB/COMPE, cores/proporção preservadas e fallback
genérico com nome. Sem CDN no uso ou JavaScript executado no celular. Catálogo não comprova
importação compatível nem integração bancária.

Antes de aprovar a implementação, verificar regras de dinheiro/datas com exemplos independentes,
ações inválidas do SLM, autorização do espaço, reenvios/conflitos, importação interrompida e
alertas nos dois aparelhos. Medir inferência fria/carregada, RAM, qualidade, bateria e calor;
realizar depois piloto de 30 dias. Aceite detalhado: plano §§7–8, 12–14 e cada tela. Esta revisão
não executou nenhuma dessas validações de produto.
