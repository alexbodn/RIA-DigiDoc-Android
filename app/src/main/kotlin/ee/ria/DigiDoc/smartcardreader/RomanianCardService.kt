/*
 * Copyright 2026 Riigi Infosüsteemi Amet
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */

package ee.ria.DigiDoc.smartcardreader

import android.nfc.tech.IsoDep
import net.sf.scuba.smartcards.APDUEvent
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CardServiceException
import net.sf.scuba.smartcards.CommandAPDU
import net.sf.scuba.smartcards.ResponseAPDU
import org.bouncycastle.util.encoders.Hex
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil.Companion.debugLog

class RomanianCardService(private val isoDep: IsoDep) : CardService() {
    private val logTag = "RomanianCardService"

    override fun open() {
        try {
            if (!isoDep.isConnected) {
                isoDep.connect()
            }
        } catch (e: Exception) {
            throw CardServiceException(e.message ?: "Failed to connect IsoDep")
        }
    }

    override fun isOpen(): Boolean {
        return isoDep.isConnected
    }

    override fun transmit(command: CommandAPDU): ResponseAPDU {
        try {
            val cmdBytes = command.bytes
            // Redact log to prevent leaking PINs (e.g. in VERIFY commands)
            debugLog(logTag, "TX: [Redacted for Security]")

            val respBytes = isoDep.transceive(cmdBytes)
            val sw1 = respBytes[respBytes.size - 2]
            val sw2 = respBytes[respBytes.size - 1]
            debugLog(logTag, "RX: [Redacted for Security] SW=${String.format("%02X%02X", sw1, sw2)}")

            // JMRTD expects CardService to notify listeners, though for this custom implementation
            // strictly for one-off use it might be optional, but good practice.
            // notifyExchange(APDUEvent(this, "transceive", 1, cmdBytes, respBytes))

            return ResponseAPDU(respBytes)
        } catch (e: Exception) {
            throw CardServiceException(e.message ?: "Transceive failed")
        }
    }

    override fun getATR(): ByteArray {
        // IsoDep doesn't provide ATR directly usually, often uses historical bytes
        return isoDep.historicalBytes ?: ByteArray(0)
    }

    override fun close() {
        try {
            isoDep.close()
        } catch (e: Exception) {
            // Ignore close errors
        }
    }

    override fun isConnectionLost(e: Exception): Boolean {
        return e.message?.contains("TagLostException") == true || !isoDep.isConnected
    }
}
