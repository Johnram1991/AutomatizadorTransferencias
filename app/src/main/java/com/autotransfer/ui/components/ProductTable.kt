package com.autotransfer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProductTable(
    productos: List<Map<String, Any?>>,
    totalVenta: Double,
    modifier: Modifier = Modifier
) {
    val fmt = NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text("Nombre", fontWeight = FontWeight.SemiBold, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, modifier = Modifier.weight(2f))
            Text("Tipo", fontWeight = FontWeight.SemiBold, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, modifier = Modifier.width(80.dp))
            Text("Vta", fontWeight = FontWeight.SemiBold, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, textAlign = TextAlign.End,
                modifier = Modifier.width(36.dp))
            Text("Bono", fontWeight = FontWeight.SemiBold, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, textAlign = TextAlign.End,
                modifier = Modifier.width(36.dp))
            Text("Costo", fontWeight = FontWeight.SemiBold, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, textAlign = TextAlign.End,
                modifier = Modifier.width(60.dp))
            Text("Total", fontWeight = FontWeight.SemiBold, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, textAlign = TextAlign.End,
                modifier = Modifier.width(60.dp))
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

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(nombre, fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(2f))
                Text(tipo, fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(80.dp))
                Text("$piezasV", fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
                Text("$piezasB", fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
                Text(fmt.format(pu), fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
                Text(fmt.format(total), fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 1.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(2f))
            Spacer(modifier = Modifier.width(80.dp))
            Spacer(modifier = Modifier.width(36.dp))
            Spacer(modifier = Modifier.width(36.dp))
            Text("Subtotal", fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
                modifier = Modifier.width(60.dp))
            Text(fmt.format(subtotal), fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
                modifier = Modifier.width(60.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(2f))
            Spacer(modifier = Modifier.width(80.dp))
            Spacer(modifier = Modifier.width(36.dp))
            Spacer(modifier = Modifier.width(36.dp))
            Text("Total venta", fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
                modifier = Modifier.width(60.dp))
            Text(fmt.format(totalVenta), fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
                modifier = Modifier.width(60.dp))
        }
    }
}
