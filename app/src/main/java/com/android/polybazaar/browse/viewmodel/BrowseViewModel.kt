// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.browse.viewmodel

import androidx.lifecycle.ViewModel

data class BrowseListing(
    val id: String,
    val title: String,
    val category: String,
    val distance: String,
    val price: String,
)

data class BrowseUiState(
    val listings: List<BrowseListing> = emptyList(),
    val categories: List<String> = emptyList(),
)

/** Placeholder for browse state and listing retrieval. */
class BrowseViewModel : ViewModel()
