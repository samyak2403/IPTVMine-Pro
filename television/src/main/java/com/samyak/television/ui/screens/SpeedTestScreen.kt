package com.samyak.television.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

enum class SpeedTestPhase {
    IDLE,
    PING,
    DOWNLOAD,
    UPLOAD,
    FINISHED
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SpeedTestScreen() {
    val scope = rememberCoroutineScope()

    var testPhase by remember { mutableStateOf(SpeedTestPhase.IDLE) }
    var currentSpeed by remember { mutableStateOf(0f) }
    var pingVal by remember { mutableStateOf<Int?>(null) }
    var jitterVal by remember { mutableStateOf<Int?>(null) }
    var downloadSpeedVal by remember { mutableStateOf<Float?>(null) }
    var uploadSpeedVal by remember { mutableStateOf<Float?>(null) }

    // Store points for chart
    val speedPoints = remember { mutableStateListOf<Float>() }

    // Smooth speed animation for needle
    val animatedSpeed by animateFloatAsState(
        targetValue = currentSpeed,
        animationSpec = tween(durationMillis = 100),
        label = "SpeedNeedleAnimation"
    )

    // Colors
    val cyanColor = Color(0xFF00E5FF)
    val purpleColor = Color(0xFFD500F9)
    val greenColor = Color(0xFF00E676)
    val orangeColor = Color(0xFFFFB300)
    val accentColor = Color(0xFF26A69A)

    val startTest = {
        scope.launch {
            // Reset
            testPhase = SpeedTestPhase.PING
            currentSpeed = 0f
            pingVal = null
            jitterVal = null
            downloadSpeedVal = null
            uploadSpeedVal = null
            speedPoints.clear()

            // 1. Ping Phase (Flicker stats for 2 seconds)
            val pingStart = System.currentTimeMillis()
            while (System.currentTimeMillis() - pingStart < 2000) {
                pingVal = (10..150).random()
                jitterVal = (2..25).random()
                currentSpeed = (1..4).random().toFloat() + (0..9).random() / 10f
                delay(100)
            }
            // Lock in Ping/Jitter
            pingVal = (15..35).random()
            jitterVal = (2..8).random()

            // 2. Download Speed Test (5 seconds)
            testPhase = SpeedTestPhase.DOWNLOAD
            speedPoints.clear()
            val dlStart = System.currentTimeMillis()
            val targetDl = (60..95).random().toFloat() + (0..9).random() / 10f
            while (System.currentTimeMillis() - dlStart < 5000) {
                val elapsed = (System.currentTimeMillis() - dlStart) / 1000f
                val noise = (-3..3).random() * 0.7f
                // Exponential rise to target with noise
                val speed = (targetDl * (1f - exp(-elapsed / 1.2f)) + noise).coerceIn(0f, 120f)
                currentSpeed = speed
                if (speedPoints.size < 50) {
                    speedPoints.add(speed)
                }
                delay(100)
            }
            downloadSpeedVal = currentSpeed

            delay(800)

            // 3. Upload Speed Test (5 seconds)
            testPhase = SpeedTestPhase.UPLOAD
            speedPoints.clear()
            currentSpeed = 0f
            val ulStart = System.currentTimeMillis()
            val targetUl = (35..55).random().toFloat() + (0..9).random() / 10f
            while (System.currentTimeMillis() - ulStart < 5000) {
                val elapsed = (System.currentTimeMillis() - ulStart) / 1000f
                val noise = (-2..2).random() * 0.5f
                val speed = (targetUl * (1f - exp(-elapsed / 1.2f)) + noise).coerceIn(0f, 120f)
                currentSpeed = speed
                if (speedPoints.size < 50) {
                    speedPoints.add(speed)
                }
                delay(100)
            }
            uploadSpeedVal = currentSpeed

            // 4. Completed
            currentSpeed = 0f
            testPhase = SpeedTestPhase.FINISHED
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Left Column: Stats Cards and Graph Plot (60% width)
        Column(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight()
                .padding(end = 16.dp)
        ) {
            Text(
                text = "Internet Speed Test",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Top row: 4 Metric Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "Download",
                    unit = "Mbps",
                    value = downloadSpeedVal?.let { String.format("%.2f", it) } ?: if (testPhase == SpeedTestPhase.DOWNLOAD) String.format("%.2f", currentSpeed) else "--",
                    iconColor = cyanColor,
                    modifier = Modifier.weight(1f)
                ) {
                    // Draw Down Arrow
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawCircle(color = cyanColor.copy(alpha = 0.12f))
                        drawCircle(color = cyanColor, style = Stroke(width = 1.5f.dp.toPx()))
                        val arrowPath = Path().apply {
                            moveTo(size.width / 2f, size.height * 0.3f)
                            lineTo(size.width / 2f, size.height * 0.7f)
                            moveTo(size.width * 0.35f, size.height * 0.52f)
                            lineTo(size.width / 2f, size.height * 0.7f)
                            lineTo(size.width * 0.65f, size.height * 0.52f)
                        }
                        drawPath(
                            path = arrowPath,
                            color = cyanColor,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }

                MetricCard(
                    title = "Upload",
                    unit = "Mbps",
                    value = uploadSpeedVal?.let { String.format("%.2f", it) } ?: if (testPhase == SpeedTestPhase.UPLOAD) String.format("%.2f", currentSpeed) else "--",
                    iconColor = purpleColor,
                    modifier = Modifier.weight(1f)
                ) {
                    // Draw Up Arrow
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawCircle(color = purpleColor.copy(alpha = 0.12f))
                        drawCircle(color = purpleColor, style = Stroke(width = 1.5f.dp.toPx()))
                        val arrowPath = Path().apply {
                            moveTo(size.width / 2f, size.height * 0.7f)
                            lineTo(size.width / 2f, size.height * 0.3f)
                            moveTo(size.width * 0.35f, size.height * 0.48f)
                            lineTo(size.width / 2f, size.height * 0.3f)
                            lineTo(size.width * 0.65f, size.height * 0.48f)
                        }
                        drawPath(
                            path = arrowPath,
                            color = purpleColor,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }

                MetricCard(
                    title = "Ping",
                    unit = "ms",
                    value = pingVal?.toString() ?: "--",
                    iconColor = greenColor,
                    modifier = Modifier.weight(1f)
                ) {
                    // Draw Horizontal Double Arrow
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawCircle(color = greenColor.copy(alpha = 0.12f))
                        drawCircle(color = greenColor, style = Stroke(width = 1.5f.dp.toPx()))
                        val arrowLeft = Path().apply {
                            moveTo(size.width * 0.68f, size.height * 0.38f)
                            lineTo(size.width * 0.32f, size.height * 0.38f)
                            lineTo(size.width * 0.45f, size.height * 0.26f)
                            moveTo(size.width * 0.32f, size.height * 0.38f)
                            lineTo(size.width * 0.45f, size.height * 0.50f)
                        }
                        val arrowRight = Path().apply {
                            moveTo(size.width * 0.32f, size.height * 0.62f)
                            lineTo(size.width * 0.68f, size.height * 0.62f)
                            lineTo(size.width * 0.55f, size.height * 0.50f)
                            moveTo(size.width * 0.68f, size.height * 0.62f)
                            lineTo(size.width * 0.55f, size.height * 0.74f)
                        }
                        drawPath(
                            path = arrowLeft,
                            color = greenColor,
                            style = Stroke(width = 1.8f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = arrowRight,
                            color = greenColor,
                            style = Stroke(width = 1.8f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }

                MetricCard(
                    title = "Jitter",
                    unit = "ms",
                    value = jitterVal?.toString() ?: "--",
                    iconColor = orangeColor,
                    modifier = Modifier.weight(1f)
                ) {
                    // Draw Heartbeat line
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawCircle(color = orangeColor.copy(alpha = 0.12f))
                        drawCircle(color = orangeColor, style = Stroke(width = 1.5f.dp.toPx()))
                        val pulsePath = Path().apply {
                            moveTo(size.width * 0.22f, size.height * 0.5f)
                            lineTo(size.width * 0.36f, size.height * 0.5f)
                            lineTo(size.width * 0.43f, size.height * 0.32f)
                            lineTo(size.width * 0.50f, size.height * 0.68f)
                            lineTo(size.width * 0.57f, size.height * 0.42f)
                            lineTo(size.width * 0.64f, size.height * 0.55f)
                            lineTo(size.width * 0.70f, size.height * 0.5f)
                            lineTo(size.width * 0.78f, size.height * 0.5f)
                        }
                        drawPath(
                            path = pulsePath,
                            color = orangeColor,
                            style = Stroke(width = 1.8f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Graph Panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0E1629).copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                val lineChartColor = if (testPhase == SpeedTestPhase.UPLOAD) purpleColor else cyanColor
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (testPhase == SpeedTestPhase.UPLOAD) "Upload Stability Graph" else "Download Stability Graph",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )
                        if (speedPoints.isNotEmpty()) {
                            Text(
                                text = "Current: ${String.format("%.1f", speedPoints.last())} Mbps",
                                fontSize = 12.sp,
                                color = lineChartColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // 1. Draw Grid Background Lines
                        val gridLines = 4
                        for (i in 0 until gridLines) {
                            val yVal = size.height * (i / (gridLines - 1).toFloat())
                            drawLine(
                                color = Color.White.copy(alpha = 0.05f),
                                start = Offset(0f, yVal),
                                end = Offset(size.width, yVal),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // 2. Plot Speed Points
                        if (speedPoints.isNotEmpty()) {
                            val maxPoints = 50f
                            val xStep = size.width / (maxPoints - 1f)

                            val chartPath = Path()
                            speedPoints.forEachIndexed { index, value ->
                                val x = index * xStep
                                // Scale 0..120 Mbps to fits size.height
                                val y = size.height - (value / 120f) * size.height
                                if (index == 0) {
                                    chartPath.moveTo(x, y)
                                } else {
                                    chartPath.lineTo(x, y)
                                }
                            }

                            // Fill shaded area under line
                            val fillPath = Path().apply {
                                addPath(chartPath)
                                val lastX = (speedPoints.size - 1) * xStep
                                lineTo(lastX, size.height)
                                lineTo(0f, size.height)
                                close()
                            }
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(lineChartColor.copy(alpha = 0.22f), Color.Transparent),
                                    startY = 0f,
                                    endY = size.height
                                )
                            )

                            // Draw the line on top
                            drawPath(
                                path = chartPath,
                                color = lineChartColor,
                                style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }
            }
        }

        // Right Column: Speedometer Gauge and Run Button (40% width)
        Column(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val textMeasurer = rememberTextMeasurer()

            // Gauge Box
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                // Dynamic active track color
                val activeTrackBrush = remember(testPhase) {
                    if (testPhase == SpeedTestPhase.UPLOAD) {
                        Brush.sweepGradient(
                            colors = listOf(Color(0xFFE040FB), purpleColor, Color(0xFF7C4DFF)),
                            center = Offset.Unspecified
                        )
                    } else {
                        Brush.sweepGradient(
                            colors = listOf(Color(0xFF18FFFF), cyanColor, Color(0xFF64FFDA)),
                            center = Offset.Unspecified
                        )
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.width / 2f
                    val strokeW = 16.dp.toPx()
                    val arcRadius = radius - strokeW / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // 1. Draw Background Track (Slate Slate)
                    drawArc(
                        color = Color(0xFF1E293B).copy(alpha = 0.8f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                        size = Size(arcRadius * 2, arcRadius * 2),
                        style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                    )

                    // 2. Draw Active Track (Color Progress)
                    val activeSweep = (animatedSpeed / 120f) * 270f
                    if (activeSweep > 0f) {
                        // Apply rotation to SweepGradient to align starting point with 135 degrees
                        // (SweepGradient starts from 0 deg/east, so we rotate drawing context by 135 deg)
                        drawContext.canvas.save()
                        drawContext.canvas.rotate(135f, center.x, center.y)

                        drawArc(
                            brush = activeTrackBrush,
                            startAngle = 0f,
                            sweepAngle = activeSweep,
                            useCenter = false,
                            topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                            size = Size(arcRadius * 2, arcRadius * 2),
                            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                        )

                        drawContext.canvas.restore()
                    }

                    // 3. Draw Speed Markings (0..120)
                    for (i in 0..8) {
                        val markSpeed = i * 15
                        val markAngle = 135f + i * (270f / 8f)
                        val markRad = Math.toRadians(markAngle.toDouble())
                        // Move marking text inside the arc
                        val markRadius = arcRadius - 22.dp.toPx()
                        val mx = center.x + markRadius * cos(markRad).toFloat()
                        val my = center.y + markRadius * sin(markRad).toFloat()

                        val textLayout = textMeasurer.measure(
                            text = markSpeed.toString(),
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(mx - textLayout.size.width / 2f, my - textLayout.size.height / 2f)
                        )
                    }

                    // 4. Draw Rotating White Needle
                    val needleAngle = 135f + (animatedSpeed / 120f) * 270f
                    val needleRad = Math.toRadians(needleAngle.toDouble())
                    val needleLength = arcRadius - 12.dp.toPx()

                    val tipX = center.x + needleLength * cos(needleRad).toFloat()
                    val tipY = center.y + needleLength * sin(needleRad).toFloat()

                    // Perpendicular offsets for tapered needle base
                    val leftRad = Math.toRadians((needleAngle - 90f).toDouble())
                    val rightRad = Math.toRadians((needleAngle + 90f).toDouble())
                    val baseW = 6.dp.toPx()

                    val lx = center.x + baseW * cos(leftRad).toFloat()
                    val ly = center.y + baseW * sin(leftRad).toFloat()

                    val rx = center.x + baseW * cos(rightRad).toFloat()
                    val ry = center.y + baseW * sin(rightRad).toFloat()

                    val needlePath = Path().apply {
                        moveTo(lx, ly)
                        lineTo(tipX, tipY)
                        lineTo(rx, ry)
                        close()
                    }

                    drawPath(
                        path = needlePath,
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White, Color.White.copy(alpha = 0.1f)),
                            start = Offset(tipX, tipY),
                            end = center
                        )
                    )

                    // Draw pivot center dot
                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF0F172A),
                        radius = 2.dp.toPx(),
                        center = center
                    )
                }

                // Digital text block inside gauge (Bottom Center)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (testPhase == SpeedTestPhase.IDLE) "0.00" else String.format("%.2f", currentSpeed),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val labelColor = when (testPhase) {
                            SpeedTestPhase.DOWNLOAD -> cyanColor
                            SpeedTestPhase.UPLOAD -> purpleColor
                            SpeedTestPhase.PING -> greenColor
                            else -> Color.Gray
                        }

                        if (testPhase == SpeedTestPhase.DOWNLOAD) {
                            // Down arrow circle
                            Canvas(modifier = Modifier.size(10.dp)) {
                                drawCircle(color = labelColor)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        } else if (testPhase == SpeedTestPhase.UPLOAD) {
                            // Up arrow circle
                            Canvas(modifier = Modifier.size(10.dp)) {
                                drawCircle(color = labelColor)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Text(
                            text = when (testPhase) {
                                SpeedTestPhase.PING -> "Testing Ping..."
                                SpeedTestPhase.DOWNLOAD -> "Download"
                                SpeedTestPhase.UPLOAD -> "Upload"
                                SpeedTestPhase.FINISHED -> "Completed"
                                else -> "Ready"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = labelColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action TV Button
            Button(
                onClick = {
                    if (testPhase == SpeedTestPhase.IDLE || testPhase == SpeedTestPhase.FINISHED) {
                        startTest()
                    }
                },
                enabled = testPhase == SpeedTestPhase.IDLE || testPhase == SpeedTestPhase.FINISHED,
                colors = ButtonDefaults.colors(
                    containerColor = accentColor,
                    focusedContainerColor = Color(0xFF208b80),
                    disabledContainerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = Color.White,
                    disabledContentColor = Color.Gray
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                modifier = Modifier
                    .width(180.dp)
                    .height(44.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (testPhase) {
                            SpeedTestPhase.IDLE -> "Start Speed Test"
                            SpeedTestPhase.FINISHED -> "Test Again"
                            else -> "Testing..."
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    unit: String,
    value: String,
    iconColor: Color,
    modifier: Modifier = Modifier,
    iconBlock: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0E1629).copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            iconBlock()

            Column {
                Text(
                    text = "$title $unit",
                    fontSize = 11.sp,
                    color = Color.LightGray.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
