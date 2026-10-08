package com.wodrol.brakoff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wodrol.brakoff.ui.screens.ActiveDeliveriesScreen
import com.wodrol.brakoff.ui.screens.HomeScreen
import com.wodrol.brakoff.ui.screens.ProductDetailsScreen
import com.wodrol.brakoff.ui.screens.QrConfigScannerScreen
import com.wodrol.brakoff.ui.screens.ScannerScreen
import com.wodrol.brakoff.ui.screens.SettingsScreen
import com.wodrol.brakoff.ui.screens.VerificationScreen
import com.wodrol.brakoff.ui.theme.BrakOffTheme
import com.wodrol.brakoff.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as BrakOffApp
        viewModel = MainViewModel(
            repository = app.repository,
            preferencesManager = app.preferencesManager,
            updateRepository = app.updateRepository,
            updateManager = app.updateManager
        )
        
        enableEdgeToEdge()
        setContent {
            BrakOffTheme {
                BrakOffNavigation(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
}

@Composable
fun BrakOffNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onScanClick = { navController.navigate("scanner") },
                onVerifyClick = { navController.navigate("verification") },
                onSettingsClick = { navController.navigate("settings") },
                onDeliveriesClick = { navController.navigate("deliveries") },
                onNavigateToDetails = { barcode -> navController.navigate("details/$barcode") }
            )
        }
        composable("deliveries") {
            ActiveDeliveriesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("scanner") {
            ScannerScreen(
                viewModel = viewModel,
                onBarcodeScanned = { barcode ->
                    navController.navigate("details/$barcode") {
                        popUpTo("home")
                    }
                }
            )
        }
        composable("details/{barcode}") { backStackEntry ->
            val barcode = backStackEntry.arguments?.getString("barcode") ?: ""
            ProductDetailsScreen(
                barcode = barcode,
                viewModel = viewModel,
                onBack = { navController.popBackStack("home", false) }
            )
        }
        composable("verification") {
            VerificationScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenQrScanner = { navController.navigate("settings/qr-scanner") },
                onNavigateToDeliveries = { navController.navigate("deliveries") }
            )
        }
        composable("settings/qr-scanner") {
            QrConfigScannerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
