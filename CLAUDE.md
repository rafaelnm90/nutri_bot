# CLAUDE.md

App Android pessoal do Rafael para contar calorias. Kotlin + Jetpack Compose,
um módulo só (`app/`), dados só no celular. Adapta as regras e as funções do
bot de nutrição dele (`rafaelnm90/nutri_bot`, em Python), para onde o app vai
quando ele liberar.

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

- `app/src/main/assets/taco.json`: a tabela TACO, gerada pelo workflow TACO (`ferramentas/gerar_taco.py`). Nunca escrever valores à mão.
- Chave fixa de assinatura: sem ela o APK novo não instala por cima e o Rafael perderia o diário. Nunca trocar depois da primeira instalação. Não fica no repositório (o `.gitignore` barra `*.jks`; o nutri_bot é público): está nos segredos do GitHub `ASSINATURA_JKS` (arquivo em base64) e `ASSINATURA_SENHA`, e na cópia de segurança do Drive dele. O workflow APK recria o `app/assinatura.jks` antes de montar.

## Testes e checagens

- A sessão na nuvem não baixa o Android SDK (a rede bloqueia `dl.google.com`). Quem compila é o GitHub Actions: o workflow **APK** roda os testes (`testReleaseUnitTest`) e monta o APK em todo PR.
- Na sessão, `ferramentas/regras_local/testar.sh` compila as regras (`Nutricao.kt`, `Internet.kt`) com o compilador Kotlin que vem no Gradle e roda o `NutricaoTeste`. As telas ficam de fora. O org.json e o `android.util.Base64` de lá são imitações só para esses testes.
- Regra de negócio nova fica em `Nutricao.kt` (sem Android) e ganha teste em `app/src/test`.
- Toda correção de bug ganha um teste que falha sem a correção.

## Como trabalhamos

- Mesmas regras do projeto dos robôs da Shopee (`rafaelnm90/shopee`, CLAUDE.md, "Como trabalhamos"): responder em português simples, perguntar pelo seletor de opções antes de mudar comportamento, registrar no `DECISOES.md`, commits em português no formato `arquivo ou área: o que mudou`.
- PR, CI e merge: mudança pronta e validada → PR → workflow APK verde → merge. O merge publica a versão nova em Releases, de onde o Rafael baixa o APK no celular.
- Comentários em português explicam o porquê; sem histórico e sem emoji nos comentários.
- A chave do Gemini nunca entra no código nem no repositório: o Rafael digita no app.
