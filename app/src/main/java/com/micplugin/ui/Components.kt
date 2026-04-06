package com.micplugin.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlin.math.*

// ─── VU Meter ────────────────────────────────────────────────────────────────
@Composable
fun VuMeterBar(
    levelDb: Float,
    label: String,
    modifier: Modifier = Modifier,
    isGainReduction: Boolean = false,
) {
    val animLevel by animateFloatAsState(
        targetValue = levelDb.coerceIn(-60f, 0f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "vu",
    )
    // Peak hold
    var peakDb by remember { mutableFloatStateOf(-100f) }
    var peakTimer by remember { mutableIntStateOf(0) }
    LaunchedEffect(levelDb) {
        if (levelDb > peakDb) { peakDb = levelDb; peakTimer = 0 }
        peakTimer++
        if (peakTimer > 40) { peakDb -= 0.5f }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = StudioColors.TextMuted,
            fontSize = 8.sp,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .weight(1f)
                .width(18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(StudioColors.Card)
                .drawBehind {
                    drawVuFill(animLevel, peakDb, isGainReduction)
                }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (animLevel > -60f) "${animLevel.toInt()} dB" else "-∞",
            style = MaterialTheme.typography.labelSmall,
            color = StudioColors.TextMuted,
            fontSize = 7.sp,
        )
    }
}

private fun DrawScope.drawVuFill(levelDb: Float, peakDb: Float, invert: Boolean) {
    val fraction = ((levelDb + 60f) / 60f).coerceIn(0f, 1f)
    val h = size.height

    if (!invert) {
        val fillH = h * fraction
        val y0    = h - fillH
        // Gradient: green → amber → red
        val colors = when {
            levelDb > -3f  -> listOf(StudioColors.MeterGreen, StudioColors.MeterAmber, StudioColors.MeterRed)
            levelDb > -12f -> listOf(StudioColors.MeterGreen, StudioColors.MeterAmber)
            else           -> listOf(StudioColors.MeterGreen)
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = colors.reversed(),
                startY = y0, endY = h,
            ),
            topLeft = Offset(0f, y0),
            size    = Size(size.width, fillH),
        )
        // Peak hold line
        val peakFrac = ((peakDb + 60f) / 60f).coerceIn(0f, 1f)
        val peakY    = h - h * peakFrac
        if (peakDb > -60f) {
            drawRect(
                color = StudioColors.MeterRed,
                topLeft = Offset(0f, peakY - 1.5f),
                size    = Size(size.width, 2.5f),
            )
        }
    } else {
        // GR meter — inverted orange bar from top
        val grFrac = (levelDb.coerceIn(0f, 30f) / 30f)
        val fillH  = h * grFrac
        drawRect(
            color = StudioColors.GainReduction,
            size  = Size(size.width, fillH),
        )
    }
}

// ─── Section header ───────────────────────────────────────────────────────────
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = StudioColors.TextMuted,
        letterSpacing = 2.sp,
        fontSize = 9.sp,
        modifier = modifier,
    )
}

// ─── Effect card ─────────────────────────────────────────────────────────────
@Composable
fun EffectCard(
    title: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    Surface(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(8.dp),
        color     = StudioColors.Card,
        border    = BorderStroke(1.dp, if (enabled) StudioColors.AccentDim else StudioColors.Border),
    ) {
        Column {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text  = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) StudioColors.TextPrimary else StudioColors.TextMuted,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked  = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor  = Color.White,
                        checkedTrackColor  = StudioColors.Accent,
                        uncheckedTrackColor = StudioColors.Border,
                    ),
                    modifier = Modifier.height(20.dp),
                )
            }
            if (expanded && enabled) {
                HorizontalDivider(color = StudioColors.Border, thickness = 0.5.dp)
                Column(
                    modifier = Modifier.padding(14.dp),
                    content  = content,
                )
            }
        }
    }
}

// ─── Labeled slider ──────────────────────────────────────────────────────────
@Composable
fun ParamSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String = "",
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    Column(modifier = modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted, fontSize = 10.sp)
            Text(
                text  = "%.1f%s".format(value, unit),
                style = MaterialTheme.typography.labelSmall,
                color = StudioColors.Accent,
                fontSize = 10.sp,
            )
        }
        Slider(
            value         = value,
            onValueChange = onValueChange,
            valueRange    = valueRange,
            steps         = steps,
            colors = SliderDefaults.colors(
                thumbColor       = StudioColors.Accent,
                activeTrackColor = StudioColors.Accent,
                inactiveTrackColor = StudioColors.Border,
            ),
            modifier = Modifier.height(32.dp),
        )
    }
}

