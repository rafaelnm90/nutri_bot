# CLAUDE.md

App Android pessoal do Rafael para contar calorias. Kotlin + Jetpack Compose,
um módulo só (`app/`), dados só no celular. Adapta as regras e as funções do
bot de nutrição dele (em Python). Mora na pasta `app_calorias/` do repositório
`rafaelnm90/nutri_bot`, ao lado do bot, mas é independente dele.

O `DECISOES.md` é o diário dos pedidos do Rafael: leia a área do que for mudar.

## Onde fica cada coisa (`app/src/main/java/com/rafaelnm90/calorias/`)

| Arquivo | O que tem |
|---|---|
| `Nutricao.kt` | Regras sem Android: `Alimento` e `Micros` (valores de 100 g), `Registro`, `Perfil`, `Fase`, `Calculos` (metas e faixas), `MetasNutrientes`, `CheckIn`, `Sugestao`, `Lembretes` (quando avisar), `Bebidas`, `Busca`, `LeitorIa` (respostas do Gemini) e `Taco` |
| `Banco.kt` | SQLite do celular: diário, meus alimentos, água, jejum, exercícios e peso. `versao` muda a cada gravação e as telas releem |
| `Ajustes.kt` | Perfil (com fase e início dela) e chave do Gemini nas preferências do celular |
| `Internet.kt` | `Gemini` (foto, descrição, correção, sugestão, rótulo, relatório e exercício, com cascata de modelos) e `OpenFoodFacts` (código de barras) |
| `Lembrete.kt` | Os lembretes: WorkManager a cada 30 min e as notificações |
| `Componentes.kt` | Formato pt-BR, `Cartao`, diálogos de espera e aviso, `CampoNumero`, foto reduzida, `TacoNoApp` |
| `MainActivity.kt` | Tema e troca de abas (Diário, Peso, Ajustes) e da tela de adicionar |
| `DiarioTela.kt`, `AdicionarTela.kt`, `PesoTela.kt`, `AjustesTela.kt` | As telas |

- `app/src/main/assets/taco.json`: a tabela TACO, gerada pelo workflow **App Calorias: TACO** (`ferramentas/gerar_taco.py`), que grava no branch `app-calorias-taco`; de lá ela entra por PR. Nunca escrever valores à mão.
- Chave fixa de assinatura: sem ela o APK novo não instala por cima e o Rafael perderia o diário. Nunca trocar depois da primeira instalação. Não fica no repositório (o `.gitignore` barra `*.jks`; o nutri_bot é público): está nos segredos do GitHub `ASSINATURA_JKS` (arquivo em base64) e `ASSINATURA_SENHA`, e na cópia de segurança do Drive dele. O workflow APK recria o `app/assinatura.jks` antes de montar.

## Testes e checagens

- A sessão na nuvem não baixa o Android SDK (a rede bloqueia `dl.google.com`). Quem compila é o GitHub Actions: o workflow **App Calorias: APK** roda os testes (`testReleaseUnitTest`) e monta o APK em todo PR que mexe em `app_calorias/`.
- Os workflows do app ficam na raiz do repositório (`.github/workflows/app_calorias_apk.yml` e `app_calorias_taco.yml`; o GitHub só lê os de lá) e só rodam quando `app_calorias/` muda. O `deploy.yml` do bot ignora a pasta do app.
- Na sessão, `ferramentas/regras_local/testar.sh` compila as regras (`Nutricao.kt`, `Internet.kt`) com o compilador Kotlin que vem no Gradle e roda o `NutricaoTeste`. As telas ficam de fora. O org.json e o `android.util.Base64` de lá são imitações só para esses testes.
- Regra de negócio nova fica em `Nutricao.kt` (sem Android) e ganha teste em `app/src/test`.
- Toda correção de bug ganha um teste que falha sem a correção.

## Como trabalhamos

- O app e os robôs da Shopee são projetos separados e independentes, sem ligação nenhuma. Nada do app passa pelo Telegram, pelos workflows (`avisar.yml` e outros) ou pelas rotinas dos robôs. Avisos e perguntas sobre o app ficam só na conversa do app.
- Responder em português simples.
- Antes de mudar como o app se comporta, perguntar pelo seletor de opções, com a recomendada primeiro. A opção fica aberta até o Rafael escolher. Se o seletor sumir sem resposta, mostrar a mesma pergunta de novo.
- Todo pedido do Rafael que define comportamento entra no `DECISOES.md`. Pedido novo que contradiz um antigo: riscar o antigo com "substituída em DD/MM/AAAA".
- Pedido concluído termina com um bloco "✅ Concluído": o que foi feito, o que muda no uso e onde achar no app.
- A rotina "Calorias: retomar trabalho pela metade" (7h52, 10h52, 13h52, 16h52 e 19h52) confere sozinha esta conversa e resolve o que ficou pela metade, sem o Rafael precisar lembrar.
- Um assunto por commit, em português, no formato `arquivo ou área: o que mudou`.
- PR, CI e merge: mudança pronta e validada → PR → workflow App Calorias: APK verde, montado com a chave fixa (rodou "Montar o APK", não "Montar o APK de teste") → merge. Sem os segredos `ASSINATURA_JKS` e `ASSINATURA_SENHA`, não fazer o merge: na main o workflow falha e não publica a versão. O merge publica a versão nova em Releases, de onde o Rafael baixa o APK no celular.
- Merge com "Create a merge commit", nunca squash nem rebase: o app entrou com `git subtree` e o histórico dele depende disso.
- Comentários em português explicam o porquê; sem histórico e sem emoji nos comentários.
- A chave do Gemini nunca entra no código nem no repositório: o Rafael digita no app.
