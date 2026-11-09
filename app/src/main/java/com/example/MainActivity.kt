package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.MainStudioApp
import com.example.viewmodel.MainStudioViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainStudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainStudioApp(viewModel = viewModel)
        }
    }
}
