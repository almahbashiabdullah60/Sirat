package com.atyafcode.sirat.features.lockscreen.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.atyafcode.sirat.R
import com.atyafcode.sirat.core.utils.appLockRepository
import com.atyafcode.sirat.core.utils.vibrate
import com.atyafcode.sirat.data.repository.AppLockRepository
import com.atyafcode.sirat.data.repository.PreferencesRepository
import com.atyafcode.sirat.services.AppLockManager
import com.atyafcode.sirat.ui.icons.Backspace
import com.atyafcode.sirat.ui.icons.Fingerprint
import com.atyafcode.sirat.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class PasswordOverlayActivity : FragmentActivity() {
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var appLockRepository: AppLockRepository
    internal var lockedPackageNameFromIntent: String? = null
    internal var triggeringPackageNameFromIntent: String? = null

    private var isBiometricPromptShowingLocal = false
    private var appName: String = ""

    private val TAG = "PasswordOverlayActivity"

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.atyafcode.sirat.core.utils.LocaleUtils.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lockedPackageNameFromIntent = intent.getStringExtra("locked_package")
        triggeringPackageNameFromIntent = intent.getStringExtra("triggering_package")
        if (lockedPackageNameFromIntent == null) {
            Log.e(TAG, "No locked_package name provided in intent. Finishing.")
            finishAffinity()
            return
        }

        enableEdgeToEdge()

        appLockRepository = applicationContext.appLockRepository()

        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Log.d(TAG, "Back pressed ignored on AppLock overlay")
                }
            })

        setupWindow()
        loadAppNameAndSetupUI()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        setupBiometricPromptInternal()
    }

    override fun onPostResume() {
        super.onPostResume()
        setupBiometricPromptInternal()
        if (appLockRepository.isBiometricAuthEnabled()) {
            triggerBiometricPrompt()
        }
    }

    private fun setupWindow() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SECURE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }

        val layoutParams = window.attributes
        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT

        if (appLockRepository.shouldUseMaxBrightness()) {
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        window.attributes = layoutParams
    }

    private fun loadAppNameAndSetupUI() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                appName = packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(lockedPackageNameFromIntent!!, 0)
                ).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading app name: ${e.message}")
                appName = getString(R.string.default_app_name)
            }
        }
        setupUI()
    }

    private fun setupUI() {
        val onPinAttemptCallback = { pin: String ->
            val isValid = appLockRepository.validatePassword(pin)
            if (isValid) {
                lockedPackageNameFromIntent?.let { pkgName ->
                    AppLockManager.unlockApp(pkgName)
                    finishAfterTransition()
                }
            }
            isValid
        }

        // val onPatternAttemptCallback = ...

        setContent {
            AppLockTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    val lockType = appLockRepository.getLockType()
                    when (lockType) {
                        PreferencesRepository.LOCK_TYPE_SUPERVISED -> {
                            SupervisedLockOverlay(
                                lockedAppName = appName,
                                onUnlock = {
                                    lockedPackageNameFromIntent?.let { pkgName ->
                                        AppLockManager.unlockApp(pkgName)
                                        finishAfterTransition()
                                    }
                                },
                                onExit = {
                                    finish()
                                }
                            )
                        }

                        PreferencesRepository.LOCK_TYPE_PASSWORD -> {
                            AlphanumericPasswordOverlayScreen(
                                modifier = Modifier.padding(innerPadding),
                                showBiometricButton = appLockRepository.isBiometricAuthEnabled(),
                                fromMainActivity = false,
                                onBiometricAuth = { triggerBiometricPrompt() },
                                onAuthSuccess = {},
                                lockedAppName = appName,
                                triggeringPackageName = triggeringPackageNameFromIntent,
                                onPasswordAttempt = onPinAttemptCallback,
                                showCloseButton = true,
                                onClose = { finish() }
                            )
                        }

                        else -> {
                            PinPasswordOverlayScreen(
                                modifier = Modifier.padding(innerPadding),
                                showBiometricButton = appLockRepository.isBiometricAuthEnabled(),
                                fromMainActivity = false,
                                onBiometricAuth = { triggerBiometricPrompt() },
                                onAuthSuccess = {},
                                lockedAppName = appName,
                                triggeringPackageName = triggeringPackageNameFromIntent,
                                onPinAttempt = onPinAttemptCallback,
                                showCloseButton = true,
                                onClose = { finish() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setupBiometricPromptInternal() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt =
            BiometricPrompt(this@PasswordOverlayActivity, executor, authenticationCallbackInternal)

        val appNameForPrompt = appName.ifEmpty { getString(R.string.this_app) }
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_app_title, appNameForPrompt))
            .setSubtitle(getString(R.string.confirm_biometric_subtitle))
            .setNegativeButtonText(getString(R.string.use_pin_button))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .setConfirmationRequired(false)
            .build()
    }

    private val authenticationCallbackInternal =
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                isBiometricPromptShowingLocal = false
                AppLockManager.reportBiometricAuthFinished()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isBiometricPromptShowingLocal = false
                lockedPackageNameFromIntent?.let { pkgName ->
                    AppLockManager.temporarilyUnlockAppWithBiometrics(pkgName)
                }
                finishAfterTransition()
            }
        }

    override fun onResume() {
        super.onResume()
        AppLockManager.isLockScreenShown.set(true)
        applyUserPreferences()
    }

    private fun applyUserPreferences() {
        if (appLockRepository.shouldUseMaxBrightness()) {
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
            if (window.decorView.isAttachedToWindow) {
                windowManager.updateViewLayout(window.decorView, window.attributes)
            }
        }
    }

    fun triggerBiometricPrompt() {
        if (appLockRepository.isBiometricAuthEnabled()) {
            AppLockManager.reportBiometricAuthStarted()
            isBiometricPromptShowingLocal = true
            try {
                biometricPrompt.authenticate(promptInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling biometricPrompt.authenticate: ${e.message}", e)
                isBiometricPromptShowingLocal = false
                AppLockManager.reportBiometricAuthFinished()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations && !isBiometricPromptShowingLocal) {
            AppLockManager.isLockScreenShown.set(false)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        AppLockManager.isLockScreenShown.set(false)
        if (!isFinishing && !isDestroyed) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLockManager.isLockScreenShown.set(false)
        AppLockManager.reportBiometricAuthFinished()
    }
}


@Composable
fun PinPasswordOverlayScreen(
    modifier: Modifier = Modifier,
    showBiometricButton: Boolean = false,
    fromMainActivity: Boolean = false,
    showCloseButton: Boolean = false,
    onClose: () -> Unit = {},
    onBiometricAuth: () -> Unit = {},
    onAuthSuccess: () -> Unit,
    lockedAppName: String? = null,
    triggeringPackageName: String? = null,
    onPinAttempt: ((pin: String) -> Boolean)? = null
) {
    val appLockRepository = LocalContext.current.appLockRepository()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenHeightDp = configuration.screenHeightDp.dp

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (showCloseButton) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 8.dp, top = 8.dp)
                        .align(Alignment.TopStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            val passwordState = remember { mutableStateOf("") }
            var showError by remember { mutableStateOf(false) }
            val minLength = 4

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (!fromMainActivity && !lockedAppName.isNullOrEmpty())
                                stringResource(R.string.continue_to_app, lockedAppName)
                            else
                                stringResource(R.string.enter_password_to_continue),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        PasswordIndicators(
                            passwordLength = passwordState.value.length,
                        )

                        if (showError) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.incorrect_pin_try_again),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        KeypadSection(
                            passwordState = passwordState,
                            minLength = minLength,
                            showBiometricButton = showBiometricButton,
                            fromMainActivity = fromMainActivity,
                            onBiometricAuth = onBiometricAuth,
                            onAuthSuccess = onAuthSuccess,
                            onPinAttempt = onPinAttempt,
                            onPasswordChange = {
                                showError = false
                                onPinAttempt?.invoke(passwordState.value)
                            },
                            onPinIncorrect = { showError = true }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = if (fromMainActivity) 24.dp else 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    val topSpacerHeight = if (screenHeightDp < 600.dp) 12.dp else 48.dp
                    Spacer(modifier = Modifier.height(topSpacerHeight))

                    Text(
                        text = if (!fromMainActivity && !lockedAppName.isNullOrEmpty())
                            stringResource(R.string.continue_to_app, lockedAppName)
                        else
                            stringResource(R.string.enter_password_to_continue),
                        style = if (!fromMainActivity && !lockedAppName.isNullOrEmpty())
                            MaterialTheme.typography.titleLargeEmphasized
                        else
                            MaterialTheme.typography.headlineMediumEmphasized,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PasswordIndicators(
                        passwordLength = passwordState.value.length,
                    )

                    if (showError) {
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
                        minLength = minLength,
                        showBiometricButton = showBiometricButton,
                        fromMainActivity = fromMainActivity,
                        onBiometricAuth = onBiometricAuth,
                        onAuthSuccess = onAuthSuccess,
                        onPinAttempt = onPinAttempt,
                        onPasswordChange = {
                            showError = false
                            onPinAttempt?.invoke(passwordState.value)
                        },
                        onPinIncorrect = { showError = true }
                    )
                }
            }
        }
    }
}

