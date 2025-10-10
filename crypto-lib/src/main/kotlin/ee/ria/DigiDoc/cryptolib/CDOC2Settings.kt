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

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import androidx.preference.PreferenceManager
import ee.ria.DigiDoc.common.Constant.DIR_CRYPTO_CERT
import ee.ria.DigiDoc.utilsLib.file.FileUtil
import javax.inject.Inject

class CDOC2Settings
    @Inject
    constructor(
        private var context: Context,
    ) {
        private var preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        private var resources: Resources = context.resources

        fun getUseEncryption(): Boolean =
            preferences.getBoolean(
                resources.getString(R.string.crypto_settings_use_cdoc2_encryption),
                false,
            )

        fun getUseOnlineEncryption(): Boolean =
            preferences.getBoolean(
                resources.getString(R.string.crypto_settings_use_cdoc2_online_encryption),
                false,
            )

        fun getCDOC2UUID(): String =
            preferences.getString(
                resources.getString(R.string.crypto_settings_use_cdoc2_uuid),
                "",
            ) ?: ""

        fun getCDOC2PostURL(): String =
            preferences.getString(
                resources.getString(R.string.crypto_settings_use_cdoc2_post_url),
                "",
            ) ?: ""

        fun getCDOC2FetchURL(): String =
            preferences.getString(
                resources.getString(R.string.crypto_settings_use_cdoc2_fetch_url),
                "",
            ) ?: ""

        fun getCDOC2Cert(): String? {
            val cryptoCertName =
                preferences.getString(
                    resources.getString(ee.ria.DigiDoc.network.R.string.main_settings_crypto_cert_key),
                    "",
                )
                    ?: ""
            val cryptoCertFile = FileUtil.getCertFile(context, cryptoCertName, DIR_CRYPTO_CERT)
            if (cryptoCertFile != null) {
                val fileContents = FileUtil.readFileContent(cryptoCertFile.path)

                return fileContents
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replace("\\s".toRegex(), "")
            }
            return null
        }
    }
