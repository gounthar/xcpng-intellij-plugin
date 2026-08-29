plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.intellij.platform)
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()

        // NOTE the difference from the gateway sibling, which additionally needs snapshots().
        // Gateway is published only to the JetBrains snapshots repository (releases answers
        // 404 for it, measured 2026-08-29). IDEA Community is a normal release artifact, so
        // defaultRepositories() is enough here. Do not copy snapshots() across without a
        // reason: it widens what a bad version string can silently resolve to.
    }
}

dependencies {
    intellijPlatform {
        // intellijIdea, NOT intellijIdeaCommunity. IDEA Community has not been published
        // since 2025.3 and the accessor cannot resolve a download URL for anything newer; the
        // plugin says so by name when you try. Full working, and the mistake that produced it,
        // in gradle/libs.versions.toml.
        //
        // The plugin depends only on com.intellij.modules.platform, so it is intended to load
        // in WebStorm too. Still not observed. Targeting WebStorm directly is now a reasonable
        // alternative and is discussed in the catalog.
        intellijIdea(libs.versions.idea)

        pluginVerifier()
    }

    // compileOnly, not implementation: the IntelliJ Platform provides both at runtime.
    // Bundling either ships a second copy for the platform to load, which surfaces as a
    // NoSuchMethodError or a LinkageError inside the IDE and not as a build failure.
    //
    // kotlin-stdlib is handled separately, by kotlin.stdlib.default.dependency=false in
    // gradle.properties, because the Kotlin Gradle plugin adds it rather than this block.
    compileOnly(libs.coroutines.core)
    compileOnly(libs.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.core)
    testImplementation(libs.serialization.json)
}

intellijPlatform {
    pluginConfiguration {
        // Written into plugin.xml at build time. Keeping them here rather than in the file
        // means the version cannot drift from gradle.properties. The toolbox sibling hit
        // exactly that drift with its two name fields: one was changed, the build stayed
        // green, and the UI still showed the old one.
        id = "dev.gounthar.xcpng.intellij"
        name = "XCP-ng (unofficial)"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "262"
            // Deliberately unset. A fixed upper bound is the single most common reason a
            // working plugin stops loading after an IDE upgrade, and there is no evidence of
            // an incompatibility to encode.
            untilBuild = provider { null }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

/**
 * Points git at the tracked hooks directory, once, for whoever builds this.
 *
 * Carried from the toolbox sibling for the reason recorded there: hooks are not installed by
 * cloning, so a hook nobody enables enforces nothing. It sets the LOCAL value on purpose, so a
 * developer with a global hooks directory still gets this project's hooks, and it says so when
 * it overrides one. `-PskipGitHooks` opts out.
 */
// tasks.register, not `by tasks.registering`. The delegated form is deprecated as of
// Gradle 9.6 ("Use the named() API instead") and warns on every build.
val installGitHooks = tasks.register("installGitHooks") {
    description = "Points core.hooksPath at .githooks for this repository."
    group = "verification"

    val hooksDir = ".githooks"
    val projectDir = layout.projectDirectory.asFile
    val gitDir = layout.projectDirectory.file(".git").asFile
    val skip = providers.gradleProperty("skipGitHooks").isPresent

    onlyIf { !skip && gitDir.exists() }

    doLast {
        fun git(vararg args: String): Pair<Int, String> {
            val proc = ProcessBuilder(listOf("git") + args)
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            return proc.waitFor() to out
        }

        val (_, local) = git("config", "--local", "--get", "core.hooksPath")
        if (local == hooksDir) return@doLast

        val (inheritedCode, inherited) = git("config", "--get", "core.hooksPath")
        val (code, out) = git("config", "--local", "core.hooksPath", hooksDir)
        if (code != 0) {
            println("Could not set core.hooksPath: $out")
            return@doLast
        }
        println("Set core.hooksPath to $hooksDir for this repository; commit-message hooks are active.")
        if (inheritedCode == 0 && inherited.isNotEmpty() && inherited != hooksDir) {
            println("  Note: this overrides '$inherited' here only. Use -PskipGitHooks to opt out.")
        }
    }
}

tasks.named("build") { dependsOn(installGitHooks) }
