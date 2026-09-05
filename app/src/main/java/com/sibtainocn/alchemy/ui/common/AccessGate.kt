package com.sibtainocn.alchemy.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sibtainocn.alchemy.R
import com.sibtainocn.alchemy.ui.theme.Radii
import com.sibtainocn.alchemy.ui.theme.TextHigh
import com.sibtainocn.alchemy.ui.theme.TextLow
import com.sibtainocn.alchemy.ui.theme.TextMid

/**
 * Shown when storage access is missing - on first run, and again if the grant is revoked
 * while the app is in the background. It states plainly what is needed and why, because
 * "All files access" is a permission users are right to hesitate over.
 */
@Composable
fun AccessGate(
    reason: String? = null,
    onGrant: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_alchemy_mark),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            // The brand mark stays white on black; cyan is for things you can press.
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(TextHigh),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            reason ?: "Alchemy needs file access",
            style = MaterialTheme.typography.headlineSmall,
            color = TextHigh,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "To browse folders and open your code, Android asks you to turn on " +
                "All files access for Alchemy. Nothing leaves your device - the app has no " +
                "network permission at all.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMid,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Open settings", onClick = onGrant)
        Spacer(Modifier.height(12.dp))
        Text(
            "Settings › Alchemy IDE › Allow access to manage all files",
            style = MaterialTheme.typography.labelSmall,
            color = TextLow,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Motion.snappy(), label = "cta")

    Box(
        modifier
            .scale(scale)
            .clip(RoundedCornerShape(Radii.md))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** How long the splash is on screen, and how long one pass of the shine takes. */
const val SPLASH_MS = 1100

/** The word between sweeps: dark, but never so dark that it is not there. */
private val SHIMMER_REST = Color(0xFF3A3A42)

/**
 * The opening beat: the mark, and the name with a light passing across it.
 *
 * The system splash hands over on the first frame, so this is what launching the app
 * actually looks like. The shine is one sweep timed to the screen's own life rather than
 * a spinner, because a spinner says "wait" and there is nothing here to wait for.
 */
@Composable
fun BrandSplash() {
    // One sweep, timed to the life of the screen: the shine reaches the last letter as
    // the splash hands over, so it never repeats and never gets cut off mid-stroke.
    val transition = rememberInfiniteTransition(label = "splash")
    val travel by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SPLASH_MS, easing = LinearEasing)),
        label = "sweep",
    )

    // The gradient is positioned in pixels, so it has to know how wide the word came out.
    var wordWidth by remember { mutableStateOf(0) }
    val span = wordWidth.toFloat().coerceAtLeast(1f)
    val band = span * 0.55f
    val head = -band + travel * (span + band * 2f)

    // Dark letters with a light passing over them, rather than light letters that fade.
    // The unlit colour has to stay readable on true black or the word disappears between
    // sweeps, which is the one thing a splash cannot do.
    val brush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to SHIMMER_REST,
            0.5f to Color.White,
            1f to SHIMMER_REST,
        ),
        start = Offset(head, 0f),
        end = Offset(head + band, 0f),
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The name alone, on the centre line. The mark sat above it and pushed the word
        // off centre to make room for itself, which is the wrong way round: the word is
        // what the shine is for, and the icon is already the thing you tapped to get here.
        Text(
            "ALCHEMY",
            style = TextStyle(
                brush = brush,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 9.sp,
            ),
            modifier = Modifier.onSizeChanged { wordWidth = it.width },
        )
    }
}

/** A dismissible strip for recoverable failures - a locked folder, a failed write. */
@Composable
fun ErrorNotice(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(Radii.sm))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ico.Info, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.error)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = TextHigh,
            modifier = Modifier.padding(horizontal = 12.dp).weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radii.xs))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(Radii.xs))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ico.Close, "Dismiss", Modifier.size(15.dp), tint = TextMid)
        }
    }
}
