package com.autotransfer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CityDao {
    @Query("SELECT * FROM ciudades WHERE cliente = :cliente")
    suspend fun get(cliente: String): CityEntity?

    @Query("SELECT * FROM ciudades ORDER BY cliente ASC")
    suspend fun getAll(): List<CityEntity>

    @Query("SELECT * FROM ciudades WHERE cliente LIKE '%' || :q || '%' ORDER BY cliente ASC")
    suspend fun searchByCliente(q: String): List<CityEntity>

    @Query("SELECT * FROM ciudades WHERE ciudad LIKE '%' || :q || '%' ORDER BY cliente ASC")
    suspend fun searchByCiudad(q: String): List<CityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(ciudad: CityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun bulkInsert(ciudades: List<CityEntity>)

    @Query("DELETE FROM ciudades WHERE cliente = :cliente")
    suspend fun delete(cliente: String)

    @Query("SELECT COUNT(*) FROM ciudades")
    suspend fun count(): Int
}
