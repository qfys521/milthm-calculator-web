import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object NewUiScoreImageGenerator {
    private const val UPDATED_TEXT = "Updated at 2025.11.09 21:30 (UTC+8)"
    private const val BASE_HEIGHT = 2200
    private const val HEADER_HEIGHT = 200
    private const val CARD_WIDTH = 442
    private const val CARD_HEIGHT = 130
    private const val COVER_WIDTH = 185
    private const val COVER_HEIGHT = 104
    private const val ICON_SIZE = 91
    private const val CARD_X = 110
    private const val CARD_Y = 350
    private const val CARD_COL_GAP = 520
    private const val CARD_ROW_GAP = 162.5
    private const val DEFAULT_PLAYER_NAME = "玩家"
    private const val ICON_NAME_ZERO_MINUS_ONE = "0-1"
    private const val ICON_NAME_FALLBACK = "-1"
    // Sentinel used in the JS logic to trigger "Unable to deduce points" output.
    private const val SENTINEL_UNABLE_TO_DEDUCE = 114514.0

    data class Options(
        val maxCards: Int = 20,
        val constantsPath: Path = Path.of("./js/constant.js"),
        val backgroundPath: Path = Path.of("./jpgs/background/562.jpg"),
        val jpgsFolder: Path = Path.of("./jpgs"),
        val tipsPath: Path = Path.of("./tips.txt")
    )

    data class ChartConstant(
        val constant: Double,
        val constantv3: Double,
        val category: String,
        val name: String,
        val yct: Double
    )

    data class ScoreItem(
        val isV3: Boolean,
        val name: String,
        val category: String,
        val constant: Double,
        val constantv3: Double,
        val yct: Double,
        val bestScore: Int,
        val bestAccuracy: Double,
        val bestLevel: Int,
        val achievedStatus: List<Int>,
        val singleRealityRaw: Double,
        val singleReality: String,
        val mergeKey: String
    )

    data class ScorePayload(
        val username: String,
        val nickname: String,
        val userId: String,
        val items: List<ScoreItem>,
        val average: Double,
        val averageDisplay: Double
    )

    fun generateScoreImageBase64(scoreText: String, options: Options = Options()): String {
        val image = generateScoreImage(scoreText, options)
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }

    fun generateScoreImage(scoreText: String, options: Options = Options()): BufferedImage {
        val constants = loadConstants(options.constantsPath)
        val payload = parseScorePayload(scoreText, constants)
        val maxItems = max(0, options.maxCards)
        val items = payload.items.take(min(maxItems, payload.items.size))
        val canvasHeight = max(
            BASE_HEIGHT,
            (400 + ceil(items.size / 2.0 * 165.0)).toInt()
        )

        val canvas = BufferedImage(1200, canvasHeight, BufferedImage.TYPE_INT_ARGB)
        val g = canvas.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val background = loadImageSafe(options.backgroundPath)
        if (background != null) {
            g.drawImage(background, 0, 0, canvas.width, canvas.height, null)
        } else {
            g.color = Color.BLACK
            g.fillRect(0, 0, canvas.width, canvas.height)
        }

        drawHeader(g, payload, options.tipsPath)
        drawCards(g, payload, items, options.jpgsFolder)

        g.dispose()
        return canvas
    }

    private fun drawHeader(g: java.awt.Graphics2D, payload: ScorePayload, tipsPath: Path) {
        g.color = Color(128, 128, 128, 77)
        g.fillRect(0, 50, 1200, HEADER_HEIGHT)

        g.stroke = BasicStroke(3f)
        g.color = Color(255, 255, 255, 204)
        g.drawLine(550, 250, 650, 50)

        val star = computeStar(payload.items)
        g.font = Font("Arial", Font.PLAIN, 25)
        g.color = Color.WHITE
        g.drawString(star, 660, 75)

        g.drawString("Player: ${payload.username}  (${payload.nickname})", 660, 100)
        g.drawString("userID: ${payload.userId}", 660, 128)
        g.drawString("Reality: ${String.format(Locale.US, "%.4f", payload.averageDisplay)}", 660, 160)

        val dateStr = "${LocalDate.now(ZoneOffset.UTC)} ${LocalTime.now().withNano(0)}"
        g.drawString("Date: $dateStr", 660, 190)
        drawTip(g, payload, tipsPath)

        g.font = Font("Arial", Font.PLAIN, 50)
        g.drawString("Milthm-calculator", 100, 95)
        g.font = Font("Arial", Font.PLAIN, 25)
        g.drawString("https://mhtlim.top/", 100, 125)
        g.drawString("http://k9.lv/c/", 100, 153)
        g.drawString("https://milcalc.netlify.app/", 100, 181)
        g.drawString("https://mkzi-nya.github.io/c/", 100, 207)
        g.font = Font("Arial", Font.PLAIN, 30)
        g.drawString("←查分上这里", 400, 130)
        g.font = Font("Arial", Font.PLAIN, 20)
        g.drawString("这几个网址都行", 440, 155)
        g.drawString(UPDATED_TEXT, 100, 230)
    }

    private fun drawTip(g: java.awt.Graphics2D, payload: ScorePayload, tipsPath: Path) {
        val tips = loadTips(tipsPath)
        if (tips.isEmpty()) {
            return
        }
        val avg = payload.averageDisplay
        val rand = kotlin.random.Random.Default.nextDouble()
        val tip = when {
            avg >= 13.475 && rand < 0.5 -> tips.first()
            avg >= 13.45 && rand < 0.3 -> tips[kotlin.random.Random.Default.nextInt(min(tips.size, 2))]
            else -> tips[kotlin.random.Random.Default.nextInt(tips.size)]
        }
        val tipText = "Tip: " + tip.replace("{Name}", if (payload.username.isNotBlank()) payload.username else DEFAULT_PLAYER_NAME)

        val maxWidth = 500
        val lineHeight = 24
        g.font = Font("Arial", Font.PLAIN, 20)
        g.color = Color.WHITE
        var line = ""
        var y = 220
        val x = 660
        val metrics = g.fontMetrics
        tipText.forEach { ch ->
            val testLine = line + ch
            if (metrics.stringWidth(testLine) > maxWidth) {
                g.drawString(line, x, y)
                line = ch.toString()
                y += lineHeight
            } else {
                line = testLine
            }
        }
        if (line.isNotEmpty()) {
            g.drawString(line, x, y)
        }
    }

    private fun drawCards(
        g: java.awt.Graphics2D,
        payload: ScorePayload,
        items: List<ScoreItem>,
        jpgsFolder: Path
    ) {
        val imageCache = mutableMapOf<Path, BufferedImage?>()
        items.forEachIndexed { index, item ->
            val x = CARD_X + (index % 2) * CARD_COL_GAP
            val y = (CARD_Y + kotlin.math.floor(index / 2.0 * CARD_ROW_GAP)).toInt() - if (index % 2 == 0) 50 else 0

            val scoreIsV3 = item.isV3 || item.bestLevel <= 1 || item.bestScore >= 1005000 ||
                item.achievedStatus.contains(2) || item.achievedStatus.contains(5)
            g.color = if (scoreIsV3) Color(128, 128, 128, 128) else Color(128, 128, 128, 51)
            g.fillRect(x, y, CARD_WIDTH, CARD_HEIGHT)

            g.font = Font("Arial", Font.PLAIN, 17)
            g.color = if (index < 20) Color(0xFA, 0xFA, 0xFA) else Color(0xC9, 0xC9, 0xC9)
            g.drawString("#${index + 1}", x + CARD_WIDTH - 35, y + 24)

            val scoreStr = String.format(Locale.US, "%07d", item.bestScore)
            val scorePaint = when {
                item.achievedStatus.contains(5) -> GradientPaint(
                    0f,
                    (y + 52).toFloat(),
                    Color(0x99, 0xC5, 0xFB),
                    0f,
                    (y + 91).toFloat(),
                    Color(0xD8, 0xC3, 0xFA)
                )
                item.achievedStatus.contains(4) -> Color(0x90, 0xCA, 0xEF)
                else -> Color.WHITE
            }
            g.font = Font("Arial", Font.PLAIN, 39)
            g.paint = scorePaint
            g.drawString(scoreStr, x + 208, y + 52)
            g.paint = Color.WHITE

            var fontSize = 25
            var titleFont = Font("Arial", Font.PLAIN, fontSize)
            g.font = titleFont
            while (g.fontMetrics.stringWidth(item.name) > 200 && fontSize > 10) {
                fontSize -= 1
                titleFont = Font("Arial", Font.PLAIN, fontSize)
                g.font = titleFont
            }
            g.color = Color.WHITE
            g.drawString(item.name, x + 212, y + 23)

            val acc = String.format(Locale.US, "%.2f%%", item.bestAccuracy * 100)
            g.font = Font("Arial", Font.PLAIN, 20)
            val ratingText = String.format(
                Locale.US,
                "%s %.1f > %s   %s",
                normalizeCategory(item.category),
                item.constantv3,
                item.singleReality,
                acc
            )
            g.drawString(ratingText, x + 208, y + 98)

            g.font = Font("Arial", Font.PLAIN, 13)
            val targetScore = findScoreText(
                item.constantv3,
                targetReality(payload.average, item, items)
            )
            g.drawString(">>$targetScore", x + 212, y + 86)

            val coverPath = jpgsFolder.resolve("${sanitizeTitleForFile(item.name, item.category)}.jpg")
            val cover = loadImageSafe(coverPath, imageCache)
            if (cover != null) {
                g.drawImage(cover, x + 13, y + 13, COVER_WIDTH, COVER_HEIGHT, null)
            }

            val iconName = getLevelIconName(item)
            val iconPath = jpgsFolder.resolve("$iconName.png")
            val icon = loadImageSafe(iconPath, imageCache)
            if (icon != null) {
                g.drawImage(icon, x + 351, y + 26, ICON_SIZE, ICON_SIZE, null)
            }
        }
    }

    private fun targetReality(average: Double, item: ScoreItem, items: List<ScoreItem>): Double {
        val avgTimes100 = average * 100
        val rounded = ceil(avgTimes100 - 0.5) + 0.5
        if (rounded == avgTimes100) {
            return SENTINEL_UNABLE_TO_DEDUCE
        }
        val base = (rounded - avgTimes100) / 5.0
        val baseline = max(item.singleRealityRaw, items.getOrNull(19)?.singleRealityRaw ?: 0.0)
        return base + baseline
    }

    private fun findScoreText(constant: Double, target: Double): String {
        if (target == SENTINEL_UNABLE_TO_DEDUCE) return "Unable to deduce points"
        if (target <= 0) return "600000"
        if (target > constant + 1.5) return "Unable to deduce points"
        if (target >= constant) {
            if (target == constant + 1.5) return "1000000"
            return ceil(850000 + (target - constant) * 100000).toInt().toString()
        }
        if (target >= max(0.0, 0.5 * constant - 1.5)) {
            val denominator = constant / 300000 + 1.0 / 100000.0
            val score = (target + constant * 11 / 6 + 8.5) / denominator
            return min(ceil(score).toInt(), 849999).toString()
        }
        if (kotlin.math.abs(constant - 3) < 1e-6) return "600000"
        val score = 600000 + (target * 200000) / (constant - 3)
        return min(ceil(score).toInt(), 699999).toString()
    }

    private fun computeStar(items: List<ScoreItem>): String {
        var maxConstant = Double.NEGATIVE_INFINITY
        items.forEach { item ->
            if (item.achievedStatus.contains(5) && item.constant > maxConstant) {
                maxConstant = item.constant
            }
        }
        return when {
            maxConstant > 12 -> "☆☆☆"
            maxConstant > 9 -> "☆☆"
            maxConstant > 6 -> "☆"
            else -> ""
        }
    }

    private fun normalizeCategory(category: String): String {
        if (category == "Ø") return "UN"
        return if (category in setOf("CB", "CL", "SP", "UN", "SK", "DZ")) {
            category
        } else {
            "SP"
        }
    }

    private fun sanitizeTitleForFile(title: String, category: String): String {
        var clean = title.replace(Regex("[#?<>*\"|\\\\/:]"), "")
        if (normalizeCategory(category) == "SP" && clean == "Welcome to Milthm") {
            clean = "Welcome to Milthm SP"
        }
        return clean
    }

    private fun getLevelIconName(item: ScoreItem): String {
        val iconName = when {
            item.bestLevel == 0 -> "0"
            item.bestLevel == 6 || item.bestLevel == 7 -> "6"
            item.achievedStatus.contains(5) -> "${item.bestLevel}0"
            item.achievedStatus.contains(4) -> "${item.bestLevel}1"
            else -> item.bestLevel.toString()
        }
        return if (iconName.toIntOrNull() != null || iconName == ICON_NAME_ZERO_MINUS_ONE) iconName else ICON_NAME_FALLBACK
    }

    private fun parseScorePayload(scoreText: String, constants: Map<String, ChartConstant>): ScorePayload {
        val jsonText = extractJson(scoreText) ?: scoreText
        val parsed = JsonParser(jsonText).parse() as? Map<*, *>
            ?: error("Unable to parse score data JSON.")

        val username = (parsed["Username"] ?: parsed["UserName"] ?: "").toString()
        val nickname = (parsed["Nickname"] ?: "").toString()
        val userId = (parsed["UserID"] ?: "").toString()

        val records = (parsed["SongRecords"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
        val recordsV3 = (parsed["SongRecordsV3"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()

        val items = (records.mapNotNull { processRecord(it, constants, false) } +
            recordsV3.mapNotNull { processRecord(it, constants, true) })

        val merged = mergeSongVersions(items)
        val sorted = merged.sortedWith(
            compareByDescending<ScoreItem> { it.singleRealityRaw }.thenByDescending { it.bestScore }
        )
        val average = computeAverage(sorted)
        val averageDisplay = floor(average * 10000.0) / 10000.0
        return ScorePayload(username, nickname, userId, sorted, average, averageDisplay)
    }

    private fun computeAverage(items: List<ScoreItem>): Double {
        val values = items.filter { it.singleRealityRaw > 0 }
            .sortedByDescending { it.singleRealityRaw }
            .take(20)
            .map { it.singleRealityRaw }
        if (values.isEmpty()) return 0.0
        return values.sum() / 20.0
    }

    private fun processRecord(
        record: Map<*, *>,
        constants: Map<String, ChartConstant>,
        isV3: Boolean
    ): ScoreItem? {
        val beatmapId = record["BeatmapID"]?.toString() ?: return null
        val constantObj = constants[beatmapId] ?: return null

        val scoreVal = (record["BestScore"] as? Number)?.toInt() ?: 0
        val accVal = (record["BestAccuracy"] as? Number)?.toDouble() ?: 0.0
        val levelVal = (record["BestLevel"] as? Number)?.toInt() ?: 0
        val status = (record["AchievedStatus"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()

        val rV2 = reality(scoreVal, constantObj.constant)
        val rV3 = realityV3(scoreVal, constantObj.constantv3)
        val useV3 = isV3 || levelVal <= 1 || scoreVal >= 1005000 || status.contains(2) || status.contains(5)
        val singleRealityRaw = if (isV3) rV3 else if (useV3) {
            if (constantObj.constantv3 > 1e-5) constantObj.constantv3 + 1.5 else 0.0
        } else {
            rV2
        }

        val singleReality = if (singleRealityRaw.isFinite()) {
            String.format(Locale.US, "%.2f", singleRealityRaw)
        } else {
            "0.00"
        }

        return ScoreItem(
            isV3 = isV3,
            name = constantObj.name,
            category = constantObj.category,
            constant = constantObj.constant,
            constantv3 = constantObj.constantv3,
            yct = constantObj.yct,
            bestScore = scoreVal,
            bestAccuracy = accVal,
            bestLevel = levelVal,
            achievedStatus = status,
            singleRealityRaw = singleRealityRaw,
            singleReality = singleReality,
            mergeKey = "${constantObj.name}@@${String.format(Locale.US, "%.4f", constantObj.constant)}"
        )
    }

    private fun mergeSongVersions(items: List<ScoreItem>): List<ScoreItem> {
        val map = linkedMapOf<String, ScoreItem>()
        items.forEach { item ->
            val key = item.mergeKey
            val prev = map[key]
            if (prev == null) {
                map[key] = item
                return@forEach
            }
            val mergedStatus = (prev.achievedStatus + item.achievedStatus).distinct().sorted()
            val bestScore = max(prev.bestScore, item.bestScore)
            val bestAccuracy = max(prev.bestAccuracy, item.bestAccuracy)
            val bestLevel = min(prev.bestLevel, item.bestLevel)
            val singleRealityRaw = max(prev.singleRealityRaw, item.singleRealityRaw)
            val singleReality = String.format(Locale.US, "%.2f", singleRealityRaw)

            map[key] = prev.copy(
                isV3 = prev.isV3 || item.isV3,
                bestScore = bestScore,
                bestAccuracy = bestAccuracy,
                bestLevel = bestLevel,
                achievedStatus = mergedStatus,
                singleRealityRaw = singleRealityRaw,
                singleReality = singleReality
            )
        }
        return map.values.toList()
    }

    private fun reality(score: Int, constant: Double): Double {
        if (constant < 0.001) return 0.0
        return when {
            score >= 1005000 -> 1 + constant
            score >= 995000 -> 1.4 / (kotlin.math.exp(363.175 - score * 0.000365) + 1) - 0.4 + constant
            score >= 980000 -> ((kotlin.math.exp(3.1 * (score - 980000) / 15000) - 1) /
                (kotlin.math.exp(3.1) - 1)) * 0.8 - 0.5 + constant
            score >= 700000 -> score / 280000.0 - 4 + constant
            else -> 0.0
        }
    }

    private fun realityV3(score: Int, constant: Double): Double {
        if (constant < 1e-3) return 0.0
        return when {
            score >= 1000000 -> constant + 1.5
            score >= 850000 -> constant + (score - 850000) / 100000.0
            score >= 700000 -> max(0.0, constant * (0.5 + (score - 700000) / 300000.0) +
                (score - 850000) / 100000.0)
            score >= 600000 -> max(0.0, (constant - 3) * (score - 600000) / 200000.0)
            else -> 0.0
        }
    }

    private fun extractJson(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }
        val userdataIndex = trimmed.indexOf("userdata:")
        if (userdataIndex >= 0) {
            val candidate = trimmed.substring(userdataIndex + "userdata:".length).trim()
            if (candidate.startsWith("{")) {
                return candidate
            }
        }
        val start = trimmed.indexOf('{')
        if (start == -1) return null
        var depth = 0
        for (i in start until trimmed.length) {
            when (trimmed[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            if (depth == 0) {
                return trimmed.substring(start, i + 1)
            }
        }
        return null
    }

    private fun loadConstants(path: Path): Map<String, ChartConstant> {
        val content = Files.readString(path)
        val match = Regex("const\\s+constantsData\\s*=\\s*\\{([\\s\\S]*?)\\};").find(content)
            ?: error("constantsData block not found")
        val body = match.groupValues[1]
        val entryRegex = Regex("\"([^\"]+)\":\\s*\\[([\\s\\S]*?)]")
        val result = linkedMapOf<String, ChartConstant>()
        entryRegex.findAll(body).forEach { entry ->
            val id = entry.groupValues[1]
            val raw = entry.groupValues[2]
            val tokens = splitArrayTokens(raw)
            if (tokens.isEmpty()) return@forEach
            val adjusted = tokens.toMutableList()
            val constantv3OrNull = adjusted.getOrNull(1)?.toDoubleOrNull()
            if (constantv3OrNull == null) {
                adjusted.add(1, adjusted.getOrNull(0).orEmpty())
            }
            val constant = adjusted.getOrNull(0)?.toDoubleOrNull() ?: 0.0
            val constantv3 = adjusted.getOrNull(1)?.toDoubleOrNull() ?: constant
            val category = stripQuotes(adjusted.getOrNull(2).orEmpty())
            val name = stripQuotes(adjusted.getOrNull(3).orEmpty())
            val yctRaw = adjusted.getOrNull(4)?.toDoubleOrNull()
            val yct = yctRaw ?: ceil(constantv3 * 20)
            result[id] = ChartConstant(constant, constantv3, category, name, yct)
        }
        return result
    }

    private fun splitArrayTokens(raw: String): List<String> {
        val tokens = mutableListOf<String>()
        val buf = StringBuilder()
        var inString = false
        var stringChar = '\u0000'
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (inString) {
                buf.append(ch)
                if (ch == stringChar && !isEscaped(raw, i)) {
                    inString = false
                }
            } else {
                when (ch) {
                    '"', '\'' -> {
                        inString = true
                        stringChar = ch
                        buf.append(ch)
                    }
                    ',' -> {
                        tokens.add(buf.toString().trim())
                        buf.setLength(0)
                    }
                    else -> buf.append(ch)
                }
            }
            i++
        }
        tokens.add(buf.toString().trim())
        return tokens
    }

    private fun stripQuotes(value: String): String {
        val trimmed = value.trim()
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))
        ) {
            return trimmed.substring(1, trimmed.length - 1)
        }
        return trimmed
    }

    private fun isEscaped(text: String, index: Int): Boolean {
        var backslashes = 0
        var i = index - 1
        while (i >= 0 && text[i] == '\\') {
            backslashes++
            i--
        }
        return backslashes % 2 == 1
    }

    private fun loadTips(path: Path): List<String> {
        return if (Files.exists(path)) {
            Files.readAllLines(path).map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
    }

    private fun loadImageSafe(path: Path, cache: MutableMap<Path, BufferedImage?> = mutableMapOf()): BufferedImage? {
        if (cache.containsKey(path)) {
            return cache[path]
        }
        val image = try {
            if (Files.exists(path)) ImageIO.read(path.toFile()) else null
        } catch (_: Exception) {
            null
        }
        cache[path] = image
        return image
    }

    private class JsonParser(private val input: String) {
        private var index = 0

        fun parse(): Any? {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            return when (peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            consume('{')
            skipWhitespace()
            val map = linkedMapOf<String, Any?>()
            if (peek() == '}') {
                consume('}')
                return map
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                consume(':')
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                when (peek()) {
                    ',' -> {
                        consume(',')
                    }
                    '}' -> {
                        consume('}')
                        return map
                    }
                    else -> error("Unexpected JSON object separator at position $index")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            consume('[')
            skipWhitespace()
            val list = mutableListOf<Any?>()
            if (peek() == ']') {
                consume(']')
                return list
            }
            while (true) {
                val value = parseValue()
                list.add(value)
                skipWhitespace()
                when (peek()) {
                    ',' -> consume(',')
                    ']' -> {
                        consume(']')
                        return list
                    }
                    else -> error("Unexpected JSON array separator at position $index")
                }
            }
        }

        private fun parseString(): String {
            consume('"')
            val sb = StringBuilder()
            while (index < input.length) {
                val ch = input[index++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> sb.append(parseEscape())
                    else -> sb.append(ch)
                }
            }
            error("Unterminated JSON string")
        }

        private fun parseEscape(): Char {
            if (index >= input.length) error("Invalid JSON escape sequence")
            return when (val ch = input[index++]) {
                '"', '\\', '/' -> ch
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    val hex = input.substring(index, index + 4)
                    index += 4
                    hex.toInt(16).toChar()
                }
                else -> error("Invalid JSON escape sequence: \\$ch")
            }
        }

        private fun parseNumber(): Number {
            val start = index
            if (peek() == '-') index++
            while (peek().isDigit()) index++
            if (peek() == '.') {
                index++
                while (peek().isDigit()) index++
            }
            if (peek() == 'e' || peek() == 'E') {
                index++
                if (peek() == '+' || peek() == '-') index++
                while (peek().isDigit()) index++
            }
            val text = input.substring(start, index)
            return if (text.contains('.') || text.contains('e', true)) {
                text.toDouble()
            } else {
                text.toLong()
            }
        }

        private fun parseLiteral(expected: String, value: Any?): Any? {
            if (!input.startsWith(expected, index)) {
                error("Expected literal $expected at position $index")
            }
            index += expected.length
            return value
        }

        private fun skipWhitespace() {
            while (index < input.length && input[index].isWhitespace()) {
                index++
            }
        }

        private fun peek(): Char {
            return if (index < input.length) input[index] else '\u0000'
        }

        private fun consume(ch: Char) {
            if (peek() != ch) {
                error("Expected '$ch' at position $index")
            }
            index++
        }
    }
}
