package com.samyak.iptvminepro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun TermsAndConditionsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms & Conditions", fontWeight = FontWeight.Bold) },
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
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF26A69A).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Terms of Service",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF26A69A)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Effective from: June 2026",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                LegalSection(
                    title = "1. Acceptance of Terms",
                    content = """
                        By downloading and using IPTV Mine Pro, you agree to be bound by these Terms and Conditions. If you do not agree to these terms, please do not use our application.
                    """.trimIndent()
                )

                LegalSection(
                    title = "2. License Grant",
                    content = """
                        We grant you a limited, non-exclusive, non-transferable license to use IPTV Mine Pro for personal, non-commercial purposes. You may not:
                        • Modify or reverse engineer the app
                        • Use it for commercial purposes
                        • Distribute or sell the app
                        • Attempt to bypass security features
                    """.trimIndent()
                )

                LegalSection(
                    title = "3. User Responsibilities",
                    content = """
                        You are responsible for:
                        • Maintaining the confidentiality of your account credentials
                        • All activities that occur under your account
                        • Ensuring your use complies with all applicable laws
                        • Not engaging in illegal streaming or content piracy
                        • Respecting intellectual property rights of content providers
                    """.trimIndent()
                )

                LegalSection(
                    title = "4. Content Providers",
                    content = """
                        IPTV Mine Pro is a platform for accessing content. We do not host, produce, or distribute content. Users are responsible for ensuring they have proper licensing to access content through their selected providers. We are not liable for content available through third-party providers.
                    """.trimIndent()
                )

                LegalSection(
                    title = "5. Restrictions",
                    content = """
                        You may not use the app to:
                        • Access copyrighted content illegally
                        • Perform illegal activities
                        • Violate any local, state, or international laws
                        • Harass or abuse other users
                        • Interfere with app functionality
                        • Circumvent security measures
                    """.trimIndent()
                )

                LegalSection(
                    title = "6. Limitation of Liability",
                    content = """
                        IPTV Mine Pro is provided "as is" without warranties. We are not responsible for:
                        • Service interruptions or downtime
                        • Data loss or device damage
                        • Third-party content or services
                        • Indirect, incidental, or consequential damages
                        • Content unavailability or provider issues
                    """.trimIndent()
                )

                LegalSection(
                    title = "7. Termination",
                    content = """
                        We reserve the right to:
                        • Terminate access for violation of these terms
                        • Terminate access for illegal use
                        • Suspend accounts engaging in harmful activities
                        • Modify or discontinue the service
                        • Update these terms at any time
                    """.trimIndent()
                )

                LegalSection(
                    title = "8. Indemnification",
                    content = """
                        You agree to indemnify and hold harmless IPTV Mine Pro from any claims, damages, or liabilities arising from:
                        • Your use of the app
                        • Your violation of these terms
                        • Your violation of any laws
                        • Your infringement of third-party rights
                    """.trimIndent()
                )

                LegalSection(
                    title = "9. Governing Law",
                    content = """
                        These Terms and Conditions are governed by applicable laws and jurisdiction where the service is provided. Any disputes shall be resolved according to these laws.
                    """.trimIndent()
                )

                LegalSection(
                    title = "10. Contact Information",
                    content = """
                        For questions regarding these Terms and Conditions:
                        Email: iptvminepro@gmail.com
                        
                        We welcome your feedback and questions.
                    """.trimIndent()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF26A69A).copy(alpha = 0.05f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Text(
                        text = "By using IPTV Mine Pro, you acknowledge that you have read, understood, and agree to be bound by these Terms and Conditions.",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
