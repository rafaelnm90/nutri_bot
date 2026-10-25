package com.rafaelnm90.calorias

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/*
 * A aba Peso: o peso atual e a meta, o registro do peso do dia, o gráfico
 * até a meta e o histórico. Registrar o peso também atualiza o perfil, e
 * com ele a meta de calorias.
 */

private val DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM/yy")

@Composable
fun PesoTela() {
    val contexto = LocalContext.current
    val banco = remember { Banco.de(contexto) }
    val ajustes = remember { Ajustes.de(contexto) }
    val versao by banco.versao.collectAsStateWithLifecycle()
    val perfil by ajustes.perfil.collectAsStateWithLifecycle()
    val pesos = remember(versao) { banco.pesos() }
    var texto by rememberSaveable { mutableStateOf("") }
    var aviso by remember { mutableStateOf<String?>(null) }

    val atual = pesos.lastOrNull()?.second ?: perfil.pesoKg
    val meta = perfil.pesoMetaKg

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Peso", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Cartao {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Valor(if (atual > 0) "${umaCasa(atual)} kg" else "—", "Atual")
                    Valor(if (meta > 0) "${umaCasa(meta)} kg" else "—", "Meta")
                    Valor(
                        if (atual > 0 && meta > 0) "${umaCasa(kotlin.math.abs(atual - meta))} kg" else "—",
                        if (atual > meta) "Faltam perder" else "Faltam ganhar",
                    )
                }
                val inicio = pesos.firstOrNull()?.second
                if (inicio != null && pesos.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    val mudou = atual - inicio
                    Text(
                        "Desde ${LocalDate.parse(pesos.first().first).format(DATA_CURTA)}: " +
                            "${if (mudou > 0) "+" else ""}${umaCasa(mudou)} kg",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            Cartao {
                Text("Peso de hoje", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CampoNumero(texto, { texto = it }, "Peso", Modifier.weight(1f), sufixo = "kg")
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = {
                        val kg = lerNumero(texto)
                        if (kg == null || kg < 20 || kg > 400) {
                            aviso = "Digite o peso em quilos, por exemplo 81,5."
                        } else {
                            banco.salvarPeso(hoje(), kg)
                            ajustes.salvar(perfil.copy(pesoKg = kg))
                            texto = ""
                        }
                    }) { Text("Salvar") }
                }
            }
        }
        if (pesos.size >= 2) {
            item {
                Cartao {
                    Text("Progresso", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Grafico(pesos.takeLast(90), meta)
                }
            }
        }
        if (pesos.isNotEmpty()) {
            item { Text("Histórico", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(pesos.reversed()) { (dia, kg) ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(LocalDate.parse(dia).format(DATA_CURTA), Modifier.weight(1f))
                        Text("${umaCasa(kg)} kg", fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            // O perfil acompanha o peso mais recente, que calcula as metas.
                            pesoDepoisDeApagar(pesos, dia)?.let { anterior ->
                                ajustes.salvar(perfil.copy(pesoKg = anterior))
                            }
                            banco.apagarPeso(dia)
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Apagar")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }

    aviso?.let { Aviso(it) { aviso = null } }
}

@Composable
private fun Valor(valor: String, rotulo: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(rotulo, style = MaterialTheme.typography.bodySmall)
    }
}

/** Linha do peso por registro, com a meta tracejada. */
@Composable
private fun Grafico(pesos: List<Pair<String, Double>>, meta: Double) {
    val corLinha = MaterialTheme.colorScheme.primary
    val corMeta = MaterialTheme.colorScheme.tertiary
    val valores = pesos.map { it.second }
    var menor = valores.min()
    var maior = valores.max()
    if (meta > 0) {
        menor = min(menor, meta)
        maior = max(maior, meta)
    }
    // Folga em cima e embaixo para a linha não encostar na borda.
    val folga = max(0.5, (maior - menor) * 0.1)
    menor -= folga
    maior += folga

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        fun y(kg: Double) = (size.height * (1 - (kg - menor) / (maior - menor))).toFloat()
        fun x(i: Int) = if (valores.size == 1) size.width / 2 else size.width * i / (valores.size - 1)

        if (meta > 0) {
            drawLine(
                color = corMeta,
                start = Offset(0f, y(meta)),
                end = Offset(size.width, y(meta)),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)),
            )
        }
        for (i in 1 until valores.size) {
            drawLine(
                color = corLinha,
                start = Offset(x(i - 1), y(valores[i - 1])),
                end = Offset(x(i), y(valores[i])),
                strokeWidth = 6f,
            )
        }
        valores.forEachIndexed { i, kg -> drawCircle(corLinha, radius = 7f, center = Offset(x(i), y(kg))) }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            LocalDate.parse(pesos.first().first).format(DATA_CURTA),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (meta > 0) {
            Text("- - meta ${umaCasa(meta)} kg", style = MaterialTheme.typography.bodySmall, color = corMeta)
            Spacer(Modifier.weight(1f))
        }
        Text(LocalDate.parse(pesos.last().first).format(DATA_CURTA), style = MaterialTheme.typography.bodySmall)
    }
}
