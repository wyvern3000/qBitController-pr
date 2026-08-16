package dev.bartuzen.qbitcontroller.ui.settings.prowlarr.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bartuzen.qbitcontroller.model.ProwlarrDownloadDefaults
import dev.bartuzen.qbitcontroller.model.ProwlarrDownloadRoute
import dev.bartuzen.qbitcontroller.model.ProwlarrIndexer
import dev.bartuzen.qbitcontroller.model.ServerConfig
import dev.bartuzen.qbitcontroller.ui.components.ActionMenuItem
import dev.bartuzen.qbitcontroller.ui.components.AppBarActions
import dev.bartuzen.qbitcontroller.ui.components.CheckboxWithLabel
import dev.bartuzen.qbitcontroller.ui.components.Dialog
import dev.bartuzen.qbitcontroller.ui.components.DropdownMenuItem
import dev.bartuzen.qbitcontroller.ui.components.SwipeableSnackbarHost
import dev.bartuzen.qbitcontroller.ui.prowlarr.CategoryGroup
import dev.bartuzen.qbitcontroller.ui.prowlarr.CategorySelectionSection
import dev.bartuzen.qbitcontroller.ui.prowlarr.IndexerCategoryGroup
import dev.bartuzen.qbitcontroller.ui.prowlarr.IndexerSelectionSection
import dev.bartuzen.qbitcontroller.ui.prowlarr.buildSiteSpecificGroups
import dev.bartuzen.qbitcontroller.ui.prowlarr.buildStandardCategoryGroups
import dev.bartuzen.qbitcontroller.utils.EventEffect
import dev.bartuzen.qbitcontroller.utils.getDecimalSeparator
import dev.bartuzen.qbitcontroller.utils.getErrorMessage
import dev.bartuzen.qbitcontroller.utils.getString
import dev.bartuzen.qbitcontroller.utils.jsonSaver
import dev.bartuzen.qbitcontroller.utils.stateListSaver
import dev.bartuzen.qbitcontroller.utils.stringResource
import dev.bartuzen.qbitcontroller.utils.stringResourceSaver
import dev.bartuzen.qbitcontroller.utils.topAppBarColors
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.viewmodel.koinViewModel
import qbitcontroller.composeapp.generated.resources.Res
import qbitcontroller.composeapp.generated.resources.dialog_cancel
import qbitcontroller.composeapp.generated.resources.dialog_ok
import qbitcontroller.composeapp.generated.resources.error_required_field
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_add_route
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_delete_route
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_move_route_down
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_move_route_up
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_no_routes
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_paused
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_route_dialog_add
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_route_dialog_edit
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_route_field_hint
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_route_indexers_any
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_route_match_required
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_route_name
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_route_summary_both
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_save_success
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_section_default
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_section_routes
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_server
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_server_app_active
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_server_use_default
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_title
import qbitcontroller.composeapp.generated.resources.prowlarr_search_categories_selected
import qbitcontroller.composeapp.generated.resources.settings_prowlarr_action_save
import qbitcontroller.composeapp.generated.resources.speed_kibibytes_per_second
import qbitcontroller.composeapp.generated.resources.torrent_add_category
import qbitcontroller.composeapp.generated.resources.torrent_add_content_layout
import qbitcontroller.composeapp.generated.resources.torrent_add_content_layout_no_subfolder
import qbitcontroller.composeapp.generated.resources.torrent_add_content_layout_original
import qbitcontroller.composeapp.generated.resources.torrent_add_content_layout_subfolder
import qbitcontroller.composeapp.generated.resources.torrent_add_default
import qbitcontroller.composeapp.generated.resources.torrent_add_download_speed_limit
import qbitcontroller.composeapp.generated.resources.torrent_add_prioritize_first_last_piece
import qbitcontroller.composeapp.generated.resources.torrent_add_ratio_limit
import qbitcontroller.composeapp.generated.resources.torrent_add_save_path
import qbitcontroller.composeapp.generated.resources.torrent_add_seeding_time_limit
import qbitcontroller.composeapp.generated.resources.torrent_add_sequential_download
import qbitcontroller.composeapp.generated.resources.torrent_add_skip_hash_checking
import qbitcontroller.composeapp.generated.resources.torrent_add_stop_condition
import qbitcontroller.composeapp.generated.resources.torrent_add_stop_condition_files_checked
import qbitcontroller.composeapp.generated.resources.torrent_add_stop_condition_metadata_received
import qbitcontroller.composeapp.generated.resources.torrent_add_stop_condition_none
import qbitcontroller.composeapp.generated.resources.torrent_add_tags
import qbitcontroller.composeapp.generated.resources.torrent_add_torrent_management_mode
import qbitcontroller.composeapp.generated.resources.torrent_add_torrent_management_mode_auto
import qbitcontroller.composeapp.generated.resources.torrent_add_torrent_management_mode_manual
import qbitcontroller.composeapp.generated.resources.torrent_add_upload_speed_limit

