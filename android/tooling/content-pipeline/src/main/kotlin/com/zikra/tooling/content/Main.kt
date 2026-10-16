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
  translation-bundle --sources ../content/sources --out <translation-en.sqlite>
  adhkar-bundle --sources ../content/sources --out <adhkar.sqlite>
  hisn-shamela-text --sources ../content/sources --html-dir <saved shamela.ws/book/31307 pages>
  hisn-review --sources ../content/sources --out <review.md>
  hadith-cited --sources ../content/sources --books-dir <dir with ara-bukhari.json ... from hadith-api>
  hisn-verify --sources ../content/sources --out <verification.md>
  hadith-packs --sources ../content/sources --books-dir <hadith-api downloads> --out <dir> --private <key> --public <key> --version <v>
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
            "quran", "english", "hadith-packs", "verify" -> runPacks(command, ::file, options["version"])
            "places", "quran-bundle", "translation-bundle", "places-extract" -> buildBundledData(command, ::file)
            "adhkar-bundle" -> {
                HisnPipeline(file("sources")).buildAdhkarBundle(file("out"))
                println("Built ${file("out").path}")
            }
            "hisn-shamela-text", "hisn-review", "hadith-cited", "hisn-verify" -> runHisn(command, ::file)
            else -> println(USAGE)
        }
    } catch (e: PipelineException) {
        System.err.println("FAILED: ${e.message}")
        exitProcess(1)
    }
}

/** Signed packs: built (Quran, translation, hadith) or verified. */
private fun runPacks(command: String, file: (String) -> File, version: String?) {
    when (command) {
        "verify" -> {
            val manifest = Pipeline.verify(file("pack"), Signing.loadPublic(file("public")))
            println("OK: ${manifest.area} ${manifest.editionCode} v${manifest.version}")
        }
        "hadith-packs" -> buildHadithPacks(file, version ?: fail("missing --version"))
        else -> buildSignedPack(command, file, version ?: fail("missing --version"))
    }
}

/** Hisn al-Muslim: references (WP 2.14a) and their independent check (ADR-042). */
private fun runHisn(command: String, file: (String) -> File) {
    val pipeline = HisnPipeline(file("sources"))
    when (command) {
        "hisn-shamela-text" -> pipeline.extractShamelaHisn(file("html-dir"))
        "hisn-review" -> {
            val refs = pipeline.hisnReferences()
            HisnReview.write(parseHisnMuslim(File(file("sources"), "hisnmuslim/ar/")), refs, file("out"))
            println(refs.groupingBy { it.status }.eachCount())
        }
        "hadith-cited" -> pipeline.extractCitedHadiths(file("books-dir"))
        else -> {
            val verified = pipeline.verifyReferences()
            HisnVerificationReport.write(verified, file("out"))
            println(verified.results.groupingBy { it.result }.eachCount())
            println("Open: ${verified.open.size}, stale verdicts: ${verified.stale.size}")
        }
    }
}

/** The six books as signed packs for GitHub Releases (WP 3.4a, ADR-046), each verified after signing. */
private fun buildHadithPacks(file: (String) -> File, version: String) {
    val keys = PackKeys(Signing.loadPrivate(file("private")), Signing.loadPublic(file("public")))
    val cited = HisnPipeline(file("sources")).citedHadiths()
    HadithPacks.build(cited, file("books-dir"), file("out"), keys, version).forEach { manifest ->
        val book = HadithBook.entries.first { HadithPacks.edition(it) == manifest.editionCode }
        val packDir = file("out")
        val text = File(packDir, HadithPacks.manifestName(book)).readText()
        val signed = Signing.open(Signing.decode(text), keys.publicKey)
        // As the app will: the download, then the unpacked database.
        val download = signed.files.single()
        val gz = File(packDir, download.path)
        ensure(gz.length() == download.sizeBytes && sha256Hex(gz.readBytes()) == download.sha256) { "Bad ${gz.name}" }
        val unpacked = File(packDir, "check-" + HadithPacks.assetName(book))
        HadithPacks.unpack(gz, download, unpacked)
        HadithPacks.check(unpacked, signed.verification)
        unpacked.delete()
        val size = manifest.files.single().sizeBytes
        println("${manifest.editionCode}: ${manifest.verification.itemCount} hadiths, $size bytes")
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
        "translation-bundle" -> {
            pipeline.buildTranslationBundle(file("out"))
            println("Built and checked ${file("out").path}")
        }
        else -> {
            pipeline.extractPlaceNames(file("alternate-names"))
            println("Names file regenerated. Update its sha256 in SOURCES.lock.json after review.")
        }
    }
}

private fun fail(message: String): Nothing = throw PipelineException(message)
