package com.autotransfer.ui.holland

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import com.autotransfer.MainViewModel
import com.autotransfer.util.Fechas
import java.text.NumberFormat
import java.util.Locale

private const val TOP_N = 12

/**
 * Graficas del rango de fechas: total por cliente y total por producto.
 * Sin librerias externas, las barras se dibujan con Canvas.
 */
@Composable
fun HollandGraficosScreen(
    viewModel: MainViewModel,
    zona: String,
    onZonaChange: (String) -> Unit,
    onClienteClick: (String) -> Unit,
    onProductoClick: (String) -> Unit
) {
    val porClienteResumen by viewModel.topVentasPorCliente.collectAsState()
    val topProductos by viewModel.topProductos.collectAsState()
    val desde by viewModel.fechaDesde.collectAsState()
    val hasta by viewModel.fechaHasta.collectAsState()
    val isLoading by viewModel.isFirebaseLoading.collectAsState()

    val fmt = NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    // La clave es la zona: cambiar de zona recarga, y dentro del dia sale del
    // cache (sin red). refrescarGraficos ademas recalcula los totales al entrar.
    LaunchedEffect(zona) {
        viewModel.refrescarGraficos(Fechas.zonas(zona))
    }

    val porCliente = remember(porClienteResumen) {
        porClienteResumen.take(TOP_N).map { HollandBarra(it.cliente, it.total, fmt.format(it.total)) }
    }
    val porProducto = remember(topProductos) {
        topProductos.take(TOP_N).map { p ->
            HollandBarra(p.nombre, p.total, "${p.piezas} pzs")
        }
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

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SeccionGrafico(
                    titulo = "💰 Total por cliente",
                    subtitulo = if (porCliente.isNotEmpty()) "Toca una barra para ver sus transfers" else "",
                    vacio = "No hay ventas en este rango para la zona $zona.",
                    hayDatos = porCliente.isNotEmpty()
                ) {
                    HollandBarChart(
                        filas = porCliente,
                        onClick = { onClienteClick(it.etiqueta) }
                    )
                }

                SeccionGrafico(
                    titulo = "📦 Total por producto",
                    subtitulo = if (porProducto.isNotEmpty()) "Suma de los transfers del rango, top $TOP_N" else "",
                    vacio = "No hay productos en este rango.",
                    hayDatos = porProducto.isNotEmpty()
                ) {
                    HollandBarChart(
                        filas = porProducto,
                        colorBarra = MaterialTheme.colorScheme.tertiary,
                        onClick = { onProductoClick(it.etiqueta) }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SeccionGrafico(
    titulo: String,
    subtitulo: String,
    vacio: String,
    hayDatos: Boolean,
    contenido: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (subtitulo.isNotBlank()) {
            Text(
                subtitulo,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        if (hayDatos) {
            contenido()
        } else {
            Text(
                vacio,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
