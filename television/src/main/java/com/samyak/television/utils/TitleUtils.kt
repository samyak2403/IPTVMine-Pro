package com.samyak.television.utils

import java.util.Locale

data class EpisodeBadgeMeta(
    val displayTitle: String,
    val qualityBadge: String,
    val seasonLabel: String?,
    val episodeLabel: String?,
    val bottomRightTag: String?
)

object TitleUtils {
    private val qualityRules = listOf(
        Regex("(?i)\\b2160p\\b") to "2160p",
        Regex("(?i)\\b(?:4k|uhd)\\b") to "4K",
        Regex("(?i)\\b1080p\\b") to "1080p",
        Regex("(?i)\\b720p\\b") to "720p",
        Regex("(?i)\\b480p\\b") to "480p",
        Regex("(?i)\\b360p\\b") to "360p"
    )
    private val seasonRegex = Regex("(?i)\\b(?:season|s)\\s*[-:.]?\\s*(\\d{1,2})\\b")
    private val seEpRegex = Regex("(?i)\\bS\\d{1,2}\\s*E(\\d{1,3})\\b")
    private val episodeRegex = Regex("(?i)\\b(?:episode|ep|e)\\s*[-:.#]?\\s*(\\d{1,3})\\b")
    private val digitsRegex = Regex("(\\d+)")
    private val sizeRegex = Regex("(?i)\\b(\\d+(?:\\.\\d+)?\\s*(?:GB|MB|TB))\\b")

    /**
     * Clean movie or series title by repairing common UTF-8 mojibake, removing private use
     * characters (icon glyphs) and control characters, and collapsing whitespace.
     */
    fun cleanTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""

        val repaired = title
            .replace("\u00E2\u0080\u00A2", " \u2022 ")
            .replace("â€¢", " \u2022 ")
            .replace("â¢", " \u2022 ")
            .replace("\u00E2\u0080\u0093", "\u2013")
            .replace("â€“", "\u2013")
            .replace("\u00E2\u0080\u0094", "\u2014")
            .replace("â€”", "\u2014")
            .replace("\u00E2\u0080\u0099", "\u2019")
            .replace("â€™", "\u2019")
            .replace("\u00E2\u0080\u0098", "\u2018")
            .replace("â€˜", "\u2018")
            .replace("\u00E2\u0080\u009C", "\u201C")
            .replace("â€œ", "\u201C")
            .replace("\u00E2\u0080\u009D", "\u201D")
            .replace("\u00C2\u00A0", " ")

        val sb = StringBuilder()
        var i = 0
        while (i < repaired.length) {
            val codePoint = repaired.codePointAt(i)
            val charCount = Character.charCount(codePoint)

            // Check if codePoint is in Private Use Area or is control character
            val isPua = (codePoint in 0xE000..0xF8FF) ||
                        (codePoint in 0xF0000..0xFFFFD) ||
                        (codePoint in 0x100000..0x10FFFD)

            val isControl = Character.isISOControl(codePoint)

            if (!isPua && !isControl) {
                sb.appendRange(repaired, i, i + charCount)
            }
            i += charCount
        }

        return sb.toString()
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    /**
     * Extracts structured display metadata (title, quality pill, Sxx/EP xx badges, and
     * duration/file size tag) from episode and parent season strings.
     */
    fun extractEpisodeBadgeMeta(
        episodeTitle: String,
        seasonTitle: String = "",
        rawSeasonNumber: String? = null,
        rawEpisodeNumber: String? = null,
        rawDuration: String? = null,
        fallbackQualitySource: String = ""
    ): EpisodeBadgeMeta {
        val cleanEp = cleanTitle(episodeTitle)
        val cleanSeason = cleanTitle(seasonTitle)
        val cleanFallback = cleanTitle(fallbackQualitySource)
        val combinedForQuality = "$cleanEp $cleanSeason $cleanFallback"

        val qualityBadge = qualityRules.firstOrNull { (pattern, _) ->
            pattern.containsMatchIn(combinedForQuality)
        }?.second ?: "HD"

        val seasonNum = rawSeasonNumber
            ?.let { digitsRegex.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?: seasonRegex.find(cleanEp)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: seasonRegex.find(cleanSeason)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val seasonLabel = seasonNum?.let { String.format(Locale.US, "S%02d", it) }

        val episodeNum = rawEpisodeNumber
            ?.let { digitsRegex.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?: seEpRegex.find(cleanEp)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: episodeRegex.find(cleanEp)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val episodeLabel = episodeNum?.let {
            if (it < 100) String.format(Locale.US, "EP %02d", it) else "EP $it"
        }

        val sizeInEp = sizeRegex.find(cleanEp)?.groupValues?.getOrNull(1)
        val extractedSize = sizeInEp ?: sizeRegex.find(cleanFallback)?.groupValues?.getOrNull(1)
        val bottomRightTag = rawDuration?.trim()?.takeIf { it.isNotEmpty() } ?: extractedSize

        val strippedTitle = if (rawDuration.isNullOrBlank() && sizeInEp != null) {
            cleanEp
                .replace(Regex("[\\s\u2022\\-|/\\(\\[]*${Regex.escape(sizeInEp)}[\\)\\]]*"), " ")
                .replace(Regex("[\\s\u2022\\-|/]+$"), "")
                .replace("\\s+".toRegex(), " ")
                .trim()
        } else {
            cleanEp
        }
        val displayTitle = strippedTitle.ifBlank { cleanEp }

        return EpisodeBadgeMeta(
            displayTitle = displayTitle,
            qualityBadge = qualityBadge,
            seasonLabel = seasonLabel,
            episodeLabel = episodeLabel,
            bottomRightTag = bottomRightTag
        )
    }

    /**
     * Extracts the clean core title suitable for search across multiple providers.
     * Strips out language tags like [Hindi], audio tags, release years, season suffixes, etc.
     */
    fun getSearchableTitle(title: String?): String {
        val base = cleanTitle(title)
        if (base.isBlank()) return ""
        return base
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("(?i)\\b(season\\s*\\d+|s\\d+|episode\\s*\\d+|ep\\s*\\d+|part\\s*\\d+)\\b.*"), "")
            .replace(Regex("(?i)\\b(hindi|english|tamil|telugu|kannada|malayalam|dubbed|dual audio|multi audio|org audio|web-dl|bluray|hdrip|hdcam|720p|1080p|480p|4k)\\b.*"), "")
            .replace(Regex("[-–—:|]+"), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
