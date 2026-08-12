package dev.bartuzen.qbitcontroller.ui.prowlarr.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bartuzen.qbitcontroller.data.SearchSort
import dev.bartuzen.qbitcontroller.data.ServerManager
import dev.bartuzen.qbitcontroller.data.SettingsManager
import dev.bartuzen.qbitcontroller.data.repositories.AddTorrentRepository
import dev.bartuzen.qbitcontroller.data.repositories.ProwlarrRepository
import dev.bartuzen.qbitcontroller.data.repositories.search.ProwlarrSearchRepository
import dev.bartuzen.qbitcontroller.model.Category
import dev.bartuzen.qbitcontroller.model.ProwlarrCategoryRoute
import dev.bartuzen.qbitcontroller.model.ProwlarrDownloadDefaults
import dev.bartuzen.qbitcontroller.model.ProwlarrIndexer
import dev.bartuzen.qbitcontroller.model.Search
import dev.bartuzen.qbitcontroller.network.RequestResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Drives the standalone Prowlarr search screen (see docs/prowlarr-integration-plan.md, round 3).
 * Deliberately separate from [dev.bartuzen.qbitcontroller.ui.search.result.SearchResultViewModel]:
 * Prowlarr's `/api/v1/search` returns everything in one response, so there is no
 * start/poll/stop lifecycle to manage here, and this screen isn't tied to a particular
 * qBittorrent server the way qBittorrent-plugin search is (only adding a torrent needs one,
 * chosen at call time in [addTorrent]).
 */
