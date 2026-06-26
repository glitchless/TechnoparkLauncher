import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.buildconfig) apply false
}

allprojects {
    group = "ru.lionzxy.tplauncher"
    version = "1.2." + SimpleDateFormat("MMddHHmm").format(Date())
}
