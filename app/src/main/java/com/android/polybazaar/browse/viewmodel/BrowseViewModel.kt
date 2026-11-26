// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.browse.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.attempt
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.model.formatChf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property category the listing's category, which also gives its type (item or service).
 * @property photoPath Storage path of the listing's first photo, null when it has none.
 */
data class BrowseListing(
    val id: String,
    val title: String,
    val category: ListingCategory,
    val distance: String? = null,
    val price: String,
    val photoPath: String? = null,
)

/**
 * What the renter narrowed Browse down to. A listing is shown when it matches all of them.
 *
 * @property query text the listing's title must contain, ignoring case. Empty matches every title.
 * @property type the type listings must have, or null for both items and services.
 * @property category the category listings must be in, or null for any. When [type] is set, it is
 *   one of that type's categories.
 */
data class BrowseFilters(
    val query: String = "",
    val type: ListingType? = null,
    val category: ListingCategory? = null,
) {
  fun matches(listing: BrowseListing): Boolean =
      (type == null || listing.category.type == type) &&
          (category == null || listing.category == category) &&
          listing.title.contains(query, ignoreCase = true)
}

/**
 * @property photos the cards' photos downloaded so far, by Storage path. A photo still downloading,
 *   or whose download failed (offline, for example), is missing, and its card shows a placeholder.
 */
data class BrowseUiState(
    val listings: List<BrowseListing> = emptyList(),
    val filters: BrowseFilters = BrowseFilters(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val photos: Map<String, ByteArray> = emptyMap(),
) {
  /**
   * The categories to offer as chips: those with at least one listing, of the selected type when
   * one is. Ordered by name, items before services for the same name, so the two "Other" categories
   * sit side by side.
   */
  val categories: List<ListingCategory>
    get() =
        listings
            .map { it.category }
            .filter { filters.type == null || it.type == filters.type }
            .distinct()
            .sortedWith(compareBy({ it.displayName }, { it.type != ListingType.ITEM }))

  val filteredListings: List<BrowseListing>
    get() = listings.filter(filters::matches)
}

/**
 * Maps a listing to its Browse card, or returns null for a listing that has not been saved yet.
 *
 * The price is formatted by [formatChf]; the card adds the per-day suffix. There is no location
 * data yet, so the distance is left out.
 */
internal fun Listing.toBrowseListing(): BrowseListing? {
  val id = listingId ?: return null
  return BrowseListing(
      id = id,
      title = title,
      category = category,
      price = formatChf(price),
      photoPath = imagePaths.firstOrNull(),
  )
}

/**
 * Holds the Browse screen: the listings other users offer, with the renter's [BrowseFilters].
 * [currentUid] is the signed-in user, whose own listings are left out because they cannot rent from
 * themselves.
 */
class BrowseViewModel(
    private val listingRepository: ListingRepository,
    private val currentUid: String,
) : ViewModel() {

  private val _uiState = MutableStateFlow(BrowseUiState())
  val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

  /** The photos being downloaded, so that no photo is downloaded twice at once. */
  private val photosDownloading = mutableSetOf<String>()

  init {
    loadListings()
  }

  /**
   * Loads every listing from the repository except the signed-in user's own, which they manage from
   * the Toolbox. On failure the listings already shown are kept, so a reload while offline does not
   * empty the screen. Each card's photo is then downloaded in the background.
   */
  fun loadListings() {
    _uiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      try {
        listingRepository
            .getAllListings()
            .onSuccess { listings ->
              val cards =
                  listings.filter { it.ownerUid != currentUid }.mapNotNull { it.toBrowseListing() }
              val photoPaths = cards.mapNotNull { it.photoPath }.toSet()
              _uiState.update {
                it.copy(
                    listings = cards,
                    // A category with no listing left has no chip, so could not be unselected.
                    filters =
                        it.filters.copy(
                            category =
                                it.filters.category?.takeIf { category ->
                                  cards.any { card -> card.category == category }
                                }
                        ),
                    // A photo no card shows any more is dropped with its card.
                    photos = it.photos.filterKeys { path -> path in photoPaths },
                )
              }
              photoPaths.forEach { downloadPhoto(it) }
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

  /**
   * Downloads the photo at [path] into [BrowseUiState.photos], in its own coroutine so the cards
   * show without waiting for it. It is downloaded again even if it is already shown: an edit can
   * put a new photo at the same path, and the old one stays shown until the new one arrives. A
   * photo still downloading is skipped. A failure leaves the placeholder without an error, and the
   * photo is tried again on the next load.
   */
  private fun downloadPhoto(path: String) {
    if (!photosDownloading.add(path)) return
    viewModelScope.launch {
      try {
        attempt { listingRepository.loadListingImage(path) }
            .onSuccess { photo ->
              _uiState.update { state ->
                // Dropped if a reload removed its card while it was downloading.
                if (state.listings.none { it.photoPath == path }) state
                else state.copy(photos = state.photos + (path to photo))
              }
            }
      } finally {
        photosDownloading.remove(path)
      }
    }
  }

  fun updateQuery(query: String) {
    _uiState.update { it.copy(filters = it.filters.copy(query = query)) }
  }

  /**
   * Shows only listings of [type], or both items and services for null. A selected category of the
   * other type is cleared, since its chip is no longer shown.
   */
  fun selectType(type: ListingType?) {
    _uiState.update {
      val category =
          it.filters.category?.takeIf { category -> type == null || category.type == type }
      it.copy(filters = it.filters.copy(type = type, category = category))
    }
  }

  /** Selects [category], or clears it when it is already selected. */
  fun selectCategory(category: ListingCategory) {
    _uiState.update {
      val selected = if (it.filters.category == category) null else category
      it.copy(filters = it.filters.copy(category = selected))
    }
  }
}
