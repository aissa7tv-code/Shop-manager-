package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ai.configureAppCheck
import com.example.data.db.ShopDatabase
import com.example.ui.AiViewModel
import com.example.ui.ShopViewModel
import com.example.ui.SmartShopApp
import com.example.ui.theme.SmartShopTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val database = ShopDatabase.getInstance(applicationContext)
        setContent {
            SmartShopTheme {
                val shopVm: ShopViewModel = viewModel(factory = ShopViewModel.Factory(database))
                val aiVm: AiViewModel = viewModel()
                SmartShopApp(viewModel = shopVm, aiViewModel = aiVm)
            }
        }
    }
}
