package com.example.gemmaai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.gemmaai.ui.ChatScreen
import com.example.gemmaai.ui.theme.DarkBackground
import com.example.gemmaai.ui.theme.GemmaAITheme
import com.example.gemmaai.viewmodel.RemoteViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: RemoteViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RemoteViewModel(applicationContext) as T
            }
        }
        
        viewModel = ViewModelProvider(this, factory)[RemoteViewModel::class.java]

        setContent {
            GemmaAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    ChatScreen(
                        viewModel = viewModel,
                        onVoiceClick = {},
                        isListening = false
                    )
                }
            }
        }
    }
}
