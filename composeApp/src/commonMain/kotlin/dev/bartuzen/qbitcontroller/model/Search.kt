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
        // ProwlarrSearchViewModel.addTorrent() to pick a matching ProwlarrDownloadRoute - see
        // docs/prowlarr-download-defaults-plan.md, section 2.3.
        val categories: List<Int> = emptyList(),

        // Torznab/Newznab "indexer flags" - site-specific promo tags (Freeleech/Halfleech/etc,
        // see docs/prowlarr-p2-feedback-round1-plan.md section 4). Same rationale as [categories]
        // above: always empty for qBittorrent's own search plugin results, no @SerialName since
        // it's not part of that wire format, only Prowlarr results populate it.
        val indexerFlags: List<String> = emptyList(),

        // Which Prowlarr indexer (site) this result came from - the second matching dimension for
        // ProwlarrDownloadRoute (docs/prowlarr-route-and-category-grouping-plan.md section 3), used
        // by resolveProwlarrDownloadRouting() alongside [categories]. Same null/empty-for-qBit-own-
        // results rationale as [categories]/[indexerFlags] above, but nullable rather than an empty
        // list/default: unlike those two (naturally "no categories"/"no flags" when absent), a
        // missing indexer *id* has no sensible non-null default that wouldn't accidentally match a
        // real indexer's id, so null unambiguously means "not a Prowlarr result" or "not yet
        // verified against a real response" rather than "indexer 0". Like [categories] before it,
        // this field's presence/shape in Prowlarr's actual /api/v1/search response hasn't been
        // confirmed against a real device yet - see the "待确认事项" note this adds to PROGRESS.md.
        val indexerId: Int? = null,
    )

    enum class Status {
        @SerialName("Running")
        RUNNING,

        @SerialName("Stopped")
        STOPPED,
    }
}
