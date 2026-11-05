package com.dompetku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dompetku.ui.DompetViewModel
import com.dompetku.ui.HomeScreen
import com.dompetku.ui.theme.DompetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DompetTheme {
                val vm: DompetViewModel = viewModel(
                    factory = DompetViewModel.factory(application)
                )
                HomeScreen(vm)
            }
        }
    }
}
