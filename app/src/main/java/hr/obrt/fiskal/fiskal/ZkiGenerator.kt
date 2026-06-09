package hr.obrt.fiskal.fiskal

import java.security.PrivateKey
import java.security.Signature
import java.security.MessageDigest

/**
 * Generira Zaštitni kôd izdavatelja (ZKI) prema tehničkoj specifikaciji.
 *
 * Postupak:
 *  1. Spoje se podaci u jedan niz, bez razdjelnika, točno ovim redoslijedom:
 *       OIB + DatVrijeme + BrOznRac + OznPosPr + OznNapUr + IznosUkupno
 *  2. Niz se elektronički potpiše privatnim ključem certifikata (RSA, SHA-1).
 *  3. Nad potpisom se izračuna MD5 sažetak.
 *  4. ZKI je MD5 sažetak zapisan kao 32 heksadecimalne znamenke (mala slova).
 *
 * Napomena o datumu: koristi se isti zapis kao u polju DatVrijeme
 * (dd.MM.yyyy'T'HH:mm:ss). Iznos je u obliku "0.00".
 */
object ZkiGenerator {

    fun generate(
        privateKey: PrivateKey,
        oib: String,
        datVrijeme: String,
        brOznRac: String,
        oznPosPr: String,
        oznNapUr: String,
        iznosUkupno: String,
    ): String {
        val data = oib + datVrijeme + brOznRac + oznPosPr + oznNapUr + iznosUkupno

        val signer = Signature.getInstance("SHA1withRSA")
        signer.initSign(privateKey)
        signer.update(data.toByteArray(Charsets.UTF_8))
        val signature = signer.sign()

        val md5 = MessageDigest.getInstance("MD5").digest(signature)
        return md5.toHexLower()
    }

    private fun ByteArray.toHexLower(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
