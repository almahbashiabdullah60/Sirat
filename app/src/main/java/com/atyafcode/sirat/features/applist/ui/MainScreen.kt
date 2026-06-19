package com.atyafcode.sirat.features.applist.ui

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.atyafcode.sirat.R
import com.atyafcode.sirat.core.broadcast.DeviceAdmin
import com.atyafcode.sirat.core.navigation.Screen
import com.atyafcode.sirat.core.utils.appLockRepository
import com.atyafcode.sirat.core.utils.hasUsagePermission
import com.atyafcode.sirat.core.utils.isAccessibilityServiceEnabled
import com.atyafcode.sirat.core.utils.openAccessibilitySettings
import com.atyafcode.sirat.data.repository.BackendImplementation
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.rounded.Psychology
import com.atyafcode.sirat.features.chat.ui.ChatScreen
import com.atyafcode.sirat.features.behavior.ui.BehaviorScreen
import com.atyafcode.sirat.features.planbuilder.ui.PlanBuilderScreen
import com.atyafcode.sirat.ui.components.DonateModalBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private enum class MainTab(
    val route: String,
    val titleResId: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    APPS("apps", R.string.nav_apps, Icons.Outlined.Apps, Icons.Default.Apps),
    BEHAVIOR("behavior", R.string.nav_behavior, Icons.Outlined.BarChart, Icons.Default.BarChart),
    PLAN("plan", R.string.nav_plan, Icons.AutoMirrored.Outlined.Assignment, Icons.AutoMirrored.Filled.Assignment),
    REMINDERS("reminders", R.string.nav_reminders, Icons.Outlined.Psychology, Icons.Rounded.Psychology),
    AI_SETTINGS("ai_settings", R.string.ai_settings_title, Icons.Outlined.Settings, Icons.Default.Settings)
}

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun MainScreen(
    navController: NavController,
    mainViewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val isLoading by mainViewModel.isLoading.collectAsState()
    val lockedApps by mainViewModel.lockedAppsFlow.collectAsState()
    val unlockedApps by mainViewModel.unlockedAppsFlow.collectAsState()

    var showAddAppsSheet by remember { mutableStateOf(false) }

    var applockEnabled by remember { mutableStateOf(true) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var firstMissingPermission by remember { mutableStateOf<MissingPermission?>(null) }
    var selectedTab by remember { mutableStateOf(MainTab.APPS) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val appLockRepository = context.appLockRepository()
        applockEnabled = appLockRepository.isProtectEnabled()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val appLockRepository = context.appLockRepository()
                val backend = appLockRepository.getBackendImplementation()
                val isAntiUninstallEnabled = appLockRepository.isAntiUninstallEnabled()
                val dpm =
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val component = ComponentName(context, DeviceAdmin::class.java)

                firstMissingPermission = when {
                    !Settings.canDrawOverlays(context) -> MissingPermission.OVERLAY
                    backend == BackendImplementation.ACCESSIBILITY && !context.isAccessibilityServiceEnabled() -> MissingPermission.ACCESSIBILITY
                    backend == BackendImplementation.USAGE_STATS && !context.hasUsagePermission() -> MissingPermission.USAGE_STATS
                    backend == BackendImplementation.SHIZUKU && (runCatching { !Shizuku.pingBinder() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_DENIED }.getOrDefault(
                        true
                    )) -> MissingPermission.SHIZUKU

                    isAntiUninstallEnabled && !context.isAccessibilityServiceEnabled() -> MissingPermission.ACCESSIBILITY
                    isAntiUninstallEnabled && !dpm.isAdminActive(component) -> MissingPermission.DEVICE_ADMIN
                    else -> null
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val appLockRepository = context.appLockRepository()

    // Removed CommunityDialog and Donate dialog check from here as requested

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.app_name),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                HorizontalDivider()
                MainTab.entries.forEach { tab ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(tab.titleResId)) },
                        selected = selectedTab == tab,
                        onClick = {
                            if (tab == MainTab.AI_SETTINGS) {
                                scope.launch { drawerState.close() }
                                navController.navigate(Screen.AISettings.route)
                            } else {
                                selectedTab = tab
                                scope.launch { drawerState.close() }
                            }
                        },
                        icon = {
                            Icon(
                                if (selectedTab == tab) tab.selectedIcon else tab.icon,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                HorizontalDivider()

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.settings_screen_title)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.Settings.route)
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.trigger_exclusions_title)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.TriggerExclusions.route)
                    },
                    icon = { Icon(Icons.Default.Block, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.anti_uninstall_title)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.AntiUninstall.route)
                    },
                    icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                MediumTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.main_screen_menu_cd))
                        }
                    },
                    actions = {
                        Surface(
                            onClick = {
                                appLockRepository.setProtectEnabled(!applockEnabled)
                                applockEnabled = !applockEnabled
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (applockEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (applockEnabled) Icons.Default.Shield else Icons.Outlined.Shield,
                                    contentDescription = stringResource(R.string.main_screen_app_protection_cd),
                                    modifier = Modifier.size(18.dp),
                                    tint = if (applockEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (applockEnabled) stringResource(R.string.status_on) else stringResource(R.string.status_off),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (applockEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            floatingActionButton = {
                if (selectedTab == MainTab.APPS && !isLoading) {
                    FloatingActionButton(
                        onClick = { showAddAppsSheet = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.main_screen_search_cd)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    MainTab.APPS -> {
                        firstMissingPermission?.let { missingPerm ->
                            PermissionWarningBanner(
                                missingPermission = missingPerm,
                                onClick = {
                                    when (missingPerm) {
                                        MissingPermission.OVERLAY -> {
                                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                                data = "package:${context.packageName}".toUri()
                                            })
                                        }

                                        MissingPermission.ACCESSIBILITY -> {
                                            openAccessibilitySettings(context)
                                        }

                                        MissingPermission.USAGE_STATS -> {
                                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                        }

                                        MissingPermission.SHIZUKU -> {
                                            try {
                                                if (Shizuku.isPreV11()) {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.main_screen_shizuku_manual_permission_toast),
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                } else {
                                                    Shizuku.requestPermission(423)
                                                }
                                            } catch (_: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.main_screen_shizuku_not_available_toast),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }

                                        MissingPermission.DEVICE_ADMIN -> {
                                            val component =
                                                ComponentName(context, DeviceAdmin::class.java)
                                            val intent =
                                                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                                    putExtra(
                                                        DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                                        component
                                                    )
                                                    putExtra(
                                                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                                        context.getString(R.string.main_screen_device_admin_explanation)
                                                    )
                                                }
                                            context.startActivity(intent)
                                        }
                                    }
                                }
                            )
                        }
                        if (isLoading) {
                            LoadingContent(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        } else {
                            ProtectedAppsDashboard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                lockedApps = lockedApps,
                                onUnlockApp = { appItem ->
                                    mainViewModel.unlockApp(appItem.packageName)
                                }
                            )
                        }
                    }

                    MainTab.BEHAVIOR -> {
                        BehaviorScreen()
                    }

                    MainTab.PLAN -> {
                        PlanBuilderScreen(
                            onNavigateToSettings = {
                                navController.navigate(Screen.AISettings.route)
                            }
                        )
                    }

                    MainTab.REMINDERS -> {
                        ChatScreen(
                            onNavigateToSettings = {
                                navController.navigate(Screen.AISettings.route)
                            }
                        )
                    }

                    else -> {
                        EmptyTabPlaceholder(tab = selectedTab)
                    }
                }
            }
        }
    }

    if (showAddAppsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val selectedPackages = remember { mutableStateListOf<String>() }
        var bottomSheetSearchQuery by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = {
                bottomSheetSearchQuery = ""
                showAddAppsSheet = false
            },
            sheetState = sheetState
        ) {
            AddProtectedAppsSheetContent(
                unlockedApps = unlockedApps,
                searchQuery = bottomSheetSearchQuery,
                onSearchQueryChanged = { bottomSheetSearchQuery = it },
                selectedPackages = selectedPackages,
                onToggleSelection = { packageName ->
                    if (selectedPackages.contains(packageName)) {
                        selectedPackages.remove(packageName)
                    } else {
                        selectedPackages.add(packageName)
                    }
                },
                onSave = {
                    mainViewModel.lockApps(selectedPackages)
                    bottomSheetSearchQuery = ""
                    showAddAppsSheet = false
                },
                onCancel = {
                    bottomSheetSearchQuery = ""
                    showAddAppsSheet = false
                }
            )
        }
    }
}

