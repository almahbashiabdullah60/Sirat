package com.atyafcode.sirat.features.setpassword.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.atyafcode.sirat.core.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisedMethodChoiceScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("اختر طريقة الحماية") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "كيف تريد أن يساعدك المشرف؟",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            ChoiceCard(
                title = "قفل شخصي (عادي)",
                description = "أنت من تحدد الرمز وتفتحه بنفسك.",
                icon = Icons.Default.Person,
                onClick = { navController.navigate(Screen.SetPassword.route) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            ChoiceCard(
                title = "قفل المشرف (QR Code)",
                description = "يتم توليد رمز لا تعرفه، ويفتحه المشرف عبر مسح الكود.",
                icon = Icons.Default.QrCode,
                onClick = { /* TODO: Navigate to Supervised Setup with QR */ }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            ChoiceCard(
                title = "قفل المشرف (الوجه)",
                description = "يفتح التطبيق فقط عند التعرف على وجه المشرف.",
                icon = Icons.Default.Face,
                onClick = { /* TODO: Navigate to Supervised Setup with Face */ }
            )
        }
    }
}

@Composable
fun ChoiceCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
