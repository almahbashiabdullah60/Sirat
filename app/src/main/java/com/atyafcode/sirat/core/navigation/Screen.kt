package com.atyafcode.sirat.core.navigation

sealed class Screen(val route: String) {
    object AppIntro : Screen("app_intro")
    object SetPassword : Screen("set_password")
    // object SetPasswordPattern : Screen("set_password_pattern")
    object SetPasswordAlphanumeric : Screen("set_password_alphanumeric")
    object SupervisedSetup : Screen("supervised_setup")
    object SupervisedMethodChoice : Screen("supervised_method_choice")
    object ChangePassword : Screen("change_password")
    object Main : Screen("main")
    object PasswordOverlay : Screen("password_overlay")
    object Settings : Screen("settings")
    object TriggerExclusions : Screen("trigger_exclusions")
    object AntiUninstall: Screen("anti_uninstall")
    object AISettings: Screen("ai_settings")
    object FilteringDashboard : Screen("filtering")
    object CustomRules : Screen("custom_rules")
    object ContentDetectionSettings : Screen("content_detection_settings")
}

