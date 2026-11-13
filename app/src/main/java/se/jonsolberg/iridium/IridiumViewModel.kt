package se.jonsolberg.iridium

import android.app.Application
import android.content.Context
import android.media.midi.MidiDeviceInfo
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import se.jonsolberg.iridium.midi.BleMidiDevice
import se.jonsolberg.iridium.midi.BleMidiScanner
import se.jonsolberg.iridium.midi.MidiController
import se.jonsolberg.iridium.model.Cc
import se.jonsolberg.iridium.model.Favorite
import se.jonsolberg.iridium.model.Iridium
import se.jonsolberg.iridium.model.PedalSound
import se.jonsolberg.iridium.model.Preset
import se.jonsolberg.iridium.model.PresetStore
import se.jonsolberg.iridium.model.SyncState
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IridiumViewModel(app: Application) : AndroidViewModel(app) {

    val midi = MidiController(app)
    val ble = BleMidiScanner(app)
    private val store = PresetStore(File(app.filesDir, "presets.json"))
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ---- pedal state ----
    var sound by mutableStateOf(PedalSound())
        private set
    var engaged by mutableStateOf(true)
        private set
    var favLit by mutableStateOf(false)
        private set
    var presets by mutableStateOf(store.load())
        private set
    var current by mutableStateOf(prefs.getInt("current", 0).coerceIn(0, Iridium.PRESET_COUNT - 1))
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    /** The sound behind the FAV footswitch, as last stored from this app. */
    var favorite by mutableStateOf(store.loadFavorite())
        private set
    private var soundBeforeFav: PedalSound? = null
    val log = mutableStateListOf<String>()

    // ---- settings ----
    var midiChannel by mutableStateOf(prefs.getInt("channel", 0))   // 0..15
        private set
    var oneBased by mutableStateOf(prefs.getBoolean("oneBased", false))
        private set
    var saveViaProgramChange by mutableStateOf(prefs.getBoolean("saveViaPc", true))
        private set
    var favHoldMs by mutableStateOf(prefs.getInt("favHoldMs", 2000))
        private set
    var followPedal by mutableStateOf(prefs.getBoolean("followPedal", true))
        private set
    var logOutgoing by mutableStateOf(false)

    private var lastDevice: String? = prefs.getString("lastDevice", null)
    private var incomingBank = 0
    private var saveJob: Job? = null

    val currentPreset: Preset get() = presets[current]
    val isEdited: Boolean get() = currentPreset.sound != null && currentPreset.sound != sound
    val isConnected: Boolean get() = midi.connected.value != null

    init {
        midi.onLog = ::addLog
        midi.onControlChange = ::onIncomingCc
        midi.onProgramChange = ::onIncomingProgram
        midi.onDevicesChanged = ::autoConnect
        midi.start()
        currentPreset.sound?.let { sound = it }
        autoConnect()
    }

    override fun onCleared() {
        ble.stop()
        midi.stop()
    }

    fun displayNumber(n: Int) = n + if (oneBased) 1 else 0

    // ---------------------------------------------------------------- connection
    private fun autoConnect() {
        if (isConnected) return
        val devs = midi.devices.value
        val target = devs.firstOrNull { MidiController.nameOf(it) == lastDevice }
            ?: devs.firstOrNull { MidiController.nameOf(it).contains("iridium", ignoreCase = true) }
        if (target != null) connect(target)
    }

    fun connect(info: MidiDeviceInfo) {
        rememberDevice(MidiController.nameOf(info))
        midi.connect(info)
    }

    fun connectBle(d: BleMidiDevice) {
        ble.stop()
        rememberDevice(d.name)
        midi.connectBluetooth(d.device, d.name)
    }

    fun disconnect() {
        rememberDevice(null)
        midi.disconnect()
    }

    private fun rememberDevice(name: String?) {
        lastDevice = name
        prefs.edit().putString("lastDevice", name).apply()
    }

    // ---------------------------------------------------------------- knobs & switches
    private fun cc(n: Int, v: Int) {
        val ok = midi.sendCc(midiChannel, n, v)
        if (logOutgoing && ok) addLog("→ ch${midiChannel + 1} CC $n = $v")
    }

    private fun program(n: Int) {
        cc(Cc.BANK, n / 128)
        midi.sendProgram(midiChannel, n % 128)
        if (logOutgoing) addLog("→ ch${midiChannel + 1} bank ${n / 128} PC ${n % 128}")
    }

    fun setValue(ccNumber: Int, value: Int) {
        val next = sound.withCc(ccNumber, value)
        if (next == sound) return
        sound = next
        cc(ccNumber, value)
    }

    fun setAmp(amp: Int) {
        if (amp == sound.amp) return
        val cab = (amp - 1) * 3 + sound.cabLetter
        sound = sound.copy(amp = amp, cab = cab)
        cc(Cc.AMP, amp)
        cc(Cc.CAB, cab)
    }

    fun setCabLetter(letter: Int) = setCab(sound.cabFamily * 3 + letter)
    fun setCabFamily(family: Int) = setCab(family * 3 + sound.cabLetter)

    private fun setCab(cab: Int) {
        if (cab == sound.cab) return
        sound = sound.copy(cab = cab)
        cc(Cc.CAB, cab)
    }

    fun setRoomSize(size: Int) = setValue(Cc.ROOM_SIZE, size)
    fun setAmpDisabled(disabled: Boolean) = setValue(Cc.AMP_DISABLE, if (disabled) 127 else 0)
    fun setVolumePost(post: Boolean) = setValue(Cc.VOL_POSITION, if (post) 127 else 0)

    /** When the app last switched ON/bypass; the pedal echoes it back as an ON footswitch press. */
    private var onSentAt = 0L

    fun toggleOn() {
        onSentAt = SystemClock.uptimeMillis()
        engaged = !engaged
        cc(Cc.BYPASS, if (engaged) 127 else 0)
    }

    /** When the app last pressed FAV over MIDI; the pedal echoes that press back. */
    private var favSentAt = 0L

    fun tapFav() {
        favSentAt = SystemClock.uptimeMillis()
        toggleFavDisplay()
        viewModelScope.launch { pressSwitch(Cc.FAV_SWITCH) }
    }

    /** Mirrors the pedal: FAV on shows the favorite sound, FAV off returns to the previous one. */
    private fun toggleFavDisplay() {
        favLit = !favLit
        if (favLit) {
            favorite?.let {
                soundBeforeFav = sound
                sound = it.sound
            }
        } else {
            soundBeforeFav?.let { sound = it }
            soundBeforeFav = null
        }
    }

    fun storeFavorite(name: String, alsoToPedal: Boolean) {
        val fav = Favorite(name.trim(), sound)
        favorite = fav
        store.saveFavorite(fav)
        addLog("Stored FAV sound in the app")
        if (alsoToPedal) writeFavoriteToPedal(fav)
    }

    /** Loads the stored FAV sound into the editor and sends it live (does not touch the pedal's FAV memory). */
    fun recallFavorite() {
        favorite?.let {
            sound = it.sound
            sendAll()
        }
    }

    /**
     * Writes the favorite into the pedal: switch to manual mode (bank 0 / PC 127), send every
     * setting, then hold FAV the way you would on the pedal to store a new favorite.
     */
    private fun writeFavoriteToPedal(fav: Favorite) {
        if (!isConnected || saveJob?.isActive == true) return
        saveJob = viewModelScope.launch {
            try {
                busy = "Writing the FAV sound to the pedal…"
                program(Iridium.MANUAL_SLOT)
                delay(300)
                sendAllNow(fav.sound)
                delay(200)
                cc(Cc.FAV_SWITCH, 127)
                delay(favHoldMs.toLong())
                cc(Cc.FAV_SWITCH, 0)
                delay(800)
                addLog("Sent FAV store sequence to pedal")
            } finally {
                busy = null
            }
        }
    }

    /** Connection test used by the original app: virtually press the ON footswitch. */
    fun testOnSwitch() {
        viewModelScope.launch { pressSwitch(Cc.ON_SWITCH) }
    }

    private suspend fun pressSwitch(ccNumber: Int, holdMs: Long = 80) {
        cc(ccNumber, 127)
        delay(holdMs)
        cc(ccNumber, 0)
    }

    fun sendAll() {
        viewModelScope.launch { sendAllNow(sound) }
    }

    private suspend fun sendAllNow(s: PedalSound) {
        for ((c, v) in s.ccMessages()) {
            cc(c, v)
            delay(12)
        }
    }

    fun revert() {
        currentPreset.sound?.let {
            sound = it
            sendAll()
        }
    }

    // ---------------------------------------------------------------- presets
    fun select(n: Int) {
        if (!Iridium.isStorable(n)) return
        current = n
        prefs.edit().putInt("current", n).apply()
        val p = presets[n]
        when (p.sync) {
            SyncState.SYNCED -> {
                sound = p.sound!!
                program(n)
            }
            SyncState.EDITED -> {
                sound = p.sound!!
                sendAll()
            }
            SyncState.EMPTY -> Unit // keep the current sound so it can be stored here
        }
    }

    fun step(delta: Int) {
        var n = current
        do {
            n = (n + delta + Iridium.PRESET_COUNT) % Iridium.PRESET_COUNT
        } while (!Iridium.isStorable(n))
        select(n)
    }

    private fun update(n: Int, transform: (Preset) -> Preset) {
        presets = presets.toMutableList().also { it[n] = transform(it[n]) }
        store.save(presets)
    }

    /** Store the knob settings in the current slot, optionally writing it to the pedal. */
    fun storeCurrent(name: String, alsoToPedal: Boolean) {
        val s = sound
        update(current) { it.copy(name = name.trim(), sound = s, sync = SyncState.EDITED) }
        if (alsoToPedal) saveToPedal(listOf(current))
    }

    fun rename(n: Int, name: String) = update(n) { it.copy(name = name.trim()) }

    fun reset(numbers: Collection<Int>) {
        presets = presets.toMutableList().also { l -> numbers.forEach { l[it] = Preset(it) } }
        store.save(presets)
    }

    /** Copies the selected presets, in order, to consecutive storable slots starting at [dest]. */
    fun copy(numbers: List<Int>, dest: Int): Int {
        val sources = numbers.sorted().map { presets[it] }
        val list = presets.toMutableList()
        var target = dest
        var copied = 0
        for (src in sources) {
            while (target < Iridium.PRESET_COUNT && !Iridium.isStorable(target)) target++
            if (target >= Iridium.PRESET_COUNT) break
            list[target] = src.copy(number = target, sync = if (src.sound == null) SyncState.EMPTY else SyncState.EDITED)
            target++
            copied++
        }
        presets = list
        store.save(presets)
        return copied
    }

    fun exportBackup(): String {
        val settings = JSONObject()
            .put("midiChannel", midiChannel)
            .put("oneBased", oneBased)
            .put("saveViaProgramChange", saveViaProgramChange)
            .put("favHoldMs", favHoldMs)
            .put("followPedal", followPedal)
        return PresetStore.backupJson(presets, favorite, settings)
    }

    /** Restores a full backup, replacing the library, FAV sound and settings. Returns preset count. */
    fun importBackup(text: String): Int {
        val b = PresetStore.parseBackup(text)
        presets = b.presets
        store.save(presets)
        b.favorite?.let {
            favorite = it
            store.saveFavorite(it)
        }
        b.settings?.let { st ->
            if (st.has("midiChannel")) updateChannel(st.getInt("midiChannel"))
            if (st.has("oneBased")) updateOneBased(st.getBoolean("oneBased"))
            if (st.has("saveViaProgramChange")) updateSaveViaPc(st.getBoolean("saveViaProgramChange"))
            if (st.has("favHoldMs")) updateFavHold(st.getInt("favHoldMs"))
            if (st.has("followPedal")) updateFollowPedal(st.getBoolean("followPedal"))
        }
        presets[current].sound?.let { sound = it }
        return presets.count { it.sound != null }
    }

    fun exportJson(numbers: Collection<Int>): String =
        PresetStore.exportJson(numbers.sorted().map { presets[it] }.filter { it.sound != null })

    fun importJson(text: String, startAt: Int): Int {
        val items = PresetStore.importJson(text)
        val list = presets.toMutableList()
        var target = startAt
        var count = 0
        for ((name, s) in items) {
            while (target < Iridium.PRESET_COUNT && !Iridium.isStorable(target)) target++
            if (target >= Iridium.PRESET_COUNT) break
            list[target] = Preset(target, name, s, SyncState.EDITED)
            target++
            count++
        }
        presets = list
        store.save(presets)
        return count
    }

    /**
     * Writes presets into the pedal. For each slot: recall it, send every parameter,
     * hold FAV (pedal enters save mode, FAV blinks blue), release, then confirm with
     * either a Program Change (firmware 1.33+ over USB, or TRS MIDI) or a second FAV press.
     */
    fun saveToPedal(numbers: List<Int>) {
        if (!isConnected) {
            addLog("Not connected – cannot save to pedal")
            return
        }
        if (saveJob?.isActive == true) return
        val targets = numbers.sorted().filter { Iridium.isStorable(it) && presets[it].sound != null }
        if (targets.isEmpty()) return
        saveJob = viewModelScope.launch {
            try {
                for ((i, n) in targets.withIndex()) {
                    busy = "Saving preset ${displayNumber(n)} to pedal (${i + 1}/${targets.size})…"
                    val s = presets[n].sound ?: continue
                    program(n)
                    delay(300)
                    sendAllNow(s)
                    delay(200)
                    cc(Cc.FAV_SWITCH, 127)
                    delay(favHoldMs.toLong())
                    cc(Cc.FAV_SWITCH, 0)
                    delay(400)
                    if (saveViaProgramChange) program(n) else pressSwitch(Cc.FAV_SWITCH)
                    delay(800)
                    update(n) { it.copy(sync = SyncState.SYNCED) }
                    addLog("Saved preset ${displayNumber(n)} to pedal")
                }
                val last = targets.last()
                current = last
                prefs.edit().putInt("current", last).apply()
                presets[last].sound?.let { sound = it }
                favLit = false
            } finally {
                busy = null
            }
        }
    }

    fun cancelSave() {
        saveJob?.cancel()
    }

    // ---------------------------------------------------------------- incoming MIDI
    private fun onIncomingCc(ch: Int, ccNumber: Int, v: Int) {
        addLog("← ch${ch + 1} CC $ccNumber = $v")
        // Listen on every channel: older Iridium firmware talks over USB on a fixed channel
        // even when the TRS MIDI channel has been changed.
        if (ccNumber == Cc.BANK) {
            incomingBank = v
            return
        }
        if (!followPedal) return
        when (ccNumber) {
            Cc.LEVEL, Cc.DRIVE, Cc.BASS, Cc.MIDDLE, Cc.TREBLE, Cc.ROOM, Cc.ROOM_SIZE,
            Cc.AMP_DISABLE, Cc.VOL_POSITION, Cc.CAB -> sound = sound.withCc(ccNumber, v)
            Cc.AMP -> if (v in 1..3) sound = sound.copy(amp = v, cab = (v - 1) * 3 + sound.cabLetter)
            // The pedal reports its physical switches with their own value orders.
            Cc.AMP_KNOB_OUT -> if (v in 1..3) {
                val amp = AMP_SWITCH_LOOKUP[v - 1]
                sound = sound.copy(amp = amp, cab = (amp - 1) * 3 + sound.cabLetter)
            }
            Cc.CAB_KNOB_OUT -> if (v in 1..3) {
                sound = sound.copy(cab = (sound.amp - 1) * 3 + CAB_SWITCH_LOOKUP[v - 1])
            }
            Cc.BYPASS -> engaged = v > 0
            // Footswitch presses reported by the pedal: it sends 1 on press and 0 on release
            // (any non-zero value counts as a press, so 127 from other controllers works too).
            // Ignore the pedal's echo of an ON change the app just sent, or the LED would flip back.
            Cc.ON_SWITCH -> if (v > 0 && SystemClock.uptimeMillis() - onSentAt > ECHO_WINDOW_MS) engaged = !engaged
            // Ignore the pedal's echo of a FAV press the app just sent, or the LED would flip back.
            Cc.FAV_SWITCH -> if (v > 0 && SystemClock.uptimeMillis() - favSentAt > ECHO_WINDOW_MS) toggleFavDisplay()
        }
    }

    private fun onIncomingProgram(ch: Int, p: Int) {
        addLog("← ch${ch + 1} PC $p (bank $incomingBank)")
        if (!followPedal) return
        val n = incomingBank * 128 + p
        if (!Iridium.isStorable(n)) return
        current = n
        presets[n].sound?.let { sound = it }
    }

    private fun addLog(line: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())
        log.add(0, "$t  $line")
        while (log.size > 300) log.removeAt(log.size - 1)
    }

    fun clearLog() = log.clear()

    // ---------------------------------------------------------------- settings
    fun updateChannel(ch: Int) {
        midiChannel = ch.coerceIn(0, 15)
        prefs.edit().putInt("channel", midiChannel).apply()
    }

    fun updateOneBased(b: Boolean) {
        oneBased = b
        prefs.edit().putBoolean("oneBased", b).apply()
    }

    fun updateSaveViaPc(b: Boolean) {
        saveViaProgramChange = b
        prefs.edit().putBoolean("saveViaPc", b).apply()
    }

    fun updateFavHold(ms: Int) {
        favHoldMs = ms
        prefs.edit().putInt("favHoldMs", ms).apply()
    }

    fun updateFollowPedal(b: Boolean) {
        followPedal = b
        prefs.edit().putBoolean("followPedal", b).apply()
    }

    companion object {
        private const val ECHO_WINDOW_MS = 800L
        private val AMP_SWITCH_LOOKUP = intArrayOf(3, 1, 2)
        private val CAB_SWITCH_LOOKUP = intArrayOf(2, 0, 1)
    }
}
