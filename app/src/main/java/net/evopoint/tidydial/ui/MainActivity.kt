package net.evopoint.tidydial.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import net.evopoint.tidydial.ui.theme.TidyDialTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Keep the splash until the first permission check (on resume) has decided the route.
        splash.setKeepOnScreenCondition {
            viewModel.state.value.permission == PermissionStatus.Unknown
        }
        setContent {
            TidyDialTheme {
                TidyDialApp(viewModel)
            }
        }
    }
}
