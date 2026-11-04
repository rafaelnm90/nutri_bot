package com.rafaelnm90.calorias

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Regras de nutrição do app, sem nada de tela nem de Android: metas de calorias,
 * macros, água e micronutrientes, fases da dieta e check-in semanal, busca de
 * alimentos, leitura das respostas da IA, a decisão dos lembretes e a jornada de
 * hábitos (etapas, semanas e a bebida com açúcar achada no diário). Usado pelas
 * telas, pelos lembretes e pelos testes locais (app/src/test).
 *
 * As regras vêm do nutri_bot do Rafael (DECISOES.md, "Regras do nutri_bot").
 */

/** Micronutrientes, fibra e açúcar de uma quantidade. Zero = não informado. */
data class Micros(
    val fibra: Double = 0.0,      // g
    val acucar: Double = 0.0,     // g
    val sodio: Double = 0.0,      // mg
    val calcio: Double = 0.0,     // mg
    val ferro: Double = 0.0,      // mg
    val zinco: Double = 0.0,      // mg
    val potassio: Double = 0.0,   // mg
    val vitaminaC: Double = 0.0,  // mg
    val vitaminaA: Double = 0.0,  // mcg
) {
    fun vezes(f: Double) = Micros(
        fibra * f, acucar * f, sodio * f, calcio * f, ferro * f,
        zinco * f, potassio * f, vitaminaC * f, vitaminaA * f,
    )

    operator fun plus(o: Micros) = Micros(
        fibra + o.fibra, acucar + o.acucar, sodio + o.sodio, calcio + o.calcio, ferro + o.ferro,
        zinco + o.zinco, potassio + o.potassio, vitaminaC + o.vitaminaC, vitaminaA + o.vitaminaA,
    )

    companion object {
        val ZERO = Micros()
    }
}

/** Um alimento com os valores de 100 g. A quantidade comida fica no Registro. */
data class Alimento(
    val nome: String,
    val kcal100: Double,
    val prot100: Double,
    val carb100: Double,
    val gord100: Double,
    val micros100: Micros = Micros.ZERO,
    /** Bebida alcoólica ou refrigerante: o volume bebido soma na meta de água do dia. */
    val pedeAgua: Boolean = false,
    /** Porção sugerida ao adicionar (ex.: 1 unidade = 50 g); sem ela, 100 g. */
    val porcaoG: Double? = null,
    val porcaoNome: String? = null,
    /** taco, meu (criado à mão), off (Open Food Facts) ou ia (estimado pela IA). */
    val origem: String = ORIGEM_TACO,
    val codigoBarras: String? = null,
    val categoria: String? = null,
) {
    fun kcal(gramas: Double) = kcal100 * gramas / 100
    fun prot(gramas: Double) = prot100 * gramas / 100
    fun carb(gramas: Double) = carb100 * gramas / 100
    fun gord(gramas: Double) = gord100 * gramas / 100
    fun micros(gramas: Double) = micros100.vezes(gramas / 100)
}

const val ORIGEM_TACO = "taco"
const val ORIGEM_MEU = "meu"
const val ORIGEM_OFF = "off"
const val ORIGEM_IA = "ia"

enum class Refeicao(val titulo: String, val emoji: String, private val plural: Boolean = false) {
    CAFE("Café da manhã", "☕"),
    ALMOCO("Almoço", "🍛"),
    JANTAR("Jantar", "🍲"),
    LANCHES("Lanches", "🍎", plural = true),
    ;

    /** "Adicionar ao Jantar", "Adicionar aos Lanches". */
    val adicionarAo get() = "Adicionar ${if (plural) "aos" else "ao"} $titulo"

    /** "O que você comeu no jantar?", "O que você comeu nos lanches?" */
    val oQueComeu get() = "O que você comeu ${if (plural) "nos" else "no"} ${titulo.lowercase()}?"
}

/** O que foi comido: o alimento (por 100 g) e quantos gramas (ou ml, nas bebidas). */
data class Registro(
    val id: Long,
    val dia: String,
    val refeicao: Refeicao,
    val alimento: Alimento,
    val gramas: Double,
) {
    val kcal get() = alimento.kcal(gramas)
    val prot get() = alimento.prot(gramas)
    val carb get() = alimento.carb(gramas)
    val gord get() = alimento.gord(gramas)
    val micros get() = alimento.micros(gramas)

    /** Água a mais que o dia pede por causa desta bebida (1 ml para cada ml bebido). */
    val aguaExtraMl get() = if (alimento.pedeAgua) gramas else 0.0
}

data class Exercicio(val id: Long, val dia: String, val descricao: String, val minutos: Int, val kcal: Int)

data class Macros(val kcal: Double, val prot: Double, val carb: Double, val gord: Double) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, prot + o.prot, carb + o.carb, gord + o.gord)

    companion object {
        val ZERO = Macros(0.0, 0.0, 0.0, 0.0)
    }
}

fun List<Registro>.somar(): Macros =
    fold(Macros.ZERO) { soma, r -> soma + Macros(r.kcal, r.prot, r.carb, r.gord) }

fun List<Registro>.somarMicros(): Micros = fold(Micros.ZERO) { soma, r -> soma + r.micros }

fun List<Registro>.aguaExtraMl(): Int = sumOf { it.aguaExtraMl }.roundToInt()

enum class Sexo(val titulo: String) { MASCULINO("Masculino"), FEMININO("Feminino") }

enum class Atividade(val titulo: String, val fator: Double) {
    SEDENTARIO("Sedentário (quase sem exercício)", 1.2),
    LEVE("Leve (exercício 1 a 3 dias por semana)", 1.375),
    MODERADO("Moderado (3 a 5 dias por semana)", 1.55),
    INTENSO("Intenso (6 ou 7 dias por semana)", 1.725),
}

enum class Objetivo(val titulo: String) {
    PERDER("Emagrecer"),
    MANTER("Manter o peso"),
    GANHAR("Ganhar massa"),
}

/** As três fases do nutri_bot: quanto a meta fica abaixo (emagrecer) ou acima (ganhar) do gasto. */
enum class Fase(val numero: Int, val nome: String, val ajusteKcal: Int, val explicacao: String) {
    ADAPTACAO(1, "Adaptação", 200, "Ótima para começar sem choque e criar o hábito."),
    IDEAL(2, "Ideal", 400, "A recomendada: equilibra o resultado com a saúde e a massa magra."),
    DRASTICA(3, "Drástica", 600, "Uso pontual para quebrar platôs. Por muito tempo, há risco de perder massa muscular."),
    ;

    companion object {
        fun de(numero: Int) = entries.firstOrNull { it.numero == numero } ?: ADAPTACAO
    }
}

/** Tudo o que a pessoa configura em Ajustes. Altura 0 = perfil ainda não preenchido. */
data class Perfil(
    val sexo: Sexo = Sexo.MASCULINO,
    val anoNascimento: Int = 1990,
    val alturaCm: Double = 0.0,
    val pesoKg: Double = 0.0,
    val pesoMetaKg: Double = 0.0,
    val atividade: Atividade = Atividade.LEVE,
    val objetivo: Objetivo = Objetivo.PERDER,
    val fase: Fase = Fase.ADAPTACAO,
    /** Dia em que a fase atual começou (yyyy-MM-dd); o check-in vem 7 dias depois. */
    val inicioFase: String? = null,
    /** Meta digitada à mão; quando existe, vale no lugar da calculada. */
    val metaManualKcal: Int? = null,
    /** Meta de água digitada à mão; sem ela, 35 ml por kg. */
    val metaAguaManualMl: Int? = null,
    val copoMl: Int = 250,
    val metaJejumHoras: Int = 16,
    val lembretes: Boolean = true,
    val chaveGemini: String = "",
) {
    val preenchido get() = alturaCm > 0 && pesoKg > 0

    fun idade(anoAtual: Int) = anoAtual - anoNascimento
}

/** Faixa aceita de um valor do dia: dentro dela, ✅. */
data class Faixa(val min: Double, val max: Double) {
    operator fun contains(valor: Double) = valor >= min && valor <= max
}

/**
 * Sinal das calorias do dia: ✅ na faixa e ❌ acima dela. Abaixo, ⏳ enquanto o
 * dia não acabou (ainda dá tempo de comer) e ❌ nos dias que já passaram.
 */
fun sinalDaFaixa(valor: Double, faixa: Faixa, diaEmCurso: Boolean): String = when {
    valor in faixa -> "✅"
    valor < faixa.min && diaEmCurso -> "⏳"
    else -> "❌"
}

object Calculos {
    private const val META_SEM_PERFIL = 2000
    private const val AGUA_SEM_PERFIL = 2000
    private const val AGUA_ML_POR_KG = 35

    /** Gasto em repouso pela fórmula de Mifflin-St Jeor. */
    fun gastoBasal(p: Perfil, anoAtual: Int): Double {
        val base = 10 * p.pesoKg + 6.25 * p.alturaCm - 5 * p.idade(anoAtual)
        return if (p.sexo == Sexo.MASCULINO) base + 5 else base - 161
    }

    /** Gasto do dia: o de repouso vezes o fator de atividade. */
    fun gastoDiario(p: Perfil, anoAtual: Int) = gastoBasal(p, anoAtual) * p.atividade.fator

    /** Meta de calorias do dia: a manual, ou o gasto do dia com o ajuste da fase. */
    fun metaKcal(p: Perfil, anoAtual: Int): Int {
        p.metaManualKcal?.let { return it }
        if (!p.preenchido) return META_SEM_PERFIL
        val gasto = gastoDiario(p, anoAtual)
        val meta = when (p.objetivo) {
            Objetivo.PERDER -> gasto - p.fase.ajusteKcal
            Objetivo.MANTER -> gasto
            Objetivo.GANHAR -> gasto + p.fase.ajusteKcal
        }
        // Abaixo disso a dieta deixa de ser segura sem acompanhamento.
        val minimo = if (p.sexo == Sexo.MASCULINO) 1500.0 else 1200.0
        return max(meta, minimo).roundToInt()
    }

    /**
     * O que vale é ficar na faixa, não acertar o número: 100 kcal para baixo ou
     * para cima da meta, nunca abaixo de 1.200.
     */
    fun faixaKcal(meta: Int): Faixa {
        val piso = max(1200.0, meta - 100.0)
        // Quando o piso de 1.200 corta a faixa (meta abaixo de 1.300), ela continua
        // com os 200 kcal de largura a partir dele: meta 1.000 dá 1.200 a 1.400.
        return Faixa(piso, max(meta + 100.0, piso + 200.0))
    }

    /** Divisão do nutri_bot: 30% proteína, 40% carboidrato e 30% gordura. */
    fun metaMacros(kcal: Int) = Macros(
        kcal = kcal.toDouble(),
        prot = kcal * 0.30 / 4,
        carb = kcal * 0.40 / 4,
        gord = kcal * 0.30 / 9,
    )

