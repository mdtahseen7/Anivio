package com.nuvio.app.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.anilist_unavailable_message
import nuvio.composeapp.generated.resources.anilist_unavailable_retry
import nuvio.composeapp.generated.resources.anilist_unavailable_title
import org.jetbrains.compose.resources.stringResource

/**
 * Shown wherever an AniList outage would otherwise leave an unexplained empty screen.
 *
 * The illustration is drawn rather than shipped as an asset so this cannot fail to render. To use an
 * animated character instead, drop a Lottie JSON into `composeResources/files/` and pass its path as
 * [lottieAssetPath]; a file that fails to load falls back to the drawn face rather than blanking.
 */
@Composable
fun AniListUnavailableCard(
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    lottieAssetPath: String? = null,
) {
    NuvioSurfaceCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (lottieAssetPath != null) {
                    AniListOutageLottie(assetPath = lottieAssetPath)
                } else {
                    SadFaceIllustration(modifier = Modifier.fillMaxSize())
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.anilist_unavailable_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.anilist_unavailable_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (onRetry != null) {
                Spacer(modifier = Modifier.height(16.dp))
                NuvioPrimaryButton(
                    text = stringResource(Res.string.anilist_unavailable_retry),
                    onClick = onRetry,
                )
            }
        }
    }
}

@Composable
private fun AniListOutageLottie(assetPath: String) {
    val composition by rememberLottieComposition {
        LottieCompositionSpec.JsonString(Res.readBytes(assetPath).decodeToString())
    }
    if (composition == null) {
        // Still loading, or the asset is missing or malformed. Either way something has to occupy
        // the slot, and the drawn face is the safe choice.
        SadFaceIllustration(modifier = Modifier.fillMaxSize())
        return
    }
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = Compottie.IterateForever,
    )
    Image(
        painter = rememberLottiePainter(composition = composition, progress = { progress }),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun SadFaceIllustration(modifier: Modifier = Modifier) {
    val faceColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val side = minOf(size.width, size.height)
        val radius = side / 2f * 0.86f
        val center = Offset(size.width / 2f, size.height / 2f)
        val stroke = Stroke(width = side * 0.045f)

        drawCircle(
            color = faceColor,
            radius = radius,
            center = center,
            style = stroke,
        )

        // Closed, downturned eyes read as sad at small sizes where pupils just look like dots.
        val eyeOffsetX = radius * 0.4f
        val eyeY = center.y - radius * 0.18f
        val eyeWidth = radius * 0.34f
        listOf(-eyeOffsetX, eyeOffsetX).forEach { offsetX ->
            drawArc(
                color = faceColor,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x + offsetX - eyeWidth / 2f, eyeY - eyeWidth / 2f),
                size = Size(eyeWidth, eyeWidth),
                style = stroke,
            )
        }

        val mouthWidth = radius * 0.9f
        drawArc(
            color = faceColor,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(center.x - mouthWidth / 2f, center.y + radius * 0.34f),
            size = Size(mouthWidth, mouthWidth * 0.55f),
            style = stroke,
        )

        drawCircle(
            color = accentColor,
            radius = side * 0.045f,
            center = Offset(center.x + eyeOffsetX, eyeY + radius * 0.42f),
        )
    }
}
