package com.atyafcode.sirat.features.filtering.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.atyafcode.sirat.R
import com.atyafcode.sirat.core.navigation.Screen
import com.atyafcode.sirat.data.filter.BlockedLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilteringDashboardScreen(navController: NavHostController) {
    val viewModel: FilteringViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleVpn()
        }
    }

    val logs by viewModel.logs.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshVpnState()
        viewModel.loadLogs()
    }

    fun onVpnToggle() {
        if (viewModel.vpnRunning.value) {
            viewModel.toggleVpn()
        } else {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                vpnPermissionLauncher.launch(intent)
            } else {
                viewModel.toggleVpn()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.filtering_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("<")
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
            item { VpnStatusCard(viewModel, onVpnToggle = { onVpnToggle() }) }
            item { BlockingOptions(viewModel) }
            item {
                Text(
                    text = stringResource(R.string.filtering_ui_recent_blocks),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
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
private fun VpnStatusCard(viewModel: FilteringViewModel, onVpnToggle: () -> Unit = {}) {
    val vpnRunning by viewModel.vpnRunning.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (vpnRunning) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                        text = if (vpnRunning) stringResource(R.string.filtering_ui_protection_active) else stringResource(R.string.filtering_ui_protection_inactive),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (vpnRunning) stringResource(R.string.vpn_filter_notification_text) else stringResource(R.string.filtering_ui_vpn_start_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = vpnRunning,
                    onCheckedChange = { onVpnToggle() }
                )
            }
        }
    }
}

@Composable
private fun BlockingOptions(viewModel: FilteringViewModel) {
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
            ToggleRow(stringResource(R.string.filtering_ui_porn), blockPorn) { viewModel.setBlockPorn(it) }
            ToggleRow(stringResource(R.string.filtering_ui_gambling), blockGambling) { viewModel.setBlockGambling(it) }
            ToggleRow(stringResource(R.string.filtering_ui_social), blockSocial) { viewModel.setBlockSocial(it) }
            ToggleRow(stringResource(R.string.filtering_ui_safesearch), safeSearch) { viewModel.setSafeSearch(it) }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
