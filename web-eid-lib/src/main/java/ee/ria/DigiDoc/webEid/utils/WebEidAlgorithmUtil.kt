/*
 * Copyright 2017 - 2026 Riigi Infosüsteemi Amet
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

package ee.ria.DigiDoc.webEid.utils

import org.json.JSONArray
import org.json.JSONObject
import java.security.PublicKey
import java.security.interfaces.ECPublicKey

object WebEidAlgorithmUtil {
    private val SUPPORTED_HASH_FUNCTIONS =
        listOf(
            "SHA-224",
            "SHA-256",
            "SHA-384",
            "SHA-512",
            "SHA3-224",
            "SHA3-256",
            "SHA3-384",
            "SHA3-512",
        )

    fun buildSupportedSignatureAlgorithms(publicKey: PublicKey): JSONArray =
        JSONArray().apply {
            when (publicKey) {
                is ECPublicKey -> {
                    SUPPORTED_HASH_FUNCTIONS.forEach { hashFunction ->
                        put(
                            JSONObject().apply {
                                put("cryptoAlgorithm", "ECC")
                                put("hashFunction", hashFunction)
                                put("paddingScheme", "NONE")
                            },
                        )
                    }
                }

                else -> throw IllegalArgumentException("Unsupported key type")
            }
        }

    fun getAlgorithm(publicKey: PublicKey): String =
        when (publicKey) {
            is ECPublicKey -> {
                when (publicKey.params.curve.field.fieldSize) {
                    256 -> "ES256"
                    384 -> "ES384"
                    521 -> "ES512"
                    else -> throw IllegalArgumentException("Unsupported EC key length")
                }
            }

            else -> throw IllegalArgumentException("Unsupported key type")
        }
}
