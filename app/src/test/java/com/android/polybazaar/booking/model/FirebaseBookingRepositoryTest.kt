// Co-authored-by: Copilot (Auto)
// Co-authored-by: Copilot (GPT-5.6 Terra)
package com.android.polybazaar.booking.model

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FirebaseBookingRepositoryTest {
  private val firestore = mock<FirebaseFirestore>()
  private val auth = mock<FirebaseAuth>()
  private val collection = mock<CollectionReference>()
  private val document = mock<DocumentReference>()
  private val listingQuery = mock<Query>()
  private val lenderQuery = mock<Query>()
  private val snapshot = mock<QuerySnapshot>()
  private val stored = mock<DocumentSnapshot>()
  private val repository = FirebaseBookingRepository(firestore, auth)
  private val booking =
      Booking(
          listingId = "listing",
          renterUid = "caller-renter",
          lenderUid = "lender",
          startDate = LocalDate.of(2026, 12, 31),
          endDate = LocalDate.of(2027, 1, 2),
      )

  @Before
  fun setUp() {
    val user = mock<FirebaseUser>()
    whenever(user.uid).thenReturn("renter")
    whenever(auth.currentUser).thenReturn(user)
    whenever(firestore.collection("bookings")).thenReturn(collection)
    whenever(collection.document()).thenReturn(document)
    whenever(collection.whereEqualTo("listingId", "listing")).thenReturn(listingQuery)
    whenever(listingQuery.whereEqualTo("lenderUid", "lender")).thenReturn(lenderQuery)
  }

  @Test
  fun requestBookingUsesAuthenticatedRenterUidAndStoresEpochDays() = runTest {
    whenever(document.set(any<Map<String, Any>>())).thenReturn(Tasks.forResult(null))

    repository.requestBooking(booking)

    verify(document)
        .set(
            eq(
                mapOf(
                    "listingId" to "listing",
                    "renterUid" to "renter",
                    "lenderUid" to "lender",
                    "startDate" to booking.startDate.toEpochDay(),
                    "endDate" to booking.endDate.toEpochDay(),
                    "status" to "REQUESTED",
                )
            )
        )
  }

  @Test
  fun requestBookingReturnsWhileServerConfirmationIsPending() = runTest {
    val pendingWrite = TaskCompletionSource<Void>()
    whenever(document.set(any<Map<String, Any>>())).thenReturn(pendingWrite.task)

    withTimeout(1_000) { repository.requestBooking(booking) }

    assertTrue(!pendingWrite.task.isComplete)
    verify(document).set(any<Map<String, Any>>())
  }

  @Test
  fun requestBookingReportsLaterWriteFailures() = runTest {
    val pendingWrite = TaskCompletionSource<Void>()
    whenever(document.set(any<Map<String, Any>>())).thenReturn(pendingWrite.task)
    var reportedFailure: Exception? = null
    val repository = FirebaseBookingRepository(firestore, auth) { reportedFailure = it }

    repository.requestBooking(booking)
    val failure = IllegalStateException("Write rejected")
    pendingWrite.setException(failure)

    assertSame(failure, reportedFailure)
  }

  @Test
  fun requestBookingRejectsInvalidDatesAndStatus() = runTest {
    val reversed = booking.copy(endDate = booking.startDate.minusDays(1))
    val confirmed = booking.copy(status = BookingStatus.CONFIRMED)

    assertTrue(
        runCatching { repository.requestBooking(reversed) }.exceptionOrNull()
            is IllegalArgumentException
    )
    assertTrue(
        runCatching { repository.requestBooking(confirmed) }.exceptionOrNull()
            is IllegalArgumentException
    )
    verify(collection, never()).document()
  }

  @Test
  fun getBookingsQueriesLenderAndDecodesStoredFields() = runTest {
    val lender = mock<FirebaseUser>()
    whenever(lender.uid).thenReturn("lender")
    whenever(auth.currentUser).thenReturn(lender)
    whenever(lenderQuery.get()).thenReturn(Tasks.forResult(snapshot))
    whenever(snapshot.documents).thenReturn(listOf(stored))
    whenever(stored.getString("listingId")).thenReturn(booking.listingId)
    whenever(stored.getString("renterUid")).thenReturn(booking.renterUid)
    whenever(stored.getString("lenderUid")).thenReturn(booking.lenderUid)
    whenever(stored.getLong("startDate")).thenReturn(booking.startDate.toEpochDay())
    whenever(stored.getLong("endDate")).thenReturn(booking.endDate.toEpochDay())
    whenever(stored.getString("status")).thenReturn("CANCELLED")
    whenever(stored.id).thenReturn("booking-id")

    assertEquals(
        listOf(booking.copy(id = "booking-id", status = BookingStatus.CANCELLED)),
        repository.getBookingsForListing("listing"),
    )
    verify(listingQuery).whereEqualTo("lenderUid", "lender")
  }

  @Test
  fun getBookingsPropagatesMalformedBookingDocuments() = runTest {
    val lender = mock<FirebaseUser>()
    whenever(lender.uid).thenReturn("lender")
    whenever(auth.currentUser).thenReturn(lender)
    whenever(lenderQuery.get()).thenReturn(Tasks.forResult(snapshot))
    whenever(snapshot.documents).thenReturn(listOf(stored))
    assertTrue(
        runCatching { repository.getBookingsForListing("listing") }.exceptionOrNull()
            is IllegalStateException
    )
  }

  @Test
  fun getBookingsRejectsSignedOutUser() = runTest {
    whenever(auth.currentUser).thenReturn(null)
    val error = runCatching { repository.getBookingsForListing("listing") }.exceptionOrNull()
    assertTrue(error is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        (error as FirebaseFirestoreException).code,
    )
  }
}
