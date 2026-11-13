package se.jonsolberg.iridium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.jonsolberg.iridium.IridiumViewModel
import se.jonsolberg.iridium.model.Cc
import se.jonsolberg.iridium.model.Iridium
import se.jonsolberg.iridium.model.SyncState

@Composable
fun PedalScreen(vm: IridiumViewModel, onOpenSettings: () -> Unit) {
    val connected by vm.midi.connected.collectAsState()
    var showStore by rememberSaveable { mutableStateOf(false) }
    var showFav by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        val knob = when {
            wide -> minOf(84.dp, maxHeight / 5.5f)
            maxWidth < 360.dp -> 58.dp
            else -> 68.dp
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1.1f).verticalScroll(rememberScrollState())) {
                    PedalFace(vm, knob, onFav = { showFav = true })
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConnectionBanner(connected, onOpenSettings)
                    PresetBar(vm, onStore = { showStore = true })
                    LiveEditPanel(vm)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConnectionBanner(connected, onOpenSettings)
                PresetBar(vm, onStore = { showStore = true })
                PedalFace(vm, knob, onFav = { showFav = true })
                LiveEditPanel(vm)
            }
        }
    }

    if (showStore) {
        StoreDialog(vm, connected != null, onDismiss = { showStore = false })
    }
    if (showFav) {
        FavDialog(vm, connected != null, onDismiss = { showFav = false })
    }
}

@Composable
private fun ConnectionBanner(connected: String?, onOpenSettings: () -> Unit) {
    if (connected != null) return // status lives in Settings; keep the pedal on screen
    val bg = if (connected == null) Color(0xFF5A2A22) else Color(0xFF1F3A27)
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .clickable(onClick = onOpenSettings)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Led(if (connected == null) LedRed else LedGreen, on = true, size = 14.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            if (connected == null) "Not connected to pedal — tap to choose a MIDI device"
            else "Connected: $connected",
            fontSize = 13.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PedalFace(vm: IridiumViewModel, knob: Dp, onFav: () -> Unit) {
    val s = vm.sound
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(14.dp, shape)
            .brushedMetal(22.dp)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top: DRIVE, the two mini toggles, LEVEL
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            PedalKnob("DRIVE", s.drive, { vm.setValue(Cc.DRIVE, it) }, Modifier.weight(1f), knob)
            ThreeWayToggle(
                "AMP", Iridium.AMPS, s.amp - 1, { vm.setAmp(it + 1) },
                Modifier.weight(1f).padding(top = 2.dp), labelsLeft = true, height = maxOf(knob, 60.dp),
            )
            ThreeWayToggle(
                "CAB", Iridium.CAB_LETTERS, s.cabLetter, { vm.setCabLetter(it) },
                Modifier.weight(1f).padding(top = 2.dp), labelsLeft = false, height = maxOf(knob, 60.dp),
            )
            PedalKnob("LEVEL", s.level, { vm.setValue(Cc.LEVEL, it) }, Modifier.weight(1f), knob)
        }
        // MIDDLE and TREBLE sit between the outer rows; BASS and ROOM a little lower.
        Row(Modifier.fillMaxWidth().offset(y = -knob * 0.12f), verticalAlignment = Alignment.Top) {
            val drop = knob * 0.45f
            PedalKnob("BASS", s.bass, { vm.setValue(Cc.BASS, it) }, Modifier.weight(1f).padding(top = drop), knob)
            PedalKnob("MIDDLE", s.middle, { vm.setValue(Cc.MIDDLE, it) }, Modifier.weight(1f), knob)
            PedalKnob("TREBLE", s.treble, { vm.setValue(Cc.TREBLE, it) }, Modifier.weight(1f), knob)
            PedalKnob("ROOM", s.room, { vm.setValue(Cc.ROOM, it) }, Modifier.weight(1f).padding(top = drop), knob)
        }
        Spacer(Modifier.height(4.dp))
        // Footswitches under the outer columns, the model name between them.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Footswitch(
                    "FAV", LedRed, vm.favLit,
                    onTap = vm::tapFav,
                    onLongPress = onFav,
                    switchSize = knob * 0.95f,
                    gap = knob * 0.45f,
                )
            }
            Column(Modifier.weight(2f).padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(18.dp).height(1.dp).background(FaceText))
                    Text(" amp & ir cab ", color = FaceText, fontSize = 13.sp)
                    Box(Modifier.width(18.dp).height(1.dp).background(FaceText))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (s.ampDisabled) "amp off · ${Iridium.cabName(s.cab)}" else "${Iridium.AMPS[s.amp - 1]} · ${Iridium.cabName(s.cab)}",
                    color = FaceTextDim, fontSize = 11.sp, letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(knob * 0.5f))
                Text("hold FAV for the favorite", color = FaceTextDim.copy(alpha = 0.6f), fontSize = 10.sp)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Footswitch("ON", LedRed, vm.engaged, onTap = vm::toggleOn, switchSize = knob * 0.95f, gap = knob * 0.45f)
            }
        }
    }
}

