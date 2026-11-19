// Co-authored-by: Copilot (Auto)
// Co-authored-by: Copilot (GPT-5.6 Terra)
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.model

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.time.DateTimeException
import java.time.LocalDate
import java.util.concurrent.Executor
import kotlinx.coroutines.tasks.await

class FirebaseBookingRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    /** The current day, to leave finished requests alone when accepting. */
    private val today: () -> LocalDate = LocalDate::now,
    private val logWriteFailure: (Exception) -> Unit = {
      Log.e("BookingRepository", "Booking write failed", it)
    },
) : BookingRepository {

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
    val uid = signedInUid("Sign in to read bookings")
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
    val uid = signedInUid("Sign in to read bookings")
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
    val uid = signedInUid("Sign in to read bookings")
    return firestore
        .collection(BOOKINGS)
        .whereEqualTo("renterUid", uid)
        .get()
        .await()
        .documents
        .map { it.toBooking() }
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
    )
  }

  private companion object {
    const val BOOKINGS = "bookings"
    const val STATUS = "status"

    /** The booked days, as epoch days. */
    const val DAYS = "days"

    /** The listings, and their offered days, as FirebaseListingRepository stores them. */
    const val LISTINGS = "listings"
    const val AVAILABLE_DATES = "availableDates"
  }
}
