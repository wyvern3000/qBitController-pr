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
import dev.bartuzen.qbitcontroller.model.ProwlarrCategoryRoute
import dev.bartuzen.qbitcontroller.model.ProwlarrDownloadDefaults
import dev.bartuzen.qbitcontroller.ui.components.ActionMenuItem
import dev.bartuzen.qbitcontroller.ui.components.AppBarActions
import dev.bartuzen.qbitcontroller.ui.components.CheckboxWithLabel
import dev.bartuzen.qbitcontroller.ui.components.Dialog
import dev.bartuzen.qbitcontroller.ui.components.DropdownMenuItem
import dev.bartuzen.qbitcontroller.ui.components.SwipeableSnackbarHost
import dev.bartuzen.qbitcontroller.ui.prowlarr.CategoryGroup
import dev.bartuzen.qbitcontroller.ui.prowlarr.CategorySelectionSection
import dev.bartuzen.qbitcontroller.ui.prowlarr.buildCategoryGroups
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
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_route_categories_required
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_route_dialog_add
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_route_dialog_edit
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_route_field_hint
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_route_name
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_save_success
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_section_default
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_section_routes
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
 * default) plus an editable "Category Routes" list that overrides just save path/category/tags for
 * results matching specific Torznab categories - see [ProwlarrCategoryRoute] KDoc for why the
 * override is scoped to just those three fields.
 *
 * Save path/category fields are plain text (no per-server autocomplete/dropdown, unlike
 * AddTorrentScreen) - confirmed with the user (plan doc section 7): these defaults need to make
 * sense regardless of which qBittorrent server ends up handling a given download, so binding the
 * settings screen to one "reference server" to query real categories/paths from wasn't worth the
 * added complexity for this round.
 */
