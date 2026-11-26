// Co-authored-by: Copilot (Auto)
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.booking.model

import kotlinx.coroutines.flow.Flow

/** Bookings and the IDs whose local writes have not yet been acknowledged by the server. */
data class ObservedBookings(val bookings: List<Booking>, val pendingIds: Set<String> = emptySet())

interface BookingRepository {
  /**
   * Queues a booking request without waiting for server confirmation. Firestore may later reject
   * the write and report the failure through [onWriteFailure].
   *
   * @param onWriteFailure receives a write failure that arrives after local queuing.
   */
  suspend fun requestBooking(
      booking: Booking,
      onWriteFailure: (Exception) -> Unit = {},
  )

  /** Returns bookings for this listing owned by the signed-in lender. */
  suspend fun getBookingsForListing(listingId: String): List<Booking>

  /** Returns the bookings of all the listings of the signed-in lender, in any status. */
  suspend fun getBookingsForLender(): List<Booking>

  /** Returns bookings made by the signed-in renter. */
  suspend fun getBookingsForRenter(): List<Booking>

  /** Observes the lender's bookings for a listing, including local pending-write changes. */
  fun observeBookingsForListing(listingId: String): Flow<ObservedBookings>

  /** Observes the renter's bookings, including local pending-write changes. */
  fun observeBookingsForRenter(): Flow<ObservedBookings>

  /**
   * Queues a cancellation without waiting for server confirmation; offline, the write stays queued
   * until the connection returns. If the server later rejects it, for example because the booking
   * is no longer requested or confirmed, the failure reaches [onWriteFailure].
   *
   * @param onWriteFailure receives a write failure that arrives after local queuing.
   */
  suspend fun cancelBooking(id: String, onWriteFailure: (Exception) -> Unit = {})

  /**
   * Queues one confirmation without waiting for server acknowledgement. Only confirmed bookings can
   * advance: the lender notifies handover, the renter confirms it, the renter notifies return, and
   * the lender confirms it. Rejections are reported through [onWriteFailure].
   */
  suspend fun notifyHandover(id: String, onWriteFailure: (Exception) -> Unit = {})

  /** Queues the renter's handover confirmation. */
  suspend fun confirmHandover(id: String, onWriteFailure: (Exception) -> Unit = {})

  /** Queues the renter's return notification. */
  suspend fun notifyReturn(id: String, onWriteFailure: (Exception) -> Unit = {})

  /** Queues the lender's return confirmation. */
  suspend fun confirmReturn(id: String, onWriteFailure: (Exception) -> Unit = {})

  /**
   * Accepts [booking], a request on one of the signed-in lender's listings, in a single write: it
   * becomes [BookingStatus.CONFIRMED], its days are taken off the listing's available days so no
   * one can request them again, and every other request on the listing that asks for one of those
   * days becomes [BookingStatus.CANCELLED]. Requests for other days stay as they are.
   *
   * Fails when nobody is signed in, or when Firestore cannot answer or refuses the write: for
   * example when [booking] was already answered, or one of its days was taken by another request
   * accepted first.
   */
  suspend fun acceptBooking(booking: Booking)
}
