# AGENTS.md

Orientações compartilhadas para Codex e Claude Code. `CLAUDE.md` importa este arquivo; Codex
lê diretamente. Ler instruções aninhadas antes de mudar uma área. Pedidos explícitos do usuário
e instruções da sessão prevalecem sobre orientações locais.

## Produto e estado real

**Accord** é um aplicativo Android de controle financeiro do casal, em português brasileiro.
A base contém documentação, Beads, aplicativo Compose, motor financeiro, persistência Room,
protocolo/adaptador remoto, migrações Supabase e testes. S1 está entregue no Beads, com pendências
de validação; S2 está entregue no Beads, com validação nos aparelhos (`accord-chf`) e decisões
abertas (`accord-smv`) pendentes. Código e ambiente local não comprovam implantação hospedada,
validação nos dois celulares ou aprovação de propostas de implementação.
Não apresentar requisitos, wireframes, fornecedor escolhido ou metas como funcionalidades
implementadas, configurações executadas ou resultados medidos.

Confirmados: **Kotlin e Jetpack Compose**, **SLM no aparelho**, **Supabase Auth e PostgreSQL
hospedados no plano Free**, com **banco e fila locais**. **Room com BundledSQLiteDriver** foi
confirmado em 24/10/2026. Módulos, adaptador, esquema e protocolo concretos já têm implementação;
seu aceite e pendências estão diferenciados nos ADRs. Modelo/motor de inferência e leitores
bancários continuam abertos. Não introduzir outra plataforma ou backend por herança de outro projeto.

## Ler antes de alterar

