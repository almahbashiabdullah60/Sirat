package com.atyafcode.sirat.core.navigation

import android.content.Context
import com.atyafcode.sirat.features.appintro.domain.AppIntroManager

/**
 * Manages navigation logic and routing decisions for the application.
 * Centralizes navigation-related business logic for better maintainability.
 */
class NavigationManager(private val context: Context) {

    /**
     * Determines the appropriate starting destination based on app state.
     */
    fun determineStartDestination(): String {
        return when {
            shouldShowAppIntro() -> Screen.AppIntro.route
            !isPasswordSet() -> Screen.SetPassword.route
            else -> Screen.PasswordOverlay.route
        }
    }

    /**
     * Checks if password verification should be skipped for the given route.
     */
    fun shouldSkipPasswordCheck(currentRoute: String?): Boolean {
        return currentRoute in ROUTES_THAT_SKIP_PASSWORD_CHECK
    }

    private fun shouldShowAppIntro(): Boolean {
        return AppIntroManager.shouldShowIntro(context)
    }

    private fun isPasswordSet(): Boolean {
        val appLockPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val settingsPrefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)

        val hasPin = appLockPrefs.getString(PASSWORD_KEY, null) != null
        val hasPattern = appLockPrefs.getString(PATTERN_KEY, null) != null
        val isSupervised = settingsPrefs.getString(LOCK_TYPE_KEY, "") == LOCK_TYPE_SUPERVISED &&
                appLockPrefs.getString(SUPERVISED_SECRET_KEY, null) != null

        return (hasPin || hasPattern || isSupervised)
    }

    companion object {
        private const val PREFS_NAME = "app_lock_prefs"
        private const val SETTINGS_PREFS_NAME = "app_lock_settings"
        private const val PASSWORD_KEY = "password"
        private const val PATTERN_KEY = "pattern"
        private const val LOCK_TYPE_KEY = "lock_type"
        private const val SUPERVISED_SECRET_KEY = "supervised_secret"
        private const val LOCK_TYPE_SUPERVISED = "supervised"

        private val ROUTES_THAT_SKIP_PASSWORD_CHECK = setOf(
            Screen.AppIntro.route,
            Screen.SetPassword.route,
            Screen.SupervisedMethodChoice.route,
            "${Screen.SupervisedSetup.route}/{method}"
        )
    }
}

