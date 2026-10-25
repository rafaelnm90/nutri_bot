package com.rafaelnm90.calorias

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/*
 * As consultas do app pela internet: o Gemini (alimentos de uma foto ou
 * descrição, correção de item, sugestão de refeição, rótulo, relatório do dia e
 * exercício) e o Open Food Facts (produto pelo código de barras). Os textos
 * pedidos à IA vêm do nutri_bot do Rafael (DECISOES.md, "IA").
 */

/** Falha que a tela mostra como está: o texto já vem pronto para a pessoa. */
class ErroParaMostrar(mensagem: String) : Exception(mensagem)

object Gemini {
    /** Ordem de tentativa: o primeiro que responder vence. Os "flash" têm cota grátis maior. */
    private val MODELOS = listOf(
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-3-flash-preview",
        "gemini-2.5-flash",
        "gemini-3.5-flash-lite",
        "gemini-2.5-flash-lite",
    )

    private const val FORMATO_ITENS = """Responda só com JSON, neste formato:
{"e_comida": true, "resposta": "", "itens": [{"nome": "Arroz branco cozido", "gramas": 150, "kcal": 192, "proteina": 3.8, "carboidrato": 42.1, "gordura": 0.3, "fibra": 2.4, "acucar": 0.1, "sodio_mg": 2, "calcio_mg": 6, "ferro_mg": 0.2, "zinco_mg": 0.8, "potassio_mg": 23, "vitamina_c_mg": 0, "vitamina_a_mcg": 0, "agua_extra_ml": 0}]}
Os valores são da quantidade do item (não de 100 g). Use nomes curtos em português.
Use como referência a Tabela Brasileira de Composição de Alimentos (TACO) e, para produtos
industrializados, o rótulo comum no Brasil. Alimentos cozidos: valores do alimento cozido.
REGRA DA ÁGUA: se o item for bebida alcoólica de qualquer tipo ou refrigerante (mesmo zero),
"agua_extra_ml" é igual ao volume bebido em ml (ex.: 330 ml de cerveja = 330). Para o resto, 0."""

    private const val TEXTO_SIMPLES =
        "Responda em texto simples, sem markdown e sem asteriscos, com linhas curtas e alguns emojis."

    suspend fun estimarFoto(chave: String, jpeg: ByteArray): RespostaIa = LeitorIa.ler(
        pedir(
            chave,
            """Você é um nutricionista experiente, de olho clínico, ajudando a preencher um diário alimentar no Brasil.
Veja qual destes cenários se aplica à foto:

CENÁRIO 1, prato ou alimento visível (refeição, bebida, suplemento): identifique cada alimento separadamente.
Para os gramas, analise a textura e use as bordas do prato, copos, talheres ou mãos visíveis como régua.
Não chute valores genéricos.

CENÁRIO 2, embalagem com tabela nutricional visível: leia os valores da tabela ao pé da letra, para a porção indicada.

CENÁRIO 3, não é comida (ou é só um código de barras): "e_comida": false, "itens": [] e, em "resposta",
um comentário curto e amigável.

$FORMATO_ITENS""",
            jpeg,
            json = true,
        )
    )

    suspend fun estimarTexto(chave: String, descricao: String): RespostaIa = LeitorIa.ler(
        pedir(
            chave,
            """Você é um nutricionista experiente ajudando a preencher um diário alimentar no Brasil.
A pessoa escreveu: "${aspas(descricao)}"

Se ela conta o que comeu ou bebeu, liste cada alimento separadamente, com as quantidades que ela disse;
sem quantidade, use uma porção comum.
Se a mensagem não for sobre comida: "e_comida": false, "itens": [] e uma resposta curta e amigável em "resposta".

$FORMATO_ITENS""",
            null,
            json = true,
        )
    )

    /** Corrige um item da revisão pelo que a pessoa escreveu ("não foi pão de queijo, foi pão de leite"). */
    suspend fun corrigirItem(chave: String, item: ItemEstimado, correcao: String): RespostaIa = LeitorIa.ler(
        pedir(
            chave,
            """Você é um nutricionista experiente ajudando a preencher um diário alimentar no Brasil.
Um item foi estimado assim: ${item.nome}, ${item.gramas.toInt()} g, ${item.kcal.toInt()} kcal.
A pessoa corrigiu: "${aspas(correcao)}"

Entenda a troca de alimento ou o ajuste de quantidade (ex.: "não foi pão de queijo e sim pão de leite",
"não foi 80 g e sim 100 g", "eram 2 pedaços", "foi frito e não assado") e devolva o item corrigido, com gramas,
calorias e nutrientes recalculados. Se a correção pedir mais de um item, devolva todos.

$FORMATO_ITENS""",
            null,
            json = true,
        )
    )

