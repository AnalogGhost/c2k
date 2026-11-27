package com.hackerapps.c2k.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.hackerapps.c2k.R
import com.hackerapps.c2k.ui.programNameRes
import com.hackerapps.c2k.ui.screen.history.formatDuration
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Draws the share image: 1080 × 1920, story-sized, all on the device. The layout matches the iOS
 * app's, which is set out in points at 3×, so sizes here are those points times [S].
 */
object ShareCardRenderer {
    const val WIDTH = 1080
    const val HEIGHT = 1920
    private const val S = 3f
    private const val DIR = "share"

    val gradients: List<IntArray> = listOf(
        intArrayOf(0xFFFF6B35.toInt(), Color.rgb(140, 31, 51)),
        intArrayOf(0xFF4A90D9.toInt(), Color.rgb(20, 33, 89)),
        intArrayOf(0xFF7BC67E.toInt(), Color.rgb(10, 77, 82)),
    )

    fun photoRes(number: Int): Int = when (number) {
        1 -> R.drawable.share_bg_01
        2 -> R.drawable.share_bg_02
        3 -> R.drawable.share_bg_03
        4 -> R.drawable.share_bg_04
        5 -> R.drawable.share_bg_05
        6 -> R.drawable.share_bg_06
        7 -> R.drawable.share_bg_07
        8 -> R.drawable.share_bg_08
        9 -> R.drawable.share_bg_09
        10 -> R.drawable.share_bg_10
        11 -> R.drawable.share_bg_11
        12 -> R.drawable.share_bg_12
        else -> R.drawable.share_bg_13
    }

    /** Call off the main thread: it decodes a photo. */
    fun render(
        context: Context,
        stats: ShareCardStats,
        route: List<OutlinePoint>,
        background: ShareBackground,
        customPhoto: Bitmap?
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackdrop(context, canvas, background, customPhoto)
        drawContent(context, canvas, stats, route, sticker = background == ShareBackground.Sticker)
        return bitmap
    }

    private fun drawBackdrop(context: Context, canvas: Canvas, background: ShareBackground, customPhoto: Bitmap?) {
        when (background) {
            ShareBackground.Sticker -> Unit
            is ShareBackground.Gradient -> {
                val colors = gradients[background.number % gradients.size]
                val paint = Paint().apply {
                    shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), colors[0], colors[1], Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
            }
            is ShareBackground.Photo, ShareBackground.Custom -> {
                val photo = if (background is ShareBackground.Photo) {
                    BitmapFactory.decodeResource(context.resources, photoRes(background.number),
                        BitmapFactory.Options().apply { inScaled = false })
                } else {
                    customPhoto
                }
                if (photo != null) drawCentreCropped(canvas, photo)
                // Darkens the lower half so the white text reads on any photo.
                val shade = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, HEIGHT.toFloat(),
                        intArrayOf(Color.argb(64, 0, 0, 0), Color.TRANSPARENT, Color.argb(191, 0, 0, 0)),
                        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), shade)
            }
        }
    }

    private fun drawCentreCropped(canvas: Canvas, photo: Bitmap) {
        val scale = maxOf(WIDTH.toFloat() / photo.width, HEIGHT.toFloat() / photo.height)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate((WIDTH - photo.width * scale) / 2, (HEIGHT - photo.height * scale) / 2)
        }
        canvas.drawBitmap(photo, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawContent(context: Context, canvas: Canvas, stats: ShareCardStats, route: List<OutlinePoint>, sticker: Boolean) {
        val shadow = if (sticker) Color.argb(179, 0, 0, 0) else Color.argb(89, 0, 0, 0)
        fun paint(sizePt: Float, bold: Boolean, alpha: Float = 1f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.alpha = (alpha * 255).toInt()
            textSize = sizePt * S
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            setShadowLayer(4 * S, 0f, 0f, shadow)
        }
        val left = 28 * S
        val textWidth = (WIDTH - 2 * left).toInt()

        // Wordmark, top left.
        val mark = paint(24f, bold = true)
        canvas.drawText("C2K", left, left - mark.ascent(), mark)

        // Everything else stacks up from the bottom.
        var bottom = HEIGHT - left

        val footer = paint(12f, bold = false, alpha = 0.75f)
        canvas.drawText("hackerapps.com/apps/c2k", left, bottom - footer.descent(), footer)
        bottom -= footer.textSize + 16 * S

        val value = paint(24f, bold = true)
        val label = paint(11f, bold = true, alpha = 0.8f)
        val statTop = bottom - label.textSize - 2 * S - value.textSize
        var x = left
        for ((v, l) in statItems(context, stats)) {
            canvas.drawText(v, x, statTop - value.ascent(), value)
            canvas.drawText(l.uppercase(), x, bottom - label.descent(), label)
            x += maxOf(value.measureText(v), label.measureText(l.uppercase())) + 26 * S
        }
        bottom = statTop - 16 * S

        val subtitle = paint(17f, bold = true, alpha = 0.9f)
        canvas.drawText(subtitle(context, stats), left, bottom - subtitle.descent(), subtitle)
        bottom -= subtitle.textSize + 4 * S

        val headlinePaint = paint(30f, bold = true)
        val headlineText = headline(context, stats)
        val headline = StaticLayout.Builder.obtain(headlineText, 0, headlineText.length, headlinePaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(3).build()
        bottom -= headline.height
        canvas.save()
        canvas.translate(left, bottom)
        headline.draw(canvas)
        canvas.restore()

        if (route.size > 1) {
            val side = 140 * S
            val top = bottom - 16 * S - side
            val path = Path()
            route.forEachIndexed { i, p ->
                val px = left + (p.x * side).toFloat()
                val py = top + (p.y * side).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.WHITE
                strokeWidth = 4 * S
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                setShadowLayer(4 * S, 0f, 0f, shadow)
            })
        }
    }

    private fun programName(context: Context, stats: ShareCardStats) =
        programNameRes(stats.programId)?.let { context.getString(it) } ?: stats.programId

    private fun headline(context: Context, stats: ShareCardStats): String =
        if (stats.finishedProgram) context.getString(R.string.celebration_title, programName(context, stats))
        else programName(context, stats)

    private fun subtitle(context: Context, stats: ShareCardStats): String {
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(stats.startedAt))
        if (stats.finishedProgram) return date
        return "${context.getString(R.string.history_week_day, stats.week, stats.day)}  ·  $date"
    }

    private fun statItems(context: Context, stats: ShareCardStats): List<Pair<String, String>> = buildList {
        add(formatDuration(stats.durationSeconds) to context.getString(R.string.history_stats_time))
        if (stats.distanceMeters > 0f) {
            add("%.2f".format(stats.distanceMeters / 1000f) to context.getString(R.string.history_stats_km))
        }
        stats.paceSecondsPerKm?.let { pace ->
            val text = "%d:%02d".format(pace / 60, pace % 60)
            add(context.getString(R.string.workout_pace, text) to context.getString(R.string.history_stats_pace))
        }
    }

    /** Writes [bitmap] as a PNG (keeping the sticker's transparency) and builds the share intent. */
    fun shareIntent(context: Context, bitmap: Bitmap, message: String?): Intent {
        val dir = File(context.cacheDir, DIR).apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "c2k-workout.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (message != null) putExtra(Intent.EXTRA_TEXT, message)
            // ClipData carries the read grant through the chooser to the app the runner picks.
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
