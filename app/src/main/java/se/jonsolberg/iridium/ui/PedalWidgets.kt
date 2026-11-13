package se.jonsolberg.iridium.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Size
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

val FaceText = Color(0xFFF1EEE8)
val FaceTextDim = Color(0xFF9C9890)
val LedRed = Color(0xFFFF3B30)
val LedBlue = Color(0xFF3D8BFF)
val LedGreen = Color(0xFF34D058)

private val labelStyle = TextStyle(
    color = FaceText, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp,
)

/** Matte black enclosure top with a faint powder-coat grain. */
fun Modifier.brushedMetal(corner: Dp = 20.dp): Modifier = this.drawWithCache {
    val rnd = Random(7)
    val grain = List((size.width * size.height / 900f).toInt().coerceAtMost(4000)) {
        Triple(Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height), rnd.nextFloat() * 0.05f, rnd.nextBoolean())
    }
    val base = Brush.linearGradient(
        listOf(Color(0xFF242426), Color(0xFF1A1A1C), Color(0xFF141415)),
        start = Offset.Zero, end = Offset(size.width * 0.5f, size.height),
    )
    val sheen = Brush.radialGradient(
        listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
        center = Offset(size.width * 0.3f, size.height * 0.1f), radius = size.maxDimension * 0.8f,
    )
    val r = CornerRadius(corner.toPx())
    onDrawBehind {
        drawRoundRect(base, cornerRadius = r)
        grain.forEach { (o, a, light) ->
            drawCircle((if (light) Color.White else Color.Black).copy(alpha = a), radius = 1f, center = o)
        }
        drawRoundRect(sheen, cornerRadius = r)
        drawRoundRect(Color.White.copy(alpha = 0.07f), cornerRadius = r, style = Stroke(1.5f))
    }
}

// ------------------------------------------------------------------ knob

/**
 * Rotary knob, 0..127, label underneath like on the pedal.
 * Drag up/right to turn clockwise, double-tap to centre. The value shows while turning.
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
        Spacer(Modifier.height(4.dp))
        Text(label, style = labelStyle, maxLines = 1)
    }
}

private val aluminium = listOf(
    Color(0xFFE9EAEC), Color(0xFF9EA1A6), Color(0xFFF6F6F7), Color(0xFF8D9095),
    Color(0xFFE2E3E5), Color(0xFFA4A7AC), Color(0xFFE9EAEC),
)

private fun DrawScope.drawKnob(value: Int) {
    val r = size.minDimension / 2f
    val c = center
    // shadow
    drawCircle(Color.Black.copy(alpha = 0.55f), radius = r * 0.97f, center = c + Offset(r * 0.04f, r * 0.10f))
    // black skirt with fine knurling
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFF2A2A2C), Color(0xFF070708)), center = c, radius = r),
        radius = r * 0.97f, center = c,
    )
    val notches = 48
    for (i in 0 until notches) {
        val a = Math.toRadians(i * 360.0 / notches)
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        drawLine(
            Color(0xFF39393C), c + Offset(ca * r * 0.86f, sa * r * 0.86f),
            c + Offset(ca * r * 0.96f, sa * r * 0.96f), strokeWidth = r * 0.03f,
        )
    }
    // white pointer on the skirt
    val a = Math.toRadians((120f + 300f * value / 127f).toDouble())
    val ca = cos(a).toFloat()
    val sa = sin(a).toFloat()
    drawLine(
        Color(0xFFF7F5F0), c + Offset(ca * r * 0.66f, sa * r * 0.66f), c + Offset(ca * r * 0.95f, sa * r * 0.95f),
        strokeWidth = r * 0.07f, cap = StrokeCap.Round,
    )
    // spun-aluminium cap
    val cap = r * 0.60f
    drawCircle(Color.Black.copy(alpha = 0.6f), radius = cap * 1.04f, center = c)
    drawCircle(
        Brush.linearGradient(aluminium, start = c - Offset(cap, cap), end = c + Offset(cap, cap)),
        radius = cap, center = c,
    )
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), center = c - Offset(cap * 0.35f, cap * 0.35f), radius = cap),
        radius = cap, center = c,
    )
    for (k in 1..5) {
        drawCircle(Color.Black.copy(alpha = 0.05f), radius = cap * k / 6f, center = c, style = Stroke(1f))
    }
}

// ------------------------------------------------------------------ 3-way mini toggle

/**
 * Mini toggle switch with its title above and the position names beside it,
 * on the left ([labelsLeft]) or right, as printed on the pedal.
 */
