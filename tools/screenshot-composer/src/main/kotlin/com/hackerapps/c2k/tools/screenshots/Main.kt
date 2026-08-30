package com.hackerapps.c2k.tools.screenshots

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private val BACKGROUND = Color(0x14, 0x18, 0x1C)
private val GLOW_CENTER = Color(0x4A, 0x30, 0x1E)
private val ACCENT = Color(0xFF, 0x6B, 0x35)
private val CAMERA_DOT = Color(0x2A, 0x2E, 0x33)
private const val WATERMARK_ALPHA = 0.22f

// Wide enough that the glow/watermark background actually has visible canvas to sit on —
// at a thin margin the frame covers almost the whole image and both effects render mostly
// underneath it, invisible.
private const val SIDE_MARGIN = 220
private const val CAPTION_BAND_HEIGHT = 420
private const val FRAME_BORDER = 10
private const val FRAME_CORNER_RADIUS = 56f
private const val CAPTION_FONT_SIZE = 64f
private const val CAPTION_LINE_SPACING = 1.15f

fun main() {
    val repoRoot = File(".").canonicalFile
    val fastlaneDir = File(repoRoot, "fastlane")
    val sourceMetadataDir = File(fastlaneDir, "metadata/android")
    val outputMetadataDir = File(fastlaneDir, "metadata/android-play")
    val captions = parseCaptions(File(fastlaneDir, "screenshot_captions.yml"))

    val locales = sourceMetadataDir.listFiles { f -> f.isDirectory }
        ?.map { it.name }
        ?.sorted()
        ?: error("No locales found under $sourceMetadataDir")

    var composed = 0
    for (locale in locales) {
        val inputDir = File(sourceMetadataDir, "$locale/images/phoneScreenshots")
        if (!inputDir.isDirectory) continue

        val outputDir = File(outputMetadataDir, "$locale/images/phoneScreenshots")
        outputDir.mkdirs()

        inputDir.listFiles { f -> f.extension.equals("png", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.forEach { rawFile ->
                val screenName = rawFile.nameWithoutExtension
                val caption = captions[screenName]?.get(locale)
                    ?: error("No caption for '$screenName' in locale '$locale' (fastlane/screenshot_captions.yml)")

                val raw = ImageIO.read(rawFile)
                val composedImage = composite(raw, caption)
                ImageIO.write(composedImage, "png", File(outputDir, rawFile.name))
                composed++
            }

        copyIfExists(File(sourceMetadataDir, "$locale/images/featureGraphic.png"), File(outputMetadataDir, "$locale/images/featureGraphic.png"))
        copyIfExists(File(sourceMetadataDir, "$locale/images/featureGraphic.svg"), File(outputMetadataDir, "$locale/images/featureGraphic.svg"))
        copyIfExists(File(sourceMetadataDir, "$locale/images/icon.png"), File(outputMetadataDir, "$locale/images/icon.png"))
    }

    println("Composed $composed screenshot(s) into $outputMetadataDir")
}

private fun copyIfExists(source: File, destination: File) {
    if (!source.isFile) return
    destination.parentFile.mkdirs()
    source.copyTo(destination, overwrite = true)
}

/**
 * Parses the narrow two-level structure used by fastlane/screenshot_captions.yml:
 *   <screen-name>:
 *     <locale>: "<caption text>"
 * This is not a general YAML parser — it only understands that one shape.
 */
private fun parseCaptions(file: File): Map<String, Map<String, String>> {
    if (!file.isFile) error("Missing caption file: $file")

    val screenKeyRegex = Regex("""^(\S+):\s*$""")
    val localeLineRegex = Regex("""^\s{2}([\w-]+):\s*"(.*)"\s*$""")

    val result = mutableMapOf<String, MutableMap<String, String>>()
    var currentScreen: String? = null

    file.readLines().forEach { rawLine ->
        val line = rawLine.substringBefore("\n")
        if (line.isBlank() || line.trimStart().startsWith("#")) return@forEach

        screenKeyRegex.matchEntire(line)?.let { match ->
            currentScreen = match.groupValues[1]
            result.getOrPut(currentScreen!!) { mutableMapOf() }
            return@forEach
        }

        localeLineRegex.matchEntire(line)?.let { match ->
            val screen = currentScreen ?: error("Caption locale line found before any screen key: $line")
            result.getOrPut(screen) { mutableMapOf() }[match.groupValues[1]] = match.groupValues[2]
        }
    }

    return result
}

private fun composite(raw: BufferedImage, caption: String): BufferedImage {
    val canvasWidth = raw.width + SIDE_MARGIN * 2
    val canvasHeight = raw.height + SIDE_MARGIN * 2 + CAPTION_BAND_HEIGHT

    val canvas = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB)
    val g = canvas.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

    g.color = BACKGROUND
    g.fillRect(0, 0, canvasWidth, canvasHeight)

    // Centered on the frame's top edge so the brightest part of the glow sits in the visible
    // caption band / side margins rather than hidden under the opaque frame fill below it.
    val glowCenter = Point2D.Float(canvasWidth * 0.5f, (CAPTION_BAND_HEIGHT + SIDE_MARGIN).toFloat())
    val glowRadius = canvasWidth * 0.9f
    g.paint = RadialGradientPaint(glowCenter, glowRadius, floatArrayOf(0f, 1f), arrayOf(GLOW_CENTER, BACKGROUND))
    g.fillRect(0, 0, canvasWidth, canvasHeight)
    g.paint = null

    drawWatermarkRunner(g, canvasWidth, canvasHeight)

    drawCaption(g, caption, canvasWidth)

    val frameX = SIDE_MARGIN - FRAME_BORDER
    val frameY = CAPTION_BAND_HEIGHT + SIDE_MARGIN - FRAME_BORDER
    val frameWidth = raw.width + FRAME_BORDER * 2
    val frameHeight = raw.height + FRAME_BORDER * 2

    g.color = ACCENT
    g.fill(
        RoundRectangle2D.Float(
            frameX.toFloat(), frameY.toFloat(),
            frameWidth.toFloat(), frameHeight.toFloat(),
            FRAME_CORNER_RADIUS, FRAME_CORNER_RADIUS
        )
    )

    val screenshotRadius = (FRAME_CORNER_RADIUS - FRAME_BORDER).coerceAtLeast(0f)
    val screenshotX = SIDE_MARGIN
    val screenshotY = CAPTION_BAND_HEIGHT + SIDE_MARGIN
    val oldClip = g.clip
    g.clip = RoundRectangle2D.Float(
        screenshotX.toFloat(), screenshotY.toFloat(),
        raw.width.toFloat(), raw.height.toFloat(),
        screenshotRadius, screenshotRadius
    )
    g.drawImage(raw, screenshotX, screenshotY, null)
    g.clip = oldClip

    g.color = CAMERA_DOT
    val dotRadius = 10
    g.fillOval(canvasWidth / 2 - dotRadius, frameY - dotRadius - 18, dotRadius * 2, dotRadius * 2)

    g.dispose()
    return canvas
}

/**
 * Traces the same running-figure glyph used in featureGraphic.svg (head circle + torso/arm/leg
 * strokes, in that file's local 0-100ish coordinate space) as a large, low-alpha watermark, so
 * the screenshots share a visual motif with the existing feature graphic instead of just a flat
 * accent color.
 */
private fun drawWatermarkRunner(g: Graphics2D, canvasWidth: Int, canvasHeight: Int) {
    val oldTransform = g.transform
    val oldComposite = g.composite
    val oldColor = g.color
    val oldStroke = g.stroke

    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, WATERMARK_ALPHA)
    g.color = Color.WHITE

    // Local figure bounding box is roughly x:[32,82] y:[18,83] (center ~57,50) — anchor so
    // that center lands near the bottom-right corner margin, letting the figure bleed off the
    // canvas edge and/or under the frame corner while most of it stays on visible canvas.
    val figureScale = SIDE_MARGIN * 2.6 / 65.0
    val targetX = canvasWidth - 120.0
    val targetY = canvasHeight - 120.0
    g.translate((targetX - 57.0 * figureScale).toInt(), (targetY - 50.0 * figureScale).toInt())
    g.rotate(Math.toRadians(-8.0))
    g.scale(figureScale, figureScale)

    g.fill(Ellipse2D.Double(50.0 - 8.0, 26.0 - 8.0, 16.0, 16.0))

    fun stroke(width: Float) {
        g.stroke = BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    }

    stroke(8f)
    g.draw(Line2D.Double(52.0, 34.0, 60.0, 57.0))
    stroke(7f)
    g.draw(Line2D.Double(50.0, 43.0, 33.0, 31.0))
    g.draw(Line2D.Double(53.0, 42.0, 70.0, 55.0))
    stroke(8f)
    g.draw(GeneralPath().apply {
        moveTo(60.0, 57.0); lineTo(72.0, 73.0); lineTo(82.0, 83.0)
    })
    g.draw(GeneralPath().apply {
        moveTo(58.0, 57.0); lineTo(46.0, 71.0); lineTo(32.0, 64.0)
    })

    g.transform = oldTransform
    g.composite = oldComposite
    g.color = oldColor
    g.stroke = oldStroke
}

private fun drawCaption(g: java.awt.Graphics2D, caption: String, canvasWidth: Int) {
    g.color = ACCENT
    g.font = Font("SansSerif", Font.BOLD, CAPTION_FONT_SIZE.toInt())
    val metrics = g.fontMetrics
    val maxTextWidth = canvasWidth - SIDE_MARGIN * 2

    val lines = wrapText(caption, maxTextWidth) { text -> metrics.stringWidth(text) }
    val lineHeight = metrics.height * CAPTION_LINE_SPACING
    val blockHeight = lineHeight * lines.size
    var y = (CAPTION_BAND_HEIGHT - blockHeight) / 2f + metrics.ascent

    for (line in lines) {
        val x = (canvasWidth - metrics.stringWidth(line)) / 2
        g.drawString(line, x, y.toInt())
        y += lineHeight
    }
}

private fun wrapText(text: String, maxWidth: Int, widthOf: (String) -> Int): List<String> {
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var current = StringBuilder()

    for (word in words) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (widthOf(candidate) <= maxWidth || current.isEmpty()) {
            current = StringBuilder(candidate)
        } else {
            lines += current.toString()
            current = StringBuilder(word)
        }
    }
    if (current.isNotEmpty()) lines += current.toString()
    return lines
}
