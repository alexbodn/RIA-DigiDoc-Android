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
