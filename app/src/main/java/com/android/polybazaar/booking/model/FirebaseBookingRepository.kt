// Co-authored-by: Copilot (Auto)
// Co-authored-by: Copilot (GPT-5.6 Terra)
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.model

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.time.DateTimeException
import java.time.LocalDate
import java.util.concurrent.Executor
import kotlinx.coroutines.tasks.await

class FirebaseBookingRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val logWriteFailure: (Exception) -> Unit = {
      Log.e("BookingRepository", "Booking write failed", it)
    },
) : BookingRepository {

  override suspend fun requestBooking(
      booking: Booking,
      onWriteFailure: (Exception) -> Unit,
  ) {
    val renterUid =
        auth.currentUser?.uid
            ?: throw FirebaseFirestoreException(
                "Sign in to request a booking",
                FirebaseFirestoreException.Code.UNAUTHENTICATED,
            )
    require(booking.status == BookingStatus.REQUESTED) { "A new booking must be requested" }
    require(booking.days.isNotEmpty()) { "A booking needs at least one day" }
    firestore
        .collection("bookings")
        .document()
        .set(
            mapOf(
                "listingId" to booking.listingId,
                "renterUid" to renterUid,
                "lenderUid" to booking.lenderUid,
                // Earliest first: the rules check that the first day is not in the past.
                DAYS to booking.days.sorted().map { it.toEpochDay() },
                "status" to booking.status.name,
            )
        )
        .addOnFailureListener(Executor { it.run() }) {
          logWriteFailure(it)
          onWriteFailure(it)
        }
  }

  override suspend fun getBookingsForListing(listingId: String): List<Booking> {
    val uid =
        auth.currentUser?.uid
            ?: throw FirebaseFirestoreException(
                "Sign in to read bookings",
                FirebaseFirestoreException.Code.UNAUTHENTICATED,
            )
    return firestore
        .collection("bookings")
        .whereEqualTo("listingId", listingId)
        .whereEqualTo("lenderUid", uid)
        .get()
        .await()
        .documents
        .map { it.toBooking() }
  }

  override suspend fun getBookingsForRenter(): List<Booking> {
    val uid =
        auth.currentUser?.uid
            ?: throw FirebaseFirestoreException(
                "Sign in to read bookings",
                FirebaseFirestoreException.Code.UNAUTHENTICATED,
            )
    return firestore
        .collection("bookings")
        .whereEqualTo("renterUid", uid)
        .get()
        .await()
        .documents
        .map { it.toBooking() }
  }

  override suspend fun cancelBooking(
      id: String,
      onWriteFailure: (Exception) -> Unit,
      onWriteSuccess: () -> Unit,
  ) {
    if (auth.currentUser == null) {
      throw FirebaseFirestoreException(
          "Sign in to cancel a booking",
          FirebaseFirestoreException.Code.UNAUTHENTICATED,
      )
    }
    firestore
        .collection("bookings")
        .document(id)
        .update("status", BookingStatus.CANCELLED.name)
        .addOnFailureListener(Executor { it.run() }) {
          logWriteFailure(it)
          onWriteFailure(it)
        }
        .addOnSuccessListener(Executor { it.run() }) { onWriteSuccess() }
  }

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
        status = BookingStatus.valueOf(string("status")),
        id = id,
    )
  }

  private companion object {
    /** The booked days, as epoch days. */
    const val DAYS = "days"
  }
}