/**
 * Settings screen for docs/prowlarr-download-defaults-plan.md - the always-applied "Default
 * Parameters" form (mirrors [dev.bartuzen.qbitcontroller.ui.addtorrent.AddTorrentScreen]'s field
 * set, minus the file/magnet picker and torrent-name field, which don't make sense for a shared
 * default) plus an editable "Routes" list that overrides just save path/category/tags for results
 * matching a route's category and/or indexer criteria - see [ProwlarrDownloadRoute] KDoc for why
 * the override is scoped to just those three fields, and
 * docs/prowlarr-route-and-category-grouping-plan.md section 3 for the indexer matching dimension
 * that turned this from a category-only list into a "category and/or indexer" one (and prompted
 * the "Category Routes" -> "Routes" label rename, section 3.5).
 *
 * Save path/category fields are still plain text, not a per-server autocomplete/dropdown like
 * AddTorrentScreen's - this was originally because these defaults needed to make sense regardless
 * of which qBittorrent server ends up handling a download (plan doc section 7). P2 feedback round
 * 1 (docs/prowlarr-p2-feedback-round1-plan.md section 3) added an explicit [ProwlarrDownloadDefaults.serverId]/
 * [ProwlarrDownloadRoute.serverId] field, so that original "server-agnostic" reasoning no longer
 * strictly holds - each default/route now does pin one specific server. Turning save
 * path/category into a live dropdown backed by that server's real category list wasn't part of
 * that feedback round though, so it's left as plain text for now; worth revisiting later.
 */
