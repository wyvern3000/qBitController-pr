package dev.bartuzen.qbitcontroller.ui.prowlarr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bartuzen.qbitcontroller.model.ProwlarrCategory
import dev.bartuzen.qbitcontroller.model.ProwlarrIndexer
import dev.bartuzen.qbitcontroller.ui.components.CategoryChip
import dev.bartuzen.qbitcontroller.utils.stringResource
import qbitcontroller.composeapp.generated.resources.Res
import qbitcontroller.composeapp.generated.resources.prowlarr_search_categories
import qbitcontroller.composeapp.generated.resources.prowlarr_search_categories_all
import qbitcontroller.composeapp.generated.resources.prowlarr_search_categories_selected
import qbitcontroller.composeapp.generated.resources.prowlarr_search_categories_site_specific
import qbitcontroller.composeapp.generated.resources.prowlarr_search_categories_standard

/**
 * Shared category-group multi-select, extracted verbatim (pure move, no logic changes) from
 * `ui/prowlarr/search/ProwlarrSearchScreen.kt` so it can also back the category-route picker in
 * `ui/settings/prowlarr/download/ProwlarrDownloadDefaultsScreen.kt` (see
 * docs/prowlarr-download-defaults-plan.md) - the search screen's category picker and the download-
 * defaults screen's per-route category picker are the same UI concept (pick a subset of the
 * categories the configured indexers actually report), so this avoids a second copy of the
 * grouping/expand logic. Visibility changed from `private` (file-scoped) to `internal`
 * (module-wide) as part of the move; nothing about behavior or layout changed.
 */

/**
 * A top-level Torznab/Newznab category entry (e.g. id 2000 "Movies", or an indexer-specific custom
 * entry like id 100401 "Movies" - see [buildCategoryGroups]) together with the union of its
 * [subCategories][ProwlarrCategory.subCategories] across whichever indexers are currently in play.
 */
internal data class CategoryGroup(val id: Int, val name: String, val subCategories: List<ProwlarrCategory>)

/**
 * Groups [indexers]' `capabilities.categories` ([ProwlarrCategory] list) by top-level id,
 * unioning subcategories for ids more than one indexer reports.
 *
 * Deliberately **not** the plan doc's original design (section 2.2): that called for a fixed list of
 * the 8 standard Torznab top-level categories (1000 Console ... 8000 Other) as the only top-level
 * groups. Checked against the real indexer sample from round 7 and that would have made the picker
 * nearly useless for exactly the indexers this instance has configured - e.g. OpenCD (a Chinese
 * music tracker) puts effectively its entire genre taxonomy (华语流行/古典音乐/摇滚/爵士/... ) under
 * custom ids in the 100000+ range, as flat top-level entries with no parent among the 8 standard
 * ones, and OurBits reports both a standard "Movies" (2000, with real subCategories) and a redundant
 * custom "Movies" (100401, no subCategories) as two separate top-level entries. Prowlarr's
 * `capabilities.categories` array doesn't structurally distinguish "standard" from "custom" - both
 * are just top-level entries, some with subCategories, some without - so building groups from
 * whatever indexers actually report (rather than hardcoding the 8) is the only way to expose the
 * custom ones at all. Sorting by id keeps the standard 1000-8000 range naturally first, ahead of the
 * 100000+ custom entries, without needing to special-case anything.
 *
 * One known quirk this doesn't try to solve: two groups from different indexers (or, per the OurBits
 * example above, the same indexer) can share a display name but not an id - they show up as separate
 * chips. Deduping by name would risk merging categories that only coincidentally share a label, so
 * this leaves that as-is rather than guessing.
 */
internal fun buildCategoryGroups(indexers: List<ProwlarrIndexer>): List<CategoryGroup> {
    val byId = linkedMapOf<Int, CategoryGroup>()
    for (indexer in indexers) {
        val categories = indexer.capabilities?.categories ?: continue
        for (category in categories) {
            val existing = byId[category.id]
            byId[category.id] = if (existing == null) {
                CategoryGroup(category.id, category.name, category.subCategories)
            } else {
                existing.copy(subCategories = (existing.subCategories + category.subCategories).distinctBy { it.id })
            }
        }
    }
    return byId.values.sortedBy { it.id }
}

// Torznab/Newznab spec reserves ids >= 100000 for indexer-specific ("site-specific") categories,
// precisely so they don't collide with the ~30 standard categories in 1000-8999 (confirmed against
// the spec, not guessed - see https://torznab.github.io/spec-1.3-draft and Sonarr's Torznab-indexer
// wiki page). Used purely as a presentation split (see CategorySelectionSection KDoc) - the actual
// id sent to search() is unaffected either way.
internal const val SITE_SPECIFIC_CATEGORY_ID_THRESHOLD = 100_000

