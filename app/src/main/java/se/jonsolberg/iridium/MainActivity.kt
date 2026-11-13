package se.jonsolberg.iridium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import se.jonsolberg.iridium.ui.IridiumTheme
import se.jonsolberg.iridium.ui.PedalScreen
import se.jonsolberg.iridium.ui.PresetsScreen
import se.jonsolberg.iridium.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IridiumTheme {
                App(viewModel())
            }
        }
    }
}

@Composable
private fun App(vm: IridiumViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Home, null) }, label = { Text("Pedal") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("Presets") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> PedalScreen(vm, onOpenSettings = { tab = 2 })
                1 -> PresetsScreen(vm, onOpenPreset = { tab = 0 })
                else -> SettingsScreen(vm)
            }
        }
    }

    vm.busy?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Talking to the pedal") },
            text = {
                Column {
                    Text(msg)
                    LinearProgressIndicator(Modifier.padding(top = 16.dp))
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::cancelSave) { Text("Cancel") } },
        )
    }
}
