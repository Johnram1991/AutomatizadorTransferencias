package com.autotransfer.ui.holland

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autotransfer.util.Fechas

/**
 * Rango de fechas "Desde" / "Hasta" con un boton para volver al rango del mes.
 *
 * Antes habia dos botones con iconos casi identicos ("🔄" y "⟲") y nadie sabia
 * cual era cual. Ahora: "Restablecer" es texto explicito, y la recarga de Firebase
 * ya no vive aqui (esta en el boton "Actualizar Datos" del menu).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HollandDateFilter(
    fechaDesde: String,
    fechaHasta: String,
    onDesde: (String) -> Unit,
    onHasta: (String) -> Unit,
    onRestablecer: () -> Unit,
    modifier: Modifier = Modifier,
    onRefrescar: (() -> Unit)? = null
) {
    var showDesdePicker by remember { mutableStateOf(false) }
    var showHastaPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { showDesdePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (fechaDesde.isBlank()) "📅 Desde" else fechaDesde,
                    fontSize = 11.sp
                )
            }
            OutlinedButton(
                onClick = { showHastaPicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (fechaHasta.isBlank()) "📅 Hasta" else fechaHasta,
                    fontSize = 11.sp
                )
            }
            // recarga manual de Firebase, con texto para que no se confunda
            if (onRefrescar != null) {
                OutlinedButton(onClick = onRefrescar, modifier = Modifier.height(40.dp)) {
                    Text("Actualizar", fontSize = 10.sp)
                }
            }
        }
        // Segunda fila: una sola accion, dicha con palabras.
        OutlinedButton(
            onClick = onRestablecer,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) {
            Text("↺  Restablecer al mes (${Fechas.primerDiaDelMes()} → ${Fechas.hoy()})", fontSize = 11.sp)
        }
    }

    if (showDesdePicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDesdePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDesde(Fechas.deMillis(it)) }
                    showDesdePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDesdePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showHastaPicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showHastaPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onHasta(Fechas.deMillis(it)) }
                    showHastaPicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showHastaPicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
