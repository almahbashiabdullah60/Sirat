package com.atyafcode.sirat.data.repository

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class PlanRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val planFile = File(context.filesDir, PLAN_FILE_NAME)

    fun savePlan(planText: String) {
        try {
            planFile.writeText(planText)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getPlan(): String? {
        return if (planFile.exists()) {
            try {
                planFile.readText()
            } catch (e: Exception) {
                null
            }
        } else null
    }

    fun clearPlan() {
        if (planFile.exists()) {
            planFile.delete()
        }
    }

    fun setAIProvider(provider: String) {
        prefs.edit().putString(KEY_AI_PROVIDER, provider).apply()
    }

    fun getAIProvider(): String {
        return prefs.getString(KEY_AI_PROVIDER, AI_PROVIDER_LOCAL) ?: AI_PROVIDER_LOCAL
    }

    fun setApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun setReligion(religion: String) {
        prefs.edit().putString(KEY_RELIGION, religion).apply()
    }

    fun getReligion(): String {
        return prefs.getString(KEY_RELIGION, "") ?: ""
    }

    fun setPlanLanguage(language: String) {
        prefs.edit().putString(KEY_PLAN_LANGUAGE, language).apply()
    }

    fun getPlanLanguage(): String {
        return prefs.getString(KEY_PLAN_LANGUAGE, "ar") ?: "ar"
    }

    companion object {
        private const val PREFS_NAME = "plan_builder_prefs"
        private const val PLAN_FILE_NAME = "user_recovery_plan.txt"
        
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_RELIGION = "religion"
        private const val KEY_PLAN_LANGUAGE = "plan_language"

        const val AI_PROVIDER_LOCAL = "local"
        const val AI_PROVIDER_CLOUD = "cloud"
    }
}
