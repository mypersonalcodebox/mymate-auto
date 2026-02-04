package com.mymate.auto.ui.parking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mymate.auto.data.local.AppDatabase
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.data.model.ParkingLocation
import com.mymate.auto.service.ParkingLocationService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ParkingViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getInstance(application)
    private val parkingDao = database.parkingDao()
    private val preferencesManager = PreferencesManager(application)
    
    val locations: Flow<List<ParkingLocation>> = parkingDao.getRecentLocations(50)
    val activeLocation: Flow<ParkingLocation?> = parkingDao.getActiveLocation()
    
    private val _uiState = MutableStateFlow(ParkingUiState())
    val uiState: StateFlow<ParkingUiState> = _uiState.asStateFlow()
    
    // Settings
    val autoSaveEnabled: Flow<Boolean> = preferencesManager.autoSaveParking
    val sendToTelegramEnabled: Flow<Boolean> = preferencesManager.sendParkingToTelegram
    
    init {
        viewModelScope.launch {
            locations.collect { locs ->
                _uiState.update { it.copy(locationCount = locs.size) }
            }
        }
    }
    
    /**
     * Manually save current location
     */
    fun saveCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                ParkingLocationService.start(getApplication())
                _uiState.update { it.copy(isSaving = false, message = "Locatie wordt opgeslagen...") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
    
    /**
     * Update note for a parking location
     */
    fun updateNote(location: ParkingLocation, note: String) {
        viewModelScope.launch {
            parkingDao.update(location.copy(note = note))
        }
    }
    
    /**
     * Delete a parking location
     */
    fun deleteLocation(location: ParkingLocation) {
        viewModelScope.launch {
            parkingDao.delete(location)
            _uiState.update { it.copy(message = "Locatie verwijderd") }
        }
    }
    
    /**
     * Clear all old (non-active) locations
     */
    fun clearOldLocations() {
        viewModelScope.launch {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            parkingDao.deleteOldLocations(thirtyDaysAgo)
            _uiState.update { it.copy(message = "Oude locaties verwijderd") }
        }
    }
    
    /**
     * Toggle auto-save setting
     */
    fun setAutoSave(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoSaveParking(enabled)
        }
    }
    
    /**
     * Toggle send to Telegram setting
     */
    fun setSendToTelegram(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSendParkingToTelegram(enabled)
        }
    }
    
    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }
}

data class ParkingUiState(
    val isSaving: Boolean = false,
    val locationCount: Int = 0,
    val message: String? = null,
    val error: String? = null
)