@Composable
private fun EmptyTabPlaceholder(tab: MainTab) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(tab.titleResId),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.main_screen_dev_tab_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.main_screen_loading_applications_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProtectedAppsDashboard(
    modifier: Modifier = Modifier,
    lockedApps: List<AppItem>,
    onUnlockApp: (AppItem) -> Unit
) {
    if (lockedApps.isEmpty()) {
        EmptyDashboardState(modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 88.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(lockedApps, key = { it.packageName }) { appItem ->
                ProtectedAppItem(
                    appItem = appItem,
                    onUnlock = { onUnlockApp(appItem) }
                )
            }
        }
    }
}

@Composable
private fun EmptyDashboardState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Shield,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.no_protected_apps),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tap_to_secure_apps),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProtectedAppsSheetContent(
    unlockedApps: List<AppItem>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    selectedPackages: List<String>,
    onToggleSelection: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    val filteredApps by produceState(
        initialValue = unlockedApps,
        unlockedApps,
        searchQuery
    ) {
        value = if (searchQuery.isBlank()) {
            unlockedApps
        } else {
            withContext(Dispatchers.Default) {
                val lowerQuery = searchQuery.lowercase()
                unlockedApps.filter { appItem ->
                    appItem.label.lowercase().contains(lowerQuery) || 
                            appItem.packageName.contains(lowerQuery, ignoreCase = true)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.select_apps),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                onClick = onSave,
                enabled = selectedPackages.isNotEmpty()
            ) {
                Text(stringResource(R.string.protect_with_count, selectedPackages.size))
            }
        }

        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChanged,
            onSearch = { focusManager.clearFocus() },
            active = false,
            onActiveChange = {},
            placeholder = {
                Text(
                    stringResource(R.string.main_screen_search_apps_placeholder),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.main_screen_search_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(28.dp),
            colors = SearchBarDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {}

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(filteredApps, key = { it.packageName }) { appItem ->
                val isSelected = selectedPackages.contains(appItem.packageName)
                SelectableAppItem(
                    appItem = appItem,
                    isSelected = isSelected,
                    onClick = { onToggleSelection(appItem.packageName) }
                )
            }
        }
    }
}

@Composable
private fun ProtectedAppItem(
    appItem: AppItem,
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    val appInfo = appItem.applicationInfo

    var icon by remember(appInfo) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(appInfo) {
        withContext(Dispatchers.IO) {
            icon = AppIconCache.getIcon(context, appInfo)
        }
    }

    ListItem(
        headlineContent = {
            Text(
                text = appItem.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.protected_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Image(
                            bitmap = icon!!,
                            contentDescription = appItem.label,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onUnlock) {
                Icon(
                    imageVector = Icons.Outlined.LockOpen,
                    contentDescription = "Unlock ${appItem.label}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
    )
}

@Composable
private fun SelectableAppItem(
    appItem: AppItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val appInfo = appItem.applicationInfo

    var icon by remember(appInfo) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(appInfo) {
        withContext(Dispatchers.IO) {
            icon = AppIconCache.getIcon(context, appInfo)
        }
    }

    ListItem(
        headlineContent = {
            Text(
                text = appItem.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = appItem.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Image(
                            bitmap = icon!!,
                            contentDescription = appItem.label,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        },
        trailingContent = {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    )
}


private enum class MissingPermission(val titleResId: Int, val descriptionResId: Int) {
    OVERLAY(
        titleResId = R.string.permission_warning_overlay_title,
        descriptionResId = R.string.permission_warning_overlay_desc
    ),
    ACCESSIBILITY(
        titleResId = R.string.permission_warning_accessibility_title,
        descriptionResId = R.string.permission_warning_accessibility_desc
    ),
    USAGE_STATS(
        titleResId = R.string.permission_warning_usage_stats_title,
        descriptionResId = R.string.permission_warning_usage_stats_desc
    ),
    SHIZUKU(
        titleResId = R.string.permission_warning_shizuku_title,
        descriptionResId = R.string.permission_warning_shizuku_desc
    ),
    DEVICE_ADMIN(
        titleResId = R.string.permission_warning_device_admin_title,
        descriptionResId = R.string.permission_warning_device_admin_desc
    )
}

@Composable
private fun PermissionWarningBanner(
    missingPermission: MissingPermission,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${stringResource(missingPermission.titleResId)} ${stringResource(R.string.permission_warning_title_suffix)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(missingPermission.descriptionResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

