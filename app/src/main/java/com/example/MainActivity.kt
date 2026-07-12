package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.AppRole
import com.example.ui.DashboardScreen
import com.example.ui.HostScreen
import com.example.ui.ClientScreen
import com.example.ui.SplashScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          val navController = rememberNavController()
          val viewModel: MainViewModel = viewModel()

          NavHost(
            navController = navController,
            startDestination = "splash"
          ) {
            composable("splash") {
              SplashScreen(
                onTimeout = {
                  navController.navigate("dashboard") {
                    popUpTo("splash") { inclusive = true }
                  }
                }
              )
            }
            composable("dashboard") {
              DashboardScreen(
                viewModel = viewModel,
                onNavigateToHost = { navController.navigate("host") },
                onNavigateToClient = { navController.navigate("client") }
              )
            }
            composable("host") {
              HostScreen(
                viewModel = viewModel,
                onNavigateBack = {
                  viewModel.selectRole(AppRole.NONE)
                  navController.popBackStack()
                }
              )
            }
            composable("client") {
              ClientScreen(
                viewModel = viewModel,
                onNavigateBack = {
                  viewModel.selectRole(AppRole.NONE)
                  navController.popBackStack()
                }
              )
            }
          }
        }
      }
    }
  }
}

