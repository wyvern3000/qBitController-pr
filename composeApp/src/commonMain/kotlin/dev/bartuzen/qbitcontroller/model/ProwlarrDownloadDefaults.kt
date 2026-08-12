package dev.bartuzen.qbitcontroller.model

import kotlinx.serialization.Serializable

/**
 * The always-applied fallback download params for a Prowlarr search result "Download" tap - see
 * docs/prowlarr-download-defaults-plan.md. Field set is copied straight from
 * [dev.bartuzen.qbitcontroller.data.repositories.AddTorrentRepository.addTorrent]'s parameter list
 * (the same set [dev.bartuzen.qbitcontroller.ui.addtorrent.AddTorrentScreen] exposes for manually
 * added torrents), minus `torrentName` - renaming every torrent to a single fixed string across an
 * entire default profile doesn't make sense, so that one is always passed as `null` regardless of
 * what's configured here.
 *
 * Stored as a single JSON blob via [dev.bartuzen.qbitcontroller.data.SettingsManager]'s
 * `prowlarrDownloadDefaults` (same `jsonPreference` mechanism as `prowlarrConfig`), not a list -
 * there is always exactly one of these, used as the fallback when no [ProwlarrCategoryRoute]
 * matches a result's categories (or when it does match but a given route field is `null`/empty -
 * see [ProwlarrCategoryRoute] KDoc).
 */
@Serializable
data class ProwlarrDownloadDefaults(
    // The qBittorrent server (see dev.bartuzen.qbitcontroller.model.ServerConfig.id) results get
    // added to when no ProwlarrCategoryRoute overrides it either - see
    // dev.bartuzen.qbitcontroller.ui.prowlarr.search.resolveProwlarrDownloadRouting. null means "not
    // set", which falls back to whichever server is currently active elsewhere in the app (the same
    // single-server behavior this had before P2 feedback round 1 added multi-server awareness here -
    // see docs/prowlarr-p2-feedback-round1-plan.md, section 3). Added because a user managing several
    // qBittorrent servers has no other way to say which one Prowlarr downloads should land on by
    // default.
    val serverId: Int? = null,
    val savePath: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    // null | "None" | "MetadataReceived" | "FilesChecked" - same string values AddTorrentScreen sends,
    // null meaning "let the server decide" (server-side default), not "None" (which is itself one of
    // the three explicit choices qBittorrent offers).
    val stopCondition: String? = null,
    // null | "Original" | "Subfolder" | "NoSubfolder" - same values/semantics as stopCondition above.
    val contentLayout: String? = null,
    // KiB/s. null means "don't send a limit" (server/global default applies), which is distinct from
    // an explicit 0 (qBittorrent's own "unlimited" value) - this mirrors AddTorrentViewModel's
    // handling, which only sends dlLimit/upLimit when the user actually typed something.
    val downloadSpeedLimit: Int? = null,
    val uploadSpeedLimit: Int? = null,
    val ratioLimit: Double? = null,
    // Minutes, matching qBittorrent's seedingTimeLimit API field.
    val seedingTimeLimit: Int? = null,
    val isPaused: Boolean = false,
    val skipHashChecking: Boolean = false,
    // null = follow the server's global Automatic Torrent Management setting, matching
    // AddTorrentScreen's three-state autoTmm picker (index 0 there).
    val isAutoTorrentManagementEnabled: Boolean? = null,
    val isSequentialDownloadEnabled: Boolean = false,
    val isFirstLastPiecePrioritized: Boolean = false,
)
