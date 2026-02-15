package ee.ria.DigiDoc.domain.model

import ee.ria.DigiDoc.idcard.CardType
import ee.ria.DigiDoc.idcard.PersonalData
import java.time.LocalDate

class RomanianPersonalData(
    private val givenNames: String,
    private val surname: String,
    private val citizenship: String,
    private val personalCode: String,
    private val documentNumber: String,
    private val expiryDate: LocalDate?,
    private val faceImage: ByteArray? = null,
    private val placeOfBirth: String? = null,
    private val permanentAddress: String? = null
) : PersonalData() {

    fun faceImage(): ByteArray? = faceImage

    fun placeOfBirth(): String? = placeOfBirth

    fun permanentAddress(): String? = permanentAddress

    override fun givenNames(): String = givenNames

    override fun surname(): String = surname

    override fun citizenship(): String = citizenship

    override fun personalCode(): String = personalCode

    override fun documentNumber(): String = documentNumber

    override fun expiryDate(): LocalDate? = expiryDate

    override fun cardType(): CardType = CardType.THALES

    override fun dateOfBirth(): LocalDate? = null
}
