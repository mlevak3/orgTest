package hr.obrt.fiskal.fiskal

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.util.UUID

/** Uparen Bluetooth uređaj (za prikaz u popisu za odabir pisača). */
data class PairedDevice(val name: String, val address: String)

/**
 * Ispis na Bluetooth POS termalni pisač (npr. Bixolon SPP-R200II) preko
 * klasičnog Bluetootha (SPP — Serial Port Profile). Pisač se prvo mora upariti
 * s telefonom kroz Android postavke; ovdje se samo bira od već uparenih.
 *
 * Mnogi jeftiniji/POS termalni pisači ne odgovaraju ispravno na Androidov SDP
 * upit za standardni SPP UUID, pa `createRfcommSocketToServiceRecord()` zna
 * baciti "read failed, socket might closed" — stoga se, ako standardni pristup
 * ne uspije, koristi dobro poznati fallback: refleksijom otvoren RFCOMM kanal 1
 * (isti trik koriste gotovo svi Android ESC/POS printer SDK-ovi, uklj. proizvođačke).
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

        // cancelDiscovery() na Androidu 12+ zahtijeva BLUETOOTH_SCAN (drugu dozvolu od
        // BLUETOOTH_CONNECT koju tražimo) — bez nje baca SecurityException. Nije nužan za
        // spajanje na već uparen uređaj, pa se preskače ako dozvola nedostaje.
        runCatching { adapter.cancelDiscovery() }

        // 1) Standardni pristup (SDP upit za SPP UUID).
        val standardno = pokusajSpojiIPoslati(device, bytes) { device.createRfcommSocketToServiceRecord(SPP_UUID) }
        if (standardno.isSuccess) return standardno

        // 2) Fallback: izravno otvori RFCOMM kanal 1 (zaobilazi SDP; radi na većini POS pisača
        //    koji ne odgovaraju ispravno na standardni upit).
        val fallback = pokusajSpojiIPoslati(device, bytes) { createRfcommSocketKanal(device, 1) }
        if (fallback.isSuccess) return fallback

        return Result.failure(
            IllegalStateException(
                "Ispis nije uspio ni standardnim ni fallback načinom.\n" +
                    "Standardni: ${standardno.exceptionOrNull()?.message}\n" +
                    "Fallback (kanal 1): ${fallback.exceptionOrNull()?.message}"
            )
        )
    }

    @SuppressLint("MissingPermission")
    private inline fun pokusajSpojiIPoslati(
        device: BluetoothDevice,
        bytes: ByteArray,
        stvoriSocket: () -> BluetoothSocket,
    ): Result<Unit> = try {
        val socket = stvoriSocket()
        socket.use {
            it.connect()
            it.outputStream.write(bytes)
            it.outputStream.flush()
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.message ?: e.javaClass.simpleName))
    }

    /** Otvara RFCOMM utičnicu na fiksnom kanalu refleksijom (skrivena metoda BluetoothDevice-a). */
    private fun createRfcommSocketKanal(device: BluetoothDevice, kanal: Int): BluetoothSocket {
        val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        return m.invoke(device, kanal) as BluetoothSocket
    }
}
