package com.samyak.iptvminepro.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.samyak.iptvminepro.R
import com.samyak.iptvminepro.model.Provider
import com.samyak.iptvminepro.model.ProviderType
import com.samyak.iptvminepro.model.VegaProvider
import com.samyak.iptvminepro.provider.ExtensionRepository
import com.samyak.iptvminepro.provider.ProviderRepository
import com.samyak.iptvminepro.provider.VegaProviderRunner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val providerRepository = remember { ProviderRepository(context) }
    val extensionRepository = remember { ExtensionRepository.getInstance(context) }
    val runner = remember { VegaProviderRunner(context) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { runner.destroy() } }
    
    var isLoading by remember { mutableStateOf(true) }
    var allExtensions by remember { mutableStateOf<List<VegaProvider>>(emptyList()) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf(stringResource(id = R.string.tab_installed), stringResource(id = R.string.tab_available))
    
    var vegaProviders by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var selectedSource by remember { mutableStateOf<Provider?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successExtensionName by remember { mutableStateOf("") }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    // Collect reactive flow of installed extensions
    val installedExtensionsState by extensionRepository.installedExtensionsFlow.collectAsState()
    
    val installedExtensions = remember(allExtensions, installedExtensionsState) {
        allExtensions.filter { it.value in installedExtensionsState }
    }
    val availableExtensions = remember(allExtensions) {
        allExtensions.filter { !it.disabled }
    }

    // Load sources
    LaunchedEffect(Unit) {
        val providers = providerRepository.getProviders().filter { it.safeType == ProviderType.VEGA }
        if (providers.isEmpty()) {
            val defaultVega = Provider(
                title = "Vega",
                url = VegaProviderRunner.DEFAULT_VEGA_REPO,
                type = ProviderType.VEGA,
                isActive = true
            )
            providerRepository.addProvider(defaultVega)
            vegaProviders = listOf(defaultVega)
            selectedSource = defaultVega
        } else {
            vegaProviders = providers
            selectedSource = providers.firstOrNull()
        }
    }

    // Load extensions when source changes or user requests reload
    LaunchedEffect(selectedSource, reloadTrigger) {
        val source = selectedSource
        if (source != null) {
            isLoading = true
            val manifestList = mutableListOf<VegaProvider>()
            try {
                val manifest = runner.fetchManifest(source.url)
                manifestList.addAll(manifest)
            } catch (e: Exception) {
                android.util.Log.e("ExtensionsScreen", "Failed to fetch manifest", e)
            }
            val distinctManifest = manifestList.distinctBy { it.value }
            allExtensions = distinctManifest
            
            val installedCount = distinctManifest.count { it.value in installedExtensionsState }
            val availableCount = distinctManifest.count { !it.disabled }
            if (installedCount == 0 && availableCount > 0) {
                selectedTabIndex = 1 // Switch to available tab if none are installed
            }
            isLoading = false
        } else {
            allExtensions = emptyList()
            isLoading = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.title_extensions)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.desc_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { reloadTrigger++ }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Extensions"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Provider Source Selector
            if (vegaProviders.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = isDropdownExpanded,
                        onExpandedChange = { isDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedSource?.title ?: "Select Source",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Provider Source") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false }
                        ) {
                            vegaProviders.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.title) },
                                    onClick = {
                                        selectedSource = provider
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    val count = if (index == 0) installedExtensions.size else availableExtensions.size
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(if (count > 0) "$title ($count)" else title) }
                    )
                }
            }
            
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val currentData = if (selectedTabIndex == 0) installedExtensions else availableExtensions
                
                if (currentData.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = if (selectedTabIndex == 0) "No extensions installed" else "No extensions available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (allExtensions.isEmpty()) {
                                Button(onClick = { reloadTrigger++ }) {
                                    Text("Retry Loading Extensions")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(currentData, key = { it.value }) { extension ->
                            val isItemInstalled = extension.value in installedExtensionsState
                            ExtensionItem(
                                extension = extension,
                                isInstalled = isItemInstalled,
                                onAction = { installed ->
                                    extensionRepository.setExtensionInstalled(extension.value, installed)
                                    if (installed) {
                                        successExtensionName = extension.display_name
                                        showSuccessDialog = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text(text = "Extension Installed") },
            text = { Text(text = "$successExtensionName has been installed successfully!") },
            confirmButton = {
                TextButton(onClick = { showSuccessDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun ExtensionItem(
    extension: VegaProvider,
    isInstalled: Boolean,
    onAction: (Boolean) -> Unit
) {
    val isBlackTheme = com.samyak.iptvminepro.ui.theme.LocalIsBlackTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isBlackTheme) Color(0xFF121212) else Color.White),
        border = if (isBlackTheme) BorderStroke(1.dp, Color(0xFF242424)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Extension Logo
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isBlackTheme) Color(0xFF1E1E1E) else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, if (isBlackTheme) Color(0xFF2E2E2E) else Color(0xFFE2E8F0)),
                modifier = Modifier.size(48.dp)
            ) {
                val iconUrl = remember(extension.icon) {
                    val raw = extension.icon.trim()
                    when {
                        raw.isBlank() -> ""
                        raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) -> raw
                        raw.startsWith("//") -> "https:$raw"
                        else -> raw
                    }
                }

                if (iconUrl.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(iconUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${extension.display_name} logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp)),
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = extension.display_name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isInstalled) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Installed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "v${extension.version} • ${extension.type}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { onAction(!isInstalled) },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (isInstalled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isInstalled) Icons.Default.Delete else Icons.Default.Download,
                    contentDescription = if (isInstalled) stringResource(id = R.string.btn_uninstall) else stringResource(id = R.string.btn_install)
                )
            }
        }
    }
}
