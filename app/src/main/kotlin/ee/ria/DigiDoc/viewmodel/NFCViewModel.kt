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

@file:Suppress("PackageName", "MaxLineLength")

package ee.ria.DigiDoc.viewmodel

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableMap
import dagger.hilt.android.lifecycle.HiltViewModel
import ee.ria.DigiDoc.R
import ee.ria.DigiDoc.common.Constant.NFCConstants.CAN_LENGTH
import ee.ria.DigiDoc.common.Constant.SignatureRequest.SIGNATURE_PROFILE_TS
import ee.ria.DigiDoc.configuration.repository.ConfigurationRepository
import ee.ria.DigiDoc.cryptolib.CDOC2Settings
import ee.ria.DigiDoc.cryptolib.CryptoContainer
import ee.ria.DigiDoc.domain.model.IdCardData
import ee.ria.DigiDoc.domain.service.IdCardService
import ee.ria.DigiDoc.idcard.CertificateType
import ee.ria.DigiDoc.idcard.CodeType
import ee.ria.DigiDoc.idcard.PaceTunnelException
import ee.ria.DigiDoc.idcard.TokenWithPace
import ee.ria.DigiDoc.libdigidoclib.SignedContainer
import ee.ria.DigiDoc.libdigidoclib.domain.model.ContainerWrapper
import ee.ria.DigiDoc.libdigidoclib.domain.model.RoleData
import ee.ria.DigiDoc.libdigidoclib.domain.model.ValidatorInterface
import ee.ria.DigiDoc.network.sid.dto.response.SessionStatusResponseProcessStatus
import ee.ria.DigiDoc.network.utils.SendDiagnostics
import ee.ria.DigiDoc.network.utils.UserAgentUtil
import ee.ria.DigiDoc.smartcardreader.ApduResponseException
import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReaderManager
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReaderManager.NfcStatus
import ee.ria.DigiDoc.utils.pin.PinCodeUtil.isPINLengthValid
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil.Companion.debugLog
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil.Companion.errorLog
import ee.ria.libdigidocpp.ExternalSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.bouncycastle.util.encoders.Hex
import java.util.Arrays
import java.util.Base64
import javax.inject.Inject
import android.nfc.tech.IsoDep
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import ee.ria.DigiDoc.domain.model.RomanianPersonalData
import ee.ria.DigiDoc.common.model.EIDType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.jmrtd.protocol.PACEProtocol
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import ee.ria.DigiDoc.smartcardreader.RomanianCardService
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CommandAPDU
import net.sf.scuba.smartcards.ResponseAPDU
import org.jmrtd.lds.icao.MRZInfo
import org.jmrtd.PACEKeySpec
import java.math.BigInteger

