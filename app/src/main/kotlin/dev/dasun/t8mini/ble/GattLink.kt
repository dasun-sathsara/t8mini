package dev.dasun.t8mini.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Minimal GATT client for the T8 Mini control channel.
 *
 * One connection, one write characteristic, one notify characteristic, no MTU
 * negotiation and no bonding. Writes are serialised so frames cannot interleave.
 */
@SuppressLint("MissingPermission")
class GattLink(private val context: Context) {

    private val manager = context.getSystemService(BluetoothManager::class.java)

    private val _replies = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    val replies: SharedFlow<ByteArray> = _replies.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val writeLock = Mutex()

    private var gatt: BluetoothGatt? = null
    private var events: Channel<Event>? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    private val prefs by lazy { context.getSharedPreferences("t8mini", Context.MODE_PRIVATE) }

    /**
     * Opens the control channel.
     *
     * The bonded entry is a classic bond, and the speaker does not answer an LE
     * connect aimed at that address, so a direct attempt ends in status 147.
     * Probe the bonded entry, then the address that worked last time, then
     * whatever is advertising nearby, and remember the winner.
     */
    suspend fun connectToSpeaker(): BluetoothDevice {
        val adapter = manager?.adapter ?: throw IllegalStateException("this phone has no Bluetooth")
        if (!adapter.isEnabled) throw IllegalStateException("Bluetooth is off")

        var failure: Exception? = null

        // The speaker advertises its name, so this is the reliable path.
        scanForSpeaker()?.let { device ->
            try {
                connect(device)
                remember(device)
                return device
            } catch (error: Exception) {
                android.util.Log.d(TAG, "named ${device.address} failed: ${error.message}")
                failure = error
                disconnect()
            }
        }

        // Fallbacks for firmware that hides the name: the classic bond, then the
        // address that worked last, then anything advertising nearby.
        val direct = LinkedHashMap<String, BluetoothDevice>()
        runCatching { adapter.bondedDevices.orEmpty() }.getOrDefault(emptySet())
            .firstOrNull { matchesName(it.name) }
            ?.let { direct[it.address] = it }
        prefs.getString(KEY_LAST_DEVICE, null)?.let { address ->
            runCatching { adapter.getRemoteDevice(address) }.getOrNull()
                ?.let { direct[it.address] = it }
        }
        for (device in direct.values) {
            try {
                connect(device)
                remember(device)
                return device
            } catch (error: Exception) {
                android.util.Log.d(TAG, "direct ${device.address} failed: ${error.message}")
                failure = error
                disconnect()
            }
        }

        for (device in scanCandidates()) {
            if (device.address in direct) continue
            try {
                connect(device)
                remember(device)
                return device
            } catch (error: Exception) {
                android.util.Log.d(TAG, "probe ${device.address} failed: ${error.message}")
                failure = error
                disconnect()
            }
        }

        throw failure ?: IllegalStateException(
            "Speaker not found. Turn it on, close any other app or Mac holding it, then tap Connect.",
        )
    }

