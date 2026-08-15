package ninja.notnot.countdown

import java.io.File
import org.junit.jupiter.api.fail

/**
 * A file from the app module, as text, so a rule about the source or about what
 * is declared to Android can be checked rather than assumed.
 *
 * Walks up from the working directory, which Gradle sets to the module rather
 * than the repo root.
 */
fun appFile(relative: String): String {
    var directory: File? = File("").absoluteFile
    while (directory != null) {
        for (candidate in listOf(File(directory, relative), File(directory, "app/$relative"))) {
            if (candidate.isFile) return candidate.readText()
        }
        directory = directory.parentFile
    }
    fail("cannot find $relative from ${File("").absolutePath}")
}

/** A Kotlin source from the app's package. */
fun appSource(name: String): String = appFile("src/main/kotlin/ninja/notnot/countdown/$name")

/** XML with the comments taken out, so a check reads the declarations only. */
fun withoutComments(xml: String): String =
    Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL).replace(xml, "")
