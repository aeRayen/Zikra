package com.zikra.core.hadith

/**
 * Arabic for the source's English grading terms and scholar names (WP 3.4c, ADR-046). Exact matches
 * only: the common forms cover the great majority of gradings; any other text is shown as published,
 * never guessed. The books' own names are their customary titles.
 */
object HadithTerms {
    private val grades = mapOf(
        "Sahih" to "صحيح",
        "Daif" to "ضعيف",
        "Hasan" to "حسن",
        "Hasan Sahih" to "حسن صحيح",
        "Sahih - Agreed Upon" to "صحيح، متفق عليه",
        "Isnaad Hasan" to "إسناده حسن",
        "Isnaad Sahih" to "إسناده صحيح",
        "Sahih Muslim" to "صحيح، رواه مسلم",
        "Sahih Isnaad" to "صحيح الإسناد",
        "Sahih Lighairihi" to "صحيح لغيره",
        "Daif Isnaad" to "ضعيف الإسناد",
        "Sahih Bukhari" to "صحيح، رواه البخاري",
        "Very Daif" to "ضعيف جدًا",
        "Sahih - Bukhari And Muslim" to "صحيح، رواه البخاري ومسلم",
        "Shadh" to "شاذ",
        "Munkar" to "منكر",
        "Hasan Lighairihi" to "حسن لغيره",
        "Hasan Isnaad" to "حسن الإسناد",
        "Mawdu" to "موضوع",
        "Sahih Muquf" to "صحيح موقوف",
        "Sahih Maqtu" to "صحيح مقطوع",
        "Sahih Hadith" to "حديث صحيح",
        "Sahih Isnaad Maqtu" to "صحيح الإسناد مقطوع",
        "Sahih Isnaad Mauquf" to "صحيح الإسناد موقوف",
        "Hasan Sahih Isnaad" to "حسن صحيح الإسناد",
        "Maqtu" to "مقطوع",
        "Daif Isnaad Maqtu" to "ضعيف الإسناد مقطوع",
        "Sahih Mutawatir" to "صحيح متواتر",
        "Sanad Daif" to "سنده ضعيف",
        "Very Daif Isnaad" to "إسناده ضعيف جدًا",
        "Daif Maqtu" to "ضعيف مقطوع",
        "Daif Muquf" to "ضعيف موقوف",
        "Hasan Maqtu" to "حسن مقطوع",
        "Daif Munkar" to "ضعيف منكر",
        "Sahih Matn" to "صحيح المتن",
        "Sahih Isnaad Mursal" to "صحيح الإسناد مرسل",
        "Munkar Daif" to "منكر ضعيف",
    )

    private val scholars = mapOf(
        "Al-Albani" to "الألباني",
        "Zubair Ali Zai" to "زبير علي زئي",
        "Muhammad Muhyi Al-Din Abdul Hamid" to "محمد محيي الدين عبد الحميد",
        "Shuaib Al Arnaut" to "شعيب الأرناؤوط",
        "Ahmad Muhammad Shakir" to "أحمد محمد شاكر",
        "Bashar Awad Maarouf" to "بشار عواد معروف",
        "Abu Ghuddah" to "عبد الفتاح أبو غدة",
        "Muhammad Fouad Abd al-Baqi" to "محمد فؤاد عبد الباقي",
    )

    private val books = mapOf(
        HadithBook.BUKHARI to "صحيح البخاري",
        HadithBook.MUSLIM to "صحيح مسلم",
        HadithBook.ABU_DAWUD to "سنن أبي داود",
        HadithBook.TIRMIDHI to "جامع الترمذي",
        HadithBook.NASAI to "سنن النسائي",
        HadithBook.IBN_MAJAH to "سنن ابن ماجه",
    )

    private val booksEnglish = mapOf(
        HadithBook.BUKHARI to "Sahih al-Bukhari",
        HadithBook.MUSLIM to "Sahih Muslim",
        HadithBook.ABU_DAWUD to "Sunan Abi Dawud",
        HadithBook.TIRMIDHI to "Jami` at-Tirmidhi",
        HadithBook.NASAI to "Sunan an-Nasa'i",
        HadithBook.IBN_MAJAH to "Sunan Ibn Majah",
    )

    /** The grading in Arabic when it is a known term, else null (show it as published). */
    fun gradeArabic(grade: String): String? = grades[grade.trim()]

    fun scholarArabic(name: String): String? = scholars[name.trim()]

    /** A grading with no content ("-" in the source) is not shown. */
    fun isEmpty(grade: HadithGrade): Boolean = grade.grade.trim().let { it.isEmpty() || it == "-" }

    fun title(book: HadithBook, arabic: Boolean): String = (if (arabic) books else booksEnglish).getValue(book)
}
