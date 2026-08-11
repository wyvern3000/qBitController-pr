package dev.bartuzen.qbitcontroller.ui.settings.prowlarr.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bartuzen.qbitcontroller.data.SettingsManager
import dev.bartuzen.qbitcontroller.data.repositories.ProwlarrRepository
import dev.bartuzen.qbitcontroller.model.ProwlarrCategoryRoute
import dev.bartuzen.qbitcontroller.model.ProwlarrDownloadDefaults
import dev.bartuzen.qbitcontroller.model.ProwlarrIndexer
import dev.bartuzen.qbitcontroller.network.RequestResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Backs [ProwlarrDownloadDefaultsScreen] - see docs/prowlarr-download-defaults-plan.md.
 *
 * [downloadDefaults] is a one-shot snapshot, not a reactive [kotlinx.coroutines.flow.StateFlow] -
 * same pattern as [dev.bartuzen.qbitcontroller.ui.settings.prowlarr.ProwlarrSettingsViewModel.config]:
 * this section of the screen is a single edit-then-tap-Save form, not something that needs to react
 * to the underlying preference changing out from under it while open. [categoryRoutes] is different
 * - it's a live list the screen adds/edits/deletes entries from in place (see [saveCategoryRoute]/
 * [deleteCategoryRoute]/[moveCategoryRoute]), each change persisted immediately rather than batched
 * behind a single Save action, so it needs to actually be observable.
 */
class ProwlarrDownloadDefaultsViewModel(
    private val prowlarrRepository: ProwlarrRepository,
    private val settingsManager: SettingsManager,
) : ViewModel() {
    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    val downloadDefaults = settingsManager.prowlarrDownloadDefaults.value

    private val _categoryRoutes = MutableStateFlow(settingsManager.prowlarrCategoryRoutes.value)
    val categoryRoutes = _categoryRoutes.asStateFlow()

    // Same null-means-not-loaded-yet convention as ProwlarrSearchViewModel.indexers - a failure here
    // just means the category picker in the add/edit route dialog has nothing to offer yet, it
    // doesn't block the rest of the screen.
    private val _indexers = MutableStateFlow<List<ProwlarrIndexer>?>(null)
    val indexers = _indexers.asStateFlow()

    private val _isLoadingIndexers = MutableStateFlow(false)
    val isLoadingIndexers = _isLoadingIndexers.asStateFlow()

    private var loadIndexersJob: Job? = null

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

    fun saveDownloadDefaults(defaults: ProwlarrDownloadDefaults) {
        settingsManager.prowlarrDownloadDefaults.value = defaults
    }

    /**
     * Adds a new route ([existingId] `null`) or updates the one whose id equals [existingId] in
     * place (preserving its position in the list - editing a route doesn't change its match
     * priority).
     */
    fun saveCategoryRoute(
        existingId: String?,
        name: String,
        categoryIds: List<Int>,
        savePath: String?,
        category: String?,
        tags: List<String>,
    ) {
        val route = ProwlarrCategoryRoute(
            id = existingId ?: randomRouteId(),
            name = name,
            categoryIds = categoryIds,
            savePath = savePath,
            category = category,
            tags = tags,
        )

        val current = _categoryRoutes.value
        val index = current.indexOfFirst { it.id == route.id }
        val updated = if (index >= 0) {
            current.toMutableList().apply { this[index] = route }
        } else {
            current + route
        }
        persistCategoryRoutes(updated)
    }

    fun deleteCategoryRoute(id: String) {
        persistCategoryRoutes(_categoryRoutes.value.filterNot { it.id == id })
    }

    /** Moves the route at [fromIndex] to [toIndex] - list order is match priority, see [resolveProwlarrDownloadRouting]. */
    fun moveCategoryRoute(fromIndex: Int, toIndex: Int) {
        val current = _categoryRoutes.value
        if (fromIndex == toIndex || fromIndex !in current.indices || toIndex !in current.indices) {
            return
        }

        persistCategoryRoutes(current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) })
    }

    private fun persistCategoryRoutes(routes: List<ProwlarrCategoryRoute>) {
        _categoryRoutes.value = routes
        settingsManager.prowlarrCategoryRoutes.value = routes
    }

    private fun randomRouteId(): String =
        Random.nextLong().toULong().toString(16) + Random.nextLong().toULong().toString(16)

    sealed class Event {
        data class IndexersError(val error: RequestResult.Error) : Event()
    }
}
