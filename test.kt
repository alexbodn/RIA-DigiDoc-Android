import ee.ria.DigiDoc.utils.pin.PinCodeUtil
import ee.ria.DigiDoc.idcard.CodeType

fun main() {
    println(PinCodeUtil.isPINLengthValid(byteArrayOf(), CodeType.PIN1))
}