    fun faixaProt(meta: Macros) = Faixa(max(0.0, meta.prot - 15), meta.prot + 15)
    fun faixaCarb(meta: Macros) = Faixa(max(0.0, meta.carb - 20), meta.carb + 20)
    fun faixaGord(meta: Macros) = Faixa(max(0.0, meta.gord - 10), meta.gord + 10)

    /** Meta de água sem as bebidas do dia: a manual, ou 35 ml por kg. */
    fun metaAguaMl(p: Perfil): Int =
        p.metaAguaManualMl ?: if (p.pesoKg > 0) (p.pesoKg * AGUA_ML_POR_KG).roundToInt() else AGUA_SEM_PERFIL
}

enum class TipoMeta { MINIMO, MAXIMO, FAIXA }

/** A meta de um nutriente monitorado no resumo do dia. */
data class MetaNutriente(
    val nome: String,
    val emoji: String,
    val unidade: String,
    val tipo: TipoMeta,
    /** O mínimo (MINIMO e FAIXA) ou o máximo (MAXIMO). */
    val valor: Double,
    /** O teto, só na FAIXA. */
    val teto: Double? = null,
    val ler: (Micros) -> Double,
) {
    fun ok(consumido: Double): Boolean = when (tipo) {
        TipoMeta.MINIMO -> consumido >= valor
        TipoMeta.MAXIMO -> consumido <= valor
        TipoMeta.FAIXA -> consumido >= valor && consumido <= (teto ?: Double.MAX_VALUE)
    }
}

object MetasNutrientes {
    /** As metas do nutri_bot, por idade e sexo (DRIs), na ordem em que aparecem no resumo. */
    fun de(p: Perfil, anoAtual: Int): List<MetaNutriente> {
        val idade = p.idade(anoAtual)
        val mulher = p.sexo == Sexo.FEMININO
        return listOf(
            MetaNutriente("Fibras", "🌾", "g", TipoMeta.MINIMO, 25.0) { it.fibra },
            MetaNutriente("Açúcar", "🍬", "g", TipoMeta.MAXIMO, 50.0) { it.acucar },
            MetaNutriente("Sódio", "🧂", "mg", TipoMeta.MAXIMO, 2000.0) { it.sodio },
            MetaNutriente(
                "Cálcio", "🥛", "mg", TipoMeta.MINIMO,
                if (mulher) (if (idade >= 51) 1200.0 else 1000.0) else (if (idade >= 71) 1200.0 else 1000.0),
            ) { it.calcio },
            MetaNutriente("Ferro", "🧲", "mg", TipoMeta.MINIMO, if (mulher && idade < 51) 18.0 else 8.0) { it.ferro },
            MetaNutriente("Zinco", "🥩", "mg", TipoMeta.MINIMO, if (mulher) 8.0 else 11.0) { it.zinco },
            MetaNutriente("Potássio", "🍌", "mg", TipoMeta.MINIMO, 3500.0) { it.potassio },
            MetaNutriente("Vitamina C", "🍊", "mg", TipoMeta.MINIMO, if (mulher) 75.0 else 90.0) { it.vitaminaC },
            MetaNutriente("Vitamina A", "🥕", "mcg", TipoMeta.FAIXA, if (mulher) 700.0 else 900.0, 3000.0) { it.vitaminaA },
        )
    }
}

/** Como a pessoa se sentiu na semana, no check-in das fases. */
enum class Sensacao(val titulo: String) {
    FOME_FRAQUEZA("😫 Muita fome / Fraqueza"),
    EQUILIBRIO("😐 Fome moderada / Energia OK"),
    ENERGIA_ALTA("😎 Sem fome / Energia alta"),
}

/** O diagnóstico do check-in e as fases que fazem sentido escolher a seguir (a atual sempre entra). */
data class Conselho(val diagnostico: String, val recomendacao: String, val fases: List<Fase>)

object CheckIn {
    const val DIAS = 7L

    /** Já passou uma semana na fase (só para quem quer emagrecer ou ganhar massa). */
    fun pendente(p: Perfil, hoje: LocalDate): Boolean {
        if (p.objetivo == Objetivo.MANTER || p.metaManualKcal != null) return false
        val inicio = p.inicioFase?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        return ChronoUnit.DAYS.between(inicio, hoje) >= DIAS
    }

    fun conselho(sensacao: Sensacao, fase: Fase): Conselho {
        val anterior = Fase.entries.firstOrNull { it.numero == fase.numero - 1 }
        val proxima = Fase.entries.firstOrNull { it.numero == fase.numero + 1 }
        return when (sensacao) {
            Sensacao.FOME_FRAQUEZA -> Conselho(
                "Seu corpo está sinalizando um nível alto de estresse.",
                "O ideal agora é recuar ou manter a fase atual, protegendo os músculos e o metabolismo antes de novas mudanças.",
                listOfNotNull(anterior, fase),
            )
            Sensacao.EQUILIBRIO -> Conselho(
                "Você chegou a um ótimo ponto de equilíbrio.",
                "O mais seguro é manter esta fase para consolidar os resultados.",
                listOfNotNull(fase, proxima),
            )
            Sensacao.ENERGIA_ALTA -> Conselho(
                "Você está com uma excelente reserva de energia.",
                "Com essa disposição, dá para avançar para a próxima fase, se quiser acelerar.",
                listOfNotNull(proxima, fase),
            )
        }
    }
}

/** Quanto a próxima refeição deve ter, para a IA sugerir pratos que cabem no dia. */
data class AlvoRefeicao(val periodo: String, val kcal: Int)

object Sugestao {
    /**
     * A fatia ideal do dia para o horário (café 25%, almoço 35%, lanche 15%,
     * jantar 25%), sem passar do que falta. Depois das 19 h, se faltam até 600
     * kcal, o alvo é fechar o dia. Null se faltam 50 kcal ou menos.
     */
    fun alvo(meta: Int, consumidas: Double, hora: Int): AlvoRefeicao? {
        val falta = meta - consumidas
        if (falta <= 50) return null
        val (periodo, fatia) = when {
            hora < 11 -> "café da manhã" to 0.25
            hora < 15 -> "almoço" to 0.35
            hora < 19 -> "lanche da tarde" to 0.15
            else -> "jantar ou ceia" to 0.25
        }
        var alvo = min(falta, meta * fatia)
        if (hora >= 19 && falta <= 600) alvo = falta
        return AlvoRefeicao(periodo, alvo.roundToInt())
    }
}

object Lembretes {
    const val HORA_INICIO = 8
    const val HORA_FIM = 22

    /** Quanto do dia ativo (8 h às 22 h) já passou, de 0 a 1; null fora dessa janela. */
    fun fracaoDoDia(hora: Int, minuto: Int): Double? {
        if (hora < HORA_INICIO || hora >= HORA_FIM) return null
        return ((hora - HORA_INICIO) + minuto / 60.0) / (HORA_FIM - HORA_INICIO)
    }

    /**
     * Lembra de beber água quando o atraso em relação ao esperado para a hora
     * passa de 600 ml. Espera 2,5 h entre avisos se o atraso passa de 1 L, e 4 h
     * nos outros casos.
     */
    fun aguaAtrasada(metaMl: Int, bebidoMl: Int, fracao: Double, horasDesdeUltimo: Double): Boolean {
        val atraso = metaMl * fracao - bebidoMl
        if (atraso < 600 || bebidoMl >= metaMl) return false
        val espera = if (atraso >= 1000) 2.5 else 4.0
        return horasDesdeUltimo >= espera
    }

    /**
     * Lembra de comer quando o que foi comido está mais de 35% do dia atrás do
     * esperado para a hora. Espera 3 h entre avisos se o atraso passa de 50%, e
     * 5 h nos outros casos. Em jejum, nunca.
     */
    fun comidaAtrasada(
        metaKcal: Int,
        consumidas: Double,
        fracao: Double,
        horasDesdeUltimo: Double,
        emJejum: Boolean,
    ): Boolean {
        if (emJejum || metaKcal <= 0 || consumidas >= metaKcal) return false
        val atraso = fracao - consumidas / metaKcal
        if (atraso <= 0.35) return false
        val espera = if (atraso > 0.50) 3.0 else 5.0
        return horasDesdeUltimo >= espera
    }
}

object Busca {
    private val acentos = Regex("\\p{M}+")
    private val separadores = Regex("[^a-z0-9]+")

    /** Minúsculas e sem acento: "Feijão" acha "feijao" e vice-versa. */
    fun normalizar(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD).replace(acentos, "")

    fun palavras(texto: String) = normalizar(texto).split(separadores).filter { it.isNotBlank() }

    /**
     * Alimentos cujo nome tem todas as palavras digitadas (no começo de alguma
     * palavra do nome). Vêm primeiro os que começam pela primeira palavra
     * digitada e, entre eles, os de nome mais curto (o mais simples).
     */
    fun buscar(alimentos: List<Alimento>, termo: String, limite: Int = 60): List<Alimento> {
        val procuradas = palavras(termo)
        if (procuradas.isEmpty()) return emptyList()
        return alimentos
            .map { it to palavras(it.nome) }
            .filter { (_, doNome) -> procuradas.all { p -> doNome.any { it.startsWith(p) } } }
            .sortedWith(
                compareBy<Pair<Alimento, List<String>>> { (_, doNome) ->
                    if (doNome.firstOrNull()?.startsWith(procuradas.first()) == true) 0 else 1
                }.thenBy { (a, _) -> a.nome.length }
            )
            .map { it.first }
            .distinctBy { normalizar(it.nome) }
            .take(limite)
    }
}

object Bebidas {
    /** Palavras de bebida alcoólica. */
    private val ALCOOL = listOf(
        "cerveja", "chope", "chopp", "vinho", "espumante", "champanhe", "cachaca", "caipirinha",
        "caipiroska", "vodka", "whisky", "uisque", "gin", "rum", "tequila", "licor", "conhaque",
        "sake", "drink", "aguardente", "mojito", "mule", "cuba", "negroni", "aperol", "spritz", "sangria",
        "quentao", "daiquiri",
    )

    /** Palavras de refrigerante (inclusive zero). */
    private val REFRI = listOf("refrigerante", "coca", "pepsi", "fanta", "sprite", "soda", "tonica", "guarana")

    /** Palavras que marcam bebida alcoólica ou refrigerante: as duas somam igual na meta de água. */
    private val PALAVRAS = ALCOOL + REFRI

    /** Comidas que levam bebida no preparo ou no nome: não pedem água. */
    private val PREPAROS = listOf(
        "molho", "vinagre", "xarope", "bolo", "sorvete", "calda", "bicarbonato", "pao", "bala",
    )

    /**
     * "Frango ao vinho", "Costela na cerveja": a bebida depois destas palavras tempera o
     * prato. "com" fica de fora porque é o jeito de escrever drink ("Limonada com vodka").
     */
    private val LIGACOES = setOf("ao", "aos", "a", "as", "na", "nas", "no", "nos", "em")

    /** Outras bebidas: a quantidade aparece em ml. */
    private val OUTRAS_BEBIDAS = listOf(
        "suco", "agua", "cha", "cafe", "leite", "bebida", "refresco", "isotonico", "energetico", "vitamina",
    )

