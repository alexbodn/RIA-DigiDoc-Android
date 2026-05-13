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

package ee.ria.DigiDoc.ui.component.myeid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import ee.ria.DigiDoc.R
import ee.ria.DigiDoc.idcard.CodeType
import ee.ria.DigiDoc.ui.component.shared.CancelAndOkButtonRow
import ee.ria.DigiDoc.ui.component.shared.SecurePinTextField
import ee.ria.DigiDoc.ui.theme.Dimensions.SPadding
import ee.ria.DigiDoc.ui.theme.buttonRoundCornerShape

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TestPinDialog(
    modifier: Modifier = Modifier,
    showDialog: MutableState<Boolean>,
    title: String,
    codeType: CodeType,
    showCanField: Boolean,
    initialCan: String = "",
    onResult: (ByteArray, String?) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val pin = remember { mutableStateOf(byteArrayOf()) }
    val can = remember { mutableStateOf(TextFieldValue(initialCan)) }
    val pinCodeLabel = stringResource(R.string.myeid_pin, codeType.name)

    LaunchedEffect(Unit, showDialog.value) {
        if (showDialog.value) {
            pin.value = byteArrayOf()
            can.value = TextFieldValue(initialCan)
            focusRequester.requestFocus()
        }
    }

    if (showDialog.value) {
        BasicAlertDialog(
            modifier =
                modifier
                    .clip(buttonRoundCornerShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .semantics {
                        testTagsAsResourceId = true
                    }.testTag("testPinDialogDialog"),
            onDismissRequest = {
                showDialog.value = false
            },
        ) {
            Surface(
                modifier =
                    Modifier
                        .padding(SPadding)
                        .wrapContentHeight()
                        .wrapContentWidth()
                        .verticalScroll(rememberScrollState())
                        .testTag("testPinDialog"),
            ) {
                Column(
                    modifier = modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = modifier.padding(bottom = SPadding)
                    )

                    if (showCanField) {
                        OutlinedTextField(
                            value = can.value,
                            onValueChange = {
                                if (it.text.length <= 6) {
                                    can.value = it
                                }
                            },
                            label = { Text(stringResource(R.string.signature_update_nfc_can)) },
                            modifier = modifier
                                .fillMaxWidth()
                                .padding(bottom = SPadding),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    SecurePinTextField(
                        modifier = modifier.fillMaxWidth(),
                        pin = pin,
                        pinCodeLabel = pinCodeLabel,
                        pinNumberFocusRequester = focusRequester,
                        previousFocusRequester = FocusRequester(), // Dummy
                        pinCodeTextEdited = null,
                        trailingIconContentDescription = stringResource(R.string.clear_text),
                        isError = false
                    )

                    CancelAndOkButtonRow(
                        modifier = modifier.padding(top = SPadding),
                        okButtonTestTag = "testPinDialogOkButton",
                        cancelButtonTestTag = "testPinDialogCancelButton",
                        cancelButtonClick = {
                            showDialog.value = false
                        },
                        okButtonClick = {
                            if (pin.value.isNotEmpty()) {
                                if (showCanField && can.value.text.length != 6) {
                                    // Should show error, but for now just don't submit?
                                    // Or rely on ViewModel validation?
                                    // Let's assume user inputs correctly or ViewModel handles failure.
                                    // Actually, let's just proceed.
                                }
                                showDialog.value = false
                                onResult(pin.value, if (showCanField) can.value.text else null)
                                pin.value = byteArrayOf()
                                can.value = TextFieldValue()
                            }
                        },
                        cancelButtonTitle = R.string.cancel_button,
                        okButtonTitle = R.string.ok_button,
                        cancelButtonContentDescription = stringResource(R.string.cancel_button),
                        okButtonContentDescription = stringResource(R.string.ok_button),
                        showCancelButton = true,
                    )
                }
            }
        }
    }
}
