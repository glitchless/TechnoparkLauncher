plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.roborazzi)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)   // foundation + ui (ImageComposeScene) + Skiko
    implementation(libs.sentry)
    testImplementation(libs.junit)
    testImplementation(libs.roborazzi.compose.desktop)
}

tasks.test {
    systemProperty("java.awt.headless", "true")
    systemProperty("skiko.renderApi", "SOFTWARE")
}

compose.desktop {
    application {
        mainClass = "ru.lionzxy.tplauncher.MainKt"
    }
}