// ─── EQ band vertical fader ──────────────────────────────────────────────────
@Composable
fun EqBandFader(
    freq: String,
    gainDb: Float,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range   = 15f
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var baseGain   by remember { mutableFloatStateOf(gainDb) }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "%.0f".format(gainDb),
            fontSize = 8.sp,
            color    = if (gainDb != 0f) StudioColors.Accent else StudioColors.TextMuted,
        )
        Box(
            Modifier
                .width(24.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(StudioColors.Card)
                .border(1.dp, StudioColors.Border, RoundedCornerShape(3.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { baseGain = gainDb },
                        onDragEnd   = {},
                        onVerticalDrag = { _, dy ->
                            val newGain = (baseGain - dy / 3f).coerceIn(-range, range)
                            baseGain = newGain
                            onGainChange(newGain)
                        },
                    )
                }
                .drawBehind {
                    val mid  = size.height / 2f
                    val frac = gainDb / range
                    val fillH = abs(frac) * mid
                    val y     = if (frac >= 0f) mid - fillH else mid
                    drawRect(
                        color   = if (gainDb > 0f) StudioColors.Accent else StudioColors.MeterAmber,
                        topLeft = Offset(4f, y),
                        size    = Size(size.width - 8f, fillH),
                    )
                    // Center line
                    drawRect(color = StudioColors.Border, topLeft = Offset(0f, mid - 0.5f),
                        size = Size(size.width, 1f))
                    // Thumb
                    val thumbY = mid - frac * mid
                    drawRect(
                        color   = StudioColors.TextPrimary,
                        topLeft = Offset(2f, thumbY - 3f),
                        size    = Size(size.width - 4f, 6f),
                    )
                }
        )
        Text(freq, fontSize = 7.sp, color = StudioColors.TextMuted, textAlign = TextAlign.Center)
    }
}

// ─── Plugin format badge ──────────────────────────────────────────────────────
@Composable
fun PluginFormatBadge(format: com.micplugin.plugin.PluginFormat, modifier: Modifier = Modifier) {
    val color = Color(format.colorHex)
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(4.dp),
        color    = color.copy(alpha = 0.15f),
        border   = BorderStroke(0.5.dp, color.copy(alpha = 0.5f)),
    ) {
        Text(
            text     = format.displayName,
            color    = color,
            fontSize = 8.sp,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            letterSpacing = 0.5.sp,
        )
    }
}

// ─── Rotary knob ─────────────────────────────────────────────────────────────
@Composable
fun KnobWidget(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    size: Dp = 48.dp,
    unit: String = "",
) {
    val startAngle = 225f
    val sweepAngle = 270f
    val frac = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    var lastY by remember { mutableFloatStateOf(0f) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .size(size)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset -> lastY = offset.y },
                        onDragEnd   = {},
                        onVerticalDrag = { _, dy ->
                            val delta = -dy / 200f
                            val range = valueRange.endInclusive - valueRange.start
                            val newVal = (value + delta * range)
                                .coerceIn(valueRange.start, valueRange.endInclusive)
                            onValueChange(newVal)
                        },
                    )
                },
        ) {
            val cx = size.toPx() / 2
            val r  = cx - 4.dp.toPx()

            // Track background
            drawArc(
                color     = StudioColors.Border,
                startAngle = startAngle, sweepAngle = sweepAngle,
                useCenter  = false,
                topLeft    = Offset(cx - r, cx - r), size = Size(r * 2, r * 2),
                style      = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(),
                    cap = StrokeCap.Round),
            )
            // Active arc
            drawArc(
                color      = StudioColors.Accent,
                startAngle = startAngle, sweepAngle = sweepAngle * frac,
                useCenter  = false,
                topLeft    = Offset(cx - r, cx - r), size = Size(r * 2, r * 2),
                style      = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(),
                    cap = StrokeCap.Round),
            )
            // Pointer line
            val angle = Math.toRadians((startAngle + sweepAngle * frac).toDouble())
            val x2    = cx + (r - 6.dp.toPx()) * cos(angle).toFloat()
            val y2    = cx + (r - 6.dp.toPx()) * sin(angle).toFloat()
            drawLine(
                color  = StudioColors.TextPrimary,
                start  = Offset(cx, cx),
                end    = Offset(x2, y2),
                strokeWidth = 2.dp.toPx(),
                cap         = StrokeCap.Round,
            )
        }
        Text(
            text = "%.1f%s".format(value, unit),
            fontSize = 8.sp,
            color = StudioColors.Accent,
        )
        Text(label, fontSize = 7.sp, color = StudioColors.TextMuted)
    }
}

// ─── Metering strip ──────────────────────────────────────────────────────────
@Composable
fun MeteringStrip(
    inputDb: Float,
    outputDb: Float,
    grDb: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(8.dp),
        color    = StudioColors.Card,
        border   = BorderStroke(1.dp, StudioColors.Border),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // IN meters (left)
            repeat(2) {
                VuMeterBar(inputDb, if (it == 0) "IN L" else "IN R",
                    modifier = Modifier.weight(1f).fillMaxHeight())
            }
            // GR meter
            VerticalDivider(color = StudioColors.Border)
            VuMeterBar(grDb, "GR", modifier = Modifier.weight(1f).fillMaxHeight(),
                isGainReduction = true)
            VerticalDivider(color = StudioColors.Border)
            // OUT meters (right)
            repeat(2) {
                VuMeterBar(outputDb, if (it == 0) "OUT L" else "OUT R",
                    modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}
