package se.jonsolberg.iridium.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.jonsolberg.iridium.IridiumViewModel
import se.jonsolberg.iridium.midi.MidiController
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: IridiumViewModel) {
    val devices by vm.midi.devices.collectAsState()
    val connected by vm.midi.connected.collectAsState()
    val bleFound by vm.ble.found.collectAsState()
    val bleScanning by vm.ble.scanning.collectAsState()
    val bleError by vm.ble.error.collectAsState()
    val context = LocalContext.current
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    val backupOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(vm.exportBackup().toByteArray()) }
        }.onSuccess { toast("Backup saved") }.onFailure { toast("Backup failed: ${it.message}") }
    }
    val backupIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            vm.importBackup(text)
        }.onSuccess { toast("Restored $it presets" + if (vm.favorite != null) " and the FAV sound" else "") }
            .onFailure { toast("Restore failed: ${it.message}") }
    }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) vm.ble.start()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("MIDI connection") {
            Text(
                connected?.let { "Connected to $it" } ?: "Not connected",
                fontWeight = FontWeight.SemiBold,
                color = if (connected != null) SyncGreen else MaterialTheme.colorScheme.error,
            )
            if (!vm.midi.isSupported) Text("This phone does not support Android MIDI.", color = MaterialTheme.colorScheme.error)
            Hint(
                "USB: connect the Iridium's USB port to the phone with a USB-C OTG adapter or cable " +
                    "(the pedal still needs its own power). It shows up below and is connected automatically. " +
                    "TRS MIDI: use any class-compliant USB or Bluetooth MIDI interface into the EXP/MIDI jack."
            )
            if (devices.isEmpty()) Hint("No MIDI devices found.")
            devices.forEach { info ->
                val name = MidiController.nameOf(info)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name)
                        Text(MidiController.transportOf(info), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (connected == name) {
                        OutlinedButton(onClick = vm::disconnect) { Text("Disconnect") }
                    } else {
                        Button(onClick = { vm.connect(info) }) { Text("Connect") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.midi.refresh() }) { Text("Refresh") }
                OutlinedButton(onClick = { if (bleScanning) vm.ble.stop() else permLauncher.launch(permissions) }) {
                    Text(if (bleScanning) "Stop scan" else "Scan Bluetooth MIDI")
                }
            }
            bleError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            bleFound.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name)
                        Text("Bluetooth LE MIDI", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { vm.connectBle(d) }) { Text("Connect") }
                }
            }
        }

        Section("Pedal") {
            Text("MIDI channel", fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.updateChannel(vm.midiChannel - 1) }, enabled = vm.midiChannel > 0) { Text("−") }
                Text("${vm.midiChannel + 1}", Modifier.width(56.dp).padding(horizontal = 8.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { vm.updateChannel(vm.midiChannel + 1) }, enabled = vm.midiChannel < 15) { Text("+") }
            }
            Hint("Must match the channel set on the Iridium (factory default is 1).")
            SwitchRow("Number presets from 1 (instead of 0)", vm.oneBased, vm::updateOneBased)
            SwitchRow("Follow the pedal's knobs and switches", vm.followPedal, vm::updateFollowPedal)
            Hint(
                "For this the pedal's MIDI Output must be ON (green), it is OFF by default: unplug USB, " +
                    "hold both footswitches while connecting power, release when the LEDs stop blinking, " +
                    "turn LEVEL until the ON LED is green, then press ON. Check the MIDI monitor below for ← lines."
            )
        }

        Section("Saving to the pedal") {
            Hint(
                "The app recalls the target slot, sends every setting, then holds the FAV switch " +
                    "over MIDI so the pedal enters save mode, and confirms with:"
            )
            RadioRow("Program Change (firmware 1.33 or newer)", vm.saveViaProgramChange) { vm.updateSaveViaPc(true) }
            RadioRow("Second FAV press (older firmware)", !vm.saveViaProgramChange) { vm.updateSaveViaPc(false) }
            Text("FAV hold time: ${vm.favHoldMs} ms", fontSize = 13.sp)
            Slider(
                value = vm.favHoldMs.toFloat(),
                onValueChange = { vm.updateFavHold((it / 100f).roundToInt() * 100) },
                valueRange = 1000f..4000f,
            )
        }

        Section("Backup (JSON)") {
            Hint(
                "Saves every preset, the FAV sound and these settings in one .json file. " +
                    "Restoring replaces the app's library with the file's contents."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { backupOut.launch("iridium-backup.json") }) { Text("Export") }
                OutlinedButton(onClick = { backupIn.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import") }
            }
        }

        Section("Connection test") {
            Hint("Each press should toggle the matching footswitch on the pedal.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::testOnSwitch, enabled = connected != null) { Text("Press ON") }
                OutlinedButton(onClick = vm::tapFav, enabled = connected != null) { Text("Press FAV") }
                OutlinedButton(onClick = vm::sendAll, enabled = connected != null) { Text("Send all") }
            }
        }

        Section("MIDI monitor") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = vm.logOutgoing, onClick = { vm.logOutgoing = !vm.logOutgoing }, label = { Text("Show outgoing") })
                Spacer(Modifier.weight(1f))
                TextButton(onClick = vm::clearLog) { Text("Clear") }
            }
            if (vm.log.isEmpty()) Hint("Nothing yet.")
            vm.log.take(80).forEach {
                Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }

        Hint("Iridium Control is an independent app and is not affiliated with Strymon.")
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, fontSize = 14.sp)
    }
}
