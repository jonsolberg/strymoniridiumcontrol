package se.jonsolberg.iridium.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.jonsolberg.iridium.model.pct
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

val FaceText = Color(0xFFECE7DC)
val FaceTextDim = Color(0xFF9C9890)
val LedRed = Color(0xFFFF3B30)
val LedBlue = Color(0xFF3D8BFF)
val LedGreen = Color(0xFF34D058)

private val labelStyle = TextStyle(
    color = FaceText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp,
)

/** Brushed dark-metal enclosure top. */
fun Modifier.brushedMetal(corner: Dp = 20.dp): Modifier = this.drawWithCache {
    val rnd = Random(7)
    val lines = List(size.height.toInt() / 2) {
        Triple(rnd.nextFloat() * size.height, rnd.nextFloat() * 0.07f, rnd.nextBoolean())
    }
    val base = Brush.linearGradient(
        listOf(Color(0xFF4B4E54), Color(0xFF34373C), Color(0xFF26282C)),
        start = Offset.Zero, end = Offset(size.width * 0.6f, size.height),
    )
    val sheen = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.07f), Color.Transparent, Color.Black.copy(alpha = 0.18f)),
    )
    val r = CornerRadius(corner.toPx())
    onDrawBehind {
        drawRoundRect(base, cornerRadius = r)
        lines.forEach { (y, a, light) ->
            drawLine(
                (if (light) Color.White else Color.Black).copy(alpha = a),
                Offset(0f, y), Offset(size.width, y), strokeWidth = 1f,
            )
        }
        drawRoundRect(sheen, cornerRadius = r)
        drawRoundRect(Color.White.copy(alpha = 0.12f), cornerRadius = r, style = Stroke(1.5f))
    }
}

// ------------------------------------------------------------------ knob

/**
 * Rotary knob, 0..127. Drag up/right to turn clockwise, double-tap to centre.
 */
@Composable
fun PedalKnob(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    knobSize: Dp = 68.dp,
    format: (Int) -> String = { "${pct(it)}" },
) {
    var dragging by remember { mutableStateOf(false) }
    var accum by remember { mutableFloatStateOf(value.toFloat()) }
    val latestValue by rememberUpdatedState(value)
    val latestChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(value) { if (!dragging) accum = value.toFloat() }
    val pxPerStep = with(LocalDensity.current) { 1.5.dp.toPx() }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = labelStyle, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .size(knobSize)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            accum = latestValue.toFloat()
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, drag ->
                        change.consume()
                        accum = (accum + (drag.x - drag.y) / pxPerStep).coerceIn(0f, 127f)
                        val nv = accum.roundToInt()
                        if (nv != latestValue) latestChange(nv)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { latestChange(64) })
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) { drawKnob(value) }
            if (dragging) {
                Box(
                    Modifier
                        .background(Color(0xE0101010), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(format(value), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(format(value), color = FaceTextDim, fontSize = 10.sp)
    }
}

private fun DrawScope.drawKnob(value: Int) {
    val r = size.minDimension / 2f
    val c = center
    val start = 120f
    val sweep = 300f

    // scale dots on the faceplate
    for (i in 0..10) {
        val a = Math.toRadians((start + sweep * i / 10f).toDouble())
        drawCircle(
            FaceTextDim.copy(alpha = 0.8f), radius = r * 0.03f,
            center = c + Offset((cos(a) * r * 0.97f).toFloat(), (sin(a) * r * 0.97f).toFloat()),
        )
    }
    val body = r * 0.86f
    // shadow
    drawCircle(Color.Black.copy(alpha = 0.5f), radius = body, center = c + Offset(body * 0.05f, body * 0.10f))
    // knurled skirt
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFF2F2F32), Color(0xFF0A0A0B)), center = c, radius = body),
        radius = body, center = c,
    )
    val notches = 44
    for (i in 0 until notches) {
        val a = Math.toRadians(i * 360.0 / notches)
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        drawLine(
            Color(0xFF404044), c + Offset(ca * body * 0.80f, sa * body * 0.80f),
            c + Offset(ca * body * 0.97f, sa * body * 0.97f), strokeWidth = body * 0.035f,
        )
    }
    // cap
    val cap = body * 0.74f
    drawCircle(
        Brush.linearGradient(
            listOf(Color(0xFF45454A), Color(0xFF1A1A1C), Color(0xFF101011)),
            start = c - Offset(cap, cap), end = c + Offset(cap, cap),
        ),
        radius = cap, center = c,
    )
    drawCircle(Color.White.copy(alpha = 0.10f), radius = cap, center = c, style = Stroke(body * 0.025f))
    // pointer
    val a = Math.toRadians((start + sweep * value / 127f).toDouble())
    val ca = cos(a).toFloat()
    val sa = sin(a).toFloat()
    drawLine(
        Color(0xFFF4F0E6), c + Offset(ca * cap * 0.25f, sa * cap * 0.25f), c + Offset(ca * cap * 0.92f, sa * cap * 0.92f),
        strokeWidth = body * 0.08f, cap = StrokeCap.Round,
    )
}

