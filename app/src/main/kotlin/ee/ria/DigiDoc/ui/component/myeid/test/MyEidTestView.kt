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

package ee.ria.DigiDoc.ui.component.myeid.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ee.ria.DigiDoc.R
import ee.ria.DigiDoc.idcard.CodeType
import ee.ria.DigiDoc.ui.component.shared.SecurePinTextField
import ee.ria.DigiDoc.ui.theme.Dimensions.SPadding

@Composable
fun MyEidTestView(
    modifier: Modifier = Modifier,
    canNumber: MutableState<TextFieldValue>,
    pin1: MutableState<ByteArray>,
    onTestClick: () -> Unit,
    testResult: String?,
) {
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(SPadding)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.myeid_test_tab_description),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = canNumber.value,
            onValueChange = {
                if (it.text.length <= 6) {
                    canNumber.value = it
                }
            },
            label = { Text(stringResource(R.string.signature_update_nfc_can)) },
            modifier = modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        SecurePinTextField(
            modifier = modifier.fillMaxWidth(),
            pin = pin1,
            pinCodeLabel = stringResource(R.string.myeid_pin, CodeType.PIN1.name),
            pinNumberFocusRequester = focusRequester,
            previousFocusRequester = FocusRequester(),
            pinCodeTextEdited = null,
            trailingIconContentDescription = stringResource(R.string.clear_text),
            isError = false,
        )

        Button(
            onClick = onTestClick,
            modifier = modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            enabled = canNumber.value.text.isNotEmpty() && pin1.value.isNotEmpty(),
        ) {
            Text(
                text = stringResource(R.string.myeid_test_button),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        if (testResult != null) {
            Text(
                text = testResult,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = modifier.padding(top = SPadding),
            )
        }
    }
}
