// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.toolbox.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingRepository
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.toolbox.model.ToolboxListingStatus
import com.android.polybazaar.toolbox.model.toolboxListingStatus
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the Toolbox, the part of the profile that shows what the user is responsible for (#20).
 *
 * Only the user's own listings for now; the items they rent will be added once rentals exist.
 *
 * @property listings the signed-in user's listings, in the order the repository returns them.
 * @property isLoading true while the listings are being loaded.
 * @property error why the last load failed, or null.
 * @property selectedType the type the listings are filtered by, or null to show them all.
 * @property bookings the bookings of all the user's listings, or null until they are first loaded
 *   and after the last load of them failed: the listings then show only whether they are paused,
 *   since bookings from an earlier load may be out of date.
 */
data class ToolboxUiState(
    val listings: List<Listing> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val selectedType: ListingType? = null,
    val bookings: List<Booking>? = null,
) {
  /**
   * The status of [listing] on [today], or null when it depends on [bookings] that are not loaded:
   * a paused listing is known to be paused without them.
   */
  fun statusOf(listing: Listing, today: LocalDate): ToolboxListingStatus? =
      when {
        listing.isPaused -> ToolboxListingStatus.Paused
        bookings == null -> null
        else -> toolboxListingStatus(listing, bookings, today)
      }

  /** The [listings] of the [selectedType], or all of them when no type is selected. */
  val filteredListings: List<Listing>
    get() = if (selectedType == null) listings else listings.filter { it.type == selectedType }
}

/**
 * Loads the signed-in user's listings for the Toolbox, with the bookings that give their status.
 */
class ToolboxViewModel(
    private val listingRepository: ListingRepository,
    private val bookingRepository: BookingRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ToolboxUiState())
  val uiState: StateFlow<ToolboxUiState> = _uiState.asStateFlow()

  init {
    loadListings()
  }

  /** Shows only the listings of [type], or all of them when [type] is null. */
  fun selectType(type: ListingType?) {
    _uiState.update { it.copy(selectedType = type) }
  }

  /**
   * Loads the user's listings and their bookings, for example again after a failure (Retry) or
   * after creating one. Ignored while a load is already running. On failure the listings already
   * shown are kept, so a reload while offline does not empty the Toolbox, and the error says they
   * may be out of date. A failure of the bookings shows no error, as the listings are still worth
   * showing, so the bookings are dropped instead: a status from an earlier load would look current.
   */
  fun loadListings() {
    // Two loads at once could finish in any order, and the older answer would overwrite the newer.
    if (_uiState.value.isLoading) return
    _uiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      // At the same time as the listings, so the statuses do not arrive a round trip later.
      val bookings = async { loadBookings() }
      try {
        listingRepository
            .getMyListings()
            .onSuccess { listings -> _uiState.update { it.copy(listings = listings) } }
            .onFailure { exception ->
              // Cancellation means the Toolbox was closed: it is not an error to show.
              if (exception is CancellationException) throw exception
              _uiState.update { it.copy(error = exception.toAppError()) }
            }
        val loaded = bookings.await()
        _uiState.update { it.copy(bookings = loaded) }
      } finally {
        // Also on cancellation, otherwise the guard above would block every later load.
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  /**
   * The user's bookings as a lender, or null when they cannot be read. The failure is logged, as
   * nothing on screen reports it: a malformed booking would otherwise hide every status silently.
   */
  private suspend fun loadBookings(): List<Booking>? =
      try {
        bookingRepository.getBookingsForLender()
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        Log.w(TAG, "Could not load the bookings, so the listings show no status", exception)
        null
      }

  private companion object {
    const val TAG = "ToolboxViewModel"
  }
}
