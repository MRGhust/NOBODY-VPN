package com.nobodyiran.nobodyvpn.ui.home

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobodyiran.nobodyvpn.R
import com.nobodyiran.nobodyvpn.data.Repo
import com.nobodyiran.nobodyvpn.service.ConnState
import com.nobodyiran.nobodyvpn.service.NobodyVpnService
import com.nobodyiran.nobodyvpn.service.VpnState
import com.nobodyiran.nobodyvpn.ui.UiBus
import com.nobodyiran.nobodyvpn.ui.components.ProtocolChip
import com.nobodyiran.nobodyvpn.ui.components.delayColor
import com.nobodyiran.nobodyvpn.ui.theme.AmberWarn
import com.nobodyiran.nobodyvpn.ui.theme.DangerRed
import com.nobodyiran.nobodyvpn.ui.theme.NeonGreen
import com.nobodyiran.nobodyvpn.util.Fmt

@Composable
fun HomeScreen(onOpenServers: () -> Unit, onOpenLogs: () -> Unit) {
    val context = LocalContext.current
    val repo = Repo.get(context)

    val connState by VpnState.state.collectAsState()
    val error by VpnState.error.collectAsState()
    val runningRemark by VpnState.runningRemark.collectAsState()
    val elapsed by VpnState.elapsedSeconds.collectAsState()
    val upSpeed by VpnState.upSpeed.collectAsState()
    val downSpeed by VpnState.downSpeed.collectAsState()
    val sessionUp by VpnState.sessionUp.collectAsState()
    val sessionDown by VpnState.sessionDown.collectAsState()
    val totalUp by repo.totalUp.collectAsState()
    val totalDown by repo.totalDown.collectAsState()
    val selectedId by repo.selectedId.collectAsState()
    val profiles by repo.profiles.collectAsState()

    val selected = profiles.firstOrNull { it.id == selectedId }

    // ---- VPN permission flow: ask the system consent dialog BEFORE starting the service.
    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            NobodyVpnService.start(context, NobodyVpnService.ACTION_CONNECT)
        } else {
            UiBus.show(context.getString(R.string.err_vpn_prepare))
        }
    }

    val connect: () -> Unit = {
        try {
            val consent = VpnService.prepare(context)
            if (consent != null) vpnLauncher.launch(consent)
            else NobodyVpnService.start(context, NobodyVpnService.ACTION_CONNECT)
        } catch (_: Exception) {
            NobodyVpnService.start(context, NobodyVpnService.ACTION_CONNECT)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // ------------------------------------------------------------ minimal header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(connState = connState, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenLogs) {
                Icon(
                    Icons.Rounded.Insights,
                    contentDescription = stringResource(R.string.nav_logs),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ------------------------------------------------------------ power button
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            PowerButton(
                state = connState,
                onToggle = {
                    when (connState) {
                        ConnState.CONNECTED -> NobodyVpnService.start(context, NobodyVpnService.ACTION_DISCONNECT)
                        ConnState.DISCONNECTED, ConnState.ERROR -> connect()
                        else -> Unit
                    }
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        // ------------------------------------------------------------ status caption
        val active = connState == ConnState.CONNECTED
        when {
            active -> {
                Text(
                    text = runningRemark.ifBlank { stringResource(R.string.app_name) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = Fmt.duration(elapsed),
                    style = MaterialTheme.typography.headlineSmall,
                    color = NeonGreen,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            connState == ConnState.ERROR -> Text(
                text = error ?: stringResource(R.string.status_error),
                style = MaterialTheme.typography.titleSmall,
                color = DangerRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )
            connState == ConnState.CONNECTING || connState == ConnState.DISCONNECTING -> Text(
                text = stringResource(
                    if (connState == ConnState.CONNECTING) R.string.status_connecting
                    else R.string.status_stopping
                ),
                style = MaterialTheme.typography.titleSmall,
                color = AmberWarn,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            else -> Text(
                text = stringResource(R.string.tap_to_connect),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(22.dp))

        // ------------------------------------------------------------ selected server
        Card(
            onClick = onOpenServers,
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.selected_server),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    if (selected == null) {
                        Text(
                            stringResource(R.string.no_server_selected),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            selected.remark,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (selected != null) {
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        ProtocolChip(selected.type)
                        val delay = selected.delayMs
                        if (delay > 0) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                stringResource(R.string.ping_ms, delay.toInt()),
                                style = MaterialTheme.typography.labelSmall,
                                color = delayColor(delay)
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ------------------------------------------------------------ live speed cards
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.ArrowDownward,
                iconTint = NeonGreen,
                label = stringResource(R.string.speed_down),
                value = Fmt.speed(downSpeed),
                sub = "${Fmt.volume(sessionDown)} · ${Fmt.volume(totalDown)}"
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.ArrowUpward,
                iconTint = Color(0xFF60A5FA),
                label = stringResource(R.string.speed_up),
                value = Fmt.speed(upSpeed),
                sub = "${Fmt.volume(sessionUp)} · ${Fmt.volume(totalUp)}"
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.total_traffic) + " · " + Fmt.volume(totalDown + totalUp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- status pill

@Composable
private fun StatusPill(connState: ConnState, modifier: Modifier = Modifier) {
    val (labelRes, color) = when (connState) {
        ConnState.CONNECTED -> R.string.status_connected to NeonGreen
        ConnState.CONNECTING -> R.string.status_connecting to AmberWarn
        ConnState.DISCONNECTING -> R.string.status_stopping to AmberWarn
        ConnState.ERROR -> R.string.status_error to DangerRed
        else -> R.string.status_disconnected to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.10f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1
            )
        }
    }
}

// ---------------------------------------------------------------- power button

@Composable
private fun PowerButton(state: ConnState, onToggle: () -> Unit) {
    val active = state == ConnState.CONNECTED
    val connecting = state == ConnState.CONNECTING || state == ConnState.DISCONNECTING
    val error = state == ConnState.ERROR

    val accent = when {
        error -> DangerRed
        connecting -> AmberWarn
        else -> NeonGreen
    }

    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "scale"
    )

    // glow pulse while connected
    val glowAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "glowAlpha"
    )
    val glowPulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700), RepeatMode.Reverse),
        label = "pulse"
    )

    // full ring when connected
    val ringProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "ringProgress"
    )

    // rotating arc while connecting / disconnecting
    val rotation by rememberInfiniteTransition(label = "rot").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing)),
        label = "rotV"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(284.dp)) {

        // soft ambient glow (connected)
        if (glowAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .graphicsLayer {
                        alpha = glowAlpha * (0.10f + glowPulse * 0.12f)
                    }
                    .background(NeonGreen, CircleShape)
            )
        }

        // animated ring
        Canvas(modifier = Modifier.size(248.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2 + 5.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            // track
            drawArc(
                color = Color.White.copy(alpha = 0.055f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            when {
                // rotating arc
                connecting -> drawArc(
                    brush = Brush.sweepGradient(listOf(AmberWarn, Color(0xFFFDE68A), AmberWarn)),
                    startAngle = -90f + rotation,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                // error: static partial red arc
                error -> drawArc(
                    brush = Brush.sweepGradient(listOf(DangerRed, Color(0xFFB91C1C), DangerRed)),
                    startAngle = -90f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                // connected: full gradient ring
                ringProgress > 0.01f -> drawArc(
                    brush = Brush.sweepGradient(listOf(NeonGreen, Color(0xFF4ADE80), NeonGreen)),
                    startAngle = -90f,
                    sweepAngle = 360f * ringProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        // inner button
        Box(
            modifier = Modifier
                .size(196.dp)
                .scale(scale)
                .background(
                    brush = Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surface
                        )
                    ),
                    shape = CircleShape
                )
                .border(
                    width = 1.dp,
                    color = accent.copy(alpha = if (active) 0.45f else 0.18f),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
            contentAlignment = Alignment.Center
        ) {
            // small inner accent dot ring for depth
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .border(1.dp, accent.copy(alpha = if (active) 0.22f else 0.08f), CircleShape)
            )
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = stringResource(
                    if (active) R.string.btn_disconnect else R.string.btn_connect
                ),
                tint = accent,
                modifier = Modifier.size(64.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- stat card

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    sub: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(3.dp))
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
