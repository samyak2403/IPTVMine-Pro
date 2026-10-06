package com.samyak.player.dns

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class DnsMode(
    val key: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val defaultUrl: String
) {
    CLOUDFLARE(
        key = "cloudflare",
        title = "Cloudflare (1.1.1.1)",
        subtitle = "Fast, privacy-first DNS resolver with zero logging",
        badge = "Recommended",
        defaultUrl = "https://1.1.1.1/dns-query"
    ),
    GOOGLE(
        key = "google",
        title = "Google (8.8.8.8)",
        subtitle = "Reliable, globally distributed public DNS by Google",
        badge = "Reliable",
        defaultUrl = "https://8.8.8.8/resolve"
    ),
    ADGUARD(
        key = "adguard",
        title = "AdGuard DNS",
        subtitle = "Privacy protection with built-in ad and tracker blocking",
        badge = "Ad-Blocking",
        defaultUrl = "https://dns.adguard-dns.com/resolve"
    ),
    CUSTOM(
        key = "custom",
        title = "Custom Resolver",
        subtitle = "User-defined DNS over HTTPS (DoH) URL",
        badge = "Custom",
        defaultUrl = ""
    ),
    OFF(
        key = "off",
        title = "Off (System Default)",
        subtitle = "Use default DNS provided by your ISP or device",
        badge = "Default",
        defaultUrl = ""
    );

    companion object {
        fun fromKey(key: String?): DnsMode {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: CLOUDFLARE
        }
    }
}

object DnsPreferenceManager {
    private const val TAG = "DnsPreferenceManager"
    private const val PREFS_NAME = "dns_preferences"
    private const val KEY_DNS_MODE = "dns_mode"
    private const val KEY_CUSTOM_URL = "custom_doh_url"

    private val cache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()
    private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

    private val bootstrapHosts = setOf(
        "1.1.1.1", "1.0.0.1", "cloudflare-dns.com", "one.one.one.one",
        "8.8.8.8", "8.8.4.4", "dns.google",
        "94.140.14.14", "94.140.15.15", "dns.adguard-dns.com"
    )

    // Bootstrap DNS that provides hardcoded IP addresses for DoH domain names
    // This prevents ISPs from blocking the resolution of DoH endpoints themselves
    private val bootstrapDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val lower = hostname.lowercase()
            return try {
                when (lower) {
                    "cloudflare-dns.com", "one.one.one.one" -> listOf(
                        InetAddress.getByName("104.16.249.249"),
                        InetAddress.getByName("104.16.248.249"),
                        InetAddress.getByName("1.1.1.1"),
                        InetAddress.getByName("1.0.0.1")
                    )
                    "dns.google" -> listOf(
                        InetAddress.getByName("8.8.8.8"),
                        InetAddress.getByName("8.8.4.4")
                    )
                    "dns.adguard-dns.com" -> listOf(
                        InetAddress.getByName("94.140.14.14"),
                        InetAddress.getByName("94.140.15.15")
                    )
                    else -> Dns.SYSTEM.lookup(hostname)
                }
            } catch (e: Exception) {
                try {
                    Dns.SYSTEM.lookup(hostname)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
    }

    // Dedicated client with bootstrapDns to avoid circular lookup deadlock and bypass ISP DNS blocks
    private val internalClient by lazy {
        OkHttpClient.Builder()
            .dns(bootstrapDns)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getMode(context: Context): DnsMode {
        val key = getPrefs(context).getString(KEY_DNS_MODE, DnsMode.CLOUDFLARE.key)
        return DnsMode.fromKey(key)
    }

    fun setMode(context: Context, mode: DnsMode) {
        getPrefs(context).edit().putString(KEY_DNS_MODE, mode.key).apply()
        cache.clear()
        Log.d(TAG, "DNS mode changed to: ${mode.name}")
    }

    fun getCustomUrl(context: Context): String {
        return getPrefs(context).getString(KEY_CUSTOM_URL, "") ?: ""
    }

    fun setCustomUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_URL, url.trim()).apply()
        cache.clear()
        Log.d(TAG, "Custom DoH URL changed to: $url")
    }

    fun clearCache() {
        cache.clear()
    }

