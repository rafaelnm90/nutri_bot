package com.rafaelnm90.calorias

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/*
 * Os lembretes no celular, como os do nutri_bot: água e comida atrasadas para a
 * hora (das 8h às 22h, regras em Nutricao.kt, objeto Lembretes), o check-in
 * semanal das fases e o relatório do dia. O Android roda a conferência a cada
 * 30 minutos, mesmo com o app fechado.
 */
object Lembrete {
    private const val TRABALHO = "lembretes"
    private const val CANAL = "lembretes"

    /** Liga ou desliga a conferência periódica. Chamado ao abrir o app e ao salvar os Ajustes. */
    fun agendar(contexto: Context, ligado: Boolean) {
        val wm = WorkManager.getInstance(contexto)
        if (!ligado) {
            wm.cancelUniqueWork(TRABALHO)
            return
        }
        val pedido = PeriodicWorkRequestBuilder<LembreteWorker>(30, TimeUnit.MINUTES).build()
        wm.enqueueUniquePeriodicWork(TRABALHO, ExistingPeriodicWorkPolicy.KEEP, pedido)
    }

    fun notificar(contexto: Context, id: Int, titulo: String, texto: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val gerente = contexto.getSystemService(NotificationManager::class.java)
        gerente.createNotificationChannel(
            NotificationChannel(CANAL, "Lembretes", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val abrir = PendingIntent.getActivity(
            contexto,
            id,
            Intent(contexto, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notificacao = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(contexto).notify(id, notificacao)
    }
}

class LembreteWorker(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    private val marcas = contexto.getSharedPreferences("lembretes", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val perfil = Ajustes.de(ctx).perfil.value
        if (!perfil.lembretes) return Result.success()
        val agora = LocalTime.now()
        val fracao = Lembretes.fracaoDoDia(agora.hour, agora.minute) ?: return Result.success()

        val banco = Banco.de(ctx)
        val dia = hoje()
        val registros = banco.registrosDoDia(dia)

        val metaAgua = Calculos.metaAguaMl(perfil) + registros.aguaExtraMl()
        val bebido = banco.aguaMl(dia)
        if (Lembretes.aguaAtrasada(metaAgua, bebido, fracao, horasDesde("agua"))) {
            Lembrete.notificar(
                ctx, 1, "🔔 Hora de beber água",
                "Você está um pouco atrás na água de hoje: faltam ${umaCasa((metaAgua - bebido) / 1000.0)} L. " +
                    "Que tal um copo agora? 🚰",
            )
            marcar("agua")
        }

        val metaKcal = Calculos.metaKcal(perfil, LocalDate.now().year)
        val consumidas = registros.somar().kcal
        val emJejum = banco.jejumAtual() != null
        if (Lembretes.comidaAtrasada(metaKcal, consumidas, fracao, horasDesde("comida"), emJejum)) {
            val alerta = when (perfil.objetivo) {
                Objetivo.GANHAR -> "Você está atrasado no superávit para este horário. Lembre-se: precisa comer para construir músculos."
                Objetivo.PERDER -> "Você está comendo menos do que o planejado para este horário. Cuidado com intervalos grandes: eles trazem fome e exagero mais tarde."
                Objetivo.MANTER -> "Você está um pouco atrasado na energia do dia."
            }
            Lembrete.notificar(
                ctx, 2, "🔔 Hora de comer",
                "$alerta Ainda faltam ${inteiro(metaKcal - consumidas)} kcal hoje. Depois, registre no app. 🍽️",
            )
            marcar("comida")
        }

        if (CheckIn.pendente(perfil, LocalDate.now()) && !avisouHoje("checkin")) {
            Lembrete.notificar(
                ctx, 3, "🗓️ Check-in semanal",
                "Você completou 7 dias na Fase ${perfil.fase.numero}. Abra o app e conte como estão a fome e a energia.",
            )
            marcar("checkin")
        }

        if (agora.hour == 21 && registros.isNotEmpty() && !avisouHoje("relatorio")) {
            Lembrete.notificar(
                ctx, 4, "📈 Relatório do dia",
                "Seu dia está fechando. Abra o Diário e toque em Relatório do dia para ver a avaliação da IA.",
            )
            marcar("relatorio")
        }
        return Result.success()
    }

    private fun horasDesde(tipo: String): Double {
        val ultimo = marcas.getLong(tipo, 0L)
        if (ultimo == 0L) return Double.MAX_VALUE
        return (System.currentTimeMillis() - ultimo) / 3_600_000.0
    }

    private fun avisouHoje(tipo: String): Boolean = marcas.getString("${tipo}_dia", null) == hoje()

    private fun marcar(tipo: String) {
        marcas.edit()
            .putLong(tipo, System.currentTimeMillis())
            .putString("${tipo}_dia", hoje())
            .apply()
    }
}
