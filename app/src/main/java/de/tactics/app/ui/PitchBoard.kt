@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)

package de.tactics.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tactics.app.Exporter
import de.tactics.app.MainViewModel
import de.tactics.app.data.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------- Spielfeld
private fun isHoriz(w: Float, h: Float): Boolean = w > h * 1.2f

/** Normierte Position (Hochformat, eigenes Tor unten) -> Bildschirmposition; im Querformat gedreht. */
private fun toScreen(x: Float, y: Float, w: Float, h: Float): Offset =
    if (isHoriz(w, h)) Offset((1f - y) * w, x * h) else Offset(x * w, y * h)

private fun toNorm(o: Offset, w: Float, h: Float): Offset =
    if (isHoriz(w, h)) Offset(o.y / h, 1f - o.x / w) else Offset(o.x / w, o.y / h)

private fun nearestToken(tokens: List<Token>, o: Offset, w: Float, h: Float, hit: Float): Int {
    var best = -1
    var bestDist = hit
    tokens.forEachIndexed { i, t ->
        val d = (toScreen(t.x, t.y, w, h) - o).getDistance()
        if (d < bestDist) { bestDist = d; best = i }
    }
    return best
}

private fun DrawScope.drawPitchMarkings(vw: Float, vh: Float, u: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1D6B52), Color(0xFF14503E))), size = Size(vw, vh))
    val stripes = 10
    for (i in 0 until stripes step 2) {
        drawRect(Color.White.copy(alpha = 0.035f), Offset(0f, vh * i / stripes), Size(vw, vh / stripes))
    }
    val line = Color.White.copy(alpha = 0.55f)
    val sw = 1.8f * u
    val m = 10 * u
    drawRoundRect(line, Offset(m, m), Size(vw - 2 * m, vh - 2 * m), CornerRadius(6 * u), style = Stroke(sw))
    drawLine(line, Offset(m, vh / 2), Offset(vw - m, vh / 2), sw)
    val mid = Offset(vw / 2, vh / 2)
    drawCircle(line, vh * 0.09f, mid, style = Stroke(sw))
    drawCircle(line, 2.5f * u, mid)
    val bw = vw * 0.58f
    val bh = vh * 0.15f
    val gw = vw * 0.26f
    val gh = vh * 0.055f
    drawRect(line, Offset((vw - bw) / 2, m), Size(bw, bh), style = Stroke(sw))
    drawRect(line, Offset((vw - bw) / 2, vh - m - bh), Size(bw, bh), style = Stroke(sw))
    drawRect(line, Offset((vw - gw) / 2, m), Size(gw, gh), style = Stroke(sw))
    drawRect(line, Offset((vw - gw) / 2, vh - m - gh), Size(gw, gh), style = Stroke(sw))
    drawCircle(line, 2f * u, Offset(vw / 2, m + bh * 0.72f))
    drawCircle(line, 2f * u, Offset(vw / 2, vh - m - bh * 0.72f))
}

/**
 * Taktikfeld: Figuren per Finger verschieben, Tippen wählt Spieler bzw. Beschriftung,
 * im Pfeil-Modus werden Laufwege gezeichnet. Im Querformat wird das Feld gedreht.
 */
