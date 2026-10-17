package com.example.audifonos.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audifonos.data.protocol.AncMode
import com.example.audifonos.data.protocol.AncToggleLoop
import com.example.audifonos.data.protocol.DoubleTapAction
import com.example.audifonos.data.protocol.HoldAction
import com.example.audifonos.domain.model.HeadphonesUiState

@Composable
fun MainScreen(viewModel: HeadphonesViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val isBlePermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var hasBluetoothPermission by remember {
        mutableStateOf(
            if (isBlePermissionRequired) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasBluetoothPermission = if (isBlePermissionRequired) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            true
        }
    }

    LaunchedEffect(hasBluetoothPermission) {
        if (!hasBluetoothPermission && isBlePermissionRequired) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        } else if (hasBluetoothPermission) {
            val lastMac = viewModel.getLastMacAddress()
            if (lastMac.isNotBlank() && uiState.connectionState == "Desconectado") {
                viewModel.connect(lastMac)
            }
        }
    }

    val isConnected = uiState.connectionState == "Conectado"
    val isConnecting = uiState.connectionState == "Conectando"
    val currentMac = viewModel.getLastMacAddress()

    var selectedEarbudTab by remember { mutableStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0D0E12)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            AmbientGlowCircle(
                color = Color(0xFF00B4D8).copy(alpha = 0.10f),
                offset = Offset(-160f, 140f),
                radius = 400f
            )
            AmbientGlowCircle(
                color = Color(0xFF7209B7).copy(alpha = 0.08f),
                offset = Offset(800f, 950f),
                radius = 450f
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Mi audifono",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ConnectionStatusBadge(connectionState = uiState.connectionState)

                        if (isConnected || isConnecting) {
                            IconButton(
                                onClick = { viewModel.disconnect() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF5350).copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Desconectar",
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { viewModel.connect(currentMac) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00B4D8).copy(alpha = 0.20f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Conectar",
                                    tint = Color(0xFF00B4D8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                if (!hasBluetoothPermission && isBlePermissionRequired) {
                    PermissionBanner {
                        launcher.launch(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN
                            )
                        )
                    }
                }

                Box(modifier = Modifier.alpha(if (isConnected) 1f else 0.45f)) {
                    SpaciousBatterySection(uiState = uiState)
                }

                Box(modifier = Modifier.alpha(if (isConnected) 1f else 0.45f)) {
                    NoiseControlCard(
                        currentMode = uiState.ancMode,
                        enabled = isConnected,
                        onSelectMode = { viewModel.setAncMode(it) }
                    )
                }

                Box(modifier = Modifier.alpha(if (isConnected) 1f else 0.45f)) {
                    SpaciousGesturesCard(
                        uiState = uiState,
                        selectedEarbudTab = selectedEarbudTab,
                        enabled = isConnected,
                        onSelectEarbudTab = { selectedEarbudTab = it },
                        onSelectLeftDoubleTap = { viewModel.setDoubleTapActions(it, uiState.doubleTapRight) },
                        onSelectRightDoubleTap = { viewModel.setDoubleTapActions(uiState.doubleTapLeft, it) },
                        onSelectHold = { viewModel.setHoldAction(it) },
                        onSelectAncLoop = { viewModel.setAncToggleLoop(it) },
                        onToggleWearDetection = { viewModel.setWearDetection(it) }
                    )
                }

                val errorMessage = uiState.connectionErrorMessage
                if (uiState.connectionState == "No encontrado" && errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFFB74D),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun SpaciousBatterySection(uiState: HeadphonesUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.035f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BatteryColumnItem(
                label = "Izquierdo",
                percent = uiState.batteryLeft,
                isCharging = uiState.isLeftCharging,
                isPlaced = uiState.isLeftPlaced,
                drawEarbud = true,
                isLeft = true
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(60.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )

            BatteryColumnItem(
                label = "Estuche",
                percent = uiState.batteryCase,
                isCharging = uiState.isCaseCharging,
                isPlaced = true,
                drawEarbud = false,
                isLeft = false
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(60.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )

            BatteryColumnItem(
                label = "Derecho",
                percent = uiState.batteryRight,
                isCharging = uiState.isRightCharging,
                isPlaced = uiState.isRightPlaced,
                drawEarbud = true,
                isLeft = false
            )
        }
    }
}

@Composable
fun BatteryColumnItem(
    label: String,
    percent: Int,
    isCharging: Boolean,
    isPlaced: Boolean,
    drawEarbud: Boolean,
    isLeft: Boolean
) {
    val batteryColor by animateColorAsState(
        targetValue = when {
            percent <= 20 -> Color(0xFFE57373)
            percent <= 60 -> Color(0xFFFFD54F)
            else -> Color(0xFF81C784)
        },
        label = "color"
    )

    val animatedPercent by animateFloatAsState(
        targetValue = percent / 100f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.60f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(62.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = Color.White.copy(alpha = 0.06f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = batteryColor,
                    startAngle = -90f,
                    sweepAngle = animatedPercent * 360f,
                    useCenter = false,
                    style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            if (isCharging) {
                val infinite = rememberInfiniteTransition(label = "pulse")
                val scale by infinite.animateFloat(
                    initialValue = 0.85f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(750, easing = FastOutLinearInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale),
                    contentAlignment = Alignment.Center
                ) {
                    ChargingIllustration(color = batteryColor)
                }
            } else if (drawEarbud) {
                EarbudIllustration(isLeft = isLeft, isPlaced = isPlaced, color = batteryColor)
            } else {
                CaseIllustration(color = batteryColor)
            }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = if (percent > 0) "$percent" else "--",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "%",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 1.dp)
            )
        }

        val statusText = when {
            isCharging -> "Cargando"
            drawEarbud && isPlaced -> "Puesto"
            drawEarbud && !isPlaced -> "Fuera"
            else -> "Estuche"
        }

        Text(
            text = statusText,
            color = when {
                isCharging -> Color(0xFFFFD54F)
                drawEarbud && isPlaced -> Color(0xFF81C784)
                else -> Color.White.copy(alpha = 0.40f)
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun NoiseControlCard(
    currentMode: AncMode,
    enabled: Boolean,
    onSelectMode: (AncMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.035f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Control de Ruido",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when (currentMode) {
                        AncMode.NOISE_CANCELLING -> "Cancelación activa"
                        AncMode.TRANSPARENCY -> "Modo atención"
                        AncMode.OFF -> "Desactivado"
                    },
                    color = Color(0xFF00B4D8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SpaciousSegmentButton(
                    title = "Cancelación",
                    isSelected = currentMode == AncMode.NOISE_CANCELLING,
                    enabled = enabled,
                    onClick = { onSelectMode(AncMode.NOISE_CANCELLING) },
                    modifier = Modifier.weight(1f)
                )
                SpaciousSegmentButton(
                    title = "Desactivado",
                    isSelected = currentMode == AncMode.OFF,
                    enabled = enabled,
                    onClick = { onSelectMode(AncMode.OFF) },
                    modifier = Modifier.weight(1f)
                )
                SpaciousSegmentButton(
                    title = "Atención",
                    isSelected = currentMode == AncMode.TRANSPARENCY,
                    enabled = enabled,
                    onClick = { onSelectMode(AncMode.TRANSPARENCY) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SpaciousSegmentButton(
    title: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF00B4D8).copy(alpha = 0.18f) else Color.Transparent,
        label = "bg"
    )
    val border by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF00B4D8).copy(alpha = 0.50f) else Color.Transparent,
        label = "border"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF00B4D8) else Color.White.copy(alpha = 0.60f),
        label = "textColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun SpaciousGesturesCard(
    uiState: HeadphonesUiState,
    selectedEarbudTab: Int,
    enabled: Boolean,
    onSelectEarbudTab: (Int) -> Unit,
    onSelectLeftDoubleTap: (DoubleTapAction) -> Unit,
    onSelectRightDoubleTap: (DoubleTapAction) -> Unit,
    onSelectHold: (HoldAction) -> Unit,
    onSelectAncLoop: (AncToggleLoop) -> Unit,
    onToggleWearDetection: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.035f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            Text(
                text = "Gestos y Accesos Rápidos",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SpaciousSegmentButton(
                    title = "Auricular Izquierdo",
                    isSelected = selectedEarbudTab == 0,
                    enabled = enabled,
                    onClick = { onSelectEarbudTab(0) },
                    modifier = Modifier.weight(1f)
                )
                SpaciousSegmentButton(
                    title = "Auricular Derecho",
                    isSelected = selectedEarbudTab == 1,
                    enabled = enabled,
                    onClick = { onSelectEarbudTab(1) },
                    modifier = Modifier.weight(1f)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (selectedEarbudTab == 0) "Acción al tocar 2 veces (Izquierdo)" else "Acción al tocar 2 veces (Derecho)",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                val currentDoubleTap = if (selectedEarbudTab == 0) uiState.doubleTapLeft else uiState.doubleTapRight
                val onSelectAction = if (selectedEarbudTab == 0) onSelectLeftDoubleTap else onSelectRightDoubleTap

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val doubleTapOptions = listOf(
                        DoubleTapAction.PLAY_PAUSE to "Play / Pausa",
                        DoubleTapAction.NEXT_TRACK to "Siguiente",
                        DoubleTapAction.PREVIOUS_TRACK to "Anterior",
                        DoubleTapAction.VOICE_ASSISTANT to "Asistente",
                        DoubleTapAction.NONE to "Ninguno"
                    )

                    doubleTapOptions.forEach { (action, label) ->
                        PillSelector(
                            label = label,
                            isSelected = currentDoubleTap == action,
                            enabled = enabled,
                            onClick = { onSelectAction(action) }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.05f))
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Acción al mantener presionado",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val holdOptions = listOf(
                        HoldAction.TOGGLE_ANC to "Alternar Control de Ruido",
                        HoldAction.NONE to "Desactivado"
                    )

                    holdOptions.forEach { (action, label) ->
                        PillSelector(
                            label = label,
                            isSelected = uiState.holdAction == action,
                            enabled = enabled,
                            onClick = { onSelectHold(action) }
                        )
                    }
                }
            }

            if (uiState.holdAction == HoldAction.TOGGLE_ANC) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Modos incluidos en el ciclo:",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val loopOptions = listOf(
                            AncToggleLoop.NOISE_CANCELLING_TRANSPARENCY to "Cancelación ↔ Atención",
                            AncToggleLoop.NOISE_CANCELLING_TRANSPARENCY_OFF to "Cancelación ↔ Atención ↔ Apagado",
                            AncToggleLoop.OFF_NOISE_CANCELLING to "Apagado ↔ Cancelación"
                        )

                        loopOptions.forEach { (loop, label) ->
                            PillSelector(
                                label = label,
                                isSelected = uiState.ancToggleLoop == loop,
                                enabled = enabled,
                                onClick = { onSelectAncLoop(loop) }
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.05f))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Detección de uso",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Pausar música automáticamente al quitarse un auricular",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked = uiState.wearDetectionEnabled,
                    enabled = enabled,
                    onCheckedChange = onToggleWearDetection,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00B4D8),
                        checkedTrackColor = Color(0xFF00B4D8).copy(alpha = 0.3f),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.08f)
                    )
                )
            }
        }
    }
}

