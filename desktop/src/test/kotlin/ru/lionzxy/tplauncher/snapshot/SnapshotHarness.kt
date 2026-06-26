package ru.lionzxy.tplauncher.snapshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

val SnapGugi = FontFamily(Font("fonts/Gugi-Regular.ttf", FontWeight.Normal))
val SnapRoboto = FontFamily(Font("fonts/Roboto-Regular.ttf", FontWeight.Normal))

fun headlessInit() {
    System.setProperty("java.awt.headless", "true")
    System.setProperty("skiko.renderApi", "SOFTWARE")
}

/** Renders [content] to build/snapshots/<name>.png at the given pixel size; returns the file. */
fun snapshot(
    name: String,
    widthPx: Int,
    heightPx: Int,
    density: Float = 960f / 592f,   // 592dp window -> 960px to match img/ mockups
    content: @Composable () -> Unit,
): File {
    headlessInit()
    val image = renderComposeScene(widthPx, heightPx, Density(density), content = content)
    val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG encode null for $name")
    val out = File("build/snapshots/$name.png").apply { parentFile.mkdirs() }
    out.writeBytes(data.bytes)
    return out
}
