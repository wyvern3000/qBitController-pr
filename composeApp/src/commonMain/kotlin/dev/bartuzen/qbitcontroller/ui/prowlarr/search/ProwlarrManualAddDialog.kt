package dev.bartuzen.qbitcontroller.ui.prowlarr.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bartuzen.qbitcontroller.model.ProwlarrDownloadDefaults
import dev.bartuzen.qbitcontroller.model.Search
import dev.bartuzen.qbitcontroller.model.ServerConfig
import dev.bartuzen.qbitcontroller.ui.components.CategoryChip
import dev.bartuzen.qbitcontroller.ui.components.CheckboxWithLabel
import dev.bartuzen.qbitcontroller.ui.components.DropdownMenuItem
import dev.bartuzen.qbitcontroller.ui.components.TagChip
import dev.bartuzen.qbitcontroller.utils.getDecimalSeparator
import dev.bartuzen.qbitcontroller.utils.stateListSaver
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import qbitcontroller.composeapp.generated.resources.Res
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_server
import qbitcontroller.composeapp.generated.resources.prowlarr_search_manual_download
import qbitcontroller.composeapp.generated.resources.speed_kibibytes_per_second
import qbitcontroller.composeapp.generated.resources.torrent_add_category
import qbitcontroller.composeapp.generated.resources.torrent_add_content_layout
import qbitcontroller.composeapp.generated.resources.torrent_add_content_layout_no_subfolder
import qbitcontroller.composeapp.generated.resources.torrent_add_content_layout_original
import qbitcontroller.composeapp.generated.resources.torrent_add_content_layout_subfolder
import qbitcontroller.composeapp.generated.resources.torrent_add_default
import qbitcontroller.composeapp.generated.resources.torrent_add_download_speed_limit
import qbitcontroller.composeapp.generated.resources.torrent_add_name
import qbitcontroller.composeapp.generated.resources.torrent_add_prioritize_first_last_piece
import qbitcontroller.composeapp.generated.resources.torrent_add_ratio_limit
import qbitcontroller.composeapp.generated.resources.torrent_add_save_path
import qbitcontroller.composeapp.generated.resources.torrent_add_seeding_time_limit
import qbitcontroller.composeapp.generated.resources.torrent_add_sequential_download
import qbitcontroller.composeapp.generated.resources.torrent_add_skip_hash_checking
import qbitcontroller.composeapp.generated.resources.torrent_add_start_torrent
import qbitcontroller.composeapp.generated.resources.torrent_add_stop_condition
import qbitcontroller.composeapp.generated.resources.torrent_add_stop_condition_files_checked
import qbitcontroller.composeapp.generated.resources.torrent_add_stop_condition_metadata_received
import qbitcontroller.composeapp.generated.resources.torrent_add_stop_condition_none
import qbitcontroller.composeapp.generated.resources.torrent_add_tags
import qbitcontroller.composeapp.generated.resources.torrent_add_torrent_management_mode
import qbitcontroller.composeapp.generated.resources.torrent_add_torrent_management_mode_auto
import qbitcontroller.composeapp.generated.resources.torrent_add_torrent_management_mode_manual
import qbitcontroller.composeapp.generated.resources.torrent_add_upload_speed_limit
import qbitcontroller.composeapp.generated.resources.torrent_no_categories

/**
 * Long-press-on-download manual mode - P2 feedback round 1 item 5, see
 * docs/prowlarr-p2-feedback-round1-plan.md section 5 for why this exists as its own dialog rather
 * than reusing [dev.bartuzen.qbitcontroller.ui.addtorrent.AddTorrentScreen] directly (in short: that
 * screen's URL mode hands the link to qBittorrent server-side, which is exactly what round 3
 * deliberately avoided for Prowlarr `downloadUrl`s - magnet-passthrough/client-side-download is
 * fixed either way here, only the destination server/save path/category/etc are user-editable).
 *
 * Fields are pre-filled from [viewModel]'s [ProwlarrSearchViewModel.resolveDownloadRouting] result
 * for [searchResult] plus [ProwlarrSearchViewModel.downloadDefaults] - the same starting point the
 * one-tap auto path would use - so "manual mode" means "review and adjust", not "start blank".
 *
 * Directory-suggestion autocomplete (unlike AddTorrentScreen) is deliberately not implemented here,
 * same scope cut as ProwlarrDownloadDefaultsScreen's save path field - see plan doc section 5.
 */
