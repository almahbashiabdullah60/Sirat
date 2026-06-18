package com.atyafcode.sirat.features.aisettings.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.atyafcode.sirat.R
import com.atyafcode.sirat.data.repository.PlanRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsScreen(
    navController: NavController,
    viewModel: AISettingsViewModel = viewModel()
) {
    val aiProvider by viewModel.aiProvider.collectAsState()
    val cloudProvider by viewModel.cloudProvider.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val planLanguage by viewModel.planLanguage.collectAsState()
    val religion by viewModel.religion.collectAsState()
    val openRouterModels by viewModel.openRouterModels.collectAsState()
    val isModelDownloaded by viewModel.isModelDownloaded.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { 
                        viewModel.saveSettings()
                        navController.popBackStack() 
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.plan_settings_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Language
                    Text(stringResource(R.string.plan_language_label), style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = planLanguage == "ar",
                            onClick = { viewModel.planLanguage.value = "ar" },
                            label = { Text(stringResource(R.string.language_arabic)) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        FilterChip(
                            selected = planLanguage == "en",
                            onClick = { viewModel.planLanguage.value = "en" },
                            label = { Text(stringResource(R.string.language_english)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Religion
                    OutlinedTextField(
                        value = religion,
                        onValueChange = { viewModel.religion.value = it },
                        label = { Text(stringResource(R.string.plan_religion_label)) },
                        placeholder = { Text(stringResource(R.string.plan_religion_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(24.dp))

                    // AI Provider Selection
                    Text(stringResource(R.string.plan_ai_source_label), style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = aiProvider == PlanRepository.AI_PROVIDER_LOCAL,
                            onClick = { viewModel.aiProvider.value = PlanRepository.AI_PROVIDER_LOCAL },
                            label = { Text(stringResource(R.string.plan_source_local)) },
                            leadingIcon = { Icon(Icons.Default.Psychology, null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        FilterChip(
                            selected = aiProvider == PlanRepository.AI_PROVIDER_CLOUD,
                            onClick = { viewModel.aiProvider.value = PlanRepository.AI_PROVIDER_CLOUD },
                            label = { Text(stringResource(R.string.plan_source_cloud)) },
                            leadingIcon = { Icon(Icons.Default.Cloud, null, modifier = Modifier.size(18.dp)) }
                        )
                    }

                    AnimatedVisibility(visible = aiProvider == PlanRepository.AI_PROVIDER_CLOUD) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.plan_ai_settings_cloud), style = MaterialTheme.typography.labelLarge)
                            
                            Row(modifier = Modifier.fillMaxWidth()) {
                                FilterChip(
                                    selected = cloudProvider == PlanRepository.CLOUD_PROVIDER_OPENROUTER,
                                    onClick = { 
                                        viewModel.cloudProvider.value = PlanRepository.CLOUD_PROVIDER_OPENROUTER
                                        viewModel.refreshModels()
                                    },
                                    label = { Text("OpenRouter (Open Source/Powerful)") },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { 
                                    viewModel.apiKey.value = it
                                    if (it.length > 10) viewModel.refreshModels()
                                },
                                label = { Text("OpenRouter API Key") },
                                placeholder = { Text("sk-or-v1-...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            if (cloudProvider == PlanRepository.CLOUD_PROVIDER_OPENROUTER) {
                                Spacer(modifier = Modifier.height(8.dp))
                                var modelExpanded by remember { mutableStateOf(false) }
                                
                                ExposedDropdownMenuBox(
                                    expanded = modelExpanded,
                                    onExpandedChange = { 
                                        modelExpanded = !modelExpanded 
                                        if (modelExpanded) viewModel.refreshModels()
                                    }
                                ) {
                                    OutlinedTextField(
                                        value = selectedModel,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.plan_ai_model_label)) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    
                                    ExposedDropdownMenu(
                                        expanded = modelExpanded,
                                        onDismissRequest = { modelExpanded = false }
                                    ) {
                                        if (openRouterModels.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.plan_ai_model_loading)) },
                                                onClick = { modelExpanded = false }
                                            )
                                        }
                                        openRouterModels.forEach { model ->
                                            DropdownMenuItem(
                                                text = { 
                                                    Column {
                                                        Text(model.name)
                                                        Text(model.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.selectedModel.value = model.id
                                                    modelExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (aiProvider == PlanRepository.AI_PROVIDER_LOCAL && !isModelDownloaded) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (downloadStatus.isRunning) 
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        else 
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Download, null, tint = if (downloadStatus.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        
                        if (downloadStatus.isRunning) {
                            Text(stringResource(R.string.plan_download_title), fontWeight = FontWeight.Bold)
                            Text("${downloadStatus.progress}%", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = downloadStatus.progress / 100f,
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = { viewModel.cancelDownload() }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.plan_download_cancel))
                            }
                        } else {
                            Text(stringResource(R.string.plan_download_local_not_found), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.plan_download_local_desc), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.downloadModel() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                Text(stringResource(R.string.plan_download_now))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { 
                    viewModel.saveSettings()
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.save_settings))
            }
        }
    }
}
