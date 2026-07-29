package com.autotransfer.data

import android.content.Context
import com.autotransfer.model.Operacion
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CityDatabase(context: Context) {

    private val file = File(context.filesDir, "city_cache.json")
    private val data = mutableMapOf<String, Pair<String, String>>()

    init {
        load()
    }

    fun get(noTransfer: String): Pair<String, String>? {
        return data[noTransfer.trim()]
    }

    fun set(noTransfer: String, distribuidor: String, ciudad: String) {
        val key = noTransfer.trim()
        if (key.isNotEmpty()) {
            data[key] = Pair(distribuidor, ciudad)
        }
    }

    fun updateFromOperaciones(operaciones: List<Operacion>) {
        for (op in operaciones) {
            val key = op.noTransfer.trim()
            if (key.isNotEmpty() && (op.distribuidor.isNotEmpty() || op.ciudad.isNotEmpty())) {
                data[key] = Pair(op.distribuidor, op.ciudad)
            }
        }
        save()
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val json = JSONArray(file.readText())
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val key = obj.getString("noTransfer")
                val dist = obj.optString("distribuidor", "")
                val city = obj.optString("ciudad", "")
                data[key] = Pair(dist, city)
            }
        } catch (_: Exception) {}
    }

    fun save() {
        try {
            val arr = JSONArray()
            for ((key, pair) in data) {
                val obj = JSONObject()
                obj.put("noTransfer", key)
                obj.put("distribuidor", pair.first)
                obj.put("ciudad", pair.second)
                arr.put(obj)
            }
            file.writeText(arr.toString(2))
        } catch (_: Exception) {}
    }
}
