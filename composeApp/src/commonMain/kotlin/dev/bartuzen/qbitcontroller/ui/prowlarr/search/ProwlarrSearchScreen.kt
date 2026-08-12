package dev.bartuzen.qbitcontroller.ui.prowlarr.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bartuzen.qbitcontroller.data.SearchSort
import dev.bartuzen.qbitcontroller.model.ProwlarrIndexer
import dev.bartuzen.qbitcontroller.model.Search
import dev.bartuzen.qbitcontroller.ui.components.ActionMenuItem
import dev.bartuzen.qbitcontroller.ui.components.AppBarActions
import dev.bartuzen.qbitcontroller.ui.components.Dialog
import dev.bartuzen.qbitcontroller.ui.components.DropdownMenuItem
import dev.bartuzen.qbitcontroller.ui.components.EmptyListMessage
import dev.bartuzen.qbitcontroller.ui.components.RadioButtonWithLabel
import dev.bartuzen.qbitcontroller.ui.components.SwipeableSnackbarHost
import dev.bartuzen.qbitcontroller.ui.components.TagChip
import dev.bartuzen.qbitcontroller.ui.prowlarr.CategorySelectionSection
import dev.bartuzen.qbitcontroller.ui.prowlarr.buildCategoryGroups
import dev.bartuzen.qbitcontroller.ui.theme.LocalCustomColors
import dev.bartuzen.qbitcontroller.utils.EventEffect
import dev.bartuzen.qbitcontroller.utils.formatBytes
import dev.bartuzen.qbitcontroller.utils.formatUri
import dev.bartuzen.qbitcontroller.utils.getErrorMessage
import dev.bartuzen.qbitcontroller.utils.getString
import dev.bartuzen.qbitcontroller.utils.jsonSaver
import dev.bartuzen.qbitcontroller.utils.measureTextWidth
import dev.bartuzen.qbitcontroller.utils.stateListSaver
import dev.bartuzen.qbitcontroller.utils.stringResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import qbitcontroller.composeapp.generated.resources.Res
import qbitcontroller.composeapp.generated.resources.destination_prowlarr
import qbitcontroller.composeapp.generated.resources.dialog_cancel
import qbitcontroller.composeapp.generated.resources.dialog_ok
import qbitcontroller.composeapp.generated.resources.prowlarr_search_filter_flags
import qbitcontroller.composeapp.generated.resources.prowlarr_search_filter_flags_none
import qbitcontroller.composeapp.generated.resources.prowlarr_search_filter_keyword
import qbitcontroller.composeapp.generated.resources.prowlarr_search_filter_keyword_hint
import qbitcontroller.composeapp.generated.resources.prowlarr_search_go_to_settings
import qbitcontroller.composeapp.generated.resources.prowlarr_search_indexer
import qbitcontroller.composeapp.generated.resources.prowlarr_search_indexers
import qbitcontroller.composeapp.generated.resources.prowlarr_search_indexers_all
import qbitcontroller.composeapp.generated.resources.prowlarr_search_indexers_enabled
import qbitcontroller.composeapp.generated.resources.prowlarr_search_indexers_select
import qbitcontroller.composeapp.generated.resources.prowlarr_search_no_detail_link
import qbitcontroller.composeapp.generated.resources.prowlarr_search_no_results
import qbitcontroller.composeapp.generated.resources.prowlarr_search_no_server_selected
import qbitcontroller.composeapp.generated.resources.prowlarr_search_not_configured
import qbitcontroller.composeapp.generated.resources.prowlarr_search_query_hint
import qbitcontroller.composeapp.generated.resources.prowlarr_search_torrent_added
import qbitcontroller.composeapp.generated.resources.search_result_action_filter
import qbitcontroller.composeapp.generated.resources.search_result_action_sort
import qbitcontroller.composeapp.generated.resources.search_result_action_sort_leechers
import qbitcontroller.composeapp.generated.resources.search_result_action_sort_name
import qbitcontroller.composeapp.generated.resources.search_result_action_sort_reverse
import qbitcontroller.composeapp.generated.resources.search_result_action_sort_seeders
import qbitcontroller.composeapp.generated.resources.search_result_action_sort_size
import qbitcontroller.composeapp.generated.resources.search_result_filter_max
import qbitcontroller.composeapp.generated.resources.search_result_filter_min
import qbitcontroller.composeapp.generated.resources.search_result_filter_reset
import qbitcontroller.composeapp.generated.resources.search_result_filter_seeds
import qbitcontroller.composeapp.generated.resources.search_result_filter_size
import qbitcontroller.composeapp.generated.resources.search_result_no_browser
import qbitcontroller.composeapp.generated.resources.size_bytes
import qbitcontroller.composeapp.generated.resources.size_exbibytes
import qbitcontroller.composeapp.generated.resources.size_gibibytes
import qbitcontroller.composeapp.generated.resources.size_kibibytes
import qbitcontroller.composeapp.generated.resources.size_mebibytes
import qbitcontroller.composeapp.generated.resources.size_pebibytes
import qbitcontroller.composeapp.generated.resources.size_tebibytes
import qbitcontroller.composeapp.generated.resources.torrent_add_error
import qbitcontroller.composeapp.generated.resources.torrent_add_invalid_file

