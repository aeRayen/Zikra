package com.zikra.tooling.content

/** Length of the longest run of consecutive words found in both lists, in the same order. */
internal fun longestCommonRun(a: List<String>, b: List<String>): Int {
    var best = 0
    var previous = IntArray(b.size + 1)
    for (word in a) {
        val row = runLengths(word, b, previous)
        best = maxOf(best, row.maxOrNull() ?: 0)
        previous = row
    }
    return best
}

/** Row of the classic longest-common-substring table, for one word of the first list. */
private fun runLengths(word: String, b: List<String>, previous: IntArray): IntArray {
    val row = IntArray(b.size + 1)
    for (j in 1..b.size) {
        if (word == b[j - 1]) row[j] = previous[j - 1] + 1
    }
    return row
}
