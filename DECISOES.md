# DECISOES.md

Diário dos pedidos do Rafael sobre o app. Pedido novo que contradiz um antigo:
riscar o antigo com "substituída em DD/MM/AAAA" e registrar o novo abaixo.

## Origem e formato

- 11/10/2026: o Rafael quer um app como o Yazio (contador de calorias) só para ele. Escrito do zero, sem copiar código, imagens nem a base de alimentos do Yazio.
- 11/10/2026: formato: app Android de verdade (APK).
- ~~11/10/2026: código num repositório novo e privado, separado dos robôs da Shopee.~~ (substituída em 11/10/2026)
- 11/10/2026: o app vai, mais para frente, para o repositório `rafaelnm90/nutri_bot` (o do bot de nutrição dele). Até ele liberar, ninguém mexe no nutri_bot e o código espera fora do GitHub. O nutri_bot é público: a chave de assinatura e a senha não vão para o código; ficam nos segredos `ASSINATURA_JKS` e `ASSINATURA_SENHA` do GitHub (o Rafael cadastra ao levar o app) e na cópia do Drive.
- 11/10/2026: o APK novo é baixado do GitHub (Releases), não mandado pelo Telegram.
- 11/10/2026: até o código ir para o nutri_bot, uma cópia de segurança fica no Google Drive dele (pasta "App Calorias (cópia de segurança)"), um arquivo por arquivo do projeto.
- 11/10/2026: o app adapta o que o nutri_bot já fazia (pedido do Rafael: "pegar alguns detalhes do original e adaptar"). Tudo o que ele marcou entrou; as seções abaixo dizem como.

## Funções essenciais (11/10/2026)

- Foto do prato pela IA.
- Código de barras.
- Água e jejum.
- Peso e objetivo.

## IA

- 11/10/2026: o app usa uma chave do Gemini nova, só dele, que o Rafael cola em Ajustes. Fica só no celular; se vazar, apaga-se só ela e os robôs não param.
- O que a IA estima (foto ou descrição) passa por uma revisão antes de entrar no diário: a pessoa desmarca itens e corrige os gramas.

## Padrões iniciais (escolhidos pelo Claude, a confirmar com o Rafael)

- Meta de calorias pela fórmula de Mifflin-St Jeor com o fator de atividade; ~~perder ou ganhar 0,5 kg por semana = 550 kcal a menos ou a mais por dia~~ (substituída em 11/10/2026 pelas fases); nunca abaixo de 1.500 kcal (homem) ou 1.200 kcal (mulher). Meta digitada à mão vale no lugar da calculada.
- ~~Macros: 50% carboidrato, 20% proteína, 30% gordura.~~ (substituída em 11/10/2026)
- ~~Água: 2.000 ml por dia~~ (substituída em 11/10/2026), copo de 250 ml. Jejum: 16 h. Tudo muda em Ajustes.

## Regras do nutri_bot (escolha de 11/10/2026: "do nutri_bot + Mifflin")

