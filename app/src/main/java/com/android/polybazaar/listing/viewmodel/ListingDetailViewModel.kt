// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.AuthRepository
import com.android.polybazaar.auth.model.User
import com.android.polybazaar.error.model.AppError
import com.android.polybazaar.error.model.toAppError
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingPendingDetails
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.ListingRules
import com.android.polybazaar.listing.model.withPaused
import com.android.polybazaar.profile.model.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the listing detail screen (#22).
 *
 * @property listing the listing shown, or null until it is loaded.
 * @property lender the profile of the listing's owner, or null while it loads, after a failure, or
 *   when the owner has no profile document.
 * @property lenderPhoto the encoded bytes of the lender's profile photo, or null when they have
 *   none, while it downloads, or if it could not be downloaded (the screen then shows the default
 *   photo).
 * @property listingPhotos the listing's photos downloaded so far, by Storage path (one of
 *   [Listing.imagePaths]). A photo still downloading, or whose download failed (offline, for
 *   example), is missing, and the screen shows the placeholder in its place. A reload keeps the
 *   photos shown until their new download arrives.
 * @property pendingDetails the design fields no listing stores yet. Always empty for now, so the
 *   screen shows its "to be confirmed" text for them.
 * @property canContactLender whether "Message" and "Request booking" are enabled, from
 *   [ListingRules.canContactLender]. False until both the listing and the viewer are known.
 * @property isOwner whether the viewer owns the listing, so the screen shows the owner's actions
 *   (edit, pause or resume, delete) instead of the renter's. False until both are known.
 * @property isLoading true while the listing is being loaded.
 * @property isUpdating true while the owner's pause, resume or delete is being saved. The owner's
 *   actions are disabled meanwhile.
 * @property error why the listing could not be loaded, or null.
 * @property lenderError why the lender's profile could not be loaded, or null. The listing is still
 *   shown without it.
 * @property ownerActionError why the owner's last pause, resume or delete failed, or null. Cleared
 *   when the owner tries again.
 * @property isDeleted true once the owner deleted the listing, until the screen has left (see
 *   [ListingDetailViewModel.onDeletedHandled]).
 */
data class ListingDetailUiState(
    val listing: Listing? = null,
    val lender: User? = null,
    val lenderPhoto: ByteArray? = null,
    val listingPhotos: Map<String, ByteArray> = emptyMap(),
    val pendingDetails: ListingPendingDetails = ListingPendingDetails(),
    val canContactLender: Boolean = false,
    val isOwner: Boolean = false,
    val isLoading: Boolean = false,
    val isUpdating: Boolean = false,
    val error: AppError? = null,
    val lenderError: AppError? = null,
    val ownerActionError: AppError? = null,
    val isDeleted: Boolean = false,
)

/**
 * Whether the actions are enabled for [viewer] on [listing]. An unknown viewer gets false: without
 * them the screen cannot tell whether they are verified or own the listing.
 */
internal fun contactAllowed(viewer: User?, listing: Listing): Boolean =
    viewer != null && ListingRules.canContactLender(viewer, listing)

/**
 * Whether [viewer] owns [listing]. An unknown viewer, for example offline before the first read,
 * gets false: they see the renter's view with its actions disabled until the viewer is known.
 */
internal fun ownedBy(viewer: User?, listing: Listing): Boolean =
    viewer != null && listing.ownerUid == viewer.uid