    suspend fun sugerirRefeicao(chave: String, alvo: AlvoRefeicao, objetivo: Objetivo, consumido: String): String {
        val meta = when (objetivo) {
            Objetivo.PERDER -> "garantir o déficit calórico para emagrecer"
            Objetivo.GANHAR -> "atingir o superávit calórico para ganhar massa muscular"
            Objetivo.MANTER -> "manter o peso atual"
        }
        return pedir(
            chave,
            """Você é um nutricionista experiente.
A pessoa tem cerca de ${alvo.kcal} kcal para o ${alvo.periodo}, com o objetivo de $meta.
Até agora, hoje, ela consumiu: $consumido.
Atue de forma compensatória: se notar excesso de gordura, sugira opções magras; se faltar proteína,
fibras ou vitamina C, priorize alimentos ricos nelas.

Sugira 3 opções de refeição saudáveis, práticas e do dia a dia no Brasil, cuja soma fique perto de ${alvo.kcal} kcal.
Para cada uma, dê as porções em gramas ou medidas caseiras, o total de calorias e uma frase de por que ela
equilibra o dia. Seja encorajador.
$TEXTO_SIMPLES""",
            null,
            json = false,
        )
    }

    /** Análise dos ingredientes da embalagem, no estilo do app Desrotulando. */
    suspend fun lerRotulo(chave: String, jpeg: ByteArray): String = pedir(
        chave,
        """Você é um nutricionista rigoroso analisando rótulos no estilo do aplicativo Desrotulando.
A pessoa está no mercado em Minas Gerais e precisa de uma análise direta e completa. Sem saudações.

Responda exatamente nesta estrutura:

🔍 Raio-X dos ingredientes
(Cada ingrediente da embalagem numa linha: ✅ para os naturais e inofensivos, ❌ para os ultraprocessados,
aditivos ou prejudiciais, com o motivo em até 3 palavras nos ❌.)

📊 Avaliação geral
(Um selo: 🟢 Excelente, 🟡 Bom, 🟠 Atenção ou 🔴 Ruim/Ultraprocessado, e uma frase sobre o impacto na saúde.)

🛒 Alternativas mais saudáveis
(Opções mais limpas do mesmo produto, com marcas reais:
💰 Custo-benefício: marca acessível e fácil de achar.
⭐ Premium: marca de melhor qualidade, independente do preço.)

Se a foto não mostrar ingredientes de um produto, diga isso em uma frase.
$TEXTO_SIMPLES""",
        jpeg,
        json = false,
    )

    suspend fun relatorioDoDia(
        chave: String,
        objetivo: Objetivo,
        metaKcal: Int,
        total: Macros,
        aguaMl: Int,
        metaAguaMl: Int,
        exercicioKcal: Int,
    ): String = pedir(
        chave,
        """Aja como um nutricionista clínico analítico e encorajador. Avalie o dia de hoje do seu paciente.

DADOS DE HOJE:
- Objetivo: ${objetivo.titulo}
- Meta de calorias: $metaKcal kcal
- Consumo: ${total.kcal.toInt()} kcal
- Proteínas ${total.prot.toInt()} g, carboidratos ${total.carb.toInt()} g, gorduras ${total.gord.toInt()} g
- Água: $aguaMl ml de uma meta de $metaAguaMl ml
- Exercícios: $exercicioKcal kcal gastas

Comece com um cumprimento de fim de dia. Avalie o saldo de calorias (se cumpriu o déficit ou superávit
planejado), se a proteína foi suficiente para manter a massa magra, o equilíbrio dos outros macros e a
hidratação. Termine com um conselho prático ou uma palavra de motivação para amanhã. Evite jargões.
$TEXTO_SIMPLES""",
        null,
        json = false,
    )

