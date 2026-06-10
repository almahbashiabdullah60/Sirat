package dev.pranav.applock.services

object AppLockConstants {
    val EXCLUDED_APPS = setOf(
        "com.android.settings",
        "com.android.systemui",
        "com.google.android.packageinstaller"
    )

    val ACCESSIBILITY_SETTINGS_CLASSES = setOf(
        "com.android.settings.SubSettings",
        "com.android.settings.Settings\$AccessibilitySettingsActivity",
        "com.android.settings.accessibility.AccessibilitySettings",
        "com.android.settings.Settings\$AccessibilitySettings"
    )
}
