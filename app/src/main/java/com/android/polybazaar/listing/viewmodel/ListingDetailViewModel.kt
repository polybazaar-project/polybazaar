// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingPendingDetails
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.ListingRules
import com.android.polybazaar.profile.model.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the listing detail screen (#22).
 *
 * @property listing the listing shown, or null until it is loaded.
 * @property lender the profile of the listing's owner, or null while it loads, after a failure, or
 *   when the owner has no profile document.
 * @property pendingDetails the design fields no listing stores yet. Always empty for now, so the
 *   screen shows its "to be confirmed" text for them.
 * @property canContactLender whether "Message" and "Request booking" are enabled, from
 *   [ListingRules.canContactLender]. False until both the listing and the viewer are known.
 * @property isLoading true while the listing is being loaded.
 * @property error why the listing could not be loaded, or null.
 * @property lenderError why the lender's profile could not be loaded, or null. The listing is still
 *   shown without it.
 */
data class ListingDetailUiState(
    val listing: Listing? = null,
    val lender: User? = null,
    val pendingDetails: ListingPendingDetails = ListingPendingDetails(),
    val canContactLender: Boolean = false,
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val lenderError: AppError? = null,
)

/**
 * Whether the actions are enabled for [viewer] on [listing]. An unknown viewer gets false: without
 * them the screen cannot tell whether they are verified or own the listing.
 */
internal fun contactAllowed(viewer: User?, listing: Listing): Boolean =
    viewer != null && ListingRules.canContactLender(viewer, listing)

/** Loads one listing, the viewer and the lender's profile for the listing detail screen. */
class ListingDetailViewModel(
    private val listingId: String,
    private val listingRepository: ListingRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ListingDetailUiState())
  val uiState: StateFlow<ListingDetailUiState> = _uiState.asStateFlow()

  private var lenderObservation: Job? = null

  /** The viewer once read, kept for the rest of the screen's life (see [showListing]). */
  private var knownViewer: User? = null

  init {
    loadListing()
  }

  /**
   * Loads the listing and, until it is known, the viewer, then starts observing the lender, for
   * example again after a failure (Retry). Ignored while a load is already running. On failure the
   * listing already shown is kept, so a reload while offline does not empty the screen.
   */
  fun loadListing() {
    // Two loads at once could finish in any order, and the older answer would overwrite the newer.
    if (_uiState.value.isLoading) return
    _uiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      try {
        listingRepository
            .getListing(listingId)
            .onSuccess { listing -> showListing(listing) }
            .onFailure { exception ->
              // Cancellation means the screen was closed: it is not an error to show.
              if (exception is CancellationException) throw exception
              _uiState.update { it.copy(error = exception.toAppError()) }
            }
      } finally {
        // Also on cancellation, otherwise the guard above would block every later load.
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  private suspend fun showListing(listing: Listing) {
    // Read once and kept: an offline reload would otherwise lose a viewer it already knew and
    // disable the actions, and every getCurrentUser() also writes the viewer's profile document.
    // Read again only while unknown, so a reload after a failed read can still enable them.
    val viewer = knownViewer ?: currentViewer()
    knownViewer = viewer
    _uiState.update {
      it.copy(listing = listing, canContactLender = contactAllowed(viewer, listing))
    }
    listing.ownerUid?.let { observeLender(it) }
  }

  /**
   * The signed-in user, or null when it cannot be read, for example offline before the username is
   * cached. Not shown as an error: the listing can still be read, only the actions stay disabled.
   */
  private suspend fun currentViewer(): User? =
      try {
        authRepository.getCurrentUser()
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        // Only for the log: toAppError() logs exceptions it does not recognise, such as a bug in
        // getCurrentUser(), and stays quiet for expected ones such as a network failure.
        exception.toAppError()
        null
      }

  /** Keeps [ListingDetailUiState.lender] up to date with the owner's profile. */
  private fun observeLender(ownerUid: String) {
    // A listing's owner never changes, so a reload keeps the running observation instead of
    // starting a second one. A failed observation has ended, so a reload restarts it.
    if (lenderObservation?.isActive == true) return
    _uiState.update { it.copy(lenderError = null) }
    lenderObservation = viewModelScope.launch {
      profileRepository
          .observeProfile(ownerUid)
          // catch never sees a cancellation, so closing the screen is not shown as an error.
          .catch { exception -> _uiState.update { it.copy(lenderError = exception.toAppError()) } }
          .collect { lender -> _uiState.update { it.copy(lender = lender) } }
    }
  }
}
