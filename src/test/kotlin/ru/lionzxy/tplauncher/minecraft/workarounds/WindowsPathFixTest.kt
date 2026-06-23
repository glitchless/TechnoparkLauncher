package ru.lionzxy.tplauncher.minecraft.workarounds

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class WindowsPathFixTest {
    private val sep = File.separator
    // %APPDATA%\.minecraft\technomine\newhorizon with a Cyrillic Windows username.
    private val gameDir = "C:${sep}Users${sep}Конст${sep}AppData${sep}Roaming${sep}.minecraft${sep}technomine${sep}newhorizon"
    private val fix = WindowsPathFix(gameDir)

    // The bug: the --gameDir value equals the game dir with NO trailing separator, so the old
    // "currentPath + separator" replace missed it and left the Cyrillic absolute path in argv.
    @Test
    fun `bare game-dir token (the --gameDir value) is rewritten to the working directory`() {
        assertEquals(".", fix.rewriteToken(gameDir))
    }

    // Paths *under* the game dir were already handled and must keep working (relative to cwd).
    @Test
    fun `path under the game dir is rewritten to a relative path`() {
        val natives = "$gameDir${sep}versions${sep}1.7.10${sep}natives"
        assertEquals(".${sep}versions${sep}1.7.10${sep}natives", fix.rewriteToken(natives))
    }

    @Test
    fun `game-dir prefix is rewritten even inside a flag token`() {
        val token = "-Djava.library.path=$gameDir${sep}versions${sep}1.7.10${sep}natives"
        assertEquals("-Djava.library.path=.${sep}versions${sep}1.7.10${sep}natives", fix.rewriteToken(token))
    }

    @Test
    fun `tokens that are not paths are left untouched`() {
        assertEquals("-Xmx3G", fix.rewriteToken("-Xmx3G"))
        assertEquals("net.minecraft.launchwrapper.Launch", fix.rewriteToken("net.minecraft.launchwrapper.Launch"))
    }
}
