# Accord

Aplicativo Android de controle financeiro do casal, em português brasileiro, desenvolvido com
Kotlin e Jetpack Compose. Usa Room para banco e fila locais e Supabase para autenticação e
sincronização dos dados financeiros.

O projeto está em desenvolvimento. Há implementação do motor financeiro, persistência, telas
de registro manual e sincronização. O Chat ainda é provisório; SLM local, importação bancária
e alertas coordenados continuam pendentes. Consulte o estado detalhado em
[ARCHITECTURE.md](ARCHITECTURE.md).

## Pré-requisitos

- Android Studio com SDK Android **37** instalado pelo SDK Manager.
- **JDK 21**, disponível no JBR do Android Studio usado neste projeto. O Java 8 do PATH desta
  máquina não atende ao Gradle.
- Android SDK Platform-Tools (`adb`) e, para emulação, Android Emulator e um AVD.
- Emulador ou aparelho com **Android 10 / API 29 ou superior** para executar o app.
- Para login e sincronização locais: Supabase CLI e Docker em execução.

O wrapper incluído usa Gradle **9.8.2**; não é necessário instalar Gradle separadamente.
A primeira compilação precisa de internet para baixar o Gradle e as dependências.

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
primeiros passos. A segunda pessoa cria sua própria conta e entra usando o código de convite.
Para registrar despesas enquanto o Chat estiver provisório, use o registro manual.

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
| `docs/` | Plano, specs e decisões de arquitetura. |

- [Arquitetura e estado de implementação](ARCHITECTURE.md)
- [Plano de desenvolvimento](docs/plano-desenvolvimento.md)
- [Especificações das telas](docs/specs/design/README.md)
- [Decisões de arquitetura (ADRs)](docs/adr/README.md)
- [Instruções para trabalhar no repositório](AGENTS.md)

Build e testes locais não substituem a validação nos Galaxy S20 FE e S23 FE, ainda pendente.
