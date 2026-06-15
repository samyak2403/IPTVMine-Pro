package com.samyak.television.model

data class VegaProvider(
    val display_name: String,
    val value: String,
    val version: String,
    val icon: String = "",
    val type: String = "global",
    val disabled: Boolean = false,
    var isInstalled: Boolean = true
)

data class VegaCatalog(
    val title: String,
    val filter: String
)

data class VegaPost(
    val title: String,
    val link: String,
    val image: String
)

data class VegaMeta(
    val title: String,
    val synopsis: String,
    val image: String,
    val imdbId: String,
    val type: String,
    val linkList: List<VegaLink> = emptyList()
)

data class VegaLink(
    val title: String,
    val episodesLink: String? = null,
    val directLinks: List<VegaDirectLink>? = null
)

data class VegaDirectLink(
    val title: String,
    val link: String,
    val type: String
)

data class VegaStream(
    val server: String,
    val link: String,
    val type: String,
    val quality: String,
    val headers: Map<String, String>? = null
)
