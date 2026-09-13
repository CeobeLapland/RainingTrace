package com.rainingtrace.feature.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.domain.memory.MemoryNode
import com.rainingtrace.domain.memory.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class JournalViewModel(
    private val memoryRepository: MemoryRepository,
) : ViewModel() {

    private val _memories = MutableStateFlow<List<MemoryNode>>(emptyList())
    val memories: StateFlow<List<MemoryNode>> = _memories.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _memories.value = memoryRepository.latest(limit = 100)
        }
    }
}
