package com.psycode.spotiflac.ui.component.managefiles

import java.text.Normalizer
import kotlin.math.max

private val trackPrefixRegex =
    Regex("^\\s*(?:\\(|\\[)?\\d{1,3}(?:\\)|\\])?[\\s._\\-\\u2013\\u2014)+]*")
private val bracketedRegex = Regex("[\\[\\(\\{][^\\]\\)\\}]+[\\]\\)\\}]")
private val separatorsRegex = Regex("[\\s._\\-\\u2013\\u2014/+|,:;]+")
private val nonAlphaNumRegex = Regex("[^\\p{L}\\p{N}]+")
private val spacesRegex = Regex("\\s+")
private val dupSeparatorsRegex = Regex("[\\s._\\-\\u2013\\u2014/+|]+")

fun fileExtension(name: String): String = name.substringAfterLast('.', "").lowercase()

fun fileBaseName(name: String): String = if ('.' in name) name.substringBeforeLast('.') else name

fun parentDirectoryLabel(path: String): String {
    if (path.isBlank()) return "/"
    val normalizedPath = path.replace('\\', '/').trim('/')
    val directoryPath = normalizedPath.substringBeforeLast('/', "")
    if (directoryPath.isEmpty()) return "/"
    return directoryPath.substringAfterLast('/', directoryPath)
}

fun stripTrackNumberPrefix(value: String): String = value.replace(trackPrefixRegex, "")

fun removeBracketed(value: String): String = value.replace(bracketedRegex, " ")

fun normalizeCore(raw: String): String {
    if (raw.isBlank()) return ""
    val normalizedPunctuation = raw
        .replace('\u2013', '-')
        .replace('\u2014', '-')
        .replace('\u2019', '\'')
    val nfkd = Normalizer.normalize(normalizedPunctuation, Normalizer.Form.NFKD)
    val noMarks = buildString(nfkd.length) {
        for (ch in nfkd) {
            if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) append(ch)
        }
    }
    return noMarks
        .lowercase()
        .replace(separatorsRegex, " ")
        .replace(nonAlphaNumRegex, " ")
        .replace(spacesRegex, " ")
        .trim()
}

fun duplicateKeyOf(rawBaseName: String): String {
    val stripped = removeBracketed(stripTrackNumberPrefix(rawBaseName))
    val nfkd = Normalizer.normalize(stripped, Normalizer.Form.NFKD)
    val noMarks = buildString(nfkd.length) {
        for (ch in nfkd) {
            if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) append(ch)
        }
    }
    return noMarks
        .lowercase()
        .replace(dupSeparatorsRegex, " ")
        .replace(nonAlphaNumRegex, " ")
        .replace(spacesRegex, " ")
        .trim()
}

fun damerauLevenshteinSimilarity(first: String, second: String): Double {
    if (first.isEmpty() || second.isEmpty()) return 0.0
    val firstLength = first.length
    val secondLength = second.length
    if (firstLength == 0 || secondLength == 0) return 0.0

    val dp = Array(firstLength + 1) { IntArray(secondLength + 1) }
    for (i in 0..firstLength) dp[i][0] = i
    for (j in 0..secondLength) dp[0][j] = j

    for (i in 1..firstLength) {
        for (j in 1..secondLength) {
            val cost = if (first[i - 1] == second[j - 1]) 0 else 1
            var value = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
            if (i > 1 && j > 1 && first[i - 1] == second[j - 2] && first[i - 2] == second[j - 1]) {
                value = minOf(value, dp[i - 2][j - 2] + 1)
            }
            dp[i][j] = value
        }
    }

    val distance = dp[firstLength][secondLength]
    return 1.0 - distance.toDouble() / max(firstLength, secondLength).toDouble()
}

fun calculateListTitleScore(name: String, size: Long, groupTitle: String): Int {
    val normalizedTitle = normalizeCore(groupTitle)
    val baseNormalized = normalizeCore(fileBaseName(name))
    var score = 0
    if (normalizedTitle.isNotEmpty() && baseNormalized.contains(normalizedTitle)) score += 3
    if (size >= 80L * 1024 * 1024) score += 1
    return score
}

fun buildTitleCandidates(normalizedBaseName: String, artistNormalized: String): List<String> {
    val values = linkedSetOf<String>()
    if (normalizedBaseName.isNotEmpty()) {
        values += normalizedBaseName
        values += normalizedBaseName.replace(" ", "")
    }

    val withoutTrackNumber = normalizeCore(stripTrackNumberPrefix(normalizedBaseName))
    if (withoutTrackNumber.isNotEmpty()) {
        values += withoutTrackNumber
        values += withoutTrackNumber.replace(" ", "")
    }

    if (artistNormalized.isNotEmpty() && withoutTrackNumber.startsWith("$artistNormalized ")) {
        val cut = withoutTrackNumber.removePrefix("$artistNormalized ").trim()
        if (cut.isNotEmpty()) {
            values += cut
            values += cut.replace(" ", "")
        }
    }

    if (artistNormalized.isNotEmpty() && withoutTrackNumber.endsWith(" $artistNormalized")) {
        val cut = withoutTrackNumber.removeSuffix(" $artistNormalized").trim()
        if (cut.isNotEmpty()) {
            values += cut
            values += cut.replace(" ", "")
        }
    }

    return values.filter { it.isNotBlank() }
}
