package com.devbehindyou.refract.ui.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Fingerprint or face unlock, falling back to the screen lock (PIN, pattern or password). */
private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** True when the device has a screen lock or an enrolled biometric that [authenticate] can use. */
fun canAuthenticate(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/** Shows the system prompt. [onResult] receives `true` only after a successful unlock. */
fun authenticate(
    activity: FragmentActivity,
    title: String,
    onResult: (Boolean) -> Unit,
) {
    val callback =
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true)
            }

            override fun onAuthenticationError(
                errorCode: Int,
                errString: CharSequence,
            ) {
                onResult(false)
            }
        }
    val info =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
    BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
}

/** The hosting [FragmentActivity], found through any [ContextWrapper] layers. */
fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Shows [content] only after the user has unlocked the device, when [required]. Leaving the screen
 * and coming back asks again; rotating the device does not.
 */
@Composable
fun AuthGate(
    required: Boolean,
    title: String,
    onDenied: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var unlocked by rememberSaveable { mutableStateOf(!required) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val activity = remember(context) { context.findFragmentActivity() }
    val available = remember(context) { canAuthenticate(context) }

    LaunchedEffect(attempt, unlocked) {
        if (!unlocked && activity != null && available) {
            authenticate(activity, title) { ok ->
                if (ok) {
                    unlocked = true
                } else {
                    failed = true
                }
            }
        }
    }

    if (unlocked) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                !available || activity == null -> {
                    Text(
                        "Set up a screen lock, fingerprint or face unlock to open protected files. " +
                            "You can also turn the lock off in Settings.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
                failed -> {
                    Text(
                        "Authentication is required to open this screen.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = {
                            failed = false
                            attempt++
                        },
                    ) { Text("Try again") }
                }
                else -> Text("Waiting for authentication…", style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedButton(onClick = onDenied) { Text("Go back") }
        }
    }
}
