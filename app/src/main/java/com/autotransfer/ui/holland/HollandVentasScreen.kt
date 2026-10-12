package com.autotransfer.ui.holland

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.autotransfer.util.Fechas
import java.text.NumberFormat
import java.util.Locale

/**
 * Todos los transfers de la zona dentro del rango de fechas global.
 * El boton de refresco vuelve a pedir clientes y ventas a Firebase.
 */
@Composable
fun HollandVentasScreen(
    viewModel: MainViewModel,
    zona: String,
    onZonaChange: (String) -> Unit
) {
    val ventas by viewModel.ventasFiltradas.collectAsState()
    val desde by viewModel.fechaDesde.collectAsState()
    val hasta by viewModel.fechaHasta.collectAsState()
    val isLoading by viewModel.isFirebaseLoading.collectAsState()
    val asc by viewModel.isAscending.collectAsState()
    val clientes by viewModel.firebaseClientesZona.collectAsState()

    val fmt = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val direccionPorNombre = remember(clientes) {
        clientes.associate { it.nombre.lowercase().trim() to it.direccion }
    }

    // La clave es la zona: cambiar de zona recarga, y dentro del dia sale del
    // cache (sin red). La descarga forzada vive en el boton "Actualizar Datos".
    LaunchedEffect(zona) {
        viewModel.loadFirebaseData(Fechas.zonas(zona))
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = zona,
            onValueChange = onZonaChange,
            label = { Text("Zona") },
            placeholder = { Text("25") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        HollandDateFilter(
            fechaDesde = desde,
            fechaHasta = hasta,
            onDesde = { viewModel.setFechasYGuardar(it, hasta) },
            onHasta = { viewModel.setFechasYGuardar(desde, it) },
            onRestablecer = { viewModel.restablecerFechas() },
            onRefrescar = { viewModel.loadFirebaseData(Fechas.zonas(zona)) }
        )

        if (ventas.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Total comprado: ${fmt.format(ventas.sumOf { it.total })}",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("${ventas.size} ventas", style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(
                onClick = { viewModel.toggleVentasOrder() },
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
                "No hay ventas en este rango para la zona $zona.",
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
