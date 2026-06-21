package com.samyak.iptvminepro.ui.screens.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import com.samyak.iptvminepro.R
import com.samyak.iptvminepro.provider.ChannelsProvider
import kotlin.math.absoluteValue

@Composable
fun CategoryScreen(
    viewModel: ChannelsProvider = viewModel(),
    onCategoryClick: (String) -> Unit
) {
    val channels by viewModel.channels.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMessage by viewModel.error.observeAsState(null)

    LaunchedEffect(Unit) {
        if (viewModel.channels.value.isNullOrEmpty()) {
            viewModel.fetchM3UFile()
        }
    }

    val categoriesWithCount = remember(channels) {
        channels.groupBy { it.category }
            .mapValues { it.value.size }
            .toList()
            .sortedBy { it.first } // Sorted alphabetically
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading && channels.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (!errorMessage.isNullOrEmpty() && channels.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.undraw_files_missing_ntwe),
                        contentDescription = stringResource(id = R.string.desc_error),
                        modifier = Modifier
                            .size(200.dp)
                            .padding(bottom = 16.dp)
                    )
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else if (channels.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.undraw_files_missing_ntwe),
                        contentDescription = stringResource(id = R.string.desc_no_channels),
                        modifier = Modifier
                            .size(200.dp)
                            .padding(bottom = 16.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.label_no_categories),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(categoriesWithCount, key = { it.first }) { (categoryName, count) ->
                        CategoryCard(
                            name = categoryName,
                            count = count,
                            onClick = { onCategoryClick(categoryName) }
                        )
                    }
                }
            }

            // Overlay loading indicator for background updates
            if (isLoading && channels.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun CategoryCard(
    name: String,
    count: Int,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1.0f)

    val gradients = remember {
        listOf(
            Brush.linearGradient(listOf(Color(0xFFE0F2F1), Color(0xFFB2DFDB))), // Teal
            Brush.linearGradient(listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))), // Blue
            Brush.linearGradient(listOf(Color(0xFFEDE7F6), Color(0xFFD1C4E9))), // Purple
            Brush.linearGradient(listOf(Color(0xFFFCE4EC), Color(0xFFF8BBD0))), // Pink
            Brush.linearGradient(listOf(Color(0xFFFFF8E1), Color(0xFFFFECB3))), // Amber
            Brush.linearGradient(listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9))), // Green
            Brush.linearGradient(listOf(Color(0xFFF3E5F5), Color(0xFFE1BEE7))), // Deep Purple
            Brush.linearGradient(listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2)))  // Orange
        )
    }

    val cardGradient = remember(name) {
        val index = name.hashCode().absoluteValue % gradients.size
        gradients[index]
    }

    val primaryColor = remember(name) {
        val hash = name.hashCode().absoluteValue
        when (hash % 8) {
            0 -> Color(0xFF00796B)
            1 -> Color(0xFF1976D2)
            2 -> Color(0xFF512DA8)
            3 -> Color(0xFFC2185B)
            4 -> Color(0xFFF57C00)
            5 -> Color(0xFF388E3C)
            6 -> Color(0xFF7B1FA2)
            else -> Color(0xFFE65100)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 8.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cardGradient)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(primaryColor.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (count == 1) stringResource(id = R.string.item_count_single) else stringResource(id = R.string.item_count_plural, count),
                            color = primaryColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121), // High contrast text for light colored card backgrounds
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
