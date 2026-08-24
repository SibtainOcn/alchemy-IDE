package dev.hazel.code.ui.common

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hazel.code.R
import dev.hazel.code.ui.theme.Radii
import dev.hazel.code.ui.theme.TextHigh
import dev.hazel.code.ui.theme.TextLow
import dev.hazel.code.ui.theme.TextMid

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
            painter = painterResource(R.drawable.ic_hazel_bolt),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            // The brand mark stays white on black; cyan is for things you can press.
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(TextHigh),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            reason ?: "Hazel needs file access",
            style = MaterialTheme.typography.headlineSmall,
            color = TextHigh,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "To browse folders and open your code, Android asks you to turn on " +
                "All files access for Hazel. Nothing leaves your device - the app has no " +
                "network permission at all.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMid,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Open settings", onClick = onGrant)
        Spacer(Modifier.height(12.dp))
        Text(
            "Settings › Hazel IDE › Allow access to manage all files",
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

/**
 * The in-app opening beat: the mark, then the loader, held only while the first directory
 * listing resolves. It exists so the transition out of the system splash is continuous
 * rather than a black flash into a populated list.
 */
@Composable
fun BrandSplash() {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.hazel_mark),
            contentDescription = "Hazel IDE",
            modifier = Modifier.size(88.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(TextHigh),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "HAZEL IDE",
            style = MaterialTheme.typography.labelMedium,
            color = TextHigh,
        )
        Spacer(Modifier.height(34.dp))
        ShapeLoader(size = 22.dp)
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
