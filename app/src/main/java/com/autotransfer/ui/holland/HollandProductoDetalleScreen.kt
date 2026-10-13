package com.autotransfer.ui.holland

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autotransfer.MainViewModel
import java.text.NumberFormat
import java.util.Locale

/**
 * Transfers del rango global que contienen el producto que se toco en Graficos.
 * Usa la zona ya descargada (mismo camino que Ventas): no pega a Firebase.
 */
@Composable
fun HollandProductoDetalleScreen(
    viewModel: MainViewModel,
    producto: String,
    onBack: () -> Unit
) {
    val ventas by viewModel.ventasProducto.collectAsState()
    val clientes by viewModel.firebaseClientesZona.collectAsState()

    val fmt = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val direccionPorNombre = remember(clientes) {
        clientes.associate { it.nombre.lowercase().trim() to it.direccion }
    }

    LaunchedEffect(producto) {
        viewModel.verVentasDeProducto(producto)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Volver a Gráficos")
        }

        Text(
            producto,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        val total = ventas.sumOf { it.total }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Total comprado: ${fmt.format(total)}",
                    fontWeight = FontWeight.SemiBold
                )
                Text("${ventas.size} transfers en el rango", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (ventas.isNotEmpty()) {
            OutlinedButton(
                onClick = { viewModel.verVentasDeProducto(producto) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refrescar listado")
            }
        }

        if (ventas.isEmpty()) {
            Text(
                "Este producto no aparece en ningún transfer del rango.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(ventas, key = { it.id }) { v ->
                    VentaItemCard(v, direccionPorNombre)
                }
            }
        }
    }
}