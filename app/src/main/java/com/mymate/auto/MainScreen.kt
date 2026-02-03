package com.mymate.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import java.io.IOException

class MainScreen(carContext: CarContext) : Screen(carContext) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private var lastResponse = "Hoi! Ik ben MyMate. Kies een optie of stel een vraag."

    private val quickActions = listOf(
        "📅 Agenda vandaag" to "Wat staat er vandaag op mijn agenda?",
        "📧 Ongelezen mail" to "Heb ik belangrijke ongelezen emails?",
        "☀️ Weer" to "Wat is het weer vandaag?",
        "🏠 Onderweg naar huis" to "Ik ben onderweg naar huis",
        "💬 Vrije vraag" to ""
    )

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        quickActions.forEach { (title, query) ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(title)
                    .setOnClickListener { 
                        if (query.isNotEmpty()) {
                            sendToMyMate(query)
                        }
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle("MyMate")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }

    private fun sendToMyMate(message: String) {
        val json = gson.toJson(mapOf("message" to message))
        val body = json.toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("http://100.124.24.27:18791/auto")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                lastResponse = "Kon geen verbinding maken met MyMate"
                invalidate()
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    val result = gson.fromJson(responseBody, Map::class.java)
                    lastResponse = result["reply"]?.toString() ?: "Geen antwoord"
                    invalidate()
                }
            }
        })
    }
}
