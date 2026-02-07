import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

object ResourceLoader {
    private fun normalizeResourcePath(path: Path): String {
        return path.toString()
            .replace('\\', '/')
            .removePrefix("./")
            .removePrefix("/")
    }

    fun openStream(path: Path) = when {
        Files.exists(path) -> Files.newInputStream(path)
        else -> ResourceLoader::class.java.classLoader.getResourceAsStream(normalizeResourcePath(path))
    }

    fun readText(path: Path): String {
        val stream = openStream(path) ?: error("Resource not found: ${normalizeResourcePath(path)}")
        return stream.bufferedReader().use { it.readText() }
    }

    fun readLines(path: Path): List<String> {
        val stream = openStream(path) ?: return emptyList()
        return stream.bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }

    fun loadImage(path: Path): BufferedImage? {
        val stream = openStream(path) ?: return null
        return stream.use { ImageIO.read(it) }
    }
}
