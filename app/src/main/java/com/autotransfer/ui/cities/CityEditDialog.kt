package com.autotransfer.ui.cities

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autotransfer.data.CityEntity

@Composable
fun CityEditDialog(
    city: CityEntity,
    onSave: (clienteOriginal: String, clienteNuevo: String, ciudad: String) -> Unit,
    onDismiss: () -> Unit
) {
    var cliente by remember(city) { mutableStateOf(city.cliente) }
    var ciudad by remember(city) { mutableStateOf(city.ciudad) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Cliente") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = cliente,
                    onValueChange = { cliente = it },
                    label = { Text("Nombre del Cliente") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ciudad,
                    onValueChange = { ciudad = it },
                    label = { Text("Ciudad / Congreso") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(city.cliente, cliente, ciudad) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
