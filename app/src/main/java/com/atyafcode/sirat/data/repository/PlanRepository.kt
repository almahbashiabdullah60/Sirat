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

    fun setCloudProvider(provider: String) {
        prefs.edit().putString(KEY_CLOUD_PROVIDER, provider).apply()
    }

    fun getCloudProvider(): String {
        return prefs.getString(KEY_CLOUD_PROVIDER, CLOUD_PROVIDER_GEMINI) ?: CLOUD_PROVIDER_GEMINI
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

    fun setDownloadId(id: Long) {
        prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
    }

    fun getDownloadId(): Long {
        return prefs.getLong(KEY_DOWNLOAD_ID, -1L)
    }

    fun setModelCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_MODEL_COMPLETED, completed).apply()
    }

    fun isModelCompleted(): Boolean {
        return prefs.getBoolean(KEY_MODEL_COMPLETED, false)
    }

    companion object {
        private const val PREFS_NAME = "plan_builder_prefs"
        private const val PLAN_FILE_NAME = "user_recovery_plan.txt"
        
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_RELIGION = "religion"
        private const val KEY_PLAN_LANGUAGE = "plan_language"
        private const val KEY_CLOUD_PROVIDER = "cloud_provider"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_MODEL_COMPLETED = "model_completed"

        const val AI_PROVIDER_LOCAL = "local"
        const val AI_PROVIDER_CLOUD = "cloud"

        const val CLOUD_PROVIDER_GEMINI = "google"
        const val CLOUD_PROVIDER_OPENAI = "openai"
    }
}
