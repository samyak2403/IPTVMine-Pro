package com.samyak.updater

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Theme colors matching the app's teal accent
private val TealPrimary = Color(0xFF26A69A)
private val TealDark = Color(0xFF00897B)
private val TealLight = Color(0xFF4DB6AC)
private val SurfaceLight = Color(0xFFF5F9F8)
private val TextPrimary = Color(0xFF1B2631)
private val TextSecondary = Color(0xFF5D6D7E)

/**
 * Material 3 update dialog that displays release information and
 * manages the download + install flow.
 *
 * @param updateInfo The update information to display
 * @param downloadState Current download state
 * @param onDownloadClick Callback when the user taps "Update Now"
 * @param onInstallClick Callback when the user taps "Install" after download
 * @param onDismiss Callback when the user taps "Later" or closes the dialog
 */
@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    downloadState: DownloadState = DownloadState.Idle,
    onDownloadClick: () -> Unit,
    onInstallClick: (Uri) -> Unit,
    onRetryClick: () -> Unit = onDownloadClick,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            // Only allow dismiss when not actively downloading
            if (downloadState is DownloadState.Idle || downloadState is DownloadState.Failed) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = downloadState is DownloadState.Idle || downloadState is DownloadState.Failed,
            dismissOnClickOutside = downloadState is DownloadState.Idle
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header with gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(TealPrimary, TealDark)
                            ),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SystemUpdateAlt,
                                contentDescription = "Update Available",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Update Available",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = updateInfo.releaseName,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Version info row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceLight)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VersionColumn(label = "Current", version = "v${updateInfo.currentVersion}")
                        Icon(
                            imageVector = Icons.Outlined.NewReleases,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        VersionColumn(label = "Latest", version = "v${updateInfo.latestVersion}")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // APK size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Download Size: ${updateInfo.formattedApkSize}",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFE8E8E8))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Release notes
                    Text(
                        text = "What's New",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceLight)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = updateInfo.releaseNotes,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Download state UI
                    when (downloadState) {
                        is DownloadState.Idle -> {
                            IdleButtons(onDownloadClick = onDownloadClick, onDismiss = onDismiss)
                        }

                        is DownloadState.Downloading -> {
                            DownloadingSection(progress = downloadState.progress)
                        }

                        is DownloadState.Downloaded -> {
                            DownloadedSection(onInstallClick = { onInstallClick(downloadState.uri) })
                        }

                        is DownloadState.Failed -> {
                            FailedSection(
                                message = downloadState.message,
                                onRetryClick = onRetryClick,
                                onDismiss = onDismiss
                            )
                        }

                        is DownloadState.Installing -> {
                            InstallingSection()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionColumn(label: String, version: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = version,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun IdleButtons(onDownloadClick: () -> Unit, onDismiss: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onDownloadClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
        ) {
            Icon(
                imageVector = Icons.Outlined.SystemUpdateAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Update Now", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Later", color = TextSecondary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DownloadingSection(progress: Int) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val animatedProgress by animateFloatAsState(
            targetValue = progress / 100f,
            animationSpec = tween(300),
            label = "downloadProgress"
        )

        Text(
            text = "Downloading update...",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = TealPrimary,
            trackColor = TealLight.copy(alpha = 0.2f),
            strokeCap = StrokeCap.Round
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$progress%",
            color = TealPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DownloadedSection(onInstallClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Download complete!",
            color = TealPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onInstallClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TealDark)
        ) {
            Text("Install Now", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FailedSection(message: String, onRetryClick: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = TextSecondary)
            }
            Button(
                onClick = onRetryClick,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun InstallingSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = TealPrimary,
            trackColor = TealLight.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Launching installer...",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Convenience state holder for drop-in usage
// ─────────────────────────────────────────────────────────────────────────────

/**
 * State holder for the update check + dialog + download flow.
 *
 * @param owner GitHub repository owner
 * @param repo GitHub repository name
 */
class UpdaterState(
    val owner: String,
    val repo: String
) {
    internal val updater = GithubUpdater(owner, repo)

    var updateInfo by mutableStateOf<UpdateInfo?>(null)
        internal set

    var showDialog by mutableStateOf(false)
        internal set

    var downloadState by mutableStateOf<DownloadState>(DownloadState.Idle)
        internal set

    var isChecking by mutableStateOf(false)
        internal set

    var error by mutableStateOf<String?>(null)
        internal set

    fun dismissDialog() {
        showDialog = false
        // Reset download state only if idle or failed
        if (downloadState is DownloadState.Idle || downloadState is DownloadState.Failed) {
            downloadState = DownloadState.Idle
        }
    }
}

/**
 * Creates and remembers an [UpdaterState] that automatically checks for
 * updates when first composed.
 *
 * Usage in your composable:
 * ```kotlin
 * val updaterState = rememberUpdaterState("samyak2403", "IPTVMine-Pro")
 *
 * if (updaterState.showDialog && updaterState.updateInfo != null) {
 *     AppUpdateDialog(updaterState)
 * }
 * ```
 *
 * @param owner GitHub repository owner
 * @param repo GitHub repository name
 */
@Composable
fun rememberUpdaterState(owner: String, repo: String): UpdaterState {
    val context = LocalContext.current
    val state = remember { UpdaterState(owner, repo) }

    LaunchedEffect(context) {
        state.isChecking = true
        state.error = null
        val result = state.updater.checkForUpdate(context)
        result.onSuccess { info ->
            state.updateInfo = info
            state.showDialog = info.isUpdateAvailable
        }.onFailure { throwable ->
            state.error = throwable.message
        }
        state.isChecking = false
    }

    return state
}

/**
 * Convenience composable that combines [UpdateDialog] with [UpdaterState]
 * for a complete, self-contained update experience.
 *
 * Simply call this from your root composable and it handles everything.
 *
 * @param state The [UpdaterState] from [rememberUpdaterState]
 */
@Composable
fun AppUpdateDialog(state: UpdaterState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateInfo = state.updateInfo ?: return

    AnimatedVisibility(
        visible = state.showDialog,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(200))
    ) {
        UpdateDialog(
            updateInfo = updateInfo,
            downloadState = state.downloadState,
            onDownloadClick = {
                scope.launch {
                    state.updater.downloadApk(context, updateInfo).collectLatest { downloadState ->
                        state.downloadState = downloadState
                    }
                }
            },
            onInstallClick = { uri ->
                state.downloadState = DownloadState.Installing
                state.updater.installApk(context, uri)
            },
            onRetryClick = {
                state.downloadState = DownloadState.Idle
                scope.launch {
                    state.updater.downloadApk(context, updateInfo).collectLatest { downloadState ->
                        state.downloadState = downloadState
                    }
                }
            },
            onDismiss = { state.dismissDialog() }
        )
    }
}
