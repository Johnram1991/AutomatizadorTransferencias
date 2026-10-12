package com.autotransfer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autotransfer.ui.holland.HollandScreen
import com.autotransfer.ui.login.LoginDialog
import com.autotransfer.ui.splash.OsirisSplash
import com.autotransfer.ui.theme.SithTheme
import com.autotransfer.util.Fechas

private const val XLSX_MIME =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.BLACK
        setContent {
            val viewModel: MainViewModel = viewModel()
            AutoTransferApp(viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoTransferApp(viewModel: MainViewModel) {
    var splash by rememberSaveable { mutableStateOf(true) }

    if (splash) {
        OsirisSplash(
            title = "Auto Transfer",
            subtitle = "By John Ram",
            onTimeout = { splash = false }
        )
        return
    }

    val loginState by viewModel.firebaseLoginState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val rootFolderUri by viewModel.rootFolderUri.collectAsState()
    val lastResult by viewModel.lastResult.collectAsState()
    var showConfig by remember { mutableStateOf(false) }
    var showCities by remember { mutableStateOf(false) }
    var showHolland by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }

    val isLoggedIn = loginState is FirebaseLoginState.LoggedIn

    BackHandler(enabled = showConfig || showCities || showHolland) {
        when {
            showConfig -> showConfig = false
            showCities -> showCities = false
            showHolland -> showHolland = false
        }
    }

    SithTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(when { showConfig -> "Configuración"; showCities -> "Clientes"; showHolland -> "App Holland"; else -> "Auto Transfer" }) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                if (showConfig) {
                    ConfigScreen(
                        viewModel = viewModel,
                        onBack = { showConfig = false }
                    )
                } else if (showCities) {
                    com.autotransfer.ui.cities.ClientsScreen(
                        onBack = { showCities = false }
                    )
                } else if (showHolland) {
                    if (isLoggedIn) {
                        HollandScreen(
                            viewModel = viewModel,
                            onBack = { showHolland = false }
                        )
                    } else {
                        Text("No has iniciado sesión en App Holland",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            showLoginDialog = true
                            showHolland = false
                        }) {
                            Text("Iniciar sesión")
                        }
                    }
                } else {
                    MainScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        rootFolderUri = rootFolderUri,
                        lastResult = lastResult,
                        onOpenConfig = { showConfig = true },
                        onOpenCities = { showCities = true },
                        onOpenHolland = {
                            if (isLoggedIn) {
                                showHolland = true
                            } else {
                                showLoginDialog = true
                            }
                        },
                        onLogout = { viewModel.firebaseLogout() },
                        isLoggedIn = isLoggedIn
            )
        }
    }
}

        LaunchedEffect(loginState) {
            if (loginState is FirebaseLoginState.LoggedIn) {
                showLoginDialog = false
                showHolland = true
            }
        }

        if (showLoginDialog) {
            val isLoading = loginState is FirebaseLoginState.Loading
            val errorMsg = (loginState as? FirebaseLoginState.Error)?.message
            LoginDialog(
                onLogin = { email, password ->
                    viewModel.firebaseLogin(email, password)
                },
                onDismiss = {
                    showLoginDialog = false
                },
                isLoading = isLoading,
                errorMessage = errorMsg
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CityReviewDialog(
    items: List<CityReviewItem>,
    onAccept: (String) -> Unit,
    onUpdate: (String, String) -> Unit,
    onAcceptAll: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 600.dp)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Revisar ciudades pendientes",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                Text("${items.size} ciudades por confirmar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { "${it.cliente}_${it.ciudadPropuesta}" }) { item ->
                        CityReviewRow(
                            item = item,
                            onAccept = { onAccept(item.cliente) },
                            onUpdate = { ciudad -> onUpdate(item.cliente, ciudad) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAcceptAll,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("✓ Aceptar todas")
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}

@Composable
private fun CityReviewRow(
    item: CityReviewItem,
    onAccept: () -> Unit,
    onUpdate: (String) -> Unit
) {
    var editText by remember(item.cliente) { mutableStateOf(item.ciudadPropuesta) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Cliente: ${item.cliente}", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall)
            if (item.direccion.isNotBlank()) Text("Dirección: ${item.direccion}",
                style = MaterialTheme.typography.bodySmall)
            item.repoOriginal?.let { repo ->
                if (repo != item.ciudadPropuesta) {
                    Text("Anterior: $repo",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Ciudad") },
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = {
                        if (editText.isNotBlank()) {
                            if (editText == item.ciudadPropuesta) onAccept()
                            else onUpdate(editText.trim())
                        }
                    }
                ) {
                    Text("✓", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    viewModel: MainViewModel,
    uiState: UiState,
    rootFolderUri: android.net.Uri?,
    lastResult: UiState.Result?,
    onOpenConfig: () -> Unit,
    onOpenCities: () -> Unit,
    onOpenHolland: () -> Unit,
    onLogout: () -> Unit,
    isLoggedIn: Boolean
) {
    val context = LocalContext.current

    var folderError by remember { mutableStateOf<String?>(null) }
    var showFirebaseProcess by remember { mutableStateOf(false) }
    val savedFbDesde = remember { viewModel.getSavedFechaDesde() }
    val savedFbHasta = remember { viewModel.getSavedFechaHasta() }
    var fbDesde by remember { mutableStateOf(savedFbDesde) }
    var fbHasta by remember { mutableStateOf(savedFbHasta) }
    var fbZonas by remember { mutableStateOf("") }
    val excelUriState by viewModel.excelUri.collectAsState()
    val excelFileNameState by viewModel.excelFileName.collectAsState()
    val pendingOps by viewModel.pendingOperaciones.collectAsState()
    val excelAccesoTemporal by viewModel.excelAccesoTemporal.collectAsState()
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
        uri?.let {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flags)
                folderError = null
                viewModel.setRootFolder(it)
            } catch (e: SecurityException) {
                try {
                    context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    folderError = "Sin permiso de escritura. Solo lectura."
                    viewModel.setRootFolder(it)
                } catch (e2: SecurityException) {
                    folderError = "No se pudieron obtener permisos de la carpeta"
                }
            } catch (e: Exception) {
                folderError = "Error al seleccionar carpeta: ${e.message}"
            }
        }
    }

    val excelPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
        uri?.let {
            val fileName = result.data?.data?.let { dataUri ->
                try {
                    val cursor = context.contentResolver.query(dataUri, null, null, null, null)
                    cursor?.use { c ->
                        val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0 && c.moveToFirst()) c.getString(nameIdx) else null
                    }
                } catch (_: Exception) { null }
            }
            viewModel.setExcelUri(it, fileName)
            folderError = null
        }
    }

    val crearCopiaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
        uri?.let { viewModel.guardarCopiaEn(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var mostrarOpcionesExcel by remember { mutableStateOf(false) }

        Button(
            onClick = { mostrarOpcionesExcel = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Seleccionar archivo Formato Transfer.xlsx")
        }

        if (mostrarOpcionesExcel) {
            AlertDialog(
                onDismissRequest = { mostrarOpcionesExcel = false },
                title = { Text("¿Dónde está tu Excel?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                mostrarOpcionesExcel = false
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = XLSX_MIME
                                    addFlags(
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    )
                                }
                                excelPickerLauncher.launch(
                                    Intent.createChooser(intent, "Seleccionar archivo Excel")
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Drive / Archivos del sistema") }

                        OutlinedButton(
                            onClick = {
                                mostrarOpcionesExcel = false
                                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = XLSX_MIME
                                    addFlags(
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    )
                                }
                                excelPickerLauncher.launch(
                                    Intent.createChooser(intent, "Seleccionar archivo Excel")
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Mis archivos (Samsung) u otras apps") }

                        Text(
                            "Si tu Excel está en OneDrive o Google Drive, usa " +
                                "\"Drive / Archivos del sistema\": con \"Mis archivos\" " +
                                "esos archivos solo se pueden leer. \"Drive / Archivos\" " +
                                "guarda el acceso aunque cierres la app; \"Mis archivos\" " +
                                "solo dura mientras la app esté abierta.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { mostrarOpcionesExcel = false }) { Text("Cancelar") }
                }
            )
        }

        if (excelFileNameState != null) {
            Text(
                "${excelFileNameState} ✓",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
            if (excelAccesoTemporal) {
                Text(
                    "Elegido con Mis Archivos: el acceso dura hasta que cierres la app. " +
                        "Si la app se reinicia, vuelve a seleccionarlo.",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else if (excelUriState != null) {
            Text(
                "Archivo Excel seleccionado ✓",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                folderPickerLauncher.launch(Intent.createChooser(intent, "Seleccionar carpeta"))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Seleccionar carpeta Automatización")
        }

        if (rootFolderUri != null) {
            Text(
                "Carpeta seleccionada ✓",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        folderError?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(it, modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        val hasFolder = rootFolderUri != null || excelUriState != null

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.scanAndProcess() },
                modifier = Modifier.weight(1f),
                enabled = hasFolder && uiState !is UiState.Scanning && uiState !is UiState.Processing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Procesar", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onOpenCities,
                modifier = Modifier.weight(1f),
                enabled = hasFolder,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Clientes")
            }

            OutlinedButton(
                onClick = onOpenConfig,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("⚙")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpenHolland,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("App Holland", fontWeight = FontWeight.Bold)
            }

            if (isLoggedIn) {
                OutlinedButton(
                    onClick = onLogout,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Salir")
                }
            }
        }

        if (isLoggedIn) {
            OutlinedButton(
                onClick = { showFirebaseProcess = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = excelUriState != null && uiState !is UiState.Scanning && uiState !is UiState.Processing
            ) {
                Text("Procesar desde Firebase", fontWeight = FontWeight.Bold)
            }
        }

        if (showFirebaseProcess) {
            var fbShowDesde by remember { mutableStateOf(false) }
            var fbShowHasta by remember { mutableStateOf(false) }
            if (fbZonas.isBlank()) fbZonas = viewModel.getSavedZonaFilter()

            AlertDialog(
                onDismissRequest = { showFirebaseProcess = false },
                title = { Text("Procesar desde Firebase") },
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { fbShowDesde = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (fbDesde.isBlank()) "Desde" else fbDesde)
                            }
                            OutlinedButton(
                                onClick = { fbShowHasta = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (fbHasta.isBlank()) "Hasta" else fbHasta)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = fbZonas,
                            onValueChange = { fbZonas = it },
                            label = { Text("Zonas") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("25") }
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.saveFechaDesde(fbDesde)
                        viewModel.saveFechaHasta(fbHasta)
                        viewModel.procesarDesdeFirebase(fbDesde, fbHasta, Fechas.zonas(fbZonas))
                        showFirebaseProcess = false
                    }) { Text("Ejecutar") }
                },
                dismissButton = {
                    TextButton(onClick = { showFirebaseProcess = false }) { Text("Cancelar") }
                }
            )

            if (fbShowDesde) {
                val st = androidx.compose.material3.rememberDatePickerState()
                androidx.compose.material3.DatePickerDialog(
                    onDismissRequest = { fbShowDesde = false },
                    confirmButton = {
                        TextButton(onClick = {
                            st.selectedDateMillis?.let { fbDesde = Fechas.deMillis(it) }
                            fbShowDesde = false
                        }) { Text("Aceptar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { fbShowDesde = false }) { Text("Cancelar") }
                    }
                ) { androidx.compose.material3.DatePicker(state = st) }
            }
            if (fbShowHasta) {
                val st = androidx.compose.material3.rememberDatePickerState()
                androidx.compose.material3.DatePickerDialog(
                    onDismissRequest = { fbShowHasta = false },
                    confirmButton = {
                        TextButton(onClick = {
                            st.selectedDateMillis?.let { fbHasta = Fechas.deMillis(it) }
                            fbShowHasta = false
                        }) { Text("Aceptar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { fbShowHasta = false }) { Text("Cancelar") }
                    }
                ) { androidx.compose.material3.DatePicker(state = st) }
            }
        }

        when (val state = uiState) {
            is UiState.Idle -> {
                if (lastResult != null) {
                    ResultCard(
                        message = lastResult.message,
                        processedCount = lastResult.processedCount,
                        errorCount = lastResult.errorCount
                    )
                    if (lastResult.operaciones.isNotEmpty()) {
                        Text("Últimas operaciones procesadas:", fontWeight = FontWeight.Bold)
                        lastResult.operaciones.take(10).forEach { op ->
                            OperacionCard(op)
                        }
                        if (lastResult.operaciones.size > 10) {
                            Text("... y ${lastResult.operaciones.size - 10} más",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Selecciona la carpeta Automatización y presiona Procesar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            is UiState.Scanning -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Escaneando...")
            }

            is UiState.Processing -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(8.dp))
                Text(state.message)
            }

            is UiState.DiagnosticResult, is UiState.ExtractionDiagnostic, is UiState.ExcelDiagnosticResult -> {
                val log = when (state) {
                    is UiState.DiagnosticResult -> state.log
                    is UiState.ExtractionDiagnostic -> state.log
                    is UiState.ExcelDiagnosticResult -> state.log
                    else -> ""
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    SelectionContainer {
                        Text(
                            text = log,
                            modifier = Modifier.padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            is UiState.Result -> {
                ResultCard(
                    message = state.message,
                    processedCount = state.processedCount,
                    errorCount = state.errorCount,
                    success = state.errorCount == 0
                )

                val pendientes by viewModel.ciudadesPendientes.collectAsState()
                if (pendientes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.showCityReviewDialog() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✓ Revisar ${pendientes.size} ciudades")
                    }
                }

                if (pendingOps.isNotEmpty() && pendientes.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.solicitarGrabar() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("💾 Grabar datos Excel")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val ts = java.text.SimpleDateFormat(
                                "yyyyMMdd_HHmmss", java.util.Locale.US
                            ).format(java.util.Calendar.getInstance().time)
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = XLSX_MIME
                                putExtra(Intent.EXTRA_TITLE, "FormatoTransfer_NUEVO_$ts.xlsx")
                            }
                            crearCopiaLauncher.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📄 Guardar copia en otro lugar…")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.limpiarPendientes() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✖ Limpiar pendientes (ya confirmé el archivo)")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.generarDiagnosticoEscritura() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔬 Ver diagnóstico de escritura")
                    }
                }

                if (state.operaciones.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Operaciones procesadas:", fontWeight = FontWeight.Bold)
                    state.operaciones.forEach { op ->
                        OperacionCard(op)
                    }
                }
            }

            is UiState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(state.message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }

    val showDialog by viewModel.showCityReview.collectAsState()
    val reviewItems by viewModel.pendingReview.collectAsState()
    if (showDialog && reviewItems.isNotEmpty()) {
        CityReviewDialog(
            items = reviewItems,
            onAccept = { viewModel.acceptCityItem(it) },
            onUpdate = { cliente, ciudad -> viewModel.updateCityItem(cliente, ciudad) },
            onAcceptAll = { viewModel.acceptAllCitiesDialog() },
            onDismiss = { viewModel.dismissCityReview() }
        )
    }

    val confirmarGrabar by viewModel.confirmarGrabar.collectAsState()
    if (confirmarGrabar && pendingOps.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelarGrabar() },
            title = { Text("¿Sobrescribir tu Excel?") },
            text = {
                Text(
                    "Se van a escribir ${pendingOps.size} transferencias en tu archivo.\n\n" +
                            "No se guarda ninguna copia del anterior."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.grabarDatosExcel() }) { Text("Continuar") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelarGrabar() }) { Text("Cancelar") }
            }
        )
    }

    val diagnostico by viewModel.diagnosticoEscritura.collectAsState()
    diagnostico?.let { (texto, _) ->
        AlertDialog(
            onDismissRequest = { viewModel.cerrarDiagnosticoEscritura() },
            title = { Text("Diagnóstico de escritura") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 420.dp)
                ) {
                    Text(
                        texto,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.cerrarDiagnosticoEscritura() }) { Text("Cerrar") }
            }
        )
    }

}

@Composable
private fun ResultCard(message: String, processedCount: Int, errorCount: Int, success: Boolean = true) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (success) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (success) "✓ Exitoso" else "✗ No exitoso",
                    fontWeight = FontWeight.Bold,
                    color = if (success) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            SelectionContainer { Text(message) }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Procesados: $processedCount | Errores: $errorCount")
        }
    }
}

@Composable
private fun OperacionCard(op: com.autotransfer.model.Operacion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("No. Transfer: ${op.noTransfer}", fontWeight = FontWeight.Bold)
            Text("Cliente: ${op.cliente}")
            Text("Fecha: ${op.fecha}")
            Text("Distribuidor: ${op.distribuidor}")
            Text("Ciudad: ${op.ciudad}")
            Text("Monto: ${op.monto}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val concentradoRules = remember { viewModel.getConcentradoRules() }
    val transferRules = remember { viewModel.getTransferRules() }
    val firestoreDiag by viewModel.firestoreDiagnosticResult.collectAsState()
    val searchResult by viewModel.clienteSearchResult.collectAsState()
    val scanDiag by viewModel.scanDiagResult.collectAsState()
    val extractDiag by viewModel.extractDiagResult.collectAsState()
    val excelDiag by viewModel.excelDiagResult.collectAsState()
    val fbProcessDiag by viewModel.fbProcessDiagResult.collectAsState()
    var searchName by remember { mutableStateOf("") }
    var showFbProcessDiag by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Text("Volver")
        }

        Text("Buscar cliente en Firebase", fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchName,
                onValueChange = { searchName = it },
                label = { Text("Nombre del cliente") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = { viewModel.searchClienteConVentas(searchName) },
                enabled = searchName.isNotBlank(),
                modifier = Modifier.height(40.dp)
            ) { Text("Buscar") }
        }

        searchResult?.let { (clientes, ventas) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Resultados", fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { viewModel.clearClienteSearch() },
                            modifier = Modifier.height(36.dp)
                        ) { Text("Cerrar") }
                    }
                    Text("${clientes.size} cliente(s), ${ventas.size} venta(s)")
                    Spacer(modifier = Modifier.height(4.dp))
                    clientes.forEach { c ->
                        Text("• ${c.nombre} | Zona: ${c.zona} | ${c.empresa}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (ventas.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        ventas.forEach { v ->
                            Text("${v.id} | ${v.fechaSubidoString} | Total: ${v.total}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            if (v.productos.isNotEmpty()) {
                                com.autotransfer.ui.components.ProductTable(
                                    productos = v.productos,
                                    totalVenta = v.total
                                )
                            }
                        }
                    }
                }
            }
        }

        firestoreDiag?.let { log ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Diagnóstico Firebase", fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { viewModel.clearFirestoreDiagnostic() },
                            modifier = Modifier.height(36.dp)
                        ) { Text("Cerrar") }
                    }
                    SelectionContainer {
                        Text(
                            text = log,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Text("Diagnóstico", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.runDiagnostic() },
                modifier = Modifier.weight(1f)
            ) { Text("Escanear") }
            OutlinedButton(
                onClick = { viewModel.runExtractionDiagnostic() },
                modifier = Modifier.weight(1f)
            ) { Text("Extraer") }
            OutlinedButton(
                onClick = { viewModel.runExcelDiagnostic() },
                modifier = Modifier.weight(1f)
            ) { Text("Excel") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.diagnosticFirestore() },
                modifier = Modifier.weight(1f)
            ) { Text("Firebase") }
            OutlinedButton(
                onClick = { showFbProcessDiag = true },
                modifier = Modifier.weight(1f)
            ) { Text("Process Firebase") }
        }

        @Composable fun DiagnosticResultCard(title: String, log: String?, onClear: () -> Unit) {
            log?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(title, fontWeight = FontWeight.Bold)
                            Button(onClick = onClear, modifier = Modifier.height(36.dp)) { Text("Cerrar") }
                        }
                        SelectionContainer {
                            Text(text = it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        DiagnosticResultCard(title = "Escanear", log = scanDiag, onClear = { viewModel.clearScanDiag() })
        DiagnosticResultCard(title = "Extracción", log = extractDiag, onClear = { viewModel.clearExtractDiag() })
        DiagnosticResultCard(title = "Excel", log = excelDiag, onClear = { viewModel.clearExcelDiag() })

        if (showFbProcessDiag) {
            var fbShowDesde by remember { mutableStateOf(false) }
            var fbShowHasta by remember { mutableStateOf(false) }
            var fbDesde by remember { mutableStateOf(viewModel.getSavedFechaDesde()) }
            var fbHasta by remember { mutableStateOf(viewModel.getSavedFechaHasta()) }
            var fbZonas by remember { mutableStateOf(viewModel.getSavedZonaFilter()) }

            AlertDialog(
                onDismissRequest = { showFbProcessDiag = false },
                title = { Text("Diagnóstico Firebase Process") },
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { fbShowDesde = true },
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Text(if (fbDesde.isBlank()) "Desde" else fbDesde)
                            }
                            OutlinedButton(
                                onClick = { fbShowHasta = true },
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Text(if (fbHasta.isBlank()) "Hasta" else fbHasta)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = fbZonas,
                            onValueChange = { fbZonas = it },
                            label = { Text("Zonas") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("25") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveFechaDesde(fbDesde)
                            viewModel.saveFechaHasta(fbHasta)
                            viewModel.procesarFirebaseDiagnostic(fbDesde, fbHasta, Fechas.zonas(fbZonas))
                            showFbProcessDiag = false
                        },
                        modifier = Modifier.height(40.dp)
                    ) { Text("Ejecutar") }
                },
                dismissButton = {
                    TextButton(onClick = { showFbProcessDiag = false }) { Text("Cancelar") }
                }
            )

            if (fbShowDesde) {
                val st = androidx.compose.material3.rememberDatePickerState()
                androidx.compose.material3.DatePickerDialog(
                    onDismissRequest = { fbShowDesde = false },
                    confirmButton = {
                        TextButton(onClick = {
                            st.selectedDateMillis?.let { fbDesde = Fechas.deMillis(it) }
                            fbShowDesde = false
                        }) { Text("Aceptar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { fbShowDesde = false }) { Text("Cancelar") }
                    }
                ) { androidx.compose.material3.DatePicker(state = st) }
            }
            if (fbShowHasta) {
                val st = androidx.compose.material3.rememberDatePickerState()
                androidx.compose.material3.DatePickerDialog(
                    onDismissRequest = { fbShowHasta = false },
                    confirmButton = {
                        TextButton(onClick = {
                            st.selectedDateMillis?.let { fbHasta = Fechas.deMillis(it) }
                            fbShowHasta = false
                        }) { Text("Aceptar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { fbShowHasta = false }) { Text("Cancelar") }
                    }
                ) { androidx.compose.material3.DatePicker(state = st) }
            }
        }

        fbProcessDiag?.let { log ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Resultado Firebase Process", fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { viewModel.clearFbProcessDiag() },
                            modifier = Modifier.height(36.dp)
                        ) { Text("Cerrar") }
                    }
                    SelectionContainer {
                        Text(
                            text = log,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        HorizontalDivider()
        var showPatterns by remember { mutableStateOf(false) }
        OutlinedButton(
            onClick = { showPatterns = !showPatterns },
            modifier = Modifier.fillMaxWidth().height(40.dp)
        ) {
            Text(if (showPatterns) "Ocultar Patrones Json" else "Mostrar Patrones Json")
        }
        if (showPatterns) {
        Text("Patrones - Concentrado", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        concentradoRules.forEach { rule ->
            RuleEditor(
                label = rule.label,
                pattern = rule.pattern,
                onPatternChange = { newPattern ->
                    viewModel.updateConcentradoRule(rule.field, newPattern)
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Patrones - Transfer", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        transferRules.forEach { rule ->
            RuleEditor(
                label = rule.label,
                pattern = rule.pattern,
                onPatternChange = { newPattern ->
                    viewModel.updateTransferRule(rule.field, newPattern)
                }
            )
        }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Los cambios se guardan automáticamente en la memoria. Para persistirlos, " +
                        "reemplaza los archivos JSON en la carpeta assets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RuleEditor(label: String, pattern: String, onPatternChange: (String) -> Unit) {
    var text by remember(pattern) { mutableStateOf(pattern) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = text,
                onValueChange = { newVal ->
                    text = newVal
                    onPatternChange(newVal)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
        }
    }
}
