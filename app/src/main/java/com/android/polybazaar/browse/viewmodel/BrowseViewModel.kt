// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.browse.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    val query: String = "",
    val selectedCategory: String? = null,
) {
  val filteredListings: List<BrowseListing>
    get() = listings.filter { listing ->
      (selectedCategory == null || listing.category == selectedCategory) &&
          listing.title.contains(query, ignoreCase = true)
    }
}

class BrowseViewModel(initialUiState: BrowseUiState = BrowseUiState()) : ViewModel() {

  private val _uiState = MutableStateFlow(initialUiState)
  val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

  fun updateQuery(query: String) {
    _uiState.update { it.copy(query = query) }
  }

  fun selectCategory(category: String) {
    _uiState.update { it.copy(selectedCategory = category) }
  }
}
