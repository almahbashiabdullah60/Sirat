package dev.pranav.applock

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.util.Log
import dev.pranav.applock.core.utils.LogUtils
import dev.pranav.applock.data.repository.AppLockRepository
import dev.pranav.applock.data.repository.PreferencesRepository
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.sui.Sui
import java.util.Locale
import kotlin.concurrent.thread

class AppLockApplication : Application() {

    lateinit var appLockRepository: AppLockRepository
        private set

    override fun attachBaseContext(base: Context?) {
        if (base == null) {
            super.attachBaseContext(base)
            return
        }
        val repository = PreferencesRepository(base)
        val language = repository.getAppLanguage()
        val context = if (language != "system") {
            val locale = Locale(language)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocales(LocaleList(locale))
            base.createConfigurationContext(config)
        } else {
            base
        }
        super.attachBaseContext(context)
    }

    override fun onCreate() {
        super.onCreate()
        initializeComponents()

        LogUtils.initialize(this)
        LogUtils.setLoggingEnabled(appLockRepository.isLoggingEnabled())
        // Purge logs older than 3 days on every app start (run in background to avoid ANR)
        thread(start = true, name = "LogPurge") {
            LogUtils.purgeOldLogs()
        }
        initializeHiddenApiBypass()
    }

    private fun initializeHiddenApiBypass() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("L")
                Log.d(TAG, "Hidden API bypass initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize hidden API bypass", e)
            }
        }
    }

    private fun initializeComponents() {
        try {
            appLockRepository = AppLockRepository(this)
            initializeSui()
            Log.d(TAG, "Application components initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize application components", e)
        }
    }

    private fun initializeSui() {
        try {
            Sui.init(packageName)
            Log.d(TAG, "Sui initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Sui", e)
        }
    }

    companion object {
        private const val TAG = "AppLockApplication"
    }
}