- Gasto do dia pela fórmula de Mifflin-St Jeor (o nutri_bot usa Harris-Benedict; o Rafael preferiu a Mifflin), vezes o fator de atividade (1,2 / 1,375 / 1,55 / 1,725, iguais aos do bot).
- Macros: 30% proteína, 40% carboidrato, 30% gordura, cada um com faixa (proteína ±15 g, carboidrato ±20 g, gordura ±10 g).
- ~~Calorias como faixa: meta ±100 kcal, nunca abaixo de 1.200 (meta à mão abaixo de 1.100: a faixa vira o próprio piso); ✅ dentro, ❌ fora, também no dia em curso, como no bot.~~ (substituída em 11/10/2026) O mínimo da meta continua 1.500 (homem) e 1.200 (mulher).
- 11/10/2026: calorias como faixa: meta ±100 kcal, nunca abaixo de 1.200. Quando o piso corta a faixa, ela fica com os 200 kcal de largura a partir dele (resposta do Rafael para meta à mão abaixo de 1.100: "1.200 a 1.400"; vale até a meta 1.300, onde as duas contas se encontram).
- 11/10/2026: o sinal das calorias no resumo: ✅ dentro da faixa, ❌ acima. Abaixo dela, ⏳ no dia de hoje (ainda dá tempo) e ❌ nos dias que já passaram (resposta do Rafael: "⏳ hoje, ❌ nos outros").
- 11/10/2026: na tela de adicionar comida, quando o dia não é hoje, o dia aparece embaixo do título ("Ontem", "Sex, 09/10"), para não lançar no dia errado sem perceber. A comida vai para o dia em que a pessoa tocou em ＋, mesmo que a meia-noite passe no meio.
- Água: 35 ml por kg (meta à mão opcional). Bebida alcoólica ou refrigerante (mesmo zero) soma o mesmo volume na meta do dia.
- 25/10/2026: prato com bebida no nome não soma na água quando a bebida vem depois de "ao", "na", "no" ("Frango ao vinho", "Costela na cerveja"). Depois de "com", a bebida conta como drink e soma ("Limonada com vodka", "Red Bull com vodka"), mesmo que raramente seja prato ("Strogonoff com conhaque"). Resposta do Rafael: "com" é drink.
- 25/10/2026: a refeição Lanches usa o plural nos textos: "Adicionar aos Lanches", "O que você comeu nos lanches?" (resposta do Rafael).
- Fases (no lugar do ritmo em kg por semana): Fase 1 Adaptação ±200 kcal, Fase 2 Ideal ±400 (a recomendada), Fase 3 Drástica ±600 (aviso: não usar por mais de 2 semanas). Quem está começando, Fase 1; quem tem experiência, Fase 2.
- Check-in semanal: 7 dias depois do começo da fase, o Diário pergunta fome e energia (muita fome/fraqueza, moderada/OK, sem fome/energia alta) e sugere recuar, manter ou avançar, com os textos do bot. A contagem recomeça quando ele responde ou muda a fase ou o objetivo.
- Micronutrientes com as metas do bot por idade e sexo: fibras mín. 25 g, açúcar máx. 50 g, sódio máx. 2.000 mg, cálcio, ferro, zinco, potássio, vitamina C e vitamina A (faixa até 3.000 mcg).

## IA (escolha de 11/10/2026: as quatro funções)

- Foto: os três cenários do bot (prato, tabela da embalagem lida ao pé da letra, não é comida), com o prato e os talheres como régua para os gramas.
- Corrigir item conversando, na revisão ("não foi pão de queijo, foi pão de leite"), e "esqueci um item".
- Sugerir refeição: 3 opções que cabem no que falta, com a fatia do horário do bot (café 25%, almoço 35%, lanche 15%, jantar 25%; depois das 19 h, com até 600 kcal faltando, fecha o dia).
- Ler rótulo no estilo Desrotulando (ingredientes ✅/❌, selo, marcas mais saudáveis, mercado em Minas Gerais).
- Relatório do dia pela IA, por botão no Diário.

## Acompanhamento (escolha de 11/10/2026: as quatro funções)

- Exercícios: o treino descrito em texto; a IA estima as calorias pelo peso da pessoa. Aparecem como "Gastas" no resumo, sem mudar a meta. Resposta do Rafael em 11/10/2026: "só mostrar" (não somar na meta, como no Yazio; a IA costuma superestimar o gasto).
- Lembretes no celular, das 8h às 22h, com as regras do bot: água atrasada 600 ml ou mais (de novo em 2,5 h se passa de 1 L, senão 4 h); comida atrasada mais de 35% do dia (de novo em 3 h se passa de 50%, senão 5 h). Diferença do bot: nenhum lembrete de comida durante o jejum. Também avisam o check-in semanal e, às 21 h, o relatório do dia.
