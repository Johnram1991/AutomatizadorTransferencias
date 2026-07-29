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
    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): FirebaseVenta {
            return FirebaseVenta(
                id = id,
                clienteNombre = map["clienteNombre"]?.toString() ?: "",
                ventaT = map["VentaT"]?.toString() ?: "",
                observa = map["observaciones"]?.toString() ?: "",
                promos = map["promociones"]?.toString() ?: "",
                zonaVenta = map["zona"]?.toString() ?: "",
                fechaSubidoString = map["fecha"]?.toString() ?: "",
                total = (map["total"] as? Number)?.toDouble() ?: 0.0,
                productos = (map["productos"] as? List<Map<String, Any?>>) ?: emptyList()
            )
        }
    }
}
