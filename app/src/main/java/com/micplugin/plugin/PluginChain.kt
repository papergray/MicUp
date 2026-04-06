package com.micplugin.plugin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginChain @Inject constructor() {

    private val _slots = MutableStateFlow<List<PluginSlot>>(emptyList())
    val slots: StateFlow<List<PluginSlot>> = _slots

    fun addPlugin(slot: PluginSlot) {
        _slots.value = _slots.value + slot.copy(position = _slots.value.size)
    }

    fun removePlugin(id: UUID) {
        _slots.value = _slots.value
            .filter { it.id != id }
            .mapIndexed { i, s -> s.copy(position = i) }
    }

    fun toggleEnabled(id: UUID) {
        _slots.value = _slots.value.map {
            if (it.id == id) it.copy(enabled = !it.enabled) else it
        }
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        val list = _slots.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _slots.value = list.mapIndexed { i, s -> s.copy(position = i) }
    }

    fun updateParam(id: UUID, paramId: Int, value: Float) {
        _slots.value = _slots.value.map { slot ->
            if (slot.id == id)
                slot.copy(paramValues = slot.paramValues + (paramId to value))
            else slot
        }
    }

    fun clearAll() { _slots.value = emptyList() }

    fun getSlot(id: UUID) = _slots.value.find { it.id == id }
}
