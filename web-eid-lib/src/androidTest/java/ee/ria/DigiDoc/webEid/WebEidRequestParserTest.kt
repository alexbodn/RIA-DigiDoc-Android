@file:Suppress("PackageName")

package ee.ria.DigiDoc.webEid

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ee.ria.DigiDoc.webEid.domain.model.WebEidAuthRequest
import ee.ria.DigiDoc.webEid.domain.model.WebEidSignRequest
import ee.ria.DigiDoc.webEid.exception.WebEidErrorCode
import ee.ria.DigiDoc.webEid.exception.WebEidException
import ee.ria.DigiDoc.webEid.utils.WebEidRequestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class WebEidRequestParserTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun parseAuthUri_validUri_success() {
        val loginUri = "https://rp.example.com/auth/eid/login"
        val uri = android.net.Uri.parse(createAuthUri("test-challenge-00000000000000000000000000000", loginUri, true))
        val result: WebEidAuthRequest = WebEidRequestParser.parseAuthUri(uri)

        assertEquals("test-challenge-00000000000000000000000000000", result.challenge)
        assertEquals(loginUri, result.loginUri)
        assertEquals(true, result.getSigningCertificate)
        assertTrue(result.origin.startsWith("https://rp.example.com"))
    }

    @Test
    fun parseAuthUri_missingScheme_throwsException() {
        val loginUri = "rp.example.com/auth/eid/login"
        val uri = android.net.Uri.parse(createAuthUri("abc1234", loginUri, false))

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                WebEidRequestParser.parseAuthUri(uri)
            }

        assertEquals("Invalid response URI scheme", exception.message)
    }

    @Test
    fun parseAuthUri_invalidScheme_throwsException() {
        val loginUri = "http://rp.example.com/auth/eid/login"
        val uri = android.net.Uri.parse(createAuthUri("abc1234", loginUri, false))

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                WebEidRequestParser.parseAuthUri(uri)
            }
        assertEquals("Response URI must use HTTPS scheme", exception.message)
    }

    @Test
    fun parseAuthUri_emptyHost_throwsException() {
        val loginUri = "https:///auth/eid/login"
        val uri = android.net.Uri.parse(createAuthUri("abc1234", loginUri, false))

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                WebEidRequestParser.parseAuthUri(uri)
            }

        assertEquals("Invalid response URI host", exception.message)
    }

    @Test
    fun parseAuthUri_forbiddenUserInfo_throwsException() {
        val loginUri = "https://rp.example.com:pass@evil.example.com/auth/eid/login"
        val uri = android.net.Uri.parse(createAuthUri("abc1235", loginUri, false))

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                WebEidRequestParser.parseAuthUri(uri)
            }
        assertTrue(exception.message!!.contains("Response URI must not contain userinfo"))
    }

    @Test
    fun parseAuthUri_invalidResponseUri_throwsException() {
        val loginUri = "://rp.example.com/auth/eid/login"
        val uri = android.net.Uri.parse(createAuthUri("abc1234", loginUri, false))

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                WebEidRequestParser.parseAuthUri(uri)
            }

        assertTrue(exception.message!!.contains("Invalid response URI"))
    }

    @Test
    fun parseAuthUri_invalidChallengeLength_throwsWebEidException() {
        val loginUri = "https://rp.example.com/auth/eid/login"
        val json =
            """
            {
              "challenge": "abc123",
              "login_uri": "$loginUri",
              "get_signing_certificate": false
            }
            """.trimIndent()

        val encoded = Base64.getEncoder().encodeToString(json.toByteArray())
        val uri = android.net.Uri.parse("web-eid://auth#$encoded")

        val exception =
            assertThrows(WebEidException::class.java) {
                WebEidRequestParser.parseAuthUri(uri)
            }

        assertEquals(WebEidErrorCode.ERR_WEBEID_MOBILE_INVALID_REQUEST, exception.errorCode)
        assertTrue(exception.message.contains("Invalid challenge length"))
        assertEquals(loginUri, exception.responseUri)
    }

    private fun createAuthUri(
        challenge: String,
        loginUri: String,
        getCert: Boolean,
    ): String {
        val json =
            """
            {
              "challenge": "$challenge",
              "login_uri": "$loginUri",
              "get_signing_certificate": $getCert
            }
            """.trimIndent()
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray())
        return "web-eid://auth#$encoded"
    }

    @Test
    fun parseAuthUri_invalidBase64_throwsException() {
        val uri = android.net.Uri.parse("web-eid://auth#%%%INVALID%%%")
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                WebEidRequestParser.parseAuthUri(uri)
            }
        assertTrue(exception.message!!.contains("Invalid URI fragment"))
    }

    @Test
    fun parseAuthUri_originTooLong_throwsWebEidException() {
        val longHost = "a".repeat(260)
        val loginUri = "https://$longHost.com/auth/eid/login"

        val json =
            """
            {
              "challenge": "${"b".repeat(60)}",
              "login_uri": "$loginUri",
              "get_signing_certificate": false
            }
            """.trimIndent()

        val encoded = Base64.getEncoder().encodeToString(json.toByteArray())
        val uri = android.net.Uri.parse("web-eid://auth#$encoded")

        val exception =
            assertThrows(WebEidException::class.java) {
                WebEidRequestParser.parseAuthUri(uri)
            }

        assertEquals(WebEidErrorCode.ERR_WEBEID_MOBILE_INVALID_REQUEST, exception.errorCode)
        assertTrue(exception.message.contains("Invalid origin length"))
    }

    @Test
    fun parseSignUri_valid_withHashAndFunction_success() {
        val responseUri = "https://rp.example.com/sign/response"
        val uri = android.net.Uri.parse(createSignUri("abcd1234hash", "SHA-384"))
        val result: WebEidSignRequest = WebEidRequestParser.parseSignUri(uri)

        assertEquals(responseUri, result.responseUri)
        assertEquals("abcd1234hash", result.hash)
        assertEquals("SHA-384", result.hashFunction)
    }

    @Test
    fun parseCertificateUri_valid_success() {
        val responseUri = "https://rp.example.com/sign/response"
        val uri = android.net.Uri.parse(createSignUri(null, null))
        val result: WebEidSignRequest = WebEidRequestParser.parseCertificateUri(uri)

        assertEquals(responseUri, result.responseUri)
        assertNull(result.hash)
        assertNull(result.hashFunction)
    }

    @Test
    fun parseSignUri_invalidBase64_throwsException() {
        val uri = android.net.Uri.parse("web-eid://sign#%%%INVALID%%%")
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                WebEidRequestParser.parseSignUri(uri)
            }
        assertTrue(exception.message!!.contains("Invalid URI fragment"))
    }

    private fun createSignUri(
        hash: String?,
        hashFunction: String?,
    ): String {
        val responseUri = "https://rp.example.com/sign/response"
        val sb = StringBuilder()
        sb.append("{\"response_uri\":\"$responseUri\"")
        if (hash != null) sb.append(",\"hash\":\"$hash\"")
        if (hashFunction != null) sb.append(",\"hash_function\":\"$hashFunction\"")
        sb.append("}")
        val encoded = Base64.getEncoder().encodeToString(sb.toString().toByteArray())
        return "web-eid://sign#$encoded"
    }
}
