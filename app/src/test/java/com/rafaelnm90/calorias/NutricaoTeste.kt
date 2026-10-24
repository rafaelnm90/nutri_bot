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
    }

    @Test
    fun comidaComBebidaNoNomeNaoPedeAgua() {
        assertFalse(Bebidas.pedeAgua("Açaí, polpa, com xarope de guaraná e glucose"))
        assertFalse(Bebidas.pedeAgua("Feijão refrito"))
        assertFalse(Bebidas.pedeAgua("Frango ao molho de vinho"))
        assertFalse(Bebidas.pedeAgua("Bolo de cerveja preta"))
        assertFalse(Bebidas.pedeAgua("Costela na cerveja"))
        assertFalse(Bebidas.pedeAgua("Strogonoff com conhaque"))
        assertFalse(Bebidas.pedeAgua("Peixe ao vinho branco"))
        assertFalse(Bebidas.pedeAgua("Camarão na cachaça"))
        assertFalse(Bebidas.pedeAgua("Pão de cerveja"))
        assertFalse(Bebidas.pedeAgua("Bala de guaraná"))
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
}
