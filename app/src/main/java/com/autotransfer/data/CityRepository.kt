package com.autotransfer.data

import android.content.Context

class CityRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).cityDao()

    companion object {
        private val forbiddenCities = listOf("congreso", "congaeso", "cvd1", "cvd")
        private fun isCityValid(ciudad: String): Boolean {
            val lower = ciudad.trim().lowercase()
            if (lower.isEmpty()) return false
            return forbiddenCities.none { lower.contains(it) }
        }

        fun filterCity(ciudad: String): String {
            val trimmed = ciudad.trim()
            return if (isCityValid(trimmed)) trimmed else ""
        }
    }

    suspend fun get(clientName: String): String? {
        val entity = dao.get(clientName.trim())
        return entity?.ciudad
    }

    suspend fun set(cliente: String, ciudad: String) {
        val key = cliente.trim()
        if (key.isEmpty()) return
        val filteredCity = filterCity(ciudad)
        if (filteredCity.isNotEmpty()) {
            dao.insertOrUpdate(CityEntity(
                cliente = key,
                ciudad = filteredCity,
                ultimaActualizacion = System.currentTimeMillis()
            ))
        }
    }

    suspend fun update(clienteOriginal: String, clienteNuevo: String, ciudad: String) {
        val key = clienteNuevo.trim()
        if (key.isEmpty()) return
        val orig = clienteOriginal.trim()
        if (orig != key) dao.delete(orig)
        val filteredCity = filterCity(ciudad)
        if (filteredCity.isNotEmpty()) {
            dao.insertOrUpdate(CityEntity(
                cliente = key,
                ciudad = filteredCity,
                ultimaActualizacion = System.currentTimeMillis()
            ))
        }
    }

    suspend fun updateFromOperaciones(operaciones: List<com.autotransfer.model.Operacion>) {
        for (op in operaciones) {
            val clienteName = op.cliente.trim()
            if (clienteName.isEmpty()) continue
            val ciudad = filterCity(op.ciudad)
            if (ciudad.isNotEmpty()) {
                val existing = dao.get(clienteName)
                if (existing == null || existing.ciudad.isEmpty()) {
                    dao.insertOrUpdate(CityEntity(
                        cliente = clienteName,
                        ciudad = ciudad,
                        ultimaActualizacion = System.currentTimeMillis()
                    ))
                }
            }
        }
    }

    suspend fun bulkSet(data: Map<String, String>) {
        val entities = data.mapNotNull { (cliente, ciudad) ->
            val filtered = filterCity(ciudad)
            if (cliente.trim().isNotEmpty() && filtered.isNotEmpty()) {
                CityEntity(
                    cliente = cliente.trim(),
                    ciudad = filtered,
                    ultimaActualizacion = System.currentTimeMillis()
                )
            } else null
        }
        if (entities.isNotEmpty()) dao.bulkInsert(entities)
    }

    suspend fun getAll(): List<CityEntity> = dao.getAll()

    suspend fun searchByCliente(q: String): List<CityEntity> = dao.searchByCliente(q)

    suspend fun searchByCiudad(q: String): List<CityEntity> = dao.searchByCiudad(q)

    suspend fun delete(cliente: String) {
        dao.delete(cliente.trim())
    }

    suspend fun count(): Int = dao.count()
}
