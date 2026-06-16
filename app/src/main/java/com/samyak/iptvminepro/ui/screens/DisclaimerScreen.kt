package com.samyak.iptvminepro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisclaimerScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Disclaimer", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF26A69A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F7))
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Warning Header
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEBEE)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Important Disclaimer",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Please read this disclaimer carefully before using IPTV Mine Pro",
                                fontSize = 12.sp,
                                color = Color(0xFF7F0000)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                LegalSection(
                    title = "1. No Legal Responsibility",
                    content = """
                        IPTV Mine Pro is provided on an "as-is" basis. The developers and maintainers of this application are not responsible for:
                        • Any legal issues arising from your use of this application
                        • Unauthorized access or illegal streaming of content
                        • Violations of copyright or intellectual property rights
                        • Your compliance with local, state, or international laws
                    """.trimIndent()
                )

                LegalSection(
                    title = "2. User Responsibility",
                    content = """
                        You are solely responsible for:
                        • Ensuring your use is legal in your jurisdiction
                        • Obtaining proper licenses for content access
                        • Respecting the rights of content creators
                        • Your compliance with all applicable laws
                        • Understanding potential legal consequences
                    """.trimIndent()
                )

                LegalSection(
                    title = "3. Not a Streaming Service",
                    content = """
                        IPTV Mine Pro is not a streaming service. It is a playlist management application that:
                        • Allows users to add custom playlists
                        • Organizes and displays content from user-provided sources
                        • Does not host, produce, or distribute content
                        • Does not endorse or promote any specific content
                        • Users are responsible for content sources
                    """.trimIndent()
                )

                LegalSection(
                    title = "4. Third-Party Content",
                    content = """
                        We do not control or verify third-party content accessed through this app. We are not responsible for:
                        • Quality or legality of content
                        • Availability or accuracy of information
                        • Actions of content providers
                        • Copyright infringement by providers
                        • Misleading or harmful content
                    """.trimIndent()
                )

                LegalSection(
                    title = "5. No Warranty",
                    content = """
                        This application is provided without any warranty, express or implied:
                        • No guarantee of functionality or availability
                        • No guarantee of bug-free operation
                        • No guarantee of compatibility with all devices
                        • No guarantee of data preservation
                        • We may modify or discontinue the service anytime
                    """.trimIndent()
                )

                LegalSection(
                    title = "6. Legal Compliance",
                    content = """
                        Users must comply with all applicable laws including:
                        • Copyright and intellectual property laws
                        • Anti-piracy regulations
                        • Content distribution laws
                        • Terms of service of content providers
                        • Local and international legal requirements
                        
                        IPTV Mine Pro is not intended for illegal streaming.
                    """.trimIndent()
                )

                LegalSection(
                    title = "7. Age Restriction",
                    content = """
                        By using this application, you confirm that:
                        • You are at least 18 years old, or
                        • You have parental/guardian consent
                        • You are legally authorized to use the service
                        • You understand the legal implications
                    """.trimIndent()
                )

                LegalSection(
                    title = "8. Limitation of Liability",
                    content = """
                        Under no circumstances shall IPTV Mine Pro be liable for:
                        • Direct or indirect damages
                        • Lost profits or data
                        • Device damage
                        • Service interruptions
                        • Third-party claims
                        • Consequential or punitive damages
                    """.trimIndent()
                )

                LegalSection(
                    title = "9. Changes to Service",
                    content = """
                        We reserve the right to:
                        • Modify or discontinue the service
                        • Update features at any time
                        • Change access methods
                        • Remove functionality
                        • Update this disclaimer
                        • Terminate user access
                    """.trimIndent()
                )

                LegalSection(
                    title = "10. Legal Advice",
                    content = """
                        This disclaimer does not constitute legal advice. If you have legal concerns:
                        • Consult with a qualified attorney
                        • Research applicable laws in your jurisdiction
                        • Contact local authorities if needed
                        • Do not rely solely on this disclaimer
                    """.trimIndent()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Warning Box at Bottom
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEBEE)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = Color(0xFFEF5350)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "⚠️ WARNING",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Unauthorized streaming of copyrighted content may be illegal in your country. IPTV Mine Pro is provided for educational and legal use only. Users are solely responsible for ensuring their use complies with all applicable laws.",
                            fontSize = 12.sp,
                            color = Color(0xFF7F0000),
                            textAlign = TextAlign.Justify,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Last Updated: June 2024",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
