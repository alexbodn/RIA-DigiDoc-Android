/*
 * Copyright 2017 - 2025 Riigi Infosüsteemi Amet
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

@file:Suppress("PackageName")

package ee.ria.DigiDoc.configuration.utils

import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil.Companion.errorLog
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.openssl.PEMParser
import java.io.IOException
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureException
import java.security.interfaces.ECPublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec

object SignatureVerifier {
    private val LOG_TAG = javaClass.simpleName

    fun verify(
        signature: ByteArray,
        publicKeyPEM: String,
        signedContent: String,
    ): Boolean {
        val publicKeyInfo = parsePublicKeyInfo(publicKeyPEM)
        val publicKey = convertPublicKeyInfoToPublicKey(publicKeyInfo)
        return verifySignature(signature, publicKey, signedContent)
    }

    private fun convertPublicKeyInfoToPublicKey(publicKeyInfo: SubjectPublicKeyInfo): PublicKey =
        try {
            val keySpec = X509EncodedKeySpec(publicKeyInfo.encoded)
            val keyFactory = KeyFactory.getInstance("EC")
            keyFactory.generatePublic(keySpec)
        } catch (e: InvalidKeySpecException) {
            errorLog(LOG_TAG, "PublicKey conversion failed", e)
            throw IllegalStateException(
                "Failed to convert SubjectPublicKeyInfo to EC java.security.PublicKey",
                e,
            )
        } catch (e: NoSuchAlgorithmException) {
            errorLog(LOG_TAG, "PublicKey conversion failed", e)
            throw IllegalStateException(
                "Failed to convert SubjectPublicKeyInfo to EC java.security.PublicKey",
                e,
            )
        } catch (e: IOException) {
            errorLog(LOG_TAG, "PublicKey conversion failed", e)
            throw IllegalStateException(
                "Failed to convert SubjectPublicKeyInfo to EC java.security.PublicKey",
                e,
            )
        }

    private fun parsePublicKeyInfo(publicKeyPem: String): SubjectPublicKeyInfo {
        try {
            PEMParser(StringReader(publicKeyPem)).use { pemParser ->
                return pemParser.readObject() as SubjectPublicKeyInfo
            }
        } catch (e: IOException) {
            throw IllegalStateException("Failed to parse PEM encoded public key", e)
        } catch (e: ClassCastException) {
            throw IllegalStateException(
                "PEM did not contain SubjectPublicKeyInfo. Make sure it's 'BEGIN PUBLIC KEY' (SPKI).",
                e,
            )
        }
    }

    private fun verifySignature(
        signatureBytes: ByteArray,
        publicKey: PublicKey,
        signedContent: String,
    ): Boolean =
        try {
            val jcaAlg = pickEcdsaAlgorithm(publicKey)
            val signature = Signature.getInstance(jcaAlg)
            signature.initVerify(publicKey)
            signature.update(signedContent.toByteArray(StandardCharsets.UTF_8))
            val result = signature.verify(signatureBytes)

            result
        } catch (e: NoSuchAlgorithmException) {
            errorLog(LOG_TAG, "Signature verification failed", e)
            throw IllegalStateException("Failed to verify signature", e)
        } catch (e: SignatureException) {
            errorLog(LOG_TAG, "Signature verification failed", e)
            throw IllegalStateException("Failed to verify signature", e)
        } catch (e: InvalidKeyException) {
            errorLog(LOG_TAG, "Signature verification failed", e)
            throw IllegalStateException("Failed to verify signature", e)
        } catch (e: java.lang.Exception) {
            errorLog(LOG_TAG, "Signature verification failed", e)
            throw IllegalStateException("Failed to verify signature", e)
        }

    private fun pickEcdsaAlgorithm(publicKey: PublicKey): String {
        val ecKey =
            publicKey as? ECPublicKey
                ?: throw IllegalStateException("Public key is not EC (ECDSA). Got: ${publicKey.algorithm}")

        val fieldSizeBits = ecKey.params.curve.field.fieldSize

        return when {
            fieldSizeBits <= 256 -> "SHA256withECDSA"
            fieldSizeBits <= 384 -> "SHA384withECDSA"
            else -> "SHA512withECDSA"
        }
    }
}
