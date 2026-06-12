package com.atyafcode.sirat.features.setpassword.ui

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.mrhwsn.composelock.Dot
import com.mrhwsn.composelock.LockCallback
import com.mrhwsn.composelock.PatternLock
import com.atyafcode.sirat.AppLockApplication
import com.atyafcode.sirat.R
import com.atyafcode.sirat.core.navigation.Screen
import com.atyafcode.sirat.core.navigation.findFragmentActivity
import com.atyafcode.sirat.core.utils.vibrate
import com.atyafcode.sirat.data.repository.PreferencesRepository
import com.atyafcode.sirat.ui.theme.titleMediumEmphasized

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternSetPasswordScreen(
    navController: NavController,
    isFirstTimeSetup: Boolean
) {
    var patternState by remember { mutableStateOf("") }
    var confirmPatternState by remember { mutableStateOf("") }
    var isConfirmationMode by remember { mutableStateOf(false) }
    var isVerifyOldPasswordMode by remember { mutableStateOf(!isFirstTimeSetup) }

    var showMismatchError by remember { mutableStateOf(false) }
    var showMinLengthError by remember { mutableStateOf(false) }
    var showInvalidOldPasswordError by remember { mutableStateOf(false) }

    val minLength = 4
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val appLockRepository = remember {
        (context.applicationContext as? AppLockApplication)?.appLockRepository
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler {
        if (isFirstTimeSetup) {
            if (isConfirmationMode) {
                isConfirmationMode = false
            } else {
                Toast.makeText(context, R.string.set_pin_to_continue_toast, Toast.LENGTH_SHORT)
                    .show()
            }
        } else {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                activity?.finish()
            }
        }
    }

    fun launchDeviceCredentialAuth() {
        if (activity == null) return
        val executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.authenticate_to_reset_pin_title))
            .setSubtitle(context.getString(R.string.use_device_pin_pattern_password_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        val biometricPrompt = BiometricPrompt(
            activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isVerifyOldPasswordMode = false
                    patternState = ""
                    confirmPatternState = ""
                    showInvalidOldPasswordError = false
                }
            })
        biometricPrompt.authenticate(promptInfo)
    }

    fun switchToPinMethod() {
        navController.navigate(Screen.SetPassword.route) {
            popUpTo(Screen.SetPasswordPattern.route) { inclusive = true }
        }
    }

    fun submitPattern() {
        val currentPattern = when {
            isVerifyOldPasswordMode -> patternState
            isConfirmationMode -> confirmPatternState
            else -> patternState
        }

        if (currentPattern.length < minLength) {
            showMinLengthError = true
            return
        }

        when {
            isVerifyOldPasswordMode -> {
                if (appLockRepository!!.validatePattern(patternState)) {
                    isVerifyOldPasswordMode = false
                    patternState = ""
                    showInvalidOldPasswordError = false
                } else {
                    showInvalidOldPasswordError = true
                    patternState = ""
                }
            }

            !isConfirmationMode -> {
                isConfirmationMode = true
                showMinLengthError = false
            }

            else -> {
                if (patternState == confirmPatternState) {
                    appLockRepository?.setLockType(PreferencesRepository.LOCK_TYPE_PATTERN)
                    appLockRepository?.setPattern(patternState)
                    Toast.makeText(
                        context,
                        context.getString(R.string.password_set_successfully_toast),
                        Toast.LENGTH_SHORT
                    ).show()

                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.SetPassword.route) { inclusive = true }
                        if (isFirstTimeSetup) {
                            popUpTo(Screen.AppIntro.route) { inclusive = true }
                        }
                    }
                } else {
                    showMismatchError = true
                    confirmPatternState = ""
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (isFirstTimeSetup && !isLandscape) {
                TopAppBar(
                    title = {
                        Text(
                            text = when {
                                isVerifyOldPasswordMode -> stringResource(R.string.enter_current_pin_title)
                                isConfirmationMode -> stringResource(R.string.confirm_pin_title)
                                else -> stringResource(R.string.set_new_pin_title)
                            },
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    ) { innerPadding ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when {
                            isVerifyOldPasswordMode -> "Enter current Pattern"
                            isConfirmationMode -> "Confirm pattern"
                            else -> "Create a pattern"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (showMismatchError) {
                        Text(
                            text = "Incorrect Pattern",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (showMinLengthError) {
                        Text(
                            text = "Patter length should be at least 4",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (showInvalidOldPasswordError) {
                        Text(
                            text = "Incorrect pattern",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.enter_pattern_to_continue),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.alpha(0.8f),
                        textAlign = TextAlign.Center
                    )

                    if (isVerifyOldPasswordMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { launchDeviceCredentialAuth() }) {
                            Text(stringResource(R.string.reset_using_device_password_button))
                        }
                    }

                    if (isFirstTimeSetup && !isVerifyOldPasswordMode && !isConfirmationMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { switchToPinMethod() }) {
                                Text(stringResource(R.string.use_pin_instead))
                            }
                            TextButton(onClick = { navController.navigate(Screen.SetPasswordAlphanumeric.route) }) {
                                Text(stringResource(R.string.use_password_button))
                            }
                        }
                    }

                    if (isVerifyOldPasswordMode || isConfirmationMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                if (isVerifyOldPasswordMode) {
                                    if (navController.previousBackStackEntry != null) {
                                        navController.popBackStack()
                                    } else {
                                        activity?.finish()
                                    }
                                } else {
                                    isConfirmationMode = false
                                    if (!isFirstTimeSetup) {
                                        isVerifyOldPasswordMode = true
                                    }
                                }
                                patternState = ""
                                confirmPatternState = ""
                                showMismatchError = false
                                showMinLengthError = false
                                showInvalidOldPasswordError = false
                            }
                        ) {
                            Text(
                                if (isVerifyOldPasswordMode) stringResource(R.string.cancel_button)
                                else stringResource(R.string.start_over_button)
                            )
                        }
                    }
                }

                PatternLock(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    dimension = 3,
                    sensitivity = 50f,
                    dotsColor = MaterialTheme.colorScheme.primary,
                    dotsSize = 14f,
                    linesColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    linesStroke = 6f,
                    animationDuration = 120,
                    callback = object : LockCallback {
                        override fun onStart(dot: Dot) {
                            if (!appLockRepository!!.shouldDisableHaptics()) {
                                vibrate(context, 10)
                            }
                        }

                        override fun onDotConnected(dot: Dot) {
                            if (!appLockRepository!!.shouldDisableHaptics()) {
                                vibrate(context, 10)
                            }
                        }

                        override fun onResult(result: List<Dot>) {
                            val patternString = result.joinToString("") { it.id.toString() }
                            if (isVerifyOldPasswordMode) {
                                patternState = patternString
                            } else if (isConfirmationMode) {
                                confirmPatternState = patternString
                            } else {
                                patternState = patternString
                            }
                            submitPattern()
                        }
                    }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when {
                            isVerifyOldPasswordMode -> stringResource(R.string.enter_current_pin_label)
                            isConfirmationMode -> stringResource(R.string.confirm_new_pin_label)
                            else -> stringResource(R.string.create_new_pin_label)
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )

                    if (showMismatchError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.pins_dont_match_error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (showMinLengthError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Patter length should be at least 4",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (showInvalidOldPasswordError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Incorrect pattern",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.enter_pattern_to_continue),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.alpha(0.8f),
                        textAlign = TextAlign.Center
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PatternLock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        dimension = 3,
                        sensitivity = 50f,
                        dotsColor = MaterialTheme.colorScheme.primary,
                        dotsSize = 16f,
                        linesColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        linesStroke = 7f,
                        animationDuration = 120,
                        callback = object : LockCallback {
                            override fun onStart(dot: Dot) {
                                if (!appLockRepository!!.shouldDisableHaptics()) {
                                    vibrate(context, 10)
                                }
                            }

                            override fun onDotConnected(dot: Dot) {
                                if (!appLockRepository!!.shouldDisableHaptics()) {
                                    vibrate(context, 10)
                                }
                            }

                            override fun onResult(result: List<Dot>) {
                                val patternString = result.joinToString("") { it.id.toString() }
                                if (isVerifyOldPasswordMode) {
                                    patternState = patternString
                                } else if (isConfirmationMode) {
                                    confirmPatternState = patternString
                                } else {
                                    patternState = patternString
                                }
                                submitPattern()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isVerifyOldPasswordMode && !isConfirmationMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { switchToPinMethod() }) {
                                Text(stringResource(R.string.use_pin_instead))
                            }
                            TextButton(onClick = { navController.navigate(Screen.SetPasswordAlphanumeric.route) }) {
                                Text(stringResource(R.string.use_password_button))
                            }
                        }
                    }

                    if (isVerifyOldPasswordMode) {
                        TextButton(onClick = { launchDeviceCredentialAuth() }) {
                            Text(stringResource(R.string.reset_using_device_password_button))
                        }
                    }

                    if (isVerifyOldPasswordMode || isConfirmationMode) {
                        TextButton(
                            onClick = {
                                if (isVerifyOldPasswordMode) {
                                    if (navController.previousBackStackEntry != null) {
                                        navController.popBackStack()
                                    } else {
                                        activity?.finish()
                                    }
                                } else {
                                    isConfirmationMode = false
                                    if (!isFirstTimeSetup) {
                                        isVerifyOldPasswordMode = true
                                    }
                                }
                                patternState = ""
                                confirmPatternState = ""
                                showMismatchError = false
                                showMinLengthError = false
                                showInvalidOldPasswordError = false
                            }
                        ) {
                            Text(
                                if (isVerifyOldPasswordMode) stringResource(R.string.cancel_button)
                                else stringResource(R.string.start_over_button)
                            )
                        }
                    }
                }
            }
        }
    }
}