    suspend fun estimarExercicio(chave: String, descricao: String, pesoKg: Double): Pair<Exercicio?, String> {
        val peso = if (pesoKg > 0) pesoKg.toInt() else 70
        return LeitorIa.lerExercicio(
            pedir(
                chave,
                """Você é um personal trainer e nutricionista experiente.
A pessoa escreveu: "${aspas(descricao)}"

Se é um exercício físico: "e_exercicio": true, a descrição curta, a duração em minutos (sem duração dita,
30) e as calorias gastas por uma pessoa de $peso kg. Referência para 70 kg (ajuste ao peso):
caminhada leve ~4 kcal/min, corrida moderada ~10, musculação ~6, ciclismo moderado ~8, natação ~9, HIIT ~12.
Se não é exercício: "e_exercicio": false, minutos e kcal 0, e uma resposta curta e amigável em "resposta".

Responda só com JSON:
{"e_exercicio": true, "descricao": "Corrida em ritmo moderado", "minutos": 30, "kcal": 300, "resposta": ""}""",
                null,
                json = true,
            )
        )
    }

    private fun aspas(texto: String) = texto.replace("\"", "'")

    /** Manda o pedido ao primeiro modelo que responder e devolve o texto da resposta. */
    private suspend fun pedir(chave: String, texto: String, jpeg: ByteArray?, json: Boolean): String =
        withContext(Dispatchers.IO) {
            if (chave.isBlank()) {
                throw ErroParaMostrar("Falta a chave do Gemini. Cadastre em Ajustes.")
            }
            val partes = JSONArray().put(JSONObject().put("text", texto))
            if (jpeg != null) {
                partes.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", "image/jpeg")
                            .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP)),
                    )
                )
            }
            val config = JSONObject().put("temperature", if (json) 0.2 else 0.7)
            if (json) config.put("responseMimeType", "application/json")
            val corpo = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", partes)))
                .put("generationConfig", config)
                .toString()

            var ultimoErro = "nenhum modelo respondeu"
            var esperaCota: String? = null
            for (modelo in MODELOS) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelo:generateContent"
                val resposta = try {
                    postar(url, corpo, mapOf("x-goog-api-key" to chave.trim()))
                } catch (e: IOException) {
                    throw ErroParaMostrar("Sem internet para falar com o Gemini. Tente de novo.")
                }
                when {
                    resposta.codigo == 200 -> {
                        val textoIa = textoDaResposta(resposta.corpo)
                        if (textoIa.isNotBlank()) return@withContext textoIa.trim()
                        ultimoErro = "resposta vazia"
                    }
                    resposta.codigo == 400 && "API key" in resposta.corpo ->
                        throw ErroParaMostrar("O Gemini recusou a chave. Confira a chave em Ajustes.")
                    resposta.codigo == 403 ->
                        throw ErroParaMostrar("A chave do Gemini não tem permissão. Confira a chave em Ajustes.")
                    // Modelo que não existe (404), sem cota (429) ou fora do ar (5xx): tenta o próximo.
                    else -> {
                        ultimoErro = "erro ${resposta.codigo} no $modelo"
                        if (resposta.codigo == 429) {
                            esperaCota = Regex("\"retryDelay\"\\s*:\\s*\"(\\d+)").find(resposta.corpo)?.groupValues?.get(1)
                        }
                    }
                }
            }
            if (esperaCota != null) {
                throw ErroParaMostrar("A cota grátis do Gemini acabou por agora. Ela volta em $esperaCota segundos: tente de novo depois.")
            }
            throw ErroParaMostrar("O Gemini não respondeu agora ($ultimoErro). Tente daqui a pouco.")
        }

    /** Junta o texto das partes da primeira resposta, sem as partes de raciocínio. */
    private fun textoDaResposta(json: String): String {
        val partes = JSONObject(json)
            .optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val texto = StringBuilder()
        for (i in 0 until partes.length()) {
            val parte = partes.optJSONObject(i) ?: continue
            if (parte.optBoolean("thought", false)) continue
            texto.append(parte.optString("text"))
        }
        return texto.toString()
    }
}

object OpenFoodFacts {
    // Só álcool e refrigerante: "en:carbonated-drinks" inclui água com gás.
    private val CATEGORIAS_QUE_PEDEM_AGUA = listOf("en:alcoholic-beverages", "en:sodas")

