package com.samyak.iptvminepro.ui.screens.provider

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.samyak.iptvminepro.R
import com.samyak.iptvminepro.model.Provider
import com.samyak.iptvminepro.model.ProviderType
import com.samyak.iptvminepro.provider.ProviderDetectionEngine
import com.samyak.iptvminepro.provider.ProviderRepository

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProviderScreen(
    onProviderAdded: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    editUrl: String? = null
) {
    val context = LocalContext.current
    val repository = remember { ProviderRepository(context) }
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }
    var providerType by remember { mutableStateOf(ProviderType.IPTV) }
    var isDetecting by remember { mutableStateOf(false) }

    LaunchedEffect(editUrl) {
        if (editUrl != null) {
            val provider = repository.getProviders().find { it.url == editUrl }
            if (provider != null) {
                title = provider.title
                url = provider.url
                userAgent = provider.userAgent ?: ""
                providerType = provider.safeType
            }
        }
    }

    val scrollState = rememberScrollState()

    // Hoist resource strings so they aren't queried via LocalContext inside the click lambda
    val msgRequiredFields = stringResource(id = R.string.msg_required_fields)
    val msgProviderAdded = stringResource(id = R.string.msg_provider_added)
    val msgProviderUpdated = stringResource(id = R.string.msg_provider_updated)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.add_provider),
            contentDescription = stringResource(id = R.string.add_provider_icon_desc),
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 16.dp)
        )

        Text(
            text = if (editUrl == null) stringResource(id = R.string.title_add_provider) else stringResource(id = R.string.title_update_provider),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = title,
            onValueChange = { 
                title = it
                providerType = ProviderDetectionEngine.detectQuick(url.trim(), it)
            },
            label = { Text(stringResource(id = R.string.label_provider_title)) },
            placeholder = { Text(if (providerType == ProviderType.IPTV) stringResource(id = R.string.placeholder_provider_title) else "e.g. Vega-Org Scraper") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = url,
            onValueChange = { newValue ->
                url = newValue
                providerType = ProviderDetectionEngine.detectQuick(newValue.trim(), title)
            },
            label = { 
                Text(
                    when (providerType) {
                        ProviderType.IPTV -> stringResource(id = R.string.label_playlist_url)
                        ProviderType.VIDEO -> "Video URL (.mp4, .mkv, ...)"
                        else -> "Provider URL or identifier"
                    }
                ) 
            },
            placeholder = {
                Text(
                    when (providerType) {
                        ProviderType.VEGA -> "@vega-org or URL"
                        ProviderType.VIDEO -> "http://.../video.mp4"
                        else -> stringResource(id = R.string.placeholder_url)
                    }
                )
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = userAgent,
            onValueChange = { userAgent = it },
            label = { Text(stringResource(id = R.string.label_user_agent)) },
            placeholder = { Text(stringResource(id = R.string.placeholder_user_agent)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            singleLine = true
        )

        Button(
            enabled = !isDetecting,
            onClick = {
                val trimmedUrl = url.trim()
                if (title.isBlank() || trimmedUrl.isEmpty()) {
                    Toast.makeText(context, msgRequiredFields, Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        isDetecting = true
                        val resolvedType = if (ProviderDetectionEngine.needsContentInspection(trimmedUrl)) {
                            val result = ProviderDetectionEngine.detectFromUrl(trimmedUrl)
                            providerType = result.providerType
                            val detectedLabel = when (result.sourceKind) {
                                ProviderDetectionEngine.SourceKind.VOD_SCRAPER_MAP ->
                                    "VOD Scraper JSON detected (${result.scraperEntries.size} scrapers)"
                                ProviderDetectionEngine.SourceKind.VOD_MANIFEST ->
                                    "VOD Extension Repo detected (${result.manifestEntries.size} extensions)"
                                ProviderDetectionEngine.SourceKind.IPTV_JSON ->
                                    "IPTV Channel JSON detected"
                                ProviderDetectionEngine.SourceKind.IPTV_M3U ->
                                    "IPTV M3U Playlist detected"
                                ProviderDetectionEngine.SourceKind.DIRECT_VIDEO ->
                                    "Direct Video Stream detected"
                                ProviderDetectionEngine.SourceKind.UNKNOWN -> null
                            }
                            if (detectedLabel != null) {
                                Toast.makeText(context, detectedLabel, Toast.LENGTH_SHORT).show()
                            }
                            result.providerType
                        } else {
                            ProviderDetectionEngine.detectQuick(trimmedUrl, title)
                        }

                        val provider = Provider(
                            title = title.trim(),
                            url = trimmedUrl,
                            userAgent = userAgent.trim().takeIf { it.isNotEmpty() },
                            type = resolvedType
                        )
                        if (editUrl == null) {
                            repository.addProvider(provider)
                            Toast.makeText(context, msgProviderAdded, Toast.LENGTH_SHORT).show()
                        } else {
                            repository.updateProviderWithUrl(editUrl, provider)
                            Toast.makeText(context, msgProviderUpdated, Toast.LENGTH_SHORT).show()
                        }
                        isDetecting = false
                        onProviderAdded()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (isDetecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(id = R.string.btn_save_provider), fontSize = 18.sp)
            }
        }
        }
    }
}
