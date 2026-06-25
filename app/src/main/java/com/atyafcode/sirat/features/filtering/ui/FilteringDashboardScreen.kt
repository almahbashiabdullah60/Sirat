package com.atyafcode.sirat.features.filtering.ui

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.atyafcode.sirat.R
import com.atyafcode.sirat.data.filter.BlockedLog
import com.atyafcode.sirat.data.filter.entities.CustomRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilteringDashboardScreen(navController: NavHostController) {
    val viewModel: FilteringViewModel = viewModel()
    val context = LocalContext.current

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.start(context)
        }
    }

    val active by viewModel.active.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val blockCount by viewModel.blockCount.collectAsState()
    val keywords by viewModel.keywords.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshState()
    }

    fun onToggle() {
        if (active) {
            viewModel.stop(context)
        } else {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                vpnPermissionLauncher.launch(intent)
            } else {
                viewModel.start(context)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.filtering_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_screen_back_cd)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { StatusCard(active, blockCount, onToggle = { onToggle() }) }
            item { BlockingOptions(viewModel, active) }
            item { KeywordsSection(viewModel, keywords, active) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = stringResource(R.string.filtering_ui_recent_blocks),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(onClick = { navController.navigate("custom_rules") }) {
                        Icon(Icons.Default.List, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.custom_rules_title))
                    }
                }
            }
            if (logs.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.filtering_ui_no_blocks),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(logs) { log -> BlockedLogItem(log) }
            }
        }
    }
}

@Composable
private fun StatusCard(active: Boolean, blockCount: Int, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (active) stringResource(R.string.filtering_ui_protection_active)
                else stringResource(R.string.filtering_ui_protection_inactive),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (active) {
                Text(
                    text = stringResource(R.string.blocking_stats_blocked, blockCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (active) stringResource(R.string.vpn_filter_notification_text)
                    else stringResource(R.string.filtering_ui_vpn_start_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = active,
                    onCheckedChange = { onToggle() }
                )
            }
        }
    }
}

@Composable
private fun BlockingOptions(viewModel: FilteringViewModel, active: Boolean) {
    val blockPorn by viewModel.blockPorn.collectAsState()
    val blockGambling by viewModel.blockGambling.collectAsState()
    val blockSocial by viewModel.blockSocial.collectAsState()
    val safeSearch by viewModel.safeSearch.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.filtering_ui_blocks_categories),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            ToggleRow(stringResource(R.string.filtering_ui_porn), blockPorn, false) { }
            ToggleRow(stringResource(R.string.filtering_ui_gambling), blockGambling, !active) { viewModel.setBlockGambling(it) }
            ToggleRow(stringResource(R.string.filtering_ui_social), blockSocial, !active) { viewModel.setBlockSocial(it) }
            ToggleRow(stringResource(R.string.filtering_ui_safesearch), safeSearch, !active) { viewModel.setSafeSearch(it) }
        }
    }
}

@Composable
private fun KeywordsSection(viewModel: FilteringViewModel, keywords: Set<String>, active: Boolean) {
    var showDialog by remember { mutableStateOf(false) }
    var newKeyword by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.keywords_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { showDialog = true }, enabled = !active) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.keywords_add))
                }
            }
            Spacer(Modifier.height(8.dp))
            if (keywords.isEmpty()) {
                Text(
                    text = stringResource(R.string.keywords_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                keywords.forEach { kw ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(kw, style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { viewModel.removeKeyword(kw) }, enabled = !active) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.custom_rules_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false; newKeyword = "" },
            title = { Text(stringResource(R.string.keywords_add)) },
            text = {
                OutlinedTextField(
                    value = newKeyword,
                    onValueChange = { newKeyword = it },
                    label = { Text(stringResource(R.string.keywords_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newKeyword.isNotBlank()) {
                        viewModel.addKeyword(newKeyword)
                        newKeyword = ""
                        showDialog = false
                    }
                }) { Text(stringResource(R.string.generic_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false; newKeyword = "" }) { Text(stringResource(R.string.generic_cancel)) }
            }
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BlockedLogItem(log: BlockedLog) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.domain,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = log.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = timeFormat.format(Date(log.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
