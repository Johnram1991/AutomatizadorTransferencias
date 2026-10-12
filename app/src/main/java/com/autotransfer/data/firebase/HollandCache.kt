package com.autotransfer.data.firebase

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * Cache en disco de los datos de App Holland.
 *
 * Sin esto, cerrar y reabrir la app volvia a bajar todo de Firestore: la marca
 * de "ya descargado hoy" vivia en memoria y se perdia con el proceso. Aqui los
 * clientes y las ventas de una zona se guardan en un JSON dentro de filesDir,
 * asi que al reabrir la app en el mismo dia se abre al instante.
 *
 * La marca diaria es POR ZONA: cambiar de 25 a 11A descarga esa zona, pero
 * volver a 25 no vuelve a descargar nada.
 */
object HollandCache {

    private const val TAG = "HollandCache"
    private const val PREFS = "autotransfer"

    data class Datos(
        val clientes: List<FirebaseCliente>,
        val ventas: List<FirebaseVenta>
    )

    /** Clave de archivo/preferencia para un conjunto de zonas: "25", "25_11a", "todas". */
    fun clave(zonas: List<String>): String {
        val norm = zonas.map { FirebaseRepository.normalizarZona(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        return if (norm.isEmpty()) "todas" else norm.joinToString("_")
    }

    private fun archivo(context: Context, clave: String) =
        File(context.filesDir, "holland_$clave.json")

    // ------------------------------------------------------------ marca diaria

    /** Ultima sincronizacion guardada para esa zona, o "" si nunca se hizo hoy. */
    fun ultimaSync(context: Context, clave: String): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("holland_sync_$clave", "") ?: ""

    /** true si esa zona ya se descargo HOY. */
    fun esDeHoy(context: Context, clave: String): Boolean {
        val guardado = ultimaSync(context, clave)
        return guardado.isNotEmpty() && guardado.substringBefore('|') == hoyIso()
    }

    private fun marcarHoy(context: Context, clave: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("holland_sync_$clave", hoyIso() + "|" + horaActual())
            .apply()
    }

    /** Para mostrar en el menu: "hoy 14:32", "ayer 09:10" o "sin descargar". */
    fun textoUltimaSync(context: Context, clave: String): String {
        val guardado = ultimaSync(context, clave)
        if (guardado.isEmpty()) return "Nunca se ha descargado"
        val partes = guardado.split('|')
        val dia = partes.getOrNull(0) ?: ""
        val hora = partes.getOrNull(1) ?: ""
        val etiqueta = when (dia) {
            hoyIso() -> "Hoy"
            ayerIso() -> "Ayer"
            else -> dia
        }
        return if (hora.isEmpty()) etiqueta else "$etiqueta $hora"
    }

    // --------------------------------------------------------------- disco I/O

    fun guardar(context: Context, clave: String, datos: Datos) {
        try {
            val raiz = JSONObject().apply {
                put("version", 1)
                put("guardado", hoyIso() + "|" + horaActual())
                put(
                    "clientes",
                    JSONArray().apply { datos.clientes.forEach { put(it.toMap()) } }
                )
                put(
                    "ventas",
                    JSONArray().apply { datos.ventas.forEach { put(it.toMap()) } }
                )
            }
            // primero a un temporal y luego renombrar: si la app muere a mitad,
            // el archivo bueno sigue intacto
            val destino = archivo(context, clave)
            val temporal = File(context.filesDir, "${destino.name}.tmp")
            temporal.writeText(raiz.toString())
            if (!temporal.renameTo(destino)) {
                destino.writeText(raiz.toString())
                temporal.delete()
            }
            marcarHoy(context, clave)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo guardar la cache: ${e.message}")
        }
    }

    fun leer(context: Context, clave: String): Datos? {
        val f = archivo(context, clave)
        if (!f.exists()) return null
        return try {
            val raiz = JSONObject(f.readText())
            Datos(
                clientes = (raiz.optJSONArray("clientes") ?: JSONArray()).objetos()
                    .map { j -> FirebaseCliente.fromMap(j.optString("id"), j.aMapa()) },
                ventas = (raiz.optJSONArray("ventas") ?: JSONArray()).objetos()
                    .map { j -> FirebaseVenta.fromMap(j.optString("id"), j.aMapa()) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Cache ilegible, se ignora: ${e.message}")
            f.delete()
            null
        }
    }

    /** JSONObject -> Map<String, Any?> porque los data class leen de un Map. */
    private fun JSONObject.aMapa(): Map<String, Any?> {
        val out = mutableMapOf<String, Any?>()
        keys().forEach { k ->
            out[k] = when (val v = get(k)) {
                JSONObject.NULL -> null
                else -> v
            }
        }
        return out
    }

    private fun JSONArray.objetos(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    // ------------------------------------------------------------------ fechas

    private fun hoyIso(): String = iso(Calendar.getInstance())

    private fun ayerIso(): String = iso(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) })

    private fun iso(cal: Calendar): String {
        val d = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        val m = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
        return "${cal.get(Calendar.YEAR)}-$m-$d"
    }

    private fun horaActual(): String =
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(Calendar.getInstance().time)

    /** Limpia la cache al cerrar sesion. */
    fun borrarTodo(context: Context) {
        try {
            val dir: File = context.filesDir
            val archivos: Array<File>? = dir.listFiles { f: File, _: String ->
                f.name.startsWith("holland_") && f.name.endsWith(".json")
            }
            if (archivos != null) {
                for (a in archivos) a.delete()
            }

            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val claves: MutableSet<String> = sp.all.keys
            val editor = sp.edit()
            for (k in claves) if (k.startsWith("holland_sync_")) editor.remove(k)
            editor.apply()
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo limpiar la cache: ${e.message}")
        }
    }
}
