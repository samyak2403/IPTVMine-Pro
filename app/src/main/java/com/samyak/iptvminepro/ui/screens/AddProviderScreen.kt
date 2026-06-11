package com.samyak.iptvminepro.ui.screens

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
import com.samyak.iptvminepro.provider.ProviderRepository

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProviderScreen(
    onProviderAdded: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    editUrl: String? = null
) {
    val context = LocalContext.current
    val repository = remember { ProviderRepository(context) }

    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }

    LaunchedEffect(editUrl) {
        if (editUrl != null) {
            val provider = repository.getProviders().find { it.url == editUrl }
            if (provider != null) {
                title = provider.title
                url = provider.url
                userAgent = provider.userAgent ?: ""
            }
        }
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
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
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(id = R.string.label_provider_title)) },
            placeholder = { Text(stringResource(id = R.string.placeholder_provider_title)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(id = R.string.label_playlist_url)) },
            placeholder = { Text(stringResource(id = R.string.placeholder_url)) },
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
            onClick = {
                if (title.isBlank() || url.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.msg_required_fields), Toast.LENGTH_SHORT).show()
                } else {
                    val provider = Provider(title.trim(), url.trim(), userAgent.trim().takeIf { it.isNotEmpty() })
                    if (editUrl == null) {
                        repository.addProvider(provider)
                        Toast.makeText(context, context.getString(R.string.msg_provider_added), Toast.LENGTH_SHORT).show()
                    } else {
                        repository.updateProviderWithUrl(editUrl, provider)
                        Toast.makeText(context, context.getString(R.string.msg_provider_updated), Toast.LENGTH_SHORT).show()
                    }
                    onProviderAdded()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(stringResource(id = R.string.btn_save_provider), fontSize = 18.sp)
        }
        }
    }
}
