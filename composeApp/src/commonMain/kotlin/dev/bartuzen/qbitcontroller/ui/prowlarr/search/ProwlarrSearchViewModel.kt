package dev.bartuzen.qbitcontroller.ui.prowlarr.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bartuzen.qbitcontroller.data.repositories.AddTorrentRepository
import dev.bartuzen.qbitcontroller.data.repositories.ProwlarrRepository
import dev.bartuzen.qbitcontroller.data.repositories.search.ProwlarrSearchRepository
import dev.bartuzen.qbitcontroller.model.Search
import dev.bartuzen.qbitcontroller.network.RequestResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

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
) : ViewModel() {
    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    val configFlow = prowlarrRepository.configFlow

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _results = MutableStateFlow<List<Search.Result>>(emptyList())
    val results = _results.asStateFlow()

    private val _isAdding = MutableStateFlow(false)
    val isAdding = _isAdding.asStateFlow()

    private var searchJob: Job? = null
    private var addTorrentJob: Job? = null

    fun search(query: String) {
        if (query.isBlank()) {
            searchJob?.cancel()
            _results.value = emptyList()
            return
        }

        searchJob?.cancel()

        _isLoading.value = true
        val job = viewModelScope.launch {
            when (val result = prowlarrSearchRepository.search(query)) {
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
     * Adds [searchResult] to the server identified by [serverId] using fixed defaults (server's
     * default save path/category, no tags, not paused) - there is no save-path/category picker in
     * this round, see docs/prowlarr-integration-plan.md, round 3 notes.
     *
     * Magnet links are passed straight through as a link. Everything else is assumed to be a
     * direct .torrent file link and is downloaded by this device first, then uploaded to
     * qBittorrent as file bytes - see [ProwlarrSearchRepository.downloadTorrentFile].
     */
    fun addTorrent(serverId: Int, searchResult: Search.Result) {
        if (_isAdding.value) {
            return
        }

        _isAdding.value = true
        val job = viewModelScope.launch {
            val result = if (searchResult.fileUrl.startsWith("magnet:", ignoreCase = true)) {
                addTorrent(serverId, links = listOf(searchResult.fileUrl), files = null)
            } else {
                when (val fileResult = prowlarrSearchRepository.downloadTorrentFile(searchResult.fileUrl)) {
                    is RequestResult.Success -> {
                        val fileName = searchResult.fileName.let {
                            if (it.endsWith(".torrent", ignoreCase = true)) it else "$it.torrent"
                        }
                        addTorrent(serverId, links = null, files = listOf(fileName to fileResult.data))
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

    private suspend fun addTorrent(serverId: Int, links: List<String>?, files: List<Pair<String, ByteArray>>?) =
        addTorrentRepository.addTorrent(
            serverId = serverId,
            links = links,
            files = files,
            savePath = null,
            category = null,
            tags = emptyList(),
            stopCondition = null,
            contentLayout = null,
            torrentName = null,
            downloadSpeedLimit = null,
            uploadSpeedLimit = null,
            ratioLimit = null,
            seedingTimeLimit = null,
            isPaused = false,
            skipHashChecking = false,
            isAutoTorrentManagementEnabled = null,
            isSequentialDownloadEnabled = false,
            isFirstLastPiecePrioritized = false,
        )

    sealed class Event {
        data class SearchError(val error: RequestResult.Error) : Event()
        data class Error(val error: RequestResult.Error) : Event()
        data object InvalidTorrentFile : Event()
        data object AddTorrentError : Event()
        data object AddTorrentSuccess : Event()
    }
}
