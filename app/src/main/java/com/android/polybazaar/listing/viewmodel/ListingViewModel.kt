// Co-authored-by: Copilot GPT-5.6 Terra
// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar.listing.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.polybazaar.auth.model.AuthRules
import com.android.polybazaar.listing.model.Listing
import com.android.polybazaar.listing.model.ListingRepository
import com.android.polybazaar.listing.model.ListingRules.MAX_DESCRIPTION_LENGTH
import com.android.polybazaar.listing.model.ListingRules.MAX_PHOTOS
import com.android.polybazaar.listing.model.ListingRules.MAX_PRICE
import com.android.polybazaar.listing.model.ListingRules.MAX_REFUND_VALUE
import com.android.polybazaar.listing.model.ListingRules.MAX_TITLE_LENGTH
import com.android.polybazaar.listing.model.ListingRules.buildListing
import com.android.polybazaar.listing.model.ListingRules.categoriesFor
import com.android.polybazaar.listing.model.ListingRules.isValidPriceInput
import com.android.polybazaar.listing.model.ListingRules.isValidRefundInput
import com.android.polybazaar.listing.model.ListingRules.parsePrice
import com.android.polybazaar.listing.model.ListingType
import com.android.polybazaar.listing.model.PhotoStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the "Create listing" form.
 *
 * @property price the price as typed, so that "12," stays on screen while the user types "12,50".
 * @property priceTouched whether the user has left the price field; errors only show after that.
 * @property createdListing the saved listing, until the screen has handled it.
 */
data class ListingUiState(
    val type: ListingType = ListingType.ITEM,
    val title: String = "",
    val description: String = "",
    val price: String = "",
    val refundValue: String = "",
    val priceTouched: Boolean = false,
    val refundTouched: Boolean = false,
    val category: String? = null,
    val photos: List<Uri> = emptyList(),
    val createdListing: Listing? = null,
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
) {
  /** The listing the form describes, or null while it is incomplete or invalid. */
  val listing: Listing?
    get() = buildListing(type, title, description, price, category, photos, refundValue)

  val canCreate: Boolean
    get() = listing != null && !isCreating

  val categories: List<String>
    get() = categoriesFor(type)

  val showPriceError: Boolean
    get() = showAmountError(priceTouched, price)

  val showRefundError: Boolean
    get() =
        type == ListingType.ITEM && showAmountError(refundTouched, refundValue, MAX_REFUND_VALUE)
}

/** Whether to flag [input] as an invalid amount: only once the user has left a non-empty field. */
private fun showAmountError(touched: Boolean, input: String, max: Double = MAX_PRICE): Boolean =
    touched && input.isNotEmpty() && parsePrice(input, max) == null

/**
 * Holds the "Create listing" form and saves it through [listingRepository].
 *
 * @param photoStorage keeps the photos taken with the camera until they are uploaded.
 * @param savedStateHandle keeps the form when Android kills the app, which can happen while the
 *   camera app is in front. Pass `createSavedStateHandle()` from the factory: a handle made by hand
 *   is not saved by Android.
 */
