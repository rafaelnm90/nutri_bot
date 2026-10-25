package com.rafaelnm90.calorias

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Tela de adicionar alimento a uma refeição. Quatro caminhos:
 *   - busca nos recentes, nos alimentos criados e na tabela TACO;
 *   - foto do prato (câmera ou galeria), que o Gemini transforma em itens;
 *   - código de barras, procurado nos alimentos já lidos e no Open Food Facts;
 *   - descrição em texto ("2 ovos e 1 pão francês"), também pelo Gemini.
 * O que a IA estima passa por uma revisão antes de entrar no diário.
 */

/**
 * [hojeAgora] é o dia de hoje que o app viu ao voltar para a frente: muda depois da
 * meia-noite e refaz o título, que passa a mostrar o dia da comida.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdicionarTela(dia: String, hojeAgora: String, refeicao: Refeicao, aoVoltar: () -> Unit) {
    val contexto = LocalContext.current
    val banco = remember { Banco.de(contexto) }
    val ajustes = remember { Ajustes.de(contexto) }
    val perfil by ajustes.perfil.collectAsStateWithLifecycle()
    val versao by banco.versao.collectAsStateWithLifecycle()
    val escopo = rememberCoroutineScope()
    val taco = remember { TacoNoApp.de(contexto) }

    var termo by rememberSaveable { mutableStateOf("") }
    val recentes = remember(versao) { banco.recentes() }
    val meus = remember(versao) { banco.meusAlimentos() }
    val resultados = remember(termo, versao) {
        if (termo.isBlank()) emptyList() else Busca.buscar(recentes + meus + taco, termo)
    }

    var escolhido by remember { mutableStateOf<Alimento?>(null) }
    val revisados = remember { mutableStateListOf<ItemRevisado>() }
    var revisando by remember { mutableStateOf(false) }
    var corrigindo by remember { mutableStateOf<Int?>(null) }
    var acrescentando by remember { mutableStateOf(false) }
    var esperando by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var criandoComCodigo by remember { mutableStateOf<String?>(null) }
    var criando by remember { mutableStateOf(false) }
    var descrevendo by remember { mutableStateOf(false) }
    var escolhendoFoto by remember { mutableStateOf(false) }
    var adicionados by remember { mutableIntStateOf(0) }

    fun adicionar(alimento: Alimento, gramas: Double) {
        banco.adicionar(dia, refeicao, alimento, gramas)
        adicionados++
    }

    /** Chama a IA; com itens, entrega a aoReceber; sem itens, mostra o que ela respondeu. */
    fun pedirIa(mensagem: String, pedido: suspend () -> RespostaIa, aoReceber: (List<ItemEstimado>) -> Unit) {
        if (perfil.chaveGemini.isBlank()) {
            aviso = "Para usar a IA, cadastre a chave do Gemini em Ajustes (aba Ajustes, no fim da página)."
            return
        }
        escopo.launch {
            esperando = mensagem
            try {
                val resposta = pedido()
                if (resposta.itens.isEmpty()) {
                    aviso = resposta.resposta.ifBlank {
                        "A IA não reconheceu nenhum alimento. Tente de novo ou busque pelo nome."
                    }
                } else {
                    aoReceber(resposta.itens)
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

    /** Abre a revisão com os itens novos da IA. */
    fun revisar(itens: List<ItemEstimado>) {
        revisados.clear()
        revisados.addAll(itens.map { ItemRevisado.de(it) })
        revisando = true
    }

    fun analisarFoto(uri: Uri) = pedirIa(
        "Analisando a sua imagem…",
        {
            val jpeg = withContext(Dispatchers.IO) { Imagens.jpegReduzido(contexto, uri) }
            Gemini.estimarFoto(perfil.chaveGemini, jpeg)
        },
        ::revisar,
    )

    fun corrigir(indice: Int, correcao: String) {
        val item = revisados.getOrNull(indice) ?: return
        pedirIa(
            "Ajustando os valores conforme o seu pedido…",
            { Gemini.corrigirItem(perfil.chaveGemini, item.comoEstimado(), correcao) },
        ) { novos ->
            val posicao = revisados.indexOf(item)
            if (posicao >= 0) {
                revisados.removeAt(posicao)
                revisados.addAll(posicao, novos.map { ItemRevisado.de(it) })
            }
        }
    }

    fun acrescentar(descricao: String) = pedirIa(
        "Incluindo no prato…",
        { Gemini.estimarTexto(perfil.chaveGemini, descricao) },
    ) { novos -> revisados.addAll(novos.map { ItemRevisado.de(it) }) }

    val uriFoto = remember {
        val pasta = File(contexto.cacheDir, "fotos").apply { mkdirs() }
        FileProvider.getUriForFile(contexto, "${contexto.packageName}.arquivos", File(pasta, "prato.jpg"))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) analisarFoto(uriFoto)
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) analisarFoto(uri)
    }

    fun buscarCodigo(codigo: String) {
        val salvo = banco.porCodigo(codigo)
        if (salvo != null) {
            escolhido = salvo
            return
        }
        escopo.launch {
            esperando = "Buscando o produto…"
            try {
                val produto = OpenFoodFacts.buscar(codigo)
                if (produto != null) {
                    // Guardado para a próxima leitura não depender da internet.
                    banco.salvarMeuAlimento(produto)
                    escolhido = produto
                } else {
                    criandoComCodigo = codigo
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

    fun lerCodigo() {
        val opcoes = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
        GmsBarcodeScanning.getClient(contexto, opcoes).startScan()
            .addOnSuccessListener { lido -> lido.rawValue?.let { buscarCodigo(it) } }
            .addOnFailureListener { e -> aviso = "Não deu para abrir o leitor de código: ${e.message}" }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    // Fora de hoje, o dia aparece embaixo: é nele que a comida entra.
                    Column {
                        Text(refeicao.adicionarAo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (dia != hojeAgora || dia != hoje()) {
                            Text(
                                tituloDoDia(dia),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { margens ->
        Column(
            Modifier
                .padding(margens)
                .fillMaxSize(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BotaoCaminho("📷", "Foto", Modifier.weight(1f)) { escolhendoFoto = true }
                BotaoCaminho("▦", "Código", Modifier.weight(1f)) { lerCodigo() }
                BotaoCaminho("✍️", "Descrever", Modifier.weight(1f)) { descrevendo = true }
            }
            OutlinedTextField(
                value = termo,
                onValueChange = { termo = it },
                placeholder = { Text(refeicao.oQueComeu) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (termo.isNotEmpty()) {
                        IconButton(onClick = { termo = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Limpar")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                if (termo.isBlank()) {
                    if (recentes.isNotEmpty()) {
                        item { Secao("Recentes") }
                        items(recentes) { a -> LinhaAlimento(a) { escolhido = a } }
                    }
                    if (meus.isNotEmpty()) {
                        item { Secao("Meus alimentos") }
                        items(meus) { a -> LinhaAlimento(a) { escolhido = a } }
                    }
                    if (recentes.isEmpty() && meus.isEmpty()) {
                        item {
                            Text(
                                "Busque pelo nome, tire uma foto do prato, leia o código de barras " +
                                    "ou descreva o que comeu.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                } else {
                    items(resultados) { a -> LinhaAlimento(a) { escolhido = a } }
                    if (resultados.isEmpty()) {
                        item {
                            Text(
                                if (taco.isEmpty()) {
                                    "A tabela TACO ainda não está neste app. Use foto, código ou descrição."
                                } else {
                                    "Nada encontrado. Tente outra palavra ou use Descrever."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
                item {
                    TextButton(onClick = { criando = true }, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text("+ Criar alimento")
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when (adicionados) {
                        0 -> "Nenhum item adicionado"
                        1 -> "1 item adicionado"
                        else -> "$adicionados itens adicionados"
                    },
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = aoVoltar) { Text("Pronto") }
            }
        }
    }

    escolhido?.let { a ->
        Quantidade(
            alimento = a,
            aoAdicionar = { gramas -> adicionar(a, gramas) },
            aoFechar = { escolhido = null },
        )
    }
    if (revisando) {
        RevisaoIa(
            revisados = revisados,
            aoCorrigir = { corrigindo = it },
            aoAcrescentar = { acrescentando = true },
            aoAdicionar = { escolhas -> escolhas.forEach { (a, g) -> adicionar(a, g) } },
            aoFechar = {
                revisando = false
                revisados.clear()
            },
        )
    }
    corrigindo?.let { i ->
        PedirTexto(
            titulo = "Corrigir: ${revisados.getOrNull(i)?.alimento?.nome.orEmpty()}",
            exemplo = "Ex.: eram 2 pedaços; foi frito e não assado; não foi pão de queijo, foi pão de leite",
            botao = "Corrigir",
            aoEnviar = { corrigir(i, it) },
            aoFechar = { corrigindo = null },
        )
    }
    if (acrescentando) {
        PedirTexto(
            titulo = "O que mais tinha no prato?",
            exemplo = "Ex.: esqueci 1 copo de suco natural de 200 ml",
            botao = "Incluir",
            aoEnviar = { acrescentar(it) },
            aoFechar = { acrescentando = false },
        )
    }
    if (escolhendoFoto) {
        AlertDialog(
            onDismissRequest = { escolhendoFoto = false },
            title = { Text("Foto do prato") },
            text = { Text("A IA reconhece os alimentos e estima as quantidades. Você revisa antes de adicionar.") },
            confirmButton = {
                TextButton(onClick = {
                    escolhendoFoto = false
                    camera.launch(uriFoto)
                }) { Text("Tirar foto") }
            },
            dismissButton = {
                TextButton(onClick = {
                    escolhendoFoto = false
                    galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Escolher da galeria") }
            },
        )
    }
    if (descrevendo) {
        Descrever(
            aoEnviar = { texto ->
                pedirIa("Lendo a sua mensagem…", { Gemini.estimarTexto(perfil.chaveGemini, texto) }, ::revisar)
            },
            aoFechar = { descrevendo = false },
        )
    }
    if (criando || criandoComCodigo != null) {
        NovoAlimento(
            codigo = criandoComCodigo,
            aoSalvar = { novo ->
                banco.salvarMeuAlimento(novo)
                escolhido = novo
            },
            aoFechar = {
                criando = false
                criandoComCodigo = null
            },
        )
    }
    esperando?.let { Esperando(it) }
    aviso?.let { Aviso(it) { aviso = null } }
}

@Composable
private fun BotaoCaminho(emoji: String, rotulo: String, modifier: Modifier, aoTocar: () -> Unit) {
    OutlinedButton(onClick = aoTocar, modifier = modifier.heightIn(min = 64.dp), contentPadding = PaddingValues(4.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(rotulo, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Secao(titulo: String) {
    Text(
        titulo,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

private fun descricaoOrigem(a: Alimento): String = when (a.origem) {
    ORIGEM_TACO -> "TACO"
    ORIGEM_OFF -> "código de barras"
    ORIGEM_IA -> "estimado pela IA"
    else -> "meu alimento"
}

@Composable
private fun LinhaAlimento(a: Alimento, aoTocar: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = aoTocar)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(a.nome, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val porcao = a.porcaoG
                Text(
                    if (porcao != null) {
                        "${paraCampo(porcao)} ${unidade(a)} · ${inteiro(a.kcal(porcao))} kcal · ${descricaoOrigem(a)}"
                    } else {
                        "100 ${unidade(a)} · ${inteiro(a.kcal100)} kcal · ${descricaoOrigem(a)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.Add, contentDescription = "Adicionar", tint = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun LinhaValores(a: Alimento, gramas: Double) {
    Text(
        "${inteiro(a.kcal(gramas))} kcal · Carb ${umaCasa(a.carb(gramas))} g · " +
            "Prot ${umaCasa(a.prot(gramas))} g · Gord ${umaCasa(a.gord(gramas))} g",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun Quantidade(alimento: Alimento, aoAdicionar: (Double) -> Unit, aoFechar: () -> Unit) {
    var texto by remember { mutableStateOf(paraCampo(alimento.porcaoG ?: 100.0)) }
    val gramas = lerNumero(texto)
    val valido = gramas != null && gramas > 0
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(alimento.nome) },
        text = {
            Column {
                CampoNumero(
                    texto,
                    { texto = it },
                    "Quantidade",
                    sufixo = unidade(alimento),
                    ajuda = alimento.porcaoNome?.let { "Porção do rótulo: $it" },
                )
                if (gramas != null && valido) LinhaValores(alimento, gramas)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (gramas != null && valido) aoAdicionar(gramas)
                    aoFechar()
                },
                enabled = valido,
            ) { Text("Adicionar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

/** Uma linha da revisão: marcada ou não, e a quantidade que a pessoa ajustou. */
class ItemRevisado(val alimento: Alimento, marcado: Boolean, gramas: String) {
    var marcado by mutableStateOf(marcado)
    var gramas by mutableStateOf(gramas)

    /** Como a IA vê o item agora, com a quantidade ajustada, para pedir uma correção. */
    fun comoEstimado(): ItemEstimado {
        val g = lerNumero(gramas)?.takeIf { it > 0 } ?: alimento.porcaoG ?: 100.0
        return ItemEstimado(
            nome = alimento.nome,
            gramas = g,
            kcal = alimento.kcal(g),
            prot = alimento.prot(g),
            carb = alimento.carb(g),
            gord = alimento.gord(g),
            micros = alimento.micros(g),
            aguaExtraMl = if (alimento.pedeAgua) g else 0.0,
        )
    }

    companion object {
        fun de(item: ItemEstimado) = ItemRevisado(item.comoAlimento(), true, paraCampo(item.gramas))
    }
}

@Composable
private fun RevisaoIa(
    revisados: List<ItemRevisado>,
    aoCorrigir: (Int) -> Unit,
    aoAcrescentar: () -> Unit,
    aoAdicionar: (List<Pair<Alimento, Double>>) -> Unit,
    aoFechar: () -> Unit,
) {
    val escolhas = revisados.mapNotNull { r ->
        val g = lerNumero(r.gramas)
        if (r.marcado && g != null && g > 0) r.alimento to g else null
    }
    val totalKcal = escolhas.sumOf { (a, g) -> a.kcal(g) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("A IA encontrou") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                revisados.forEachIndexed { i, r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = r.marcado, onCheckedChange = { r.marcado = it })
                        Column(Modifier.weight(1f)) {
                            Text(r.alimento.nome, fontWeight = FontWeight.Bold)
                            val g = lerNumero(r.gramas)
                            if (g != null) LinhaValores(r.alimento, g)
                            Text(
                                "✏️ Corrigir",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .clickable { aoCorrigir(i) }
                                    .padding(vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = r.gramas,
                            onValueChange = { novo -> r.gramas = novo.filter { it.isDigit() || it == ',' || it == '.' } },
                            suffix = { Text(unidade(r.alimento)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            modifier = Modifier.width(96.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = aoAcrescentar) { Text("➕ Esqueci um item") }
                Text(
                    "Total: ${inteiro(totalKcal)} kcal. Confira as quantidades: a IA estima pelo tamanho na foto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    aoAdicionar(escolhas)
                    aoFechar()
                },
                enabled = escolhas.isNotEmpty(),
            ) { Text("Adicionar (${escolhas.size})") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

@Composable
private fun Descrever(aoEnviar: (String) -> Unit, aoFechar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("O que você comeu?") },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                placeholder = { Text("Ex.: 4 colheres de arroz, 1 concha de feijão e 1 bife grelhado") },
                minLines = 3,
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
            ) { Text("Calcular") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

@Composable
private fun NovoAlimento(codigo: String?, aoSalvar: (Alimento) -> Unit, aoFechar: () -> Unit) {
    var nome by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var prot by remember { mutableStateOf("") }
    var carb by remember { mutableStateOf("") }
    var gord by remember { mutableStateOf("") }
    var porcao by remember { mutableStateOf("") }
    val kcalLida = lerNumero(kcal)
    val valido = nome.isNotBlank() && kcalLida != null
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (codigo != null) "Produto não encontrado" else "Criar alimento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (codigo != null) {
                    Text(
                        "O código $codigo não está no Open Food Facts. Copie os valores do rótulo " +
                            "(por 100 g) e ele fica salvo para a próxima vez.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                CampoNumero(kcal, { kcal = it }, "Calorias em 100 g", sufixo = "kcal")
                CampoNumero(carb, { carb = it }, "Carboidratos em 100 g", sufixo = "g")
                CampoNumero(prot, { prot = it }, "Proteínas em 100 g", sufixo = "g")
                CampoNumero(gord, { gord = it }, "Gorduras em 100 g", sufixo = "g")
                CampoNumero(porcao, { porcao = it }, "Porção (opcional)", sufixo = "g")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (kcalLida != null) {
                        aoSalvar(
                            alimentoFeitoAMao(
                                nome = nome,
                                kcal100 = kcalLida,
                                prot100 = lerNumero(prot) ?: 0.0,
                                carb100 = lerNumero(carb) ?: 0.0,
                                gord100 = lerNumero(gord) ?: 0.0,
                                porcaoG = lerNumero(porcao)?.takeIf { it > 0 },
                                codigoBarras = codigo,
                            )
                        )
                    }
                    aoFechar()
                },
                enabled = valido,
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}