@Composable
fun ProwlarrDownloadDefaultsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProwlarrDownloadDefaultsViewModel = koinViewModel(),
) {
    val defaults = viewModel.downloadDefaults
    val servers by viewModel.servers.collectAsStateWithLifecycle()

    var serverId by rememberSaveable { mutableStateOf(defaults.serverId) }
    var savePath by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(defaults.savePath ?: ""))
    }
    var category by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(defaults.category ?: ""))
    }
    var tags by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(defaults.tags.joinToString(", ")))
    }
    var stopConditionIndex by rememberSaveable {
        mutableIntStateOf(
            when (defaults.stopCondition) {
                "None" -> 1
                "MetadataReceived" -> 2
                "FilesChecked" -> 3
                else -> 0
            },
        )
    }
    var contentLayoutIndex by rememberSaveable {
        mutableIntStateOf(
            when (defaults.contentLayout) {
                "Original" -> 1
                "Subfolder" -> 2
                "NoSubfolder" -> 3
                else -> 0
            },
        )
    }
    var downloadSpeedLimit by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(defaults.downloadSpeedLimit?.toString() ?: ""))
    }
    var uploadSpeedLimit by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(defaults.uploadSpeedLimit?.toString() ?: ""))
    }
    var ratioLimit by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(defaults.ratioLimit?.let { formatRatioLimit(it) } ?: ""))
    }
    var seedingTimeLimit by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(defaults.seedingTimeLimit?.toString() ?: ""))
    }
    var isPaused by rememberSaveable { mutableStateOf(defaults.isPaused) }
    var skipHashChecking by rememberSaveable { mutableStateOf(defaults.skipHashChecking) }
    var autoTmmIndex by rememberSaveable {
        mutableIntStateOf(
            when (defaults.isAutoTorrentManagementEnabled) {
                false -> 1
                true -> 2
                else -> 0
            },
        )
    }
    var isSequentialDownloadEnabled by rememberSaveable { mutableStateOf(defaults.isSequentialDownloadEnabled) }
    var isFirstLastPiecePrioritized by rememberSaveable { mutableStateOf(defaults.isFirstLastPiecePrioritized) }

    fun buildDefaultsToSave() = ProwlarrDownloadDefaults(
        serverId = serverId,
        savePath = savePath.text.ifBlank { null },
        category = category.text.ifBlank { null },
        tags = tags.text.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        stopCondition = when (stopConditionIndex) {
            1 -> "None"
            2 -> "MetadataReceived"
            3 -> "FilesChecked"
            else -> null
        },
        contentLayout = when (contentLayoutIndex) {
            1 -> "Original"
            2 -> "Subfolder"
            3 -> "NoSubfolder"
            else -> null
        },
        downloadSpeedLimit = downloadSpeedLimit.text.toIntOrNull(),
        uploadSpeedLimit = uploadSpeedLimit.text.toIntOrNull(),
        ratioLimit = parseRatioLimit(ratioLimit.text),
        seedingTimeLimit = seedingTimeLimit.text.toIntOrNull(),
        isPaused = isPaused,
        skipHashChecking = skipHashChecking,
        isAutoTorrentManagementEnabled = when (autoTmmIndex) {
            1 -> false
            2 -> true
            else -> null
        },
        isSequentialDownloadEnabled = isSequentialDownloadEnabled,
        isFirstLastPiecePrioritized = isFirstLastPiecePrioritized,
    )

    val routes by viewModel.routes.collectAsStateWithLifecycle()
    val indexers by viewModel.indexers.collectAsStateWithLifecycle()
    val standardCategoryGroups = remember(indexers) { buildStandardCategoryGroups(indexers ?: emptyList()) }
    val siteSpecificCategoryGroups = remember(indexers) { buildSiteSpecificGroups(indexers ?: emptyList()) }

    LaunchedEffect(Unit) {
        viewModel.loadIndexers()
    }

    var currentDialog by rememberSaveable(stateSaver = jsonSaver()) { mutableStateOf<RouteDialog?>(null) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    EventEffect(viewModel.eventFlow) { event ->
        when (event) {
            is ProwlarrDownloadDefaultsViewModel.Event.IndexersError -> {
                snackbarHostState.currentSnackbarData?.dismiss()
                scope.launch {
                    snackbarHostState.showSnackbar(getErrorMessage(event.error))
                }
            }
        }
    }

    val scrollState = rememberScrollState()
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.prowlarr_download_defaults_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    AppBarActions(
                        items = listOf(
                            ActionMenuItem(
                                title = stringResource(Res.string.settings_prowlarr_action_save),
                                icon = Icons.Filled.Save,
                                onClick = {
                                    viewModel.saveDownloadDefaults(buildDefaultsToSave())
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(
                                            getString(Res.string.prowlarr_download_defaults_save_success),
                                        )
                                    }
                                },
                                showAsAction = true,
                            ),
                        ),
                    )
                },
                colors = scrollState.topAppBarColors(),
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
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.prowlarr_download_defaults_section_default),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )

            ServerDropdown(
                servers = servers,
                selectedServerId = serverId,
                onSelect = { serverId = it },
                noneLabel = stringResource(Res.string.prowlarr_download_defaults_server_app_active),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = savePath,
                onValueChange = { savePath = it },
                label = {
                    Text(
                        text = stringResource(Res.string.torrent_add_save_path),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = {
                    Text(
                        text = stringResource(Res.string.torrent_add_category),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = {
                    Text(text = stringResource(Res.string.torrent_add_tags), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            EnumDropdown(
                label = stringResource(Res.string.torrent_add_stop_condition),
                selectedIndex = stopConditionIndex,
                onSelect = { stopConditionIndex = it },
                options = listOf(
                    Res.string.torrent_add_default,
                    Res.string.torrent_add_stop_condition_none,
                    Res.string.torrent_add_stop_condition_metadata_received,
                    Res.string.torrent_add_stop_condition_files_checked,
                ),
            )

            EnumDropdown(
                label = stringResource(Res.string.torrent_add_content_layout),
                selectedIndex = contentLayoutIndex,
                onSelect = { contentLayoutIndex = it },
                options = listOf(
                    Res.string.torrent_add_default,
                    Res.string.torrent_add_content_layout_original,
                    Res.string.torrent_add_content_layout_subfolder,
                    Res.string.torrent_add_content_layout_no_subfolder,
                ),
            )

            EnumDropdown(
                label = stringResource(Res.string.torrent_add_torrent_management_mode),
                selectedIndex = autoTmmIndex,
                onSelect = { autoTmmIndex = it },
                options = listOf(
                    Res.string.torrent_add_default,
                    Res.string.torrent_add_torrent_management_mode_manual,
                    Res.string.torrent_add_torrent_management_mode_auto,
                ),
            )

            OutlinedTextField(
                value = downloadSpeedLimit,
                onValueChange = { if (it.text.all { c -> c.isDigit() }) downloadSpeedLimit = it },
                label = {
                    Text(
                        text = "${stringResource(Res.string.torrent_add_download_speed_limit)} " +
                            "(${stringResource(Res.string.speed_kibibytes_per_second)})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uploadSpeedLimit,
                onValueChange = { if (it.text.all { c -> c.isDigit() }) uploadSpeedLimit = it },
                label = {
                    Text(
                        text = "${stringResource(Res.string.torrent_add_upload_speed_limit)} " +
                            "(${stringResource(Res.string.speed_kibibytes_per_second)})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            val ratioLimitRegex = remember {
                val decimalSeparator = getDecimalSeparator()
                Regex("^\\d*\\$decimalSeparator?\\d*$|^$")
            }
            OutlinedTextField(
                value = ratioLimit,
                onValueChange = { newValue -> if (ratioLimitRegex.matches(newValue.text)) ratioLimit = newValue },
                label = {
                    Text(
                        text = stringResource(Res.string.torrent_add_ratio_limit),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = seedingTimeLimit,
                onValueChange = { if (it.text.all { c -> c.isDigit() }) seedingTimeLimit = it },
                label = {
                    Text(
                        text = stringResource(Res.string.torrent_add_seeding_time_limit),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            CheckboxWithLabel(
                checked = isPaused,
                onCheckedChange = { isPaused = it },
                label = stringResource(Res.string.prowlarr_download_defaults_paused),
            )
            CheckboxWithLabel(
                checked = skipHashChecking,
                onCheckedChange = { skipHashChecking = it },
                label = stringResource(Res.string.torrent_add_skip_hash_checking),
            )
            CheckboxWithLabel(
                checked = isSequentialDownloadEnabled,
                onCheckedChange = { isSequentialDownloadEnabled = it },
                label = stringResource(Res.string.torrent_add_sequential_download),
            )
            CheckboxWithLabel(
                checked = isFirstLastPiecePrioritized,
                onCheckedChange = { isFirstLastPiecePrioritized = it },
                label = stringResource(Res.string.torrent_add_prioritize_first_last_piece),
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.prowlarr_download_defaults_section_routes),
                style = MaterialTheme.typography.titleMedium,
            )

            if (routes.isEmpty()) {
                Text(
                    text = stringResource(Res.string.prowlarr_download_defaults_no_routes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                routes.forEachIndexed { index, route ->
                    RouteListItem(
                        route = route,
                        canMoveUp = index > 0,
                        canMoveDown = index < routes.lastIndex,
                        onMoveUp = { viewModel.moveRoute(index, index - 1) },
                        onMoveDown = { viewModel.moveRoute(index, index + 1) },
                        onEdit = { currentDialog = RouteDialog.EditRoute(route) },
                        onDelete = { currentDialog = RouteDialog.DeleteRoute(route.id, route.name) },
                    )
                }
            }

            OutlinedButton(
                onClick = { currentDialog = RouteDialog.AddRoute },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(text = stringResource(Res.string.prowlarr_download_defaults_add_route))
            }

            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
        }
    }

    when (val dialog = currentDialog) {
        is RouteDialog.AddRoute -> {
            DownloadRouteDialog(
                existingRoute = null,
                standardGroups = standardCategoryGroups,
                siteSpecificIndexerGroups = siteSpecificCategoryGroups,
                indexers = indexers ?: emptyList(),
                servers = servers,
                onDismiss = { currentDialog = null },
                onConfirm = { name, categoryIds, indexerIds, routeServerId, routeSavePath, routeCategory, routeTags ->
                    viewModel.saveRoute(
                        null,
                        name,
                        categoryIds,
                        indexerIds,
                        routeServerId,
                        routeSavePath,
                        routeCategory,
                        routeTags,
                    )
                    currentDialog = null
                },
            )
        }
        is RouteDialog.EditRoute -> {
            DownloadRouteDialog(
                existingRoute = dialog.route,
                standardGroups = standardCategoryGroups,
                siteSpecificIndexerGroups = siteSpecificCategoryGroups,
                indexers = indexers ?: emptyList(),
                servers = servers,
                onDismiss = { currentDialog = null },
                onConfirm = { name, categoryIds, indexerIds, routeServerId, routeSavePath, routeCategory, routeTags ->
                    viewModel.saveRoute(
                        dialog.route.id,
                        name,
                        categoryIds,
                        indexerIds,
                        routeServerId,
                        routeSavePath,
                        routeCategory,
                        routeTags,
                    )
                    currentDialog = null
                },
            )
        }
        is RouteDialog.DeleteRoute -> {
            DeleteRouteDialog(
                routeName = dialog.routeName,
                onDismiss = { currentDialog = null },
                onConfirm = {
                    viewModel.deleteRoute(dialog.routeId)
                    currentDialog = null
                },
            )
        }
        null -> {}
    }
}

/**
 * Server picker used both by the "Default Parameters" form and [DownloadRouteDialog] - P2
 * feedback round 1 (docs/prowlarr-p2-feedback-round1-plan.md section 3). [noneLabel] differs
 * between the two call sites: the defaults form's `null` means "no default set, fall back to
 * whichever server is active elsewhere in the app", while a route's `null` means "don't override,
 * inherit the default's server" - same shape, different fallback semantics, so the label is left
 * to the caller rather than hardcoded here.
 */
@Composable
private fun ServerDropdown(
    servers: List<ServerConfig>,
    selectedServerId: Int?,
    onSelect: (Int?) -> Unit,
    noneLabel: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedServer = servers.find { it.id == selectedServerId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            label = {
                Text(
                    text = stringResource(Res.string.prowlarr_download_defaults_server),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            value = selectedServer?.displayName ?: noneLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(text = noneLabel) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            servers.forEach { server ->
                DropdownMenuItem(
                    text = { Text(text = server.displayName) },
                    onClick = {
                        onSelect(server.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EnumDropdown(
    label: String,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    options: List<StringResource>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            label = { Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            value = stringResource(options[selectedIndex]),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, stringRes ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(stringRes)) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RouteListItem(
    route: ProwlarrDownloadRoute,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = route.name, style = MaterialTheme.typography.bodyLarge)
            // Both dimensions filled in: dedicated two-placeholder string rather than concatenating
            // two calls to prowlarr_search_categories_selected client-side - a hardcoded separator
            // (", " or similar) between two independently-translated fragments can come out in the
            // wrong order/punctuation for languages with different list conventions, the same
            // reasoning the plan doc gives for this string (section 3.4). Single-dimension routes
            // (still the only kind creatable before this round, and likely the common case for a
            // while after) keep reusing the plain "%1$d selected" count - genuinely dimension-
            // agnostic text despite the string's "categories" name (docs/prowlarr-route-and-
            // category-grouping-plan.md section 3.4).
            val summary = if (route.categoryIds.isNotEmpty() && route.indexerIds.isNotEmpty()) {
                stringResource(
                    Res.string.prowlarr_download_defaults_route_summary_both,
                    route.categoryIds.size,
                    route.indexerIds.size,
                )
            } else if (route.indexerIds.isNotEmpty()) {
                stringResource(Res.string.prowlarr_search_categories_selected, route.indexerIds.size)
            } else {
                stringResource(Res.string.prowlarr_search_categories_selected, route.categoryIds.size)
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(Res.string.prowlarr_download_defaults_move_route_up),
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(Res.string.prowlarr_download_defaults_move_route_down),
            )
        }
        IconButton(onClick = onEdit) {
            Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(Res.string.prowlarr_download_defaults_delete_route),
            )
        }
    }
}

@Serializable
private sealed class RouteDialog {
    @Serializable
    data object AddRoute : RouteDialog()

    @Serializable
    data class EditRoute(val route: ProwlarrDownloadRoute) : RouteDialog()

    @Serializable
    data class DeleteRoute(val routeId: String, val routeName: String) : RouteDialog()
}

/**
 * Add/edit dialog for a single [ProwlarrDownloadRoute]. Reuses [CategorySelectionSection] (shared
 * with the search screen's picker, see ui/prowlarr/ProwlarrCategoryPicker.kt) so selecting which
 * categories this route matches is the exact same interaction as filtering search results by
 * category - no separate picker UI to maintain, including the standard/site-specific-per-indexer
 * split (docs/prowlarr-route-and-category-grouping-plan.md section 4.4). Also reuses
 * [IndexerSelectionSection] (ui/prowlarr/ProwlarrIndexerPicker.kt) the same way for the indexer
 * matching dimension (plan doc section 3).
 *
 * This dialog was renamed from `CategoryRouteDialog` to `DownloadRouteDialog` a round ago (plan doc
 * section 3.5) - the old name read as "the category-route dialog" once a route could match on
 * category *and/or* indexer, and was easy to confuse with the sibling `sealed class RouteDialog`
 * (dialog *visibility* state, an unrelated concept) sharing most of the same words. The rest of the
 * plan doc's 3.5 rename table - [ProwlarrDownloadRoute] itself (formerly `ProwlarrCategoryRoute`,
 * both the class and its file), `SettingsManager.prowlarrDownloadRoutes` (formerly
 * `prowlarrCategoryRoutes` - the underlying storage key string is unchanged either way), and this
 * screen's `ProwlarrDownloadDefaultsViewModel` method names (`saveRoute`/`deleteRoute`/`moveRoute`)
 * - lands in this same round, one commit later.
 *
 * [orphanCategoryIds]/[orphanIndexerIds]: if the route being edited references a category or
 * indexer id that isn't offered by/present in any currently configured indexer (that indexer was
 * since disabled/removed, or indexers just haven't finished loading yet), those ids won't render
 * as chips in either picker - but editing the route and saving shouldn't silently drop them. Both
 * are carried through untouched and merged back into the saved
 * [ProwlarrDownloadRoute.categoryIds]/[ProwlarrDownloadRoute.indexerIds] on confirm.
 *
 * Validation (plan doc section 3.4): a route needs *at least one* of categories/indexers selected,
 * not both - [matchError] only fires when the category selection (top + sub + orphan ids) *and*
 * [selectedIndexerIds] (+ orphans) both end up empty at confirm time. Either dimension being empty
 * on its own is a valid "wildcard, matches anything on this dimension" state - see
 * [resolveProwlarrDownloadRouting].
 */
@Composable
private fun DownloadRouteDialog(
    existingRoute: ProwlarrDownloadRoute?,
    standardGroups: List<CategoryGroup>,
    siteSpecificIndexerGroups: List<IndexerCategoryGroup>,
    indexers: List<ProwlarrIndexer>,
    servers: List<ServerConfig>,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        categoryIds: List<Int>,
        indexerIds: List<Int>,
        serverId: Int?,
        savePath: String?,
        category: String?,
        tags: List<String>,
    ) -> Unit,
) {
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingRoute?.name ?: ""))
    }
    var nameError by rememberSaveable(
        stateSaver = stringResourceSaver(Res.string.error_required_field),
    ) { mutableStateOf(null) }
    var matchError by rememberSaveable(
        stateSaver = stringResourceSaver(Res.string.prowlarr_download_defaults_route_match_required),
    ) { mutableStateOf(null) }

    var serverId by rememberSaveable { mutableStateOf(existingRoute?.serverId) }
    var savePath by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingRoute?.savePath ?: ""))
    }
    var category by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingRoute?.category ?: ""))
    }
    var tags by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingRoute?.tags?.joinToString(", ") ?: ""))
    }

    // Orphan/initial-selection derivation below needs to look across both sections - a route's
    // categoryIds don't record which section a category came from, only its raw id, and that's
    // still true after the standard/site-specific-per-indexer split (see plan doc section 5: the
    // grouping-by-indexer change only affects how site-specific categories are *displayed*, not
    // what ends up in ProwlarrDownloadRoute.categoryIds).
    val allCategoryGroups = remember(standardGroups, siteSpecificIndexerGroups) {
        standardGroups + siteSpecificIndexerGroups.flatMap { it.categories }
    }

    val orphanCategoryIds = remember(allCategoryGroups, existingRoute) {
        val knownIds = allCategoryGroups.flatMap { group -> listOf(group.id) + group.subCategories.map { it.id } }.toSet()
        (existingRoute?.categoryIds ?: emptyList()).filterNot { it in knownIds }
    }
    val orphanIndexerIds = remember(indexers, existingRoute) {
        val knownIndexerIds = indexers.map { it.id }.toSet()
        (existingRoute?.indexerIds ?: emptyList()).filterNot { it in knownIndexerIds }
    }

    var categoryExpanded by rememberSaveable { mutableStateOf(true) }
    val expandedGroupIds = rememberSaveable(saver = stateListSaver()) { mutableStateListOf<Int>() }
    val expandedIndexerGroupIds = rememberSaveable(saver = stateListSaver()) { mutableStateListOf<Int>() }
    var indexerExpanded by rememberSaveable { mutableStateOf(true) }

    val initialTopCategoryIds = remember(allCategoryGroups, existingRoute) {
        existingRoute?.categoryIds?.filter { id -> allCategoryGroups.any { it.id == id } } ?: emptyList()
    }
    val initialSubCategoryIds = remember(allCategoryGroups, existingRoute) {
        existingRoute?.categoryIds?.filter { id ->
            allCategoryGroups.any { group -> group.subCategories.any { it.id == id } }
        } ?: emptyList()
    }
    val selectedTopCategoryIds = rememberSaveable(saver = stateListSaver()) {
        mutableStateListOf<Int>().apply { addAll(initialTopCategoryIds) }
    }
    val selectedSubCategoryIds = rememberSaveable(saver = stateListSaver()) {
        mutableStateListOf<Int>().apply { addAll(initialSubCategoryIds) }
    }
    val initialIndexerIds = remember(indexers, existingRoute) {
        existingRoute?.indexerIds?.filter { id -> indexers.any { it.id == id } } ?: emptyList()
    }
    val selectedIndexerIds = rememberSaveable(saver = stateListSaver()) {
        mutableStateListOf<Int>().apply { addAll(initialIndexerIds) }
    }

    fun tryConfirm() {
        val categoryIds = (selectedTopCategoryIds + selectedSubCategoryIds + orphanCategoryIds).distinct()
        val indexerIds = (selectedIndexerIds + orphanIndexerIds).distinct()

        nameError = if (name.text.isBlank()) Res.string.error_required_field else null
        matchError = if (categoryIds.isEmpty() && indexerIds.isEmpty()) {
            Res.string.prowlarr_download_defaults_route_match_required
        } else {
            null
        }

        if (nameError == null && matchError == null) {
            onConfirm(
                name.text,
                categoryIds,
                indexerIds,
                serverId,
                savePath.text.ifBlank { null },
                category.text.ifBlank { null },
                tags.text.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingRoute == null) {
                    stringResource(Res.string.prowlarr_download_defaults_route_dialog_add)
                } else {
                    stringResource(Res.string.prowlarr_download_defaults_route_dialog_edit)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        if (it.text != name.text) nameError = null
                        name = it
                    },
                    label = {
                        Text(
                            text = stringResource(Res.string.prowlarr_download_defaults_route_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { error -> { Text(text = stringResource(error)) } },
                    trailingIcon = nameError?.let {
                        { Icon(imageVector = Icons.Filled.Error, contentDescription = null) }
                    },
                    keyboardActions = KeyboardActions(onDone = { tryConfirm() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                ServerDropdown(
                    servers = servers,
                    selectedServerId = serverId,
                    onSelect = { serverId = it },
                    noneLabel = stringResource(Res.string.prowlarr_download_defaults_server_use_default),
                    modifier = Modifier.fillMaxWidth(),
                )

                IndexerSelectionSection(
                    expanded = indexerExpanded,
                    onExpandedChange = { indexerExpanded = it },
                    indexers = indexers,
                    selectedIndexerIds = selectedIndexerIds,
                    onToggleIndexer = { id ->
                        if (id in selectedIndexerIds) {
                            selectedIndexerIds.remove(id)
                        } else {
                            selectedIndexerIds.add(id)
                        }
                        matchError = null
                    },
                )

                CategorySelectionSection(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                    standardGroups = standardGroups,
                    siteSpecificIndexerGroups = siteSpecificIndexerGroups,
                    expandedGroupIds = expandedGroupIds,
                    expandedIndexerGroupIds = expandedIndexerGroupIds,
                    onToggleGroupExpanded = { id ->
                        if (id in expandedGroupIds) expandedGroupIds.remove(id) else expandedGroupIds.add(id)
                    },
                    onToggleIndexerGroupExpanded = { id ->
                        if (id in expandedIndexerGroupIds) {
                            expandedIndexerGroupIds.remove(id)
                        } else {
                            expandedIndexerGroupIds.add(id)
                        }
                    },
                    selectedTopCategoryIds = selectedTopCategoryIds,
                    onToggleTopCategory = { id ->
                        if (id in selectedTopCategoryIds) {
                            selectedTopCategoryIds.remove(id)
                        } else {
                            selectedTopCategoryIds.add(id)
                        }
                        matchError = null
                    },
                    selectedSubCategoryIds = selectedSubCategoryIds,
                    onToggleSubCategory = { id ->
                        if (id in selectedSubCategoryIds) {
                            selectedSubCategoryIds.remove(id)
                        } else {
                            selectedSubCategoryIds.add(id)
                        }
                        matchError = null
                    },
                )
                if (matchError != null) {
                    Text(
                        text = stringResource(matchError!!),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                val fieldHint = stringResource(Res.string.prowlarr_download_defaults_route_field_hint)

                OutlinedTextField(
                    value = savePath,
                    onValueChange = { savePath = it },
                    label = { Text(text = stringResource(Res.string.torrent_add_save_path)) },
                    placeholder = { Text(text = fieldHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(text = stringResource(Res.string.torrent_add_category)) },
                    placeholder = { Text(text = fieldHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(text = stringResource(Res.string.torrent_add_tags)) },
                    placeholder = { Text(text = fieldHint) },
                    singleLine = true,
                    keyboardActions = KeyboardActions(onDone = { tryConfirm() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { tryConfirm() }) {
                Text(text = stringResource(Res.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.dialog_cancel))
            }
        },
    )
}

@Composable
private fun DeleteRouteDialog(routeName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.prowlarr_download_defaults_delete_route)) },
        text = { Text(text = routeName) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = stringResource(Res.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.dialog_cancel))
            }
        },
    )
}

private fun formatRatioLimit(value: Double): String {
    val decimalSeparator = getDecimalSeparator()
    // Trim a trailing ".0"/",0" so re-editing an integral ratio like 2.0 doesn't show noise -
    // matches the plain textual round-trip AddTorrentScreen's own ratio field does.
    val text = value.toString().trimEnd('0').trimEnd('.')
    return text.replace('.', decimalSeparator)
}

private fun parseRatioLimit(text: String): Double? {
    if (text.isBlank()) {
        return null
    }
    return text.replace(getDecimalSeparator(), '.').toDoubleOrNull()
}
