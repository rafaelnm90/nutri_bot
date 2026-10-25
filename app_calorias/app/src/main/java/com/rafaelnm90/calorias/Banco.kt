package com.rafaelnm90.calorias

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/*
 * Tudo o que o app guarda no celular (banco SQLite "calorias.db"): o diário,
 * os alimentos criados ou lidos pelo código de barras, a água, os jejuns, os
 * exercícios e o peso.
 * Nada vai para servidor. As telas leem por aqui e redesenham quando "versao" muda.
 */

data class Jejum(val id: Long, val inicio: Long, val fim: Long?)

class Banco private constructor(contexto: Context) :
    SQLiteOpenHelper(contexto, "calorias.db", null, 1) {

    private val _versao = MutableStateFlow(0)

    /** Muda a cada gravação; as telas releem o que mostram quando ela muda. */
    val versao: StateFlow<Int> = _versao

    private fun mudou() {
        _versao.value = _versao.value + 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE registros (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                dia TEXT NOT NULL,
                refeicao TEXT NOT NULL,
                nome TEXT NOT NULL,
                gramas REAL NOT NULL,
                kcal100 REAL NOT NULL,
                prot100 REAL NOT NULL,
                carb100 REAL NOT NULL,
                gord100 REAL NOT NULL,
                $COLUNAS_MICROS,
                origem TEXT NOT NULL,
                criado_em INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX registros_dia ON registros(dia)")
        db.execSQL(
            """CREATE TABLE meus_alimentos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nome TEXT NOT NULL,
                kcal100 REAL NOT NULL,
                prot100 REAL NOT NULL,
                carb100 REAL NOT NULL,
                gord100 REAL NOT NULL,
                $COLUNAS_MICROS,
                porcao_g REAL,
                porcao_nome TEXT,
                codigo_barras TEXT,
                origem TEXT NOT NULL)"""
        )
        db.execSQL("CREATE TABLE agua (dia TEXT PRIMARY KEY, ml INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE jejum (id INTEGER PRIMARY KEY AUTOINCREMENT, inicio INTEGER NOT NULL, fim INTEGER)")
        db.execSQL("CREATE TABLE peso (dia TEXT PRIMARY KEY, kg REAL NOT NULL)")
        db.execSQL(
            """CREATE TABLE exercicios (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                dia TEXT NOT NULL,
                descricao TEXT NOT NULL,
                minutos INTEGER NOT NULL,
                kcal INTEGER NOT NULL)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, antiga: Int, nova: Int) {
        // Versão 1: nada a migrar ainda.
    }

    // --- Diário ---

    fun registrosDoDia(dia: String): List<Registro> =
        readableDatabase.rawQuery(
            "SELECT * FROM registros WHERE dia = ? ORDER BY criado_em",
            arrayOf(dia),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Registro(
                            id = c.long("id"),
                            dia = c.texto("dia"),
                            refeicao = Refeicao.valueOf(c.texto("refeicao")),
                            alimento = c.alimento(),
                            gramas = c.real("gramas"),
                        )
                    )
                }
            }
        }

    fun adicionar(dia: String, refeicao: Refeicao, alimento: Alimento, gramas: Double) {
        writableDatabase.insert("registros", null, ContentValues().apply {
            put("dia", dia)
            put("refeicao", refeicao.name)
            put("gramas", gramas)
            putAlimento(alimento)
            put("criado_em", System.currentTimeMillis())
        })
        mudou()
    }

    fun mudarGramas(id: Long, gramas: Double) {
        writableDatabase.update(
            "registros",
            ContentValues().apply { put("gramas", gramas) },
            "id = ?",
            arrayOf(id.toString()),
        )
        mudou()
    }

    fun apagarRegistro(id: Long) {
        writableDatabase.delete("registros", "id = ?", arrayOf(id.toString()))
        mudou()
    }

    /**
     * Os últimos alimentos usados, sem repetir nome, com a quantidade da última
     * vez como porção sugerida. É a lista que aparece antes de digitar a busca.
     */
    fun recentes(limite: Int = 40): List<Alimento> =
        readableDatabase.rawQuery(
            // No SQLite, as colunas soltas vêm da mesma linha do MAX(criado_em).
            """SELECT *, MAX(criado_em) AS ultimo
               FROM registros GROUP BY nome ORDER BY ultimo DESC LIMIT ?""",
            arrayOf(limite.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(c.alimento().copy(porcaoG = c.real("gramas")))
            }
        }

    // --- Meus alimentos (criados à mão ou lidos pelo código de barras) ---

    fun meusAlimentos(): List<Alimento> =
        readableDatabase.rawQuery("SELECT * FROM meus_alimentos ORDER BY nome", null).use { c ->
            buildList { while (c.moveToNext()) add(c.meuAlimento()) }
        }

    fun porCodigo(codigo: String): Alimento? =
        readableDatabase.rawQuery(
            "SELECT * FROM meus_alimentos WHERE codigo_barras = ? ORDER BY id DESC LIMIT 1",
            arrayOf(codigo),
        ).use { c -> if (c.moveToNext()) c.meuAlimento() else null }

    /** Grava o alimento; se já há um com o mesmo código de barras, troca pelo novo. */
    fun salvarMeuAlimento(a: Alimento) {
        val db = writableDatabase
        if (a.codigoBarras != null) {
            db.delete("meus_alimentos", "codigo_barras = ?", arrayOf(a.codigoBarras))
        }
        db.insert("meus_alimentos", null, ContentValues().apply {
            putAlimento(a)
            put("porcao_g", a.porcaoG)
            put("porcao_nome", a.porcaoNome)
            put("codigo_barras", a.codigoBarras)
        })
        mudou()
    }

    // --- Água ---

    fun aguaMl(dia: String): Int =
        readableDatabase.rawQuery("SELECT ml FROM agua WHERE dia = ?", arrayOf(dia)).use { c ->
            if (c.moveToNext()) c.getInt(0) else 0
        }

    fun somarAgua(dia: String, ml: Int) {
        val total = (aguaMl(dia) + ml).coerceAtLeast(0)
        writableDatabase.insertWithOnConflict(
            "agua",
            null,
            ContentValues().apply {
                put("dia", dia)
                put("ml", total)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        mudou()
    }

    // --- Jejum ---

    /** O jejum em andamento (sem fim), ou null se a pessoa está na janela de comer. */
    fun jejumAtual(): Jejum? =
        readableDatabase.rawQuery("SELECT * FROM jejum WHERE fim IS NULL ORDER BY id DESC LIMIT 1", null)
            .use { c -> if (c.moveToNext()) c.jejum() else null }

    fun ultimoJejumEncerrado(): Jejum? =
        readableDatabase.rawQuery("SELECT * FROM jejum WHERE fim IS NOT NULL ORDER BY fim DESC LIMIT 1", null)
            .use { c -> if (c.moveToNext()) c.jejum() else null }

    fun iniciarJejum(inicio: Long = System.currentTimeMillis()) {
        if (jejumAtual() != null) return
        writableDatabase.insert("jejum", null, ContentValues().apply { put("inicio", inicio) })
        mudou()
    }

    fun encerrarJejum(fim: Long = System.currentTimeMillis()) {
        writableDatabase.update(
            "jejum",
            ContentValues().apply { put("fim", fim) },
            "fim IS NULL",
            null,
        )
        mudou()
    }

    fun mudarInicioDoJejum(inicio: Long) {
        writableDatabase.update(
            "jejum",
            ContentValues().apply { put("inicio", inicio) },
            "fim IS NULL",
            null,
        )
        mudou()
    }

    // --- Exercícios ---

    fun exerciciosDoDia(dia: String): List<Exercicio> =
        readableDatabase.rawQuery("SELECT * FROM exercicios WHERE dia = ? ORDER BY id", arrayOf(dia)).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Exercicio(
                            id = c.long("id"),
                            dia = c.texto("dia"),
                            descricao = c.texto("descricao"),
                            minutos = c.getInt(c.getColumnIndexOrThrow("minutos")),
                            kcal = c.getInt(c.getColumnIndexOrThrow("kcal")),
                        )
                    )
                }
            }
        }

    fun adicionarExercicio(dia: String, e: Exercicio) {
        writableDatabase.insert("exercicios", null, ContentValues().apply {
            put("dia", dia)
            put("descricao", e.descricao)
            put("minutos", e.minutos)
            put("kcal", e.kcal)
        })
        mudou()
    }

    fun apagarExercicio(id: Long) {
        writableDatabase.delete("exercicios", "id = ?", arrayOf(id.toString()))
        mudou()
    }

    // --- Peso ---

    /** Pesos registrados, do mais antigo ao mais novo: (dia, kg). */
    fun pesos(): List<Pair<String, Double>> =
        readableDatabase.rawQuery("SELECT dia, kg FROM peso ORDER BY dia", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getDouble(1)) }
        }

    fun salvarPeso(dia: String, kg: Double) {
        writableDatabase.insertWithOnConflict(
            "peso",
            null,
            ContentValues().apply {
                put("dia", dia)
                put("kg", kg)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        mudou()
    }

    fun apagarPeso(dia: String) {
        writableDatabase.delete("peso", "dia = ?", arrayOf(dia))
        mudou()
    }

    // --- Leitura das linhas ---

    private fun Cursor.texto(coluna: String): String = getString(getColumnIndexOrThrow(coluna))
    private fun Cursor.real(coluna: String): Double = getDouble(getColumnIndexOrThrow(coluna))
    private fun Cursor.long(coluna: String): Long = getLong(getColumnIndexOrThrow(coluna))

    private fun Cursor.realOuNulo(coluna: String): Double? {
        val i = getColumnIndexOrThrow(coluna)
        return if (isNull(i)) null else getDouble(i)
    }

    private fun Cursor.textoOuNulo(coluna: String): String? {
        val i = getColumnIndexOrThrow(coluna)
        return if (isNull(i)) null else getString(i)
    }

    private fun Cursor.alimento() = Alimento(
        nome = texto("nome"),
        kcal100 = real("kcal100"),
        prot100 = real("prot100"),
        carb100 = real("carb100"),
        gord100 = real("gord100"),
        micros100 = Micros(
            fibra = real("fibra100"),
            acucar = real("acucar100"),
            sodio = real("sodio100"),
            calcio = real("calcio100"),
            ferro = real("ferro100"),
            zinco = real("zinco100"),
            potassio = real("potassio100"),
            vitaminaC = real("vitc100"),
            vitaminaA = real("vita100"),
        ),
        pedeAgua = getInt(getColumnIndexOrThrow("pede_agua")) == 1,
        origem = texto("origem"),
    )

    private fun ContentValues.putAlimento(a: Alimento) {
        put("nome", a.nome)
        put("kcal100", a.kcal100)
        put("prot100", a.prot100)
        put("carb100", a.carb100)
        put("gord100", a.gord100)
        put("fibra100", a.micros100.fibra)
        put("acucar100", a.micros100.acucar)
        put("sodio100", a.micros100.sodio)
        put("calcio100", a.micros100.calcio)
        put("ferro100", a.micros100.ferro)
        put("zinco100", a.micros100.zinco)
        put("potassio100", a.micros100.potassio)
        put("vitc100", a.micros100.vitaminaC)
        put("vita100", a.micros100.vitaminaA)
        put("pede_agua", if (a.pedeAgua) 1 else 0)
        put("origem", a.origem)
    }

    private fun Cursor.meuAlimento() = alimento().copy(
        porcaoG = realOuNulo("porcao_g"),
        porcaoNome = textoOuNulo("porcao_nome"),
        codigoBarras = textoOuNulo("codigo_barras"),
    )

    private fun Cursor.jejum(): Jejum {
        val iFim = getColumnIndexOrThrow("fim")
        return Jejum(
            id = long("id"),
            inicio = long("inicio"),
            fim = if (isNull(iFim)) null else getLong(iFim),
        )
    }

    companion object {
        /** Valores de 100 g além dos macros, e se a bebida pede água extra. */
        private const val COLUNAS_MICROS = """fibra100 REAL NOT NULL DEFAULT 0,
                acucar100 REAL NOT NULL DEFAULT 0,
                sodio100 REAL NOT NULL DEFAULT 0,
                calcio100 REAL NOT NULL DEFAULT 0,
                ferro100 REAL NOT NULL DEFAULT 0,
                zinco100 REAL NOT NULL DEFAULT 0,
                potassio100 REAL NOT NULL DEFAULT 0,
                vitc100 REAL NOT NULL DEFAULT 0,
                vita100 REAL NOT NULL DEFAULT 0,
                pede_agua INTEGER NOT NULL DEFAULT 0"""

        @Volatile
        private var unico: Banco? = null

        fun de(contexto: Context): Banco =
            unico ?: synchronized(this) {
                unico ?: Banco(contexto.applicationContext).also { unico = it }
            }
    }
}
