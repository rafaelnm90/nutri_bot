package com.rafaelnm90.calorias

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NutricaoTeste {

    private val homem = Perfil(
        sexo = Sexo.MASCULINO,
        anoNascimento = 1990,
        alturaCm = 175.0,
        pesoKg = 80.0,
        pesoMetaKg = 72.0,
        atividade = Atividade.LEVE,
        objetivo = Objetivo.PERDER,
        fase = Fase.ADAPTACAO,
    )

    // --- Metas ---

    @Test
    fun metaPeloGastoMenosAFase() {
        // Basal: 10*80 + 6,25*175 - 5*36 + 5 = 1718,75; com atividade leve: 2363,3.
        assertEquals(2163, Calculos.metaKcal(homem, 2026))
        assertEquals(1963, Calculos.metaKcal(homem.copy(fase = Fase.IDEAL), 2026))
        assertEquals(1763, Calculos.metaKcal(homem.copy(fase = Fase.DRASTICA), 2026))
        assertEquals(2363, Calculos.metaKcal(homem.copy(objetivo = Objetivo.MANTER), 2026))
        assertEquals(2563, Calculos.metaKcal(homem.copy(objetivo = Objetivo.GANHAR), 2026))
    }

    @Test
    fun metaDigitadaValeNoLugarDaCalculada() {
        assertEquals(1753, Calculos.metaKcal(homem.copy(metaManualKcal = 1753), 2026))
    }

    @Test
    fun metaNaoDesceDoMinimoSeguro() {
        val pequeno = homem.copy(pesoKg = 50.0, alturaCm = 150.0, atividade = Atividade.SEDENTARIO, fase = Fase.DRASTICA)
        assertEquals(1500, Calculos.metaKcal(pequeno, 2026))
        assertEquals(1200, Calculos.metaKcal(pequeno.copy(sexo = Sexo.FEMININO), 2026))
    }

    @Test
    fun semPerfilAMetaE2000() {
        assertEquals(2000, Calculos.metaKcal(Perfil(), 2026))
    }

    @Test
    fun faixaDeCemKcalNuncaAbaixoDe1200() {
        assertEquals(Faixa(1900.0, 2100.0), Calculos.faixaKcal(2000))
        assertEquals(Faixa(1200.0, 1400.0), Calculos.faixaKcal(1250))
        assertTrue(1950.0 in Calculos.faixaKcal(2000))
        assertFalse(2101.0 in Calculos.faixaKcal(2000))
    }

    @Test
    fun faixaComMetaBaixaFicaCom200KcalAPartirDoPiso() {
        // Escolha do Rafael: meta à mão abaixo do piso dá 1.200 a 1.400.
        assertEquals(Faixa(1200.0, 1400.0), Calculos.faixaKcal(1000))
        assertEquals(Faixa(1200.0, 1400.0), Calculos.faixaKcal(1100))
        assertEquals(Faixa(1200.0, 1400.0), Calculos.faixaKcal(1200))
        assertEquals(Faixa(1200.0, 1400.0), Calculos.faixaKcal(1300))
        assertEquals(Faixa(1300.0, 1500.0), Calculos.faixaKcal(1400))
        assertTrue(1300.0 in Calculos.faixaKcal(1000))
    }

    @Test
    fun abaixoDaFaixaHojeEsperaENosOutrosDiasErra() {
        val f = Faixa(1900.0, 2100.0)
        assertEquals("⏳", sinalDaFaixa(1500.0, f, diaEmCurso = true))
        assertEquals("❌", sinalDaFaixa(1500.0, f, diaEmCurso = false))
        assertEquals("✅", sinalDaFaixa(2000.0, f, diaEmCurso = true))
        assertEquals("✅", sinalDaFaixa(2000.0, f, diaEmCurso = false))
        assertEquals("❌", sinalDaFaixa(2200.0, f, diaEmCurso = true))
        assertEquals("❌", sinalDaFaixa(2200.0, f, diaEmCurso = false))
    }

    @Test
    fun macrosDivididos30_40_30ComFaixa() {
        val m = Calculos.metaMacros(2000)
        assertEquals(150.0, m.prot, 0.01)
        assertEquals(200.0, m.carb, 0.01)
        assertEquals(66.67, m.gord, 0.01)
        assertEquals(Faixa(135.0, 165.0), Calculos.faixaProt(m))
        assertEquals(Faixa(180.0, 220.0), Calculos.faixaCarb(m))
        assertEquals(56.67, Calculos.faixaGord(m).min, 0.01)
    }

    @Test
    fun aguaDe35MlPorKgOuAManual() {
        assertEquals(2800, Calculos.metaAguaMl(homem))
        assertEquals(2500, Calculos.metaAguaMl(homem.copy(metaAguaManualMl = 2500)))
        assertEquals(2000, Calculos.metaAguaMl(Perfil()))
    }

    @Test
    fun metasDeNutrientesPorIdadeESexo() {
        fun valor(p: Perfil, nome: String) = MetasNutrientes.de(p, 2026).first { it.nome == nome }.valor
        val mulher = homem.copy(sexo = Sexo.FEMININO, anoNascimento = 1996)
        assertEquals(1000.0, valor(homem, "Cálcio"), 0.0)
        assertEquals(8.0, valor(homem, "Ferro"), 0.0)
        assertEquals(11.0, valor(homem, "Zinco"), 0.0)
        assertEquals(90.0, valor(homem, "Vitamina C"), 0.0)
        assertEquals(18.0, valor(mulher, "Ferro"), 0.0)
        assertEquals(75.0, valor(mulher, "Vitamina C"), 0.0)
        assertEquals(700.0, valor(mulher, "Vitamina A"), 0.0)
        assertEquals(1200.0, valor(mulher.copy(anoNascimento = 1970), "Cálcio"), 0.0)
        assertEquals(8.0, valor(mulher.copy(anoNascimento = 1970), "Ferro"), 0.0)
        assertEquals(1200.0, valor(homem.copy(anoNascimento = 1950), "Cálcio"), 0.0)
    }

    @Test
    fun metaDeNutrienteMinimoMaximoEFaixa() {
        val metas = MetasNutrientes.de(homem, 2026)
        val fibra = metas.first { it.nome == "Fibras" }
        val sodio = metas.first { it.nome == "Sódio" }
        val vitA = metas.first { it.nome == "Vitamina A" }
        assertTrue(fibra.ok(25.0))
        assertFalse(fibra.ok(10.0))
        assertTrue(sodio.ok(1500.0))
        assertFalse(sodio.ok(2500.0))
        assertTrue(vitA.ok(1000.0))
        assertFalse(vitA.ok(500.0))
        assertFalse(vitA.ok(3500.0))
    }

    // --- Fases e check-in ---

    @Test
    fun checkInDepoisDeSeteDias() {
        val hoje = LocalDate.of(2026, 10, 11)
        assertTrue(CheckIn.pendente(homem.copy(inicioFase = "2026-10-04"), hoje))
        assertFalse(CheckIn.pendente(homem.copy(inicioFase = "2026-10-05"), hoje))
        assertFalse(CheckIn.pendente(homem.copy(inicioFase = null), hoje))
        assertFalse(CheckIn.pendente(homem.copy(inicioFase = "2026-10-01", objetivo = Objetivo.MANTER), hoje))
        assertFalse(CheckIn.pendente(homem.copy(inicioFase = "2026-10-01", metaManualKcal = 1800), hoje))
    }

    @Test
    fun conselhoDoCheckInSugereAsFasesCertas() {
        assertEquals(listOf(Fase.ADAPTACAO, Fase.IDEAL), CheckIn.conselho(Sensacao.FOME_FRAQUEZA, Fase.IDEAL).fases)
        assertEquals(listOf(Fase.ADAPTACAO), CheckIn.conselho(Sensacao.FOME_FRAQUEZA, Fase.ADAPTACAO).fases)
        assertEquals(listOf(Fase.ADAPTACAO, Fase.IDEAL), CheckIn.conselho(Sensacao.EQUILIBRIO, Fase.ADAPTACAO).fases)
        assertEquals(listOf(Fase.DRASTICA, Fase.IDEAL), CheckIn.conselho(Sensacao.ENERGIA_ALTA, Fase.IDEAL).fases)
        assertEquals(listOf(Fase.DRASTICA), CheckIn.conselho(Sensacao.ENERGIA_ALTA, Fase.DRASTICA).fases)
    }

    // --- Sugestão de refeição ---

    @Test
    fun alvoDaSugestaoPeloHorario() {
        assertEquals(AlvoRefeicao("café da manhã", 500), Sugestao.alvo(2000, 0.0, 9))
        assertEquals(AlvoRefeicao("almoço", 700), Sugestao.alvo(2000, 900.0, 13))
        assertEquals(AlvoRefeicao("lanche da tarde", 300), Sugestao.alvo(2000, 1000.0, 16))
        assertEquals(AlvoRefeicao("jantar ou ceia", 500), Sugestao.alvo(2000, 500.0, 20))
        // Depois das 19 h, com até 600 kcal faltando, o alvo fecha o dia.
        assertEquals(AlvoRefeicao("jantar ou ceia", 550), Sugestao.alvo(2000, 1450.0, 20))
        // O alvo nunca passa do que falta.
        assertEquals(AlvoRefeicao("almoço", 200), Sugestao.alvo(2000, 1800.0, 12))
        assertNull(Sugestao.alvo(2000, 1960.0, 12))
    }

    // --- Lembretes ---

    @Test
    fun fracaoDoDiaSoDas8As22() {
        assertNull(Lembretes.fracaoDoDia(7, 59))
        assertEquals(0.0, Lembretes.fracaoDoDia(8, 0)!!, 0.0)
        assertEquals(0.5, Lembretes.fracaoDoDia(15, 0)!!, 0.0)
        assertNull(Lembretes.fracaoDoDia(22, 0))
    }

    @Test
    fun lembreteDeAguaPeloAtrasoEIntervalo() {
        // Às 15 h deveria ter bebido 1.400 ml de 2.800.
        assertTrue(Lembretes.aguaAtrasada(2800, 0, 0.5, Double.MAX_VALUE))
        assertFalse(Lembretes.aguaAtrasada(2800, 0, 0.5, 2.0))
        assertTrue(Lembretes.aguaAtrasada(2800, 0, 0.5, 2.5))
        assertFalse(Lembretes.aguaAtrasada(2800, 1000, 0.5, 9.0))
        // Atraso de 800 ml: espera 4 h.
        assertFalse(Lembretes.aguaAtrasada(2800, 600, 0.5, 3.0))
        assertTrue(Lembretes.aguaAtrasada(2800, 600, 0.5, 4.5))
    }

    @Test
    fun lembreteDeComidaPeloAtrasoSemJejum() {
        assertTrue(Lembretes.comidaAtrasada(2000, 0.0, 0.5, Double.MAX_VALUE, emJejum = false))
        assertFalse(Lembretes.comidaAtrasada(2000, 0.0, 0.5, 4.0, emJejum = false))
        assertTrue(Lembretes.comidaAtrasada(2000, 0.0, 0.6, 3.5, emJejum = false))
        assertFalse(Lembretes.comidaAtrasada(2000, 0.0, 0.6, Double.MAX_VALUE, emJejum = true))
        assertFalse(Lembretes.comidaAtrasada(2000, 600.0, 0.5, Double.MAX_VALUE, emJejum = false))
    }

    // --- Bebidas ---

    @Test
    fun bebidaAlcoolicaERefrigerantePedemAgua() {
        assertTrue(Bebidas.pedeAgua("Cerveja, pilsen"))
        assertTrue(Bebidas.pedeAgua("Refrigerante, tipo cola"))
        assertTrue(Bebidas.pedeAgua("Coca-Cola Zero"))
        assertTrue(Bebidas.pedeAgua("Água tônica"))
        assertTrue(Bebidas.pedeAgua("Vinho tinto"))
        assertFalse(Bebidas.pedeAgua("Guaraná em pó"))
        assertFalse(Bebidas.pedeAgua("Cocada branca"))
        assertFalse(Bebidas.pedeAgua("Suco de laranja"))
        assertFalse(Bebidas.pedeAgua("Arroz, tipo 1, cozido"))
        assertTrue(Bebidas.pedeAgua("Guaraná Antarctica"))
        assertTrue(Bebidas.pedeAgua("Refri zero"))
        assertTrue(Bebidas.pedeAgua("Lata de guaraná"))
        assertTrue(Bebidas.pedeAgua("Kuat guaraná"))
        assertTrue(Bebidas.pedeAgua("Taça de vinho"))
        assertTrue(Bebidas.pedeAgua("Gin com tônica"))
        assertTrue(Bebidas.pedeAgua("Suco com vodka"))
        assertTrue(Bebidas.pedeAgua("Batida de coco com cachaça"))
        // Drinks com o nome próprio na frente e a cachaça da TACO.
        assertTrue(Bebidas.pedeAgua("Mojito com rum"))
        assertTrue(Bebidas.pedeAgua("Moscow mule com vodka"))
        assertTrue(Bebidas.pedeAgua("Cuba libre"))
        assertTrue(Bebidas.pedeAgua("Cana, aguardente 1"))
        assertFalse(Bebidas.pedeAgua("Sanduíche cubano"))
        // Drink escrito com "com": a bebida depois dele conta.
        assertTrue(Bebidas.pedeAgua("Limonada com vodka"))
        assertTrue(Bebidas.pedeAgua("Caipifruta de morango com vodka"))
        assertTrue(Bebidas.pedeAgua("Coquetel de frutas com vodka"))
        assertTrue(Bebidas.pedeAgua("Red Bull com vodka"))
        assertTrue(Bebidas.pedeAgua("Batida de maracujá"))
    }

    @Test
    fun comidaComBebidaNoNomeNaoPedeAgua() {
        assertFalse(Bebidas.pedeAgua("Açaí, polpa, com xarope de guaraná e glucose"))
        assertFalse(Bebidas.pedeAgua("Feijão refrito"))
        assertFalse(Bebidas.pedeAgua("Frango ao molho de vinho"))
        assertFalse(Bebidas.pedeAgua("Bolo de cerveja preta"))
        assertFalse(Bebidas.pedeAgua("Costela na cerveja"))
        assertFalse(Bebidas.pedeAgua("Peixe ao vinho branco"))
        assertFalse(Bebidas.pedeAgua("Camarão na cachaça"))
        assertFalse(Bebidas.pedeAgua("Frango à cerveja"))
        assertFalse(Bebidas.pedeAgua("Pão de cerveja"))
        assertFalse(Bebidas.pedeAgua("Bala de guaraná"))
        // "Batida" fora do começo é a vitamina, sem álcool.
        assertFalse(Bebidas.pedeAgua("Banana batida com leite"))
        assertFalse(Bebidas.pedeAgua("Vitamina de banana batida"))
        assertFalse(Bebidas.pedeAgua("Clara batida em neve"))
    }

    @Test
    fun naIaValeAAguaQueElaInformou() {
        // Sem agua_extra_ml, o nome não passa por cima da IA.
        assertFalse(ItemEstimado("Frango ao vinho", 200.0, 300.0, 30.0, 5.0, 15.0).comoAlimento().pedeAgua)
        assertTrue(ItemEstimado("Long neck", 355.0, 150.0, 1.0, 12.0, 0.0, aguaExtraMl = 355.0).comoAlimento().pedeAgua)
    }

    @Test
    fun alimentoFeitoAMaoSegueARegraDaAgua() {
        val ipa = alimentoFeitoAMao("Cerveja artesanal IPA ", 60.0, 0.5, 5.0, 0.0, 350.0, "789")
        assertTrue(ipa.pedeAgua)
        assertEquals("Cerveja artesanal IPA", ipa.nome)
        assertEquals(ORIGEM_MEU, ipa.origem)
        assertEquals("789", ipa.codigoBarras)
        assertFalse(alimentoFeitoAMao("Granola", 400.0, 10.0, 60.0, 12.0, null, null).pedeAgua)
        // Prato com bebida no nome não soma na água.
        assertFalse(alimentoFeitoAMao("Frango ao vinho", 180.0, 20.0, 2.0, 9.0, null, null).pedeAgua)
        assertFalse(alimentoFeitoAMao("Costela na cerveja", 300.0, 25.0, 3.0, 20.0, null, null).pedeAgua)
        assertFalse(alimentoFeitoAMao("Vitamina de banana batida", 90.0, 3.0, 15.0, 2.0, null, null).pedeAgua)
        assertTrue(alimentoFeitoAMao("Caipifruta com vodka", 150.0, 0.0, 20.0, 0.0, null, null).pedeAgua)
    }

    @Test
    fun bebidaApareceEmMl() {
        assertTrue(Bebidas.ehBebida("Suco de laranja"))
        assertTrue(Bebidas.ehBebida("Café com leite"))
        assertTrue(Bebidas.ehBebida("Cerveja, pilsen"))
        assertFalse(Bebidas.ehBebida("Leite condensado"))
        assertFalse(Bebidas.ehBebida("Arroz com leite"))
    }

    @Test
    fun aguaExtraDoDiaPelasBebidas() {
        val cerveja = Alimento("Cerveja", 41.0, 0.3, 3.3, 0.0, pedeAgua = true)
        val arroz = Alimento("Arroz", 128.0, 2.5, 28.1, 0.2)
        val dia = listOf(
            Registro(1, "2026-10-11", Refeicao.JANTAR, cerveja, 350.0),
            Registro(2, "2026-10-11", Refeicao.JANTAR, arroz, 100.0),
        )
        assertEquals(350, dia.aguaExtraMl())
    }

    @Test
    fun textoDaRefeicaoNoPluralUsaAosENos() {
        assertEquals("Adicionar ao Jantar", Refeicao.JANTAR.adicionarAo)
        assertEquals("Adicionar ao Café da manhã", Refeicao.CAFE.adicionarAo)
        assertEquals("Adicionar aos Lanches", Refeicao.LANCHES.adicionarAo)
        assertEquals("O que você comeu no almoço?", Refeicao.ALMOCO.oQueComeu)
        assertEquals("O que você comeu nos lanches?", Refeicao.LANCHES.oQueComeu)
    }

    // --- Dia mostrado, peso e números digitados ---

    @Test
    fun diaVoltaParaHojeNumDiaNovo() {
        // Estava vendo hoje (10/10) e voltou no dia 11: passa para o 11.
        assertEquals("2026-10-11", diaAoVoltar("2026-10-10", "2026-10-10", "2026-10-11"))
        // Tinha escolhido um dia antigo de propósito: fica nele.
        assertEquals("2026-10-05", diaAoVoltar("2026-10-05", "2026-10-10", "2026-10-11"))
        // Mesmo dia: nada muda.
        assertEquals("2026-10-11", diaAoVoltar("2026-10-11", "2026-10-11", "2026-10-11"))
    }

    @Test
    fun apagarOPesoMaisRecenteVoltaOPerfilAoAnterior() {
        val pesos = listOf("2026-10-01" to 82.0, "2026-10-05" to 81.2, "2026-10-11" to 80.4)
        assertEquals(81.2, pesoDepoisDeApagar(pesos, "2026-10-11")!!, 0.0)
        assertNull(pesoDepoisDeApagar(pesos, "2026-10-05"))
        assertNull(pesoDepoisDeApagar(listOf("2026-10-11" to 80.4), "2026-10-11"))
    }

    @Test
    fun inteiroDigitadoAceitaPontoDeMilhar() {
        assertEquals(1800, lerInteiro("1.800"))
        assertEquals(1800, lerInteiro("1800"))
        assertEquals(2500, lerInteiro(" 2.500 "))
        assertEquals(250, lerInteiro("250,5"))
        assertNull(lerInteiro(""))
    }

    // --- Busca ---

    private val tabela = listOf(
        Alimento("Arroz, tipo 1, cozido", 128.0, 2.5, 28.1, 0.2),
        Alimento("Feijão, carioca, cozido", 76.0, 4.8, 13.6, 0.5),
        Alimento("Pão, trigo, francês", 300.0, 8.0, 58.6, 3.1),
        Alimento("Biscoito, doce, recheado com chocolate", 472.0, 6.4, 70.5, 19.6),
        Alimento("Arroz, integral, cozido", 124.0, 2.6, 25.8, 1.0),
    )

    @Test
    fun buscaSemAcentoEPeloComecoDasPalavras() {
        assertEquals(listOf("Feijão, carioca, cozido"), Busca.buscar(tabela, "feijao").map { it.nome })
        assertEquals(listOf("Pão, trigo, francês"), Busca.buscar(tabela, "PAO FRANC").map { it.nome })
        assertTrue(Busca.buscar(tabela, "ozido").isEmpty())
    }

    @Test
    fun buscaPoeOMaisSimplesPrimeiro() {
        val nomes = Busca.buscar(tabela, "arroz").map { it.nome }
        assertEquals(listOf("Arroz, tipo 1, cozido", "Arroz, integral, cozido"), nomes)
        assertEquals("Arroz, tipo 1, cozido", Busca.buscar(tabela, "cozido").first().nome)
    }

    @Test
    fun buscaNaoRepeteOMesmoNome() {
        val repetida = tabela + Alimento("Feijao, Carioca, cozido", 80.0, 5.0, 14.0, 0.5, origem = ORIGEM_MEU)
        assertEquals(1, Busca.buscar(repetida, "feijão").size)
    }

    // --- Respostas da IA ---

    @Test
    fun leitorIaLeItensComNutrientesEAguaExtra() {
        val r = LeitorIa.ler(
            """{"e_comida": true, "resposta": "", "itens": [
                {"nome": "arroz branco cozido", "gramas": 150, "kcal": 192, "proteina": 3.8, "carboidrato": 42.1,
                 "gordura": 0.3, "fibra": 2.4, "sodio_mg": 2, "vitamina_c_mg": 0},
                {"nome": "Cerveja", "gramas": 330, "kcal": 140, "agua_extra_ml": 330}]}"""
        )
        assertEquals(2, r.itens.size)
        assertEquals("Arroz branco cozido", r.itens[0].nome)
        assertEquals(2.4, r.itens[0].micros.fibra, 0.0)
        assertEquals(330.0, r.itens[1].aguaExtraMl, 0.0)
        assertTrue(r.itens[1].comoAlimento().pedeAgua)
    }

    @Test
    fun leitorIaDevolveARespostaQuandoNaoEComida() {
        val r = LeitorIa.ler("""{"e_comida": false, "resposta": "Isso é um gato, não um prato 😺", "itens": []}""")
        assertTrue(r.itens.isEmpty())
        assertEquals("Isso é um gato, não um prato 😺", r.resposta)
    }

    @Test
    fun leitorIaAceitaBlocoDeCodigoEListaSolta() {
        val emBloco = LeitorIa.ler("```json\n{\"itens\":[{\"nome\":\"Ovo\",\"gramas\":50,\"kcal\":72}]}\n```")
        assertEquals(1, emBloco.itens.size)
        assertEquals(0.0, emBloco.itens[0].prot, 0.0)
        val solta = LeitorIa.ler("""[{"nome":"Banana","gramas":"80","kcal":70}]""")
        assertEquals(80.0, solta.itens[0].gramas, 0.0)
    }

    @Test
    fun leitorIaIgnoraItensSemNomeOuSemGramasEOTextoIlegivel() {
        val r = LeitorIa.ler("""{"itens":[{"nome":"","gramas":10},{"nome":"Suco","gramas":0},{"nome":"Pão","gramas":50,"kcal":150}]}""")
        assertEquals(listOf("Pão"), r.itens.map { it.nome })
        assertTrue(LeitorIa.ler("não sei").itens.isEmpty())
        assertTrue(LeitorIa.ler("{quebrado").itens.isEmpty())
    }

    @Test
    fun itemDaIaViraValoresDe100g() {
        val a = ItemEstimado("Arroz", 150.0, 192.0, 3.75, 42.0, 0.3, Micros(fibra = 3.0)).comoAlimento()
        assertEquals(128.0, a.kcal100, 0.001)
        assertEquals(2.5, a.prot100, 0.001)
        assertEquals(2.0, a.micros100.fibra, 0.001)
        assertEquals(150.0, a.porcaoG!!, 0.0)
        assertEquals(192.0, a.kcal(150.0), 0.001)
        assertEquals(ORIGEM_IA, a.origem)
        assertFalse(a.pedeAgua)
    }

    @Test
    fun leitorDeExercicio() {
        val (treino, _) = LeitorIa.lerExercicio(
            """{"e_exercicio": true, "descricao": "corrida na esteira", "minutos": 40, "kcal": 420, "resposta": ""}"""
        )
        assertEquals("Corrida na esteira", treino!!.descricao)
        assertEquals(40, treino.minutos)
        assertEquals(420, treino.kcal)
        val (semDuracao, _) = LeitorIa.lerExercicio("""{"e_exercicio": true, "descricao": "Musculação", "kcal": 180}""")
        assertEquals(30, semDuracao!!.minutos)
        val (nada, resposta) = LeitorIa.lerExercicio("""{"e_exercicio": false, "resposta": "Isso não é treino"}""")
        assertNull(nada)
        assertEquals("Isso não é treino", resposta)
    }

    // --- Soma do dia ---

    @Test
    fun somaDosRegistrosDoDia() {
        val arroz = tabela[0].copy(micros100 = Micros(fibra = 1.6, sodio = 1.0))
        val feijao = tabela[1].copy(micros100 = Micros(fibra = 8.5, ferro = 1.3))
        val dia = listOf(
            Registro(1, "2026-10-11", Refeicao.ALMOCO, arroz, 200.0),
            Registro(2, "2026-10-11", Refeicao.ALMOCO, feijao, 100.0),
        )
        val soma = dia.somar()
        assertEquals(332.0, soma.kcal, 0.001)
        assertEquals(9.8, soma.prot, 0.001)
        val micros = dia.somarMicros()
        assertEquals(11.7, micros.fibra, 0.001)
        assertEquals(1.3, micros.ferro, 0.001)
        assertEquals(2.0, micros.sodio, 0.001)
    }

    // --- Fontes de alimentos ---

    @Test
    fun tacoLeOArquivoGeradoComNutrientes() {
        val lista = Taco.ler(
            """[{"nome":"Arroz, tipo 1, cozido","categoria":"Cereais e derivados","kcal":128,"proteina":2.5,
                 "carboidrato":28.1,"gordura":0.2,"fibra":1.6,"sodio":1,"ferro":0.1,"vitamina_c":null},
                {"nome":"Cerveja, pilsen","kcal":41},
                {"nome":"","kcal":1}]"""
        )
        assertEquals(2, lista.size)
        assertEquals("Cereais e derivados", lista[0].categoria)
        assertEquals(28.1, lista[0].carb100, 0.0)
        assertEquals(1.6, lista[0].micros100.fibra, 0.0)
        assertEquals(0.0, lista[0].micros100.vitaminaC, 0.0)
        assertTrue(lista[1].pedeAgua)
        assertTrue(Taco.ler("isso não é json").isEmpty())
    }

    @Test
    fun openFoodFactsLeProdutoComMarcaPorcaoENutrientes() {
        val a = OpenFoodFacts.lerProduto(
            "7891000100103",
            """{"status":1,"product":{"product_name":"Leite condensado","brands":"Moça,Nestlé",
               "serving_size":"20 g","serving_quantity":20,
               "nutriments":{"energy-kcal_100g":325,"proteins_100g":7.5,"carbohydrates_100g":55,"fat_100g":8,
                             "sugars_100g":55,"sodium_100g":0.12,"calcium_100g":0.25}}}"""
        )!!
        assertEquals("Leite condensado (Moça)", a.nome)
        assertEquals(325.0, a.kcal100, 0.0)
        assertEquals(20.0, a.porcaoG!!, 0.0)
        assertEquals(55.0, a.micros100.acucar, 0.0)
        assertEquals(120.0, a.micros100.sodio, 0.001)
        assertEquals(250.0, a.micros100.calcio, 0.001)
        assertEquals("7891000100103", a.codigoBarras)
        assertEquals(ORIGEM_OFF, a.origem)
        assertFalse(a.pedeAgua)
    }

    @Test
    fun openFoodFactsMarcaRefrigerantePelaCategoria() {
        val a = OpenFoodFacts.lerProduto(
            "1",
            """{"status":1,"product":{"product_name":"Guaraná Antarctica","categories_tags":["en:beverages","en:sodas"],
               "nutriments":{"energy-kcal_100g":41}}}"""
        )!!
        assertTrue(a.pedeAgua)
    }

    @Test
    fun aguaComGasNaoContaComoRefrigerante() {
        val a = OpenFoodFacts.lerProduto(
            "2",
            """{"status":1,"product":{"product_name":"Água mineral com gás",
               "categories_tags":["en:beverages","en:carbonated-drinks","en:waters","en:carbonated-waters"],
               "nutriments":{"energy-kcal_100g":0}}}"""
        )!!
        assertFalse(a.pedeAgua)
    }

    @Test
    fun openFoodFactsConverteKjESemProdutoDevolveNulo() {
        val a = OpenFoodFacts.lerProduto(
            "1",
            """{"status":1,"product":{"product_name":"Biscoito","nutriments":{"energy_100g":418.4}}}"""
        )!!
        assertEquals(100.0, a.kcal100, 0.001)
        assertNull(a.porcaoG)
        assertNull(OpenFoodFacts.lerProduto("2", """{"status":0,"status_verbose":"product not found"}"""))
        assertNull(OpenFoodFacts.lerProduto("3", """{"status":1,"product":{"product_name":"Sem tabela"}}"""))
    }

    // --- Jornada de hábitos ---

    private fun item(
        nome: String,
        kcal: Double,
        carb: Double,
        acucar: Double = 0.0,
        prot: Double = 0.0,
        gord: Double = 0.0,
        pedeAgua: Boolean = false,
        origem: String = ORIGEM_IA,
    ) = Alimento(nome, kcal, prot, carb, gord, Micros(acucar = acucar), pedeAgua = pedeAgua, origem = origem)

    @Test
    fun bebidaComAcucarContaAsDaListaDoEliminar() {
        val contam = listOf(
            item("Coca-Cola", 42.0, 10.6, acucar = 10.6, pedeAgua = true, origem = ORIGEM_OFF),
            item("Refrigerante, tipo cola", 34.0, 8.7, pedeAgua = true, origem = ORIGEM_TACO),
            item("Guaraná Antarctica", 41.0, 10.0, acucar = 10.0, pedeAgua = true),
            item("Refri", 42.0, 10.6, acucar = 10.6, pedeAgua = true),
            item("Sukita", 45.0, 11.0, origem = ORIGEM_MEU),
            item("Água tônica", 37.0, 9.0, acucar = 9.0, pedeAgua = true),
            item("Néctar de pêssego", 50.0, 12.0, acucar = 11.0, origem = ORIGEM_OFF),
            item("Suco de caixinha de uva", 55.0, 13.0, acucar = 12.0),
            item("Suco Del Valle uva", 52.0, 12.5, acucar = 12.0, origem = ORIGEM_OFF),
            item("Suco de maracujá com açúcar", 50.0, 12.0, acucar = 10.0),
            item("Refresco em pó", 380.0, 95.0, origem = ORIGEM_MEU),
            item("Tang laranja", 380.0, 95.0, acucar = 90.0, origem = ORIGEM_OFF),
            item("Chá pronto de pêssego", 28.0, 7.0, acucar = 6.5, origem = ORIGEM_OFF),
            item("Chá gelado de pêssego (Leão)", 28.0, 7.0, acucar = 6.0, origem = ORIGEM_OFF),
            item("Ice tea limão", 30.0, 7.5, acucar = 7.0),
            item("Achocolatado pronto", 75.0, 12.0, acucar = 10.0, prot = 2.4, gord = 2.0, origem = ORIGEM_OFF),
            item("Toddynho", 80.0, 13.0, acucar = 11.0, origem = ORIGEM_OFF),
            item("Bebida láctea, pêssego", 55.0, 9.0, prot = 2.0, gord = 1.3, origem = ORIGEM_TACO),
            item("Cana, caldo de", 65.0, 18.0, origem = ORIGEM_TACO),
            item("Caldo de cana", 70.0, 18.0, acucar = 17.0),
            item("Garapa", 70.0, 18.0, acucar = 17.0),
            item("Red Bull", 46.0, 11.0, acucar = 11.0, origem = ORIGEM_OFF),
            item("Energético", 45.0, 11.0, acucar = 11.0),
            item("Gatorade limão", 24.0, 6.0, acucar = 6.0, origem = ORIGEM_OFF),
            item("Bebida isotônica, sabores variados", 26.0, 6.4, origem = ORIGEM_TACO),
            item("Limonada suíça", 45.0, 11.0, acucar = 10.0),
            item("Leite com achocolatado", 85.0, 12.0, acucar = 10.0, prot = 3.0, gord = 3.0),
            item("Leite fermentado", 70.0, 15.0, acucar = 13.0, origem = ORIGEM_OFF),
            item("Iogurte de beber de morango", 75.0, 12.0, acucar = 11.0, origem = ORIGEM_OFF),
            item("Achocolatado em pó", 380.0, 90.0, acucar = 75.0, origem = ORIGEM_OFF),
            item("Chocolate quente", 90.0, 13.0, acucar = 11.0, prot = 3.0, gord = 3.0),
            item("Red Bull Energy Drink", 46.0, 11.0, acucar = 11.0, pedeAgua = true, origem = ORIGEM_OFF),
            item("Suco natural de laranja com açúcar", 55.0, 13.0, acucar = 12.0),
            item("Suco de uva", 55.0, 13.0, origem = ORIGEM_MEU),
            item("Yakult", 70.0, 15.0, acucar = 13.0, origem = ORIGEM_OFF),
            // "Zero lactose" e "zero gordura" não falam do açúcar.
            item("Bebida láctea zero lactose sabor morango", 70.0, 11.0, acucar = 10.0, prot = 2.0, gord = 1.5, origem = ORIGEM_OFF),
            item("Achocolatado pronto zero lactose", 75.0, 12.0, acucar = 10.0, prot = 2.4, gord = 2.0, origem = ORIGEM_OFF),
            item("Iogurte de beber zero gordura morango", 45.0, 8.0, acucar = 7.5, prot = 3.0, origem = ORIGEM_OFF),
            // Néctar é adoçado mesmo com "natural" no nome.
            item("Néctar de uva Natural One", 55.0, 13.0, acucar = 12.0, origem = ORIGEM_OFF),
            item("Néctar natural de pêssego", 50.0, 12.0, acucar = 11.0),
            // Chá das marcas de chá pronto.
            item("Chá Fuze pêssego", 28.0, 7.0, acucar = 7.0),
            item("Chá Lipton limão", 28.0, 7.0, acucar = 7.0),
            // Sem álcool, mas doce: só a cerveja sem álcool fica de fora.
            item("Espumante sem álcool", 45.0, 11.0, acucar = 10.0, pedeAgua = true, origem = ORIGEM_OFF),
            item("Drink sem álcool de morango", 45.0, 11.0, acucar = 10.0),
            item("Espumante zero álcool", 45.0, 11.0, acucar = 10.0, pedeAgua = true, origem = ORIGEM_OFF),
            // Os limites contam (a partir de 2,5 g de açúcar ou, sem ele, de 5 g de carboidrato).
            item("Refri no limite", 10.0, 2.5, acucar = 2.5, pedeAgua = true),
            item("Refri só com carboidrato", 20.0, 5.0, pedeAgua = true),
            // Marca que o nome não diz: é bebida pela marca "pede água" do código de barras.
            item("Pureza", 40.0, 10.0, acucar = 10.0, pedeAgua = true, origem = ORIGEM_OFF),
        )
        contam.forEach { assertTrue(it.nome, Jornada.bebidaComAcucar(it)) }
    }

    @Test
    fun bebidaComAcucarDeixaDeForaOZeroOAlcoolOCafeEOSuco100() {
        val ficamDeFora = listOf(
            item("Coca-Cola Zero", 0.3, 0.0, pedeAgua = true, origem = ORIGEM_OFF),
            item("Refri zero", 1.0, 0.1, pedeAgua = true),
            item("Guaraná Antarctica Zero Açúcar", 1.0, 0.0, pedeAgua = true),
            item("Pepsi Black", 0.4, 0.0, pedeAgua = true, origem = ORIGEM_OFF),
            item("Red Bull Sugarfree", 3.0, 0.0, origem = ORIGEM_OFF),
            item("Monster Ultra", 2.0, 1.0, origem = ORIGEM_OFF),
            item("Limonada sem açúcar", 8.0, 2.0),
            item("Água", 0.0, 0.0),
            item("Água com gás", 0.0, 0.0),
            item("Água de coco", 22.0, 5.0, acucar = 5.0),
            item("Coco, água de", 22.0, 5.3, origem = ORIGEM_TACO),
            item("Café com açúcar", 30.0, 7.0, acucar = 7.0),
            item("Cappuccino", 60.0, 10.0, acucar = 8.0),
            item("Chá mate com açúcar", 20.0, 5.0, acucar = 5.0),
            item("Chá verde", 1.0, 0.2),
            item("Chá, mate, infusão 5%", 0.0, 0.0, origem = ORIGEM_TACO),
            item("Leite, de vaca, integral", 61.0, 4.7, origem = ORIGEM_TACO),
            item("Leite integral (Italac)", 60.0, 4.5, acucar = 4.5, origem = ORIGEM_OFF),
            item("Suco de laranja", 45.0, 10.4, acucar = 8.4),
            item("Laranja, pêra, suco", 33.0, 7.6, origem = ORIGEM_TACO),
            item("Suco de uva integral", 64.0, 16.0, acucar = 16.0, origem = ORIGEM_OFF),
            item("Suco 100% maçã", 46.0, 11.0, acucar = 10.0, origem = ORIGEM_OFF),
            item("Suco de laranja natural", 45.0, 10.4, acucar = 8.4),
            item("Iogurte natural de beber", 60.0, 5.0, acucar = 5.0, origem = ORIGEM_OFF),
            item("Vitamina de banana", 90.0, 15.0, acucar = 12.0, prot = 3.0, gord = 2.0),
            item("Bebida de soja original", 40.0, 2.5, acucar = 1.5, origem = ORIGEM_OFF),
            item("Cerveja, pilsen", 41.0, 3.3, pedeAgua = true, origem = ORIGEM_TACO),
            item("Cerveja sem álcool", 21.0, 4.8, acucar = 2.9, pedeAgua = true, origem = ORIGEM_OFF),
            item("Heineken long neck", 42.0, 3.2, pedeAgua = true),
            item("Catuaba Selvagem", 150.0, 15.0, acucar = 14.0, pedeAgua = true),
            item("Smirnoff Ice", 70.0, 12.0, acucar = 12.0, pedeAgua = true),
            item("Caipirinha", 120.0, 15.0, acucar = 14.0, pedeAgua = true),
            item("Vinho suave", 90.0, 8.0, acucar = 7.0, pedeAgua = true),
            item("Suco de laranja com vodka", 90.0, 9.0, acucar = 8.0, pedeAgua = true),
            item("Bolo de laranja", 330.0, 50.0, acucar = 30.0),
            item("Bala de refresco", 390.0, 95.0, acucar = 70.0),
            item("Açaí, polpa, com xarope de guaraná e glucose", 110.0, 21.0, origem = ORIGEM_TACO),
            item("Guaraná em pó", 300.0, 60.0, origem = ORIGEM_MEU),
            item("Mingau de achocolatado", 120.0, 20.0, acucar = 12.0),
            item("Açúcar", 387.0, 99.5, acucar = 99.5),
            item("Arroz, tipo 1, cozido", 128.0, 28.1, origem = ORIGEM_TACO),
            // O açúcar maior que zero vale mais que o carboidrato.
            item("Suco de caixinha de goiaba", 50.0, 12.0, acucar = 1.0, origem = ORIGEM_OFF),
            // Abaixo dos limites.
            item("Refri abaixo do limite", 10.0, 2.4, acucar = 2.4, pedeAgua = true),
            item("Refri com pouco carboidrato", 20.0, 4.9, pedeAgua = true),
            item("Refrigerante diet", 1.0, 0.0, pedeAgua = true),
            item("Suco de uva sem adição de açúcar", 60.0, 15.0, acucar = 14.0, origem = ORIGEM_OFF),
            item("Chá pronto 0% açúcar", 1.0, 0.2, origem = ORIGEM_OFF),
            item("Energético sugar free", 3.0, 0.0),
            item("Chimarrão", 2.0, 0.4),
            item("Cerveja zero álcool", 21.0, 4.8, acucar = 2.9, pedeAgua = true, origem = ORIGEM_OFF),
            item("Chopp sem álcool", 21.0, 4.8, acucar = 2.9, pedeAgua = true),
        )
        ficamDeFora.forEach { assertFalse(it.nome, Jornada.bebidaComAcucar(it)) }
    }

    @Test
    fun alcoolSeparadoDoRefriSemMudarAAgua() {
        assertTrue(Bebidas.temAlcool("Cerveja sem álcool"))
        assertTrue(Bebidas.temAlcool("Vinho tinto"))
        assertTrue(Bebidas.temAlcool("Batida de coco com cachaça"))
        assertTrue(Bebidas.temAlcool("Moscow mule com vodka"))
        assertFalse(Bebidas.temAlcool("Coca-Cola"))
        assertFalse(Bebidas.temAlcool("Guaraná Antarctica"))
        assertFalse(Bebidas.temAlcool("Banana batida com leite"))
        assertFalse(Bebidas.temAlcool("Sanduíche cubano"))
        assertFalse(Bebidas.temAlcool("Red Bull Energy Drink"))
        assertTrue(Bebidas.temAlcool("Drink de maracujá"))
    }

    private val coca = item("Coca-Cola", 42.0, 10.6, acucar = 10.6, pedeAgua = true, origem = ORIGEM_OFF)

    /** Um dia lançado: arroz no almoço e, se pedir, uma lata de Coca. */
    private fun lancado(dia: String, comCoca: Boolean = false) = buildList {
        add(Registro(0, dia, Refeicao.ALMOCO, tabela[0], 150.0))
        if (comCoca) add(Registro(0, dia, Refeicao.ALMOCO, coca, 350.0))
    }

    /** Dias de [de] a [ate] (inclusive), lançados; os de [comCoca] com Coca. */
    private fun periodo(de: String, ate: String, comCoca: Set<String> = emptySet()): List<Registro> {
        val fim = LocalDate.parse(ate)
        return generateSequence(LocalDate.parse(de)) { it.plusDays(1) }.takeWhile { !it.isAfter(fim) }
            .flatMap { lancado(it.toString(), it.toString() in comCoca) }.toList()
    }

    private fun progresso(
        registros: List<Registro>,
        hoje: String,
        toques: Map<String, EstadoDia> = emptyMap(),
        seguro: Boolean = false,
        inicio: String = "2026-11-02",
        reduzir: Boolean = false,
    ) = Jornada.progresso(Habito.BEBIDA_ACUCAR, inicio, hoje, registros, toques, seguro, reduzir)

    @Test
    fun diaSemRegistroFicaSemDado() {
        assertEquals(EstadoDia.SEM_DADO, Jornada.estadoPeloDiario(Habito.BEBIDA_ACUCAR, emptyList()))
        assertEquals(EstadoDia.NA_META, Jornada.estadoPeloDiario(Habito.BEBIDA_ACUCAR, lancado("2026-11-02")))
        assertEquals(EstadoDia.DESLIZE, Jornada.estadoPeloDiario(Habito.BEBIDA_ACUCAR, lancado("2026-11-02", comCoca = true)))
    }

    /** Arroz no almoço e [ml] de suco de caixinha (bebida com açúcar), em um ou mais copos. */
    private fun comSuco(dia: String, vararg ml: Double) = lancado(dia) + ml.map {
        Registro(0, dia, Refeicao.ALMOCO, item("Suco de caixinha de uva", 55.0, 13.0, acucar = 12.0), it)
    }

    @Test
    fun emReduzirODiaContaOTetoDe200Ml() {
        val b = Habito.BEBIDA_ACUCAR
        // Em Reduzir (modo seguro), até 200 ml no dia ficam dentro do teto, somando os copos.
        assertEquals(EstadoDia.NA_META, Jornada.estadoPeloDiario(b, comSuco("2026-11-02", 150.0), reduzir = true))
        assertEquals(EstadoDia.NA_META, Jornada.estadoPeloDiario(b, comSuco("2026-11-02", 200.0), reduzir = true))
        assertEquals(EstadoDia.DESLIZE, Jornada.estadoPeloDiario(b, comSuco("2026-11-02", 250.0), reduzir = true))
        assertEquals(EstadoDia.DESLIZE, Jornada.estadoPeloDiario(b, comSuco("2026-11-02", 150.0, 100.0), reduzir = true))
        assertEquals(EstadoDia.DESLIZE, Jornada.estadoPeloDiario(b, lancado("2026-11-02", comCoca = true), reduzir = true))
        assertEquals(EstadoDia.SEM_DADO, Jornada.estadoPeloDiario(b, emptyList(), reduzir = true))
        // Em Eliminar, qualquer gole é deslize.
        assertEquals(EstadoDia.DESLIZE, Jornada.estadoPeloDiario(b, comSuco("2026-11-02", 150.0)))
        // O progresso repassa o Reduzir para os dias e para os textos.
        val r = comSuco("2026-11-02", 200.0) + comSuco("2026-11-03", 350.0) + comSuco("2026-11-04", 150.0)
        val p = progresso(r, hoje = "2026-11-04", seguro = true, reduzir = true)
        assertEquals(listOf(EstadoDia.NA_META, EstadoDia.DESLIZE, EstadoDia.NA_META), p.atual.dias.take(3).map { it.estado })
        assertEquals("Hoje, até agora: dentro do teto ✅", Jornada.textoHoje(p, seguro = true))
        assertEquals("Hoje: passou do teto 🔸", Jornada.textoHoje(progresso(r, hoje = "2026-11-03", seguro = true, reduzir = true), seguro = true))
        assertEquals("passou do teto", Jornada.rotuloDoDia(b, EstadoDia.DESLIZE, reduzir = true))
        assertTrue(Jornada.legendaDaSemana(b, reduzir = true).startsWith("✅ dentro do teto, 🔸 passou do teto, "))
        // Só a etapa marcada em Reduzir usa o teto.
        val seguro = Jornada.montar(JornadaSalva(ligada = true), true, Jornada.escolhasIniciais(true), emptyList(), "2026-11-02")
        assertTrue(Jornada.emReduzir(seguro))
        assertFalse(Jornada.emReduzir(montada()))
    }

    @Test
    fun semanaBoaCom5DiasLancadosE1DeslizePerdoado() {
        // Etapa começou numa segunda, 02/11; a semana 1 vai até domingo, 08/11.
        val p = progresso(periodo("2026-11-02", "2026-11-06", comCoca = setOf("2026-11-04")), hoje = "2026-11-09")
        val semana1 = p.ultimaCompleta!!
        assertEquals(1, semana1.numero)
        assertEquals(EstadoSemana.BOA, semana1.estado)
        assertEquals(5, semana1.diasComDado)
        assertEquals(1, semana1.deslizes)
        assertEquals(1, p.seguidas)
        assertNull(p.vencidaEm)
        assertEquals(2, p.atual.numero)
    }

    @Test
    fun doisDeslizesNaSemanaZeramAsSeguidas() {
        val r = periodo("2026-11-02", "2026-11-15", comCoca = setOf("2026-11-09", "2026-11-10"))
        val p = progresso(r, hoje = "2026-11-16")
        assertEquals(EstadoSemana.RUIM, p.ultimaCompleta!!.estado)
        assertEquals(0, p.seguidas)
        assertNull(p.vencidaEm)
    }

    @Test
    fun semanaComMenosDe5DiasLancadosSoEspera() {
        // Semana 1 boa, semana 2 com 4 dias lançados (sem dado), semana 3 boa: vence no fim da 3.
        val r = periodo("2026-11-02", "2026-11-06") + periodo("2026-11-09", "2026-11-12") + periodo("2026-11-16", "2026-11-20")
        val p = progresso(r, hoje = "2026-11-23")
        assertEquals(EstadoSemana.SEM_DADO, progresso(r, hoje = "2026-11-16").ultimaCompleta!!.estado)
        assertEquals(2, p.seguidas)
        assertEquals("2026-11-22", p.vencidaEm)
    }

    @Test
    fun duasSemanasBoasSeguidasVencemAEtapa() {
        val p = progresso(periodo("2026-11-02", "2026-11-15"), hoje = "2026-11-16")
        assertEquals(2, p.seguidas)
        assertEquals("2026-11-15", p.vencidaEm)
    }

    @Test
    fun soSemanaCompletaConta() {
        // No último dia da semana 2 ela ainda está em curso: a vitória só aparece no dia seguinte.
        val r = periodo("2026-11-02", "2026-11-15")
        val noDomingo = progresso(r, hoje = "2026-11-15")
        assertEquals(EstadoSemana.EM_CURSO, noDomingo.atual.estado)
        assertEquals(1, noDomingo.seguidas)
        assertNull(noDomingo.vencidaEm)
        assertEquals("2026-11-15", progresso(r, hoje = "2026-11-16").vencidaEm)
    }

    @Test
    fun semanaRuimNoMeioRecomecaAContagem() {
        val r = periodo("2026-11-02", "2026-11-22", comCoca = setOf("2026-11-10", "2026-11-12"))
        val p = progresso(r, hoje = "2026-11-23")
        assertEquals(1, p.seguidas)
        assertNull(p.vencidaEm)
    }

    @Test
    fun toqueValeMaisQueODiario() {
        // 02 e 03 com Coca pelo diário: 2 deslizes. O toque no dia 02 diz que foi isotônico no treino.
        val r = periodo("2026-11-02", "2026-11-06", comCoca = setOf("2026-11-02", "2026-11-03"))
        assertEquals(EstadoSemana.RUIM, progresso(r, hoje = "2026-11-09").ultimaCompleta!!.estado)
        val corrigido = progresso(r, hoje = "2026-11-09", toques = mapOf("2026-11-02" to EstadoDia.NA_META))
        assertEquals(EstadoSemana.BOA, corrigido.ultimaCompleta!!.estado)
        val dia2 = corrigido.ultimaCompleta!!.dias[0]
        assertEquals(EstadoDia.NA_META, dia2.estado)
        assertEquals(EstadoDia.DESLIZE, dia2.peloDiario)
        assertTrue(dia2.tocado)
        // Toque num dia sem nada lançado faz o dia contar: 4 dias lançados + 1 tocado = semana boa.
        val quatro = periodo("2026-11-02", "2026-11-05")
        assertEquals(EstadoSemana.SEM_DADO, progresso(quatro, hoje = "2026-11-09").ultimaCompleta!!.estado)
        val comToque = progresso(quatro, hoje = "2026-11-09", toques = mapOf("2026-11-07" to EstadoDia.NA_META))
        assertEquals(EstadoSemana.BOA, comToque.ultimaCompleta!!.estado)
    }

    @Test
    fun lancarEmDiaPassadoMudaAConta() {
        // Nada é contador gravado: a semana 2 tinha 4 dias; lançar o dia 13 depois faz ela boa.
        val antes = periodo("2026-11-02", "2026-11-12")
        assertNull(progresso(antes, hoje = "2026-11-16").vencidaEm)
        assertEquals("2026-11-15", progresso(antes + lancado("2026-11-13"), hoje = "2026-11-16").vencidaEm)
    }

    @Test
    fun modoSeguroNaoContaSemanas() {
        val p = progresso(periodo("2026-11-02", "2026-11-15"), hoje = "2026-11-16", seguro = true)
        assertEquals(0, p.seguidas)
        assertNull(p.vencidaEm)
        assertEquals(EstadoSemana.BOA, p.ultimaCompleta!!.estado)
    }

    @Test
    fun semanaAtualTemSeteDiasEOsFuturosNaoContam() {
        val p = progresso(
            periodo("2026-11-02", "2026-11-04"),
            hoje = "2026-11-04",
            toques = mapOf("2026-11-06" to EstadoDia.DESLIZE),
        )
        assertEquals(7, p.atual.dias.size)
        assertEquals("2026-11-02", p.atual.dias.first().dia)
        assertEquals("2026-11-08", p.atual.dias.last().dia)
        assertEquals(listOf(false, false, false, true, true, true, true), p.atual.dias.map { it.futuro })
        assertEquals(EstadoDia.SEM_DADO, p.atual.dias[4].estado)
        assertEquals("2026-11-04", p.hoje.dia)
        assertEquals(EstadoDia.NA_META, p.hoje.estado)
        assertNull(p.ultimaCompleta)
        assertEquals(0, p.seguidas)
    }

    @Test
    fun relogioAntesDoInicioFicaNoPrimeiroDia() {
        val p = progresso(emptyList(), hoje = "2026-11-01")
        assertEquals(1, p.atual.numero)
        assertEquals("2026-11-02", p.hoje.dia)
    }

    @Test
    fun semanaComPoucosDiasEDoisDeslizesEhRuim() {
        // Semana 2 com 4 dias lançados e 2 com Coca: 2 deslizes já passam do perdão, então ela é
        // ruim mesmo sem os 5 dias e zera a semana 1. A semana 3, boa, conta 1 de novo.
        val r = periodo("2026-11-02", "2026-11-06") +
            periodo("2026-11-09", "2026-11-12", comCoca = setOf("2026-11-09", "2026-11-10")) +
            periodo("2026-11-16", "2026-11-20")
        val naSemana3 = progresso(r, hoje = "2026-11-16")
        assertEquals(EstadoSemana.RUIM, naSemana3.ultimaCompleta!!.estado)
        assertEquals(4, naSemana3.ultimaCompleta!!.diasComDado)
        assertEquals(0, naSemana3.seguidas)
        assertEquals(
            "Semana passada: 2 dias com bebida com açúcar; a contagem recomeça.",
            Jornada.textoSemanaPassada(naSemana3.ultimaCompleta!!, Habito.BEBIDA_ACUCAR),
        )
        val p = progresso(r, hoje = "2026-11-23")
        assertEquals(1, p.seguidas)
        assertNull(p.vencidaEm)
        // Só 2 dias lançados, os 2 com Coca: ruim também.
        val doisDias = periodo("2026-11-02", "2026-11-03", comCoca = setOf("2026-11-02", "2026-11-03"))
        assertEquals(EstadoSemana.RUIM, progresso(doisDias, hoje = "2026-11-09").ultimaCompleta!!.estado)
        // 2 deslizes tocados em dias sem nada lançado contam do mesmo jeito.
        val tocados = mapOf("2026-11-07" to EstadoDia.DESLIZE, "2026-11-08" to EstadoDia.DESLIZE)
        assertEquals(EstadoSemana.RUIM, progresso(emptyList(), hoje = "2026-11-09", toques = tocados).ultimaCompleta!!.estado)
        // Com 1 deslize só, a semana de 4 dias continua sem dado: só espera.
        val umDeslize = periodo("2026-11-02", "2026-11-05", comCoca = setOf("2026-11-02"))
        assertEquals(EstadoSemana.SEM_DADO, progresso(umDeslize, hoje = "2026-11-09").ultimaCompleta!!.estado)
    }

    @Test
    fun naSegundaOToqueCorrigeODomingoDaSemanaPassada() {
        // Semana 1 com Coca na quinta e no domingo: ruim. Na segunda, 09/11, o toque no domingo
        // (foi isotônico no treino) faz a semana que fechou ficar boa, e a contagem anda.
        val r = periodo("2026-11-02", "2026-11-08", comCoca = setOf("2026-11-05", "2026-11-08"))
        assertEquals(EstadoSemana.RUIM, progresso(r, hoje = "2026-11-09").ultimaCompleta!!.estado)
        val salva = Jornada.comToque(montada(), "2026-11-08", Habito.BEBIDA_ACUCAR, EstadoDia.NA_META)
        val p = progresso(r, hoje = "2026-11-09", toques = Jornada.toquesDo(salva, Habito.BEBIDA_ACUCAR))
        assertEquals(EstadoSemana.BOA, p.ultimaCompleta!!.estado)
        assertTrue(p.ultimaCompleta!!.dias.last().tocado)
        assertEquals(1, p.seguidas)
        // A semana que fechou não tem dia futuro: os 7 aceitam o toque.
        assertTrue(p.ultimaCompleta!!.dias.none { it.futuro })
    }

    @Test
    fun semanaRuimDepoisDaVitoriaNaoApagaAVitoria() {
        val p = progresso(periodo("2026-11-02", "2026-11-22", comCoca = setOf("2026-11-16", "2026-11-17")), hoje = "2026-11-23")
        assertEquals(2, p.seguidas)
        assertEquals("2026-11-15", p.vencidaEm)
    }

    @Test
    fun toqueSemDadoOuForaDaEtapaNaoMudaNada() {
        val r = periodo("2026-11-02", "2026-11-05")
        assertEquals(4, progresso(r, hoje = "2026-11-09", toques = mapOf("2026-11-03" to EstadoDia.SEM_DADO)).ultimaCompleta!!.diasComDado)
        assertEquals(
            EstadoSemana.SEM_DADO,
            progresso(r, hoje = "2026-11-09", toques = mapOf("2026-11-01" to EstadoDia.NA_META)).ultimaCompleta!!.estado,
        )
        // O toque de deslize num dia que o diário diz limpo também vale mais.
        val comUmaCoca = periodo("2026-11-02", "2026-11-06", comCoca = setOf("2026-11-02"))
        assertEquals(
            EstadoSemana.RUIM,
            progresso(comUmaCoca, hoje = "2026-11-09", toques = mapOf("2026-11-03" to EstadoDia.DESLIZE)).ultimaCompleta!!.estado,
        )
    }

    private fun montada(escolhas: Map<Habito, Escolha> = Jornada.escolhasIniciais(false) + (Habito.CIGARRO to Escolha.NAO_E_MEU_CASO)) =
        Jornada.montar(JornadaSalva(ligada = true), seguro = false, escolhas = escolhas, outros = emptyList(), hoje = "2026-11-02")

    @Test
    fun montarComecaAEtapaDaBebidaHoje() {
        val j = montada()
        assertTrue(j.montada)
        assertEquals(Habito.BEBIDA_ACUCAR, j.etapa)
        assertEquals("2026-11-02", j.inicioEtapa)
        assertFalse(j.aguardando)
        assertEquals(MomentoDaJornada.EM_CURSO, Jornada.momento(j))
        assertEquals(Escolha.ELIMINAR, j.escolhas[Habito.EMBUTIDOS])
        assertEquals(Escolha.NAO_E_MEU_CASO, j.escolhas[Habito.DOCES])
        assertEquals(MomentoDaJornada.MONTAR, Jornada.momento(JornadaSalva(ligada = true)))
    }

    @Test
    fun jaNaoFacoComecaComoMantidoSemEtapa() {
        val e = Jornada.escolhasIniciais(false) + mapOf(
            Habito.BEBIDA_ACUCAR to Escolha.JA_NAO_FACO,
            Habito.ALCOOL to Escolha.JA_NAO_FACO,
            Habito.CIGARRO to Escolha.NAO_E_MEU_CASO,
        )
        val j = montada(e)
        assertNull(j.etapa)
        assertNull(j.inicioEtapa)
        assertEquals(MomentoDaJornada.SEM_ETAPA, Jornada.momento(j))
        assertEquals(listOf(Habito.BEBIDA_ACUCAR, Habito.ALCOOL), Jornada.mantidos(j))
    }

    @Test
    fun montarDeNovoGuardaAEtapaEOsToques() {
        val j = Jornada.comToque(montada(), "2026-11-03", Habito.BEBIDA_ACUCAR, EstadoDia.NA_META)
        val de = Jornada.montar(j, false, j.escolhas + (Habito.DOCES to Escolha.REDUZIR), emptyList(), "2026-11-10")
        assertEquals("2026-11-02", de.inicioEtapa)
        assertEquals(j.toques, de.toques)
        assertEquals(Escolha.REDUZIR, de.escolhas[Habito.DOCES])
        // Trocar a bebida para "Já não faço" encerra a etapa e apaga os toques; a vitória fica.
        val vencida = Jornada.comVitoria(j, Habito.BEBIDA_ACUCAR, "2026-11-15")
        val semBebida = Jornada.montar(vencida, false, vencida.escolhas + (Habito.BEBIDA_ACUCAR to Escolha.JA_NAO_FACO), emptyList(), "2026-11-20")
        assertNull(semBebida.etapa)
        assertTrue(semBebida.toques.isEmpty())
        assertFalse(semBebida.aguardando)
        assertEquals(listOf(Vencida(Habito.BEBIDA_ACUCAR, "2026-11-15")), semBebida.vencidas)
        // Voltar a eliminar uma etapa já vencida não conta de novo: fica esperando a próxima.
        val deVolta = Jornada.montar(semBebida, false, semBebida.escolhas + (Habito.BEBIDA_ACUCAR to Escolha.ELIMINAR), emptyList(), "2026-11-21")
        assertTrue(deVolta.aguardando)
        assertEquals(MomentoDaJornada.VENCIDA, Jornada.momento(deVolta))
    }

    @Test
    fun mudarARespostaDeSaudeRecomecaAEtapa() {
        // As semanas do modo seguro não foram contadas e tinham outra meta: trocar a resposta recomeça a etapa.
        val e = Jornada.escolhasIniciais(true) + (Habito.CIGARRO to Escolha.NAO_E_MEU_CASO)
        val seguro = Jornada.comToque(
            Jornada.montar(JornadaSalva(ligada = true), true, e, emptyList(), "2026-11-02"),
            "2026-11-03", Habito.BEBIDA_ACUCAR, EstadoDia.NA_META,
        )
        val semModoSeguro = Jornada.montar(seguro, false, e, emptyList(), "2026-11-16")
        assertEquals("2026-11-16", semModoSeguro.inicioEtapa)
        assertTrue(semModoSeguro.toques.isEmpty())
        assertFalse(Jornada.continuaAEtapa(seguro, Habito.BEBIDA_ACUCAR, seguro = false))
        // Com a mesma resposta, a etapa continua de onde estava.
        val deNovo = Jornada.montar(seguro, true, e, emptyList(), "2026-11-16")
        assertEquals("2026-11-02", deNovo.inicioEtapa)
        assertEquals(seguro.toques, deNovo.toques)
        assertTrue(Jornada.continuaAEtapa(seguro, Habito.BEBIDA_ACUCAR, seguro = true))
    }

    @Test
    fun montarNaoGuardaOutroHabitoQueNaoPodeSerEliminado() {
        val outros = listOf(
            OutroHabito("Arroz", Escolha.ELIMINAR),
            OutroHabito("Pipoca", Escolha.ELIMINAR),
            OutroHabito("Pão", Escolha.REDUZIR),
        )
        val j = Jornada.montar(JornadaSalva(ligada = true), false, montada().escolhas, outros, "2026-11-02")
        assertEquals(listOf(OutroHabito("Pipoca", Escolha.ELIMINAR), OutroHabito("Pão", Escolha.REDUZIR)), j.outros)
        assertFalse("Arroz" in Jornada.evitarNaSugestao(j))
        assertTrue("Pipoca" in Jornada.evitarNaSugestao(j))
    }

    @Test
    fun obrigatorioSemCadeadoSoNoModoSeguro() {
        assertEquals(listOf(Escolha.ELIMINAR, Escolha.JA_NAO_FACO), Jornada.opcoes(Habito.BEBIDA_ACUCAR, seguro = false))
        assertEquals(listOf(Escolha.ELIMINAR, Escolha.REDUZIR, Escolha.JA_NAO_FACO), Jornada.opcoes(Habito.BEBIDA_ACUCAR, seguro = true))
        assertEquals(listOf(Escolha.ELIMINAR, Escolha.NAO_E_MEU_CASO), Jornada.opcoes(Habito.CIGARRO, seguro = true))
        assertEquals(Escolha.ELIMINAR, Jornada.escolhaInicial(Habito.ALCOOL, seguro = false))
        // No modo seguro o álcool continua em Eliminar (álcool zero na gravidez e no fígado; com
        // insulina ou remédio que baixa o açúcar, risco de hipoglicemia), mas sem cadeado: dá para
        // trocar para Reduzir.
        assertEquals(Escolha.ELIMINAR, Jornada.escolhaInicial(Habito.ALCOOL, seguro = true))
        assertTrue(Escolha.REDUZIR in Jornada.opcoes(Habito.ALCOOL, seguro = true))
        assertEquals(Escolha.REDUZIR, Jornada.escolhaInicial(Habito.BEBIDA_ACUCAR, seguro = true))
        assertEquals(Escolha.REDUZIR, Jornada.escolhaInicial(Habito.EMBUTIDOS, seguro = true))
        assertNull(Jornada.escolhaInicial(Habito.CIGARRO, seguro = false))
        // Reduzir num obrigatório fora do modo seguro vira Eliminar.
        val v = Jornada.escolhasValidas(mapOf(Habito.BEBIDA_ACUCAR to Escolha.REDUZIR), seguro = false)
        assertEquals(Escolha.ELIMINAR, v[Habito.BEBIDA_ACUCAR])
        assertNull(v[Habito.CIGARRO])
        // Mudar a resposta de saúde: os obrigatórios voltam à marcação do modo; "Já não faço" fica.
        val s = Jornada.aoMudarSaude(
            mapOf(Habito.BEBIDA_ACUCAR to Escolha.ELIMINAR, Habito.ALCOOL to Escolha.JA_NAO_FACO, Habito.DOCES to Escolha.ELIMINAR),
            seguro = true,
        )
        assertEquals(Escolha.REDUZIR, s[Habito.BEBIDA_ACUCAR])
        assertEquals(Escolha.JA_NAO_FACO, s[Habito.ALCOOL])
        assertEquals(Escolha.REDUZIR, s[Habito.EMBUTIDOS])
        assertEquals(Escolha.ELIMINAR, s[Habito.DOCES])
        // O sim na saúde leva o álcool para Eliminar, e o não volta tudo ao cadeado.
        val sim = Jornada.aoMudarSaude(Jornada.escolhasIniciais(false) + (Habito.ALCOOL to Escolha.ELIMINAR), seguro = true)
        assertEquals(Escolha.ELIMINAR, sim[Habito.ALCOOL])
        val nao = Jornada.aoMudarSaude(sim + (Habito.ALCOOL to Escolha.REDUZIR), seguro = false)
        assertEquals(listOf(Escolha.ELIMINAR, Escolha.ELIMINAR, Escolha.ELIMINAR),
            listOf(nao[Habito.BEBIDA_ACUCAR], nao[Habito.EMBUTIDOS], nao[Habito.ALCOOL]))
        val seguro = Jornada.montar(JornadaSalva(ligada = true), true, Jornada.escolhasIniciais(true), emptyList(), "2026-11-02")
        assertEquals(Escolha.REDUZIR, seguro.escolhas[Habito.BEBIDA_ACUCAR])
        assertEquals(Habito.BEBIDA_ACUCAR, seguro.etapa)
        assertTrue(seguro.seguro)
    }

    @Test
    fun rotulosDosBotoes() {
        assertEquals("Vou eliminar", Jornada.rotulo(Habito.BEBIDA_ACUCAR, Escolha.ELIMINAR, seguro = false))
        assertEquals("Eliminar", Jornada.rotulo(Habito.BEBIDA_ACUCAR, Escolha.ELIMINAR, seguro = true))
        assertEquals("Já não faço", Jornada.rotulo(Habito.ALCOOL, Escolha.JA_NAO_FACO, seguro = false))
        assertEquals("Vou parar", Jornada.rotulo(Habito.CIGARRO, Escolha.ELIMINAR, seguro = false))
        assertEquals("Não é meu caso", Jornada.rotulo(Habito.CIGARRO, Escolha.NAO_E_MEU_CASO, seguro = false))
        assertEquals("Reduzir", Jornada.rotulo(Habito.DOCES, Escolha.REDUZIR, seguro = false))
    }

    @Test
    fun faltaResponderSaudeECigarro() {
        val semCigarro = Jornada.escolhasIniciais(false)
        val comCigarro = semCigarro + (Habito.CIGARRO to Escolha.ELIMINAR)
        assertEquals("Responda à pergunta de saúde e à do cigarro para começar.", Jornada.faltaResponder(null, semCigarro))
        assertEquals("Responda à pergunta de saúde para começar.", Jornada.faltaResponder(null, comCigarro))
        assertEquals("Responda à pergunta do cigarro para começar.", Jornada.faltaResponder(false, semCigarro))
        assertNull(Jornada.faltaResponder(true, comCigarro))
    }

    @Test
    fun toqueTrocaEApagaACorrecaoDoDia() {
        var j = montada()
        j = Jornada.comToque(j, "2026-11-03", Habito.BEBIDA_ACUCAR, EstadoDia.DESLIZE)
        j = Jornada.comToque(j, "2026-11-03", Habito.BEBIDA_ACUCAR, EstadoDia.NA_META)
        assertEquals(mapOf("2026-11-03" to EstadoDia.NA_META), Jornada.toquesDo(j, Habito.BEBIDA_ACUCAR))
        j = Jornada.comToque(j, "2026-11-03", Habito.BEBIDA_ACUCAR, null)
        assertTrue(j.toques.isEmpty())
        j = Jornada.comToque(j, "2026-11-04", Habito.BEBIDA_ACUCAR, EstadoDia.SEM_DADO)
        assertTrue(j.toques.isEmpty())
    }

    @Test
    fun vitoriaGravadaUmaVezSoEEsperaComecarAProxima() {
        val j = Jornada.comVitoria(montada(), Habito.BEBIDA_ACUCAR, "2026-11-15")
        assertTrue(j.aguardando)
        assertEquals(MomentoDaJornada.VENCIDA, Jornada.momento(j))
        assertEquals(j, Jornada.comVitoria(j, Habito.BEBIDA_ACUCAR, "2026-11-20"))
        assertEquals(listOf(Habito.BEBIDA_ACUCAR), Jornada.mantidos(j))
        // Só a etapa em curso vence.
        assertEquals(montada(), Jornada.comVitoria(montada(), Habito.ALCOOL, "2026-11-15"))
    }

    @Test
    fun sugerirRefeicaoEvitaOEliminado() {
        val e = Jornada.escolhasIniciais(false) + mapOf(
            Habito.ALCOOL to Escolha.JA_NAO_FACO,
            Habito.CIGARRO to Escolha.ELIMINAR,
            Habito.DOCES to Escolha.ELIMINAR,
            Habito.FRITURA to Escolha.REDUZIR,
            Habito.BELISCAR to Escolha.ELIMINAR,
        )
        val outros = listOf(OutroHabito("Pão de queijo", Escolha.ELIMINAR), OutroHabito("Pipoca", Escolha.REDUZIR))
        val j = Jornada.montar(JornadaSalva(ligada = true), false, e, outros, "2026-11-02")
        assertEquals(
            listOf(Habito.BEBIDA_ACUCAR.paraIa, Habito.EMBUTIDOS.paraIa, Habito.ALCOOL.paraIa, Habito.DOCES.paraIa, "Pão de queijo"),
            Jornada.evitarNaSugestao(j),
        )
        assertTrue(Jornada.evitarNaSugestao(j.copy(ligada = false)).isEmpty())
        assertTrue(Jornada.evitarNaSugestao(JornadaSalva(ligada = true)).isEmpty())
        // No modo seguro, a bebida com açúcar e os embutidos em Reduzir não vão para o pedido; o
        // álcool, que começa em Eliminar, vai.
        val seguro = Jornada.montar(JornadaSalva(ligada = true), true, Jornada.escolhasIniciais(true), emptyList(), "2026-11-02")
        assertEquals(listOf(Habito.ALCOOL.paraIa), Jornada.evitarNaSugestao(seguro))
        val tudoReduzir = Jornada.montar(
            JornadaSalva(ligada = true), true, Jornada.escolhasIniciais(true) + (Habito.ALCOOL to Escolha.REDUZIR),
            emptyList(), "2026-11-02",
        )
        assertTrue(Jornada.evitarNaSugestao(tudoReduzir).isEmpty())
    }

    @Test
    fun pedidoDaSugestaoSemEliminadosNaoTemALinhaDoEvitar() {
        val alvo = AlvoRefeicao("almoço", 700)
        val semEvitar = """Você é um nutricionista experiente.
A pessoa tem cerca de 700 kcal para o almoço, com o objetivo de garantir o déficit calórico para emagrecer.
Até agora, hoje, ela consumiu: proteínas 30 g.
Atue de forma compensatória: se notar excesso de gordura, sugira opções magras; se faltar proteína,
fibras ou vitamina C, priorize alimentos ricos nelas.

Sugira 3 opções de refeição saudáveis, práticas e do dia a dia no Brasil, cuja soma fique perto de 700 kcal.
Para cada uma, dê as porções em gramas ou medidas caseiras, o total de calorias e uma frase de por que ela
equilibra o dia. Seja encorajador.
Responda em texto simples, sem markdown e sem asteriscos, com linhas curtas e alguns emojis."""
        assertEquals(semEvitar, Gemini.textoSugestao(alvo, Objetivo.PERDER, "proteínas 30 g"))
        val novo = Gemini.textoSugestao(alvo, Objetivo.PERDER, "proteínas 30 g", listOf("bebida alcoólica", "Pão \"de\" queijo"))
        assertTrue(novo.contains("A pessoa tirou da alimentação: bebida alcoólica; Pão 'de' queijo. Não sugira nada disso"))
        assertTrue(novo.contains("nelas.\nA pessoa tirou"))
    }

    @Test
    fun outroHabitoNuncaEliminaRefeicaoGrupoSalOuAgua() {
        assertEquals(Bloqueio.REFEICAO, Jornada.bloqueioDeEliminar("jantar"))
        assertEquals(Bloqueio.REFEICAO, Jornada.bloqueioDeEliminar("Pular o café da manhã"))
        assertEquals(Bloqueio.REFEICAO, Jornada.bloqueioDeEliminar("lanche da tarde"))
        assertNull(Jornada.bloqueioDeEliminar("comer depois do jantar"))
        assertNull(Jornada.bloqueioDeEliminar("lanche de rua"))
        assertEquals(Bloqueio.GRUPO, Jornada.bloqueioDeEliminar("Pão"))
        assertEquals(Bloqueio.GRUPO, Jornada.bloqueioDeEliminar("sem arroz"))
        assertEquals(Bloqueio.GRUPO, Jornada.bloqueioDeEliminar("Frutas"))
        assertEquals(Bloqueio.GRUPO, Jornada.bloqueioDeEliminar("glúten"))
        assertEquals(Bloqueio.GRUPO, Jornada.bloqueioDeEliminar("leite"))
        assertNull(Jornada.bloqueioDeEliminar("pão de queijo"))
        assertEquals(Bloqueio.SAL, Jornada.bloqueioDeEliminar("sal"))
        assertEquals(Bloqueio.SAL, Jornada.bloqueioDeEliminar("Sal grosso"))
        assertNull(Jornada.bloqueioDeEliminar("Bolacha água e sal"))
        assertNull(Jornada.bloqueioDeEliminar("salgadinho"))
        assertEquals(Bloqueio.AGUA, Jornada.bloqueioDeEliminar("água"))
        assertEquals(Bloqueio.AGUA, Jornada.bloqueioDeEliminar("Água com gás"))
        assertNull(Jornada.bloqueioDeEliminar("água de coco"))
        assertNull(Jornada.bloqueioDeEliminar("Água tônica"))
        assertNull(Jornada.bloqueioDeEliminar("chocolate"))
        assertNull(Jornada.bloqueioDeEliminar(""))
    }

    @Test
    fun outroHabitoBarraOCorteEscritoDoJeitoComum() {
        listOf(
            "arroz e feijão", "cortar o pão", "tirar o arroz", "parar de comer pão", "evitar arroz", "nada de pão",
            "não comer arroz", "todo tipo de pão", "qualquer fruta", "carboidratos em geral", "leite e derivados",
        ).forEach { assertEquals(it, Bloqueio.GRUPO, Jornada.bloqueioDeEliminar(it)) }
        listOf("cortar o sal", "tirar o sal", "usar sal").forEach { assertEquals(it, Bloqueio.SAL, Jornada.bloqueioDeEliminar(it)) }
        assertEquals(Bloqueio.AGUA, Jornada.bloqueioDeEliminar("cortar a água"))
        listOf("deixar de jantar", "ficar sem jantar", "o jantar todo dia", "lanche da manhã").forEach {
            assertEquals(it, Bloqueio.REFEICAO, Jornada.bloqueioDeEliminar(it))
        }
        listOf(
            "pão de queijo", "comer depois do jantar", "lanche de rua", "Bolacha água e sal", "água de coco",
            "zero açúcar", "chocolate", "parar de tomar refri", "nada de doce",
        ).forEach { assertNull(it, Jornada.bloqueioDeEliminar(it)) }
    }

    @Test
    fun primeiraParadaQuandoAMetaEstaLonge() {
        val p = Jornada.primeiraParada(100.0, 85.0, Objetivo.PERDER)!!
        assertEquals(95.0, p.first, 0.001)
        assertEquals(90.0, p.second, 0.001)
        assertNull(Jornada.primeiraParada(100.0, 90.0, Objetivo.PERDER))
        assertNull(Jornada.primeiraParada(100.0, 92.0, Objetivo.PERDER))
        assertNull(Jornada.primeiraParada(100.0, 85.0, Objetivo.MANTER))
        assertNull(Jornada.primeiraParada(0.0, 85.0, Objetivo.PERDER))
        assertNull(Jornada.primeiraParada(100.0, 0.0, Objetivo.PERDER))
    }

    @Test
    fun avisoDeImcBaixoNaMetaSemTravar() {
        // Com uma casa, para o lado do aviso: o IMC para baixo e o peso mínimo para cima.
        val a = Jornada.avisoImc(55.0, 175.0, idade = 36)!!
        assertEquals(17.9, a.imc, 0.001)
        assertEquals(18.5, a.minimo, 0.0)
        assertEquals(56.7, a.pesoMinimoKg, 0.001)
        assertNull(Jornada.avisoImc(60.0, 175.0, idade = 36))
        val idoso = Jornada.avisoImc(65.0, 175.0, idade = 60)!!
        assertEquals(22.0, idoso.minimo, 0.0)
        assertEquals(67.4, idoso.pesoMinimoKg, 0.001)
        // 54,7 kg em 1,72 m dá IMC 18,49: a tela não pode mostrar "18,5, abaixo de 18,5" nem um mínimo que ainda avisa.
        val quase = Jornada.avisoImc(54.7, 172.0, idade = 36)!!
        assertEquals(18.4, quase.imc, 0.001)
        assertEquals(54.8, quase.pesoMinimoKg, 0.001)
        assertNull(Jornada.avisoImc(54.8, 172.0, idade = 36))
        assertNull(Jornada.avisoImc(65.0, 175.0, idade = 59))
        assertNull(Jornada.avisoImc(60.0, 0.0, idade = 30))
        assertNull(Jornada.avisoImc(0.0, 175.0, idade = 30))
    }

    @Test
    fun avisoDoJejumAcimaDe24Horas() {
        assertNull(avisoDoJejum(16.0))
        assertNull(avisoDoJejum(24.0))
        assertEquals("Jejum acima de 24 h, só com acompanhamento médico.", avisoDoJejum(25.0))
        assertEquals("Jejum acima de 24 h, só com acompanhamento médico.", avisoDoJejum(72.0))
        // Acima de 72 h o Salvar não aceita: o aviso diz o máximo.
        assertEquals("O máximo é 72 h. Jejum acima de 24 h, só com acompanhamento médico.", avisoDoJejum(73.0))
        assertNull(avisoDoJejum(null))
    }

    @Test
    fun cartaoSemVermelhoESemFalhou() {
        val dias = progresso(periodo("2026-11-02", "2026-11-04", comCoca = setOf("2026-11-03")), hoje = "2026-11-05").atual.dias
        val simbolos = dias.map { Jornada.simboloDoDia(it) }
        assertEquals(listOf("✅", "🔸", "✅", "⚪", "·", "·", "·"), simbolos)
        assertEquals("teve bebida com açúcar", Jornada.rotuloDoDia(Habito.BEBIDA_ACUCAR, EstadoDia.DESLIZE))
        // A água do copo e os exercícios não contam: o texto diz "nas refeições".
        assertEquals("nada lançado nas refeições", Jornada.rotuloDoDia(Habito.BEBIDA_ACUCAR, EstadoDia.SEM_DADO))
        // O "·" é o dia que ainda não chegou: a legenda separa por vírgula e explica o ponto.
        assertEquals(
            "✅ sem bebida com açúcar, 🔸 teve, ⚪ nada lançado nas refeições, · ainda não chegou. Toque num dia para corrigir.",
            Jornada.legendaDaSemana(Habito.BEBIDA_ACUCAR),
        )
        assertEquals(listOf("S", "Q", "D"), listOf("2026-11-02", "2026-11-04", "2026-11-08").map { Jornada.letraDoDia(it) })

        // Todos os textos do cartão e da montagem, em todos os momentos: nada de "falhou" nem de ❌.
        val j = montada()
        val seguroReduzir = j.copy(seguro = true, escolhas = j.escolhas + (Habito.BEBIDA_ACUCAR to Escolha.REDUZIR))
        val semCoca = periodo("2026-11-02", "2026-11-15")
        val progressos = listOf(
            progresso(semCoca, hoje = "2026-11-16"),
            progresso(periodo("2026-11-02", "2026-11-16", comCoca = setOf("2026-11-09", "2026-11-10", "2026-11-16")), hoje = "2026-11-16"),
            progresso(periodo("2026-11-02", "2026-11-03", comCoca = setOf("2026-11-02", "2026-11-03")), hoje = "2026-11-03"),
            progresso(periodo("2026-11-02", "2026-11-02"), hoje = "2026-11-09"),
            progresso(emptyList(), hoje = "2026-11-02", toques = mapOf("2026-11-02" to EstadoDia.DESLIZE)),
            progresso(comSuco("2026-11-02", 300.0), hoje = "2026-11-02", seguro = true, reduzir = true),
        )
        val dosProgressos = progressos.flatMap { p ->
            listOf(Jornada.textoHoje(p, seguro = false), Jornada.textoHoje(p, seguro = true)) +
                listOfNotNull(p.ultimaCompleta?.let { Jornada.textoSemanaPassada(it, Habito.BEBIDA_ACUCAR) })
        }
        val daMontagem = listOf(null, Habito.BEBIDA_ACUCAR).flatMap { etapa ->
            listOf(false, true).flatMap { seguro ->
                listOf(false, true).flatMap { continua ->
                    listOf(false, true).map { vencida -> Jornada.textoDaEtapaNaMontagem(etapa, seguro, continua, vencida) }
                }
            }
        }
        val semCigarro = Jornada.escolhasIniciais(false)
        val falta = listOfNotNull(
            Jornada.faltaResponder(null, semCigarro),
            Jornada.faltaResponder(null, semCigarro + (Habito.CIGARRO to Escolha.ELIMINAR)),
            Jornada.faltaResponder(false, semCigarro),
        )
        val dosDias = EstadoDia.entries.flatMap { e ->
            Habito.entries.flatMap { h -> listOf(Jornada.rotuloDoDia(h, e), Jornada.rotuloDoDia(h, e, reduzir = true)) }
        }
        val legendas = Habito.entries.flatMap { h -> listOf(Jornada.legendaDaSemana(h), Jornada.legendaDaSemana(h, reduzir = true)) }
        val textos = Habito.entries.flatMap { listOf(it.titulo, it.oQueConta, it.motivo, it.teto, it.fonte, it.troca) } +
            Bloqueio.entries.map { it.explicacao } +
            listOf(
                Jornada.PERGUNTA_SAUDE, Jornada.TEXTO_MODO_SEGURO, Jornada.TEXTO_MODO_SEGURO_CARTAO,
                Jornada.TEXTO_RESPOSTA_SAUDE, Jornada.TEXTO_SUS, Jornada.TEXTO_PESO_CIGARRO, Jornada.TEXTO_LALLY,
                Jornada.TEXTO_PROXIMA_VERSAO, Jornada.TEXTO_VER_SEMANA_PASSADA, Jornada.textoMeta(j),
                Jornada.textoMeta(j.copy(seguro = true)),
                Jornada.textoMeta(seguroReduzir),
            ) +
            listOfNotNull(Jornada.textoMantidos(Jornada.comVitoria(j, Habito.BEBIDA_ACUCAR, "2026-11-15"))) +
            dosProgressos + daMontagem + falta + dosDias + legendas
        textos.forEach { t -> assertFalse(t, t.contains("falh", ignoreCase = true) || t.contains("❌")) }
    }

    @Test
    fun preferenciasDaJornadaIdaEVolta() {
        val e = Jornada.escolhasIniciais(false) + (Habito.CIGARRO to Escolha.NAO_E_MEU_CASO)
        assertEquals(e, Jornada.escolhasDeTexto(Jornada.escolhasParaTexto(e)))
        val o = listOf(OutroHabito("Pão \"francês\"", Escolha.REDUZIR), OutroHabito("Pipoca", Escolha.ELIMINAR))
        assertEquals(o, Jornada.outrosDeTexto(Jornada.outrosParaTexto(o)))
        val v = listOf(Vencida(Habito.BEBIDA_ACUCAR, "2026-11-15"))
        assertEquals(v, Jornada.vencidasDeTexto(Jornada.vencidasParaTexto(v)))
        val t = listOf(Toque("2026-11-03", Habito.BEBIDA_ACUCAR, EstadoDia.NA_META), Toque("2026-11-04", Habito.BEBIDA_ACUCAR, EstadoDia.DESLIZE))
        assertEquals(t, Jornada.toquesDeTexto(Jornada.toquesParaTexto(t)))
        // Texto vazio, quebrado ou com id que o app não conhece não derruba nada.
        assertTrue(Jornada.escolhasDeTexto(null).isEmpty())
        assertTrue(Jornada.toquesDeTexto("{quebrado").isEmpty())
        assertEquals(
            mapOf(Habito.DOCES to Escolha.REDUZIR),
            Jornada.escolhasDeTexto("""{"doces":"reduzir","habito_novo":"eliminar","alcool":"talvez"}"""),
        )
        assertEquals(
            listOf(Toque("2026-11-03", Habito.BEBIDA_ACUCAR, EstadoDia.DESLIZE)),
            Jornada.toquesDeTexto(
                """[{"dia":"2026-11-03","habito":"bebida_acucar","estado":"deslize"},
                    {"dia":"2026-11-04","habito":"bebida_acucar","estado":"sem_dado"},
                    {"dia":"2026-11-05","habito":"xyz","estado":"na_meta"}]"""
            ),
        )
    }

    @Test
    fun textosDoCartao() {
        val j = montada()
        assertEquals("Meta: nenhum dia com bebida com açúcar. Um deslize por semana é perdoado.", Jornada.textoMeta(j))
        assertEquals("Meta: nenhum dia com bebida com açúcar.", Jornada.textoMeta(j.copy(seguro = true)))
        val reduzir = j.copy(seguro = true, escolhas = j.escolhas + (Habito.BEBIDA_ACUCAR to Escolha.REDUZIR))
        assertEquals("Meta: reduzir. ${Habito.BEBIDA_ACUCAR.teto}", Jornada.textoMeta(reduzir))
        assertEquals(1, Jornada.numeroDaEtapa(j))
        assertEquals(1, Jornada.numeroDaEtapa(Jornada.comVitoria(j, Habito.BEBIDA_ACUCAR, "2026-11-15")))

        val r = periodo("2026-11-02", "2026-11-15", comCoca = setOf("2026-11-09", "2026-11-10", "2026-11-16"))
        val p = progresso(r + lancado("2026-11-16", comCoca = true), hoje = "2026-11-16")
        assertEquals("Semana passada: 2 dias com bebida com açúcar; a contagem recomeça.", Jornada.textoSemanaPassada(p.ultimaCompleta!!, Habito.BEBIDA_ACUCAR))
        assertEquals("Hoje: teve bebida com açúcar 🔸. Um deslize por semana é perdoado.", Jornada.textoHoje(p, seguro = false))
        assertEquals("Hoje: teve bebida com açúcar 🔸", Jornada.textoHoje(p, seguro = true))
        val boa = progresso(periodo("2026-11-02", "2026-11-09"), hoje = "2026-11-09")
        assertEquals("Semana passada: boa.", Jornada.textoSemanaPassada(boa.ultimaCompleta!!, Habito.BEBIDA_ACUCAR))
        assertEquals("Hoje, até agora: sem bebida com açúcar ✅", Jornada.textoHoje(boa, seguro = false))
        val vazia = progresso(periodo("2026-11-02", "2026-11-02"), hoje = "2026-11-09")
        assertEquals("Semana passada: 1 dia lançado; ela não conta nem atrapalha.", Jornada.textoSemanaPassada(vazia.ultimaCompleta!!, Habito.BEBIDA_ACUCAR))
        assertEquals("Hoje: ainda nada lançado nas refeições ⚪", Jornada.textoHoje(vazia, seguro = false))
        val tocado = progresso(emptyList(), hoje = "2026-11-02", toques = mapOf("2026-11-02" to EstadoDia.NA_META))
        assertEquals("Hoje: sem bebida com açúcar ✅ (corrigido por você)", Jornada.textoHoje(tocado, seguro = false))
        val doisHoje = progresso(periodo("2026-11-02", "2026-11-03", comCoca = setOf("2026-11-02", "2026-11-03")), hoje = "2026-11-03")
        assertEquals("Hoje: teve bebida com açúcar 🔸. A próxima semana é uma nova chance.", Jornada.textoHoje(doisHoje, seguro = false))

        assertNull(Jornada.textoMantidos(j))
        val comMantidos = Jornada.comVitoria(j.copy(escolhas = j.escolhas + (Habito.ALCOOL to Escolha.JA_NAO_FACO)), Habito.BEBIDA_ACUCAR, "2026-11-15")
        assertEquals("Mantidos: Bebida com açúcar, Bebida alcoólica.", Jornada.textoMantidos(comMantidos))
    }

    @Test
    fun textoDoFimDaMontagem() {
        assertTrue(Jornada.textoDaEtapaNaMontagem(Habito.BEBIDA_ACUCAR, seguro = false, continua = false).startsWith("A Etapa 1 é a bebida com açúcar e começa hoje. Você vence a etapa com 2 semanas boas seguidas"))
        assertEquals("A Etapa 1 é a bebida com açúcar e começa hoje. No modo seguro, você passa de etapa quando quiser.", Jornada.textoDaEtapaNaMontagem(Habito.BEBIDA_ACUCAR, seguro = true, continua = false))
        assertEquals("A etapa em curso continua de onde está.", Jornada.textoDaEtapaNaMontagem(Habito.BEBIDA_ACUCAR, seguro = false, continua = true))
        assertEquals(
            "A bebida com açúcar já está nos mantidos. A próxima etapa chega numa nova versão do app.",
            Jornada.textoDaEtapaNaMontagem(null, seguro = false, continua = false),
        )
        assertTrue(Jornada.continuaAEtapa(montada(), Habito.BEBIDA_ACUCAR))
        assertFalse(Jornada.continuaAEtapa(JornadaSalva(), Habito.BEBIDA_ACUCAR))
        assertFalse(Jornada.continuaAEtapa(montada(), null))
        // Etapa já vencida: montar de novo não a recomeça, e o texto diz isso (logo depois da vitória,
        // ou quando ela passou por "Já não faço" e voltou para "Vou eliminar").
        val vencida = Jornada.comVitoria(montada(), Habito.BEBIDA_ACUCAR, "2026-11-15")
        val textoVencida = "A etapa da bebida com açúcar já foi vencida e está nos mantidos. A próxima etapa chega numa nova versão do app."
        assertTrue(Jornada.jaVencida(vencida, Habito.BEBIDA_ACUCAR))
        assertFalse(Jornada.jaVencida(montada(), Habito.BEBIDA_ACUCAR))
        assertFalse(Jornada.jaVencida(vencida, null))
        assertEquals(textoVencida, Jornada.textoDaEtapaNaMontagem(Habito.BEBIDA_ACUCAR, seguro = false, continua = true, vencida = true))
        val semBebida = Jornada.montar(vencida, false, vencida.escolhas + (Habito.BEBIDA_ACUCAR to Escolha.JA_NAO_FACO), emptyList(), "2026-11-20")
        val deVolta = Jornada.continuaAEtapa(semBebida, Habito.BEBIDA_ACUCAR)
        assertFalse(deVolta)
        assertEquals(
            textoVencida,
            Jornada.textoDaEtapaNaMontagem(Habito.BEBIDA_ACUCAR, false, deVolta, Jornada.jaVencida(semBebida, Habito.BEBIDA_ACUCAR)),
        )
    }

    @Test
    fun perguntaDeSaudeTemOsSeisCasosEMandaSeguirOMedico() {
        val p = Busca.normalizar(Jornada.PERGUNTA_SAUDE)
        listOf("rins", "coracao", "figado", "insulina", "gravida", "transtorno alimentar").forEach { assertTrue(it, it in p) }
        assertTrue(Jornada.TEXTO_MODO_SEGURO.contains("Siga o seu médico"))
        assertTrue(Jornada.TEXTO_MODO_SEGURO_CARTAO.contains("Siga o seu médico"))
    }

    @Test
    fun idsDosHabitosNuncaMudam() {
        assertEquals(
            listOf(
                "bebida_acucar", "embutidos", "alcool", "cigarro", "cafe_cha_adocado", "suco_100", "doces",
                "ultraprocessados", "delivery", "fritura", "carne_vermelha", "beliscar", "depois_jantar", "refri_zero",
                "energetico_cafeina",
            ),
            Habito.entries.map { it.id },
        )
        assertEquals(listOf("eliminar", "reduzir", "ja_nao_faco", "nao_e_meu_caso"), Escolha.entries.map { it.id })
        assertEquals(listOf("na_meta", "deslize", "sem_dado"), EstadoDia.entries.map { it.id })
    }
}
