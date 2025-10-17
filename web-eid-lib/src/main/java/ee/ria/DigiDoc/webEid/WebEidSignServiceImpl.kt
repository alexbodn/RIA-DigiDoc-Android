@file:Suppress("PackageName")

package ee.ria.DigiDoc.webEid

import ee.ria.DigiDoc.webEid.utils.WebEidAlgorithmUtil.buildSupportedSignatureAlgorithms
import ee.ria.DigiDoc.webEid.utils.WebEidAlgorithmUtil.getAlgorithm
import org.json.JSONObject
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebEidSignServiceImpl
    @Inject
    constructor() : WebEidSignService {
        override fun buildCertificatePayload(signingCert: ByteArray): JSONObject {
            val cert =
                CertificateFactory
                    .getInstance("X.509")
                    .generateCertificate(signingCert.inputStream()) as X509Certificate
            val publicKey = cert.publicKey
            val supportedSignatureAlgorithms = buildSupportedSignatureAlgorithms(publicKey)

            return JSONObject().apply {
                put("certificate", Base64.getEncoder().encodeToString(signingCert))
                put("supportedSignatureAlgorithms", supportedSignatureAlgorithms)
            }
        }

        override fun buildSignPayload(
            signingCert: String,
            signature: ByteArray,
        ): JSONObject {
            val certBytes = Base64.getDecoder().decode(signingCert)
            val cert =
                CertificateFactory
                    .getInstance("X.509")
                    .generateCertificate(certBytes.inputStream()) as X509Certificate

            val publicKey = cert.publicKey
            val algorithm = getAlgorithm(publicKey)

            return JSONObject().apply {
                put("signature", Base64.getEncoder().encodeToString(signature))
                put("signatureAlgorithm", algorithm)
                put("signingCertificate", signingCert)
            }
        }
    }
