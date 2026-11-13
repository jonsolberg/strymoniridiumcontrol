package se.jonsolberg.iridium.midi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin wrapper around android.media.midi: lists USB/BLE MIDI devices,
 * opens one, sends CC/PC and parses incoming CC/PC.
 */
class MidiController(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val manager: MidiManager? = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager

    private val _devices = MutableStateFlow<List<MidiDeviceInfo>>(emptyList())
    val devices: StateFlow<List<MidiDeviceInfo>> = _devices.asStateFlow()

    private val _connected = MutableStateFlow<String?>(null)
    /** Name of the open device, or null. */
    val connected: StateFlow<String?> = _connected.asStateFlow()

    private var device: MidiDevice? = null
    private var deviceId: Int = -1
    private var inputPort: MidiInputPort? = null
    private var outputPort: MidiOutputPort? = null

    var onControlChange: ((channel: Int, cc: Int, value: Int) -> Unit)? = null
    var onProgramChange: ((channel: Int, program: Int) -> Unit)? = null
    var onDevicesChanged: (() -> Unit)? = null
    var onLog: ((String) -> Unit)? = null

    val isSupported: Boolean get() = manager != null

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            refresh()
            onDevicesChanged?.invoke()
        }

        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            refresh()
            if (info.id == deviceId) {
                onLog?.invoke("Device removed: ${nameOf(info)}")
                disconnect()
            }
        }
    }

    @Suppress("DEPRECATION")
    fun start() {
        manager?.registerDeviceCallback(callback, main)
        refresh()
    }

    fun stop() {
        manager?.unregisterDeviceCallback(callback)
        disconnect()
    }

    @Suppress("DEPRECATION")
    fun refresh() {
        _devices.value = manager?.devices?.toList().orEmpty()
            .filter { info -> info.ports.any { it.type == MidiDeviceInfo.PortInfo.TYPE_INPUT } }
    }

    fun connect(info: MidiDeviceInfo) {
        val m = manager ?: return
        disconnect()
        val name = nameOf(info)
        onLog?.invoke("Opening $name…")
        m.openDevice(info, { d ->
            if (d == null) onLog?.invoke("Could not open $name") else attach(d, name)
        }, main)
    }

    @SuppressLint("MissingPermission")
    fun connectBluetooth(bt: BluetoothDevice, label: String) {
        val m = manager ?: return
        disconnect()
        onLog?.invoke("Opening Bluetooth MIDI $label…")
        m.openBluetoothDevice(bt, { d ->
            if (d == null) onLog?.invoke("Could not open $label") else attach(d, label)
        }, main)
    }

    private fun attach(d: MidiDevice, name: String) {
        device = d
        deviceId = d.info.id
        val ports = d.info.ports
        inputPort = ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_INPUT }
            ?.let { d.openInputPort(it.portNumber) }
        outputPort = ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
            ?.let { d.openOutputPort(it.portNumber) }
            ?.also { it.connect(receiver) }
        if (inputPort == null) {
            onLog?.invoke("$name has no MIDI input port")
            disconnect()
            return
        }
        _connected.value = name
        onLog?.invoke("Connected to $name" + if (outputPort == null) " (send only)" else "")
    }

    fun disconnect() {
        runCatching { outputPort?.disconnect(receiver) }
        runCatching { outputPort?.close() }
        runCatching { inputPort?.close() }
        runCatching { device?.close() }
        outputPort = null
        inputPort = null
        device = null
        deviceId = -1
        if (_connected.value != null) {
            _connected.value = null
        }
    }

    fun send(bytes: ByteArray): Boolean {
        val port = inputPort ?: return false
        return try {
            port.send(bytes, 0, bytes.size)
            true
        } catch (e: Exception) {
            onLog?.invoke("Send failed: ${e.message}")
            false
        }
    }

    fun sendCc(channel: Int, cc: Int, value: Int) = send(
        byteArrayOf((0xB0 or (channel and 0x0F)).toByte(), (cc and 0x7F).toByte(), value.coerceIn(0, 127).toByte())
    )

    fun sendProgram(channel: Int, program: Int) = send(
        byteArrayOf((0xC0 or (channel and 0x0F)).toByte(), (program and 0x7F).toByte())
    )

    // ---- incoming parser (running status, skips sysex and realtime) ----
    private var status = 0
    private val data = IntArray(2)
    private var dataCount = 0
    private var inSysex = false

    private val receiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            for (i in offset until offset + count) {
                val b = msg[i].toInt() and 0xFF
                when {
                    b >= 0xF8 -> Unit
                    b == 0xF0 -> inSysex = true
                    b == 0xF7 -> inSysex = false
                    inSysex -> Unit
                    b >= 0x80 -> {
                        status = if (b < 0xF0) b else 0
                        dataCount = 0
                    }
                    status != 0 -> {
                        data[dataCount++] = b
                        val type = status and 0xF0
                        val needed = if (type == 0xC0 || type == 0xD0) 1 else 2
                        if (dataCount >= needed) {
                            dispatch(type, status and 0x0F, data[0], data[1])
                            dataCount = 0
                        }
                    }
                }
            }
        }
    }

    private fun dispatch(type: Int, ch: Int, d1: Int, d2: Int) {
        when (type) {
            0xB0 -> main.post { onControlChange?.invoke(ch, d1, d2) }
            0xC0 -> main.post { onProgramChange?.invoke(ch, d1) }
        }
    }

    companion object {
        fun nameOf(info: MidiDeviceInfo): String {
            val p = info.properties
            return p.getString(MidiDeviceInfo.PROPERTY_NAME)
                ?: p.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
                ?: "MIDI device ${info.id}"
        }

        fun transportOf(info: MidiDeviceInfo): String = when (info.type) {
            MidiDeviceInfo.TYPE_USB -> "USB"
            MidiDeviceInfo.TYPE_BLUETOOTH -> "Bluetooth"
            MidiDeviceInfo.TYPE_VIRTUAL -> "Virtual"
            else -> "MIDI"
        }
    }
}
