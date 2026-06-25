package com.atyafcode.sirat.features.setpassword.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.atyafcode.sirat.AppLockApplication
import com.atyafcode.sirat.R
import com.atyafcode.sirat.core.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisedMethodChoiceScreen(navController: NavController) {
    val context = LocalContext.current
    val appLockRepository = (context.applicationContext as AppLockApplication).appLockRepository
    val hasPassword = appLockRepository.getPassword() != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.supervised_choice_title)) },
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
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.supervised_choice_question),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            ChoiceCard(
                title = stringResource(R.string.lock_personal_title),
                description = stringResource(R.string.lock_personal_desc),
                icon = Icons.Default.Person,
                onClick = {
                    val route = if (hasPassword) Screen.ChangePassword.route else Screen.SetPassword.route
                    navController.navigate(route) {
                        popUpTo(Screen.SupervisedMethodChoice.route) { inclusive = true }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            ChoiceCard(
                title = stringResource(R.string.lock_supervised_qr_title),
                description = stringResource(R.string.lock_supervised_qr_desc),
                icon = Icons.Default.QrCode,
                onClick = {
                    navController.navigate(Screen.SupervisedSetup.route + "/qr") {
                        popUpTo(Screen.SupervisedMethodChoice.route) { inclusive = true }
                    }
                }
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, 
                contentDescription = null, 
                modifier = Modifier.size(48.dp), 
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    title, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    description, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