    /** Scans until a device advertising the speaker's name shows up. */
    private suspend fun scanForSpeaker(timeoutMs: Long = 10_000): BluetoothDevice? =
        withContext(Dispatchers.Default) {
            val scanner = manager?.adapter?.bluetoothLeScanner ?: return@withContext null
            try {
                withTimeout(timeoutMs) {
                    suspendCancellableCoroutine<BluetoothDevice> { continuation ->
                        val callback = object : ScanCallback() {
                            override fun onScanResult(callbackType: Int, result: ScanResult) {
                                if (!continuation.isActive) return
                                val name = result.scanRecord?.deviceName
                                    ?: runCatching { result.device.name }.getOrNull()
                                android.util.Log.d(
                                    TAG,
                                    "scan ${result.device.address} name=$name rssi=${result.rssi}",
                                )
                                if (!matchesName(name)) return
                                scanner.stopScan(this)
                                continuation.resumeWith(Result.success(result.device))
                            }

                            override fun onScanFailed(errorCode: Int) {
                                if (continuation.isActive) {
                                    continuation.resumeWith(
                                        Result.failure(IllegalStateException("scan failed: $errorCode")),
                                    )
                                }
                            }
                        }
                        scanner.startScan(callback)
                        continuation.invokeOnCancellation { scanner.stopScan(callback) }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                null
            }
        }

    private fun remember(device: BluetoothDevice) {
        android.util.Log.d(TAG, "connected to ${device.address}")
        prefs.edit().putString(KEY_LAST_DEVICE, device.address).apply()
    }

    /** Every advertiser in range, strongest first. */
    private suspend fun scanCandidates(durationMs: Long = 6_000): List<BluetoothDevice> =
        withContext(Dispatchers.Default) {
            val scanner = manager?.adapter?.bluetoothLeScanner ?: return@withContext emptyList()
            val seen = LinkedHashMap<String, Pair<BluetoothDevice, Int>>()
            withTimeoutOrNull(durationMs) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val callback = object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: ScanResult) {
                            val name = result.scanRecord?.deviceName
                                ?: runCatching { result.device.name }.getOrNull()
                            android.util.Log.d(
                                TAG,
                                "scan ${result.device.address} name=$name rssi=${result.rssi}",
                            )
                            val previous = seen[result.device.address]
                            if (previous == null || result.rssi > previous.second) {
                                seen[result.device.address] = result.device to result.rssi
                            }
                        }
                    }
                    scanner.startScan(callback)
                    continuation.invokeOnCancellation { scanner.stopScan(callback) }
                }
            }
            seen.values.sortedByDescending { it.second }.map { it.first }
        }

    private fun matchesName(name: String?): Boolean =
        name != null && NAME_PREFIXES.any { name.startsWith(it, ignoreCase = true) }

    private suspend fun connect(device: BluetoothDevice) {
        disconnect()

        val channel = Channel<Event>(Channel.UNLIMITED)
        events = channel

        val g = device.connectGatt(context, false, callback(channel), BluetoothDevice.TRANSPORT_LE)
            ?: throw IllegalStateException("connectGatt returned null")
        gatt = g

        await(channel, Event.Connected, "connect")
        g.discoverServices()
        await(channel, Event.Services, "discover services")

        val service = g.getService(SERVICE_UUID) ?: throw IllegalStateException("control service missing")
        writeChar = service.getCharacteristic(WRITE_UUID) ?: throw IllegalStateException("write characteristic missing")
        notifyChar = service.getCharacteristic(NOTIFY_UUID) ?: throw IllegalStateException("notify characteristic missing")

        g.setCharacteristicNotification(notifyChar, true)
        val cccd = notifyChar!!.getDescriptor(CCCD_UUID)
            ?: throw IllegalStateException("notification descriptor missing")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
        await(channel, Event.Descriptors, "enable notifications")
        _connected.value = true

        scope.launch {
            for (event in channel) {
                when (event) {
                    is Event.Notification -> _replies.emit(event.value)
                    Event.Disconnected, is Event.Failure -> {
                        android.util.Log.d(TAG, "link lost: $event")
                        _connected.value = false
                        break
                    }
                    else -> Unit
                }
            }
        }
    }

    private suspend fun await(channel: Channel<Event>, expected: Event, step: String) {
        try {
            withTimeout(8_000) {
                var matched = false
                while (!matched) {
                    when (val event = channel.receive()) {
                        expected -> matched = true
                        is Event.Failure -> throw IllegalStateException("$step: ${event.message}")
                        Event.Disconnected -> throw IllegalStateException("$step: link dropped")
                        else -> Unit
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            throw IllegalStateException("$step timed out")
        }
    }

    suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val g = gatt ?: throw IllegalStateException("not connected")
            val characteristic = writeChar ?: throw IllegalStateException("not ready")

            val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(
                    characteristic,
                    bytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    characteristic.value = bytes
                    g.writeCharacteristic(characteristic)
                }
            }
            if (!accepted) throw IllegalStateException("write rejected")
            // The controller needs a beat between back to back commands.
            delay(25)
        }
    }

    fun disconnect() {
        events?.close()
        events = null
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
        writeChar = null
        notifyChar = null
        _connected.value = false
    }

    private fun callback(channel: Channel<Event>) = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> channel.trySend(Event.Connected)
                BluetoothProfile.STATE_DISCONNECTED ->
                    if (status == BluetoothGatt.GATT_SUCCESS) channel.trySend(Event.Disconnected)
                    else channel.trySend(Event.Failure("status $status"))
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) channel.trySend(Event.Services)
            else channel.trySend(Event.Failure("service discovery $status"))
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) channel.trySend(Event.Descriptors)
            else channel.trySend(Event.Failure("descriptor write $status"))
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            channel.trySend(Event.Notification(value))
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            channel.trySend(Event.Notification(value))
        }
    }

    private sealed interface Event {
        data object Connected : Event
        data object Disconnected : Event
        data object Services : Event
        data object Descriptors : Event
        data class Failure(val message: String) : Event
        data class Notification(val value: ByteArray) : Event
    }

    companion object {
        private val SERVICE_UUID = UUID.fromString("011ff5da-0000-1000-8000-00805f9b34fb")
        private val WRITE_UUID = UUID.fromString("00007777-0000-1000-8000-00805f9b34fb")
        private val NOTIFY_UUID = UUID.fromString("00008888-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val TAG = "T8LINK"
        private val NAME_PREFIXES = listOf("Tronsmart", "T8")
        private const val KEY_LAST_DEVICE = "last_device"
    }
}
