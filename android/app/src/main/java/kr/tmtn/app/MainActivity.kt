package kr.tmtn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.tmtn.app.designsystem.TmtnTheme
import kr.tmtn.app.ui.TodayViewModel
import kr.tmtn.app.ui.nav.TmtnApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TmtnApplication).container
        setContent {
            TmtnTheme {
                val today: TodayViewModel = viewModel(
                    factory = viewModelFactory { initializer { TodayViewModel(container) } },
                )
                TmtnApp(today)
            }
        }
    }
}
