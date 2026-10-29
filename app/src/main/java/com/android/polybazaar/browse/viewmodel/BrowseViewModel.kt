// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.browse.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRepository
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val isLoading: Boolean = false,
    val error: AppError? = null,
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

class BrowseViewModel(private val listingRepository: ListingRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(BrowseUiState())
  val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

  init {
    loadListings()
  }

  /**
   * Loads every listing from the repository. On failure the listings already shown are kept, so a
   * reload while offline does not empty the screen.
   */
  fun loadListings() {
    _uiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      try {
        listingRepository
            .getAllListings()
            .onSuccess { listings ->
              val cards = listings.mapNotNull { it.toBrowseListing() }
              val categories = cards.map { it.category }.distinct().sorted()
              _uiState.update {
                it.copy(
                    listings = cards,
                    categories = categories,
                    // A category with no listing left cannot be unselected from the screen.
                    selectedCategory =
                        it.selectedCategory?.takeIf { category -> category in categories },
                )
              }
            }
            .onFailure { exception ->
              if (exception is CancellationException) throw exception
              _uiState.update { it.copy(error = exception.toAppError()) }
            }
      } finally {
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  fun updateQuery(query: String) {
    _uiState.update { it.copy(query = query) }
  }

  fun selectCategory(category: String) {
    _uiState.update {
      it.copy(selectedCategory = if (it.selectedCategory == category) null else category)
    }
  }
}
