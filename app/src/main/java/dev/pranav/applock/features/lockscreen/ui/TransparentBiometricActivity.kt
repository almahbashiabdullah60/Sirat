package dev.pranav.applock.features.lockscreen.ui

import android.os.Bundle
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.pranav.applock.R
import dev.pranav.applock.services.AppLockManager

class TransparentBiometricActivity: FragmentActivity() {
    private val TAG = "TransparentBiometric"
    private var lockedPackageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockedPackageName = intent.getStringExtra("locked_package")

        AppLockManager.reportBiometricAuthStarted()

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this, executor,
            object: BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    AppLockManager.reportBiometricAuthFinished()
                    finish() // Close transparent activity if failed/canceled
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    AppLockManager.reportBiometricAuthFinished()
                    lockedPackageName?.let {
                        AppLockManager.temporarilyUnlockAppWithBiometrics(it)
                    }

                    // The Accessibility service will detect the unlock state
                    // and close the Service View automatically
                    finish()
                }
            })

        val appNameForPrompt = getString(R.string.this_app)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_app_title, appNameForPrompt))
            .setSubtitle(getString(R.string.confirm_biometric_subtitle))
            .setNegativeButtonText(getString(R.string.use_pin_button))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .setConfirmationRequired(false)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Biometric failed to start", e)
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            AppLockManager.reportBiometricAuthFinished()
        }
    }
}
