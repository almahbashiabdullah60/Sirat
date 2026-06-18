package com.atyafcode.sirat.features.setpassword.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.atyafcode.sirat.AppLockApplication
import com.atyafcode.sirat.core.navigation.Screen
import com.atyafcode.sirat.core.utils.SecurityGenerator
import com.atyafcode.sirat.core.utils.SupervisedLockManager
import com.atyafcode.sirat.data.repository.PreferencesRepository
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisedSetupScreen(navController: NavController, method: String) {
    val context = LocalContext.current
    val appLockRepository = (context.applicationContext as AppLockApplication).appLockRepository
    
    val secret = remember { SecurityGenerator.generateRandomPassword(16) }
    val qrBitmap = remember { SupervisedLockManager.generateQRCode(secret) }
    
    var step by remember { mutableStateOf(1) }
    var hasCameraPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

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
            if (method == "qr") {
                if (step == 1) {
                    Text("الخطوة 1: مشاركة الرمز مع المشرف", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    qrBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(250.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(onClick = { shareQRCode(context, qrBitmap, secret) }) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مشاركة الكود")
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Button(
                        onClick = { step = 2 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("التالي")
                    }
                } else {
                    Text("الخطوة 2: تجربة المسح", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text("الآن، حاول مسح الكود من الجهاز الآخر للتأكد من نجاح العملية.")
                    
                    Spacer(modifier = Modifier.height(60.dp))
                    
                    if (hasCameraPermission) {
                        com.atyafcode.sirat.features.lockscreen.ui.CameraPreview(
                            onBarcodeDetected = { barcode ->
                                if (barcode == secret) {
                                    appLockRepository.setLockType(PreferencesRepository.LOCK_TYPE_SUPERVISED)
                                    appLockRepository.setSupervisedSecret(secret)
                                    appLockRepository.setSupervisedMethod(PreferencesRepository.SUPERVISED_METHOD_QR)
                                    
                                    Toast.makeText(context, "تم التحقق بنجاح!", Toast.LENGTH_SHORT).show()
                                    navController.navigate(Screen.Main.route) {
                                        popUpTo(Screen.AppIntro.route) { inclusive = true }
                                    }
                                }
                            }
                        )
                    } else {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("منح صلاحية الكاميرا للمسح")
                        }
                    }
                }
            } else {
                // Face setup
                Text("إعداد التعرف على الوجه", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(24.dp))
                
                if (step == 1) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "سيتم استخدام الكاميرا للتعرف على وجه المشرف وتخزينه كبصمة للفتح.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Button(
                        onClick = { step = 2 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ابدأ التقاط الوجه")
                    }
                } else {
                    if (hasCameraPermission) {
                        com.atyafcode.sirat.features.lockscreen.ui.FaceCameraPreview(
                            onFaceDetected = {
                                appLockRepository.setLockType(PreferencesRepository.LOCK_TYPE_SUPERVISED)
                                appLockRepository.setSupervisedSecret(secret)
                                appLockRepository.setSupervisedMethod(PreferencesRepository.SUPERVISED_METHOD_FACE)
                                
                                Toast.makeText(context, "تم حفظ بصمة الوجه بنجاح!", Toast.LENGTH_SHORT).show()
                                navController.navigate(Screen.Main.route) {
                                    popUpTo(Screen.AppIntro.route) { inclusive = true }
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("يرجى وضع وجه المشرف أمام الكاميرا")
                    } else {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("منح صلاحية الكاميرا")
                        }
                    }
                }
            }
        }
    }
}

private fun shareQRCode(context: android.content.Context, bitmap: Bitmap?, secret: String) {
    if (bitmap == null) return
    
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val stream = FileOutputStream("$cachePath/qr_code.png")
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        
        val imagePath = File(context.cacheDir, "images")
        val newFile = File(imagePath, "qr_code.png")
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", newFile)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, "هذا هو رمز القفل الخاص بي في تطبيق صراط. الرمز: $secret")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة الرمز"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
