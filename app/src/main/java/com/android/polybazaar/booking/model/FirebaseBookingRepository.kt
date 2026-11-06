// Co-authored-by: Copilot (Auto)
package com.android.polybazaar.booking.model

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.time.LocalDate
import kotlinx.coroutines.tasks.await

class FirebaseBookingRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : BookingRepository {

  override suspend fun requestBooking(booking: Booking) {
    require(booking.status == BookingStatus.REQUESTED) { "A new booking must be requested" }
    require(!booking.endDate.isBefore(booking.startDate)) { "Booking end date precedes start date" }
    firestore
        .collection("bookings")
        .document()
        .set(
            mapOf(
                "listingId" to booking.listingId,
                "renterUid" to booking.renterUid,
                "lenderUid" to booking.lenderUid,
                "startDate" to booking.startDate.toEpochDay(),
                "endDate" to booking.endDate.toEpochDay(),
                "status" to booking.status.name,
            )
        )
        .await()
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

  private fun DocumentSnapshot.toBooking(): Booking {
    fun string(field: String): String =
        getString(field) ?: throw IllegalStateException("Booking $id is missing $field")
    fun date(field: String): LocalDate =
        LocalDate.ofEpochDay(
            getLong(field) ?: throw IllegalStateException("Booking $id is missing $field")
        )

    return Booking(
        listingId = string("listingId"),
        renterUid = string("renterUid"),
        lenderUid = string("lenderUid"),
        startDate = date("startDate"),
        endDate = date("endDate"),
        status = BookingStatus.valueOf(string("status")),
    )
  }
}
