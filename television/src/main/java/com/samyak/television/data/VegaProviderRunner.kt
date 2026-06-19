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

    private val dohDns = object : Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            if (hostname == "8.8.8.8" || hostname == "8.8.4.4" || hostname == "dns.google") {
                return Dns.SYSTEM.lookup(hostname)
            }
            
            try {
                val url = "https://8.8.8.8/resolve?name=$hostname&type=A"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()
                
                val dnsClient = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
                
                dnsClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        if (json.has("Answer")) {
                            val answer = json.getJSONArray("Answer")
                            val list = mutableListOf<java.net.InetAddress>()
                            for (i in 0 until answer.length()) {
                                val obj = answer.getJSONObject(i)
                                val type = obj.optInt("type", 1)
                                if (type == 1) { // Type A
                                    val data = obj.getString("data")
                                    list.addAll(java.net.InetAddress.getAllByName(data))
                                }
                            }
                            if (list.isNotEmpty()) {
                                return list
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "DoH lookup failed for $hostname: ${e.message}")
            }
            return Dns.SYSTEM.lookup(hostname)
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(cookieJar)
        .dns(dohDns)
        .addInterceptor { chain ->
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
                    if (attempt < 2) {
                        Thread.sleep((500 * (attempt + 1)).toLong())
                    }
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
    }

    private var baseUrls = mutableMapOf<String, String>()
    private var baseUrlsFetched = false
    private val baseUrlsLock = Any()

    private fun ensureBaseUrlsFetched() {
        if (baseUrlsFetched) return
        synchronized(baseUrlsLock) {
            if (baseUrlsFetched) return
            try {
                val request = Request.Builder()
                    .url("https://himanshu8443.github.io/providers/modflix.json")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val json = JSONObject(body)
                        for (key in json.keys()) {
                            val obj = json.getJSONObject(key)
                            val url = obj.optString("url", "")
                            if (url.isNotEmpty()) {
                                baseUrls[key] = url
                            }
                        }
                        baseUrlsFetched = true
                        Log.d(TAG, "Loaded ${baseUrls.size} base URLs from modflix.json")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch modflix.json: ${e.message}")
                baseUrls.putAll(mapOf(
                    "Vega" to "https://vegamovies.mq",
                    "Moviesmod" to "https://moviesmod.farm",
                    "Animeflix" to "https://ww3.animeflix.ltd",
                    "Topmovies" to "https://moviesleech.bar",
                    "UhdMovies" to "https://uhdmovies.food",
                    "lux" to "https://rogmovies.club",
                    "drive" to "https://new3.moviesdrives.my/",
                    "multi" to "https://multimovies.fyi",
                    "extra" to "https://extramovies.ist",
                    "hdhub" to "https://new1.hdhub4u.limo",
                    "kat" to "https://new1.katmoviehd.cymru",
                    "autoEmbed" to "https://autoembed.cc",
                    "tokyoinsider" to "https://www.tokyoinsider.com",
                    "primewire" to "https://primewire.si",
                    "rive" to "https://www.rivestream.app",
                    "kissKh" to "https://kisskh.do",
                    "showbox" to "https://www.showbox.media",
                    "protonMovies" to "https://m.protonmovies.space",
                    "filmyfly" to "https://new2.filmyfiy.org",
                    "4khdhub" to "https://4khdhub.link",
                    "moviezwap" to "https://www.moviezwap.onl/",
                    "movieBox" to "https://api6.aoneroom.com",
                    "1cinevood" to "https://1cinevood.in",
                    "zeefliz" to "https://zeefliz.beer",
                    "movies4u" to "https://movies4u.ee"
                ))
                baseUrlsFetched = true
            }
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

        // Safely destroy old webView if exists to avoid memory and execution leaks
        webView?.let { oldWebView ->
            try {
                oldWebView.stopLoading()
                oldWebView.clearHistory()
                oldWebView.clearCache(true)
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
                webViewReady.complete(Unit)
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
            <html>
            <head>
                <script>
                    console.log("Headless JS environment ready");
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

    // Helper to evaluate JS and await the result with automatic retry on timeout
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
                return withTimeout(45_000) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "JS evaluation timed out after 45s on attempt $attempt")
                deferred.cancel()
                lastException = Exception("JS evaluation timed out")
                if (attempt < 3) {
                    Log.d(TAG, "Re-initializing WebView due to timeout before attempt ${attempt + 1}")
                    withContext(Dispatchers.Main) {
                        setupWebView()
                    }
                    delay(1000)
                }
            } catch (e: Exception) {
                deferred.cancel()
                throw e
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
        val trimmed = input.trim().removeSuffix("/")
        if (trimmed.isBlank()) return ""

        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val urlLower = trimmed.lowercase()
            if (urlLower.startsWith("https://github.com/")) {
                val parts = trimmed.substring("https://github.com/".length).split("/")
                if (parts.size >= 2) {
                    val author = parts[0]
                    val repo = parts[1]
                    val branch = if (parts.size >= 4 && parts[2] == "tree") parts[3] else "main"
                    return "https://raw.githubusercontent.com/$author/$repo/refs/heads/$branch"
                }
            } else if (urlLower.startsWith("https://raw.githubusercontent.com/")) {
                return trimmed
            }
            return trimmed
        }

        val author = trimmed.removePrefix("@")
        return "https://raw.githubusercontent.com/$author/vega-providers/refs/heads/main"
    }

    private suspend fun fetchFile(fileUrl: String): String {
        val request = Request.Builder().url(fileUrl).build()
        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Failed to fetch $fileUrl: code ${response.code}")
                response.body?.string() ?: ""
            }
        }
    }

    private var isCompatInjected = false

    private suspend fun injectCompatibilityLayer() {
        if (isCompatInjected) return

        val compatJs = """
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
                    const responseHeaders = new Headers(responseObj.headers || {});
                    
                    return {
                        ok: responseObj.status >= 200 && responseObj.status < 300,
                        status: responseObj.status,
                        statusText: responseObj.status >= 200 && responseObj.status < 300 ? 'OK' : 'Error',
                        headers: responseHeaders,
                        url: url,
                        redirected: false,
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
                axios: async function(configOrUrl, config) {
                    let url = "";
                    let method = "GET";
                    let headers = {};
                    let data = null;
                    if (typeof configOrUrl === 'string') {
                        url = configOrUrl;
                        if (config) {
                            method = config.method || "GET";
                            headers = config.headers || {};
                            data = config.data || null;
                        }
                    } else {
                        url = configOrUrl.url;
                        method = configOrUrl.method || "GET";
                        headers = configOrUrl.headers || {};
                        data = configOrUrl.data || null;
                    }

                    try {
                        const responseStr = await window.AndroidBridge.httpFetch(url, method, JSON.stringify(headers), data ? (typeof data === 'string' ? data : JSON.stringify(data)) : null);
                        const parsedRes = JSON.parse(responseStr);
                        
                        let responseData = parsedRes.body;
                        try {
                            responseData = JSON.parse(parsedRes.body);
                        } catch(e) {
                        }
                        
                        if (parsedRes.status < 200 || parsedRes.status >= 300) {
                            throw new Error('Request failed with status code ' + parsedRes.status + ': ' + (typeof responseData === 'string' ? responseData.substring(0, 100) : ''));
                        }
                        
                        return {
                            data: responseData,
                            status: parsedRes.status,
                            headers: parsedRes.headers
                        };
                    } catch (e) {
                        console.error("axios fetch failed: ", e);
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
        
        evalJsAsync(compatJs + "\nwindow.AndroidBridge.onResult('__CALLBACK_ID__', 'ok');")
        isCompatInjected = true
    }

    private val loadedScrapers = mutableSetOf<String>()

    suspend fun loadScraper(repoUrl: String, providerValue: String) {
        if (loadedScrapers.contains("${repoUrl}::${providerValue}")) {
            evalJsDirect("window.providerContext.providerValue = '$providerValue';")
            return
        }
        
        val resolvedRepo = resolveRepoUrl(repoUrl)
        
        injectCompatibilityLayer()
        evalJsDirect("window.providerContext.providerValue = '$providerValue';")
        
        val modules = listOf("catalog", "posts", "meta", "stream", "episodes")
        for (module in modules) {
            try {
                val fileUrl = "$resolvedRepo/dist/$providerValue/$module.js"
                var js = fetchFile(fileUrl)
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
                val base64Js = android.util.Base64.encodeToString(js.toByteArray(), android.util.Base64.NO_WRAP)
                val wrapper = """
                    (function() {
                        var exports = {};
                        var module = { exports: exports };
                        try {
                            var decodedJs = atob('$base64Js');
                            (new Function('exports', 'module', decodedJs))(exports, module);
                        } catch(e) {
                            console.error("Error executing $module:", e);
                        }
                        if (!window.vegaModules) window.vegaModules = {};
                        if (!window.vegaModules['$providerValue']) window.vegaModules['$providerValue'] = {};
                        var moduleExports = module.exports;
                        if (moduleExports && moduleExports.__esModule && moduleExports.default) {
                            if (typeof moduleExports.default === 'object' && moduleExports.default !== null) {
                                moduleExports = Object.assign({}, moduleExports, moduleExports.default);
                            }
                        }
                        window.vegaModules['$providerValue']['$module'] = moduleExports || {};
                        window.AndroidBridge.onResult('__CALLBACK_ID__', 'ok');
                    })();
                """.trimIndent()
                evalJsAsync(wrapper)
                Log.d(TAG, "Loaded module: $providerValue/$module")
            } catch (e: Exception) {
                if (e.message?.contains("code 404") == true) {
                    Log.d(TAG, "Module $module not found for $providerValue (optional)")
                } else {
                    Log.w(TAG, "Failed to load module $module: ${e.message}")
                }
            }
        }
        loadedScrapers.add("${repoUrl}::${providerValue}")
    }

    suspend fun fetchManifest(repoUrl: String): List<VegaProvider> {
        val resolvedRepo = resolveRepoUrl(repoUrl)
        val url = "$resolvedRepo/manifest.json"
        val request = Request.Builder().url(url).build()
        
        val jsonText = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Failed to fetch manifest: code ${response.code}")
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
        return list
    }

    suspend fun getCatalog(repoUrl: String, providerValue: String): Pair<List<VegaCatalog>, List<VegaCatalog>> {
        loadScraper(repoUrl, providerValue)
        val jsCode = """
            let mod = (window.vegaModules['$providerValue'] || {})['catalog'] || {};
            let catalogArr = mod.catalog || (mod.default && mod.default.catalog) || [];
            let genresArr = mod.genres || (mod.default && mod.default.genres) || [];
            window.AndroidBridge.onResult('__CALLBACK_ID__', JSON.stringify({ catalog: catalogArr, genres: genresArr }));
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

    suspend fun getPosts(repoUrl: String, providerValue: String, filter: String, page: Int): List<VegaPost> {
        loadScraper(repoUrl, providerValue)
        val safeFilter = filter.replace("'", "\\'")
        val jsCode = """
            try {
                const mod = window.vegaModules['$providerValue']['posts'];
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
                const res = await fn({ filter: '$safeFilter', page: $page, provider: '$providerValue', providerValue: '$providerValue', providerContext: window.providerContext });
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
        return posts
    }

    suspend fun getSearchPosts(repoUrl: String, providerValue: String, query: String, page: Int): List<VegaPost> {
        loadScraper(repoUrl, providerValue)
        val safeQuery = query.replace("'", "\\'").replace("\\", "\\\\")
        val jsCode = """
            try {
                const mod = (window.vegaModules['$providerValue'] || {})['posts'];
                let fn = null;
                if (mod) {
                    if (typeof mod.getSearchPosts === 'function') {
                        fn = mod.getSearchPosts;
                    } else if (mod.default && typeof mod.default === 'function') {
                        fn = mod.default;
                    } else if (mod.default && typeof mod.default.getSearchPosts === 'function') {
                        fn = mod.default.getSearchPosts;
                    }
                }
                if (fn) {
                    const res = await fn({ searchQuery: '$safeQuery', page: $page, providerValue: '$providerValue', signal: null, providerContext: window.providerContext });
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
        return posts
    }

    suspend fun getMeta(repoUrl: String, providerValue: String, link: String): VegaMeta {
        loadScraper(repoUrl, providerValue)
        val safeLink = link.replace("'", "\\'").replace("\\", "\\\\")
        val jsCode = """
            try {
                const mod = window.vegaModules['$providerValue']['meta'];
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
                const res = await fn({ link: '$safeLink', provider: '$providerValue', providerValue: '$providerValue', providerContext: window.providerContext });
                window.AndroidBridge.onResult('__CALLBACK_ID__', JSON.stringify(res || {}));
            } catch(e) {
                console.error("Error in getMeta:", e);
                window.AndroidBridge.onError('__CALLBACK_ID__', e.stack || e.message || e.toString());
            }
        """.trimIndent()
        
        val jsonStr = evalJsAsync(jsCode)
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
                
                var directLinks: MutableList<VegaDirectLink>? = null
                if (item.has("directLinks") && !item.isNull("directLinks")) {
                    val dArr = item.getJSONArray("directLinks")
                    directLinks = mutableListOf()
                    for (j in 0 until dArr.length()) {
                        val dObj = dArr.getJSONObject(j)
                        directLinks.add(VegaDirectLink(
                            title = cleanTitle(dObj.optString("title", "")),
                            link = dObj.optString("link", ""),
                            type = dObj.optString("type", "")
                        ))
                    }
                }
                linkList.add(VegaLink(title, episodesLink, directLinks))
            }
        }
        
        return VegaMeta(
            title = cleanTitle(obj.optString("title", "")),
            synopsis = obj.optString("synopsis", ""),
            image = obj.optString("image", ""),
            imdbId = obj.optString("imdbId", ""),
            type = obj.optString("type", "movie"),
            linkList = linkList
        )
    }

    suspend fun getStream(repoUrl: String, providerValue: String, link: String, type: String): List<VegaStream> {
        loadScraper(repoUrl, providerValue)
        val safeLink = link.replace("'", "\\'").replace("\\", "\\\\")
        val safeType = type.replace("'", "\\'")
        val jsCode = """
            try {
                const mod = (window.vegaModules['$providerValue'] || {})['stream'];
                if (!mod) { window.AndroidBridge.onResult('__CALLBACK_ID__', '[]'); return; }
                let fn = null;
                if (typeof mod === 'function') {
                    fn = mod;
                } else {
                    if (typeof mod.getStream === 'function') {
                        fn = mod.getStream;
                    } else if (mod.default && typeof mod.default === 'function') {
                        fn = mod.default;
                    } else if (mod.default && typeof mod.default.getStream === 'function') {
                        fn = mod.default.getStream;
                    }
                }
                if (!fn) { window.AndroidBridge.onResult('__CALLBACK_ID__', '[]'); return; }
                const res = await fn({ link: '$safeLink', type: '$safeType', signal: null, providerContext: window.providerContext });
                window.AndroidBridge.onResult('__CALLBACK_ID__', JSON.stringify(res || []));
            } catch(e) {
                console.error("Error in getStream:", e);
                window.AndroidBridge.onError('__CALLBACK_ID__', e.stack || e.message || e.toString());
            }
        """.trimIndent()
        
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
            streams.add(VegaStream(
                server = item.optString("server", ""),
                link = item.optString("link", ""),
                type = item.optString("type", ""),
                quality = item.optString("quality", ""),
                headers = headersMap
            ))
        }
        return streams
    }

    suspend fun getEpisodes(repoUrl: String, providerValue: String, url: String): List<VegaLink> {
        loadScraper(repoUrl, providerValue)
        val safeUrl = url.replace("'", "\\'").replace("\\", "\\\\")
        val jsCode = """
            try {
                const mod = (window.vegaModules['$providerValue'] || {})['episodes'];
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
                        const res = await fn({ url: '$safeUrl', providerContext: window.providerContext });
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
            
            var directLinks: MutableList<VegaDirectLink>? = null
            if (item.has("directLinks") && !item.isNull("directLinks")) {
                val dArr = item.getJSONArray("directLinks")
                directLinks = mutableListOf()
                for (j in 0 until dArr.length()) {
                    val dObj = dArr.getJSONObject(j)
                    directLinks.add(VegaDirectLink(
                        title = cleanTitle(dObj.optString("title", "")),
                        link = dObj.optString("link", ""),
                        type = dObj.optString("type", "")
                    ))
                }
            }
            links.add(VegaLink(title, episodesLink, directLinks))
        }
        return links
    }

    fun destroy() {
        mainHandler.post {
            webView?.let { oldWebView ->
                try {
                    oldWebView.stopLoading()
                    oldWebView.clearHistory()
                    oldWebView.clearCache(true)
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

    inner class AndroidBridge {
        private val callbacks = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<String>>()
        private var callbackIdCounter = 0

        fun clearPendingCallbacks() {
            callbacks.values.forEach { deferred ->
                if (deferred.isActive) {
                    deferred.cancel()
                }
            }
            callbacks.clear()
        }

        fun registerCallback(deferred: CompletableDeferred<String>): String {
            val id = "cb_${++callbackIdCounter}"
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
            ensureBaseUrlsFetched()
            val cleanName = name.trim()
            var url = baseUrls[cleanName]
            if (url == null) {
                url = baseUrls.entries.firstOrNull { it.key.equals(cleanName, ignoreCase = true) }?.value
            }
            return url ?: "https://$cleanName.com"
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
        fun httpFetch(url: String, method: String, headersJson: String, bodyData: String?): String {
            val requestBuilder = Request.Builder().url(url)
            
            try {
                val headers = JSONObject(headersJson)
                for (key in headers.keys()) {
                    requestBuilder.header(key, headers.getString(key))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse headers JSON", e)
            }

            when {
                method.equals("POST", ignoreCase = true) -> {
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    val requestBody = (bodyData ?: "").toRequestBody(mediaType)
                    requestBuilder.post(requestBody)
                }
                method.equals("PUT", ignoreCase = true) -> {
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    val requestBody = (bodyData ?: "").toRequestBody(mediaType)
                    requestBuilder.put(requestBody)
                }
                method.equals("DELETE", ignoreCase = true) -> {
                    requestBuilder.delete()
                }
                method.equals("HEAD", ignoreCase = true) -> {
                    requestBuilder.head()
                }
                else -> {
                    requestBuilder.get()
                }
            }

            return try {
                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    val responseHeaders = JSONObject()
                    for (name in response.headers.names()) {
                        responseHeaders.put(name, response.header(name))
                    }
                    
                    val result = JSONObject()
                    result.put("status", response.code)
                    result.put("body", responseBody)
                    result.put("headers", responseHeaders)
                    result.toString()
                }
            } catch (e: Exception) {
                Log.e(TAG, "HTTP fetch error: $url: ${e.message}")
                
                val result = JSONObject()
                result.put("status", 500)
                result.put("body", "Error: ${e.message}")
                result.put("headers", JSONObject())
                return result.toString()
            }
        }
    }

    private fun patchVegaStreamJs(js: String): String {
        var patched = js
        val target1 = "function hubcloudExtractor(link,signal,axios,cheerio,headers2){return __async(this,null,function*(){var _a,_b,_c,_d,_e,_f,_g;try{"
        val replacement1 = "function hubcloudExtractor(link,signal,axios,cheerio,headers2){return __async(this,null,function*(){if(!link||link===\"undefined\")return[];var _a,_b,_c,_d,_e,_f,_g;try{"
        patched = patched.replace(target1, replacement1)

        val target2 = "\"movie\"===type){"
        val replacement2 = "\"movie\"===type && link && !link.includes(\"cloud\") && !link.includes(\"pixeld\") && !link.includes(\"dev\")){"
        patched = patched.replace(target2, replacement2)
        
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
}
