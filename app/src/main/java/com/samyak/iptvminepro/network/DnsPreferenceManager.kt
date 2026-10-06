package com.samyak.iptvminepro.network

import android.content.Context
import okhttp3.Dns

typealias DnsMode = com.samyak.player.dns.DnsMode

object DnsPreferenceManager {
    fun getMode(context: Context): DnsMode = com.samyak.player.dns.DnsPreferenceManager.getMode(context)
    fun setMode(context: Context, mode: DnsMode) = com.samyak.player.dns.DnsPreferenceManager.setMode(context, mode)
    fun getCustomUrl(context: Context): String = com.samyak.player.dns.DnsPreferenceManager.getCustomUrl(context)
    fun setCustomUrl(context: Context, url: String) = com.samyak.player.dns.DnsPreferenceManager.setCustomUrl(context, url)
    fun clearCache() = com.samyak.player.dns.DnsPreferenceManager.clearCache()
    fun getDns(context: Context): Dns = com.samyak.player.dns.DnsPreferenceManager.getDns(context)
    suspend fun testDns(mode: DnsMode, customUrl: String? = null): Pair<Boolean, Long> =
        com.samyak.player.dns.DnsPreferenceManager.testDns(mode, customUrl)
}
