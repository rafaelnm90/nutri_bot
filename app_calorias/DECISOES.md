# DECISOES.md

Diário dos pedidos do Rafael sobre o app. Pedido novo que contradiz um antigo:
riscar o antigo com "substituída em DD/MM/AAAA" e registrar o novo abaixo.

## Origem e formato

- 11/10/2026: o Rafael quer um app como o Yazio (contador de calorias) só para ele. Escrito do zero, sem copiar código, imagens nem a base de alimentos do Yazio.
- 11/10/2026: formato: app Android de verdade (APK).
- ~~11/10/2026: código num repositório novo e privado, separado dos robôs da Shopee.~~ (substituída em 11/10/2026)
- ~~11/10/2026: o app vai, mais para frente, para o repositório `rafaelnm90/nutri_bot` (o do bot de nutrição dele). Até ele liberar, ninguém mexe no nutri_bot e o código espera fora do GitHub.~~ (substituída em 25/10/2026) O nutri_bot é público: a chave de assinatura e a senha não vão para o código; ficam nos segredos `ASSINATURA_JKS` e `ASSINATURA_SENHA` do GitHub (o Rafael cadastra ao levar o app) e na cópia do Drive.
- 25/10/2026: o Rafael liberou o nutri_bot ("Pode levar agora"). O app mora na pasta `app_calorias/`, com o histórico, e os workflows dele (App Calorias: APK e App Calorias: TACO) só rodam quando essa pasta muda. O código do bot não muda; o `deploy.yml` do bot ganhou um filtro para que mudança só no app não reinicie o bot no servidor. O filtro também ignora o próprio `deploy.yml` (assim o merge do app não reiniciou o bot); por isso ele ganhou o botão de rodar à mão (Actions → Deploy NutriBot → Run workflow), para quando só ele mudar.
- 11/10/2026: o APK novo é baixado do GitHub (Releases), não mandado pelo Telegram.
- 11/10/2026: até o código ir para o nutri_bot, uma cópia de segurança fica no Google Drive dele (pasta "App Calorias (cópia de segurança)"), um arquivo por arquivo do projeto.
- 25/10/2026: o app e os robôs da Shopee são projetos separados e independentes. Nada do app vai para o Telegram dos robôs (ele é só da Shopee). Avisos e conferências do app ficam na conversa do app, e uma rotina confere sozinha o que ficou pela metade (pedido do Rafael: "sempre avisa aqui, e sempre tenta resolver automaticamente").
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

## Jornada de hábitos (03/11/2026 e 04/11/2026)

