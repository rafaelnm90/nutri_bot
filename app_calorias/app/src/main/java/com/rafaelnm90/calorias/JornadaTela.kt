package com.rafaelnm90.calorias

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/*
 * A tela Montar a jornada, em tela cheia como a de adicionar comida: onde quer chegar
 * (peso, peso meta e objetivo do perfil), a pergunta de saúde, o que sai da rotina (com
 * cadeado), o cigarro, o que reduzir e outro hábito. As regras ficam em Nutricao.kt
 * (Jornada) e o que é gravado, em Ajustes.kt (JornadaAjustes). Nada é gravado até o
 * botão do fim.
 */

/**
 * [aoComecar] vem depois de gravar a montagem (e o perfil, se o peso, a meta ou o
 * objetivo mudaram): o app volta ao Diário de hoje, que já mostra a etapa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JornadaTela(aoVoltar: () -> Unit, aoComecar: () -> Unit) {
    val contexto = LocalContext.current
    val ajustes = remember { Ajustes.de(contexto) }
    val jornada = remember { JornadaAjustes.de(contexto) }
    val perfil by ajustes.perfil.collectAsStateWithLifecycle()
    // Como estava ao abrir: é daí que saem as marcações e o texto do botão do fim.
    val salva = remember { jornada.salva.value }
    val pesoInicial = remember { if (perfil.pesoKg > 0) paraCampo(perfil.pesoKg) else "" }
    val metaInicial = remember { if (perfil.pesoMetaKg > 0) paraCampo(perfil.pesoMetaKg) else "" }

    // Tudo em rememberSaveable, em texto e valores simples: girar o celular no meio da
    // montagem não pode apagar as escolhas.
    var peso by rememberSaveable { mutableStateOf(pesoInicial) }
    var pesoMeta by rememberSaveable { mutableStateOf(metaInicial) }
    var objetivo by rememberSaveable { mutableStateOf(perfil.objetivo) }
    // null: a pergunta de saúde ainda sem resposta; true: modo seguro.
    var saude by rememberSaveable { mutableStateOf<Boolean?>(if (salva.montada) salva.seguro else null) }
    // As escolhas e os outros hábitos no mesmo texto das preferências da jornada.
    var escolhasTexto by rememberSaveable {
        mutableStateOf(
            Jornada.escolhasParaTexto(
                if (salva.montada) Jornada.escolhasValidas(salva.escolhas, salva.seguro) else Jornada.escolhasIniciais(false)
            )
        )
    }
    var outrosTexto by rememberSaveable { mutableStateOf(Jornada.outrosParaTexto(salva.outros)) }
    var outroNome by rememberSaveable { mutableStateOf("") }
    var bloqueio by remember { mutableStateOf<Bloqueio?>(null) }

    val escolhas = remember(escolhasTexto) { Jornada.escolhasDeTexto(escolhasTexto) }
    val outros = remember(outrosTexto) { Jornada.outrosDeTexto(outrosTexto) }
    val seguro = saude == true
    val etapa = Jornada.primeiraEtapa(Jornada.escolhasValidas(escolhas, seguro))
    val falta = Jornada.faltaResponder(saude, escolhas)
    val pesoKg = lerNumero(peso) ?: 0.0
    val metaKg = lerNumero(pesoMeta) ?: 0.0

    fun escolher(h: Habito, e: Escolha) {
        escolhasTexto = Jornada.escolhasParaTexto(escolhas + (h to e))
    }

    fun mudarSaude(sim: Boolean) {
        if (saude == sim) return
        saude = sim
        escolhasTexto = Jornada.escolhasParaTexto(Jornada.aoMudarSaude(escolhas, sim))
    }

    fun adicionarOutro(e: Escolha) {
        val nome = outroNome.trim()
        if (nome.isEmpty()) return
        if (e == Escolha.ELIMINAR) {
            val b = Jornada.bloqueioDeEliminar(nome)
            if (b != null) {
                bloqueio = b
                return
            }
        }
        val igual = outros.indexOfFirst { Busca.normalizar(it.nome) == Busca.normalizar(nome) }
        val novos = when {
            // O mesmo hábito escrito de novo só troca a escolha dele, sem repetir a linha.
            igual >= 0 -> outros.mapIndexed { i, o -> if (i == igual) o.copy(escolha = e) else o }
            outros.size < Jornada.MAX_OUTROS -> outros + OutroHabito(nome, e)
            else -> return
        }
        outrosTexto = Jornada.outrosParaTexto(novos)
        outroNome = ""
        bloqueio = null
    }

    fun comecar() {
        // Campo sem mudança fica com o valor exato do perfil (o texto arredonda a 1 casa), e
        // campo vazio não apaga o peso do perfil: isso se faz em Ajustes.
        val novoPerfil = perfil.copy(
            pesoKg = if (peso == pesoInicial || pesoKg <= 0) perfil.pesoKg else pesoKg,
            pesoMetaKg = if (pesoMeta == metaInicial || metaKg <= 0) perfil.pesoMetaKg else metaKg,
            objetivo = objetivo,
        )
        if (novoPerfil != perfil) ajustes.salvar(novoPerfil)
        jornada.montar(seguro, escolhas, outros)
        aoComecar()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Montar a jornada") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { margens ->
        // Sem imePadding: o safeDrawing do Scaffold já inclui o teclado.
        Column(
            Modifier
                .padding(margens)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Um hábito por vez. O app confere pelo seu diário, e um toque corrige o que ele não vê.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Cartao {
                TituloDoCartao("🎯 Onde quer chegar")
                TextoApagado(
                    "Os mesmos dados do seu perfil em Ajustes: o que mudar aqui muda lá também, e a meta de " +
                        "calorias calculada acompanha."
                )
                // O mesmo rótulo de Ajustes: aqui o peso só muda o perfil, sem ir para o histórico da aba Peso.
                CampoNumero(peso, { peso = it }, "Peso atual", sufixo = "kg")
                CampoNumero(pesoMeta, { pesoMeta = it }, "Peso meta", sufixo = "kg")
                Spacer(Modifier.height(8.dp))
                Text("Objetivo", fontWeight = FontWeight.Bold)
                Objetivo.entries.forEach { o -> OpcaoDaMontagem(o.titulo, objetivo == o) { objetivo = o } }
                if (pesoKg > 0 && metaKg > 0) {
                    val diferenca = kotlin.math.abs(pesoKg - metaKg)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (diferenca < 0.05) {
                            "Você já está no peso meta."
                        } else {
                            "Hoje ${umaCasa(pesoKg)} kg, meta ${umaCasa(metaKg)} kg: faltam " +
                                "${if (pesoKg > metaKg) "perder" else "ganhar"} ${umaCasa(diferenca)} kg."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                // Com o sim na saúde (logo abaixo), a linha troca na hora pela do médico.
                Jornada.textoPrimeiraParada(pesoKg, metaKg, objetivo, seguro, ::umaCasa)?.let { texto ->
                    Spacer(Modifier.height(4.dp))
                    Text(texto, style = MaterialTheme.typography.bodyMedium)
                }
                // Aviso leve: nunca trava o botão do fim.
                Jornada.avisoImc(metaKg, perfil.alturaCm, perfil.idade(LocalDate.now().year))?.let { a ->
                    Spacer(Modifier.height(4.dp))
                    TextoApagado(
                        "ℹ️ Com ${umaCasa(metaKg)} kg, o IMC fica em ${umaCasa(a.imc)}, abaixo de " +
                            (if (a.minimo > 18.5) "22, o mínimo saudável a partir dos 60 anos" else "18,5, o mínimo saudável") +
                            ". Para a sua altura, o mínimo é ${umaCasa(a.pesoMinimoKg)} kg. " +
                            "Vale conversar com um médico ou nutricionista."
                    )
                }
            }

            Cartao {
                TituloDoCartao("🩺 Sua saúde")
                Text(Jornada.PERGUNTA_SAUDE, style = MaterialTheme.typography.bodyMedium)
                OpcaoDaMontagem("Não, nenhum desses", saude == false) { mudarSaude(false) }
                OpcaoDaMontagem("Sim, um ou mais desses", saude == true) { mudarSaude(true) }
                if (saude == true) {
                    Text(
                        Jornada.TEXTO_MODO_SEGURO,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextoApagado(Jornada.TEXTO_RESPOSTA_SAUDE)
            }

            Cartao {
                // No modo seguro a bebida com açúcar e os embutidos começam em Reduzir (o álcool, em
                // Eliminar): o título diz que eles também podem só diminuir.
                TituloDoCartao(if (seguro) "O que sai ou diminui na rotina" else "🔒 O que sai da rotina")
                Text(
                    if (seguro) {
                        "No modo seguro não há cadeado: escolha Eliminar, Reduzir com o teto sugerido ou Já não faço."
                    } else {
                        "O app toma a frente nestes três: são hábitos que não dá para manter de forma alguma. " +
                            "Meta zero, e um deslize não apaga o que você já conquistou. Se já não faz, marque " +
                            "Já não faço: o hábito começa como mantido."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Habito.entries.filter { it.grupo == GrupoHabito.OBRIGATORIO }.forEachIndexed { i, h ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    BlocoHabito(h, seguro, escolhas[h]) { escolher(h, it) }
                }
            }

            Cartao {
                TituloDoCartao("🚭 Cigarro")
                // O título do cartão já é o nome do hábito.
                BlocoHabito(Habito.CIGARRO, seguro, escolhas[Habito.CIGARRO], comTitulo = false) {
                    escolher(Habito.CIGARRO, it)
                }
            }

            Cartao {
                TituloDoCartao("📉 O que você quer reduzir")
                Text(
                    "Escolha o que quer mudar; nada aqui é obrigatório. O teto é uma sugestão. Cada hábito vira " +
                        "uma etapa, uma por vez, nas próximas versões do app.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Habito.entries.filter { it.grupo == GrupoHabito.REDUZIR }.forEachIndexed { i, h ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    BlocoHabito(h, seguro, escolhas[h]) { escolher(h, it) }
                }
            }

            Cartao {
                TituloDoCartao("✍️ Outro hábito")
                Text(
                    "Algo seu que não está na lista, como pão de queijo ou pipoca. Até ${Jornada.MAX_OUTROS}.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = outroNome,
                    onValueChange = {
                        outroNome = it.take(Jornada.MAX_NOME_OUTRO)
                        bloqueio = null
                    },
                    label = { Text("Outro hábito") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val podeAdicionar = outroNome.isNotBlank() && outros.size < Jornada.MAX_OUTROS
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { adicionarOutro(Escolha.ELIMINAR) },
                        enabled = podeAdicionar,
                        modifier = Modifier.weight(1f),
                    ) { Text("Eliminar") }
                    OutlinedButton(
                        onClick = { adicionarOutro(Escolha.REDUZIR) },
                        enabled = podeAdicionar,
                        modifier = Modifier.weight(1f),
                    ) { Text("Reduzir") }
                }
                // O porquê em cinza, nunca em vermelho: é uma explicação, não um erro.
                bloqueio?.let {
                    Spacer(Modifier.height(4.dp))
                    TextoApagado(it.explicacao)
                }
                outros.forEach { o ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${o.nome}: ${if (o.escolha == Escolha.ELIMINAR) "Eliminar" else "Reduzir"}",
                            Modifier.weight(1f),
                        )
                        TextButton(onClick = { outrosTexto = Jornada.outrosParaTexto(outros - o) }) { Text("Tirar") }
                    }
                }
            }

            Cartao {
                TituloDoCartao("🚀 Começar")
                Text(
                    Jornada.textoDaEtapaNaMontagem(
                        etapa,
                        seguro,
                        continua = Jornada.continuaAEtapa(salva, etapa, seguro),
                        vencida = Jornada.jaVencida(salva, etapa),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                falta?.let {
                    Spacer(Modifier.height(4.dp))
                    TextoApagado(it)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { comecar() }, enabled = falta == null, modifier = Modifier.fillMaxWidth()) {
                    Text(if (salva.montada) "Salvar as escolhas" else "Começar a jornada")
                }
            }
        }
    }
}

@Composable
private fun TituloDoCartao(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

/** Texto de apoio em cinza: avisos leves, que nunca travam nada. */
@Composable
private fun TextoApagado(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Botão de rádio com o texto, como o de Ajustes. */
@Composable
private fun OpcaoDaMontagem(texto: String, marcada: Boolean, aoEscolher: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = aoEscolher),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = marcada, onClick = aoEscolher)
        Spacer(Modifier.width(4.dp))
        Text(texto)
    }
}

