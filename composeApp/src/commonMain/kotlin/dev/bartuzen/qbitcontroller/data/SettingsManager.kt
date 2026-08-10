package dev.bartuzen.qbitcontroller.data

import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.russhwolf.settings.Settings
import dev.bartuzen.qbitcontroller.model.ProwlarrConfig
import dev.bartuzen.qbitcontroller.ui.theme.defaultPrimaryColor
import dev.bartuzen.qbitcontroller.ui.torrentlist.TorrentFilter

// Computes the initial value visibleTabs should fall back to when its key has never been written -
// see the visibleTabs property doc comment below. Kept as a top-level function (rather than inline
// in the property initializer) purely for readability; it has no state of its own.
private fun defaultVisibleTabs(settings: Settings, hadShowProwlarrTabEnabled: Boolean): Set<OptionalTab> {
    val allVisible = OptionalTab.entries.toSet()
    val isUpgradingFromShowProwlarrTabOnly = !settings.hasKey("visibleTabs") && settings.hasKey("showProwlarrTab")
    return if (isUpgradingFromShowProwlarrTabOnly && !hadShowProwlarrTabEnabled) {
        allVisible - OptionalTab.PROWLARR
    } else {
        allVisible
    }
}

open class SettingsManager(
    settings: Settings,
) {
    val theme = preference(settings, "theme", Theme.SYSTEM_DEFAULT)
    val enableDynamicColors = preference(settings, "enableDynamicColors", true)
    val appColor = preference(
        settings,
        "appColor",
        defaultPrimaryColor,
        serializer = { it.value.shr(32).and(0xFFFFFFu).toString(16).padStart(6, '0') },
        deserializer = { Color(it.toULong(16) or 0xFF000000u shl 32) },
    )
    val paletteStyle = preference(settings, "paletteStyle", PaletteStyle.TonalSpot)
    val pureBlackDarkMode = preference(settings, "pureBlackDarkMode", false)
    val showRelativeTimestamps = preference(settings, "showRelativeTimestamps", true)
    val sort = preference(settings, "sort", TorrentSort.NAME)
    val isReverseSorting = preference(settings, "isReverseSorting", false)
    val connectionTimeout = preference(settings, "connectionTimeout", 10)
    val autoRefreshInterval = preference(settings, "autoRefreshInterval", 3)
    val notificationCheckInterval = preference(settings, "notificationCheckInterval", 15)
    val areTorrentSwipeActionsEnabled = preference(settings, "areTorrentSwipeActionsEnabled", true)
    val trafficStatsInList = preference(settings, "trafficStatsInList", TrafficStats.NONE)
    val hideServerUrls = preference(settings, "hideServerUrls", false)

    val defaultTorrentStatus = preference(settings, "defaultTorrentState", TorrentFilter.ALL)
    val areStatesCollapsed = preference(settings, "areStatesCollapsed", false)
    val areCategoriesCollapsed = preference(settings, "areCategoriesCollapsed", false)
    val areTagsCollapsed = preference(settings, "areTagsCollapsed", false)
    val areTrackersCollapsed = preference(settings, "areTrackersCollapsed", false)

    val searchSort = preference(settings, "searchSort", SearchSort.NAME)
    val isReverseSearchSorting = preference(settings, "isReverseSearchSort", false)

    // Deliberately separate keys from searchSort/isReverseSearchSorting above (same SearchSort
    // enum, independent preference) so switching sort order on one search screen doesn't affect
    // the other - see docs/prowlarr-p1-search-ui-and-tabs-plan.md, section 2.3.
    val prowlarrSearchSort = preference(settings, "prowlarrSearchSort", SearchSort.NAME)
    val isReverseProwlarrSearchSort = preference(settings, "isReverseProwlarrSearchSort", false)

    // Global Prowlarr connection config. A single instance is stored (as opposed to a list like
    // ServerManager's ServerConfigs) since Prowlarr aggregates indexers independently of any one
    // qBittorrent server - see docs/prowlarr-integration-plan.md section 4.3.
    val prowlarrConfig = jsonPreference(settings, "prowlarrConfig", ProwlarrConfig())

    // Superseded by visibleTabs below (see docs/prowlarr-p1-search-ui-and-tabs-plan.md, section
    // 4.3). Kept declared - but no longer written to - purely so the one-time migration below can
    // still read a value for users who had already set it under the old single-tab toggle.
    val showProwlarrTab = preference(settings, "showProwlarrTab", false)

    // Which of the optional bottom-nav tabs are shown; Torrents and Settings are always shown and
    // aren't part of this set. Defaults to everything visible for new users. Users upgrading from a
    // version that only had showProwlarrTab get that toggle folded into the default here the first
    // time this is read (before they've explicitly touched visibleTabs themselves), so their
    // previous show/hide choice for the Prowlarr tab isn't silently lost - see
    // docs/prowlarr-p1-search-ui-and-tabs-plan.md, section 4.3.
    val visibleTabs = preference(
        settings,
        "visibleTabs",
        defaultVisibleTabs(settings, showProwlarrTab.value),
        serializer = { tabs -> tabs.joinToString(",") { it.name } },
        deserializer = { raw ->
            raw.split(",").filter { it.isNotBlank() }.mapNotNull { name ->
                OptionalTab.entries.find { it.name == name }
            }.toSet()
        },
    )

    val checkUpdates = preference(settings, "checkUpdates", true)
}

enum class Theme {
    LIGHT,
    DARK,
    SYSTEM_DEFAULT,
}

enum class TorrentSort {
    NAME,
    STATUS,
    HASH,
    DOWNLOAD_SPEED,
    UPLOAD_SPEED,
    PRIORITY,
    ETA,
    SIZE,
    RATIO,
    PROGRESS,
    CONNECTED_SEEDS,
    TOTAL_SEEDS,
    CONNECTED_LEECHES,
    TOTAL_LEECHES,
    ADDITION_DATE,
    COMPLETION_DATE,
    LAST_ACTIVITY,
    DOWNLOADED,
    UPLOADED,
}

enum class SearchSort {
    NAME,
    SIZE,
    SEEDERS,
    LEECHERS,
    SEARCH_ENGINE,
}

enum class TrafficStats {
    NONE,
    TOTAL,
    SESSION,
    COMPLETE,
}

// The optional bottom-nav tabs a user can hide from appearance settings. Torrents and Settings are
// intentionally not included - they can't be hidden, so there's no "always true, can't uncheck"
// state to model - see docs/prowlarr-p1-search-ui-and-tabs-plan.md, section 4.1.
enum class OptionalTab {
    SEARCH,
    PROWLARR,
    RSS,
    LOGS,
}
