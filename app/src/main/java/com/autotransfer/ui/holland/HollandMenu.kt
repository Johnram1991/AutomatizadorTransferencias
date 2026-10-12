package com.autotransfer.ui.holland

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Portada de App Holland: los 4 botones de seccion y, abajo, "Actualizar Datos".
 *
 * El menu no dispara ninguna llamada a Firebase al abrirse: cada pantalla usa el
 * cache del dia. El unico que fuerza una descarga real es este boton.
 */
@Composable
fun HollandMenu(
    zona: String,
    ultimaSync: String,
    isLoading: Boolean,
    onActualizar: () -> Unit,
    onClientes: () -> Unit,
    onVentas: () -> Unit,
    onResumen: () -> Unit,
    onGraficos: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "App Holland",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Elige una sección",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        HollandMenuButton("👥  Clientes", "Buscar clientes por zona y ver sus ventas", onClientes)
        HollandMenuButton("🧾  Ventas", "Transfers de la zona en un rango de fechas", onVentas)
        HollandMenuButton("📊  Resumen", "Total comprado por cliente", onResumen)
        HollandMenuButton("📈  Gráficos", "Barras por cliente y por producto", onGraficos)

        Spacer(Modifier.height(8.dp))
        Card(
            onClick = onActualizar,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (isLoading) "⏳  Descargando…" else "🔄  Actualizar Datos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Zona $zona  •  Última descarga: $ultimaSync",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Los datos se guardan en el teléfono, así que no se descargan " +
                        "otra vez el mismo día. Usa esto solo si esperas datos nuevos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HollandMenuButton(titulo: String, subtitulo: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitulo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
