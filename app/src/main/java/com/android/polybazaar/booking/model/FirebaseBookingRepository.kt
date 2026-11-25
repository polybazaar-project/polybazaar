// Co-authored-by: Copilot (Auto)
// Co-authored-by: Copilot (GPT-5.6 Terra)
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.model

import android.util.Log
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import java.time.DateTimeException
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirebaseBookingRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val journal: ConfirmationJournal,
    /** The current day, to leave finished requests alone when accepting. */
    private val today: () -> LocalDate = LocalDate::now,
    private val logWriteFailure: (Exception) -> Unit = {
      Log.e("BookingRepository", "Booking write failed", it)
    },
) : BookingRepository {
  private val journalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override suspend fun requestBooking(
      booking: Booking,
      onWriteFailure: (Exception) -> Unit,
  ) {
    val renterUid = signedInUid("Sign in to request a booking")
    require(booking.status == BookingStatus.REQUESTED) { "A new booking must be requested" }
    require(booking.days.isNotEmpty()) { "A booking needs at least one day" }
    firestore
        .collection(BOOKINGS)
        .document()
        .set(
            mapOf(
                "listingId" to booking.listingId,
                "renterUid" to renterUid,
                "lenderUid" to booking.lenderUid,
                // Earliest first: the rules check that the first day is not in the past.
                DAYS to booking.days.sorted().map { it.toEpochDay() },
                STATUS to booking.status.name,
            )
        )
        .addOnFailureListener(Executor { it.run() }) {
          logWriteFailure(it)
          onWriteFailure(it)
        }
  }

  override suspend fun getBookingsForListing(listingId: String): List<Booking> {
    val uid = signedInUid(SIGN_IN_TO_READ_BOOKINGS)
    return firestore
        .collection(BOOKINGS)
        .whereEqualTo("listingId", listingId)
        .whereEqualTo("lenderUid", uid)
        .get()
        .await()
        .documents
        .map { it.toBooking() }
  }

  override suspend fun getBookingsForLender(): List<Booking> {
    val uid = signedInUid(SIGN_IN_TO_READ_BOOKINGS)
    // The rules let a lender read the bookings that name them, so the query passes as a whole.
    return firestore
        .collection(BOOKINGS)
        .whereEqualTo("lenderUid", uid)
        .get()
        .await()
        .documents
        .map { it.toBooking() }
  }

  override suspend fun getBookingsForRenter(): List<Booking> {
    val uid = signedInUid(SIGN_IN_TO_READ_BOOKINGS)
    return firestore
        .collection(BOOKINGS)
        .whereEqualTo("renterUid", uid)
        .get()
        .await()
        .documents
        .map { it.toBooking() }
  }

  override fun observeBookingsForListing(listingId: String): Flow<ObservedBookings> =
      observeBookings {
        val uid = signedInUid(SIGN_IN_TO_READ_BOOKINGS)
        firestore
            .collection(BOOKINGS)
            .whereEqualTo("listingId", listingId)
            .whereEqualTo("lenderUid", uid)
      }

  override fun observeBookingsForRenter(): Flow<ObservedBookings> = observeBookings {
    val uid = signedInUid(SIGN_IN_TO_READ_BOOKINGS)
    firestore.collection(BOOKINGS).whereEqualTo("renterUid", uid)
  }

  private data class BookingSnapshot(val observed: ObservedBookings, val fromCache: Boolean)

  private fun observeBookings(query: () -> Query): Flow<ObservedBookings> {
    val uid = signedInUid(SIGN_IN_TO_READ_BOOKINGS)
    val recovered =
        journal.attempts.value
            .filter { it.uid == uid && it.token !in journal.activeTokens.value }
            .mapTo(mutableSetOf()) { it.token }
    return combine(observeSnapshots(query), journal.attempts, journal.activeTokens) {
        snapshot,
        attempts,
        activeTokens ->
      val observed = snapshot.observed
      val relevant = attempts.filter { attempt ->
        attempt.uid == uid && observed.bookings.any { it.id == attempt.bookingId }
      }
      if (!snapshot.fromCache) {
        for (attempt in relevant) {
          val booking = observed.bookings.first { it.id == attempt.bookingId }
          if (booking.id !in observed.pendingIds) {
            if (attempt.action.isRecorded(booking)) journal.remove(attempt.token)
            else if (attempt.error == null && attempt.token in recovered) {
              journal.fail(attempt.token, AppError.UNKNOWN)
            }
          }
        }
      }
      val current =
          journal.attempts.value.filter { attempt ->
            attempt.uid == uid && observed.bookings.any { it.id == attempt.bookingId }
          }
      observed.copy(
          checkingConfirmationIds =
              current
                  .filter {
                    (it.error == null || it.token in activeTokens) &&
                        it.bookingId !in observed.pendingIds
                  }
                  .mapTo(mutableSetOf()) { it.bookingId },
          confirmationFailures =
              current
                  .mapNotNull { attempt ->
                    attempt.error
                        ?.takeIf { attempt.token !in activeTokens }
                        ?.let {
                          attempt.bookingId to RecoveredConfirmationFailure(attempt.action, it)
                        }
                  }
                  .toMap(),
      )
    }
  }

  private fun observeSnapshots(query: () -> Query): Flow<BookingSnapshot> = callbackFlow {
    val listener =
        query().addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
          when {
            error != null -> close(error)
            snapshot == null -> close(IllegalStateException("Booking snapshot was null"))
            else -> {
              try {
                val documents = snapshot.documents
                trySend(
                    BookingSnapshot(
                        observed =
                            ObservedBookings(
                                bookings = documents.map { it.toBooking() },
                                pendingIds =
                                    documents
                                        .filter { it.metadata.hasPendingWrites() }
                                        .mapTo(mutableSetOf()) { it.id },
                            ),
                        fromCache = snapshot.metadata.isFromCache,
                    )
                )
              } catch (exception: Exception) {
                close(exception)
              }
            }
          }
        }
    awaitClose { listener.remove() }
  }

  override suspend fun cancelBooking(id: String, onWriteFailure: (Exception) -> Unit) {
    signedInUid("Sign in to cancel a booking")
    firestore
        .collection(BOOKINGS)
        .document(id)
        .update(STATUS, BookingStatus.CANCELLED.name)
        .addOnFailureListener(Executor { it.run() }) {
          logWriteFailure(it)
          onWriteFailure(it)
        }
  }

  override suspend fun notifyHandover(id: String, onWriteFailure: (Exception) -> Unit) =
      queueConfirmation(
          id,
          ConfirmationAction.NOTIFY_HANDOVER,
          LENDER_HANDOVER_CONFIRMED,
          onWriteFailure,
      )

  override suspend fun confirmHandover(id: String, onWriteFailure: (Exception) -> Unit) =
      queueConfirmation(
          id,
          ConfirmationAction.CONFIRM_HANDOVER,
          RENTER_HANDOVER_CONFIRMED,
          onWriteFailure,
      )

  override suspend fun notifyReturn(id: String, onWriteFailure: (Exception) -> Unit) =
      queueConfirmation(
          id,
          ConfirmationAction.NOTIFY_RETURN,
          RENTER_RETURN_CONFIRMED,
          onWriteFailure,
      )

  override suspend fun confirmReturn(id: String, onWriteFailure: (Exception) -> Unit) =
      queueConfirmation(
          id,
          ConfirmationAction.CONFIRM_RETURN,
          LENDER_RETURN_CONFIRMED,
          onWriteFailure,
      )

  private suspend fun queueConfirmation(
      id: String,
      action: ConfirmationAction,
      field: String,
      onWriteFailure: (Exception) -> Unit,
  ) {
    val uid = signedInUid("Sign in to confirm a booking")
    val attempt = ConfirmationAttempt(uid, id, action, UUID.randomUUID().toString())
    journal.put(attempt)
    try {
      val write = firestore.collection(BOOKINGS).document(id).update(field, true)
      write.addOnSuccessListener(Executor { it.run() }) { journal.finish(attempt.token) }
      write.addOnFailureListener(Executor { it.run() }) { failure ->
        logWriteFailure(failure)
        journalScope.launch {
          try {
            journal.fail(attempt.token, failure.toAppError())
          } catch (exception: Exception) {
            logWriteFailure(exception)
          } finally {
            try {
              onWriteFailure(failure)
            } finally {
              journal.finish(attempt.token)
            }
          }
        }
      }
    } catch (exception: Exception) {
      try {
        journal.fail(attempt.token, exception.toAppError())
      } finally {
        journal.finish(attempt.token)
      }
      throw exception
    }
  }

  override suspend fun dismissConfirmationFailure(id: String) {
    val uid = signedInUid("Sign in to confirm a booking")
    journal.attempts.value
        .filter { it.uid == uid && it.bookingId == id && it.error != null }
        .forEach { journal.remove(it.token) }
  }

  override suspend fun acceptBooking(booking: Booking) {
    signedInUid("Sign in to accept a booking")
    require(booking.status == BookingStatus.REQUESTED) { "Only a request can be accepted" }
    require(booking.id.isNotEmpty()) { "Only a stored booking can be accepted" }
    // Read again rather than taken from the screen: a request sent since it loaded must be
    // cancelled too. One sent between this read and the write stays requested; the rules then
    // refuse to accept it, since its days are no longer offered. A finished request is history:
    // the rules refuse to cancel it, which would refuse the whole accept, so it is left as it is.
    val today = today()
    val overlapping =
        getBookingsForListing(booking.listingId).filter {
          it.id != booking.id &&
              it.status == BookingStatus.REQUESTED &&
              it.overlaps(booking) &&
              !it.isFinished(today)
        }
    val bookings = firestore.collection(BOOKINGS)
    val batch = firestore.batch()
    batch.update(bookings.document(booking.id), STATUS, BookingStatus.CONFIRMED.name)
    overlapping.forEach {
      batch.update(bookings.document(it.id), STATUS, BookingStatus.CANCELLED.name)
    }
    // The rules only confirm a booking whose days this same write takes off the listing.
    batch.update(
        firestore.collection(LISTINGS).document(booking.listingId),
        AVAILABLE_DATES,
        FieldValue.arrayRemove(*booking.days.map { it.toEpochDay() }.toTypedArray()),
    )
    batch.commit().await()
  }

  /** The signed-in user's uid, or an UNAUTHENTICATED failure saying [message]. */
  private fun signedInUid(message: String): String =
      auth.currentUser?.uid
          ?: throw FirebaseFirestoreException(
              message,
              FirebaseFirestoreException.Code.UNAUTHENTICATED,
          )

  private fun DocumentSnapshot.toBooking(): Booking {
    fun string(field: String): String =
        getString(field) ?: throw IllegalStateException("Booking $id is missing $field")
    fun confirmation(field: String): Boolean {
      if (!contains(field)) return false
      return get(field) as? Boolean
          ?: throw IllegalStateException("Booking $id has an invalid $field")
    }

    fun days(): Set<LocalDate> {
      val epochDays =
          (get(DAYS) as? List<*>)?.takeIf { it.isNotEmpty() }
              ?: throw IllegalStateException("Booking $id is missing $DAYS")
      return epochDays.mapTo(mutableSetOf()) {
        val epochDay =
            it as? Long ?: throw IllegalStateException("Booking $id has an invalid day: $it")
        try {
          LocalDate.ofEpochDay(epochDay)
        } catch (e: DateTimeException) {
          throw IllegalStateException("Booking $id has an invalid day: $it", e)
        }
      }
    }

    return Booking(
        listingId = string("listingId"),
        renterUid = string("renterUid"),
        lenderUid = string("lenderUid"),
        days = days(),
        status = BookingStatus.valueOf(string(STATUS)),
        id = id,
        handoverNotified = confirmation(LENDER_HANDOVER_CONFIRMED),
        renterHandoverConfirmed = confirmation(RENTER_HANDOVER_CONFIRMED),
        returnNotified = confirmation(RENTER_RETURN_CONFIRMED),
        lenderReturnConfirmed = confirmation(LENDER_RETURN_CONFIRMED),
    )
  }

  private companion object {
    const val BOOKINGS = "bookings"
    // Shared by every read: they all need a signed-in user.
    const val SIGN_IN_TO_READ_BOOKINGS = "Sign in to read bookings"
    const val STATUS = "status"
    // Keep the original Firestore field names for bookings written by earlier clients.
    const val LENDER_HANDOVER_CONFIRMED = "lenderHandoverConfirmed"
    const val RENTER_HANDOVER_CONFIRMED = "renterHandoverConfirmed"
    const val RENTER_RETURN_CONFIRMED = "renterReturnConfirmed"
    const val LENDER_RETURN_CONFIRMED = "lenderReturnConfirmed"

    /** The booked days, as epoch days. */
    const val DAYS = "days"

    /** The listings, and their offered days, as FirebaseListingRepository stores them. */
    const val LISTINGS = "listings"
    const val AVAILABLE_DATES = "availableDates"
  }
}
