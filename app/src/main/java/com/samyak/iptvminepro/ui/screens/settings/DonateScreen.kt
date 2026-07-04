package com.samyak.iptvminepro.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samyak.iptvminepro.R

private const val BUY_ME_A_COFFEE_URL = "https://buymeacoffee.com/mr_samyakkamble"
private const val UPI_ID = "samyakkamble01430-4@oksbi"

@Composable
fun DonateScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Card with Gradient
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFFF7043),
                                Color(0xFFF4511E)
                            )
                        )
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(id = R.string.donate_header_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.donate_header_desc),
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Buy Me a Coffee Card
        DonateOptionCard(
            title = stringResource(id = R.string.donate_bmc_title),
            subtitle = "buymeacoffee.com/mr_samyakkamble",
            subtitleColor = Color(0xFF000000),
            description = stringResource(id = R.string.donate_bmc_desc),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Coffee,
                    contentDescription = stringResource(id = R.string.donate_bmc_title),
                    tint = Color(0xFFFFDD00),
                    modifier = Modifier.size(40.dp)
                )
            },
            onCardClick = { openBuyMeACoffee(context) }
        ) {
            // Official Buy Me a Coffee branding: yellow (#FFDD00) with black content
            Button(
                onClick = { openBuyMeACoffee(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFDD00),
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Coffee,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.btn_open_bmc),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // UPI Card
        DonateOptionCard(
            title = stringResource(id = R.string.donate_upi_title),
            subtitle = UPI_ID,
            subtitleColor = Color(0xFF43A047),
            description = stringResource(id = R.string.donate_upi_desc),
            leadingIcon = {
                Image(
                    painter = painterResource(id = R.drawable.ic_upi),
                    contentDescription = stringResource(id = R.string.donate_upi_title),
                    modifier = Modifier.size(40.dp)
                )
            },
            onCardClick = { payViaUpi(context) }
        ) {
            Button(
                onClick = { payViaUpi(context) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF43A047),
                    contentColor = Color.White
                )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_upi),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.btn_pay_upi),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val copiedMessage = stringResource(id = R.string.donate_upi_copied)
            OutlinedButton(
                onClick = { copyUpiId(context, copiedMessage) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF43A047)
                ),
                border = BorderStroke(1.dp, Color(0xFF43A047).copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.btn_copy_upi),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DonateOptionCard(
    title: String,
    subtitle: String,
    subtitleColor: Color,
    description: String,
    leadingIcon: @Composable () -> Unit,
    onCardClick: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .clickable { onCardClick() }
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    leadingIcon()
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = subtitleColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            actions()
        }
    }
}

private fun openBuyMeACoffee(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BUY_ME_A_COFFEE_URL))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open the link. Please ensure a browser is installed.", Toast.LENGTH_LONG).show()
    }
}

private fun payViaUpi(context: Context) {
    val upiUri = Uri.parse(
        "upi://pay?pa=$UPI_ID&pn=Samyak%20Kamble&cu=INR"
    )
    val intent = Intent(Intent.ACTION_VIEW, upiUri)
    try {
        context.startActivity(Intent.createChooser(intent, "Pay via"))
    } catch (e: Exception) {
        Toast.makeText(context, "No UPI app found. Copy the UPI ID instead.", Toast.LENGTH_LONG).show()
    }
}

private fun copyUpiId(context: Context, message: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", UPI_ID))
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Could not copy UPI ID.", Toast.LENGTH_SHORT).show()
    }
}
