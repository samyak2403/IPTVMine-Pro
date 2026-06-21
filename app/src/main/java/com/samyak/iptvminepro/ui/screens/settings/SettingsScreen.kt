package com.samyak.iptvminepro.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.samyak.iptvminepro.provider.ProviderRepository
import com.samyak.iptvminepro.model.ProviderType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.res.stringResource
import com.samyak.iptvminepro.R

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import com.samyak.updater.GithubUpdater
import com.samyak.updater.UpdateDialog
import com.samyak.updater.DownloadState
import com.samyak.updater.UpdateInfo
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

@Composable
fun SettingsScreen(
    onNavigateToProviders: () -> Unit,
    onNavigateToExtensions: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToBugReport: () -> Unit,
    onNavigateToWatchHistory: () -> Unit,
    onNavigateToLegal: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val providerRepo = remember { ProviderRepository(context) }
    val hasActiveVegaProvider = providerRepo.getProviders().any { it.isActive && it.safeType == ProviderType.VEGA }

    // Update check states
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val updater = remember { GithubUpdater("samyak2403", "IPTVMine-Pro") }

    // Loading overlay dialog for checking updates
    if (isCheckingUpdates) {
        Dialog(
            onDismissRequest = { /* Cannot dismiss */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF26A69A),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(
                        text = "Checking for updates...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // Material 3 Update Dialog
    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            downloadState = downloadState,
            onDownloadClick = {
                scope.launch {
                    updater.downloadApk(context, updateInfo!!).collectLatest { state ->
                        downloadState = state
                    }
                }
            },
            onInstallClick = { uri ->
                downloadState = DownloadState.Installing
                updater.installApk(context, uri)
            },
            onRetryClick = {
                downloadState = DownloadState.Idle
                scope.launch {
                    updater.downloadApk(context, updateInfo!!).collectLatest { state ->
                        downloadState = state
                    }
                }
            },
            onDismiss = {
                showUpdateDialog = false
                downloadState = DownloadState.Idle
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        
        SettingsSectionTitle(title = stringResource(id = R.string.section_content))
        SettingsItem(
            title = stringResource(id = R.string.setting_manage_providers),
            icon = Icons.AutoMirrored.Filled.List,
            onClick = onNavigateToProviders
        )
        SettingsItem(
            title = stringResource(id = R.string.setting_downloads),
            icon = Icons.Filled.Download,
            onClick = onNavigateToDownloads
        )
        SettingsItem(
            title = stringResource(id = R.string.setting_watch_history),
            icon = Icons.Filled.History,
            onClick = onNavigateToWatchHistory
        )
        if (hasActiveVegaProvider) {
            SettingsItem(
                title = stringResource(id = R.string.setting_extensions),
                icon = Icons.Filled.Extension,
                onClick = onNavigateToExtensions
            )
        }

        SettingsSectionTitle(title = stringResource(id = R.string.section_about))
        SettingsItem(
            title = stringResource(id = R.string.setting_about_app),
            icon = Icons.Filled.Info,
            onClick = onNavigateToAbout
        )
        SettingsItem(
            title = stringResource(id = R.string.setting_report_bug),
            icon = Icons.Filled.BugReport,
            onClick = onNavigateToBugReport
        )
        SettingsItem(
            title = "Check for Updates",
            icon = Icons.Filled.SystemUpdate,
            onClick = {
                if (!isCheckingUpdates) {
                    isCheckingUpdates = true
                    scope.launch {
                        GithubUpdater.clearCache()
                        val result = updater.checkForUpdate(context)
                        isCheckingUpdates = false
                        result.onSuccess { info ->
                            if (info.isUpdateAvailable) {
                                updateInfo = info
                                showUpdateDialog = true
                            } else {
                                Toast.makeText(
                                    context,
                                    "App is up to date (v${info.currentVersion})",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }.onFailure { error ->
                            Toast.makeText(
                                context,
                                "Failed to check for updates: ${error.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        )

        SettingsSectionTitle(title = stringResource(id = R.string.section_legal))
        SettingsItem(
            title = stringResource(id = R.string.setting_privacy_policy),
            icon = Icons.Filled.Security,
            onClick = { onNavigateToLegal("privacy") }
        )
        SettingsItem(
            title = stringResource(id = R.string.setting_terms_conditions),
            icon = Icons.Filled.Description,
            onClick = { onNavigateToLegal("terms") }
        )
        SettingsItem(
            title = stringResource(id = R.string.setting_disclaimer),
            icon = Icons.Filled.Warning,
            onClick = { onNavigateToLegal("disclaimer") }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(title: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = stringResource(id = R.string.desc_navigate),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
