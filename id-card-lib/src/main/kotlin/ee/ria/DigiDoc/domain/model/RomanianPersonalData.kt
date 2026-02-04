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
    private val expiryDate: LocalDate?
) : PersonalData() {

    override fun givenNames(): String = givenNames

    override fun surname(): String = surname

    override fun citizenship(): String = citizenship

    override fun personalCode(): String = personalCode

    override fun documentNumber(): String = documentNumber

    override fun expiryDate(): LocalDate? = expiryDate

    override fun cardType(): CardType = CardType.THALES

    override fun dateOfBirth(): LocalDate? = null
}
