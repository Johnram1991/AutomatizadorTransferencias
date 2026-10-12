package com.autotransfer.ui.holland

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autotransfer.FirebaseLoginState
import com.autotransfer.MainViewModel
import com.autotransfer.util.Fechas

/**
 * Raiz de la seccion App Holland.
 *
 * El menu no carga nada: cada pantalla pide sus datos al abrirse. El estado de
 * navegacion usa rememberSaveable, asi que sobrevive a rotar el telefono.
 * El boton fisico atras baja un nivel (ClienteDetalle -> Clientes -> Menu) y
 * desde el menu sale a la pantalla principal.
 */
@Composable
fun HollandScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val loginState by viewModel.firebaseLoginState.collectAsState()
    val isLoggedIn = loginState is FirebaseLoginState.LoggedIn

    var destino by rememberSaveable { mutableStateOf(HollandDestino.Menu) }
    var clienteNombre by rememberSaveable { mutableStateOf("") }
    var zona by rememberSaveable { mutableStateOf(viewModel.getSavedZonaFilter()) }

    val ultimaSync by viewModel.ultimaSync.collectAsState()
    val isFirebaseLoading by viewModel.isFirebaseLoading.collectAsState()
    val syncMensaje by viewModel.syncMensaje.collectAsState()

    // Al entrar al menu, que se vea cuando se descargo la zona actual.
    LaunchedEffect(zona) { viewModel.refrescarEstadoSync(Fechas.zonas(zona)) }

    BackHandler(enabled = isLoggedIn) {
        destino = when (destino) {
            HollandDestino.ClienteDetalle -> HollandDestino.Clientes
            HollandDestino.Menu -> {
                onBack()
                HollandDestino.Menu
            }
            else -> HollandDestino.Menu
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // El detalle de cliente trae su propio boton "volver al listado".
        if (destino != HollandDestino.ClienteDetalle) {
            Button(
                onClick = {
                    if (destino == HollandDestino.Menu) onBack() else destino = HollandDestino.Menu
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("← Volver")
            }
        }

        if (!isLoggedIn) {
            Text(
                "Sesión cerrada",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        syncMensaje?.let { mensaje ->
            Card(
                onClick = { viewModel.limpiarMensajeSync() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Text(
                    mensaje,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            when (destino) {
                HollandDestino.Menu -> HollandMenu(
                    zona = zona.ifBlank { "25" },
                    ultimaSync = ultimaSync,
                    isLoading = isFirebaseLoading,
                    onActualizar = { viewModel.actualizarDatosFirebase(Fechas.zonas(zona)) },
                    onClientes = { destino = HollandDestino.Clientes },
                    onVentas = { destino = HollandDestino.Ventas },
                    onResumen = { destino = HollandDestino.Resumen },
                    onGraficos = { destino = HollandDestino.Graficos }
                )

                HollandDestino.Clientes -> HollandClientesScreen(
                    viewModel = viewModel,
                    zona = zona,
                    onZonaChange = { zona = it },
                    onClienteClick = { cliente ->
                        clienteNombre = cliente.nombre
                        destino = HollandDestino.ClienteDetalle
                    }
                )

                HollandDestino.ClienteDetalle -> HollandClienteDetalleScreen(
                    viewModel = viewModel,
                    clienteNombre = clienteNombre,
                    onBack = { destino = HollandDestino.Clientes }
                )

                HollandDestino.Ventas -> HollandVentasScreen(
                    viewModel = viewModel,
                    zona = zona,
                    onZonaChange = { zona = it }
                )

                HollandDestino.Resumen -> HollandResumenScreen(
                    viewModel = viewModel,
                    zona = zona,
                    onZonaChange = { zona = it },
                    onClienteClick = { cliente ->
                        clienteNombre = cliente
                        destino = HollandDestino.ClienteDetalle
                    }
                )

                HollandDestino.Graficos -> HollandGraficosScreen(
                    viewModel = viewModel,
                    zona = zona,
                    onZonaChange = { zona = it },
                    onClienteClick = { cliente ->
                        clienteNombre = cliente
                        destino = HollandDestino.ClienteDetalle
                    }
                )
            }
        }
    }
}