/**
 * Collapsible category multi-select (see docs/prowlarr-p1-search-ui-and-tabs-plan.md, section 2.2,
 * and [buildCategoryGroups] for why the grouping itself deviates from that doc). Two-level: each
 * [CategoryGroup] is its own row - directly selectable via [CategoryChip], with an expand chevron
 * next to it only when it actually has subcategories to show. There's no existing two-level
 * selector anywhere else in this codebase to mirror (TorrentListScreen's category filter is a flat,
 * single-select list) - only the leaf [CategoryChip] component itself follows an established
 * pattern, the grouping/expand layout here is new.
 *
 * Split into "Standard"/"Site-Specific" sections (round 9 fix, reported against a real device):
 * a flat id-sorted list put e.g. OurBits' standard "Movies" (2000, with a real "Movies/3D"
 * subcategory) directly next to its own redundant site-specific "Movies" (100401, no subcategories)
 * with no visual distinction - looked like duplicated/glitched data rather than two different,
 * intentionally-separate ids. [buildCategoryGroups]' KDoc already called this out as a known
 * "same name, different id" quirk, but seeing it on a real device made clear a flat list wasn't
 * good enough - it needed the section split, not just a code comment.
 */
@Composable
internal fun CategorySelectionSection(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    categoryGroups: List<CategoryGroup>,
    expandedGroupIds: List<Int>,
    onToggleGroupExpanded: (Int) -> Unit,
    selectedTopCategoryIds: List<Int>,
    onToggleTopCategory: (Int) -> Unit,
    selectedSubCategoryIds: List<Int>,
    onToggleSubCategory: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)
    val selectedCount = selectedTopCategoryIds.size + selectedSubCategoryIds.size

    Column(modifier = modifier) {
        val summary = if (selectedCount == 0) {
            stringResource(Res.string.prowlarr_search_categories_all)
        } else {
            stringResource(Res.string.prowlarr_search_categories_selected, selectedCount)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Category,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "${stringResource(Res.string.prowlarr_search_categories)}: $summary",
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
            if (categoryGroups.isEmpty()) {
                Text(
                    text = stringResource(Res.string.prowlarr_search_categories_all),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                val (standardGroups, siteSpecificGroups) = categoryGroups.partition {
                    it.id < SITE_SPECIFIC_CATEGORY_ID_THRESHOLD
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Only bother labeling sections when there's actually a second one to
                    // distinguish from - a lone "Standard" header with nothing else on screen would
                    // just be noise.
                    val showSectionLabels = standardGroups.isNotEmpty() && siteSpecificGroups.isNotEmpty()

                    if (standardGroups.isNotEmpty()) {
                        if (showSectionLabels) {
                            CategorySectionLabel(text = stringResource(Res.string.prowlarr_search_categories_standard))
                        }
                        standardGroups.forEach { group ->
                            CategoryGroupRow(
                                group = group,
                                isExpanded = group.id in expandedGroupIds,
                                onToggleExpanded = { onToggleGroupExpanded(group.id) },
                                isTopSelected = group.id in selectedTopCategoryIds,
                                onToggleTop = { onToggleTopCategory(group.id) },
                                selectedSubCategoryIds = selectedSubCategoryIds,
                                onToggleSub = onToggleSubCategory,
                            )
                        }
                    }

                    if (siteSpecificGroups.isNotEmpty()) {
                        if (showSectionLabels) {
                            CategorySectionLabel(
                                text = stringResource(Res.string.prowlarr_search_categories_site_specific),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        siteSpecificGroups.forEach { group ->
                            CategoryGroupRow(
                                group = group,
                                isExpanded = group.id in expandedGroupIds,
                                onToggleExpanded = { onToggleGroupExpanded(group.id) },
                                isTopSelected = group.id in selectedTopCategoryIds,
                                onToggleTop = { onToggleTopCategory(group.id) },
                                selectedSubCategoryIds = selectedSubCategoryIds,
                                onToggleSub = onToggleSubCategory,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CategorySectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}

/** A single [CategoryGroup] row plus its (optionally expanded) subcategory chips - see [CategorySelectionSection]. */
@Composable
internal fun CategoryGroupRow(
    group: CategoryGroup,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    isTopSelected: Boolean,
    onToggleTop: () -> Unit,
    selectedSubCategoryIds: List<Int>,
    onToggleSub: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (group.subCategories.isNotEmpty()) {
                val groupRotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)
                IconButton(onClick = onToggleExpanded, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(groupRotation),
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(32.dp))
            }

            CategoryChip(
                category = group.name,
                isSelected = isTopSelected,
                onClick = onToggleTop,
            )
        }

        if (group.subCategories.isNotEmpty()) {
            AnimatedVisibility(visible = isExpanded) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    group.subCategories.forEach { subCategory ->
                        CategoryChip(
                            category = subCategory.name,
                            isSelected = subCategory.id in selectedSubCategoryIds,
                            onClick = { onToggleSub(subCategory.id) },
                        )
                    }
                }
            }
        }
    }
}
