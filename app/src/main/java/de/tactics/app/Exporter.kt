package de.tactics.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import de.tactics.app.data.Briefing
import de.tactics.app.data.ExItem
import de.tactics.app.data.Exercise
import de.tactics.app.data.Phase
import de.tactics.app.data.Player
import de.tactics.app.data.Scribble
import de.tactics.app.data.SessionEntry
import de.tactics.app.data.Token
import de.tactics.app.data.TrainingSession
import de.tactics.app.data.decodeItems
import de.tactics.app.data.decodeScribbles
import de.tactics.app.data.materialCounts
import de.tactics.app.data.materialText
import java.io.File
import java.io.FileOutputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Einfacher PDF-Schreiber (A4) mit Seitenumbruch für Text und feste Zeichenblöcke. */
class PdfWriter {
    val pageW = 595
    val pageH = 842
    val margin = 40f
    val contentW: Float get() = pageW - 2 * margin

    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var pageNo = 0
    private var y = 0f

    private fun startPage() {
        finishPage()
        pageNo++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        page = p
        canvas = p.canvas
        y = margin
    }

    private fun finishPage() {
        page?.let { doc.finishPage(it) }
        page = null
        canvas = null
    }

    fun newPage() = startPage()

    private fun ensure(h: Float) {
        if (canvas == null || y + h > pageH - margin) startPage()
    }

    fun text(t: String, size: Float = 12f, bold: Boolean = false, color: Int = Color.BLACK, after: Float = 6f) {
        if (t.isBlank()) {
            y += after
            return
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val layout = StaticLayout.Builder.obtain(t, 0, t.length, paint, contentW.toInt()).build()
        for (i in 0 until layout.lineCount) {
            val lh = (layout.getLineBottom(i) - layout.getLineTop(i)).toFloat()
            ensure(lh)
            val baseline = y + (layout.getLineBaseline(i) - layout.getLineTop(i))
            val start = layout.getLineStart(i)
            var end = layout.getLineEnd(i)
            while (end > start && t[end - 1] == '\n') end--
            canvas!!.drawText(t, start, end, margin, baseline, paint)
            y += lh
        }
        y += after
    }

    /** Reserviert [height] Punkte und ruft [draw] mit (canvas, links, oben, breite) auf. */
    fun block(height: Float, draw: (Canvas, Float, Float, Float) -> Unit) {
        ensure(height)
        draw(canvas!!, margin, y, contentW)
        y += height + 8f
    }

    fun save(file: File) {
        if (pageNo == 0) startPage()
        finishPage()
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }
}

object Exporter {
    private const val MINT = 0xFF5DE4AE.toInt()
    private const val CORAL = 0xFFE5484D.toInt()

    private fun dir(ctx: Context): File = File(ctx.cacheDir, "exports").apply {
        mkdirs()
        // Aus Datenschutzgründen alte Exporte (z. B. Aufstellungen mit Spielernamen) wieder entfernen
        val limit = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
    }

    private fun safe(name: String): String =
        name.replace(Regex("[^A-Za-z0-9ÄÖÜäöüß_-]+"), "_").trim('_').ifBlank { "Futlytix" }

