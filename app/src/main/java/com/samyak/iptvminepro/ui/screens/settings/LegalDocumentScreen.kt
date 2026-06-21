package com.samyak.iptvminepro.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LegalDocumentScreen(docType: String) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        when (docType) {
            "privacy" -> PrivacyPolicyContent()
            "terms" -> TermsAndConditionsContent()
            "disclaimer" -> DisclaimerContent()
            else -> GeneralLegalContent()
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun PrivacyPolicyContent() {
    DocumentTitle(text = "Privacy Policy")
    DocumentSubTitle(text = "Last updated: June 14, 2026")
    
    DocumentParagraph(
        text = "At IPTVMine Pro, we prioritize the privacy of our users. This Privacy Policy document outlines the types of information we collect, how we use it, and the security measures in place to protect your data."
    )

    DocumentSectionHeader(text = "1. Information We Collect")
    DocumentParagraph(
        text = "We do not require users to create an account to use the application. The app runs locally on your device. However, the application may collect and store local configuration files, including saved IPTV providers, playlists, extension registries, and watch history data. This data is stored strictly in your device's private storage (SharedPreferences and internal filesDir) and is never transmitted to our servers."
    )

    DocumentSectionHeader(text = "2. How We Use Information")
    DocumentParagraph(
        text = "Any local data collected by the application is utilized solely to enable features within the app, such as:"
    )
    DocumentBulletPoint(text = "Maintaining your customized provider list and channel playlist mapping.")
    DocumentBulletPoint(text = "Enabling the Watch History features, allowing you to resume playback where you left off.")
    DocumentBulletPoint(text = "Caching temporary stream metadata for extensions and search functionality.")

    DocumentSectionHeader(text = "3. Third-Party Services")
    DocumentParagraph(
        text = "The app allows you to integrate third-party IPTV playlists and extensions. Please note that when you request content from these third-party streams or sources, you are connecting directly to their servers. We do not control and are not responsible for the privacy practices, tracking cookies, or contents of third-party websites and IPTV services."
    )

    DocumentSectionHeader(text = "4. Security of Data")
    DocumentParagraph(
        text = "Since all data remains on your local device, the security of your configuration is dependent on your device's security. We recommend keeping your Android OS updated and using device-level encryption or screen locks to prevent unauthorized access."
    )
}

@Composable
fun TermsAndConditionsContent() {
    DocumentTitle(text = "Terms & Conditions")
    DocumentSubTitle(text = "Last updated: June 14, 2026")

    DocumentParagraph(
        text = "Please read these Terms & Conditions carefully before using the IPTVMine Pro application. By installing and using this application, you agree to be bound by these terms."
    )

    DocumentSectionHeader(text = "1. Acceptance of Terms")
    DocumentParagraph(
        text = "By accessing and using this application, you accept and agree to be bound by the terms and provision of this agreement. If you do not agree to abide by these terms, you are not authorized to use the application."
    )

    DocumentSectionHeader(text = "2. Use License")
    DocumentParagraph(
        text = "IPTVMine Pro grants you a limited, non-exclusive, non-transferable, revocable license to download, install, and use the application solely for your personal, non-commercial entertainment purposes on compatible Android devices."
    )

    DocumentSectionHeader(text = "3. User Conduct and Content")
    DocumentParagraph(
        text = "You agree to use the application only for lawful purposes. You are solely responsible for any IPTV playlists, M3U links, or provider extensions you add to the application. You must ensure you have the appropriate legal rights or licenses to access any streams or content you load into the player."
    )

    DocumentSectionHeader(text = "4. Intellectual Property")
    DocumentParagraph(
        text = "The application structure, UI assets, logos, and custom code are the intellectual property of Samyak/IPTVMine Pro. Third-party content loaded via IPTV playlists or external provider extensions remains the property of their respective owners."
    )

    DocumentSectionHeader(text = "5. Termination")
    DocumentParagraph(
        text = "We reserve the right, without notice and in our sole discretion, to terminate your license to use the application if you violate any of these Terms & Conditions."
    )
}

@Composable
fun DisclaimerContent() {
    DocumentTitle(text = "Disclaimer")
    DocumentSubTitle(text = "Last updated: June 14, 2026")

    DocumentParagraph(
        text = "The information and services provided by the IPTVMine Pro application are on an 'as is' and 'as available' basis. Please read this disclaimer in its entirety."
    )

    DocumentSectionHeader(text = "1. General Information Only")
    DocumentParagraph(
        text = "IPTVMine Pro is a media player shell designed to aggregate and play user-provided IPTV playlists and scraper extensions. We do not host, own, or distribute any media content, streams, channels, or video links."
    )

    DocumentSectionHeader(text = "2. Playlist and Content Responsibility")
    DocumentParagraph(
        text = "Users are entirely responsible for the validity, legality, and safety of the playlists and extensions they add to the application. IPTVMine Pro does not endorse, verify, or warrant the legality of any third-party streaming links or scraping results. Using this application to stream unlicensed or copyrighted content may violate regional laws."
    )

    DocumentSectionHeader(text = "3. Limitation of Liability")
    DocumentParagraph(
        text = "Under no circumstances shall IPTVMine Pro, its developers, or its affiliates be liable for any direct, indirect, incidental, consequential, or punitive damages arising out of your use of or inability to use the application, or any reliance on the content played through it."
    )

    DocumentSectionHeader(text = "4. No Warranties")
    DocumentParagraph(
        text = "We make no representations or warranties of any kind, express or implied, about the completeness, accuracy, reliability, suitability, or availability of the application, streams, or related services for any purpose."
    )
}

@Composable
fun GeneralLegalContent() {
    DocumentTitle(text = "Legal Information")
    DocumentParagraph(
        text = "Please select one of the legal policy documents from the settings menu to view the details."
    )
}

@Composable
fun DocumentTitle(text: String) {
    Text(
        text = text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun DocumentSubTitle(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = Modifier.padding(bottom = 24.dp)
    )
}

@Composable
fun DocumentSectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun DocumentParagraph(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        lineHeight = 22.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun DocumentBulletPoint(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, bottom = 6.dp)
    ) {
        Text(
            text = "• ",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            lineHeight = 22.sp
        )
    }
}
