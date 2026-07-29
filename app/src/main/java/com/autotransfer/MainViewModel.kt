package com.autotransfer

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autotransfer.data.CityEntity
import com.autotransfer.data.CityRepository
import com.autotransfer.data.firebase.FirebaseCliente
import com.autotransfer.data.firebase.FirebaseRepository
import com.autotransfer.data.firebase.FirebaseVenta
import com.autotransfer.excel.ExcelManager
import com.autotransfer.file.FileNavigator
import com.autotransfer.file.FileOrganizer
import com.autotransfer.match.OperationMatcher
import com.autotransfer.model.Operacion
import com.autotransfer.model.Rule
import com.autotransfer.pdf.ExtractorPDF
import com.autotransfer.pdf.PDFReader
import com.autotransfer.ui.components.todosMunicipios
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.StringWriter

sealed class UiState {
    data object Idle : UiState()
    data object Scanning : UiState()
    data class Processing(val message: String = "Procesando...") : UiState()
    data class Result(
        val message: String,
        val processedCount: Int,
        val errorCount: Int,
        val operaciones: List<Operacion>
    ) : UiState()
    data class Error(val message: String) : UiState()
    data class DiagnosticResult(val log: String) : UiState()
    data class ExtractionDiagnostic(val log: String) : UiState()
    data class ExcelDiagnosticResult(val log: String) : UiState()
}

sealed class FirebaseLoginState {
    data object Idle : FirebaseLoginState()
    data object Loading : FirebaseLoginState()
    data class LoggedIn(val user: FirebaseUser) : FirebaseLoginState()
    data class Error(val message: String) : FirebaseLoginState()
}

