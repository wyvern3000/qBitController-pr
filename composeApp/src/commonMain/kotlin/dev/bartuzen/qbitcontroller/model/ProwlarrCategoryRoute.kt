package dev.bartuzen.qbitcontroller.model

import kotlinx.serialization.Serializable

/**
 * A single "if the result's Torznab categories include one of these ids, use this save
 * path/category/tags instead" rule - see docs/prowlarr-download-defaults-plan.md, section 2.2.
 *
 * Deliberately covers **only** [savePath]/[category]/[tags] - the destination-routing fields the
 * user actually asked for ("movie category to one place, music to another") - and not the full
 * [ProwlarrDownloadDefaults] field set (speed/ratio/seeding limits, paused, skip hash check,
 * autoTMM, sequential download, first-last-piece). Confirmed with the user (2026-08-10, plan doc
 * section 7): those behavior knobs stay global-only, since the request was about destination
 * routing, not per-category behavior policy, and covering every field per-route would roughly
 * double the edit UI's field count for a need that wasn't actually asked for.
 *
 * Stored as a list via [dev.bartuzen.qbitcontroller.data.SettingsManager]'s
 * `prowlarrCategoryRoutes`. Resolution is "first list entry whose [categoryIds] intersects the
 * result's categories wins" (list order = user-controlled priority) - see
 * [dev.bartuzen.qbitcontroller.ui.prowlarr.search.resolveProwlarrDownloadRouting]. A matched
 * route's `null`/empty field falls back to [ProwlarrDownloadDefaults] for that one field
 * individually, not all-or-nothing - e.g. a route can override just [savePath] and still inherit
 * the global default [category]/[tags]. [serverId] follows the same per-field fallback (added in
 * P2 feedback round 1, see docs/prowlarr-p2-feedback-round1-plan.md section 3 - covers e.g. movies
 * routing to one qBittorrent server while music routes to a different one).
 */
@Serializable
data class ProwlarrCategoryRoute(
    // Generated once when the route is created (see ProwlarrDownloadDefaultsViewModel.addRoute());
    // stable across edits so the settings list can locate/update/delete a specific entry without
    // relying on list position or re-deriving an id from mutable fields like name/categoryIds.
    val id: String,
    val name: String,
    val categoryIds: List<Int>,
    val serverId: Int? = null,
    val savePath: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
)
