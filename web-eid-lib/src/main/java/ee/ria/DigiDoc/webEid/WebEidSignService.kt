@file:Suppress("PackageName")

package ee.ria.DigiDoc.webEid

import org.json.JSONObject

interface WebEidSignService {
    fun buildCertificatePayload(signingCert: ByteArray): JSONObject

    fun buildSignPayload(
        signingCert: String,
        signature: ByteArray,
    ): JSONObject
}
