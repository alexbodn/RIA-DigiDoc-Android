@file:Suppress("ktlint:standard:package-name", "ktlint:standard:max-line-length")
package ee.ria.DigiDoc.smartcardreader.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import ee.ria.DigiDoc.smartcardreader.SmartCardReader
import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException
import java.io.IOException
import java.util.Arrays

class NfcSmartCardReader(private val tag: Tag) : SmartCardReader() {

    private var isoDep: IsoDep? = null
    private var apduEncryptor: ApduEncryptor? = null
    private val historicalBytes: ByteArray

    init {
        try {
            isoDep = IsoDep.get(tag)
            if (isoDep == null) {
                throw SmartCardReaderException("Tag does not support IsoDep")
            }

            // Connect immediately
            isoDep?.connect()
            isoDep?.timeout = 20000 // Ensure extended timeout for PACE

            // Save historical bytes (ATR equivalent)
            historicalBytes = isoDep?.historicalBytes ?: ByteArray(0)

            // Bypass ALL ATS checks! Allow ANY IsoDep card to connect.
            // (The original AAR code checked historicalBytes against a whitelist and threw "ATS not supported" here)
        } catch (e: IOException) {
            throw SmartCardReaderException("Failed to connect to NFC tag", e)
        }
    }

    override fun close() {
        try {
            isoDep?.close()
        } catch (e: IOException) {
            // Ignore
        }
    }

    override fun connected(): Boolean {
        return isoDep?.isConnected == true
    }

    override fun atr(): ByteArray {
        return historicalBytes
    }

    fun setApduEncryptor(encryptor: ApduEncryptor?) {
        this.apduEncryptor = encryptor
    }

    @Throws(SmartCardReaderException::class)
    override fun transmit(command: ByteArray): ByteArray {
        val response: ByteArray
        try {
            response = isoDep?.transceive(command)
                ?: throw SmartCardReaderException("IsoDep is null or disconnected")
        } catch (e: IOException) {
            throw SmartCardReaderException("Transceive failed: ${e.message}", e)
        }

        return response
    }

    @Throws(SmartCardReaderException::class)
    override fun transmit(cla: Int, ins: Int, p1: Int, p2: Int, data: ByteArray?, le: Int?): ByteArray {
        var transmitCmd: ByteArray? = null

        val currentEncryptor = apduEncryptor
        if (currentEncryptor != null) {
            try {
                transmitCmd = currentEncryptor.encryptAndMac(cla, ins, p1, p2, data, if (le != null) Integer.valueOf(le) else null)
            } catch (e: Exception) {
                throw SmartCardReaderException("Failed to encrypt APDU", e)
            }
        }

        if (transmitCmd == null) {
            val cmdLen = 4 + (data?.size ?: 0) + if (le != null) 1 else 0
            transmitCmd = ByteArray(cmdLen)
            transmitCmd[0] = cla.toByte()
            transmitCmd[1] = ins.toByte()
            transmitCmd[2] = p1.toByte()
            transmitCmd[3] = p2.toByte()

            var offset = 4
            if (data != null && data.isNotEmpty()) {
                transmitCmd[offset++] = data.size.toByte()
                System.arraycopy(data, 0, transmitCmd, offset, data.size)
                offset += data.size
            }

            if (le != null) {
                transmitCmd[offset] = le.toInt().toByte()
            }
        }

        val response = transmit(transmitCmd)

        if (currentEncryptor != null) {
            try {
                return currentEncryptor.decryptAndVerify(response)
            } catch (e: Exception) {
                throw SmartCardReaderException("Failed to decrypt APDU", e)
            }
        }

        return response
    }
}
