package ee.ria.DigiDoc.smartcardreader

import android.nfc.tech.IsoDep
import android.util.Log
import ee.ria.DigiDoc.common.model.EIDType
import ee.ria.DigiDoc.domain.model.IdCardData
import ee.ria.DigiDoc.idcard.PersonalData
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CommandAPDU
import net.sf.scuba.smartcards.IsoDepCardService
import net.sf.scuba.smartcards.ResponseAPDU
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.jmrtd.PACEKeySpec
import org.jmrtd.PACEProtocol
import org.jmrtd.PassportService
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import java.io.ByteArrayInputStream
import java.security.Security
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RomanianCardService @Inject constructor() {

    companion object {
        private const val TAG = "RomanianCardService"
        // 00 B0 9C 00 00 (Read Binary via SFI 1C)
        private val SFI_READ_APDU = CommandAPDU(0x00, 0xB0, 0x9C, 0x00, 0x00)
        private const val ROMANIAN_PACE_OID = "0.4.0.127.0.7.2.2.4.2.4"
        // BrainpoolP256r1
        private const val PARAM_ID_BRAINPOOL_P256R1 = 13 // 0x0D

        init {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.insertProviderAt(BouncyCastleProvider(), 1)
            }
        }
    }

    fun tryRomanianDiscovery(isoDep: IsoDep, canNumber: String): IdCardData? {
        Log.d(TAG, "Starting Romanian Discovery with CAN: $canNumber")

        try {
            // 1. Setup Service
            val cardService = IsoDepCardService(isoDep)
            cardService.open()

            // 2. Discovery: Send SFI Read
            // We use raw transmission via IsoDep first if needed, but cardService.transmit works too.
            // Let's try sending the APDU directly to see if it responds.
            val response = cardService.transmit(SFI_READ_APDU)

            if (response.sw != 0x9000) {
                 Log.d(TAG, "SFI Read failed: " + Integer.toHexString(response.sw))
                 // Fallback or exit? The instructions say "Give the SFI read a try first".
                 // If it fails, maybe not a Romanian card or wrong state.
                 // However, let's proceed to try PACE if the user provided CAN,
                 // assuming the user knows what they are doing by entering CAN.
            } else {
                Log.d(TAG, "SFI Read Success: " + response.bytes.joinToString("") { "%02x".format(it) })
                // We could parse the OID here, but JMRTD handles it.
                // The instructions say: "If that returns data, the rest is just standard protocol!"
            }

            // 3. PACE Handshake
            val pace = PACEProtocol(cardService)
            val canKey = PACEKeySpec.createCAN(canNumber)

            // Hardcode OID and Param as per instructions
            pace.doPACE(canKey, ROMANIAN_PACE_OID, PACEProtocol.PaceParam.fromInt(PARAM_ID_BRAINPOOL_P256R1))
            Log.d(TAG, "PACE Success")

            // 4. Secure Messaging is active
            val service = PassportService(cardService)
            service.open()

            // 5. Read DG1 (MRZ)
            var personalData: PersonalData? = null
            try {
                val dg1In = service.getInputStream(PassportService.EF_DG1)
                val dg1File = DG1File(dg1In)
                val mrzInfo = dg1File.mrzInfo

                // Construct PersonalData from MRZ
                // We need to check how to construct PersonalData.
                // Since I cannot see PersonalData source (it's in AAR?), I will try to use reflection or just check if I can construct it.
                // Wait, if PersonalData is in AAR, I might not be able to instantiate it easily if it doesn't have a public constructor matching what I have.
                // Existing code uses `token.personalData()`.
                // Let's look at `PersonalData` class via javap or assume standard fields.
                // For now, I will return null for personalData if I can't construct it, or try to mock it.
                // Actually, I should inspect `PersonalData` structure using `javap` or similar if possible.
                // But I'll assume for now I can't easily construct it without more info.
                // However, the prompt says "Construct IdCardData".
                // I will try to create a dummy PersonalData or use a wrapper if I can find one.

                // Let's assume PersonalData has a constructor taking arguments.
                // Since I can't verify, I will use a placeholder or reflection to print members in a separate step if compilation fails.
                // For this step, I will leave the construction of IdCardData to the next step "Implement Data Reading".
                // I am returning null here as per plan step "Implement Discovery and PACE".

                return null

            } catch (e: Exception) {
                Log.e(TAG, "Failed to read DG1", e)
                return null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Romanian Discovery Failed", e)
            return null
        }
    }
}
