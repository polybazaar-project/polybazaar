// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.viewmodel

import com.android.polybazaar.connectivity.model.FakeConnectivityRepository
import com.android.polybazaar.listing.model.FakeListingRepository
import com.android.polybazaar.listing.model.FakePendingListingQueue
import com.android.polybazaar.listing.model.ItemListing
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingCategory
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.ServiceListing
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListingDraftPublisherViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private val today = LocalDate.of(2026, 11, 22)
  private val ada = "uid-ada"
  private val bob = "uid-bob"

  private val queue = FakePendingListingQueue(ownerUid = ada)
  private val fake = FakeListingRepository(ownerUid = ada)
  private val listings = RecordingListingRepository(fake)
  private val connectivity = FakeConnectivityRepository(online = true)

  private fun drill(title: String = "Bosch drill set", dates: Set<LocalDate> = emptySet()) =
      ItemListing(
          ListingCategory.ITEM_TOOLS,
          8.0,
          title,
          "With bits",
          120.0,
          availableDates = dates,
      )

  private val tutoring =
      ServiceListing(ListingCategory.SERVICE_TUTORING, 25.0, "Math tutoring", "One-hour session")

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun publisher(uid: String? = ada) =
      ListingDraftPublisherViewModel(queue, listings, connectivity) { today }
          .also { it.setSignedInUser(uid) }

  private fun pendingIds() = queue.pending.value.map { it.id }

  private fun attemptedIds() = listings.attempts.map { it.first }

  @Test
  fun appStartingOnlineWithAUser_publishesTheDrafts_removesThem_andTellsHowMany() = runTest {
    queue.enqueue("d1", drill())
    queue.enqueue("d2", tutoring)

    val viewModel = publisher()
    advanceUntilIdle()

    assertEquals(listOf("d1", "d2"), fake.publishedListingIds)
    assertEquals(listOf("d1", "d2"), fake.allListings.map { it.listingId })
    assertEquals(listOf("Bosch drill set", "Math tutoring"), fake.allListings.map { it.title })
    assertTrue(queue.pending.value.isEmpty())
    assertEquals(DraftPublishNotice(published = 2, failed = 0), viewModel.notice.value)
  }

  @Test
  fun offline_publishesNothing_untilTheConnectionReturns() = runTest {
    connectivity.isOnline.value = false
    queue.enqueue("d1", drill())

    val viewModel = publisher()
    advanceUntilIdle()

    assertTrue(listings.attempts.isEmpty())
    assertEquals(listOf("d1"), pendingIds())
    assertNull(viewModel.notice.value)

    connectivity.isOnline.value = true
    advanceUntilIdle()

    assertEquals(listOf("d1"), fake.publishedListingIds)
    assertTrue(queue.pending.value.isEmpty())
    assertEquals(DraftPublishNotice(1, 0), viewModel.notice.value)
  }

  @Test
  fun withoutASignedInUser_publishesNothing_untilOneSignsIn() = runTest {
    queue.enqueue("d1", drill())

    val viewModel = publisher(uid = null)
    advanceUntilIdle()

    assertTrue(listings.attempts.isEmpty())
    assertEquals(listOf("d1"), pendingIds())
    assertNull(viewModel.notice.value)

    viewModel.setSignedInUser(ada)
    advanceUntilIdle()

    assertEquals(listOf("d1"), fake.publishedListingIds)
    assertEquals(DraftPublishNotice(1, 0), viewModel.notice.value)
  }

  @Test
  fun onlyTheSignedInUsersDrafts_arePublished_andTheOthersStayOnTheDevice() = runTest {
    queue.enqueue("ada-1", drill())
    queue.ownerUid = bob
    queue.enqueue("bob-1", tutoring)

    val viewModel = publisher(uid = ada)
    advanceUntilIdle()

    assertEquals(listOf("ada-1"), attemptedIds())
    assertEquals(listOf("bob-1"), pendingIds())
    assertEquals(DraftPublishNotice(1, 0), viewModel.notice.value)

    // Bob signs in on the same device: only then are his drafts published.
    viewModel.clearNotice()
    viewModel.setSignedInUser(bob)
    advanceUntilIdle()

    assertEquals(listOf("ada-1", "bob-1"), attemptedIds())
    assertTrue(queue.pending.value.isEmpty())
    assertEquals(DraftPublishNotice(1, 0), viewModel.notice.value)
  }

  @Test
  fun withOnlyOtherAccountsDrafts_triesNothing_andTellsNothing() = runTest {
    queue.ownerUid = bob
    queue.enqueue("bob-1", tutoring)

    val viewModel = publisher(uid = ada)
    advanceUntilIdle()

    assertTrue(listings.attempts.isEmpty())
    assertEquals(listOf("bob-1"), pendingIds())
    assertNull(viewModel.notice.value)
  }

  @Test
  fun failure_keepsTheDraft_andTellsHowManyFailed() = runTest {
    queue.enqueue("d1", drill())
    queue.enqueue("d2", tutoring)
    fake.failure = IOException("Network down")

    val viewModel = publisher()
    advanceUntilIdle()

    assertEquals(listOf("d1", "d2"), attemptedIds())
    assertEquals(listOf("d1", "d2"), pendingIds())
    assertTrue(fake.allListings.isEmpty())
    assertEquals(DraftPublishNotice(published = 0, failed = 2), viewModel.notice.value)
  }

  @Test
  fun mixedOutcomes_removeOnlyThePublishedDrafts_andCountBoth() = runTest {
    queue.enqueue("d1", drill("First drill"))
    queue.enqueue("d2", drill("Second drill"))
    queue.enqueue("d3", tutoring)
    listings.failingIds += "d2"

    val viewModel = publisher()
    advanceUntilIdle()

    assertEquals(listOf("d1", "d2", "d3"), attemptedIds())
    assertEquals(listOf("d2"), pendingIds())
    assertEquals(listOf("d1", "d3"), fake.publishedListingIds)
    assertEquals(DraftPublishNotice(published = 2, failed = 1), viewModel.notice.value)
  }

  @Test
  fun failedDraft_isNotTriedAgainWhileStillOnline_evenWhenTheDraftsChange() = runTest {
    queue.enqueue("d1", drill())
    listings.failingIds += "d1"
    val viewModel = publisher()
    advanceUntilIdle()
    viewModel.clearNotice()

    // Another draft saved while online: only it is tried, the failed one is not retried in a loop.
    queue.enqueue("d2", tutoring)
    advanceUntilIdle()

    assertEquals(listOf("d1", "d2"), attemptedIds())
    assertEquals(listOf("d1"), pendingIds())
    assertEquals(DraftPublishNotice(published = 1, failed = 0), viewModel.notice.value)

    // The same user signing in again changes nothing either.
    viewModel.clearNotice()
    viewModel.setSignedInUser(ada)
    advanceUntilIdle()

    assertEquals(listOf("d1", "d2"), attemptedIds())
    assertNull(viewModel.notice.value)
  }

  @Test
  fun failedDraft_isTriedAgainWhenTheConnectionReturns() = runTest {
    queue.enqueue("d1", drill())
    listings.failingIds += "d1"
    val viewModel = publisher()
    advanceUntilIdle()
    assertEquals(DraftPublishNotice(0, 1), viewModel.notice.value)

    listings.failingIds.clear()
    connectivity.isOnline.value = false
    advanceUntilIdle()
    assertEquals(listOf("d1"), attemptedIds())

    connectivity.isOnline.value = true
    advanceUntilIdle()

    assertEquals(listOf("d1", "d1"), attemptedIds())
    assertEquals(listOf("d1"), fake.publishedListingIds)
    assertTrue(queue.pending.value.isEmpty())
    assertEquals(DraftPublishNotice(1, 0), viewModel.notice.value)
  }

  @Test
  fun failedDraft_isTriedAgainOnTheNextSignIn() = runTest {
    queue.enqueue("d1", drill())
    listings.failingIds += "d1"
    val viewModel = publisher()
    advanceUntilIdle()

    listings.failingIds.clear()
    viewModel.setSignedInUser(null)
    advanceUntilIdle()
    assertEquals(listOf("d1"), attemptedIds())

    viewModel.setSignedInUser(ada)
    advanceUntilIdle()

    assertEquals(listOf("d1", "d1"), attemptedIds())
    assertTrue(queue.pending.value.isEmpty())
    assertEquals(DraftPublishNotice(1, 0), viewModel.notice.value)
  }

  @Test
  fun failedDraft_isTriedAgainOnTheNextAppStart() = runTest {
    queue.enqueue("d1", drill())
    listings.failingIds += "d1"
    publisher()
    advanceUntilIdle()

    listings.failingIds.clear()
    val restarted = publisher()
    advanceUntilIdle()

    assertTrue(queue.pending.value.isEmpty())
    assertEquals(listOf("d1"), fake.publishedListingIds)
    assertEquals(DraftPublishNotice(1, 0), restarted.notice.value)
  }

  @Test
  fun draftSavedWhileOnline_isPublished() = runTest {
    val viewModel = publisher()
    advanceUntilIdle()
    assertNull(viewModel.notice.value)

    queue.enqueue("d1", drill())
    advanceUntilIdle()

    assertEquals(listOf("d1"), fake.publishedListingIds)
    assertTrue(queue.pending.value.isEmpty())
    assertEquals(DraftPublishNotice(1, 0), viewModel.notice.value)
  }

  @Test
  fun removingThePublishedDrafts_startsNoOtherPass_andTellsOnce() = runTest {
    queue.enqueue("d1", drill())
    val viewModel = publisher()
    val notices = mutableListOf<DraftPublishNotice?>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      viewModel.notice.toList(notices)
    }

    // Removing d1 makes the queue emit again, without any draft left to try.
    advanceUntilIdle()

    assertEquals(listOf("d1"), attemptedIds())
    assertEquals(listOf(null, DraftPublishNotice(1, 0)), notices)
  }

  @Test
  fun draftSavedDuringAPass_isPublishedOnceAfterIt_andNoDraftIsPublishedTwice() = runTest {
    queue.enqueue("d1", drill())
    val gate = CompletableDeferred<Unit>()
    fake.gate = gate
    val viewModel = publisher()
    advanceUntilIdle()
    assertEquals(listOf("d1"), attemptedIds())

    queue.enqueue("d2", tutoring)
    advanceUntilIdle()
    // The second draft waits for the pass in progress.
    assertEquals(listOf("d1"), attemptedIds())

    gate.complete(Unit)
    advanceUntilIdle()

    assertEquals(listOf("d1", "d2"), attemptedIds())
    assertEquals(listOf("d1", "d2"), fake.publishedListingIds)
    assertEquals(listOf("d1", "d2"), fake.allListings.map { it.listingId })
    assertTrue(queue.pending.value.isEmpty())
    assertEquals(DraftPublishNotice(1, 0), viewModel.notice.value)
  }

  @Test
  fun draftAlreadyPublishedUnderItsId_isNotPublishedAgain_andLeavesTheDevice() = runTest {
    // Published earlier, but the device never got the answer.
    fake.allListings = listOf(drill().saved("d1", emptyList(), ada, isPaused = false))
    queue.enqueue("d1", drill())

    val viewModel = publisher()
    advanceUntilIdle()

    assertEquals(listOf("d1"), fake.allListings.map { it.listingId })
    assertTrue(queue.pending.value.isEmpty())
    assertEquals(DraftPublishNotice(1, 0), viewModel.notice.value)
  }

  @Test
  fun goingOfflineDuringAPass_cancelsIt_keepsTheDraft_andRetriesWhenBackOnline() = runTest {
    queue.enqueue("d1", drill())
    val gate = CompletableDeferred<Unit>()
    fake.gate = gate
    val viewModel = publisher()
    advanceUntilIdle()

    connectivity.isOnline.value = false
    advanceUntilIdle()
    gate.complete(Unit)
    advanceUntilIdle()

    assertTrue(fake.publishedListingIds.isEmpty())
    assertEquals(listOf("d1"), pendingIds())
    assertNull(viewModel.notice.value)

    connectivity.isOnline.value = true
    advanceUntilIdle()

    assertEquals(listOf("d1"), fake.publishedListingIds)
    assertTrue(queue.pending.value.isEmpty())
    assertEquals(DraftPublishNotice(1, 0), viewModel.notice.value)
  }

  @Test
  fun signingOutDuringAPass_cancelsIt_andKeepsTheDraft() = runTest {
    queue.enqueue("d1", drill())
    val gate = CompletableDeferred<Unit>()
    fake.gate = gate
    val viewModel = publisher()
    advanceUntilIdle()

    viewModel.setSignedInUser(null)
    advanceUntilIdle()
    gate.complete(Unit)
    advanceUntilIdle()

    assertTrue(fake.publishedListingIds.isEmpty())
    assertEquals(listOf("d1"), pendingIds())
    assertNull(viewModel.notice.value)
  }

  @Test
  fun pastAvailableDays_areLeftOutOfThePublishedListing() = runTest {
    val yesterday = today.minusDays(1)
    val later = today.plusDays(3)
    queue.enqueue("d1", drill(dates = setOf(yesterday, today, later)))
    queue.enqueue(
        "d2",
        tutoring.copy(availableDates = setOf(yesterday, later)),
    )

    publisher()
    advanceUntilIdle()

    assertEquals(
        listOf(setOf(today, later), setOf(later)),
        listings.attempts.map { it.second.availableDates },
    )
    assertEquals(
        listOf(ItemListing::class, ServiceListing::class),
        listings.attempts.map { it.second::class },
    )
  }

  @Test
  fun clearNotice_forgetsTheLastOutcome() = runTest {
    queue.enqueue("d1", drill())
    val viewModel = publisher()
    advanceUntilIdle()
    assertEquals(DraftPublishNotice(1, 0), viewModel.notice.value)

    viewModel.clearNotice()

    assertNull(viewModel.notice.value)
  }

  /**
   * Records every [publishListing] attempt, failing those whose id is in [failingIds], and
   * otherwise answers as [fake] does.
   */
  private class RecordingListingRepository(private val fake: FakeListingRepository) :
      ListingRepository by fake {
    val attempts = mutableListOf<Pair<String, Listing>>()
    val failingIds = mutableSetOf<String>()

    override suspend fun publishListing(listingId: String, listing: Listing) {
      attempts += listingId to listing
      if (listingId in failingIds) throw IOException("Could not publish $listingId")
      fake.publishListing(listingId, listing)
    }
  }
}
