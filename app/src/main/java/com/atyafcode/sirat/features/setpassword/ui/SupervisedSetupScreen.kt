package com.atyafcode.sirat.features.setpassword.ui

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.atyafcode.sirat.AppLockApplication
import com.atyafcode.sirat.core.navigation.Screen
import com.atyafcode.sirat.core.utils.SecurityGenerator
import com.atyafcode.sirat.core.utils.SupervisedLockManager
import com.atyafcode.sirat.data.repository.PreferencesRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisedSetupScreen(navController: NavController, method: String) {
    val context = LocalContext.current
    val appLockRepository = (context.applicationContext as AppLockApplication).appLockRepository
    
    val secret = remember { SecurityGenerator.generateRandomPassword(16) }
    val qrBitmap = remember { SupervisedLockManager.generateQRCode(secret) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("إعداد قفل المشرف") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "هذا هو رمز الأمان الخاص بك",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (method == "qr") {
                qrBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(250.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "قم بمشاركة هذا الكود مع المشرف الخاص بك. ستحتاج لمسحه لفتح التطبيقات المقفلة.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        appLockRepository.setLockType(PreferencesRepository.LOCK_TYPE_SUPERVISED)
                        appLockRepository.setSupervisedSecret(secret)
                        appLockRepository.setSupervisedMethod(PreferencesRepository.SUPERVISED_METHOD_QR)
                        
                        Toast.makeText(context, "تم حفظ الإعدادات بنجاح", Toast.LENGTH_SHORT).show()
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.AppIntro.route) { inclusive = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إتمام الإعداد")
                }
            } else {
                // Face setup placeholder
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Face,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "سيتم استخدام الكاميرا للتعرف على وجه المشرف.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        // TODO: Implement actual face capture
                        appLockRepository.setLockType(PreferencesRepository.LOCK_TYPE_SUPERVISED)
                        appLockRepository.setSupervisedSecret(secret)
                        appLockRepository.setSupervisedMethod(PreferencesRepository.SUPERVISED_METHOD_FACE)
                        
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.AppIntro.route) { inclusive = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ابدأ التقاط وجه المشرف")
                }
            }
        }
    }
}
