package com.mymate.auto

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.graphics.Color

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(48, 48, 48, 48)
        }
        
        val title = TextView(this).apply {
            text = "MyMate"
            textSize = 32f
            setTextColor(Color.parseColor("#2196F3"))
            gravity = Gravity.CENTER
        }
        
        val subtitle = TextView(this).apply {
            text = "\nAndroid Auto Companion\n"
            textSize = 18f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }
        
        val instructions = TextView(this).apply {
            text = """
                |Deze app werkt in Android Auto.
                |
                |1. Verbind je telefoon met je auto
                |2. Open Android Auto
                |3. MyMate verschijnt in de app lijst
                |4. Tik om te chatten met je AI assistant
                |
                |Webhook: 100.124.24.27:18791
            """.trimMargin()
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }
        
        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(instructions)
        
        setContentView(layout)
    }
}