/** Loads one listing, the viewer and the lender's profile for the listing detail screen. */
class ListingDetailViewModel(
    private val listingId: String,
    private val listingRepository: ListingRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ListingDetailUiState())
  val uiState: StateFlow<ListingDetailUiState> = _uiState.asStateFlow()

  private var lenderObservation: Job? = null

  /** The listing photos being downloaded, so that each is downloaded once at a time. */
  private val photosDownloading = mutableSetOf<String>()

  /** The viewer once read, kept for the rest of the screen's life (see [showListing]). */
  private var knownViewer: User? = null

  /**
   * Set once the listing is deleted and never reset, unlike [ListingDetailUiState.isDeleted] which
   * [onDeletedHandled] clears. While the screen is still shown after a delete, it keeps the listing
   * and isOwner, so without this its actions and Retry would reach a listing that is gone.
   */
  private var deleted = false

  init {
    loadListing()
  }

  /**
   * Loads the listing and, until it is known, the viewer, then starts observing the lender, for
   * example again after a failure (Retry). Ignored while a load or an owner action is running, and
   * once the listing is deleted. On failure the listing already shown is kept, so a reload while
   * offline does not empty the screen.
   */
  fun loadListing() {
    val state = _uiState.value
    // Two loads at once could finish in any order, and the older answer would overwrite the newer.
    // A read started during a pause could likewise answer after it and put the old state back.
    // After a delete, the read could only answer NOT_FOUND.
    if (deleted || state.isLoading || state.isUpdating) return
    _uiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      try {
        listingRepository
            .getListing(listingId)
            .onSuccess { listing -> showListing(listing) }
            .onFailure { exception ->
              // Cancellation means the screen was closed: it is not an error to show.
              if (exception is CancellationException) throw exception
              _uiState.update { it.copy(error = exception.toAppError()) }
            }
      } finally {
        // Also on cancellation, otherwise the guard above would block every later load.
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  /**
   * Pauses the listing when [paused] is true, hiding it from Browse and the owner's profile, or
   * resumes it. Only for its owner, once the listing is loaded, and not while the listing is being
   * read again or another owner action is being saved. On failure the listing keeps its state and
   * [ListingDetailUiState.ownerActionError] says why.
   */
  fun setPaused(paused: Boolean) {
    val listingId = ownerActionTarget() ?: return
    runOwnerAction(
        save = { listingRepository.setPaused(listingId, paused) },
        // Shown from the answer rather than read again: Firestore saved exactly this change.
        onSaved = { _uiState.update { it.copy(listing = it.listing?.withPaused(paused)) } },
    )
  }

  /**
   * Deletes the listing and its photos, for its owner, under the same conditions as [setPaused].
   * The screen asks for confirmation first. On success [ListingDetailUiState.isDeleted] tells the
   * screen to leave; on failure the listing stays shown and [ListingDetailUiState.ownerActionError]
   * says why.
   */
  fun deleteListing() {
    val listingId = ownerActionTarget() ?: return
    runOwnerAction(
        save = { listingRepository.deleteListing(listingId) },
        onSaved = {
          deleted = true
          _uiState.update { it.copy(isDeleted = true) }
        },
    )
  }

  /**
   * Called by the screen once it has left after a delete, so going back to it (it stays in the back
   * stack until popped) does not leave a second time. Same pattern as the listing form's
   * `savedListing`. The owner's actions stay refused (see [deleted]).
   */
  fun onDeletedHandled() {
    _uiState.update { it.copy(isDeleted = false) }
  }

  /** The id of the listing an owner action may change now, or null when none may start. */
  private fun ownerActionTarget(): String? {
    val state = _uiState.value
    // isOwner is false until the viewer is known. The security rules refuse anyone else anyway,
    // but this keeps a stray call from reaching Firestore. While a reload runs, its answer could
    // come after the write and show the listing as it was before it (see loadListing).
    if (deleted || !state.isOwner || state.isUpdating || state.isLoading) return null
    return state.listing?.listingId
  }

  /**
   * Runs one of the owner's writes with [save], then calls [onSaved] to show it, or shows why it
   * failed. Offline the write only answers once it reaches the server (Firestore keeps it and sends
   * it on reconnection), so [ListingDetailUiState.isUpdating] stays true until then.
   */
  private fun runOwnerAction(
      save: suspend () -> Result<Unit>,
      onSaved: () -> Unit,
  ) {
    // Set before launching, so a second tap in the same frame is already refused.
    _uiState.update { it.copy(isUpdating = true, ownerActionError = null) }
    viewModelScope.launch {
      try {
        save()
            .onSuccess { onSaved() }
            .onFailure { exception ->
              if (exception is CancellationException) throw exception
              _uiState.update { it.copy(ownerActionError = exception.toAppError()) }
            }
      } finally {
        // Also on cancellation, as in loadListing.
        _uiState.update { it.copy(isUpdating = false) }
      }
    }
  }

  private suspend fun showListing(listing: Listing) {
    // Read once and kept: an offline reload would otherwise lose a viewer it already knew and
    // disable the actions, and every getCurrentUser() also writes the viewer's profile document.
    // Read again only while unknown, so a reload after a failed read can still enable them.
    val viewer = knownViewer ?: currentViewer()
    knownViewer = viewer
    _uiState.update {
      it.copy(
          listing = listing,
          canContactLender = contactAllowed(viewer, listing),
          isOwner = ownedBy(viewer, listing),
          // A photo the owner removed since the last load is dropped with it.
          listingPhotos = it.listingPhotos.filterKeys { path -> path in listing.imagePaths },
      )
    }
    listing.imagePaths.forEach { downloadPhoto(it) }
    listing.ownerUid?.let { observeLender(it) }
  }

  /**
   * Downloads the listing photo at [path] into [ListingDetailUiState.listingPhotos], in its own
   * coroutine so the rest of the screen never waits for it. Every load downloads it again, even if
   * it is already shown: an edit can put a new photo at the same path (updateListing reuses free
   * slots), and the old one stays shown until the new one arrives. A photo still downloading is
   * skipped. Storage has no offline cache, so a failed photo is tried again on the next reload.
   */
  private fun downloadPhoto(path: String) {
    if (!photosDownloading.add(path)) return
    viewModelScope.launch {
      try {
        val photo = listingRepository.loadListingImage(path)
        _uiState.update {
          // Unless a reload meanwhile showed the listing without it.
          if (path in it.listing?.imagePaths.orEmpty()) {
            it.copy(listingPhotos = it.listingPhotos + (path to photo))
          } else {
            it
          }
        }
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        // Only for the log, as in currentViewer(): the placeholder stays, a photo is not worth an
        // error.
        exception.toAppError()
      } finally {
        photosDownloading.remove(path)
      }
    }
  }

  /**
   * The signed-in user, or null when it cannot be read, for example offline before the username is
   * cached. Not shown as an error: the listing can still be read, only the actions stay disabled.
   */
  private suspend fun currentViewer(): User? =
      try {
        authRepository.getCurrentUser()
      } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        // Only for the log: toAppError() logs exceptions it does not recognise, such as a bug in
        // getCurrentUser(), and stays quiet for expected ones such as a network failure.
        exception.toAppError()
        null
      }

  /** Keeps [ListingDetailUiState.lender] and its photo up to date with the owner's profile. */
  private fun observeLender(ownerUid: String) {
    // A listing's owner never changes, so a reload keeps the running observation instead of
    // starting a second one. A failed observation has ended, so a reload restarts it.
    if (lenderObservation?.isActive == true) return
    _uiState.update { it.copy(lenderError = null) }
    lenderObservation = viewModelScope.launch {
      profileRepository
          .observeProfile(ownerUid)
          .onEach { lender -> showLender(lender) }
          // Downloads the photo only when its path changes, not on every profile update (a new
          // bio).
          .map { it?.photoPath }
          .distinctUntilChanged()
          // catch never sees a cancellation, so closing the screen is not shown as an error.
          .catch { exception -> _uiState.update { it.copy(lenderError = exception.toAppError()) } }
          // collectLatest cancels a download still running when the path changes again.
          .collectLatest { photoPath -> photoPath?.let { loadLenderPhoto(it) } }
    }
  }

  private fun showLender(lender: User?) {
    _uiState.update {
      // A removed photo goes back to the default one. The path is always users/{uid}/profile.jpg,
      // so a replaced photo keeps the same path and shows the next time the screen is opened.
      it.copy(
          lender = lender,
          lenderPhoto = if (lender?.photoPath == null) null else it.lenderPhoto,
      )
    }
  }

  /** On failure (offline, for example) the default photo stays: the photo is not worth an error. */
  private suspend fun loadLenderPhoto(photoPath: String) {
    try {
      val photo = profileRepository.loadProfilePhoto(photoPath)
      _uiState.update { it.copy(lenderPhoto = photo) }
    } catch (exception: Exception) {
      if (exception is CancellationException) throw exception
      // Only for the log, as in currentViewer().
      exception.toAppError()
    }
  }
}
