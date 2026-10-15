package com.zikra.app.ui

/** Reader actions; [onToggleNight] receives whether night colours are currently shown. */
class ReaderActions(
    val onBack: () -> Unit,
    val onSmaller: () -> Unit,
    val onBigger: () -> Unit,
    val onToggleNight: (Boolean) -> Unit,
    val onAbout: () -> Unit,
)