| Documento | Autoridade e uso |
| --- | --- |
| [Plano de desenvolvimento](docs/plano-desenvolvimento.md) | Requisitos, etapas e aceite; decisões posteriores §§14 e 16 prevalecem sobre propostas anteriores |
| [Índice das telas](docs/specs/design/README.md) | Navegação, regras comuns, prioridades, direção visual e pendências |
| [Specs 01–38](docs/specs/design/README.md#4-índice-dos-arquivos) | Tela afetada, estados, recuperação, acessibilidade, aceite e dependências |
| [Arquitetura](ARCHITECTURE.md) | Mapa atual, responsabilidades planejadas, invariantes e escolhas abertas |
| [ADRs](docs/adr/README.md) | Decisões estruturais, motivos, autoridade e histórico; consultar os relacionados à mudança |
| [Catálogo de agentes](.claude/agents-roster.md) | Responsabilidades dos papéis locais e adaptação entre runtimes |

IDs 01–38 identificam telas: 28 P0, cinco P1 e cinco P2. Não usar numeração de specs de outro
projeto nem transformar P1/P2 em pré-requisito oculto de P0. Ler destinos relacionados se a
mudança afetar continuidade. O `plan.md` original citado nas fontes é externo; a cópia de
trabalho está em `docs/`. Preservar decisões confirmadas, identificar propostas como propostas
e não resolver pendências por suposição. Quando faltar informação decisiva, avançar no que é
independente e esclarecer uma decisão por vez com o usuário, conforme plano §14.

## Registro contínuo de decisões

Ao propor ou alterar tecnologia estrutural, contratos, persistência, sincronização, autorização,
privacidade ou regras financeiras que moldam dados, consultar os ADRs relacionados e aplicar
[adr-writing](.agents/skills/adr-writing/SKILL.md). Registrar a decisão relevante na mesma entrega,
conforme o [guia](docs/adr/README.md), e conferir o registro na revisão/encerramento. Aceite segue
a autoridade da decisão; código existente não aprova proposta. Beads mantém tarefas e memórias
curtas com links; ADRs preservam justificativas. O papel `adr-writer` não autoriza delegação.

## Invariantes do produto

- **Acesso:** contas individuais por e-mail/senha; ambos criam/editam todos os dados e ajustes
  do espaço, sem papéis administrativos, gastos privados ou cotas individuais. Convite por
  código e autorização do espaço são validados no serviço/banco, não apenas no cliente.
- **Piloto sem e-mails:** planejar Confirm Email desativado; sem confirmação, recuperação, OTP,
  magic link ou convite por e-mail, SMTP ou compra de domínio. E-mail identifica login sem
  comprovar posse. Ajuda orienta atendimento manual fora do app; credenciais administrativas
  ficam em ambiente confiável, nunca no aplicativo.
- **IA:** SLM propõe ações estruturadas; app valida campos, referências, alvo e ambiguidade e
  calcula valores. Sem SQL livre do modelo, confiança só na sua autoavaliação ou IA externa
  automática. Ações claras válidas salvam com editar/desfazer; dúvidas são recuperáveis.
- **Dinheiro:** centavos ou decimal exato. Categoria/subcategoria, compra/parcelas e quitação
  da fatura não duplicam despesas. Renda prevista não é saldo confirmado; pago não se soma ao
  comprometido. Intenções/simulações não mudam orçamento real. Assinaturas ficam em Assinaturas;
  compras variadas usam categoria predominante.
- **Datas:** cartão/parcelas usam vencimento da fatura atribuída; Pix usa movimentação. Boleto
  não pago é previsto pelo vencimento; pago ocupa só o mês do pagamento efetivo, removendo a
  previsão e preservando ambas as datas. Fechamento 8/vencimento 15 são configuração inicial,
  sem garantir atribuição. Dúvida só de fatura permite salvar na estimada como “a conferir”.
  Um cartão é o escopo inicial; não adicionar gestão de múltiplos cartões por suposição.
- **Créditos e pagamentos:** estorno confirmado abate categoria original no período do crédito,
  preserva compra e não vira renda. Futuras canceladas ficam até confirmar não cobrança, sem
  crédito fictício. P0 aceita apenas quitação integral conferida da fatura; parcial/divergente
  preserva valor real para revisão. Encargos confirmados: Serviços > Tarifas e juros, com
  correção possível e sem inventar juros bancários.
- **Limites:** categoria principal com divisão opcional total/parcial entre subcategorias,
  sem ampliar total. Soma distribuída não supera principal; sem distribuir difere de restante
  após gastos. Sem divisão não presume limite zero; excesso não redistribui nem aumenta limites.
  Cada edição escolhe só o mês ou esse mês e os seguintes, preservando anteriores, categorias
  não editadas e configuração futura numa edição pontual.
  Reorganizar subcategoria com divisão exige revisar as distribuições envolvidas antes de
  confirmar, sem transferir valores silenciosamente. Limite ausente difere de limite zero.
- **Alcance de edição:** categoria/descrição de compra parcelada mudam em todas as parcelas,
  inclusive anteriores. Recorrência escolhe só esta ocorrência ou esta e as próximas. Preservar
  identidades, pagamentos e originais importados, mostrar alcance e recalcular períodos.
  Não estender esses alcances a valor/datas/cancelamento/pagamento por suposição.
- **Sincronização:** banco/fila persistidos, IDs estáveis, reenvio idempotente e versão validada
  no serviço. Conflitos mostram duas versões para escolha explícita, sem última edição vence
  ou mesclagem automática. Nova edição exige nova comparação. Versões não viram duas despesas.
  Salvo localmente difere de reconhecido; não afirmar conhecer registros offline do outro
  celular. Realtime não substitui fila/protocolo. Política detalhada de acesso local é aberta.
- **Privacidade:** Chat só no aparelho de origem; dados financeiros compartilhados. Não
  sincronizar mensagens/transcrições ou PDFs/OFX completos. Não incluir senhas, tokens, chaves
  administrativas, conversas ou conteúdo bancário em logs ou fixtures compartilhadas.
- **Importação:** OFX estruturado e PDF bancário com texto selecionável, processados localmente;
  sem OCR no P0. Prévia/revisão antes de efetivar. Guardar extraídos, campos originais dos
  movimentos, metadados e decisões, sem cópia permanente do arquivo. Descartar temporários sem
  alterar arquivo externo. Retomar usa dados persistidos; reler exige seleção. Reimportar não
  duplica; valor/data iguais não fundem compras legítimas; FITID divergente exige revisão.
- **Compatibilidade:** Itaú é prioridade de fatura/extrato BRL, sem suporte comprovado. Inspeção
  de `example.ofx`: dez movimentos EUR e FITID repetido, sem origem Itaú comprovada. Moeda não
  suportada bloqueia efetivação sem conversão automática; preservar moeda da prévia.
- **Alertas:** até um evento por categoria principal por semana, destinado aos dois e respeitando
  preferências/permissões. Janela America/Sao_Paulo: segunda 00h até segunda seguinte 00h,
  fim exclusivo. Recálculo/limites não renovam cota; nova janela não agenda envio. Coordenação
  entre aparelhos ainda precisa de implementação; conhecidos e novos variáveis são separados.
- **Custo:** preservar uso local/fila em falha ou pausa do serviço. Se Free exigir pagamento,
  rever com usuário; sem cobrança automática. Consultar condições atuais quando necessário,
  sem prometer gratuidade permanente.

Fora do escopo inicial: Open Finance, mensageria externa, investimentos, múltiplas famílias,
reembolsos com terceiros, divisão obrigatória por item, IA remota automática e ajuste fino.

## Android, modelo e experiência

Validar Galaxy S20 FE e S23 FE. S20 FE é referência mínima, com 6 GB de RAM total informados;
isso não é memória disponível. Verificar variante, Android e espaço nos dois, RAM do S23 FE e
consumo real. Modelo/motor/quantização/hospedagem e critérios de qualidade/RAM são abertos.
Candidatos do plano §13.3 são hipóteses de prova de conceito, não decisões finais.

Download separado no primeiro uso, até **2 GB decimais** por celular para o conjunto necessário,
com tamanho real/progresso e alternativa manual. Distinguir download, espaço instalado e RAM.
Meta: **até 5 s** do envio ao resultado visível de frase curta com modelo carregado; carga,
download e sync são medidos separadamente. Sem medição concluída ou cancelamento automático
pela meta. Preservar mensagem/registro manual em demora/falha; inferência fora da thread da UI.

Chat inicia o uso. Raízes: Chat, Orçamento, Lançamentos, Revisar e Mais. Seguir estados/recuperação
de cada spec e preservar rascunhos/contexto ao voltar. Texto pt-BR claro e sem julgamento,
temas claro/escuro, fonte nativa, tokens semânticos, TalkBack, texto ampliado, movimento reduzido
e teclado sem esconder ações. Referências adaptáveis: corpo 16 sp, títulos 20–24 sp, margem
16 dp, ritmo 4/8 dp; toque 48 × 48 dp, contraste texto 4,5:1 e gráficos essenciais 3:1.

Lucide para interface via adaptação comunitária Compose Icons Android (`icons-lucide-android`);
logos-bancos-br para catálogo/logos locais com origem e ISPB/COMPE. Sem CDN em uso ou JS no
celular. Ausência de logo usa símbolo genérico com nome; preservar cores/proporção nos temas.
Catálogo não prova importação compatível ou conexão bancária. Skills genéricas não substituem
as escolhas de plataforma, navegação, ícones ou acessibilidade.

## Trabalho, comandos e validação

Inspecionar árvore real antes de sugerir comandos. Existem o wrapper Gradle (9.8.2) e os módulos
`:core` (Kotlin JVM, motor financeiro e contratos), `:persistence` (Room, fachada e sync local),
`:remote` (fake e adaptador HTTP) e `:app` (Android, Compose; minSdk 29, compileSdk 37).
Há configuração/migrações/testes Supabase locais em `supabase/` (ver seu README); isso não prova
serviço em execução ou projeto hospedado. Não há gates `just`. Não executar comandos de outro
projeto como disponíveis. O `java` do PATH desta máquina é
o 1.8; Gradle exige JDK 17+ (verificado com o JBR 21 do Android Studio):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\SEU_USUARIO\AppData\Local\Android\Sdk"
.\gradlew.bat :core:test :persistence:test :remote:test # JVM, sem aparelho
.\gradlew.bat :app:assembleDebug                # APK de debug
.\gradlew.bat :app:connectedDebugAndroidTest    # exige emulador ou aparelho conectado
supabase start; supabase test db                # Supabase LOCAL (Docker; portas 5532x), pgTAP; ver supabase/README.md
.\scripts\run-debug.ps1                         # instala o debug e abre o app (-Fresh, -Shot, -Dump, -TestClass, -Serial)
```

`gradle.properties` ativa cache de build, execução paralela e cache de configuração. O emulador
preferido é o AVD `Accord_Rapido_API_33` (Android 33 Google APIs x86_64, 3 GB de RAM, 6 núcleos,
GPU `host`, 1080×2400 a 420 dpi como o S20 FE); o `4.65_720p_Galaxy_Nexus_API_33` (720×1280)
continua disponível. Com os dois abertos, informar o serial (`-Serial` ou `ANDROID_SERIAL`). No
AVD rápido as animações do sistema estão em 0 (`adb shell settings put global
window_animation_scale 0`, idem `transition_animation_scale` e `animator_duration_scale`); ele não
serve para validar movimento nem substitui a validação no Galaxy S20 FE e no S23 FE. Não há
`cmdline-tools` instalado (sem `avdmanager`); os AVDs foram criados copiando `config.ini`.

Para docs/configuração, verificar UTF-8, links locais, frontmatter, JSON/TOML, coerência entre
papéis e existência de skills. Após editar nome/descrição/lista de skills de agente ou metadados
de skill, regenerar adaptadores e entradas de descoberta:

```powershell
python .codex/sync-agents.py
python .codex/sync-agents.py --check
python -m unittest discover -s .codex -p "test_*.py"
git diff --check
git status --short
```

Gerador usa Python 3 com PyYAML, sem depender do build Android. Não sobrescreve
adaptações manuais nem remove obsoletos automaticamente; revisar renomes/remoções. Hooks Beads
em `.claude/settings.json` e `.codex/hooks.json` continuam; não presumir recarga de agentes
numa sessão aberta. Não alterar permissões, sandbox ou modelos por decisão de produto.

Testes: `fun x() = runBlocking { ... }` cujo último comando devolve valor é ignorado em silêncio
pelo JUnit; usar `runBlocking<Unit>` e conferir a contagem de `@Test` contra `build/test-results`.
Nos testes instrumentados, rodar uma classe por vez
(`-Pandroid.testInstrumentationRunnerArguments.class=app.accord.X`; lista com vírgula só executa a primeira).
A suíte de contrato do serviço (`:remote:test`) roda contra o fake sempre e contra o Supabase local
quando `supabase start` está ativo (`ACCORD_REQUIRE_SUPABASE=1` transforma a ausência em falha).

Na implementação, testar dinheiro/datas com exemplos independentes, autorização no serviço,
reenvio após resposta perdida, conflitos, importação/retomada e alertas coordenados. Validar UI
nos dois celulares. Build verde não comprova qualidade do modelo ou compatibilidade bancária.
Relatar precisamente o que foi executado e o que está pendente.

## Agentes e autoridade

Corpos compartilhados: `.claude/agents/`. Adaptadores Codex: `.codex/agents/`. Skills canônicas:
`.claude/skills/`; `.agents/skills/` contém entradas geradas que carregam a fonte, sem junction.
O [catálogo](.claude/agents-roster.md) organiza responsabilidades; `frontend-android` é o papel
para interface Android. Delegar só quando autorizado pelo usuário ou instruções ativas; catálogo
não dispara agentes automaticamente. Autorizações já dadas continuam válidas. Não enviar
mensagens externas, publicar, criar serviço remoto, fazer commit, push, pull ou sync remoto
sem autorização explícita.

<!-- BEGIN BEADS CODEX SETUP: generated by bd setup codex -->
## Beads Issue Tracker

Usar `bd` para todas as tarefas e `bd remember` para conhecimento persistente, sem listas de
tarefas Markdown ou `MEMORY.md`. Ler [beads](.agents/skills/beads/SKILL.md). `bd prime` recupera
fluxo/regras da sessão.

```powershell
bd prime
bd ready
bd show <id>
bd update <id> --claim
bd close <id>
```

Criar tarefa antes de mudar código/configuração e marcar em andamento. Ao encerrar, fechar
apenas concluídas, registrar pendências reais, validar proporcionalmente, conferir `git status`
e entregar mudanças/evidências. Perfil conservador/local: sem commit ou operações remotas sem
pedido explícito. Não há remote configurado nesta base.
<!-- END BEADS CODEX SETUP -->
