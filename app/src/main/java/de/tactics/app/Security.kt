package de.tactics.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** App-Sperre über Fingerabdruck/Gesicht oder die PIN bzw. das Muster des Geräts. */
object Security {
    private fun authenticators(activity: FragmentActivity): Int? {
        val bm = BiometricManager.from(activity)
        val strong = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        val weak = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        return when {
            bm.canAuthenticate(strong) == BiometricManager.BIOMETRIC_SUCCESS -> strong
            bm.canAuthenticate(weak) == BiometricManager.BIOMETRIC_SUCCESS -> weak
            bm.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS -> BIOMETRIC_WEAK
            else -> null
        }
    }

    fun isAvailable(activity: FragmentActivity): Boolean = authenticators(activity) != null

    fun prompt(
        activity: FragmentActivity,
        title: String,
        onSuccess: () -> Unit,
        onFail: (String) -> Unit = {}
    ) {
        val auth = authenticators(activity)
        if (auth == null) {
            onFail("Es ist kein Fingerabdruck und keine Bildschirmsperre eingerichtet.")
            return
        }
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(auth)
        if ((auth and DEVICE_CREDENTIAL) == 0) builder.setNegativeButtonText("Abbrechen")
        val info = builder.build()
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFail(errString.toString())
            }
        }
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Bei aktiver App-Sperre: keine Screenshots und keine Vorschau in der Übersicht „Zuletzt verwendet“,
 * damit Spielerdaten nicht an der Sperre vorbei sichtbar sind.
 */
fun Activity.applySecureFlag(enabled: Boolean) {
    if (enabled) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
}
