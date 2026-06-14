package com.samyak.iptvminepro.utils

object TitleUtils {
    /**
     * Clean movie or series title by removing private use characters (icon glyphs) and cleaning up whitespace.
     */
    fun cleanTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""

        val sb = StringBuilder()
        var i = 0
        while (i < title.length) {
            val codePoint = title.codePointAt(i)
            val charCount = Character.charCount(codePoint)

            // Check if codePoint is in Private Use Area or is control character
            val isPua = (codePoint in 0xE000..0xF8FF) || 
                        (codePoint in 0xF0000..0xFFFFD) || 
                        (codePoint in 0x100000..0x10FFFD)

            val isControl = Character.isISOControl(codePoint)

            if (!isPua && !isControl) {
                sb.appendRange(title, i, i + charCount)
            }
            i += charCount
        }

        return sb.toString()
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
