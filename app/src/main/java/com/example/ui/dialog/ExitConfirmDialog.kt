package com.example.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.ExamAlarmManager

const val DEFAULT_EXIT_PIN = "1234"

@Composable
fun ExitConfirmDialog(
    alarmManager: ExamAlarmManager,
    onDismiss: () -> Unit,
    onConfirmedExit: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Start Alarm A when dialog appears, and stop when disposed
    DisposableEffect(Unit) {
        alarmManager.startAlarmA()
        onDispose {
            alarmManager.stopAlarmA()
        }
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    AlertDialog(
        onDismissRequest = {
            // Dismiss via outside click acts as KEMBALI
            alarmManager.stopAlarmA()
            onDismiss()
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Alert",
                tint = Color(0xFFD32F2F),
                modifier = Modifier.padding(top = 8.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.exit_dialog_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB71C1C)
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.exit_dialog_message),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF37474F)
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        if (errorMessage != null) {
                            errorMessage = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("exit_pin_input"),
                    label = { Text("Kode Keluar Pengawas") },
                    placeholder = { Text(stringResource(R.string.exit_code_hint)) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = Color(0xFF1565C0)
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (pinInput == DEFAULT_EXIT_PIN) {
                                alarmManager.stopAlarmA()
                                onConfirmedExit()
                            } else {
                                pinInput = ""
                                errorMessage = "Kode keluar salah"
                            }
                        }
                    ),
                    isError = errorMessage != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1565C0),
                        unfocusedBorderColor = Color(0xFF90A4AE),
                        errorBorderColor = Color(0xFFD32F2F)
                    )
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.testTag("exit_error_text")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Perhatian: Alarm darurat aktif selama dialog ini terbuka.",
                    color = Color(0xFFC62828),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinInput == DEFAULT_EXIT_PIN) {
                        alarmManager.stopAlarmA()
                        onConfirmedExit()
                    } else {
                        pinInput = ""
                        errorMessage = "Kode keluar salah"
                        // Alarm A must remain active!
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("dialog_ok_button")
            ) {
                Text(
                    text = stringResource(R.string.button_ok),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    // KEMBALI button: stops alarm immediately, closes dialog, does NOT exit app
                    alarmManager.stopAlarmA()
                    onDismiss()
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("dialog_back_button")
            ) {
                Text(
                    text = stringResource(R.string.button_back),
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF37474F)
                )
            }
        }
    )
}
