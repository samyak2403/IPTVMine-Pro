package com.samyak.iptvminepro

import com.samyak.iptvminepro.model.isPlayableMediaUrl
import com.samyak.iptvminepro.utils.TitleUtils
import org.junit.Assert.*
import org.junit.Test

class TitleUtilsAndStreamTest {

    @Test
    fun testTitleCleaningAndSearchExtraction() {
        val raw1 = "Pathaan [Hindi] (2023) 1080p WEB-DL"
        val clean1 = TitleUtils.getSearchableTitle(raw1)
        assertEquals("Pathaan", clean1)

        val raw2 = "Loki Season 2 [Multi Audio]"
        val clean2 = TitleUtils.getSearchableTitle(raw2)
        assertEquals("Loki", clean2)

        val raw3 = "Stranger Things S04 Episode 01 [Dual Audio]"
        val clean3 = TitleUtils.getSearchableTitle(raw3)
        assertEquals("Stranger Things", clean3)

        val raw4 = "Deadpool & Wolverine (2024)"
        val clean4 = TitleUtils.getSearchableTitle(raw4)
        assertEquals("Deadpool & Wolverine", clean4)

        val raw5 = "Money Heist: Part 5 [Hindi-English]"
        val clean5 = TitleUtils.getSearchableTitle(raw5)
        assertEquals("Money Heist", clean5)
    }

    @Test
    fun testIsPlayableMediaUrl() {
        assertTrue(isPlayableMediaUrl("https://example.com/stream.m3u8"))
        assertTrue(isPlayableMediaUrl("https://example.com/video.mp4?token=123"))
        assertTrue(isPlayableMediaUrl("https://example.com/video.mkv#t=10"))
        assertTrue(isPlayableMediaUrl("https://example.com/hls/live/manifest.m3u8"))
        assertTrue(isPlayableMediaUrl("https://pixeldrain.com/api/file/xyz123"))
        assertTrue(isPlayableMediaUrl("https://pixeldrain.dev/api/file/xyz123"))
        assertTrue(isPlayableMediaUrl("https://worker-stream.workers.dev/file"))
        assertTrue(isPlayableMediaUrl("https://pub-cdn.r2.dev/movie.mp4"))
        assertTrue(isPlayableMediaUrl("https://fastdl.beer/dl/video123"))
        assertTrue(isPlayableMediaUrl("https://hubcdn.club/stream/xyz"))
        
        assertFalse(isPlayableMediaUrl("https://officialmoviebox.com/movies/pathaan-detail-r0p639744"))
        assertFalse(isPlayableMediaUrl("https://vegamovies.lease/download/index.html"))
        assertFalse(isPlayableMediaUrl(""))
        assertFalse(isPlayableMediaUrl(null))
    }

    @Test
    fun testMojibakeRepairInCleanTitle() {
        assertEquals(
            "Season 2 \u2022 2160p \u2022 WEB-DL \u2022 H265 \u2022 DoVi-HDR",
            TitleUtils.cleanTitle("Season 2 \u00E2\u0080\u00A2 2160p \u00E2\u0080\u00A2 WEB-DL \u00E2\u0080\u00A2 H265 \u00E2\u0080\u00A2 DoVi-HDR")
        )
        assertEquals(
            "Season 1 \u2022 Hindi-English \u2022 480p",
            TitleUtils.cleanTitle("Season 1 â¢ Hindi-English â¢ 480p")
        )
    }

    @Test
    fun testExtractEpisodeBadgeMeta() {
        val stickMeta = TitleUtils.extractEpisodeBadgeMeta(
            episodeTitle = "Episode-05 6.39 GB",
            seasonTitle = "Season 2 â¢ 2160p â¢ WEB-DL â¢ H265 â¢ DoVi-HDR"
        )
        assertEquals("Episode-05", stickMeta.displayTitle)
        assertEquals("2160p", stickMeta.qualityBadge)
        assertEquals("S02", stickMeta.seasonLabel)
        assertEquals("EP 05", stickMeta.episodeLabel)
        assertEquals(2, stickMeta.seasonNumber)
        assertEquals(5, stickMeta.episodeNumber)
        assertEquals("6.39 GB", stickMeta.bottomRightTag)

        val structuredMeta = TitleUtils.extractEpisodeBadgeMeta(
            episodeTitle = "The Sunset Journey",
            seasonTitle = "Season 1",
            rawSeasonNumber = "1",
            rawEpisodeNumber = "4",
            rawDuration = "42:18"
        )
        assertEquals("The Sunset Journey", structuredMeta.displayTitle)
        assertEquals("HD", structuredMeta.qualityBadge)
        assertEquals("S01", structuredMeta.seasonLabel)
        assertEquals("EP 04", structuredMeta.episodeLabel)
        assertEquals("42:18", structuredMeta.bottomRightTag)
    }

    @Test
    fun testNuxtRegexExtractionPattern() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>MovieBox</title></head>
            <body>
            <script id="__NUXT_DATA__" type="application/json">
            [{"hasResource":true,"id":"r0p639744","title":"Pathaan"},1]
            </script>
            <div>Page Content</div>
            </body>
            </html>
        """.trimIndent()

        val regex = Regex("""<script[^>]*id="__NUXT_DATA__"[^>]*>([\s\S]*?)<\/script>""", RegexOption.IGNORE_CASE)
        val match = regex.find(sampleHtml)
        assertNotNull(match)
        val extractedJson = match!!.groupValues[1].trim()
        assertTrue(extractedJson.contains("\"hasResource\":true"))
        assertTrue(extractedJson.contains("\"id\":\"r0p639744\""))
    }
}