    /** Pelo nome, se é bebida (para mostrar em ml em vez de g). */
    fun ehBebida(nome: String): Boolean {
        val doNome = Busca.palavras(nome)
        if ("po" in doNome || "condensado" in doNome || "cozido" in doNome) return false
        return pedeAgua(nome) || doNome.firstOrNull() in OUTRAS_BEBIDAS
    }

    /** Pelo nome, se a bebida faz o dia pedir mais água (regra do nutri_bot). */
    fun pedeAgua(nome: String): Boolean {
        val doNome = Busca.palavras(nome)
        // "Guaraná em pó", cocada, "xarope de guaraná" e "frango ao molho de vinho" não são bebida.
        if ("po" in doNome || doNome.any { it.startsWith("cocad") || it in PREPAROS }) return false
        // Nome que começa por bebida é bebida inteiro; nos outros, a bebida logo
        // depois de "ao", "na", "no"... é do preparo ("Frango ao vinho").
        val primeira = doNome.firstOrNull() ?: return false
        // "Batida de coco" é o drink; "banana batida com leite" é vitamina.
        if (primeira == "batida") return true
        val comecaPorBebida = ehAlcoolOuRefri(primeira) || primeira in OUTRAS_BEBIDAS
        return doNome.indices.any { i ->
            ehAlcoolOuRefri(doNome[i]) && (comecaPorBebida || i == 0 || doNome[i - 1] !in LIGACOES)
        }
    }

    /**
     * Pelo nome, se tem palavra de bebida alcoólica ("Cerveja sem álcool" também
     * tem). Não olha o preparo: serve para quem já sabe que o item é bebida.
     */
    fun temAlcool(nome: String): Boolean {
        val doNome = Busca.palavras(nome)
        if (doNome.firstOrNull() == "batida") return true
        // "Energy drink" é energético, não drink.
        return doNome.indices.any { i ->
            casa(doNome[i], ALCOOL) && !(doNome[i] == "drink" && doNome.getOrNull(i - 1) == "energy")
        }
    }

    // "refri" só como palavra inteira, para "refrito" não contar.
    private fun ehAlcoolOuRefri(palavra: String) = palavra == "refri" || casa(palavra, PALAVRAS)

    private fun casa(palavra: String, lista: List<String>) =
        lista.any { p -> palavra == p || (p.length >= 5 && palavra.startsWith(p)) }
}

/** Alimento criado à mão com os valores do rótulo (100 g); a regra da água vem do nome. */
fun alimentoFeitoAMao(
    nome: String,
    kcal100: Double,
    prot100: Double,
    carb100: Double,
    gord100: Double,
    porcaoG: Double?,
    codigoBarras: String?,
) = Alimento(
    nome = nome.trim(),
    kcal100 = kcal100,
    prot100 = prot100,
    carb100 = carb100,
    gord100 = gord100,
    pedeAgua = Bebidas.pedeAgua(nome),
    porcaoG = porcaoG,
    origem = ORIGEM_MEU,
    codigoBarras = codigoBarras,
)

/**
 * O dia que o Diário mostra quando o app volta para a frente. Quem estava vendo
 * "hoje" e voltou num dia novo passa para o dia novo; um dia antigo escolhido de
 * propósito fica. Todos no formato yyyy-MM-dd.
 */
fun diaAoVoltar(dia: String, ultimoHoje: String, agora: String): String =
    if (agora != ultimoHoje && dia == ultimoHoje) agora else dia

/**
 * O peso que o perfil passa a usar ao apagar um registro (pesos do mais antigo ao
 * mais novo): o anterior, se o apagado era o mais recente; null se nada muda.
 */
fun pesoDepoisDeApagar(pesos: List<Pair<String, Double>>, apagado: String): Double? {
    if (pesos.lastOrNull()?.first != apagado) return null
    return pesos.dropLast(1).lastOrNull()?.second
}

/**
 * Aviso da meta de jejum em Ajustes: acima de 24 h, só com médico. O máximo é 72 h: acima
 * dele, o Salvar de Ajustes fica com a meta que já estava, e o aviso diz isso.
 */
fun avisoDoJejum(horas: Double?): String? = when {
    horas == null || horas <= 24 -> null
    horas > 72 -> "O máximo é 72 h. Jejum acima de 24 h, só com acompanhamento médico."
    else -> "Jejum acima de 24 h, só com acompanhamento médico."
}

/** Um alimento que a IA achou na foto ou na descrição, com os valores da quantidade estimada. */
data class ItemEstimado(
    val nome: String,
    val gramas: Double,
    val kcal: Double,
    val prot: Double,
    val carb: Double,
    val gord: Double,
    val micros: Micros = Micros.ZERO,
    val aguaExtraMl: Double = 0.0,
) {
    /** Passa para valores de 100 g, para o diário poder mudar a quantidade depois. */
    fun comoAlimento(): Alimento {
        val fator = if (gramas > 0) 100 / gramas else 1.0
        return Alimento(
            nome = nome,
            kcal100 = kcal * fator,
            prot100 = prot * fator,
            carb100 = carb * fator,
            gord100 = gord * fator,
            micros100 = micros.vezes(fator),
            // A IA já aplica a regra da água no item: o nome não passa por cima dela.
            pedeAgua = aguaExtraMl > 0,
            porcaoG = gramas,
            origem = ORIGEM_IA,
        )
    }
}

/** O que a IA devolveu: os itens, ou só uma resposta em texto quando não era comida. */
data class RespostaIa(val itens: List<ItemEstimado>, val resposta: String)

