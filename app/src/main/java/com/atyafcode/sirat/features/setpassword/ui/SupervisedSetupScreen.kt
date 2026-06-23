package com.atyafcode.sirat.features.setpassword.ui

import android.Manifest
import android.content.ClipData
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.atyafcode.sirat.AppLockApplication
import com.atyafcode.sirat.R
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
    
    var secret by rememberSaveable { mutableStateOf(SecurityGenerator.generateRandomPassword(16)) }
    val qrBitmap = remember(secret) { SupervisedLockManager.generateQRCode(secret) }
    
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
            TopAppBar(
                title = { Text(stringResource(R.string.supervised_setup_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (step == 1) {
                Text(
                    text = stringResource(R.string.supervised_setup_step1), 
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                qrBitmap?.let { bitmap ->
                    Surface(
                        modifier = Modifier.size(250.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = androidx.compose.ui.graphics.Color.White,
                        shadowElevation = 4.dp
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { shareQRCode(context, qrBitmap, secret) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.supervised_setup_share_button))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { secret = SecurityGenerator.generateRandomPassword(16) }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.supervised_setup_regenerate))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.supervised_setup_qr_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                TextButton(
                    onClick = { navController.navigate(Screen.SetPasswordAlphanumeric.route) }
                ) {
                    Text(stringResource(R.string.use_password_button))
                }
                
                OutlinedButton(
                    onClick = { navController.navigate(Screen.SetPassword.route) },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.use_pin_button))
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = { step = 2 },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.next_button))
                }
            } else {
                Text(
                    text = stringResource(R.string.supervised_setup_step2), 
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = stringResource(R.string.supervised_setup_test_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(60.dp))
                
                if (hasCameraPermission) {
                    com.atyafcode.sirat.features.lockscreen.ui.CameraPreview(
                        onBarcodeDetected = { barcode ->
                            if (barcode == secret) {
                                appLockRepository.setLockType(PreferencesRepository.LOCK_TYPE_SUPERVISED)
                                appLockRepository.setSupervisedSecret(secret)
                                appLockRepository.setSupervisedMethod(PreferencesRepository.SUPERVISED_METHOD_QR)
                                
                                Toast.makeText(context, context.getString(R.string.supervised_setup_verify_success), Toast.LENGTH_SHORT).show()
                                navController.navigate(Screen.Main.route) {
                                    popUpTo("${Screen.SupervisedSetup.route}/{method}") { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                } else {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.supervised_lock_grant_perm))
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
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.supervised_setup_qr_desc) + " Key: $secret")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "QR Code", contentUri)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.supervised_setup_share_button)))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
