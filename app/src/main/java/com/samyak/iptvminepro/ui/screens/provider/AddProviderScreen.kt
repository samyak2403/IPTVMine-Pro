package com.samyak.iptvminepro.ui.screens.provider

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.samyak.iptvminepro.R
import com.samyak.iptvminepro.model.Provider
import com.samyak.iptvminepro.model.ProviderType
import com.samyak.iptvminepro.provider.ProviderDetectionEngine
import com.samyak.iptvminepro.provider.ProviderRepository

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
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

    suspend fun saveProvider(rawTitle: String, rawUrl: String, rawUserAgent: String) {
        val trimmedTitle = rawTitle.trim()
        val trimmedUrl = rawUrl.trim()
        if (trimmedTitle.isEmpty() || trimmedUrl.isEmpty()) {
            Toast.makeText(context, msgRequiredFields, Toast.LENGTH_SHORT).show()
            return
        }
        isDetecting = true
        try {
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
                ProviderDetectionEngine.detectQuick(trimmedUrl, trimmedTitle)
            }

            val provider = Provider(
                title = trimmedTitle,
                url = trimmedUrl,
                userAgent = rawUserAgent.trim().takeIf { it.isNotEmpty() },
                type = resolvedType
            )
            if (editUrl == null) {
                repository.addProvider(provider)
                Toast.makeText(context, msgProviderAdded, Toast.LENGTH_SHORT).show()
            } else {
                repository.updateProviderWithUrl(editUrl, provider)
                Toast.makeText(context, msgProviderUpdated, Toast.LENGTH_SHORT).show()
            }
            onProviderAdded()
        } finally {
            isDetecting = false
        }
    }

    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents ?: return@rememberLauncherForActivityResult
        val parsed = ProviderDetectionEngine.parseProviderQrPayload(raw)
        if (parsed == null) {
            Toast.makeText(context, "QR code does not contain a valid provider URL", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val finalTitle = title.trim().ifBlank { parsed.title }
        val finalUa = parsed.userAgent ?: userAgent
        title = finalTitle
        url = parsed.url
        userAgent = finalUa
        providerType = ProviderDetectionEngine.detectQuick(parsed.url, finalTitle)
        scope.launch {
            saveProvider(finalTitle, parsed.url, finalUa)
        }
    }

    fun startQrScan() {
        val options = ScanOptions().apply {
            setCaptureActivity(QrScannerActivity::class.java)
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("")
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        qrScanLauncher.launch(options)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startQrScan()
        } else {
            Toast.makeText(context, "Camera permission is required to scan QR codes", Toast.LENGTH_SHORT).show()
        }
    }

    fun onScanQrClicked() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startQrScan()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

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
            trailingIcon = {
                IconButton(enabled = !isDetecting, onClick = ::onScanQrClicked) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR Code"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = userAgent,
            onValueChange = { userAgent = it },
            label = { Text(stringResource(id = R.string.label_user_agent)) },
            placeholder = { Text(stringResource(id = R.string.placeholder_user_agent)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            singleLine = true
        )

        Button(
            enabled = !isDetecting,
            onClick = {
                scope.launch {
                    saveProvider(title, url, userAgent)
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

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            enabled = !isDetecting,
            onClick = ::onScanQrClicked,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan QR Code", fontSize = 16.sp)
        }
        }
    }
}
