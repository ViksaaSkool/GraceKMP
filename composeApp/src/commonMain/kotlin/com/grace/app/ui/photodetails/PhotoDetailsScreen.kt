package com.grace.app.ui.photodetails

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.grace.app.core.GraceConstants
import com.grace.app.resources.Res
import com.grace.app.resources.ic_close
import com.grace.app.ui.theme.GraceColors
import com.grace.app.ui.theme.GraceDimens
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.painterResource

/**
 * `PhotoDetailsActivity` + `activity_photo_details.xml`.
 *
 * Translucent-black full-screen viewer with a 1000ms crossfade. The original
 * created a `PhotoViewAttacher` with zooming disabled and enabled it only once
 * the image had loaded; the same gating is applied to the gesture detector.
 */
@Composable
fun PhotoDetailsScreen(
    photoUri: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var loaded by remember(photoUri) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GraceColors.TransparentBlack)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                .data(photoUri.toPath())
                .crossfade(GraceConstants.CROSS_FADE_DURATION)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onSuccess = { loaded = true },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .pointerInput(loaded) {
                    if (!loaded) return@pointerInput
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                }
        )

        Image(
            painter = painterResource(Res.drawable.ic_close),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clickable(onClick = onClose)
                .padding(GraceDimens.ClosePadding)
        )
    }
}
