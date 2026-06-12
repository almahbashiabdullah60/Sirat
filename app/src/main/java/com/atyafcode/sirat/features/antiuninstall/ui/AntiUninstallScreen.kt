package com.atyafcode.sirat.features.antiuninstall.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.atyafcode.sirat.R
import com.atyafcode.sirat.core.utils.appLockRepository
import com.atyafcode.sirat.core.utils.blockUninstallForUser
import com.atyafcode.sirat.core.utils.unblockUninstallForUser
import com.atyafcode.sirat.features.applist.domain.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class AntiUninstallViewModel: ViewModel() {
    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()

    private val _filteredApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val filteredApps: StateFlow<List<AppInfo>> = _filteredApps.asStateFlow()

    private val _protectedApps = MutableStateFlow<Set<String>>(emptySet())
    val protectedApps: StateFlow<Set<String>> = _protectedApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _manualPackageName = MutableStateFlow("")
    val manualPackageName: StateFlow<String> = _manualPackageName.asStateFlow()

    fun loadApps(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true

            val repository = context.appLockRepository()
            _protectedApps.value = repository.getAntiUninstallApps()

            val apps = withContext(Dispatchers.IO) {
                getInstalledApps(context)
            }

            _allApps.value = apps
            _filteredApps.value = apps
            _isLoading.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterApps(query)
    }

    fun updateManualPackageName(packageName: String) {
        _manualPackageName.value = packageName
    }

    private fun filterApps(query: String) {
        _filteredApps.value = if (query.isEmpty()) {
            _allApps.value
        } else {
            _allApps.value.filter { app ->
                app.name.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    fun toggleAppProtection(context: Context, packageName: String) {
        val repository = context.appLockRepository()
        val currentProtected = _protectedApps.value.toMutableSet()

        if (currentProtected.contains(packageName)) {
            repository.removeAntiUninstallApp(packageName)
            currentProtected.remove(packageName)
            unblockUninstallForUser(packageName)
        } else {
            repository.addAntiUninstallApp(packageName)
            currentProtected.add(packageName)
            blockUninstallForUser(packageName)
        }

        _protectedApps.value = currentProtected
    }

    fun addManualPackage(context: Context, packageName: String) {
        if (packageName.isNotBlank()) {
            val repository = context.appLockRepository()
            repository.addAntiUninstallApp(packageName.trim())

            val currentProtected = _protectedApps.value.toMutableSet()
            currentProtected.add(packageName.trim())
            _protectedApps.value = currentProtected

            _manualPackageName.value = ""
        }
    }

    private fun getInstalledApps(context: Context): List<AppInfo> {
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        return installedApps
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || isImportantSystemApp(it) }
            .map { appInfo ->
                AppInfo(
                    name = packageManager.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    icon = packageManager.getApplicationIcon(appInfo)
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun isImportantSystemApp(appInfo: ApplicationInfo): Boolean {
        val importantSystemApps = setOf(
            "com.android.chrome",
            "com.android.vending",
            "com.google.android.gms",
            "com.android.settings",
            "com.android.systemui",
            "com.android.launcher3"
        )
        return appInfo.packageName in importantSystemApps
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntiUninstallScreen(
    navController: NavController,
    viewModel: AntiUninstallViewModel = viewModel()
) {
    val context = LocalContext.current

    val allApps by viewModel.allApps.collectAsState()
    val filteredApps by viewModel.filteredApps.collectAsState()
    val protectedApps by viewModel.protectedApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val manualPackageName by viewModel.manualPackageName.collectAsState()

    val showManualAddDialog = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadApps(context)
    }

    val showMessage =
        remember { mutableStateOf(Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SearchTopBar(
                title = stringResource(R.string.anti_uninstall_title),
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onBack = { navController.navigateUp() },
                onAdd = { showManualAddDialog.value = true }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = stringResource(R.string.anti_uninstall_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                    }
                }
            } else {
                val protectedNotInList = protectedApps.filter { pkg ->
                    allApps.none { it.packageName == pkg }
                }

                if (protectedNotInList.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.manually_added_packages),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(protectedNotInList) { packageName ->
                        ManualPackageItem(
                            packageName = packageName,
                            onToggle = { viewModel.toggleAppProtection(context, packageName) }
                        )
                    }
                    item {
                        Text(
                            text = stringResource(R.string.installed_apps),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                }

                items(filteredApps) { app ->
                    AppProtectionItem(
                        app = app,
                        isProtected = protectedApps.contains(app.packageName),
                        onToggle = { viewModel.toggleAppProtection(context, app.packageName) }
                    )
                }
            }
        }
    }

    if (showManualAddDialog.value) {
        AlertDialog(
            onDismissRequest = { showManualAddDialog.value = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text(stringResource(R.string.add_package_manually)) },
            text = {
                Column(Modifier.fillMaxWidth(0.8f)) {
                    Text(
                        text = stringResource(R.string.enter_package_name_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = manualPackageName,
                        onValueChange = viewModel::updateManualPackageName,
                        label = { Text(stringResource(R.string.package_name_label)) },
                        placeholder = { Text("com.example.app") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addManualPackage(context, manualPackageName)
                        showManualAddDialog.value = false
                    },
                    enabled = manualPackageName.isNotBlank()
                ) { Text(stringResource(R.string.add_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showManualAddDialog.value = false }) { Text(stringResource(R.string.cancel_button)) }
            }
        )
    }

    if (showMessage.value) {
        AlertDialog(
            onDismissRequest = { showMessage.value = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("Shizuku") },
            text = {
                Column(Modifier.fillMaxWidth(0.8f)) {
                    Text(
                        text = stringResource(R.string.shizuku_desc_detailed),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        Shizuku.requestPermission(0)
                        showMessage.value = false
                    }
                ) { Text(stringResource(R.string.confirm_button)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    navController.popBackStack()
                    showMessage.value = false
                }) { Text(stringResource(R.string.cancel_button)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    title: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(TopAppBarDefaults.windowInsets)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_screen_back_cd))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_package_manually))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(stringResource(R.string.search_apps_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp)
                )
            }
        }
    }
}

@Composable
private fun AppProtectionItem(app: AppInfo, isProtected: Boolean, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(checked = isProtected, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun ManualPackageItem(packageName: String, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.manually_added_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = true, onCheckedChange = { onToggle() })
        }
    }
}

