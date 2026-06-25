package com.samyak.iptvminepro.ui.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.samyak.iptvminepro.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition

private const val DISCORD_WEBHOOK_URL = ""
private const val TELEGRAM_BOT_TOKEN = ""
private const val TELEGRAM_CHAT_ID = ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugReportScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var discordWebhookUrl by remember { mutableStateOf(DISCORD_WEBHOOK_URL) }
    var telegramBotToken by remember { mutableStateOf(TELEGRAM_BOT_TOKEN) }
    var telegramChatId by remember { mutableStateOf(TELEGRAM_CHAT_ID) }

    LaunchedEffect(Unit) {
        // Fetch config from Firebase Firestore (Removed for F-Droid FOSS compliance)
    }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    val categories = listOf(
        "Video Playback Issues",
        "Stream Loading Errors",
        "Extension Failure",
        "UI/Layout Glitch",
        "App Crash/Freezing",
        "Other"
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 16.dp)
            )

            Text(
                text = stringResource(id = R.string.title_bug_report),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Category Dropdown
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(id = R.string.bug_category_label)) },
                    placeholder = { Text("Choose a category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                selectedCategory = category
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Title Field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(id = R.string.bug_title_label)) },
                placeholder = { Text(stringResource(id = R.string.bug_title_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )

            // Description Field
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(id = R.string.bug_desc_label)) },
                placeholder = { Text(stringResource(id = R.string.bug_desc_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(bottom = 20.dp),
                maxLines = 10
            )

            // Screenshot / Image Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.bug_image_label),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
                    )

                    if (imageUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.05f))
                        ) {
                            AsyncImage(
                                model = imageUri,
                                contentDescription = "Attached screenshot",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(
                                onClick = { imagePickerLauncher.launch("image/*") }
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(id = R.string.bug_change_image))
                            }

                            TextButton(
                                onClick = { imageUri = null },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(id = R.string.bug_remove_image))
                            }
                        }
                    } else {
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.bug_select_image))
                        }
                    }
                }
            }

            // Submit Button
            Button(
                onClick = {
                    if (title.isBlank() || description.isBlank()) {
                        Toast.makeText(context, context.getString(R.string.msg_bug_title_desc_required), Toast.LENGTH_SHORT).show()
                    } else if (selectedCategory.isBlank()) {
                        Toast.makeText(context, context.getString(R.string.msg_bug_category_required), Toast.LENGTH_SHORT).show()
                    } else {
                        isSending = true
                        scope.launch(Dispatchers.IO) {
                            val discordSuccess = sendDiscordReport(context, discordWebhookUrl, selectedCategory, title, description, imageUri)
                            val telegramSuccess = sendTelegramReport(context, telegramBotToken, telegramChatId, selectedCategory, title, description, imageUri)
                            val success = discordSuccess || telegramSuccess
                            withContext(Dispatchers.Main) {
                                isSending = false
                                if (success) {
                                    showSuccessDialog = true
                                    title = ""
                                    description = ""
                                    selectedCategory = ""
                                    imageUri = null
                                    
                                    if (!discordSuccess) {
                                        Toast.makeText(context, "Sent to Telegram only (Discord failed)", Toast.LENGTH_SHORT).show()
                                    } else if (!telegramSuccess) {
                                        Toast.makeText(context, "Sent to Discord only (Telegram failed)", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Failed to submit report. Please try again.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
                enabled = !isSending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(Icons.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.btn_submit_bug),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showSuccessDialog) {
        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.success))
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    LottieAnimation(
                        composition = composition,
                        iterations = 1,
                        modifier = Modifier.size(120.dp)
                    )
                }
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Feedback submitted successfully!",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { showSuccessDialog = false },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        )
    }
}

private suspend fun sendDiscordReport(
    context: android.content.Context,
    webhookUrl: String,
    category: String,
    title: String,
    description: String,
    imageUri: Uri?
): Boolean {
    if (webhookUrl.isBlank() || webhookUrl == "YOUR_DISCORD_WEBHOOK_URL") {
        return false
    }

    val client = OkHttpClient()
    
    val embed = JSONObject().apply {
        put("title", "Bug Report: $title")
        put("description", description)
        put("color", 0xE74C3C) // Red color hex
        
        val fields = JSONArray().apply {
            put(JSONObject().apply {
                put("name", "Category")
                put("value", category)
                put("inline", true)
            })
        }
        put("fields", fields)
        
        if (imageUri != null) {
            put("image", JSONObject().apply {
                put("url", "attachment://screenshot.jpg")
            })
        }
    }

    val payloadJson = JSONObject().apply {
        put("embeds", JSONArray().apply { put(embed) })
    }.toString()

    return try {
        if (imageUri != null) {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val bytes = inputStream?.readBytes() ?: return false
            inputStream.close()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("payload_json", payloadJson)
                .addFormDataPart(
                    "files[0]", 
                    "screenshot.jpg", 
                    bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(webhookUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    android.util.Log.e("BugReportScreen", "Discord request failed: code=${response.code} body=$body")
                }
                response.isSuccessful
            }
        } else {
            val requestBody = payloadJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            
            val request = Request.Builder()
                .url(webhookUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    android.util.Log.e("BugReportScreen", "Discord request failed: code=${response.code} body=$body")
                }
                response.isSuccessful
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("BugReportScreen", "Discord send failed", e)
        false
    }
}

private suspend fun sendTelegramReport(
    context: android.content.Context,
    token: String,
    chatId: String,
    category: String,
    title: String,
    description: String,
    imageUri: Uri?
): Boolean {
    if (token.isBlank() || chatId.isBlank()) {
        return false
    }

    val client = OkHttpClient()
    val messageText = """
        <b>🚨 New Bug Report</b>
        
        <b>Category:</b> $category
        <b>Title:</b> $title
        
        <b>Description:</b>
        $description
    """.trimIndent()

    var success = sendTelegramRequest(client, token, chatId, messageText, context, imageUri)
    if (!success && !chatId.startsWith("-")) {
        val fallbackChatId = "-100$chatId"
        android.util.Log.d("BugReportScreen", "Attempting fallback send with Telegram Chat ID: $fallbackChatId")
        success = sendTelegramRequest(client, token, fallbackChatId, messageText, context, imageUri)
    }
    return success
}

private fun sendTelegramRequest(
    client: OkHttpClient,
    token: String,
    chatId: String,
    messageText: String,
    context: android.content.Context,
    imageUri: Uri?
): Boolean {
    return try {
        val request = if (imageUri != null) {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val bytes = inputStream?.readBytes() ?: return false
            inputStream.close()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", messageText)
                .addFormDataPart("parse_mode", "HTML")
                .addFormDataPart(
                    "photo",
                    "screenshot.jpg",
                    bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            Request.Builder()
                .url("https://api.telegram.org/bot$token/sendPhoto")
                .post(requestBody)
                .build()
        } else {
            val requestBody = FormBody.Builder()
                .add("chat_id", chatId)
                .add("text", messageText)
                .add("parse_mode", "HTML")
                .build()

            Request.Builder()
                .url("https://api.telegram.org/bot$token/sendMessage")
                .post(requestBody)
                .build()
        }

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                android.util.Log.e("BugReportScreen", "Telegram request failed: code=${response.code} body=$body")
            }
            response.isSuccessful
        }
    } catch (e: Exception) {
        android.util.Log.e("BugReportScreen", "Telegram send failed for chat ID $chatId", e)
        false
    }
}
