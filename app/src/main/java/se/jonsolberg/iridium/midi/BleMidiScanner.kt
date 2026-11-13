package se.jonsolberg.iridium.midi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class BleMidiDevice(val device: BluetoothDevice, val name: String)

/** Scans for Bluetooth LE MIDI peripherals (e.g. wireless MIDI adapters). */
class BleMidiScanner(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val _found = MutableStateFlow<List<BleMidiDevice>>(emptyList())
    val found: StateFlow<List<BleMidiDevice>> = _found.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val callback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device
            if (_found.value.any { it.device.address == dev.address }) return
            val name = try {
                dev.name ?: result.scanRecord?.deviceName ?: dev.address
            } catch (e: SecurityException) {
                dev.address
            }
            _found.value = _found.value + BleMidiDevice(dev, name)
        }

        override fun onScanFailed(errorCode: Int) {
            _error.value = "Bluetooth scan failed ($errorCode)"
            _scanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        _error.value = null
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null) {
            _error.value = "This phone has no Bluetooth"
            return
        }
        if (!adapter.isEnabled) {
            _error.value = "Turn on Bluetooth first"
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            _error.value = "Bluetooth LE scanner unavailable"
            return
        }
        _found.value = emptyList()
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(MIDI_SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(listOf(filter), settings, callback)
            _scanning.value = true
            main.postDelayed({ stop() }, 12_000)
        } catch (e: SecurityException) {
            _error.value = "Bluetooth permission denied"
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        main.removeCallbacksAndMessages(null)
        if (!_scanning.value) return
        _scanning.value = false
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        try {
            adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: Exception) {
        }
    }

    companion object {
        val MIDI_SERVICE: UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
    }
}
