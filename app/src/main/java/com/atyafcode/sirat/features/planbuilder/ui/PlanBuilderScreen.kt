package com.atyafcode.sirat.features.planbuilder.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Psychology
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
                    onValueChange = { viewModel.religion.value = it },
                    label = { Text(stringResource(R.string.plan_religion_label)) },
                    placeholder = { Text(stringResource(R.string.plan_religion_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // AI Provider (Commented out cloud for now)
                /*
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

                if (aiProvider == PlanRepository.AI_PROVIDER_CLOUD) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("اختر الشركة المزودة", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = cloudProvider == PlanRepository.CLOUD_PROVIDER_GEMINI,
                            onClick = { viewModel.cloudProvider.value = PlanRepository.CLOUD_PROVIDER_GEMINI },
                            label = { Text("Google Gemini") },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        FilterChip(
                            selected = cloudProvider == PlanRepository.CLOUD_PROVIDER_OPENAI,
                            onClick = { viewModel.cloudProvider.value = PlanRepository.CLOUD_PROVIDER_OPENAI },
                            label = { Text("OpenAI (GPT)") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { viewModel.apiKey.value = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                */
                Text("المحرك النشط: ذكاء اصطناعي محلي (خصوصية تامة)", 
                    style = MaterialTheme.typography.bodyMedium, 
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!isModelDownloaded) {
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
                    Icon(
                        if (downloadStatus.isRunning) Icons.Default.Download else Icons.Default.Download, 
                        null, 
                        tint = if (downloadStatus.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    if (downloadStatus.isRunning) {
                        Text("جاري تنزيل محرك الذكاء الاصطناعي...", fontWeight = FontWeight.Bold)
                        Text("${downloadStatus.progress}%", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        
                        Spacer(Modifier.height(4.dp))
                        
                        // Detailed stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${downloadStatus.downloadedMB} MB / ${downloadStatus.totalMB} MB",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "المتبقي: ${downloadStatus.remainingMB} MB",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = downloadStatus.progress / 100f,
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("حجم المحرك حوالي 1.2 جيجابايت. يمكنك إغلاق التطبيق وسيكتمل التنزيل في الخلفية.", 
                            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        
                        Spacer(Modifier.height(16.dp))
                        
                        OutlinedButton(
                            onClick = { viewModel.cancelDownload() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("إيقاف التنزيل (إلغاء)")
                        }
                    } else if (downloadStatus.isFailed) {
                        Text("فشل التنزيل", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(downloadStatus.reason ?: "خطأ غير معروف", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.downloadModel() }) {
                            Text("إعادة المحاولة")
                        }
                    } else {
                        Text("محرك الذكاء الاصطناعي غير موجود", fontWeight = FontWeight.Bold)
                        Text("يجب تنزيل قاعدة البيانات (1.2 جيجابايت) لتعمل الميزة بخصوصية تامة.", 
                            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.downloadModel() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("تنزيل المحرك الآن")
                        }
                    }
                    
                    if (!downloadStatus.isRunning) {
                        TextButton(onClick = { viewModel.checkModelStatus() }) {
                            Text("تم التنزيل؟ اضغط للتحديث")
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.plan_custom_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { exportLauncher.launch("Sirat_Recovery_Plan.pdf") }) {
                                Icon(Icons.Default.PictureAsPdf, "Export PDF", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = (uiState as PlanUIState.Success).plan,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            is PlanUIState.Error -> {
                Text(
                    text = (uiState as PlanUIState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {
                // Idle or handled by loading button
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
