package com.autotransfer.util

import kotlin.math.abs

/**
 * Busqueda difusa para el listado de clientes: encuentra "jose" aunque el
 * cliente se llame "Jose A. Perez", y tolera acentos, signos y espacios extra.
 */
object Fuzzy {

    private val sinAlfanumericos = Regex("[^\\p{L}\\p{N} ]")

    fun normalizar(texto: String): String = texto.lowercase()
        .replace(sinAlfanumericos, " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Coincide si [texto] contiene la [consulta], si alguna palabra empieza con
     * ella, o si le llega a [maxDistancia] de Levenshtein (tolera typos).
     */
    fun coincide(texto: String, consulta: String, maxDistancia: Int = 2): Boolean {
        val q = normalizar(consulta)
        if (q.isEmpty()) return true
        val t = normalizar(texto)
        if (t.isEmpty()) return false
        if (t.contains(q)) return true
        return t.split(" ").any { palabra ->
            palabra.startsWith(q) ||
                (abs(palabra.length - q.length) <= maxDistancia &&
                    levenshtein(palabra, q) <= maxDistancia)
        }
    }

    /** Coincide contra varios campos: el cliente aparece si alguno encaja. */
    fun coincideAlguno(consulta: String, vararg textos: String): Boolean {
        if (normalizar(consulta).isEmpty()) return true
        return textos.any { coincide(it, consulta) }
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previa = IntArray(b.length + 1) { it }
        var actual = IntArray(b.length + 1)
        for (i in 1..a.length) {
            actual[0] = i
            for (j in 1..b.length) {
                val costo = if (a[i - 1] == b[j - 1]) 0 else 1
                actual[j] = minOf(
                    actual[j - 1] + 1,
                    previa[j] + 1,
                    previa[j - 1] + costo
                )
            }
            val aux = previa
            previa = actual
            actual = aux
        }
        return previa[b.length]
    }
}