/**
 * A standalone search screen for Prowlarr, deliberately kept separate from the ui.search package
 * (the qBittorrent-search-plugin feature) so this can be built/iterated on without touching that
 * existing code at all - see docs/prowlarr-integration-plan.md, rounds 3-4.
 *
 * [serverId] is the currently selected qBittorrent server (if any), used as a fallback when
 * downloading a result: Prowlarr download defaults/category routes (see
 * [dev.bartuzen.qbitcontroller.ui.prowlarr.search.resolveProwlarrDownloadRouting]) can each pin
 * their own server, and only fall back to this one when neither configures one - see P2 feedback
 * round 1, docs/prowlarr-p2-feedback-round1-plan.md section 3. Search itself doesn't need one,
 * since it only talks to Prowlarr.
 */
@Composable
fun ProwlarrSearchScreen(
    serverId: Int?,
    onNavigateToSettings: () -> Unit,
    navigateToStartFlow: Flow<Unit> = remember { emptyFlow() },
    modifier: Modifier = Modifier,
    viewModel: ProwlarrSearchViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    val config by viewModel.configFlow.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isAdding by viewModel.isAdding.collectAsStateWithLifecycle()
    val rawResults by viewModel.results.collectAsStateWithLifecycle()
    val indexers by viewModel.indexers.collectAsStateWithLifecycle()
    val isLoadingIndexers by viewModel.isLoadingIndexers.collectAsStateWithLifecycle()
    val currentSorting by viewModel.searchSort.collectAsStateWithLifecycle()
    val isReverseSorting by viewModel.isReverseSearchSort.collectAsStateWithLifecycle()

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var hasSearched by rememberSaveable { mutableStateOf(false) }

    var filter by rememberSaveable(stateSaver = jsonSaver<ProwlarrSearchViewModel.Filter>()) {
        mutableStateOf(ProwlarrSearchViewModel.Filter())
    }
    var showFilterDialog by rememberSaveable { mutableStateOf(false) }

    val results = remember(rawResults, currentSorting, isReverseSorting, filter) {
        sortAndFilterProwlarrResults(rawResults, currentSorting, isReverseSorting, filter)
    }

    var indexerSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedIndexerOption by rememberSaveable { mutableStateOf(IndexerSelection.Enabled) }
    val selectedIndexerIds = rememberSaveable(saver = stateListSaver()) { mutableStateListOf<Int>() }

    var categorySectionExpanded by rememberSaveable { mutableStateOf(false) }
    val expandedCategoryGroupIds = rememberSaveable(saver = stateListSaver()) { mutableStateListOf<Int>() }
    val selectedTopCategoryIds = rememberSaveable(saver = stateListSaver()) { mutableStateListOf<Int>() }
    val selectedSubCategoryIds = rememberSaveable(saver = stateListSaver()) { mutableStateListOf<Int>() }

    // Populate the indexer list once Prowlarr is configured (it may not be yet on first render -
    // e.g. a fresh install). loadIndexers() is a no-op if a fetch is already in flight, so this is
    // safe to re-trigger if isConfigured flips true -> false -> true again.
    LaunchedEffect(config.isConfigured) {
        if (config.isConfigured) {
            viewModel.loadIndexers()
        }
    }

    // If a previously-selected indexer disappears from Prowlarr's own config (or the list simply
    // hasn't loaded yet), drop it from the selection rather than silently keeping a stale id around
    // - mirrors SearchStartScreen's equivalent cleanup for its plugin selection.
    LaunchedEffect(indexers) {
        indexers?.let { list ->
            selectedIndexerIds.removeAll { id -> list.none { it.id == id } }
        }
    }

    // The indexers whose categories currently feed the category picker below - not the same thing
    // as the indexerIds actually sent to search() (see runSearch): "Enabled"/"Selected" fall back to
    // an empty list here while the indexer list is still loading, so the category picker just shows
    // nothing yet rather than null-checking everywhere below.
    val effectiveIndexers = when (selectedIndexerOption) {
        IndexerSelection.Enabled -> indexers?.filter { it.enable }
        IndexerSelection.All -> indexers
        IndexerSelection.Selected -> indexers?.filter { it.id in selectedIndexerIds }
    } ?: emptyList()

    // See buildCategoryGroups KDoc for why this is a union across effectiveIndexers rather than a
    // fixed 8-item Torznab top-level list, which is what the plan doc originally called for.
    val categoryGroups = remember(effectiveIndexers) { buildCategoryGroups(effectiveIndexers) }

    // Same cleanup idea as the indexer LaunchedEffect above, but for categories: dropping an
    // indexer from the selection can shrink categoryGroups (a category only offered by that
    // indexer disappears), so any previously-checked top/sub category id no longer present has to
    // be un-checked too, or the search would silently keep applying a filter the UI no longer shows
    // as selected.
    LaunchedEffect(categoryGroups) {
        val validTopIds = categoryGroups.mapTo(mutableSetOf()) { it.id }
        val validSubIds = categoryGroups.flatMapTo(mutableSetOf()) { group -> group.subCategories.map { it.id } }
        selectedTopCategoryIds.removeAll { it !in validTopIds }
        selectedSubCategoryIds.removeAll { it !in validSubIds }
    }

    fun runSearch() {
        if (!config.isConfigured) {
            return
        }
        hasSearched = true
        val indexerIds = when (selectedIndexerOption) {
            IndexerSelection.Enabled -> indexers?.filter { it.enable }?.map { it.id }
            IndexerSelection.All -> null
            IndexerSelection.Selected -> selectedIndexerIds.toList()
        }
        val categories = (selectedTopCategoryIds + selectedSubCategoryIds).takeIf { it.isNotEmpty() }
        viewModel.search(query.text, indexerIds, categories)
    }

    // Tapping the bottom-nav tab again while already on this screen resets to a blank search,
    // matching the "tap current tab to go back to its root" convention used by the other tabs
    // (which pop their own nav stack back to the start screen instead - this screen has none, so
    // clearing the query/results is the closest equivalent).
    LaunchedEffect(navigateToStartFlow) {
        navigateToStartFlow.collectLatest {
            query = TextFieldValue("")
            hasSearched = false
            viewModel.search("")
        }
    }

    EventEffect(viewModel.eventFlow) { event ->
        val message = when (event) {
            is ProwlarrSearchViewModel.Event.SearchError -> getErrorMessage(event.error)
            is ProwlarrSearchViewModel.Event.IndexersError -> getErrorMessage(event.error)
            is ProwlarrSearchViewModel.Event.Error -> getErrorMessage(event.error)
            ProwlarrSearchViewModel.Event.InvalidTorrentFile -> getString(Res.string.torrent_add_invalid_file)
            ProwlarrSearchViewModel.Event.AddTorrentError -> getString(Res.string.torrent_add_error)
            ProwlarrSearchViewModel.Event.AddTorrentSuccess -> getString(Res.string.prowlarr_search_torrent_added)
            ProwlarrSearchViewModel.Event.NoServerAvailable -> getString(Res.string.prowlarr_search_no_server_selected)
        }

        snackbarHostState.currentSnackbarData?.dismiss()
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    if (showFilterDialog) {
        val availableFlags = remember(rawResults) {
            rawResults.flatMap { it.indexerFlags }.distinct().sorted()
        }
        ProwlarrFilterDialog(
            filter = filter,
            availableFlags = availableFlags,
            onDismiss = { showFilterDialog = false },
            onConfirm = { newFilter ->
                filter = newFilter
                showFilterDialog = false
            },
            onReset = {
                filter = ProwlarrSearchViewModel.Filter()
                showFilterDialog = false
            },
        )
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.destination_prowlarr),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    var showSortMenu by rememberSaveable { mutableStateOf(false) }
                    val actionMenuItems = listOf(
                        ActionMenuItem(
                            title = stringResource(Res.string.search_result_action_filter),
                            icon = Icons.Filled.FilterList,
                            onClick = { showFilterDialog = true },
                            showAsAction = true,
                        ),
                        ActionMenuItem(
                            title = stringResource(Res.string.search_result_action_sort),
                            icon = Icons.AutoMirrored.Filled.Sort,
                            onClick = { showSortMenu = true },
                            showAsAction = true,
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowRight,
                                    contentDescription = null,
                                )
                            },
                            dropdownMenu = {
                                val scrollState = rememberScrollState()
                                LaunchedEffect(showSortMenu) {
                                    if (showSortMenu) {
                                        scrollState.scrollTo(0)
                                    }
                                }

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    scrollState = scrollState,
                                ) {
                                    Text(
                                        text = stringResource(Res.string.search_result_action_sort),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )

                                    val sortOptions = remember {
                                        listOf(
                                            Res.string.search_result_action_sort_name to SearchSort.NAME,
                                            Res.string.search_result_action_sort_size to SearchSort.SIZE,
                                            Res.string.search_result_action_sort_seeders to SearchSort.SEEDERS,
                                            Res.string.search_result_action_sort_leechers to SearchSort.LEECHERS,
                                            Res.string.prowlarr_search_indexer to SearchSort.SEARCH_ENGINE,
                                        )
                                    }
                                    sortOptions.forEach { (stringId, searchSort) ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    RadioButton(
                                                        selected = currentSorting == searchSort,
                                                        onClick = null,
                                                    )
                                                    Text(text = stringResource(stringId))
                                                }
                                            },
                                            onClick = {
                                                viewModel.setSearchSort(searchSort)
                                                showSortMenu = false
                                            },
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Checkbox(
                                                    checked = isReverseSorting,
                                                    onCheckedChange = null,
                                                )
                                                Text(text = stringResource(Res.string.search_result_action_sort_reverse))
                                            }
                                        },
                                        onClick = {
                                            viewModel.changeReverseSorting()
                                            showSortMenu = false
                                        },
                                    )
                                }
                            },
                        ),
                    )

                    AppBarActions(items = actionMenuItems)
                },
            )
        },
        snackbarHost = {
            SwipeableSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        text = stringResource(Res.string.prowlarr_search_query_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (config.isConfigured) {
                IndexerSelectionSection(
                    expanded = indexerSectionExpanded,
                    onExpandedChange = { indexerSectionExpanded = it },
                    selectedOption = selectedIndexerOption,
                    onOptionChange = { selectedIndexerOption = it },
                    indexers = indexers,
                    isLoadingIndexers = isLoadingIndexers,
                    selectedIndexerIds = selectedIndexerIds,
                    onToggleIndexer = { id ->
                        if (id in selectedIndexerIds) {
                            selectedIndexerIds.remove(id)
                        } else {
                            selectedIndexerIds.add(id)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )

                CategorySelectionSection(
                    expanded = categorySectionExpanded,
                    onExpandedChange = { categorySectionExpanded = it },
                    categoryGroups = categoryGroups,
                    expandedGroupIds = expandedCategoryGroupIds,
                    onToggleGroupExpanded = { id ->
                        if (id in expandedCategoryGroupIds) {
                            expandedCategoryGroupIds.remove(id)
                        } else {
                            expandedCategoryGroupIds.add(id)
                        }
                    },
                    selectedTopCategoryIds = selectedTopCategoryIds,
                    onToggleTopCategory = { id ->
                        if (id in selectedTopCategoryIds) {
                            selectedTopCategoryIds.remove(id)
                        } else {
                            selectedTopCategoryIds.add(id)
                        }
                    },
                    selectedSubCategoryIds = selectedSubCategoryIds,
                    onToggleSubCategory = { id ->
                        if (id in selectedSubCategoryIds) {
                            selectedSubCategoryIds.remove(id)
                        } else {
                            selectedSubCategoryIds.add(id)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    !config.isConfigured -> {
                        EmptyListMessage(
                            icon = Icons.Filled.TravelExplore,
                            title = stringResource(Res.string.prowlarr_search_not_configured),
                            actionButton = {
                                TextButton(onClick = onNavigateToSettings) {
                                    Text(text = stringResource(Res.string.prowlarr_search_go_to_settings))
                                }
                            },
                        )
                    }

                    isLoading && results.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    hasSearched && results.isEmpty() -> {
                        EmptyListMessage(
                            icon = Icons.Filled.Search,
                            title = stringResource(Res.string.prowlarr_search_no_results),
                        )
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(results, key = { it.fileUrl }) { result ->
                                ProwlarrSearchResultItem(
                                    searchResult = result,
                                    isAddEnabled = !isAdding,
                                    onDownloadClick = {
                                        viewModel.addTorrent(serverId, result)
                                    },
                                    onOpenDescription = {
                                        if (result.descriptionLink.isBlank()) {
                                            scope.launch {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                snackbarHostState.showSnackbar(
                                                    getString(Res.string.prowlarr_search_no_detail_link),
                                                )
                                            }
                                        } else {
                                            try {
                                                uriHandler.openUri(result.descriptionLink)
                                            } catch (_: IllegalArgumentException) {
                                                scope.launch {
                                                    snackbarHostState.currentSnackbarData?.dismiss()
                                                    snackbarHostState.showSnackbar(
                                                        getString(Res.string.search_result_no_browser),
                                                    )
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * [onOpenDescription] opens [searchResult]'s originating tracker page (`infoUrl`, mapped onto
 * [Search.Result.descriptionLink]) in the system browser - mirrors
 * [dev.bartuzen.qbitcontroller.ui.search.result.SearchResultScreen]'s existing "Details" dialog
 * action, just triggered directly by tapping the card instead of going through an intermediate
 * dialog - this screen doesn't have one, and the download button is the only other action a card
 * needs. Uses Material3's clickable-card overload rather than a bare `Modifier.clickable` so it
 * gets the standard ripple/touch-target semantics for free; the trailing download [IconButton]
 * still handles its own tap independently - a single tap on it only triggers [onDownloadClick],
 * not both.
 */
@Composable
private fun ProwlarrSearchResultItem(
    searchResult: Search.Result,
    isAddEnabled: Boolean,
    onDownloadClick: () -> Unit,
    onOpenDescription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onOpenDescription,
        colors = CardDefaults.elevatedCardColors(),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = searchResult.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Storage,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = if (searchResult.fileSize != null) formatBytes(searchResult.fileSize) else "-",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Language,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = formatUri(searchResult.siteUrl),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = LocalCustomColors.current.seederColor,
                        )
                        Text(
                            text = searchResult.seeders?.toString() ?: "-",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = LocalCustomColors.current.seederColor,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = LocalCustomColors.current.leecherColor,
                        )
                        Text(
                            text = searchResult.leechers?.toString() ?: "-",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = LocalCustomColors.current.leecherColor,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }

                if (searchResult.indexerFlags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        searchResult.indexerFlags.forEach { flag ->
                            TagChip(tag = flag)
                        }
                    }
                }
            }

            IconButton(onClick = onDownloadClick, enabled = isAddEnabled) {
                Icon(imageVector = Icons.Filled.Download, contentDescription = null)
            }
        }
    }
}

/**
 * Collapsible indexer multi-select, mirroring the three-state pattern (Enabled/All/Selected) from
 * SearchStartScreen.kt's plugin picker (see docs/prowlarr-p1-search-ui-and-tabs-plan.md, section
 * 2.1) - same [RadioButtonWithLabel] options in a bordered [Column], collapsed by default since
 * (unlike that screen) this one has to share vertical space with a results list that's visible at
 * the same time.
 *
 * Deliberately uses [TagChip] rather than Material3's `FilterChip` for the "Selected" list, even
 * though the plan doc mentions the latter - `FilterChip` isn't used anywhere else in this codebase,
 * while [TagChip]/[dev.bartuzen.qbitcontroller.ui.components.CategoryChip] is the app's actual
 * established chip component (see the tag picker in TorrentOverviewTab.kt). Round 7's plan wrote
 * "FilterChip" as a guess without checking, the same way the API schema was a guess before it got
 * confirmed against a real response - following the real convention here for the same reason.
 */
@Composable
private fun IndexerSelectionSection(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    selectedOption: IndexerSelection,
    onOptionChange: (IndexerSelection) -> Unit,
    indexers: List<ProwlarrIndexer>?,
    isLoadingIndexers: Boolean,
    selectedIndexerIds: List<Int>,
    onToggleIndexer: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

    Column(modifier = modifier) {
        val summary = when (selectedOption) {
            IndexerSelection.Enabled -> stringResource(Res.string.prowlarr_search_indexers_enabled)
            IndexerSelection.All -> stringResource(Res.string.prowlarr_search_indexers_all)
            IndexerSelection.Selected -> stringResource(Res.string.prowlarr_search_indexers_select)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "${stringResource(Res.string.prowlarr_search_indexers)}: $summary",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            if (isLoadingIndexers) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .rotate(rotation),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .border(
                            width = OutlinedTextFieldDefaults.UnfocusedBorderThickness,
                            color = OutlinedTextFieldDefaults.colors().unfocusedIndicatorColor,
                            shape = OutlinedTextFieldDefaults.shape,
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RadioButtonWithLabel(
                        selected = selectedOption == IndexerSelection.Enabled,
                        onClick = { onOptionChange(IndexerSelection.Enabled) },
                        label = stringResource(Res.string.prowlarr_search_indexers_enabled),
                    )
                    RadioButtonWithLabel(
                        selected = selectedOption == IndexerSelection.All,
                        onClick = { onOptionChange(IndexerSelection.All) },
                        label = stringResource(Res.string.prowlarr_search_indexers_all),
                    )
                    RadioButtonWithLabel(
                        selected = selectedOption == IndexerSelection.Selected,
                        onClick = { onOptionChange(IndexerSelection.Selected) },
                        label = stringResource(Res.string.prowlarr_search_indexers_select),
                    )
                }

                if (selectedOption == IndexerSelection.Selected && !indexers.isNullOrEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        indexers.forEach { indexer ->
                            TagChip(
                                tag = indexer.name,
                                isSelected = indexer.id in selectedIndexerIds,
                                onClick = { onToggleIndexer(indexer.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class IndexerSelection {
    Enabled,
    All,
    Selected,
}

/**
 * Pure sort + filter step for the Prowlarr results list (see docs/prowlarr-p1-search-ui-and-tabs-plan.md,
 * section 2.3). Deliberately a plain function rather than ViewModel/StateFlow machinery: [sort] and
 * [isReverse] are the only pieces of state here that come from the ViewModel (as persisted settings),
 * while [filter] is Composable-owned `rememberSaveable` state - a top-level function taking all of it
 * as parameters is the simplest way to combine the two without forcing either one to own the other.
 *
 * Comparator logic mirrors [dev.bartuzen.qbitcontroller.ui.search.result.SearchResultViewModel]'s
 * `sortedResults`; the [SearchSort.SEARCH_ENGINE] case is reused as-is to mean "indexer" here since
 * [Search.Result.siteUrl] holds the indexer name for Prowlarr-sourced results - see the "Indexer" sort
 * label added alongside it in the screen's sort menu.
 */
private fun sortAndFilterProwlarrResults(
    results: List<Search.Result>,
    sort: SearchSort,
    isReverse: Boolean,
    filter: ProwlarrSearchViewModel.Filter,
): List<Search.Result> {
    val comparator = when (sort) {
        SearchSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER, Search.Result::fileName)
        SearchSort.SIZE -> compareBy(Search.Result::fileSize).thenBy(String.CASE_INSENSITIVE_ORDER, Search.Result::fileName)
        SearchSort.SEEDERS -> compareBy(Search.Result::seeders).thenBy(String.CASE_INSENSITIVE_ORDER, Search.Result::fileName)
        SearchSort.LEECHERS -> compareBy(Search.Result::leechers).thenBy(String.CASE_INSENSITIVE_ORDER, Search.Result::fileName)
        SearchSort.SEARCH_ENGINE ->
            compareBy(String.CASE_INSENSITIVE_ORDER, Search.Result::siteUrl)
                .thenBy(String.CASE_INSENSITIVE_ORDER, Search.Result::fileName)
    }

    val sorted = results.sortedWith(comparator).let { if (isReverse) it.reversed() else it }

    return sorted.filter { result ->
        if (filter.keyword.isNotEmpty()) {
            val matchesKeyword = filter.keyword
                .split(" ")
                .filter { it.isNotEmpty() && it != "-" }
                .all { term ->
                    val isExclusion = term.startsWith("-")
                    val cleanTerm = term.removePrefix("-")
                    val containsTerm = result.fileName.contains(cleanTerm, ignoreCase = true)

                    if (isExclusion) !containsTerm else containsTerm
                }
            if (!matchesKeyword) {
                return@filter false
            }
        }

        if (filter.indexerQuery.isNotEmpty()) {
            val matchesIndexerQuery = filter.indexerQuery
                .split(" ")
                .filter { it.isNotEmpty() && it != "-" }
                .all { term ->
                    val isExclusion = term.startsWith("-")
                    val cleanTerm = term.removePrefix("-")
                    val containsTerm = result.siteUrl.contains(cleanTerm, ignoreCase = true)

                    if (isExclusion) !containsTerm else containsTerm
                }
            if (!matchesIndexerQuery) {
                return@filter false
            }
        }

        if (filter.seedsMin != null && (result.seeders ?: -1) < filter.seedsMin) {
            return@filter false
        }
        if (filter.seedsMax != null && (result.seeders ?: Int.MAX_VALUE) > filter.seedsMax) {
            return@filter false
        }

        if (filter.sizeMinBytes != null && (result.fileSize ?: -1) < filter.sizeMinBytes) {
            return@filter false
        }
        if (filter.sizeMaxBytes != null && (result.fileSize ?: Long.MAX_VALUE) > filter.sizeMaxBytes) {
            return@filter false
        }

        if (filter.flags.isNotEmpty() && result.indexerFlags.none { it in filter.flags }) {
            return@filter false
        }

        true
    }
}

/**
 * Simplified copy of [dev.bartuzen.qbitcontroller.ui.search.result.SearchResultScreen]'s private
 * `FilterDialog`, kept independent per docs/prowlarr-p1-search-ui-and-tabs-plan.md section 2.3 -
 * this screen must stay free-standing so it can be reverted without touching any file under
 * `ui/search`. Two sections the original doesn't have: a title keyword filter (top of the dialog,
 * mirroring the qBit result screen's separate search-mode free-text filter, which lives in that
 * screen's top bar instead of its filter dialog - there's no equivalent search-mode toggle here,
 * so it's a dialog field instead) and a keyword filter against the originating indexer (see
 * [ProwlarrSearchViewModel.Filter] KDoc for why that second dimension matters more here than on
 * the qBit plugin result screen).
 */
@Composable
private fun ProwlarrFilterDialog(
    filter: ProwlarrSearchViewModel.Filter,
    availableFlags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (filter: ProwlarrSearchViewModel.Filter) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var keyword by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(filter.keyword))
    }
    var indexerQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(filter.indexerQuery))
    }
    var seedsMin by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(filter.seedsMin?.toString() ?: ""))
    }
    var seedsMax by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(filter.seedsMax?.toString() ?: ""))
    }
    var sizeMin by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(filter.sizeMin?.toString() ?: ""))
    }
    var sizeMax by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(filter.sizeMax?.toString() ?: ""))
    }
    var sizeMinUnit by rememberSaveable { mutableIntStateOf(filter.sizeMinUnit) }
    var sizeMaxUnit by rememberSaveable { mutableIntStateOf(filter.sizeMaxUnit) }
    val selectedFlags = rememberSaveable(saver = stateListSaver()) {
        mutableStateListOf<String>().apply { addAll(filter.flags) }
    }

    val sizeUnits = remember {
        listOf(
            Res.string.size_bytes,
            Res.string.size_kibibytes,
            Res.string.size_mebibytes,
            Res.string.size_gibibytes,
            Res.string.size_tebibytes,
            Res.string.size_pebibytes,
            Res.string.size_exbibytes,
        )
    }

    Dialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.search_result_action_filter)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(Res.string.prowlarr_search_filter_keyword),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = {
                        Text(
                            text = stringResource(Res.string.prowlarr_search_filter_keyword),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.prowlarr_search_filter_keyword_hint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(Res.string.prowlarr_search_indexer),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                OutlinedTextField(
                    value = indexerQuery,
                    onValueChange = { indexerQuery = it },
                    label = {
                        Text(
                            text = stringResource(Res.string.prowlarr_search_indexer),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalOffer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(Res.string.prowlarr_search_filter_flags),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (availableFlags.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.prowlarr_search_filter_flags_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        availableFlags.forEach { flag ->
                            TagChip(
                                tag = flag,
                                isSelected = flag in selectedFlags,
                                onClick = {
                                    if (flag in selectedFlags) selectedFlags.remove(flag) else selectedFlags.add(flag)
                                },
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowUpward,
                        contentDescription = null,
                        tint = LocalCustomColors.current.seederColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(Res.string.search_result_filter_seeds),
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalCustomColors.current.seederColor,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = seedsMin,
                        onValueChange = {
                            if (it.text.all { it.isDigit() }) {
                                seedsMin = it
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(Res.string.search_result_filter_min),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.weight(1f),
                    )

                    OutlinedTextField(
                        value = seedsMax,
                        onValueChange = {
                            if (it.text.all { it.isDigit() }) {
                                seedsMax = it
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(Res.string.search_result_filter_max),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(Res.string.search_result_filter_size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                val speedUnitDropdownWidth = sizeUnits.maxOf { measureTextWidth(stringResource(it)) } + 72.dp

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sizeMin,
                        onValueChange = {
                            if (it.text.all { it.isDigit() }) {
                                sizeMin = it
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(Res.string.search_result_filter_min),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.weight(1f),
                    )

                    var expanded by rememberSaveable { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier
                            .width(speedUnitDropdownWidth)
                            .padding(top = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = stringResource(sizeUnits[sizeMinUnit]),
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            sizeUnits.forEachIndexed { sizeUnit, stringId ->
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(stringId)) },
                                    onClick = {
                                        sizeMinUnit = sizeUnit
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sizeMax,
                        onValueChange = {
                            if (it.text.all { it.isDigit() }) {
                                sizeMax = it
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(Res.string.search_result_filter_max),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.weight(1f),
                    )

                    var expanded by rememberSaveable { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier
                            .width(speedUnitDropdownWidth)
                            .padding(top = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = stringResource(sizeUnits[sizeMaxUnit]),
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            sizeUnits.forEachIndexed { sizeUnit, stringId ->
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(stringId)) },
                                    onClick = {
                                        sizeMaxUnit = sizeUnit
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onReset) {
                    Text(text = stringResource(Res.string.search_result_filter_reset))
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(Res.string.dialog_cancel))
                }

                Button(
                    onClick = {
                        val newFilter = ProwlarrSearchViewModel.Filter(
                            seedsMin = seedsMin.text.toIntOrNull(),
                            seedsMax = seedsMax.text.toIntOrNull(),
                            sizeMin = sizeMin.text.toLongOrNull(),
                            sizeMax = sizeMax.text.toLongOrNull(),
                            sizeMinUnit = sizeMinUnit,
                            sizeMaxUnit = sizeMaxUnit,
                            indexerQuery = indexerQuery.text,
                            keyword = keyword.text,
                            flags = selectedFlags.toList(),
                        )
                        onConfirm(newFilter)
                    },
                ) {
                    Text(text = stringResource(Res.string.dialog_ok))
                }
            }
        },
    )
}
