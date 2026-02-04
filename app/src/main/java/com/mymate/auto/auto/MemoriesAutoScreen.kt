package com.mymate.auto.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import com.mymate.auto.data.local.AppDatabase
import com.mymate.auto.data.model.Memory
import com.mymate.auto.data.model.MemoryCategory
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MemoriesAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    private val TAG = "MemoriesAutoScreen"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db = AppDatabase.getDatabase(carContext)
    private val memoryDao = db.memoryDao()
    private val dateFormat = SimpleDateFormat("dd MMM", Locale("nl", "NL"))
    
    @Volatile
    private var memories: List<Memory> = emptyList()
    
    @Volatile
    private var isLoading = true
    
    @Volatile
    private var selectedCategory: MemoryCategory? = null
    
    @Volatile
    private var categories: List<MemoryCategory> = emptyList()
    
    init {
        loadMemories()
    }
    
    private fun loadMemories() {
        scope.launch {
            try {
                memories = if (selectedCategory != null) {
                    memoryDao.getMemoriesByCategory(selectedCategory!!)
                } else {
                    memoryDao.getAllMemories()
                }
                categories = memoryDao.getAllCategories()
                isLoading = false
                withContext(Dispatchers.Main) {
                    invalidate()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading memories", e)
                isLoading = false
                withContext(Dispatchers.Main) {
                    invalidate()
                }
            }
        }
    }
    
    override fun onGetTemplate(): Template {
        if (isLoading) {
            return MessageTemplate.Builder("Memories laden...")
                .setTitle("📝 Memories")
                .setHeaderAction(Action.BACK)
                .build()
        }
        
        val listBuilder = ItemList.Builder()
        
        // Add new memory via voice
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🎤 Nieuwe memory toevoegen")
                .setOnClickListener {
                    screenManager.push(
                        VoiceInputScreen(carContext, "add_memory") { message ->
                            addMemory(message)
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
                    .setTitle("Geen memories gevonden")
                    .addText(if (selectedCategory != null) "Geen items in deze categorie" else "Voeg je eerste memory toe!")
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
                        .setOnClickListener {
                            screenManager.push(MemoryDetailAutoScreen(carContext, memory, memoryDao, scope) {
                                loadMemories()
                            })
                        }
                        .build()
                )
            }
            
            if (memories.size > 6) {
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("📋 ${memories.size - 6} meer memories...")
                        .addText("Bekijk in de phone app voor volledige lijst")
                        .build()
                )
            }
        }
        
        return ListTemplate.Builder()
            .setTitle("📝 Memories" + (selectedCategory?.let { " - ${it.name}" } ?: ""))
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
                        MessageScreen(carContext, "✅ Memory opgeslagen!", "Je memory is toegevoegd aan '${category.name}'") {
                            screenManager.popToRoot()
                            screenManager.push(MemoriesAutoScreen(carContext))
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding memory", e)
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
    
    override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
        scope.cancel()
        super.onDestroy(owner)
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
    private val scope: CoroutineScope,
    private val onDeleted: () -> Unit
) : Screen(carContext) {
    
    private val dateFormat = SimpleDateFormat("EEEE d MMMM HH:mm", Locale("nl", "NL"))
    
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
            memoryDao.deleteMemory(memory)
            withContext(Dispatchers.Main) {
                onDeleted()
                screenManager.pop()
            }
        }
    }
}

class MessageScreen(
    carContext: CarContext,
    private val title: String,
    private val message: String,
    private val onDismiss: () -> Unit
) : Screen(carContext) {
    
    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder(message)
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle("OK")
                    .setOnClickListener {
                        onDismiss()
                    }
                    .build()
            )
            .build()
    }
}
