package com.wonderland.taschasplanner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wonderland.taschasplanner.data.local.CardSuit
import com.wonderland.taschasplanner.ui.theme.LocalWonderlandColors

/**
 * Storybook + Cheshire Cat styled card with subtle antique gold/violet border trim.
 */
@Composable
fun WonderlandCard(
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val extended = LocalWonderlandColors.current
    val borderColor = accentColor?.copy(alpha = 0.55f) ?: extended.cardBorder
    val shape = RoundedCornerShape(18.dp)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            content = content
        )
    }
}

/**
 * Ornate Victorian gold divider with a centered diamond ornament.
 */
@Composable
fun OrnateGoldDivider(
    modifier: Modifier = Modifier
) {
    val gold = LocalWonderlandColors.current.goldTrim
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
    ) {
        val midY = size.height / 2f
        val midX = size.width / 2f
        val diamondRadius = 5.dp.toPx()

        drawLine(
            color = gold.copy(alpha = 0.45f),
            start = Offset(0f, midY),
            end = Offset(midX - diamondRadius * 2f, midY),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = gold.copy(alpha = 0.45f),
            start = Offset(midX + diamondRadius * 2f, midY),
            end = Offset(size.width, midY),
            strokeWidth = 1.dp.toPx()
        )

        val diamond = Path().apply {
            moveTo(midX, midY - diamondRadius)
            lineTo(midX + diamondRadius, midY)
            lineTo(midX, midY + diamondRadius)
            lineTo(midX - diamondRadius, midY)
            close()
        }
        drawPath(diamond, color = gold)
    }
}

/**
 * Custom Canvas illustration of the Cheshire Cat's glowing eyes & mischievous grin.
 */
@Composable
fun CheshireCatEmblem(
    modifier: Modifier = Modifier,
    eyeGlow: Color = LocalWonderlandColors.current.cheshireGlow,
    grinOutline: Color = Color(0xFFFF4DA6),
    teethColor: Color = Color(0xFFFFFDF8)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Subtle cat ears silhouette
        val leftEar = Path().apply {
            moveTo(w * 0.22f, h * 0.28f)
            lineTo(w * 0.14f, h * 0.04f)
            lineTo(w * 0.36f, h * 0.18f)
            close()
        }
        val rightEar = Path().apply {
            moveTo(w * 0.78f, h * 0.28f)
            lineTo(w * 0.86f, h * 0.04f)
            lineTo(w * 0.64f, h * 0.18f)
            close()
        }
        drawPath(leftEar, color = grinOutline.copy(alpha = 0.42f))
        drawPath(rightEar, color = grinOutline.copy(alpha = 0.42f))

        // Glowing golden almond eyes
        val leftEye = Path().apply {
            moveTo(w * 0.24f, h * 0.38f)
            quadraticTo(w * 0.34f, h * 0.24f, w * 0.44f, h * 0.38f)
            quadraticTo(w * 0.34f, h * 0.48f, w * 0.24f, h * 0.38f)
            close()
        }
        val rightEye = Path().apply {
            moveTo(w * 0.56f, h * 0.38f)
            quadraticTo(w * 0.66f, h * 0.24f, w * 0.76f, h * 0.38f)
            quadraticTo(w * 0.66f, h * 0.48f, w * 0.56f, h * 0.38f)
            close()
        }
        drawPath(leftEye, color = eyeGlow)
        drawPath(rightEye, color = eyeGlow)

        // Vertical cat pupils
        drawLine(
            color = Color(0xFF1A0D2E),
            start = Offset(w * 0.34f, h * 0.30f),
            end = Offset(w * 0.34f, h * 0.45f),
            strokeWidth = w * 0.032f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF1A0D2E),
            start = Offset(w * 0.66f, h * 0.30f),
            end = Offset(w * 0.66f, h * 0.45f),
            strokeWidth = w * 0.032f,
            cap = StrokeCap.Round
        )

        // Wide mischievous Cheshire grin
        val grinPath = Path().apply {
            moveTo(w * 0.10f, h * 0.56f)
            quadraticTo(w * 0.50f, h * 0.98f, w * 0.90f, h * 0.56f)
            quadraticTo(w * 0.50f, h * 0.74f, w * 0.10f, h * 0.56f)
            close()
        }
        drawPath(grinPath, color = teethColor)
        drawPath(
            grinPath,
            color = grinOutline,
            style = Stroke(width = w * 0.025f, cap = StrokeCap.Round)
        )

        // Teeth dividers along the grin
        val teethXs = listOf(0.28f, 0.39f, 0.50f, 0.61f, 0.72f)
        teethXs.forEach { rx ->
            val topY = h * (0.64f + (0.5f - kotlin.math.abs(rx - 0.5f)) * 0.08f)
            val botY = h * (0.72f + (0.5f - kotlin.math.abs(rx - 0.5f)) * 0.16f)
            drawLine(
                color = Color(0xFF4A148C).copy(alpha = 0.65f),
                start = Offset(w * rx, topY),
                end = Offset(w * rx, botY),
                strokeWidth = w * 0.014f
            )
        }
    }
}

/**
 * Hero banner for the Today dashboard featuring Cheshire stripes, glowing grin, and theme toggle.
 */
@Composable
fun CheshireGreetingBanner(
    greeting: String,
    subtitle: String,
    dateLabel: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extended = LocalWonderlandColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        extended.cheshireStripeA,
                        extended.cheshireStripeB,
                        extended.cheshireStripeA
                    )
                )
            )
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateLabel.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = extended.cheshireGlow,
                    letterSpacing = 1.1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFFFFFDF8)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFFF3DAFF)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CheshireCatEmblem(modifier = Modifier.size(64.dp))
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        contentDescription = "Switch Wonderland theme",
                        tint = extended.cheshireGlow,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact pocket-watch pill showing a scheduled time or range.
 */
@Composable
fun PocketWatchBadge(
    timeText: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(0.75.dp, LocalWonderlandColors.current.goldTrim.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

/**
 * Playing-card suit emblem used for contacts and roles.
 */
@Composable
fun CardSuitBadge(
    suit: CardSuit,
    accentColor: Color,
    size: Dp = 40.dp
) {
    val symbol = when (suit) {
        CardSuit.SPADE -> "♠"
        CardSuit.HEART -> "♥"
        CardSuit.DIAMOND -> "♦"
        CardSuit.CLUB -> "♣"
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            fontSize = (size.value * 0.48f).sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
    }
}

/**
 * Whimsical Cheshire Cat empty state for lists or quiet days.
 */
@Composable
fun CheshireEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CheshireCatEmblem(modifier = Modifier.size(72.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
