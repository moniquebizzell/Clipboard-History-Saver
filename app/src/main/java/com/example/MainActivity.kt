package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ClipboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ClipboardViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: ClipboardViewModel = viewModel()
                Surface(modifier = Modifier.fillMaxSize()) {
                    ClipboardScreen(viewModel = viewModel)
                }
            }
        }
    }
}
