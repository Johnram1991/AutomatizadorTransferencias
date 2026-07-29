package com.autotransfer.ui.cities

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autotransfer.data.CityEntity
import com.autotransfer.data.CityRepository
import com.autotransfer.data.firebase.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CitiesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CityRepository(application)
    private val firebaseRepository = FirebaseRepository()
    private val auth = FirebaseAuth.getInstance()

    private val _cities = MutableStateFlow<List<CityEntity>>(emptyList())
    val cities: StateFlow<List<CityEntity>> = _cities.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var lastClienteQ = ""
    private var lastCiudadQ = ""
    private var lastZonaQ = ""

    fun loadAll() {
        viewModelScope.launch {
            _isLoading.value = true
            val list = withContext(Dispatchers.IO) { repository.getAll() }
            _cities.value = list
            _isLoading.value = false
        }
    }

    fun search(clienteQ: String, ciudadQ: String, zonaQ: String = "") {
        lastClienteQ = clienteQ; lastCiudadQ = ciudadQ; lastZonaQ = zonaQ
        viewModelScope.launch {
            _isLoading.value = true
            val base = withContext(Dispatchers.IO) {
                when {
                    clienteQ.isNotBlank() && ciudadQ.isNotBlank() -> {
                        val byCliente = repository.searchByCliente(clienteQ)
                        val byCiudad = repository.searchByCiudad(ciudadQ)
                        val seen = mutableSetOf<String>()
                        (byCliente + byCiudad).filter { seen.add(it.cliente) }
                    }
                    clienteQ.isNotBlank() -> repository.searchByCliente(clienteQ)
                    ciudadQ.isNotBlank() -> repository.searchByCiudad(ciudadQ)
                    else -> repository.getAll()
                }
            }
            _cities.value = if (zonaQ.isBlank()) base else filtrarPorZona(base, zonaQ)
            _isLoading.value = false
        }
    }

    private suspend fun filtrarPorZona(cities: List<CityEntity>, zonaQ: String): List<CityEntity> {
        if (auth.currentUser == null) return cities
        val zonas = zonaQ.split(",", " ", ";").map { it.trim() }.filter { it.isNotBlank() }
        if (zonas.isEmpty()) return cities
        return try {
            val fbClientes = withContext(Dispatchers.IO) {
                firebaseRepository.searchClientes("", zonas)
            }
            val nombresZona = fbClientes.map { it.nombre.lowercase().trim() }.toSet()
            cities.filter { it.cliente.lowercase().trim() in nombresZona }
        } catch (_: Exception) {
            _message.value = "Error al filtrar por zona (sin conexión?)"
            cities
        }
    }

    fun update(clienteOriginal: String, clienteNuevo: String, ciudad: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.update(clienteOriginal, clienteNuevo, ciudad) }
            _message.value = "Actualizado: $clienteNuevo"
            search(lastClienteQ, lastCiudadQ, lastZonaQ)
        }
    }

    fun delete(cliente: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.delete(cliente) }
            _message.value = "Eliminado: $cliente"
            search(lastClienteQ, lastCiudadQ, lastZonaQ)
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
