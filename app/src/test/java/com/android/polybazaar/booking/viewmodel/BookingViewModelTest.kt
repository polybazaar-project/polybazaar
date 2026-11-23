// Co-authored-by: Copilot (Auto)
// Co-authored-by: Claude Code (Claude Fable 5.2)
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.viewmodel

import androidx.lifecycle.ViewModelStore
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingStatus
import com.android.polybazaar.booking.model.FakeBookingRepository
import com.android.polybazaar.booking.model.ObservedBookings
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.profile.model.FakeProfileRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookingViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val booking =
      Booking(
          listingId = "listing",
          renterUid = "renter",
          lenderUid = "lender",
          days = setOf(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3)),
      )

  private lateinit var repository: FakeBookingRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeBookingRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun requestBooking_queuesAndExposesQueuedState() = runTest {
    val viewModel = BookingViewModel(repository)

    viewModel.requestBooking(booking)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(booking, repository.requestedBooking)
    assertEquals(BookingUiState(isQueued = true), viewModel.uiState.value)
  }

  @Test
  fun requestBooking_showsPendingAndIgnoresDuplicateRequest() = runTest {
    val gate = CompletableDeferred<Unit>()
    repository.pendingRequest = gate
    val viewModel = BookingViewModel(repository)

    viewModel.requestBooking(booking)
    testDispatcher.scheduler.runCurrent()
    viewModel.requestBooking(booking.copy(listingId = "other-listing"))

    assertTrue(viewModel.uiState.value.isSubmitting)
    assertEquals(1, repository.requestCount)

    gate.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isQueued)
    assertFalse(viewModel.uiState.value.isSubmitting)
  }

  @Test
  fun requestBooking_reportsFailureAfterLocalQueueAndAllowsRetry() = runTest {
    val viewModel = BookingViewModel(repository)

    viewModel.requestBooking(booking)
    testDispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value.isQueued)

    repository.writeFailure?.invoke(AppException(AppError.NETWORK))

    assertFalse(viewModel.uiState.value.isQueued)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)

    repository.writeFailure = null
    viewModel.requestBooking(booking)
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isQueued)
    assertEquals(null, viewModel.uiState.value.error)
  }

  @Test
  fun requestBooking_reportsWriteFailureBeforeRepositoryReturns() = runTest {
    repository.immediateWriteFailure = AppException(AppError.PERMISSION_DENIED)
    val viewModel = BookingViewModel(repository)

    viewModel.requestBooking(booking)
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isSubmitting)
    assertFalse(viewModel.uiState.value.isQueued)
    assertEquals(AppError.PERMISSION_DENIED, viewModel.uiState.value.error)
  }

  @Test
  fun requestBooking_exposesFailureAndAllowsRetry() = runTest {
    repository.failure = AppException(AppError.NETWORK)
    val viewModel = BookingViewModel(repository)

    viewModel.requestBooking(booking)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isSubmitting)

    repository.failure = null
    viewModel.requestBooking(booking)
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isQueued)
    assertEquals(null, viewModel.uiState.value.error)
  }

  @Test
  fun requestBooking_cancellationIsNotExposedAsAnAppError() = runTest {
    repository.failure = CancellationException("screen closed")
    val viewModel = BookingViewModel(repository)

    viewModel.requestBooking(booking)
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isSubmitting)
    assertFalse(viewModel.uiState.value.isQueued)
    assertEquals(null, viewModel.uiState.value.error)
    assertTrue(repository.requestJob?.isCancelled == true)
  }

  @Test
  fun loadRenterBookings_exposesLoadingAndResults() = runTest {
    val expected = listOf(booking.copy(id = "booking-id"))
    val gate = CompletableDeferred<Unit>()
    repository.renterBookings = expected
    repository.pendingLoad = gate
    val viewModel = BookingViewModel(repository)

    viewModel.loadRenterBookings()
    testDispatcher.scheduler.runCurrent()

    assertTrue(viewModel.uiState.value.isLoadingBookings)
    gate.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(expected, viewModel.uiState.value.bookings)
    assertFalse(viewModel.uiState.value.isLoadingBookings)
  }

  @Test
  fun confirmationActionsFollowRolesAndHandoverReturnOrder() {
    val confirmed = booking.copy(status = BookingStatus.CONFIRMED)
    val notified = confirmed.copy(handoverNotified = true)
    val handedOver = notified.copy(renterHandoverConfirmed = true)
    val returning = handedOver.copy(returnNotified = true)
    val completed = returning.copy(lenderReturnConfirmed = true)

    assertEquals(
        ConfirmationAction.NOTIFY_HANDOVER,
        confirmed.confirmationAction(BookingParty.LENDER),
    )
    assertEquals(null, confirmed.confirmationAction(BookingParty.RENTER))
    assertEquals(
        ConfirmationAction.CONFIRM_HANDOVER,
        notified.confirmationAction(BookingParty.RENTER),
    )
    assertEquals(null, notified.confirmationAction(BookingParty.LENDER))
    assertEquals(
        ConfirmationAction.NOTIFY_RETURN,
        handedOver.confirmationAction(BookingParty.RENTER),
    )
    assertEquals(null, handedOver.confirmationAction(BookingParty.LENDER))
    assertEquals(
        ConfirmationAction.CONFIRM_RETURN,
        returning.confirmationAction(BookingParty.LENDER),
    )
    assertEquals(null, returning.confirmationAction(BookingParty.RENTER))
    assertEquals(null, completed.confirmationAction(BookingParty.LENDER))
    assertEquals(null, booking.confirmationAction(BookingParty.LENDER))
  }

  @Test
  fun eachPartyQueuesTheMatchingConfirmationOperation() = runTest {
    val base = booking.copy(id = "a", status = BookingStatus.CONFIRMED)
    val stages =
        listOf(
            Triple(BookingParty.LENDER, base, ConfirmationAction.NOTIFY_HANDOVER),
            Triple(
                BookingParty.RENTER,
                base.copy(handoverNotified = true),
                ConfirmationAction.CONFIRM_HANDOVER,
            ),
            Triple(
                BookingParty.RENTER,
                base.copy(handoverNotified = true, renterHandoverConfirmed = true),
                ConfirmationAction.NOTIFY_RETURN,
            ),
            Triple(
                BookingParty.LENDER,
                base.copy(
                    handoverNotified = true,
                    renterHandoverConfirmed = true,
                    returnNotified = true,
                ),
                ConfirmationAction.CONFIRM_RETURN,
            ),
        )
    for ((party, current, action) in stages) {
      repository.renterBookings = listOf(current)
      repository.listingBookings = listOf(current)
      val viewModel = BookingViewModel(repository)
      if (party == BookingParty.RENTER) viewModel.observeRenterBookings()
      else viewModel.observeListingBookings("listing")
      testDispatcher.scheduler.runCurrent()
      viewModel.confirmBooking("a", action)
      testDispatcher.scheduler.runCurrent()
      viewModel.stopObservingBookings()
    }

    assertEquals(listOf("a"), repository.handoverNotifications)
    assertEquals(listOf("a"), repository.handoverConfirmations)
    assertEquals(listOf("a"), repository.returnNotifications)
    assertEquals(listOf("a"), repository.returnConfirmations)
  }

  @Test
  fun confirmationTracksPendingSnapshotsAndReportsRejectionForRetry() = runTest {
    val confirmed =
        booking.copy(id = "a", status = BookingStatus.CONFIRMED, handoverNotified = true)
    repository.renterBookings = listOf(confirmed)
    val viewModel = BookingViewModel(repository)
    viewModel.observeRenterBookings()
    testDispatcher.scheduler.runCurrent()

    viewModel.confirmBooking("a", ConfirmationAction.NOTIFY_RETURN)
    viewModel.confirmBooking("a", ConfirmationAction.CONFIRM_HANDOVER)
    viewModel.confirmBooking("a", ConfirmationAction.CONFIRM_HANDOVER)
    testDispatcher.scheduler.runCurrent()
    assertEquals(listOf("a"), repository.handoverConfirmations)
    assertTrue("a" in viewModel.uiState.value.submittingConfirmations)

    val optimistic = confirmed.copy(renterHandoverConfirmed = true)
    repository.bookingUpdates.emit(ObservedBookings(listOf(optimistic), setOf("a")))
    testDispatcher.scheduler.runCurrent()
    assertTrue("a" in viewModel.uiState.value.pendingBookingIds)
    assertTrue(viewModel.uiState.value.submittingConfirmations.isEmpty())
    viewModel.confirmBooking("a", ConfirmationAction.NOTIFY_RETURN)
    testDispatcher.scheduler.runCurrent()
    assertTrue(repository.returnNotifications.isEmpty())

    repository.confirmationWriteFailure?.invoke(AppException(AppError.PERMISSION_DENIED))
    repository.bookingUpdates.emit(ObservedBookings(listOf(confirmed)))
    testDispatcher.scheduler.runCurrent()
    assertEquals(
        ConfirmationFailure(ConfirmationAction.CONFIRM_HANDOVER, AppError.PERMISSION_DENIED),
        viewModel.uiState.value.confirmationFailures["a"],
    )
    viewModel.confirmBooking("a", ConfirmationAction.CONFIRM_HANDOVER)
    testDispatcher.scheduler.runCurrent()
    assertEquals(listOf("a", "a"), repository.handoverConfirmations)
    assertTrue(viewModel.uiState.value.confirmationFailures.isEmpty())
    viewModel.stopObservingBookings()
  }

  @Test
  fun observationUpdatesOtherPartyAndClearsPendingOnAcknowledgement() = runTest {
    val original = booking.copy(id = "a", status = BookingStatus.CONFIRMED)
    repository.renterBookings = listOf(original)
    val viewModel = BookingViewModel(repository)
    viewModel.observeRenterBookings()
    testDispatcher.scheduler.runCurrent()

    val changed = original.copy(handoverNotified = true)
    repository.bookingUpdates.emit(ObservedBookings(listOf(changed), setOf("a")))
    testDispatcher.scheduler.runCurrent()
    assertEquals(changed, viewModel.uiState.value.bookings.single())
    assertEquals(setOf("a"), viewModel.uiState.value.pendingBookingIds)
    repository.bookingUpdates.emit(ObservedBookings(listOf(changed)))
    testDispatcher.scheduler.runCurrent()
    assertTrue(viewModel.uiState.value.pendingBookingIds.isEmpty())
    viewModel.stopObservingBookings()
  }

  @Test
  fun bookingObservationCanRetryAfterALoadFailure() = runTest {
    val expected = booking.copy(id = "a")
    repository.renterBookings = listOf(expected)
    repository.loadFailure = AppException(AppError.NETWORK)
    val viewModel = BookingViewModel(repository)

    viewModel.observeRenterBookings()
    testDispatcher.scheduler.runCurrent()
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoadingBookings)

    repository.loadFailure = null
    viewModel.observeRenterBookings()
    testDispatcher.scheduler.runCurrent()
    assertEquals(listOf(expected), viewModel.uiState.value.bookings)
    assertEquals(null, viewModel.uiState.value.error)
    assertEquals(2, repository.loadCount)
    viewModel.stopObservingBookings()
  }

  @Test
  fun immediateConfirmationFailureCanBeRetried() = runTest {
    val handover = booking.copy(id = "a", status = BookingStatus.CONFIRMED, handoverNotified = true)
    repository.renterBookings = listOf(handover)
    repository.immediateConfirmationFailure = AppException(AppError.NETWORK)
    val viewModel = BookingViewModel(repository)
    viewModel.observeRenterBookings()
    testDispatcher.scheduler.runCurrent()

    viewModel.confirmBooking("a", ConfirmationAction.CONFIRM_HANDOVER)
    testDispatcher.scheduler.runCurrent()
    assertTrue(viewModel.uiState.value.submittingConfirmations.isEmpty())
    assertEquals(
        ConfirmationFailure(ConfirmationAction.CONFIRM_HANDOVER, AppError.NETWORK),
        viewModel.uiState.value.confirmationFailures["a"],
    )

    repository.immediateConfirmationFailure = null
    viewModel.confirmBooking("a", ConfirmationAction.CONFIRM_HANDOVER)
    testDispatcher.scheduler.runCurrent()
    assertEquals(listOf("a", "a"), repository.handoverConfirmations)
    assertTrue(viewModel.uiState.value.confirmationFailures.isEmpty())
    viewModel.stopObservingBookings()
  }

  @Test
  fun listingNamesAreCachedAndSnapshotsDoNotOverlapInFlightReads() = runTest {
    val first =
        ItemListing(
            category = ListingCategory.ITEM_TOOLS,
            price = 8.0,
            title = "Drill",
            description = "Cordless drill",
            refundValue = 100.0,
            listingId = "listing",
            ownerUid = "lender",
        )
    val second = first.copy(listingId = "second", title = "Saw")
    val gate = CompletableDeferred<Unit>()
    val listings = FakeListingRepository(listOf(first, second)).apply { this.gate = gate }
    val original = booking.copy(id = "a")
    repository.renterBookings = listOf(original)
    val viewModel = BookingViewModel(repository, listingRepository = listings)
    viewModel.observeRenterBookings()
    testDispatcher.scheduler.runCurrent()
    assertEquals(1, listings.getListingCalls)

    repository.bookingUpdates.emit(ObservedBookings(listOf(original), setOf("a")))
    testDispatcher.scheduler.runCurrent()
    val added = original.copy(id = "b", listingId = "second")
    repository.bookingUpdates.emit(ObservedBookings(listOf(original, added)))
    testDispatcher.scheduler.runCurrent()
    assertEquals(1, listings.getListingCalls)
    assertEquals(2, viewModel.uiState.value.bookings.size)

    gate.complete(Unit)
    testDispatcher.scheduler.runCurrent()
    assertEquals(2, listings.getListingCalls)
    assertEquals(
        mapOf("listing" to "Drill", "second" to "Saw"),
        viewModel.uiState.value.listingTitles,
    )
    repository.bookingUpdates.emit(ObservedBookings(listOf(original, added), setOf("a")))
    testDispatcher.scheduler.runCurrent()
    assertEquals(2, listings.getListingCalls)
    viewModel.stopObservingBookings()
  }

  @Test
  fun renterNamesAreCachedAndFailedNamesRetryOnReobservation() = runTest {
    val james = User(uid = "renter", email = "j@epfl.ch", username = "James")
    val other = User(uid = "other", email = "o@epfl.ch", username = "Olivia")
    val profiles = FakeProfileRepository(mapOf("renter" to james, "other" to other))
    val original = booking.copy(id = "a")
    repository.listingBookings = listOf(original)
    val viewModel = BookingViewModel(repository, profileRepository = profiles)
    viewModel.observeListingBookings("listing")
    testDispatcher.scheduler.runCurrent()
    assertEquals(mapOf("renter" to 1), profiles.lookups)

    repository.bookingUpdates.emit(ObservedBookings(listOf(original), setOf("a")))
    testDispatcher.scheduler.runCurrent()
    assertEquals(mapOf("renter" to 1), profiles.lookups)

    val added = original.copy(id = "b", renterUid = "other")
    profiles.failingUids += "other"
    repository.bookingUpdates.emit(ObservedBookings(listOf(original, added)))
    testDispatcher.scheduler.runCurrent()
    assertEquals(mapOf("renter" to 1, "other" to 1), profiles.lookups)
    repository.bookingUpdates.emit(ObservedBookings(listOf(original, added), setOf("b")))
    testDispatcher.scheduler.runCurrent()
    assertEquals(mapOf("renter" to 1, "other" to 1), profiles.lookups)

    profiles.failingUids.clear()
    repository.listingBookings = listOf(original, added)
    viewModel.observeListingBookings("listing")
    testDispatcher.scheduler.runCurrent()
    assertEquals(mapOf("renter" to 1, "other" to 2), profiles.lookups)
    assertEquals(
        mapOf("renter" to "James", "other" to "Olivia"),
        viewModel.uiState.value.renterNames,
    )
    viewModel.stopObservingBookings()
  }

  @Test
  fun confirmedRemoteUpdateClearsAnOutdatedFailure() = runTest {
    val original = booking.copy(id = "a", status = BookingStatus.CONFIRMED, handoverNotified = true)
    repository.renterBookings = listOf(original)
    val viewModel = BookingViewModel(repository)
    viewModel.observeRenterBookings()
    testDispatcher.scheduler.runCurrent()
    viewModel.confirmBooking("a", ConfirmationAction.CONFIRM_HANDOVER)
    testDispatcher.scheduler.runCurrent()
    repository.confirmationWriteFailure?.invoke(AppException(AppError.PERMISSION_DENIED))
    assertTrue("a" in viewModel.uiState.value.confirmationFailures)

    repository.bookingUpdates.emit(
        ObservedBookings(listOf(original.copy(renterHandoverConfirmed = true)))
    )
    testDispatcher.scheduler.runCurrent()
    assertTrue(viewModel.uiState.value.confirmationFailures.isEmpty())
    viewModel.stopObservingBookings()
  }

  @Test
  fun loadListingBookings_exposesResultsAndFailures() = runTest {
    val expected = listOf(booking.copy(id = "booking-id"))
    repository.listingBookings = expected
    val viewModel = BookingViewModel(repository)

    viewModel.loadListingBookings("listing")
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(expected, viewModel.uiState.value.bookings)

    repository.loadFailure = AppException(AppError.PERMISSION_DENIED)
    viewModel.loadListingBookings("listing")
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(AppError.PERMISSION_DENIED, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoadingBookings)
  }

  @Test
  fun loadRenterBookings_sortsByEarliestDayAndNamesListings() = runTest {
    val drill =
        ItemListing(
            category = ListingCategory.ITEM_TOOLS,
            price = 8.0,
            title = "Bosch drill set",
            description = "Cordless drill.",
            refundValue = 120.0,
            maxRentalDays = 3,
            listingId = "listing",
            ownerUid = "lender",
        )
    val later = booking.copy(id = "later", days = setOf(LocalDate.of(2026, 12, 20)))
    val earlier =
        booking.copy(id = "earlier", listingId = "gone", days = setOf(LocalDate.of(2026, 12, 2)))
    repository.renterBookings = listOf(later, earlier)
    val viewModel =
        BookingViewModel(repository, listingRepository = FakeListingRepository(listOf(drill)))

    viewModel.loadRenterBookings()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("earlier", "later"), viewModel.uiState.value.bookings.map { it.id })
    assertEquals(mapOf("listing" to "Bosch drill set"), viewModel.uiState.value.listingTitles)
  }

  @Test
  fun loadListingBookings_namesRentersAndSkipsUnreadableProfiles() = runTest {
    val james = User(uid = "renter", email = "j@epfl.ch", username = "James Richard")
    val profiles = FakeProfileRepository(mapOf("renter" to james)).apply { failingUids += "other" }
    repository.listingBookings =
        listOf(booking.copy(id = "a"), booking.copy(id = "b", renterUid = "other"))
    val viewModel = BookingViewModel(repository, profileRepository = profiles)

    viewModel.loadListingBookings("listing")
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(mapOf("renter" to "James Richard"), viewModel.uiState.value.renterNames)
    assertEquals(2, viewModel.uiState.value.bookings.size)
    assertEquals(null, viewModel.uiState.value.error)
  }

  @Test
  fun cancelBooking_remembersTheRefusedBookingForRetryUntilTheNextAction() = runTest {
    repository.renterBookings = listOf(booking.copy(id = "a"))
    val viewModel = BookingViewModel(repository)
    viewModel.loadRenterBookings()
    testDispatcher.scheduler.advanceUntilIdle()

    repository.cancelFailure = AppException(AppError.PERMISSION_DENIED)
    viewModel.cancelBooking("a")
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals("a", viewModel.uiState.value.failedCancelId)

    repository.cancelFailure = null
    viewModel.cancelBooking("a")
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(null, viewModel.uiState.value.failedCancelId)
    assertEquals(BookingStatus.CANCELLED, viewModel.uiState.value.bookings.single().status)
  }

  @Test
  fun loadRenterBookings_exposesFailures() = runTest {
    repository.loadFailure = AppException(AppError.NETWORK)
    val viewModel = BookingViewModel(repository)

    viewModel.loadRenterBookings()
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoadingBookings)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
  }

  @Test
  fun cancelBooking_showsCancelledOnceQueuedAndPreventsDuplicates() = runTest {
    repository.renterBookings = listOf(booking.copy(id = "booking-id"))
    repository.pendingCancel = CompletableDeferred()
    val viewModel = BookingViewModel(repository)
    viewModel.loadRenterBookings()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.runCurrent()
    viewModel.cancelBooking("booking-id")

    assertEquals(1, repository.cancellationCount)
    assertTrue("booking-id" in viewModel.uiState.value.cancellingBookingIds)

    repository.pendingCancel?.complete(Unit)
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value.cancellingBookingIds.isEmpty())
    assertEquals(BookingStatus.CANCELLED, viewModel.uiState.value.bookings.single().status)
  }

  @Test
  fun cancelBooking_restoresStatusAndExposesErrorWhenWriteIsRejectedLater() = runTest {
    repository.renterBookings = listOf(booking.copy(id = "booking-id"))
    val viewModel = BookingViewModel(repository)
    viewModel.loadRenterBookings()
    testDispatcher.scheduler.advanceUntilIdle()
    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(BookingStatus.CANCELLED, viewModel.uiState.value.bookings.single().status)

    repository.cancelWriteFailure?.invoke(AppException(AppError.PERMISSION_DENIED))

    assertEquals(BookingStatus.REQUESTED, viewModel.uiState.value.bookings.single().status)
    assertEquals(AppError.PERMISSION_DENIED, viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.cancellingBookingIds.isEmpty())

    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(2, repository.cancellationCount)
  }

  @Test
  fun cancelBooking_keepsStatusWhenWriteFailsBeforeReturning() = runTest {
    repository.renterBookings = listOf(booking.copy(id = "booking-id"))
    repository.immediateCancelWriteFailure = AppException(AppError.NETWORK)
    val viewModel = BookingViewModel(repository)
    viewModel.loadRenterBookings()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(BookingStatus.REQUESTED, viewModel.uiState.value.bookings.single().status)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.cancellingBookingIds.isEmpty())
  }

  @Test
  fun cancelBooking_exposesLateWriteFailureAfterViewModelIsCleared() = runTest {
    val viewModel = BookingViewModel(repository)
    val viewModelStore = ViewModelStore()
    viewModelStore.put("booking", viewModel)

    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()

    viewModelStore.clear()
    repository.cancelWriteFailure?.invoke(AppException(AppError.NETWORK))

    assertTrue(viewModel.uiState.value.cancellingBookingIds.isEmpty())
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
  }

  @Test
  fun loadRenterBookings_ignoresDuplicateLoadWhileLoading() = runTest {
    repository.pendingLoad = CompletableDeferred()
    val viewModel = BookingViewModel(repository)

    viewModel.loadRenterBookings()
    testDispatcher.scheduler.runCurrent()
    viewModel.loadRenterBookings()
    testDispatcher.scheduler.runCurrent()

    assertEquals(1, repository.loadCount)
  }

  @Test
  fun loadRenterBookings_cancellationIsNotExposedAsAnAppError() = runTest {
    repository.loadFailure = CancellationException("screen closed")
    val viewModel = BookingViewModel(repository)

    viewModel.loadRenterBookings()
    testDispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoadingBookings)
    assertEquals(null, viewModel.uiState.value.error)
  }

  @Test
  fun cancelBooking_ignoresAlreadyCancelledBooking() = runTest {
    repository.renterBookings =
        listOf(booking.copy(id = "booking-id", status = BookingStatus.CANCELLED))
    val viewModel = BookingViewModel(repository)
    viewModel.loadRenterBookings()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(0, repository.cancellationCount)
    assertTrue(viewModel.uiState.value.cancellingBookingIds.isEmpty())
  }

  @Test
  fun cancelBooking_onlyCancelsTheMatchingBooking() = runTest {
    val other = booking.copy(id = "other-id", status = BookingStatus.CONFIRMED)
    repository.renterBookings = listOf(booking.copy(id = "booking-id"), other)
    val viewModel = BookingViewModel(repository)
    viewModel.loadRenterBookings()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()

    val bookings = viewModel.uiState.value.bookings
    assertEquals(BookingStatus.CANCELLED, bookings.first { it.id == "booking-id" }.status)
    assertEquals(other, bookings.first { it.id == "other-id" })
  }

  @Test
  fun cancelBooking_exposesRepositoryFailureAndAllowsRetry() = runTest {
    repository.cancelFailure = AppException(AppError.PERMISSION_DENIED)
    val viewModel = BookingViewModel(repository)

    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(AppError.PERMISSION_DENIED, viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.cancellingBookingIds.isEmpty())

    repository.cancelFailure = null
    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(2, repository.cancellationCount)
    assertEquals(null, viewModel.uiState.value.error)
  }

  @Test
  fun cancelBooking_cancellationIsNotExposedAsAnAppError() = runTest {
    repository.cancelFailure = CancellationException("screen closed")
    val viewModel = BookingViewModel(repository)

    viewModel.cancelBooking("booking-id")
    testDispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value.cancellingBookingIds.isEmpty())
    assertEquals(null, viewModel.uiState.value.error)
  }
}
