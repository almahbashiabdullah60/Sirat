package com.atyafcode.sirat

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.atyafcode.sirat.core.navigation.AppNavHost
import com.atyafcode.sirat.core.navigation.NavigationManager
import com.atyafcode.sirat.ui.theme.AppLockTheme

class MainActivity : FragmentActivity() {

    private lateinit var navigationManager: NavigationManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.atyafcode.sirat.core.utils.LocaleUtils.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        navigationManager = NavigationManager(this)

        setContent {
            AppLockTheme {
                val navController = rememberNavController()
                val startDestination = navigationManager.determineStartDestination()

                AppNavHost(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }
    }


}



