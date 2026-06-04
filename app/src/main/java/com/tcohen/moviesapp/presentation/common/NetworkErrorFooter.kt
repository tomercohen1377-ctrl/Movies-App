package com.tcohen.moviesapp.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme

@Preview(showBackground = true)
@Composable
private fun NetworkErrorFooterPreview() {
    MoviesAppTheme {
        NetworkErrorFooter(onRetry = {})
    }
}

/**
 * Inline footer shown at the bottom of the movie list when paging hits a
 * `NetworkUnavailableException` — i.e. the user scrolled past the cached pages
 * while offline. The list above remains scrollable.
 */
@Composable
fun NetworkErrorFooter(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "No internet — showing cached results.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        OutlinedButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}
