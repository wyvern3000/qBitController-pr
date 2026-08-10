package dev.bartuzen.qbitcontroller.model

import dev.bartuzen.qbitcontroller.model.serializers.NullableIntSerializer
import dev.bartuzen.qbitcontroller.model.serializers.NullableLongSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Search(
    val status: Status,
    val results: List<Result>,
    val total: Int,
) {
    @Serializable
    data class Result(
        @SerialName("descrLink")
        val descriptionLink: String,

        val fileName: String,

        @Serializable(with = NullableLongSerializer::class)
        val fileSize: Long?,

        val fileUrl: String,

        @SerialName("nbLeechers")
        @Serializable(with = NullableIntSerializer::class)
        val leechers: Int?,

        @SerialName("nbSeeders")
        @Serializable(with = NullableIntSerializer::class)
        val seeders: Int?,

        val siteUrl: String,

        // Torznab/Newznab category ids for this result. Always empty for qBittorrent's own search
        // plugin results (that API has no notion of categories) - only Prowlarr-sourced results
        // populate this, via ProwlarrSearchResult.toSearchResult(). No @SerialName since this isn't
        // part of qBittorrent's own search-plugin wire format; the default keeps decoding qBit's
        // actual API responses (which never send this field) working unchanged. Used by
        // ProwlarrSearchViewModel.addTorrent() to pick a matching ProwlarrCategoryRoute - see
        // docs/prowlarr-download-defaults-plan.md, section 2.3.
        val categories: List<Int> = emptyList(),
    )

    enum class Status {
        @SerialName("Running")
        RUNNING,

        @SerialName("Stopped")
        STOPPED,
    }
}
