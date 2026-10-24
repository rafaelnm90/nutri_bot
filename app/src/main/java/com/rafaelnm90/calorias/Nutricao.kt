package com.rafaelnm90.calorias

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Regras de nutrição do app, sem nada de tela nem de Android: metas de calorias,
 * macros, água e micronutrientes, fases da dieta e check-in semanal, busca de
 * alimentos, leitura das respostas da IA e a decisão dos lembretes. Usado pelas
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

enum class Refeicao(val titulo: String, val emoji: String) {
    CAFE("Café da manhã", "☕"),
    ALMOCO("Almoço", "🍛"),
    JANTAR("Jantar", "🍲"),
    LANCHES("Lanches", "🍎"),
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
    /** Palavras que marcam bebida alcoólica ou refrigerante (inclusive zero). */
    private val PALAVRAS = listOf(
        "cerveja", "chope", "chopp", "vinho", "espumante", "champanhe", "cachaca", "caipirinha",
        "caipiroska", "vodka", "whisky", "uisque", "gin", "rum", "tequila", "licor", "conhaque",
        "sake", "drink", "batida", "refrigerante", "coca", "pepsi", "fanta", "sprite", "soda", "tonica",
        "guarana", "aguardente", "mojito", "mule", "cuba", "negroni", "aperol", "spritz", "sangria", "quentao",
        "daiquiri",
    )

    /** Comidas que levam bebida no preparo ou no nome: não pedem água. */
    private val PREPAROS = listOf(
        "molho", "vinagre", "xarope", "bolo", "sorvete", "calda", "bicarbonato", "pao", "bala",
    )

    /** "Frango ao vinho", "Costela na cerveja": a bebida depois destas palavras tempera o prato. */
    private val LIGACOES = setOf("ao", "aos", "a", "as", "na", "nas", "no", "nos", "com", "em")

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
        // Nome que começa por bebida ("Suco com vodka", "Gin com tônica") é bebida inteiro;
        // nos outros, a bebida logo depois de "ao", "na", "com"... é do preparo.
        val primeira = doNome.firstOrNull() ?: return false
        val comecaPorBebida = ehAlcoolOuRefri(primeira) || primeira in OUTRAS_BEBIDAS
        return doNome.indices.any { i ->
            ehAlcoolOuRefri(doNome[i]) && (comecaPorBebida || i == 0 || doNome[i - 1] !in LIGACOES)
        }
    }

    // "refri" só como palavra inteira, para "refrito" não contar.
    private fun ehAlcoolOuRefri(palavra: String) =
        palavra == "refri" || PALAVRAS.any { p -> palavra == p || (p.length >= 5 && palavra.startsWith(p)) }
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
     * Lê o taco.json gerado pelo workflow taco.yml a partir da tabela oficial:
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