data class CityReviewItem(
    val cliente: String,
    val ciudadPropuesta: String,
    val repoOriginal: String? = null,
    val direccion: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _rootFolderUri = MutableStateFlow<Uri?>(null)
    val rootFolderUri: StateFlow<Uri?> = _rootFolderUri.asStateFlow()

    private val _excelUri = MutableStateFlow<Uri?>(null)
    val excelUri: StateFlow<Uri?> = _excelUri.asStateFlow()

    private val _excelFileName = MutableStateFlow<String?>(null)
    val excelFileName: StateFlow<String?> = _excelFileName.asStateFlow()

    private val _lastResult = MutableStateFlow<UiState.Result?>(null)
    val lastResult: StateFlow<UiState.Result?> = _lastResult.asStateFlow()

    private val _firebaseLoginState = MutableStateFlow<FirebaseLoginState>(FirebaseLoginState.Idle)
    val firebaseLoginState: StateFlow<FirebaseLoginState> = _firebaseLoginState.asStateFlow()

    private val _firebaseClientes = MutableStateFlow<List<FirebaseCliente>>(emptyList())
    val firebaseClientes: StateFlow<List<FirebaseCliente>> = _firebaseClientes.asStateFlow()

    private val _firebaseVentas = MutableStateFlow<List<FirebaseVenta>>(emptyList())
    val firebaseVentas: StateFlow<List<FirebaseVenta>> = _firebaseVentas.asStateFlow()

    private val _isFirebaseLoading = MutableStateFlow(false)
    val isFirebaseLoading: StateFlow<Boolean> = _isFirebaseLoading.asStateFlow()

    private val _firestoreDiagnosticResult = MutableStateFlow<String?>(null)
    val firestoreDiagnosticResult: StateFlow<String?> = _firestoreDiagnosticResult.asStateFlow()

    private val _clienteSearchResult = MutableStateFlow<Pair<List<FirebaseCliente>, List<FirebaseVenta>>?>(null)
    val clienteSearchResult: StateFlow<Pair<List<FirebaseCliente>, List<FirebaseVenta>>?> = _clienteSearchResult.asStateFlow()

    private val _fechaDesde = MutableStateFlow("")
    val fechaDesde: StateFlow<String> = _fechaDesde.asStateFlow()
    private val _fechaHasta = MutableStateFlow("")
    val fechaHasta: StateFlow<String> = _fechaHasta.asStateFlow()

    private val _ventasFiltradas = MutableStateFlow<List<FirebaseVenta>>(emptyList())
    val ventasFiltradas: StateFlow<List<FirebaseVenta>> = _ventasFiltradas.asStateFlow()

    private val _isAscending = MutableStateFlow(true)
    val isAscending: StateFlow<Boolean> = _isAscending.asStateFlow()

    private val _pendingOperaciones = MutableStateFlow<List<Operacion>>(emptyList())
    val pendingOperaciones: StateFlow<List<Operacion>> = _pendingOperaciones.asStateFlow()

    private val _ciudadesPendientes = MutableStateFlow<List<CityEntity>>(emptyList())
    val ciudadesPendientes: StateFlow<List<CityEntity>> = _ciudadesPendientes.asStateFlow()

    private val _showCityReview = MutableStateFlow(false)
    val showCityReview: StateFlow<Boolean> = _showCityReview.asStateFlow()

    private val _pendingReview = MutableStateFlow<List<CityReviewItem>>(emptyList())
    val pendingReview: StateFlow<List<CityReviewItem>> = _pendingReview.asStateFlow()

    private val _fbProcessDiagResult = MutableStateFlow<String?>(null)
    val fbProcessDiagResult: StateFlow<String?> = _fbProcessDiagResult.asStateFlow()

    private val _scanDiagResult = MutableStateFlow<String?>(null)
    val scanDiagResult: StateFlow<String?> = _scanDiagResult.asStateFlow()
    private val _extractDiagResult = MutableStateFlow<String?>(null)
    val extractDiagResult: StateFlow<String?> = _extractDiagResult.asStateFlow()
    private val _excelDiagResult = MutableStateFlow<String?>(null)
    val excelDiagResult: StateFlow<String?> = _excelDiagResult.asStateFlow()

    fun clearFbProcessDiag() { _fbProcessDiagResult.value = null }
    fun clearScanDiag() { _scanDiagResult.value = null }
    fun clearExtractDiag() { _extractDiagResult.value = null }
    fun clearExcelDiag() { _excelDiagResult.value = null }

    private val fileNavigator = FileNavigator(application)
    private val fileOrganizer = FileOrganizer(application)
    private val excelManager = ExcelManager()
    private val cityRepository = CityRepository(application)

    private val concentradoExtractor = ExtractorPDF()
    private val transferExtractor = ExtractorPDF()
    private val operatioMatcher = OperationMatcher()

    private var treeUri: Uri? = null
    private var corrections: Map<String, String> = emptyMap()

    private val auth = FirebaseAuth.getInstance()
    private val firebaseRepository = FirebaseRepository()
    private val prefs = application.getSharedPreferences("autotransfer", Context.MODE_PRIVATE)

    init {
        try {
            val savedUri = prefs.getString("tree_uri", null)
            if (savedUri != null) {
                treeUri = Uri.parse(savedUri)
                _rootFolderUri.value = treeUri
            }
        } catch (_: Exception) {}
        try {
            val savedExcelUri = prefs.getString("excel_uri", null)
            if (savedExcelUri != null) {
                val uri = Uri.parse(savedExcelUri)
                _excelUri.value = uri
                _excelFileName.value = prefs.getString("excel_name", null)
            }
        } catch (_: Exception) {}
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val app = getApplication<Application>()
                    val excelConfigJson = app.assets.open("config_excel.json")
                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    excelManager.loadConfig(excelConfigJson)

                    val concJson = app.assets.open("patrones_concentrado.json")
                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    concentradoExtractor.loadFromJson(concJson)

                    val transJson = app.assets.open("patrones_transfer.json")
                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    transferExtractor.loadFromJson(transJson)

                    corrections = loadCorrections(app)
                } catch (e: Exception) {
                    _uiState.value = UiState.Error("Error cargando config: ${e.message}")
                }
            }
        }

        // Restaurar sesión Firebase si existe token activo
        val user = auth.currentUser
        if (user != null) {
            _firebaseLoginState.value = FirebaseLoginState.LoggedIn(user)
        }
    }

    private fun loadCorrections(app: Application): Map<String, String> {
        return try {
            val json = app.assets.open("patrones_correcciones.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val obj = org.json.JSONObject(json)
            val arr = obj.getJSONArray("correcciones")
            val map = mutableMapOf<String, String>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                map[item.getString("buscar")] = item.getString("reemplazar")
            }
            map
        } catch (_: Exception) { emptyMap() }
    }

    private fun applyCorrections(name: String): String {
        var result = name
        for ((buscar, reemplazar) in corrections) {
            result = result.replace(buscar, reemplazar, ignoreCase = false)
        }
        return result
    }

    private fun excelColumnToIndex(column: String): Int {
        var result = 0
        for (ch in column.uppercase()) {
            result = result * 26 + (ch - 'A' + 1)
        }
        return result - 1
    }

    private fun excelColumnLetter(index: Int): String {
        var i = index
        var result = ""
        while (i >= 0) {
            result = ('A' + (i % 26)) + result
            i = i / 26 - 1
        }
        return result
    }

    fun getConcentradoRules(): List<Rule> = concentradoExtractor.getRules()
    fun getTransferRules(): List<Rule> = transferExtractor.getRules()
    fun updateConcentradoRule(field: String, newPattern: String) {
        concentradoExtractor.updateRule(field, newPattern)
    }
    fun updateTransferRule(field: String, newPattern: String) {
        transferExtractor.updateRule(field, newPattern)
    }

    fun setExcelUri(uri: Uri, fileName: String?) {
        _excelUri.value = uri
        _excelFileName.value = fileName
        prefs.edit().putString("excel_uri", uri.toString()).apply()
        prefs.edit().putString("excel_name", fileName).apply()
        treeUri = null
        _rootFolderUri.value = null
        prefs.edit().remove("tree_uri").apply()
        try {
            val ctx = getApplication<Application>()
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {}
    }

    fun setRootFolder(uri: Uri) {
        treeUri = uri
        _rootFolderUri.value = uri
        prefs.edit().putString("tree_uri", uri.toString()).apply()
        _excelUri.value = null
        _excelFileName.value = null
        prefs.edit().remove("excel_uri").apply()
        prefs.edit().remove("excel_name").apply()

        try {
            val ctx = getApplication<Application>()
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {}
    }

    // Firebase Auth

    fun firebaseLogin(email: String, password: String) {
        _firebaseLoginState.value = FirebaseLoginState.Loading
        viewModelScope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user
                if (user != null) {
                    _firebaseLoginState.value = FirebaseLoginState.LoggedIn(user)
                } else {
                    _firebaseLoginState.value = FirebaseLoginState.Error("Usuario no encontrado")
                }
            } catch (e: Exception) {
                _firebaseLoginState.value = FirebaseLoginState.Error(
                    e.message ?: "Error de conexión"
                )
            }
        }
    }

    fun firebaseLogout() {
        auth.signOut()
        _firebaseLoginState.value = FirebaseLoginState.Idle
        _firebaseClientes.value = emptyList()
        _firebaseVentas.value = emptyList()
        _ventasFiltradas.value = emptyList()
    }

    fun loadFirebaseData(zonas: List<String> = emptyList()) {
        if (auth.currentUser == null) return
        if (zonas.isNotEmpty()) saveZonaFilter(zonas)
        _isFirebaseLoading.value = true
        viewModelScope.launch {
            try {
                val clientes = withContext(Dispatchers.IO) {
                    firebaseRepository.searchClientes("", zonas)
                }
                val ventas = withContext(Dispatchers.IO) {
                    firebaseRepository.getVentasTransfer()
                }
                _firebaseClientes.value = clientes
                _isFirebaseLoading.value = false
                _firebaseVentas.value = withContext(Dispatchers.Default) {
                    val nombresZona = clientes.map { it.nombre.lowercase().trim() }
                    val fbCorrecciones = mapOf("macotel" to "macotela")
                    ventas.filter { v ->
                        if (zonas.isEmpty()) return@filter true
                        val vName = v.clienteNombre.lowercase().trim()
                        val vNameCorr = fbCorrecciones.entries.fold(vName) { acc, (k, v) -> acc.replace(k, v) }
                        nombresZona.any { FirebaseRepository.matchNombres(vNameCorr, it) }
                    }.sortedByDescending { ventaSortKey(it) }
                }

                setFechas(_fechaDesde.value, _fechaHasta.value)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error cargando datos Firebase: ${e.message}")
                _isFirebaseLoading.value = false
            }
        }
    }

    fun searchFirebaseClientes(query: String, zonas: List<String> = emptyList()) {
        viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    firebaseRepository.searchClientes(query, zonas)
                }
                _firebaseClientes.value = results
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error buscando clientes: ${e.message}")
            }
        }
    }

    fun loadVentasByCliente(clienteNombre: String, zonas: List<String> = emptyList()) {
        viewModelScope.launch {
            _isFirebaseLoading.value = true
            try {
                val ventas = withContext(Dispatchers.IO) {
                    firebaseRepository.getVentasByCliente(clienteNombre)
                        .sortedByDescending { ventaSortKey(it) }
                }
                val ventasFiltradas = if (zonas.isNotEmpty()) {
                    val nombresZona = _firebaseClientes.value
                        .filter { c -> zonas.any { z -> c.zona == z } }
                        .map { it.nombre.lowercase().trim() }
                    val fbCorrecciones = mapOf("macotel" to "macotela")
                    ventas.filter { v ->
                        val vName = v.clienteNombre.lowercase().trim()
                        val vNameCorr = fbCorrecciones.entries.fold(vName) { acc, (k, v) -> acc.replace(k, v) }
                        nombresZona.any { FirebaseRepository.matchNombres(vNameCorr, it) }
                    }
                } else ventas
                _firebaseVentas.value = ventasFiltradas
                setFechas(_fechaDesde.value, _fechaHasta.value)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error cargando ventas: ${e.message}")
            }
            _isFirebaseLoading.value = false
        }
    }

    fun diagnosticFirestore() {
        viewModelScope.launch {
            _isFirebaseLoading.value = true
            try {
                val log = withContext(Dispatchers.IO) {
                    firebaseRepository.diagnosticClientesRaw()
                }
                _firestoreDiagnosticResult.value = log
            } catch (e: Exception) {
                _firestoreDiagnosticResult.value = "Error: ${e.message}"
            }
            _isFirebaseLoading.value = false
        }
    }

    fun clearFirestoreDiagnostic() {
        _firestoreDiagnosticResult.value = null
    }

    fun searchClienteConVentas(nombre: String) {
        viewModelScope.launch {
            _isFirebaseLoading.value = true
            try {
                val (clientes, ventas) = withContext(Dispatchers.IO) {
                    firebaseRepository.searchClienteConVentas(nombre)
                }
                _clienteSearchResult.value = clientes to ventas
            } catch (e: Exception) {
                _firestoreDiagnosticResult.value = "Error: ${e.message}"
            }
            _isFirebaseLoading.value = false
        }
    }

    fun clearClienteSearch() {
        _clienteSearchResult.value = null
    }

    fun getSavedZonaFilter(): String = prefs.getString("zona_filter", "25") ?: "25"
    fun saveZonaFilter(zonas: List<String>) {
        prefs.edit().putString("zona_filter", zonas.joinToString(",")).apply()
    }

    fun toggleVentasOrder() {
        _isAscending.value = !_isAscending.value
        _firebaseVentas.value = if (_isAscending.value)
            _firebaseVentas.value.sortedBy { ventaSortKey(it) }
        else
            _firebaseVentas.value.sortedByDescending { ventaSortKey(it) }
        setFechas(_fechaDesde.value, _fechaHasta.value)
    }

    fun setFechas(desde: String, hasta: String) {
        _fechaDesde.value = desde
        _fechaHasta.value = hasta
        viewModelScope.launch(Dispatchers.Default) {
            val desdeNum = if (desde.isNotBlank()) parseFechaLocal(desde) else 0L
            val hastaNum = if (hasta.isNotBlank()) parseFechaLocal(hasta) else Long.MAX_VALUE
            val filtradas = _firebaseVentas.value.filter { v ->
                val d = parseFechaLocal(v.fechaSubidoString)
                d in desdeNum..hastaNum
            }
            _ventasFiltradas.value = filtradas
        }
    }

    private fun parseFechaLocal(fecha: String): Long {
        if (fecha.isBlank()) return 0L
        val meses = mapOf(
            "Ene" to 1, "Feb" to 2, "Mar" to 3, "Abr" to 4, "May" to 5, "Jun" to 6,
            "Jul" to 7, "Ago" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dic" to 12
        )
        val partes = fecha.split("-")
        if (partes.size != 3) return 0L
        val dia = partes[0].padStart(2, '0')
        val mes = meses[partes[1].take(3)]?.toString()?.padStart(2, '0') ?: "00"
        val anio = partes[2]
        return (anio + mes + dia).toLongOrNull() ?: 0L
    }

    fun getSavedFechaDesde(): String = prefs.getString("fecha_desde", "") ?: ""
    fun getSavedFechaHasta(): String = prefs.getString("fecha_hasta", "") ?: ""
    fun saveFechaDesde(fecha: String) { prefs.edit().putString("fecha_desde", fecha).apply() }
    fun saveFechaHasta(fecha: String) { prefs.edit().putString("fecha_hasta", fecha).apply() }

    private fun extraerFolio(id: String): Long {
        val idx = id.lastIndexOf('-')
        if (idx < 0) return 0L
        return id.substring(idx + 1).toLongOrNull() ?: 0L
    }

    private fun ventaSortKey(venta: FirebaseVenta): Long {
        val fecha = parseFecha(venta.fechaSubidoString)
        val folio = extraerFolio(venta.id)
        return fecha * 100000 + folio
    }

    private fun parseFecha(fecha: String): Long {
        if (fecha.isBlank()) return 0L
        val meses = mapOf(
            "Ene" to 1, "Feb" to 2, "Mar" to 3, "Abr" to 4, "May" to 5, "Jun" to 6,
            "Jul" to 7, "Ago" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dic" to 12
        )
        val partes = fecha.split("-")
        if (partes.size != 3) return 0L
        val dia = partes[0].padStart(2, '0')
        val mes = meses[partes[1].take(3)]?.toString()?.padStart(2, '0') ?: "00"
        val anio = partes[2]
        return (anio + mes + dia).toLongOrNull() ?: 0L
    }

    fun confirmarCiudad(cliente: String, ciudad: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { cityRepository.set(cliente, ciudad) }
            _ciudadesPendientes.value = _ciudadesPendientes.value.filter { it.cliente != cliente }
        }
    }

    fun confirmarTodasPendientes() {
        viewModelScope.launch {
            val pendientes = _ciudadesPendientes.value.toList()
            withContext(Dispatchers.IO) {
                for (p in pendientes) cityRepository.set(p.cliente, p.ciudad)
            }
            _ciudadesPendientes.value = emptyList()
        }
    }

    fun showCityReviewDialog() { _showCityReview.value = true }
    fun dismissCityReview() { _showCityReview.value = false }

    fun acceptCityItem(cliente: String) {
        viewModelScope.launch {
            val item = _pendingReview.value.find { it.cliente == cliente } ?: return@launch
            withContext(Dispatchers.IO) { cityRepository.set(item.cliente, item.ciudadPropuesta) }
            _ciudadesPendientes.value = _ciudadesPendientes.value.filter { it.cliente != cliente }
            _pendingReview.value = _pendingReview.value.filter { it.cliente != cliente }
            _pendingOperaciones.value = _pendingOperaciones.value.map {
                if (it.cliente == cliente) it.copy(ciudad = item.ciudadPropuesta) else it
            }
            if (_pendingReview.value.isEmpty()) _showCityReview.value = false
        }
    }

    fun updateCityItem(cliente: String, nuevaCiudad: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { cityRepository.set(cliente, nuevaCiudad) }
            _ciudadesPendientes.value = _ciudadesPendientes.value.filter { it.cliente != cliente }
            _pendingReview.value = _pendingReview.value.filter { it.cliente != cliente }
            _pendingOperaciones.value = _pendingOperaciones.value.map {
                if (it.cliente == cliente) it.copy(ciudad = nuevaCiudad) else it
            }
            if (_pendingReview.value.isEmpty()) _showCityReview.value = false
        }
    }

    fun acceptAllCitiesDialog() {
        viewModelScope.launch {
            val items = _pendingReview.value.toList()
            withContext(Dispatchers.IO) {
                for (item in items) cityRepository.set(item.cliente, item.ciudadPropuesta)
            }
            val map = items.associate { it.cliente to it.ciudadPropuesta }
            _pendingOperaciones.value = _pendingOperaciones.value.map {
                map[it.cliente]?.let { ciudad -> it.copy(ciudad = ciudad) } ?: it
            }
            _ciudadesPendientes.value = emptyList()
            _pendingReview.value = emptyList()
            _showCityReview.value = false
        }
    }

    fun procesarDesdeFirebase(desde: String, hasta: String, zonas: List<String>) {
        if (auth.currentUser == null) {
            _uiState.value = UiState.Error("No hay sesión de Firebase")
            return
        }
        _pendingOperaciones.value = emptyList()
        _uiState.value = UiState.Scanning
        viewModelScope.launch {
            try {
                val resultados = withContext(Dispatchers.IO) {
                    firebaseRepository.getVentasParaExcel(zonas, desde, hasta)
                        .sortedBy { ventaSortKey(it.venta) }
                }

                val todasGuardadas = withContext(Dispatchers.IO) {
                    cityRepository.getAll().associate { it.cliente.lowercase().trim() to it.ciudad }
                }

                val (operaciones, pendientes) = withContext(Dispatchers.Default) {
                    val ops = mutableListOf<Operacion>()
                    val pends = mutableListOf<CityEntity>()
                    for ((_, r) in resultados.withIndex()) {
                        val key = r.venta.clienteNombre.lowercase().trim()
                        val repoCity = todasGuardadas[key]
                        val repoOk = repoCity != null && esCiudadValida(repoCity)
                        val extracted = extraerCiudad(r.direccion)
                        val extractedOk = esCiudadValida(extracted)
                        val ciudad = when {
                            repoOk && (!extractedOk || repoCity == extracted) -> repoCity!!
                            else -> extracted
                        }
                        ops.add(Operacion(
                            noTransfer = r.venta.id,
                            cliente = r.venta.clienteNombre,
                            fecha = r.venta.fechaSubidoString,
                            monto = String.format("%.2f", r.venta.total),
                            ciudad = ciudad,
                            distribuidor = r.distribuidor
                        ))
                        val needsReview = !repoOk || (repoOk && extractedOk && repoCity != extracted)
                        if (needsReview) {
                            pends.add(CityEntity(cliente = r.venta.clienteNombre.trim(), ciudad = ciudad))
                        }
                    }
                    Pair(ops, pends.distinctBy { it.cliente })
                }

                _pendingOperaciones.value = operaciones
                _ciudadesPendientes.value = pendientes
                val direccionPorCliente = resultados.associate {
                    it.venta.clienteNombre.trim().lowercase() to it.direccion
                }
                _pendingReview.value = pendientes.map {
                    CityReviewItem(
                        it.cliente, it.ciudad,
                        todasGuardadas[it.cliente.lowercase().trim()],
                        direccion = direccionPorCliente[it.cliente.lowercase()] ?: ""
                    )
                }

                val extra = if (pendientes.isNotEmpty()) "\n${pendientes.size} ciudades por confirmar" else ""
                val result = UiState.Result(
                    message = "${operaciones.size} operaciones listas para grabar$extra",
                    processedCount = operaciones.size,
                    errorCount = 0,
                    operaciones = operaciones
                )
                _lastResult.value = result
                _uiState.value = result
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error: ${e.message}")
            }
        }
    }

    fun procesarFirebaseDiagnostic(desde: String, hasta: String, zonas: List<String>) {
        if (auth.currentUser == null) {
            _fbProcessDiagResult.value = "No hay sesión de Firebase"
            return
        }
        _fbProcessDiagResult.value = null
        viewModelScope.launch {
            try {
                val resultados = withContext(Dispatchers.IO) {
                    firebaseRepository.getVentasParaExcel(zonas, desde, hasta)
                }
                val todasGuardadas = withContext(Dispatchers.IO) {
                    cityRepository.getAll().associate { it.cliente.lowercase().trim() to it.ciudad }
                }
                val (pendientes, diag) = withContext(Dispatchers.Default) {
                    val pends = mutableListOf<CityEntity>()
                    val d = StringBuilder()
                    for (r in resultados) {
                        val key = r.venta.clienteNombre.lowercase().trim()
                        val repoCity = todasGuardadas[key]
                        val repoOk = repoCity != null && esCiudadValida(repoCity)
                        val extracted = extraerCiudad(r.direccion)
                        val extractedOk = esCiudadValida(extracted)
                        val ciudad = when {
                            repoOk && (!extractedOk || repoCity == extracted) -> repoCity!!
                            else -> extracted
                        }
                        val src = when {
                            repoOk && (!extractedOk || repoCity == extracted) -> "repo"
                            else -> "extraer"
                        }
                        val diff = if (repoOk && extractedOk && repoCity != extracted) " [repo='$repoCity' != extraer='$extracted']" else ""
                        d.appendLine("${r.venta.id} | [$src]$diff dir='${r.direccion.take(80)}' → '$ciudad'")
                        val needsReview = !repoOk || (repoOk && extractedOk && repoCity != extracted)
                        if (needsReview) {
                            pends.add(CityEntity(cliente = r.venta.clienteNombre.trim(), ciudad = ciudad))
                        }
                    }
                    Pair(pends.distinctBy { it.cliente }, d)
                }
                _ciudadesPendientes.value = pendientes
                val direccionPorCliente = resultados.associate {
                    it.venta.clienteNombre.trim().lowercase() to it.direccion
                }
                _pendingReview.value = pendientes.map {
                    CityReviewItem(
                        it.cliente, it.ciudad,
                        todasGuardadas[it.cliente.lowercase().trim()],
                        direccion = direccionPorCliente[it.cliente.lowercase()] ?: ""
                    )
                }
                val extra = if (pendientes.isNotEmpty()) "\n\n${pendientes.size} ciudades por confirmar" else ""
                _fbProcessDiagResult.value = "${diag}$extra"
                _uiState.value = UiState.Idle
            } catch (e: Exception) {
                _fbProcessDiagResult.value = "Error: ${e.message}"
                _uiState.value = UiState.Idle
            }
        }
    }

    fun grabarDatosExcel() {
        if (auth.currentUser == null) {
            _uiState.value = UiState.Error("No hay sesión de Firebase")
            return
        }
        val excelUri = _excelUri.value
        if (excelUri == null) {
            _uiState.value = UiState.Error("Selecciona el archivo Formato Transfer.xlsx primero")
            return
        }
        val ops = _pendingOperaciones.value
        if (ops.isEmpty()) {
            _uiState.value = UiState.Error("No hay datos pendientes para grabar. Procesa desde Firebase primero.")
            return
        }
        _uiState.value = UiState.Processing("Grabando datos en Excel...")
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val resultMsg = withContext(Dispatchers.IO) {
                    excelManager.updateExcel(ctx, excelUri, ops)
                }
                _pendingOperaciones.value = emptyList()
                val result = UiState.Result(
                    message = resultMsg,
                    processedCount = ops.size,
                    errorCount = 0,
                    operaciones = ops
                )
                _lastResult.value = result
                _uiState.value = result
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error grabando Excel: ${e.message}")
            }
        }
    }

    // Casos especiales de direcciones donde la lógica no puede inferir la ciudad
    private val direccionCiudadEspecial = mapOf(
        "Carreteras La Piedad Ciudad Manuel Doblado" to "La Piedad",
        "Victorino de las Fuentes 677, Barrio Nuevo" to "Irapuato"
    )

    private val estados = mapOf(
        "guanajuato" to true, "michoacan" to true, "michoacán" to true, "guerrero" to true,
        "aguascalientes" to true, "baja california" to true, "baja california sur" to true,
        "campeche" to true, "chiapas" to true, "chihuahua" to true, "coahuila" to true,
        "colima" to true, "durango" to true, "mexico" to true, "méxico" to true,
        "nuevo leon" to true, "nuevo león" to true, "oaxaca" to true, "puebla" to true,
        "queretaro" to true, "querétaro" to true, "quintana roo" to true,
        "san luis potosi" to true, "san luis potosí" to true, "sinaloa" to true,
        "sonora" to true, "tabasco" to true, "tamaulipas" to true, "tlaxcala" to true,
        "veracruz" to true, "yucatan" to true, "yucatán" to true, "zacatecas" to true,
        "morelos" to true, "nayarit" to true, "hidalgo" to true, "jalisco" to true
    )

    private val abreviaturas = mapOf(
        "mic" to "michoacan", "mich" to "michoacan",
        "gto" to "guanajuato", "gro" to "guerrero",
        "mor" to "morelos", "jal" to "jalisco", "mex" to "mexico",
        "nl" to "nuevo leon", "ags" to "aguascalientes",
        "bc" to "baja california", "bcs" to "baja california sur",
        "camp" to "campeche", "chis" to "chiapas", "chih" to "chihuahua",
        "coah" to "coahuila", "col" to "colima", "dgo" to "durango",
        "hgo" to "hidalgo", "nay" to "nayarit", "oax" to "oaxaca",
        "pue" to "puebla", "qro" to "queretaro",
        "q. roo" to "quintana roo", "slp" to "san luis potosi",
        "sin" to "sinaloa", "son" to "sonora", "tab" to "tabasco",
        "tamps" to "tamaulipas", "tlax" to "tlaxcala",
        "ver" to "veracruz", "yuc" to "yucatan", "zac" to "zacatecas"
    )

    private fun esCiudadValida(ciudad: String): Boolean {
        val c = ciudad.trim()
        if (c.length > 30) return false
        if (c.any { it.isDigit() }) return false
        val cNorm = normalizar(c)
        return todosMunicipios.any { normalizar(it) == cNorm }
    }

    private fun extraerCiudad(direccion: String): String {
        if (direccion.isBlank()) return ""
        val dTrim = direccion.trim()

        // Casos especiales
        for ((patron, ciudad) in direccionCiudadEspecial) {
            if (dTrim.contains(patron, ignoreCase = true)) return ciudad
        }

        val partes = dTrim.split(",", "\\.")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // 1. Buscar estado (completo o abreviatura) con límites de palabra
        for ((i, parte) in partes.withIndex()) {
            val pNorm = normalizar(parte)

            // 1a. Fragmento completo = abreviatura exacta → ciudad en fragmento anterior
            val esAbrev = abreviaturas.keys.any { it == pNorm }
            if (esAbrev && i > 0) {
                val ant = partes[i - 1]
                val c = extraerCiudadDeParte(ant)
                if (c != null) return c
            }

            // 1b. Buscar estado dentro del fragmento (con límites de palabra)
            var mejorPos = -1
            for (abrev in abreviaturas.keys) {
                val idx = indexOfPalabra(pNorm, abrev)
                if (idx >= 0 && idx > mejorPos) mejorPos = idx
            }
            for (estado in estados.keys) {
                val idx = indexOfPalabra(pNorm, estado)
                if (idx >= 0 && idx > mejorPos) mejorPos = idx
            }

            if (mejorPos >= 0) {
                val antes = parte.substring(0, mejorPos).trimEnd(',', ' ', '.').trim()
                val c = extraerCiudadDeParte(antes)
                if (c != null) return c
            }
        }

        // 2. Sin estado visible: buscar municipio conocido (más a la derecha)
        var mejorMatch: String? = null
        var mejorParteIdx = -1
        var mejorPosicion = -1
        for ((i, parte) in partes.withIndex()) {
            val pNorm = normalizar(parte)
            if (estados.containsKey(pNorm)) continue
            val matches = mutableListOf<Pair<String, Int>>()
            for (m in todosMunicipios) {
                val mNorm = normalizar(m)
                val idx = indexOfPalabra(pNorm, mNorm)
                if (idx >= 0) matches.add(m to idx)
            }
            val filtered = matches.filter { (m, start) ->
                val end = start + normalizar(m).length
                matches.none { (other, otherStart) ->
                    other != m && otherStart <= start && otherStart + normalizar(other).length >= end
                }
            }
            for ((m, idx) in filtered) {
                if (i > mejorParteIdx || (i == mejorParteIdx && idx > mejorPosicion)) {
                    mejorMatch = m; mejorParteIdx = i; mejorPosicion = idx
                }
            }
        }
        if (mejorMatch != null) return mejorMatch

        // 3. Fallback: fragmento no-numérico, no-estado, desde el final
        for (i in (partes.size - 1) downTo 0) {
            val c = partes[i]
            val cNorm = normalizar(c)
            if (c.all { it.isDigit() || it.isWhitespace() }) continue
            if (c.length < 3) continue
            if (estados.keys.any { it == cNorm }) continue
            if (abreviaturas.keys.any { it == cNorm }) continue
            if (c.all { it == '.' || it == ',' || it.isWhitespace() }) continue
            return c
        }

        return dTrim
    }

    // Extrae el municipio más a la derecha dentro de una parte

    private fun extraerCiudadDeParte(texto: String): String? {
        if (texto.isBlank()) return null
        val tNorm = normalizar(texto)
        val matches = todosMunicipios
            .filter { indexOfPalabra(tNorm, normalizar(it)) >= 0 }
            .map { m -> m to tNorm.lastIndexOf(normalizar(m)) }
        if (matches.isNotEmpty()) {
            return matches.maxBy { (_, idx) -> idx }.first
        }
        // Última palabra como fallback
        val lastSpace = texto.lastIndexOf(' ')
        val palabra = if (lastSpace > 0) texto.substring(lastSpace + 1).trim() else texto.trim()
        return if (palabra.length >= 3) palabra else null
    }

    // indexOf con límites de palabra (no coincide parcialmente dentro de otra palabra)

    private fun indexOfPalabra(text: String, word: String): Int {
        if (word.isEmpty() || text.isEmpty()) return -1
        var idx = text.indexOf(word)
        while (idx >= 0) {
            val before = idx == 0 || !text[idx - 1].isLetterOrDigit()
            val after = idx + word.length >= text.length || !text[idx + word.length].isLetterOrDigit()
            if (before && after) return idx
            idx = text.indexOf(word, idx + 1)
        }
        return -1
    }

    private fun normalizar(s: String): String {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .trim()
    }

    // ---- Existing methods ----

    private fun classifyPdf(concDatos: Map<String, String>, transDatos: Map<String, String>): String? {
        val concNonEmpty = concDatos.filter { it.value.isNotBlank() }.keys
        val transNonEmpty = transDatos.filter { it.value.isNotBlank() }.keys
        val isConc = concDatos["noTransfer"]?.isNotBlank() == true &&
                (concDatos["cliente"]?.isNotBlank() == true || concDatos["total"]?.isNotBlank() == true)
        val isTrans = transDatos["noTransfer"]?.isNotBlank() == true &&
                (transDatos["ciudad"]?.isNotBlank() == true || transDatos["distribuidor"]?.isNotBlank() == true)
        return when {
            isConc && !isTrans -> "concentrado"
            isTrans && !isConc -> "transfer"
            isConc && isTrans -> if (concNonEmpty.size >= transNonEmpty.size) "concentrado" else "transfer"
            else -> null
        }
    }

    fun runDiagnostic() {
        if (treeUri == null) {
            _scanDiagResult.value = "Selecciona una carpeta primero"
            return
        }
        _scanDiagResult.value = "Escaneando..."
        viewModelScope.launch {
            try {
                val excelFileName = excelManager.getConfig().excelFileName
                val diag = withContext(Dispatchers.IO) {
                    fileNavigator.diagnoseFolder(treeUri!!, excelFileName)
                }
                _scanDiagResult.value = diag
            } catch (e: Exception) {
                _scanDiagResult.value = "Diagnóstico falló: ${e.message}"
            }
        }
    }

    fun runExtractionDiagnostic() {
        if (treeUri == null) {
            _extractDiagResult.value = "Selecciona una carpeta primero"
            return
        }
        _extractDiagResult.value = "Extrayendo..."
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val ctx = getApplication<Application>()
                    val config = excelManager.getConfig()
                    val contents = fileNavigator.scanFolder(treeUri!!, config.excelFileName)
                    val pdfs = contents.concentradoPdfs + contents.transferPdfs + contents.rootPdfs

                    val sb = StringBuilder()
                    sb.appendLine("=== EXTRACCIÓN DE PDFs ===")
                    sb.appendLine("Total PDFs: ${pdfs.size}")

                    for ((i, pdf) in pdfs.withIndex()) {
                        sb.appendLine("")
                        sb.appendLine("--- PDF ${i + 1}: ${pdf.name} ---")
                        sb.appendLine("URI: ${pdf.uri}")
                        try {
                            val text = PDFReader.extractText(ctx, pdf.uri)
                            val excerpt = text.take(8000)
                            sb.appendLine("--- TEXTO (primeros 500 chars) ---")
                            sb.appendLine(excerpt)
                            sb.appendLine("--- FIN TEXTO ---")

                            val concDatos = concentradoExtractor.extractAll(text)
                            val transDatos = transferExtractor.extractAll(text)

                            sb.appendLine("Campos CONCENTRADO:")
                            for ((k, v) in concDatos) {
                                sb.appendLine("  $k = '${v.take(100)}'")
                            }
                            sb.appendLine("Campos TRANSFER:")
                            for ((k, v) in transDatos) {
                                sb.appendLine("  $k = '${v.take(100)}'")
                            }

                            val tipo = classifyPdf(concDatos, transDatos)
                            sb.appendLine("Clasificación: ${tipo ?: "NO CLASIFICADO"}")

                            val concRows = concentradoExtractor.extractTableRows(text)
                            if (concRows.isNotEmpty()) {
                                sb.appendLine("FILAS extraídas del Concentrado:")
                                for ((j, row) in concRows.withIndex()) {
                                    sb.appendLine("  [${j + 1}] noTransfer='${row["noTransfer"] ?: ""}' | cliente='${(row["cliente"] ?: "").take(40)}' | fecha='${row["fecha"] ?: ""}' | monto='${row["monto"] ?: ""}'")
                                }
                            }
                        } catch (e: Exception) {
                            sb.appendLine("ERROR al leer PDF: ${e.message}")
                        }
                    }
                    sb.appendLine("")
                    sb.appendLine("=== FIN EXTRACCIÓN ===")
                    sb.toString()
                }
                _extractDiagResult.value = result
            } catch (e: Exception) {
                _extractDiagResult.value = "Extracción falló: ${e.message}"
            }
        }
    }

    fun runExcelDiagnostic() {
        val excelUri = _excelUri.value
        if (excelUri == null && treeUri == null) {
            _excelDiagResult.value = "Selecciona el archivo Excel o una carpeta primero"
            return
        }
        _excelDiagResult.value = "Analizando..."
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val ctx = getApplication<Application>()
                    val config = excelManager.getConfig()
                    val excelFile = if (excelUri != null) {
                        val docFile = DocumentFile.fromSingleUri(ctx, excelUri)
                        if (docFile?.exists() == true) docFile else null
                    } else {
                        fileNavigator.scanFolder(treeUri!!, config.excelFileName).excelFile
                    }
                    val sb = StringBuilder()
                    sb.appendLine("=== DIAGNÓSTICO EXCEL ===")
                    sb.appendLine("Archivo esperado: ${config.excelFileName}")
                    if (excelFile == null) {
                        sb.appendLine("ERROR: No se encontró '${config.excelFileName}' en la raíz")
                        return@withContext sb.toString()
                    }
                    sb.appendLine("Archivo encontrado: ${excelFile.name}")
                    sb.appendLine("URI: ${excelFile.uri}")
                    val bytes = try {
                        ctx.contentResolver.openInputStream(excelFile.uri)?.use { it.readBytes() }
                    } catch (_: Exception) { null }
                    if (bytes == null) { sb.appendLine("ERROR: No se pudo leer el archivo"); return@withContext sb.toString() }
                    sb.appendLine("Tamaño: ${bytes.size} bytes")
                    val magic = bytes.take(4).joinToString("") { "%02x".format(it) }
                    sb.appendLine("Magic bytes: $magic ${if (magic.equals("504b0304", ignoreCase = true)) "✓ ZIP válido" else "✗ NO es ZIP"}")
                    if (bytes.size < 100) { sb.appendLine("ERROR: Archivo demasiado pequeño"); return@withContext sb.toString() }
                    if (!magic.equals("504b0304", ignoreCase = true)) { sb.appendLine("ERROR: No es ZIP válido"); return@withContext sb.toString() }
                    val tempFile = java.io.File(ctx.cacheDir, "excel_diag_temp.xlsx")
                    tempFile.writeBytes(bytes)
                    sb.appendLine(""); sb.appendLine("--- Estrategias de apertura ---")
                    var workbook: org.apache.poi.xssf.usermodel.XSSFWorkbook? = null
                    var exitosa = ""
                    try {
                        workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook(tempFile.inputStream())
                        exitosa = "XSSFWorkbook(InputStream)"; sb.appendLine("✓ Estrategia 1: OK")
                    } catch (e1: Exception) {
                        sb.appendLine("✗ Estrategia 1: ${e1.message}")
                        try {
                            workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(tempFile) as org.apache.poi.xssf.usermodel.XSSFWorkbook
                            exitosa = "WorkbookFactory.create(File)"; sb.appendLine("✓ Estrategia 2: OK")
                        } catch (e2: Exception) {
                            sb.appendLine("✗ Estrategia 2: ${e2.message}")
                            try {
                                val pkg = org.apache.poi.openxml4j.opc.OPCPackage.open(tempFile, org.apache.poi.openxml4j.opc.PackageAccess.READ)
                                workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook(pkg)
                                exitosa = "OPCPackage.open + XSSFWorkbook"; sb.appendLine("✓ Estrategia 3: OK")
                            } catch (e3: Exception) {
                                sb.appendLine("✗ Estrategia 3: ${e3.message}"); sb.appendLine("ERROR: No se pudo abrir el Excel")
                                return@withContext sb.toString()
                            }
                        }
                    }
                    sb.appendLine("Abierto con: $exitosa"); sb.appendLine("")
                    val numSheets = workbook!!.numberOfSheets
                    sb.appendLine("--- Hojas ($numSheets) ---")
                    for (i in 0 until numSheets) {
                        val sheet = workbook.getSheetAt(i)
                        sb.appendLine("[$i] '${sheet.sheetName}' — última fila: ${sheet.lastRowNum}, filas físicas: ${sheet.physicalNumberOfRows}")
                        val headerRowIdx = config.headerRow - 1
                        val headerRow = sheet.getRow(headerRowIdx)
                        if (headerRow != null) {
                            sb.appendLine("     Fila encabezados (fila ${config.headerRow}):")
                            for (c in headerRow.firstCellNum until headerRow.lastCellNum) {
                                sb.appendLine("       Col ${excelColumnLetter(c)} (idx $c): \"${headerRow.getCell(c)?.toString()?.take(50) ?: "(vacía)"}\"")
                            }
                        }
                        val dataStartIdx = config.dataStartRow - 1
                        if (sheet.lastRowNum >= dataStartIdx) {
                            val sampleCount = minOf(3, sheet.lastRowNum - dataStartIdx + 1)
                            sb.appendLine("     Muestra primeras $sampleCount filas:")
                            for (r in dataStartIdx until dataStartIdx + sampleCount) {
                                val row = sheet.getRow(r)
                                if (row != null) {
                                    val idVal = row.getCell(excelColumnToIndex(config.columnaId))?.toString()?.take(40) ?: "(vacía)"
                                    sb.appendLine("       Fila ${r + 1}: colId='$idVal'")
                                } else sb.appendLine("       Fila ${r + 1}: (vacía)")
                            }
                        }
                    }
                    sb.appendLine(""); sb.appendLine("--- Verificación de columnas ---")
                    for ((name, col) in mapOf("fecha" to config.columnas["fecha"], "noTransfer" to config.columnas["noTransfer"], "cliente" to config.columnas["cliente"], "distribuidor" to config.columnas["distribuidor"], "ciudad" to config.columnas["ciudad"], "monto" to config.columnas["monto"])) {
                        if (col != null) sb.appendLine("  $name -> columna $col ✓") else sb.appendLine("  $name -> NO CONFIGURADA ✗")
                    }
                    sb.appendLine(""); sb.appendLine("--- Nombres de meses ---")
                    for (m in listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")) {
                        val s = workbook.getSheet(m)
                        sb.appendLine("  $m: ${if (s != null) "EXISTE (${s.lastRowNum + 1} filas)" else "NO EXISTE"}")
                    }
                    workbook.close(); sb.appendLine(""); sb.appendLine("=== FIN DIAGNÓSTICO EXCEL ===")
                    sb.toString()
                }
                _excelDiagResult.value = result
            } catch (e: Exception) {
                val sw = StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                _excelDiagResult.value = "Error: ${e.message}\n${sw.toString().take(1500)}"
            }
        }
    }

    fun scanAndProcess() {
        if (treeUri == null) {
            _uiState.value = UiState.Error("No se seleccionó carpeta")
            return
        }

        _uiState.value = UiState.Scanning

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val ctx = getApplication<Application>()
                    val config = excelManager.getConfig()
                    val contents = fileNavigator.scanFolder(treeUri!!, config.excelFileName)

                    if (contents.excelFile == null) {
                        _uiState.value = UiState.Error("No se encontró '${config.excelFileName}' en la raíz")
                        return@withContext
                    }

                    val procesados = fileNavigator.ensureFolderExists(treeUri!!, "Procesados")
                    val errores = fileNavigator.ensureFolderExists(treeUri!!, "Errores")

                    val allPdfs = contents.concentradoPdfs + contents.transferPdfs
                    val rootPdfs = contents.rootPdfs

                    if (allPdfs.isEmpty() && rootPdfs.isEmpty()) {
                        _uiState.value = UiState.Error("No hay PDFs en la carpeta")
                        return@withContext
                    }

                    var concentradoRows = mutableListOf<Map<String, String>>()
                    var transferMaps = mutableListOf<Map<String, String>>()

                    for (pdf in contents.concentradoPdfs + rootPdfs.filter {
                        try {
                            val t = PDFReader.extractText(ctx, it.uri)
                            concentradoExtractor.extractAll(t)["noTransfer"]?.isNotBlank() == true
                        } catch (_: Exception) { false }
                    }) {
                        try {
                            val text = PDFReader.extractText(ctx, pdf.uri)
                            val rows = concentradoExtractor.extractTableRows(text)
                            concentradoRows.addAll(rows)
                        } catch (e: Exception) {
                            if (errores != null) {
                                fileOrganizer.moveToErrors(pdf, errores, "Error lectura: ${e.message}")
                            }
                        }
                    }

                    for (pdf in contents.transferPdfs + rootPdfs.filter {
                        try {
                            val t = PDFReader.extractText(ctx, it.uri)
                            transferExtractor.extractAll(t)["noTransfer"]?.isNotBlank() == true
                        } catch (_: Exception) { false }
                    }) {
                        try {
                            val text = PDFReader.extractText(ctx, pdf.uri)
                            val datos = transferExtractor.extractAll(text)
                            transferMaps.add(datos)
                        } catch (e: Exception) {
                            if (errores != null) {
                                fileOrganizer.moveToErrors(pdf, errores, "Error lectura: ${e.message}")
                            }
                        }
                    }

                    if (concentradoRows.isEmpty() && transferMaps.isEmpty()) {
                        _uiState.value = UiState.Error("No se pudieron extraer datos de los PDFs")
                        return@withContext
                    }

                    val rawOperaciones = if (concentradoRows.isNotEmpty()) {
                        operatioMatcher.matchByNoTransfer(concentradoRows, transferMaps)
                    } else {
                        transferMaps.map { t ->
                            Operacion(
                                noTransfer = t["noTransfer"]?.trim() ?: "",
                                cliente = t["cliente"]?.trim() ?: "",
                                fecha = t["fecha"]?.trim() ?: "",
                                monto = t["monto"]?.trim() ?: "",
                                distribuidor = (t["distribuidor"] ?: "").uppercase(),
                                ciudad = t["ciudad"]?.trim() ?: ""
                            )
                        }
                    }
                    val operaciones = rawOperaciones.map { it.copy(cliente = applyCorrections(it.cliente)) }
                    var errorCount = 0

                    if (operaciones.isEmpty()) {
                        _uiState.value = UiState.Error("No se pudieron cotejar operaciones")
                        return@withContext
                    }

                    _uiState.value = UiState.Processing("Procesando PDFs y actualizando Excel...")

                    val excelCities = excelManager.loadCitiesFromExcel(ctx, contents.excelFile.uri)
                    cityRepository.bulkSet(excelCities)

                    val operacionesConCiudades = operaciones.map { op ->
                        if (op.ciudad.isEmpty()) {
                            val cachedCity = cityRepository.get(op.cliente)
                            if (cachedCity != null && esCiudadValida(cachedCity)) {
                                op.copy(ciudad = cachedCity)
                            } else op
                        } else op
                    }

                    val resultMsg = excelManager.updateExcel(ctx, contents.excelFile.uri, operacionesConCiudades)

                    cityRepository.updateFromOperaciones(operacionesConCiudades)

                    val allProcessedPdfs = contents.concentradoPdfs + contents.transferPdfs + rootPdfs
                    var processedCount = 0
                    for (pdf in allProcessedPdfs) {
                        if (procesados != null && fileOrganizer.moveToProcessed(pdf, procesados))
                            processedCount++
                        else errorCount++
                    }

                    val result = UiState.Result(
                        message = resultMsg,
                        processedCount = processedCount,
                        errorCount = errorCount,
                        operaciones = operacionesConCiudades
                    )
                    _lastResult.value = result
                    _uiState.value = result

                } catch (e: Exception) {
                    val sw = StringWriter()
                    e.printStackTrace(java.io.PrintWriter(sw))
                    _uiState.value = UiState.Error("Error: ${e.message}\n${sw.toString().take(1500)}")
                }
            }
        }
    }
}
