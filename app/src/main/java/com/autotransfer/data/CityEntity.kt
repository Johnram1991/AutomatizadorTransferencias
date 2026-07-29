package com.autotransfer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ciudades")
data class CityEntity(
    @PrimaryKey val cliente: String,
    val ciudad: String,
    val ultimaActualizacion: Long = System.currentTimeMillis()
)
