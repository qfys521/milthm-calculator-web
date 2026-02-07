import java.awt.Font
import java.nio.file.Path

object FontFallback {
    private const val SAMPLE_TEXT = "中文"
    private val fallbackFont: Font? = loadFallbackFont()

    fun resolve(family: String, style: Int, size: Int): Font {
        val base = Font(family, style, size)
        if (base.canDisplayUpTo(SAMPLE_TEXT) == -1) {
            return base
        }
        val fallback = fallbackFont?.deriveFont(style, size.toFloat())
        if (fallback != null && fallback.canDisplayUpTo(SAMPLE_TEXT) == -1) {
            return fallback
        }
        return base
    }

    private fun loadFallbackFont(): Font? {
        val embedded = loadFont(Path.of("./fonts/alimamafangyuanti/AlimamaFangYuanTiVF-Thin.ttf"))
        if (embedded != null && embedded.canDisplayUpTo(SAMPLE_TEXT) == -1) {
            return embedded
        }
        val candidates = listOf(
            "PingFang SC",
            "Microsoft YaHei",
            "WenQuanYi Micro Hei",
            "Noto Sans CJK SC",
            "SansSerif"
        )
        for (family in candidates) {
            val candidate = Font(family, Font.PLAIN, 12)
            if (candidate.canDisplayUpTo(SAMPLE_TEXT) == -1) {
                return candidate
            }
        }
        return null
    }

    private fun loadFont(path: Path): Font? {
        val stream = ResourceLoader.openStream(path) ?: return null
        return stream.use { Font.createFont(Font.TRUETYPE_FONT, it) }
    }
}
