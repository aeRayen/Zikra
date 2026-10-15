package com.zikra.tooling.content

import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """
Zikra content pipeline (CONTENT_PACKS_AR.md)

  keygen  --private <file outside the repo> --public <file>
  quran   --sources ../content/sources --out <dir> --private <key> --public <key> --version <v>
  english --sources ../content/sources --out <dir> --private <key> --public <key> --version <v>
  verify  --pack <dir> --public <key>
  places  --sources ../content/sources --out <places.sqlite>
  quran-bundle --sources ../content/sources --out <quran.sqlite>
  places-extract --sources ../content/sources --alternate-names <GeoNames alternateNamesV2.txt>

Paths are relative to the android/ directory when run with:
  ./gradlew :tooling:content-pipeline:run --args="<command> ..."
"""

fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: "help"
    val options = args.drop(1).chunked(2).associate { (k, v) -> k.removePrefix("--") to v }
    fun file(name: String) = File(options[name] ?: fail("missing --$name"))

    try {
        when (command) {
            "keygen" -> {
                val id = Signing.generateKeyPair(file("private"), file("public"))
                println("Key pair created. keyId=$id. Keep the private key OUT of git.")
            }
            "quran", "english" -> buildSignedPack(command, ::file, options["version"] ?: fail("missing --version"))
            "places", "quran-bundle", "places-extract" -> buildBundledData(command, ::file)
            "verify" -> {
                val manifest = Pipeline.verify(file("pack"), Signing.loadPublic(file("public")))
                println("OK: ${manifest.area} ${manifest.editionCode} v${manifest.version}")
            }
            else -> println(USAGE)
        }
    } catch (e: PipelineException) {
        System.err.println("FAILED: ${e.message}")
        exitProcess(1)
    }
}

/** Signed packs for the CDN (WP 1.15). */
private fun buildSignedPack(command: String, file: (String) -> File, version: String) {
    val pipeline = Pipeline(file("sources"))
    val private = Signing.loadPrivate(file("private"))
    val public = Signing.loadPublic(file("public"))
    val out = file("out").apply { mkdirs() }
    if (command == "quran") {
        pipeline.buildQuranText(out, PackKeys(private, public), version)
    } else {
        pipeline.buildEnglishSaheeh(out, PackKeys(private, public), version)
    }
    val manifest = Pipeline.verify(out, public)
    println("Built and verified ${manifest.area} ${manifest.editionCode} v${manifest.version} in ${out.path}")
}

/** Data bundled in the APK, generated at build time (WP 2.1, 2.9). */
private fun buildBundledData(command: String, file: (String) -> File) {
    val pipeline = Pipeline(file("sources"))
    when (command) {
        "places" -> {
            val places = pipeline.buildPlaces(file("out"))
            println("Built ${file("out").path}: ${places.cities.size} cities, ${places.searchKeys.size} search keys")
        }
        "quran-bundle" -> {
            pipeline.buildQuranBundle(file("out"))
            println("Built and checked ${file("out").path}")
        }
        else -> {
            pipeline.extractPlaceNames(file("alternate-names"))
            println("Names file regenerated. Update its sha256 in SOURCES.lock.json after review.")
        }
    }
}

private fun fail(message: String): Nothing = throw PipelineException(message)