class ProwlarrSearchViewModel(
    private val prowlarrRepository: ProwlarrRepository,
    private val prowlarrSearchRepository: ProwlarrSearchRepository,
    private val addTorrentRepository: AddTorrentRepository,
    private val settingsManager: SettingsManager,
    serverManager: ServerManager,
) : ViewModel() {
    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    val configFlow = prowlarrRepository.configFlow

    // For ProwlarrManualAddDialog's server picker - P2 feedback round 1 item 5, see
    // docs/prowlarr-p2-feedback-round1-plan.md section 5.
    val servers = serverManager.serversFlow

    // Non-reactive snapshot, matching how ProwlarrDownloadDefaultsViewModel/addTorrent() already
    // read this - a settings change mid-search-session isn't expected to update an already-open
    // manual dialog live.
    val downloadDefaults get() = settingsManager.prowlarrDownloadDefaults.value

    /**
     * Same resolution [addTorrent] uses internally, exposed so
     * [dev.bartuzen.qbitcontroller.ui.prowlarr.search.ProwlarrManualAddDialog] can pre-fill its
     * fields with the same starting point the auto path would use - see
     * docs/prowlarr-p2-feedback-round1-plan.md section 5.
     */
    fun resolveDownloadRouting(resultCategoryIds: List<Int>): ProwlarrResolvedDownloadRouting {
        val defaults = settingsManager.prowlarrDownloadDefaults.value
        val routes = settingsManager.prowlarrCategoryRoutes.value
        return resolveProwlarrDownloadRouting(resultCategoryIds, routes, defaults)
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _results = MutableStateFlow<List<Search.Result>>(emptyList())
    val results = _results.asStateFlow()

    // Independent from SettingsManager.searchSort/isReverseSearchSorting (the qBit-plugin result
    // screen's own preferences) - see docs/prowlarr-p1-search-ui-and-tabs-plan.md, section 2.3.
    // Sort order is the only piece of Prowlarr result state that's a persisted user setting rather
    // than session-only screen state - the min/seed/size/indexer Filter below is intentionally kept
    // at the Composable level (rememberSaveable) instead, since it only needs to survive this
    // search session, not persist across app restarts.
    val searchSort = settingsManager.prowlarrSearchSort.flow
    val isReverseSearchSort = settingsManager.isReverseProwlarrSearchSort.flow

    private val _isAdding = MutableStateFlow(false)
    val isAdding = _isAdding.asStateFlow()

    // null means "not loaded yet" (either still loading, or the fetch failed) - distinct from an
    // empty list, which would mean Prowlarr genuinely has zero indexers configured. Screen code
    // should treat null as "fall back to an unrestricted search", per ProwlarrRepository.getIndexers
    // KDoc: a failure here must not block searching.
    private val _indexers = MutableStateFlow<List<ProwlarrIndexer>?>(null)
    val indexers = _indexers.asStateFlow()

    private val _isLoadingIndexers = MutableStateFlow(false)
    val isLoadingIndexers = _isLoadingIndexers.asStateFlow()

    // Categories/tags/default save path for whichever server is currently picked in
    // ProwlarrManualAddDialog - null means "not loaded (yet)". Reloaded from scratch on every
    // server switch, same shape as AddTorrentViewModel.ServerData but session-only (no
    // SavedStateHandle persistence): a config-change-only cache isn't worth the complexity here,
    // unlike AddTorrentScreen which is a whole navigation destination. Directory-suggestion
    // autocomplete (AddTorrentViewModel.directorySuggestions) is deliberately not replicated - see
    // docs/prowlarr-p2-feedback-round1-plan.md section 5's explicit scope cut.
    private val _manualAddServerData = MutableStateFlow<ManualAddServerData?>(null)
    val manualAddServerData = _manualAddServerData.asStateFlow()

    private val _isLoadingManualAddServerData = MutableStateFlow(false)
    val isLoadingManualAddServerData = _isLoadingManualAddServerData.asStateFlow()

    private var searchJob: Job? = null
    private var addTorrentJob: Job? = null
    private var loadIndexersJob: Job? = null
    private var loadManualAddServerDataJob: Job? = null

    fun search(query: String, indexerIds: List<Int>? = null, categories: List<Int>? = null) {
        if (query.isBlank()) {
            searchJob?.cancel()
            _results.value = emptyList()
            return
        }

        searchJob?.cancel()

        _isLoading.value = true
        val job = viewModelScope.launch {
            when (val result = prowlarrSearchRepository.search(query, indexerIds, categories)) {
                is RequestResult.Success -> _results.value = result.data
                is RequestResult.Error -> {
                    _results.value = emptyList()
                    eventChannel.send(Event.SearchError(result))
                }
            }
        }

        job.invokeOnCompletion { e ->
            if (e !is CancellationException) {
                _isLoading.value = false
                searchJob = null
            }
        }
        searchJob = job
    }

    /**
     * Populates the indexer multi-select (see docs/prowlarr-p1-search-ui-and-tabs-plan.md, section
     * 2.1). Safe to call repeatedly (e.g. from a LaunchedEffect keyed on the config becoming
     * configured) - a call already in flight is not duplicated.
     */
    fun loadIndexers() {
        if (loadIndexersJob != null) {
            return
        }

        _isLoadingIndexers.value = true
        val job = viewModelScope.launch {
            when (val result = prowlarrRepository.getIndexers()) {
                is RequestResult.Success -> _indexers.value = result.data
                is RequestResult.Error -> eventChannel.send(Event.IndexersError(result))
            }
        }

        job.invokeOnCompletion { e ->
            if (e !is CancellationException) {
                _isLoadingIndexers.value = false
                loadIndexersJob = null
            }
        }
        loadIndexersJob = job
    }

    /**
     * Loads categories/tags/default save path for [serverId] - see [manualAddServerData] KDoc.
     * Safe to call repeatedly (e.g. every time the manual dialog's server picker changes); a call
     * already in flight for a previous server is cancelled first, since only the latest selection
     * matters.
     */
    fun loadManualAddServerData(serverId: Int) {
        loadManualAddServerDataJob?.cancel()
        _manualAddServerData.value = null

        _isLoadingManualAddServerData.value = true
        val job = viewModelScope.launch {
            val categoriesDeferred = async {
                when (val result = addTorrentRepository.getCategories(serverId)) {
                    is RequestResult.Success -> result.data.values.toList().sortedWith(Category.comparator)
                    is RequestResult.Error -> {
                        eventChannel.send(Event.Error(result))
                        throw CancellationException()
                    }
                }
            }
            val tagsDeferred = async {
                when (val result = addTorrentRepository.getTags(serverId)) {
                    is RequestResult.Success -> result.data.sorted()
                    is RequestResult.Error -> {
                        eventChannel.send(Event.Error(result))
                        throw CancellationException()
                    }
                }
            }
            val defaultSavePathDeferred = async {
                when (val result = addTorrentRepository.getDefaultSavePath(serverId)) {
                    is RequestResult.Success -> result.data
                    is RequestResult.Error -> {
                        eventChannel.send(Event.Error(result))
                        throw CancellationException()
                    }
                }
            }

            try {
                _manualAddServerData.value = ManualAddServerData(
                    categories = categoriesDeferred.await(),
                    tags = tagsDeferred.await(),
                    defaultSavePath = defaultSavePathDeferred.await(),
                )
            } catch (_: CancellationException) {
            }
        }

        job.invokeOnCompletion { e ->
            if (e !is CancellationException) {
                _isLoadingManualAddServerData.value = false
                loadManualAddServerDataJob = null
            }
        }
        loadManualAddServerDataJob = job
    }

    /**
     * Adds [searchResult] using the user's configured Prowlarr download defaults/category routes
     * (`SettingsManager.prowlarrDownloadDefaults`/`prowlarrCategoryRoutes`) - no per-download
     * popup, see docs/prowlarr-download-defaults-plan.md (this replaces the fixed-empty-defaults
     * behavior from docs/prowlarr-integration-plan.md, round 3, and formally supersedes the
     * never-implemented "jump to AddTorrentScreen every download" direction from
     * docs/prowlarr-p1-search-ui-and-tabs-plan.md section 2.4, which directly conflicted with "no
     * popup per download").
     *
     * [fallbackServerId] is the server currently active elsewhere in the app (see
     * [dev.bartuzen.qbitcontroller.ui.prowlarr.search.ProwlarrSearchScreen]'s own `serverId`
     * param) - only used when neither a matching [ProwlarrCategoryRoute] nor
     * [ProwlarrDownloadDefaults] configures a server (P2 feedback round 1, see
     * docs/prowlarr-p2-feedback-round1-plan.md section 3: a user with several qBittorrent servers
     * needs a way to say which one Prowlarr downloads land on that isn't just "whichever one
     * happens to be open right now"). If nothing resolves a server at all,
     * [Event.NoServerAvailable] is sent instead of silently failing.
     *
     * Magnet links are passed straight through as a link. Everything else is assumed to be a
     * direct .torrent file link and is downloaded by this device first, then uploaded to
     * qBittorrent as file bytes - see [ProwlarrSearchRepository.downloadTorrentFile].
     */
    fun addTorrent(fallbackServerId: Int?, searchResult: Search.Result) {
        if (_isAdding.value) {
            return
        }

        val defaults = settingsManager.prowlarrDownloadDefaults.value
        val routes = settingsManager.prowlarrCategoryRoutes.value
        val routing = resolveProwlarrDownloadRouting(searchResult.categories, routes, defaults)
        val serverId = routing.serverId ?: fallbackServerId
        if (serverId == null) {
            viewModelScope.launch { eventChannel.send(Event.NoServerAvailable) }
            return
        }

        _isAdding.value = true
        val job = viewModelScope.launch {
            val result = if (searchResult.fileUrl.startsWith("magnet:", ignoreCase = true)) {
                addTorrent(
                    serverId,
                    links = listOf(searchResult.fileUrl),
                    files = null,
                    routing = routing,
                    defaults = defaults,
                )
            } else {
                when (val fileResult = prowlarrSearchRepository.downloadTorrentFile(searchResult.fileUrl)) {
                    is RequestResult.Success -> {
                        val fileName = searchResult.fileName.let {
                            if (it.endsWith(".torrent", ignoreCase = true)) it else "$it.torrent"
                        }
                        addTorrent(
                            serverId,
                            links = null,
                            files = listOf(fileName to fileResult.data),
                            routing = routing,
                            defaults = defaults,
                        )
                    }
                    is RequestResult.Error -> fileResult
                }
            }

            when (result) {
                is RequestResult.Success -> {
                    if (result.data == "Fails.") {
                        eventChannel.send(Event.AddTorrentError)
                    } else {
                        eventChannel.send(Event.AddTorrentSuccess)
                    }
                }
                is RequestResult.Error.ApiError if result.code == 409 -> {
                    eventChannel.send(Event.AddTorrentError)
                }
                is RequestResult.Error.ApiError if result.code == 415 -> {
                    eventChannel.send(Event.InvalidTorrentFile)
                }
                is RequestResult.Error -> {
                    eventChannel.send(Event.Error(result))
                }
            }
        }

        job.invokeOnCompletion { e ->
            if (e !is CancellationException) {
                _isAdding.value = false
                addTorrentJob = null
            }
        }
        addTorrentJob = job
    }

    /**
     * Manual-mode counterpart to [addTorrent] (P2 feedback round 1 item 5, see
     * docs/prowlarr-p2-feedback-round1-plan.md section 5): same magnet-passthrough/client-side-
     * download-then-upload mechanism, but every param comes from what the user confirmed in
     * [dev.bartuzen.qbitcontroller.ui.prowlarr.search.ProwlarrManualAddDialog] instead of
     * [resolveProwlarrDownloadRouting]'s auto-resolved values.
     */
    fun addTorrentManual(serverId: Int, searchResult: Search.Result, options: ManualDownloadOptions) {
        if (_isAdding.value) {
            return
        }

        _isAdding.value = true
        val job = viewModelScope.launch {
            val result = if (searchResult.fileUrl.startsWith("magnet:", ignoreCase = true)) {
                addTorrentManual(serverId, links = listOf(searchResult.fileUrl), files = null, options = options)
            } else {
                when (val fileResult = prowlarrSearchRepository.downloadTorrentFile(searchResult.fileUrl)) {
                    is RequestResult.Success -> {
                        val fileName = searchResult.fileName.let {
                            if (it.endsWith(".torrent", ignoreCase = true)) it else "$it.torrent"
                        }
                        addTorrentManual(
                            serverId,
                            links = null,
                            files = listOf(fileName to fileResult.data),
                            options = options,
                        )
                    }
                    is RequestResult.Error -> fileResult
                }
            }

            when (result) {
                is RequestResult.Success -> {
                    if (result.data == "Fails.") {
                        eventChannel.send(Event.AddTorrentError)
                    } else {
                        eventChannel.send(Event.AddTorrentSuccess)
                    }
                }
                is RequestResult.Error.ApiError if result.code == 409 -> {
                    eventChannel.send(Event.AddTorrentError)
                }
                is RequestResult.Error.ApiError if result.code == 415 -> {
                    eventChannel.send(Event.InvalidTorrentFile)
                }
                is RequestResult.Error -> {
                    eventChannel.send(Event.Error(result))
                }
            }
        }

        job.invokeOnCompletion { e ->
            if (e !is CancellationException) {
                _isAdding.value = false
                addTorrentJob = null
            }
        }
        addTorrentJob = job
    }

    fun setSearchSort(searchSort: SearchSort) {
        settingsManager.prowlarrSearchSort.value = searchSort
    }

    fun changeReverseSorting() {
        settingsManager.isReverseProwlarrSearchSort.value = !isReverseSearchSort.value
    }

    private suspend fun addTorrent(
        serverId: Int,
        links: List<String>?,
        files: List<Pair<String, ByteArray>>?,
        routing: ProwlarrResolvedDownloadRouting,
        defaults: ProwlarrDownloadDefaults,
    ): RequestResult<String> = addTorrentRepository.addTorrent(
        serverId = serverId,
        links = links,
        files = files,
        savePath = routing.savePath,
        category = routing.category,
        tags = routing.tags,
        stopCondition = defaults.stopCondition,
        contentLayout = defaults.contentLayout,
        torrentName = null,
        downloadSpeedLimit = defaults.downloadSpeedLimit,
        uploadSpeedLimit = defaults.uploadSpeedLimit,
        ratioLimit = defaults.ratioLimit,
        seedingTimeLimit = defaults.seedingTimeLimit,
        isPaused = defaults.isPaused,
        skipHashChecking = defaults.skipHashChecking,
        isAutoTorrentManagementEnabled = defaults.isAutoTorrentManagementEnabled,
        isSequentialDownloadEnabled = defaults.isSequentialDownloadEnabled,
        isFirstLastPiecePrioritized = defaults.isFirstLastPiecePrioritized,
    )

    private suspend fun addTorrentManual(
        serverId: Int,
        links: List<String>?,
        files: List<Pair<String, ByteArray>>?,
        options: ManualDownloadOptions,
    ): RequestResult<String> = addTorrentRepository.addTorrent(
        serverId = serverId,
        links = links,
        files = files,
        savePath = options.savePath,
        category = options.category,
        tags = options.tags,
        stopCondition = options.stopCondition,
        contentLayout = options.contentLayout,
        torrentName = options.torrentName,
        downloadSpeedLimit = options.downloadSpeedLimit,
        uploadSpeedLimit = options.uploadSpeedLimit,
        ratioLimit = options.ratioLimit,
        seedingTimeLimit = options.seedingTimeLimit,
        isPaused = options.isPaused,
        skipHashChecking = options.skipHashChecking,
        isAutoTorrentManagementEnabled = options.isAutoTorrentManagementEnabled,
        isSequentialDownloadEnabled = options.isSequentialDownloadEnabled,
        isFirstLastPiecePrioritized = options.isFirstLastPiecePrioritized,
    )

    /**
     * User-confirmed values from [dev.bartuzen.qbitcontroller.ui.prowlarr.search.ProwlarrManualAddDialog]
     * - see [addTorrentManual]. Same field set as [ProwlarrDownloadDefaults] plus [torrentName]
     * (meaningful for a single manual download, unlike a shared default profile - see
     * [ProwlarrDownloadDefaults] KDoc for why that one is always null on the auto path).
     */
    data class ManualDownloadOptions(
        val savePath: String?,
        val category: String?,
        val tags: List<String>,
        val torrentName: String?,
        val stopCondition: String?,
        val contentLayout: String?,
        val downloadSpeedLimit: Int?,
        val uploadSpeedLimit: Int?,
        val ratioLimit: Double?,
        val seedingTimeLimit: Int?,
        val isPaused: Boolean,
        val skipHashChecking: Boolean,
        val isAutoTorrentManagementEnabled: Boolean?,
        val isSequentialDownloadEnabled: Boolean,
        val isFirstLastPiecePrioritized: Boolean,
    )

    /**
     * Loaded per selected server in [dev.bartuzen.qbitcontroller.ui.prowlarr.search.ProwlarrManualAddDialog]
     * - see [manualAddServerData] KDoc. Same shape as
     * [dev.bartuzen.qbitcontroller.ui.addtorrent.ServerData] but declared separately rather than
     * reused directly: that type lives in the `addtorrent` package and is really about that
     * screen's own [androidx.lifecycle.SavedStateHandle]-backed persistence, which this doesn't
     * need or want.
     */
    data class ManualAddServerData(
        val categories: List<Category>,
        val tags: List<String>,
        val defaultSavePath: String,
    )

    /**
     * Result filter for [ProwlarrSearchScreen] (deliberately a
     * separate type from [dev.bartuzen.qbitcontroller.ui.search.result.SearchResultViewModel.Filter]
     * rather than a shared one - see docs/prowlarr-p1-search-ui-and-tabs-plan.md, section 2.3 - the
     * seeds/size min/max comparison logic below is copied from there, not reused).
     *
     * [indexerQuery] is the one filter dimension the qBit result screen doesn't have: a single
     * Prowlarr search can span dozens of different indexers (unlike a qBit plugin search's more
     * limited notion of "source"), so filtering results down to a keyword match against the
     * originating indexer is useful here specifically. Matched against [Search.Result.siteUrl]
     * (which holds the indexer name for Prowlarr-sourced results) case-insensitively, split on
     * spaces with +/- term exclusion - same syntax as the existing free-text result filter.
     *
     * [keyword] matches against the release title ([Search.Result.fileName]) - same split/+-
     * syntax as [indexerQuery] and as the qBit result screen's own free-text filter
     * ([dev.bartuzen.qbitcontroller.ui.search.result.SearchResultViewModel]'s `filterQuery`).
     * Deliberately title-only, **not** "title and description/synopsis" despite that being how
     * this was first requested: checked Prowlarr's actual `/api/v1/search` response schema (both
     * the official docs and several third-party client libraries' field lists) and it doesn't
     * return a synopsis/description text field at all - Torznab/Newznab search results never have
     * one, only a title. The qBit-plugin result screen's equivalent filter is title-only for the
     * same reason, so this isn't a gap specific to the Prowlarr integration.
     *
     * Declared here (rather than only inline in the Composable) purely so the type is discoverable
     * alongside the rest of this screen's domain concepts, even though actual instances of it live
     * as Composable-level `rememberSaveable` state, not anything this ViewModel reads or writes -
     * unlike [searchSort]/[isReverseSearchSort] above, filter values are session-only and don't need
     * to survive an app restart.
     */
    @Serializable
    data class Filter(
        val seedsMin: Int? = null,
        val seedsMax: Int? = null,
        val sizeMin: Long? = null,
        val sizeMax: Long? = null,
        val sizeMinUnit: Int = 2,
        val sizeMaxUnit: Int = 2,
        val indexerQuery: String = "",
        val keyword: String = "",
        // Selected indexer flags (e.g. "freeleech"/"halfleech" - see Search.Result.indexerFlags).
        // A result matches if it carries ANY of these (OR, not AND) - freeleech/halfleech are
        // normally mutually exclusive per result, so requiring all selected flags at once would
        // rarely match anything. Empty = no flag filtering. See
        // docs/prowlarr-p2-feedback-round1-plan.md section 4.
        val flags: List<String> = emptyList(),
    ) {
        private fun Int.pow(x: Int): Long {
            var number = 1L
            repeat(x) {
                number *= this
            }
            return number
        }

        val sizeMinBytes = if (sizeMin != null) {
            sizeMin * 1024.pow(sizeMinUnit)
        } else {
            null
        }

        val sizeMaxBytes = if (sizeMax != null) {
            sizeMax * 1024.pow(sizeMaxUnit)
        } else {
            null
        }
    }

    sealed class Event {
        data class SearchError(val error: RequestResult.Error) : Event()
        data class IndexersError(val error: RequestResult.Error) : Event()
        data class Error(val error: RequestResult.Error) : Event()
        data object InvalidTorrentFile : Event()
        data object AddTorrentError : Event()
        data object AddTorrentSuccess : Event()

        // Neither a matching ProwlarrCategoryRoute nor ProwlarrDownloadDefaults configured a
        // server, and the app doesn't currently have one active either - see addTorrent() KDoc.
        data object NoServerAvailable : Event()
    }
}

/**
 * Resolves the server/save path/category/tags to actually submit for a Prowlarr result, given its
 * Torznab [resultCategoryIds] - see docs/prowlarr-download-defaults-plan.md, sections 2.2/3, and
 * docs/prowlarr-p2-feedback-round1-plan.md section 3 for [ProwlarrResolvedDownloadRouting.serverId].
 *
 * The first entry in [routes] (user-controlled priority via list order, not "most specific match"
 * or any other automatic ranking) whose [ProwlarrCategoryRoute.categoryIds] intersects
 * [resultCategoryIds] wins. A matched route's own `null`/empty field falls back to [defaults] for
 * that field individually - e.g. a route can override just `savePath` and still inherit the global
 * default `category`/`tags` - rather than being all-or-nothing. No route matching (including when
 * [resultCategoryIds] is empty, e.g. an indexer that doesn't report categories) falls straight back
 * to [defaults] for all fields.
 *
 * Deliberately doesn't know about "the server currently active elsewhere in the app" - that
 * fallback is the caller's job (see [ProwlarrSearchViewModel.addTorrent]), not this pure function's.
 *
 * Pure function, no ViewModel/state dependency, so it's usable from [ProwlarrSearchViewModel]
 * without needing test doubles for anything beyond plain data.
 */
internal fun resolveProwlarrDownloadRouting(
    resultCategoryIds: List<Int>,
    routes: List<ProwlarrCategoryRoute>,
    defaults: ProwlarrDownloadDefaults,
): ProwlarrResolvedDownloadRouting {
    val route = routes.firstOrNull { route -> route.categoryIds.any { it in resultCategoryIds } }
    return if (route == null) {
        ProwlarrResolvedDownloadRouting(defaults.serverId, defaults.savePath, defaults.category, defaults.tags)
    } else {
        ProwlarrResolvedDownloadRouting(
            serverId = route.serverId ?: defaults.serverId,
            savePath = route.savePath ?: defaults.savePath,
            category = route.category ?: defaults.category,
            tags = route.tags.ifEmpty { defaults.tags },
        )
    }
}

internal data class ProwlarrResolvedDownloadRouting(
    val serverId: Int?,
    val savePath: String?,
    val category: String?,
    val tags: List<String>,
)
