package com.samyak.television.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log
import com.samyak.television.model.*
import com.samyak.television.utils.TitleUtils.cleanTitle
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VegaProviderRunner(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    @Volatile
    private var webViewReady = CompletableDeferred<Unit>()
    private val bridge = AndroidBridge()
    private val runnerMutex = Mutex()

    @Volatile
    private var currentUserAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val cookieJar = object : CookieJar {
        private val cookieManager = android.webkit.CookieManager.getInstance()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val urlString = url.toString()
            cookies.forEach { cookie ->
                cookieManager.setCookie(urlString, cookie.toString())
            }
            cookieManager.flush()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val urlString = url.toString()
            val cookieString = cookieManager.getCookie(urlString) ?: ""
            if (cookieString.isEmpty()) return emptyList()
            
            val cookies = mutableListOf<Cookie>()
            val cookiePairs = cookieString.split(";")
            cookiePairs.forEach { pair ->
                val trimmed = pair.trim()
                if (trimmed.isNotEmpty()) {
                    try {
                        val cookie = Cookie.parse(url, trimmed)
                        if (cookie != null) {
                            cookies.add(cookie)
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
            return cookies
        }
    }

    private val appDns: Dns = com.samyak.player.dns.DnsPreferenceManager.getDns(context)

    private val httpClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(cookieJar)
        .dns(appDns)
        .addInterceptor { chain ->
            // BUG-008 fix: removed Thread.sleep() from interceptor to avoid blocking thread pool.
            var lastException: Exception? = null
            for (attempt in 0..2) {
                try {
                    val response = chain.proceed(chain.request())
                    if (response.isSuccessful || attempt == 2 || response.code in listOf(404, 403, 401)) {
                        return@addInterceptor response
                    }
                    response.close()
                } catch (e: Exception) {
                    lastException = e
                    // Do NOT sleep here — OkHttp threads are shared; sleeping starves other requests.
                }
            }
            throw lastException ?: Exception("Request failed after 3 attempts")
        }
        .addInterceptor { chain ->
            val original = chain.request()
            val hasUserAgent = original.header("User-Agent") != null
            if (hasUserAgent) {
                chain.proceed(original)
            } else {
                val modified = original.newBuilder()
                    .header("User-Agent", CHROME_USER_AGENT)
                    .build()
                chain.proceed(modified)
            }
        }
        .build()

    companion object {
        private const val TAG = "VegaProviderRunner"
        private const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        const val DEFAULT_VEGA_REPO = "https://raw.githubusercontent.com/vega-org/vega-providers/refs/heads/main"
        const val FALLBACK_VEGA_REPO = "https://raw.githubusercontent.com/himanshu8443/vega-providers/refs/heads/main"
    }

    private var baseUrls = mutableMapOf<String, String>()
    private var baseUrlsFetched = false
    private val baseUrlsLock = Any()

    private fun ensureBaseUrlsFetched() {
        if (baseUrlsFetched) return
        synchronized(baseUrlsLock) {
            if (baseUrlsFetched) return
            val modflixSources = listOf(
                "https://raw.githubusercontent.com/vega-org/vega-providers/refs/heads/main/urls.json",
                "https://raw.githubusercontent.com/himanshu8443/vega-providers/refs/heads/main/modflix.json",
                "https://himanshu8443.github.io/providers/modflix.json"
            )
            var loaded = false
            for (source in modflixSources) {
                try {
                    val request = Request.Builder().url(source).build()
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: "{}"
                            val json = JSONObject(body)
                            for (key in json.keys()) {
                                try {
                                    val obj = json.getJSONObject(key)
                                    val url = obj.optString("url", "")
                                    if (url.isNotEmpty()) baseUrls[key] = url
                                } catch (_: Exception) { /* skip malformed entries */ }
                            }
                            if (baseUrls.isNotEmpty()) {
                                loaded = true
                                Log.d(TAG, "Loaded ${baseUrls.size} base URLs from $source")
                            }
                        } else {
                            Log.w(TAG, "modflix source returned ${response.code}: $source")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch modflix from $source: ${e.message}")
                }
                if (loaded) break
            }
            if (!loaded) {
                Log.w(TAG, "All modflix sources failed, using hardcoded fallback URLs")
            }
            val hardcodedDefaults = mapOf(
                "Vega" to "https://new1.vegamovies.lease",
                "vegamovies" to "https://new1.vegamovies.lease",
                "vegastream" to "https://new1.vegamovies.lease",
                "Moviesmod" to "https://moviesmod.uk.com",
                "Animeflix" to "https://ww3.animeflix.ltd",
                "Topmovies" to "https://moviesleech.club",
                "topmovies" to "https://moviesleech.club",
                "UhdMovies" to "https://uhdmovies.us.org",
                "uhdmovies" to "https://uhdmovies.us.org",
                "lux" to "https://rogmovies.one",
                "luxMovies" to "https://rogmovies.one",
                "drive" to "https://new3.moviesdrive.beer/",
                "multi" to "https://m1.multimovies.courses",
                "extra" to "https://extramovies.ist",
                "hdhub" to "https://new8.hdhub4u.cl",
                "hdhub4u" to "https://new8.hdhub4u.cl",
                "kat" to "https://new.katmoviehd.top",
                "katmovies" to "https://new.katmoviehd.top",
                "autoEmbed" to "https://autoembed.me",
                "tokyoinsider" to "https://www.tokyoinsider.com",
                "primewire" to "https://primewire.pw",
                "rive" to "https://rivestream.live",
                "kissKh" to "https://kisskh.is",
                "showbox" to "https://www.showbox.media",
                "protonMovies" to "https://m.protonmovies.space",
                "filmyfly" to "https://filmyfiy.co.in",
                "4khdhub" to "https://4khdhub.one",
                "moviezwap" to "https://www.moviezwap.ltd/",
                "movieBox" to "https://api6.aoneroom.com",
                "movieBoxWeb" to "https://officialmoviebox.com",
                "1cinevood" to "https://new1.cinevood.capital",
                "cinevood" to "https://new1.cinevood.capital",
                "kmmovies" to "https://kmmovies.cam",
                "kmMovies" to "https://kmmovies.cam",
                "zeefliz" to "https://zeefliz.club",
                "movies4u" to "https://movies4u.cr",
                "bolly4u" to "https://bolly4u.yoga",
                "bollyflix" to "https://bollyflix.army",
                "hubcloud" to "https://hubcloud.band",
                "gofile" to "https://gofile.io",
                "eonMovies" to "https://new4.eonmovies.click",
                "dramacool" to "https://asianc.id",
                "asianc" to "https://asianc.id",
                "cinemaLuxe" to "https://cinemalux.cyou",
                "anikoto" to "https://anikototv.to",
                "kickAssAnime" to "https://kaa.lt",
                "animesalt" to "https://animesalt.in",
                "animeworld" to "https://watchanimeworld.one",
                "movielinkdb" to "https://ex5wfv.movielinkbd.li",
                "movielinkbd" to "https://ex5wfv.movielinkbd.li",
                "skymovieshd" to "https://new1.skymovieshd.clinic"
            )
            for ((k, v) in hardcodedDefaults) {
                if (!baseUrls.containsKey(k)) baseUrls[k] = v
            }
            baseUrlsFetched = true
        }
    }

    init {
        mainHandler.post {
            setupWebView()
        }
    }

    private fun setupWebView() {
        if (webViewReady.isCompleted) {
            webViewReady = CompletableDeferred()
        }
        isCompatInjected = false
        loadedScrapers.clear()

        // Safely destroy old webView if exists to avoid memory and execution leaks
        webView?.let { oldWebView ->
            try {
                oldWebView.stopLoading()
                oldWebView.clearHistory()
                try { oldWebView.clearCache(true) } catch (e: Exception) { Log.w(TAG, "clearCache error: ${e.message}") }
                oldWebView.loadUrl("about:blank")
                oldWebView.onPause()
                oldWebView.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying old WebView: ${e.message}")
            }
        }

        val webViewInstance = WebView(context.applicationContext)
        webViewInstance.settings.javaScriptEnabled = true
        webViewInstance.settings.domStorageEnabled = true
        webViewInstance.settings.allowContentAccess = true
        webViewInstance.settings.mediaPlaybackRequiresUserGesture = false
        webViewInstance.addJavascriptInterface(bridge, "AndroidBridge")
        webViewInstance.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "Headless WebView loaded: $url")
                isCompatInjected = true
                webViewReady.complete(Unit)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                return true
            }

            @Suppress("DeprecatedCallableMember")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                Log.w(TAG, "WebView load error: $description")
                webViewReady.complete(Unit)
            }

            override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                Log.w(TAG, "WebView SSL error: $error")
                handler?.cancel()
                webViewReady.complete(Unit)
            }

            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                val didCrash = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    detail?.didCrash() ?: false
                } else {
                    true
                }
                Log.w(TAG, "Headless WebView render process gone (didCrash=$didCrash). Recovering WebView...")
                isCompatInjected = false
                loadedScrapers.clear()
                bridge.clearPendingCallbacks()
                mainHandler.post {
                    setupWebView()
                }
                return true
            }
        }
        webViewInstance.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    val msg = "[WebView Console] ${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
                    if (consoleMessage.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                        Log.e(TAG, msg)
                    } else if (consoleMessage.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.WARNING) {
                        Log.w(TAG, msg)
                    } else {
                        Log.d(TAG, msg)
                    }
                }
                return true
            }
        }
        
        val initHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <script>
                    ${getCompatJs()}
                    console.log("Headless JS environment ready with providerContext");
                </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()
        webViewInstance.loadDataWithBaseURL("https://raw.githubusercontent.com/", initHtml, "text/html", "UTF-8", null)
        webView = webViewInstance
    }

    private suspend fun awaitWebViewReady() {
        try {
            withTimeout(15_000) { webViewReady.await() }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "WebView readiness gate timed out after 15s, proceeding with evaluation")
            webViewReady.complete(Unit)
        }
    }

    // Helper to evaluate JS and await the result with automatic retry on timeout or failure
    private suspend fun evalJsAsync(jsCode: String): String {
        var lastException: Exception? = null
        for (attempt in 1..3) {
            val deferred = CompletableDeferred<String>()
            val callbackId = bridge.registerCallback(deferred)
            val processedJs = jsCode.replace("__CALLBACK_ID__", callbackId)
            
            val wrappedJs = """
                (async function() {
                    try {
                        $processedJs
                    } catch(err) {
                        console.error("JS Error:", err);
                        window.AndroidBridge.onError('$callbackId', err.message || err.toString());
                    }
                })();
            """.trimIndent()
            
            awaitWebViewReady()
            
            withContext(Dispatchers.Main) {
                if (webView == null) {
                    setupWebView()
                    withContext(Dispatchers.Default) {
                        awaitWebViewReady()
                    }
                }
                webView?.evaluateJavascript(wrappedJs, null)
            }
            
            try {
                return withTimeout(25_000) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "JS evaluation timed out after 25s on attempt $attempt")
                bridge.removeCallback(callbackId)
                deferred.cancel()
                lastException = Exception("JS evaluation timed out")
                if (attempt < 3) {
                    Log.d(TAG, "Re-initializing WebView due to timeout before attempt ${attempt + 1}")
                    withContext(Dispatchers.Main) {
                        setupWebView()
                    }
                    delay(500)
                }
            } catch (e: Exception) {
                bridge.removeCallback(callbackId)
                deferred.cancel()
                lastException = e
                if (attempt < 3) {
                    Log.d(TAG, "JS evaluation error on attempt $attempt (${e.message}), recovering WebView...")
                    withContext(Dispatchers.Main) {
                        setupWebView()
                    }
                    delay(500)
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: Exception("JS evaluation timed out after 3 attempts")
    }

    private suspend fun evalJsDirect(jsCode: String) {
        awaitWebViewReady()
        withContext(Dispatchers.Main) {
            if (webView == null) {
                setupWebView()
                withContext(Dispatchers.Default) {
                    awaitWebViewReady()
                }
            }
            webView?.evaluateJavascript(jsCode, null)
        }
    }

    fun resolveRepoUrl(input: String): String {
        var trimmed = input.trim().removeSuffix("/")
        if (trimmed.isBlank() || trimmed.equals("vega", ignoreCase = true) || trimmed.equals("@vega", ignoreCase = true) ||
            trimmed.equals("vega-org", ignoreCase = true) || trimmed.equals("@vega-org", ignoreCase = true)) {
            return DEFAULT_VEGA_REPO
        }

        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            if (trimmed.startsWith("github.com/", ignoreCase = true) || trimmed.startsWith("raw.githubusercontent.com/", ignoreCase = true)) {
                trimmed = "https://$trimmed"
            }
        }

        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val urlLower = trimmed.lowercase()
            if (urlLower.startsWith("https://github.com/") || urlLower.startsWith("http://github.com/")) {
                val prefixLength = if (urlLower.startsWith("https://")) "https://github.com/".length else "http://github.com/".length
                val parts = trimmed.substring(prefixLength).split("/")
                if (parts.size >= 2) {
                    var author = parts[0]
                    if (author.equals("vega", ignoreCase = true)) {
                        author = "vega-org"
                    }
                    val repo = parts[1]
                    val branch = if (parts.size >= 4 && parts[2] == "tree") parts[3] else "main"
                    return "https://raw.githubusercontent.com/$author/$repo/refs/heads/$branch"
                }
            } else if (urlLower.startsWith("https://raw.githubusercontent.com/") || urlLower.startsWith("http://raw.githubusercontent.com/")) {
                return trimmed.replace("raw.githubusercontent.com/vega/vega-providers", "raw.githubusercontent.com/vega-org/vega-providers")
            }
            return trimmed
        }

        val author = trimmed.removePrefix("@")
        val cleanAuthor = if (author.equals("vega", ignoreCase = true)) "vega-org" else author
        return "https://raw.githubusercontent.com/$cleanAuthor/vega-providers/refs/heads/main"
    }

    private val scraperCacheDir by lazy {
        java.io.File(context.cacheDir, "vega_scraper_cache").apply { mkdirs() }
    }

    private fun getCacheFileForUrl(url: String): java.io.File {
        val hash = try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val digest = md.digest(url.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            url.replace("[^a-zA-Z0-9]".toRegex(), "_").takeLast(64)
        }
        return java.io.File(scraperCacheDir, "mod_$hash.js")
    }

    private suspend fun fetchFile(fileUrl: String): String {
        val cacheFile = getCacheFileForUrl(fileUrl)
        val maxAgeMs = 6 * 60 * 60 * 1000L  // 6 hours — providers update frequently
        if (cacheFile.exists() && cacheFile.length() > 0 && (System.currentTimeMillis() - cacheFile.lastModified() < maxAgeMs)) {
            try {
                val cached = cacheFile.readText()
                if (cached.isNotBlank()) return cached
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read cache for $fileUrl: ${e.message}")
            }
        }

        val request = Request.Builder().url(fileUrl).build()
        return withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (cacheFile.exists() && cacheFile.length() > 0) {
                            Log.w(TAG, "Fetch $fileUrl returned ${response.code}, using disk cache fallback")
                            return@withContext cacheFile.readText()
                        }
                        throw Exception("Failed to fetch $fileUrl: code ${response.code}")
                    }
                    val body = response.body?.string() ?: ""
                    if (body.isNotBlank()) {
                        try {
                            cacheFile.writeText(body)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to write scraper cache for $fileUrl: ${e.message}")
                        }
                    }
                    body
                }
            } catch (e: Exception) {
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    Log.w(TAG, "Fetch $fileUrl failed (${e.message}), using disk cache fallback")
                    return@withContext cacheFile.readText()
                }
                throw e
            }
        }
    }

    private var isCompatInjected = false

    private fun getCompatJs(): String {
        return """
            function splitByComma(str) {
                const parts = [];
                let current = "";
                let inQuote = null;
                let parenDepth = 0;
                for (let i = 0; i < str.length; i++) {
                    const char = str[i];
                    if (inQuote) {
                        if (char === inQuote && (i === 0 || str[i - 1] !== '\\')) {
                            inQuote = null;
                        }
                        current += char;
                    } else {
                        if ((char === '"' || char === "'") && (i === 0 || str[i - 1] !== '\\')) {
                            inQuote = char;
                            current += char;
                        } else if (char === '(') {
                            parenDepth++;
                            current += char;
                        } else if (char === ')') {
                            parenDepth--;
                            current += char;
                        } else if (char === ',' && parenDepth === 0) {
                            parts.push(current.trim());
                            current = "";
                        } else {
                            current += char;
                        }
                    }
                }
                if (current) {
                    parts.push(current.trim());
                }
                return parts;
            }

            function safeQuery(root, selector) {
                if (!selector) return [];
                const parts = splitByComma(selector);
                if (parts.length > 1) {
                    const results = [];
                    const seen = new Set();
                    parts.forEach(part => {
                        const elms = safeQuery(root, part);
                        elms.forEach(el => {
                            if (!seen.has(el)) {
                                seen.add(el);
                                results.push(el);
                            }
                        });
                    });
                    return results;
                }
                
                selector = selector.trim();
                
                const notContainsRegex = /:not\(:contains\((['"]?)(.*?)\1\)\)/;
                const notMatch = selector.match(notContainsRegex);
                if (notMatch) {
                    const fullMatch = notMatch[0];
                    const excludeText = notMatch[2];
                    const matchIndex = selector.indexOf(fullMatch);
                    
                    let leftPart = selector.substring(0, matchIndex).trim();
                    let rightPart = selector.substring(matchIndex + fullMatch.length).trim();
                    
                    if (!leftPart) {
                        leftPart = "*";
                    }
                    
                    let leftElements = safeQuery(root, leftPart);
                    let filteredElements = leftElements.filter(el => {
                        const text = el.textContent || "";
                        return !text.includes(excludeText);
                    });
                    
                    if (!rightPart) {
                        return filteredElements;
                    }
                    
                    const firstChar = rightPart[0];
                    const isCombinator = firstChar === ' ' || firstChar === '>' || firstChar === '+' || firstChar === '~';
                    if (isCombinator) {
                        const finalResults = [];
                        const seen = new Set();
                        filteredElements.forEach(el => {
                            const rightElements = safeQuery(el, rightPart);
                            rightElements.forEach(rel => {
                                if (!seen.has(rel)) {
                                    seen.add(rel);
                                    finalResults.push(rel);
                                }
                            });
                        });
                        return finalResults;
                    } else {
                        return filteredElements.filter(el => safeMatches(el, rightPart, root.ownerDocument || root));
                    }
                }
                
                const containsRegex = /:contains\((['"]?)(.*?)\1\)/;
                const match = selector.match(containsRegex);
                if (!match) {
                    try {
                        return root.querySelectorAll ? Array.from(root.querySelectorAll(selector)) : [];
                    } catch (e) {
                        console.warn("Failed querySelectorAll for selector:", selector, e);
                        return [];
                    }
                }
                
                const fullMatch = match[0];
                const searchText = match[2];
                const matchIndex = selector.indexOf(fullMatch);
                
                let leftPart = selector.substring(0, matchIndex).trim();
                let rightPart = selector.substring(matchIndex + fullMatch.length).trim();
                
                if (!leftPart) {
                    leftPart = "*";
                }
                
                let leftElements = safeQuery(root, leftPart);
                let filteredElements = leftElements.filter(el => {
                    const text = el.textContent || "";
                    return text.includes(searchText);
                });
                
                if (!rightPart) {
                    return filteredElements;
                }
                
                const firstChar = rightPart[0];
                const isCombinator = firstChar === ' ' || firstChar === '>' || firstChar === '+' || firstChar === '~';
                if (isCombinator) {
                    const finalResults = [];
                    const seen = new Set();
                    filteredElements.forEach(el => {
                        const rightElements = safeQuery(el, rightPart);
                        rightElements.forEach(rel => {
                            if (!seen.has(rel)) {
                                seen.add(rel);
                                finalResults.push(rel);
                            }
                        });
                    });
                    return finalResults;
                } else {
                    return filteredElements.filter(el => safeMatches(el, rightPart, root.ownerDocument || root));
                }
            }

            function safeMatches(el, selector, doc) {
                if (!el || !selector) return false;
                try {
                    if (!selector.includes(':contains')) {
                        return el.matches(selector);
                    }
                    const simpleContainsRegex = /^([^:\s]+):contains\((['"]?)(.*?)\1\)$/;
                    const match = selector.trim().match(simpleContainsRegex);
                    if (match) {
                        const baseSelector = match[1];
                        const text = match[3];
                        return el.matches(baseSelector) && (el.textContent || "").includes(text);
                    }
                    const allMatched = safeQuery(doc || el.ownerDocument || document, selector);
                    return allMatched.includes(el);
                } catch (e) {
                    return false;
                }
            }

            window.process = window.process || { env: {} };

            window.atob = window.atob || function(str) { 
                return window.AndroidBridge.atob(str);
            };
            window.btoa = window.btoa || function(str) {
                return window.AndroidBridge.btoa(str);
            };

            window.vegaModules = {};

            window.Crypto = window.Crypto || {
                digestStringAsync: async function(algorithm, data) {
                    return window.AndroidBridge.digestString(algorithm, data);
                },
                CryptoDigestAlgorithm: {
                    SHA1: 'SHA-1',
                    SHA256: 'SHA-256',
                    SHA384: 'SHA-384',
                    SHA512: 'SHA-512',
                    MD5: 'MD5'
                }
            };

            window.fetch = async function(url, options) {
                try {
                    if (typeof url === 'string' && !url.startsWith('http')) {
                        if (url.startsWith('//')) {
                            url = 'https:' + url;
                        } else if (url.startsWith('/')) {
                            url = window.location.origin + url;
                        }
                    }
                    
                    let method = 'GET';
                    let headers = {};
                    let data = null;
                    
                    if (options) {
                        method = options.method || 'GET';
                        if (options.headers) {
                            if (options.headers instanceof Headers) {
                                options.headers.forEach((value, key) => {
                                    headers[key] = value;
                                });
                            } else if (typeof options.headers === 'object') {
                                headers = Object.assign({}, options.headers);
                            }
                        }
                        data = options.body;
                    }
                    
                    const responseStr = await window.AndroidBridge.httpFetch(
                        url, 
                        method, 
                        JSON.stringify(headers), 
                        data ? (typeof data === 'string' ? data : JSON.stringify(data)) : null
                    );
                    
                    const responseObj = JSON.parse(responseStr);
                    const bodyText = responseObj.body || '';
                    const responseHeaders = new Headers();
                    if (responseObj.headers) {
                        for (const [k, v] of Object.entries(responseObj.headers)) {
                            if (Array.isArray(v)) {
                                v.forEach(item => responseHeaders.append(k, item));
                            } else if (v !== null && v !== undefined) {
                                responseHeaders.set(k, String(v));
                            }
                        }
                    }
                    
                    const finalUrl = responseObj.url || url;
                    return {
                        ok: responseObj.status >= 200 && responseObj.status < 300,
                        status: responseObj.status,
                        statusText: responseObj.status >= 200 && responseObj.status < 300 ? 'OK' : 'Error',
                        headers: responseHeaders,
                        url: finalUrl,
                        redirected: !!(responseObj.url && responseObj.url !== url),
                        text: async () => bodyText,
                        json: async () => JSON.parse(bodyText),
                        arrayBuffer: async () => {
                            const encoder = new TextEncoder();
                            return encoder.encode(bodyText).buffer;
                        },
                        blob: async () => new Blob([bodyText]),
                        clone: function() {
                            return Object.assign({}, this);
                        }
                    };
                } catch (e) {
                    console.error("fetch failed via AndroidBridge:", e);
                    throw new TypeError('Failed to fetch: ' + (e.message || e));
                }
            };

            window.providerContext = {
                getBaseUrl: async function(name) {
                    return window.AndroidBridge.getBaseUrl(name);
                },
                openWebView: async function(url, options) {
                    try {
                        const optStr = options ? JSON.stringify(options) : "{}";
                        const res = await window.AndroidBridge.openWebView(url, optStr);
                        return JSON.parse(res);
                    } catch (e) {
                        console.warn("openWebView error:", e);
                        return { data: "", cookies: "", cookie: "", cookieMap: {}, userAgent: "", url: url };
                    }
                },
                kvStore: {
                    get: async function(key) {
                        try {
                            const val = window.AndroidBridge.kvGet(key);
                            if (val === null || val === undefined || val === '') return null;
                            try { return JSON.parse(val); } catch(e) { return val; }
                        } catch(e) { return null; }
                    },
                    set: async function(key, val) {
                        try {
                            const str = (typeof val === 'string') ? val : JSON.stringify(val);
                            window.AndroidBridge.kvSet(key, str);
                        } catch(e) {}
                    },
                    delete: async function(key) {
                        try { window.AndroidBridge.kvDelete(key); } catch(e) {}
                    },
                    remove: async function(key) {
                        try { window.AndroidBridge.kvDelete(key); } catch(e) {}
                    },
                    clear: async function() {
                        try { window.AndroidBridge.kvClear(); } catch(e) {}
                    }
                },
                axios: async function(configOrUrl, config) {
                    let url = "";
                    let method = "GET";
                    let headers = {};
                    let data = null;
                    let cfg = {};
                    if (typeof configOrUrl === 'string') {
                        url = configOrUrl;
                        if (config) {
                            cfg = config;
                            method = config.method || "GET";
                            headers = config.headers || {};
                            data = config.data || null;
                        }
                    } else if (configOrUrl) {
                        cfg = configOrUrl;
                        url = configOrUrl.url || "";
                        method = configOrUrl.method || "GET";
                        headers = configOrUrl.headers || {};
                        data = configOrUrl.data || null;
                    }

                    // Handle maxRedirects === 0 (vital for vegadrive, gofile, hubcloud 302 Location extraction)
                    if (cfg && (cfg.maxRedirects === 0 || cfg.maxRedirects === '0')) {
                        headers['x-max-redirects'] = '0';
                    }

                    // Handle params — append query parameters to URL (critical for Gofile, Movy, etc.)
                    const params = (cfg && cfg.params) || {};
                    if (typeof params === 'object' && Object.keys(params).length > 0) {
                        const separator = url.includes('?') ? '&' : '?';
                        const queryParts = [];
                        for (const [k, v] of Object.entries(params)) {
                            if (v !== null && v !== undefined) {
                                queryParts.push(encodeURIComponent(k) + '=' + encodeURIComponent(String(v)));
                            }
                        }
                        if (queryParts.length > 0) {
                            url = url + separator + queryParts.join('&');
                        }
                    }

                    try {
                        const responseStr = await window.AndroidBridge.httpFetch(
                            url, 
                            method, 
                            JSON.stringify(headers), 
                            data ? (typeof data === 'string' ? data : JSON.stringify(data)) : null
                        );
                        const parsedRes = JSON.parse(responseStr);
                        
                        let responseData = parsedRes.body;
                        try {
                            responseData = JSON.parse(parsedRes.body);
                        } catch(e) {
                        }
                        
                        const validateStatus = (cfg && cfg.validateStatus) || (status => status >= 200 && status < 300);
                        if (!validateStatus(parsedRes.status)) {
                            const err = new Error('Request failed with status code ' + parsedRes.status + ': ' + (typeof responseData === 'string' ? responseData.substring(0, 100) : ''));
                            err.response = {
                                data: responseData,
                                status: parsedRes.status,
                                headers: parsedRes.headers || {}
                            };
                            throw err;
                        }
                        
                        const resolvedUrl = parsedRes.url || url;
                        return {
                            data: responseData,
                            status: parsedRes.status,
                            headers: parsedRes.headers || {},
                            config: cfg,
                            url: resolvedUrl,
                            request: { responseURL: resolvedUrl }
                        };
                    } catch (e) {
                        if (e && e.response && (e.response.status === 404 || e.response.status === 403)) {
                            console.warn("axios fetch failed (" + (e.response && e.response.status) + "):", e.message || e);
                        } else {
                            console.error("axios fetch failed: ", e);
                        }
                        throw e;
                    }
                },
                cheerio: {
                    load: function(html) {
                        const safeHtml = html;
                        const parser = new DOMParser();
                        const doc = parser.parseFromString(safeHtml, 'text/html');
                        
                        const selectorFn = function(selector, context) {
                            if (typeof selector === 'string') {
                                if (selector.trim().startsWith('<')) {
                                    const tempDiv = doc.createElement('div');
                                    tempDiv.innerHTML = selector;
                                    return wrapElements(Array.from(tempDiv.childNodes), doc);
                                }
                                
                                let elements;
                                if (context) {
                                    let rawContext;
                                    if (typeof context === 'string') {
                                        const tempDiv = doc.createElement('div');
                                        tempDiv.innerHTML = context;
                                        rawContext = tempDiv;
                                    } else if (context.jquery) {
                                        rawContext = context[0]?._el || context[0];
                                    } else if (context.length !== undefined) {
                                        rawContext = context[0]?._el || context[0];
                                    } else if (context._el) {
                                        rawContext = context._el;
                                    } else {
                                        rawContext = context;
                                    }
                                    if (rawContext) {
                                        elements = safeQuery(rawContext, selector);
                                    } else if (rawContext && rawContext.length && rawContext[0]) {
                                        elements = safeQuery(rawContext[0], selector);
                                    } else if (rawContext && rawContext._el) {
                                        elements = safeQuery(rawContext._el, selector);
                                    } else {
                                        elements = [];
                                    }
                                } else {
                                    elements = safeQuery(doc, selector);
                                }
                                return wrapElements(elements, doc);
                            }
                            if (selector instanceof Node || selector instanceof Element) {
                                return wrapElements([selector], doc);
                            }
                            if (selector && selector._el) {
                                return wrapElements([selector._el], doc);
                            }
                            if (Array.isArray(selector)) {
                                return wrapElements(selector.map(s => s._el || s).filter(Boolean), doc);
                            }
                            if (selector && selector.length !== undefined && selector.toArray) {
                                return selector;
                            }
                            return wrapElements([], doc);
                        };
                        
                        selectorFn.html = function() {
                            return doc.documentElement.innerHTML;
                        };
                        selectorFn.root = function() {
                            return wrapElements([doc.documentElement], doc);
                        };
                        
                        return selectorFn;
                    }
                },
                commonHeaders: {
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                }
            };

            window.providerContext.providerValue = '';
            window.providerContext.axios.get = (url, config) => window.providerContext.axios(url, { ...config, method: 'GET' });
            window.providerContext.axios.post = (url, data, config) => window.providerContext.axios(url, { ...config, method: 'POST', data });
            window.providerContext.axios.put = (url, data, config) => window.providerContext.axios(url, { ...config, method: 'PUT', data });
            window.providerContext.axios.delete = (url, config) => window.providerContext.axios(url, { ...config, method: 'DELETE' });
            window.providerContext.axios.head = (url, config) => window.providerContext.axios(url, { ...config, method: 'HEAD' });
            window.providerContext.axios.patch = (url, data, config) => window.providerContext.axios(url, { ...config, method: 'PATCH', data });
            window.providerContext.axios.request = (config) => window.providerContext.axios(config);

            function createMockNode(el) {
                if (!el) return null;
                if (el._mockNode) return el._mockNode;
                
                const type = el.nodeType === 1 ? 'tag' : (el.nodeType === 3 ? 'text' : 'comment');
                const mockNode = {
                    _el: el,
                    type: type,
                    attribs: {},
                    get children() {
                        return Array.from(el.childNodes).map(child => createMockNode(child)).filter(Boolean);
                    },
                    get parent() {
                        return createMockNode(el.parentNode);
                    },
                    get prev() {
                        return createMockNode(el.previousSibling);
                    },
                    get next() {
                        return createMockNode(el.nextSibling);
                    }
                };
                
                if (type === 'tag') {
                    mockNode.name = el.localName || el.tagName?.toLowerCase() || '';
                } else {
                    mockNode.data = el.nodeValue || el.textContent || '';
                }
                
                if (el.attributes) {
                    for (let attr of el.attributes) {
                        mockNode.attribs[attr.name] = attr.value;
                    }
                }
                
                el._mockNode = mockNode;
                return mockNode;
            }

            function wrapElements(elements, doc, prevObject) {
                const wrapped = {
                    length: elements.length,
                    prevObject: prevObject || null,
                    end: function() {
                        return this.prevObject || wrapElements([], doc);
                    },
                    [Symbol.iterator]: function*() {
                        for (let i = 0; i < this.length; i++) {
                            yield this[i];
                        }
                    },
                    text: function() {
                        if (elements.length === 0) return "";
                        return Array.from(elements).map(el => el.textContent).join(" ").trim();
                    },
                    attr: function(name, value) {
                        if (elements.length === 0) return value !== undefined ? this : undefined;
                        if (!name) return undefined;
                        if (value !== undefined) {
                            elements.forEach(el => el.setAttribute(name, value));
                            return this;
                        }
                        return elements[0].getAttribute(name) || undefined;
                    },
                    find: function(selector) {
                        const found = [];
                        elements.forEach(el => {
                            safeQuery(el, selector).forEach(subEl => {
                                found.push(subEl);
                            });
                        });
                        return wrapElements(found, doc, this);
                    },
                    next: function(selector) {
                        const nextEls = [];
                        elements.forEach(el => {
                            let next = el.nextElementSibling;
                            while (next) {
                                if (!selector || safeMatches(next, selector, doc)) {
                                    nextEls.push(next);
                                    break;
                                }
                                next = next.nextElementSibling;
                            }
                        });
                        return wrapElements(nextEls, doc, this);
                    },
                    prev: function(selector) {
                        const prevEls = [];
                        elements.forEach(el => {
                            let prev = el.previousElementSibling;
                            while (prev) {
                                if (!selector || safeMatches(prev, selector, doc)) {
                                    prevEls.push(prev);
                                    break;
                                }
                                prev = prev.previousElementSibling;
                            }
                        });
                        return wrapElements(prevEls, doc, this);
                    },
                    nextUntil: function(selector) {
                        const els = [];
                        elements.forEach(el => {
                            let next = el.nextElementSibling;
                            while (next) {
                                if (selector && safeMatches(next, selector, doc)) break;
                                els.push(next);
                                next = next.nextElementSibling;
                            }
                        });
                        return wrapElements(els, doc, this);
                    },
                    nextAll: function(selector) {
                        const nextEls = [];
                        elements.forEach(el => {
                            let next = el.nextElementSibling;
                            while (next) {
                                if (!selector || safeMatches(next, selector, doc)) {
                                    nextEls.push(next);
                                }
                                next = next.nextElementSibling;
                            }
                        });
                        const uniqueEls = Array.from(new Set(nextEls));
                        return wrapElements(uniqueEls, doc, this);
                    },
                    prevAll: function(selector) {
                        const prevEls = [];
                        elements.forEach(el => {
                            let prev = el.previousElementSibling;
                            while (prev) {
                                if (!selector || safeMatches(prev, selector, doc)) {
                                    prevEls.push(prev);
                                }
                                prev = prev.previousElementSibling;
                            }
                        });
                        const uniqueEls = Array.from(new Set(prevEls));
                        return wrapElements(uniqueEls, doc, this);
                    },
                    parents: function(selector) {
                        const parentsEls = [];
                        elements.forEach(el => {
                            let par = el.parentElement;
                            while (par) {
                                if (!selector || safeMatches(par, selector, doc)) {
                                    parentsEls.push(par);
                                }
                                par = par.parentElement;
                            }
                        });
                        const uniqueEls = Array.from(new Set(parentsEls));
                        return wrapElements(uniqueEls, doc, this);
                    },
                    clone: function() {
                        const cloned = elements.map(el => el.cloneNode(true));
                        return wrapElements(cloned, doc, this);
                    },
                    closest: function(selector) {
                        const closestEls = [];
                        elements.forEach(el => {
                            if (el.closest) {
                                const closest = el.closest(selector);
                                if (closest) closestEls.push(closest);
                            }
                        });
                        return wrapElements(closestEls, doc, this);
                    },
                    parent: function() {
                        const parents = [];
                        elements.forEach(el => {
                            if (el.parentElement) parents.push(el.parentElement);
                        });
                        return wrapElements(parents, doc, this);
                    },
                    children: function(selector) {
                        const childEls = [];
                        elements.forEach(el => {
                            if (el.children) {
                                Array.from(el.children).forEach(child => {
                                    if (!selector || safeMatches(child, selector, doc)) {
                                        childEls.push(child);
                                    }
                                });
                            }
                        });
                        return wrapElements(childEls, doc, this);
                    },
                    first: function() {
                        return wrapElements(elements.length > 0 ? [elements[0]] : [], doc, this);
                    },
                    last: function() {
                        return wrapElements(elements.length > 0 ? [elements[elements.length - 1]] : [], doc, this);
                    },
                    eq: function(index) {
                        return wrapElements(index >= 0 && index < elements.length ? [elements[index]] : [], doc, this);
                    },
                    each: function(callback) {
                        elements.forEach((el, idx) => {
                            const mockNode = createMockNode(el);
                            callback.call(mockNode, idx, mockNode);
                        });
                        return this;
                    },
                    map: function(callback) {
                        const res = Array.from(elements).map((el, idx) => {
                            const mockNode = createMockNode(el);
                            return callback.call(mockNode, idx, mockNode);
                        });
                        
                        function wrapMapResult(arr) {
                            const nativeSlice = arr.slice;
                            arr.get = function(index) {
                                if (index === undefined) return arr;
                                return arr[index];
                            };
                            arr.toArray = function() { return arr; };
                            arr.slice = function(start, end) {
                                return wrapMapResult(nativeSlice.call(arr, start, end));
                            };
                            return arr;
                        }
                        
                        return wrapMapResult(res);
                    },
                    filter: function(selector) {
                        if (typeof selector === 'string') {
                            return wrapElements(elements.filter(el => safeMatches(el, selector, doc)), doc, this);
                        } else if (typeof selector === 'function') {
                            return wrapElements(elements.filter((el, idx) => {
                                const mockNode = createMockNode(el);
                                return selector.call(mockNode, idx, mockNode);
                            }), doc, this);
                        }
                        return this;
                    },
                    html: function() {
                        if (elements.length === 0) return "";
                        return elements[0].innerHTML;
                    },
                    val: function() {
                        if (elements.length === 0) return undefined;
                        return elements[0].value;
                    },
                    hasClass: function(className) {
                        return elements.some(el => el.classList && el.classList.contains(className));
                    },
                    is: function(selector) {
                        return elements.some(el => safeMatches(el, selector, doc));
                    },
                    toArray: function() {
                        return Array.from(elements).map(el => wrapElements([el], doc, this));
                    },
                    get: function(index) {
                        if (index === undefined) {
                            return elements.map(el => createMockNode(el));
                        }
                        return createMockNode(elements[index]);
                    },
                    data: function(name, value) {
                        if (name === undefined) {
                            if (elements.length === 0) return {};
                            const dataObj = {};
                            const el = elements[0];
                            if (el.attributes) {
                                for (let attr of el.attributes) {
                                    if (attr.name.startsWith('data-')) {
                                        const key = attr.name.substring(5).replace(/-([a-z])/g, g => g[1].toUpperCase());
                                        dataObj[key] = attr.value;
                                    }
                                }
                            }
                            return dataObj;
                        }
                        if (value !== undefined) {
                            const attrName = 'data-' + name.replace(/([A-Z])/g, '-$1').toLowerCase();
                            elements.forEach(el => {
                                if (el.setAttribute) el.setAttribute(attrName, value);
                            });
                            return this;
                        }
                        const attrName = 'data-' + name.replace(/([A-Z])/g, '-$1').toLowerCase();
                        return this.attr(attrName);
                    },
                    contents: function() {
                        const allNodes = [];
                        elements.forEach(el => {
                            Array.from(el.childNodes).forEach(child => allNodes.push(child));
                        });
                        return wrapElements(allNodes, doc, this);
                    },
                    siblings: function(selector) {
                        const siblingEls = [];
                        elements.forEach(el => {
                            if (el.parentElement) {
                                Array.from(el.parentElement.children).forEach(sibling => {
                                    if (sibling !== el && (!selector || safeMatches(sibling, selector, doc))) {
                                        siblingEls.push(sibling);
                                    }
                                });
                            }
                        });
                        return wrapElements(siblingEls, doc, this);
                    },
                    addClass: function(className) {
                        elements.forEach(el => el.classList && el.classList.add(className));
                        return this;
                    },
                    removeClass: function(className) {
                        elements.forEach(el => el.classList && el.classList.remove(className));
                        return this;
                    },
                    remove: function() {
                        elements.forEach(el => el.parentNode && el.parentNode.removeChild(el));
                        return this;
                    },
                    replaceWith: function(content) {
                        elements.forEach(el => {
                            if (typeof content === 'string') {
                                const tempDiv = doc.createElement('div');
                                tempDiv.innerHTML = content;
                                while (tempDiv.firstChild) {
                                    el.parentNode.insertBefore(tempDiv.firstChild, el);
                                }
                                el.parentNode.removeChild(el);
                            }
                        });
                        return this;
                    },
                    not: function(selector) {
                        if (typeof selector === 'string') {
                            return wrapElements(elements.filter(el => !safeMatches(el, selector, doc)), doc, this);
                        }
                        return this;
                    },
                    index: function() {
                        if (elements.length === 0) return -1;
                        const el = elements[0];
                        if (!el.parentElement) return -1;
                        return Array.from(el.parentElement.children).indexOf(el);
                    },
                    slice: function(start, end) {
                        return wrapElements(Array.from(elements).slice(start, end), doc, this);
                    }
                };
                
                wrapped.attribs = {};
                for (let i = 0; i < elements.length; i++) {
                    const el = elements[i];
                    if (el) {
                        const mockNode = createMockNode(el);
                        wrapped[i] = mockNode;
                        if (i === 0) {
                            wrapped.attribs = mockNode.attribs;
                        }
                    }
                }
                return wrapped;
            }
        """.trimIndent()
    }

    private suspend fun injectCompatibilityLayer() {
        if (isCompatInjected) {
            try {
                val check = evalJsAsync("""
                    const exists = (typeof window.providerContext !== 'undefined' && !!window.providerContext && !!window.providerContext.axios);
                    window.AndroidBridge.onResult('__CALLBACK_ID__', exists ? '1' : '0');
                """.trimIndent())
                if (check == "1") return
            } catch (e: Exception) {
                // Check failed, re-inject below
            }
        }

        val compatJs = getCompatJs()
        evalJsAsync(compatJs + "\nwindow.AndroidBridge.onResult('__CALLBACK_ID__', 'ok');")
        isCompatInjected = true
    }

    private val loadedScrapers = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private suspend fun loadScraperInternal(repoUrl: String, providerValue: String) {
        val escapedProviderValue = providerValue.replace("\\", "\\\\").replace("'", "\\'")
        val resolvedRepo = resolveRepoUrl(repoUrl).ifBlank { DEFAULT_VEGA_REPO }
        if (loadedScrapers.contains("${resolvedRepo}::${providerValue}")) {
            val isActuallyLoaded = try {
                val check = evalJsAsync("""
                    const isReady = (
                        typeof window.vegaModules !== 'undefined' &&
                        !!window.vegaModules['$escapedProviderValue'] &&
                        !!window.vegaModules['$escapedProviderValue']['stream']
                    );
                    window.AndroidBridge.onResult('__CALLBACK_ID__', isReady ? '1' : '0');
                """.trimIndent())
                check == "1"
            } catch (e: Exception) {
                false
            }

            if (isActuallyLoaded) {
                evalJsDirect("""
                    if (typeof window.providerContext === 'undefined') { window.providerContext = {}; }
                    window.providerContext.providerValue = '$escapedProviderValue';
                """.trimIndent())
                return
            } else {
                Log.w(TAG, "Scraper $providerValue was in loadedScrapers but missing from JS runtime. Reloading...")
                loadedScrapers.remove("${resolvedRepo}::${providerValue}")
            }
        }
        
        injectCompatibilityLayer()
        evalJsDirect("""
            if (typeof window.providerContext === 'undefined') { window.providerContext = {}; }
            window.providerContext.providerValue = '$escapedProviderValue';
        """.trimIndent())
        
        val reposToTry = linkedSetOf(
            resolvedRepo,
            DEFAULT_VEGA_REPO,
            FALLBACK_VEGA_REPO
        )
        
        val modules = listOf("catalog", "posts", "meta", "stream", "episodes")
        for (module in modules) {
            var js: String? = null
            var lastErr: Exception? = null
            for (repo in reposToTry) {
                try {
                    val fileUrl = "$repo/dist/$providerValue/$module.js"
                    val content = fetchFile(fileUrl)
                    if (content.isNotBlank()) {
                        js = content
                        break
                    }
                } catch (e: Exception) {
                    lastErr = e
                }
            }

            if (js == null) {
                if (lastErr?.message?.contains("code 404") == true) {
                    Log.d(TAG, "Module $module not found for $providerValue (optional)")
                } else {
                    Log.w(TAG, "Failed to load module $module for $providerValue: ${lastErr?.message}")
                }
                continue
            }

            try {
                if (providerValue.equals("vega", ignoreCase = true) && module == "stream") {
                    js = patchVegaStreamJs(js)
                }
                if (providerValue.equals("vega", ignoreCase = true) && module == "meta") {
                    js = patchVegaMetaJs(js)
                }
                if ((providerValue.equals("hdhub", ignoreCase = true) || providerValue.equals("hdhub4u", ignoreCase = true)) && module == "meta") {
                    js = patchHdhubMetaJs(js)
                }
                if ((providerValue.equals("hdhub", ignoreCase = true) || providerValue.equals("hdhub4u", ignoreCase = true)) && module == "stream") {
                    js = patchHdhubStreamJs(js)
                }
                if (providerValue.contains("netflixMirror", ignoreCase = true) || providerValue.contains("netmirror", ignoreCase = true)) {
                    js = patchNetmirrorJs(js)
                }
                if (providerValue.contains("torrentio", ignoreCase = true) ||
                    providerValue.contains("everything", ignoreCase = true) ||
                    js.contains("CINEMETA_BASE_URL", ignoreCase = true) ||
                    js.contains("cinemeta", ignoreCase = true)) {
                    js = patchCinemetaMetaJs(js)
                }
                if (providerValue.equals("kissKh", ignoreCase = true) && module == "posts") {
                    js = patchKissKhPostsJs(js)
                }
                if (providerValue.equals("movieBoxWeb", ignoreCase = true) && module == "stream") {
                    js = patchMovieBoxWebStreamJs(js)
                }
                val escapedModule = module.replace("\\", "\\\\").replace("'", "\\'")
                val base64Js = android.util.Base64.encodeToString(js.toByteArray(), android.util.Base64.NO_WRAP)
                val wrapper = """
                    (function() {
                        var exports = {};
                        var module = { exports: exports };
                        try {
                            var decodedJs = atob('$base64Js');
                            (new Function('exports', 'module', decodedJs))(exports, module);
                        } catch(e) {
                            console.error("Error executing $escapedModule:", e);
                        }
                        if (!window.vegaModules) window.vegaModules = {};
                        if (!window.vegaModules['$escapedProviderValue']) window.vegaModules['$escapedProviderValue'] = {};
                        var moduleExports = module.exports;
                        if (moduleExports && moduleExports.__esModule && moduleExports.default) {
                            if (typeof moduleExports.default === 'object' && moduleExports.default !== null) {
                                moduleExports = Object.assign({}, moduleExports, moduleExports.default);
                            }
                        }
                        window.vegaModules['$escapedProviderValue']['$escapedModule'] = moduleExports || {};
                        window.AndroidBridge.onResult('__CALLBACK_ID__', 'ok');
                    })();
                """.trimIndent()
                evalJsAsync(wrapper)
                Log.d(TAG, "Loaded module: $providerValue/$module")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to evaluate module $module: ${e.message}")
            }
        }
        loadedScrapers.add("${resolvedRepo}::${providerValue}")
    }

    suspend fun loadScraper(repoUrl: String, providerValue: String) = runnerMutex.withLock {
        loadScraperInternal(repoUrl, providerValue)
    }

    suspend fun fetchManifest(repoUrl: String): List<VegaProvider> {
        val resolvedRepo = resolveRepoUrl(repoUrl).ifBlank { DEFAULT_VEGA_REPO }
        val reposToTry = linkedSetOf(
            resolvedRepo,
            DEFAULT_VEGA_REPO,
            FALLBACK_VEGA_REPO
        )

        var lastException: Exception? = null
        for (repo in reposToTry) {
            try {
                val url = "$repo/manifest.json"
                val request = Request.Builder().url(url).build()
                val jsonText = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("Failed to fetch manifest from $url: code ${response.code}")
                        response.body?.string() ?: "[]"
                    }
                }
                val arr = JSONArray(jsonText)
                val list = mutableListOf<VegaProvider>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    list.add(VegaProvider(
                        display_name = item.optString("display_name", ""),
                        value = item.optString("value", ""),
                        version = item.optString("version", ""),
                        icon = item.optString("icon", ""),
                        type = item.optString("type", "global"),
                        disabled = item.optBoolean("disabled", false)
                    ))
                }
                if (list.isNotEmpty()) {
                    Log.d(TAG, "Successfully fetched ${list.size} providers from $repo")
                    return list
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch manifest from $repo: ${e.message}")
                lastException = e
            }
        }
        throw lastException ?: Exception("Failed to fetch manifest from all sources")
    }

    private suspend fun getCatalogInternal(repoUrl: String, providerValue: String): Pair<List<VegaCatalog>, List<VegaCatalog>> {
        loadScraperInternal(repoUrl, providerValue)
        val jsCode = """
            var mod = (window.vegaModules['$providerValue'] || {})['catalog'] || {};
            async function getCatData() {
                var c = [];
                var g = [];
                if (typeof mod === 'function') {
                    try { var res = await mod({ providerContext: (window.providerContext || {}) }); if (res) { c = res.catalog || []; g = res.genres || []; } } catch(e){}
                } else if (mod) {
                    if (typeof mod.getCatalog === 'function') {
                        try { var res = await mod.getCatalog({ providerContext: (window.providerContext || {}) }); if (res) { c = res.catalog || []; g = res.genres || []; } } catch(e){}
                    } else if (mod.default && typeof mod.default.getCatalog === 'function') {
                        try { var res = await mod.default.getCatalog({ providerContext: (window.providerContext || {}) }); if (res) { c = res.catalog || []; g = res.genres || []; } } catch(e){}
                    } else {
                        c = mod.catalog || (mod.default && mod.default.catalog) || [];
                        g = mod.genres || (mod.default && mod.default.genres) || [];
                    }
                }
                return { catalog: Array.isArray(c) ? c : [], genres: Array.isArray(g) ? g : [] };
            }
            getCatData().then(function(res) {
                window.AndroidBridge.onResult('__CALLBACK_ID__', JSON.stringify(res));
            }).catch(function(e) {
                window.AndroidBridge.onResult('__CALLBACK_ID__', JSON.stringify({ catalog: [], genres: [] }));
            });
        """.trimIndent()
        
        val jsonStr = evalJsAsync(jsCode)
        val obj = JSONObject(jsonStr)
        val catalogArr = obj.getJSONArray("catalog")
        val genresArr = obj.getJSONArray("genres")
        
        val catalog = mutableListOf<VegaCatalog>()
        for (i in 0 until catalogArr.length()) {
            val item = catalogArr.getJSONObject(i)
            catalog.add(VegaCatalog(item.getString("title"), item.optString("filter", "")))
        }
        
        val genres = mutableListOf<VegaCatalog>()
        for (i in 0 until genresArr.length()) {
            val item = genresArr.getJSONObject(i)
            genres.add(VegaCatalog(item.getString("title"), item.optString("filter", "")))
        }
        
        return Pair(catalog, genres)
    }

    suspend fun getCatalog(repoUrl: String, providerValue: String): Pair<List<VegaCatalog>, List<VegaCatalog>> = runnerMutex.withLock {
        getCatalogInternal(repoUrl, providerValue)
    }

    suspend fun getPosts(repoUrl: String, providerValue: String, filter: String, page: Int): List<VegaPost> = runnerMutex.withLock {
        loadScraperInternal(repoUrl, providerValue)
        var actualFilter = filter
        if (actualFilter.isBlank()) {
            try {
                val (catalog, _) = getCatalogInternal(repoUrl, providerValue)
                val defaultFilter = catalog.firstOrNull()?.filter
                if (!defaultFilter.isNullOrBlank()) {
                    actualFilter = defaultFilter
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error resolving default catalog filter for $providerValue: ${e.message}")
            }
        }
        if (actualFilter.isBlank()) {
            actualFilter = when (providerValue) {
                "autoEmbed" -> "/top/catalog/movie/top.json"
                "kissKh" -> "/api/DramaList/List?type=0&sub=0&country=0&status=0&order=2"
                "drive" -> ""
                else -> ""
            }
        }
        // Escape backslashes first, then single quotes in filter to prevent JS syntax error
        val safeFilter = actualFilter.replace("\\", "\\\\").replace("'", "\\'")
        val jsCode = """
            try {
                if (!window.providerContext) {
                    window.providerContext = {};
                }
                const mod = (window.vegaModules && window.vegaModules['$providerValue']) ? window.vegaModules['$providerValue']['posts'] : null;
                if (!mod) {
                    window.AndroidBridge.onResult('__CALLBACK_ID__', '[]');
                    return;
                }
                let fn = null;
                if (typeof mod === 'function') {
                    fn = mod;
                } else if (mod) {
                    if (typeof mod.getPosts === 'function') {
                        fn = mod.getPosts;
                    } else if (mod.default && typeof mod.default === 'function') {
                        fn = mod.default;
                    } else if (mod.default && typeof mod.default.getPosts === 'function') {
                        fn = mod.default.getPosts;
                    } else if (typeof mod.default === 'object' && mod.default) {
                        fn = mod.default.getPosts || mod.default;
                    } else {
                        fn = mod;
                    }
                }
                if (typeof fn !== 'function') {
                    window.AndroidBridge.onResult('__CALLBACK_ID__', '[]');
                    return;
                }
                let resolvedFilter = '$safeFilter';
                if (!resolvedFilter) {
                    try {
                        const catMod = (window.vegaModules && window.vegaModules['$providerValue']) ? window.vegaModules['$providerValue']['catalog'] : null;
                        const catList = (catMod && (catMod.catalog || (catMod.default && catMod.default.catalog))) || [];
                        if (Array.isArray(catList) && catList.length > 0 && catList[0] && catList[0].filter) {
                            resolvedFilter = catList[0].filter;
                        }
                    } catch(e) {}
                }
                const localContext = Object.assign({}, window.providerContext || {}, { providerValue: '$providerValue' });
                const res = await fn({ filter: resolvedFilter, page: $page, provider: '$providerValue', providerValue: '$providerValue', providerContext: localContext });
                window.AndroidBridge.onResult('__CALLBACK_ID__', JSON.stringify(res || []));
            } catch(e) {
                console.error("Error in getPosts:", e);
                window.AndroidBridge.onError('__CALLBACK_ID__', e.stack || e.message || e.toString());
            }
        """.trimIndent()
        
        val jsonStr = evalJsAsync(jsCode)
        val arr = JSONArray(jsonStr)
        val posts = mutableListOf<VegaPost>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            posts.add(VegaPost(
                title = cleanTitle(item.optString("title", "")),
                link = item.optString("link", ""),
                image = item.optString("image", "")
            ))
        }
        posts
    }

    suspend fun getSearchPosts(repoUrl: String, providerValue: String, query: String, page: Int): List<VegaPost> = runnerMutex.withLock {
        loadScraperInternal(repoUrl, providerValue)
        val quotedQuery = JSONObject.quote(query)
        val jsCode = """
            try {
                if (!window.providerContext) {
                    window.providerContext = {};
                }
                const mod = (window.vegaModules && window.vegaModules['$providerValue']) ? window.vegaModules['$providerValue']['posts'] : null;
                let fn = null;
                let isSearchPosts = true;
                if (mod) {
                    if (typeof mod.getSearchPosts === 'function') {
                        fn = mod.getSearchPosts;
                    } else if (mod.default && typeof mod.default.getSearchPosts === 'function') {
                        fn = mod.default.getSearchPosts;
                    } else if (typeof mod.getPosts === 'function') {
                        fn = mod.getPosts;
                        isSearchPosts = false;
                    } else if (mod.default && typeof mod.default.getPosts === 'function') {
                        fn = mod.default.getPosts;
                        isSearchPosts = false;
                    } else if (mod.default && typeof mod.default === 'function') {
                        fn = mod.default;
                    }
                }
                if (fn) {
                    const localContext = Object.assign({}, window.providerContext || {}, { providerValue: '$providerValue' });
                    const queryStr = $quotedQuery;
                    const args = isSearchPosts
                        ? { searchQuery: queryStr, page: $page, providerValue: '$providerValue', signal: null, providerContext: localContext }
                        : { filter: queryStr, page: $page, providerValue: '$providerValue', signal: null, providerContext: localContext };
                    const res = await fn(args);
                    window.AndroidBridge.onResult('__CALLBACK_ID__', JSON.stringify(res || []));
                } else {
                    window.AndroidBridge.onResult('__CALLBACK_ID__', '[]');
                }
            } catch(e) {
                console.error("Error in getSearchPosts:", e);
                window.AndroidBridge.onError('__CALLBACK_ID__', e.stack || e.message || e.toString());
            }
        """.trimIndent()
        
        val jsonStr = evalJsAsync(jsCode)
        val arr = JSONArray(jsonStr)
        val posts = mutableListOf<VegaPost>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            posts.add(VegaPost(
                title = cleanTitle(item.optString("title", "")),
                link = item.optString("link", ""),
                image = item.optString("image", "")
            ))
        }
        posts
    }

    suspend fun getMeta(repoUrl: String, providerValue: String, link: String): VegaMeta = runnerMutex.withLock {
        loadScraperInternal(repoUrl, providerValue)
        val safeLink = link.replace("\\", "\\\\").replace("'", "\\'")
        val jsCode = """
            try {
                if (!window.providerContext) {
                    window.providerContext = {};
                }
                const mod = (window.vegaModules && window.vegaModules['$providerValue']) ? window.vegaModules['$providerValue']['meta'] : null;
                let fn = null;
                if (typeof mod === 'function') {
                    fn = mod;
                } else if (mod) {
                    if (typeof mod.getMeta === 'function') {
                        fn = mod.getMeta;
                    } else if (mod.default && typeof mod.default === 'function') {
                        fn = mod.default;
                    } else if (mod.default && typeof mod.default.getMeta === 'function') {
                        fn = mod.default.getMeta;
                    } else if (typeof mod.default === 'object' && mod.default) {
                        fn = mod.default.getMeta || mod.default;
                    } else {
                        fn = mod;
                    }
                }
                if (!fn) {
                    window.AndroidBridge.onResult('__CALLBACK_ID__', '{}');
                    return;
                }
                const res = await fn({ link: '$safeLink', provider: '$providerValue', providerValue: '$providerValue', providerContext: (window.providerContext || {}) });
                window.AndroidBridge.onResult('__CALLBACK_ID__', JSON.stringify(res || {}));
            } catch(e) {
                console.error("Error in getMeta:", e);
                window.AndroidBridge.onError('__CALLBACK_ID__', e.stack || e.message || e.toString());
            }
        """.trimIndent()
        
        val jsonStr = try {
            evalJsAsync(jsCode)
        } catch (e: Exception) {
            val imdbMatch = Regex("tt\\d+").find(link)
            if (imdbMatch != null && (providerValue.contains("torrentio", ignoreCase = true) || providerValue.contains("everything", ignoreCase = true))) {
                Log.w(TAG, "getMeta failed for $providerValue, providing fallback VegaMeta: ${e.message}")
                val imdbId = imdbMatch.value
                val isSeries = link.contains("series", ignoreCase = true)
                val type = if (isSeries) "series" else "movie"
                val payload = JSONObject().apply {
                    put("title", imdbId)
                    put("imdbId", imdbId)
                    put("type", type)
                }.toString()
                return@withLock VegaMeta(
                    title = imdbId,
                    synopsis = "Metadata unavailable from Cinemeta. Streams may still be available.",
                    image = "",
                    imdbId = imdbId,
                    type = type,
                    linkList = listOf(
                        VegaLink(
                            title = if (isSeries) "Episodes" else "Movie",
                            episodesLink = null,
                            directLinks = listOf(
                                VegaDirectLink(
                                    title = imdbId,
                                    link = payload,
                                    type = type
                                )
                            )
                        )
                    )
                )
            }
            throw e
        }
        val obj = JSONObject(jsonStr)
        
        val linkList = mutableListOf<VegaLink>()
        if (obj.has("linkList")) {
            val linkListArr = obj.getJSONArray("linkList")
            for (i in 0 until linkListArr.length()) {
                val item = linkListArr.getJSONObject(i)
                val title = cleanTitle(item.optString("title", ""))
                val episodesLink = (item.optString("episodesLink", "").takeIf { it.isNotEmpty() }
                    ?: item.optString("link", "").takeIf { it.isNotEmpty() }
                    ?: item.optString("url", "").takeIf { it.isNotEmpty() })
                val itemImage = (item.optString("image", "").takeIf { it.isNotEmpty() }
                    ?: item.optString("thumbnail", "").takeIf { it.isNotEmpty() }
                    ?: item.optString("poster", "").takeIf { it.isNotEmpty() }
                    ?: item.optString("poster_path", "").takeIf { it.isNotEmpty() })
                val itemDuration = (item.optString("duration", "").takeIf { it.isNotEmpty() }
                    ?: item.optString("runtime", "").takeIf { it.isNotEmpty() }
                    ?: item.optString("time", "").takeIf { it.isNotEmpty() })
                val itemDate = (item.optString("date", "").takeIf { it.isNotEmpty() }
                    ?: item.optString("releaseDate", "").takeIf { it.isNotEmpty() }
                    ?: item.optString("airDate", "").takeIf { it.isNotEmpty() }
                    ?: item.optString("aired", "").takeIf { it.isNotEmpty() })
                val itemEp = (item.optString("episodeNumber", "").takeIf { it.isNotEmpty() }
                    ?: item.optString("episode", "").takeIf { it.isNotEmpty() }
                    ?: item.optString("ep", "").takeIf { it.isNotEmpty() }
                    ?: item.optString("number", "").takeIf { it.isNotEmpty() })
                val itemSeason = (item.optString("seasonNumber", "").takeIf { it.isNotEmpty() }
                    ?: item.optString("season", "").takeIf { it.isNotEmpty() })
                
                var directLinks: MutableList<VegaDirectLink>? = null
                if (item.has("directLinks") && !item.isNull("directLinks")) {
                    val dArr = item.getJSONArray("directLinks")
                    directLinks = mutableListOf()
                    for (j in 0 until dArr.length()) {
                        val dObj = dArr.getJSONObject(j)
                        val dImage = (dObj.optString("image", "").takeIf { it.isNotEmpty() }
                            ?: dObj.optString("thumbnail", "").takeIf { it.isNotEmpty() }
                            ?: itemImage)
                        val dDuration = (dObj.optString("duration", "").takeIf { it.isNotEmpty() }
                            ?: dObj.optString("runtime", "").takeIf { it.isNotEmpty() }
                            ?: itemDuration)
                        val dDate = (dObj.optString("date", "").takeIf { it.isNotEmpty() }
                            ?: dObj.optString("releaseDate", "").takeIf { it.isNotEmpty() }
                            ?: itemDate)
                        val dEp = (dObj.optString("episodeNumber", "").takeIf { it.isNotEmpty() }
                            ?: dObj.optString("episode", "").takeIf { it.isNotEmpty() }
                            ?: itemEp)
                        val dSeason = (dObj.optString("seasonNumber", "").takeIf { it.isNotEmpty() }
                            ?: dObj.optString("season", "").takeIf { it.isNotEmpty() }
                            ?: itemSeason)
                        directLinks.add(VegaDirectLink(
                            title = cleanTitle(dObj.optString("title", "")),
                            link = dObj.optString("link", ""),
                            type = dObj.optString("type", ""),
                            image = dImage,
                            duration = dDuration,
                            date = dDate,
                            episodeNumber = dEp,
                            seasonNumber = dSeason
                        ))
                    }
                }
                linkList.add(VegaLink(
                    title = title,
                    episodesLink = episodesLink,
                    directLinks = directLinks,
                    image = itemImage,
                    duration = itemDuration,
                    date = itemDate,
                    episodeNumber = itemEp,
                    seasonNumber = itemSeason
                ))
            }
        }
        
        VegaMeta(
            title = cleanTitle(obj.optString("title", "")),
            synopsis = obj.optString("synopsis", ""),
            image = obj.optString("image", ""),
            imdbId = obj.optString("imdbId", ""),
            type = obj.optString("type", "movie"),
            linkList = linkList
        )
    }

    suspend fun getStream(repoUrl: String, providerValue: String, link: String, type: String): List<VegaStream> = runnerMutex.withLock {
        // Direct playable media shortcut (.m3u8, .mp4, .mkv, .ts, etc.)
        val cleanL = link.trim().lowercase()
        val isDirectMedia = cleanL.endsWith(".m3u8") || cleanL.endsWith(".mp4") ||
            cleanL.endsWith(".mkv") || cleanL.endsWith(".ts") || cleanL.endsWith(".mpd") ||
            cleanL.contains(".m3u8?") || cleanL.contains(".mp4?") || cleanL.contains(".mkv?")
        if (isDirectMedia) {
            return listOf(VegaStream("Direct Source", link.trim(), "mp4", "HD"))
        }

        // Check if link is a JSON string with a direct playable media URL inside
        if (link.trim().startsWith("{") && link.trim().endsWith("}")) {
            try {
                val json = JSONObject(link.trim())
                val directUrl = json.optString("url", "").ifBlank { json.optString("link", "") }
                if (directUrl.isNotBlank()) {
                    val c = directUrl.trim().lowercase()
                    if (c.endsWith(".m3u8") || c.endsWith(".mp4") || c.endsWith(".mkv") || c.endsWith(".mpd") ||
                        c.contains(".m3u8?") || c.contains(".mp4?") || c.contains(".mkv?")) {
                        val serverName = json.optString("title", json.optString("server", "Direct Server"))
                        return listOf(VegaStream(serverName, directUrl.trim(), "mp4", "HD"))
                    }
                }
            } catch (_: Exception) {}
        }

        loadScraperInternal(repoUrl, providerValue)

        // Helper to evaluate scraper stream module for a specific type
        suspend fun resolveWithJs(targetType: String): List<VegaStream> {
            val safeLink = link
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
            val safeType = targetType
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
            // Escape providerValue to match what loadScraperInternal stored in window.vegaModules
            val escapedPV = providerValue
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
            val jsCode = """
                try {
                    if (!window.providerContext) {
                        window.providerContext = {};
                    }
                    // Ensure providerValue is set for scrapers that read it
                    window.providerContext.providerValue = '$escapedPV';
                    const mod = (window.vegaModules && window.vegaModules['$escapedPV']) ? window.vegaModules['$escapedPV']['stream'] : null;
                    if (!mod) {
                        console.warn('Stream module not found for provider: $escapedPV');
                        window.AndroidBridge.onResult('__CALLBACK_ID__', '[]');
                        return;
                    }
                    let fn = null;
                    if (typeof mod === 'function') {
                        fn = mod;
                    } else if (typeof mod.getStream === 'function') {
                        fn = mod.getStream;
                    } else if (mod.default) {
                        if (typeof mod.default === 'function') {
                            fn = mod.default;
                        } else if (typeof mod.default.getStream === 'function') {
                            fn = mod.default.getStream;
                        }
                    } else {
                        // Try to find any exported function as last resort
                        const keys = Object.keys(mod);
                        for (let k of keys) {
                            if (typeof mod[k] === 'function' && k !== 'default') {
                                fn = mod[k];
                                break;
                            }
                        }
                    }
                    if (!fn) {
                        console.warn('No getStream function found in stream module for: $escapedPV');
                        window.AndroidBridge.onResult('__CALLBACK_ID__', '[]');
                        return;
                    }
                    const localContext = Object.assign({}, window.providerContext || {}, { providerValue: '$escapedPV' });
                    const res = await fn({ link: '$safeLink', type: '$safeType', signal: null, providerContext: localContext });
                    const resultArr = Array.isArray(res) ? res : (res ? [res] : []);
                    window.AndroidBridge.onResult('__CALLBACK_ID__', JSON.stringify(resultArr));
                } catch(e) {
                    console.error("Error in getStream for $escapedPV:", e.message || e);
                    // Return empty array instead of error so caller can try alternate type
                    window.AndroidBridge.onResult('__CALLBACK_ID__', '[]');
                }
            """.trimIndent()
            
            return try {
                val jsonStr = evalJsAsync(jsCode)
                val arr = JSONArray(jsonStr)
                val streams = mutableListOf<VegaStream>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val headersMap = mutableMapOf<String, String>()
                    if (item.has("headers") && !item.isNull("headers")) {
                        val hObj = item.getJSONObject("headers")
                        for (key in hObj.keys()) {
                            headersMap[key] = hObj.getString(key)
                        }
                    }
                    val sLink = item.optString("link", "")
                    if (sLink.isNotBlank()) {
                        streams.add(VegaStream(
                            server = item.optString("server", ""),
                            link = sLink,
                            type = item.optString("type", ""),
                            quality = item.optString("quality", ""),
                            headers = headersMap
                        ))
                    }
                }
                streams
            } catch (e: Exception) {
                Log.w(TAG, "getStream resolution failed for type '$targetType': ${e.message}")
                emptyList()
            }
        }

        // First attempt with requested type
        var streams = resolveWithJs(type)

        // 3. Fallback: if empty, retry with alternate type (movie <-> series)
        if (streams.isEmpty()) {
            val alternateType = if (type.equals("movie", ignoreCase = true)) "series" else "movie"
            Log.d(TAG, "No streams for type '$type', retrying with alternate type '$alternateType'")
            streams = resolveWithJs(alternateType)
        }

        // Auto-recovery retry: if still empty, re-inject/reload the scraper and retry once
        if (streams.isEmpty()) {
            val resolvedRepo = resolveRepoUrl(repoUrl).ifBlank { DEFAULT_VEGA_REPO }
            Log.w(TAG, "No streams found on first pass for $providerValue. Evicting module cache and retrying...")
            loadedScrapers.remove("${resolvedRepo}::${providerValue}")
            try {
                loadScraperInternal(repoUrl, providerValue)
                streams = resolveWithJs(type)
                if (streams.isEmpty()) {
                    val alternateType = if (type.equals("movie", ignoreCase = true)) "series" else "movie"
                    streams = resolveWithJs(alternateType)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Scraper retry failed: ${e.message}")
            }
        }

        // Fallback: if scraper returned nothing and link is a genuine playable media stream (not an HTML webpage)
        if (streams.isEmpty() && isPlayableMediaUrl(link) &&
            !link.contains("pixeldrain", ignoreCase = true) && !link.contains("pdrain", ignoreCase = true)) {
            Log.d(TAG, "Scraper returned 0 streams, link is playable media: $link")
            val ext = link.substringBefore('?').substringBefore('#').substringAfterLast('.', "mp4").lowercase()
            streams = listOf(VegaStream("Direct Source", link, ext, "HD"))
        }

        return prioritizeStreams(streams)
    }

    suspend fun getEpisodes(repoUrl: String, providerValue: String, url: String): List<VegaLink> = runnerMutex.withLock {
        loadScraperInternal(repoUrl, providerValue)
        val safeUrl = url.replace("\\", "\\\\").replace("'", "\\'")
        val jsCode = """
            try {
                if (!window.providerContext) {
                    window.providerContext = {};
                }
                const mod = (window.vegaModules && window.vegaModules['$providerValue']) ? window.vegaModules['$providerValue']['episodes'] : null;
                if (mod) {
                    let fn = null;
                    if (typeof mod === 'function') {
                        fn = mod;
                    } else {
                        if (typeof mod.getEpisodes === 'function') {
                            fn = mod.getEpisodes;
                        } else if (mod.default && typeof mod.default === 'function') {
                            fn = mod.default;
                        } else if (mod.default && typeof mod.default.getEpisodes === 'function') {
                            fn = mod.default.getEpisodes;
                        }
                    }
                    if (fn) {
                        const res = await fn({ url: '$safeUrl', providerContext: (window.providerContext || {}) });
                        window.AndroidBridge.onResult('__CALLBACK_ID__', JSON.stringify(res || []));
                    } else {
                        window.AndroidBridge.onResult('__CALLBACK_ID__', '[]');
                    }
                } else {
                    window.AndroidBridge.onResult('__CALLBACK_ID__', '[]');
                }
            } catch(e) {
                console.error("Error in getEpisodes:", e);
                window.AndroidBridge.onError('__CALLBACK_ID__', e.stack || e.message || e.toString());
            }
        """.trimIndent()
        
        val jsonStr = evalJsAsync(jsCode)
        val arr = JSONArray(jsonStr)
        val links = mutableListOf<VegaLink>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val title = cleanTitle(item.optString("title", ""))
            val episodesLink = (item.optString("episodesLink", "").takeIf { it.isNotEmpty() }
                ?: item.optString("link", "").takeIf { it.isNotEmpty() }
                ?: item.optString("url", "").takeIf { it.isNotEmpty() })
            
            val itemImage = (item.optString("image", "").takeIf { it.isNotEmpty() }
                ?: item.optString("thumbnail", "").takeIf { it.isNotEmpty() }
                ?: item.optString("poster", "").takeIf { it.isNotEmpty() }
                ?: item.optString("poster_path", "").takeIf { it.isNotEmpty() })
            val itemDuration = (item.optString("duration", "").takeIf { it.isNotEmpty() }
                ?: item.optString("runtime", "").takeIf { it.isNotEmpty() }
                ?: item.optString("time", "").takeIf { it.isNotEmpty() })
            val itemDate = (item.optString("date", "").takeIf { it.isNotEmpty() }
                ?: item.optString("releaseDate", "").takeIf { it.isNotEmpty() }
                ?: item.optString("airDate", "").takeIf { it.isNotEmpty() }
                ?: item.optString("aired", "").takeIf { it.isNotEmpty() })
            val itemEp = (item.optString("episodeNumber", "").takeIf { it.isNotEmpty() }
                ?: item.optString("episode", "").takeIf { it.isNotEmpty() }
                ?: item.optString("ep", "").takeIf { it.isNotEmpty() }
                ?: item.optString("number", "").takeIf { it.isNotEmpty() })
            val itemSeason = (item.optString("seasonNumber", "").takeIf { it.isNotEmpty() }
                ?: item.optString("season", "").takeIf { it.isNotEmpty() })

            var directLinks: MutableList<VegaDirectLink>? = null
            if (item.has("directLinks") && !item.isNull("directLinks")) {
                val dArr = item.getJSONArray("directLinks")
                directLinks = mutableListOf()
                for (j in 0 until dArr.length()) {
                    val dObj = dArr.getJSONObject(j)
                    val dImage = (dObj.optString("image", "").takeIf { it.isNotEmpty() }
                        ?: dObj.optString("thumbnail", "").takeIf { it.isNotEmpty() }
                        ?: itemImage)
                    val dDuration = (dObj.optString("duration", "").takeIf { it.isNotEmpty() }
                        ?: dObj.optString("runtime", "").takeIf { it.isNotEmpty() }
                        ?: itemDuration)
                    val dDate = (dObj.optString("date", "").takeIf { it.isNotEmpty() }
                        ?: dObj.optString("releaseDate", "").takeIf { it.isNotEmpty() }
                        ?: itemDate)
                    val dEp = (dObj.optString("episodeNumber", "").takeIf { it.isNotEmpty() }
                        ?: dObj.optString("episode", "").takeIf { it.isNotEmpty() }
                        ?: itemEp)
                    val dSeason = (dObj.optString("seasonNumber", "").takeIf { it.isNotEmpty() }
                        ?: dObj.optString("season", "").takeIf { it.isNotEmpty() }
                        ?: itemSeason)
                    directLinks.add(VegaDirectLink(
                        title = cleanTitle(dObj.optString("title", "")),
                        link = dObj.optString("link", ""),
                        type = dObj.optString("type", ""),
                        image = dImage,
                        duration = dDuration,
                        date = dDate,
                        episodeNumber = dEp,
                        seasonNumber = dSeason
                    ))
                }
            }
            links.add(VegaLink(
                title = title,
                episodesLink = episodesLink,
                directLinks = directLinks,
                image = itemImage,
                duration = itemDuration,
                date = itemDate,
                episodeNumber = itemEp,
                seasonNumber = itemSeason
            ))
        }
        links
    }

    fun destroy() {
        mainHandler.post {
            webView?.let { oldWebView ->
                try {
                    oldWebView.stopLoading()
                    oldWebView.clearHistory()
                    try { oldWebView.clearCache(true) } catch (e: Exception) { Log.w(TAG, "clearCache error: ${e.message}") }
                    oldWebView.loadUrl("about:blank")
                    oldWebView.onPause()
                    oldWebView.destroy()
                } catch (e: Exception) {
                    Log.w(TAG, "Error destroying WebView in destroy(): ${e.message}")
                }
            }
            webView = null
        }
        bridge.clearPendingCallbacks()
        isCompatInjected = false
        loadedScrapers.clear()
    }

    fun getBaseUrl(name: String): String {
        ensureBaseUrlsFetched()
        val cleanName = name.trim()
        var url = baseUrls[cleanName]
        if (url == null) {
            url = baseUrls.entries.firstOrNull { it.key.equals(cleanName, ignoreCase = true) }?.value
        }
        return url ?: "https://$cleanName.com"
    }

    inner class AndroidBridge {
        private val callbacks = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<String>>()
        private val callbackIdCounter = java.util.concurrent.atomic.AtomicLong(0)

        fun clearPendingCallbacks() {
            callbacks.values.forEach { deferred ->
                if (deferred.isActive) {
                    deferred.completeExceptionally(Exception("WebView execution interrupted or renderer reset"))
                }
            }
            callbacks.clear()
        }

        fun removeCallback(callbackId: String): CompletableDeferred<String>? {
            return callbacks.remove(callbackId)
        }

        fun registerCallback(deferred: CompletableDeferred<String>): String {
            val id = "cb_${callbackIdCounter.incrementAndGet()}"
            callbacks[id] = deferred
            return id
        }

        @JavascriptInterface
        fun onResult(callbackId: String, resultJson: String) {
            val deferred = callbacks.remove(callbackId)
            deferred?.complete(resultJson)
        }

        @JavascriptInterface
        fun onError(callbackId: String, errorMsg: String) {
            val deferred = callbacks.remove(callbackId)
            deferred?.completeExceptionally(Exception(errorMsg))
        }

        @JavascriptInterface
        fun getBaseUrl(name: String): String {
            return this@VegaProviderRunner.getBaseUrl(name)
        }

        @JavascriptInterface
        fun atob(str: String): String {
            return try {
                String(android.util.Base64.decode(str, android.util.Base64.DEFAULT))
            } catch (e: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun btoa(str: String): String {
            return try {
                android.util.Base64.encodeToString(str.toByteArray(), android.util.Base64.NO_WRAP)
            } catch (e: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun digestString(algorithm: String, data: String): String {
            return try {
                val algo = when (algorithm.uppercase().replace("-", "")) {
                    "SHA1" -> "SHA-1"
                    "SHA256" -> "SHA-256"
                    "SHA384" -> "SHA-384"
                    "SHA512" -> "SHA-512"
                    "MD5" -> "MD5"
                    else -> algorithm
                }
                val digest = java.security.MessageDigest.getInstance(algo)
                val hash = digest.digest(data.toByteArray())
                hash.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                Log.e(TAG, "digestString error: ${e.message}")
                ""
            }
        }

        @JavascriptInterface
        fun kvGet(key: String): String? {
            val prefs = context.getSharedPreferences("vega_kv_store", Context.MODE_PRIVATE)
            return prefs.getString(key, null)
        }

        @JavascriptInterface
        fun kvSet(key: String, value: String) {
            val prefs = context.getSharedPreferences("vega_kv_store", Context.MODE_PRIVATE)
            prefs.edit().putString(key, value).apply()
        }

        @JavascriptInterface
        fun kvDelete(key: String) {
            val prefs = context.getSharedPreferences("vega_kv_store", Context.MODE_PRIVATE)
            prefs.edit().remove(key).apply()
        }

        @JavascriptInterface
        fun kvClear() {
            val prefs = context.getSharedPreferences("vega_kv_store", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        }

        @JavascriptInterface
        fun httpFetch(url: String, method: String, headersJson: String, bodyData: String?): String {
            val requestBuilder = Request.Builder().url(url)
            var contentTypeStr: String? = null
            var followRedirects = true
            
            try {
                val headers = JSONObject(headersJson)
                for (key in headers.keys()) {
                    val value = headers.getString(key)
                    if (key.equals("content-type", ignoreCase = true)) {
                        contentTypeStr = value
                    } else if (key.equals("x-max-redirects", ignoreCase = true) || key.equals("x-axios-max-redirects", ignoreCase = true)) {
                        if (value == "0") {
                            followRedirects = false
                        }
                        continue
                    }
                    requestBuilder.header(key, value)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse headers JSON", e)
            }

            val finalMediaType = (contentTypeStr ?: "application/json; charset=utf-8").toMediaTypeOrNull()

            when {
                method.equals("POST", ignoreCase = true) -> {
                    val requestBody = (bodyData ?: "").toRequestBody(finalMediaType)
                    requestBuilder.post(requestBody)
                }
                method.equals("PUT", ignoreCase = true) -> {
                    val requestBody = (bodyData ?: "").toRequestBody(finalMediaType)
                    requestBuilder.put(requestBody)
                }
                method.equals("PATCH", ignoreCase = true) -> {
                    val requestBody = (bodyData ?: "").toRequestBody(finalMediaType)
                    requestBuilder.patch(requestBody)
                }
                method.equals("DELETE", ignoreCase = true) -> {
                    if (bodyData != null) {
                        requestBuilder.delete(bodyData.toRequestBody(finalMediaType))
                    } else {
                        requestBuilder.delete()
                    }
                }
                method.equals("HEAD", ignoreCase = true) -> {
                    requestBuilder.head()
                }
                else -> {
                    requestBuilder.get()
                }
            }

            val callClient = if (!followRedirects) {
                httpClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
            } else {
                httpClient
            }

            fun executeHttp(req: Request): Pair<Int, JSONObject> {
                return try {
                    callClient.newCall(req).execute().use { response ->
                        val responseBody = response.body?.string() ?: ""
                        val responseHeaders = JSONObject()
                        
                        for (name in response.headers.names()) {
                            val lowerName = name.lowercase()
                            val values = response.headers.values(name)
                            if (lowerName == "set-cookie") {
                                val cookieArr = JSONArray()
                                for (v in values) {
                                    cookieArr.put(v)
                                }
                                responseHeaders.put("set-cookie", cookieArr)
                                responseHeaders.put("Set-Cookie", cookieArr)
                            } else {
                                val singleVal = response.header(name)
                                responseHeaders.put(lowerName, singleVal)
                                responseHeaders.put(name, singleVal)
                            }
                        }
                        
                        val result = JSONObject()
                        result.put("status", response.code)
                        result.put("body", responseBody)
                        result.put("headers", responseHeaders)
                        result.put("url", response.request.url.toString())
                        Pair(response.code, result)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "HTTP fetch error: $url: ${e.message}")
                    val result = JSONObject()
                    result.put("status", 500)
                    result.put("body", "Error: ${e.message}")
                    result.put("headers", JSONObject())
                    result.put("url", url)
                    Pair(500, result)
                }
            }

            val baseReq = requestBuilder.build()
            var (statusCode, resultJson) = executeHttp(baseReq)

            // Auto-recovery for HTTP 403 / 431 / 429: stale CF clearance cookies or bloated headers
            if (statusCode == 403 || statusCode == 431 || statusCode == 429) {
                try {
                    val cm = android.webkit.CookieManager.getInstance()
                    cm.setCookie(url, "cf_clearance=; Max-Age=0; path=/")
                    cm.setCookie(url, "__cf_bm=; Max-Age=0; path=/")
                    cm.flush()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to evict stale cookies for $url: ${e.message}")
                }
                val retryReq = baseReq.newBuilder().removeHeader("Cookie").removeHeader("cookie").build()
                val retry = executeHttp(retryReq)
                if (retry.first in 200..399) {
                    resultJson = retry.second
                }
            }

            return resultJson.toString()
        }

        @JavascriptInterface
        fun openWebView(url: String, optionsJson: String): String {
            return try {
                val options = try { JSONObject(optionsJson) } catch (_: Exception) { JSONObject() }
                val reqBuilder = Request.Builder().url(url)
                val cm = android.webkit.CookieManager.getInstance()
                val cookie = try { cm.getCookie(url) ?: "" } catch (_: Exception) { "" }
                if (cookie.isNotBlank()) {
                    reqBuilder.header("Cookie", cookie)
                }
                reqBuilder.header("User-Agent", CHROME_USER_AGENT)
                if (options.has("headers")) {
                    val h = options.optJSONObject("headers")
                    if (h != null) {
                        for (k in h.keys()) {
                            reqBuilder.header(k, h.getString(k))
                        }
                    }
                }
                val method = options.optString("method", "GET").uppercase()
                val bodyStr = if (options.has("body")) options.optString("body") else null
                if (method == "POST") {
                    val reqBody = (bodyStr ?: "").toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
                    reqBuilder.post(reqBody)
                } else {
                    reqBuilder.get()
                }
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val finalUrl = resp.request.url.toString()
                    val body = resp.body?.string() ?: ""
                    for (header in resp.headers("Set-Cookie")) {
                        try { cm.setCookie(finalUrl, header) } catch (_: Exception) {}
                    }
                    try { cm.flush() } catch (_: Exception) {}
                    val finalCookie = try { cm.getCookie(finalUrl) ?: "" } catch (_: Exception) { "" }
                    val cookieMap = JSONObject()
                    if (finalCookie.isNotEmpty()) {
                        finalCookie.split(";").forEach { part ->
                            val eq = part.indexOf('=')
                            if (eq > 0) {
                                val k = part.substring(0, eq).trim()
                                val v = part.substring(eq + 1).trim()
                                cookieMap.put(k, v)
                            }
                        }
                    }
                    val resObj = JSONObject()
                    resObj.put("data", body)
                    resObj.put("cookies", finalCookie)
                    resObj.put("cookie", finalCookie)
                    resObj.put("cookieMap", cookieMap)
                    resObj.put("userAgent", CHROME_USER_AGENT)
                    resObj.put("url", finalUrl)
                    resObj.toString()
                }
            } catch (e: Exception) {
                Log.e(TAG, "openWebView error for $url: ${e.message}")
                val fallback = JSONObject()
                fallback.put("data", "")
                fallback.put("cookies", "")
                fallback.put("cookie", "")
                fallback.put("cookieMap", JSONObject())
                fallback.put("userAgent", CHROME_USER_AGENT)
                fallback.put("url", url)
                fallback.toString()
            }
        }
    }

    private fun patchVegaStreamJs(js: String): String {
        var patched = js
        
        // 1. Prevent unhandled 403 from terminating stream resolution when openWebView solver is absent.
        //    The minified code reads: if(!openWebView)throw console.log(`hubcloudExtractor: 403 ...`),error;
        //    We replace the throw with a graceful return of whatever streams were collected so far.
        patched = patched.replace(
            "if(!openWebView)throw console.log(`hubcloudExtractor: 403 Forbidden for \${link}, but openWebView solver is not available!`),error",
            "if(!openWebView){console.log('hubcloud 403 solver unavailable, continuing with collected streams...');return streamLinks}"
        )
        // Also handle the second 403 throw inside vcloud link fetch
        patched = patched.replace(
            "if(403!==(null==(_b=error.response)?void 0:_b.status)||!openWebView)throw error",
            "if(403!==(null==(_b=error.response)?void 0:_b.status)){throw error}else{console.log('hubcloud vcloud 403, skipping...')}"
        )
        
        // 2. Clear the hardcoded stale cf_clearance cookie that causes 403 responses.
        //    The minified code sets headers.Cookie to a hardcoded expired clearance token.
        //    We replace it with a minimal cookie that won't trigger WAF rejection.
        val staleCookiePrefix = "headers.Cookie||(headers.Cookie=\"ext_name=ojplmecpdpgccookcobabopnaifgidhf"
        if (patched.contains(staleCookiePrefix)) {
            // Find and replace the entire hardcoded cookie assignment
            val cookieStart = patched.indexOf(staleCookiePrefix)
            if (cookieStart >= 0) {
                // Find the closing of this assignment (next ")," or "))")
                val afterCookie = patched.indexOf("\")", cookieStart + staleCookiePrefix.length)
                if (afterCookie > cookieStart) {
                    val oldCookie = patched.substring(cookieStart, afterCookie + 2)
                    patched = patched.replace(oldCookie, "headers.Cookie=(headers.Cookie||\"\")")
                }
            }
        }

        // 3. Inject a no-op openWebView stub so scrapers that check for it don't throw.
        //    We inject this at the start of hubcloudExtractor.
        patched = patched.replace(
            "const baseUrl=link.split(\"/\").slice(0,3).join(\"/\"),streamLinks=[],openWebView=null==providerContext?void 0:providerContext.openWebView",
            "const baseUrl=link.split(\"/\").slice(0,3).join(\"/\"),streamLinks=[],openWebView=(null==providerContext?void 0:providerContext.openWebView)||null"
        )

        // 4. Wrap the final throwProviderError in hubcloudExtractor to return empty instead of throwing,
        //    so fallback engine can try other servers.
        patched = patched.replace(
            "throwProviderError(\"HubCloud\",`extract \${link}`,error)",
            "console.warn('HubCloud extract failed:',error&&error.message||error);return[]"
        )

        // 5. Decode direct Google CDN / GDrive video links embedded in ?link= query parameters
        patched = patched.replace(
            "newLink.includes(\"?link=\")&&(newLink=newLink.split(\"?link=\")[1]||newLink)",
            "newLink.includes(\"?link=\")&&(newLink=decodeURIComponent(newLink.split(\"?link=\")[1]||newLink))"
        )
        
        // 6. Wrap the Gofile wt.obf.js execution (new Function) in try-catch for WebView CSP safety
        patched = patched.replace(
            "const runner=new Function(\"navigator\",\"window\",\"document\",\"location\",`\${code}\\nreturn generateWT;`)",
            "let runner;try{runner=new Function(\"navigator\",\"window\",\"document\",\"location\",`\${code}\\nreturn generateWT;`)}catch(e){console.warn('gofile wt.obf.js Function() failed:',e);return accountToken=>accountToken}"
        )

        // 7. Wrap vegadriveExtractor calls in try-catch so failures don't propagate
        patched = patched.replace(
            "throwProviderError(\"Vega\",\"stream\",error)",
            "console.warn('Vega stream error:',error&&error.message||error);return[]"
        )

        return patched
    }

    private fun patchHdhubMetaJs(js: String): String {
        var patched = js
        patched = patched.replace(
            "container=$(\".page-body\")",
            "container=$(\".entry-content, .post-inner, .post-content, .page-body, .page-content, article, #content\")"
        )
        patched = patched.replace(
            "title=container.find('h2[data-ved=\"2ahUKEwjL0NrBk4vnAhWlH7cAHRCeAlwQ3B0oATAfegQIFBAM\"],h2[data-ved=\"2ahUKEwiP0pGdlermAhUFYVAKHV8tAmgQ3B0oATAZegQIDhAM\"]').text()",
            "title=($(\"h1.entry-title, h1.post-title, h1.title, .entry-title, h1\").first().text().trim() || container.find(\"h2\").first().text().trim() || \"\")"
        )
        patched = patched.replace(
            "image=container.find('img[decoding=\"async\"]').attr(\"src\")||\"\"",
            "image=(container.find('img[decoding=\"async\"]').attr(\"src\") || container.find('img[data-lazy-src]').attr('data-lazy-src') || container.find('img').first().attr('src') || \"\")"
        )
        patched = patched.replace(
            "synopsis=container.find('strong:contains(\"DESCRIPTION\")').parent().text().replace(\"DESCRIPTION:\",\"\")",
            "synopsis=(container.find('strong:contains(\"DESCRIPTION\"), strong:contains(\"PLOT\"), strong:contains(\"Synopsis\"), strong:contains(\"Story\")').parent().text().replace(/DESCRIPTION:|PLOT:|Synopsis:|Story:/gi, \"\").trim() || container.find('.synopsis').text().trim() || \"\")"
        )
        return patched
    }

    private fun patchVegaMetaJs(js: String): String {
        var patched = js
        patched = patched.replace(
            "let title=$(\"h1.post-title\").text().trim();",
            "let title=$(\"h1.entry-title, h1.post-title, h1.title, .entry-title, h1\").first().text().trim();"
        )
        patched = patched.replace(
            "return list.each((index,element)=>{",
            "if(list){list.each((index,element)=>{"
        )
        patched = patched.replace(
            "}),{title:title,synopsis:synopsis,image:image,imdbId:imdbId,type:type,linkList:links}",
            "});}return {title:title,synopsis:synopsis,image:image,imdbId:imdbId,type:type,linkList:links}"
        )
        return patched
    }

    private fun patchHdhubStreamJs(js: String): String {
        var patched = js
        // Add safety check to prevent TypeErrors when redirectLinkText.match returns null
        val target = "redirectLinkText.match(/href=\"(https:\\/\\/hubcloud\\.[^\\/]+\\/drive\\/[^\"]+)\"/)[1]"
        val replacement = "(redirectLinkText.match(/href=\"(https:\\/\\/hubcloud\\.[^\\/]+\\/drive\\/[^\"]+)\"/)||[])[1]"
        patched = patched.replace(target, replacement)
        return patched
    }

    private fun patchNetmirrorJs(js: String): String {
        var patched = js
        // Reduce verify polling from 45s to 15s so it never freezes the app if verification is slow
        patched = patched.replace(Regex("Date\\.now\\(\\)\\s*-\\s*startTime\\s*<\\s*45e3"), "Date.now() - startTime < 15e3")
        return patched
    }

    private fun patchCinemetaMetaJs(js: String): String {
        var patched = js

        // 1. Normalize Cinemeta meta object:
        // Cinemeta returns { meta: { id: "tt...", name: "..." } } where id contains the IMDb ID, but imdb_id is undefined.
        // Scrapers like Torrentio and Everything do `if (!meta.name || meta.imdb_id !== imdbId)` which throws "Cinemeta returned invalid metadata".
        val metaAssign = "const meta=null==(_a=response.data)?void 0:_a.meta;"
        val metaReplacement = """
            let meta=null==(_a=response.data)?void 0:_a.meta;
            if(meta){
                if(!meta.imdb_id&&meta.id) meta.imdb_id=meta.id;
                if(!meta.id&&meta.imdb_id) meta.id=meta.imdb_id;
                if(!meta.name&&meta.title) meta.name=meta.title;
                if(!meta.name) meta.name=imdbId;
                if(!meta.imdb_id) meta.imdb_id=imdbId;
            }
        """.trimIndent()
        patched = patched.replace(metaAssign, metaReplacement)

        // 2. Prevent false negatives in the validation check
        patched = patched.replace(
            "meta.imdb_id!==imdbId",
            "((meta.imdb_id||meta.id||'')!==imdbId && meta.imdb_id!==imdbId && false)"
        )

        // 3. Relax cache check to accept either imdb_id or id
        patched = patched.replace(
            "cached.imdb_id===imdbId",
            "(cached.imdb_id===imdbId||cached.id===imdbId)"
        )

        // 4. Auto-fallback between movie and series in getCinemetaMeta if primary 404s/fails
        patched = patched.replace(
            "const url=`${'$'}{CINEMETA_BASE_URL}/${'$'}{\"series\"===type?\"series\":\"movie\"}/${'$'}{imdbId}.json`,",
            "const primaryType=(\"series\"===type?\"series\":\"movie\"), altType=(\"series\"===type?\"movie\":\"series\"), url=`${'$'}{CINEMETA_BASE_URL}/${'$'}{primaryType}/${'$'}{imdbId}.json`,"
        )
        patched = patched.replace(
            "request=providerContext.axios.get(url).then(response=>{",
            "request=providerContext.axios.get(url).catch(err=>{ return providerContext.axios.get(`${'$'}{CINEMETA_BASE_URL}/${'$'}{altType}/${'$'}{imdbId}.json`); }).then(response=>{"
        )

        // 5. Auto-detect series when Cinemeta returns a series payload or contains video episodes
        patched = patched.replace(
            "if(\"series\"===type){",
            "const effectiveIsSeries = (\"series\" === type || (meta && (meta.type === \"series\" || (Array.isArray(meta.videos) && meta.videos.length > 0)))); if(effectiveIsSeries){"
        )

        // 6. Graceful fallback on getMeta failure instead of throwing unhandled error to UI
        val fallbackBlock = """
            catch(err){
                console.warn("Cinemeta metadata failed, using fallback:", (err && err.message) || err);
                try {
                    const fbMatches = link.match(/tt\d+/);
                    const fbId = (fbMatches && fbMatches[0]) || link;
                    const fbType = (/\bseries\b/i.test(link) || (err && err.message && /\bseries\b/i.test(err.message))) ? "series" : "movie";
                    return {
                        title: fbId,
                        synopsis: "Metadata could not be loaded from Cinemeta. Streams may still be available.",
                        image: "",
                        poster: "",
                        imdbId: fbId,
                        type: fbType,
                        linkList: [{
                            title: fbType === "series" ? "Episodes" : "Movie",
                            directLinks: [{
                                title: fbId,
                                type: fbType,
                                link: JSON.stringify({ title: fbId, imdbId: fbId, type: fbType })
                            }]
                        }]
                    };
                } catch(fallbackErr) {
                    throw err;
                }
            }
        """.trimIndent()

        patched = patched.replace("catch(err){throwProviderError(\"Torrentio\",\"metadata\",err)}", fallbackBlock)
        patched = patched.replace("catch(err){throwProviderError(\"Everything\",\"metadata\",err)}", fallbackBlock)

        return patched
    }

    private fun patchKissKhPostsJs(js: String): String {
        var patched = js
        // 1. Ensure page argument is received from caller
        patched = patched.replace(
            "{filter:filter,signal:signal,providerContext:providerContext}",
            "{filter:filter,page:page,signal:signal,providerContext:providerContext}"
        )
        // 2. Build clean URL with filter fallback, pageSize=100, and page pagination
        patched = patched.replace(
            "url=`\${baseUrl+filter}&type=0`",
            """url=((baseUrl, filter, page) => {
                let f = (filter && filter.trim()) || '/api/DramaList/List?type=0&sub=0&country=0&status=0&order=2';
                if (!f.startsWith('/')) f = '/' + f;
                const p = (typeof page !== 'undefined' && page && page > 1) ? page : 1;
                const sep = f.includes('?') ? '&' : '?';
                return `${'$'}{baseUrl}${'$'}{f}${'$'}{sep}pageSize=100&page=${'$'}{p}&type=0`;
            })(baseUrl, filter, typeof page !== 'undefined' ? page : 1)""".trimIndent()
        )
        // 3. Fallback simple replace if obfuscated differently
        patched = patched.replace(
            "baseUrl+filter",
            "baseUrl + (filter || '/api/DramaList/List?type=0&sub=0&country=0&status=0&order=2')"
        )
        return patched
    }

    private fun patchMovieBoxWebStreamJs(js: String): String {
        var patched = js
        // 1. Resilient Nuxt data deserialization with regex fallback
        patched = patched.replace(
            "const serialized=cheerio.load(html)(\"#__NUXT_DATA__\").text();return serialized?decodeNuxtData(JSON.parse(serialized)):null",
            """
            let serialized = "";
            try { serialized = cheerio.load(html)("#__NUXT_DATA__").text(); } catch(e) {}
            if (!serialized) {
                const m = (html || "").match(/<script[^>]*id="__NUXT_DATA__"[^>]*>([\s\S]*?)<\/script>/i);
                if (m) serialized = m[1];
            }
            try {
                return serialized ? decodeNuxtData(JSON.parse(serialized)) : null;
            } catch(e) {
                const m = (html || "").match(/<script[^>]*id="__NUXT_DATA__"[^>]*>([\s\S]*?)<\/script>/i);
                if (m) {
                    try { return decodeNuxtData(JSON.parse(m[1])); } catch(err) {}
                }
                return null;
            }
            """.trimIndent()
        )

        // 2. Fallback to API base or mobile mirror when officialmoviebox returns 404 or empty
        patched = patched.replace(
            "const playUrl=absoluteUrl(apiBase,`/wefeed-h5api-bff/subject/play?",
            "const playUrl=absoluteUrl((apiBase && !apiBase.includes('localhost')) ? apiBase : 'https://officialmoviebox.com', `/wefeed-h5api-bff/subject/play?"
        )

        // 3. Prevent unhandled playback limit exception from blocking stream resolution
        patched = patched.replace(
            "if(err instanceof MovieBoxPlaybackLimitError)throw err;",
            "if(err instanceof MovieBoxPlaybackLimitError){ console.warn('[movieBoxWeb] Playback limit hit:', err.message); return null; }"
        )

        return patched
    }
}

