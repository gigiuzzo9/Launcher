package com.simplylauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apri direttamente la HomeActivity
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