@Composable
fun PasswordIndicators(
    passwordLength: Int
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenWidthDp = configuration.screenWidthDp.dp

    val indicatorSize = remember(screenWidthDp) {
        if (screenWidthDp >= 600.dp) 28.dp else 22.dp
    }

    val indicatorSpacing = remember(screenWidthDp) {
        if (screenWidthDp >= 600.dp) 14.dp else 8.dp
    }

    val maxWidth = if (isLandscape) screenWidthDp * 0.5f else screenWidthDp * 0.85f

    val lazyListState = rememberLazyListState()

    LaunchedEffect(passwordLength) {
        if (passwordLength > 0) {
            lazyListState.animateScrollToItem(index = passwordLength - 1)
        }
    }

    Box(
        modifier = Modifier
            .width(maxWidth)
            .height(indicatorSize + 32.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(indicatorSpacing, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(passwordLength) { index ->
                key("digit_$index") {
                    val isNewest = index == passwordLength - 1
                    var animationTarget by remember { mutableStateOf(0f) }

                    LaunchedEffect(Unit) { animationTarget = 1f }

                    val animationProgress by animateFloatAsState(
                        targetValue = animationTarget,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "indicatorProgress"
                    )

                    val scale = if (isNewest && animationProgress < 1f) 1.2f else 1f

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .size(indicatorSize)
                            .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun KeypadSection(
    passwordState: MutableState<String>,
    minLength: Int,
    showBiometricButton: Boolean,
    fromMainActivity: Boolean = false,
    onBiometricAuth: () -> Unit,
    onAuthSuccess: () -> Unit,
    onPinAttempt: ((pin: String) -> Boolean)? = null,
    onPasswordChange: () -> Unit,
    onPinIncorrect: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    val horizontalPadding = if (isLandscape) 0.dp else screenWidthDp * 0.12f
    val buttonSpacing = if (isLandscape) screenHeightDp * 0.015f else screenWidthDp * 0.02f
    val buttonSize = if (isLandscape) 64.dp else 72.dp

    val onDigitKeyClick = { key: String ->
        passwordState.value += key
        onPasswordChange()
    }

    val disableHaptics = context.appLockRepository().shouldDisableHaptics()

    val onSpecialKeyClick = { key: String ->
        val appLockRepository = context.appLockRepository()
        when (key) {
            "0" -> {
                passwordState.value += key
                onPasswordChange()
            }
            "backspace" -> {
                if (passwordState.value.isNotEmpty()) {
                    passwordState.value = passwordState.value.dropLast(1)
                    onPasswordChange()
                }
            }
            "proceed" -> {
                if (passwordState.value.length < minLength) {
                    if (!disableHaptics) vibrate(context, 100)
                    passwordState.value = ""
                } else {
                    if (fromMainActivity) {
                        if (appLockRepository.validatePassword(passwordState.value)) {
                            onAuthSuccess()
                        } else {
                            passwordState.value = ""
                            if (!disableHaptics) vibrate(context, 100)
                            onPinIncorrect()
                        }
                    } else {
                        onPinAttempt?.let { attempt ->
                            if (!attempt(passwordState.value)) {
                                passwordState.value = ""
                                if (!disableHaptics) vibrate(context, 100)
                            }
                        }
                    }
                }
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(buttonSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (isLandscape) Modifier.navigationBarsPadding() else Modifier.padding(horizontal = horizontalPadding).navigationBarsPadding()
    ) {
        if (showBiometricButton) {
            FilledTonalIconButton(
                onClick = onBiometricAuth,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Fingerprint,
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    contentDescription = stringResource(R.string.biometric_authentication_cd),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        KeypadRow(disableHaptics, listOf("1", "2", "3"), emptyList(), onDigitKeyClick, buttonSize, buttonSpacing)
        KeypadRow(disableHaptics, listOf("4", "5", "6"), emptyList(), onDigitKeyClick, buttonSize, buttonSpacing)
        KeypadRow(disableHaptics, listOf("7", "8", "9"), emptyList(), onDigitKeyClick, buttonSize, buttonSpacing)
        KeypadRow(disableHaptics, listOf("backspace", "0", "proceed"), listOf(Backspace, null, Icons.AutoMirrored.Rounded.KeyboardArrowRight), onSpecialKeyClick, buttonSize, buttonSpacing)
    }
}

@Composable
fun KeypadRow(
    disableHaptics: Boolean = false,
    keys: List<String>,
    icons: List<ImageVector?> = emptyList(),
    onKeyClick: (String) -> Unit,
    buttonSize: Dp,
    buttonSpacing: Dp
) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(buttonSpacing)) {
        keys.forEachIndexed { index, key ->
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            val containerColor = if (isPressed) MaterialTheme.colorScheme.primaryContainer 
                                 else if (icons.getOrNull(index) != null) MaterialTheme.colorScheme.secondaryContainer 
                                 else MaterialTheme.colorScheme.surfaceVariant

            FilledTonalButton(
                onClick = {
                    if (!disableHaptics) vibrate(context, 100)
                    onKeyClick(key)
                },
                modifier = Modifier.size(buttonSize),
                interactionSource = interactionSource,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = containerColor)
            ) {
                if (icons.getOrNull(index) != null) {
                    Icon(icons[index]!!, contentDescription = key, modifier = Modifier.size(24.dp))
                } else {
                    Text(key, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

