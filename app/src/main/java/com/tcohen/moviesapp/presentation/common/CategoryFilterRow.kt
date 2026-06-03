package com.tcohen.moviesapp.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.displayName

/**
 * Horizontally scrollable row of [FilterChip]s — one per [Category].
 *
 * The selected chip is visually distinguished (filled tint + checkmark icon).
 * The [LazyRow] is future-proof: adding more categories requires no layout changes.
 */
@Composable
fun CategoryFilterRow(
    selectedCategory: Category,
    onCategorySelected: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(Category.entries) { category ->
            FilterChip(
                selected = category == selectedCategory,
                onClick = { onCategorySelected(category) },
                label = { Text(text = category.displayName) }
            )
        }
    }
}
