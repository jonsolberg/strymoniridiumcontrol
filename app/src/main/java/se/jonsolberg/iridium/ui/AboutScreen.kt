// Iridium Control – Copyright (C) 2026 Jon Solberg – GPL v3 or later, see LICENSE.
package se.jonsolberg.iridium.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val APP = "Iridium Control"
private const val REPO = "https://github.com/jonsolberg/strymoniridiumcontrol"
private const val SITE = "https://jonsolberg.se:8080/"
private const val KOFI = "https://ko-fi.com/jonsolberg"

/** Help, copyright and links – the About tab. */
@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }
    fun open(url: String) { runCatching { uri.openUri(url) } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(APP, style = MaterialTheme.typography.headlineSmall)
        Text("Version $version · an editor and preset library for the Strymon Iridium", fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        AboutCard("How to use it") {
            Bullet("Connect the Iridium to the phone with a USB cable (and a USB-C OTG adapter), or pair a Bluetooth LE MIDI adapter. Then pick it under Settings → MIDI connection.")
            Bullet("Pedal: turn the knobs and flip the switches on screen – the pedal follows. Drag a knob up or right, double-tap to centre it.")
            Bullet("Live edit (on the Pedal tab): room size, cab, amp bypass and volume pedal position.")
            Bullet("Presets: 300 slots. Green = stored in the pedal, amber = only in the app. Tap a preset to load it, long-press to select several.")
            Bullet("Settings: MIDI channel (must match the pedal), backup to a JSON file, a connection test and a MIDI monitor.")
            Bullet("To let the app follow the real knobs, set the pedal's MIDI Output to ON – see the README on GitHub.")
        }

        AboutCard("About") {
            Text("© 2026 Jon Solberg", fontSize = 14.sp)
            Hint2("Free and open source under the GNU General Public License v3. No ads, no tracking.")
            Hint2("$APP is an independent app and is not affiliated with Strymon.")
            OutlinedButton(onClick = { open(SITE) }, modifier = Modifier.fillMaxWidth()) { Text("jonsolberg.se – more apps and games") }
            OutlinedButton(onClick = { open(REPO) }, modifier = Modifier.fillMaxWidth()) { Text("Source code and help on GitHub") }
        }

        AboutCard("Support") {
            Hint2("If $APP is useful to you, you can support me.")
            Button(onClick = { open(KOFI) }, modifier = Modifier.fillMaxWidth()) { Text("❤️ Support me on Ko-fi") }
        }
    }
}

@Composable
private fun AboutCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text("•  $text", fontSize = 13.sp)
}

@Composable
private fun Hint2(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
