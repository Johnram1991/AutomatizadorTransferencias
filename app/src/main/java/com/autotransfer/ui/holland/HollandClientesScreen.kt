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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.autotransfer.MainViewModel
import com.autotransfer.data.firebase.FirebaseCliente

/**
 * Listado de clientes de una zona.
 *
 * Orden de la pantalla: filtro de zona (con boton de refresco) -> busqueda
 * difusa (con boton de orden) -> listado con Nombre, Zona y Direccion.
 */
@Composable
fun HollandClientesScreen(
    viewModel: MainViewModel,
    zona: String,
    onZonaChange: (String) -> Unit,
    onClienteClick: (FirebaseCliente) -> Unit
) {
    val clientes by viewModel.firebaseClientes.collectAsState()
    val cargados by viewModel.clientesCargados.collectAsState()
    val isLoading by viewModel.isFirebaseLoading.collectAsState()
    val asc by viewModel.isClientesAscending.collectAsState()
    val totalZona by remember { derivedStateOf { viewModel.firebaseClientesZona.value.size } }

    var busqueda by rememberSaveable { mutableStateOf("") }
    var busquedaCiudad by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (!cargados) viewModel.cargarClientes(zona)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = zona,
                onValueChange = onZonaChange,
                label = { Text("Zona") },
                placeholder = { Text("25") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { viewModel.actualizarDatosFirebase(com.autotransfer.util.Fechas.zonas(zona)) },
                modifier = Modifier.height(48.dp)
            ) { Text("Actualizar", fontSize = 10.sp) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = busqueda,
                onValueChange = {
                    busqueda = it
                    viewModel.setClientesQuery(it)
                },
                label = { Text("Buscar cliente") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { viewModel.toggleClientesOrder() },
                enabled = clientes.isNotEmpty(),
                modifier = Modifier.height(48.dp)
            ) {
                Text(if (asc) "↑ A-Z" else "↓ Z-A", fontSize = 12.sp)
            }
        }

        OutlinedTextField(
            value = busquedaCiudad,
            onValueChange = {
                busquedaCiudad = it
                viewModel.setClientesCiudadQuery(it)
            },
            label = { Text("Buscar por Ciudad") },
            placeholder = { Text("Av. Madero, Morelia…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            buildString {
                append("${clientes.size} clientes")
                if (busqueda.isNotBlank() || busquedaCiudad.isNotBlank()) {
                    append(" de $totalZona")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            clientes.isEmpty() -> Text(
                text = when {
                    busqueda.isNotBlank() && busquedaCiudad.isNotBlank() ->
                        "Ningún cliente cumple \"$busqueda\" en \"$busquedaCiudad\"."
                    busquedaCiudad.isNotBlank() ->
                        "Ningún cliente tiene \"$busquedaCiudad\" en su dirección."
                    busqueda.isNotBlank() ->
                        "Ningún cliente coincide con \"$busqueda\"."
                    else -> "No hay clientes en la zona $zona."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(clientes, key = { it.id }) { c ->
                    ClienteRow(c) { onClienteClick(c) }
                }
            }
        }
    }
}

@Composable
private fun ClienteRow(c: FirebaseCliente, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(c.nombre, fontWeight = FontWeight.Bold)
            if (c.zona.isNotBlank()) {
                Text(
                    "Zona: ${c.zona}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (c.direccion.isNotBlank()) {
                Text("Dirección: ${c.direccion}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