@Composable
fun ThreeWayToggle(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    labelsLeft: Boolean = false,
    height: Dp = 60.dp,
) {
    val latestSelect by rememberUpdatedState(onSelect)
    val labels = @Composable {
        Column(
            Modifier.height(height),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = if (labelsLeft) Alignment.End else Alignment.Start,
        ) {
            options.forEachIndexed { i, o ->
                Text(
                    o,
                    color = if (i == selected) FaceText else FaceTextDim.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { latestSelect(i) },
                )
            }
        }
    }
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
        if (labelsLeft) {
            labels()
            Spacer(Modifier.size(6.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = labelStyle)
            Spacer(Modifier.height(2.dp))
            Canvas(
                Modifier
                    .size(width = height * 0.5f, height = height)
                    .pointerInput(Unit) {
                        detectTapGestures { off ->
                            latestSelect((off.y / size.height * 3).toInt().coerceIn(0, 2))
                        }
                    },
            ) { drawToggle(selected) }
        }
        if (!labelsLeft) {
            Spacer(Modifier.size(6.dp))
            labels()
        }
    }
}

private val chrome = listOf(Color(0xFFF2F2F2), Color(0xFF8C8F94), Color(0xFFE0E0E0), Color(0xFF6B6E73), Color(0xFFF2F2F2))

private fun DrawScope.drawToggle(pos: Int) {
    val w = size.width
    val c = center
    // hex nut
    drawCircle(Color.Black.copy(alpha = 0.5f), radius = w * 0.40f, center = c + Offset(0f, w * 0.05f))
    drawCircle(Brush.sweepGradient(chrome, c), radius = w * 0.36f, center = c)
    drawCircle(Color(0xFF2B2C2F), radius = w * 0.20f, center = c)
    val tipY = when (pos) {
        0 -> size.height * 0.16f
        2 -> size.height * 0.84f
        else -> c.y
    }
    if (pos == 1) {
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF8A8D92)), center = c, radius = w * 0.2f), radius = w * 0.17f, center = c)
    } else {
        val tip = Offset(c.x, tipY)
        drawLine(
            Brush.linearGradient(listOf(Color(0xFFE0E0E0), Color(0xFF7B7E83)), start = Offset(c.x - w * 0.08f, 0f), end = Offset(c.x + w * 0.08f, 0f)),
            c, tip, strokeWidth = w * 0.14f, cap = StrokeCap.Round,
        )
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF9A9DA2)), center = tip, radius = w * 0.14f), radius = w * 0.12f, center = tip)
    }
}

// ------------------------------------------------------------------ footswitch & LED

@Composable
fun Led(color: Color, on: Boolean, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        if (on) {
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.6f), Color.Transparent), center = center, radius = r), radius = r)
            drawCircle(Brush.radialGradient(listOf(Color.White, color), center = center, radius = r * 0.45f), radius = r * 0.40f)
        } else {
            drawCircle(color.copy(alpha = 0.22f), radius = r * 0.40f)
            drawCircle(Color.Black.copy(alpha = 0.4f), radius = r * 0.40f, style = Stroke(1f))
        }
    }
}

/** Label and LED above, chrome square-cap footswitch below, as on the pedal. */
@Composable
fun Footswitch(
    label: String,
    ledColor: Color,
    ledOn: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    switchSize: Dp = 64.dp,
    gap: Dp = 12.dp,
) {
    var pressed by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = labelStyle)
        Spacer(Modifier.height(4.dp))
        Led(ledColor, ledOn)
        Spacer(Modifier.height(gap))
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
    val s = size.minDimension
    val c = center
    // hex nut ring
    drawCircle(Color.Black.copy(alpha = 0.55f), radius = s * 0.47f, center = c + Offset(s * 0.02f, s * 0.05f))
    drawCircle(Brush.sweepGradient(chrome, c), radius = s * 0.45f, center = c)
    drawCircle(Color(0xFF1A1B1D), radius = s * 0.36f, center = c)
    // rounded-square chrome cap
    val k = if (pressed) 0.92f else 1f
    val side = s * 0.58f * k
    val tl = c - Offset(side / 2, side / 2)
    val rad = CornerRadius(side * 0.28f)
    drawRoundRect(Color.Black.copy(alpha = 0.5f), topLeft = tl + Offset(0f, s * 0.03f), size = Size(side, side), cornerRadius = rad)
    drawRoundRect(
        Brush.linearGradient(listOf(Color(0xFFFAFAFA), Color(0xFFB4B7BC), Color(0xFFE8E9EB), Color(0xFF8E9196)), start = tl, end = tl + Offset(side, side)),
        topLeft = tl, size = Size(side, side), cornerRadius = rad,
    )
    val inset = side * 0.14f
    drawRoundRect(
        Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFC3C6CA)), center = c - Offset(side * 0.15f, side * 0.15f), radius = side * 0.7f),
        topLeft = tl + Offset(inset, inset), size = Size(side - 2 * inset, side - 2 * inset), cornerRadius = CornerRadius(side * 0.2f),
    )
    drawRoundRect(Color.Black.copy(alpha = 0.2f), topLeft = tl, size = Size(side, side), cornerRadius = rad, style = Stroke(1.2f))
}
