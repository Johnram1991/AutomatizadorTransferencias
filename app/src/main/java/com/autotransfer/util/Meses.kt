package com.autotransfer.util

object Meses {

    private val numerosPorAbreviatura = mapOf(
        "ene" to 1, "jan" to 1,
        "feb" to 2,
        "mar" to 3,
        "abr" to 4, "apr" to 4,
        "may" to 5,
        "jun" to 6,
        "jul" to 7,
        "ago" to 8, "aug" to 8,
        "sep" to 9, "set" to 9,
        "oct" to 10,
        "nov" to 11,
        "dic" to 12, "dec" to 12
    )

    private val nombresEspanol = listOf(
        "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    fun numero(token: String): Int {
        if (token.isBlank()) return 0
        val t = token.trim().lowercase()
        val numerico = t.toIntOrNull()
        if (numerico != null) return if (numerico in 1..12) numerico else 0
        return numerosPorAbreviatura[t.take(3)] ?: 0
    }

    fun nombreEspanol(numero: Int): String {
        return if (numero in 1..12) nombresEspanol[numero] else ""
    }
}