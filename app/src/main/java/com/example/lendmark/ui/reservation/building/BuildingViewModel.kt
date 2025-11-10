package com.example.lendmark.ui.reservation.building

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lendmark.data.model.Building
import com.example.lendmark.data.repository.BuildingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BuildingViewModel : ViewModel() {
    private val repository = BuildingRepository()
    private val _buildings = MutableStateFlow<List<Building>>(emptyList())
    val buildings: StateFlow<List<Building>> get() = _buildings

    fun loadBuildings() {
        viewModelScope.launch {
            _buildings.value = repository.getBuildings()
        }
    }
}