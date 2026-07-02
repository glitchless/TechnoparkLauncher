plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.buildconfig)
}

kotlin {
    jvmToolchain(21)
}

// Single source of truth for build metadata, shared by both the GUI (:desktop consumes
// this) and the headless CLI (MainCli lives here in :core). Generated PUBLIC because
// gmazzo defaults to `internal`, which would be invisible to the separate :desktop module.
buildConfig {
    useKotlinOutput { internalVisibility = false }
    packageName("ru.lionzxy.tplauncher")
    buildConfigField("String", "NAME", "\"TechnoparkLauncher\"")
    buildConfigField("String", "VERSION", "\"${project.version}\"")
    buildConfigField("String", "SENTRY_DSN", "\"https://cd312e191fbd44b49c6cc526bb91817c@sentry.team.glitchless.ru/18\"")
}

dependencies {
    implementation(libs.gson)
    implementation(libs.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.encoding)
    implementation(libs.jna)
    implementation(libs.commons.codec)
    implementation(libs.sentry)
    implementation(libs.jarchivelib)
    implementation(libs.zt.zip)
    implementation(libs.mclauncher.api)
    implementation(libs.oslib)
    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.coroutines.test)
}

// Headless login/launch harness (replaces the old root-project `runCli`).
// Credentials via -Pemail=.. -Ppassword=.. ; stop after auth with -Pcli='--no-launch'.
tasks.register<JavaExec>("runCli") {
    group = "application"
    description = "Run the login/launch flow headless (CLI)"
    mainClass.set("ru.lionzxy.tplauncher.MainCliKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    (project.findProperty("email") as String?)?.let { environment("TPL_EMAIL", it) }
    (project.findProperty("password") as String?)?.let { environment("TPL_PASSWORD", it) }
    (project.findProperty("cli") as String?)?.let { args(it.split(" ")) }
}
