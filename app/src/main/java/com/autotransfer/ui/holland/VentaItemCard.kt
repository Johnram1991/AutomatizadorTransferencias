package com.autotransfer.ui.holland

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autotransfer.data.firebase.FirebaseVenta
import com.autotransfer.ui.components.ProductTable

/**
 * Tarjeta de un transfer. [direccionPorNombre] se arma de la lista de clientes
 * de la zona (sin filtro de busqueda) para que la direccion no desaparezca
 * mientras el usuario escribe.
 */
@Composable
fun VentaItemCard(v: FirebaseVenta, direccionPorNombre: Map<String, String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (v.clienteNombre.isNotBlank()) {
                Text(
                    v.clienteNombre,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row {
                if (v.id.isNotBlank()) {
                    Text(
                        "${v.id}  ",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (v.fechaSubidoString.isNotBlank()) {
                    Text(v.fechaSubidoString, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (v.zonaVenta.isNotBlank()) {
                val dir = direccionPorNombre[v.clienteNombre.lowercase().trim()] ?: ""
                Text(
                    text = if (dir.isNotBlank()) "Zona: ${v.zonaVenta}  Dir: $dir" else "Zona: ${v.zonaVenta}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (v.observa.isNotBlank()) {
                Text(v.observa, style = MaterialTheme.typography.bodySmall)
            }
            if (v.productos.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                ProductTable(productos = v.productos, totalVenta = v.total)
            }
        }
    }
}