- 03/11/2026: pedido do Rafael: "o usuário é questionado onde quer chegar, o que quer eliminar de hábitos e o que quer reduzir; você também pode tomar a frente do que deve ser eliminado, uma vez que existem hábitos que não podem ser mantidos de forma alguma."
- 03/11/2026: calorias como hoje (resposta do Rafael: "Contagem como hoje"): a jornada fica à parte, ligada em Ajustes. Calorias, fases da dieta, check-in e lembretes não mudam, com déficit desde o primeiro dia. Desligada, o app fica igual ao de antes da jornada.
- 03/11/2026: o app toma a frente, com cadeado, em três hábitos: bebida com açúcar, embutidos e álcool. Meta zero; um deslize não apaga nada; o que ele já não faz começa como mantido; o resto ele escolhe (eliminar, reduzir com teto ou não é meu caso).
- 03/11/2026: a etapa vence com 2 semanas boas seguidas. Semana boa: 5 de 7 dias na meta nos hábitos de todo dia (nos de eliminar, 1 deslize perdoado) e dentro do teto nos de vezes por semana. Um dia ruim não zera nada. O vencido segue conferido como mantido.
- 03/11/2026: cada parte da jornada é uma etapa (resposta do Rafael: "Etapa", como em "Jornada de hábitos: Etapa 2 de 8, Água"). As fases da dieta continuam Fase 1 Adaptação, 2 Ideal e 3 Drástica.
- 04/11/2026: a jornada aparece num cartão curto no topo do Diário, antes do Resumo, com a etapa atual, os toques do dia e os mantidos numa linha.
- 04/11/2026: a jornada vale para todos os objetivos (Emagrecer, Manter e Ganhar).
- 04/11/2026: depois da vitória, a próxima etapa espera o toque em "Começar a próxima".
- ~~04/11/2026: dia sem nada lançado fica sem dado (não conta a favor nem contra). A semana só pode ser boa com pelo menos 5 dias lançados, e a sequência de semanas só espera.~~ (substituída em 04/11/2026)
- 04/11/2026: dia sem nada lançado fica sem dado (não conta a favor nem contra). A semana só pode ser boa com pelo menos 5 dias lançados; com menos, a sequência de semanas só espera. A exceção é a semana com 2 dias ou mais com deslize: ela conta como ruim e zera as semanas boas seguidas, mesmo com menos de 5 dias lançados (resposta do Rafael: "Conta como ruim"; 2 deslizes já passam do perdão).
- 04/11/2026: saúde: uma pergunta só, ao ligar a jornada (rins, coração, fígado, insulina, gravidez, transtorno alimentar). Um sim deixa só o que é seguro: sem etapas de água e proteína, sem eliminar com cadeado (vira reduzir), sem contar semanas seguidas (passa de etapa quando quiser) e com "siga o seu médico". A resposta fica só no celular.
- 04/11/2026: jejum: a meta continua aceitando até 72 h, mas avisa acima de 24 h ("só com acompanhamento médico"), com a jornada ligada ou não.
- 04/11/2026: café e chá adoçados em casa ficam no reduzir, com teto: até 1 colher de chá rasa por xícara (uns 4 g), ou nada.
- 04/11/2026: suco 100% de fruta fica no reduzir: até 150 ml por dia, junto com a refeição.
- 04/11/2026: trocas da etapa da bebida: primeiro água, água com gás e chá sem açúcar; refri zero só como ponte. A regra da água fica igual.
- 04/11/2026: o Sugerir refeição pede à IA que evite o que foi eliminado (bebida com açúcar, embutidos, álcool).
- 04/11/2026: o cigarro entra na lista do eliminar, conferido por toque, com "não é meu caso", a indicação do tratamento de graça pelo SUS (INCA) e o aviso de que parar costuma trazer uns 4 a 5 kg no primeiro ano (Aubin e outros, BMJ, 2012).
- 04/11/2026: onde quer chegar: mostra a primeira parada, de 5% a 10% do peso de hoje, quando o peso meta está longe (AHA/ACC/TOS, 2013), e um aviso leve, sem travar, se o peso meta der IMC abaixo de 18,5 (ou de 22 a partir dos 60 anos).
- 04/11/2026: bebida adoçada feita em casa, fora o café e o chá (suco ou limonada com açúcar, leite com achocolatado), conta como bebida com açúcar (resposta do Rafael: "Conta"; a dose é de refri).
- 04/11/2026: a semana que acabou de fechar também aceita correção: tocar na linha "Semana passada" abre os 7 dias dela (na segunda-feira, dá para acertar o domingo).
- 04/11/2026: na montagem, a lista do reduzir começa toda em "Não é meu caso", e ele marca só o que quer reduzir.
- 04/11/2026: a jornada chega em passos. O passo 1 traz o interruptor em Ajustes, a tela Montar a jornada, o cartão no Diário, a etapa da bebida com açúcar, o aviso do jejum e o Sugerir refeição sem o que foi eliminado. As outras etapas, a conferência dos mantidos, a pausa, o mapa da jornada e os lembretes novos vêm nos próximos.

Padrões da jornada (escolhidos pelo Claude, a confirmar com o Rafael):

- A bebida com açúcar é achada pelo diário: é bebida pelo nome (uma lista da jornada, além das bebidas que o app já reconhece) ou pela marca "pede água". Ficam de fora o álcool e a cerveja sem álcool (a troca sugerida para ele), o zero, diet e light ("zero lactose" e "zero gordura" não contam como zero), a água, a água de coco, o café, o chá feito em casa, o leite puro e o suco 100%. Conta com 2,5 g de açúcar por 100 ml ou mais ou, sem o açúcar informado, com 5 g de carboidrato por 100 ml ou mais. Um toque no dia vale mais que a conta.
- "Outro hábito" nunca aceita como eliminar uma refeição, um grupo inteiro (arroz, feijão, pão, fruta, leite, óleo), o sal ou a água, nem escritos de outro jeito ("cortar o pão", "arroz e feijão", "ficar sem jantar"), e explica o porquê. Reduzir sempre pode.
- O cartão só aparece no dia de hoje.
- A pergunta de saúde fica na tela Montar a jornada (aberta por Ajustes ou pelo cartão do Diário, com a jornada ligada): nada começa sem ela e sem a resposta do cigarro.
- "Onde quer chegar" usa o peso, o peso meta e o objetivo do perfil, sem campos novos: o que mudar ali muda o perfil, e a meta de calorias calculada acompanha. A primeira parada aparece com o peso meta mais de 10% abaixo do peso de hoje.
- No modo seguro, os três obrigatórios começam em Reduzir. A bebida com açúcar em Reduzir tem o teto de 1 copo de 200 ml por dia, e o dia só é deslize acima dele.
- O Sugerir refeição também evita o que está em "Já não faço" e os "Outro hábito" marcados como eliminar.
- Montar de novo com a mesma etapa e a mesma resposta de saúde guarda o início, os toques e a espera de "Começar a próxima"; mudar a resposta de saúde recomeça a etapa.
