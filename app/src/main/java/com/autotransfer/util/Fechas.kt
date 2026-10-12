package com.autotransfer.util

import java.util.Calendar
import java.util.TimeZone

/**
 * Formato de fecha usado en toda la app: "dd-MMM-yyyy" (05-Ago-2026).
 * Meses bilingues: [Meses] reconoce abreviaturas en ingles y espanol.
 */
object Fechas {

    private val abreviaturas = listOf(
        "Ene", "Feb", "Mar", "Abr", "May", "Jun",
        "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    )

    /** "25, 26;30" -> ["25","26","30"] */
    fun zonas(input: String): List<String> = input.split(",", " ", ";")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    /**
     * Convierte los milisegundos del DatePicker de Material3 a "dd-MMM-yyyy".
     * El DatePicker trabaja en UTC, por eso el Calendar tambien.
     */
    fun deMillis(millis: Long): String = formatear(
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
    )

    /** Fecha de hoy en hora local: "28-Sep-2026" */
    fun hoy(): String = formatear(Calendar.getInstance())

    /** Primer dia del mes en curso: "01-Sep-2026" */
    fun primerDiaDelMes(): String = formatear(
        Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    )

    /**
     * Rango por defecto de toda la app: primer dia del mes -> hoy.
     * Se usa al abrir en cada pantalla, sin importar que se haya usado antes,
     * para que nunca se abra con las fechas en blanco o con un mes viejo.
     */
    fun rangoPorDefecto(): Pair<String, String> = primerDiaDelMes() to hoy()

    private fun formatear(cal: Calendar): String {
        val dia = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        return "$dia-${abreviaturas[cal.get(Calendar.MONTH)]}-${cal.get(Calendar.YEAR)}"
    }
}
