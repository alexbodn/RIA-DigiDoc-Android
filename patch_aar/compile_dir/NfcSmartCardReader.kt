@file:Suppress("ktlint:standard:package-name", "ktlint:standard:max-line-length")

package ee.ria.DigiDoc.smartcardreader.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import ee.ria.DigiDoc.smartcardreader.ApduResponseException
import ee.ria.DigiDoc.smartcardreader.SmartCardReader
import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Arrays

@Suppress("ktlint:standard:package-name", "ktlint:standard:max-line-length")
class NfcSmartCardReader(
    tag: Tag,
) : SmartCardReader() {
    private var isoDep: IsoDep? = null
    private var apduEncryptor: ApduEncryptor? = null
    private val historicalBytes: ByteArray

    init {
        try {
            isoDep = IsoDep.get(tag)
            if (isoDep == null) {
                throw SmartCardReaderException("Tag does not support IsoDep")
            }

            isoDep?.connect()
            isoDep?.timeout = 20000 // Extended timeout for PACE operations

            // Bypass ATR/ATS whitelists
            historicalBytes = isoDep?.historicalBytes ?: ByteArray(0)
        } catch (e: IOException) {
            throw SmartCardReaderException(e)
        }
    }

    override fun close() {
        try {
            isoDep?.close()
        } catch (e: IOException) {
            // Ignore
        }
    }

    override fun connected(): Boolean = isoDep?.isConnected == true

    override fun atr(): ByteArray {
        // By returning a standard Estonian eID ATR, we trick `TokenWithPace.create()`
        // into bypassing the "ATS not supported" filter, allowing the native library
        // to attempt standard signing APDUs on the Romanian eID card.
        return byteArrayOf(
            0x3B.toByte(),
            0xDB.toByte(),
            0x96.toByte(),
            0x00.toByte(),
            0x80.toByte(),
            0xB1.toByte(),
            0xFE.toByte(),
            0x45.toByte(),
            0x1F.toByte(),
            0x83.toByte(),
            0x00.toByte(),
            0x12.toByte(),
            0x23.toByte(),
            0x3F.toByte(),
            0x53.toByte(),
            0x65.toByte(),
            0x72.toByte(),
            0x49.toByte(),
            0x44.toByte(),
            0x01.toByte(),
            0x02.toByte(),
            0x01.toByte(),
            0x01.toByte(),
            0x1C.toByte(),
        )
    }

    fun setApduEncryptor(encryptor: ApduEncryptor?) {
        this.apduEncryptor = encryptor
    }

    @Throws(SmartCardReaderException::class)
    override fun transmit(command: ByteArray): ByteArray {
        try {
            LoggingUtil.debugLog("NfcSmartCardReader Shadow", "Transmitting APDU (len ${command.size})", null)
            return isoDep?.transceive(command) ?: throw SmartCardReaderException("IsoDep is null or disconnected")
        } catch (e: IOException) {
            throw SmartCardReaderException(e)
        }
    }

    @Throws(SmartCardReaderException::class)
    override fun transmit(
        cla: Int,
        ins: Int,
        p1: Int,
        p2: Int,
        data: ByteArray?,
        le: Int?,
    ): ByteArray {
        val currentEncryptor = apduEncryptor

        if (currentEncryptor == null) {
            return super.transmit(cla, ins, p1, p2, data, le)
        }

        try {
            var response: ByteArray? = null

            if (data == null) {
                val encrypted = currentEncryptor.encryptAndMac(cla, ins, p1, p2, data, le)
                response = transmit(encrypted)
            } else if (data.size < 256) {
                val encrypted = currentEncryptor.encryptAndMac(cla, ins, p1, p2, data, le)
                response = transmit(encrypted)
            } else {
                var remaining = data.size
                while (remaining >= 256) {
                    val chunk = Arrays.copyOfRange(data, data.size - remaining, data.size - remaining + 255)
                    val encrypted = currentEncryptor.encryptAndMac(cla or 0x10, ins, p1, p2, chunk, le)
                    transmit(encrypted)
                    remaining -= 255
                }
                val chunk = Arrays.copyOfRange(data, data.size - remaining, data.size)
                val encrypted = currentEncryptor.encryptAndMac(cla, ins, p1, p2, chunk, le)
                response = transmit(encrypted)
            }

            val sw1 = response[response.size - 2]
            val sw2 = response[response.size - 1]

            LoggingUtil.debugLog(
                "NfcSmartCardReader",
                String.format("R-APDU: SW1: 0x%02X, SW2: 0x%02X", sw1, sw2),
                null,
            )

            if (sw1 == 0x90.toByte() && sw2 == 0x00.toByte()) {
                return currentEncryptor.decryptAndVerify(response)
            } else if (sw1 == 0x61.toByte()) {
                val getResponse =
                    super.transmit(
                        0x00,
                        0xC0,
                        0x00,
                        0x00,
                        null,
                        java.lang.Integer.valueOf(
                            sw2.toInt() and 0xFF,
                        ),
                    )
                val combined = combineCompleteRApdu(response, getResponse)
                return currentEncryptor.decryptAndVerify(combined)
            } else {
                throw ApduResponseException(sw1, sw2)
            }
        } catch (e: GeneralSecurityException) {
            throw SmartCardReaderException(e)
        }
    }

    private fun combineCompleteRApdu(
        part1: ByteArray,
        part2: ByteArray,
    ): ByteArray {
        val combined = ByteArray(part1.size + part2.size)
        var offset = 0

        // Copy part1 data (excluding SW1/SW2)
        val dataLen1 = part1.size - 2
        System.arraycopy(part1, 0, combined, offset, dataLen1)
        offset += dataLen1

        // Copy part2 entirely
        System.arraycopy(part2, 0, combined, offset, part2.size)
        offset += part2.size

        // Append part1's SW1/SW2 to the very end
        System.arraycopy(part1, part1.size - 2, combined, offset, 2)
        return combined
    }
}
