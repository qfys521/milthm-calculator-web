import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.Locale
import javax.imageio.ImageIO

object NewUiImageGenerator {
    private const val CANVAS_WIDTH = 1000
    private const val BACKGROUND_PATH = "./jpgs/background/562.jpg"
    private const val CONSTANT_JS_PATH = "./js/constant.js"
    private const val JPGS_FOLDER = "./jpgs"
    private const val COVER_W = 129
    private const val COVER_H = 75
    private const val H_SPACING = 11
    private const val V_SPACING = 70
    private const val COVERS_PER_ROW = 5
    private const val START_X = 180
    private const val START_Y = 220
    private const val TITLE_FONT_SIZE = 50
    private const val LEVEL_FONT_SIZE = 30
    private const val TITLE_X = 20
    private const val TITLE_Y = 80
    private const val LEVEL_TEXT_X = 20
    private val SEMI_BLACK = Color(0, 0, 0, 153)
    private val DIFFICULTY_COLORS = mapOf(
        "CL" to Color(64, 64, 64, 102),
        "CB" to Color(255, 68, 68, 102),
        "SK" to Color(68, 136, 255, 102),
        "EZ" to Color(68, 255, 68, 102),
        "DEFAULT" to Color(255, 255, 255, 102)
    )

    data class Song(val level: Double, val difficulty: String, val title: String)

    private fun parseConstantJs(path: Path): List<Song> {
        val content = ResourceLoader.readText(path)
        val match = Regex("\\{([\\s\\S]*)\\}").find(content)
            ?: throw IllegalArgumentException("constantsData content not found")
        val body = match.groupValues[1]
        val entries = Regex("\"[^\"]+\": \\[([^\\]]+)]").findAll(body)
        val result = mutableListOf<Song>()
        for (entry in entries) {
            val parts = entry.groupValues[1].split(",").map { it.trim() }
            if (parts.size >= 3) {
                val level = parts[0].toDoubleOrNull() ?: continue
                val difficulty = parts[1].replace("\"", "").trim()
                val title = parts[2].replace("\"", "").trim()
                result.add(Song(level, difficulty, title))
            }
        }
        return result
    }

    private fun parseArgs(
        data: List<Song>,
        difficulty: String,
        minVal: Double?,
        maxVal: Double?
    ): List<Song> {
        return data.filter { item ->
            if (difficulty != "all" && item.difficulty != difficulty) return@filter false
            if (minVal != null && maxVal != null) {
                return@filter item.level >= minVal && item.level <= maxVal
            }
            if (minVal != null && maxVal == null) {
                return@filter item.level == minVal
            }
            true
        }
    }

    private fun groupByLevel(data: List<Song>): Map<String, List<Song>> {
        val sorted = data.sortedByDescending { it.level }
        val grouped = linkedMapOf<String, MutableList<Song>>()
        for (item in sorted) {
            val key = String.format(Locale.US, "%.1f", item.level)
            grouped.getOrPut(key) { mutableListOf() }.add(item)
        }
        return grouped
    }

    private fun drawTextWithBackground(g: java.awt.Graphics2D, text: String, x: Int, y: Int, fontSize: Int) {
        g.font = Font("SansSerif", Font.PLAIN, fontSize)
        val metrics = g.fontMetrics
        val padding = 20
        val bgX = x - padding
        val bgY = y - fontSize - padding / 2
        val bgW = metrics.stringWidth(text) + padding * 2
        val bgH = fontSize + padding
        g.color = SEMI_BLACK
        g.fillRect(bgX, bgY, bgW, bgH)
        g.color = Color.WHITE
        g.drawString(text, x, y)
    }

    private fun loadImageSafe(path: Path): BufferedImage? {
        return ResourceLoader.loadImage(path)
    }

