package com.atyafcode.sirat.features.setpassword.ui

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.atyafcode.sirat.AppLockApplication
import com.atyafcode.sirat.R
import com.atyafcode.sirat.core.navigation.Screen
import com.atyafcode.sirat.core.navigation.findFragmentActivity
import com.atyafcode.sirat.core.utils.SecurityGenerator
import com.atyafcode.sirat.data.repository.PreferencesRepository
import com.atyafcode.sirat.features.lockscreen.ui.KeypadRow
import com.atyafcode.sirat.features.lockscreen.ui.PasswordIndicators
import com.atyafcode.sirat.ui.icons.Backspace
import com.atyafcode.sirat.ui.theme.titleMediumEmphasized

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetPasswordScreen(
    navController: NavController,
    isFirstTimeSetup: Boolean
) {
    var passwordState by remember { mutableStateOf("") }
    var confirmPasswordState by remember { mutableStateOf("") }
    var isConfirmationMode by remember { mutableStateOf(false) }
    var isVerifyOldPasswordMode by remember { mutableStateOf(!isFirstTimeSetup) }

    var isRandomMode by remember { mutableStateOf(false) }
    var pinLength by remember { mutableStateOf(4f) }

    var showMismatchError by remember { mutableStateOf(false) }
    var showLengthError by remember { mutableStateOf(false) }
    var showInvalidOldPasswordError by remember { mutableStateOf(false) }
    val minLength = 4

    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val appLockRepository = remember {
        (context.applicationContext as? AppLockApplication)?.appLockRepository
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    val horizontalPadding = if (isLandscape) 0.dp else screenWidthDp * 0.12f
    val buttonSpacing = if (isLandscape) screenHeightDp * 0.015f else screenWidthDp * 0.02f
    val buttonSize = if (isLandscape) 64.dp else 72.dp

    BackHandler {
        if (isFirstTimeSetup) {
            // If first time, allow going back to Method Choice instead of being stuck
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                Toast.makeText(context, R.string.set_pin_to_continue_toast, Toast.LENGTH_SHORT).show()
            }
        } else {
            // Normal settings change, always allow back
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
                    passwordState = ""
                    confirmPasswordState = ""
                    showInvalidOldPasswordError = false
                }
            })
        biometricPrompt.authenticate(promptInfo)
    }

    fun onFinish(pin: String) {
        appLockRepository?.setLockType(PreferencesRepository.LOCK_TYPE_PIN)
        appLockRepository?.setPassword(pin)
        appLockRepository?.setLockGenerationType(isRandomMode)
        appLockRepository?.setPinLength(pinLength.toInt())
        
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
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (!isLandscape) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isVerifyOldPasswordMode && !isConfirmationMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.random_generation_label), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isRandomMode,
                        onCheckedChange = { 
                            isRandomMode = it
                            if (it) {
                                passwordState = SecurityGenerator.generateRandomPin(pinLength.toInt())
                            } else {
                                passwordState = ""
                            }
                        }
                    )
                }

                if (isRandomMode) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.pin_length_label, pinLength.toInt()), modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                passwordState = SecurityGenerator.generateRandomPin(pinLength.toInt())
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_code_cd))
                            }
                        }
                        Slider(
                            value = pinLength,
                            onValueChange = { 
                                pinLength = it
                                passwordState = SecurityGenerator.generateRandomPin(it.toInt())
                            },
                            valueRange = 4f..12f,
                            steps = 7
                        )
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text(
                                text = passwordState,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center,
                                letterSpacing = 8.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        Button(
                            onClick = { 
                                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Sirat PIN", passwordState)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, R.string.code_copied_toast, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.copy_code_button))
                        }

                        Button(
                            onClick = { isConfirmationMode = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.use_this_code))
                        }
                    }
                }
            }

            if (!isRandomMode || isVerifyOldPasswordMode || isConfirmationMode) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = when {
                        isVerifyOldPasswordMode -> stringResource(R.string.enter_current_pin_label)
                        isConfirmationMode -> stringResource(R.string.confirm_new_pin_label)
                        else -> stringResource(R.string.create_new_pin_label)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (showMismatchError) {
                    Text(text = stringResource(R.string.pins_dont_match_error), color = MaterialTheme.colorScheme.error)
                }
                if (showLengthError) {
                    Text(text = stringResource(R.string.pin_min_length_error), color = MaterialTheme.colorScheme.error)
                }
                if (showInvalidOldPasswordError) {
                    Text(text = stringResource(R.string.incorrect_pin_try_again), color = MaterialTheme.colorScheme.error)
                }

                val currentPassword = when {
                    isVerifyOldPasswordMode -> passwordState
                    isConfirmationMode -> confirmPasswordState
                    else -> passwordState
                }

                PasswordIndicators(passwordLength = currentPassword.length)

                Text(
                    text = when {
                        isVerifyOldPasswordMode -> stringResource(R.string.enter_current_pin_label)
                        isConfirmationMode -> stringResource(R.string.re_enter_new_pin_confirm_label)
                        else -> stringResource(R.string.tooltip_create_pin_min_length)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.alpha(0.8f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.weight(1f))

                if (isVerifyOldPasswordMode) {
                    TextButton(onClick = { launchDeviceCredentialAuth() }) {
                        Text(stringResource(R.string.reset_using_device_password_button))
                    }
                }

                if (isVerifyOldPasswordMode || isConfirmationMode) {
                    TextButton(onClick = {
                        if (isVerifyOldPasswordMode) {
                            if (navController.previousBackStackEntry != null) navController.popBackStack()
                            else activity?.finish()
                        } else {
                            isConfirmationMode = false
                            if (!isFirstTimeSetup) isVerifyOldPasswordMode = true
                        }
                        passwordState = ""
                        confirmPasswordState = ""
                        showMismatchError = false
                        showLengthError = false
                    }) {
                        Text(if (isVerifyOldPasswordMode) stringResource(R.string.cancel_button) else stringResource(R.string.start_over_button))
                    }
                }

                if (!isVerifyOldPasswordMode && !isConfirmationMode) {
                    TextButton(onClick = { navController.navigate(Screen.SetPasswordAlphanumeric.route) }) {
                        Text(stringResource(R.string.use_password_button))
                    }
                    
                    OutlinedButton(
                        onClick = { navController.navigate(Screen.SupervisedMethodChoice.route) },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.SupervisorAccount, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.switch_to_supervisor_lock))
                    }
                }

                val onKeyClick: (String) -> Unit = { key ->
                    val currentActivePassword = when {
                        isVerifyOldPasswordMode -> passwordState
                        isConfirmationMode -> confirmPasswordState
                        else -> passwordState
                    }
                    val updatePassword: (String) -> Unit = when {
                        isVerifyOldPasswordMode -> { newPass -> passwordState = newPass }
                        isConfirmationMode -> { newPass -> confirmPasswordState = newPass }
                        else -> { newPass -> passwordState = newPass }
                    }

                    when (key) {
                        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> updatePassword(currentActivePassword + key)
                        "backspace" -> if (currentActivePassword.isNotEmpty()) updatePassword(currentActivePassword.dropLast(1))
                        "proceed" -> {
                            if (currentActivePassword.length >= minLength) {
                                when {
                                    isVerifyOldPasswordMode -> {
                                        if (appLockRepository!!.validatePassword(passwordState)) {
                                            isVerifyOldPasswordMode = false
                                            passwordState = ""
                                        } else {
                                            showInvalidOldPasswordError = true
                                            passwordState = ""
                                        }
                                    }
                                    !isConfirmationMode -> {
                                        isConfirmationMode = true
                                    }
                                    else -> {
                                        if (passwordState == confirmPasswordState) {
                                            onFinish(passwordState)
                                        } else {
                                            showMismatchError = true
                                            confirmPasswordState = ""
                                        }
                                    }
                                }
                            } else {
                                showLengthError = true
                            }
                        }
                    }
                }

                val disableHaptics = appLockRepository?.shouldDisableHaptics() ?: false
                Column(
                    verticalArrangement = Arrangement.spacedBy(buttonSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = horizontalPadding)
                ) {
                    KeypadRow(disableHaptics, listOf("1", "2", "3"), listOf(null, null, null), onKeyClick, buttonSize, buttonSpacing)
                    KeypadRow(disableHaptics, listOf("4", "5", "6"), listOf(null, null, null), onKeyClick, buttonSize, buttonSpacing)
                    KeypadRow(disableHaptics, listOf("7", "8", "9"), listOf(null, null, null), onKeyClick, buttonSize, buttonSpacing)
                    KeypadRow(disableHaptics, listOf("backspace", "0", "proceed"), 
                        listOf(Backspace, null, if (isConfirmationMode || isVerifyOldPasswordMode) Icons.Default.Check else Icons.AutoMirrored.Rounded.KeyboardArrowRight),
                        onKeyClick, buttonSize, buttonSpacing)
                }
            }
        }
    }
}