object LeitorIa {
    /** Tira bloco ```json e devolve o primeiro objeto ou lista JSON do texto, ou null. */
    private fun json(texto: String): Any? {
        val limpo = texto.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val inicio = limpo.indexOfFirst { it == '{' || it == '[' }
        if (inicio < 0) return null
        val corpo = limpo.substring(inicio)
        return try {
            if (corpo.startsWith("[")) JSONArray(corpo) else JSONObject(corpo)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Lê a resposta de foto ou descrição:
     * {"e_comida": true, "resposta": "", "itens": [{"nome", "gramas", "kcal", "proteina",
     * "carboidrato", "gordura", "fibra", "acucar", "sodio_mg", ..., "agua_extra_ml"}]}.
     * Aceita a lista solta. Itens sem nome ou sem gramas ficam de fora.
     */
    fun ler(texto: String): RespostaIa {
        val raiz = json(texto) ?: return RespostaIa(emptyList(), "")
        val lista = when (raiz) {
            is JSONArray -> raiz
            is JSONObject -> raiz.optJSONArray("itens") ?: JSONArray()
            else -> JSONArray()
        }
        val resposta = (raiz as? JSONObject)?.optString("resposta").orEmpty().trim()
        val itens = mutableListOf<ItemEstimado>()
        for (i in 0 until lista.length()) {
            val o = lista.optJSONObject(i) ?: continue
            val nome = o.optString("nome").trim()
            val gramas = o.numero("gramas")
            if (nome.isEmpty() || gramas <= 0) continue
            itens += ItemEstimado(
                nome = nome.replaceFirstChar { it.uppercase() },
                gramas = gramas,
                kcal = o.numero("kcal"),
                prot = o.numero("proteina"),
                carb = o.numero("carboidrato"),
                gord = o.numero("gordura"),
                micros = Micros(
                    fibra = o.numero("fibra"),
                    acucar = o.numero("acucar"),
                    sodio = o.numero("sodio_mg"),
                    calcio = o.numero("calcio_mg"),
                    ferro = o.numero("ferro_mg"),
                    zinco = o.numero("zinco_mg"),
                    potassio = o.numero("potassio_mg"),
                    vitaminaC = o.numero("vitamina_c_mg"),
                    vitaminaA = o.numero("vitamina_a_mcg"),
                ),
                aguaExtraMl = o.numero("agua_extra_ml"),
            )
        }
        return RespostaIa(itens, resposta)
    }

    /**
     * Lê a resposta do exercício: {"e_exercicio", "descricao", "minutos", "kcal", "resposta"}.
     * Devolve o exercício (sem id nem dia) ou a resposta em texto quando não era exercício.
     */
    fun lerExercicio(texto: String): Pair<Exercicio?, String> {
        val o = json(texto) as? JSONObject ?: return null to ""
        val resposta = o.optString("resposta").trim()
        val descricao = o.optString("descricao").trim()
        val kcal = o.numero("kcal").roundToInt()
        if (!o.optBoolean("e_exercicio", false) || descricao.isEmpty() || kcal <= 0) return null to resposta
        val minutos = o.numero("minutos").roundToInt().takeIf { it > 0 } ?: 30
        return Exercicio(0, "", descricao.replaceFirstChar { it.uppercase() }, minutos, kcal) to resposta
    }

    private fun JSONObject.numero(chave: String): Double {
        val v = optDouble(chave, 0.0)
        return if (v.isNaN() || v < 0) 0.0 else v
    }
}

object Taco {
    /**
     * Lê o taco.json gerado pelo workflow App Calorias: TACO a partir da tabela oficial:
     * lista de {"nome", "categoria", "kcal", "proteina", "carboidrato", "gordura",
     * "fibra", "sodio", "calcio", "ferro", "zinco", "potassio", "vitamina_c",
     * "vitamina_a"}, valores de 100 g (minerais e vitamina C em mg, vitamina A em mcg).
     */
    fun ler(json: String): List<Alimento> {
        val lista = try {
            JSONArray(json)
        } catch (e: Exception) {
            return emptyList()
        }
        val alimentos = mutableListOf<Alimento>()
        for (i in 0 until lista.length()) {
            val o = lista.optJSONObject(i) ?: continue
            val nome = o.optString("nome").trim()
            if (nome.isEmpty()) continue
            val categoria = o.optString("categoria").ifBlank { null }
            alimentos += Alimento(
                nome = nome,
                kcal100 = o.valor("kcal"),
                prot100 = o.valor("proteina"),
                carb100 = o.valor("carboidrato"),
                gord100 = o.valor("gordura"),
                micros100 = Micros(
                    fibra = o.valor("fibra"),
                    sodio = o.valor("sodio"),
                    calcio = o.valor("calcio"),
                    ferro = o.valor("ferro"),
                    zinco = o.valor("zinco"),
                    potassio = o.valor("potassio"),
                    vitaminaC = o.valor("vitamina_c"),
                    vitaminaA = o.valor("vitamina_a"),
                ),
                pedeAgua = Bebidas.pedeAgua(nome),
                origem = ORIGEM_TACO,
                categoria = categoria,
            )
        }
        return alimentos
    }

    private fun JSONObject.valor(chave: String): Double {
        val v = optDouble(chave, 0.0)
        return if (v.isNaN() || v < 0) 0.0 else v
    }
}

// --- Jornada de hábitos (DECISOES.md, "Jornada de hábitos") ---

/** Onde o hábito aparece na montagem, e com que botões. */
enum class GrupoHabito { OBRIGATORIO, CIGARRO, REDUZIR }

/**
 * Os hábitos da jornada, com os textos da tela. O id fica gravado nas
 * preferências "jornada": nunca mudar um id, só acrescentar hábitos novos.
 */
enum class Habito(
    val id: String,
    val grupo: GrupoHabito,
    val titulo: String,
    /** O que conta como o hábito. */
    val oQueConta: String,
    /** Por que sair; vazio nos de reduzir, que mostram só o teto. */
    val motivo: String,
    /** O teto sugerido para Reduzir (nos obrigatórios, só no modo seguro); vazio no cigarro. */
    val teto: String,
    val fonte: String,
    /** A troca sugerida, em minúsculas (a tela põe "Troca:" na frente); vazio quando não há. */
    val troca: String = "",
    /** Como o pedido de Sugerir refeição chama o hábito; null quando não é comida nem bebida. */
    val paraIa: String? = null,
) {
    BEBIDA_ACUCAR(
        "bebida_acucar", GrupoHabito.OBRIGATORIO, "Bebida com açúcar",
        "Conta: refrigerante comum, néctar e suco de caixinha adoçado, suco e limonada com açúcar (mesmo feitos " +
            "em casa), refresco em pó, chá pronto, achocolatado (pronto ou no leite) e chocolate quente, bebida " +
            "láctea e leite fermentado adoçados, caldo de cana, energético comum e isotônico fora do treino. " +
            "Não conta: água, água de coco, leite puro, refri zero, café e chá adoçados em casa e suco 100% de " +
            "fruta (esses três últimos ficam no Reduzir).",
        "Açúcar líquido não tira a fome e soma calorias sem você perceber: uma lata de 350 ml tem uns 35 g de " +
            "açúcar, quase todo o teto de 50 g do app. Não contam como deslize o isotônico em treino de mais de 1 hora " +
            "ou no calor forte e o açúcar para tratar hipoglicemia de quem usa insulina: corrija o dia com um toque.",
        "Até 1 copo de 200 ml por dia, uns 20 g de açúcar, ou nada (OMS, 2015: de preferência abaixo de 5% das calorias).",
        "OMS, açúcares (2015): açúcar livre abaixo de 10% das calorias; Guia Alimentar (Ministério da Saúde, 2014), " +
            "passo 4; DiMeglio e Mattes, International Journal of Obesity (2000): com 450 kcal por dia em refrigerante, " +
            "as pessoas não comeram menos no resto do dia e engordaram; com as mesmas calorias em jujuba, comeram menos. " +
            "Exceções: ACSM (2007) e ADA (2025).",
        troca = "água, água com gás ou chá sem açúcar; refri zero só como ponte.",
        paraIa = "bebida com açúcar (refrigerante comum, suco de caixinha ou néctar, refresco, chá pronto, achocolatado pronto)",
    ),
    EMBUTIDOS(
        "embutidos", GrupoHabito.OBRIGATORIO, "Embutidos e carne processada",
        "Salsicha, presunto, peito de peru, mortadela, salame, linguiça, calabresa e bacon.",
        "Causam câncer de intestino: cada 50 g por dia aumentam o risco em cerca de 18%. Não é regra de peso, e uma " +
            "fatia de vez em quando tem risco pequeno, por isso um deslize não apaga nada.",
        "Pouco ou nada: até 1 vez por semana, porção pequena (número sugerido pelo app, sem fonte oficial).",
        "IARC (2015): carne processada no grupo 1, o de causa comprovada de câncer; WCRF/AICR (2018): pouca, se " +
            "alguma; INCA (Informe 413, novembro de 2021): evitar a carne processada.",
        troca = "frango, ovo, carne fresca, atum ou queijo branco.",
        paraIa = "embutidos e carne processada (salsicha, presunto, peito de peru, mortadela, salame, linguiça, calabresa, bacon)",
    ),
    ALCOOL(
        "alcool", GrupoHabito.OBRIGATORIO, "Bebida alcoólica",
        "Cerveja, vinho, destilados e drinks.",
        "Nenhuma dose é segura para o câncer. O álcool tem 7 kcal por grama, e beber muito está ligado a ganho de " +
            "peso e desmonta a dieta do dia. Meta zero: um deslize por semana é perdoado e não apaga o que você já conquistou.",
        // O teto só aparece no modo seguro, que inclui quem está grávida, tem doença do fígado ou
        // usa insulina: para esses, nada de álcool sem o médico liberar.
        "Na gravidez, em doença do fígado ou com insulina: nada, ou só o que o seu médico liberar. Nos outros " +
            "casos, até 2 doses por semana e nunca 5 ou mais numa mesma ocasião (4 para mulher). Dose: 1 lata de " +
            "cerveja, 1 taça de vinho ou 1 dose de destilado (Canadá, 2023; VIGITEL, Ministério da Saúde, 2023).",
        "OMS Europa, Lancet Public Health (2023): não há nível seguro; Ministério da Saúde (Nota Técnica nº 263/2024, " +
            "reforçada em março de 2026); Traversy e Chaput, Current Obesity Reports (2015): beber muito está ligado a ganho de peso.",
        troca = "água com gás com limão, chá gelado sem açúcar ou cerveja sem álcool.",
        paraIa = "bebida alcoólica",
    ),
    CIGARRO(
        "cigarro", GrupoHabito.CIGARRO, "Cigarro",
        "Cigarro e outros jeitos de fumar tabaco (palheiro, charuto, narguilé).",
        "Para o fumo não há nível sem risco. O diário não vê o cigarro: na etapa dele, a conferência é por toque.",
        "",
        "Surgeon General dos EUA (2010): não há nível sem risco na fumaça do tabaco; Aubin e outros, BMJ (2012); " +
            "Hu e outros, NEJM (2018).",
    ),
    CAFE_CHA_ADOCADO(
        "cafe_cha_adocado", GrupoHabito.REDUZIR, "Café e chá adoçados em casa",
        "Açúcar, mel ou melado no café e no chá.",
        "",
        "Baixar aos poucos até 1 colher de chá rasa (uns 4 g) por xícara, ou nada.",
        "OMS, açúcares (2015): açúcar livre abaixo de 10% das calorias; AHA, Johnson e outros, Circulation (2009): " +
            "até uns 37 g de açúcar adicionado por dia para homem e 25 g para mulher.",
        paraIa = "café ou chá com açúcar",
    ),
    SUCO_100(
        "suco_100", GrupoHabito.REDUZIR, "Suco 100% de fruta",
        "Suco natural, integral ou de caixinha sem adição de açúcar.",
        "",
        "Até 150 ml por dia, junto com a refeição. A fruta inteira é melhor.",
        "OMS (2015): o açúcar do suco conta como açúcar livre; NHS, Eatwell Guide (2016): no máximo 150 ml por dia.",
        paraIa = "suco de fruta",
    ),
    DOCES(
        "doces", GrupoHabito.REDUZIR, "Doces",
        "Chocolate, bolo, sorvete, pudim, brigadeiro, bala e biscoito recheado.",
        "",
        "Até 3 dias por semana, uma porção pequena e planejada (número sugerido pelo app, sem fonte oficial).",
        "OMS (2015): açúcar livre abaixo de 10% das calorias; Guia Alimentar (Ministério da Saúde, 2014): evitar o " +
            "doce ultraprocessado.",
        paraIa = "doces (chocolate, bolo, sorvete, pudim, brigadeiro, bala, biscoito recheado)",
    ),
    ULTRAPROCESSADOS(
        "ultraprocessados", GrupoHabito.REDUZIR, "Ultraprocessados",
        "Salgadinho de pacote, macarrão instantâneo e congelados prontos (bebida, embutido e doce têm item próprio).",
        "",
        "Fora do dia a dia: até 1 ou 2 vezes por semana (número sugerido pelo app, sem fonte oficial).",
        "Guia Alimentar (Ministério da Saúde, 2014), passo 4: evitar; Hall e outros, Cell Metabolism (2019): com a " +
            "dieta ultraprocessada, 508 kcal a mais por dia.",
        paraIa = "ultraprocessados (salgadinho de pacote, macarrão instantâneo, congelados prontos)",
    ),
    DELIVERY(
        "delivery", GrupoHabito.REDUZIR, "Delivery, fast food e lanche de rua",
        "Rede de fast food, pedido de delivery e lanche de rua. Marmita, prato feito e restaurante por quilo não contam.",
        "",
        "Até 2 vezes por semana; até 1 é o mais seguro.",
        "Guia Alimentar (Ministério da Saúde, 2014), passo 9: evitar redes de fast food; Pereira e outros, Lancet " +
            "(2005): mais de 2 vezes por semana, contra menos de 1, deu 4,5 kg a mais em 15 anos.",
        paraIa = "fast food e lanche de rua",
    ),
    FRITURA(
        "fritura", GrupoHabito.REDUZIR, "Fritura",
        "Fritura por imersão: pastel, coxinha, batata frita, torresmo e empanados.",
        "",
        "Até 1 vez por semana, trocando por forno, air fryer ou grelha (número sugerido pelo app, sem fonte oficial).",
        "OMS, alimentação saudável (2020): preferir cozido ou no vapor; Qin e outros, Heart (2021): no maior consumo, " +
            "28% mais eventos graves do coração e dos vasos, 22% mais doença coronariana e 37% mais insuficiência " +
            "cardíaca (só estudos de observação).",
        paraIa = "frituras (pastel, coxinha, batata frita, empanados)",
    ),
    CARNE_VERMELHA(
        "carne_vermelha", GrupoHabito.REDUZIR, "Carne vermelha",
        "Boi, porco e carneiro.",
        "",
        "Até 500 g de carne cozida por semana.",
        "WCRF/AICR (2018): de 350 a 500 g cozida por semana; INCA (Informe 413, novembro de 2021).",
        paraIa = "carne vermelha (boi, porco, carneiro)",
    ),
    BELISCAR(
        "beliscar", GrupoHabito.REDUZIR, "Beliscar entre as refeições",
        "Comer fora das refeições e dos lanches planejados.",
        "",
        "Comer nos horários das refeições e dos lanches planejados.",
        "Guia Alimentar (Ministério da Saúde, 2014), passo 5: evitar beliscar nos intervalos. Evidência fraca a moderada.",
    ),
    DEPOIS_JANTAR(
        "depois_jantar", GrupoHabito.REDUZIR, "Comer depois do jantar",
        "O que você come entre o jantar e a hora de dormir.",
        "",
        "Jantar no horário, com proteína e fibra. Não há teto oficial.",
        "Ruddick-Collins e outros, Cell Metabolism (2022): com as mesmas calorias, comer mais cedo deu menos fome; " +
            "Vujović e outros, Cell Metabolism (2022): comer tarde aumentou a fome. Evidência fraca.",
    ),
    REFRI_ZERO(
        "refri_zero", GrupoHabito.REDUZIR, "Refri zero e adoçante",
        "Refrigerante zero, diet ou light e adoçante.",
        "",
        "Ponte para sair do refri comum; o destino é água, água com gás, chá e café sem açúcar. Até 3 dias por semana " +
            "para começar (número sugerido pelo app, sem fonte oficial).",
        "OMS, adoçantes (2023): não usar para controlar o peso (recomendação condicional, que não vale para quem já " +
            "tem diabetes); McGlynn e outros, JAMA Network Open (2022): trocar o refri comum pelo zero tirou 1,06 kg, " +
            "igual à troca por água.",
        paraIa = "refrigerante zero e adoçante",
    ),
    ENERGETICO_CAFEINA(
        "energetico_cafeina", GrupoHabito.REDUZIR, "Energético e cafeína",
        "Energético sem açúcar, café forte e outras bebidas com cafeína (o energético comum já está em Bebida com açúcar).",
        "",
        "Até 400 mg de cafeína por dia e 200 mg de uma vez; na gravidez e na amamentação, até 200 mg por dia. " +
            "Uma lata de 250 ml de energético tem uns 80 mg.",
        "EFSA, segurança da cafeína (2015).",
        paraIa = "energético e bebida com muita cafeína",
    ),
    ;

    companion object {
        fun deId(id: String?): Habito? = entries.firstOrNull { it.id == id }
    }
}

/** O que a pessoa escolheu para cada hábito na montagem. */
enum class Escolha(val id: String) {
    ELIMINAR("eliminar"),
    REDUZIR("reduzir"),
    /** Só nos obrigatórios: o hábito começa como mantido, sem etapa. */
    JA_NAO_FACO("ja_nao_faco"),
    NAO_E_MEU_CASO("nao_e_meu_caso"),
    ;

    companion object {
        fun deId(id: String?): Escolha? = entries.firstOrNull { it.id == id }
    }
}

/** Como ficou um dia da etapa. Nunca "falhou": um deslize não apaga nada. */
enum class EstadoDia(val id: String) {
    NA_META("na_meta"),
    DESLIZE("deslize"),
    /** Nada lançado no diário: não conta a favor nem contra. */
    SEM_DADO("sem_dado"),
    ;

    companion object {
        fun deId(id: String?): EstadoDia? = entries.firstOrNull { it.id == id }
    }
}

enum class EstadoSemana { BOA, RUIM, SEM_DADO, EM_CURSO }

/** Um dia da etapa: o que vale (o toque, se houver) e o que o diário diz. */
data class DiaDaEtapa(
    /** yyyy-MM-dd */
    val dia: String,
    val estado: EstadoDia,
    val peloDiario: EstadoDia,
    val tocado: Boolean,
    /** Depois de hoje: aparece na semana, mas não conta e não aceita toque. */
    val futuro: Boolean,
)

/** Uma semana da etapa: 7 dias seguidos a partir do dia em que a etapa começou. */
data class SemanaDaEtapa(
    /** 1 é a semana do início da etapa. */
    val numero: Int,
    /** Sempre 7. */
    val dias: List<DiaDaEtapa>,
    val estado: EstadoSemana,
    val diasComDado: Int,
    val deslizes: Int,
)

data class ProgressoEtapa(
    val habito: Habito,
    /** A semana que tem o dia de hoje (ainda não conta). */
    val atual: SemanaDaEtapa,
    /** A semana fechada mais recente; null na primeira semana. */
    val ultimaCompleta: SemanaDaEtapa?,
    /** Semanas boas seguidas até agora, de 0 a 2; sempre 0 no modo seguro. */
    val seguidas: Int,
    /** Último dia da 2ª semana boa seguida; null se ainda não venceu (e sempre no modo seguro). */
    val vencidaEm: String?,
    val hoje: DiaDaEtapa,
    /** A etapa está em Reduzir: o dia na meta é o que fica dentro do teto (os textos dizem isso). */
    val reduzir: Boolean = false,
)

/** Hábito escrito pela pessoa em "Outro hábito": Eliminar ou Reduzir. */
data class OutroHabito(val nome: String, val escolha: Escolha)

/** Etapa vencida: o hábito e o dia da vitória (yyyy-MM-dd). */
data class Vencida(val habito: Habito, val dia: String)

/** Correção de um dia feita pela pessoa: vale mais que o diário. Só NA_META ou DESLIZE. */
data class Toque(val dia: String, val habito: Habito, val estado: EstadoDia)

/** Tudo o que a jornada guarda nas preferências "jornada" (Ajustes.kt, JornadaAjustes). */
data class JornadaSalva(
    val ligada: Boolean = false,
    val montada: Boolean = false,
    /** Respondeu sim na pergunta de saúde: modo seguro. */
    val seguro: Boolean = false,
    val escolhas: Map<Habito, Escolha> = emptyMap(),
    val outros: List<OutroHabito> = emptyList(),
    /** A etapa em curso e o dia em que ela começou (yyyy-MM-dd). */
    val etapa: Habito? = null,
    val inicioEtapa: String? = null,
    val vencidas: List<Vencida> = emptyList(),
    /** Venceu a etapa e espera o toque em "Começar a próxima". */
    val aguardando: Boolean = false,
    val toques: List<Toque> = emptyList(),
)

/** O que o cartão do Diário mostra. */
enum class MomentoDaJornada { MONTAR, EM_CURSO, VENCIDA, SEM_ETAPA }

/** O que nunca entra como eliminar em "Outro hábito", com o porquê. */
enum class Bloqueio(val explicacao: String) {
    REFEICAO(
        "Uma refeição inteira não entra como eliminar: pular refeição para compensar é sinal de alerta, " +
            "não uma etapa. Se quiser, marque Reduzir."
    ),
    GRUPO(
        "Um grupo inteiro (arroz, feijão, pão, fruta, leite, óleo) não entra como eliminar: tira nutrientes e não " +
            "emagrece mais. Dieta pobre em carboidrato e dieta pobre em gordura emagreceram igual em 1 ano (Gardner e " +
            "outros, JAMA, 2018). Se for por motivo médico, siga o médico, sem etapa. Se quiser, marque Reduzir."
    ),
    SAL(
        "O sal não entra como eliminar: no Brasil ele é a fonte de iodo (ANVISA, RDC 23/2013). O teto de sódio, " +
            "2.000 mg por dia, já está no cartão Nutrientes. Se quiser, marque Reduzir."
    ),
    AGUA(
        "A água não entra como eliminar: o corpo precisa dela todo dia. Se o médico mandou limitar líquidos, siga o médico."
    ),
}

/**
 * O peso meta dá IMC abaixo do mínimo saudável para a idade. [imc] e [pesoMinimoKg] já vêm com
 * uma casa, arredondados para o lado do aviso (Jornada.avisoImc).
 */
data class AvisoImc(val imc: Double, val minimo: Double, val pesoMinimoKg: Double)

object Jornada {
    const val SEMANAS_PARA_VENCER = 2
    const val DIAS_COM_DADO_NA_SEMANA = 5
    const val DESLIZES_PERDOADOS = 1
    const val MAX_OUTROS = 5
    const val MAX_NOME_OUTRO = 40

    const val PERGUNTA_SAUDE = "Você tem doença nos rins, no coração ou no fígado, usa insulina, está grávida ou " +
        "tem (ou já teve) transtorno alimentar?"
    const val TEXTO_MODO_SEGURO = "Modo seguro: sem cadeado (você escolhe entre eliminar e reduzir) e sem contar " +
        "semanas seguidas (você passa de etapa quando quiser). Siga o seu médico."
    const val TEXTO_MODO_SEGURO_CARTAO = "Modo seguro: você passa de etapa quando quiser. Siga o seu médico."
    const val TEXTO_RESPOSTA_SAUDE = "A resposta fica só no app, como o seu diário, e não vai para a IA."
    const val TEXTO_SUS = "O SUS trata de graça quem quer parar de fumar, com acompanhamento e, se preciso, remédio " +
        "(Programa Nacional de Controle do Tabagismo, INCA). Procure o posto de saúde (Unidade Básica de Saúde) " +
        "mais perto de você."
    // O medo de engordar segura muita gente no cigarro: por isso o aviso do peso vem junto do
    // benefício de parar, que é bem maior.
    const val TEXTO_PESO_CIGARRO = "Parar de fumar costuma trazer uns 4 a 5 kg no primeiro ano (Aubin e outros, " +
        "BMJ, 2012): leve isso em conta na meta de peso. Mesmo assim, parar faz muito mais bem à saúde do que " +
        "esses quilos fazem mal (Hu e outros, NEJM, 2018)."
    const val TEXTO_LALLY = "Vencer a etapa não quer dizer que o hábito já ficou automático: no estudo de Lally e " +
        "outros (2010), isso levou em média 66 dias, de 18 a 254. Por isso ele fica nos mantidos."
    const val TEXTO_PROXIMA_VERSAO = "A próxima etapa chega numa nova versão do app. Até lá, a etapa vencida " +
        "fica nos mantidos."

    // --- Bebida com açúcar ---

    // Abaixo de 2,5 g de açúcar por 100 ml é "baixo teor" (Regulamento CE 1924/2006). Quando o açúcar
    // vem zero (no app, não informado), o carboidrato faz o papel dele, com folga para o que não é açúcar.
    const val ACUCAR_MINIMO_100 = 2.5
    const val CARBOIDRATO_MINIMO_100 = 5.0

    // As kcal que o carboidrato, a proteína e a gordura não explicam vêm do álcool (7 kcal por grama).
    // 15 kcal por 100 ml são uns 2 g de álcool; no refri e no suco, a sobra fica perto de zero.
    const val KCAL_DE_ALCOOL_MINIMA_100 = 15.0

    /**
     * O teto da bebida com açúcar em Reduzir (só no modo seguro), o mesmo do texto em
     * Habito.BEBIDA_ACUCAR.teto: até 1 copo de 200 ml por dia. Acima dele, o dia é deslize.
     */
    const val TETO_BEBIDA_REDUZIR_ML = 200.0

    /** Nomes de bebida que o Bebidas.ehBebida não reconhece (ele só olha a primeira palavra). */
    private val NOMES_DE_BEBIDA = setOf(
        "suco", "nectar", "refresco", "tang", "limonada", "laranjada", "achocolatado", "toddynho", "garapa",
        "energetico", "isotonico", "isotonica", "gatorade", "powerade", "monster", "redbull", "icetea", "lipton",
        "fuze", "kuat", "sukita", "schweppes", "itubaina", "tubaina", "dolly", "guaraviton", "refri", "refrigerante",
        "yakult", "nescau", "toddy", "kapo", "ades",
    )

    /** Pares de palavras que, juntas no nome, dizem que é bebida. */
    private val PARES_DE_BEBIDA = listOf(
        "caldo" to "cana", "red" to "bull", "ice" to "tea", "bebida" to "lactea",
        "iogurte" to "beber", "iogurte" to "liquido", "chocolate" to "quente",
    )

    /** Comida que leva nome de bebida ("Bolo de laranja com suco", "Bala de refresco"): não é bebida. */
    private val COMIDA_COM_NOME_DE_BEBIDA = setOf(
        "bolo", "sorvete", "picole", "bala", "calda", "xarope", "molho", "pudim", "mousse", "musse", "torta",
        "gelatina", "biscoito", "bolacha", "cobertura", "recheio", "recheado", "recheada", "bombom", "cereal",
        "barra", "barrinha", "pao", "brigadeiro", "geleia", "mingau", "pave",
    )

    private val SEM_ACUCAR = setOf("zero", "diet", "light", "sugarfree")

    // "Zero lactose" e "zero gordura" não falam do açúcar; "zero álcool" também não.
    private val ZERO_QUE_NAO_E_ACUCAR = setOf(
        "lactose", "gordura", "gorduras", "gluten", "colesterol", "sodio", "conservantes", "alcool",
    )
    private val CAFE = setOf(
        "cafe", "cafezinho", "cappuccino", "capuccino", "capucino", "expresso", "espresso", "latte", "macchiato",
        "mocha", "mocaccino",
    )
    // Lipton e Fuze são as marcas de chá pronto da lista de nomes: "Chá Fuze pêssego" é o pronto.
    private val CHA_PRONTO = setOf(
        "pronto", "pronta", "industrializado", "lata", "garrafa", "caixinha", "lipton", "fuze",
    )
    private val AGUA_QUE_E_OUTRA_BEBIDA = setOf("tonica", "coco", "saborizada", "saborizado")

    /** Leite com isto já não é leite puro. */
    private val LEITE_COM_OUTRA_COISA = setOf(
        "achocolatado", "chocolate", "choco", "nescau", "toddy", "ovomaltine", "fermentado", "condensado", "mel",
        "acucar", "adocado", "adocada", "sabor", "morango", "baunilha", "shake", "milkshake",
    )

    /** Suco 100%: fica no Reduzir, não na bebida com açúcar. */
    private val SUCO_SEM_ACUCAR = setOf("100", "integral", "natural", "espremido", "fresco")

    /** Suco com estas palavras é o adoçado ou o de caixinha. */
    private val SUCO_ADOCADO = setOf(
        "nectar", "adocado", "adocada", "acucar", "caixinha", "caixa", "lata", "garrafa", "pronto", "industrializado",
        "refresco", "po", "tang",
    )

    /**
     * Se o item é bebida com açúcar para a etapa:
     * 1. comida com nome de bebida não é bebida;
     * 2. é bebida pelo nome (lista da jornada, Bebidas.ehBebida, Bebidas.pedeAgua) ou pela marca "pede água";
     * 3. ficam de fora álcool (pelo nome ou pelas kcal; a cerveja sem álcool também), zero/diet/light/sem
     *    açúcar, água, água de coco, café, chá feito em casa, leite puro e, sem açúcar, néctar ou refresco
     *    no nome, "natural", suco 100% e vitamina;
     * 4. o resto conta com açúcar a partir de 2,5 g por 100 ml ou, com o açúcar zero (não informado),
     *    com carboidrato a partir de 5 g por 100 ml.
     */
    fun bebidaComAcucar(a: Alimento): Boolean {
        val w = Busca.palavras(a.nome)
        if (w.isEmpty()) return false
        if (w.any { it in COMIDA_COM_NOME_DE_BEBIDA || it.startsWith("cocad") }) return false
        val daJornada = w.any { it in NOMES_DE_BEBIDA } || PARES_DE_BEBIDA.any { (x, y) -> x in w && y in w }
        if (!(daJornada || a.pedeAgua || Bebidas.ehBebida(a.nome) || Bebidas.pedeAgua(a.nome))) return false

        // "Espumante sem álcool" e "drink sem álcool" são bebida com açúcar; a cerveja sem álcool
        // fica de fora, porque é a troca sugerida para o álcool.
        val semAlcool = w.indices.any { i ->
            (w[i] == "sem" || w[i] == "zero") && w.getOrNull(i + 1)?.startsWith("alcool") == true
        }
        val cerveja = w.any { it.startsWith("cerveja") || it == "chope" || it == "chopp" }
        if ((Bebidas.temAlcool(a.nome) && (!semAlcool || cerveja)) || pareceAlcool(a)) return false
        val zero = w.indices.any { i ->
            w[i] in SEM_ACUCAR && !(w[i] == "zero" && w.getOrNull(i + 1) in ZERO_QUE_NAO_E_ACUCAR)
        }
        if (zero || semAcucar(w)) return false
        if (w.first() == "agua" && w.none { it in AGUA_QUE_E_OUTRA_BEBIDA }) return false
        if ("agua" in w && "coco" in w) return false
        if (w.any { it in CAFE }) return false
        // O chá do código de barras com açúcar no rótulo é o pronto; sachê e folha vêm sem açúcar.
        if (("cha" in w || "chimarrao" in w) && w.none { it in CHA_PRONTO } &&
            !(a.origem == ORIGEM_OFF && a.micros100.acucar > 0)
        ) {
            return false
        }
        if (w.first() == "leite" && w.none { it in LEITE_COM_OUTRA_COISA }) return false
        // "Sem açúcar" já saiu acima: aqui, açúcar no nome quer dizer que foi adoçado. Néctar,
        // refresco e Tang são adoçados mesmo com "natural" no nome ("Néctar de uva Natural One").
        val adocado = w.any {
            it.startsWith("acucar") || it.startsWith("adocad") || it == "nectar" || it == "refresco" || it == "tang"
        }
        if ("natural" in w && !adocado) return false
        if ("suco" in w && !adocado && !sucoConta(w, a.origem)) return false
        if (("vitamina" in w || "smoothie" in w) && !adocado) return false

        val acucar = a.micros100.acucar
        return if (acucar > 0) acucar >= ACUCAR_MINIMO_100 else a.carb100 >= CARBOIDRATO_MINIMO_100
    }

    // "sem açúcar", "sem adição de açúcar", "sugar free", "0% açúcar".
    private fun semAcucar(w: List<String>): Boolean = w.indices.any { i ->
        val proxima = w.getOrNull(i + 1).orEmpty()
        (w[i] == "sem" && (proxima.startsWith("acucar") || proxima.startsWith("adicao"))) ||
            (w[i] == "sugar" && proxima == "free") ||
            (w[i] == "0" && proxima.startsWith("acucar"))
    }

    // Suco sem nada que diga como é: o do código de barras e o criado à mão (com os valores do
    // rótulo) são de caixinha; o da IA e o da TACO, natural.
    private fun sucoConta(w: List<String>, origem: String): Boolean = when {
        w.any { it in SUCO_SEM_ACUCAR } -> false
        w.any { it in SUCO_ADOCADO } -> true
        else -> origem == ORIGEM_OFF || origem == ORIGEM_MEU
    }

    private fun pareceAlcool(a: Alimento): Boolean {
        if (!a.pedeAgua) return false
        val explicadas = 4 * maxOf(a.carb100, a.micros100.acucar) + 4 * a.prot100 + 9 * a.gord100
        return a.kcal100 - explicadas >= KCAL_DE_ALCOOL_MINIMA_100
    }

    /**
     * O dia pelo diário: sem nenhum registro, sem dado. Só a bebida com açúcar é achada sozinha:
     * em Eliminar, qualquer gole é deslize; em Reduzir ([reduzir], só no modo seguro), só o que
     * passa do teto de TETO_BEBIDA_REDUZIR_ML no dia.
     */
    fun estadoPeloDiario(habito: Habito, registrosDoDia: List<Registro>, reduzir: Boolean = false): EstadoDia {
        if (registrosDoDia.isEmpty()) return EstadoDia.SEM_DADO
        return when (habito) {
            Habito.BEBIDA_ACUCAR -> {
                val bebidas = registrosDoDia.filter { bebidaComAcucar(it.alimento) }
                // Na bebida, os gramas do diário são os ml.
                val passou = if (reduzir) bebidas.sumOf { it.gramas } > TETO_BEBIDA_REDUZIR_ML else bebidas.isNotEmpty()
                if (passou) EstadoDia.DESLIZE else EstadoDia.NA_META
            }
            else -> EstadoDia.SEM_DADO
        }
    }

    /**
     * Semana boa: 5 dias ou mais com dado e no máximo 1 deslize (nos de eliminar, 1 é perdoado).
     * 2 deslizes ou mais: ruim, mesmo com menos de 5 dias com dado. Menos de 5 dias com dado e
     * no máximo 1 deslize: sem dado, não conta nem quebra. A semana em curso ainda não conta.
     */
    fun avaliarSemana(dias: List<DiaDaEtapa>, completa: Boolean): EstadoSemana {
        if (!completa) return EstadoSemana.EM_CURSO
        val comDado = dias.count { it.estado != EstadoDia.SEM_DADO }
        val deslizes = dias.count { it.estado == EstadoDia.DESLIZE }
        return when {
            // Os deslizes vêm antes dos dias lançados: 2 já passam do perdão, seja qual for o
            // resto da semana (DECISOES.md, "Jornada de hábitos").
            deslizes > DESLIZES_PERDOADOS -> EstadoSemana.RUIM
            comDado < DIAS_COM_DADO_NA_SEMANA -> EstadoSemana.SEM_DADO
            else -> EstadoSemana.BOA
        }
    }

    /**
     * Reconta a etapa do zero a cada leitura: dá para lançar comida em dia passado, então
     * nada de contador gravado. Semanas de 7 dias a partir do início; só as completas contam;
     * semana ruim zera as seguidas e semana sem dado só espera; 2 boas seguidas vencem.
     * No modo seguro não há contagem: a pessoa passa de etapa quando quiser.
     * [registros]: os do início da etapa até hoje; [toques]: dia (yyyy-MM-dd) e o estado tocado;
     * [reduzir]: a etapa está em Reduzir (emReduzir), e o dia na meta é o que fica dentro do teto.
     */
    fun progresso(
        habito: Habito,
        inicio: String,
        hoje: String,
        registros: List<Registro>,
        toques: Map<String, EstadoDia>,
        seguro: Boolean,
        reduzir: Boolean = false,
    ): ProgressoEtapa {
        val ini = LocalDate.parse(inicio)
        // Relógio do celular antes do início: fica no primeiro dia, sem semana negativa.
        val h = maxOf(LocalDate.parse(hoje), ini)
        val porDia = registros.groupBy { it.dia }

        fun dia(d: LocalDate): DiaDaEtapa {
            val s = d.toString()
            val futuro = d.isAfter(h)
            val diario = if (futuro) EstadoDia.SEM_DADO else estadoPeloDiario(habito, porDia[s].orEmpty(), reduzir)
            val toque = if (futuro) null else toques[s]?.takeIf { it != EstadoDia.SEM_DADO }
            return DiaDaEtapa(s, toque ?: diario, diario, toque != null, futuro)
        }

        val indiceAtual = (ChronoUnit.DAYS.between(ini, h) / 7).toInt()

        fun semana(k: Int): SemanaDaEtapa {
            val dias = (0 until 7).map { dia(ini.plusDays(7L * k + it)) }
            return SemanaDaEtapa(
                numero = k + 1,
                dias = dias,
                estado = avaliarSemana(dias, completa = k < indiceAtual),
                diasComDado = dias.count { it.estado != EstadoDia.SEM_DADO },
                deslizes = dias.count { it.estado == EstadoDia.DESLIZE },
            )
        }

        val completas = (0 until indiceAtual).map { semana(it) }
        var seguidas = 0
        var vencidaEm: String? = null
        if (!seguro) {
            for (s in completas) {
                when (s.estado) {
                    EstadoSemana.BOA -> seguidas++
                    EstadoSemana.RUIM -> seguidas = 0
                    else -> {}
                }
                if (seguidas >= SEMANAS_PARA_VENCER) {
                    vencidaEm = s.dias.last().dia
                    break
                }
            }
        }
        return ProgressoEtapa(habito, semana(indiceAtual), completas.lastOrNull(), seguidas, vencidaEm, dia(h), reduzir)
    }

    // --- Montagem ---

    /** Os botões de cada hábito, na ordem da tela. */
    fun opcoes(h: Habito, seguro: Boolean): List<Escolha> = when (h.grupo) {
        GrupoHabito.OBRIGATORIO ->
            if (seguro) listOf(Escolha.ELIMINAR, Escolha.REDUZIR, Escolha.JA_NAO_FACO)
            else listOf(Escolha.ELIMINAR, Escolha.JA_NAO_FACO)
        GrupoHabito.CIGARRO -> listOf(Escolha.ELIMINAR, Escolha.NAO_E_MEU_CASO)
        GrupoHabito.REDUZIR -> listOf(Escolha.ELIMINAR, Escolha.REDUZIR, Escolha.NAO_E_MEU_CASO)
    }

    /**
     * Já marcada ao abrir: o obrigatório em Eliminar, com cadeado (no modo seguro, Reduzir);
     * o de reduzir em "Não é meu caso"; o cigarro sem nada, até a pessoa responder.
     */
    fun escolhaInicial(h: Habito, seguro: Boolean): Escolha? = when (h.grupo) {
        GrupoHabito.OBRIGATORIO -> if (seguro) Escolha.REDUZIR else Escolha.ELIMINAR
        GrupoHabito.CIGARRO -> null
        GrupoHabito.REDUZIR -> Escolha.NAO_E_MEU_CASO
    }

    fun escolhasIniciais(seguro: Boolean): Map<Habito, Escolha> =
        Habito.entries.mapNotNull { h -> escolhaInicial(h, seguro)?.let { h to it } }.toMap()

    /** Texto do botão. */
    fun rotulo(h: Habito, e: Escolha, seguro: Boolean): String = when {
        h.grupo == GrupoHabito.CIGARRO && e == Escolha.ELIMINAR -> "Vou parar"
        h.grupo == GrupoHabito.OBRIGATORIO && e == Escolha.ELIMINAR && !seguro -> "Vou eliminar"
        e == Escolha.ELIMINAR -> "Eliminar"
        e == Escolha.REDUZIR -> "Reduzir"
        e == Escolha.JA_NAO_FACO -> "Já não faço"
        else -> "Não é meu caso"
    }

    /**
     * A resposta de saúde mudou: os obrigatórios voltam à marcação inicial do modo novo
     * (no seguro, Reduzir). "Já não faço" fica.
     */
    fun aoMudarSaude(escolhas: Map<Habito, Escolha>, seguro: Boolean): Map<Habito, Escolha> =
        escolhas.toMutableMap().apply {
            Habito.entries.filter { it.grupo == GrupoHabito.OBRIGATORIO }.forEach { h ->
                if (this[h] != Escolha.JA_NAO_FACO) this[h] = escolhaInicial(h, seguro)!!
            }
        }

    /** Tira o que o modo não aceita (Reduzir num obrigatório fora do modo seguro) e completa o que falta. */
    fun escolhasValidas(escolhas: Map<Habito, Escolha>, seguro: Boolean): Map<Habito, Escolha> =
        Habito.entries.mapNotNull { h ->
            val e = escolhas[h]?.takeIf { it in opcoes(h, seguro) } ?: escolhaInicial(h, seguro)
            e?.let { h to it }
        }.toMap()

    /** O que falta para começar; null quando está tudo respondido. */
    fun faltaResponder(saude: Boolean?, escolhas: Map<Habito, Escolha>): String? {
        val semSaude = saude == null
        val semCigarro = escolhas[Habito.CIGARRO] == null
        return when {
            semSaude && semCigarro -> "Responda à pergunta de saúde e à do cigarro para começar."
            semSaude -> "Responda à pergunta de saúde para começar."
            semCigarro -> "Responda à pergunta do cigarro para começar."
            else -> null
        }
    }

    /** A única etapa que existe é a da bebida com açúcar: ela começa se não for "Já não faço". */
    fun primeiraEtapa(escolhas: Map<Habito, Escolha>): Habito? =
        Habito.BEBIDA_ACUCAR.takeIf { escolhas[it] == Escolha.ELIMINAR || escolhas[it] == Escolha.REDUZIR }

    /**
     * Montar de novo com a mesma etapa em curso e a mesma resposta de saúde guarda o início dela
     * e os toques. Mudou a resposta de saúde, a etapa recomeça: as semanas do modo seguro não
     * foram contadas, e a meta delas era outra.
     */
    fun continuaAEtapa(atual: JornadaSalva, etapa: Habito?, seguro: Boolean = atual.seguro): Boolean =
        atual.montada && etapa != null && atual.etapa == etapa && atual.inicioEtapa != null && atual.seguro == seguro

    /** A etapa já foi vencida uma vez: ela não volta a contar e fica nos mantidos. */
    fun jaVencida(s: JornadaSalva, etapa: Habito?): Boolean = etapa != null && s.vencidas.any { it.habito == etapa }

    /**
     * Grava a montagem. Montar de novo com a mesma etapa guarda o início, os toques e a
     * espera de "Começar a próxima"; etapa vencida nunca volta a contar.
     */
    fun montar(
        atual: JornadaSalva,
        seguro: Boolean,
        escolhas: Map<Habito, Escolha>,
        outros: List<OutroHabito>,
        hoje: String,
    ): JornadaSalva {
        val validas = escolhasValidas(escolhas, seguro)
        val etapa = primeiraEtapa(validas)
        val continua = continuaAEtapa(atual, etapa, seguro)
        return atual.copy(
            ligada = true,
            montada = true,
            seguro = seguro,
            escolhas = validas,
            // A tela já barra, e a regra também: refeição, grupo, sal e água nunca entram como eliminar.
            outros = outros.filterNot { it.escolha == Escolha.ELIMINAR && bloqueioDeEliminar(it.nome) != null }
                .take(MAX_OUTROS),
            etapa = etapa,
            inicioEtapa = when {
                etapa == null -> null
                continua -> atual.inicioEtapa
                else -> hoje
            },
            aguardando = jaVencida(atual, etapa) || (continua && atual.aguardando),
            toques = if (continua) atual.toques else emptyList(),
        )
    }

    /** Toque num dia; null (ou sem dado) apaga a correção e volta ao que o diário diz. */
    fun comToque(atual: JornadaSalva, dia: String, habito: Habito, estado: EstadoDia?): JornadaSalva {
        val resto = atual.toques.filterNot { it.dia == dia && it.habito == habito }
        val novo = estado?.takeIf { it != EstadoDia.SEM_DADO }?.let { Toque(dia, habito, it) }
        return atual.copy(toques = if (novo == null) resto else resto + novo)
    }

    /** Grava a vitória uma vez só: um deslize lançado depois não apaga o que foi conquistado. */
    fun comVitoria(atual: JornadaSalva, habito: Habito, dia: String): JornadaSalva {
        if (atual.etapa != habito) return atual
        val vencidas = if (atual.vencidas.any { it.habito == habito }) atual.vencidas else atual.vencidas + Vencida(habito, dia)
        return atual.copy(vencidas = vencidas, aguardando = true)
    }

    fun toquesDo(s: JornadaSalva, habito: Habito): Map<String, EstadoDia> =
        s.toques.filter { it.habito == habito }.associate { it.dia to it.estado }

    /** A etapa em curso está em Reduzir (só no modo seguro): a meta do dia é o teto, não o zero. */
    fun emReduzir(s: JornadaSalva): Boolean = s.etapa != null && s.escolhas[s.etapa] == Escolha.REDUZIR

    fun momento(s: JornadaSalva): MomentoDaJornada = when {
        !s.montada -> MomentoDaJornada.MONTAR
        s.etapa == null || s.inicioEtapa == null -> MomentoDaJornada.SEM_ETAPA
        s.aguardando -> MomentoDaJornada.VENCIDA
        else -> MomentoDaJornada.EM_CURSO
    }

    /** O número da etapa em curso: as vencidas antes dela mais um. */
    fun numeroDaEtapa(s: JornadaSalva): Int = s.vencidas.count { it.habito != s.etapa } + 1

    /** Os "Já não faço" e os vencidos, na ordem da lista. */
    fun mantidos(s: JornadaSalva): List<Habito> =
        Habito.entries.filter { h -> s.escolhas[h] == Escolha.JA_NAO_FACO || s.vencidas.any { it.habito == h } }

    /** O que o pedido de Sugerir refeição manda evitar: o eliminado (e o "Já não faço"), com a jornada ligada. */
    fun evitarNaSugestao(s: JornadaSalva): List<String> {
        if (!s.ligada || !s.montada) return emptyList()
        val habitos = Habito.entries.filter { h ->
            val e = s.escolhas[h]
            h.paraIa != null && (e == Escolha.ELIMINAR || e == Escolha.JA_NAO_FACO)
        }.mapNotNull { it.paraIa }
        return habitos + s.outros.filter { it.escolha == Escolha.ELIMINAR }.map { it.nome }
    }

    // --- Outro hábito ---

    private val PALAVRAS_SOLTAS = setOf(
        "o", "a", "os", "as", "de", "da", "do", "das", "dos", "com", "sem", "e", "no", "na", "nos", "nas", "em",
        "comer", "pular", "tomar", "beber", "fazer", "meu", "minha", "todo", "toda", "todos", "todas",
        // Jeitos comuns de escrever o corte: "cortar o pão", "parar de comer arroz", "nada de pão".
        "nao", "nada", "zero", "cortar", "tirar", "parar", "deixar", "evitar", "ficar", "usar", "colocar", "por",
        "qualquer", "tipo", "tipos", "geral", "inteiro", "inteira", "dia", "dias", "derivados",
    )
    private val REFEICOES = listOf(
        listOf("cafe", "manha"), listOf("almoco"), listOf("jantar"), listOf("janta"), listOf("lanche"),
        listOf("lanches"), listOf("lanche", "tarde"), listOf("lanche", "manha"), listOf("ceia"), listOf("refeicao"),
        listOf("refeicoes"), listOf("desjejum"), listOf("cafe", "tarde"),
    )
    private val GRUPOS = setOf(
        "arroz", "feijao", "pao", "paes", "fruta", "frutas", "leite", "laticinios", "oleo", "oleos", "carboidrato",
        "carboidratos", "gordura", "gorduras", "gluten", "lactose",
    )
    private val AGUA_PURA = setOf("mineral", "filtrada", "gas", "potavel", "torneira", "gelada", "pura")

    /**
     * Por que o nome não entra como eliminar; null quando pode. Sem as palavras soltas, só grupos
     * valem como o grupo ("pão" e "arroz e feijão" sim, "pão de queijo" não), e o sal e a água
     * como primeira palavra.
     */
    fun bloqueioDeEliminar(nome: String): Bloqueio? {
        val w = Busca.palavras(nome).filter { it !in PALAVRAS_SOLTAS }
        if (w.isEmpty()) return null
        return when {
            w in REFEICOES -> Bloqueio.REFEICAO
            w.all { it in GRUPOS } -> Bloqueio.GRUPO
            w[0] == "sal" || w[0] == "sodio" -> Bloqueio.SAL
            w[0] == "agua" && (w.size == 1 || w[1] in AGUA_PURA) -> Bloqueio.AGUA
            else -> null
        }
    }

    // --- Onde quer chegar ---

    /**
     * A primeira parada de quem quer emagrecer com o peso meta longe (mais de 10% abaixo do
     * peso de hoje): perder de 5% a 10% (AHA/ACC/TOS, 2013). Devolve os pesos com 5% e com
     * 10% a menos; null quando não se aplica.
     */
    fun primeiraParada(pesoKg: Double, pesoMetaKg: Double, objetivo: Objetivo): Pair<Double, Double>? {
        if (objetivo != Objetivo.PERDER || pesoKg <= 0 || pesoMetaKg <= 0) return null
        if (pesoMetaKg >= pesoKg * 0.90) return null
        return pesoKg * 0.95 to pesoKg * 0.90
    }

    fun imc(pesoKg: Double, alturaCm: Double): Double? =
        if (pesoKg > 0 && alturaCm > 0) pesoKg / ((alturaCm / 100) * (alturaCm / 100)) else null

    /** IMC 18,5 (OMS); a partir dos 60 anos, 22 (Lipschitz, a régua do SISVAN). */
    fun imcMinimo(idade: Int): Double = if (idade >= 60) 22.0 else 18.5

    /** Aviso leve, sem travar nada, quando o peso meta dá IMC abaixo do mínimo saudável. */
    fun avisoImc(pesoMetaKg: Double, alturaCm: Double, idade: Int): AvisoImc? {
        val valor = imc(pesoMetaKg, alturaCm) ?: return null
        val minimo = imcMinimo(idade)
        if (valor >= minimo) return null
        val m = alturaCm / 100
        // A tela mostra uma casa: o IMC vai para baixo e o peso mínimo para cima, para nunca
        // aparecer "18,5, abaixo de 18,5" nem um peso mínimo que ainda daria o aviso.
        return AvisoImc(floor(valor * 10) / 10, minimo, ceil(minimo * m * m * 10) / 10)
    }

    // --- Textos do cartão e da montagem ---

    /**
     * Como o dia aparece. "Nas refeições" porque a água do copo e os exercícios não contam.
     * Em Reduzir ([reduzir]), a meta é o teto.
     */
    fun rotuloDoDia(h: Habito, e: EstadoDia, reduzir: Boolean = false): String = when {
        e == EstadoDia.SEM_DADO -> "nada lançado nas refeições"
        reduzir -> if (e == EstadoDia.NA_META) "dentro do teto" else "passou do teto"
        h == Habito.BEBIDA_ACUCAR -> if (e == EstadoDia.NA_META) "sem bebida com açúcar" else "teve bebida com açúcar"
        else -> if (e == EstadoDia.NA_META) "dentro da meta" else "fora da meta"
    }

    /**
     * A legenda embaixo da semana. O "·" é o dia que ainda não chegou, por isso os itens se
     * separam por vírgula.
     */
    fun legendaDaSemana(h: Habito, reduzir: Boolean = false): String {
        val naMeta = rotuloDoDia(h, EstadoDia.NA_META, reduzir)
        // Depois de "sem bebida com açúcar", basta "teve".
        val deslize = if (!reduzir && h == Habito.BEBIDA_ACUCAR) "teve" else rotuloDoDia(h, EstadoDia.DESLIZE, reduzir)
        return "✅ $naMeta, 🔸 $deslize, ⚪ ${rotuloDoDia(h, EstadoDia.SEM_DADO)}, · ainda não chegou. " +
            "Toque num dia para corrigir."
    }

    /** Sem vermelho e sem "falhou": o deslize é laranja. */
    fun simboloDoDia(d: DiaDaEtapa): String = when {
        d.futuro -> "·"
        d.estado == EstadoDia.NA_META -> "✅"
        d.estado == EstadoDia.DESLIZE -> "🔸"
        else -> "⚪"
    }

    /** Letra do dia da semana, como nos calendários: D S T Q Q S S. */
    fun letraDoDia(dia: String): String = LETRAS[LocalDate.parse(dia).dayOfWeek.value - 1]

    // DayOfWeek.value vai de 1 (segunda) a 7 (domingo).
    private val LETRAS = listOf("S", "T", "Q", "Q", "S", "S", "D")

    /** A linha da meta no cartão. */
    fun textoMeta(s: JornadaSalva): String {
        val etapa = s.etapa ?: return ""
        val nome = etapa.titulo.lowercase()
        return when {
            emReduzir(s) -> "Meta: reduzir. ${etapa.teto}"
            s.seguro -> "Meta: nenhum dia com $nome."
            else -> "Meta: nenhum dia com $nome. Um deslize por semana é perdoado."
        }
    }

    /** Depois da linha "Semana passada": o toque nela abre os 7 dias da semana que fechou. */
    const val TEXTO_VER_SEMANA_PASSADA = "Toque para ver os dias e corrigir."

    fun textoSemanaPassada(semana: SemanaDaEtapa, h: Habito): String = when (semana.estado) {
        EstadoSemana.BOA -> "Semana passada: boa."
        EstadoSemana.RUIM -> "Semana passada: ${semana.deslizes} dias com ${h.titulo.lowercase()}; a contagem recomeça."
        else -> "Semana passada: ${semana.diasComDado} ${if (semana.diasComDado == 1) "dia lançado" else "dias lançados"}; " +
            "ela não conta nem atrapalha."
    }

    fun textoHoje(p: ProgressoEtapa, seguro: Boolean): String {
        val d = p.hoje
        val rotulo = rotuloDoDia(p.habito, d.estado, p.reduzir)
        val simbolo = simboloDoDia(d)
        return when {
            d.tocado -> "Hoje: $rotulo $simbolo (corrigido por você)"
            d.estado == EstadoDia.NA_META -> "Hoje, até agora: $rotulo $simbolo"
            d.estado == EstadoDia.SEM_DADO -> "Hoje: ainda $rotulo $simbolo"
            seguro -> "Hoje: $rotulo $simbolo"
            p.atual.deslizes <= DESLIZES_PERDOADOS -> "Hoje: $rotulo $simbolo. Um deslize por semana é perdoado."
            else -> "Hoje: $rotulo $simbolo. A próxima semana é uma nova chance."
        }
    }

    /** "Mantidos: ..." numa linha; null quando não há nenhum. */
    fun textoMantidos(s: JornadaSalva): String? {
        val m = mantidos(s)
        return if (m.isEmpty()) null else "Mantidos: ${m.joinToString(", ") { it.titulo }}."
    }

    /**
     * O fim da montagem: o que acontece ao tocar em "Começar a jornada". [vencida]: a etapa já
     * foi vencida (jaVencida) e não volta a contar.
     */
    fun textoDaEtapaNaMontagem(etapa: Habito?, seguro: Boolean, continua: Boolean, vencida: Boolean = false): String {
        val nome = etapa?.titulo?.lowercase()
        return when {
            etapa == null -> "A bebida com açúcar já está nos mantidos. A próxima etapa chega numa nova versão do app."
            vencida -> "A etapa da $nome já foi vencida e está nos mantidos. A próxima etapa chega numa nova " +
                "versão do app."
            continua -> "A etapa em curso continua de onde está."
            seguro -> "A Etapa 1 é a $nome e começa hoje. No modo seguro, você passa de etapa quando quiser."
            else -> "A Etapa 1 é a $nome e começa hoje. Você vence a etapa com 2 semanas boas seguidas: 5 dias ou " +
                "mais lançados no diário e no máximo 1 dia com $nome. Dia sem nada lançado não conta nem " +
                "atrapalha, mas 2 dias com $nome na mesma semana recomeçam a contagem. Depois, a próxima " +
                "etapa espera o seu toque."
        }
    }

    // --- Preferências em texto (JSON) ---

    fun escolhasParaTexto(e: Map<Habito, Escolha>): String {
        val o = JSONObject()
        Habito.entries.forEach { h -> e[h]?.let { o.put(h.id, it.id) } }
        return o.toString()
    }

    fun escolhasDeTexto(texto: String?): Map<Habito, Escolha> {
        val o = objeto(texto) ?: return emptyMap()
        return Habito.entries.mapNotNull { h -> Escolha.deId(o.optString(h.id))?.let { h to it } }.toMap()
    }

    fun outrosParaTexto(outros: List<OutroHabito>): String {
        val a = JSONArray()
        outros.forEach { a.put(JSONObject().put("nome", it.nome).put("escolha", it.escolha.id)) }
        return a.toString()
    }

    fun outrosDeTexto(texto: String?): List<OutroHabito> = linhas(texto).mapNotNull { o ->
        val nome = o.optString("nome").trim()
        val escolha = Escolha.deId(o.optString("escolha"))
        if (nome.isEmpty() || escolha == null) null else OutroHabito(nome, escolha)
    }

    fun vencidasParaTexto(v: List<Vencida>): String {
        val a = JSONArray()
        v.forEach { a.put(JSONObject().put("habito", it.habito.id).put("dia", it.dia)) }
        return a.toString()
    }

    fun vencidasDeTexto(texto: String?): List<Vencida> = linhas(texto).mapNotNull { o ->
        val h = Habito.deId(o.optString("habito"))
        val dia = o.optString("dia")
        if (h == null || dia.isEmpty()) null else Vencida(h, dia)
    }

    fun toquesParaTexto(t: List<Toque>): String {
        val a = JSONArray()
        t.forEach { a.put(JSONObject().put("dia", it.dia).put("habito", it.habito.id).put("estado", it.estado.id)) }
        return a.toString()
    }

    fun toquesDeTexto(texto: String?): List<Toque> = linhas(texto).mapNotNull { o ->
        val h = Habito.deId(o.optString("habito"))
        val e = EstadoDia.deId(o.optString("estado"))?.takeIf { it != EstadoDia.SEM_DADO }
        val dia = o.optString("dia")
        if (h == null || e == null || dia.isEmpty()) null else Toque(dia, h, e)
    }

    private fun objeto(texto: String?): JSONObject? =
        if (texto.isNullOrBlank()) null else try { JSONObject(texto) } catch (e: Exception) { null }

    // Texto quebrado vira lista vazia: a jornada recomeça sem derrubar o app.
    private fun linhas(texto: String?): List<JSONObject> {
        if (texto.isNullOrBlank()) return emptyList()
        val a = try { JSONArray(texto) } catch (e: Exception) { return emptyList() }
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }
}
