import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

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
        // NOT "...xcpng.intellij", and this is a Marketplace rule rather than taste. The
        // Plugin Verifier refuses the artifact outright:
        //   Invalid plugin descriptor 'plugin.xml'. The plugin ID
        //   'dev.gounthar.xcpng.intellij' should not include the word 'intellij'.
        //   (TemplateWordInPluginId, reported since 2024-03-26)
        // It is a structural refusal, so verification does not merely warn, it schedules zero
        // checks and fails. There is a -mute switch; muting a rule that blocks publication
        // would only move the failure to the day it matters.
        //
        // The repository directory is still named xcpng-intellij-plugin, deliberately. The
        // constraint is on the plugin ID, and "IntelliJ Platform" is the correct name of the
        // SDK this targets, so the directory keeps it and the published identity does not.
        id = "dev.gounthar.xcpng.ide"
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

    // THE ONE CHECK THAT ANSWERS THE REPOSITORY'S OWN PREMISE.
    //
    // A green `build` compiles against IDEA and proves nothing about loading in another IDE,
    // because a <depends> is resolved at LOAD time. The whole argument for this repository is
    // that a com.intellij.modules.platform-only plugin is portable across the IDE family, and
    // until this block existed that argument rested on documentation.
    //
    // WebStorm is named explicitly and first, because it is the IDE the request that prompted
    // this repository was actually about. Verifying only against IDEA would be a fixture that
    // agrees with the belief being tested.
    pluginVerification {
        ides {
            // create(), not ide(). The DSL method is `create`; `ide` does not exist and fails
            // as an unresolved reference in the build script, which is a cheap failure but a
            // confusing one because every blog post writes it the other way. Read from
            // IntelliJPlatformExtension$PluginVerification$Ides, which also offers
            // recommended(), latest {}, select {}, current() and local(...).
            create(IntelliJPlatformType.WebStorm, libs.versions.idea.get())
            create(IntelliJPlatformType.IntellijIdea, libs.versions.idea.get())
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