    /** Führt einen Export aus und zeigt bei einem Fehler eine Meldung statt abzustürzen. */
    fun attempt(ctx: Context, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            android.widget.Toast.makeText(ctx, "Export fehlgeschlagen: " + (e.message ?: "unbekannter Fehler"), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun share(ctx: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title)
        if (ctx !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }

    // ------------------------------------------------------------ Spielfeld (nativ)
    fun drawPitch(c: Canvas, left: Float, top: Float, w: Float, h: Float, tokens: List<Token>, arrows: List<Scribble>, byId: Map<Long, Player>) {
        val grass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(left, top, left, top + h, 0xFF1D6B52.toInt(), 0xFF14503E.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(RectF(left, top, left + w, top + h), w * 0.03f, w * 0.03f, grass)

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x8CFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = w / 180f
        }
        val m = w * 0.03f
        c.drawRect(left + m, top + m, left + w - m, top + h - m, line)
        c.drawLine(left + m, top + h / 2, left + w - m, top + h / 2, line)
        c.drawCircle(left + w / 2, top + h / 2, h * 0.09f, line)
        val bw = w * 0.58f
        val bh = h * 0.15f
        val gw = w * 0.26f
        val gh = h * 0.055f
        c.drawRect(left + (w - bw) / 2, top + m, left + (w + bw) / 2, top + m + bh, line)
        c.drawRect(left + (w - bw) / 2, top + h - m - bh, left + (w + bw) / 2, top + h - m, line)
        c.drawRect(left + (w - gw) / 2, top + m, left + (w + gw) / 2, top + m + gh, line)
        c.drawRect(left + (w - gw) / 2, top + h - m - gh, left + (w + gw) / 2, top + h - m, line)

        // Pfeile
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = w / 150f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            pathEffect = DashPathEffect(floatArrayOf(w / 40f, w / 55f), 0f)
        }
        val headPaint = Paint(arrowPaint).apply { pathEffect = null }
        arrows.forEach { a ->
            val pts = a.points
            if (pts.size >= 4) {
                val path = Path()
                path.moveTo(left + pts[0] * w, top + pts[1] * h)
                var i = 2
                while (i + 1 < pts.size) {
                    path.lineTo(left + pts[i] * w, top + pts[i + 1] * h)
                    i += 2
                }
                c.drawPath(path, arrowPaint)
                val ex = left + pts[pts.size - 2] * w
                val ey = top + pts[pts.size - 1] * h
                // Richtung aus einem Punkt etwas vor dem Ende
                var k = pts.size - 4
                var sx = left + pts[k] * w
                var sy = top + pts[k + 1] * h
                while (k > 0 && (ex - sx) * (ex - sx) + (ey - sy) * (ey - sy) < (w / 30f) * (w / 30f)) {
                    k -= 2
                    sx = left + pts[k] * w
                    sy = top + pts[k + 1] * h
                }
                val ang = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                val len = w / 28f
                for (d in doubleArrayOf(2.6, -2.6)) {
                    c.drawLine(ex, ey, ex + (len * cos(ang + d)).toFloat(), ey + (len * sin(ang + d)).toFloat(), headPaint)
                }
            }
        }

        // Spieler
        val r = w * 0.045f
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = w / 250f }
        val num = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = r * 1.0f
        }
        val nameP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
            textSize = r * 0.62f
        }
        val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC0B1826.toInt() }
        tokens.forEach { t ->
            val cx = left + t.x * w
            val cy = top + t.y * h
            val pl = if (t.playerId >= 0) byId[t.playerId] else null
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when {
                    t.opponent -> CORAL
                    pl != null -> MINT
                    else -> 0x405DE4AE
                }
            }
            c.drawCircle(cx, cy + r * 0.12f, r, shadow)
            c.drawCircle(cx, cy, r, fill)
            c.drawCircle(cx, cy, r, ring)
            num.color = if (t.opponent) Color.WHITE else 0xFF062A1D.toInt()
            val label = pl?.number?.toString() ?: t.label
            c.drawText(label, cx, cy + r * 0.35f, num)
            if (pl != null) {
                val nm = pl.name.substringAfterLast(' ').take(12)
                val tw = nameP.measureText(nm)
                val ph = r * 0.9f
                val top2 = cy + r + r * 0.15f
                c.drawRoundRect(RectF(cx - tw / 2 - r * 0.3f, top2, cx + tw / 2 + r * 0.3f, top2 + ph), ph / 2, ph / 2, pill)
                c.drawText(nm, cx, top2 + ph * 0.72f, nameP)
            }
        }
    }

    // ------------------------------------------------------------ Übungsfeld (nativ)
    fun drawExercise(c: Canvas, left: Float, top: Float, w: Float, h: Float, items: List<ExItem>, scribbles: List<Scribble>) {
        val clip = c.save()
        c.clipRect(left, top, left + w, top + h)
        val stripes = 8
        val sp = Paint()
        for (i in 0 until stripes) {
            sp.color = if (i % 2 == 0) 0xFF2E7D32.toInt() else 0xFF2A7430.toInt()
            c.drawRect(left, top + h * i / stripes, left + w, top + h * (i + 1) / stripes + 1f, sp)
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB3FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = w / 200f
        }
        c.drawRect(left + 6f, top + 6f, left + w - 6f, top + h - 6f, border)

        val u = w / 400f
        val sc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFEB3B.toInt(); style = Paint.Style.STROKE; strokeWidth = 2.5f * u
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        scribbles.forEach { s ->
            val pts = s.points
            if (pts.size >= 4) {
                val path = Path()
                path.moveTo(left + pts[0] * w, top + pts[1] * h)
                var i = 2
                while (i + 1 < pts.size) {
                    path.lineTo(left + pts[i] * w, top + pts[i + 1] * h)
                    i += 2
                }
                c.drawPath(path, sc)
            }
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE; textSize = 8f * u
        }
        items.forEach { t ->
            val cx = left + t.x * w
            val cy = top + t.y * h
            val save = c.save()
            if (t.rotated) c.rotate(90f, cx, cy)
            when (t.type) {
                "cone" -> {
                    val p = Path()
                    p.moveTo(cx, cy - 9 * u); p.lineTo(cx - 8 * u, cy + 7 * u); p.lineTo(cx + 8 * u, cy + 7 * u); p.close()
                    fill.color = 0xFFFF9800.toInt()
                    c.drawPath(p, fill)
                }
                "pole" -> {
                    c.restoreToCount(save)
                    c.save()
                    val baseY = cy + 13 * u
                    val topY = cy - 13 * u
                    fill.color = 0x59000000
                    c.drawOval(RectF(cx - 8 * u, baseY - 3 * u, cx + 8 * u, baseY + 3 * u), fill)
                    val seg = (baseY - topY) / 4f
                    stroke.strokeWidth = 3.5f * u
                    for (k in 0 until 4) {
                        stroke.color = if (k % 2 == 0) 0xFFE53935.toInt() else Color.WHITE
                        c.drawLine(cx, baseY - k * seg, cx, baseY - (k + 1) * seg, stroke)
                    }
                    fill.color = 0xFFE53935.toInt()
                    c.drawCircle(cx, topY, 3 * u, fill)
                    fill.color = 0xFF455A64.toInt()
                    c.drawCircle(cx, baseY, 2.5f * u, fill)
                }
                "wall" -> {
                    for (k in -1..1) {
                        val rect = RectF(cx + k * 14 * u - 6 * u, cy - 11 * u, cx + k * 14 * u + 6 * u, cy + 11 * u)
                        fill.color = 0xFF455A64.toInt()
                        c.drawRect(rect, fill)
                        stroke.color = Color.WHITE; stroke.strokeWidth = 1.5f * u
                        c.drawRect(rect, stroke)
                    }
                }
                "ladder" -> {
                    stroke.color = Color.WHITE; stroke.strokeWidth = 2 * u
                    c.drawRect(cx - 33 * u, cy - 10 * u, cx + 33 * u, cy + 10 * u, stroke)
                    for (k in 1..5) {
                        val x = cx - 33 * u + k * 11 * u
                        c.drawLine(x, cy - 10 * u, x, cy + 10 * u, stroke)
                    }
                }
                "hurdle" -> {
                    stroke.color = Color.WHITE; stroke.strokeWidth = 3 * u
                    c.drawLine(cx - 14 * u, cy, cx + 14 * u, cy, stroke)
                    c.drawLine(cx - 14 * u, cy, cx - 14 * u, cy + 9 * u, stroke)
                    c.drawLine(cx + 14 * u, cy, cx + 14 * u, cy + 9 * u, stroke)
                }
                "goal", "minigoal" -> {
                    val gw = if (t.type == "goal") 56 * u else 32 * u
                    val gh = if (t.type == "goal") 18 * u else 12 * u
                    fill.color = 0x40FFFFFF
                    c.drawRect(cx - gw / 2, cy - gh / 2, cx + gw / 2, cy + gh / 2, fill)
                    stroke.color = Color.WHITE; stroke.strokeWidth = 3 * u
                    c.drawRect(cx - gw / 2, cy - gh / 2, cx + gw / 2, cy + gh / 2, stroke)
                }
                "ball" -> {
                    fill.color = Color.WHITE
                    c.drawCircle(cx, cy, 7 * u, fill)
                    stroke.color = Color.BLACK; stroke.strokeWidth = 1.5f * u
                    c.drawCircle(cx, cy, 7 * u, stroke)
                }
                "player", "gk", "opp" -> {
                    fill.color = when (t.type) {
                        "opp" -> 0xFFD32F2F.toInt()
                        "gk" -> 0xFFF9A825.toInt()
                        else -> 0xFF1976D2.toInt()
                    }
                    c.drawCircle(cx, cy, 13 * u, fill)
                    stroke.color = Color.WHITE; stroke.strokeWidth = 1.5f * u
                    c.drawCircle(cx, cy, 13 * u, stroke)
                    if (t.label.isNotBlank()) c.drawText(t.label, cx, cy + 3 * u, text)
                }
            }
            c.restoreToCount(save)
        }
        c.restoreToCount(clip)
    }

    // ------------------------------------------------------------ Aufstellung
    fun formationPdf(ctx: Context, title: String, subtitle: String, phases: List<Phase>, byId: Map<Long, Player>): File {
        val w = PdfWriter()
        phases.forEachIndexed { i, ph ->
            if (i > 0) w.newPage()
            w.text(title, 18f, bold = true)
            if (subtitle.isNotBlank()) w.text(subtitle, 11f, color = Color.DKGRAY)
            w.text(ph.name, 14f, bold = true, after = 8f)
            w.block(540f) { c, l, t, cw ->
                val pw = 380f
                drawPitch(c, l + (cw - pw) / 2, t, pw, 540f, ph.tokens, ph.arrows, byId)
            }
            fun lineup(opponent: Boolean): String =
                ph.tokens.filter { it.opponent == opponent && it.playerId >= 0 }
                    .mapNotNull { byId[it.playerId] }
                    .sortedBy { it.number }
                    .joinToString(", ") { "${it.number} ${it.name}" }
            val own = lineup(false)
            val opp = lineup(true)
            if (own.isNotBlank()) w.text("Eigene Aufstellung: $own", 10f)
            if (opp.isNotBlank()) w.text("Gegner: $opp", 10f)
        }
        val f = File(dir(ctx), safe(title) + ".pdf")
        w.save(f)
        return f
    }

    fun formationPng(ctx: Context, title: String, phase: Phase, byId: Map<Long, Player>): File {
        val bw = 900
        val bh = 1300
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 40f; typeface = Typeface.DEFAULT_BOLD }
        c.drawText("$title – ${phase.name}", 40f, 70f, tp)
        drawPitch(c, 40f, 100f, bw - 80f, bh - 140f, phase.tokens, phase.arrows, byId)
        val f = File(dir(ctx), safe(title + "_" + phase.name) + ".png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    // ------------------------------------------------------------ Übungen & Trainingseinheiten
    private fun writeExercise(w: PdfWriter, ex: Exercise, minutes: Int?) {
        w.text(ex.name, 18f, bold = true)
        val meta = ex.category + " · " + (minutes ?: ex.durationMin) + " Min" +
            (if (ex.playersCount > 0) " · ${ex.playersCount} Spieler" else "")
        w.text(meta, 11f, color = Color.DKGRAY, after = 8f)
        val items = decodeItems(ex.itemsJson)
        val scribbles = decodeScribbles(ex.scribblesJson)
        w.block(400f) { c, l, t, cw -> drawExercise(c, l, t, cw, 390f, items, scribbles) }
        val mat = listOf(materialText(materialCounts(items)), ex.extraMaterial).filter { it.isNotBlank() }.joinToString(", ")
        if (mat.isNotBlank()) w.text("Material: $mat", 11f, bold = true)
        if (ex.description.isNotBlank()) w.text(ex.description, 11f)
    }

    fun exercisePdf(ctx: Context, ex: Exercise): File {
        val w = PdfWriter()
        writeExercise(w, ex, null)
        val f = File(dir(ctx), safe(ex.name) + ".pdf")
        w.save(f)
        return f
    }

    fun exercisePng(ctx: Context, ex: Exercise): File {
        val bw = 1200
        val bh = 1150
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 46f; typeface = Typeface.DEFAULT_BOLD }
        c.drawText(ex.name, 40f, 70f, tp)
        val items = decodeItems(ex.itemsJson)
        drawExercise(c, 40f, 100f, bw - 80f, 900f, items, decodeScribbles(ex.scribblesJson))
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 32f }
        val mat = listOf(materialText(materialCounts(items)), ex.extraMaterial).filter { it.isNotBlank() }.joinToString(", ")
        c.drawText(ex.category + " · " + ex.durationMin + " Min", 40f, 1050f, sub)
        if (mat.isNotBlank()) c.drawText("Material: $mat".take(70), 40f, 1100f, sub)
        val f = File(dir(ctx), safe(ex.name) + ".png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    fun sessionPdf(
        ctx: Context,
        session: TrainingSession,
        entries: List<SessionEntry>,
        exercises: List<Exercise>,
        material: String
    ): File {
        val w = PdfWriter()
        w.text(session.title, 20f, bold = true)
        val total = entries.sumOf { it.minutes }
        w.text(listOf(session.date, "$total Min").filter { it.isNotBlank() }.joinToString(" · "), 12f, color = Color.DKGRAY)
        if (session.goal.isNotBlank()) w.text("Ziel: ${session.goal}", 12f)
        w.text("Ablauf", 14f, bold = true)
        entries.forEachIndexed { i, e ->
            val ex = exercises.firstOrNull { it.id == e.exerciseId }
            w.text("${i + 1}. ${ex?.name ?: "Übung"} (${e.minutes} Min)", 12f, after = 2f)
        }
        if (material.isNotBlank()) w.text("\nMaterial: $material", 11f)
        if (session.notes.isNotBlank()) w.text("\nNotizen: ${session.notes}", 11f)
        entries.forEach { e ->
            val ex = exercises.firstOrNull { it.id == e.exerciseId }
            if (ex != null) {
                w.newPage()
                writeExercise(w, ex, e.minutes)
            }
        }
        val f = File(dir(ctx), safe(session.title) + ".pdf")
        w.save(f)
        return f
    }

    fun briefingPdf(ctx: Context, b: Briefing): File {
        val w = PdfWriter()
        w.text(b.title, 20f, bold = true, after = 10f)
        w.text(b.text, 12f)
        val f = File(dir(ctx), safe(b.title) + ".pdf")
        w.save(f)
        return f
    }
}
