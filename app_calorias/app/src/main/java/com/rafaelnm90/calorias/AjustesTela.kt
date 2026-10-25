package com.rafaelnm90.calorias

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/*
 * A aba Ajustes: o perfil que calcula a meta de calorias, a fase da dieta, as
 * metas de água e de jejum, os lembretes e a chave do Gemini. Nada é gravado
 * até tocar em Salvar.
 */

@Composable
fun AjustesTela() {
    val contexto = LocalContext.current
    val ajustes = remember { Ajustes.de(contexto) }
    val salvo by ajustes.perfil.collectAsStateWithLifecycle()

    var sexo by remember { mutableStateOf(salvo.sexo) }
    var ano by remember { mutableStateOf(salvo.anoNascimento.toString()) }
    var altura by remember { mutableStateOf(if (salvo.alturaCm > 0) paraCampo(salvo.alturaCm) else "") }
    var peso by remember { mutableStateOf(if (salvo.pesoKg > 0) paraCampo(salvo.pesoKg) else "") }
    var pesoMeta by remember { mutableStateOf(if (salvo.pesoMetaKg > 0) paraCampo(salvo.pesoMetaKg) else "") }
    var atividade by remember { mutableStateOf(salvo.atividade) }
    var objetivo by remember { mutableStateOf(salvo.objetivo) }
    var fase by remember { mutableStateOf(salvo.fase) }
    var metaManual by remember { mutableStateOf(salvo.metaManualKcal?.toString() ?: "") }
    var metaAgua by remember { mutableStateOf(salvo.metaAguaManualMl?.toString() ?: "") }
    var copo by remember { mutableStateOf(salvo.copoMl.toString()) }
    var metaJejum by remember { mutableStateOf(salvo.metaJejumHoras.toString()) }
    var lembretes by remember { mutableStateOf(salvo.lembretes) }
    var chave by remember { mutableStateOf(salvo.chaveGemini) }
    var mostrarChave by remember { mutableStateOf(false) }

    val novo = salvo.copy(
        sexo = sexo,
        anoNascimento = ano.toIntOrNull() ?: salvo.anoNascimento,
        alturaCm = lerNumero(altura) ?: 0.0,
        pesoKg = lerNumero(peso) ?: 0.0,
        pesoMetaKg = lerNumero(pesoMeta) ?: 0.0,
        atividade = atividade,
        objetivo = objetivo,
        fase = fase,
        metaManualKcal = lerInteiro(metaManual)?.takeIf { it > 0 },
        metaAguaManualMl = lerInteiro(metaAgua)?.takeIf { it > 0 },
        copoMl = lerInteiro(copo)?.takeIf { it > 0 } ?: salvo.copoMl,
        metaJejumHoras = lerNumero(metaJejum)?.toInt()?.takeIf { it in 1..72 } ?: salvo.metaJejumHoras,
        lembretes = lembretes,
        chaveGemini = chave,
    )
    val anoAtual = LocalDate.now().year
    val metaKcal = Calculos.metaKcal(novo, anoAtual)
    val faixa = Calculos.faixaKcal(metaKcal)

    // O imePadding antes do verticalScroll: com o teclado aberto, a tela encolhe e os
    // campos do fim (a chave do Gemini) continuam alcançáveis.
    Column(
        Modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Cartao {
            Text("Sua meta: ${inteiro(metaKcal.toDouble())} kcal por dia", fontWeight = FontWeight.Bold)
            Text("Faixa aceita: ${inteiro(faixa.min)} a ${inteiro(faixa.max)} kcal", style = MaterialTheme.typography.bodyMedium)
            Text(
                when {
                    novo.metaManualKcal != null -> "Meta digitada por você."
                    novo.preenchido -> "Gasto do dia ${inteiro(Calculos.gastoDiario(novo, anoAtual))} kcal " +
                        "(fórmula de Mifflin-St Jeor) " + when (novo.objetivo) {
                            Objetivo.PERDER -> "menos ${novo.fase.ajusteKcal} kcal da fase ${novo.fase.numero}."
                            Objetivo.GANHAR -> "mais ${novo.fase.ajusteKcal} kcal da fase ${novo.fase.numero}."
                            Objetivo.MANTER -> "para manter o peso."
                        }
                    else -> "Preencha altura e peso para calcular."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Cartao {
            Text("Perfil", fontWeight = FontWeight.Bold)
            Sexo.entries.forEach { s -> Opcao(s.titulo, sexo == s) { sexo = s } }
            CampoNumero(ano, { ano = it }, "Ano de nascimento")
            CampoNumero(altura, { altura = it }, "Altura", sufixo = "cm")
            CampoNumero(peso, { peso = it }, "Peso atual", sufixo = "kg")
            CampoNumero(pesoMeta, { pesoMeta = it }, "Peso meta", sufixo = "kg")
        }

        Cartao {
            Text("Atividade física", fontWeight = FontWeight.Bold)
            Atividade.entries.forEach { a -> Opcao(a.titulo, atividade == a) { atividade = a } }
        }

        Cartao {
            Text("Objetivo", fontWeight = FontWeight.Bold)
            Objetivo.entries.forEach { o -> Opcao(o.titulo, objetivo == o) { objetivo = o } }
            if (objetivo != Objetivo.MANTER) {
                Spacer(Modifier.height(8.dp))
                Text("Fase da dieta", fontWeight = FontWeight.Bold)
                Text(
                    "Começando agora? Fase 1. Já tem experiência? Fase 2. A cada 7 dias o app pergunta como " +
                        "estão a fome e a energia e sugere a fase.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Fase.entries.forEach { f ->
                    val sinal = if (objetivo == Objetivo.PERDER) "−" else "+"
                    Opcao(
                        "Fase ${f.numero}, ${f.nome} ($sinal${f.ajusteKcal} kcal): ${f.explicacao}",
                        fase == f,
                    ) { fase = f }
                }
            }
            Spacer(Modifier.height(8.dp))
            CampoNumero(
                metaManual,
                { metaManual = it },
                "Meta de calorias à mão (opcional)",
                sufixo = "kcal",
                ajuda = "Vazio = calcular pelo perfil e pela fase.",
            )
        }

        Cartao {
            Text("Água e jejum", fontWeight = FontWeight.Bold)
            CampoNumero(
                metaAgua,
                { metaAgua = it },
                "Meta de água à mão (opcional)",
                sufixo = "ml",
                ajuda = "Vazio = 35 ml por kg (${inteiro(Calculos.metaAguaMl(novo.copy(metaAguaManualMl = null)).toDouble())} ml). " +
                    "Bebida alcoólica e refrigerante somam o volume na meta do dia.",
            )
            CampoNumero(copo, { copo = it }, "Tamanho do copo", sufixo = "ml")
            CampoNumero(metaJejum, { metaJejum = it }, "Meta de jejum", sufixo = "horas")
        }

        Cartao {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Lembretes", fontWeight = FontWeight.Bold)
                    Text(
                        "Avisa quando a água ou a comida estão atrasadas para a hora (das 8h às 22h), " +
                            "o check-in semanal e o relatório do dia. Nada de comida durante o jejum.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = lembretes, onCheckedChange = { lembretes = it })
            }
        }

        Cartao {
            Text("Chave do Gemini (IA)", fontWeight = FontWeight.Bold)
            Text(
                "1. Entre em aistudio.google.com com a sua conta do Google.\n" +
                    "2. Toque em Get API key e depois em Create API key.\n" +
                    "3. Copie o código e cole aqui. Ele fica só neste celular.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = chave,
                onValueChange = { chave = it.trim() },
                label = { Text("Chave") },
                singleLine = true,
                visualTransformation = if (mostrarChave) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { mostrarChave = !mostrarChave }) {
                        Text(if (mostrarChave) "Ocultar" else "Ver")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = {
                ajustes.salvar(novo)
                Lembrete.agendar(contexto, novo.lembretes)
                Toast.makeText(contexto, "Ajustes salvos", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Salvar") }

        Text(
            "Alimentos: Tabela Brasileira de Composição de Alimentos (TACO, NEPA/UNICAMP) e " +
                "Open Food Facts (licença ODbL). IA: Google Gemini.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Opcao(texto: String, marcada: Boolean, aoEscolher: () -> Unit) {
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
