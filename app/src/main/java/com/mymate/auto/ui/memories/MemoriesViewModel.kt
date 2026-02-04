package com.mymate.auto.ui.memories

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mymate.auto.data.local.AppDatabase
import com.mymate.auto.data.model.Memory
import com.mymate.auto.data.model.MemoryCategory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MemoriesViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getInstance(application)
    private val memoryDao = database.memoryDao()
    
    val allMemories: Flow<List<Memory>> = memoryDao.getAllMemories()
    val todos: Flow<List<Memory>> = memoryDao.getTodos()
    
    private val _uiState = MutableStateFlow(MemoriesUiState())
    val uiState: StateFlow<MemoriesUiState> = _uiState.asStateFlow()
    
    private val _selectedCategory = MutableStateFlow<MemoryCategory?>(null)
    val selectedCategory: StateFlow<MemoryCategory?> = _selectedCategory.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    val filteredMemories: Flow<List<Memory>> = combine(
        allMemories, _selectedCategory, _searchQuery
    ) { memories, category, query ->
        memories.filter { memory ->
            val matchesCategory = category == null || memory.category == category
            val matchesQuery = query.isBlank() || 
                memory.content.contains(query, ignoreCase = true) ||
                memory.tags?.contains(query, ignoreCase = true) == true
            matchesCategory && matchesQuery
        }
    }
    
    /**
     * Add a new memory
     */
    fun addMemory(content: String, category: MemoryCategory = MemoryCategory.GENERAL, tags: String? = null) {
        if (content.isBlank()) return
        
        viewModelScope.launch {
            val memory = Memory(
                content = content,
                category = category,
                tags = tags,
                source = "app"
            )
            memoryDao.insert(memory)
            _uiState.update { it.copy(message = "Opgeslagen!") }
        }
    }
    
    /**
     * Add a to-do item
     */
    fun addTodo(content: String) {
        addMemory(content, MemoryCategory.TODO)
    }
    
    /**
     * Update a memory
     */
    fun updateMemory(memory: Memory) {
        viewModelScope.launch {
            memoryDao.update(memory.copy(updatedAt = System.currentTimeMillis()))
        }
    }
    
    /**
     * Delete a memory
     */
    fun deleteMemory(memory: Memory) {
        viewModelScope.launch {
            memoryDao.delete(memory)
            _uiState.update { it.copy(message = "Verwijderd") }
        }
    }
    
    /**
     * Set category filter
     */
    fun setCategory(category: MemoryCategory?) {
        _selectedCategory.value = category
    }
    
    /**
     * Set search query
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }
}

data class MemoriesUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null
)
