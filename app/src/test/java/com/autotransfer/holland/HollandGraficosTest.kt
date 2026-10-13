package com.autotransfer.holland

import com.autotransfer.data.firebase.FirebaseVenta
import com.autotransfer.filtrarVentasPorProducto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HollandGraficosTest {

    private fun venta(
        id: String,
        productos: List<Map<String, Any?>>
    ) = FirebaseVenta(id = id, productos = productos, total = 100.0)

    private val prodA = mapOf("nombre" to "Vacuna X", "tipo" to "VACUNA", "total" to 40.0)
    private val prodB = mapOf("nombre" to "Antiparasitario", "tipo" to "FARMACO", "total" to 60.0)
    private val prodSinNombre = mapOf("tipo" to "SERVICIO", "total" to 30.0)

    @Test
    fun `filtra ventas por nombre ignorando mayusculas y espacios`() {
        val v1 = venta("v1", listOf(prodA, prodB))
        val v2 = venta("v2", listOf(prodB))
        val resultado = filtrarVentasPorProducto(listOf(v1, v2), "  vacuna x ")

        assertEquals(listOf("v1"), resultado.map { it.id })
    }

    @Test
    fun `usa el tipo cuando el nombre viene vacio`() {
        val v1 = venta("v1", listOf(prodSinNombre))
        val v2 = venta("v2", listOf(prodA))
        val resultado = filtrarVentasPorProducto(listOf(v1, v2), "servicio")

        assertEquals(listOf("v1"), resultado.map { it.id })
    }

    @Test
    fun `con nombre en blanco devuelve lista vacia`() {
        val ventas = listOf(venta("v1", listOf(prodA)))
        assertTrue(filtrarVentasPorProducto(ventas, "   ").isEmpty())
    }

    @Test
    fun `no regresa ventas que no contienen el producto`() {
        val v1 = venta("v1", listOf(prodA))
        val v2 = venta("v2", listOf(prodB))
        val resultado = filtrarVentasPorProducto(listOf(v1, v2), "Antiparasitario")

        assertEquals(listOf("v2"), resultado.map { it.id })
    }
}