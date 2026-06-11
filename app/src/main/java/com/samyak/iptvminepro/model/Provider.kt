package com.samyak.iptvminepro.model

data class Provider(
    val title: String,
    val url: String,
    val userAgent: String? = null,
    val isActive: Boolean = true,
    val channelCount: Int = 0
)
