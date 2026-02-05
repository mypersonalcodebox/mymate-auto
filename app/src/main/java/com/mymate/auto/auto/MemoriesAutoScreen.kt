package com.mymate.auto.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mymate.auto.data.local.AppDatabase
import com.mymate.auto.data.model.Memory
import com.mymate.auto.data.model.MemoryCategory
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MemoriesAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    private val TAG = "MemoriesAutoScreen"
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private val db = AppDatabase.getInstance(carContext)
    private val memoryDao = db.memoryDao()
    private val dateFormat = SimpleDateFormat("dd MMM", Locale("nl", "NL"))
    
    @Volatile
    private var memories: List<Memory> = emptyList()
    
    @Volatile
    private var isLoading = true
    
    @Volatile
    private var loadError: String? = null
    
    @Volatile
    private var selectedCategory: MemoryCategory? = null
    
    @Volatile
    private var categories: List<MemoryCategory> = emptyList()
    
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                supervisorJob.cancel()
            }
        })
        loadMemories()
    }
    
    private fun loadMemories() {
        scope.launch {
            try {
                loadError = null
                memories = if (selectedCategory != null) {
                    memoryDao.getMemoriesByCategory(selectedCategory!!)
                } else {
                    memoryDao.getAllMemoriesSync()
                }
                categories = memoryDao.getAllCategories()
                isLoading = false
                withContext(Dispatchers.Main) {
                    invalidate()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading memories", e)
                loadError = "Kon notities niet laden"
                isLoading = false
                withContext(Dispatchers.Main) {
                    invalidate()
                }
            }
        }
    }
    
    override fun onGetTemplate(): Template {
        if (isLoading) {
            return MessageTemplate.Builder("Notities laden...")
                .setTitle("📝 Notities")
                .setHeaderAction(Action.BACK)
                .build()
        }
        
        if (loadError != null) {
            return MessageTemplate.Builder(loadError!!)
                .setTitle("❌ Fout")
                .setHeaderAction(Action.BACK)
                .addAction(
                    Action.Builder()
                        .setTitle("🔄 Opnieuw")
                        .setOnClickListener {
                            isLoading = true
                            loadError = null
                            invalidate()
                            loadMemories()
                        }
                        .build()
                )
                .build()
        }
        
        val listBuilder = ItemList.Builder()
        
        // Add new memory via voice
        listBuilder.addItem(
            Row.Builder()
                .setTitle("➕ Nieuwe notitie")
                .addText("Tik om een notitie toe te voegen")
                .setOnClickListener {
                    screenManager.push(
                        VoiceInputScreen(carContext, "add_memory") { text ->
                            addMemory(text)
                        }
                    )
                }
                .build()
        )
        
        // Category filter
        if (categories.isNotEmpty()) {
            val categoryText = selectedCategory?.name ?: "Alle categorieën"
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("📁 Filter: $categoryText")
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(MemoryCategoryFilterScreen(carContext, categories) { category ->
                            selectedCategory = category
                            isLoading = true
                            invalidate()
                            loadMemories()
                        })
                    }
                    .build()
            )
        }
        
        // Show memories
        if (memories.isEmpty()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Geen notities")
                    .addText(if (selectedCategory != null) "Geen items in deze categorie" else "Tik hierboven om te beginnen")
                    .build()
            )
        } else {
            memories.take(6).forEach { memory ->
                val emoji = getCategoryEmoji(memory.category)
                val preview = memory.content.take(50).replace("\n", " ")
                val date = dateFormat.format(Date(memory.createdAt))
                
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("$emoji $preview")
                        .addText("$date • ${memory.category.name}")
                        .setBrowsable(true)
                        .setOnClickListener {
                            screenManager.push(MemoryDetailAutoScreen(carContext, memory, memoryDao) {
                                loadMemories()
                            })
                        }
                        .build()
                )
            }
            
            if (memories.size > 6) {
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("📋 ${memories.size - 6} meer notities...")
                        .addText("Bekijk in de app voor de volledige lijst")
                        .build()
                )
            }
        }
        
        return ListTemplate.Builder()
            .setTitle("📝 Notities" + (selectedCategory?.let { " - ${it.name}" } ?: ""))
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
    
    private fun addMemory(content: String) {
        scope.launch {
            try {
                // Detect category from content
                val category = detectCategory(content)
                
                val memory = Memory(
                    content = content,
                    category = category,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                
                memoryDao.insertMemory(memory)
                loadMemories()
                
                withContext(Dispatchers.Main) {
                    screenManager.push(
                        MessageScreen(carContext, "✅ Opgeslagen!", "Notitie toegevoegd aan '${category.name}'") {
                            screenManager.popToRoot()
                            screenManager.push(MemoriesAutoScreen(carContext))
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding memory", e)
                withContext(Dispatchers.Main) {
                    screenManager.push(
                        MessageScreen(carContext, "❌ Fout", "Kon notitie niet opslaan") {
                            screenManager.pop()
                        }
                    )
                }
            }
        }
    }
    
    private fun detectCategory(content: String): MemoryCategory {
        val lower = content.lowercase()
        return when {
            lower.contains("todo") || lower.contains("moet") || lower.contains("doen") || lower.contains("vergeet niet") -> MemoryCategory.TODO
            lower.contains("werk") || lower.contains("kantoor") || lower.contains("collega") || lower.contains("project") -> MemoryCategory.WORK
            lower.contains("adres") || lower.contains("straat") || lower.contains("woon") || lower.contains("locatie") -> MemoryCategory.LOCATION
            lower.contains("telefoon") || lower.contains("nummer") || lower.contains("email") || lower.contains("contact") -> MemoryCategory.CONTACT
            lower.contains("favoriet") || lower.contains("lekker") || lower.contains("hou van") || lower.contains("voorkeur") -> MemoryCategory.PERSONAL
            else -> MemoryCategory.GENERAL
        }
    }
    
    private fun getCategoryEmoji(category: MemoryCategory): String {
        return when (category) {
            MemoryCategory.GENERAL -> "📝"
            MemoryCategory.PERSONAL -> "❤️"
            MemoryCategory.WORK -> "💼"
            MemoryCategory.LOCATION -> "📍"
            MemoryCategory.CONTACT -> "👤"
            MemoryCategory.TODO -> "✅"
        }
    }
}

class MemoryCategoryFilterScreen(
    carContext: CarContext,
    private val categories: List<MemoryCategory>,
    private val onCategorySelected: (MemoryCategory?) -> Unit
) : Screen(carContext) {
    
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        // All categories option
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📋 Alle categorieën")
                .setOnClickListener {
                    onCategorySelected(null)
                    screenManager.pop()
                }
                .build()
        )
        
        // Individual categories
        MemoryCategory.values().forEach { category ->
            val emoji = when (category) {
                MemoryCategory.GENERAL -> "📝"
                MemoryCategory.PERSONAL -> "❤️"
                MemoryCategory.WORK -> "💼"
                MemoryCategory.LOCATION -> "📍"
                MemoryCategory.CONTACT -> "👤"
                MemoryCategory.TODO -> "✅"
            }
            
            val inList = categories.contains(category)
            
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("$emoji ${category.name}" + if (!inList) " (leeg)" else "")
                    .setOnClickListener {
                        onCategorySelected(category)
                        screenManager.pop()
                    }
                    .build()
            )
        }
        
        return ListTemplate.Builder()
            .setTitle("📁 Categorie kiezen")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}

class MemoryDetailAutoScreen(
    carContext: CarContext,
    private val memory: Memory,
    private val memoryDao: com.mymate.auto.data.local.MemoryDao,
    private val onDeleted: () -> Unit
) : Screen(carContext) {
    
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private val dateFormat = SimpleDateFormat("EEEE d MMMM HH:mm", Locale("nl", "NL"))
    
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                supervisorJob.cancel()
            }
        })
    }
    
    override fun onGetTemplate(): Template {
        val preview = memory.content.take(30)
        val date = dateFormat.format(Date(memory.createdAt))
        
        val paneBuilder = Pane.Builder()
        
        // Content
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("📝 Inhoud")
                .addText(memory.content.take(200))
                .build()
        )
        
        // Category
        val categoryEmoji = when (memory.category) {
            MemoryCategory.GENERAL -> "📝"
            MemoryCategory.PERSONAL -> "❤️"
            MemoryCategory.WORK -> "💼"
            MemoryCategory.LOCATION -> "📍"
            MemoryCategory.CONTACT -> "👤"
            MemoryCategory.TODO -> "✅"
        }
        
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("📁 Categorie")
                .addText("$categoryEmoji ${memory.category.name}")
                .build()
        )
        
        // Date
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("📅 Aangemaakt")
                .addText(date)
                .build()
        )
        
        // Tags if any
        memory.tags?.let { tags ->
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("🏷️ Tags")
                    .addText(tags)
                    .build()
            )
        }
        
        // Edit button
        paneBuilder.addAction(
            Action.Builder()
                .setTitle("✏️ Bewerk")
                .setOnClickListener {
                    screenManager.push(
                        VoiceInputScreen(carContext, "edit_memory") { newContent ->
                            updateMemory(newContent)
                        }
                    )
                }
                .build()
        )
        
        // Delete button
        paneBuilder.addAction(
            Action.Builder()
                .setTitle("🗑️ Verwijder")
                .setOnClickListener {
                    deleteMemory()
                }
                .build()
        )
        
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle(preview)
            .setHeaderAction(Action.BACK)
            .build()
    }
    
    private fun deleteMemory() {
        scope.launch {
            try {
                memoryDao.deleteMemory(memory)
                withContext(Dispatchers.Main) {
                    onDeleted()
                    screenManager.pop()
                }
            } catch (e: Exception) {
                Log.e("MemoryDetailAutoScreen", "Error deleting memory", e)
                withContext(Dispatchers.Main) {
                    screenManager.push(
                        MessageScreen(carContext, "❌ Fout", "Kon notitie niet verwijderen") {
                            screenManager.pop()
                        }
                    )
                }
            }
        }
    }
    
    private fun updateMemory(newContent: String) {
        scope.launch {
            try {
                val updatedMemory = memory.copy(
                    content = newContent,
                    updatedAt = System.currentTimeMillis()
                )
                memoryDao.update(updatedMemory)
                withContext(Dispatchers.Main) {
                    onDeleted() // Refresh the list
                    screenManager.pop()
                }
            } catch (e: Exception) {
                Log.e("MemoryDetailAutoScreen", "Error updating memory", e)
                withContext(Dispatchers.Main) {
                    screenManager.push(
                        MessageScreen(carContext, "❌ Fout", "Kon notitie niet bijwerken") {
                            screenManager.pop()
                        }
                    )
                }
            }
        }
    }
}
