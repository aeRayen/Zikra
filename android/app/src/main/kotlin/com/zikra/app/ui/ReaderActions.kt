package com.zikra.app.ui

/** Reader actions; [onToggleNight] receives whether night colours are currently shown. */
interface ReaderActions {
    val onBack: () -> Unit
    val onSmaller: () -> Unit
    val onBigger: () -> Unit
    val onToggleNight: (Boolean) -> Unit
    val onAbout: () -> Unit

    /** Ayah number in the open surah. */
    val onToggleBookmark: (Int) -> Unit

    /** The ayah at the top of the screen, for «continue reading». */
    val onRead: (Int) -> Unit

    /** Receives whether the translation is currently shown. */
    val onToggleTranslation: (Boolean) -> Unit
}
