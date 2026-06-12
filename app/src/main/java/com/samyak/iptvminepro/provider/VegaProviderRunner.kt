package com.samyak.iptvminepro.provider

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log
import com.samyak.iptvminepro.model.*
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

    // In-memory cookie jar for session persistence across requests
    private val cookieJar = object : CookieJar {
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            cookieStore.getOrPut(host) { mutableListOf() }.apply {
                // Remove existing cookies with same name before adding new ones
                cookies.forEach { newCookie ->
                    removeAll { it.name == newCookie.name }
                }
                addAll(cookies)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val host = url.host
            val cookies = mutableListOf<Cookie>()
            // Match exact host and parent domains
            cookieStore.forEach { (storedHost, storedCookies) ->
                if (host == storedHost || host.endsWith(".$storedHost")) {
                    cookies.addAll(storedCookies.filter { !it.expiresAt.let { exp -> exp < System.currentTimeMillis() } })
                }
            }
            return cookies
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
        .addInterceptor { chain ->
            // Retry interceptor: retry up to 2 times on failure
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
            // User-Agent interceptor: ensure all requests have a modern User-Agent
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

    // Dynamic base URLs mapping - fetched from modflix.json
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
                // Fallback hardcoded URLs
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
        // Reset readiness gate for this new WebView instance
        if (webViewReady.isCompleted) {
            webViewReady = CompletableDeferred()
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
                // Signal that the WebView is ready for JS evaluation
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
        
        // Load a minimal page to initialize JS environment
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

    // Helper to evaluate JS and await the result
    private suspend fun evalJsAsync(jsCode: String): String {
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
        
        // Wait for WebView to be ready (page loaded) before evaluating JS
        withTimeout(15_000) { webViewReady.await() }
        
        withContext(Dispatchers.Main) {
            if (webView == null) {
                setupWebView()
                // Wait again for the new WebView to be ready
                withContext(Dispatchers.Default) {
                    withTimeout(15_000) { webViewReady.await() }
                }
            }
            webView?.evaluateJavascript(wrappedJs, null)
        }
        
        return try {
            withTimeout(45_000) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "JS evaluation timed out after 45s")
            deferred.cancel()
            throw Exception("JS evaluation timed out")
        }
    }

    private suspend fun evalJsDirect(jsCode: String) {
        // Wait for WebView to be ready before evaluating JS
        withTimeout(15_000) { webViewReady.await() }
        
        withContext(Dispatchers.Main) {
            if (webView == null) {
                setupWebView()
                withContext(Dispatchers.Default) {
                    withTimeout(15_000) { webViewReady.await() }
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

        // Treat as author (e.g. "@vega-org" or "vega-org")
        val author = trimmed.removePrefix("@")
        return "https://raw.githubusercontent.com/$author/vega-providers/refs/heads/main"
    }

    private fun cleanTypeScript(tsCode: String): String {
        var js = tsCode

        // Remove single-line ts-ignore / ts-expect-error comments
        js = js.replace(Regex("//\\s*@ts-(?:ignore|expect-error).*"), "")

        // Remove import statements (single and multi-line)
        js = js.replace(Regex("import\\s+\\{[^}]*\\}\\s+from\\s+['\"][^'\"]*['\"];?"), "")
        js = js.replace(Regex("import\\s+\\*\\s+as\\s+\\w+\\s+from\\s+['\"][^'\"]*['\"];?"), "")
        js = js.replace(Regex("import\\s+\\w+\\s+from\\s+['\"][^'\"]*['\"];?"), "")
        js = js.replace(Regex("import\\s+['\"][^'\"]*['\"];?"), "")

        // Remove export keywords (keep the declarations)
        js = js.replace(Regex("\\bexport\\s+default\\s+"), "")
        js = js.replace(Regex("\\bexport\\s+async\\s+function\\b"), "async function")
        js = js.replace(Regex("\\bexport\\s+function\\b"), "function")
        js = js.replace(Regex("\\bexport\\s+const\\b"), "const")
        js = js.replace(Regex("\\bexport\\s+let\\b"), "let")
        js = js.replace(Regex("\\bexport\\s+var\\b"), "var")
        js = js.replace(Regex("\\bexport\\s+"), "")

        // Remove full interface blocks (multi-line)
        js = js.replace(Regex("\\binterface\\s+\\w+[^{]*\\{[^}]*\\}"), "")

        // Remove type alias declarations
        js = js.replace(Regex("\\btype\\s+\\w+\\s*=[^;]+;"), "")

        // Remove inline type annotation blocks after destructured params
        js = js.replace(Regex("\\}\\s*:\\s*\\{[^}]*\\}\\s*\\)\\s*:\\s*[^=>{)]+\\s*=>"), "}) =>")
        js = js.replace(Regex("\\}\\s*:\\s*\\{[^}]*\\}\\s*\\)\\s*:\\s*[^{)]+\\s*\\{"), "}) {")
        js = js.replace(Regex("\\}\\s*:\\s*\\{[^}]*\\}\\s*\\)"), "})")

        // Remove return type annotations on functions/arrows
        js = js.replace(Regex("\\)\\s*:\\s*Promise\\s*<[^>]*>\\s*=>"), ") =>")
        js = js.replace(Regex("\\)\\s*:\\s*Promise\\s*<[^>]*>\\s*\\{"), ") {")
        js = js.replace(Regex("\\)\\s*:\\s*[A-Za-z_][A-Za-z0-9_]*(?:\\[\\])?\\s*=>"), ") =>")
        js = js.replace(Regex("\\)\\s*:\\s*[A-Za-z_][A-Za-z0-9_]*(?:\\[\\])?\\s*\\{"), ") {")

        // Remove generic type annotations from variable declarations
        js = js.replace(Regex(":\\s*(?:Record|Map|Set|Array|Promise)\\s*<[^>]*>\\s*(?=\\s*[=,);])"), "")
        js = js.replace(Regex(":\\s*[A-Za-z_][A-Za-z0-9_]*\\s*<[^>]*>\\s*(?=\\s*[=,);])"), "")
        js = js.replace(Regex(":\\s*[A-Za-z_][A-Za-z0-9_|\\s]*\\[\\]\\s*(?=\\s*[=,);])"), "")
        js = js.replace(Regex(":\\s*\\b(?:string|number|boolean|any|void|never|undefined|null|object)\\b(?:\\s*\\|\\s*\\b(?:string|number|boolean|any|void|never|undefined|null|object)\\b)*\\s*(?=\\s*[=,);])"), "")
        
        // Remove type annotations from function parameters
        js = js.replace(Regex("(\\.\\.\\.)[a-zA-Z_][a-zA-Z0-9_]*)\\s*:\\s*(?:Array|Record|Map|Set)\\s*<[^>]*>"), "$1")
        js = js.replace(Regex("(\\.\\.\\.)[a-zA-Z_][a-zA-Z0-9_]*)\\s*:\\s*[A-Za-z_][A-Za-z0-9_|\\s]*(?:\\[\\])?"), "$1")
        
        // Remove remaining simple param/variable type annotations
        js = js.replace(Regex("([a-zA-Z_][a-zA-Z0-9_]*)\\s*:\\s*(?:string|number|boolean|any|void|AbortSignal|ProviderContext|Post|Info|Stream|Link|Content|TextTracks)(?:\\[\\])?\\s*(?=[,)\\n])"), "$1")
        
        // Remove non-null assertion operator
        js = js.replace(Regex("!(?=\\.)"), "")
        js = js.replace(Regex("!(?=\\[)"), "")

        return js
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
            // Polyfill process environment to prevent crashes in scrapers
            window.process = window.process || { env: {} };

            // Base64 encode/decode
            window.atob = window.atob || function(str) { 
                return window.AndroidBridge.atob(str);
            };
            window.btoa = window.btoa || function(str) {
                return window.AndroidBridge.btoa(str);
            };

            window.vegaModules = {};

            // Crypto polyfill for expo-crypto compatibility
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

            // Polyfill fetch to use AndroidBridge for HTTP requests
            window.fetch = async function(url, options) {
                try {
                    // Convert relative URLs to absolute
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
                        
                        // Try to parse body as JSON if possible, otherwise return as string
                        let responseData = parsedRes.body;
                        try {
                            responseData = JSON.parse(parsedRes.body);
                        } catch(e) {
                            // body is not JSON, keep as string
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
                                    if (rawContext && rawContext.querySelectorAll) {
                                        elements = Array.from(rawContext.querySelectorAll(selector));
                                    } else if (rawContext && rawContext.length && rawContext[0] && rawContext[0].querySelectorAll) {
                                        elements = Array.from(rawContext[0].querySelectorAll(selector));
                                    } else if (rawContext && rawContext._el && rawContext._el.querySelectorAll) {
                                        elements = Array.from(rawContext._el.querySelectorAll(selector));
                                    }
                                } else {
                                    elements = Array.from(doc.querySelectorAll(selector));
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
                                // already a wrapped object
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

            function wrapElements(elements, doc) {
                const wrapped = {
                    length: elements.length,
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
                            if (el.querySelectorAll) {
                                el.querySelectorAll(selector).forEach(subEl => {
                                    found.push(subEl);
                                });
                            }
                        });
                        return wrapElements(found, doc);
                    },
                    next: function(selector) {
                        const nextEls = [];
                        elements.forEach(el => {
                            let next = el.nextElementSibling;
                            while (next) {
                                if (!selector || next.matches(selector)) {
                                    nextEls.push(next);
                                    break;
                                }
                                next = next.nextElementSibling;
                            }
                        });
                        return wrapElements(nextEls, doc);
                    },
                    prev: function(selector) {
                        const prevEls = [];
                        elements.forEach(el => {
                            let prev = el.previousElementSibling;
                            while (prev) {
                                if (!selector || prev.matches(selector)) {
                                    prevEls.push(prev);
                                    break;
                                }
                                prev = prev.previousElementSibling;
                            }
                        });
                        return wrapElements(prevEls, doc);
                    },
                    nextUntil: function(selector) {
                        const els = [];
                        elements.forEach(el => {
                            let next = el.nextElementSibling;
                            while (next) {
                                if (selector && next.matches(selector)) break;
                                els.push(next);
                                next = next.nextElementSibling;
                            }
                        });
                        return wrapElements(els, doc);
                    },
                    closest: function(selector) {
                        const closestEls = [];
                        elements.forEach(el => {
                            if (el.closest) {
                                const closest = el.closest(selector);
                                if (closest) closestEls.push(closest);
                            }
                        });
                        return wrapElements(closestEls, doc);
                    },
                    parent: function() {
                        const parents = [];
                        elements.forEach(el => {
                            if (el.parentElement) parents.push(el.parentElement);
                        });
                        return wrapElements(parents, doc);
                    },
                    children: function(selector) {
                        const childEls = [];
                        elements.forEach(el => {
                            if (el.children) {
                                Array.from(el.children).forEach(child => {
                                    if (!selector || child.matches(selector)) {
                                        childEls.push(child);
                                    }
                                });
                            }
                        });
                        return wrapElements(childEls, doc);
                    },
                    first: function() {
                        return wrapElements(elements.length > 0 ? [elements[0]] : [], doc);
                    },
                    last: function() {
                        return wrapElements(elements.length > 0 ? [elements[elements.length - 1]] : [], doc);
                    },
                    eq: function(index) {
                        return wrapElements(index >= 0 && index < elements.length ? [elements[index]] : [], doc);
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
                        res.get = function() { return res; };
                        res.toArray = function() { return res; };
                        return res;
                    },
                    filter: function(selector) {
                        if (typeof selector === 'string') {
                            return wrapElements(elements.filter(el => el.matches(selector)), doc);
                        } else if (typeof selector === 'function') {
                            return wrapElements(elements.filter((el, idx) => {
                                const mockNode = createMockNode(el);
                                return selector.call(mockNode, idx, mockNode);
                            }), doc);
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
                        return elements.some(el => el.matches && el.matches(selector));
                    },
                    toArray: function() {
                        return Array.from(elements).map(el => wrapElements([el], doc));
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
                        return wrapElements(allNodes, doc);
                    },
                    siblings: function(selector) {
                        const siblingEls = [];
                        elements.forEach(el => {
                            if (el.parentElement) {
                                Array.from(el.parentElement.children).forEach(sibling => {
                                    if (sibling !== el && (!selector || sibling.matches(selector))) {
                                        siblingEls.push(sibling);
                                    }
                                });
                            }
                        });
                        return wrapElements(siblingEls, doc);
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
                            return wrapElements(elements.filter(el => !el.matches(selector)), doc);
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
                        return wrapElements(Array.from(elements).slice(start, end), doc);
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
        // Skip if already loaded
        if (loadedScrapers.contains("${repoUrl}::${providerValue}")) {
            // Still update providerValue on context
            evalJsDirect("window.providerContext.providerValue = '$providerValue';")
            return
        }
        
        val resolvedRepo = resolveRepoUrl(repoUrl)
        
        injectCompatibilityLayer()
        
        // Set the providerValue on the context so scrapers can access it
        evalJsDirect("window.providerContext.providerValue = '$providerValue';")
        
        // Load provider scraper files from dist
        val modules = listOf("catalog", "posts", "meta", "stream", "episodes")
        for (module in modules) {
            try {
                val fileUrl = "$resolvedRepo/dist/$providerValue/$module.js"
                var js = fetchFile(fileUrl)
                if (providerValue.equals("vega", ignoreCase = true) && module == "stream") {
                    js = patchVegaStreamJs(js)
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
                            moduleExports = Object.assign({}, moduleExports, moduleExports.default);
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
            let catalogArr = mod.catalog || [];
            let genresArr = mod.genres || [];
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
        // Escape single quotes in filter to prevent JS injection
        val safeFilter = filter.replace("'", "\\'")
        val jsCode = """
            try {
                const mod = window.vegaModules['$providerValue']['posts'];
                const fn = mod.getPosts || mod.default?.getPosts || mod;
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
                title = item.optString("title", ""),
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
                if (mod) {
                    const fn = mod.getSearchPosts || mod.default?.getSearchPosts;
                    if (fn) {
                        const res = await fn({ searchQuery: '$safeQuery', page: $page, providerValue: '$providerValue', signal: null, providerContext: window.providerContext });
                        window.AndroidBridge.onResult('__CALLBACK_ID__', JSON.stringify(res || []));
                    } else {
                        window.AndroidBridge.onResult('__CALLBACK_ID__', '[]');
                    }
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
                title = item.optString("title", ""),
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
                const fn = mod.getMeta || mod.default?.getMeta || mod;
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
                val title = item.optString("title", "")
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
                            title = dObj.optString("title", ""),
                            link = dObj.optString("link", ""),
                            type = dObj.optString("type", "")
                        ))
                    }
                }
                linkList.add(VegaLink(title, episodesLink, directLinks))
            }
        }
        
        return VegaMeta(
            title = obj.optString("title", ""),
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
                const fn = mod.getStream || mod.default?.getStream;
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
                    const fn = mod.getEpisodes || mod.default?.getEpisodes;
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
            val title = item.optString("title", "")
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
                        title = dObj.optString("title", ""),
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
            webView?.destroy()
            webView = null
        }
        isCompatInjected = false
        loadedScrapers.clear()
    }

    inner class AndroidBridge {
        private val callbacks = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<String>>()
        private var callbackIdCounter = 0

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
        
        // 1. Add undefined/null protection to hubcloudExtractor
        val target1 = "function hubcloudExtractor(link,signal,axios,cheerio,headers2){return __async(this,null,function*(){var _a,_b,_c,_d,_e,_f,_g;try{"
        val replacement1 = "function hubcloudExtractor(link,signal,axios,cheerio,headers2){return __async(this,null,function*(){if(!link||link===\"undefined\")return[];var _a,_b,_c,_d,_e,_f,_g;try{"
        patched = patched.replace(target1, replacement1)

        // 2. Add bypass for direct cloud/drive links in getStream
        val target2 = "\"movie\"===type){const dotlinkText=(yield axios(`${'$'}{link}`,{headers:headers})).data;link=(dotlinkText.match(/<a\\s+href=\"([^\"]*cloud\\.[^\"]*)\"/i)||[])[1];"
        val replacement2 = "\"movie\"===type && link && !link.includes(\"cloud\") && !link.includes(\"pixeld\") && !link.includes(\"dev\")){try{const dotlinkText=(yield axios(`${'$'}{link}`,{headers:headers})).data;const matchedLink=(dotlinkText.match(/<a\\s+href=\"([^\"]*cloud\\.[^\"]*)\"/i)||[])[1];if(matchedLink)link=matchedLink;}catch(e){console.log(\"dotlink error\",e);}}"
        patched = patched.replace(target2, replacement2)
        
        return patched
    }
}
