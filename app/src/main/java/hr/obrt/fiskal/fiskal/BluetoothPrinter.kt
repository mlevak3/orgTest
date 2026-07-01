package hr.obrt.fiskal.fiskal

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import java.util.UUID

/** Uparen Bluetooth uređaj (za prikaz u popisu za odabir pisača). */
data class PairedDevice(val name: String, val address: String)

/**
 * Ispis na Bluetooth POS termalni pisač (npr. Bixolon SPP-R200II) preko
 * klasičnog Bluetootha (SPP — Serial Port Profile). Pisač se prvo mora upariti
 * s telefonom kroz Android postavke; ovdje se samo bira od već uparenih.
 */
object BluetoothPrinter {

    /** Standardni SPP UUID kojeg koriste gotovo svi POS/termalni pisači. */
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun uparenaUredaji(context: Context): List<PairedDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return runCatching {
            adapter.bondedDevices
                .map { PairedDevice(it.name ?: it.address, it.address) }
                .sortedBy { it.name }
        }.getOrDefault(emptyList())
    }

    fun bluetoothDostupanIUkljucen(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return adapter.isEnabled
    }

    /** Šalje sirove bajtove uparenom pisaču na adresi [address]. Blokira dretvu — pozvati na IO dretvi. */
    @SuppressLint("MissingPermission")
    fun posalji(context: Context, address: String, bytes: ByteArray): Result<Unit> {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return Result.failure(IllegalStateException("Uređaj nema Bluetooth."))
        if (!adapter.isEnabled) return Result.failure(IllegalStateException("Bluetooth je isključen."))

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Neispravna adresa pisača: ${e.message}"))
        }

        return try {
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter.cancelDiscovery()
            socket.use {
                it.connect()
                it.outputStream.write(bytes)
                it.outputStream.flush()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Ispis nije uspio: ${e.message ?: e.javaClass.simpleName}"))
        }
    }
}
