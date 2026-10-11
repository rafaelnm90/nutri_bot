package com.rafaelnm90.calorias

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Peças usadas por várias telas: formato de números e datas em português,
 * o cartão branco, os diálogos de espera e de erro, o campo numérico, a foto
 * reduzida para a IA e a tabela TACO carregada uma vez.
 */

val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

/** 1886.4 -> "1.886" */
fun inteiro(v: Double): String = "%,d".format(PT_BR, v.roundToInt())

/** 82.0 -> "82,0" */
fun umaCasa(v: Double): String = "%.1f".format(PT_BR, v)

/** Para preencher um campo: 150.0 -> "150", 82.5 -> "82,5". */
fun paraCampo(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(PT_BR, v)

/** Lê o que a pessoa digitou, aceitando vírgula ou ponto. */
fun lerNumero(texto: String): Double? = texto.trim().replace(",", ".").toDoubleOrNull()

/** Inteiro digitado: "1.800" e "1800" valem 1800 (em pt-BR o ponto separa milhar). */
fun lerInteiro(texto: String): Int? = texto.substringBefore(',').filter { it.isDigit() }.toIntOrNull()

fun hoje(): String = LocalDate.now().toString()

private val FORMATO_DIA = DateTimeFormatter.ofPattern("EEE, dd/MM", PT_BR)

fun tituloDoDia(dia: String): String {
    val data = LocalDate.parse(dia)
    val hoje = LocalDate.now()
    return when (data) {
        hoje -> "Hoje"
        hoje.minusDays(1) -> "Ontem"
        else -> data.format(FORMATO_DIA).replaceFirstChar { it.uppercase() }
    }
}

/** "12 h 05 min" */
fun duracao(ms: Long): String {
    val minutos = max(0L, ms / 60_000)
    return "%d h %02d min".format(minutos / 60, minutos % 60)
}

/** Bebida aparece em ml; o resto, em g. */
fun unidade(a: Alimento): String = if (a.pedeAgua || Bebidas.ehBebida(a.nome)) "ml" else "g"

@Composable
fun Cartao(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), content = conteudo)
    }
}

@Composable
fun Esperando(mensagem: String) {
    Dialog(onDismissRequest = {}) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(
                Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(Modifier.size(32.dp))
                Text(mensagem)
            }
        }
    }
}

@Composable
fun Aviso(mensagem: String, aoFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoFechar,
        confirmButton = { TextButton(onClick = aoFechar) { Text("OK") } },
        text = { Text(mensagem) },
    )
}

/** Texto da IA (sugestão, rótulo, relatório) num diálogo com rolagem. */
@Composable
fun TextoDaIa(titulo: String, texto: String, aoFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // A IA às vezes ainda manda negrito do markdown: tira os asteriscos.
                Text(texto.replace("**", "").replace("*", "").replace("#", ""))
            }
        },
        confirmButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
    )
}

@Composable
fun CampoNumero(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    sufixo: String? = null,
    ajuda: String? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { novo -> aoMudar(novo.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(rotulo) },
        suffix = if (sufixo != null) {
            { Text(sufixo) }
        } else {
            null
        },
        supportingText = if (ajuda != null) {
            { Text(ajuda) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

object Imagens {
    /** A foto em JPEG com no máximo 1280 px no lado maior: chega rápido ao Gemini e basta para ele. */
    fun jpegReduzido(contexto: Context, uri: Uri, ladoMax: Int = 1280): ByteArray {
        val resolver = contexto.contentResolver
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, medidas) }
        var amostra = 1
        while (max(medidas.outWidth, medidas.outHeight) / (amostra * 2) >= ladoMax) amostra *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra })
        } ?: throw ErroParaMostrar("Não deu para abrir a foto.")
        val escala = min(1.0, ladoMax.toDouble() / max(bitmap.width, bitmap.height))
        val final = if (escala < 1.0) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * escala).roundToInt(),
                (bitmap.height * escala).roundToInt(),
                true,
            )
        } else {
            bitmap
        }
        return ByteArrayOutputStream().use { saida ->
            final.compress(Bitmap.CompressFormat.JPEG, 85, saida)
            saida.toByteArray()
        }
    }
}

object TacoNoApp {
    @Volatile
    private var lista: List<Alimento>? = null

    /** A tabela TACO do assets/taco.json, lida uma vez. Vazia se o arquivo ainda não existe. */
    fun de(contexto: Context): List<Alimento> =
        lista ?: synchronized(this) {
            lista ?: carregar(contexto).also { lista = it }
        }

    private fun carregar(contexto: Context): List<Alimento> =
        try {
            Taco.ler(contexto.assets.open("taco.json").bufferedReader().use { it.readText() })
        } catch (e: FileNotFoundException) {
            emptyList()
        }
}
