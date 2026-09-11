package com.autotransfer.data.firebase

import com.autotransfer.util.Meses
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.Normalizer

class FirebaseRepository {

    private val db by lazy { FirebaseFirestore.getInstance() }

    suspend fun getClientes(): List<FirebaseCliente> {
        val snapshot = db.collection("clientes")
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data
            if (doc.exists() && data != null) {
                FirebaseCliente.fromMap(doc.id, data)
            } else null
        }
    }

    suspend fun getVentasTransfer(): List<FirebaseVenta> {
        val snapshot = db.collection("ventas")
            .whereEqualTo("VentaT", "T")
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data
            if (doc.exists() && data != null) {
                FirebaseVenta.fromMap(doc.id, data)
            } else null
        }
    }

    suspend fun searchClientes(query: String, zonas: List<String>): List<FirebaseCliente> {
        val snapshot = db.collection("clientes")
            .get()
            .await()
        val q = query.lowercase().trim()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data
            if (doc.exists() && data != null) {
                val c = FirebaseCliente.fromMap(doc.id, data)
                val matchZona = zonas.isEmpty() || zonas.any { normalizarZona(c.zona) == normalizarZona(it) }
                val matchQuery = q.isEmpty() ||
                    c.nombre.lowercase().contains(q) ||
                    c.empresa.lowercase().contains(q) ||
                    c.direccion.lowercase().contains(q) ||
                    c.rfc.lowercase().contains(q)
                if (matchZona && matchQuery) c else null
            } else null
        }.sortedBy { it.nombre }
    }

    suspend fun getVentasByCliente(clienteNombre: String): List<FirebaseVenta> {
        val snapshot = db.collection("ventas")
            .whereEqualTo("VentaT", "T")
            .get()
            .await()
        val q = clienteNombre.lowercase().trim()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data
            if (doc.exists() && data != null) {
                val v = FirebaseVenta.fromMap(doc.id, data)
                if (v.clienteNombre.lowercase().trim().contains(q)) v else null
            } else null
        }
    }

    suspend fun getVentasParaExcel(
        zonas: List<String>,
        desde: String,
        hasta: String
    ): List<OperacionData> {
        val zonasNorm = zonas.map { normalizarZona(it) }.filter { it.isNotEmpty() }
        val clientesZona = db.collection("clientes").get().await().documents.mapNotNull { doc ->
            val data = doc.data
            if (doc.exists() && data != null) {
                val c = FirebaseCliente.fromMap(doc.id, data)
                if (zonasNorm.isEmpty() || zonasNorm.any { normalizarZona(c.zona) == it }) c else null
            } else null
        }
        val nombresZona = clientesZona.filter { normalizarZona(it.zona).isNotEmpty() }
            .map { it.nombre.lowercase().trim() }
        val direccionPorNombre = clientesZona.filter { normalizarZona(it.zona).isNotEmpty() }
            .associate { it.nombre.lowercase().trim() to it.direccion }

        val ventas = db.collection("ventas")
            .whereEqualTo("VentaT", "T")
            .get().await().documents.mapNotNull { doc ->
                val data = doc.data
                if (doc.exists() && data != null) FirebaseVenta.fromMap(doc.id, data) else null
            }

        val desdeNum = if (desde.isNotBlank()) dateToNum(desde) else 0L
        val hastaNum = if (hasta.isNotBlank()) dateToNum(hasta) else Long.MAX_VALUE
        val coincidirConZona = zonasNorm.isNotEmpty()
        return ventas.mapNotNull { v ->
            val d = dateToNum(v.fechaSubidoString)
            val okFecha = d in desdeNum..hastaNum
            if (!okFecha) return@mapNotNull null
            val vZona = normalizarZona(v.zonaVenta)
            val ventaTieneZona = v.zonaVenta.isNotBlank() && vZona.isNotEmpty()
            val coincidenciaCliente = zonasNorm.isNotEmpty() && !ventaTieneZona && nombresZona.any { matchNombres(normalizarNombre(v.clienteNombre), it) }
            val protegidaPorVenta = ventaTieneZona && vZona in zonasNorm
            val incluida = !coincidirConZona || protegidaPorVenta || coincidenciaCliente
            if (!incluida) return@mapNotNull null
            val clienteMatch = nombresZona.firstOrNull { matchNombres(normalizarNombre(v.clienteNombre), it) }
            val dist = listOf("COVEGUSA", "COFARVET", "GRUCOSA")
                .firstOrNull { v.observa.uppercase().contains(it) } ?: ""
            OperacionData(
                venta = v,
                direccion = if (clienteMatch != null) direccionPorNombre[clienteMatch] ?: "" else "",
                distribuidor = dist
            )
        }
    }

    companion object {
        fun normalizarZona(z: String): String {
            return z.lowercase()
                .replace(" ", "")
                .replace("-", "")
                .removePrefix("z")
        }

        fun normalizarNombre(nombre: String): String {
            return nombre.lowercase()
                .replace(Regex("[^\\p{L}\\p{N} ]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun matchNombres(a: String, b: String): Boolean {
            if (a == b) return true
            if (a.length >= 5 && b.length >= 5 && (a.contains(b) || b.contains(a))) return true
            if (levenshtein(a, b) <= 2) return true
            return false
        }

        private fun levenshtein(a: String, b: String): Int {
            val costs = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                costs[0] = i
                var nw = i - 1
                for (j in 1..b.length) {
                    val cj = minOf(costs[j] + 1, costs[j - 1] + 1, nw + if (a[i - 1] == b[j - 1]) 0 else 1)
                    nw = costs[j]
                    costs[j] = cj
                }
            }
            return costs[b.length]
        }
    }

    private fun dateToNum(fecha: String): Long {
        if (fecha.isBlank()) return 0L
        val partes = fecha.split("-", "/")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (partes.size != 3) return 0L
        val dia = partes[0].padStart(2, '0')
        val mes = Meses.numero(partes[1]).toString().padStart(2, '0')
        val anio = partes[2]
        return (anio + mes + dia).toLongOrNull() ?: 0L
    }

    data class OperacionData(
        val venta: FirebaseVenta,
        val direccion: String,
        val distribuidor: String
    )

    suspend fun searchClienteConVentas(nombre: String): Pair<List<FirebaseCliente>, List<FirebaseVenta>> {
        val q = nombre.lowercase().trim()
        val clientes = db.collection("clientes").get().await().documents.mapNotNull { doc ->
            val data = doc.data
            if (doc.exists() && data != null) {
                val c = FirebaseCliente.fromMap(doc.id, data)
                if (c.nombre.lowercase().trim().contains(q)) c else null
            } else null
        }
        val ventas = db.collection("ventas")
            .whereEqualTo("VentaT", "T")
            .get()
            .await()
            .documents.mapNotNull { doc ->
                val data = doc.data
                if (doc.exists() && data != null) {
                    val v = FirebaseVenta.fromMap(doc.id, data)
                    if (v.clienteNombre.lowercase().trim().contains(q)) v else null
                } else null
            }
        return clientes to ventas
    }

    suspend fun diagnosticClientesRaw(): String {
        val sb = StringBuilder()
        try {
            val snap = db.collection("clientes").limit(2).get().await()
            sb.appendLine("=== CLIENTES (${snap.documents.size} docs) ===")
            for ((i, doc) in snap.documents.withIndex()) {
                sb.appendLine("--- Cliente ${i + 1} | id=${doc.id} ---")
                if (!doc.exists()) { sb.appendLine("  (no existe)"); continue }
                val data = doc.data ?: continue
                for ((key, value) in data) {
                    val tipo = when (value) {
                        null -> "null"
                        is String -> "String"
                        is Number -> "Number($value)"
                        is Map<*, *> -> "Map{${(value as Map<*,*>).keys.joinToString(",")}}"
                        is List<*> -> "List(${value.size})"
                        else -> value::class.simpleName ?: "Unknown"
                    }
                    sb.appendLine("  $key : $tipo = $value")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("ERROR clientes: ${e.message}")
        }
        try {
            sb.appendLine()
            val vSnap = db.collection("ventas").limit(2).get().await()
            sb.appendLine("=== VENTAS (sin filtro) (${vSnap.documents.size} docs) ===")
            for ((i, doc) in vSnap.documents.withIndex()) {
                sb.appendLine("--- Venta ${i + 1} | id=${doc.id} ---")
                if (!doc.exists()) { sb.appendLine("  (no existe)"); continue }
                val data = doc.data ?: continue
                for ((key, value) in data) {
                    val tipo = when (value) {
                        null -> "null"
                        is String -> "String"
                        is Number -> "Number($value)"
                        is Map<*, *> -> "Map{${(value as Map<*,*>).keys.joinToString(",")}}"
                        is List<*> -> "List(${value.size})"
                        else -> value::class.simpleName ?: "Unknown"
                    }
                    sb.appendLine("  $key : $tipo = $value")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("ERROR ventas: ${e.message}")
        }
        try {
            sb.appendLine()
            for (colName in listOf("usuarios", "users", "zonas", "zona")) {
                try {
                    val colSnap = db.collection(colName).limit(1).get().await()
                    sb.appendLine("=== COLECCIÓN '$colName' (${colSnap.documents.size} docs) ===")
                    for ((i, doc) in colSnap.documents.withIndex()) {
                        sb.appendLine("--- Doc ${i + 1} | id=${doc.id} ---")
                        if (!doc.exists()) { sb.appendLine("  (no existe)"); continue }
                        val data = doc.data ?: continue
                        for ((key, value) in data) {
                            val tipo = when (value) {
                                null -> "null"
                                is String -> "String"
                                is Number -> "Number($value)"
                                is Map<*, *> -> "Map{${(value as Map<*,*>).keys.joinToString(",")}}"
                                is List<*> -> "List(${value.size})"
                                else -> value::class.simpleName ?: "Unknown"
                            }
                            sb.appendLine("  $key : $tipo = $value")
                        }
                    }
                } catch (e: Exception) {
                    sb.appendLine("COLECCIÓN '$colName': no existe o error (${e.message})")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("ERROR explorando colecciones: ${e.message}")
        }
        return sb.toString()
    }
}
