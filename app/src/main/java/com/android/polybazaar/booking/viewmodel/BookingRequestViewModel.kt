// Co-authored-by: Claude Code (Claude Fable 5.2)
package com.android.polybazaar.booking.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.DateRun
import com.android.polybazaar.booking.model.groupIntoRuns
import com.android.polybazaar.booking.model.upcomingDays
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.ListingRules
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [selection] cut down to one period, since a booking runs from a first day to a last day. Days in
 * a row are kept as they are. Otherwise the period holding a day that is not in [previous] wins, so
 * that choosing a day away from the period chosen so far starts a new one; when no day was added (a
 * day in the middle was unselected), the earliest period is kept.
 */
internal fun singlePeriod(previous: Set<LocalDate>, selection: Set<LocalDate>): Set<LocalDate> {
  val runs = groupIntoRuns(selection)
  if (runs.size <= 1) return selection
  fun DateRun.holds(day: LocalDate) = !day.isBefore(start) && !day.isAfter(end)
  val added = selection - previous
  val kept = runs.firstOrNull { run -> added.any { run.holds(it) } } ?: runs.first()
  return selection.filterTo(mutableSetOf()) { kept.holds(it) }
}

/**
 * State of the screen where a renter requests a listing for some days (#26).
 *
 * @property listing the listing to request, or null until it is loaded.
 * @property renterUid uid of the signed-in user when they may request [listing]
 *   ([ListingRules.canContactLender]): null for the owner, an unverified or unknown user, and a
 *   paused listing.
 * @property selectableDays the days the lender offers that are not in the past.
 * @property selection the days the renter chose: one period, all among [selectableDays].
 * @property today the day earlier days cannot be requested from, for the calendar to use as well.
 * @property isLoading true while the listing is being loaded.
 * @property error why the listing could not be loaded, or null.
 */
data class BookingRequestUiState(
    val listing: Listing? = null,
    val renterUid: String? = null,
    val selectableDays: Set<LocalDate> = emptySet(),
    val selection: Set<LocalDate> = emptySet(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
) {
  /** Whether the signed-in user may request [listing]. */
  val canRequest: Boolean
    get() = renterUid != null

  /** The period chosen, or null when no day is chosen. */
  val period: DateRun?
    get() = groupIntoRuns(selection).singleOrNull()

  /** How many days [period] lasts, both ends included; 0 without a period. */
  val dayCount: Int
    get() = period?.let { ChronoUnit.DAYS.between(it.start, it.end).toInt() + 1 } ?: 0

  /** The longest the item can be kept, in days, or null for a service or an item without limit. */
  val maxRentalDays: Int?
    get() = (listing as? ItemListing)?.maxRentalDays

  /** Whether [period] is longer than [maxRentalDays], so that it cannot be requested. */
  val isTooLong: Boolean
    get() = maxRentalDays?.let { dayCount > it } ?: false

  /**
   * The price of [period]: the listing's price for each of its days. Null without a period that can
   * be requested, so that no total is shown for one that [isTooLong].
   */
  val total: Double?
    get() = listing?.takeIf { period != null && !isTooLong }?.let { it.price * dayCount }

  /**
   * The request to send, or null while it cannot be sent: no listing, a user who may not request
   * it, no day chosen or a period that [isTooLong].
   */
  val booking: Booking?
    get() {
      val listingId = listing?.listingId
      val lenderUid = listing?.ownerUid
      val chosen = period
      if (listingId == null || lenderUid == null || renterUid == null) return null
      if (chosen == null || isTooLong) return null
      return Booking(
          listingId = listingId,
          renterUid = renterUid,
          lenderUid = lenderUid,
          startDate = chosen.start,
          endDate = chosen.end,
      )
    }
}

/**
 * Loads one listing and holds the days the renter chooses on it. Sending the request is left to
 * [BookingViewModel], which takes [BookingRequestUiState.booking].
 *
 * @param today the day the screen is opened on: earlier days cannot be requested.
 */
class BookingRequestViewModel(
    private val listingId: String,
    private val listingRepository: ListingRepository,
    private val authRepository: AuthRepository,
    private val today: LocalDate = LocalDate.now(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(BookingRequestUiState(today = today))
  val uiState: StateFlow<BookingRequestUiState> = _uiState.asStateFlow()

  /** The signed-in user once read, kept for the rest of the screen's life (see [showListing]). */
  private var knownViewer: User? = null

  init {
    loadListing()
  }

  /**
   * Loads the listing and the signed-in user, for example again after a failure (Retry). Ignored
   * while a load is already running. On failure the listing already shown is kept.
   */
  fun loadListing() {
    if (_uiState.value.isLoading) return
    _uiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      try {
        showListing(listingRepository.getListing(listingId).getOrThrow())
      } catch (exception: Exception) {
        // Cancellation means the screen was closed: it is not an error to show.
        if (exception is CancellationException) throw exception
        _uiState.update { it.copy(error = exception.toAppError()) }
      } finally {
        // Also on cancellation, otherwise the guard above would block every later load.
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  /**
   * Replaces the days chosen by [selection], leaving out any day the lender does not offer and
   * keeping one period (see [singlePeriod]).
   */
  fun updateSelection(selection: Set<LocalDate>) {
    _uiState.update {
      it.copy(selection = singlePeriod(it.selection, selection intersect it.selectableDays))
    }
  }

  private suspend fun showListing(listing: Listing) {
    // Read once and kept, as on the listing detail: every getCurrentUser() also writes the user's
    // profile document. Read again only while unknown, so a reload after a failed read can still
    // allow the request.
    val viewer = knownViewer ?: currentViewer()
    knownViewer = viewer
    val selectableDays = upcomingDays(listing.availableDates, today)
    _uiState.update {
      it.copy(
          listing = listing,
          renterUid = viewer?.takeIf { user -> ListingRules.canContactLender(user, listing) }?.uid,
          selectableDays = selectableDays,
          // A reload may find days the lender has taken back since, even in the middle of the
          // period.
          selection =
              (it.selection intersect selectableDays).let { left -> singlePeriod(left, left) },
      )
    }
  }

  /**
   * The signed-in user, or null when there is none or it cannot be read. Not shown as an error: the
   * listing is still shown, only the request stays disabled.
   */
  private suspend fun currentViewer(): User? =
      try {
        authRepository.getCurrentUser()
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        // Only for the log: toAppError() logs the exceptions it does not recognise.
        exception.toAppError()
        null
      }
}
