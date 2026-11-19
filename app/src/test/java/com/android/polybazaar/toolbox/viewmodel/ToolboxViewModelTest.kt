// Co-authored-by: Claude Code (Claude Opus 5.5)
// Co-authored-by: Copilot GPT-5.6 Terra
package com.android.polybazaar.toolbox.viewmodel

import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.FakeBookingRepository
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.model.ServiceListing
import com.android.polybazaar.toolbox.model.ToolboxListingStatus
import java.net.UnknownHostException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToolboxViewModelTest {

  private val myListings =
      listOf(
          ItemListing(
              ListingCategory.ITEM_TOOLS,
              8.0,
              "Bosch drill set",
              "With bits",
              120.0,
              listingId = "drill",
          ),
          ServiceListing(
              ListingCategory.SERVICE_TUTORING,
              25.0,
              "Math tutoring",
              "One hour",
              listingId = "math",
          ),
      )

  private val repository = FakeListingRepository(myListings = myListings)
  private val bookings = FakeBookingRepository()

  private val today = LocalDate.of(2026, 11, 19)
  private val drillToday = Booking("drill", "renter", "lender", setOf(today))

  @Before
  fun setUp() {
    // init loads the listings on Dispatchers.Main; run it before each test's assertions.
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun init_loadsMyListings() {
    val viewModel = ToolboxViewModel(repository, bookings)

    val state = viewModel.uiState.value
    assertEquals(myListings, state.listings)
    assertFalse(state.isLoading)
    assertNull(state.error)
    assertEquals(1, repository.getMyListingsCalls)
    // Browse's query is not used for the Toolbox.
    assertEquals(0, repository.getAllListingsCalls)
  }

  @Test
  fun init_withNoListings_showsAnEmptyToolboxWithoutError() {
    repository.myListings = emptyList()

    val viewModel = ToolboxViewModel(repository, bookings)

    assertEquals(emptyList<Listing>(), viewModel.uiState.value.listings)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun loadListings_isLoadingOnlyWhileTheRepositoryAnswers() {
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate

    val viewModel = ToolboxViewModel(repository, bookings)
    assertTrue(viewModel.uiState.value.isLoading)
    assertEquals(emptyList<Listing>(), viewModel.uiState.value.listings)

    gate.complete(Unit)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(myListings, viewModel.uiState.value.listings)
  }

  @Test
  fun loadListings_whileALoadIsRunning_isIgnored() {
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate
    val viewModel = ToolboxViewModel(repository, bookings)

    viewModel.loadListings()
    viewModel.loadListings()

    assertEquals(1, repository.getMyListingsCalls)
    gate.complete(Unit)
    assertEquals(myListings, viewModel.uiState.value.listings)
  }

  @Test
  fun loadListings_onFailure_setsTheErrorAndStopsLoading() {
    repository.failure = UnknownHostException("offline")

    val viewModel = ToolboxViewModel(repository, bookings)

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals(AppError.NETWORK, state.error)
    assertEquals(emptyList<Listing>(), state.listings)
  }

  @Test
  fun loadListings_onFailure_keepsTheListingsAlreadyShown() {
    val viewModel = ToolboxViewModel(repository, bookings)
    repository.failure = UnknownHostException("offline")

    viewModel.loadListings()

    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)
    assertEquals(myListings, viewModel.uiState.value.listings)
  }

  @Test
  fun loadListings_retryAfterFailure_clearsTheErrorAndShowsTheListings() {
    repository.failure = UnknownHostException("offline")
    val viewModel = ToolboxViewModel(repository, bookings)
    assertEquals(AppError.NETWORK, viewModel.uiState.value.error)

    val gate = CompletableDeferred<Unit>()
    repository.gate = gate
    repository.failure = null
    viewModel.loadListings()
    // The old error goes away as soon as the retry starts.
    assertNull(viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.isLoading)

    gate.complete(Unit)
    assertNull(viewModel.uiState.value.error)
    assertEquals(myListings, viewModel.uiState.value.listings)
    assertEquals(2, repository.getMyListingsCalls)
  }

  @Test
  fun loadListings_afterANewListing_showsIt() {
    val viewModel = ToolboxViewModel(repository, bookings)
    val newListing = ItemListing(ListingCategory.ITEM_BOOKS, 3.0, "Novel", "", listingId = "novel")
    repository.myListings = myListings + newListing

    viewModel.loadListings()

    assertEquals(myListings + newListing, viewModel.uiState.value.listings)
  }

  @Test
  fun loadListings_whenCancelled_reportsNoErrorAndAllowsTheNextLoad() {
    repository.failure = CancellationException("left the screen")
    val viewModel = ToolboxViewModel(repository, bookings)
    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)

    repository.failure = null
    viewModel.loadListings()

    assertEquals(myListings, viewModel.uiState.value.listings)
    assertEquals(2, repository.getMyListingsCalls)
  }

  @Test
  fun init_selectsNoTypeAndShowsAllListings() {
    val viewModel = ToolboxViewModel(repository, bookings)

    assertNull(viewModel.uiState.value.selectedType)
    assertEquals(myListings, viewModel.uiState.value.filteredListings)
  }

  @Test
  fun selectType_item_showsOnlyItems() {
    val viewModel = ToolboxViewModel(repository, bookings)

    viewModel.selectType(ListingType.ITEM)

    assertEquals(ListingType.ITEM, viewModel.uiState.value.selectedType)
    assertEquals(listOf(myListings[0]), viewModel.uiState.value.filteredListings)
    // The filter does not drop the other listings from the state.
    assertEquals(myListings, viewModel.uiState.value.listings)
  }

  @Test
  fun selectType_service_showsOnlyServices() {
    val viewModel = ToolboxViewModel(repository, bookings)

    viewModel.selectType(ListingType.SERVICE)

    assertEquals(ListingType.SERVICE, viewModel.uiState.value.selectedType)
    assertEquals(listOf(myListings[1]), viewModel.uiState.value.filteredListings)
  }

  @Test
  fun selectType_null_showsAllListings() {
    val viewModel = ToolboxViewModel(repository, bookings)
    viewModel.selectType(ListingType.ITEM)

    viewModel.selectType(null)

    assertNull(viewModel.uiState.value.selectedType)
    assertEquals(myListings, viewModel.uiState.value.filteredListings)
  }

  @Test
  fun selectType_theSelectedTypeAgain_keepsIt() {
    val viewModel = ToolboxViewModel(repository, bookings)
    viewModel.selectType(ListingType.ITEM)

    viewModel.selectType(ListingType.ITEM)

    assertEquals(ListingType.ITEM, viewModel.uiState.value.selectedType)
    assertEquals(listOf(myListings[0]), viewModel.uiState.value.filteredListings)
  }

  @Test
  fun selectType_theOtherType_switchesDirectly() {
    val viewModel = ToolboxViewModel(repository, bookings)
    viewModel.selectType(ListingType.ITEM)

    viewModel.selectType(ListingType.SERVICE)

    assertEquals(ListingType.SERVICE, viewModel.uiState.value.selectedType)
    assertEquals(listOf(myListings[1]), viewModel.uiState.value.filteredListings)
  }

  @Test
  fun selectType_withNoListingOfThatType_filtersToAnEmptyList() {
    repository.myListings = listOf(myListings[0])
    val viewModel = ToolboxViewModel(repository, bookings)

    viewModel.selectType(ListingType.SERVICE)

    assertEquals(emptyList<Listing>(), viewModel.uiState.value.filteredListings)
    assertEquals(listOf(myListings[0]), viewModel.uiState.value.listings)
  }

  @Test
  fun loadListings_afterANewListing_keepsTheFilter() {
    val viewModel = ToolboxViewModel(repository, bookings)
    viewModel.selectType(ListingType.ITEM)
    val newItem = ItemListing(ListingCategory.ITEM_BOOKS, 3.0, "Novel", "", listingId = "novel")
    val newService =
        ServiceListing(ListingCategory.SERVICE_TUTORING, 20.0, "Physics", "", listingId = "phys")
    repository.myListings = myListings + newItem + newService

    viewModel.loadListings()

    assertEquals(ListingType.ITEM, viewModel.uiState.value.selectedType)
    assertEquals(listOf(myListings[0], newItem), viewModel.uiState.value.filteredListings)
  }

  @Test
  fun loadListings_retryAfterFailure_keepsTheFilter() {
    val viewModel = ToolboxViewModel(repository, bookings)
    viewModel.selectType(ListingType.SERVICE)
    repository.failure = UnknownHostException("offline")
    viewModel.loadListings()
    assertEquals(ListingType.SERVICE, viewModel.uiState.value.selectedType)

    repository.failure = null
    viewModel.loadListings()

    assertNull(viewModel.uiState.value.error)
    assertEquals(ListingType.SERVICE, viewModel.uiState.value.selectedType)
    assertEquals(listOf(myListings[1]), viewModel.uiState.value.filteredListings)
  }

  @Test
  fun init_loadsTheLenderBookings_soEachListingHasItsStatus() {
    bookings.lenderBookings = listOf(drillToday)

    val state = ToolboxViewModel(repository, bookings).uiState.value

    assertEquals(1, bookings.lenderLoadCount)
    assertEquals(ToolboxListingStatus.BookedToday, state.statusOf(myListings[0], today))
    assertEquals(ToolboxListingStatus.Available, state.statusOf(myListings[1], today))
  }

  @Test
  fun loadListings_staysLoadingUntilTheBookingsArrive() {
    val gate = CompletableDeferred<Unit>()
    bookings.pendingLenderLoad = gate
    bookings.lenderBookings = listOf(drillToday)

    val viewModel = ToolboxViewModel(repository, bookings)
    assertTrue(viewModel.uiState.value.isLoading)
    // The listings already show; their status is unknown until the bookings arrive.
    assertEquals(myListings, viewModel.uiState.value.listings)
    assertNull(viewModel.uiState.value.statusOf(myListings[0], today))

    gate.complete(Unit)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(
        ToolboxListingStatus.BookedToday,
        viewModel.uiState.value.statusOf(myListings[0], today),
    )
  }

  @Test
  fun bookingsFailing_showsTheListingsWithoutStatusAndNoError() {
    bookings.lenderLoadFailure = UnknownHostException("offline")
    val paused = (myListings[0] as ItemListing).copy(isPaused = true)
    repository.myListings = listOf(paused, myListings[1])

    val state = ToolboxViewModel(repository, bookings).uiState.value

    assertNull(state.error)
    assertEquals(listOf(paused, myListings[1]), state.listings)
    // Paused needs no bookings; Available would be a guess.
    assertEquals(ToolboxListingStatus.Paused, state.statusOf(paused, today))
    assertNull(state.statusOf(myListings[1], today))
  }

  @Test
  fun bookingsFailingOnAReload_dropsTheStatusesAlreadyShown() {
    bookings.lenderBookings = listOf(drillToday)
    val viewModel = ToolboxViewModel(repository, bookings)
    assertEquals(
        ToolboxListingStatus.BookedToday,
        viewModel.uiState.value.statusOf(myListings[0], today),
    )
    // Cancelled meanwhile, but the reload fails: Booked today would now be wrong.
    bookings.lenderBookings = emptyList()
    bookings.lenderLoadFailure = UnknownHostException("offline")

    viewModel.loadListings()

    val state = viewModel.uiState.value
    assertNull(state.bookings)
    assertNull(state.statusOf(myListings[0], today))
    assertNull(state.statusOf(myListings[1], today))
    // The listings stay, with no error: only their status is unknown.
    assertEquals(myListings, state.listings)
    assertNull(state.error)
  }

  @Test
  fun bookingsLoadingAgainAfterAFailedReload_showTheStatusesAgain() {
    bookings.lenderLoadFailure = UnknownHostException("offline")
    val viewModel = ToolboxViewModel(repository, bookings)
    bookings.lenderLoadFailure = null
    bookings.lenderBookings = listOf(drillToday)

    viewModel.loadListings()

    assertEquals(
        ToolboxListingStatus.BookedToday,
        viewModel.uiState.value.statusOf(myListings[0], today),
    )
  }

  @Test
  fun loadListings_reloadsTheBookings_soABookingForTodayShows() {
    val viewModel = ToolboxViewModel(repository, bookings)
    assertEquals(
        ToolboxListingStatus.Available,
        viewModel.uiState.value.statusOf(myListings[0], today),
    )
    bookings.lenderBookings = listOf(drillToday)

    viewModel.loadListings()

    assertEquals(2, bookings.lenderLoadCount)
    assertEquals(
        ToolboxListingStatus.BookedToday,
        viewModel.uiState.value.statusOf(myListings[0], today),
    )
  }
}
