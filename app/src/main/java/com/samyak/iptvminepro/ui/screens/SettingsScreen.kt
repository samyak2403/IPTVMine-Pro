package com.samyak.iptvminepro.ui.screens

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

@Composable
fun SettingsScreen(
    onNavigateToProviders: () -> Unit,
    onNavigateToExtensions: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToBugReport: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val providerRepo = remember { ProviderRepository(context) }
    val hasActiveVegaProvider = providerRepo.getProviders().any { it.isActive && it.safeType == ProviderType.VEGA }

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

        SettingsSectionTitle(title = stringResource(id = R.string.section_legal))
        SettingsItem(
            title = stringResource(id = R.string.setting_privacy_policy),
            icon = Icons.Filled.Security,
            onClick = { /* TODO */ }
        )
        SettingsItem(
            title = stringResource(id = R.string.setting_terms_conditions),
            icon = Icons.Filled.Description,
            onClick = { /* TODO */ }
        )
        SettingsItem(
            title = stringResource(id = R.string.setting_disclaimer),
            icon = Icons.Filled.Warning,
            onClick = { /* TODO */ }
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