    /**
     * O produto pelo código de barras, com valores de 100 g, ou null se a base não
     * tem o produto ou não tem as calorias dele.
     */
    suspend fun buscar(codigo: String): Alimento? = withContext(Dispatchers.IO) {
        val url = "https://world.openfoodfacts.org/api/v2/product/$codigo.json" +
            "?fields=product_name,product_name_pt,brands,nutriments,serving_size,serving_quantity,categories_tags"
        val resposta = try {
            obter(url)
        } catch (e: IOException) {
            throw ErroParaMostrar("Sem internet para buscar o código de barras. Tente de novo.")
        }
        if (resposta.codigo == 404) return@withContext null
        if (resposta.codigo != 200) {
            throw ErroParaMostrar("O Open Food Facts não respondeu (erro ${resposta.codigo}). Tente de novo.")
        }
        lerProduto(codigo, resposta.corpo)
    }

    fun lerProduto(codigo: String, json: String): Alimento? {
        val raiz = JSONObject(json)
        if (raiz.optInt("status", 0) != 1) return null
        val produto = raiz.optJSONObject("product") ?: return null
        val n = produto.optJSONObject("nutriments") ?: return null
        var kcal = n.optDouble("energy-kcal_100g", Double.NaN)
        if (kcal.isNaN()) {
            // Só a energia em kJ: 1 kcal = 4,184 kJ.
            val kj = n.optDouble("energy_100g", Double.NaN)
            if (kj.isNaN()) return null
            kcal = kj / 4.184
        }
        val nome = produto.optString("product_name_pt").ifBlank { produto.optString("product_name") }
            .ifBlank { "Produto $codigo" }
        val marca = produto.optString("brands").substringBefore(",").trim()
        val porcao = produto.optDouble("serving_quantity", Double.NaN)
        val categorias = produto.optJSONArray("categories_tags")
        val bebidaQuePedeAgua = categorias != null &&
            (0 until categorias.length()).any { categorias.optString(it) in CATEGORIAS_QUE_PEDEM_AGUA }
        return Alimento(
            nome = if (marca.isNotEmpty() && !nome.contains(marca, ignoreCase = true)) "$nome ($marca)" else nome,
            kcal100 = kcal,
            prot100 = n.zeroSeFaltar("proteins_100g"),
            carb100 = n.zeroSeFaltar("carbohydrates_100g"),
            gord100 = n.zeroSeFaltar("fat_100g"),
            // A base guarda minerais e vitaminas em gramas.
            micros100 = Micros(
                fibra = n.zeroSeFaltar("fiber_100g"),
                acucar = n.zeroSeFaltar("sugars_100g"),
                sodio = n.zeroSeFaltar("sodium_100g") * 1000,
                calcio = n.zeroSeFaltar("calcium_100g") * 1000,
                ferro = n.zeroSeFaltar("iron_100g") * 1000,
                zinco = n.zeroSeFaltar("zinc_100g") * 1000,
                potassio = n.zeroSeFaltar("potassium_100g") * 1000,
                vitaminaC = n.zeroSeFaltar("vitamin-c_100g") * 1000,
                vitaminaA = n.zeroSeFaltar("vitamin-a_100g") * 1_000_000,
            ),
            pedeAgua = bebidaQuePedeAgua || Bebidas.pedeAgua(nome),
            porcaoG = porcao.takeIf { !it.isNaN() && it > 0 },
            porcaoNome = produto.optString("serving_size").ifBlank { null },
            origem = ORIGEM_OFF,
            codigoBarras = codigo,
        )
    }

    private fun JSONObject.zeroSeFaltar(chave: String): Double {
        val v = optDouble(chave, 0.0)
        return if (v.isNaN() || v < 0) 0.0 else v
    }
}

private class Resposta(val codigo: Int, val corpo: String)

private fun abrir(url: String): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 90_000
        setRequestProperty("User-Agent", "Calorias-app/1.0 (app pessoal)")
    }

private fun ler(conexao: HttpURLConnection): Resposta {
    val codigo = conexao.responseCode
    val fluxo = if (codigo in 200..299) conexao.inputStream else conexao.errorStream
    val corpo = fluxo?.bufferedReader()?.use { it.readText() } ?: ""
    conexao.disconnect()
    return Resposta(codigo, corpo)
}

private fun obter(url: String): Resposta = ler(abrir(url))

private fun postar(url: String, corpo: String, cabecalhos: Map<String, String>): Resposta {
    val conexao = abrir(url)
    conexao.requestMethod = "POST"
    conexao.doOutput = true
    conexao.setRequestProperty("Content-Type", "application/json; charset=utf-8")
    cabecalhos.forEach { (k, v) -> conexao.setRequestProperty(k, v) }
    conexao.outputStream.use { it.write(corpo.toByteArray(Charsets.UTF_8)) }
    return ler(conexao)
}
