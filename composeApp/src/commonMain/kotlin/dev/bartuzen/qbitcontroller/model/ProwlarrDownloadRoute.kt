package dev.bartuzen.qbitcontroller.model

import kotlinx.serialization.Serializable

/**
 * A single "if the result matches these criteria, use this save path/category/tags instead" rule -
 * see docs/prowlarr-download-defaults-plan.md, section 2.2, and
 * docs/prowlarr-route-and-category-grouping-plan.md section 3 for the [indexerIds] matching
 * dimension added on top of the original [categoryIds]-only design (that section 3 also covers the
 * rename from `ProwlarrCategoryRoute` this class used to be - the old name stopped being accurate
 * once a route could match by indexer alone, with no category involved at all).
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
 * `prowlarrDownloadRoutes` (Kotlin property name only - the underlying storage key string is still
 * `"prowlarrCategoryRoutes"`, kept unchanged across this rename so existing saved data keeps
 * deserializing correctly). Resolution is "first list entry that matches wins" (list order =
 * user-controlled priority) - see
 * [dev.bartuzen.qbitcontroller.ui.prowlarr.search.resolveProwlarrDownloadRouting] for the exact
 * matching rule across both [categoryIds] and [indexerIds]. A matched route's `null`/empty field
 * falls back to [ProwlarrDownloadDefaults] for that one field individually, not all-or-nothing -
 * e.g. a route can override just [savePath] and still inherit the global default [category]/
 * [tags]. [serverId] follows the same per-field fallback (added in P2 feedback round 1, see
 * docs/prowlarr-p2-feedback-round1-plan.md section 3 - covers e.g. movies routing to one
 * qBittorrent server while music routes to a different one).
 */
@Serializable
data class ProwlarrDownloadRoute(
    // Generated once when the route is created (see ProwlarrDownloadDefaultsViewModel.addRoute());
    // stable across edits so the settings list can locate/update/delete a specific entry without
    // relying on list position or re-deriving an id from mutable fields like name/categoryIds.
    val id: String,
    val name: String,
    val categoryIds: List<Int> = emptyList(),
    // Second, independent matching dimension (plan doc section 3.1) - empty means "any indexer",
    // same wildcard-when-empty convention as categoryIds. Existing saved routes deserialize this
    // as emptyList() (no migration needed), which is exactly the old implied "unrestricted by
    // indexer" behavior, so this is backward compatible by construction. Both categoryIds and
    // indexerIds being empty on the same route is disallowed, but at the UI validation layer, not
    // here - a data class shouldn't reject otherwise-valid states outright (e.g. mid-edit before
    // the user has picked either) - since that combination would silently make the route a hidden
    // second "always matches" default, confusing about why list order matters.
    val indexerIds: List<Int> = emptyList(),
    val serverId: Int? = null,
    val savePath: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
)
