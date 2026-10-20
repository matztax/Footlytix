@file:OptIn(ExperimentalTextApi::class)

package de.tactics.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tactics.app.data.ExItem
import de.tactics.app.data.Scribble

private fun nearestItem(items: List<ExItem>, o: Offset, w: Float, h: Float, hit: Float): Int {
    var best = -1
    var bestDist = hit
    items.forEachIndexed { i, t ->
        val d = (Offset(t.x * w, t.y * h) - o).getDistance()
        if (d <= bestDist) { bestDist = d; best = i }
    }
    return best
}

/**
 * Übungsfeld: Material und Spieler per Finger verschieben (Modus "Verschieben")
 * oder Laufwege/Passwege einzeichnen (Modus "Zeichnen").
 */
@Composable
fun ExerciseBoard(
    items: List<ExItem>,
    scribbles: List<Scribble>,
    drawMode: Boolean,
    selected: Int,
    onItemsChange: (List<ExItem>) -> Unit,
    onScribblesChange: (List<Scribble>) -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemsNow by rememberUpdatedState(items)
    val scribblesNow by rememberUpdatedState(scribbles)
    val onItemsNow by rememberUpdatedState(onItemsChange)
    val onScribblesNow by rememberUpdatedState(onScribblesChange)
    val onSelectNow by rememberUpdatedState(onSelect)
    var dragIdx by remember { mutableIntStateOf(-1) }
    var current by remember { mutableStateOf<List<Float>>(emptyList()) }
    val measurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }

    val gestures: Modifier = if (drawMode) {
        Modifier.pointerInput("draw") {
            detectDragGestures(
                onDragStart = { o -> current = listOf(o.x / size.width, o.y / size.height) },
                onDrag = { change, _ ->
                    change.consume()
                    val p = change.position
                    current = current + listOf(p.x / size.width, p.y / size.height)
                },
                onDragEnd = {
                    if (current.size >= 4) onScribblesNow(scribblesNow + Scribble(current))
                    current = emptyList()
                },
                onDragCancel = { current = emptyList() }
            )
        }
    } else {
        Modifier
            .pointerInput("tap") {
                detectTapGestures(onTap = { o ->
                    onSelectNow(
                        nearestItem(itemsNow, o, size.width.toFloat(), size.height.toFloat(), 30.dp.toPx())
                    )
                })
            }
            .pointerInput("drag") {
                val hit = 30.dp.toPx()
                detectDragGestures(
                    onDragStart = { o ->
                        val i = nearestItem(itemsNow, o, size.width.toFloat(), size.height.toFloat(), hit)
                        dragIdx = i
                        if (i >= 0) onSelectNow(i)
                    },
                    onDragEnd = { dragIdx = -1 },
                    onDragCancel = { dragIdx = -1 },
                    onDrag = { change, delta ->
                        if (dragIdx >= 0 && dragIdx < itemsNow.size) {
                            change.consume()
                            val list = itemsNow.toMutableList()
                            val t = list[dragIdx]
                            list[dragIdx] = t.copy(
                                x = (t.x + delta.x / size.width).coerceIn(0f, 1f),
                                y = (t.y + delta.y / size.height).coerceIn(0f, 1f)
                            )
                            onItemsNow(list)
                        }
                    }
                )
            }
    }

    Canvas(modifier.clip(RoundedCornerShape(8.dp)).then(gestures)) {
        val w = size.width
        val h = size.height
        val stripes = 8
        for (i in 0 until stripes) {
            drawRect(
                if (i % 2 == 0) Color(0xFF2E7D32) else Color(0xFF2A7430),
                Offset(0f, h * i / stripes),
                Size(w, h / stripes + 1f)
            )
        }
        drawRect(Color.White.copy(alpha = 0.7f), Offset(6f, 6f), Size(w - 12f, h - 12f), style = Stroke(3f))

        scribbles.forEach { drawPoints(it.points) }
        drawPoints(current)

        items.forEachIndexed { i, t ->
            drawItem(t, Offset(t.x * w, t.y * h), i == selected, measurer, labelStyle)
        }
    }
}

