package com.atyafcode.sirat.core.navigation

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.atyafcode.sirat.AppLockApplication
import com.atyafcode.sirat.R
import com.atyafcode.sirat.core.utils.LogUtils
import com.atyafcode.sirat.data.repository.PreferencesRepository
import com.atyafcode.sirat.features.antiuninstall.ui.AntiUninstallScreen
import com.atyafcode.sirat.features.appintro.ui.AppIntroScreen
import com.atyafcode.sirat.features.applist.ui.MainScreen
import com.atyafcode.sirat.features.lockscreen.ui.AlphanumericPasswordOverlayScreen
import com.atyafcode.sirat.features.lockscreen.ui.PinPasswordOverlayScreen
import com.atyafcode.sirat.features.lockscreen.ui.SupervisedLockOverlay
import com.atyafcode.sirat.features.setpassword.ui.AlphanumericSetPasswordScreen
import com.atyafcode.sirat.features.setpassword.ui.SetPasswordScreen
import com.atyafcode.sirat.features.settings.ui.SettingsScreen
import com.atyafcode.sirat.features.triggerexclusions.ui.TriggerExclusionsScreen
import com.atyafcode.sirat.features.aisettings.ui.AISettingsScreen
import com.atyafcode.sirat.features.filtering.ui.FilteringDashboardScreen
import com.atyafcode.sirat.features.filtering.ui.CustomRulesScreen
import com.atyafcode.sirat.features.contentdetection.ui.ContentDetectionSettingsScreen

@Composable
fun AppNavHost(navController: NavHostController, startDestination: String) {
    val context = LocalContext.current
    val application = context.applicationContext as AppLockApplication

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(ANIMATION_DURATION)) +
                    scaleIn(initialScale = SCALE_INITIAL, animationSpec = tween(ANIMATION_DURATION))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(ANIMATION_DURATION)) +
                    scaleIn(initialScale = SCALE_INITIAL, animationSpec = tween(ANIMATION_DURATION))
        },
    ) {
        composable(Screen.AppIntro.route) {
            AppIntroScreen(navController)
        }

        composable(Screen.SetPassword.route) {
            SetPasswordScreen(navController, isFirstTimeSetup = true)
        }

        composable(Screen.ChangePassword.route) {
            when (application.appLockRepository.getLockType()) {
                PreferencesRepository.LOCK_TYPE_PASSWORD -> {
                    AlphanumericSetPasswordScreen(navController, false)
                }
                PreferencesRepository.LOCK_TYPE_SUPERVISED -> {
                    // User was in supervised mode and already verified; skip old password
                    SetPasswordScreen(navController, isFirstTimeSetup = false, skipVerification = true)
                }
                else -> {
                    SetPasswordScreen(navController, isFirstTimeSetup = false)
                }
            }
        }

        composable(Screen.SetPasswordAlphanumeric.route) {
            AlphanumericSetPasswordScreen(navController, isFirstTimeSetup = true)
        }

        composable(Screen.SupervisedMethodChoice.route) {
            com.atyafcode.sirat.features.setpassword.ui.SupervisedMethodChoiceScreen(navController)
        }

        composable(Screen.SupervisedSetup.route + "/{method}") { backStackEntry ->
            val method = backStackEntry.arguments?.getString("method") ?: "qr"
            com.atyafcode.sirat.features.setpassword.ui.SupervisedSetupScreen(navController, method)
        }

        composable(Screen.Main.route) {
            MainScreen(navController)
        }

        composable(Screen.PasswordOverlay.route) {
            val activity = context.findFragmentActivity()
            val lockType = application.appLockRepository.getLockType()

            if (activity != null) {
                when (lockType) {
                    PreferencesRepository.LOCK_TYPE_PASSWORD -> {
                        AlphanumericPasswordOverlayScreen(
                            showBiometricButton = application.appLockRepository.isBiometricAuthEnabled(),
                            fromMainActivity = true,
                            onBiometricAuth = {
                                handleBiometricAuthentication(activity, navController)
                            },
                            onAuthSuccess = {
                                navController.previousBackStackEntry?.savedStateHandle?.set("authSuccess", true)
                                handleAuthenticationSuccess(navController)
                            }
                        )
                    }

                    PreferencesRepository.LOCK_TYPE_SUPERVISED -> {
                        SupervisedLockOverlay(
                            lockedAppName = context.getString(R.string.this_app),
                            onUnlock = {
                                navController.previousBackStackEntry?.savedStateHandle?.set("authSuccess", true)
                                handleAuthenticationSuccess(navController)
                            },
                            onExit = {
                                activity.finish()
                            }
                        )
                    }

                    else -> {
                        PinPasswordOverlayScreen(
                            showBiometricButton = application.appLockRepository.isBiometricAuthEnabled(),
                            fromMainActivity = true,
                            onBiometricAuth = {
                                handleBiometricAuthentication(activity, navController)
                            },
                            onAuthSuccess = {
                                navController.previousBackStackEntry?.savedStateHandle?.set("authSuccess", true)
                                handleAuthenticationSuccess(navController)
                            }
                        )
                    }
                }
            }
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController)
        }

        composable(Screen.TriggerExclusions.route) {
            TriggerExclusionsScreen(navController)
        }

        composable(Screen.AntiUninstall.route) {
            AntiUninstallScreen(navController)
        }

        composable(Screen.AISettings.route) {
            AISettingsScreen(navController)
        }

        composable(Screen.FilteringDashboard.route) {
            FilteringDashboardScreen(navController)
        }

        composable(Screen.CustomRules.route) {
            CustomRulesScreen(navController)
        }

        composable(Screen.ContentDetectionSettings.route) {
            ContentDetectionSettingsScreen(navController)
        }
    }
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return null
}

private fun handleBiometricAuthentication(
    context: FragmentActivity,
    navController: NavHostController
) {
    try {
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(
            context,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.w(TAG, "Biometric authentication error: $errString ($errorCode)")
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    LogUtils.d(TAG, "Biometric authentication succeeded")
                    navController.previousBackStackEntry?.savedStateHandle?.set("authSuccess", true)
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navigateToMain(navController)
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "Biometric authentication failed (not recognized)")
                }
            }
        )

        val promptInfo = createBiometricPromptInfo(context)
        biometricPrompt.authenticate(promptInfo)
    } catch (e: Exception) {
        Log.e(TAG, "Error during biometric authentication", e)
    }
}

private fun createBiometricPromptInfo(context: Context): BiometricPrompt.PromptInfo {
    return BiometricPrompt.PromptInfo.Builder()
        .setTitle(context.getString(R.string.unlock_app_title, context.getString(R.string.this_app)))
        .setSubtitle(context.getString(R.string.confirm_biometric_subtitle))
        .setNegativeButtonText(context.getString(R.string.use_pin_button))
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
        )
        .setConfirmationRequired(false)
        .build()
}

private fun handleAuthenticationSuccess(navController: NavHostController) {
    if (navController.previousBackStackEntry != null) {
        navController.popBackStack()
    } else {
        navigateToMain(navController)
    }
}

private fun navigateToMain(navController: NavHostController) {
    navController.navigate(Screen.Main.route) {
        popUpTo(Screen.PasswordOverlay.route) { inclusive = true }
    }
}

private const val TAG = "AppNavHost"
private const val ANIMATION_DURATION = 400
private const val SCALE_INITIAL = 0.9f
