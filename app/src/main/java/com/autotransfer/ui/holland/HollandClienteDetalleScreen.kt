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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autotransfer.MainViewModel
import java.text.NumberFormat
import java.util.Locale

/**
 * Detalle de un cliente: rango de fechas (por defecto del dia 1 del mes en curso
 * a hoy), boton de refresco, total comprado del rango, orden asc/desc y el
 * listado de sus transfers.
 */
@Composable
fun HollandClienteDetalleScreen(
    viewModel: MainViewModel,
    clienteNombre: String,
    onBack: () -> Unit
) {
    val ventas by viewModel.ventasClienteFiltradas.collectAsState()
    val desde by viewModel.fechaClienteDesde.collectAsState()
    val hasta by viewModel.fechaClienteHasta.collectAsState()
    val isLoading by viewModel.isFirebaseLoading.collectAsState()
    val asc by viewModel.isVentasClienteAsc.collectAsState()
    val clientes by viewModel.firebaseClientesZona.collectAsState()

    val fmt = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val direccionPorNombre = remember(clientes) {
        clientes.associate { it.nombre.lowercase().trim() to it.direccion }
    }

    LaunchedEffect(clienteNombre) {
        viewModel.cargarVentasDeCliente(clienteNombre)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Volver al listado")
        }

        Text(
            clienteNombre,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        HollandDateFilter(
            fechaDesde = desde,
            fechaHasta = hasta,
            onDesde = { viewModel.setFechasCliente(it, hasta) },
            onHasta = { viewModel.setFechasCliente(desde, it) },
            onRestablecer = { viewModel.restablecerFechasCliente() },
            onRefrescar = { viewModel.refrescarVentasDeCliente(clienteNombre) }
        )

        val total = ventas.sumOf { it.total }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(clienteNombre, fontWeight = FontWeight.Bold)
                Text(
                    "Total comprado: ${fmt.format(total)}",
                    fontWeight = FontWeight.SemiBold
                )
                Text("${ventas.size} ventas en el rango", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (ventas.isNotEmpty()) {
            OutlinedButton(
                onClick = { viewModel.toggleVentasClienteOrder() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (asc) "↑ Ascendente" else "↓ Descendente")
            }
        }

        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            ventas.isEmpty() -> Text(
                "Este cliente no tiene ventas en el rango elegido.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(ventas, key = { it.id }) { v ->
                    VentaItemCard(v, direccionPorNombre)
                }
            }
        }
    }
}
