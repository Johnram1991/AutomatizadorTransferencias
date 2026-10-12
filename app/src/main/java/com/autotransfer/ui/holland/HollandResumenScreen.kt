package com.autotransfer.ui.holland

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autotransfer.MainViewModel
import com.autotransfer.util.Fechas
import java.text.NumberFormat
import java.util.Locale

/**
 * Total comprado por cliente dentro del rango de fechas global.
 * Tocar un cliente abre su detalle (misma pantalla que desde el listado de Clientes).
 */
@Composable
fun HollandResumenScreen(
    viewModel: MainViewModel,
    zona: String,
    onZonaChange: (String) -> Unit,
    onClienteClick: (String) -> Unit
) {
    val resumen by viewModel.resumenClientes.collectAsState()
    val resumenAsc by viewModel.resumenAsc.collectAsState()
    val desde by viewModel.fechaDesde.collectAsState()
    val hasta by viewModel.fechaHasta.collectAsState()
    val isLoading by viewModel.isFirebaseLoading.collectAsState()

    val fmt = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    var clienteFiltro by rememberSaveable { mutableStateOf("") }

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = clienteFiltro,
                onValueChange = {
                    clienteFiltro = it
                    viewModel.setResumenClienteFiltro(it)
                },
                label = { Text("Filtrar cliente") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    clienteFiltro = ""
                    viewModel.limpiarResumenClienteFiltro()
                },
                modifier = Modifier.height(48.dp)
            ) { Text("⟲", fontSize = 12.sp) }
        }

        if (resumen.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Total general: ${fmt.format(resumen.sumOf { it.total })}",
                        fontWeight = FontWeight.Bold
                    )
                    Text("${resumen.size} clientes en el rango",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(
                onClick = { viewModel.toggleResumenOrder() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (resumenAsc) "↑ Menor total" else "↓ Mayor total")
            }
        }

        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            resumen.isEmpty() -> Text(
                "No hay ventas en este rango para la zona $zona.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(resumen, key = { it.cliente }) { r ->
                    Card(
                        onClick = { onClienteClick(r.cliente) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(r.cliente, fontWeight = FontWeight.Bold)
                            Text("${r.numVentas} ventas",
                                style = MaterialTheme.typography.bodySmall)
                            Text(
                                fmt.format(r.total),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
