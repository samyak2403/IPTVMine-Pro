package com.samyak.iptvminepro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    val scope = rememberCoroutineScope()
    val providerRepository = remember { ProviderRepository(context) }
    val extensionRepository = remember { ExtensionRepository.getInstance(context) }
    val runner = remember { VegaProviderRunner(context) }
    
    var isLoading by remember { mutableStateOf(true) }
    var allExtensions by remember { mutableStateOf<List<VegaProvider>>(emptyList()) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf(stringResource(id = R.string.tab_installed), stringResource(id = R.string.tab_available))
    
    var vegaProviders by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var selectedSource by remember { mutableStateOf<Provider?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // Collect reactive flow of installed extensions
    val installedExtensionsState by extensionRepository.installedExtensionsFlow.collectAsState()
    
    val installedExtensions = remember(allExtensions, installedExtensionsState) {
        allExtensions.filter { it.value in installedExtensionsState }
    }
    val availableExtensions = remember(allExtensions, installedExtensionsState) {
        allExtensions.filter { it.value !in installedExtensionsState && !it.disabled }
    }

    // Load sources
    LaunchedEffect(Unit) {
        vegaProviders = providerRepository.getProviders().filter { it.safeType == ProviderType.VEGA }
        selectedSource = vegaProviders.firstOrNull()
    }

    // Load extensions when source changes
    LaunchedEffect(selectedSource) {
        if (selectedSource != null) {
            scope.launch {
                isLoading = true
                val manifestList = mutableListOf<VegaProvider>()
                try {
                    val manifest = runner.fetchManifest(selectedSource!!.url)
                    manifestList.addAll(manifest)
                } catch (e: Exception) {
                    android.util.Log.e("ExtensionsScreen", "Failed to fetch manifest", e)
                }
                val distinctManifest = manifestList.distinctBy { it.value }
                allExtensions = distinctManifest
                
                val installedCount = distinctManifest.count { it.value in installedExtensionsState }
                val availableCount = distinctManifest.count { it.value !in installedExtensionsState && !it.disabled }
                if (installedCount == 0 && availableCount > 0) {
                    selectedTabIndex = 1 // Switch to available tab if none are installed
                }
                isLoading = false
            }
        } else {
            allExtensions = emptyList()
            isLoading = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(stringResource(id = R.string.title_extensions)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.desc_back)
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
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
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
                        Text(
                            text = if (selectedTabIndex == 0) "No extensions installed" else "No extensions available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(currentData, key = { it.value }) { extension ->
                            ExtensionItem(
                                extension = extension,
                                isInstalled = selectedTabIndex == 0,
                                onAction = { installed ->
                                    extensionRepository.setExtensionInstalled(extension.value, installed)
                                    if (installed) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "${extension.display_name} has been installed successfully!",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExtensionItem(
    extension: VegaProvider,
    isInstalled: Boolean,
    onAction: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    text = extension.display_name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
