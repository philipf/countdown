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

/** The manifest, ready to read declarations from. */
fun manifestXml(): String = withoutComments(appFile("src/main/AndroidManifest.xml"))

/** What the launcher is told about the widget. */
fun widgetInfoXml(): String = withoutComments(appFile("src/main/res/xml/widget_info.xml"))

/** One of the app's layouts, named without its extension. */
fun layoutXml(name: String): String = withoutComments(appFile("src/main/res/layout/$name.xml"))

/** XML with the comments taken out, so a check reads the declarations only. */
fun withoutComments(xml: String): String =
    Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL).replace(xml, "")

/** The names of the elements in [xml], in the order they open. */
fun tagsIn(xml: String): List<String> =
    Regex("""<([A-Za-z][\w.]*)""").findAll(xml).map { it.groupValues[1] }.toList()
