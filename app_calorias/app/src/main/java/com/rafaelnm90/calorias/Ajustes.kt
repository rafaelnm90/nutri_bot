package com.rafaelnm90.calorias

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/*
 * O perfil da pessoa (Ajustes) guardado nas preferências do celular, inclusive a
 * chave do Gemini, que nunca sai do aparelho a não ser nos pedidos ao próprio Gemini.
 * A jornada de hábitos (JornadaAjustes) fica num arquivo de preferências à parte.
 */
class Ajustes private constructor(contexto: Context) {
    private val prefs = contexto.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
    private val _perfil = MutableStateFlow(ler())

    val perfil: StateFlow<Perfil> = _perfil

    private fun ler(): Perfil {
        val padrao = Perfil()
        return Perfil(
            sexo = enumOu(prefs.getString("sexo", null), padrao.sexo),
            anoNascimento = prefs.getInt("ano_nascimento", padrao.anoNascimento),
            alturaCm = prefs.getFloat("altura_cm", padrao.alturaCm.toFloat()).toDouble(),
            pesoKg = prefs.getFloat("peso_kg", padrao.pesoKg.toFloat()).toDouble(),
            pesoMetaKg = prefs.getFloat("peso_meta_kg", padrao.pesoMetaKg.toFloat()).toDouble(),
            atividade = enumOu(prefs.getString("atividade", null), padrao.atividade),
            objetivo = enumOu(prefs.getString("objetivo", null), padrao.objetivo),
            fase = Fase.de(prefs.getInt("fase", padrao.fase.numero)),
            inicioFase = prefs.getString("inicio_fase", null),
            metaManualKcal = prefs.getInt("meta_manual", 0).takeIf { it > 0 },
            metaAguaManualMl = prefs.getInt("meta_agua_manual", 0).takeIf { it > 0 },
            copoMl = prefs.getInt("copo", padrao.copoMl),
            metaJejumHoras = prefs.getInt("meta_jejum", padrao.metaJejumHoras),
            lembretes = prefs.getBoolean("lembretes", padrao.lembretes),
            chaveGemini = prefs.getString("chave_gemini", "") ?: "",
        )
    }

    /**
     * Grava o perfil. Mudou a fase ou o objetivo (ou é a primeira vez com um
     * objetivo de emagrecer ou ganhar)? A contagem do check-in semanal recomeça hoje.
     */
    fun salvar(novo: Perfil) {
        val antigo = _perfil.value
        val recomeca = novo.fase != antigo.fase || novo.objetivo != antigo.objetivo || antigo.inicioFase == null
        val p = novo.copy(
            chaveGemini = novo.chaveGemini.trim(),
            inicioFase = if (recomeca) LocalDate.now().toString() else antigo.inicioFase,
        )
        prefs.edit()
            .putString("sexo", p.sexo.name)
            .putInt("ano_nascimento", p.anoNascimento)
            .putFloat("altura_cm", p.alturaCm.toFloat())
            .putFloat("peso_kg", p.pesoKg.toFloat())
            .putFloat("peso_meta_kg", p.pesoMetaKg.toFloat())
            .putString("atividade", p.atividade.name)
            .putString("objetivo", p.objetivo.name)
            .putInt("fase", p.fase.numero)
            .putString("inicio_fase", p.inicioFase)
            .putInt("meta_manual", p.metaManualKcal ?: 0)
            .putInt("meta_agua_manual", p.metaAguaManualMl ?: 0)
            .putInt("copo", p.copoMl)
            .putInt("meta_jejum", p.metaJejumHoras)
            .putBoolean("lembretes", p.lembretes)
            .putString("chave_gemini", p.chaveGemini)
            .apply()
        _perfil.value = p
    }

    /** Resposta do check-in semanal: fica na fase escolhida e a contagem recomeça hoje. */
    fun concluirCheckIn(fase: Fase) {
        val p = _perfil.value.copy(fase = fase, inicioFase = LocalDate.now().toString())
        prefs.edit()
            .putInt("fase", p.fase.numero)
            .putString("inicio_fase", p.inicioFase)
            .apply()
        _perfil.value = p
    }