@Composable
fun ProwlarrDownloadDefaultsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProwlarrDownloadDefaultsViewModel = koinViewModel(),
) {
    val defaults = viewModel.downloadDefaults

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

    val categoryRoutes by viewModel.categoryRoutes.collectAsStateWithLifecycle()
    val indexers by viewModel.indexers.collectAsStateWithLifecycle()
    val categoryGroups = remember(indexers) { buildCategoryGroups(indexers ?: emptyList()) }

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

            if (categoryRoutes.isEmpty()) {
                Text(
                    text = stringResource(Res.string.prowlarr_download_defaults_no_routes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                categoryRoutes.forEachIndexed { index, route ->
                    RouteListItem(
                        route = route,
                        canMoveUp = index > 0,
                        canMoveDown = index < categoryRoutes.lastIndex,
                        onMoveUp = { viewModel.moveCategoryRoute(index, index - 1) },
                        onMoveDown = { viewModel.moveCategoryRoute(index, index + 1) },
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
            CategoryRouteDialog(
                existingRoute = null,
                categoryGroups = categoryGroups,
                onDismiss = { currentDialog = null },
                onConfirm = { name, categoryIds, routeSavePath, routeCategory, routeTags ->
                    viewModel.saveCategoryRoute(null, name, categoryIds, routeSavePath, routeCategory, routeTags)
                    currentDialog = null
                },
            )
        }
        is RouteDialog.EditRoute -> {
            CategoryRouteDialog(
                existingRoute = dialog.route,
                categoryGroups = categoryGroups,
                onDismiss = { currentDialog = null },
                onConfirm = { name, categoryIds, routeSavePath, routeCategory, routeTags ->
                    viewModel.saveCategoryRoute(dialog.route.id, name, categoryIds, routeSavePath, routeCategory, routeTags)
                    currentDialog = null
                },
            )
        }
        is RouteDialog.DeleteRoute -> {
            DeleteRouteDialog(
                routeName = dialog.routeName,
                onDismiss = { currentDialog = null },
                onConfirm = {
                    viewModel.deleteCategoryRoute(dialog.routeId)
                    currentDialog = null
                },
            )
        }
        null -> {}
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
    route: ProwlarrCategoryRoute,
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
            Text(
                text = stringResource(Res.string.prowlarr_search_categories_selected, route.categoryIds.size),
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
    data class EditRoute(val route: ProwlarrCategoryRoute) : RouteDialog()

    @Serializable
    data class DeleteRoute(val routeId: String, val routeName: String) : RouteDialog()
}

/**
 * Add/edit dialog for a single [ProwlarrCategoryRoute]. Reuses [CategorySelectionSection] (shared
 * with the search screen's picker, see ui/prowlarr/ProwlarrCategoryPicker.kt) so selecting which
 * categories this route matches is the exact same interaction as filtering search results by
 * category - no separate picker UI to maintain.
 *
 * [orphanCategoryIds]: if the route being edited has category ids that aren't offered by any
 * currently configured indexer (e.g. that indexer was since disabled/removed, or indexers just
 * haven't finished loading yet), those ids won't render as chips - but editing the route and
 * saving shouldn't silently drop them. They're carried through untouched and merged back into the
 * saved [ProwlarrCategoryRoute.categoryIds] on confirm.
 */
@Composable
private fun CategoryRouteDialog(
    existingRoute: ProwlarrCategoryRoute?,
    categoryGroups: List<CategoryGroup>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, categoryIds: List<Int>, savePath: String?, category: String?, tags: List<String>) -> Unit,
) {
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingRoute?.name ?: ""))
    }
    var nameError by rememberSaveable(
        stateSaver = stringResourceSaver(Res.string.error_required_field),
    ) { mutableStateOf(null) }
    var categoriesError by rememberSaveable(
        stateSaver = stringResourceSaver(Res.string.prowlarr_download_defaults_route_categories_required),
    ) { mutableStateOf(null) }

    var savePath by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingRoute?.savePath ?: ""))
    }
    var category by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingRoute?.category ?: ""))
    }
    var tags by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingRoute?.tags?.joinToString(", ") ?: ""))
    }

    val orphanCategoryIds = remember(categoryGroups, existingRoute) {
        val knownIds = categoryGroups.flatMap { group -> listOf(group.id) + group.subCategories.map { it.id } }.toSet()
        (existingRoute?.categoryIds ?: emptyList()).filterNot { it in knownIds }
    }

    var categoryExpanded by rememberSaveable { mutableStateOf(true) }
    val expandedGroupIds = rememberSaveable(saver = stateListSaver()) { mutableStateListOf<Int>() }

    val initialTopCategoryIds = remember(categoryGroups, existingRoute) {
        existingRoute?.categoryIds?.filter { id -> categoryGroups.any { it.id == id } } ?: emptyList()
    }
    val initialSubCategoryIds = remember(categoryGroups, existingRoute) {
        existingRoute?.categoryIds?.filter { id ->
            categoryGroups.any { group -> group.subCategories.any { it.id == id } }
        } ?: emptyList()
    }
    val selectedTopCategoryIds = rememberSaveable(saver = stateListSaver()) {
        mutableStateListOf<Int>().apply { addAll(initialTopCategoryIds) }
    }
    val selectedSubCategoryIds = rememberSaveable(saver = stateListSaver()) {
        mutableStateListOf<Int>().apply { addAll(initialSubCategoryIds) }
    }

    fun tryConfirm() {
        val categoryIds = (selectedTopCategoryIds + selectedSubCategoryIds + orphanCategoryIds).distinct()

        nameError = if (name.text.isBlank()) Res.string.error_required_field else null
        categoriesError = if (categoryIds.isEmpty()) {
            Res.string.prowlarr_download_defaults_route_categories_required
        } else {
            null
        }

        if (nameError == null && categoriesError == null) {
            onConfirm(
                name.text,
                categoryIds,
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

                CategorySelectionSection(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                    categoryGroups = categoryGroups,
                    expandedGroupIds = expandedGroupIds,
                    onToggleGroupExpanded = { id ->
                        if (id in expandedGroupIds) expandedGroupIds.remove(id) else expandedGroupIds.add(id)
                    },
                    selectedTopCategoryIds = selectedTopCategoryIds,
                    onToggleTopCategory = { id ->
                        if (id in selectedTopCategoryIds) {
                            selectedTopCategoryIds.remove(id)
                        } else {
                            selectedTopCategoryIds.add(id)
                        }
                        categoriesError = null
                    },
                    selectedSubCategoryIds = selectedSubCategoryIds,
                    onToggleSubCategory = { id ->
                        if (id in selectedSubCategoryIds) {
                            selectedSubCategoryIds.remove(id)
                        } else {
                            selectedSubCategoryIds.add(id)
                        }
                        categoriesError = null
                    },
                )
                if (categoriesError != null) {
                    Text(
                        text = stringResource(categoriesError!!),
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
