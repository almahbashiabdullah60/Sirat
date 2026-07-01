package com.atyafcode.sirat.features.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atyafcode.sirat.R
import com.atyafcode.sirat.core.broadcast.DeviceAdmin
import com.atyafcode.sirat.core.utils.SecurityUtils
import com.atyafcode.sirat.core.utils.appLockRepository
import com.atyafcode.sirat.data.repository.AppLockRepository
import com.atyafcode.sirat.data.repository.PreferencesRepository
import com.atyafcode.sirat.features.lockscreen.ui.KeypadSection
import com.atyafcode.sirat.features.lockscreen.ui.PasswordIndicators
import com.atyafcode.sirat.features.lockscreen.ui.SupervisedLockOverlay
// import com.atyafcode.sirat.features.lockscreen.ui.PatternLockScreen
import com.atyafcode.sirat.ui.theme.AppLockTheme

class AdminDisableActivity : ComponentActivity() {
    private lateinit var appLockRepository: AppLockRepository
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var deviceAdminComponentName: ComponentName

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.atyafcode.sirat.core.utils.LocaleUtils.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        deviceAdminComponentName = ComponentName(this, DeviceAdmin::class.java)

        appLockRepository = appLockRepository()

        // Set up back press callback to prevent admin disabling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val deviceAdmin = DeviceAdmin()
                deviceAdmin.setPasswordVerified(this@AdminDisableActivity, false)
                finish()
            }
        })

        setContent {
            AppLockTheme {
                Scaffold { padding ->
                    val lockType = appLockRepository.getLockType()
                    when (lockType) {
                        PreferencesRepository.LOCK_TYPE_PASSWORD -> {
                            AdminDisablePasswordScreen(
                                modifier = Modifier.padding(padding),
                                onPasswordVerified = { handleSuccess() },
                                onCancel = { handleCancel() },
                                validatePassword = { inputPassword ->
                                    appLockRepository.validatePassword(inputPassword)
                                        .also { isValid ->
                                            if (!isValid) {
                                                Toast.makeText(
                                                    this@AdminDisableActivity,
                                                    R.string.incorrect_password_try_again,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                }
                            )
                        }

                        PreferencesRepository.LOCK_TYPE_SUPERVISED -> {
                            SupervisedLockOverlay(
                                lockedAppName = stringResource(R.string.settings_screen_anti_uninstall_title),
                                onUnlock = { handleSuccess() },
                                onExit = { handleCancel() }
                            )
                        }

                        else -> {
                            AdminDisableScreen(
                                modifier = Modifier.padding(padding),
                                onPasswordVerified = { handleSuccess() },
                                onCancel = { handleCancel() },
                                validatePassword = { inputPassword ->
                                    appLockRepository.validatePassword(inputPassword)
                                        .also { isValid ->
                                            if (!isValid) {
                                                Toast.makeText(
                                                    this@AdminDisableActivity,
                                                    R.string.incorrect_pin_try_again,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleSuccess() {
        val deviceAdmin = DeviceAdmin()
        deviceAdmin.setPasswordVerified(this, true)

        try {
            if (devicePolicyManager.isAdminActive(deviceAdminComponentName)) {
                devicePolicyManager.setUninstallBlocked(deviceAdminComponentName, packageName, false)
            }
        } catch (e: Exception) {
            Log.e("AdminDisable", "Failed to unblock uninstall: ${e.message}")
        }

        appLockRepository.setAntiUninstallEnabled(false)
        Toast.makeText(this, R.string.password_verified_admin, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun handleCancel() {
        val deviceAdmin = DeviceAdmin()
        deviceAdmin.setPasswordVerified(this, false)
        finish()
    }
}

@Composable
fun AdminDisableScreen(
    modifier: Modifier = Modifier,
    onPasswordVerified: () -> Unit,
    onCancel: () -> Unit,
    validatePassword: (String) -> Boolean
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val passwordState = remember { mutableStateOf("") }
        val showError = remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = stringResource(R.string.unlock_to_disable_admin),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            PasswordIndicators(
                passwordLength = passwordState.value.length
            )

            if (showError.value) {
                Text(
                    text = stringResource(R.string.incorrect_pin_try_again),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            KeypadSection(
                passwordState = passwordState,
                minLength = 4,
                showBiometricButton = false,
                fromMainActivity = false,
                onBiometricAuth = {},
                onAuthSuccess = {},
                onPinAttempt = { pin ->
                    val isValid = validatePassword(pin)
                    if (isValid) {
                        onPasswordVerified()
                    } else {
                        onCancel()
                    }
                    isValid
                },
                onPasswordChange = { showError.value = false },
                onPinIncorrect = { showError.value = true }
            )
        }
    }
}

@Composable
fun AdminDisablePasswordScreen(
    modifier: Modifier = Modifier,
    onPasswordVerified: () -> Unit,
    onCancel: () -> Unit,
    validatePassword: (String) -> Boolean
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        var passwordState by remember { mutableStateOf("") }
        var showError by remember { mutableStateOf(false) }
        var passwordVisible by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.unlock_to_disable_admin),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = passwordState,
                onValueChange = { input ->
                    passwordState = SecurityUtils.sanitizePassword(input)
                    showError = false
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
                isError = showError,
                singleLine = true
            )

            if (showError) {
                Text(
                    text = stringResource(R.string.incorrect_password_try_again),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp).align(Alignment.Start)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel_button))
                }

                Button(
                    onClick = {
                        if (validatePassword(passwordState)) {
                            onPasswordVerified()
                        } else {
                            showError = true
                            passwordState = ""
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.verify_button))
                }
            }
        }
    }
}


