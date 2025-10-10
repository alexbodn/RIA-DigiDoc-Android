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

@file:Suppress("PackageName")

package ee.ria.DigiDoc.cryptolib

enum class CertType {
    UnknownType,
    IDCardType,
    DigiIDType,
    EResidentType,
    MobileIDType,
    SmartIDType,
    ESealType,
}

object OID {
    const val ID_CARD_POLICY_PREFIX = "1.3.6.1.4.1.10015.1.1"
    const val ALTERNATE_ID_CARD_POLICY = "1.3.6.1.4.1.51361.1.1.1"

    const val DIGI_ID_POLICY_PREFIX = "1.3.6.1.4.1.10015.1.2"
    const val ALTERNATE_DIGI_ID_POLICY1 = "1.3.6.1.4.1.51361.1.1"
    const val ALTERNATE_DIGI_ID_POLICY2 = "1.3.6.1.4.1.51455.1.1"

    const val MOBILE_ID_POLICY_PREFIX = "1.3.6.1.4.1.10015.1.3"
    const val ALTERNATE_MOBILE_ID_POLICY = "1.3.6.1.4.1.10015.11.1"

    const val ESEAL_POLICY_PREFIX1 = "1.3.6.1.4.1.10015.7.3"
    const val ESEAL_POLICY_PREFIX2 = "1.3.6.1.4.1.10015.7.1"
    const val ESEAL_POLICY_PREFIX3 = "1.3.6.1.4.1.10015.2.1"
}
