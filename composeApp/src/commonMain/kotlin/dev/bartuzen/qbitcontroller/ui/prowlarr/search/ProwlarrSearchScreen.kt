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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bartuzen.qbitcontroller.model.ProwlarrIndexer
import dev.bartuzen.qbitcontroller.model.Search
import dev.bartuzen.qbitcontroller.ui.components.EmptyListMessage
import dev.bartuzen.qbitcontroller.ui.components.RadioButtonWithLabel
import dev.bartuzen.qbitcontroller.ui.components.SwipeableSnackbarHost
import dev.bartuzen.qbitcontroller.ui.components.TagChip
import dev.bartuzen.qbitcontroller.ui.theme.LocalCustomColors
import dev.bartuzen.qbitcontroller.utils.EventEffect
import dev.bartuzen.qbitcontroller.utils.formatBytes
import dev.bartuzen.qbitcontroller.utils.formatUri
import dev.bartuzen.qbitcontroller.utils.getErrorMessage
import dev.bartuzen.qbitcontroller.utils.getString
import dev.bartuzen.qbitcontroller.utils.stateListSaver
import dev.bartuzen.qbitcontroller.utils.stringResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import qbitcontroller.composeapp.generated.resources.Res
import qbitcontroller.composeapp.generated.resources.destination_prowlarr
import qbitcontroller.composeapp.generated.resources.prowlarr_search_go_to_settings
import qbitcontroller.composeapp.generated.resources.prowlarr_search_indexers
import qbitcontroller.composeapp.generated.resources.prowlarr_search_indexers_all
import qbitcontroller.composeapp.generated.resources.prowlarr_search_indexers_enabled
import qbitcontroller.composeapp.generated.resources.prowlarr_search_indexers_select
import qbitcontroller.composeapp.generated.resources.prowlarr_search_no_results
import qbitcontroller.composeapp.generated.resources.prowlarr_search_no_server_selected
import qbitcontroller.composeapp.generated.resources.prowlarr_search_not_configured
import qbitcontroller.composeapp.generated.resources.prowlarr_search_query_hint
import qbitcontroller.composeapp.generated.resources.prowlarr_search_torrent_added
import qbitcontroller.composeapp.generated.resources.torrent_add_error
import qbitcontroller.composeapp.generated.resources.torrent_add_invalid_file

/**
 * A standalone search screen for Prowlarr, deliberately kept separate from the ui.search package
 * (the qBittorrent-search-plugin feature) so this can be built/iterated on without touching that
 * existing code at all - see docs/prowlarr-integration-plan.md, rounds 3-4.
 *
 * [serverId] is the currently selected qBittorrent server (if any) that a tapped result gets
 * added to. Search itself doesn't need one, since it only talks to Prowlarr.
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

    val config by viewModel.configFlow.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isAdding by viewModel.isAdding.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val indexers by viewModel.indexers.collectAsStateWithLifecycle()
    val isLoadingIndexers by viewModel.isLoadingIndexers.collectAsStateWithLifecycle()

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var hasSearched by rememberSaveable { mutableStateOf(false) }

    var indexerSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedIndexerOption by rememberSaveable { mutableStateOf(IndexerSelection.Enabled) }
    val selectedIndexerIds = rememberSaveable(saver = stateListSaver()) { mutableStateListOf<Int>() }

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
        viewModel.search(query.text, indexerIds)
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
        }

        snackbarHostState.currentSnackbarData?.dismiss()
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
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
                                    isAddEnabled = serverId != null && !isAdding,
                                    onDownloadClick = {
                                        if (serverId != null) {
                                            viewModel.addTorrent(serverId, result)
                                        } else {
                                            scope.launch {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                snackbarHostState.showSnackbar(
                                                    getString(Res.string.prowlarr_search_no_server_selected),
                                                )
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

@Composable
private fun ProwlarrSearchResultItem(
    searchResult: Search.Result,
    isAddEnabled: Boolean,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
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
