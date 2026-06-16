package com.samyak.television.model

enum class ProviderType {
    IPTV,
    VEGA
}

data class Provider(
    val title: String,
    val url: String,
    val userAgent: String? = null,
    val isActive: Boolean = true,
    val channelCount: Int = 0,
    val type: ProviderType? = ProviderType.IPTV
) {
    val safeType: ProviderType
        get() = type ?: ProviderType.IPTV
}
