package com.samyak.iptvminepro

import com.samyak.iptvminepro.model.ProviderType
import com.samyak.iptvminepro.provider.ProviderDetectionEngine
import com.samyak.iptvminepro.provider.ProviderDetectionEngine.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDetectionEngineTest {

    @Test
    fun `classifyContent detects VOD scraper map json even when named movies_json`() {
        val vodScraperJson = """
            {
              "Moviesmod": {
                "name": "Moviesmod",
                "url": "https://moviesmod.fi"
              },
              "Vega": {
                "name": "vegamovies",
                "url": "https://vegamovies.latino"
              },
              "hdhub": {
                "name": "hdhub4u",
                "url": "https://new17.hdhub4u.cl"
              },
              "ExtraFlix": {
                "name": "ExtraFlix",
                "url": "https://e14.extraflix.mobi"
              }
            }
        """.trimIndent()

        val result = ProviderDetectionEngine.classifyContent(
            content = vodScraperJson,
            sourceUrl = "https://example.com/movies.json"
        )

        assertEquals(ProviderType.VEGA, result.providerType)
        assertEquals(SourceKind.VOD_SCRAPER_MAP, result.sourceKind)
        assertEquals(4, result.scraperEntries.size)
        assertEquals("https://moviesmod.fi", result.scraperEntries["Moviesmod"]?.url)
        assertEquals("vegamovies", result.scraperEntries["Vega"]?.name)
        assertEquals("https://e14.extraflix.mobi", result.scraperEntries["ExtraFlix"]?.url)
        assertTrue(ProviderDetectionEngine.isVodScraperMapJson(vodScraperJson))
    }

    @Test
    fun `classifyContent detects LiveTV json channel structure as IPTV`() {
        val liveTvJson = """
            {
              "date": "2026-11-26 12:00:00",
              "channels": {
                "News": [
                  {
                    "name": "DD India",
                    "logo": "https://example.com/logo.png",
                    "group": "News",
                    "source": "LiveTVCollector",
                    "url": "https://cdn.example.com/live/ddindia/master.m3u8"
                  }
                ]
              }
            }
        """.trimIndent()

        val result = ProviderDetectionEngine.classifyContent(
            content = liveTvJson,
            sourceUrl = "https://bugsfreeweb.github.io/LiveTVCollector/LiveTV/India/LiveTV.json"
        )

        assertEquals(ProviderType.IPTV, result.providerType)
        assertEquals(SourceKind.IPTV_JSON, result.sourceKind)
        assertFalse(ProviderDetectionEngine.isVodScraperMapJson(liveTvJson))
    }

    @Test
    fun `classifyContent detects M3U playlist as IPTV`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="News.in" group-title="News",Sample News TV
            https://stream.example.com/news/playlist.m3u8
        """.trimIndent()

        val result = ProviderDetectionEngine.classifyContent(
            content = m3u,
            sourceUrl = "https://iptv-org.github.io/iptv/index.m3u"
        )

        assertEquals(ProviderType.IPTV, result.providerType)
        assertEquals(SourceKind.IPTV_M3U, result.sourceKind)
    }

    @Test
    fun `classifyContent detects Vega manifest array as VOD_MANIFEST`() {
        val manifestJson = """
            [
              {
                "display_name": "Kartoons2",
                "value": "kartoons2",
                "version": "1.01",
                "type": "anime",
                "disabled": false
              }
            ]
        """.trimIndent()

        val result = ProviderDetectionEngine.classifyContent(
            content = manifestJson,
            sourceUrl = "https://raw.githubusercontent.com/vega-org/vega-providers/refs/heads/main/manifest.json"
        )

        assertEquals(ProviderType.VEGA, result.providerType)
        assertEquals(SourceKind.VOD_MANIFEST, result.sourceKind)
        assertEquals(1, result.manifestEntries.size)
        assertEquals("kartoons2", result.manifestEntries.first().value)
        assertEquals("Kartoons2", result.manifestEntries.first().display_name)
    }

    @Test
    fun `classifyContent detects HLS media playlist as DIRECT_VIDEO`() {
        val hls = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:100
            #EXTINF-missing
        """.trimIndent().replace("#EXTINF-missing", "")

        val result = ProviderDetectionEngine.classifyContent(
            content = hls,
            sourceUrl = "https://cdn.example.com/video/index.m3u8"
        )

        assertEquals(ProviderType.VIDEO, result.providerType)
        assertEquals(SourceKind.DIRECT_VIDEO, result.sourceKind)
    }

    @Test
    fun `detectQuick and needsContentInspection classify URLs accurately`() {
        assertEquals(ProviderType.VIDEO, ProviderDetectionEngine.detectQuick("https://cdn.example.com/movie.mp4"))
        assertEquals(ProviderType.IPTV, ProviderDetectionEngine.detectQuick("https://iptv-org.github.io/iptv/index.m3u"))
        assertEquals(ProviderType.VEGA, ProviderDetectionEngine.detectQuick("https://github.com/vega-org/vega-providers"))

        assertFalse(ProviderDetectionEngine.needsContentInspection("https://cdn.example.com/movie.mp4"))
        assertFalse(ProviderDetectionEngine.needsContentInspection("https://iptv-org.github.io/iptv/index.m3u"))
        assertTrue(ProviderDetectionEngine.needsContentInspection("https://example.com/movies.json"))
        assertTrue(ProviderDetectionEngine.needsContentInspection("https://bugsfreeweb.github.io/LiveTVCollector/LiveTV/India/LiveTV.json"))
    }

    @Test
    fun `parseProviderQrPayload extracts URL, JSON, delimited, and invalid payloads`() {
        val m3uQr = ProviderDetectionEngine.parseProviderQrPayload("https://iptv-org.github.io/iptv/index.m3u")
        assertEquals("https://iptv-org.github.io/iptv/index.m3u", m3uQr?.url)
        assertEquals("iptv", m3uQr?.title)

        val liveTvQr = ProviderDetectionEngine.parseProviderQrPayload(
            "https://bugsfreeweb.github.io/LiveTVCollector/LiveTV/India/LiveTV.json"
        )
        assertEquals("https://bugsfreeweb.github.io/LiveTVCollector/LiveTV/India/LiveTV.json", liveTvQr?.url)
        assertEquals("LiveTV", liveTvQr?.title)

        val jsonQr = ProviderDetectionEngine.parseProviderQrPayload(
            """{"title":"Vertex Repo","url":"https://raw.githubusercontent.com/vertex2029/vertex-providers/refs/heads/main/urls.json","userAgent":"MyAgent/1.0"}"""
        )
        assertEquals("Vertex Repo", jsonQr?.title)
        assertEquals(
            "https://raw.githubusercontent.com/vertex2029/vertex-providers/refs/heads/main/urls.json",
            jsonQr?.url
        )
        assertEquals("MyAgent/1.0", jsonQr?.userAgent)

        val delimitedQr = ProviderDetectionEngine.parseProviderQrPayload(
            "My VOD Map|https://example.com/movies.json"
        )
        assertEquals("My VOD Map", delimitedQr?.title)
        assertEquals("https://example.com/movies.json", delimitedQr?.url)

        val invalidQr = ProviderDetectionEngine.parseProviderQrPayload("just some random text")
        assertEquals(null, invalidQr)
    }
}
