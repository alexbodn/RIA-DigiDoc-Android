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
class WebEidAuthServiceImpl
    @Inject
    constructor() : WebEidAuthService {
        override fun buildAuthToken(
            authCert: ByteArray,
            signingCert: ByteArray?,
            signature: ByteArray,
        ): JSONObject {
            val cert =
                CertificateFactory
                    .getInstance("X.509")
                    .generateCertificate(authCert.inputStream()) as X509Certificate

            val publicKey = cert.publicKey
            val algorithm = getAlgorithm(publicKey)

            return JSONObject().apply {
                put("algorithm", algorithm)
                put("unverifiedCertificate", Base64.getEncoder().encodeToString(authCert))
                put("issuerApp", "https://web-eid.eu/web-eid-mobile-app/releases/v1.0.0")
                put("signature", Base64.getEncoder().encodeToString(signature))

                if (signingCert != null) {
                    val supportedSignatureAlgorithms = buildSupportedSignatureAlgorithms(publicKey)
                    put("unverifiedSigningCertificate", Base64.getEncoder().encodeToString(signingCert))
                    put("supportedSignatureAlgorithms", supportedSignatureAlgorithms)
                    put("format", "web-eid:1.1")
                } else {
                    put("format", "web-eid:1.0")
                }
            }
        }
    }