@Composable
private fun PresetBar(vm: IridiumViewModel, onStore: () -> Unit) {
    val p = vm.currentPreset
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.step(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous preset")
                }
                Box(
                    Modifier
                        .background(Color(0xFF0B0B0C), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF333336), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "%03d".format(vm.displayNumber(vm.current)),
                        color = LedRed, fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = { vm.step(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next preset")
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        p.name.ifEmpty { if (p.sound == null) "Empty slot" else "Untitled" },
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val (txt, col) = when {
                        vm.isEdited -> "Modified" to SyncAmber
                        p.sync == SyncState.SYNCED -> "On pedal" to SyncGreen
                        p.sync == SyncState.EDITED -> "Not on pedal yet" to SyncAmber
                        else -> "Empty" to Color.Gray
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(col, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(6.dp))
                        Text(txt, fontSize = 12.sp, color = col)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStore, modifier = Modifier.weight(1f)) { Text("Store") }
                if (vm.isEdited) {
                    OutlinedButton(onClick = vm::revert, modifier = Modifier.weight(1f)) { Text("Revert") }
                }
                OutlinedButton(onClick = vm::sendAll, modifier = Modifier.weight(1f)) { Text("Send all") }
            }
        }
    }
}

@Composable
private fun LiveEditPanel(vm: IridiumViewModel) {
    val s = vm.sound
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Live edit", style = MaterialTheme.typography.titleSmall)
            ChoiceRow("Room size", listOf("Small", "Medium", "Large"), s.roomSize - 1) { vm.setRoomSize(it + 1) }
            ChoiceRow("Cab set", listOf("Round", "Chime", "Punch"), s.cabFamily) { vm.setCabFamily(it) }
            Text(
                "Cab ${Iridium.cabName(s.cab)}: ${Iridium.CAB_DESCRIPTIONS[s.cab]}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChoiceRow("Amp", listOf("On", "Bypassed"), if (s.ampDisabled) 1 else 0) { vm.setAmpDisabled(it == 1) }
            ChoiceRow("Volume pedal", listOf("Pre", "Post"), if (s.volumePost) 1 else 0) { vm.setVolumePost(it == 1) }
            Text(
                "${Iridium.AMPS[s.amp - 1].replaceFirstChar { it.uppercase() }} amp: ${Iridium.AMP_DESCRIPTIONS[s.amp - 1]}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ChoiceRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, o ->
                FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(o, fontSize = 12.sp) })
            }
        }
    }
}

@Composable
private fun StoreDialog(vm: IridiumViewModel, connected: Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(vm.currentPreset.name) }
    var toPedal by remember { mutableStateOf(connected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Store preset ${vm.displayNumber(vm.current)}") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = toPedal, onCheckedChange = { toPedal = it }, enabled = connected)
                    Text(if (connected) "Also save to the pedal" else "Connect a MIDI device to save to the pedal", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.storeCurrent(name, toPedal && connected)
                onDismiss()
            }) { Text("Store") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FavDialog(vm: IridiumViewModel, connected: Boolean, onDismiss: () -> Unit) {
    val fav = vm.favorite
    var name by remember { mutableStateOf(fav?.name ?: "") }
    var toPedal by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("FAV sound") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (fav == null) "No FAV sound stored in the app yet."
                    else "Stored: ${fav.name.ifEmpty { "Untitled" }}\n${fav.sound.summary()}",
                    fontSize = 13.sp,
                )
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = toPedal && connected, onCheckedChange = { toPedal = it }, enabled = connected)
                    Text(
                        if (connected) "Also write it to the pedal's FAV (experimental)" else "Connect to write it to the pedal",
                        fontSize = 13.sp,
                    )
                }
                Text(
                    "Stores the current knob settings as the favorite. It is included in the JSON backup (Settings).",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.storeFavorite(name, toPedal && connected)
                onDismiss()
            }) { Text("Store current as FAV") }
        },
        dismissButton = {
            Row {
                if (fav != null) {
                    TextButton(onClick = {
                        vm.recallFavorite()
                        onDismiss()
                    }) { Text("Load FAV") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
