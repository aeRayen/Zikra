package com.zikra.buildlogic

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

/**
 * Runs the content pipeline's `places` command: pinned sources in [sourcesDir] -> places.sqlite in
 * [outputDir] (WP 2.1). Declared inputs/outputs make it up-to-date checked and configuration-cache safe.
 */
abstract class GeneratePlacesDatabase : JavaExec() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourcesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        mainClass.set("com.zikra.tooling.content.MainKt")
        val sources = sourcesDir
        val output = outputDir
        argumentProviders.add(
            CommandLineArgumentProvider {
                listOf(
                    "places",
                    "--sources",
                    sources.get().asFile.absolutePath,
                    "--out",
                    output.file("places.sqlite").get().asFile.absolutePath,
                )
            },
        )
    }
}
