// Co-authored-by: Copilot (Auto)
// Co-authored-by: Copilot (GPT-5.6 Terra)
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.model

import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.AppException
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotMetadata
import com.google.firebase.firestore.WriteBatch
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
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
  private val renterQuery = mock<Query>()
  private val allLenderQuery = mock<Query>()
  private val snapshot = mock<QuerySnapshot>()
  private val stored = mock<DocumentSnapshot>()
  private val journal = FakeConfirmationJournal()
  // A fixed day before every booking these tests store, so none of them is ever finished.
  private val repository =
      FirebaseBookingRepository(
          firestore,
          auth,
          journal = journal,
          today = { LocalDate.of(2026, 11, 1) },
      )
  private val booking =
      Booking(
          listingId = "listing",
          renterUid = "caller-renter",
          lenderUid = "lender",
          // Separate days, given out of order, across two years.
          days = setOf(LocalDate.of(2027, 1, 2), LocalDate.of(2026, 12, 31)),
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
    whenever(collection.whereEqualTo("renterUid", "renter")).thenReturn(renterQuery)
    whenever(collection.whereEqualTo("lenderUid", "renter")).thenReturn(allLenderQuery)
    whenever(collection.document("booking-id")).thenReturn(document)
    whenever(snapshot.metadata).thenReturn(mock<SnapshotMetadata>())
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
                    // Earliest first, as the rules check the first day against today.
                    "days" to
                        listOf(
                            LocalDate.of(2026, 12, 31).toEpochDay(),
                            LocalDate.of(2027, 1, 2).toEpochDay(),
                        ),
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
    val repository =
        FirebaseBookingRepository(firestore, auth, journal = journal) { reportedFailure = it }
    var reportedToCaller: Exception? = null

    repository.requestBooking(booking) { reportedToCaller = it }
    val failure = IllegalStateException("Write rejected")
    pendingWrite.setException(failure)

    assertSame(failure, reportedFailure)
    assertSame(failure, reportedToCaller)
  }

  @Test
  fun requestBookingRejectsNoDaysAndANonRequestedStatus() = runTest {
    val noDays = booking.copy(days = emptySet())
    val confirmed = booking.copy(status = BookingStatus.CONFIRMED)

    assertTrue(
        runCatching { repository.requestBooking(noDays) }.exceptionOrNull()
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
    whenever(stored.get("days")).thenReturn(booking.days.map { it.toEpochDay() })
    whenever(stored.getString("status")).thenReturn("CANCELLED")
    whenever(stored.id).thenReturn("booking-id")

    assertEquals(
        listOf(booking.copy(id = "booking-id", status = BookingStatus.CANCELLED)),
        repository.getBookingsForListing("listing"),
    )
    verify(listingQuery).whereEqualTo("lenderUid", "lender")
  }

  @Test
  fun getBookingsDecodesConfirmationsAndRejectsInvalidValues() = runTest {
    val lender = mock<FirebaseUser>()
    whenever(lender.uid).thenReturn("lender")
    whenever(auth.currentUser).thenReturn(lender)
    whenever(lenderQuery.get()).thenReturn(Tasks.forResult(snapshot))
    whenever(snapshot.documents).thenReturn(listOf(stored))
    whenever(stored.getString("listingId")).thenReturn(booking.listingId)
    whenever(stored.getString("renterUid")).thenReturn(booking.renterUid)
    whenever(stored.getString("lenderUid")).thenReturn(booking.lenderUid)
    whenever(stored.get("days")).thenReturn(booking.days.map { it.toEpochDay() })
    whenever(stored.getString("status")).thenReturn("CONFIRMED")
    whenever(stored.id).thenReturn("booking-id")
    val fields =
        listOf(
            "lenderHandoverConfirmed",
            "renterHandoverConfirmed",
            "renterReturnConfirmed",
            "lenderReturnConfirmed",
        )
    fields.forEach {
      whenever(stored.contains(it)).thenReturn(true)
      whenever(stored.get(it)).thenReturn(true)
    }

    assertEquals(
        booking.copy(
            status = BookingStatus.CONFIRMED,
            id = "booking-id",
            handoverNotified = true,
            renterHandoverConfirmed = true,
            returnNotified = true,
            lenderReturnConfirmed = true,
        ),
        repository.getBookingsForListing("listing").single(),
    )
    whenever(stored.get("renterReturnConfirmed")).thenReturn("true")
    assertTrue(
        runCatching { repository.getBookingsForListing("listing") }.exceptionOrNull()
            is IllegalStateException
    )
  }

  @Test
  fun observeBookingsEmitsPendingAndAcknowledgedChangesAndRemovesListener() = runTest {
    val registration = mock<ListenerRegistration>()
    val metadata = mock<SnapshotMetadata>()
    val listener = argumentCaptor<EventListener<QuerySnapshot>>()
    whenever(renterQuery.addSnapshotListener(eq(MetadataChanges.INCLUDE), listener.capture()))
        .thenReturn(registration)
    whenever(snapshot.documents).thenReturn(listOf(stored))
    whenever(stored.id).thenReturn("booking-id")
    whenever(stored.metadata).thenReturn(metadata)
    whenever(stored.getString("listingId")).thenReturn(booking.listingId)
    whenever(stored.getString("renterUid")).thenReturn(booking.renterUid)
    whenever(stored.getString("lenderUid")).thenReturn(booking.lenderUid)
    whenever(stored.getString("status")).thenReturn("CONFIRMED")
    whenever(stored.get("days")).thenReturn(booking.days.map { it.toEpochDay() })
    val results = mutableListOf<ObservedBookings>()
    val job = launch { repository.observeBookingsForRenter().take(2).toList(results) }
    runCurrent()

    whenever(metadata.hasPendingWrites()).thenReturn(true)
    listener.firstValue.onEvent(snapshot, null)
    runCurrent()
    whenever(metadata.hasPendingWrites()).thenReturn(false)
    listener.firstValue.onEvent(snapshot, null)
    job.join()

    assertEquals(setOf("booking-id"), results[0].pendingIds)
    assertEquals(emptySet<String>(), results[1].pendingIds)
    assertEquals(BookingStatus.CONFIRMED, results[1].bookings.single().status)
    verify(registration).remove()
  }

  @Test
  fun observeListingBookingsUsesSignedInLenderAndRemovesListener() = runTest {
    val lender = mock<FirebaseUser>()
    whenever(lender.uid).thenReturn("lender")
    whenever(auth.currentUser).thenReturn(lender)
    val registration = mock<ListenerRegistration>()
    val listener = argumentCaptor<EventListener<QuerySnapshot>>()
    whenever(lenderQuery.addSnapshotListener(eq(MetadataChanges.INCLUDE), listener.capture()))
        .thenReturn(registration)
    whenever(snapshot.documents).thenReturn(emptyList())

    val result = async { repository.observeBookingsForListing("listing").first() }
    runCurrent()
    listener.firstValue.onEvent(snapshot, null)

    assertEquals(ObservedBookings(emptyList()), result.await())
    verify(listingQuery).whereEqualTo("lenderUid", "lender")
    verify(registration).remove()
  }

  @Test
  fun observeBookingsPropagatesListenerErrorsAndMalformedSnapshots() = runTest {
    val listenerError =
        FirebaseFirestoreException(
            "Permission denied",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )
    val malformed = mock<QuerySnapshot>()
    whenever(malformed.documents).thenReturn(listOf(stored))
    val events = listOf(null to listenerError, null to null, malformed to null)

    for ((value, error) in events) {
      val query = mock<Query>()
      val registration = mock<ListenerRegistration>()
      val listener = argumentCaptor<EventListener<QuerySnapshot>>()
      whenever(collection.whereEqualTo("renterUid", "renter")).thenReturn(query)
      whenever(query.addSnapshotListener(eq(MetadataChanges.INCLUDE), listener.capture()))
          .thenReturn(registration)
      val result = async {
        runCatching { repository.observeBookingsForRenter().first() }.exceptionOrNull()
      }
      runCurrent()
      listener.firstValue.onEvent(value, error)
      val failure = result.await()

      if (error != null) assertSame(error, failure)
      else assertTrue(failure is IllegalStateException)
      verify(registration).remove()
    }
  }

  @Test
  fun observeBookingsRejectsSignedOutUser() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val failure = runCatching { repository.observeBookingsForRenter().first() }.exceptionOrNull()
    assertTrue(failure is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        (failure as FirebaseFirestoreException).code,
    )
    verify(collection, never()).whereEqualTo("renterUid", "renter")
  }

  @Test
  fun restoredAttemptWaitsForServerAndReconcilesPendingAndAcknowledgedWrites() = runTest {
    val attempt =
        ConfirmationAttempt("renter", "booking-id", ConfirmationAction.CONFIRM_HANDOVER, "token")
    journal.put(attempt)
    journal.finish(attempt.token)
    val documentMetadata = mock<SnapshotMetadata>()
    val queryMetadata = mock<SnapshotMetadata>()
    val listener = argumentCaptor<EventListener<QuerySnapshot>>()
    whenever(renterQuery.addSnapshotListener(eq(MetadataChanges.INCLUDE), listener.capture()))
        .thenReturn(mock<ListenerRegistration>())
    whenever(snapshot.metadata).thenReturn(queryMetadata)
    whenever(snapshot.documents).thenReturn(listOf(stored))
    whenever(stored.id).thenReturn("booking-id")
    whenever(stored.metadata).thenReturn(documentMetadata)
    whenever(stored.getString("listingId")).thenReturn(booking.listingId)
    whenever(stored.getString("renterUid")).thenReturn(booking.renterUid)
    whenever(stored.getString("lenderUid")).thenReturn(booking.lenderUid)
    whenever(stored.getString("status")).thenReturn("CONFIRMED")
    whenever(stored.get("days")).thenReturn(booking.days.map { it.toEpochDay() })

    suspend fun nextSnapshot(): ObservedBookings {
      val result = async { repository.observeBookingsForRenter().first() }
      runCurrent()
      listener.lastValue.onEvent(snapshot, null)
      return result.await()
    }

    whenever(queryMetadata.isFromCache).thenReturn(true)
    val cached = nextSnapshot()
    assertEquals(setOf("booking-id"), cached.checkingConfirmationIds)
    assertTrue(cached.confirmationFailures.isEmpty())

    whenever(queryMetadata.isFromCache).thenReturn(false)
    whenever(documentMetadata.hasPendingWrites()).thenReturn(true)
    val pending = nextSnapshot()
    assertEquals(setOf("booking-id"), pending.pendingIds)
    assertEquals(listOf(attempt), journal.attempts.value)

    whenever(documentMetadata.hasPendingWrites()).thenReturn(false)
    whenever(stored.contains("renterHandoverConfirmed")).thenReturn(true)
    whenever(stored.get("renterHandoverConfirmed")).thenReturn(true)
    val acknowledged = nextSnapshot()
    assertTrue(acknowledged.confirmationFailures.isEmpty())
    assertTrue(acknowledged.checkingConfirmationIds.isEmpty())
    assertTrue(journal.attempts.value.isEmpty())
  }

  @Test
  fun restoredAttemptWithoutPendingWriteOrServerConfirmationIsReported() = runTest {
    val attempt =
        ConfirmationAttempt("renter", "booking-id", ConfirmationAction.CONFIRM_HANDOVER, "token")
    val other = attempt.copy(uid = "other", token = "other-token")
    journal.put(attempt)
    journal.finish(attempt.token)
    journal.put(other)
    journal.finish(other.token)
    val listener = argumentCaptor<EventListener<QuerySnapshot>>()
    whenever(renterQuery.addSnapshotListener(eq(MetadataChanges.INCLUDE), listener.capture()))
        .thenReturn(mock<ListenerRegistration>())
    whenever(snapshot.documents).thenReturn(listOf(stored))
    whenever(stored.id).thenReturn("booking-id")
    whenever(stored.metadata).thenReturn(mock<SnapshotMetadata>())
    whenever(stored.getString("listingId")).thenReturn(booking.listingId)
    whenever(stored.getString("renterUid")).thenReturn(booking.renterUid)
    whenever(stored.getString("lenderUid")).thenReturn(booking.lenderUid)
    whenever(stored.getString("status")).thenReturn("CONFIRMED")
    whenever(stored.get("days")).thenReturn(booking.days.map { it.toEpochDay() })

    val result = async { repository.observeBookingsForRenter().first() }
    runCurrent()
    listener.firstValue.onEvent(snapshot, null)

    assertEquals(
        RecoveredConfirmationFailure(
            ConfirmationAction.CONFIRM_HANDOVER,
            AppError.CONFIRMATION_NOT_SAVED,
        ),
        result.await().confirmationFailures["booking-id"],
    )
    assertEquals(other, journal.attempts.value.first { it.uid == "other" })
    repository.dismissConfirmationFailure("booking-id")
    assertEquals(listOf(other), journal.attempts.value)
  }

  @Test
  fun failureIsStoredBeforeCallbackCanDismissIt() = runTest {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val gated =
        object : ConfirmationJournal by journal {
          override suspend fun fail(token: String, error: AppError) {
            entered.complete(Unit)
            release.await()
            journal.fail(token, error)
          }
        }
    val write = TaskCompletionSource<Void>()
    whenever(document.update(any<String>(), eq(true))).thenReturn(write.task)
    val failure =
        FirebaseFirestoreException("refused", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    val callback = CompletableDeferred<Exception>()
    val repository = FirebaseBookingRepository(firestore, auth, journal = gated) {}
    repository.confirmHandover("booking-id") { callback.complete(it) }
    write.setException(failure)

    withContext(Dispatchers.IO) { withTimeout(5_000) { entered.await() } }
    assertFalse(callback.isCompleted)
    release.complete(Unit)
    assertSame(failure, withContext(Dispatchers.IO) { withTimeout(5_000) { callback.await() } })
    assertEquals(AppError.PERMISSION_DENIED, journal.attempts.value.single().error)
    repository.dismissConfirmationFailure("booking-id")
    assertTrue(journal.attempts.value.isEmpty())
  }

  @Test
  fun persistedFailureIsNotObservedBeforeCallbackAndCannotReappearAfterDismissal() = runTest {
    val persisted = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val gated =
        object : ConfirmationJournal by journal {
          override suspend fun fail(token: String, error: AppError) {
            journal.fail(token, error)
            persisted.complete(Unit)
            release.await()
          }
        }
    val write = TaskCompletionSource<Void>()
    whenever(document.update(any<String>(), eq(true))).thenReturn(write.task)
    val listener = argumentCaptor<EventListener<QuerySnapshot>>()
    whenever(renterQuery.addSnapshotListener(eq(MetadataChanges.INCLUDE), listener.capture()))
        .thenReturn(mock<ListenerRegistration>())
    whenever(snapshot.documents).thenReturn(listOf(stored))
    whenever(stored.id).thenReturn("booking-id")
    whenever(stored.metadata).thenReturn(mock<SnapshotMetadata>())
    whenever(stored.getString("listingId")).thenReturn(booking.listingId)
    whenever(stored.getString("renterUid")).thenReturn(booking.renterUid)
    whenever(stored.getString("lenderUid")).thenReturn(booking.lenderUid)
    whenever(stored.getString("status")).thenReturn("CONFIRMED")
    whenever(stored.get("days")).thenReturn(booking.days.map { it.toEpochDay() })
    val callback = CompletableDeferred<Exception>()
    val repository = FirebaseBookingRepository(firestore, auth, journal = gated) {}
    repository.confirmHandover("booking-id") { callback.complete(it) }
    val updates = mutableListOf<ObservedBookings>()
    val observation = launch { repository.observeBookingsForRenter().collect { updates += it } }
    runCurrent()
    listener.firstValue.onEvent(snapshot, null)
    runCurrent()
    assertTrue(updates.isNotEmpty())

    val failure =
        FirebaseFirestoreException("refused", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    write.setException(failure)
    withContext(Dispatchers.IO) { withTimeout(5_000) { persisted.await() } }
    runCurrent()
    assertFalse(callback.isCompleted)
    assertTrue(updates.all { it.confirmationFailures.isEmpty() })
    assertEquals(setOf("booking-id"), updates.last().checkingConfirmationIds)
    release.complete(Unit)
    withContext(Dispatchers.IO) { withTimeout(5_000) { callback.await() } }
    repository.dismissConfirmationFailure("booking-id")
    runCurrent()
    assertTrue(journal.attempts.value.isEmpty())
    assertTrue(updates.last().confirmationFailures.isEmpty())
    observation.cancel()
  }

  @Test
  fun synchronousConfirmationWriteFailureIsPersistedFinishedAndRethrown() = runTest {
    val failure =
        IllegalStateException(
            "Could not queue confirmation",
            AppException(AppError.PERMISSION_DENIED),
        )
    whenever(document.update("renterHandoverConfirmed", true)).thenThrow(failure)
    val finished = mutableListOf<String>()
    val recordingJournal =
        object : ConfirmationJournal by journal {
          override fun finish(token: String) {
            finished += token
            journal.finish(token)
          }
        }
    val repository = FirebaseBookingRepository(firestore, auth, journal = recordingJournal)
    var callbackCalled = false

    val thrown =
        try {
          repository.confirmHandover("booking-id") { callbackCalled = true }
          null
        } catch (exception: Exception) {
          exception
        }

    val attempt = journal.attempts.value.single()
    assertSame(failure, thrown)
    assertEquals("booking-id", attempt.bookingId)
    assertEquals(ConfirmationAction.CONFIRM_HANDOVER, attempt.action)
    assertEquals(AppError.PERMISSION_DENIED, attempt.error)
    assertEquals(listOf(attempt.token), finished)
    assertTrue(journal.activeTokens.value.isEmpty())
    assertFalse(callbackCalled)
  }

  @Test
  fun confirmationOperationsQueueTheirOwnFieldsAndReportWriteFailures() = runTest {
    val pending = TaskCompletionSource<Void>()
    whenever(document.update(any<String>(), eq(true))).thenReturn(pending.task)
    var logged: Exception? = null
    val reported = CompletableDeferred<Exception>()
    val repository = FirebaseBookingRepository(firestore, auth, journal = journal) { logged = it }

    withTimeout(1_000) {
      repository.notifyHandover("booking-id") { reported.complete(it) }
      repository.confirmHandover("booking-id")
      repository.notifyReturn("booking-id")
      repository.confirmReturn("booking-id")
    }
    verify(document).update("lenderHandoverConfirmed", true)
    verify(document).update("renterHandoverConfirmed", true)
    verify(document).update("renterReturnConfirmed", true)
    verify(document).update("lenderReturnConfirmed", true)
    assertFalse(pending.task.isComplete)
    val failure =
        FirebaseFirestoreException(
            "Write rejected",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )
    pending.setException(failure)
    assertSame(failure, logged)
    assertSame(failure, withContext(Dispatchers.IO) { withTimeout(5_000) { reported.await() } })
  }

  @Test
  fun confirmationRejectsSignedOutUser() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val failure = runCatching { repository.notifyHandover("booking-id") }.exceptionOrNull()
    assertTrue(failure is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        (failure as FirebaseFirestoreException).code,
    )
    verify(document, never()).update(any<String>(), any())
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
  fun getBookingsRejectsABookingWithoutDaysOrWithAnInvalidDay() = runTest {
    val lender = mock<FirebaseUser>()
    whenever(lender.uid).thenReturn("lender")
    whenever(auth.currentUser).thenReturn(lender)
    whenever(lenderQuery.get()).thenReturn(Tasks.forResult(snapshot))
    whenever(snapshot.documents).thenReturn(listOf(stored))
    whenever(stored.getString("listingId")).thenReturn(booking.listingId)
    whenever(stored.getString("renterUid")).thenReturn(booking.renterUid)
    whenever(stored.getString("lenderUid")).thenReturn(booking.lenderUid)
    whenever(stored.getString("status")).thenReturn("REQUESTED")

    // A booking written with the first and last day of earlier versions has no days.
    whenever(stored.get("days")).thenReturn(null)
    assertTrue(
        runCatching { repository.getBookingsForListing("listing") }.exceptionOrNull()
            is IllegalStateException
    )
    whenever(stored.get("days")).thenReturn(emptyList<Long>())
    assertTrue(
        runCatching { repository.getBookingsForListing("listing") }.exceptionOrNull()
            is IllegalStateException
    )
    whenever(stored.get("days")).thenReturn(listOf("20000"))
    assertTrue(
        runCatching { repository.getBookingsForListing("listing") }.exceptionOrNull()
            is IllegalStateException
    )
    // Out of LocalDate's range: ofEpochDay throws DateTimeException, rethrown as above.
    whenever(stored.get("days")).thenReturn(listOf(Long.MAX_VALUE))
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

  @Test
  fun getBookingsForRenterQueriesAuthenticatedRenterAndDecodesBookings() = runTest {
    whenever(renterQuery.get()).thenReturn(Tasks.forResult(snapshot))
    whenever(snapshot.documents).thenReturn(listOf(stored))
    whenever(stored.getString("listingId")).thenReturn(booking.listingId)
    whenever(stored.getString("renterUid")).thenReturn("renter")
    whenever(stored.getString("lenderUid")).thenReturn(booking.lenderUid)
    whenever(stored.get("days")).thenReturn(booking.days.map { it.toEpochDay() })
    whenever(stored.getString("status")).thenReturn("REQUESTED")
    whenever(stored.id).thenReturn("booking-id")

    assertEquals(
        listOf(booking.copy(renterUid = "renter", id = "booking-id")),
        repository.getBookingsForRenter(),
    )
    verify(collection).whereEqualTo("renterUid", "renter")
  }

  @Test
  fun getBookingsForLenderQueriesTheSignedInLenderAndDecodesBookings() = runTest {
    whenever(allLenderQuery.get()).thenReturn(Tasks.forResult(snapshot))
    whenever(snapshot.documents).thenReturn(listOf(stored))
    whenever(stored.getString("listingId")).thenReturn(booking.listingId)
    whenever(stored.getString("renterUid")).thenReturn(booking.renterUid)
    whenever(stored.getString("lenderUid")).thenReturn("renter")
    whenever(stored.get("days")).thenReturn(booking.days.map { it.toEpochDay() })
    whenever(stored.getString("status")).thenReturn("CANCELLED")
    whenever(stored.id).thenReturn("booking-id")

    assertEquals(
        listOf(
            booking.copy(lenderUid = "renter", status = BookingStatus.CANCELLED, id = "booking-id")
        ),
        repository.getBookingsForLender(),
    )
    verify(collection).whereEqualTo("lenderUid", "renter")
  }

  @Test
  fun getBookingsForLenderRejectsSignedOutUser() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val error = runCatching { repository.getBookingsForLender() }.exceptionOrNull()

    assertTrue(error is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        (error as FirebaseFirestoreException).code,
    )
  }

  @Test
  fun getBookingsForRenterRejectsSignedOutUser() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val error = runCatching { repository.getBookingsForRenter() }.exceptionOrNull()

    assertTrue(error is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        (error as FirebaseFirestoreException).code,
    )
  }

  @Test
  fun cancelBookingQueuesOfflineWriteWithoutWaitingForServer() = runTest {
    val pendingWrite = TaskCompletionSource<Void>()
    whenever(document.update("status", "CANCELLED")).thenReturn(pendingWrite.task)

    withTimeout(1_000) { repository.cancelBooking("booking-id") }

    assertTrue(!pendingWrite.task.isComplete)
    verify(document).update("status", "CANCELLED")
  }

  @Test
  fun cancelBookingReportsLaterRejectedWrite() = runTest {
    val pendingWrite = TaskCompletionSource<Void>()
    whenever(document.update("status", "CANCELLED")).thenReturn(pendingWrite.task)
    var reportedFailure: Exception? = null
    val repository =
        FirebaseBookingRepository(firestore, auth, journal = journal) { reportedFailure = it }
    var reportedToCaller: Exception? = null

    repository.cancelBooking("booking-id", onWriteFailure = { reportedToCaller = it })
    val failure = IllegalStateException("Write rejected")
    pendingWrite.setException(failure)

    assertSame(failure, reportedFailure)
    assertSame(failure, reportedToCaller)
  }

  @Test
  fun cancelBookingLogsRejectedWriteWhenCallerPassesNoCallback() = runTest {
    val pendingWrite = TaskCompletionSource<Void>()
    whenever(document.update("status", "CANCELLED")).thenReturn(pendingWrite.task)
    var logged: Exception? = null
    val repository = FirebaseBookingRepository(firestore, auth, journal = journal) { logged = it }

    repository.cancelBooking("booking-id")
    val failure = IllegalStateException("Write rejected")
    pendingWrite.setException(failure)

    assertSame(failure, logged)
  }

  @Test
  fun cancelBookingRejectsSignedOutUser() = runTest {
    whenever(auth.currentUser).thenReturn(null)

    val error = runCatching { repository.cancelBooking("booking-id") }.exceptionOrNull()

    assertTrue(error is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        (error as FirebaseFirestoreException).code,
    )
    verify(document, never()).update(any<String>(), any())
  }

  /** A stored booking of the lender on "listing", as the query returns it. */
  private fun storedBooking(id: String, days: List<Int>, status: BookingStatus): DocumentSnapshot {
    val document = mock<DocumentSnapshot>()
    whenever(document.id).thenReturn(id)
    whenever(document.getString("listingId")).thenReturn("listing")
    whenever(document.getString("renterUid")).thenReturn("renter-$id")
    whenever(document.getString("lenderUid")).thenReturn("lender")
    whenever(document.get("days")).thenReturn(days.map { LocalDate.of(2026, 12, it).toEpochDay() })
    whenever(document.getString("status")).thenReturn(status.name)
    return document
  }

  /** The signed-in lender, their bookings on "listing", and a batch that commits. */
  private fun givenLenderWith(vararg documents: DocumentSnapshot): WriteBatch {
    val lender = mock<FirebaseUser>()
    whenever(lender.uid).thenReturn("lender")
    whenever(auth.currentUser).thenReturn(lender)
    whenever(lenderQuery.get()).thenReturn(Tasks.forResult(snapshot))
    whenever(snapshot.documents).thenReturn(documents.toList())
    val batch = mock<WriteBatch>()
    whenever(firestore.batch()).thenReturn(batch)
    whenever(batch.commit()).thenReturn(Tasks.forResult(null))
    return batch
  }

  @Test
  fun acceptBookingConfirmsIt_takesItsDaysOff_andCancelsTheRequestsSharingADay() = runTest {
    val batch =
        givenLenderWith(
            storedBooking("accepted", listOf(1, 2), BookingStatus.REQUESTED),
            storedBooking("overlapping", listOf(2, 3), BookingStatus.REQUESTED),
            storedBooking("elsewhere", listOf(5), BookingStatus.REQUESTED),
            storedBooking("answered", listOf(1), BookingStatus.CANCELLED),
        )
    val bookingDocuments =
        listOf("accepted", "overlapping", "elsewhere", "answered").associateWith {
          mock<DocumentReference>().also { reference ->
            whenever(collection.document(it)).thenReturn(reference)
          }
        }
    val listings = mock<CollectionReference>()
    val listing = mock<DocumentReference>()
    whenever(firestore.collection("listings")).thenReturn(listings)
    whenever(listings.document("listing")).thenReturn(listing)
    val accepted =
        Booking(
            listingId = "listing",
            renterUid = "renter-accepted",
            lenderUid = "lender",
            days = setOf(LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 1)),
            id = "accepted",
        )

    repository.acceptBooking(accepted)

    val removed = argumentCaptor<Any>()
    inOrder(batch) {
      verify(batch).update(bookingDocuments.getValue("accepted"), "status", "CONFIRMED")
      verify(batch).update(bookingDocuments.getValue("overlapping"), "status", "CANCELLED")
      verify(batch).update(eq(listing), eq("availableDates"), removed.capture())
      verify(batch).commit()
    }
    // FieldValue has no equals: its elements are read instead, the stored epoch days.
    assertEquals(
        listOf(LocalDate.of(2026, 12, 1).toEpochDay(), LocalDate.of(2026, 12, 2).toEpochDay()),
        arrayRemoveElements(removed.firstValue as FieldValue).map { it as Long }.sorted(),
    )
    verify(batch, never()).update(eq(bookingDocuments.getValue("elsewhere")), any<String>(), any())
    verify(batch, never()).update(eq(bookingDocuments.getValue("answered")), any<String>(), any())
  }

  @Test
  fun acceptBookingLeavesFinishedRequestsSharingADayAsTheyAre() = runTest {
    val batch =
        givenLenderWith(
            storedBooking("accepted", listOf(2, 3), BookingStatus.REQUESTED),
            storedBooking("finished", listOf(1, 2), BookingStatus.REQUESTED),
            storedBooking("current", listOf(3), BookingStatus.REQUESTED),
        )
    val bookingDocuments =
        listOf("accepted", "finished", "current").associateWith {
          mock<DocumentReference>().also { reference ->
            whenever(collection.document(it)).thenReturn(reference)
          }
        }
    whenever(firestore.collection("listings")).thenReturn(mock())
    val repository =
        FirebaseBookingRepository(
            firestore,
            auth,
            journal = journal,
            today = { LocalDate.of(2026, 12, 3) },
        )
    val accepted =
        Booking(
            listingId = "listing",
            renterUid = "renter-accepted",
            lenderUid = "lender",
            days = setOf(LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 3)),
            id = "accepted",
        )

    repository.acceptBooking(accepted)

    // Every day of "finished" is before today; "current" ends today, so it is still cancelled.
    verify(batch).update(bookingDocuments.getValue("current"), "status", "CANCELLED")
    verify(batch, never()).update(eq(bookingDocuments.getValue("finished")), any<String>(), any())
  }

  /** The elements of an arrayRemove, which the SDK keeps package-private. */
  private fun arrayRemoveElements(value: FieldValue): List<*> =
      value.javaClass.getDeclaredMethod("getElements").apply { isAccessible = true }.invoke(value)
          as List<*>

  @Test
  fun acceptBookingFailsWhenTheWriteIsRefused() = runTest {
    val batch = givenLenderWith(storedBooking("accepted", listOf(1), BookingStatus.REQUESTED))
    whenever(collection.document("accepted")).thenReturn(document)
    whenever(firestore.collection("listings")).thenReturn(mock())
    val refused =
        FirebaseFirestoreException("refused", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    whenever(batch.commit()).thenReturn(Tasks.forException(refused))
    val accepted =
        Booking(
            "listing",
            "renter-accepted",
            "lender",
            setOf(LocalDate.of(2026, 12, 1)),
            id = "accepted",
        )

    assertSame(refused, runCatching { repository.acceptBooking(accepted) }.exceptionOrNull())
  }

  @Test
  fun acceptBookingNeedsASignedInUser_andAStoredRequest() = runTest {
    val request = booking.copy(id = "b1")
    whenever(auth.currentUser).thenReturn(null)
    val signedOut = runCatching { repository.acceptBooking(request) }.exceptionOrNull()
    assertEquals(
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        (signedOut as FirebaseFirestoreException).code,
    )

    val lender = mock<FirebaseUser>()
    whenever(lender.uid).thenReturn("lender")
    whenever(auth.currentUser).thenReturn(lender)
    assertTrue(
        runCatching { repository.acceptBooking(booking) }.exceptionOrNull()
            is IllegalArgumentException
    )
    assertTrue(
        runCatching { repository.acceptBooking(request.copy(status = BookingStatus.CONFIRMED)) }
            .exceptionOrNull() is IllegalArgumentException
    )
    verify(firestore, never()).batch()
  }

  @Test
  fun overlapsIsTrueOnlyWhenTheBookingsShareADay() {
    val other = booking.copy(days = setOf(LocalDate.of(2027, 1, 2), LocalDate.of(2027, 1, 5)))
    assertTrue(booking.overlaps(other))
    assertFalse(booking.overlaps(other.copy(days = setOf(LocalDate.of(2027, 1, 1)))))
  }
}
