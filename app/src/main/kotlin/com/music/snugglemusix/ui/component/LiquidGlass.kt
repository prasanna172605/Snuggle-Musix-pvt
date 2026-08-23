package com.snuggle.music.ui.component

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop as nativeBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.sign
import androidx.compose.foundation.isSystemInDarkTheme

typealias PlatformBackdrop = LayerBackdrop

val LocalPlatformBackdrop = androidx.compose.runtime.staticCompositionLocalOf<PlatformBackdrop?> { null }


fun Modifier.layerBackdrop(backdrop: PlatformBackdrop): Modifier = this.nativeBackdrop(backdrop)

@Composable
fun rememberBackdrop(color: Color): PlatformBackdrop =
    rememberLayerBackdrop {
        drawRect(color)
        drawContent()
    }

class GlassInteraction(val scope: CoroutineScope) {
    val pressProgressAnimation = Animatable(0f, 0.001f)
    val pressProgress: Float get() = pressProgressAnimation.value
    var touchPosition by mutableStateOf(Offset.Zero)

    private val pressSpec = spring<Float>(1f, 1000f, 0.001f)

    fun press(position: Offset) {
        touchPosition = position
        scope.launch {
            pressProgressAnimation.animateTo(1f, pressSpec)
        }
    }

    fun release() {
        scope.launch {
            pressProgressAnimation.animateTo(0f, pressSpec)
        }
    }

    suspend fun detectPress(scope: PointerInputScope) {
        scope.inspectDragGestures(
            onDragStart = { down -> press(down.position) },
            onDragEnd = { release() },
            onDragCancel = { release() }
        ) { change, _ ->
            touchPosition = change.position
        }
    }
}

@Composable
fun rememberGlassInteraction(): GlassInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassInteraction(scope) }
}

@Composable
fun Modifier.liquidGlass(
    backdrop: PlatformBackdrop,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    highlight: Highlight = Highlight.Default,
): Modifier {
    val isDark = isSystemInDarkTheme()
    val layer = rememberGraphicsLayer()
    val interaction = rememberGlassInteraction()
    return this.drawInteractiveGlass(
        isDark = isDark,
        backdrop = backdrop,
        layer = layer,
        luminanceAnimation = 0.5f,
        shape = shape,
        interaction = if (interactive) interaction else null,
        highlight = highlight,
    )
}

fun Modifier.drawInteractiveGlass(
    isDark: Boolean,
    backdrop: PlatformBackdrop,
    layer: GraphicsLayer,
    luminanceAnimation: Float,
    shape: Shape,
    interaction: GlassInteraction?,
    pressedScale: Float = 1.05f,
    highlight: Highlight = Highlight.Default,
    blurScale: Float = 1.0f,
    minScrim: Float = 0.12f,
    maxScrim: Float = 0.5f,
): Modifier =
    this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            highlight = { highlight },
            effects = {
                val l = (luminanceAnimation * 2f - 1f).let { sign(it) * it * it }
                val press = interaction?.pressProgress ?: 0f
                vibrancy()
                colorControls(
                    brightness = 0.05f,
                    contrast = 1f,
                    saturation = 1.5f,
                )
                blur(
                    (
                        if (l > 0f) {
                            lerp(8f.dp.toPx(), 16f.dp.toPx(), l)
                        } else {
                            lerp(8f.dp.toPx(), 2f.dp.toPx(), -l)
                        }
                    ) * blurScale + 2f.dp.toPx() * press,
                )
                lens(size.minDimension / 4f + 2f.dp.toPx() * press, size.minDimension / 2f, false)
            },
            onDrawBackdrop = { drawBackdrop ->
                drawBackdrop()
                layer.record { drawBackdrop() }
            },
            onDrawSurface = {
                val darken = lerp(minScrim, maxScrim, ((luminanceAnimation - 0.3f) / 0.5f).coerceIn(0f, 1f))
                drawRect((if (isDark) Color.Black else Color.White).copy(alpha = darken))
                val press = interaction?.pressProgress ?: 0f
                if (press > 0f) {
                    drawRect(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        Color.White.copy(alpha = 0.18f * press),
                                        Color.Transparent,
                                    ),
                                center = interaction?.touchPosition ?: Offset(size.width / 2f, size.height / 2f),
                                radius = size.minDimension * 1.2f,
                            ),
                        blendMode = BlendMode.Plus,
                    )
                }
            },
            layerBlock =
                if (interaction != null) {
                    {
                        val scale = lerp(1f, pressedScale, interaction.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    }
                } else {
                    null
                },
        ).then(
            if (interaction != null) {
                Modifier.pointerInput(interaction) { interaction.detectPress(this) }
            } else {
                Modifier
            },
        )

internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        onDrag(down, Offset.Zero)
        val upEvent =
            drag(
                pointerId = down.id,
                onDrag = { onDrag(it, it.positionChange()) },
            )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) {
        return null
    }
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) {
            return null
        }
        if (change.changedToUpIgnoreConsumed()) {
            return change
        }
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) {
                return dragEvent
            }
        }
    }
}
