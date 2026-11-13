package se.jonsolberg.iridium.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.jonsolberg.iridium.IridiumViewModel
import se.jonsolberg.iridium.model.Iridium
import se.jonsolberg.iridium.model.Preset
import se.jonsolberg.iridium.model.SyncState

private enum class PresetDialog { NONE, COPY, RENAME, RESET }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PresetsScreen(vm: IridiumViewModel, onOpenPreset: () -> Unit) {
    val context = LocalContext.current
    val connected by vm.midi.connected.collectAsState()
    val selected = remember { mutableStateListOf<Int>() }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(PresetDialog.NONE) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { listState.scrollToItem((vm.current - 3).coerceAtLeast(0)) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val numbers = selected.toList().ifEmpty { vm.presets.filter { it.sound != null }.map { it.number } }
        runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(vm.exportJson(numbers).toByteArray()) }
        }.onSuccess { toast("Exported ${numbers.size} presets") }
            .onFailure { toast("Export failed: ${it.message}") }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val start = selected.minOrNull() ?: vm.current
        runCatching {
            val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            vm.importJson(text, start)
        }.onSuccess {
            toast("Imported $it presets starting at ${vm.displayNumber(start)}")
            selected.clear()
        }.onFailure { toast("Import failed: ${it.message}") }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            navigationIcon = {
                if (selected.isNotEmpty()) {
                    IconButton(onClick = { selected.clear() }) { Icon(Icons.Default.Close, "Clear selection") }
                }
            },
            title = { Text(if (selected.isEmpty()) "Presets" else "${selected.size} selected") },
            actions = {
                if (selected.isNotEmpty()) {
                    TextButton(
                        enabled = connected != null,
                        onClick = {
                            vm.saveToPedal(selected.toList())
                            selected.clear()
                        },
                    ) { Text("Save to pedal") }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (selected.isNotEmpty()) {
                            DropdownMenuItem(text = { Text("Copy to…") }, onClick = { menuOpen = false; dialog = PresetDialog.COPY })
                            if (selected.size == 1) {
                                DropdownMenuItem(text = { Text("Rename…") }, onClick = { menuOpen = false; dialog = PresetDialog.RENAME })
                            }
                            DropdownMenuItem(text = { Text("Clear selected") }, onClick = { menuOpen = false; dialog = PresetDialog.RESET })
                        }
                        DropdownMenuItem(
                            text = { Text(if (selected.isEmpty()) "Export all" else "Export selected") },
                            onClick = { menuOpen = false; exportLauncher.launch("iridium-presets.json") },
                        )
                        DropdownMenuItem(
                            text = { Text("Import (into ${vm.displayNumber(selected.minOrNull() ?: vm.current)}…)") },
                            onClick = { menuOpen = false; importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        )
                    }
                }
            },
        )
        Text(
            "Tap to load · long-press to select · colour bar: green = on pedal, amber = not saved to pedal",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(vm.presets, key = { it.number }) { p ->
                PresetRow(
                    vm, p,
                    isCurrent = p.number == vm.current,
                    isSelected = p.number in selected,
                    onClick = {
                        if (selected.isNotEmpty()) {
                            if (p.number in selected) selected.remove(p.number) else selected.add(p.number)
                        } else if (Iridium.isStorable(p.number)) {
                            vm.select(p.number)
                            onOpenPreset()
                        }
                    },
                    onLongClick = {
                        if (Iridium.isStorable(p.number)) {
                            if (p.number in selected) selected.remove(p.number) else selected.add(p.number)
                        }
                    },
                    onCheck = {
                        if (p.number in selected) selected.remove(p.number) else selected.add(p.number)
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
        }
    }

    when (dialog) {
        PresetDialog.COPY -> NumberDialog(
            title = "Copy ${selected.size} preset(s) to…",
            hint = "First destination number",
            initial = vm.displayNumber(selected.max() + 1).coerceAtMost(vm.displayNumber(Iridium.PRESET_COUNT - 1)),
            onDismiss = { dialog = PresetDialog.NONE },
        ) { shown ->
            val dest = shown - if (vm.oneBased) 1 else 0
            if (dest in 0 until Iridium.PRESET_COUNT) {
                val n = vm.copy(selected.toList(), dest)
                toast("Copied $n preset(s)")
                selected.clear()
            } else toast("Invalid preset number")
            dialog = PresetDialog.NONE
        }
        PresetDialog.RENAME -> {
            val n = selected.first()
            var name by remember { mutableStateOf(vm.presets[n].name) }
            AlertDialog(
                onDismissRequest = { dialog = PresetDialog.NONE },
                title = { Text("Rename preset ${vm.displayNumber(n)}") },
                text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text("Name") }) },
                confirmButton = {
                    TextButton(onClick = {
                        vm.rename(n, name)
                        selected.clear()
                        dialog = PresetDialog.NONE
                    }) { Text("Rename") }
                },
                dismissButton = { TextButton(onClick = { dialog = PresetDialog.NONE }) { Text("Cancel") } },
            )
        }
        PresetDialog.RESET -> AlertDialog(
            onDismissRequest = { dialog = PresetDialog.NONE },
            title = { Text("Clear ${selected.size} preset(s)?") },
            text = { Text("This empties the slots in the app. Presets already stored in the pedal are not erased.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.reset(selected.toList())
                    selected.clear()
                    dialog = PresetDialog.NONE
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { dialog = PresetDialog.NONE }) { Text("Cancel") } },
        )
        PresetDialog.NONE -> Unit
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetRow(
    vm: IridiumViewModel,
    p: Preset,
    isCurrent: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheck: () -> Unit,
) {
    val manual = p.number == Iridium.MANUAL_SLOT
    val bar = when (p.sync) {
        SyncState.SYNCED -> SyncGreen
        SyncState.EDITED -> SyncAmber
        SyncState.EMPTY -> Color.Transparent
    }
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(bar))
        Text(
            "%03d".format(vm.displayNumber(p.number)),
            fontFamily = FontFamily.Monospace, fontSize = 16.sp,
            color = if (isCurrent) LedRed else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Column(Modifier.weight(1f)) {
            if (manual) {
                Text("Manual mode", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Bank 0 / PC 127 recalls the knobs — can't store a preset", fontSize = 11.sp, color = Color.Gray)
            } else {
                Text(
                    p.name.ifEmpty { if (p.sound == null) "—" else "Untitled" },
                    fontWeight = if (p.sound != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (p.sound != null) MaterialTheme.colorScheme.onSurface else Color.Gray,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                p.sound?.let {
                    Text(it.summary(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        if (!manual) {
            Checkbox(checked = isSelected, onCheckedChange = { onCheck() })
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun NumberDialog(title: String, hint: String, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it.filter(Char::isDigit).take(3) },
                label = { Text(hint) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { text.toIntOrNull()?.let(onConfirm) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
