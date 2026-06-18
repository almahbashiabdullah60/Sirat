package com.atyafcode.sirat.features.planbuilder.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atyafcode.sirat.R
import com.atyafcode.sirat.features.planbuilder.domain.PlanPdfExporter
import com.atyafcode.sirat.core.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanBuilderScreen(
    viewModel: PlanBuilderViewModel = viewModel(),
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    val goalType by viewModel.goalType.collectAsState()
    val selectedBehavior by viewModel.selectedBehavior.collectAsState()
    val availableBehaviors by viewModel.availableBehaviors.collectAsState()

    val pdfExporter = remember { PlanPdfExporter(context) }
    val exportSuccessMessage = stringResource(R.string.plan_export_pdf_success)
    val exportFailedMessage = stringResource(R.string.plan_export_failed)
    
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && uiState is PlanUIState.Success) {
            val success = pdfExporter.exportToPdf(uri, (uiState as PlanUIState.Success).plan)
            Toast.makeText(context, if (success) exportSuccessMessage else exportFailedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Goal and Behavior Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.plan_goal_selection_title), 
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = goalType == "quit",
                        onClick = { 
                            viewModel.goalType.value = "quit"
                            viewModel.updateBehaviors()
                        },
                        label = { Text(stringResource(R.string.plan_goal_quit)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = goalType == "commit",
                        onClick = { 
                            viewModel.goalType.value = "commit"
                            viewModel.updateBehaviors()
                        },
                        label = { Text(stringResource(R.string.plan_goal_commit)) }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = selectedBehavior,
                    onValueChange = { viewModel.selectedBehavior.value = it },
                    label = { Text(stringResource(R.string.plan_target_behavior_label)) },
                    placeholder = { Text(stringResource(R.string.plan_target_behavior_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_settings_title))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.buildPlan() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = uiState !is PlanUIState.Loading
        ) {
            if (uiState is PlanUIState.Loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text((uiState as PlanUIState.Loading).message, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Icon(Icons.Default.Assignment, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.plan_build_button))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (uiState) {
            is PlanUIState.Success -> {
                Column {
                    Button(
                        onClick = { exportLauncher.launch("Sirat_Recovery_Plan.pdf") },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.plan_export_pdf_button))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.plan_custom_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                text = (uiState as PlanUIState.Success).plan,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            is PlanUIState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = (uiState as PlanUIState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                    if ((uiState as PlanUIState.Error).message.contains(stringResource(R.string.plan_error_no_api_key)) || 
                        (uiState as PlanUIState.Error).message.contains(stringResource(R.string.plan_error_local_engine))) {
                        Button(onClick = onNavigateToSettings) {
                            Text(stringResource(R.string.ai_settings_title))
                        }
                    }
                }
            }
            else -> {}
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
