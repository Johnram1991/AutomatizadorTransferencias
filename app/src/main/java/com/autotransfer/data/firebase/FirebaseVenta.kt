package com.autotransfer.data.firebase

data class FirebaseVenta(
    val id: String = "",
    val clienteNombre: String = "",
    val ventaT: String = "",
    val observa: String = "",
    val promos: String = "",
    val zonaVenta: String = "",
    val fechaSubidoString: String = "",
    val total: Double = 0.0,
    val productos: List<Map<String, Any?>> = emptyList()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "clienteNombre" to clienteNombre,
        "VentaT" to ventaT,
        "observaciones" to observa,
        "promociones" to promos,
        "zona" to zonaVenta,
        "fecha" to fechaSubidoString,
        "total" to total,
        "productos" to productos
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): FirebaseVenta {
            val zonaRaw = map["zonaClave"] ?: map["zona"]
            val zonaStr = when (zonaRaw) {
                is Map<*, *> -> {
                    val clave = zonaRaw["clave"]
                    when (clave) {
                        is String -> clave
                        is Number -> clave.toString()
                        else -> ""
                    }
                }
                is String -> zonaRaw
                is Number -> zonaRaw.toString()
                else -> ""
            }
            return FirebaseVenta(
                id = id,
                clienteNombre = map["clienteNombre"]?.toString() ?: "",
                ventaT = map["VentaT"]?.toString() ?: "",
                observa = map["observaciones"]?.toString() ?: "",
                promos = map["promociones"]?.toString() ?: "",
                zonaVenta = zonaStr,
                fechaSubidoString = map["fecha"]?.toString() ?: "",
                total = (map["total"] as? Number)?.toDouble() ?: 0.0,
                productos = (map["productos"] as? List<Map<String, Any?>>) ?: emptyList()
            )
        }
    }
}
