import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
}

group = "ru.lionzxy"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvm {
        jvmToolchain(17)
        withJava()
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlin.serialization.json)
                implementation(libs.bundles.ktor)
                implementation("com.github.LionZXY:oslib:d5ba9facde")
                implementation("com.squareup.okio:okio:3.3.0")
                implementation("org.apache.commons:commons-compress:1.23.0")
            }
        }
        val jvmTest by getting

    }
}

compose.desktop {
    application {
        mainClass = "ru.lionzxy.tplauncher.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Minecraft by Glitchless Games"
            packageVersion = "1.0.0"
            macOS {
                bundleID = "ru.lionzxy.tplauncher"
                dockName = "GlitchlessGames"
                iconFile.set(project.file("assets/logo_macos.icns"))
            }
            windows {
                dirChooser = true
                iconFile.set(project.file("assets/logo_windows.ico"))
            }
            linux {
                iconFile.set(project.file("assets/logo_linux.png"))
            }
        }
    }
}
