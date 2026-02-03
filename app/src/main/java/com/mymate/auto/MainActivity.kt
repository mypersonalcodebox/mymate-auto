package com.mymate.auto

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = "MyMate Android Auto\n\nOpen Android Auto om te gebruiken.\n\nWebhook: 100.124.24.27:18791"
            textSize = 18f
            setPadding(48, 48, 48, 48)
        }
        setContentView(tv)
    }
}
