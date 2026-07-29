package com.autotransfer.data.firebase

data class FirebaseCliente(
    val id: String = "",
    val nombre: String = "",
    val empresa: String = "",
    val direccion: String = "",
    val rfc: String = "",
    val telefono: String = "",
    val correo: String = "",
    val zona: String = ""
) {
    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): FirebaseCliente {
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
            return FirebaseCliente(
                id = id,
                nombre = map["nombre"]?.toString() ?: "",
                empresa = map["empresa"]?.toString() ?: "",
                direccion = map["direccion"]?.toString() ?: "",
                rfc = map["rfc"]?.toString() ?: "",
                telefono = map["telefono"]?.toString() ?: "",
                correo = map["correo"]?.toString() ?: "",
                zona = zonaStr
            )
        }
    }
}
