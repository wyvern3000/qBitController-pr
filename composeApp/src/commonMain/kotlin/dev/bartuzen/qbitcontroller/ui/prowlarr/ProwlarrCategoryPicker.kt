package dev.bartuzen.qbitcontroller.ui.prowlarr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
 * entry like id 100401 "Movies" - see [buildStandardCategoryGroups]/[buildSiteSpecificGroups])
 * together with the union of its [subCategories][ProwlarrCategory.subCategories] - across all
 * indexers in play for a standard entry, or within one indexer's own list for a site-specific one.
 */
internal data class CategoryGroup(val id: Int, val name: String, val subCategories: List<ProwlarrCategory>)

/**
 * Groups [indexers]' `capabilities.categories` ([ProwlarrCategory] list) that are **standard**
 * (id < [SITE_SPECIFIC_CATEGORY_ID_THRESHOLD]) Torznab categories by top-level id, unioning
 * subcategories for ids more than one indexer reports - e.g. OurBits and another tracker both
 * reporting "Movies" (2000) get merged into one chip with the union of both indexers' Movies
 * subcategories. Standard ids are part of the shared Torznab spec, not any one
 * indexer's private namespace, so merging by id across indexers is safe here in a way it isn't
 * for site-specific ids - see [buildSiteSpecificGroups].
 *
 * Split out from a single `buildCategoryGroups(indexers)` (docs/prowlarr-route-and-category-
 * grouping-plan.md section 4.2) that used to return one flat, id-sorted list mixing both ranges -
 * [CategorySelectionSection] partitioned that list into "Standard"/"Site-Specific" sections for
 * display (round 9), but the underlying site-specific half was still merged globally by id, which
 * silently combined same-id-different-meaning custom categories from different indexers (see
 * [buildSiteSpecificGroups] KDoc). Splitting the *data* layer, not just the display layer, is what
 * actually fixes that.
 */