@Composable
fun PillSelector(
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF00B4D8).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.03f),
        label = "pillBg"
    )
    val border by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF00B4D8).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.07f),
        label = "pillBorder"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF00B4D8) else Color.White.copy(alpha = 0.70f),
        label = "pillText"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ConnectionStatusBadge(connectionState: String) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alphaState by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val statusColor = when (connectionState) {
        "Conectado" -> Color(0xFF4CAF50)
        "Conectando" -> Color(0xFFFFC107)
        "No encontrado" -> Color(0xFFFF9800)
        else -> Color(0xFF9E9E9E)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    statusColor.copy(
                        alpha = if (connectionState == "Conectando") alphaState else 1f
                    )
                )
        )

        Text(
            text = connectionState,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ChargingIllustration(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.58f, 0f)
            lineTo(size.width * 0.22f, size.height * 0.55f)
            lineTo(size.width * 0.48f, size.height * 0.55f)
            lineTo(size.width * 0.42f, size.height)
            lineTo(size.width * 0.78f, size.height * 0.45f)
            lineTo(size.width * 0.52f, size.height * 0.45f)
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
fun EarbudIllustration(isLeft: Boolean, isPlaced: Boolean, color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val strokeWidth = 2.dp.toPx()
        val fillAlpha = if (isPlaced) 1f else 0.15f

        drawCircle(
            color = color.copy(alpha = fillAlpha),
            radius = 4.5.dp.toPx(),
            center = Offset(size.width / 2, size.height / 3)
        )
        drawCircle(
            color = color,
            radius = 4.5.dp.toPx(),
            center = Offset(size.width / 2, size.height / 3),
            style = Stroke(width = strokeWidth)
        )

        val startOffset = Offset(size.width / 2, size.height / 3 + 3.dp.toPx())
        val endOffset = if (isLeft) {
            Offset(size.width / 2 + 4.dp.toPx(), size.height * 0.76f)
        } else {
            Offset(size.width / 2 - 4.dp.toPx(), size.height * 0.76f)
        }

        drawLine(
            color = color,
            start = startOffset,
            end = endOffset,
            strokeWidth = strokeWidth + 0.8.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun CaseIllustration(color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        drawRoundRect(
            color = color.copy(alpha = 0.15f),
            topLeft = Offset(size.width * 0.15f, size.height * 0.25f),
            size = Size(size.width * 0.7f, size.height * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.15f, size.height * 0.25f),
            size = Size(size.width * 0.7f, size.height * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = strokeWidth)
        )
    }
}

@Composable
fun AmbientGlowCircle(color: Color, offset: Offset, radius: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, Color.Transparent),
                center = offset,
                radius = radius
            ),
            radius = radius,
            center = offset
        )
    }
}

@Composable
fun PermissionBanner(onRequestPermissions: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0x26EF5350)),
        border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.4f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Advertencia",
                tint = Color(0xFFEF5350)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Permisos necesarios",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Se requiere acceso a Bluetooth para conectarse a los auriculares.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Permitir", fontSize = 12.sp)
            }
        }
    }
}