@Composable
fun PitchBoard(
    tokens: List<Token>,
    arrows: List<Scribble>,
    players: List<Player>,
    arrowMode: Boolean,
    onChange: (List<Token>) -> Unit,
    onArrowsChange: (List<Scribble>) -> Unit,
    onTokenTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var local by remember(tokens) { mutableStateOf(tokens) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var current by remember { mutableStateOf<List<Float>>(emptyList()) }
    val measurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    val nameStyle = remember { TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
    val onChangeLatest by rememberUpdatedState(onChange)
    val onTapLatest by rememberUpdatedState(onTokenTap)
    val onArrowsLatest by rememberUpdatedState(onArrowsChange)
    val arrowsLatest by rememberUpdatedState(arrows)
    val byId = remember(players) { players.associateBy { it.id } }

    val gestures: Modifier = if (arrowMode) {
        Modifier.pointerInput("arrows") {
            detectDragGestures(
                onDragStart = { o ->
                    val n = toNorm(o, size.width.toFloat(), size.height.toFloat())
                    current = listOf(n.x, n.y)
                },
                onDrag = { change, _ ->
                    change.consume()
                    val n = toNorm(change.position, size.width.toFloat(), size.height.toFloat())
                    current = current + listOf(n.x, n.y)
                },
                onDragEnd = {
                    if (current.size >= 4) onArrowsLatest(arrowsLatest + Scribble(current))
                    current = emptyList()
                },
                onDragCancel = { current = emptyList() }
            )
        }
    } else {
        Modifier
            .pointerInput(tokens) {
                detectTapGestures(onTap = { o ->
                    val i = nearestToken(local, o, size.width.toFloat(), size.height.toFloat(), 30.dp.toPx())
                    if (i >= 0) onTapLatest(i)
                })
            }
            .pointerInput(tokens) {
                val hit = 36.dp.toPx()
                detectDragGestures(
                    onDragStart = { o ->
                        dragIndex = nearestToken(local, o, size.width.toFloat(), size.height.toFloat(), hit)
                    },
                    onDragEnd = {
                        if (dragIndex >= 0) onChangeLatest(local)
                        dragIndex = -1
                    },
                    onDragCancel = { dragIndex = -1 },
                    onDrag = { change, delta ->
                        if (dragIndex >= 0) {
                            change.consume()
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val t = local[dragIndex]
                            val n = toNorm(toScreen(t.x, t.y, w, h) + delta, w, h)
                            local = local.toMutableList().also {
                                it[dragIndex] = t.copy(x = n.x.coerceIn(0f, 1f), y = n.y.coerceIn(0f, 1f))
                            }
                        }
                    }
                )
            }
    }

    Canvas(modifier.clip(RoundedCornerShape(8.dp)).then(gestures)) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()

        if (isHoriz(w, h)) {
            withTransform({
                translate(left = w, top = 0f)
                rotate(degrees = 90f, pivot = Offset.Zero)
            }) { drawPitchMarkings(h, w, u) }
        } else {
            drawPitchMarkings(w, h, u)
        }

        // Laufwege als gestrichelte Pfeile
        fun drawArrow(pts: List<Float>) {
            if (pts.size < 4) return
            val col = Color.White.copy(alpha = 0.95f)
            val path = Path()
            val p0 = toScreen(pts[0], pts[1], w, h)
            path.moveTo(p0.x, p0.y)
            var i = 2
            while (i + 1 < pts.size) {
                val p = toScreen(pts[i], pts[i + 1], w, h)
                path.lineTo(p.x, p.y)
                i += 2
            }
            drawPath(
                path, col,
                style = Stroke(
                    2.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(9 * u, 6 * u))
                )
            )
            val end = toScreen(pts[pts.size - 2], pts[pts.size - 1], w, h)
            var k = pts.size - 4
            var from = toScreen(pts[k], pts[k + 1], w, h)
            while (k > 0 && (end - from).getDistance() < 12 * u) {
                k -= 2
                from = toScreen(pts[k], pts[k + 1], w, h)
            }
            val ang = atan2((end.y - from.y).toDouble(), (end.x - from.x).toDouble())
            val len = 12 * u
            for (d in doubleArrayOf(2.65, -2.65)) {
                drawLine(
                    col, end,
                    Offset(end.x + (len * cos(ang + d)).toFloat(), end.y + (len * sin(ang + d)).toFloat()),
                    2.4f * u, StrokeCap.Round
                )
            }
        }
        arrows.forEach { drawArrow(it.points) }
        drawArrow(current)

        // Spielerkreise im Corporate-Look: Mint (eigenes Team), Koralle (Gegner)
        val r = 17 * u
        local.forEachIndexed { i, t ->
            val c = toScreen(t.x, t.y, w, h)
            val active = i == dragIndex
            val rr = if (active) r * 1.15f else r
            val pl = if (t.playerId >= 0) byId[t.playerId] else null
            val hl = Offset(c.x - rr * 0.3f, c.y - rr * 0.35f)

            drawCircle(Color.Black.copy(alpha = if (active) 0.38f else 0.26f), rr + 1.5f * u, c + Offset(0f, 2.5f * u))

            val fill: Brush = when {
                t.opponent -> Brush.radialGradient(listOf(Color(0xFFFF9A94), Color(0xFFE5484D)), center = hl, radius = rr * 1.7f)
                pl != null -> Brush.radialGradient(listOf(Color(0xFF9AF5D6), Color(0xFF2FBF8F)), center = hl, radius = rr * 1.7f)
                else -> SolidColor(Color(0xFF5DE4AE).copy(alpha = 0.16f))
            }
            val textColor = when {
                t.opponent -> Color.White
                pl != null -> Color(0xFF062A1D)
                else -> Color(0xFFC9F7E6)
            }
            drawCircle(fill, rr, c)
            if (pl == null && !t.opponent) {
                drawCircle(
                    Color(0xFF5DE4AE).copy(alpha = 0.9f), rr, c,
                    style = Stroke(1.6f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5 * u, 4 * u)))
                )
            } else {
                drawCircle(Color.White.copy(alpha = 0.9f), rr, c, style = Stroke(1.6f * u))
            }

            val label = pl?.number?.toString() ?: t.label
            val layout = measurer.measure(label, labelStyle.copy(color = textColor))
            drawText(layout, topLeft = c - Offset(layout.size.width / 2f, layout.size.height / 2f))

            if (pl != null) {
                val nm = measurer.measure(pl.name.substringAfterLast(' ').take(12), nameStyle)
                val pw = nm.size.width + 10 * u
                val ph = nm.size.height + 3 * u
                val x = (c.x - pw / 2).coerceIn(2 * u, (w - pw - 2 * u).coerceAtLeast(2 * u))
                val y = (c.y + rr + 3 * u).coerceAtMost(h - ph - 2 * u)
                drawRoundRect(Color(0xFF0B1826).copy(alpha = 0.8f), Offset(x, y), Size(pw, ph), CornerRadius(ph / 2))
                drawText(nm, topLeft = Offset(x + 5 * u, y + 1.5f * u))
            }
        }
    }
}
