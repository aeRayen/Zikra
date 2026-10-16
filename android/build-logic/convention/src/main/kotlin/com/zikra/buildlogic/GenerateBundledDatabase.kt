package com.zikra.buildlogic

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

/**
 * Runs a content-pipeline command that turns the pinned sources in [sourcesDir] into a SQLite asset
 * [fileName] in [outputDir] (places.sqlite, quran.sqlite). Bundled in the APK, so the APK signature
 * protects it; declared inputs/outputs make it up-to-date checked and configuration-cache safe.
 */
abstract class GenerateBundledDatabase : JavaExec() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourcesDir: DirectoryProperty

    /** Pipeline command, e.g. "places" or "quran-bundle". */
    @get:Input
    abstract val command: Property<String>

    @get:Input
    abstract val fileName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        mainClass.set("com.zikra.tooling.content.MainKt")
        val sources = sourcesDir
        val output = outputDir
        val cmd = command
        val name = fileName
        argumentProviders.add(
            CommandLineArgumentProvider {
                listOf(
                    cmd.get(),
                    "--sources",
                    sources.get().asFile.absolutePath,
                    "--out",
                    output.file(name.get()).get().asFile.absolutePath,
                )
            },
        )
    }
}
