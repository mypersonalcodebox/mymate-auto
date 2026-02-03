package com.mymate.auto

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.graphics.Color

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(64, 128, 64, 64)
        }
        
        val title = TextView(this).apply {
            text = "MyMate"
            textSize = 36f
            setTextColor(Color.parseColor("#2196F3"))
            gravity = Gravity.CENTER
        }
        
        val subtitle = TextView(this).apply {
            text = "\nAndroid Auto Companion\n\n"
            textSize = 20f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }
        
        val instructions = TextView(this).apply {
            text = "Deze app werkt in Android Auto.\n\n" +
                   "1. Verbind telefoon met auto\n" +
                   "2. Open Android Auto\n" +
                   "3. Zoek MyMate in de apps\n\n" +
                   "Webhook: 100.124.24.27:18791"
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