internal fun buildStandardCategoryGroups(indexers: List<ProwlarrIndexer>): List<CategoryGroup> {
    val byId = linkedMapOf<Int, CategoryGroup>()
    for (indexer in indexers) {
        val categories = indexer.capabilities?.categories ?: continue
        for (category in categories) {
            if (category.id >= SITE_SPECIFIC_CATEGORY_ID_THRESHOLD) {
                continue
            }
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

/** One indexer's own site-specific categories - see [buildSiteSpecificGroups]. */
internal data class IndexerCategoryGroup(val indexerId: Int, val indexerName: String, val categories: List<CategoryGroup>)

/**
 * Groups [indexers]' **site-specific** (id >= [SITE_SPECIFIC_CATEGORY_ID_THRESHOLD]) categories
 * *per indexer*, deliberately **not** merged across indexers by id the way
 * [buildStandardCategoryGroups] merges the standard range. Unlike standard ids (part of the shared
 * Torznab spec), site-specific ids are each indexer's own private namespace - the old
 * global-by-id-merge implementation this replaces would combine two different indexers' unrelated
 * custom categories into one chip whenever they happened to reuse the same 100000+ id (common in
 * practice: many trackers are built from copies of the same Cardigann indexer template, which bakes
 * in the same custom ids). Grouping per indexer instead means each indexer's own ids are only ever
 * compared against its own other categories, so an accidental id collision between two unrelated
 * indexers can no longer merge them.
 *
 * Within a single indexer's own category list, merge-by-id is still applied (same logic as
 * [buildStandardCategoryGroups]) as a defensive measure in case that one indexer's own response
 * happens to repeat an id - not expected in practice, but cheap to keep correct.
 *
 * Indexers with zero site-specific categories are omitted entirely (not returned as an
 * empty-categories entry) - see [CategorySelectionSection] KDoc, "no site-specific categories at
 * all" and "no site-specific categories for *this* indexer" should both just not show up, rather
 * than rendering an empty group.
 *
 * Sorted by indexer name - see docs/prowlarr-route-and-category-grouping-plan.md section 4.2 for
 * why this is plain Unicode-codepoint ordering (correct for Latin names, not true pinyin order for
 * Chinese ones) rather than a locale-aware collator.
 */
internal fun buildSiteSpecificGroups(indexers: List<ProwlarrIndexer>): List<IndexerCategoryGroup> =
    indexers.mapNotNull { indexer ->
        val categories = indexer.capabilities?.categories ?: return@mapNotNull null

        val byId = linkedMapOf<Int, CategoryGroup>()
        for (category in categories) {
            if (category.id < SITE_SPECIFIC_CATEGORY_ID_THRESHOLD) {
                continue
            }
            val existing = byId[category.id]
            byId[category.id] = if (existing == null) {
                CategoryGroup(category.id, category.name, category.subCategories)
            } else {
                existing.copy(subCategories = (existing.subCategories + category.subCategories).distinctBy { it.id })
            }
        }

        if (byId.isEmpty()) {
            null
        } else {
            IndexerCategoryGroup(indexer.id, indexer.name, byId.values.sortedBy { it.id })
        }
    }.sortedBy { it.indexerName }

// Torznab/Newznab spec reserves ids >= 100000 for indexer-specific ("site-specific") categories,
// precisely so they don't collide with the ~30 standard categories in 1000-8999 (confirmed against
// the spec, not guessed - see https://torznab.github.io/spec-1.3-draft and Sonarr's Torznab-indexer
// wiki page). Used purely as a presentation split (see CategorySelectionSection KDoc) - the actual
// id sent to search() is unaffected either way.
internal const val SITE_SPECIFIC_CATEGORY_ID_THRESHOLD = 100_000

/**
 * Collapsible category multi-select (see docs/prowlarr-p1-search-ui-and-tabs-plan.md, section 2.2,
 * and [buildStandardCategoryGroups] for why the grouping itself deviates from that doc). Two-level: each
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
 * intentionally-separate ids. [buildStandardCategoryGroups]' KDoc already called this out as a known
 * "same name, different id" quirk, but seeing it on a real device made clear a flat list wasn't
 * good enough - it needed the section split, not just a code comment.
 *
 * The site-specific section is further grouped per indexer via [IndexerCategoryGroupRow]
 * (docs/prowlarr-route-and-category-grouping-plan.md section 4) - see [buildSiteSpecificGroups] for
 * why this is a *data*-layer split, not just a display-layer one. Each indexer's own group has its
 * own collapse state ([expandedIndexerGroupIds]/[onToggleIndexerGroupExpanded]); which individual
 * category *within* a group has its subcategories expanded still shares [expandedGroupIds]/
 * [onToggleGroupExpanded] with the standard section (see [siteSpecificExpandKey] for how that stays
 * collision-free without a third expand-state list).
 */
@Composable
internal fun CategorySelectionSection(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    standardGroups: List<CategoryGroup>,
    siteSpecificIndexerGroups: List<IndexerCategoryGroup>,
    expandedGroupIds: List<Int>,
    expandedIndexerGroupIds: List<Int>,
    onToggleGroupExpanded: (Int) -> Unit,
    onToggleIndexerGroupExpanded: (Int) -> Unit,
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
            if (standardGroups.isEmpty() && siteSpecificIndexerGroups.isEmpty()) {
                Text(
                    text = stringResource(Res.string.prowlarr_search_categories_all),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Only bother labeling sections when there's actually a second one to
                    // distinguish from - a lone "Standard" header with nothing else on screen would
                    // just be noise.
                    val showSectionLabels = standardGroups.isNotEmpty() && siteSpecificIndexerGroups.isNotEmpty()

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

                    if (siteSpecificIndexerGroups.isNotEmpty()) {
                        if (showSectionLabels) {
                            CategorySectionLabel(
                                text = stringResource(Res.string.prowlarr_search_categories_site_specific),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        siteSpecificIndexerGroups.forEach { indexerGroup ->
                            IndexerCategoryGroupRow(
                                indexerName = indexerGroup.indexerName,
                                isExpanded = indexerGroup.indexerId in expandedIndexerGroupIds,
                                onToggleExpanded = { onToggleIndexerGroupExpanded(indexerGroup.indexerId) },
                            ) {
                                indexerGroup.categories.forEach { group ->
                                    val expandKey = siteSpecificExpandKey(indexerGroup.indexerId, group.id)
                                    CategoryGroupRow(
                                        group = group,
                                        isExpanded = expandKey in expandedGroupIds,
                                        onToggleExpanded = { onToggleGroupExpanded(expandKey) },
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
    }
}

/**
 * Encodes ([indexerId], [categoryId]) into one negative int so the site-specific section can reuse
 * [CategorySelectionSection]'s existing [expandedGroupIds][CategorySelectionSection]/
 * `onToggleGroupExpanded` pair (a single `List<Int>`) as its per-category expand-state key, instead
 * of adding a third expand-state parameter - see plan doc section 4.3, "展开状态的 key 需要注意一个
 * 细节": since categories are now grouped per indexer ([buildSiteSpecificGroups]) rather than merged
 * globally by id, two different indexers can each have their own, unrelated category at e.g. id
 * 100001, and using the raw id as the expand key would wrongly mark both "expanded" together.
 *
 * Collision-free by construction, not by chance: negative results can never collide with a standard
 * group's key (always its raw, non-negative category id), and two different (indexerId, categoryId)
 * pairs only collide if `categoryId >= 1_000_000` - true of every real Torznab id seen so far,
 * standard or site-specific (see plan doc section 4.2).
 */
private fun siteSpecificExpandKey(indexerId: Int, categoryId: Int): Int = -(indexerId * 1_000_000 + categoryId)

@Composable
internal fun CategorySectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}

/**
 * Collapsible header for one indexer's own site-specific [CategoryGroup] list - see
 * [buildSiteSpecificGroups] for why site-specific categories are grouped per indexer instead of
 * merged globally, and [CategorySelectionSection] for how this fits into the overall picker. The
 * header itself isn't selectable (it's a grouping container, not a category) - only expand/collapse
 * toggles [content], which the caller fills with one [CategoryGroupRow] per category in that
 * indexer's own group.
 */
@Composable
internal fun IndexerCategoryGroupRow(
    indexerName: String,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded() }
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = indexerName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 4.dp)
                    .rotate(rotation),
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content,
            )
        }
    }
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
