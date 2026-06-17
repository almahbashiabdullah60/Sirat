package com.atyafcode.sirat.features.planbuilder.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atyafcode.sirat.R
import com.atyafcode.sirat.data.repository.PlanRepository
import com.atyafcode.sirat.features.planbuilder.domain.PlanPdfExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanBuilderScreen(
    viewModel: PlanBuilderViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isModelDownloaded by viewModel.isModelDownloaded.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val planLanguage by viewModel.planLanguage.collectAsState()
    val religion by viewModel.religion.collectAsState()
    val aiProvider by viewModel.aiProvider.collectAsState()
    val cloudProvider by viewModel.cloudProvider.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val openRouterModels by viewModel.openRouterModels.collectAsState()
    
    val goalType by viewModel.goalType.collectAsState()
    val selectedBehavior by viewModel.selectedBehavior.collectAsState()
    val availableBehaviors by viewModel.availableBehaviors.collectAsState()

    val pdfExporter = remember { PlanPdfExporter(context) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && uiState is PlanUIState.Success) {
            val success = pdfExporter.exportToPdf(uri, (uiState as PlanUIState.Success).plan)
            Toast.makeText(context, if (success) "تم تصدير الخطة بنجاح" else "فشل التصدير", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                        label = { Text("العربية") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = planLanguage == "en",
                        onClick = { viewModel.planLanguage.value = "en" },
                        label = { Text("English") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Religion
                OutlinedTextField(
                    value = religion,
                    onValueChange = { 
                        viewModel.religion.value = it
                        viewModel.updateBehaviors()
                    },
                    label = { Text(stringResource(R.string.plan_religion_label)) },
                    placeholder = { Text(stringResource(R.string.plan_religion_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                // Goal and Behavior Selection
                Text("حدد هدفك المباشر", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = goalType == "quit",
                        onClick = { 
                            viewModel.goalType.value = "quit"
                            viewModel.updateBehaviors()
                        },
                        label = { Text("تخلص من سلوك") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = goalType == "commit",
                        onClick = { 
                            viewModel.goalType.value = "commit"
                            viewModel.updateBehaviors()
                        },
                        label = { Text("التزام بسلوك") }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                var behaviorExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = behaviorExpanded,
                    onExpandedChange = { behaviorExpanded = !behaviorExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedBehavior,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("اختر السلوك المستهدف") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = behaviorExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = behaviorExpanded,
                        onDismissRequest = { behaviorExpanded = false }
                    ) {
                        availableBehaviors.forEach { behavior ->
                            DropdownMenuItem(
                                text = { Text(behavior) },
                                onClick = {
                                    viewModel.selectedBehavior.value = behavior
                                    behaviorExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                        Text("إعدادات المحرك السحابي", style = MaterialTheme.typography.labelLarge)
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            FilterChip(
                                selected = cloudProvider == PlanRepository.CLOUD_PROVIDER_OPENROUTER,
                                onClick = { 
                                    viewModel.cloudProvider.value = PlanRepository.CLOUD_PROVIDER_OPENROUTER
                                    viewModel.refreshModels()
                                },
                                label = { Text("OpenRouter (مجاني/قوي)") },
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
                                    label = { Text("اختر الموديل") },
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
                                            text = { Text("جاري جلب الموديلات.. أو أدخل الـ Key") },
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
                        Text("جاري تنزيل محرك الذكاء الاصطناعي...", fontWeight = FontWeight.Bold)
                        Text("${downloadStatus.progress}%", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = downloadStatus.progress / 100f,
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.cancelDownload() }, modifier = Modifier.fillMaxWidth()) {
                            Text("إلغاء التنزيل")
                        }
                    } else {
                        Text("المحرك المحلي غير موجود", fontWeight = FontWeight.Bold)
                        Text("يتطلب 1.2 جيجابايت للعمل بخصوصية تامة.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.downloadModel() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text("تنزيل الآن")
                        }
                        TextButton(onClick = { viewModel.aiProvider.value = PlanRepository.AI_PROVIDER_CLOUD }) {
                            Text("أو استخدم المحرك السحابي (جودة أعلى)")
                        }
                    }
                }
            }
        } else {
            Button(
                onClick = { viewModel.buildPlan() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = uiState !is PlanUIState.Loading
            ) {
                if (uiState is PlanUIState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Assignment, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.plan_build_button))
                }
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
                        Text("تنزيل الخطة كملف PDF")
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
                Text(
                    text = (uiState as PlanUIState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
            else -> {}
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
