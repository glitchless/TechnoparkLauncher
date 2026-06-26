plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.buildconfig) apply false
}

// Version scheme: 1.<MAJOR_VERSION>.<MINOR_VERSION>
//   MAJOR_VERSION — bumped manually in gradle.properties.
//   MINOR_VERSION — the CI build number (onyxmueller/build-tag-number action), passed in via the
//                   MINOR_VERSION env var (or -PMINOR_VERSION). Defaults to 0 for local builds.
val majorVersion = (findProperty("MAJOR_VERSION") as String?) ?: "0"
val minorVersion = System.getenv("MINOR_VERSION") ?: (findProperty("MINOR_VERSION") as String?) ?: "0"

allprojects {
    group = "ru.lionzxy.tplauncher"
    version = "1.$majorVersion.$minorVersion"
}
