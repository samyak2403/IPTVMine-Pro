package com.samyak.iptvminepro.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.samyak.iptvminepro.R
import com.samyak.iptvminepro.download.DownloadManager
import com.samyak.iptvminepro.download.DownloadStatus
import com.samyak.iptvminepro.download.DownloadTask
import com.samyak.player.PlayerActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DownloadsScreen(navController: NavController) {
    val context = LocalContext.current
    val tasks by DownloadManager.downloadTasks.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        stringResource(id = R.string.tab_downloading),
        stringResource(id = R.string.tab_downloaded)
    )

    val downloadingTasks = remember(tasks) {
        tasks.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING || it.status == DownloadStatus.FAILED }
            .sortedByDescending { it.addedAt }
    }

    val downloadedTasks = remember(tasks) {
        tasks.filter { it.status == DownloadStatus.COMPLETED }
            .sortedByDescending { it.addedAt }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F7)) // Premium light grey background
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = Color(0xFF26A69A),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = with(TabRowDefaults) { Modifier.tabIndicatorOffset(tabPositions[selectedTab]) },
                    color = Color(0xFF26A69A)
                )
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                val count = if (index == 0) downloadingTasks.size else downloadedTasks.size
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                color = if (selectedTab == index) Color(0xFF26A69A) else Color.Gray,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                            if (count > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(
                                    containerColor = if (selectedTab == index) Color(0xFF26A69A) else Color.Gray.copy(alpha = 0.15f),
                                    contentColor = if (selectedTab == index) Color.White else Color.Gray
                                ) {
                                    Text("$count", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (selectedTab == 0) {
                if (downloadingTasks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(id = R.string.no_downloads_active),
                            color = Color.Gray,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(downloadingTasks, key = { it.id }) { task ->
                            DownloadingItem(
                                task = task,
                                onCancel = { DownloadManager.cancelTask(task.id) },
                                onRetry = { DownloadManager.retryTask(task.id) }
                            )
                        }
                    }
                }
            } else {
                if (downloadedTasks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(id = R.string.no_downloads_completed),
                            color = Color.Gray,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(downloadedTasks, key = { it.id }) { task ->
                            DownloadedItem(
                                task = task,
                                onPlay = {
                                    val pathOrUri = task.savePath
                                    if (pathOrUri.startsWith("content://")) {
                                        val uri = android.net.Uri.parse(pathOrUri)
                                        var exists = false
                                        try {
                                            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                                                exists = true
                                            }
                                        } catch (e: Exception) {}
                                        if (exists) {
                                            PlayerActivity.start(context, task.title, pathOrUri)
                                        } else {
                                            Toast.makeText(context, "File does not exist on storage!", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        val file = File(pathOrUri)
                                        if (file.exists()) {
                                            val localUri = android.net.Uri.fromFile(file).toString()
                                            PlayerActivity.start(context, task.title, localUri)
                                        } else {
                                            Toast.makeText(context, "File does not exist on storage!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onDelete = { DownloadManager.deleteTask(task.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadingItem(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        color = Color(0xFF1C1C1E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val statusText = when (task.status) {
                        DownloadStatus.PENDING -> "Queued / Pending"
                        DownloadStatus.FAILED -> "Failed"
                        else -> "Downloading..."
                    }
                    val statusColor = when (task.status) {
                        DownloadStatus.PENDING -> Color(0xFFFFB300)
                        DownloadStatus.FAILED -> Color.Red
                        else -> Color(0xFF26A69A)
                    }
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.status == DownloadStatus.FAILED) {
                        IconButton(onClick = onRetry) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Retry Download",
                                tint = Color(0xFF26A69A)
                            )
                        }
                    }
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Filled.Cancel,
                            contentDescription = "Cancel Download",
                            tint = Color.Gray
                        )
                    }
                }
            }

            if (task.status == DownloadStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF26A69A),
                    trackColor = Color.Gray.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val pct = (task.progress * 100).toInt()
                    val sizeText = if (task.totalBytes > 0) {
                        "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}"
                    } else {
                        formatBytes(task.downloadedBytes)
                    }
                    Text(
                        text = sizeText,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (task.speed.isNotEmpty()) {
                            Text(
                                text = task.speed,
                                color = Color(0xFF26A69A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "$pct%",
                            color = Color(0xFF1C1C1E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadedItem(
    task: DownloadTask,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    color = Color(0xFF1C1C1E),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val date = Date(task.addedAt)
                val format = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val dateStr = format.format(date)
                val sizeText = if (task.totalBytes > 0) formatBytes(task.totalBytes) else "Completed"
                Text(
                    text = "$sizeText • $dateStr",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF26A69A).copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onPlay() }
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color(0xFF26A69A),
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Red.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { showConfirmDelete = true }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Delete Download?", color = Color(0xFF1C1C1E)) },
            text = { Text("Are you sure you want to delete this downloaded video from your storage?", color = Color(0xFF3A3A3C)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDelete = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancel", color = Color(0xFF26A69A))
                }
            },
            containerColor = Color.White
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups >= units.size) return "$bytes B"
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
