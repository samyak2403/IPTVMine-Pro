package com.samyak.iptvminepro.ui.screens

import android.content.Intent
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugReportScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

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
                        val emailIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "*/*"
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("arrowwouldpro@gmail.com"))
                            putExtra(Intent.EXTRA_SUBJECT, "Bug Report - IPTV Mine Pro: $title")
                            
                            val emailBody = """
                                Category: $selectedCategory
                                
                                Description:
                                $description
                            """.trimIndent()
                            
                            putExtra(Intent.EXTRA_TEXT, emailBody)
                            
                            if (imageUri != null) {
                                putExtra(Intent.EXTRA_STREAM, imageUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            
                            selector = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                            }
                        }

                        try {
                            context.startActivity(Intent.createChooser(emailIntent, "Send Bug Report via..."))
                        } catch (e: Exception) {
                            Toast.makeText(context, "No email client found", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
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
