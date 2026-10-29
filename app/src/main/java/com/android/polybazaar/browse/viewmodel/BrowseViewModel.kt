// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.browse.viewmodel

import androidx.lifecycle.ViewModel
import com.android.polybazaar.listing.model.Listing
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BrowseListing(
    val id: String,
    val title: String,
    val category: String,
    val distance: String? = null,
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

/**
 * Maps a listing to its Browse card, or returns null for a listing that has not been saved yet.
 *
 * Whole prices stay short (`8.0` → `"CHF 8"`, `8.5` → `"CHF 8.50"`) because the card adds the
 * per-day suffix. There is no location data yet, so the distance is left out.
 */
internal fun Listing.toBrowseListing(): BrowseListing? {
  val id = listingId ?: return null
  // Locale.ROOT keeps the decimal point, whatever the device language.
  val amount = String.format(Locale.ROOT, "%.2f", price).removeSuffix(".00")
  return BrowseListing(id = id, title = title, category = category, price = "CHF $amount")
}

class BrowseViewModel(initialUiState: BrowseUiState = BrowseUiState()) : ViewModel() {

  private val _uiState = MutableStateFlow(initialUiState)
  val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

  fun updateQuery(query: String) {
    _uiState.update { it.copy(query = query) }
  }

  fun selectCategory(category: String) {
    _uiState.update {
      it.copy(selectedCategory = if (it.selectedCategory == category) null else category)
    }
  }
}