private fun DrawScope.drawPoints(pts: List<Float>) {
    var i = 0
    while (i + 3 < pts.size) {
        drawLine(
            color = Color(0xFFFFEB3B),
            start = Offset(pts[i] * size.width, pts[i + 1] * size.height),
            end = Offset(pts[i + 2] * size.width, pts[i + 3] * size.height),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
        i += 2
    }
}

private fun DrawScope.drawItem(t: ExItem, c: Offset, selected: Boolean, tm: TextMeasurer, style: TextStyle) {
    val u = 1.dp.toPx()

    fun pt(dx: Float, dy: Float): Offset =
        if (t.rotated) Offset(c.x + dy, c.y + dx) else Offset(c.x + dx, c.y + dy)

    fun sz(w: Float, h: Float): Size = if (t.rotated) Size(h, w) else Size(w, h)

    when (t.type) {
        "cone" -> {
            val p = Path().apply {
                moveTo(c.x, c.y - 9 * u)
                lineTo(c.x - 8 * u, c.y + 7 * u)
                lineTo(c.x + 8 * u, c.y + 7 * u)
                close()
            }
            drawPath(p, Color(0xFFFF9800))
        }
        "pole" -> {
            // Stehende Slalomstange: Bodenschatten, Fuß, rot-weiße Abschnitte, Kappe
            val baseY = c.y + 13 * u
            val topY = c.y - 13 * u
            drawOval(Color.Black.copy(alpha = 0.35f), Offset(c.x - 8 * u, baseY - 3 * u), Size(16 * u, 6 * u))
            val seg = (baseY - topY) / 4f
            for (k in 0 until 4) {
                drawLine(
                    if (k % 2 == 0) Color(0xFFE53935) else Color.White,
                    Offset(c.x, baseY - k * seg),
                    Offset(c.x, baseY - (k + 1) * seg),
                    3.5f * u
                )
            }
            drawCircle(Color(0xFFE53935), 3 * u, Offset(c.x, topY))
            drawCircle(Color(0xFF455A64), 2.5f * u, Offset(c.x, baseY))
        }
        "wall" -> {
            for (k in -1..1) {
                val s = sz(12 * u, 22 * u)
                val ctr = pt(k * 14 * u, 0f)
                val tl = Offset(ctr.x - s.width / 2, ctr.y - s.height / 2)
                drawRect(Color(0xFF455A64), tl, s)
                drawRect(Color.White, tl, s, style = Stroke(1.5f * u))
            }
        }
        "ladder" -> {
            val s = sz(66 * u, 20 * u)
            val tl = Offset(c.x - s.width / 2, c.y - s.height / 2)
            drawRect(Color.White, tl, s, style = Stroke(2 * u))
            for (k in 1..5) {
                val off = -33 * u + k * 11 * u
                drawLine(Color.White, pt(off, -10 * u), pt(off, 10 * u), 2 * u)
            }
        }
        "hurdle" -> {
            drawLine(Color.White, pt(-14 * u, 0f), pt(14 * u, 0f), 3 * u)
            drawLine(Color.White, pt(-14 * u, 0f), pt(-14 * u, 9 * u), 3 * u)
            drawLine(Color.White, pt(14 * u, 0f), pt(14 * u, 9 * u), 3 * u)
        }
        "goal" -> {
            val s = sz(56 * u, 18 * u)
            val tl = Offset(c.x - s.width / 2, c.y - s.height / 2)
            drawRect(Color.White.copy(alpha = 0.25f), tl, s)
            drawRect(Color.White, tl, s, style = Stroke(3 * u))
        }
        "minigoal" -> {
            val s = sz(32 * u, 12 * u)
            val tl = Offset(c.x - s.width / 2, c.y - s.height / 2)
            drawRect(Color.White.copy(alpha = 0.25f), tl, s)
            drawRect(Color.White, tl, s, style = Stroke(2.5f * u))
        }
        "ball" -> {
            drawCircle(Color.White, 7 * u, c)
            drawCircle(Color.Black, 7 * u, c, style = Stroke(1.5f * u))
        }
        "player", "gk", "opp" -> {
            val col = when (t.type) {
                "opp" -> Color(0xFFD32F2F)
                "gk" -> Color(0xFFF9A825)
                else -> Color(0xFF1976D2)
            }
            drawCircle(col, 13 * u, c)
            drawCircle(Color.White, 13 * u, c, style = Stroke(1.5f * u))
            if (t.label.isNotBlank()) {
                val l = tm.measure(t.label, style)
                drawText(l, topLeft = Offset(c.x - l.size.width / 2f, c.y - l.size.height / 2f))
            }
        }
    }

    if (selected) {
        drawCircle(Color(0xFFFFEB3B), 24 * u, c, style = Stroke(2.5f * u))
    }
}
