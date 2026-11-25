// Co-authored-by: Copilot (Auto)
package com.android.polybazaar.booking.model

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.polybazaar.error.model.AppError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotMetadata
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ConfirmationJournalTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val preferences =
      context.getSharedPreferences("booking_confirmations", Context.MODE_PRIVATE)
  private val attempt =
      ConfirmationAttempt("renter", "booking", ConfirmationAction.CONFIRM_HANDOVER, "token")

  @Before
  @After
  fun clearPreferences() {
    preferences.edit().clear().commit()
  }

  @Test
  fun attemptsAndFailuresSurviveRecreationAndDismissal() = runTest {
    val journal = SharedPreferencesConfirmationJournal(context)
    journal.put(attempt)
    journal.fail(attempt.token, AppError.PERMISSION_DENIED)

    val restored = SharedPreferencesConfirmationJournal(context)
    assertEquals(listOf(attempt.copy(error = AppError.PERMISSION_DENIED)), restored.attempts.value)
    assertEquals(emptySet<String>(), restored.activeTokens.value)
    restored.remove(attempt.token)
    assertEquals(
        emptyList<ConfirmationAttempt>(),
        SharedPreferencesConfirmationJournal(context).attempts.value,
    )
  }

  @Test
  fun failedCommitDoesNotPublishOrQueueAnAttempt() = runTest {
    val context = mock<Context>()
    val preferences = mock<SharedPreferences>()
    val editor = mock<SharedPreferences.Editor>()
    whenever(context.getSharedPreferences("booking_confirmations", Context.MODE_PRIVATE))
        .thenReturn(preferences)
    whenever(preferences.getString("attempts", "[]")).thenReturn("[]")
    whenever(preferences.edit()).thenReturn(editor)
    whenever(editor.putString(eq("attempts"), any())).thenReturn(editor)
    whenever(editor.commit()).thenReturn(false)
    val journal =
        SharedPreferencesConfirmationJournal(context, StandardTestDispatcher(testScheduler))

    assertTrue(runCatching { journal.put(attempt) }.exceptionOrNull() is IOException)
    assertEquals(emptyList<ConfirmationAttempt>(), journal.attempts.value)
    assertEquals(emptySet<String>(), journal.activeTokens.value)
  }

  @Test
  fun lateFailureOrAcknowledgementCannotOverwriteNewerRetryOrOtherUser() = runTest {
    val journal = SharedPreferencesConfirmationJournal(context)
    val retry = attempt.copy(token = "retry")
    val other = attempt.copy(uid = "other", token = "other")
    journal.put(attempt)
    journal.put(other)
    journal.put(retry)
    journal.fail(attempt.token, AppError.PERMISSION_DENIED)
    journal.remove(attempt.token)

    assertEquals(
        setOf(other, retry),
        SharedPreferencesConfirmationJournal(context).attempts.value.toSet(),
    )
  }

  @Test
  fun restoredAttemptIsReconciledWithTheServerAfterRestart() = runTest {
    SharedPreferencesConfirmationJournal(context).put(attempt)
    val restored = SharedPreferencesConfirmationJournal(context)
    val firestore = mock<FirebaseFirestore>()
    val auth = mock<FirebaseAuth>()
    val user = mock<FirebaseUser>()
    val collection = mock<CollectionReference>()
    val query = mock<Query>()
    val snapshot = mock<QuerySnapshot>()
    val document = mock<DocumentSnapshot>()
    val metadata = mock<SnapshotMetadata>()
    val listener = argumentCaptor<EventListener<QuerySnapshot>>()
    whenever(auth.currentUser).thenReturn(user)
    whenever(user.uid).thenReturn("renter")
    whenever(firestore.collection("bookings")).thenReturn(collection)
    whenever(collection.whereEqualTo("renterUid", "renter")).thenReturn(query)
    whenever(query.addSnapshotListener(eq(MetadataChanges.INCLUDE), listener.capture()))
        .thenReturn(mock<ListenerRegistration>())
    whenever(snapshot.metadata).thenReturn(metadata)
    whenever(snapshot.documents).thenReturn(listOf(document))
    whenever(document.metadata).thenReturn(metadata)
    whenever(document.id).thenReturn("booking")
    whenever(document.getString("listingId")).thenReturn("listing")
    whenever(document.getString("renterUid")).thenReturn("renter")
    whenever(document.getString("lenderUid")).thenReturn("lender")
    whenever(document.getString("status")).thenReturn("CONFIRMED")
    whenever(document.get("days")).thenReturn(listOf(20_000L))
    val repository = FirebaseBookingRepository(firestore, auth, journal = restored)

    val result = async { repository.observeBookingsForRenter().first() }
    runCurrent()
    listener.firstValue.onEvent(snapshot, null)

    assertEquals(
        RecoveredConfirmationFailure(
            ConfirmationAction.CONFIRM_HANDOVER,
            AppError.CONFIRMATION_NOT_SAVED,
        ),
        result.await().confirmationFailures["booking"],
    )
    assertEquals(
        listOf(attempt.copy(error = AppError.CONFIRMATION_NOT_SAVED)),
        SharedPreferencesConfirmationJournal(context).attempts.value,
    )
  }

  @Test
  fun malformedDataDoesNotCrashAndValidRecordsAreRetained() {
    preferences.edit().putString("attempts", "not-json").commit()
    assertEquals(
        emptyList<ConfirmationAttempt>(),
        SharedPreferencesConfirmationJournal(context).attempts.value,
    )

    preferences
        .edit()
        .putString(
            "attempts",
            """[{"uid":"renter","bookingId":"booking","action":"CONFIRM_HANDOVER","token":"token"},
            {"uid":"renter","bookingId":"bad","action":"UNKNOWN_ACTION","token":"bad"},
            {"uid":"renter","bookingId":"bad2","action":"CONFIRM_HANDOVER","token":"bad2","error":"UNKNOWN_ERROR"},
            {"uid":"renter","action":"CONFIRM_HANDOVER","token":"missing"}]""",
        )
        .commit()
    assertEquals(listOf(attempt), SharedPreferencesConfirmationJournal(context).attempts.value)
  }
}