class ListingViewModel(
    private val listingRepository: ListingRepository,
    private val photoStorage: PhotoStorage,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val _uiState = MutableStateFlow(savedStateHandle.restoreForm())
  val uiState: StateFlow<ListingUiState> = _uiState.asStateFlow()

  /** The photo the camera app is taking, until it closes. */
  private var pendingPhoto: Uri?
    get() = savedStateHandle[PENDING_PHOTO]
    set(value) {
      savedStateHandle[PENDING_PHOTO] = value
    }

  /** When retaking a photo, the index of the photo that [pendingPhoto] replaces. */
  private var retakeIndex: Int?
    get() = savedStateHandle[RETAKE_INDEX]
    set(value) {
      savedStateHandle[RETAKE_INDEX] = value
    }

  init {
    // Photos of a form the user left are never published, so they are deleted when a form opens.
    // Kept: the photos of this form if it was restored, and the one the camera app is taking.
    photoStorage.deleteAllExcept(_uiState.value.photos + listOfNotNull(pendingPhoto))
  }

  /** Categories differ between items and services, so one that no longer applies is dropped. */
  fun selectType(type: ListingType) = updateState {
    it.copy(
        type = type,
        category = it.category?.takeIf { category -> category in categoriesFor(type) },
    )
  }

  fun updateTitle(title: String) = updateState {
    it.copy(title = AuthRules.limitInput(title, MAX_TITLE_LENGTH))
  }

  fun updateDescription(description: String) = updateState {
    it.copy(description = AuthRules.limitInput(description, MAX_DESCRIPTION_LENGTH))
  }

  /** Ignores input that is not an amount, so the field never shows letters. */
  fun updatePrice(price: String) {
    if (isValidPriceInput(price)) updateState { it.copy(price = price) }
  }

  fun updateRefundValue(refundValue: String) {
    if (isValidRefundInput(refundValue)) updateState { it.copy(refundValue = refundValue) }
  }

  fun leavePriceField() = updateState { it.copy(priceTouched = true) }

  fun leaveRefundField() = updateState { it.copy(refundTouched = true) }

  fun selectCategory(category: String) = updateState { it.copy(category = category) }

  /** Adds photos picked in the gallery, skipping duplicates and those beyond [MAX_PHOTOS]. */
  fun addPhotos(picked: List<Uri>) {
    // The photos being uploaded are cleared once saved, so a photo added meanwhile would be lost.
    if (_uiState.value.isCreating) return
    updateState {
      it.copy(
          photos = (it.photos + picked.filterNot { photo -> photo in it.photos }).take(MAX_PHOTOS)
      )
    }
  }

  fun removePhoto(photo: Uri) {
    // The repository is still reading the photos, so none may be deleted.
    if (_uiState.value.isCreating) return
    updateState { it.copy(photos = it.photos - photo) }
    photoStorage.delete(photo)
  }

  /**
   * Prepares a photo for the camera app to take, replacing the photo at [retakeIndex] if given.
   * Call [finishTakingPhoto] once the camera app closes.
   *
   * @return the URI the camera app writes the photo to, or null when no photo can be taken.
   */
  fun startTakingPhoto(retakeIndex: Int?): Uri? {
    // Like gallery photos, a photo taken during the upload would be lost.
    if (_uiState.value.isCreating) return null
    // A second tap before the camera app opened replaces the first photo, which stays empty.
    pendingPhoto?.let(photoStorage::delete)
    val photo = photoStorage.newPhoto() ?: return null
    pendingPhoto = photo.uri
    this.retakeIndex = retakeIndex
    return photo.cameraUri
  }

  /** Adds the photo the camera app took, or deletes its empty file if [saved] is false. */
  fun finishTakingPhoto(saved: Boolean) {
    val photo = pendingPhoto ?: return
    val index = retakeIndex
    pendingPhoto = null
    retakeIndex = null
    if (!saved) {
      photoStorage.delete(photo)
      return
    }
    val before = _uiState.value.photos
    val after = withTakenPhoto(before, photo, index)
    updateState { it.copy(photos = after) }
    // Deletes the retaken photo, or the new one if the form was already full.
    (before + photo - after.toSet()).forEach(photoStorage::delete)
  }

  fun createListing() {
    val state = _uiState.value
    val listing = state.listing ?: return
    if (state.isCreating) return

    updateState { it.copy(createdListing = null, isCreating = true, errorMessage = null) }
    viewModelScope.launch {
      try {
        val createdListing = listingRepository.createListing(listing)
        // Uploaded, so the camera photos are no longer needed on the phone.
        listing.images.forEach(photoStorage::delete)
        // A fresh form, so coming back to it cannot create the same listing twice.
        updateState { ListingUiState(createdListing = createdListing) }
      } catch (exception: Exception) {
        if (exception is CancellationException) {
          updateState { it.copy(isCreating = false) }
          throw exception
        }
        updateState {
          it.copy(
              isCreating = false,
              errorMessage = exception.message ?: "Unable to create listing",
          )
        }
      }
    }
  }

  fun clearCreatedListing() = updateState { it.copy(createdListing = null) }

  private fun updateState(transform: (ListingUiState) -> ListingUiState) {
    _uiState.update(transform)
    savedStateHandle.saveForm(_uiState.value)
  }

  private companion object {
    const val TYPE = "type"
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val PRICE = "price"
    const val REFUND_VALUE = "refundValue"
    const val PRICE_TOUCHED = "priceTouched"
    const val REFUND_TOUCHED = "refundTouched"
    const val CATEGORY = "category"
    const val PHOTOS = "photos"
    const val PENDING_PHOTO = "pendingPhoto"
    const val RETAKE_INDEX = "retakeIndex"

    /** Only the form is saved: a creation in progress does not survive the app being killed. */
    fun SavedStateHandle.saveForm(state: ListingUiState) {
      set(TYPE, state.type.name)
      set(TITLE, state.title)
      set(DESCRIPTION, state.description)
      set(PRICE, state.price)
      set(REFUND_VALUE, state.refundValue)
      set(PRICE_TOUCHED, state.priceTouched)
      set(REFUND_TOUCHED, state.refundTouched)
      set(CATEGORY, state.category)
      set(PHOTOS, ArrayList(state.photos))
    }

    fun SavedStateHandle.restoreForm() =
        ListingUiState(
            type = get<String>(TYPE)?.let(ListingType::valueOf) ?: ListingType.ITEM,
            title = get(TITLE) ?: "",
            description = get(DESCRIPTION) ?: "",
            price = get(PRICE) ?: "",
            refundValue = get(REFUND_VALUE) ?: "",
            priceTouched = get(PRICE_TOUCHED) ?: false,
            refundTouched = get(REFUND_TOUCHED) ?: false,
            category = get(CATEGORY),
            photos = get<List<Uri>>(PHOTOS).orEmpty(),
        )
  }
}

/** Adds a camera [photo], or swaps it in for the photo at [retakeIndex] when retaking one. */
private fun withTakenPhoto(photos: List<Uri>, photo: Uri, retakeIndex: Int?): List<Uri> =
    if (retakeIndex != null && retakeIndex in photos.indices) {
      photos.toMutableList().apply { set(retakeIndex, photo) }
    } else {
      (photos + photo).take(MAX_PHOTOS)
    }
