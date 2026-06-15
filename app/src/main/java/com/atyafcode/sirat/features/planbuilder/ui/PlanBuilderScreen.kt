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
    val planLanguage by viewModel.planLanguage.collectAsState()
    val religion by viewModel.religion.collectAsState()
    val aiProvider by viewModel.aiProvider.collectAsState()
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
                Text("إعدادات بناء الخطة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                // Language
                Text("لغة الخطة", style = MaterialTheme.typography.labelLarge)
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
                    label = { Text("الديانة (اختياري)") },
                    placeholder = { Text("مثلاً: الإسلام") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // AI Provider
                Text("مصدر الذكاء الاصطناعي", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = aiProvider == PlanRepository.AI_PROVIDER_LOCAL,
                        onClick = { viewModel.aiProvider.value = PlanRepository.AI_PROVIDER_LOCAL },
                        label = { Text("محلي (خصوصية)") },
                        leadingIcon = { Icon(Icons.Default.Psychology, null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = aiProvider == PlanRepository.AI_PROVIDER_CLOUD,
                        onClick = { viewModel.aiProvider.value = PlanRepository.AI_PROVIDER_CLOUD },
                        label = { Text("سحابي (سرعة)") },
                        leadingIcon = { Icon(Icons.Default.Cloud, null, modifier = Modifier.size(18.dp)) }
                    )
                }

                if (aiProvider == PlanRepository.AI_PROVIDER_CLOUD) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { viewModel.apiKey.value = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
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
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.Default.Assignment, null)
                Spacer(Modifier.width(8.dp))
                Text("بناء خطة التعافي")
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
                            Text("خطة التعافي المخصصة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
