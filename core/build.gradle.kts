plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

// REQUIRED (not optional): the Kotlin source set does NOT compile .java files. This adds the
// colocated Settings.java / UriEncodeUtils.java to the JAVA source set so compileJava (joint-compiled
// with Kotlin) builds them. Without it they silently vanish and surface as a runtime
// NoClassDefFoundError in Task 7. `withJava()` is NOT needed — kotlin("jvm") already applies the java plugin.
sourceSets["main"].java.srcDir("src/main/kotlin")

dependencies {
    implementation(libs.gson)
    implementation(libs.coroutines.core)
    implementation(libs.jna)
    implementation(libs.commons.codec)
    implementation(libs.sentry)
    implementation(libs.jarchivelib)
    implementation(libs.zt.zip)
    implementation(libs.mclauncher.api)
    implementation(libs.oslib)
    testImplementation(libs.junit)
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
