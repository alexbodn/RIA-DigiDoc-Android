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

@file:Suppress("PackageName", "FunctionName")

package ee.ria.DigiDoc.ui.component.myeid.mydata

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import android.widget.Toast
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import ee.ria.DigiDoc.ui.theme.Dimensions.XSPadding
import ee.ria.DigiDoc.utilsLib.date.DateUtil.isBefore

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MyEidMyDataView(
    modifier: Modifier = Modifier,
    firstname: String,
    lastname: String,
    citizenship: String,
    personalCode: String,
    dateOfBirth: String,
    documentNumber: String,
    validTo: String,
    faceImage: ByteArray? = null,
) {
    val context = LocalContext.current
    var showBiometricVerification by remember { mutableStateOf(false) }
    var showRNDHarness by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(vertical = XSPadding)
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (faceImage != null) {
            val bitmap = BitmapFactory.decodeByteArray(faceImage, 0, faceImage.size)
            if (bitmap != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Face Image",
                        modifier = Modifier.size(150.dp).padding(bottom = XSPadding),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    androidx.compose.foundation.layout.Column {
                        Button(onClick = { showBiometricVerification = true }) {
                            Text("Verify Identity")
                        }
                        androidx.compose.material3.TextButton(onClick = { isFrontCamera = !isFrontCamera }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Switch Camera")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isFrontCamera) "Front Camera" else "Rear Camera")
                        }
                            androidx.compose.material3.TextButton(onClick = {
                                try {
                                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    val file = File(dir, "eid_dg2_${System.currentTimeMillis()}.png")
                                    FileOutputStream(file).use { out ->
                                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                    Toast.makeText(context, "Saved DG2 to Downloads", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error saving DG2: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }) {
                                Icon(Icons.Filled.Face, contentDescription = "Download DG2 Image")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download DG2")
                            }
                        androidx.compose.material3.TextButton(onClick = { showRNDHarness = true }) {
                            Icon(Icons.Filled.Face, contentDescription = "R&D Match Harness")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open R&D Match Harness")
                        }

                    }
                }
            }
        }


        if (showRNDHarness) {
            RNDTestScreen(onDismiss = { showRNDHarness = false })
        }

        if (showBiometricVerification && faceImage != null) {
            BiometricVerificationScreen(
                dg2Image = faceImage,
                useFrontCamera = isFrontCamera,
                onDismiss = { showBiometricVerification = false },
            )
        }

        MyEidMyDataDetailItem()
            .myEidMyDataDetailItems(
                firstname = firstname,
                lastname = lastname,
                citizenship = citizenship,
                personalCode = personalCode,
                dateOfBirth = dateOfBirth,
                documentNumber = documentNumber,
                validTo = validTo,
            ).forEach { navigationItem ->
                if (!navigationItem.value.isNullOrEmpty()) {
                    MyEidMyDataItem(
                        modifier = modifier,
                        testTag = navigationItem.testTag,
                        detailKey = navigationItem.label,
                        detailValue = navigationItem.value,
                        contentDescription = navigationItem.contentDescription,
                        showTagBadge = navigationItem.showTagBadge,
                        status =
                            when (isBefore(validTo)) {
                                true -> MyEidDocumentStatus.EXPIRED
                                false -> MyEidDocumentStatus.VALID
                                null -> MyEidDocumentStatus.UNKNOWN
                            },
                    )
                    HorizontalDivider()
                }
            }
    }
}
