package com.rafaelnm90.calorias

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * A aba Diário: o resumo do dia (calorias na faixa, macros, nutrientes), o
 * check-in semanal das fases, as ferramentas de IA (sugerir refeição, ler
 * rótulo, relatório do dia), o jejum, as quatro refeições, os exercícios e a
 * água. As setas trocam o dia mostrado.
 */

private val COR_OK = Color(0xFF1B7F5C)
private val COR_AGUA = Color(0xFF2B8CC4)

@Composable
fun DiarioTela(
    dia: String,
    aoMudarDia: (String) -> Unit,
    aoAdicionar: (Refeicao) -> Unit,
    aoAbrirAjustes: () -> Unit,
) {
    val contexto = LocalContext.current
    val banco = remember { Banco.de(contexto) }
    val ajustes = remember { Ajustes.de(contexto) }
    val versao by banco.versao.collectAsStateWithLifecycle()
    val perfil by ajustes.perfil.collectAsStateWithLifecycle()
    val escopo = rememberCoroutineScope()

    val registros = remember(versao, dia) { banco.registrosDoDia(dia) }
    val exercicios = remember(versao, dia) { banco.exerciciosDoDia(dia) }
    val agua = remember(versao, dia) { banco.aguaMl(dia) }
    val ano = LocalDate.now().year
    val metaKcal = Calculos.metaKcal(perfil, ano)
    val meta = Calculos.metaMacros(metaKcal)
    val total = registros.somar()
    val micros = registros.somarMicros()
    val gastas = exercicios.sumOf { it.kcal }
    val aguaExtra = registros.aguaExtraMl()
    val metaAgua = Calculos.metaAguaMl(perfil) + aguaExtra
    val ehHoje = dia == hoje()

    var editando by remember { mutableStateOf<Registro?>(null) }
    var esperando by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var textoIa by remember { mutableStateOf<Pair<String, String>?>(null) }
    var escolhendoFotoRotulo by remember { mutableStateOf(false) }
    var descrevendoTreino by remember { mutableStateOf(false) }
    var apagandoTreino by remember { mutableStateOf<Exercicio?>(null) }

    /** Chama a IA com a mensagem de espera e mostra o texto que ela devolver. */
    fun pedirTexto(titulo: String, mensagem: String, pedido: suspend () -> String) {
        if (perfil.chaveGemini.isBlank()) {
            aviso = "Para usar a IA, cadastre a chave do Gemini em Ajustes."
            return
        }
        escopo.launch {
            esperando = mensagem
            try {
                textoIa = titulo to pedido()
            } catch (e: ErroParaMostrar) {
                aviso = e.message
            } catch (e: Exception) {
                aviso = "Algo deu errado: ${e.message}"
            } finally {
                esperando = null
            }
        }
    }

    fun sugerirRefeicao() {
        val alvo = Sugestao.alvo(metaKcal, total.kcal, LocalTime.now().hour)
        if (alvo == null) {
            aviso = "Você já atingiu (ou está muito perto) da sua meta de hoje! Que tal focar na hidratação e no descanso?"
            return
        }
        val consumido = "proteínas ${inteiro(total.prot)} g, carboidratos ${inteiro(total.carb)} g, " +
            "gorduras ${inteiro(total.gord)} g, fibras ${inteiro(micros.fibra)} g, " +
            "vitamina C ${inteiro(micros.vitaminaC)} mg, zinco ${umaCasa(micros.zinco)} mg"
        pedirTexto("💡 Sugestões para o ${alvo.periodo}", "Pensando em sugestões…") {
            Gemini.sugerirRefeicao(perfil.chaveGemini, alvo, perfil.objetivo, consumido)
        }
    }

    fun analisarRotulo(uri: Uri) = pedirTexto("🛒 Rótulo", "Lendo as letrinhas miúdas do rótulo…") {
        val jpeg = withContext(Dispatchers.IO) { Imagens.jpegReduzido(contexto, uri, ladoMax = 1600) }
        Gemini.lerRotulo(perfil.chaveGemini, jpeg)
    }

    val uriRotulo = remember {
        val pasta = File(contexto.cacheDir, "fotos").apply { mkdirs() }
        FileProvider.getUriForFile(contexto, "${contexto.packageName}.arquivos", File(pasta, "rotulo.jpg"))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) analisarRotulo(uriRotulo)
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) analisarRotulo(uri)
    }

    fun registrarTreino(descricao: String) {
        if (perfil.chaveGemini.isBlank()) {
            aviso = "Para usar a IA, cadastre a chave do Gemini em Ajustes."
            return
        }
        escopo.launch {
            esperando = "Avaliando o seu treino…"
            try {
                val (treino, resposta) = Gemini.estimarExercicio(perfil.chaveGemini, descricao, perfil.pesoKg)
                if (treino != null) {
                    banco.adicionarExercicio(dia, treino)
                    aviso = "✅ Treino registrado: ${treino.descricao}, ${treino.minutos} min, ~${treino.kcal} kcal."
                } else {
                    aviso = resposta.ifBlank { "Não entendi o exercício. Descreva de outro jeito (ex.: 40 min de esteira)." }
                }
            } catch (e: ErroParaMostrar) {
                aviso = e.message
            } catch (e: Exception) {
                aviso = "Algo deu errado: ${e.message}"
            } finally {
                esperando = null
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { CabecalhoDia(dia, aoMudarDia) }
        if (!perfil.preenchido && perfil.metaManualKcal == null) {
            item { AvisoPerfil(aoAbrirAjustes) }
        }
        if (ehHoje && CheckIn.pendente(perfil, LocalDate.now())) {
            item { CheckInCartao(perfil.fase) { fase -> ajustes.concluirCheckIn(fase) } }
        }
        item { Resumo(total, metaKcal, meta, gastas) }
        item { NutrientesCartao(micros, MetasNutrientes.de(perfil, ano)) }
        if (ehHoje) {
            item {
                Ferramentas(
                    aoSugerir = { sugerirRefeicao() },
                    aoLerRotulo = { escolhendoFotoRotulo = true },
                    aoRelatorio = {
                        pedirTexto("📈 Relatório do dia", "Revendo as anotações do seu dia…") {
                            Gemini.relatorioDoDia(perfil.chaveGemini, perfil.objetivo, metaKcal, total, agua, metaAgua, gastas)
                        }
                    },
                )
            }
            item { JejumCartao(banco, perfil.metaJejumHoras, versao) }
        }
        items(Refeicao.entries) { r ->
            RefeicaoCartao(
                refeicao = r,
                registros = registros.filter { it.refeicao == r },
                aoAdicionar = { aoAdicionar(r) },
                aoTocar = { editando = it },
            )
        }
        item {
            ExerciciosCartao(
                exercicios,
                aoAdicionar = { descrevendoTreino = true },
                aoTocar = { apagandoTreino = it },
            )
        }
        item { AguaCartao(agua, metaAgua, aguaExtra, perfil.copoMl, aoSomar = { banco.somarAgua(dia, it) }) }
    }

    editando?.let { r ->
        EditarRegistro(
            registro = r,
            aoSalvar = { gramas -> banco.mudarGramas(r.id, gramas) },
            aoApagar = { banco.apagarRegistro(r.id) },
            aoFechar = { editando = null },
        )
    }
    if (escolhendoFotoRotulo) {
        AlertDialog(
            onDismissRequest = { escolhendoFotoRotulo = false },
            title = { Text("Ler rótulo") },
            text = { Text("Fotografe a lista de ingredientes da embalagem. A IA marca cada um e sugere marcas mais saudáveis.") },
            confirmButton = {
                TextButton(onClick = {
                    escolhendoFotoRotulo = false
                    camera.launch(uriRotulo)
                }) { Text("Tirar foto") }
            },
            dismissButton = {
                TextButton(onClick = {
                    escolhendoFotoRotulo = false
                    galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Escolher da galeria") }
            },
        )
    }
    if (descrevendoTreino) {
        PedirTexto(
            titulo = "Qual foi o treino?",
            exemplo = "Ex.: corri na esteira por 40 minutos",
            botao = "Registrar",
            aoEnviar = { registrarTreino(it) },
            aoFechar = { descrevendoTreino = false },
        )
    }
    apagandoTreino?.let { e ->
        AlertDialog(
            onDismissRequest = { apagandoTreino = null },
            title = { Text(e.descricao) },
            text = { Text("${e.minutos} min, ~${e.kcal} kcal gastas.") },
            confirmButton = { TextButton(onClick = { apagandoTreino = null }) { Text("OK") } },
            dismissButton = {
                TextButton(onClick = {
                    banco.apagarExercicio(e.id)
                    apagandoTreino = null
                }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
    textoIa?.let { (titulo, texto) -> TextoDaIa(titulo, texto) { textoIa = null } }
    esperando?.let { Esperando(it) }
    aviso?.let { Aviso(it) { aviso = null } }
}

@Composable
private fun CabecalhoDia(dia: String, aoMudarDia: (String) -> Unit) {
    val data = LocalDate.parse(dia)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { aoMudarDia(data.minusDays(1).toString()) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Dia anterior")
        }
        Text(
            tituloDoDia(dia),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .clickable { aoMudarDia(hoje()) },
        )
        IconButton(
            onClick = { aoMudarDia(data.plusDays(1).toString()) },
            enabled = data.isBefore(LocalDate.now()),
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo dia")
        }
    }
}

@Composable
private fun AvisoPerfil(aoAbrirAjustes: () -> Unit) {
    Cartao {
        Text("Complete seu perfil", fontWeight = FontWeight.Bold)
        Text(
            "Com altura, peso e objetivo, o app calcula a sua meta de calorias e de água. Até lá, a meta é 2.000 kcal.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = aoAbrirAjustes) { Text("Abrir Ajustes") }
    }
}

@Composable
private fun CheckInCartao(fase: Fase, aoEscolherFase: (Fase) -> Unit) {
    var conselho by remember { mutableStateOf<Conselho?>(null) }
    Cartao {
        Text("🗓️ Check-in semanal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val c = conselho
        if (c == null) {
            Text(
                "Você completou 7 dias na Fase ${fase.numero} (${fase.nome}). Como estão a fome e a energia nos últimos dias?",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Sensacao.entries.forEach { s ->
                OutlinedButton(onClick = { conselho = CheckIn.conselho(s, fase) }, modifier = Modifier.fillMaxWidth()) {
                    Text(s.titulo)
                }
            }
        } else {
            Text("📝 ${c.diagnostico}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(c.recomendacao, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            c.fases.forEach { f ->
                val rotulo = when {
                    f == fase -> "🧘 Manter a Fase ${f.numero}"
                    f.numero < fase.numero -> "🔙 Recuar para a Fase ${f.numero} (${f.nome})"
                    else -> "🚀 Avançar para a Fase ${f.numero} (${f.nome})"
                }
                Button(onClick = { aoEscolherFase(f) }, modifier = Modifier.fillMaxWidth()) { Text(rotulo) }
            }
            if (Fase.DRASTICA in c.fases && fase != Fase.DRASTICA) {
                Text(
                    "🚨 A Fase 3 é extrema: não use por mais de 2 semanas seguidas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Resumo(total: Macros, metaKcal: Int, meta: Macros, gastas: Int) {
    val restantes = metaKcal - total.kcal
    val faixa = Calculos.faixaKcal(metaKcal)
    Cartao {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Numero(inteiro(total.kcal), "Consumidas")
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { fracao(total.kcal, metaKcal.toDouble()) },
                    modifier = Modifier.size(132.dp),
                    strokeWidth = 10.dp,
                    color = if (total.kcal > faixa.max) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        inteiro(kotlin.math.abs(restantes)),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(if (restantes < 0) "Acima" else "Restantes", style = MaterialTheme.typography.bodySmall)
                }
            }
            Numero(inteiro(gastas.toDouble()), "Gastas")
        }
        Spacer(Modifier.height(8.dp))
        val (icone, frase, cor) = when {
            total.kcal > faixa.max ->
                Triple("❌", "Acima da faixa: passou ${inteiro(total.kcal - faixa.max)} kcal do teto.", MaterialTheme.colorScheme.error)
            total.kcal < faixa.min ->
                Triple(
                    "❌",
                    "Faltam pelo menos ${inteiro(faixa.min - total.kcal)} kcal para entrar na faixa.",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else -> Triple("✅", "Na faixa ideal! Excelente controle.", COR_OK)
        }
        Text(
            "🎯 Faixa do dia: ${inteiro(faixa.min)} a ${inteiro(faixa.max)} kcal (meta ${inteiro(metaKcal.toDouble())})",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Text("$icone $frase", style = MaterialTheme.typography.bodyMedium, color = cor)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BarraMacro("Proteína", total.prot, Calculos.faixaProt(meta), Modifier.weight(1f))
            BarraMacro("Carboidratos", total.carb, Calculos.faixaCarb(meta), Modifier.weight(1f))
            BarraMacro("Gordura", total.gord, Calculos.faixaGord(meta), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "O que vale é ficar dentro da faixa: abaixo dela você perde massa magra, e acima acumula gordura.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Numero(valor: String, rotulo: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
        Text(valor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(rotulo, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BarraMacro(nome: String, valor: Double, faixa: Faixa, modifier: Modifier) {
    val icone = when {
        valor in faixa -> "✅"
        valor > faixa.max -> "❌"
        else -> ""
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$nome $icone", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fracao(valor, faixa.max) },
            modifier = Modifier.fillMaxWidth(),
            color = if (valor > faixa.max) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(4.dp))
        Text("${inteiro(valor)} g", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text(
            "${inteiro(faixa.min)} a ${inteiro(faixa.max)} g",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun fracao(valor: Double, meta: Double): Float =
    if (meta <= 0) 0f else (valor / meta).toFloat().coerceIn(0f, 1f)

@Composable
private fun NutrientesCartao(micros: Micros, metas: List<MetaNutriente>) {
    var aberto by remember { mutableStateOf(false) }
    val fora = metas.count { !it.ok(it.ler(micros)) }
    Cartao {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { aberto = !aberto },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("🔬 Nutrientes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (fora == 0) "Todos dentro da meta" else "$fora fora da meta",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(if (aberto) "Fechar ▴" else "Ver ▾", color = MaterialTheme.colorScheme.primary)
        }
        if (aberto) {
            Spacer(Modifier.height(8.dp))
            metas.forEach { m ->
                val valor = m.ler(micros)
                val ok = m.ok(valor)
                val alvo = when (m.tipo) {
                    TipoMeta.MINIMO -> "mín. ${inteiro(m.valor)} ${m.unidade}"
                    TipoMeta.MAXIMO -> "máx. ${inteiro(m.valor)} ${m.unidade}"
                    TipoMeta.FAIXA -> "${inteiro(m.valor)} a ${inteiro(m.teto ?: m.valor)} ${m.unidade}"
                }
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${m.emoji} ${m.nome}", Modifier.weight(1f))
                    Text(
                        "${if (valor < 10) umaCasa(valor) else inteiro(valor)} ${m.unidade}",
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$alvo ${if (ok) "✅" else "❌"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { fracao(valor, m.teto ?: m.valor) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (ok) COR_OK else MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Valores estimados. A tabela TACO não traz açúcar: ele só conta nos itens da IA e do código de barras.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Ferramentas(aoSugerir: () -> Unit, aoLerRotulo: () -> Unit, aoRelatorio: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BotaoFerramenta("💡", "Sugerir refeição", Modifier.weight(1f), aoSugerir)
        BotaoFerramenta("🛒", "Ler rótulo", Modifier.weight(1f), aoLerRotulo)
        BotaoFerramenta("📈", "Relatório do dia", Modifier.weight(1f), aoRelatorio)
    }
}

@Composable
private fun BotaoFerramenta(emoji: String, rotulo: String, modifier: Modifier, aoTocar: () -> Unit) {
    OutlinedButton(onClick = aoTocar, modifier = modifier, contentPadding = PaddingValues(6.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(rotulo, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
    }
}

private val HORA = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun JejumCartao(banco: Banco, metaHoras: Int, versao: Int) {
    val atual = remember(versao) { banco.jejumAtual() }
    val ultimo = remember(versao) { banco.ultimoJejumEncerrado() }
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pedindoHora by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            agora = System.currentTimeMillis()
        }
    }

    Cartao {
        if (atual != null) {
            val passou = agora - atual.inicio
            val metaMs = metaHoras * 3_600_000L
            val fim = LocalDateTime.ofInstant(Instant.ofEpochMilli(atual.inicio + metaMs), ZoneId.systemDefault())
            Text("⏳ Em jejum", fontWeight = FontWeight.Bold)
            Text(duracao(passou), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { fracao(passou.toDouble(), metaMs.toDouble()) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (passou >= metaMs) {
                    "Meta de $metaHoras h cumprida! 🎉"
                } else {
                    "Meta de $metaHoras h: termina às ${fim.format(HORA)}"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { banco.encerrarJejum() }) { Text("Encerrar jejum") }
                OutlinedButton(onClick = { pedindoHora = true }) { Text("Mudar início") }
            }
        } else {
            Text("🍽️ Janela de comer", fontWeight = FontWeight.Bold)
            val fimUltimo = ultimo?.fim
            if (ultimo != null && fimUltimo != null) {
                Text("Último jejum: ${duracao(fimUltimo - ultimo.inicio)}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { banco.iniciarJejum() }) { Text("Começar jejum") }
                OutlinedButton(onClick = { pedindoHora = true }) { Text("Comecei antes") }
            }
        }
    }

    if (pedindoHora) {
        PedirHora(
            titulo = if (atual != null) "Que horas o jejum começou?" else "Que horas você parou de comer?",
            aoConfirmar = { inicio ->
                if (atual != null) banco.mudarInicioDoJejum(inicio) else banco.iniciarJejum(inicio)
            },
            aoFechar = { pedindoHora = false },
        )
    }
}

/** Pede um horário (ex.: 20:30). Horário depois de agora vale como de ontem. */
@Composable
private fun PedirHora(titulo: String, aoConfirmar: (Long) -> Unit, aoFechar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = {
                    texto = it
                    erro = false
                },
                label = { Text("Horário (ex.: 20:30)") },
                isError = erro,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val hora = lerHora(texto)
                if (hora == null) {
                    erro = true
                } else {
                    var quando = LocalDateTime.of(LocalDate.now(), hora)
                    if (quando.isAfter(LocalDateTime.now())) quando = quando.minusDays(1)
                    aoConfirmar(quando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    aoFechar()
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

/** "20:30", "20h30", "20.30" ou "20" viram 20:30 / 20:00. */
private fun lerHora(texto: String): LocalTime? {
    val partes = texto.trim().split(':', 'h', 'H', '.', ',').filter { it.isNotBlank() }
    val h = partes.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
    val m = partes.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
    if (h !in 0..23 || m !in 0..59) return null
    return LocalTime.of(h, m)
}

@Composable
private fun RefeicaoCartao(
    refeicao: Refeicao,
    registros: List<Registro>,
    aoAdicionar: () -> Unit,
    aoTocar: (Registro) -> Unit,
) {
    val soma = registros.somar()
    Cartao {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(refeicao.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(refeicao.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${inteiro(soma.kcal)} kcal", style = MaterialTheme.typography.bodyMedium)
            }
            FilledIconButton(onClick = aoAdicionar) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar ao ${refeicao.titulo}")
            }
        }
        if (registros.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            registros.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { aoTocar(r) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.alimento.nome, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${paraCampo(r.gramas)} ${unidade(r.alimento)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("${inteiro(r.kcal)} kcal", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ExerciciosCartao(exercicios: List<Exercicio>, aoAdicionar: () -> Unit, aoTocar: (Exercicio) -> Unit) {
    Cartao {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏋️", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Exercícios", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${exercicios.sumOf { it.kcal }} kcal gastas", style = MaterialTheme.typography.bodyMedium)
            }
            FilledIconButton(onClick = aoAdicionar) {
                Icon(Icons.Filled.Add, contentDescription = "Registrar exercício")
            }
        }
        exercicios.forEach { e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { aoTocar(e) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.descricao, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${e.minutos} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("~${e.kcal} kcal", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AguaCartao(agua: Int, metaMl: Int, extraMl: Int, copoMl: Int, aoSomar: (Int) -> Unit) {
    Cartao {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "💧 Água ${if (agua >= metaMl) "✅" else ""}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${inteiro(agua.toDouble())} / ${inteiro(metaMl.toDouble())} ml",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedButton(onClick = { aoSomar(-copoMl) }, enabled = agua > 0) { Text("−") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { aoSomar(copoMl) }) { Text("+ $copoMl ml") }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { fracao(agua.toDouble(), metaMl.toDouble()) },
            modifier = Modifier.fillMaxWidth(),
            color = COR_AGUA,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
        if (extraMl > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "⚠️ +${inteiro(extraMl.toDouble())} ml na meta de hoje por causa de bebida alcoólica ou refrigerante.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EditarRegistro(
    registro: Registro,
    aoSalvar: (Double) -> Unit,
    aoApagar: () -> Unit,
    aoFechar: () -> Unit,
) {
    var gramas by remember { mutableStateOf(paraCampo(registro.gramas)) }
    val valor = lerNumero(gramas)
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(registro.alimento.nome) },
        text = {
            Column {
                CampoNumero(gramas, { gramas = it }, "Quantidade", sufixo = unidade(registro.alimento))
                if (valor != null) {
                    Text("${inteiro(registro.alimento.kcal(valor))} kcal")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valor != null && valor > 0) aoSalvar(valor)
                    aoFechar()
                },
                enabled = valor != null && valor > 0,
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = {
                aoApagar()
                aoFechar()
            }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
        },
    )
}

/** Diálogo com um campo de texto livre (treino, correção, item esquecido). */
@Composable
fun PedirTexto(titulo: String, exemplo: String, botao: String, aoEnviar: (String) -> Unit, aoFechar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                placeholder = { Text(exemplo) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    aoEnviar(texto.trim())
                    aoFechar()
                },
                enabled = texto.isNotBlank(),
            ) { Text(botao) }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}