// ------------------------------------------------------------------ 3-way toggle

@Composable
fun ThreeWayToggle(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 66.dp,
) {
    val height = maxOf(minHeight, 68.dp) // room for three labels
    val latestSelect by rememberUpdatedState(onSelect)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = labelStyle)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                Modifier
                    .size(width = height * 0.46f, height = height)
                    .pointerInput(Unit) {
                        detectTapGestures { off ->
                            latestSelect((off.y / size.height * 3).toInt().coerceIn(0, 2))
                        }
                    },
            ) { drawToggle(selected) }
            Spacer(Modifier.size(4.dp))
            Column(Modifier.height(height), verticalArrangement = Arrangement.SpaceBetween) {
                options.forEachIndexed { i, o ->
                    Text(
                        o,
                        color = if (i == selected) FaceText else FaceTextDim.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { latestSelect(i) },
                    )
                }
            }
        }
    }
}

private val chrome = listOf(Color(0xFFF2F2F2), Color(0xFF8C8F94), Color(0xFFE0E0E0), Color(0xFF6B6E73), Color(0xFFF2F2F2))

private fun DrawScope.drawToggle(pos: Int) {
    val w = size.width
    val c = center
    // slot plate
    drawRoundRect(Color.Black.copy(alpha = 0.35f), cornerRadius = CornerRadius(w / 2), size = size)
    // nut
    drawCircle(Brush.sweepGradient(chrome, c), radius = w * 0.40f, center = c)
    drawCircle(Color(0xFF2A2B2E), radius = w * 0.26f, center = c)
    val tipY = when (pos) {
        0 -> size.height * 0.12f
        2 -> size.height * 0.88f
        else -> c.y
    }
    if (pos == 1) {
        // lever pointing straight at you
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF8A8D92)), center = c, radius = w * 0.24f), radius = w * 0.22f, center = c)
    } else {
        val tip = Offset(c.x, tipY)
        drawLine(Brush.linearGradient(listOf(Color(0xFFDADADA), Color(0xFF7B7E83)), start = Offset(c.x - w * 0.1f, 0f), end = Offset(c.x + w * 0.1f, 0f)), c, tip, strokeWidth = w * 0.20f, cap = StrokeCap.Round)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF9A9DA2)), center = tip, radius = w * 0.2f), radius = w * 0.17f, center = tip)
    }
}

// ------------------------------------------------------------------ footswitch & LED

@Composable
fun Led(color: Color, on: Boolean, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        if (on) {
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), center = center, radius = r), radius = r)
            drawCircle(Brush.radialGradient(listOf(Color.White, color), center = center, radius = r * 0.45f), radius = r * 0.42f)
        } else {
            drawCircle(color.copy(alpha = 0.18f), radius = r * 0.42f)
            drawCircle(Color.Black.copy(alpha = 0.4f), radius = r * 0.42f, style = Stroke(1f))
        }
    }
}

@Composable
fun Footswitch(
    label: String,
    ledColor: Color,
    ledOn: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    switchSize: Dp = 64.dp,
) {
    var pressed by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Led(ledColor, ledOn)
        Spacer(Modifier.height(4.dp))
        Text(label, style = labelStyle)
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .size(switchSize)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { tap() },
                        onLongPress = { long?.invoke() },
                    )
                },
        ) { drawFootswitch(pressed) }
    }
}

private fun DrawScope.drawFootswitch(pressed: Boolean) {
    val r = size.minDimension / 2
    val c = center
    drawCircle(Color.Black.copy(alpha = 0.5f), radius = r * 0.98f, center = c + Offset(r * 0.04f, r * 0.08f))
    // hex nut ring
    drawCircle(Brush.sweepGradient(chrome, c), radius = r * 0.96f, center = c)
    drawCircle(Color(0xFF1C1D20), radius = r * 0.78f, center = c)
    val k = if (pressed) 0.90f else 1f
    drawCircle(Brush.sweepGradient(listOf(Color(0xFFBFC2C6), Color(0xFFF7F7F7), Color(0xFF7D8085), Color(0xFFEDEDED), Color(0xFFBFC2C6)), c), radius = r * 0.70f * k, center = c)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFDFDFD), Color(0xFFA9ACB1)), center = c - Offset(r * 0.15f, r * 0.15f), radius = r * 0.6f), radius = r * 0.52f * k, center = c)
    drawCircle(Color.Black.copy(alpha = 0.18f), radius = r * 0.52f * k, center = c, style = Stroke(1.2f))
}