    /**
     * Returns an OkHttp Dns implementation that dynamically routes lookups according to current user preferences.
     */
    fun getDns(context: Context): Dns {
        val appContext = context.applicationContext
        return object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val mode = getMode(appContext)
                if (mode == DnsMode.OFF) {
                    return Dns.SYSTEM.lookup(hostname)
                }

                // If hostname is an IP literal or a bootstrap host for DoH, resolve directly via system
                if (isBootstrapOrIp(hostname)) {
                    return Dns.SYSTEM.lookup(hostname)
                }

                // Check in-memory cache
                val now = System.currentTimeMillis()
                val cached = cache[hostname]
                if (cached != null && (now - cached.second) < CACHE_TTL_MS) {
                    return cached.first
                }

                // Query DoH
                try {
                    val resolved = performDohLookup(mode, hostname, getCustomUrl(appContext))
                    if (resolved.isNotEmpty()) {
                        cache[hostname] = Pair(resolved, now)
                        return resolved
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "DoH lookup failed for $hostname using ${mode.name}: ${e.message}")
                }

                // Fallback to system DNS
                return Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    private fun isBootstrapOrIp(hostname: String): Boolean {
        if (bootstrapHosts.contains(hostname.lowercase())) return true
        // IPv4 regex check
        if (hostname.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) return true
        // IPv6 check
        if (hostname.contains(":")) return true
        return false
    }

    private fun performDohLookup(mode: DnsMode, hostname: String, customUrl: String): List<InetAddress> {
        val candidateUrls: List<String> = when (mode) {
            DnsMode.CLOUDFLARE -> listOf(
                "https://cloudflare-dns.com/dns-query?name=${hostname}&type=A",
                "https://one.one.one.one/dns-query?name=${hostname}&type=A",
                "https://1.1.1.1/dns-query?name=${hostname}&type=A",
                "https://1.0.0.1/dns-query?name=${hostname}&type=A"
            )
            DnsMode.GOOGLE -> listOf(
                "https://dns.google/resolve?name=${hostname}&type=A",
                "https://8.8.8.8/resolve?name=${hostname}&type=A",
                "https://8.8.4.4/resolve?name=${hostname}&type=A"
            )
            DnsMode.ADGUARD -> listOf(
                "https://dns.adguard-dns.com/resolve?name=${hostname}&type=A",
                "https://94.140.14.14/resolve?name=${hostname}&type=A"
            )
            DnsMode.CUSTOM -> {
                val base = customUrl.trim()
                if (base.isEmpty()) return emptyList()
                val delimiter = if (base.contains("?")) "&" else "?"
                listOf("${base}${delimiter}name=${hostname}&type=A")
            }
            DnsMode.OFF -> return emptyList()
        }

        for (queryUrl in candidateUrls) {
            try {
                // Strictly use "application/dns-json" as multiple types trigger HTTP 400 on Cloudflare
                val request = Request.Builder()
                    .url(queryUrl)
                    .header("Accept", "application/dns-json")
                    .header("User-Agent", "IPTVMinePro-DoH/1.0")
                    .build()

                internalClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "DoH query returned HTTP ${response.code} from $queryUrl")
                        return@use
                    }
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)
                    if (json.has("Answer")) {
                        val answer = json.getJSONArray("Answer")
                        val list = mutableListOf<InetAddress>()
                        for (i in 0 until answer.length()) {
                            val obj = answer.getJSONObject(i)
                            val type = obj.optInt("type", 1)
                            if (type == 1 || type == 28) { // Type 1 (A / IPv4) or 28 (AAAA / IPv6)
                                val data = obj.optString("data", "").trim()
                                if (data.isNotEmpty()) {
                                    try {
                                        list.addAll(InetAddress.getAllByName(data))
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                        if (list.isNotEmpty()) {
                            return list
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "DoH candidate failed ($queryUrl): ${e.message}")
            }
        }
        return emptyList()
    }

    /**
     * Tests connectivity and measures latency of a specific DoH resolver mode.
     * Returns Pair(success: Boolean, latencyMs: Long).
     * If customUrl is required but empty, returns Pair(false, -1L).
     */
    suspend fun testDns(mode: DnsMode, customUrl: String? = null): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        if (mode == DnsMode.OFF) {
            val start = System.currentTimeMillis()
            return@withContext try {
                val addresses = Dns.SYSTEM.lookup("google.com")
                Pair(addresses.isNotEmpty(), System.currentTimeMillis() - start)
            } catch (e: Exception) {
                Pair(false, 0L)
            }
        }

        if (mode == DnsMode.CUSTOM && customUrl.isNullOrBlank()) {
            return@withContext Pair(false, -1L)
        }

        val targetUrl = customUrl ?: ""
        val start = System.currentTimeMillis()
        try {
            var resolved = performDohLookup(mode, "google.com", targetUrl)
            if (resolved.isEmpty()) {
                resolved = performDohLookup(mode, "cloudflare.com", targetUrl)
            }
            if (resolved.isEmpty()) {
                resolved = performDohLookup(mode, "wikipedia.org", targetUrl)
            }
            val elapsed = System.currentTimeMillis() - start
            if (resolved.isNotEmpty()) {
                Pair(true, elapsed)
            } else {
                Pair(false, 0L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "DNS test failed for ${mode.name}: ${e.message}")
            Pair(false, 0L)
        }
    }
}
