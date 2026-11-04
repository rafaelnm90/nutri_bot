package com.rafaelnm90.calorias

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * O cartão da jornada de hábitos no Diário, entre o check-in e o Resumo, só no dia de hoje:
 * o convite para montar, a semana da etapa com um toque para corrigir cada dia, as semanas
 * boas seguidas, as trocas e os mantidos numa linha. Tudo é recontado do diário a cada
 * leitura (Nutricao.kt, Jornada.progresso); só a vitória fica gravada.
 */

/**
 * [hojeTexto]: o dia que o Diário mostra, que é sempre o de hoje (o cartão só aparece nele).
 * O Diário passa o dia para o cartão acompanhar a virada do dia: sem isso, na virada nada do
 * que o cartão recebe muda, o Compose pula o cartão e a semana fica no dia anterior.
 */
@Composable
fun JornadaCartao(salva: JornadaSalva, versao: Int, aoMontar: () -> Unit, hojeTexto: String = hoje()) {
    val contexto = LocalContext.current
    val banco = remember { Banco.de(contexto) }
    val jornada = remember { JornadaAjustes.de(contexto) }
    val etapa = salva.etapa
    val inicio = salva.inicioEtapa
    // Em Reduzir (só no modo seguro), o dia na meta é o que fica dentro do teto.
    val reduzir = Jornada.emReduzir(salva)
    var corrigindo by remember { mutableStateOf<DiaDaEtapa?>(null) }
    var passando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }

    val momento = Jornada.momento(salva)
    // Relê o período a cada gravação no banco (versao), a cada toque (salva) e na virada do dia.
    val progresso = remember(versao, salva, hojeTexto) {
        if (momento == MomentoDaJornada.EM_CURSO && etapa != null && inicio != null) {
            Jornada.progresso(
                etapa, inicio, hojeTexto,
                // Com a data do celular antes do início, a etapa fica no primeiro dia, com os registros dele.
                banco.registrosEntre(inicio, maxOf(inicio, hojeTexto)),
                Jornada.toquesDo(salva, etapa), salva.seguro, reduzir,
            )
        } else {
            null
        }
    }
    val vencidaEm = progresso?.vencidaEm
    // A vitória é gravada uma vez: depois, um deslize lançado num dia passado não a desfaz.
    LaunchedEffect(vencidaEm) {
        if (vencidaEm != null && etapa != null) jornada.vencer(etapa, vencidaEm)
    }

    Cartao {
        when {
            momento == MomentoDaJornada.MONTAR -> {
                Text("🧭 Jornada de hábitos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Um hábito por vez, começando pela bebida com açúcar. O app confere pelo seu diário.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = aoMontar) { Text("Montar a jornada") }
            }
            momento == MomentoDaJornada.SEM_ETAPA || etapa == null -> {
                Text("🧭 Jornada de hábitos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Nenhuma etapa em curso por enquanto: as etapas seguintes chegam numa nova versão do app.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinhaDosMantidos(salva)
            }
            // A vitória aparece já na leitura que a achou, antes de ser gravada.
            momento == MomentoDaJornada.VENCIDA || vencidaEm != null -> {
                Text(
                    "Jornada de hábitos",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "🏆 Etapa vencida: ${etapa.titulo}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (salva.seguro) "Você passou de etapa." else "Duas semanas boas seguidas!",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(Jornada.TEXTO_LALLY, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                // Ainda não há próxima etapa: o toque só avisa, sem mudar nada gravado.
                Button(onClick = { aviso = Jornada.TEXTO_PROXIMA_VERSAO }) { Text("Começar a próxima") }
                LinhaDosMantidos(salva)
            }
            progresso != null -> EtapaEmCurso(
                salva = salva,
                etapa = etapa,
                p = progresso,
                aoTocarDia = { corrigindo = it },
                aoPassar = { passando = true },
            )
        }
    }

    if (etapa != null) {
        corrigindo?.let { d ->
            CorrigirDia(
                d = d,
                etapa = etapa,
                reduzir = reduzir,
                aoEscolher = { estado ->
                    jornada.tocar(d.dia, etapa, estado)
                    corrigindo = null
                },
                aoFechar = { corrigindo = null },
            )
        }
    }
    // Modo seguro: sem contagem de semanas, a pessoa passa de etapa quando quiser.
    if (passando && etapa != null) {
        AlertDialog(
            onDismissRequest = { passando = false },
            title = { Text("Passar de etapa?") },
            text = { Text("${etapa.titulo} vai para os mantidos. A próxima etapa chega numa nova versão do app.") },
            confirmButton = {
                TextButton(onClick = {
                    jornada.vencer(etapa, hojeTexto)
                    passando = false
                }) { Text("Passar") }
            },
            dismissButton = { TextButton(onClick = { passando = false }) { Text("Cancelar") } },
        )
    }
    aviso?.let { Aviso(it) { aviso = null } }
}

/**
 * A etapa em curso: a meta, a semana com um toque em cada dia, as semanas boas seguidas (ou,
 * no modo seguro, o botão de passar de etapa), hoje, as trocas e os mantidos. Nada de
 * vermelho: o deslize é laranja e não apaga o que já foi conquistado.
 */
@Composable
private fun EtapaEmCurso(
    salva: JornadaSalva,
    etapa: Habito,
    p: ProgressoEtapa,
    aoTocarDia: (DiaDaEtapa) -> Unit,
    aoPassar: () -> Unit,
) {
    Text(
        "Jornada de hábitos",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "🧭 Etapa ${Jornada.numeroDaEtapa(salva)}: ${etapa.titulo}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(Jornada.textoMeta(salva), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Text("Semana ${p.atual.numero} da etapa", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth()) {
        p.atual.dias.forEach { d ->
            Column(
                Modifier
                    .weight(1f)
                    // Dia futuro aparece na semana, mas ainda não aceita toque.
                    .clickable(enabled = !d.futuro) { aoTocarDia(d) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val ehHoje = d.dia == p.hoje.dia
                Text(
                    Jornada.letraDoDia(d.dia),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (ehHoje) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    d.dia.takeLast(2),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(Jornada.simboloDoDia(d), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    Text(
        Jornada.legendaDaSemana(etapa, p.reduzir),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    if (salva.seguro) {
        Text(Jornada.TEXTO_MODO_SEGURO_CARTAO, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = aoPassar) { Text("Passar de etapa") }
    } else {
        Text(
            "Semanas boas seguidas: ${p.seguidas} de ${Jornada.SEMANAS_PARA_VENCER}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        p.ultimaCompleta?.let { Text(Jornada.textoSemanaPassada(it, etapa), style = MaterialTheme.typography.bodySmall) }
    }
    Text(Jornada.textoHoje(p, salva.seguro), style = MaterialTheme.typography.bodyMedium)
    if (etapa.troca.isNotEmpty()) {
        Text("🔄 Trocas: ${etapa.troca}", style = MaterialTheme.typography.bodySmall)
    }
    LinhaDosMantidos(salva)
}

/** Os mantidos ("Já não faço" e as etapas vencidas) numa linha; nada quando não há nenhum. */
@Composable
private fun LinhaDosMantidos(salva: JornadaSalva) {
    Jornada.textoMantidos(salva)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

/**
 * Correção de um dia da semana da etapa: o toque vale mais que o que o diário achou.
 * [aoEscolher] com null apaga a correção e o dia volta ao que o diário diz. [reduzir]: a
 * etapa está em Reduzir, e os textos falam do teto.
 */
@Composable
private fun CorrigirDia(
    d: DiaDaEtapa,
    etapa: Habito,
    reduzir: Boolean,
    aoEscolher: (EstadoDia?) -> Unit,
    aoFechar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(tituloDoDia(d.dia)) },
        text = {
            Column {
                val naMeta = Jornada.rotuloDoDia(etapa, EstadoDia.NA_META, reduzir)
                val deslize = Jornada.rotuloDoDia(etapa, EstadoDia.DESLIZE, reduzir)
                Text("Pelo diário: ${Jornada.rotuloDoDia(etapa, d.peloDiario, reduzir)}.")
                if (d.tocado) Text("Você corrigiu para: ${Jornada.rotuloDoDia(etapa, d.estado, reduzir)}.")
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { aoEscolher(EstadoDia.NA_META) }) {
                    Text("✅ ${naMeta.replaceFirstChar { it.uppercase() }}")
                }
                TextButton(onClick = { aoEscolher(EstadoDia.DESLIZE) }) {
                    Text("🔸 ${deslize.replaceFirstChar { it.uppercase() }}")
                }
                if (d.tocado) {
                    TextButton(onClick = { aoEscolher(null) }) { Text("Voltar ao que o diário diz") }
                }
            }
        },
        confirmButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
    )
}
