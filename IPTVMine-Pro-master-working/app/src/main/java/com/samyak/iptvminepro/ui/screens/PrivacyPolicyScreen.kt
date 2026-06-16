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
fun PrivacyPolicyScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
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
                            text = "Your Privacy Matters",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF26A69A)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Last updated: June 2026",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Section 1
                LegalSection(
                    title = "1. Information We Collect",
                    content = """
                        We collect information you provide directly to us, such as:
                        • Account registration data
                        • Download and viewing history
                        • Device information
                        • IP address and location data
                        • Crash reports and usage analytics
                    """.trimIndent()
                )

                LegalSection(
                    title = "2. How We Use Your Information",
                    content = """
                        We use the information we collect to:
                        • Provide and improve our services
                        • Personalize your experience
                        • Send you updates and promotional materials
                        • Analyze usage patterns and trends
                        • Ensure security and prevent fraud
                    """.trimIndent()
                )

                LegalSection(
                    title = "3. Data Security",
                    content = """
                        We implement appropriate security measures to protect your personal information from unauthorized access, alteration, disclosure, or destruction. However, no method of transmission over the Internet is 100% secure.
                    """.trimIndent()
                )

                LegalSection(
                    title = "4. Third-Party Services",
                    content = """
                        Our app may use third-party services like analytics, crash reporting, and advertising platforms. These services have their own privacy policies and we encourage you to review them.
                    """.trimIndent()
                )

                LegalSection(
                    title = "5. Your Rights",
                    content = """
                        You have the right to:
                        • Access your personal data
                        • Correct inaccurate data
                        • Request deletion of your data
                        • Opt-out of marketing communications
                        • Export your data
                    """.trimIndent()
                )

                LegalSection(
                    title = "6. Changes to Privacy Policy",
                    content = """
                        We may update this privacy policy from time to time. We will notify you of any significant changes by posting the new policy with the updated effective date.
                    """.trimIndent()
                )

                LegalSection(
                    title = "7. Contact Us",
                    content = """
                        If you have questions about this Privacy Policy or our privacy practices, please contact us at:
                        Email: iptvminepro@gmail.com
                    """.trimIndent()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Footer
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF26A69A).copy(alpha = 0.05f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Text(
                        text = "We are committed to protecting your privacy and providing transparency about how we handle your data.",
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

@Composable
fun LegalSection(title: String, content: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF26A69A)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                fontSize = 13.sp,
                color = Color(0xFF424242),
                lineHeight = 20.sp
            )
        }
    }
}
