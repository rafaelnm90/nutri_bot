package com.rafaelnm90.calorias

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as CorAndroid
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/*
 * A única Activity do app: o tema, a troca entre as abas (Diário, Peso, Ajustes)
 * e a tela de adicionar alimento a uma refeição. Ao abrir, liga os lembretes e
 * pede a permissão de notificação.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fundo claro sempre: ícones escuros na barra de status, mesmo com o celular no modo escuro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(CorAndroid.TRANSPARENT, CorAndroid.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(CorAndroid.TRANSPARENT, CorAndroid.TRANSPARENT),
        )
        Lembrete.agendar(this, Ajustes.de(this).perfil.value.lembretes)
        setContent {
            TemaCalorias {
                App()
            }
        }
    }
}

private val Cores = lightColorScheme(
    primary = Color(0xFF1B7F5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0D5),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4C6358),
    secondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFF3D6373),
    tertiaryContainer = Color(0xFFC1E8FB),
    background = Color(0xFFF6F8F5),
    surface = Color(0xFFF6F8F5),
    surfaceVariant = Color(0xFFDCE5DD),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEFF3EF),
    surfaceContainerHigh = Color(0xFFF2F6F2),
    surfaceContainerHighest = Color(0xFFE3EAE4),
    error = Color(0xFFBA1A1A),
)

@Composable
fun TemaCalorias(conteudo: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Cores, content = conteudo)
}

private enum class Aba(val titulo: String) { DIARIO("Diário"), PESO("Peso"), AJUSTES("Ajustes") }

@Composable
fun App() {
    var aba by rememberSaveable { mutableStateOf(Aba.DIARIO) }
    var dia by rememberSaveable { mutableStateOf(hoje()) }
    // Saveable: girar o celular ou o Android fechar o app com a câmera aberta não
    // pode tirar a pessoa da tela de adicionar (a foto chega nela).
    var adicionandoEm by rememberSaveable { mutableStateOf<Refeicao?>(null) }
    // O dia da adição fica preso ao abrir a tela: a volta da câmera depois da meia-noite
    // pode trocar o dia do Diário, mas a comida vai para o dia em que a pessoa tocou em +.
    var diaDaAdicao by rememberSaveable { mutableStateOf(dia) }

    // Voltou ao app num dia novo: quem estava vendo "hoje" passa para o dia novo,
    // senão a comida seria gravada no dia de ontem.
    var ultimoHoje by rememberSaveable { mutableStateOf(hoje()) }
    LifecycleResumeEffect(Unit) {
        val agora = hoje()
        dia = diaAoVoltar(dia, ultimoHoje, agora)
        ultimoHoje = agora
        onPauseOrDispose { }
    }

    // Android 13 em diante: os lembretes só aparecem com a permissão de notificação.
    val contexto = LocalContext.current
    val pedirPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            Ajustes.de(contexto).perfil.value.lembretes &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pedirPermissao.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val refeicao = adicionandoEm
    if (refeicao != null) {
        BackHandler { adicionandoEm = null }
        AdicionarTela(
            dia = diaDaAdicao,
            hojeAgora = ultimoHoje,
            refeicao = refeicao,
            aoVoltar = { adicionandoEm = null },
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Aba.entries.forEach { a ->
                    NavigationBarItem(
                        selected = aba == a,
                        onClick = { aba = a },
                        icon = {
                            Icon(
                                when (a) {
                                    Aba.DIARIO -> Icons.Filled.DateRange
                                    Aba.PESO -> Icons.Filled.Favorite
                                    Aba.AJUSTES -> Icons.Filled.Settings
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(a.titulo) },
                    )
                }
            }
        },
    ) { margens ->
        Box(Modifier.padding(margens)) {
            when (aba) {
                Aba.DIARIO -> DiarioTela(
                    dia = dia,
                    aoMudarDia = { dia = it },
                    aoAdicionar = {
                        diaDaAdicao = dia
                        adicionandoEm = it
                    },
                    aoAbrirAjustes = { aba = Aba.AJUSTES },
                )
                Aba.PESO -> PesoTela()
                Aba.AJUSTES -> AjustesTela()
            }
        }
    }
}
