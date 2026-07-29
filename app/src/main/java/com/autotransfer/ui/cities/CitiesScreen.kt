package com.autotransfer.ui.cities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autotransfer.data.CityEntity

@Composable
fun ClientsScreen(onBack: () -> Unit) {
    val viewModel: CitiesViewModel = viewModel()
    val cities by viewModel.cities.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()

    var clienteQuery by remember { mutableStateOf("") }
    var ciudadQuery by remember { mutableStateOf("") }
    var zonaQuery by remember { mutableStateOf("") }
    var editingCity by remember { mutableStateOf<CityEntity?>(null) }
    var searchVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.loadAll()
    }

    LaunchedEffect(searchVersion) {
        if (searchVersion > 0) {
            delay(300)
            viewModel.search(clienteQuery, ciudadQuery, zonaQuery)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Volver")
        }

        Text("Base de Datos de Clientes", fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium)

        Text("Ciudades por Cliente — 'Congreso*' y 'CVDL' se filtran automáticamente",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = clienteQuery,
            onValueChange = { clienteQuery = it; searchVersion++ },
            label = { Text("Buscar por Cliente") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        OutlinedTextField(
            value = ciudadQuery,
            onValueChange = { ciudadQuery = it; searchVersion++ },
            label = { Text("Buscar por Ciudad") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        OutlinedTextField(
            value = zonaQuery,
            onValueChange = { zonaQuery = it; searchVersion++ },
            label = { Text("Filtrar por Zona (Firebase)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("25,26") }
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text("${cities.size} registros",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)

        message?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(it, modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(cities, key = { it.cliente }) { city ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    onClick = { editingCity = city }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Cliente: ${city.cliente}", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Ciudad: ${city.ciudad}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    editingCity?.let { city ->
        CityEditDialog(
            city = city,
            onSave = { clienteOriginal, clienteNuevo, ciudad ->
                viewModel.update(clienteOriginal, clienteNuevo, ciudad)
                editingCity = null
            },
            onDismiss = { editingCity = null }
        )
    }
}
