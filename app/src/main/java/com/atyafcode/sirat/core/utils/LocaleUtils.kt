package com.atyafcode.sirat.core.utils

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.atyafcode.sirat.data.repository.PreferencesRepository
import java.util.Locale

object LocaleUtils {
    fun applyLocale(context: Context): Context {
        val repository = PreferencesRepository(context)
        val language = repository.getAppLanguage()
        
        if (language == "system") {
            return context
        }

        val locale = Locale(language)
        Locale.setDefault(locale)
        
        val resources = context.resources
        val configuration = resources.configuration
        
        val newConfig = Configuration(configuration)
        newConfig.setLocales(LocaleList(locale))
        
        return context.createConfigurationContext(newConfig)
    }
}