@Composable
fun ProwlarrManualAddDialog(
    searchResult: Search.Result,
    viewModel: ProwlarrSearchViewModel,
    onDismiss: () -> Unit,
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val serverData by viewModel.manualAddServerData.collectAsStateWithLifecycle()
    val isLoadingServerData by viewModel.isLoadingManualAddServerData.collectAsStateWithLifecycle()

    val routing = remember { viewModel.resolveDownloadRouting(searchResult.categories, searchResult.indexerId) }
    val defaults = remember { viewModel.downloadDefaults }

    var serverId by rememberSaveable {
        mutableStateOf(routing.serverId ?: servers.firstOrNull()?.id)
    }
    var savePath by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(routing.savePath ?: ""))
    }
    var selectedCategory by rememberSaveable { mutableStateOf(routing.category) }
    val selectedTags = rememberSaveable(saver = stateListSaver()) {
        mutableStateListOf<String>().apply { addAll(routing.tags) }
    }
    var torrentName by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
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
    var autoTmmIndex by rememberSaveable {
        mutableIntStateOf(
            when (defaults.isAutoTorrentManagementEnabled) {
                false -> 1
                true -> 2
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
        mutableStateOf(TextFieldValue(defaults.ratioLimit?.let { formatManualRatioLimit(it) } ?: ""))
    }
    var seedingTimeLimit by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(defaults.seedingTimeLimit?.toString() ?: ""))
    }
    var startTorrent by rememberSaveable { mutableStateOf(!defaults.isPaused) }
    var skipHashChecking by rememberSaveable { mutableStateOf(defaults.skipHashChecking) }
    var isSequentialDownloadEnabled by rememberSaveable { mutableStateOf(defaults.isSequentialDownloadEnabled) }
    var isFirstLastPiecePrioritized by rememberSaveable { mutableStateOf(defaults.isFirstLastPiecePrioritized) }

    LaunchedEffect(serverId) {
        val id = serverId
        if (id != null) {
            viewModel.loadManualAddServerData(id)
        }
    }

    fun confirm() {
        val id = serverId ?: return
        viewModel.addTorrentManual(
            id,
            searchResult,
            ProwlarrSearchViewModel.ManualDownloadOptions(
                savePath = savePath.text.ifBlank { null },
                category = selectedCategory,
                tags = selectedTags.toList(),
                torrentName = torrentName.text.ifBlank { null },
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
                ratioLimit = parseManualRatioLimit(ratioLimit.text),
                seedingTimeLimit = seedingTimeLimit.text.toIntOrNull(),
                isPaused = !startTorrent,
                skipHashChecking = skipHashChecking,
                isAutoTorrentManagementEnabled = when (autoTmmIndex) {
                    1 -> false
                    2 -> true
                    else -> null
                },
                isSequentialDownloadEnabled = isSequentialDownloadEnabled,
                isFirstLastPiecePrioritized = isFirstLastPiecePrioritized,
            ),
        )
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(Res.string.prowlarr_search_manual_download)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = ::confirm, enabled = serverId != null) {
                            Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                        }
                    },
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ManualServerDropdown(
                    servers = servers,
                    selectedServerId = serverId,
                    onSelect = { serverId = it },
                )

                OutlinedTextField(
                    value = savePath,
                    onValueChange = { savePath = it },
                    label = {
                        Text(text = stringResource(Res.string.torrent_add_save_path), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = torrentName,
                    onValueChange = { torrentName = it },
                    label = {
                        Text(text = stringResource(Res.string.torrent_add_name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(text = stringResource(Res.string.torrent_add_category))
                AnimatedContent(targetState = isLoadingServerData to serverData?.categories) { (isLoading, categories) ->
                    when {
                        isLoading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        categories?.isNotEmpty() == true -> FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            categories.forEach { category ->
                                CategoryChip(
                                    category = category.name,
                                    isSelected = selectedCategory == category.name,
                                    onClick = {
                                        if (selectedCategory == category.name) {
                                            selectedCategory = null
                                        } else {
                                            selectedCategory = category.name
                                            if (category.savePath.isNotBlank()) {
                                                savePath = TextFieldValue(category.savePath)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        else -> Text(
                            text = stringResource(Res.string.torrent_no_categories),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(text = stringResource(Res.string.torrent_add_tags))
                AnimatedContent(targetState = isLoadingServerData to serverData?.tags) { (isLoading, tags) ->
                    when {
                        isLoading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        tags?.isNotEmpty() == true -> FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            tags.forEach { tag ->
                                TagChip(
                                    tag = tag,
                                    isSelected = tag in selectedTags,
                                    onClick = {
                                        if (tag in selectedTags) selectedTags.remove(tag) else selectedTags.add(tag)
                                    },
                                )
                            }
                        }
                        else -> {}
                    }
                }

                ManualEnumDropdown(
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

                ManualEnumDropdown(
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

                ManualEnumDropdown(
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
                        Text(text = stringResource(Res.string.torrent_add_ratio_limit), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    checked = startTorrent,
                    onCheckedChange = { startTorrent = it },
                    label = stringResource(Res.string.torrent_add_start_torrent),
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
            }
        }
    }
}

@Composable
private fun ManualServerDropdown(
    servers: List<ServerConfig>,
    selectedServerId: Int?,
    onSelect: (Int) -> Unit,
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
            value = selectedServer?.displayName ?: "",
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
private fun ManualEnumDropdown(
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

// Deliberately not locale-decimal-separator-aware unlike AddTorrentScreen's ratio limit handling -
// see docs/prowlarr-p2-feedback-round1-plan.md section 5's explicit scope cut. The input field
// itself still only accepts the locale's own decimal separator (ratioLimitRegex above), this just
// normalizes it to '.' for parsing/formatting.
private fun formatManualRatioLimit(value: Double): String {
    val text = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    return text.replace('.', getDecimalSeparator())
}

private fun parseManualRatioLimit(text: String): Double? = text.replace(getDecimalSeparator(), '.').toDoubleOrNull()
