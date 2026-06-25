package com.atyafcode.sirat.features.setpassword.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.atyafcode.sirat.core.utils.SecurityUtils
import com.atyafcode.sirat.data.repository.PreferencesRepository
import com.atyafcode.sirat.ui.theme.titleMediumEmphasized

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlphanumericSetPasswordScreen(
    navController: NavController,
    isFirstTimeSetup: Boolean,
    skipVerification: Boolean = false
) {
    var passwordState by remember { mutableStateOf("") }
    var confirmPasswordState by remember { mutableStateOf("") }
    var isConfirmationMode by remember { mutableStateOf(false) }
    var isVerifyOldPasswordMode by remember { mutableStateOf(!isFirstTimeSetup && !skipVerification) }

    var isRandomMode by remember { mutableStateOf(false) }
    var passwordLength by remember { mutableStateOf(8f) }
    var passwordVisible by remember { mutableStateOf(true) }

    var showMismatchError by remember { mutableStateOf(false) }
    var showLengthError by remember { mutableStateOf(false) }
    var showMaxLengthError by remember { mutableStateOf(false) }
    var showInvalidOldPasswordError by remember { mutableStateOf(false) }

    val minLength = 8
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val appLockRepository = remember {
        (context.applicationContext as? AppLockApplication)?.appLockRepository
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isRandomMode) {
        if (!isRandomMode) {
            focusRequester.requestFocus()
        }
    }

    BackHandler {
        if (isFirstTimeSetup) {
            if (isConfirmationMode) {
                isConfirmationMode = false
            } else if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                Toast.makeText(context, R.string.set_pin_to_continue_toast, Toast.LENGTH_SHORT).show()
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
                    passwordState = ""
                    confirmPasswordState = ""
                    showInvalidOldPasswordError = false
                }
            })
        biometricPrompt.authenticate(promptInfo)
    }

    fun submitPassword(finalPass: String) {
        appLockRepository?.setLockType(PreferencesRepository.LOCK_TYPE_PASSWORD)
        appLockRepository?.setPassword(finalPass)
        appLockRepository?.setLockGenerationType(isRandomMode)
        appLockRepository?.setPasswordLength(passwordLength.toInt())
        
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
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            isVerifyOldPasswordMode -> stringResource(R.string.enter_current_password_label)
                            isConfirmationMode -> stringResource(R.string.confirm_alphanumeric_password_label)
                            else -> stringResource(R.string.set_alphanumeric_password_title)
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (!isVerifyOldPasswordMode && !isConfirmationMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.random_generation_label), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isRandomMode,
                        onCheckedChange = { 
                            isRandomMode = it
                            if (it) {
                                passwordState = SecurityGenerator.generateRandomPassword(passwordLength.toInt())
                                passwordVisible = true
                            } else {
                                passwordState = ""
                                passwordVisible = false
                            }
                        }
                    )
                }

                if (isRandomMode) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.password_length_label, passwordLength.toInt()), modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                passwordState = SecurityGenerator.generateRandomPassword(passwordLength.toInt())
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_code_cd))
                            }
                        }
                        Slider(
                            value = passwordLength,
                            onValueChange = { 
                                passwordLength = it
                                passwordState = SecurityGenerator.generateRandomPassword(it.toInt())
                            },
                            valueRange = 8f..32f,
                            steps = 23
                        )
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text(
                                text = passwordState,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Button(
                            onClick = {
                                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Sirat Password", passwordState)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, R.string.code_copied_toast, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.copy_code_button))
                        }

                        Button(
                            onClick = { submitPassword(passwordState) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.use_this_code))
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { 
                            navController.navigate(Screen.SetPassword.route) {
                                popUpTo(Screen.SetPasswordAlphanumeric.route) { inclusive = true }
                            }
                        }) {
                            Text(stringResource(R.string.use_pin_instead))
                        }
                    }
                }
            }

            if (!isRandomMode || isVerifyOldPasswordMode || isConfirmationMode) {
                Text(
                    text = when {
                        isVerifyOldPasswordMode -> stringResource(R.string.enter_current_password_label)
                        isConfirmationMode -> stringResource(R.string.confirm_alphanumeric_password_label)
                        else -> stringResource(R.string.create_alphanumeric_password_label)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = if (isConfirmationMode) confirmPasswordState else passwordState,
                    onValueChange = { input ->
                        val sanitized = SecurityUtils.sanitizePassword(input)
                        if (isConfirmationMode) confirmPasswordState = sanitized else passwordState = sanitized
                        showMismatchError = false
                        showLengthError = false
                        showMaxLengthError = false
                        showInvalidOldPasswordError = false
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    label = { Text(stringResource(R.string.password_hint)) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        val image = if (passwordVisible)
                            Icons.Filled.Visibility
                        else Icons.Filled.VisibilityOff

                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = null)
                        }
                    },
                    isError = showMismatchError || showLengthError || showMaxLengthError || showInvalidOldPasswordError,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                if (showMismatchError) {
                    Text(text = stringResource(R.string.passwords_dont_match_error), color = MaterialTheme.colorScheme.error)
                }
                if (showLengthError) {
                    Text(text = stringResource(R.string.password_too_short_error), color = MaterialTheme.colorScheme.error)
                }
                if (showInvalidOldPasswordError) {
                    Text(text = stringResource(R.string.incorrect_password_try_again), color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        val currentInput = if (isConfirmationMode) confirmPasswordState else passwordState
                        if (currentInput.length < minLength) {
                            showLengthError = true
                        } else {
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
                                !isConfirmationMode -> isConfirmationMode = true
                                else -> {
                                    if (passwordState == confirmPasswordState) {
                                        submitPassword(passwordState)
                                    } else {
                                        showMismatchError = true
                                        confirmPasswordState = ""
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.next_button))
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isVerifyOldPasswordMode && !isConfirmationMode) {
                    TextButton(onClick = { 
                        navController.navigate(Screen.SetPassword.route) {
                            popUpTo(Screen.SetPasswordAlphanumeric.route) { inclusive = true }
                        }
                    }) {
                        Text(stringResource(R.string.use_pin_instead))
                    }

                    OutlinedButton(
                        onClick = { navController.navigate(Screen.SupervisedMethodChoice.route) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.SupervisorAccount, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.switch_to_supervisor_lock))
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
                        }
                    ) {
                        Text(if (isVerifyOldPasswordMode) stringResource(R.string.cancel_button) else stringResource(R.string.start_over_button))
                    }
                }
            }
        }
    }
}
