# Accord

Aplicativo Android de controle financeiro colaborativo, em português brasileiro. Até quatro pessoas,
cada uma com a própria conta, compartilham um espaço financeiro e registram gastos, rendas, cartões e
compromissos conversando com o app ou preenchendo um formulário.

Feito em **Kotlin e Jetpack Compose**, com banco e fila locais (**Room**) e **Supabase** (Auth e
PostgreSQL) para sincronizar os dados financeiros entre os aparelhos dos membros.

**Licença:** GNU GPL versão 3 (`GPL-3.0-only`). Copyright (c) 2026 Daniel Campos.
O Accord pode ser usado, modificado e redistribuído, inclusive comercialmente, conforme a
[licença completa](LICENSE.md). Componentes de terceiros mantêm suas próprias licenças e avisos.

> **Estado:** em desenvolvimento, com serviço hospedado em uso no piloto. Compatível com
> Android 10 ou superior; interface fixa em retrato. A cobertura de testes varia por recurso. Veja [Estado do projeto](#estado-do-projeto).

## Sumário

- [O que o Accord faz](#o-que-o-accord-faz)
- [Regras de negócio](#regras-de-negócio)
- [Estado do projeto](#estado-do-projeto)
- [Pré-requisitos](#pré-requisitos) · [Preparar o ambiente](#preparar-o-ambiente) ·
  [Supabase local](#configurar-login-e-sincronização-com-supabase-local) ·
  [Rodar o aplicativo](#rodar-o-aplicativo) · [Build de release](#build-de-release) ·
  [Testes](#rodar-os-testes) · [Problemas comuns](#problemas-comuns) ·
  [Estrutura e documentação](#estrutura-e-documentação)

## O que o Accord faz

A barra inferior tem cinco destinos: **Lançamentos**, **Orçamento**, **Chat** (ícone Astroid, sem
rótulo), **Revisar** e **Mais**. São 38 telas, descritas em
`docs/specs/design` (documentação local): 28 de prioridade P0, cinco P1 e cinco P2.

### Núcleo (P0)

| Área | O que existe |
| --- | --- |
| **Acesso e espaço** | Conta individual por e-mail e senha, espaço de até quatro membros com direitos iguais, convite multiuso por código ou QR Code e primeiros passos reduzidos para convidados. |
| **Chat** | Escreva "gastei 45 no mercado": o app entende a frase, mostra um recibo do que foi entendido com editar e desfazer, pergunta quando há dúvida e oferece registro manual. Também responde consultas por um catálogo fechado de perguntas. Joinha no recibo mede o acerto no uso real. |
| **Lançamentos** | Lista com filtros, detalhe, registro manual e edição. Ações ligadas ao lançamento: pagar, receber, estorno, cancelar parcelas e trocar a forma de pagamento. |
| **Orçamento e limites** | Orçamento mensal por categoria e subcategoria, detalhe da categoria no mês, limites com divisão opcional entre subcategorias. |
| **Categorias** | Criar, renomear, mover e remover categorias e subcategorias; categorias próprias para rendas; ícones de categoria e de marcas de estabelecimentos. |
| **Compromissos** | Boletos, parcelas e recorrências futuras, com detalhe e conferência da fatura de cada ocorrência no cartão. |
| **Cartões e faturas** | Vários cartões com fechamento, vencimento, limite e conta vinculada; cartão padrão individual, faturas por vencimento e quitação integral conferida. |
| **Contas e cofrinhos** | Saldos com data de corte, transferências entre contas e cofrinhos vinculados, com guardar/resgatar, alvo opcional e histórico compartilhado. Reservas não duplicam saldo nem alteram o orçamento. |
| **Rendas** | Rendas previstas e recebidas, com categoria de renda opcional. |
| **Importação** | OFX, PDF bancário com texto selecionável e o CSV no modelo próprio do Accord, tudo lido no aparelho. Fluxo: escolher arquivo → revisar a prévia → resultado → pendências. Reimportar não duplica. |
| **Previsões e alertas** | Previsões por categoria, detalhe da previsão e preferências de alertas (no máximo um evento por categoria principal por semana, destinado a todos os membros). A coordenação entre aparelhos permanece pendente. |
| **Mais** | Contas, cofrinhos, categorias, limites, sincronização, alertas, tema e segurança. Conversas locais podem ser criadas, renomeadas, excluídas, exportadas e importadas. |
| **Proteção e dados** | Bloqueio por biometria ou credencial do Android; exportação pessoal e financeira; exclusão recuperável da conta ou do espaço, com autorização no serviço. |
| **Sincronização** | Fila persistida, envio em segundo plano, indicadores de estado e tela para **escolher a versão** em conflito. |

### Análise e planejamento (P1 e P2)

- **P1:** revisão semanal, atividade de compras, assinaturas (com detalhe) e comparações históricas.
- **P2:** intenções de compra compartilhadas e simulador de compra, que não altera o orçamento
  real, e pistas conservadoras de lançamentos parecidos e mudanças de frequência. As pistas
  estão implementadas; a calibração com uso real permanece pendente. P2 fica oculta no release.

### Fora do escopo inicial

Open Finance, mensageria externa, investimentos, múltiplas famílias, reembolsos com terceiros,
divisão obrigatória por item, IA remota automática e ajuste fino. Importação de CSV de banco e
OCR também ficam de fora.

### Experiência

Texto em português claro e sem julgamento, temas claro e escuro, TalkBack, texto ampliado,
movimento reduzido e teclado que não esconde as ações. Corpo 16 sp, alvos de toque de 48 dp,
contraste mínimo de 4,5:1. Ícones Lucide, logos de bancos e marcas empacotados no app, sem CDN.

## Regras de negócio

O resumo abaixo vem do [AGENTS.md](AGENTS.md) e do plano local (`docs/plano-desenvolvimento.md`); em
caso de divergência, vale o plano e os [ADRs](docs/adr/README.md).

**Acesso**
- Até quatro membros criam e editam os dados e ajustes do espaço: sem papéis administrativos, gastos privados ou cotas
  individuais. Convite e autorização do espaço são validados no serviço e no banco.
- O piloto não envia e-mails: sem confirmação, recuperação de senha, OTP ou link mágico. O e-mail
  identifica o login, sem comprovar posse; ajuda é atendimento manual fora do app.

**IA no Chat**
- O interpretador **propõe** uma ação estruturada; o app valida campos, referências e ambiguidade
  e **calcula** os valores. Sem SQL livre do modelo, sem confiar na autoavaliação dele e sem IA
  externa automática.
- No P0 o interpretador é **local, por regras** ([ADR 0036](docs/adr/0036-interpretador-por-regras-no-p0-e-slm-opcional.md)).
  O SLM no aparelho é só candidato opcional adiado, sem modelo nem motor escolhidos.
- Ação clara e válida é salva com editar e desfazer; dúvidas ficam recuperáveis.

**Dinheiro**
- Valores em centavos ou decimal exato. Categoria e subcategoria, compra e parcelas, e quitação da
  fatura não duplicam despesas.
- Renda prevista não é saldo confirmado; o que foi pago não se soma ao comprometido.
- Intenções e simulações não mudam o orçamento real.

**Datas**
- Cartão e parcelas usam o vencimento da fatura atribuída; Pix usa a data da movimentação.
- Boleto não pago é previsto pelo vencimento; pago ocupa só o mês do pagamento, e ambas as datas
  ficam guardadas.
- Fechamento 8 e vencimento 15 são configuração inicial, sem garantir atribuição. Dúvida só sobre
  a fatura permite salvar na estimada como "a conferir".

**Créditos e pagamentos**
- Estorno confirmado abate a categoria original no mês do crédito, preserva a compra e não vira renda.
- Parcelas futuras canceladas ficam até confirmar a não cobrança, sem crédito fictício.
- O P0 aceita só quitação **integral** conferida da fatura; parcial ou divergente fica em revisão
  com o valor real preservado.
- Encargos confirmados vão para Serviços › Tarifas e juros; o app não inventa juros bancários.

**Limites**
- A soma distribuída entre subcategorias não passa do limite da principal; excesso não redistribui
  nem aumenta limites. Limite ausente é diferente de limite zero.
- Cada edição escolhe valer só no mês ou nesse mês e nos seguintes, preservando os anteriores.

**Alcance de edição**
- Categoria e descrição de compra parcelada mudam em todas as parcelas. Recorrência escolhe só
  esta ocorrência ou esta e as próximas. Identidades, pagamentos e originais importados são preservados.

**Sincronização**
- IDs estáveis, reenvio idempotente e versão validada no serviço. Em conflito, o app mostra as duas
  versões e a pessoa escolhe: nada de "última edição vence" nem mescla automática.
- "Salvo localmente" não é o mesmo que "reconhecido pelo serviço".

**Privacidade**
- O Chat existe só no aparelho de origem; mensagens, transcrições e arquivos de importação completos
  não são sincronizados. Dados financeiros são compartilhados entre os membros do espaço.
- A coleta opcional do conteúdo do Chat foi aprovada como decisão, mas Termos e implementação
  continuam pendentes; o app atual mantém as conversas localmente.
- Senhas, tokens, chaves, conversas e conteúdo bancário não entram em logs.

**Importação**
- Processada localmente, com prévia e revisão antes de efetivar. Guarda os dados extraídos, campos
  originais, metadados e decisões, **sem cópia do arquivo**; o arquivo externo fica intacto.
- Valor e data iguais não fundem compras legítimas; FITID divergente exige revisão. Moeda não
  suportada bloqueia a efetivação, sem conversão automática.

## Estado do projeto

| Frente | Situação |
| --- | --- |
| Motor financeiro, persistência e sincronização (S1, S2) | Implementados e testados em JVM, Supabase local e emulador; fumaça de sincronização física no piloto. Roteiro completo de recuperação, conflitos e acessibilidade permanece pendente. |
| Serviço hospedado | Em 09/11/2026: 15 migrações alinhadas, protocolo 3 e endpoint de privacidade publicados, com verificações HTTP. Clientes do piloto atualizados. Evidências em [supabase/README.md](supabase/README.md). |
| Chat | Interpretador por regras em uso, com validador, histórico local e recibo. Nenhum modelo de linguagem integrado. |
| Importação | OFX e CSV verificados com exemplos do usuário. PDF só com layout de fatura de cartão Itaú, validado com um exemplo real. Sem compatibilidade bancária geral comprovada. |
| Alertas | Preferências e janela semanal implementadas; a coordenação entre aparelhos ainda precisa de implementação. |
| Espaço e cartões | Até quatro membros, convite multiuso/QR e vários cartões implementados; migrações aplicadas no hospedado. |
| Proteção, dados, conversas e cofrinhos (S8) | Implementados, com testes JVM/SQL/AVD e serviço hospedado atualizado. Validação física completa e TalkBack permanecem em andamento. |
| P1 e P2 | Análises P1 e recursos P2 implementados, incluindo pistas (37–38); P2 oculta no release. Calibração e aceite físico permanecem pendentes. |
| Termos e coleta do Chat | Decisão aprovada; revisão jurídica e implementação pendentes. |

Estado detalhado e decisões em [ARCHITECTURE.md](ARCHITECTURE.md) e nos [ADRs](docs/adr/README.md).

## Pré-requisitos

- Android Studio com SDK Android **37** instalado pelo SDK Manager.
- **JDK 21**, disponível no JBR do Android Studio usado neste projeto. O Java 8 do PATH desta
  máquina não atende ao Gradle.
- Android SDK Platform-Tools (`adb`) e, para emulação, Android Emulator e um AVD.
- Emulador ou aparelho com **Android 10 / API 29 ou superior** para executar o app.
- Para login e sincronização locais: Supabase CLI e Docker em execução.

O wrapper incluído usa Gradle **9.8.2**; não é necessário instalar Gradle separadamente.
A primeira compilação precisa de internet para baixar o Gradle e as dependências.
As bancadas locais `:poc` e `:pocpc` são opcionais e só entram no build quando seus arquivos
estão presentes; um clone do produto não precisa desses acervos para compilar o aplicativo.

## Preparar o ambiente

Os comandos abaixo usam PowerShell, a partir da raiz do repositório. Ajuste os caminhos à sua
instalação:

```powershell
Set-Location E:\Projects\accord
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

& "$env:JAVA_HOME\bin\java.exe" -version
.\gradlew.bat --version
```

Ao abrir a pasta no Android Studio, selecione esse mesmo JDK como **Gradle JDK**, instale a
plataforma SDK 37 e sincronize o projeto. O arquivo `local.properties` é local e ignorado pelo
Git. Se precisar informar o SDK nele, use caminhos com barras normais:

```properties
sdk.dir=C:/Users/SEU_USUARIO/AppData/Local/Android/Sdk
```

## Configurar login e sincronização com Supabase local

É possível compilar e instalar o APK sem configurar o serviço. Porém, um primeiro acesso com
criação de conta e espaço precisa do Supabase: o app não usa uma conta de demonstração
automaticamente quando a configuração está ausente.

1. Inicie o Docker.
2. Na raiz do repositório, inicie o serviço local:

   ```powershell
   supabase start
   supabase status -o json
   ```

3. Copie a chave pública local (`PUBLISHABLE_KEY`, ou `ANON_KEY` se essa for a chave exibida)
   para `local.properties`, preservando a linha `sdk.dir`, se existir:

   ```properties
   accord.supabase.url=http://10.0.2.2:55321
   accord.supabase.publishableKey=COLE_A_CHAVE_PUBLICA_LOCAL
   ```

   `10.0.2.2` permite ao emulador Android acessar a máquina que executa o Docker. Para um
   aparelho físico conectado por USB, use `http://localhost:55321` e, antes de abrir o app,
   execute:

   ```powershell
   & "$env:ANDROID_HOME\platform-tools\adb.exe" -s SERIAL_DO_APARELHO reverse tcp:55321 tcp:55321
   ```

   O build de debug permite HTTP para esses endereços locais. A chave administrativa
   (`service_role`/`SECRET_KEY`) nunca deve entrar na configuração do aplicativo.

4. Compile e instale o app conforme a próxima seção. Recompile e reinstale sempre que mudar
   a URL ou a chave: esses valores são incorporados ao APK.

As configurações também podem vir de propriedades Gradle ou das variáveis
`ACCORD_SUPABASE_URL` e `ACCORD_SUPABASE_PUBLISHABLE_KEY`. A prioridade é:
`local.properties` → propriedades Gradle → variáveis de ambiente.

O Supabase local usa a API na porta **55321** e PostgreSQL na **55322**. A configuração do
repositório desativa confirmação por e-mail; não há etapa de SMTP para rodar o piloto local.
Esse procedimento não cria um projeto hospedado. Detalhes do serviço e das migrações estão em
[supabase/README.md](supabase/README.md).

## Rodar o aplicativo

Abra um emulador no **Device Manager** do Android Studio ou conecte o celular por USB com
depuração USB habilitada e autorize o computador no aparelho.

Na máquina atual, o AVD preferido é `Accord_Rapido_API_33`. Em outra instalação, crie um AVD
com API 29 ou superior. Liste os dispositivos conectados:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
```

Para compilar, instalar e abrir:

```powershell
.\scripts\run-debug.ps1
```

Se houver mais de um dispositivo, informe o serial mostrado pelo `adb devices`:

```powershell
.\scripts\run-debug.ps1 -Serial emulator-5554
```

Também é possível selecionar o módulo `app` e o dispositivo no Android Studio e clicar em
**Run**. No primeiro acesso, crie uma conta por e-mail e senha, crie o espaço e siga os
primeiros passos. Os convidados criam suas próprias contas e entram pelo código ou QR Code,
até o limite de quatro membros. O código vale por três dias e pode ser invalidado.
Para registrar despesas, use o Chat ou o registro manual.

Outras opções do script:

```powershell
.\scripts\run-debug.ps1 -Serial emulator-5554 -Shot tela.png
.\scripts\run-debug.ps1 -Serial emulator-5554 -Dump
```

A opção `-Fresh` limpa todos os dados locais do app no dispositivo selecionado, inclusive
banco e fila. Use-a apenas quando quiser reiniciar o ambiente de desenvolvimento.

### Gerar somente o APK

Não exige emulador ou aparelho conectado:

```powershell
.\gradlew.bat :app:assembleDebug
```

Saída: `app/build/outputs/apk/debug/app-debug.apk`.

## Build de release

Para preparar o APK com versão, conferir a assinatura e comparar com o APK anterior, use o
[script de release](scripts/release.ps1):

```powershell
.\scripts\release.ps1 -Tag v0.1.0 -PreviousApk CAMINHO_DO_APK_ANTERIOR.apk -RequireClean
```

O guia detalhado fica em `docs/distribuicao.md`, mantido apenas localmente. A decisão do canal
está no [ADR 0061](docs/adr/0061-distribuicao-por-github-releases-e-obtainium.md).
O Accord verifica novas versões pela API pública do GitHub ao abrir, no máximo uma vez por dia,
e oferece **Mais > Verificar atualizações**. Pré-releases estão incluídas neste piloto.
Uma versão nova permite abrir o download de `accord.apk` no navegador; confirme a atualização
no Android, preservando a instalação existente. **Não desinstale**: isso apaga dados locais.
Sem rede, continue usando o aplicativo e tente mais tarde. A consulta não envia dados financeiros
nem exige token. Obtainium foi retirado por decisão do usuário
([ADR 0066](docs/adr/0066-verificacao-de-atualizacoes-pela-api-do-github.md)).
A [pré-release v0.1.0](https://github.com/chinadcs/accord/releases/tag/v0.1.0) está publicada,
com [APK assinado](https://github.com/chinadcs/accord/releases/download/v0.1.0/accord.apk). Cada
Release tem só `accord.apk` como asset; o código-fonte da versão são os arquivos **Source code**
(`.zip` e `.tar.gz`) que o GitHub gera para a tag, e os avisos de licença acompanham o APK. As notas
contam o que mudou para quem usa o aplicativo e as limitações conhecidas do piloto; commit e SHA-256
não vão no texto. A validação de atualização entre duas versões em aparelho físico permanece pendente.

Prepare a distribuição num checkout limpo do commit escolhido. O script confere versão,
assinatura, ausência de CAMERA/modo depurável e presença dos avisos de licença no APK.
Os avisos e o inventário das bibliotecas ficam em `app/src/main/assets/licenses/` e acompanham
o aplicativo. Atualizações de dependências exigem revisar esse inventário e as fontes correspondentes.

O pacote de fontes completo, com os arquivos de fonte das dependências e os complementos nativos
fixados no inventário, **não é mais anexado às Releases** (decisão do usuário em 09/11/2026). Quem
quiser gerá-lo localmente para o mesmo commit pode usar:

```powershell
python scripts/release-sources.py --commit SHA_COMPLETO_DO_COMMIT --output app/build/releases/v0.1.0/accord-v0.1.0-sources.tar.gz
```

Esse comando baixa fontes públicas e verifica seus SHA-256. Ele usa somente arquivos do
commit, sem incluir `.git`, configurações locais, dados do usuário ou a chave de assinatura.
O pacote inclui instruções de build; ferramentas e SDKs continuam sendo obtidos separadamente.

O release aponta para o **Supabase hospedado** e é assinado com keystore própria. Ele lê as chaves
abaixo de `local.properties` ou das variáveis `ACCORD_RELEASE_*` equivalentes (nunca das chaves do
debug, que apontam para o serviço local). Sem todas elas o build falha, e a URL precisa ser HTTPS:

```properties
accord.release.supabase.url=https://SEU_PROJETO.supabase.co
accord.release.supabase.publishableKey=CHAVE_PUBLICA
accord.release.storeFile=CAMINHO/DA/KEYSTORE.jks
accord.release.storePassword=...
accord.release.keyAlias=...
accord.release.keyPassword=...
```

```powershell
.\gradlew.bat :app:assembleRelease                    # gera o APK assinado
$env:ANDROID_SERIAL = "SERIAL_DO_APARELHO"
.\gradlew.bat :app:installRelease                     # instala no aparelho
```

Debug e release têm assinaturas diferentes: instalar um por cima do outro falha com
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Para atualizar um celular que já tem o release, use
`installRelease`; desinstalar apaga os dados locais. Keystores são ignoradas pelo Git, e a chave
administrativa do Supabase nunca entra no app.

## Rodar os testes

### Testes JVM

Sem aparelho conectado:

```powershell
.\gradlew.bat :core:test :persistence:test :remote:test
.\gradlew.bat :app:testDebugUnitTest
```

A suíte de contrato em `:remote:test` executa contra o serviço fake. Os testes que precisam
do Supabase local são ignorados quando ele não está disponível. Para exigir sua execução,
inicie o serviço e rode:

```powershell
$env:ACCORD_REQUIRE_SUPABASE = "1"
.\gradlew.bat :remote:test --rerun-tasks
Remove-Item Env:ACCORD_REQUIRE_SUPABASE
```

O `--rerun-tasks` força nova execução mesmo se houver resultados anteriores em cache. A suíte
descobre as credenciais locais pelo Supabase CLI. Mantenha a configuração do emulador em
`local.properties`, para que a suíte JVM use o endereço do serviço na máquina.

### Testes do banco Supabase

Com Docker e Supabase local ativos, a partir da raiz:

```powershell
supabase test db
```

Caso precise recriar o banco local e reaplicar as migrações, `supabase db reset` apaga os dados
locais desse serviço antes de recriá-lo.

### Testes instrumentados Android

Exigem emulador ou aparelho conectado. Execute uma classe por vez; exemplo:

```powershell
.\scripts\run-debug.ps1 -Serial emulator-5554 -TestClass app.accord.AccessUiTest
```

Equivalente pelo Gradle:

```powershell
$env:ANDROID_SERIAL = "emulator-5554"
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=app.accord.AccessUiTest"
```

Não passe uma lista de classes separadas por vírgula: neste projeto só a primeira é executada.
Resultados JVM ficam em `<modulo>/build/reports/tests/`; os instrumentados, em
`app/build/reports/androidTests/connected/`.

## Problemas comuns

| Sintoma | Como resolver |
| --- | --- |
| Gradle tenta usar Java 8 | Configure `JAVA_HOME` no terminal e o Gradle JDK no Android Studio para o JBR 21. |
| SDK não encontrado ou plataforma 37 ausente | Confira `ANDROID_HOME`/`sdk.dir` e instale a plataforma 37 no SDK Manager. |
| Nenhum dispositivo ou dispositivo `unauthorized` | Abra o AVD ou habilite depuração USB e aceite a autorização no celular. |
| Mais de um dispositivo conectado | Informe `-Serial` no script ou `ANDROID_SERIAL` ao executar o Gradle. |
| Serviço não configurado ou falha no primeiro login | Confira URL/chave pública, execute `supabase start` e recompile/reinstale o APK. |
| Emulador não acessa o serviço local | Use `http://10.0.2.2:55321`; `localhost` no emulador aponta para ele próprio. |
| Celular USB não acessa o serviço local | Use `http://localhost:55321` e refaça o `adb reverse` para o serial correto. |

Para parar o serviço local ao terminar:

```powershell
supabase stop
```

## Estrutura e documentação

| Caminho | Conteúdo |
| --- | --- |
| `app/` | Aplicativo Android e telas Compose. |
| `core/` | Motor financeiro e contratos Kotlin JVM. |
| `persistence/` | Room, fachada local e fila de sincronização. |
| `remote/` | Serviço fake e adaptador HTTP do Supabase. |
| `supabase/` | Configuração local, migrações e testes pgTAP. |
| `docs/` | ADRs e modelos de arquivos versionados; os demais documentos ficam apenas locais. |

- [Arquitetura e estado de implementação](ARCHITECTURE.md)
- [Decisões de arquitetura (ADRs)](docs/adr/README.md)
- [Modelo CSV de importação](docs/modelos/accord-importacao-exemplo.csv)
- [Exemplos fictícios de frases para testes](docs/modelos/frases-chat-exemplo.tsv)
- [Instruções para trabalhar no repositório](AGENTS.md)

O plano (`docs/plano-desenvolvimento.md`), as specs (`docs/specs/`) e o restante da documentação
de trabalho são mantidos no disco, fora do versionamento. Referências a esse acervo nos ADRs e
nas instruções locais preservam suas fontes, mas não estarão disponíveis em um clone público.

Build e testes locais não substituem a validação em aparelhos físicos, incluindo TalkBack,
texto ampliado, retomada e sincronização entre membros. Os modelos usados no piloto são
referências de teste, não uma restrição de compatibilidade do produto.
