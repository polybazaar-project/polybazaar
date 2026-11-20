// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.R
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.booking.model.Booking
import com.android.polybazaar.booking.model.BookingStatus
import com.android.polybazaar.booking.model.FakeBookingRepository
import com.android.polybazaar.booking.viewmodel.ReceivedRequestsUiState
import com.android.polybazaar.booking.viewmodel.ReceivedRequestsViewModel
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.profile.model.FakeProfileRepository
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceivedRequestsScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private fun days(range: IntRange): Set<LocalDate> =
      range.mapTo(mutableSetOf()) { LocalDate.of(2026, 11, it) }

  private fun booking(
      id: String,
      renter: String,
      days: Set<LocalDate>,
      status: BookingStatus = BookingStatus.REQUESTED,
  ) = Booking("drill", renter, "lender", days, status, id)

  // Bob's request shares the 21st with Ann's.
  private val ann = booking("ann", "uid-ann", days(20..21))
  private val bob = booking("bob", "uid-bob", days(21..22))
  private val cy = booking("cy", "uid-cy", days(28..28), BookingStatus.CONFIRMED)

  private val loaded =
      ReceivedRequestsUiState(
          requests = listOf(ann, bob),
          accepted = listOf(cy),
          renterNames = mapOf("uid-ann" to "ann_m", "uid-cy" to "cy"),
          offeredDays = days(17..30),
          hasLoaded = true,
      )

  private fun setContent(
      uiState: ReceivedRequestsUiState,
      actions: ReceivedRequestsActions = ReceivedRequestsActions(),
  ) {
    composeTestRule.setContent { ReceivedRequestsContent(uiState = uiState, actions = actions) }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  /** The text [text] inside the card tagged [tag]. */
  private fun card(tag: String, text: String) =
      composeTestRule.onNode(
          hasText(text) and hasAnyAncestor(hasTestTag(tag)),
          useUnmergedTree = true,
      )

  @Test
  fun beforeTheFirstLoad_showsTheIndicator() {
    setContent(ReceivedRequestsUiState(isLoading = true))

    node(ReceivedRequestsScreenTags.LOADING_INDICATOR).assertIsDisplayed()
  }

  @Test
  fun aLoadError_isShown_withRetry() {
    var retries = 0
    setContent(
        ReceivedRequestsUiState(error = AppError.NETWORK),
        ReceivedRequestsActions(onRetry = { retries++ }),
    )

    node(ReceivedRequestsScreenTags.ERROR_MESSAGE)
        .assertTextEquals(context.getString(R.string.error_network))
    node(ReceivedRequestsScreenTags.RETRY_BUTTON).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun noBookings_saysSo() {
    setContent(ReceivedRequestsUiState(hasLoaded = true))

    node(ReceivedRequestsScreenTags.EMPTY_MESSAGE).assertIsDisplayed()
  }

  @Test
  fun showsEachRequest_withItsRenterAndDays() {
    setContent(loaded)

    val annCard = ReceivedRequestsScreenTags.request("ann")
    card(annCard, "ann_m").assertIsDisplayed()
    card(annCard, "20–21 Nov").assertIsDisplayed()
    card(annCard, "2 days").assertIsDisplayed()
    // Bob has no name read: shown as a renter.
    card(ReceivedRequestsScreenTags.request("bob"), "A renter").assertIsDisplayed()
    val cyCard = ReceivedRequestsScreenTags.accepted("cy")
    card(cyCard, "cy").assertIsDisplayed()
    card(cyCard, "28 Nov").assertIsDisplayed()
    node(ReceivedRequestsScreenTags.acceptButton("cy")).assertDoesNotExist()
    node(ReceivedRequestsScreenTags.EMPTY_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun accept_asksFirst_sayingWhatItCancels() {
    val accepted = mutableListOf<Booking>()
    setContent(loaded, ReceivedRequestsActions(onAccept = { accepted += it }))

    node(ReceivedRequestsScreenTags.acceptButton("ann")).performClick()

    node(ReceivedRequestsScreenTags.ACCEPT_DIALOG_TEXT)
        .assertTextEquals(
            "ann_m gets 20–21 Nov. No one else can request these days. " +
                "1 other request for these days will be cancelled."
        )
    assertEquals(emptyList<Booking>(), accepted)
    node(ReceivedRequestsScreenTags.ACCEPT_CONFIRM).performClick()

    node(ReceivedRequestsScreenTags.ACCEPT_DIALOG).assertDoesNotExist()
    assertEquals(listOf(ann), accepted)
  }

  @Test
  fun cancellingTheDialog_acceptsNothing() {
    val accepted = mutableListOf<Booking>()
    setContent(
        loaded.copy(requests = listOf(ann)),
        ReceivedRequestsActions(onAccept = { accepted += it }),
    )

    node(ReceivedRequestsScreenTags.acceptButton("ann")).performClick()
    node(ReceivedRequestsScreenTags.ACCEPT_DIALOG_TEXT)
        .assertTextEquals("ann_m gets 20–21 Nov. No one else can request these days.")
    node(ReceivedRequestsScreenTags.ACCEPT_CANCEL).performClick()

    node(ReceivedRequestsScreenTags.ACCEPT_DIALOG).assertDoesNotExist()
    assertEquals(emptyList<Booking>(), accepted)
  }

  @Test
  fun whileAnAcceptRuns_theButtonsAreDisabled_andAFailureIsShown() {
    setContent(loaded.copy(acceptingId = "ann", acceptError = AppError.NETWORK))

    node(ReceivedRequestsScreenTags.acceptButton("ann")).assertIsNotEnabled()
    node(ReceivedRequestsScreenTags.acceptButton("bob")).assertIsNotEnabled()
    node(ReceivedRequestsScreenTags.ACCEPT_ERROR)
        .assertTextEquals(context.getString(R.string.error_network))
  }

  @Test
  fun aRequestForADayNoLongerOffered_hasNoAccept() {
    setContent(loaded.copy(offeredDays = days(17..21)))

    node(ReceivedRequestsScreenTags.request("bob")).assertIsDisplayed()
    node(ReceivedRequestsScreenTags.acceptButton("bob")).assertDoesNotExist()
    node(ReceivedRequestsScreenTags.acceptButton("ann")).assertIsEnabled()
  }

  @Test
  fun withTheViewModel_acceptingMovesTheRequest_andRemovesTheOverlappingOne() {
    val bookings = FakeBookingRepository().apply { listingBookings = listOf(ann, bob) }
    val listings =
        FakeListingRepository(
            listOf(
                ItemListing(
                    category = ListingCategory.ITEM_TOOLS,
                    price = 8.0,
                    title = "Drill",
                    description = "Cordless drill.",
                    refundValue = 120.0,
                    listingId = "drill",
                    ownerUid = "lender",
                    availableDates = days(17..30),
                )
            )
        )
    val profiles =
        FakeProfileRepository(
            mapOf("uid-ann" to User(uid = "uid-ann", email = "", username = "ann_m"))
        )
    val viewModel =
        ReceivedRequestsViewModel("drill", bookings, listings, profiles, LocalDate.of(2026, 11, 18))
    composeTestRule.setContent { ReceivedRequestsScreen(viewModel = viewModel, onBack = {}) }

    node(ReceivedRequestsScreenTags.acceptButton("ann")).assertIsEnabled().performClick()
    node(ReceivedRequestsScreenTags.ACCEPT_CONFIRM).performClick()

    node(ReceivedRequestsScreenTags.accepted("ann")).assertIsDisplayed()
    node(ReceivedRequestsScreenTags.request("ann")).assertDoesNotExist()
    node(ReceivedRequestsScreenTags.request("bob")).assertDoesNotExist()
    assertEquals(BookingStatus.CANCELLED, bookings.listingBookings.single { it.id == "bob" }.status)
  }
}