    fun generateTable(
        difficulty: String = "all",
        minVal: Double? = null,
        maxVal: Double? = null,
        constantsPath: Path = Path.of(CONSTANT_JS_PATH),
        backgroundPath: Path = Path.of(BACKGROUND_PATH),
        jpgsFolder: Path = Path.of(JPGS_FOLDER)
    ): BufferedImage {
        val allData = parseConstantJs(constantsPath)
        val filtered = parseArgs(allData, difficulty, minVal, maxVal)
        val grouped = groupByLevel(filtered)
        val keys = grouped.filterValues { it.isNotEmpty() }.keys
        require(keys.isNotEmpty()) { "No songs match the criteria" }

        var totalHeight = START_Y
        for (key in keys) {
            val count = grouped[key]?.size ?: 0
            val rowsInBlock = kotlin.math.ceil(count / COVERS_PER_ROW.toDouble()).toInt()
            totalHeight += if (count > 0) {
                rowsInBlock * (COVER_H + V_SPACING)
            } else {
                LEVEL_FONT_SIZE + V_SPACING
            }
        }
        totalHeight += 200

        val canvas = BufferedImage(CANVAS_WIDTH, totalHeight, BufferedImage.TYPE_INT_ARGB)
        val g = canvas.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val bg = loadImageSafe(backgroundPath)
        if (bg != null) {
            g.drawImage(bg, 0, 0, CANVAS_WIDTH, totalHeight, null)
        } else {
            g.color = Color(0x14, 0x14, 0x14)
            g.fillRect(0, 0, CANVAS_WIDTH, totalHeight)
        }

        g.color = SEMI_BLACK
        g.fillRect(0, 15, CANVAS_WIDTH, 85)
        drawTextWithBackground(g, "Milthm Chart Constant Table", TITLE_X, TITLE_Y, TITLE_FONT_SIZE)

        var currentY = START_Y
        val sortedKeys = keys.sortedByDescending { it.toDouble() }
        for (key in sortedKeys) {
            val titles = grouped[key].orEmpty()
            val count = titles.size
            val levelTextY = if (count > 0) {
                currentY + (COVER_H / 2) + LEVEL_FONT_SIZE / 2
            } else {
                currentY + LEVEL_FONT_SIZE
            }

            drawTextWithBackground(g, "► $key", LEVEL_TEXT_X, levelTextY, LEVEL_FONT_SIZE)

            if (count > 0) {
                var rowX = START_X
                var rowY = currentY
                var coversInRow = 0
                for (song in titles) {
                    val coverPath = jpgsFolder.resolve("${song.title}.jpg")
                    val coverImg = loadImageSafe(coverPath)
                    if (coverImg != null) {
                        g.drawImage(coverImg, rowX, rowY, COVER_W, COVER_H, null)
                    } else {
                        g.color = Color(0x55, 0x55, 0x55)
                        g.fillRect(rowX, rowY, COVER_W, COVER_H)
                    }

                    g.stroke = BasicStroke(3f)
                    g.color = DIFFICULTY_COLORS[song.difficulty] ?: DIFFICULTY_COLORS.getValue("DEFAULT")
                    g.drawRect(rowX, rowY, COVER_W, COVER_H)

                    coversInRow++
                    rowX += COVER_W + H_SPACING
                    if (coversInRow == COVERS_PER_ROW) {
                        rowX = START_X
                        rowY += COVER_H + V_SPACING
                        coversInRow = 0
                    }
                }

                currentY = rowY + COVER_H + V_SPACING
            } else {
                currentY += LEVEL_FONT_SIZE + V_SPACING
            }
        }

        g.dispose()
        return canvas
    }

    fun writePng(image: BufferedImage, outputPath: Path) {
        outputPath.toFile().parentFile?.mkdirs()
        ImageIO.write(image, "png", outputPath.toFile())
    }
}

fun main(args: Array<String>) {
    val difficulty = args.getOrNull(0) ?: "all"
    val minVal = args.getOrNull(1)?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val maxVal = args.getOrNull(2)?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val outputPath = Path.of(args.getOrNull(3) ?: "milthm_table.png")
    val image = NewUiImageGenerator.generateTable(
        difficulty = difficulty,
        minVal = minVal,
        maxVal = maxVal
    )
    NewUiImageGenerator.writePng(image, outputPath)
}
