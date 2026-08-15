package dev.bartuzen.qbitcontroller.ui.prowlarr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bartuzen.qbitcontroller.model.ProwlarrIndexer
import dev.bartuzen.qbitcontroller.ui.components.TagChip
import dev.bartuzen.qbitcontroller.utils.stringResource
import qbitcontroller.composeapp.generated.resources.Res
import qbitcontroller.composeapp.generated.resources.prowlarr_download_defaults_route_indexers_any
import qbitcontroller.composeapp.generated.resources.prowlarr_search_categories_selected
import qbitcontroller.composeapp.generated.resources.prowlarr_search_indexers

/**
 * Flat indexer multi-select for the download-route dialog in
 * `ui/settings/prowlarr/download/ProwlarrDownloadDefaultsScreen.kt` - see
 * docs/prowlarr-route-and-category-grouping-plan.md sections 3.4/5. Not yet wired into that dialog
 * (that's plan step 6) - this file only adds the standalone component.
 *
 * Distinct from `ProwlarrSearchScreen.kt`'s own, `private` `IndexerSelectionSection` (same name,
 * different package, different job): that one picks *which indexers to search across right now*
 * (with an Enabled/All/Selected radio choice layered on top, since "enabled" and "all" are
 * meaningful default scopes for an interactive, repeatable search). This one picks *which indexers
 * a saved route should match against* - a route's indexer set is just "any" (empty list) or an
 * explicit subset, there's no equivalent "enabled indexers, whatever those happen to be at match
 * time" concept for a routing rule that has to behave predictably long after it was created. Kept
 * in this shared `ui/prowlarr` package (alongside [CategorySelectionSection]) rather than reusing
 * or duplicating the search screen's private one, since route-matching isn't that screen's concern.
 *
 * Deliberately flat - no grouping, no per-item expand/collapse, unlike [CategorySelectionSection]:
 * indexers don't have the standard/site-specific-per-indexer hierarchy categories do, they're just
 * a flat set of sites, so [TagChip] is the entire selection UI - the same component
 * `ProwlarrSearchScreen.kt`'s own indexer picker already uses, for a consistent look.
 *
 * [selectedIndexerIds] may reference an indexer id no longer present in [indexers] (that indexer
 * was disabled/removed since the route was created, or [indexers] just hasn't finished loading
 * yet) - same "orphan id" situation the route dialog already handles for categories via
 * `orphanCategoryIds`. This composable doesn't resolve orphans itself (it only renders chips for
 * what's actually in [indexers]) - the caller stays responsible for carrying orphan ids through on
 * save, same division of responsibility [CategorySelectionSection]'s callers already follow.
 */
@Composable
internal fun IndexerSelectionSection(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    indexers: List<ProwlarrIndexer>,
    selectedIndexerIds: List<Int>,
    onToggleIndexer: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

    Column(modifier = modifier) {
        val summary = if (selectedIndexerIds.isEmpty()) {
            stringResource(Res.string.prowlarr_download_defaults_route_indexers_any)
        } else {
            stringResource(Res.string.prowlarr_search_categories_selected, selectedIndexerIds.size)
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
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .rotate(rotation),
            )
        }

        AnimatedVisibility(visible = expanded) {
            // Same reasoning as CategorySelectionSection's empty-state check: reusing the header's
            // own "Any Indexer" summary text here too, rather than a separate "no indexers
            // available" message, since from the user's perspective both cases mean the same thing
            // - there's nothing to restrict the route by, so it matches every indexer either way.
            if (indexers.isEmpty()) {
                Text(
                    text = stringResource(Res.string.prowlarr_download_defaults_route_indexers_any),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
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
