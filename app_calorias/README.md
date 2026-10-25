# Calorias

App Android pessoal de contagem de calorias, escrito do zero.

- **Diário**: calorias do dia dentro de uma faixa, proteína, carboidrato e
  gordura com faixa, micronutrientes, as quatro refeições, exercícios, água
  (35 ml/kg, mais o volume de álcool e refrigerante) e jejum; check-in semanal
  das fases; sugerir refeição, ler rótulo e relatório do dia pela IA.
- **Adicionar alimento**: busca na tabela TACO e nos alimentos já usados, foto
  do prato (o Gemini reconhece e estima, e dá para corrigir conversando),
  código de barras (Open Food Facts) e descrição em texto.
- **Peso**: registro do peso, gráfico e meta.
- **Ajustes**: perfil e fase da dieta (calculam a meta), metas de água e jejum,
  lembretes e a chave do Gemini.
- **Lembretes**: água e comida atrasadas (8h às 22h), check-in e relatório.

As regras e as funções de IA vêm do bot de nutrição do Rafael. O app mora na
pasta `app_calorias/` do repositório do bot, mas é independente dele.

Os dados ficam só no celular (banco SQLite do app).

## Como instalar uma versão nova

Cada merge na `main` que muda o app publica uma versão em **Releases**
(`calorias-v1.N`). No celular, abra a
versão mais nova, baixe o arquivo `Calorias-1.N.apk` e toque nele. Na primeira
vez, o Android pede para permitir instalar apps desta fonte (navegador ou
GitHub). As versões seguintes instalam por cima, sem perder o diário.

## Desenvolvimento

- Kotlin + Jetpack Compose, um só módulo (`app/`).
- `./gradlew testReleaseUnitTest` roda os testes de `app/src/test`.
- `./gradlew assembleRelease` monta o APK, assinado com a chave fixa (para as
  atualizações instalarem por cima). A chave e a senha ficam nos segredos do
  GitHub `ASSINATURA_JKS` (o arquivo em base64) e `ASSINATURA_SENHA`; o
  workflow APK recria o `app/assinatura.jks`. Para montar à mão, ponha o
  arquivo em `app/` e a senha na variável `ASSINATURA_SENHA`.
- O workflow **App Calorias: APK** testa e monta em todo PR que mexe no app e
  publica a versão no merge.
- O workflow **App Calorias: TACO** baixa a tabela oficial e grava o
  `taco.json` no branch `app-calorias-taco`.

Fontes dos dados: Tabela Brasileira de Composição de Alimentos (TACO, 4ª
edição, NEPA/UNICAMP) e Open Food Facts (licença ODbL).
