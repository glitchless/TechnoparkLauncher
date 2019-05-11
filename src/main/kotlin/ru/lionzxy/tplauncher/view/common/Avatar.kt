package ru.lionzxy.tplauncher.view.common

import javafx.event.EventTarget
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.StackPane
import javafx.scene.shape.Circle
import javafx.scene.shape.Rectangle
import net.minidev.json.JSONObject
import net.minidev.json.parser.JSONParser
import ru.lionzxy.tplauncher.utils.*
import sk.tomsik68.mclauncher.util.FileUtils
import sk.tomsik68.mclauncher.util.HttpUtils
import tornadofx.attachTo
import tornadofx.circle
import tornadofx.singleAssign
import java.io.File
import java.io.FileInputStream


fun EventTarget.avatarimage(op: Avatar.() -> Unit = {}): Avatar {
    return Avatar().attachTo(this, op)
}

class Avatar : StackPane() {
    private var circle: Circle by singleAssign()
    private var imageView: ImageView by singleAssign()

    init {
        circle = circle {
            radius = 42.0
            fill = Constants.backgroundCircleColor
        }
        imageView = svgview("check-solid") {
            fitWidth = 42.0
            fitHeight = 42.0
        }
    }

    fun show() {
        isVisible = true
        isManaged = true
        val nickname = ConfigHelper.config.profile?.login
        if (nickname != null) {
            runAsync {
                val cacheImage = getFromCache()
                if (cacheImage != null) {
                    applyImage(cacheImage)
                }
                applyImage(download(nickname))
            }
        }
    }

    private fun getFromCache(): Image? {
        val target = File(ConfigHelper.getDefaultDirectory(), "avatar.png")
        if (target.exists()) {
            return Image(FileInputStream(target))
        }
        return null
    }

    private fun download(nickname: String): Image {
        val target = File(ConfigHelper.getDefaultDirectory(), "avatar.png")
        val json = HttpUtils.httpGet("https://games.glitchless.ru/api/minecraft/users/profiles/$nickname/avatar/")
        val jsonObject = (JSONParser(JSONParser.MODE_PERMISSIVE).parse(json) as JSONObject)["data"] as JSONObject
        val avatarUrl = jsonObject.get("avatar_url") as String
        FileUtils.downloadFileWithProgress(
            avatarUrl,
            target,
            EmptyMonitoring()
        )
        return Image(FileInputStream(target))
    }

    private fun applyImage(image: Image) {
        runOnUi {
            imageView.image = image
            //TODO Сделать нормально
            val widthAva = 84.0
            imageView.fitWidth = widthAva
            imageView.fitHeight = widthAva
            imageView.clip = Rectangle(widthAva, widthAva).apply {
                arcHeight = widthAva
                arcWidth = widthAva
            }
        }
    }
}