/**
 * Um hábito: título, o que conta, motivo, teto, troca, fontes e os botões de escolha.
 * [comTitulo] falso quando o cartão já tem o nome do hábito no título.
 */
@Composable
private fun BlocoHabito(
    h: Habito,
    seguro: Boolean,
    marcada: Escolha?,
    comTitulo: Boolean = true,
    aoEscolher: (Escolha) -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (comTitulo) {
            // O cadeado: o app toma a frente nos obrigatórios, menos no modo seguro.
            val cadeado = h.grupo == GrupoHabito.OBRIGATORIO && !seguro
            Text((if (cadeado) "🔒 " else "") + h.titulo, fontWeight = FontWeight.Bold)
        }
        Text(h.oQueConta, style = MaterialTheme.typography.bodySmall)
        if (h.motivo.isNotEmpty()) Text(h.motivo, style = MaterialTheme.typography.bodyMedium)
        if (h == Habito.CIGARRO) {
            Text(Jornada.TEXTO_SUS, style = MaterialTheme.typography.bodyMedium)
            Text(Jornada.TEXTO_PESO_CIGARRO, style = MaterialTheme.typography.bodyMedium)
        }
        // O teto aparece nos de reduzir e, no modo seguro, no obrigatório marcado em Reduzir.
        // "Sugestão" serve a todos: no beliscar e no depois do jantar, o texto não é um número.
        if (h.teto.isNotEmpty() && (h.grupo == GrupoHabito.REDUZIR || marcada == Escolha.REDUZIR)) {
            Text("Sugestão: ${h.teto}", style = MaterialTheme.typography.bodyMedium)
        }
        if (h.troca.isNotEmpty()) Text("Troca: ${h.troca}", style = MaterialTheme.typography.bodySmall)
        Text(
            "Fontes: ${h.fonte}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Jornada.opcoes(h, seguro).forEach { e ->
                val texto = Jornada.rotulo(h, e, seguro)
                // O marcado é o botão cheio; os outros, contornados.
                if (e == marcada) {
                    Button(
                        onClick = { aoEscolher(e) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp),
                    ) {
                        Text(texto, style = MaterialTheme.typography.labelMedium, maxLines = 2)
                    }
                } else {
                    OutlinedButton(
                        onClick = { aoEscolher(e) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp),
                    ) {
                        Text(texto, style = MaterialTheme.typography.labelMedium, maxLines = 2)
                    }
                }
            }
        }
    }
}
