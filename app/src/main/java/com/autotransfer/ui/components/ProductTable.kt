package com.autotransfer.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

/**
 * Tabla de productos con scroll horizontal.
 *
 * Antes las columnas fijas sumaban 272.dp y el nombre llevaba `weight(2f)`, que en
 * un celular angosto se queda en cero: por eso los nombres salian partidos cada
 * tres letras ("Mic / Opl / Ex"). Ahora cada columna tiene un ancho propio y la
 * tabla entera se desplaza con el dedo, asi que el nombre nunca se comprime.
 */
private object Col {
    val nombre: Dp = 170.dp
    val tipo: Dp = 80.dp
    val vta: Dp = 40.dp
    val bono: Dp = 40.dp
    val costo: Dp = 72.dp
    val total: Dp = 72.dp
    val todo: Dp = nombre + tipo + vta + bono + costo + total
}

@Composable
fun ProductTable(
    productos: List<Map<String, Any?>>,
    totalVenta: Double,
    modifier: Modifier = Modifier
) {
    val fmt = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    val scroll = rememberScrollState()

    @Composable
    fun encabezado(titulo: String, ancho: Dp, alDerecha: Boolean = false) {
        Text(
            titulo,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = if (alDerecha) TextAlign.End else TextAlign.Start,
            modifier = Modifier.width(ancho)
        )
    }

    Column(
        modifier = modifier
            .width(Col.todo)
            .horizontalScroll(scroll)
    ) {
        Row(modifier = Modifier.width(Col.todo).padding(bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
            encabezado("Nombre", Col.nombre)
            encabezado("Tipo", Col.tipo)
            encabezado("Vta", Col.vta, true)
            encabezado("Bono", Col.bono, true)
            encabezado("Costo", Col.costo, true)
            encabezado("Total", Col.total, true)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 1.dp))

        var subtotal = 0.0
        productos.forEach { p ->
            val nombre = p["nombre"]?.toString() ?: ""
            val tipo = p["tipo"]?.toString() ?: ""
            val piezasV = (p["piezasVenta"] as? Number)?.toInt() ?: 0
            val piezasB = (p["piezasBono"] as? Number)?.toInt() ?: 0
            val pu = (p["precioUnitario"] as? Number)?.toDouble() ?: 0.0
            val total = (p["total"] as? Number)?.toDouble() ?: 0.0
            subtotal += total

            Row(modifier = Modifier.width(Col.todo).padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                // maxLines + Ellipsis: parte por palabras, nunca a media palabra
                Text(
                    nombre, fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, softWrap = true,
                    modifier = Modifier.width(Col.nombre)
                )
                Text(tipo, fontSize = 8.sp, fontFamily = FontFamily.Monospace, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.width(Col.tipo))
                Text("$piezasV", fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End, modifier = Modifier.width(Col.vta))
                Text("$piezasB", fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End, modifier = Modifier.width(Col.bono))
                Text(fmt.format(pu), fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(Col.costo))
                Text(fmt.format(total), fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(Col.total))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 1.dp))

        Row(modifier = Modifier.width(Col.todo).padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(Col.nombre))
            Spacer(Modifier.width(Col.tipo))
            Spacer(Modifier.width(Col.vta))
            Spacer(Modifier.width(Col.bono))
            Text("Subtotal", fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
                modifier = Modifier.width(Col.costo))
            Text(fmt.format(subtotal), fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
                modifier = Modifier.width(Col.total))
        }
        Row(modifier = Modifier.width(Col.todo).padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(Col.nombre))
            Spacer(Modifier.width(Col.tipo))
            Spacer(Modifier.width(Col.vta))
            Spacer(Modifier.width(Col.bono))
            Text("Total venta", fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
                modifier = Modifier.width(Col.costo))
            Text(fmt.format(totalVenta), fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
                modifier = Modifier.width(Col.total))
        }
    }
}