    private inline fun <reified T : Enum<T>> enumOu(nome: String?, padrao: T): T =
        enumValues<T>().firstOrNull { it.name == nome } ?: padrao

    companion object {
        @Volatile
        private var unico: Ajustes? = null

        fun de(contexto: Context): Ajustes =
            unico ?: synchronized(this) {
                unico ?: Ajustes(contexto.applicationContext).also { unico = it }
            }
    }
}

/**
 * A jornada de hábitos nas preferências "jornada", à parte do perfil: nunca usa as chaves
 * "fase" e "inicio_fase" das fases da dieta. As regras ficam em Nutricao.kt (Jornada); aqui
 * só se lê e se grava. As telas acompanham "salva", como acompanham o perfil.
 */
class JornadaAjustes private constructor(contexto: Context) {
    private val prefs = contexto.getSharedPreferences("jornada", Context.MODE_PRIVATE)
    private val _salva = MutableStateFlow(ler())

    val salva: StateFlow<JornadaSalva> = _salva

    /** O interruptor de Ajustes: vale na hora, sem o Salvar. Desligar não apaga nada. */
    fun ligar(ligada: Boolean) = gravar(_salva.value.copy(ligada = ligada))

    /** "Começar a jornada" (ou "Salvar as escolhas"): etapa nova começa hoje; a que já está em curso continua. */
    fun montar(seguro: Boolean, escolhas: Map<Habito, Escolha>, outros: List<OutroHabito>) =
        gravar(Jornada.montar(_salva.value, seguro, escolhas, outros, LocalDate.now().toString()))

    /** Toque num dia da semana da etapa; null volta ao que o diário diz. */
    fun tocar(dia: String, habito: Habito, estado: EstadoDia?) =
        gravar(Jornada.comToque(_salva.value, dia, habito, estado))

    fun vencer(habito: Habito, dia: String) = gravar(Jornada.comVitoria(_salva.value, habito, dia))

    private fun ler() = JornadaSalva(
        ligada = prefs.getBoolean("ligada", false),
        montada = prefs.getBoolean("montada", false),
        seguro = prefs.getBoolean("saude_sim", false),
        escolhas = Jornada.escolhasDeTexto(prefs.getString("escolhas", null)),
        outros = Jornada.outrosDeTexto(prefs.getString("outros", null)),
        etapa = Habito.deId(prefs.getString("etapa", null)),
        // Texto que não é data vira etapa sem início: o cartão do Diário, que reconta a etapa ao
        // abrir o app, mostra "sem etapa" em vez de fechar o app, e montar de novo começa hoje.
        inicioEtapa = prefs.getString("inicio_etapa", null)
            ?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess },
        vencidas = Jornada.vencidasDeTexto(prefs.getString("vencidas", null)),
        aguardando = prefs.getBoolean("aguardando", false),
        toques = Jornada.toquesDeTexto(prefs.getString("toques", null)),
    )

    // Grava tudo de uma vez: o estado é pequeno, e assim nenhuma chave fica para trás.
    private fun gravar(nova: JornadaSalva) {
        prefs.edit()
            .putBoolean("ligada", nova.ligada)
            .putBoolean("montada", nova.montada)
            .putBoolean("saude_sim", nova.seguro)
            .putString("escolhas", Jornada.escolhasParaTexto(nova.escolhas))
            .putString("outros", Jornada.outrosParaTexto(nova.outros))
            .putString("etapa", nova.etapa?.id)
            .putString("inicio_etapa", nova.inicioEtapa)
            .putString("vencidas", Jornada.vencidasParaTexto(nova.vencidas))
            .putBoolean("aguardando", nova.aguardando)
            .putString("toques", Jornada.toquesParaTexto(nova.toques))
            .apply()
        _salva.value = nova
    }

    companion object {
        @Volatile
        private var unico: JornadaAjustes? = null

        fun de(contexto: Context): JornadaAjustes =
            unico ?: synchronized(this) {
                unico ?: JornadaAjustes(contexto.applicationContext).also { unico = it }
            }
    }
}