@HiltViewModel
class NFCViewModel
    @Inject
    constructor(
        private val nfcSmartCardReaderManager: NfcSmartCardReaderManager,
        private val containerWrapper: ContainerWrapper,
        private val cdoc2Settings: CDOC2Settings,
        private val configurationRepository: ConfigurationRepository,
        private val idCardService: IdCardService,
    ) : ViewModel() {
        private val logTag = javaClass.simpleName

        private val _signedContainer = MutableLiveData<SignedContainer?>(null)
        val signedContainer: LiveData<SignedContainer?> = _signedContainer
        private val _cryptoContainer = MutableLiveData<CryptoContainer?>(null)
        val cryptoContainer: LiveData<CryptoContainer?> = _cryptoContainer

        private val _errorState = MutableLiveData<Triple<Int, String?, Int?>?>(null)
        val errorState: LiveData<Triple<Int, String?, Int?>?> = _errorState

        private val _message = MutableLiveData<Int?>(null)
        val message: LiveData<Int?> = _message
        private val _nfcStatus = MutableLiveData<NfcStatus?>(null)
        val nfcStatus: LiveData<NfcStatus?> = _nfcStatus

        private val _signStatus = MutableLiveData<Boolean?>(null)
        val signStatus: LiveData<Boolean?> = _signStatus
        private val _decryptStatus = MutableLiveData<Boolean?>(null)
        val decryptStatus: LiveData<Boolean?> = _decryptStatus
        private val _shouldResetPIN = MutableLiveData(false)
        val shouldResetPIN: LiveData<Boolean> = _shouldResetPIN
        private val _userData = MutableLiveData<IdCardData?>(null)
        val userData: LiveData<IdCardData?> = _userData
        private val _dialogError = MutableLiveData(0)
        val dialogError: LiveData<Int> = _dialogError

        private val dialogMessages: ImmutableMap<SessionStatusResponseProcessStatus, Int> =
            ImmutableMap
                .builder<SessionStatusResponseProcessStatus, Int>()
                .put(
                    SessionStatusResponseProcessStatus.TOO_MANY_REQUESTS,
                    R.string.too_many_requests_message,
                ).put(
                    SessionStatusResponseProcessStatus.OCSP_INVALID_TIME_SLOT,
                    R.string.invalid_time_slot_message,
                ).build()

        fun resetErrorState() {
            _errorState.postValue(null)
        }

        fun resetSignStatus() {
            _signStatus.postValue(null)
        }

        fun resetDecryptStatus() {
            _decryptStatus.postValue(null)
        }

        fun resetSignedContainer() {
            _signedContainer.postValue(null)
        }

        fun resetCryptoContainer() {
            _cryptoContainer.postValue(null)
        }

        fun resetShouldResetPIN() {
            _shouldResetPIN.postValue(false)
        }

        fun shouldShowCANNumberError(canNumber: String?): Boolean =
            (
                !canNumber.isNullOrEmpty() &&
                    !isCANLengthValid(canNumber)
            )

        fun isCANLengthValid(canNumber: String): Boolean = canNumber.length == CAN_LENGTH

        fun positiveButtonEnabled(
            canNumber: String?,
            pinCode: ByteArray?,
            codeType: CodeType,
        ): Boolean {
            if (canNumber != null && pinCode != null) {
                return isCANLengthValid(canNumber) &&
                    isPINLengthValid(pinCode, codeType)
            }
            return false
        }

        fun getNFCStatus(activity: Activity): NfcStatus = NfcStatus.NFC_ACTIVE

        private fun resetValues() {
            _errorState.postValue(null)
            _message.postValue(null)
            _signStatus.postValue(null)
            _decryptStatus.postValue(null)
            _nfcStatus.postValue(null)
        }

        private fun resetNonErrorValues() {
            _message.postValue(null)
            _signStatus.postValue(null)
            _decryptStatus.postValue(null)
            _nfcStatus.postValue(null)
        }

        suspend fun removePendingSignature(signedContainer: SignedContainer) {
            val signatures = signedContainer.getSignatures(Main)
            if (signatures.isNotEmpty()) {
                val lastSignatureStatus = signatures.last().validator.status
                if (lastSignatureStatus == ValidatorInterface.Status.Invalid ||
                    lastSignatureStatus == ValidatorInterface.Status.Unknown
                ) {
                    signedContainer.removeSignature(signatures.last())
                }
            }
        }

        suspend fun cancelNFCSignWorkRequest(signedContainer: SignedContainer) {
            removePendingSignature(signedContainer)

            nfcSmartCardReaderManager.disableNfcReaderMode()
        }

        fun cancelNFCDecryptWorkRequest() {
            nfcSmartCardReaderManager.disableNfcReaderMode()
        }

        suspend fun checkNFCStatus(nfcStatus: NfcStatus) {
            withContext(Main) {
                _nfcStatus.postValue(nfcStatus)
                when (nfcStatus) {
                    NfcStatus.NFC_NOT_SUPPORTED -> _message.postValue(R.string.signature_update_nfc_adapter_missing)
                    NfcStatus.NFC_NOT_ACTIVE -> _message.postValue(R.string.signature_update_nfc_turned_off)
                    NfcStatus.NFC_ACTIVE -> _message.postValue(R.string.signature_update_nfc_hold)
                }
            }
        }

        suspend fun performNFCSignWorkRequest(
            activity: Activity,
            context: Context,
            container: SignedContainer?,
            pin2Code: ByteArray?,
            canNumber: String,
            roleData: RoleData?,
        ) {
            val pinType = context.getString(R.string.signature_id_card_pin2)
            activity.requestedOrientation = activity.resources.configuration.orientation
            resetValues()

            if (container != null) {
                withContext(Main) {
                    _message.postValue(R.string.signature_update_nfc_hold)
                }

                checkNFCStatus(
                    nfcSmartCardReaderManager.startDiscovery(activity) { nfcReader, exc ->
                        if ((nfcReader != null) && (exc == null)) {
                            try {
                                CoroutineScope(Main).launch {
                                    _message.postValue(R.string.signature_update_nfc_detected)
                                }
                                val card = TokenWithPace.create(nfcReader)
                                card.tunnel(canNumber)
                                val signerCert = card.certificate(CertificateType.SIGNING)
                                debugLog(
                                    logTag,
                                    "Signer certificate: " + Base64.getEncoder().encodeToString(signerCert),
                                )

                                val signer = ExternalSigner(signerCert)
                                signer.setProfile(SIGNATURE_PROFILE_TS)
                                signer.setUserAgent(UserAgentUtil.getUserAgent(context, SendDiagnostics.NFC))

                                val dataToSignBytes =
                                    containerWrapper.prepareSignature(signer, container, signerCert, roleData)

                                val signatureArray =
                                    card.calculateSignature(pin2Code, dataToSignBytes, true)
                                if (null != pin2Code && pin2Code.isNotEmpty()) {
                                    Arrays.fill(pin2Code, 0.toByte())
                                }
                                debugLog(logTag, "Signature: " + Hex.toHexString(signatureArray))

                                containerWrapper.finalizeSignature(
                                    signer,
                                    container,
                                    signatureArray,
                                )

                                CoroutineScope(Main).launch {
                                    _shouldResetPIN.postValue(true)
                                    _signStatus.postValue(true)
                                    _signedContainer.postValue(container)
                                }
                            } catch (ex: SmartCardReaderException) {
                                _signStatus.postValue(false)

                                if (ex.message?.contains("TagLostException") == true) {
                                    _errorState.postValue(
                                        Triple(
                                            R.string.signature_update_nfc_tag_lost,
                                            null,
                                            null,
                                        ),
                                    )
                                } else if (ex.message?.contains("PIN2 has not been changed") == true) {
                                    _dialogError.postValue(R.string.sign_blocked_pin2_unchanged_message)
                                } else if (ex.message?.contains("PIN2 verification failed") == true &&
                                    ex.message?.contains("Retries left: 2") == true
                                ) {
                                    _shouldResetPIN.postValue(true)
                                    _errorState.postValue(
                                        Triple(
                                            R.string.id_card_sign_pin_invalid,
                                            pinType,
                                            2,
                                        ),
                                    )
                                } else if (ex.message?.contains("PIN2 verification failed") == true &&
                                    ex.message?.contains("Retries left: 1") == true
                                ) {
                                    _shouldResetPIN.postValue(true)
                                    _errorState.postValue(
                                        Triple(
                                            R.string.id_card_sign_pin_invalid_final,
                                            pinType,
                                            null,
                                        ),
                                    )
                                } else if (ex.message?.contains("PIN2 verification failed") == true &&
                                    ex.message?.contains("Retries left: 0") == true
                                ) {
                                    _shouldResetPIN.postValue(true)
                                    _errorState.postValue(
                                        Triple(R.string.id_card_sign_pin_locked, pinType, null),
                                    )
                                } else if (ex is ApduResponseException) {
                                    _errorState.postValue(
                                        Triple(R.string.signature_update_nfc_technical_error, null, null),
                                    )
                                } else if (ex is PaceTunnelException) {
                                    _errorState.postValue(
                                        Triple(R.string.signature_update_nfc_wrong_can, null, null),
                                    )
                                } else {
                                    showTechnicalError(ex)
                                }

                                errorLog(logTag, "Exception: " + ex.message, ex)
                            } catch (ex: Exception) {
                                _signStatus.postValue(false)
                                _shouldResetPIN.postValue(true)

                                val message = ex.message ?: ""

                                when {
                                    message.contains("Failed to connect") ||
                                        message.contains("Failed to create connection with host") ->
                                        showNetworkError(ex)
                                    message.contains(
                                        "Failed to create proxy connection with host",
                                    ) -> showProxyError(ex)
                                    message.contains("Too Many Requests") ->
                                        setErrorState(
                                            SessionStatusResponseProcessStatus.TOO_MANY_REQUESTS,
                                        )
                                    message.contains("OCSP response not in valid time slot") ->
                                        setErrorState(
                                            SessionStatusResponseProcessStatus.OCSP_INVALID_TIME_SLOT,
                                        )
                                    message.contains("Certificate status: revoked") -> showRevokedCertificateError(ex)
                                    message.contains("Certificate status: unknown") -> showUnknownCertificateError(ex)
                                    else -> showTechnicalError(ex)
                                }

                                errorLog(logTag, "Exception: " + ex.message, ex)
                            } finally {
                                if (null != pin2Code && pin2Code.isNotEmpty()) {
                                    Arrays.fill(pin2Code, 0.toByte())
                                }
                                nfcSmartCardReaderManager.disableNfcReaderMode()
                                activity.requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }
                    },
                )
            } else {
                withContext(Main) {
                    _nfcStatus.postValue(nfcSmartCardReaderManager.detectNfcStatus(activity))
                    _signStatus.postValue(false)
                    _errorState.postValue(Triple(R.string.error_general_client, null, null))
                    errorLog(logTag, "Unable to get container value. Container is 'null'")
                }
            }
        }

        suspend fun performNFCDecryptWorkRequest(
            activity: Activity,
            context: Context,
            container: CryptoContainer?,
            pin1Code: ByteArray,
            canNumber: String,
        ) {
            val pinType = context.getString(R.string.signature_id_card_pin1)
            activity.requestedOrientation = activity.resources.configuration.orientation
            resetValues()

            if (container != null) {
                withContext(Main) {
                    _message.postValue(R.string.signature_update_nfc_hold)
                }

                checkNFCStatus(
                    nfcSmartCardReaderManager.startDiscovery(activity) { nfcReader, exc ->
                        if ((nfcReader != null) && (exc == null)) {
                            try {
                                CoroutineScope(Main).launch {
                                    _message.postValue(R.string.signature_update_nfc_detected)
                                }
                                val card = TokenWithPace.create(nfcReader)
                                card.tunnel(canNumber)

                                val authCert =
                                    card.certificate(CertificateType.AUTHENTICATION)
                                debugLog(
                                    logTag,
                                    "Auth certificate: " + Base64.getEncoder().encodeToString(authCert),
                                )
                                val decryptedContainer =
                                    CryptoContainer.decrypt(
                                        context,
                                        container.file,
                                        container.recipients,
                                        authCert,
                                        pin1Code,
                                        card,
                                        cdoc2Settings,
                                        configurationRepository,
                                    )
                                if (pin1Code.isNotEmpty()) {
                                    Arrays.fill(pin1Code, 0.toByte())
                                }
                                CoroutineScope(Main).launch {
                                    _shouldResetPIN.postValue(true)
                                    _decryptStatus.postValue(true)
                                    _cryptoContainer.postValue(decryptedContainer)
                                }
                            } catch (ex: SmartCardReaderException) {
                                _decryptStatus.postValue(false)

                                if (ex.message?.contains("TagLostException") == true) {
                                    _errorState.postValue(Triple(R.string.signature_update_nfc_tag_lost, null, null))
                                } else if (ex.message?.contains("PIN1 verification failed") == true &&
                                    ex.message?.contains("Retries left: 2") == true
                                ) {
                                    _shouldResetPIN.postValue(true)
                                    _errorState.postValue(
                                        Triple(
                                            R.string.id_card_sign_pin_invalid,
                                            pinType,
                                            2,
                                        ),
                                    )
                                } else if (ex.message?.contains("PIN1 verification failed") == true &&
                                    ex.message?.contains("Retries left: 1") == true
                                ) {
                                    _shouldResetPIN.postValue(true)
                                    _errorState.postValue(
                                        Triple(
                                            R.string.id_card_sign_pin_invalid_final,
                                            pinType,
                                            null,
                                        ),
                                    )
                                } else if (ex.message?.contains("PIN1 verification failed") == true &&
                                    ex.message?.contains("Retries left: 0") == true
                                ) {
                                    _shouldResetPIN.postValue(true)
                                    _errorState.postValue(
                                        Triple(
                                            R.string.id_card_sign_pin_locked,
                                            pinType,
                                            null,
                                        ),
                                    )
                                } else if (ex is ApduResponseException) {
                                    _errorState.postValue(
                                        Triple(R.string.signature_update_nfc_technical_error, null, null),
                                    )
                                } else if (ex is PaceTunnelException) {
                                    _errorState.postValue(
                                        Triple(R.string.signature_update_nfc_wrong_can, null, null),
                                    )
                                } else {
                                    showTechnicalError(ex)
                                }

                                errorLog(logTag, "Exception: " + ex.message, ex)
                            } catch (ex: Exception) {
                                _decryptStatus.postValue(false)
                                _shouldResetPIN.postValue(true)

                                val message = ex.message ?: ""

                                when {
                                    message.contains("Failed to connect") ||
                                        message.contains("Failed to create connection with host") ->
                                        showNetworkError(ex)

                                    message.contains(
                                        "Failed to create proxy connection with host",
                                    ) -> showProxyError(ex)

                                    message.contains("Too Many Requests") ->
                                        setErrorState(
                                            SessionStatusResponseProcessStatus.TOO_MANY_REQUESTS,
                                        )

                                    message.contains("OCSP response not in valid time slot") ->
                                        setErrorState(
                                            SessionStatusResponseProcessStatus.OCSP_INVALID_TIME_SLOT,
                                        )
                                    message.contains("No lock found with certificate key") ->
                                        showNoLockFoundError(ex)

                                    message.contains("Certificate status: revoked") -> showRevokedCertificateError(ex)
                                    message.contains("Certificate status: unknown") -> showUnknownCertificateError(ex)

                                    else -> showTechnicalError(ex)
                                }

                                errorLog(logTag, "Exception: " + ex.message, ex)
                            } finally {
                                if (pin1Code.isNotEmpty()) {
                                    Arrays.fill(pin1Code, 0.toByte())
                                }
                                nfcSmartCardReaderManager.disableNfcReaderMode()
                                activity.requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }
                    },
                )
            } else {
                withContext(Main) {
                    _nfcStatus.postValue(nfcSmartCardReaderManager.detectNfcStatus(activity))
                    _decryptStatus.postValue(false)
                    _errorState.postValue(Triple(R.string.error_general_client, null, null))
                    errorLog(logTag, "Unable to get container value. Container is 'null'")
                    _shouldResetPIN.postValue(true)
                }
            }
        }

        suspend fun loadPersonalData(
            activity: Activity,
            canNumber: String,
        ) {
            activity.requestedOrientation = activity.resources.configuration.orientation

            checkNFCStatus(
                nfcSmartCardReaderManager.startDiscovery(activity) { nfcReader, exc ->
                    if ((nfcReader != null) && (exc == null)) {
                        debugLog(logTag, "NFC Reader detected: ${nfcReader.javaClass.name}")
                        viewModelScope.launch {
                            _message.postValue(R.string.signature_update_nfc_detected)
                        }
                        try {
                            val isoDep = getIsoDep(nfcReader)
                            if (isoDep != null) {
                                debugLog(logTag, "IsoDep extracted via reflection. Starting Romanian discovery.")
                                // Romanian / Direct IsoDep Path
                                // "Run only yours for the moment" - Exclusive execution
                                val romanianData = tryRomanianDiscovery(isoDep, canNumber)
                                debugLog(logTag, "Romanian discovery success. Data: ${romanianData.personalData.givenNames()} ${romanianData.personalData.surname()}")
                                _userData.postValue(romanianData)
                            } else {
                                debugLog(logTag, "IsoDep extraction FAILED. Legacy path disabled.")
                                // Legacy Path disabled as per user request to debug IsoDep extraction
                                throw SmartCardReaderException("IsoDep extraction failed (Reflection). Legacy path disabled.")
                            }
                        } catch (e: Exception) {
                            errorLog(logTag, "Discovery failed", e)
                            resetIdCardUserData()

                            if (e.message?.contains("TagLostException") == true) {
                                _errorState.postValue(
                                    Triple(R.string.signature_update_nfc_tag_lost, null, null),
                                )
                            } else if (e is ApduResponseException) {
                                _errorState.postValue(
                                    Triple(R.string.signature_update_nfc_technical_error, null, null),
                                )
                            } else if (e is PaceTunnelException) {
                                _errorState.postValue(
                                    Triple(R.string.signature_update_nfc_wrong_can, null, null),
                                )
                            } else {
                                // DEBUG: Using version title to display dynamic exception message
                                _errorState.postValue(
                                    Triple(R.string.main_about_version_title, e.message ?: "Unknown error", null),
                                )
                            }

                            errorLog(
                                logTag,
                                "Unable to get ID-card personal data: ${e.message}",
                                e,
                            )

                            resetNonErrorValues()
                        } finally {
                            nfcSmartCardReaderManager.disableNfcReaderMode()
                            activity.requestedOrientation =
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }
                },
            )
        }

        fun handleBackButton() {
            _shouldResetPIN.postValue(true)
            resetValues()
        }

        fun resetDialogErrorState() {
            _dialogError.postValue(0)
        }

        fun resetIdCardUserData() {
            _userData.postValue(null)
        }

        private fun setErrorState(status: SessionStatusResponseProcessStatus) {
            val res = dialogMessages[status]

            if (res == R.string.too_many_requests_message ||
                res == R.string.invalid_time_slot_message
            ) {
                _dialogError.postValue(res)
            } else {
                _errorState.postValue(res?.let { Triple(it, null, null) })
            }
        }

        private fun showNetworkError(e: Exception) {
            _errorState.postValue(Triple(R.string.no_internet_connection, null, null))
            errorLog(logTag, "Unable to sign with NFC - Unable to connect to Internet", e)
        }

        private fun showProxyError(e: Exception) {
            _errorState.postValue(Triple(R.string.main_settings_proxy_invalid_settings, null, null))
            errorLog(logTag, "Unable to sign with NFC - Unable to create proxy connection with host", e)
        }

        private fun showNoLockFoundError(e: Exception) {
            _errorState.postValue(Triple(R.string.no_lock_found, null, null))
            errorLog(logTag, "Unable to decrypt with NFC - No lock found with certificate key", e)
        }

        private fun showRevokedCertificateError(e: Exception) {
            _errorState.postValue(
                Triple(
                    R.string.signature_update_signature_error_message_certificate_revoked,
                    null,
                    null,
                ),
            )
            errorLog(logTag, "Unable to sign with NFC - Certificate status: revoked", e)
        }

        private fun showUnknownCertificateError(e: Exception) {
            _errorState.postValue(
                Triple(
                    R.string.signature_update_signature_error_message_certificate_unknown,
                    null,
                    null,
                ),
            )
            errorLog(logTag, "Unable to sign with NFC - Certificate status: unknown", e)
        }

        private fun showTechnicalError(e: Exception) {
            _errorState.postValue(Triple(R.string.signature_update_nfc_technical_error, null, null))
            errorLog(logTag, "Unable to perform with NFC: ${e.message}", e)
        }

    private fun readDataGroupManual(isoDep: IsoDep, wrapper: org.jmrtd.protocol.SecureMessagingWrapper, sfi: Byte): ByteArray {
        // Manual Secure Read using Implicit SFI Addressing (skips SELECT)
        // Required for cards that return 6E00 on SELECT commands during Secure Messaging

        val buffer = java.io.ByteArrayOutputStream()
        var offset = 0
        val blockSize = 220
        var hasMore = true

        debugLog(logTag, "Manual Read (SFI): Starting implicit read for SFI $sfi using wrapper: ${wrapper.javaClass.simpleName}")

        while (hasMore) {
            // Implicit SFI addressing for READ BINARY:
            // If offset == 0: P1 = 0x80 | SFI. P2 = 0.
            // If offset > 0: The file is implicitly selected. Use standard P1/P2 for offset.

            val p1: Int
            val p2: Int

            if (offset == 0) {
                p1 = 0x80 or sfi.toInt()
                p2 = 0
            } else {
                p1 = (offset shr 8) and 0xFF
                p2 = offset and 0xFF
            }

            // 00 B0 P1 P2 Le
            // We use CLA 0x00 initially. The wrapper should transform it to 0x8C or 0x0C.
            val readCmd = CommandAPDU(0x00, 0xB0, p1, p2, blockSize)
            val wrappedRead = wrapper.wrap(readCmd)

            val wrappedBytes = wrappedRead.bytes
            debugLog(logTag, "Sending Read (Offset $offset): ${Hex.toHexString(wrappedBytes)}")

            val readRespBytes = isoDep.transceive(wrappedBytes)
            debugLog(logTag, "Received Response (Raw): ${Hex.toHexString(readRespBytes)}")

            val readResp = ResponseAPDU(readRespBytes)

            // If the card returns 6E00, it means Class Not Supported.
            // This suggests the CLA set by wrapper.wrap() is rejected.
            if (readResp.sw == 0x6E00) {
                 throw SmartCardReaderException("Manual Read Failed: Card rejected APDU Class (SW=6E00). Sent CLA: " + String.format("%02X", wrappedBytes[0]))
            }

            val unwrappedRead = wrapper.unwrap(readResp)

            if (unwrappedRead.sw == 0x9000) {
                val data = unwrappedRead.data
                debugLog(logTag, "Decrypted Data (Offset $offset): ${Hex.toHexString(data)}")
                buffer.write(data)
                offset += data.size
                if (data.size < blockSize) {
                    hasMore = false
                }
            } else if (unwrappedRead.sw == 0x6B00) {
                hasMore = false
            } else {
                throw SmartCardReaderException("Manual Wrapped READ (SFI) failed at offset $offset: Unwrapped SW=" + Integer.toHexString(unwrappedRead.sw))
            }
        }
        return buffer.toByteArray()
    }

    private fun readDataGroupSecure(isoDep: IsoDep, wrapper: org.jmrtd.protocol.SecureMessagingWrapper, sfi: Byte): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        var offset = 0
        val blockSize = 220
        var hasMore = true

        while (hasMore) {
            // Implicit SFI addressing for READ BINARY:
            // If offset == 0: P1 = 0x80 | SFI. P2 = 0.
            // If offset > 0: The file is implicitly selected. Use standard P1/P2 for offset.

            val p1: Int
            val p2: Int

            if (offset == 0) {
                p1 = 0x80 or sfi.toInt()
                p2 = 0
            } else {
                p1 = (offset shr 8) and 0xFF
                p2 = offset and 0xFF
            }

            // 00 B0 P1 P2 Le
            // We use CLA 0x00 initially. The wrapper should transform it to 0x0C (secure).
            val readCmd = CommandAPDU(0x00, 0xB0, p1, p2, blockSize)
            val wrappedRead = wrapper.wrap(readCmd)

            val readRespBytes = isoDep.transceive(wrappedRead.bytes)
            val readResp = ResponseAPDU(readRespBytes)

            if (readResp.sw == 0x6E00) {
                 throw SmartCardReaderException("Secure Read Failed: Card rejected APDU Class (SW=6E00). Wrapper failed to mask Class?")
            }

            val unwrappedRead = wrapper.unwrap(readResp)

            if (unwrappedRead.sw == 0x9000) {
                val data = unwrappedRead.data
                buffer.write(data)
                offset += data.size
                if (data.size < blockSize) {
                    hasMore = false
                }
            } else if (unwrappedRead.sw == 0x6B00) {
                hasMore = false
            } else {
                throw SmartCardReaderException("Secure Read (SFI) failed at offset $offset: Unwrapped SW=" + Integer.toHexString(unwrappedRead.sw))
            }
        }
        return buffer.toByteArray()
    }

    private fun getIsoDep(nfcReader: Any): IsoDep? {
            // Strategy 1: Public getTag() method
            try {
                debugLog(logTag, "Reflection: Trying getTag() method on ${nfcReader.javaClass.name}")
                val getTagMethod = nfcReader.javaClass.getMethod("getTag")
                val tag = getTagMethod.invoke(nfcReader) as? android.nfc.Tag
                if (tag != null) {
                    debugLog(logTag, "Reflection: Found Tag via getTag()")
                    return IsoDep.get(tag)
                }
            } catch (e: Exception) { debugLog(logTag, "Reflection: getTag() failed: ${e.message}") }

            // Strategy 2: Reflective search for fields (Tag or IsoDep)
            var currentClass: Class<*>? = nfcReader.javaClass
            while (currentClass != null) {
                debugLog(logTag, "Reflection: Searching fields in ${currentClass.name}")
                for (field in currentClass.declaredFields) {
                    try {
                        field.isAccessible = true

                        if (android.nfc.Tag::class.java.isAssignableFrom(field.type)) {
                            val tag = field.get(nfcReader) as? android.nfc.Tag
                            if (tag != null) {
                                debugLog(logTag, "Reflection: Found Tag field '${field.name}'")
                                return IsoDep.get(tag)
                            }
                        }

                        if (IsoDep::class.java.isAssignableFrom(field.type)) {
                            debugLog(logTag, "Reflection: Found IsoDep field '${field.name}'")
                            return field.get(nfcReader) as? IsoDep
                        }
                    } catch (e: Exception) { /* Continue */ }
                }
                currentClass = currentClass.superclass
            }

            debugLog(logTag, "Reflection: IsoDep not found")
            return null
        }

        private fun tryRomanianDiscovery(isoDep: IsoDep, canNumber: String): IdCardData {
             debugLog(logTag, "Starting Romanian eID Discovery...")

             // Insert Bouncy Castle at position 1 to ensure it handles AES-256 correctly
             // This prevents "Unknown OID" or algorithm support issues with standard Android providers
             if (Security.getProvider("BC") == null) {
                 Security.insertProviderAt(BouncyCastleProvider(), 1)
             } else {
                 Security.removeProvider("BC")
                 Security.insertProviderAt(BouncyCastleProvider(), 1)
             }

             // 1. Setup Card Service
             isoDep.timeout = 10000 // Extended timeout for PACE

             // Initialize Custom Romanian Card Service
             val cardService = RomanianCardService(isoDep)
             cardService.open()

             try {
                 // Create PassportService wrapper to access files
                 // Configuration: maxTranceiveLength = 256, maxBlockSize = 256, checkForExtraService = false, checkSFI = false
                 val passportService = PassportService(cardService, 256, 256, false, false)
                 passportService.open()

                 // 2. Discovery: Read EF.CardAccess (SFI 1C)
//                 // SKIPPED: We skip this to avoid triggering security errors before PACE.
                 debugLog(logTag, "Reading EF.CardAccess...")
                 // Use passportService to get the stream
                 val cardAccessFile = CardAccessFile(passportService.getInputStream(PassportService.EF_CARD_ACCESS))
                 debugLog(logTag, "EF.CardAccess read successfully.")

                 // JMRTD 0.7.18: getSecurityInfos() returns Collection<SecurityInfo>
                 val securityInfos = cardAccessFile.getSecurityInfos()
                 var paceInfo: PACEInfo? = null
                 if (securityInfos != null) {
                     for (info in securityInfos) {
                         if (info is PACEInfo) {
                             paceInfo = info
                             break
                         }
                     }
                 }

                 if (paceInfo == null) {
                     throw SmartCardReaderException("No PACE Info found in EF.CardAccess")
                 }

                 val oid = paceInfo.protocolOIDString
                 val paramId = paceInfo.parameterId
                 debugLog(logTag, "Detected PACE OID: $oid, ParamID: $paramId")


                 // Detected OID from previous runs: 0.4.0.127.0.7.2.2.4.2.4
//                 val oid = "0.4.0.127.0.7.2.2.4.2.4"
//                 val paramId = 13 // 0x0D BrainpoolP256r1
//
//                 debugLog(logTag, "Using Hardcoded PACE OID: $oid, ParamID: $paramId")

                 // 3. Establish Secure Messaging (PACE-CAN)
                 val cleanInput = canNumber.trim().replace(" ", "")
                 val keyRef = 2.toByte() // 2=CAN

                 debugLog(logTag, "Performing PACE with CAN (Input Length: ${cleanInput.length})")

                 // Use the cleaned input for the key
                 val paceKey = PACEKeySpec(cleanInput.toByteArray(), keyRef)

                 // Explicit doPACE call with hardcoded params
                 passportService.doPACE(paceKey, oid, PACEInfo.toParameterSpec(paramId), BigInteger.valueOf(paramId.toLong()))
                 debugLog(logTag, "PACE Established. Secure Messaging Active. Wrapper set: ${passportService.wrapper != null}")

                 // 4. Secure Applet Selection (After PACE)
                 // Now that we have a secure channel (MF), we select the ICAO Applet using Wrapped APDU.
                 val wrapper = passportService.wrapper
                 if (wrapper != null) {
                     debugLog(logTag, "Selecting ICAO Applet (Securely)...")
                     val aid = byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x47.toByte(), 0x10.toByte(), 0x01.toByte())
                     // ISO7816-4 SELECT: CLA=00, INS=A4, P1=04 (By Name), P2=00 (First Occurrence), Data=AID
                     val selectCmdStandard = CommandAPDU(0x00, 0xA4, 0x04, 0x00, aid)

                     val wrappedSelect = wrapper.wrap(selectCmdStandard)
                     val selectResp = cardService.transmit(wrappedSelect)
                     // Unwrap to check status? wrapper.unwrap() needs ResponseAPDU
                     // cardService.transmit returns ResponseAPDU
                     val unwrappedSelect = wrapper.unwrap(selectResp)

                     debugLog(logTag, "Secure Applet Selection SW: ${Integer.toHexString(unwrappedSelect.sw)}")
                     if (unwrappedSelect.sw != 0x9000) {
                         debugLog(logTag, "Warning: Secure Applet Selection failed. Proceeding anyway...")
                     }
                 }

                 // DG1: MRZ Data
                 debugLog(logTag, "Reading DG1 (MRZ)...")
                 var dg1File: DG1File? = null
                 var mrzInfo: MRZInfo? = null

                 try {
                     // Force Manual Secure Read because standard getInputStream sends plaintext SELECT (00A4...) which fails with 6E00
                     if (wrapper == null) throw Exception("Secure Messaging Wrapper lost")

                     val dg1Bytes = readDataGroupSecure(isoDep, wrapper, 0x01.toByte())
                     dg1File = DG1File(java.io.ByteArrayInputStream(dg1Bytes))

                     // JMRTD 0.7.18: getMRZInfo() instead of mrzInfo property
                     mrzInfo = dg1File.getMRZInfo()
                     debugLog(logTag, "DG1 Read Success: ${mrzInfo.primaryIdentifier} ${mrzInfo.secondaryIdentifier}")
                 } catch (e: Exception) {
                     debugLog(logTag, "DG1 Read Failed: ${e.message}")
                     // We continue to DG2 even if DG1 fails, to see if the tag error is specific to DG1
                 }

                 // DG2: Face Image
                 debugLog(logTag, "Reading DG2 (Face)...")
                 var faceImageBytes: ByteArray? = null
                 try {
                     if (wrapper == null) throw Exception("Secure Messaging Wrapper lost")

                     // Force Manual Secure Read for DG2 (SFI 0x02)
                     val dg2Bytes = readDataGroupSecure(isoDep, wrapper, 0x02.toByte())
                     val dg2File = DG2File(java.io.ByteArrayInputStream(dg2Bytes))

                     // JMRTD 0.7.18: getFaceInfos() instead of faceInfos property
                     val images = dg2File.getFaceInfos()
                     if (images != null && !images.isEmpty()) {
                        val imageInfo = images[0]
                        // JMRTD 0.7.18: FaceInfo might have getFaceImageInfos()
                        val imageInfos = imageInfo.getFaceImageInfos()
                        if (imageInfos != null && !imageInfos.isEmpty()) {
                            val faceImageInfo = imageInfos[0]
                            val imageInputStream = faceImageInfo.getImageInputStream()
                            faceImageBytes = imageInputStream.readBytes()
                            debugLog(logTag, "DG2 Read Success. Image size: ${faceImageBytes?.size} bytes")
                        }
                     }
                 } catch (e: Exception) {
                     errorLog(logTag, "Failed to read DG2 (Face): ${e.message}", e)
                 }

                 // Map to Personal Data
                 val givenNames = mrzInfo?.secondaryIdentifier?.replace("<", " ")?.trim() ?: "Unknown"
                 val surname = mrzInfo?.primaryIdentifier?.replace("<", " ")?.trim() ?: "Unknown"
                 val docNumber = mrzInfo?.documentNumber ?: "Unknown"
                 val personalCode = mrzInfo?.personalNumber ?: "Unknown"
                 val nationality = mrzInfo?.nationality ?: "ROU"

                 // Parse Expiry Date (YYMMDD)
                 var expiryDate: LocalDate? = null
                 if (mrzInfo != null) {
                    try {
                        val expiryStr = mrzInfo.dateOfExpiry
                        // MRZ years are 2 digits. Pivot around 50? Assume 20xx for now.
                        // A robust parser would need more context, but standard MRTD is roughly this.
                        val formatter = DateTimeFormatter.ofPattern("yyMMdd")
                        expiryDate = LocalDate.parse(expiryStr, formatter)
                        // Adjust century if needed (simplified)
                        if (expiryDate.year < 2000) {
                           expiryDate = expiryDate.plusYears(100)
                        }
                    } catch (e: Exception) {
                        debugLog(logTag, "Failed to parse expiry date: ${mrzInfo.dateOfExpiry}")
                    }
                 }

                 val personalData = RomanianPersonalData(
                     givenNames = givenNames,
                     surname = surname,
                     citizenship = nationality,
                     personalCode = personalCode,
                     documentNumber = docNumber,
                     expiryDate = expiryDate,
                     faceImage = faceImageBytes
                 )

                 return IdCardData(
                     type = EIDType.ID_CARD,
                     personalData = personalData,
                     authCertificate = null,
                     signCertificate = null,
                     pin1RetryCount = null,
                     pin2RetryCount = null,
                     pukRetryCount = null,
                     pin2CodeChanged = false
                 )

             } catch (e: Exception) {
                 throw SmartCardReaderException("Romanian Discovery Failed: ${e.message}")
             } finally {
                 try { cardService.close() } catch (e: Exception) {}
             }
        }
    }
